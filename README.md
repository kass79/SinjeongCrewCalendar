# 신정승무 캘린더 (SinjeongCrewCalendar)

서울교통공사 신정승무사업소 승무원(기관사·차장)용 근무 캘린더 안드로이드 앱.
기존 `com.mycompany.sinjeongcalendar` 앱을 Kotlin + Jetpack Compose + Firebase 기반으로 재구축한 프로젝트입니다.

- **디자인 시안(미리보기)**: https://claude.ai/code/artifact/2ae5f11d-12ef-4081-955c-e10161b6ee9d

## 기술 스택

| 영역 | 선택 |
|---|---|
| UI | Jetpack Compose · Material 3 (다이나믹 컬러, 다크모드) |
| 아키텍처 | Clean Architecture (data / domain / presentation) + MVVM |
| DI | Hilt |
| 비동기 | Coroutines / Flow |
| 백엔드 | Firebase Auth · Firestore(오프라인 캐시) · FCM(공지 알림) |
| 위젯 | Glance (오늘/내일 근무) + WorkManager 갱신 |
| 외부 연동 | Google Calendar API 양방향 동기화 |

## 폴더 구조

```
SinjeongCrewCalendar/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml            # 버전 카탈로그
├── firebase.json                        # hosting + firestore.rules 경로
├── firestore.rules                      # ★ 배포되는 보안 규칙 (여기 하나뿐)
├── firestore/
│   └── schema.json                      # 초기 설계 문서 (현재 앱은 users·rosterOverrides만 사용)
└── app/src/main/
    ├── AndroidManifest.xml
    ├── res/                             # 테마, 위젯 정보, 아이콘
    └── java/com/sinjeong/crewcalendar/
        ├── SinjeongApp.kt               # Hilt Application + 알림 채널
        ├── MainActivity.kt              # NavHost + 하단 내비 4탭 (달력/교번표/동료/설정)
        ├── di/AppModule.kt              # Firebase/Repository 바인딩
        ├── domain/
        │   ├── model/                   # User·Dia·Pattern·Schedule·DutyCode
        │   ├── repository/              # 인터페이스
        │   └── usecase/                 # GetMonthSchedule·GetTodayDuty
        ├── data/
        │   ├── repository/              # Firestore 구현체
        │   └── calendar/                # GoogleCalendarSyncManager
        ├── presentation/
        │   ├── theme/                   # 2호선 그린 시드 M3 팔레트 + 근무색 토큰
        │   └── calendar/                # MainCalendarScreen + ViewModel
        ├── widget/                      # Glance 위젯 + Worker
        └── fcm/                         # 공지/근무변경 푸시 수신
```

## 도메인 모델 요약

- **DutyCode** — 근무코드 파서. `"14"`(본선), `"44비"`(비번), `"휴5"`, `"대2"`, `"지13"·"지대1"·"지휴4"`(지선), `"~"`, `"주"` 전부 인식. 33~51은 야간으로 분류.
- **Dia** — 시각표(PDF) 1행. 평일/토/휴 구분 + 야간은 평평·평토·토휴·휴휴·휴평 조합별 시각. 출근~종료, 구간(legs) 리스트, 개정(revision).
- **Pattern** — 순환 근무표 (예: 본선 29칸). `anchorDate` + `patternOffset`으로 임의 날짜 근무 계산 → Firestore 읽기 최소화.
- **Schedule** — 날짜별 오버라이드(패턴과 다를 때만 문서 생성). 출처: 근무표 업로드 / 수동.

## 시작하기

1. **Firebase 프로젝트 생성** → Android 앱 등록 (`com.sinjeong.crewcalendar`)
   - `google-services.json`을 `app/` 폴더에 복사
   - Authentication: Google 로그인 활성화
   - Firestore 생성 후 루트 `firestore.rules` 배포(`firebase.json`이 이 파일을 가리킨다):
     ```bash
     firebase deploy --only firestore:rules
     ```
     콘솔에서 붙여넣는 비개발자용 절차·되돌리기 안내는 저장소 밖 별도 문서로 전달됨
     (`Firestore규칙_적용안내.md`). 규칙을 고치면 `firestore/rules.test.mjs`를 돌릴 것.
2. **Google Calendar API** — Google Cloud Console에서 Calendar API 활성화, OAuth 동의 화면 구성
3. **빌드**
   ```bash
   ./gradlew :app:assembleDebug
   ```

## 번들 데이터 (오프라인 기본값) — `domain/model/Bundled.kt`

25.03.04 개정 시각표·교번순서·2026 공휴일이 앱에 내장되어 있어 Firestore 시딩 없이도 동작한다:
- 패턴: 지선 29칸(`bundled-branch`), 본선 108칸(`bundled-main`) — 개정 시 관리자가 Firestore `patterns`에 같은 ID로 올리면 우선 적용
- 시각표: 지선 평/휴(전반·후반사업 포함), 본선 주간 평/휴, 본선 야간 4종(평평/평휴/휴휴/휴평), 대기조
- 공휴일: 2026 법정공휴일(휴일 시각 자동 적용) + 기념일(제헌절) + 절기

## 구현 상태 (시안 v10 반영 완료)

- [x] 달력: 근무칩 + 출근시각 + 메모, 공휴일 빨강+이름, 야간 보라(지선 지10~14 포함)
- [x] 앱바: 휴 N개 칩 · 근무선택 버튼 · 다크/라이트 토글(저장됨) · 오늘
- [x] 근무선택 2단계: 소속(지선/본선기관사/본선차장) → 교번 그리드 → 전체 자동입력
- [x] 근무변경: 하루만 오버라이드 (직접입력/변경없음 + 23종), 달력 2줄 표시(원래근무 취소선)
- [x] 날짜 상세: 출근시간/전반사업/후반사업/근무시간 + 메모 + 되돌리기
- [x] 교번표: 지선/본선주간(평·휴 칩)/본선야간(4종 칩) + 비교표 시트

## 남은 작업 (다음 단계)

- [ ] LoginScreen (Credential Manager 구글 로그인) — 현재는 로그인 없이 실행 불가하므로 **최우선**
- [ ] MatesScreen (동료 검색 + 즐겨찾기 3그룹: 동호회/우리 조/기타, 이름 변경 가능)
- [ ] SettingsScreen (근무선택 진입, 구글캘린더 토글, 공개 설정, 시각표 개정 표시)
- [ ] 위젯 실기기 확인 / Google Calendar 양방향 동기화 마무리
- [ ] Play 배포: keystore 생성 → AAB 빌드 → 비공개 테스트 트랙
- [ ] 미확인: 근무시간 계산식(지14 평일 10시 7분) — 확인되면 자동계산으로 전환

> ⚠️ 첫 빌드 전 필수: Firebase 콘솔에서 `google-services.json`을 받아 `app/`에 넣어야 빌드가 됩니다.
> 프로젝트에 gradle wrapper가 없으므로 Android Studio로 열어 sync 하는 것이 가장 간단합니다.
