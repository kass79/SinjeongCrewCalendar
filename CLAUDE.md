# 신정승무 캘린더

Android(Kotlin/Compose) + Firebase(hosting/firestore). 폴더블 대응 앱.
사용자(카스)는 비개발자입니다. 코드 조각 대신 **동작하는 APK와 스크린샷**으로 보고하세요.

## 현재 상태

- `main` = `origin/main` 동기화됨. v1.6.6, **versionCode 18**
- 산출물: `C:\Users\admin\Downloads\신정승무캘린더_체험판.apk` (덮어쓰기), 플레이 콘솔용 `신정승무캘린더_v1.6.6.aab`
- **버전 올릴 땐 `app/build.gradle.kts`의 실제 `versionCode`를 확인하고 +1 하세요.** 이 문서 숫자를 믿지 말 것 — 한 번 어긋난 적 있습니다.
- 검증은 에뮬레이터 `emulator-5554`로 실화면 스크린샷까지 확인 후 보고

## 규칙

- 커밋하면 **바로 `git push origin main`**. PR 만들지 말 것 — 1인 저장소라 과한 절차입니다(PR #2에서 정리됨).
- 이 저장소엔 캘린더 외 다른 프로젝트 파일(`7_threads_auto` 등)도 섞여 있습니다. 건드리지 마세요.
- `keystore.properties`, `keystore/`, `local.properties`는 gitignore 상태. 커밋 전 민감파일 스캔.
- 에뮬레이터 테스트 후 `night mode`·`wm size` 원복.

## 레이아웃 히스토리 (되돌리기 쉬운 수치들)

- **펼침 비율 달력 44 : 행로표 패널 56** — v1.6.4에서 재배분. 행로표 1044px → 1236px (+18.4%), 패널 bleed 10dp가 패딩 10dp를 상쇄해 폭 100% 사용.
- 행로표 확대 3단(v1.6.3): 에셋 여백 제거(+13.5%) + 화면 여백 축소 + 캡션 제거. `tools/trim_routes.py`
- 접힘 바텀시트 메모 키보드 가림 해결됨(`70f59c4`). 레이아웃 손댈 때 회귀 확인 필수.
- **동료근무 DialSheet ★ 토글**(v1.6.6). 이름 탭 시트에서 바로 즐겨찾기 지정/해제.
  내장 명단·로그인 근무자는 `RosterViewModel.setFav()`가 그 시점에 `Mate`를 만들어 저장하고,
  해제는 `favGroup=null`만 (Mate 삭제 안 함 — 수동 등록분 보호). 매칭은 이름 문자열 기준.
- **근무일 탭 → 전체화면 행로표 자동 열림**(v1.6.5 `d7d4975`). `DayDetailContent`의
  `var showRoute by remember(day.date, autoRoute) { mutableStateOf(autoRoute && routeAsset != null) }`
  한 줄이 전부라, 되돌리려면 초기값을 `false`로 바꾸면 됩니다. `autoRoute`는 펼침에서 앱 실행
  직후(오늘 자동선택) 다이얼로그가 튀는 걸 막는 용도 — 첫 탭부터 열립니다.

## 함정

- 이 파일(`MainCalendarScreen.kt`)에서 `rememberSaveable(key) { mutableStateOf(...) }`는 **초기값이 먹지 않습니다.**
  제네릭 오버로드가 `T = MutableState<Boolean>` + `autoSaver()`로 잡혀서 조용히 깨집니다. `remember`를 쓰세요.
  (대신 회전 시 다이얼로그 열림 상태는 유지 안 됨 — 알고 둔 것)

## 남은 이슈 (보고만 하고 미수정)

1. 인라인 행로표 우하단 확대 아이콘이 "야간 8:00" 셀 끝자락을 반투명하게 덮음
2. 오늘이 비번(`~`)이면 위젯 `KEY_SUB`("오늘 출근 HH:MM")가 비어 하단 여백이 뜸 → `verticalAlignment = CenterVertically` 한 줄로 해결 가능
3. 달력 44%에서 절기·공휴일 3글자 말줄임(`제헌절` → `제...`). 접힘 화면에선 원래부터 그랬음
4. `BundledRoster`에 그룹 간 동명이인 3쌍(김지환·박두원·이용석 — 본선 기관사/본선 차장).
   즐겨찾기가 이름 Set 기준이라 한쪽에 ★을 달면 다른 쪽에도 붙는다. 전화번호는 이미
   `phoneFor(name, isConductor)`로 구분됨. 고치려면 `Mate` 키를 이름+소속으로 바꿔야 함(저장 포맷 변경)
5. 동료근무 이름 칸이 64dp라 `Tester (나)` 같은 긴 이름은 말줄임(`Tester …`). ★ 추가 전부터 그랬음

## 다음 후보

- 달력 40 : 행로표 60으로 더 재배분
- 펼침에서 날짜 탭 시 전체화면 행로표부터 띄우기
