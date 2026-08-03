# 신정승무 캘린더

Android(Kotlin/Compose) + Firebase(hosting/firestore). 폴더블 대응 앱.
사용자(카스)는 비개발자입니다. 코드 조각 대신 **동작하는 APK와 스크린샷**으로 보고하세요.

## 규칙

- 버전 올릴 땐 `app/build.gradle.kts`의 실제 `versionCode`를 읽고 +1. 문서·기억 속 숫자를 믿지 말 것 — 한 번 어긋난 적 있습니다.
- 산출물: `C:\Users\admin\Downloads\신정승무캘린더_체험판.apk`(덮어쓰기), 플레이 콘솔용 `.aab`는 같은 폴더에 버전명으로.
- 에뮬레이터 `emulator-5554`에서 실화면 스크린샷을 확인한 뒤 보고. 테스트 후 `night mode`·`wm size` 원복.
- 커밋하면 바로 `git push origin main`. PR 만들지 말 것 — 1인 저장소라 과한 절차입니다(PR #2에서 정리됨).
- `keystore.properties`, `keystore/`, `local.properties`는 gitignore 상태. 커밋 전 민감파일 스캔.
- 이 저장소엔 캘린더 외 다른 프로젝트 파일(`7_threads_auto` 등)도 섞여 있습니다. 건드리지 마세요.

## 함정

- `MainCalendarScreen.kt`에서 `rememberSaveable(key) { mutableStateOf(...) }`는 **초기값이 먹지 않습니다.**
  제네릭 오버로드가 `T = MutableState<Boolean>` + `autoSaver()`로 잡혀 조용히 깨집니다. `remember`를 쓰세요.
  (대신 회전 시 다이얼로그 열림 상태는 유지 안 됨 — 알고 둔 것)

레이아웃 수치 이력·미수정 이슈·백로그는 [docs/project-notes.md](docs/project-notes.md).
레이아웃을 손댈 땐 먼저 읽고 회귀 확인.
