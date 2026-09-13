# CHD 일괄 변환 - Android

Termux 없이 Android 앱에서 CHDMan을 직접 실행하는 CUE/BIN ↔ CHD 일괄 변환기입니다.

## 기능
- 선택한 폴더의 모든 `.cue`를 같은 이름의 `.chd`로 변환
- 선택한 폴더의 모든 `.chd`를 같은 이름의 `.cue` + `.bin`으로 추출
- 한글/공백 파일명 지원
- 기존 결과 파일이 있으면 덮어쓰지 않고 건너뜀
- 진행 로그와 중지 버튼
- Android 11 이상에서 "모든 파일에 대한 접근" 권한을 사용하여 큰 디스크 이미지도 중간 복사 없이 처리
- Android 11 이상 / ARM64-v8a 전용

## APK 빌드
이 프로젝트를 GitHub 저장소에 업로드하면 `.github/workflows/build-apk.yml`이 APK를 자동 빌드합니다.
Actions → Build Android APK → Run workflow 후 생성된 Artifact에서 `app-debug.apk`를 받을 수 있습니다.

워크플로는 빌드 시점에 Android ARM64 CHDMan을 고정된 Mimir 커밋에서 내려받고 Git blob SHA를 검증한 뒤 APK에 포함합니다.

## 앱 사용
1. APK 설치
2. 첫 실행 시 "모든 파일에 대한 접근" 허용
3. `변환할 폴더 선택`
4. `CUE → CHD` 또는 `CHD → CUE + BIN` 선택
5. 결과는 원본과 같은 폴더에 생성

## 주의
- CUE가 참조하는 BIN/트랙 파일은 CUE와 같은 폴더에 있어야 합니다.
- 변환 도중 앱을 강제 종료하거나 저장 장치를 분리하지 마세요.
- APK에 포함되는 CHDMan은 GPL-2.0-or-later 구성요소입니다. 재배포 시 라이선스/소스 제공 의무를 확인하세요.
