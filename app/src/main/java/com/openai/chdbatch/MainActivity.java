package com.openai.chdbatch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 1001;
    private static final int REQ_STORAGE = 1002;

    private TextView folderText;
    private TextView logText;
    private ProgressBar progressBar;
    private Button selectButton;
    private Button cueToChdButton;
    private Button chdToCueButton;
    private Button stopButton;
    private File selectedFolder;
    private volatile Process runningProcess;
    private volatile boolean stopRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestStorageAccessIfNeeded();
        appendLog("CHD 일괄 변환기 준비 완료");
        appendLog("ARM64 Android 기기용입니다.");
    }

    private void buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("CHD 일괄 변환");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("Termux 없이 앱에서 직접 CUE/BIN ↔ CHD 변환");
        desc.setTextSize(14);
        desc.setPadding(0, dp(8), 0, dp(12));
        desc.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        folderText = new TextView(this);
        folderText.setText("폴더: 선택되지 않음");
        folderText.setTextSize(14);
        folderText.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(folderText, new LinearLayout.LayoutParams(-1, -2));

        selectButton = new Button(this);
        selectButton.setText("1. 변환할 폴더 선택");
        selectButton.setOnClickListener(v -> chooseFolder());
        root.addView(selectButton, new LinearLayout.LayoutParams(-1, -2));

        cueToChdButton = new Button(this);
        cueToChdButton.setText("2. 폴더의 CUE → CHD 일괄 변환");
        cueToChdButton.setOnClickListener(v -> startBatch(true));
        root.addView(cueToChdButton, new LinearLayout.LayoutParams(-1, -2));

        chdToCueButton = new Button(this);
        chdToCueButton.setText("3. 폴더의 CHD → CUE + BIN 일괄 추출");
        chdToCueButton.setOnClickListener(v -> startBatch(false));
        root.addView(chdToCueButton, new LinearLayout.LayoutParams(-1, -2));

        stopButton = new Button(this);
        stopButton.setText("중지");
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopCurrentJob());
        root.addView(stopButton, new LinearLayout.LayoutParams(-1, -2));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(18));
        pp.setMargins(0, dp(8), 0, dp(8));
        root.addView(progressBar, pp);

        ScrollView scroll = new ScrollView(this);
        logText = new TextView(this);
        logText.setTextSize(12);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(logText, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestStorageAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle("파일 접근 권한 필요")
                        .setMessage("게임 이미지가 있는 폴더를 직접 읽고 결과 파일을 같은 폴더에 저장하려면 '모든 파일에 대한 접근' 권한이 필요합니다.")
                        .setPositiveButton("권한 설정", (d, w) -> {
                            try {
                                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                i.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(i);
                            } catch (Exception e) {
                                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                            }
                        })
                        .setNegativeButton("나중에", null)
                        .show();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void chooseFolder() {
        if (!hasStorageAccess()) {
            requestStorageAccessIfNeeded();
            appendLog("먼저 파일 접근 권한을 허용하세요.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            } catch (Exception ignored) {}

            File path = treeUriToFile(uri);
            if (path == null || !path.isDirectory()) {
                appendLog("선택한 폴더의 실제 경로를 확인할 수 없습니다: " + uri);
                new AlertDialog.Builder(this)
                        .setTitle("폴더 경로 오류")
                        .setMessage("내부 저장소 또는 SD카드의 일반 폴더를 선택해 주세요.")
                        .setPositiveButton("확인", null)
                        .show();
                return;
            }
            selectedFolder = path;
            folderText.setText("폴더: " + path.getAbsolutePath());
            appendLog("폴더 선택: " + path.getAbsolutePath());
        }
    }

    private File treeUriToFile(Uri uri) {
        try {
            if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
            String docId = DocumentsContract.getTreeDocumentId(uri);
            String[] split = docId.split(":", 2);
            String volume = split[0];
            String relative = split.length > 1 ? split[1] : "";
            File base;
            if ("primary".equalsIgnoreCase(volume)) {
                base = Environment.getExternalStorageDirectory();
            } else {
                base = new File("/storage/" + volume);
            }
            return relative.isEmpty() ? base : new File(base, relative);
        } catch (Exception e) {
            return null;
        }
    }

    private void startBatch(boolean cueToChd) {
        if (!hasStorageAccess()) {
            requestStorageAccessIfNeeded();
            appendLog("파일 접근 권한이 없습니다.");
            return;
        }
        if (selectedFolder == null || !selectedFolder.isDirectory()) {
            appendLog("먼저 변환할 폴더를 선택하세요.");
            return;
        }

        File nativeBin = new File(getApplicationInfo().nativeLibraryDir, "libchdman.so");
        if (!nativeBin.exists()) {
            appendLog("오류: APK 내부 chdman을 찾을 수 없습니다.");
            return;
        }

        File[] files = selectedFolder.listFiles();
        if (files == null) {
            appendLog("폴더를 읽을 수 없습니다.");
            return;
        }

        String ext = cueToChd ? ".cue" : ".chd";
        List<File> targets = new ArrayList<>();
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(ext)) targets.add(f);
        }
        Collections.sort(targets, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        if (targets.isEmpty()) {
            appendLog("대상 " + ext + " 파일이 없습니다.");
            return;
        }

        setBusy(true);
        stopRequested = false;
        progressBar.setMax(targets.size());
        progressBar.setProgress(0);
        appendLog("대상 파일: " + targets.size() + "개");

        new Thread(() -> {
            int ok = 0;
            int skip = 0;
            int fail = 0;
            for (int i = 0; i < targets.size(); i++) {
                if (stopRequested) break;
                File in = targets.get(i);
                String base = stripExtension(in.getName());
                File primaryOut = new File(selectedFolder, base + (cueToChd ? ".chd" : ".cue"));
                File binOut = cueToChd ? null : new File(selectedFolder, base + ".bin");

                if (primaryOut.exists() || (binOut != null && binOut.exists())) {
                    appendLog("[건너뜀] 결과 파일 존재: " + base);
                    skip++;
                    setProgress(i + 1);
                    continue;
                }

                appendLog("[시작] " + in.getName());
                List<String> cmd;
                if (cueToChd) {
                    cmd = Arrays.asList(nativeBin.getAbsolutePath(), "createcd", "-i", in.getAbsolutePath(), "-o", primaryOut.getAbsolutePath());
                } else {
                    cmd = Arrays.asList(nativeBin.getAbsolutePath(), "extractcd", "-i", in.getAbsolutePath(), "-o", primaryOut.getAbsolutePath(), "-ob", binOut.getAbsolutePath());
                }

                int code = runChdman(cmd);
                if (stopRequested) {
                    appendLog("사용자가 중지했습니다.");
                    break;
                }
                if (code == 0) {
                    appendLog("[완료] " + in.getName());
                    ok++;
                } else {
                    appendLog("[실패] " + in.getName() + " / 종료 코드 " + code);
                    if (primaryOut.exists() && primaryOut.length() == 0) primaryOut.delete();
                    if (binOut != null && binOut.exists() && binOut.length() == 0) binOut.delete();
                    fail++;
                }
                setProgress(i + 1);
            }
            final int fOk = ok, fSkip = skip, fFail = fail;
            runOnUiThread(() -> {
                setBusy(false);
                appendLog("작업 종료 - 성공 " + fOk + ", 건너뜀 " + fSkip + ", 실패 " + fFail);
            });
        }, "chd-batch-worker").start();
    }

    private int runChdman(List<String> cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.directory(selectedFolder);
            runningProcess = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) appendLog("  " + line);
                    if (stopRequested) {
                        runningProcess.destroy();
                        break;
                    }
                }
            }
            int code = runningProcess.waitFor();
            runningProcess = null;
            return code;
        } catch (Exception e) {
            appendLog("실행 오류: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            runningProcess = null;
            return -1;
        }
    }

    private void stopCurrentJob() {
        stopRequested = true;
        Process p = runningProcess;
        if (p != null) {
            p.destroy();
            if (Build.VERSION.SDK_INT >= 26) p.destroyForcibly();
        }
        appendLog("중지 요청됨...");
    }

    private void setBusy(boolean busy) {
        runOnUiThread(() -> {
            selectButton.setEnabled(!busy);
            cueToChdButton.setEnabled(!busy);
            chdToCueButton.setEnabled(!busy);
            stopButton.setEnabled(busy);
        });
    }

    private void setProgress(int value) {
        runOnUiThread(() -> progressBar.setProgress(value));
    }

    private String stripExtension(String name) {
        int p = name.lastIndexOf('.');
        return p > 0 ? name.substring(0, p) : name;
    }

    private void appendLog(String text) {
        runOnUiThread(() -> {
            logText.append(text + "\n");
            View parent = (View) logText.getParent();
            if (parent instanceof ScrollView) {
                parent.post(() -> ((ScrollView) parent).fullScroll(View.FOCUS_DOWN));
            }
        });
    }
}
