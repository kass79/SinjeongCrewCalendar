// 신정승무 캘린더 — 루트 firestore.rules 단위 테스트 (허용 18 / 차단 35 = 53건)
//
// 규칙을 고치면 반드시 이걸 돌려라. 통과 = "앱이 안 멈춘다"의 근거다.
// 앱이 실제로 보내는 페이로드는 data/remote/FirestoreRepositories.kt 에서 그대로 베꼈다.
//
// ⚠ **페이로드가 바뀌면 여기부터 고쳐라.** v1.6.63 이 publish() 에 patternSegments 를 넣었는데
//    이 파일의 publishDoc() 이 v1.6.25 페이로드 그대로라 **41개 버전 동안 실서비스가 조용히
//    깨진 걸 못 잡았다**(hasOnly 위반 → 모든 publish 가 PERMISSION_DENIED).
//    2026-08-25 규칙만 고쳐 복구했다(앱 버전 무변경).
//
// 실행 (임시 폴더에서, 운영 Firestore 는 건드리지 않음 · JDK 21 필요):
//   mkdir /tmp/rt && cd /tmp/rt
//   cp <repo>/firestore.rules . && cp <repo>/firestore/rules.test.mjs test.mjs
//   echo '{"type":"module"}' > package.json
//   echo '{"firestore":{"rules":"firestore.rules"},"emulators":{"firestore":{"port":8181},"ui":{"enabled":false}}}' > firebase.json
//   npm i @firebase/rules-unit-testing firebase
//   JAVA_HOME="<JDK21>" npx firebase-tools emulators:exec --only firestore --project demo-rulestest "node test.mjs"
//
// ponytail: package.json/firebase.json 을 저장소에 안 넣었다 — 안드로이드 저장소에 node 프로젝트를
//           심느니 위 4줄을 그때그때 만드는 게 싸다. 상시 CI 가 생기면 그때 넣어라.
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertSucceeds, assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc, setDoc, deleteDoc, getDoc, getDocs, collection, query,
  where, serverTimestamp, updateDoc,
} from 'firebase/firestore';

const env = await initializeTestEnvironment({
  projectId: 'demo-rulestest',
  firestore: { host: '127.0.0.1', port: 8181, rules: readFileSync('firestore.rules', 'utf8') },
});

// 익명 로그인 2명 = 서로 다른 랜덤 uid (앱과 동일한 상황)
const anonA = env.authenticatedContext('anon-uid-aaaa').firestore();
const anonB = env.authenticatedContext('anon-uid-bbbb').firestore();
const nobody = env.unauthenticatedContext().firestore();

// 앱이 실제로 보내는 페이로드 (FirestoreRepositories.kt 그대로)
//
// publish 는 patternSegments 를 **항상** 보낸다(구간이 없으면 빈 배열). adminUpsert 는 **안 보낸다**.
// 그래서 규칙에서 patternSegments 는 선택 필드다 — hasAll 에 넣으면 관리자 대리등록이 죽는다.
const publishDoc = (name, segments = []) => ({   // FirestoreUserRepository.publish (6키)
  name, role: 'CONDUCTOR', patternId: 'bundled-main',
  patternOffset: 0, patternSegments: segments, updatedAt: serverTimestamp(),
});
const adminDoc = (name) => ({            // FirestoreRosterRepository.adminUpsert (6키, 구간 없음)
  name, role: 'DRIVER_BRANCH', patternId: 'bundled-branch',
  patternOffset: 7, addedBy: 'admin', updatedAt: serverTimestamp(),
});

// PatternSegment (User.kt) — from 은 LocalDate.toString().
// **첫 구간은 언제나 LocalDate.MIN** 이고 그 toString() 이 "-999999999-01-01" 이다.
// yyyy-MM-dd 로만 검사하면 정상 쓰기가 통째로 막힌다 — 아래 MIN 테스트가 그 자물쇠다.
const MIN_DATE = '-999999999-01-01';
const segment = (from, patternId = 'bundled-branch', patternOffset = 7, role = 'DRIVER_BRANCH') =>
  ({ from, patternId, patternOffset, role });
const overrideDoc = (uid, date, duty = '14') => ({  // saveOverride
  uid, date, dutyRaw: duty, originalDutyRaw: '휴3',
});

const seed = (fn) => env.withSecurityRulesDisabled((c) => fn(c.firestore()));

let pass = 0, fail = 0;
async function t(label, fn) {
  try { await fn(); console.log(`  OK   ${label}`); pass++; }
  catch (e) { console.log(`  FAIL ${label}\n       ${String(e).split('\n')[0]}`); fail++; }
}

console.log('\n=== 허용돼야 하는 것 (앱 정상 동작) ===');

await t('users: 로그인 publish — 새 문서 생성', () =>
  assertSucceeds(setDoc(doc(anonA, 'users/1001'), publishDoc('강민성'))));

await t('users: 로그인 publish — 재로그인/근무선택 변경 시 같은 문서 덮어쓰기', async () => {
  await seed((db) => setDoc(doc(db, 'users/1002'), { ...publishDoc('김철수'), updatedAt: new Date() }));
  await assertSucceeds(setDoc(doc(anonA, 'users/1002'),
    { ...publishDoc('김철수'), patternOffset: 12 }));
});

await t('users: 관리자 대리등록 adminUpsert (addedBy=admin)', () =>
  assertSucceeds(setDoc(doc(anonA, 'users/1003'), adminDoc('박영희'))));

await t('users: 본인 소유 승격 — 관리자 행 위에 본인 publish (addedBy 사라짐)', async () => {
  await seed((db) => setDoc(doc(db, 'users/1004'), { ...adminDoc('이순신'), updatedAt: new Date() }));
  await assertSucceeds(setDoc(doc(anonB, 'users/1004'), publishDoc('이순신')));
});

await t('users: adminDelete — 관리자가 올린 행 삭제', async () => {
  await seed((db) => setDoc(doc(db, 'users/1005'), { ...adminDoc('홍길동'), updatedAt: new Date() }));
  await assertSucceeds(deleteDoc(doc(anonA, 'users/1005')));
});

// v1.6.63 교번 구간 — 이 3건이 막히면 **모든 publish 가 거부돼 신규 가입자가 동료 탭에 안 뜬다**.
// (v1.6.63~66 실서비스가 정확히 이 상태였다. 규칙이 41개 버전 뒤처져 있었다.)
await t('users: publish — 교번 구간 2칸 (지선 → 다음달 본선)', () =>
  assertSucceeds(setDoc(doc(anonA, 'users/1006'), publishDoc('구간사용자', [
    segment(MIN_DATE),
    segment('2026-09-01', 'bundled-main', 3, 'DRIVER_MAIN'),
  ]))));

// ⚠ **expression 상한 자물쇠.** Firestore 는 요청당 1000 expression 이 한계고, 넘으면 규칙이
//    거짓이 아니라 **오류로 거부**한다. seg() 검사를 늘리면 12칸에서 여기 먼저 걸린다 —
//    실제로 정규식·범위검사를 넣었더니 이 테스트가 깨졌다(그대로 배포했으면 조용히 또 파손).
//    create 보다 update 가 검사식이 많아 상한에 먼저 닿으므로 둘 다 건다.
await t('users: publish — 구간 12칸 (User.MAX_SEGMENTS 상한, create+update)', async () => {
  const twelve = [
    segment(MIN_DATE),
    ...Array.from({ length: 11 }, (_, i) => segment(`2026-${String(i + 1).padStart(2, '0')}-01`)),
  ];
  await assertSucceeds(setDoc(doc(anonA, 'users/1007'), publishDoc('상한사용자', twelve)));
  await assertSucceeds(setDoc(doc(anonA, 'users/1007'), publishDoc('상한사용자', twelve)));
});

// PatternSegment.patternId 는 String? — "다이아 없이 저장" 경로가 null 을 남긴다
await t('users: publish — 구간의 patternId 가 null', () =>
  assertSucceeds(setDoc(doc(anonA, 'users/1008'), publishDoc('다이아없음', [
    segment(MIN_DATE, null), segment('2026-09-01', null),
  ]))));

// adminUpsert 는 create 뿐 아니라 **기존 관리자 행 수정**으로도 온다(admin → admin).
// B 수정(addedBy 붙이기 금지)이 이 경로를 죽이지 않는다는 잠금.
await t('users: adminUpsert — 이미 있는 관리자 행 다시 등록(수정)', async () => {
  await seed((db) => setDoc(doc(db, 'users/1009'), { ...adminDoc('최관리'), updatedAt: new Date() }));
  await assertSucceeds(setDoc(doc(anonB, 'users/1009'), adminDoc('최관리')));
});

await t('users: observeUsers — 컬렉션 전체 구독(동료근무 화면)', () =>
  assertSucceeds(getDocs(collection(anonB, 'users'))));

await t('rosterOverrides: 근무변경 저장(create)', () =>
  assertSucceeds(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-11'),
    overrideDoc('1001', '2026-08-11'))));

await t('rosterOverrides: 같은 날 다시 변경(update)', () =>
  assertSucceeds(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-11'),
    overrideDoc('1001', '2026-08-11', '지13'))));

await t('rosterOverrides: 야간 다음날 자동 비번 "~" 저장', () =>
  assertSucceeds(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-12'),
    { uid: '1001', date: '2026-08-12', dutyRaw: '~', originalDutyRaw: '' })));

await t('rosterOverrides: 패턴 복귀 = 문서 삭제', () =>
  assertSucceeds(deleteDoc(doc(anonA, 'rosterOverrides/1001_2026-08-12'))));

// v1.6.25 — 충당 계열이 다이아를 함께 담는다("충당 9"). 한글 4자 + 다이아가 16바이트를 넘어
// 상한을 32로 올렸다. 이 3건이 막히면 근무변경이 폰에만 남고 동료화면에 안 뜬다.
await t('rosterOverrides: 충당 + 본선 다이아 ("충당 9")', () =>
  assertSucceeds(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-18'),
    overrideDoc('1001', '2026-08-18', '충당 9'))));

await t('rosterOverrides: 대기충당 + 지선 야간 다이아 (최장 조합)', () =>
  assertSucceeds(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-19'),
    { uid: '1001', date: '2026-08-19', dutyRaw: '대기충당 지대11비', originalDutyRaw: '대기충당 지대11비' })));

await t('rosterOverrides: 교체 + 야간 다이아 ("교체 45")', () =>
  assertSucceeds(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-20'),
    overrideDoc('1001', '2026-08-20', '교체 45'))));

await t('rosterOverrides: observeMonthOverrides 월 범위 쿼리', () =>
  assertSucceeds(getDocs(query(collection(anonB, 'rosterOverrides'),
    where('date', '>=', '2026-08-01'), where('date', '<=', '2026-08-31')))));

console.log('\n=== 차단돼야 하는 것 ===');

await t('users: 로그인 안 한 상태에서 명단 읽기', () =>
  assertFails(getDocs(collection(nobody, 'users'))));

await t('users: 로그인 안 한 상태에서 쓰기', () =>
  assertFails(setDoc(doc(nobody, 'users/9999'), publishDoc('침입자'))));

await t('users: 남의 문서 이름 바꿔치기(주인 교체)', async () => {
  await seed((db) => setDoc(doc(db, 'users/2001'), { ...publishDoc('원래주인'), updatedAt: new Date() }));
  await assertFails(setDoc(doc(anonB, 'users/2001'), publishDoc('강탈자')));
});

await t('users: 일반 사용자(본인 가입) 문서 삭제', async () => {
  await seed((db) => setDoc(doc(db, 'users/2002'), { ...publishDoc('실사용자'), updatedAt: new Date() }));
  await assertFails(deleteDoc(doc(anonB, 'users/2002')));
});

await t('users: 명단 통째로 밀기 시도 — 남의 문서 하나 삭제', async () => {
  await seed((db) => setDoc(doc(db, 'users/2003'), { ...publishDoc('동료'), updatedAt: new Date() }));
  await assertFails(deleteDoc(doc(anonA, 'users/2003')));
});

await t('users: isAdmin 같은 임의 필드 주입', () =>
  assertFails(setDoc(doc(anonA, 'users/2004'), { ...publishDoc('가짜관리자'), isAdmin: true })));

await t('users: addedBy 를 admin 아닌 값으로', () =>
  assertFails(setDoc(doc(anonA, 'users/2005'), { ...adminDoc('사칭'), addedBy: 'me' })));

await t('users: patternOffset 이 문자열(타입 오염)', () =>
  assertFails(setDoc(doc(anonA, 'users/2006'), { ...publishDoc('타입깨짐'), patternOffset: 'zero' })));

await t('users: patternOffset 음수', () =>
  assertFails(setDoc(doc(anonA, 'users/2007'), { ...publishDoc('음수'), patternOffset: -1 })));

await t('users: 필수 필드 누락(patternId 없음)', () =>
  assertFails(setDoc(doc(anonA, 'users/2008'),
    { name: '누락', role: 'CONDUCTOR', patternOffset: 0, updatedAt: serverTimestamp() })));

await t('users: 이름이 빈 문자열', () =>
  assertFails(setDoc(doc(anonA, 'users/2009'), publishDoc(''))));

await t('users: 이름 자리에 20자 초과 쓰레기', () =>
  assertFails(setDoc(doc(anonA, 'users/2010'), publishDoc('가'.repeat(500)))));

await t('users: updatedAt 이 타임스탬프가 아님', () =>
  assertFails(setDoc(doc(anonA, 'users/2011'), { ...publishDoc('시각깨짐'), updatedAt: 'now' })));

await t('users: 부분 update 로 이름만 슬쩍 변경(updateDoc)', async () => {
  await seed((db) => setDoc(doc(db, 'users/2012'), { ...publishDoc('원본'), updatedAt: new Date() }));
  await assertFails(updateDoc(doc(anonB, 'users/2012'), { name: '바뀜' }));
});

// ── 교번 구간(patternSegments) 스키마 ──────────────────────────────────
await t('users: 구간 13칸 (MAX_SEGMENTS 초과)', () =>
  assertFails(setDoc(doc(anonA, 'users/2013'), publishDoc('넘침',
    Array.from({ length: 13 }, (_, i) => segment(`2026-01-${String(i + 1).padStart(2, '0')}`))))));

await t('users: 구간 원소에 임의 키 주입', () =>
  assertFails(setDoc(doc(anonA, 'users/2014'), publishDoc('키주입', [
    { ...segment(MIN_DATE), memo: '개인메모 유출' },
  ]))));

await t('users: 구간 원소에 필수 키 누락(role 없음)', () =>
  assertFails(setDoc(doc(anonA, 'users/2015'), publishDoc('키누락', [
    { from: MIN_DATE, patternId: 'bundled-branch', patternOffset: 7 },
  ]))));

await t('users: patternSegments 가 리스트가 아님', () =>
  assertFails(setDoc(doc(anonA, 'users/2016'),
    { ...publishDoc('타입깨짐'), patternSegments: '구간아님' })));

await t('users: 구간의 patternOffset 이 문자열', () =>
  assertFails(setDoc(doc(anonA, 'users/2017'), publishDoc('오프셋깨짐', [
    { ...segment(MIN_DATE), patternOffset: 'zero' },
  ]))));

// from 은 길이만 본다 — LocalDate.MIN("-999999999-01-01")과 yyyy-MM-dd 를 한 정규식으로 받다가
// 12칸 publish 가 expression 상한에 걸렸다(아래 상한 테스트 참고). 쓰레기통 방지까지만 한다.
await t('users: 구간의 from 에 초장문 삽입', () =>
  assertFails(setDoc(doc(anonA, 'users/2018'), publishDoc('날짜깨짐', [
    segment('x'.repeat(200)),
  ]))));

// 마지막 칸까지 검사하는지 — 규칙 언어에 반복문이 없어 12칸을 손으로 폈다. 하나라도 빠뜨리면 뚫린다.
await t('users: 12칸 중 마지막 칸만 오염', () =>
  assertFails(setDoc(doc(anonA, 'users/2019'), publishDoc('꼬리오염', [
    ...Array.from({ length: 11 }, (_, i) => segment(`2026-${String(i + 1).padStart(2, '0')}-01`)),
    { ...segment('2026-12-01'), isAdmin: true },
  ]))));

// ── addedBy 주입 → 삭제 연쇄 (2026-08-25 규칙 수정에서 막음) ──────────────────────
// 옛 규칙의 구멍: update 가 name 동일성만 봤고, 그 name 은 열린 read 로 그냥 읽혔다.
// ① addedBy:'admin' 주입 → ② delete 가 열림 → ③ 같은 사번에 다른 이름 create = 주인 교체.
await t('users: 연쇄 ①남의 행에 addedBy:"admin" 주입 → ②삭제 → ③이름 바꿔치기', async () => {
  await seed((db) => setDoc(doc(db, 'users/2020'), { ...publishDoc('피해자'), updatedAt: new Date() }));
  // ① 여기서 끊긴다 — addedBy 는 뗄 수만 있고 붙일 수 없다
  await assertFails(setDoc(doc(anonB, 'users/2020'), adminDoc('피해자')));
  // ② 주입이 막혔으니 삭제 조건(addedBy=='admin')이 성립하지 않는다
  await assertFails(deleteDoc(doc(anonB, 'users/2020')));
  // ③ 문서가 살아 있으니 다른 이름을 심을 수도 없다(create 는 기존 문서에 못 쓰고, update 는 이름 불변)
  await assertFails(setDoc(doc(anonB, 'users/2020'), publishDoc('강탈자')));
});

await t('users: addedBy 만 슬쩍 붙이는 부분 update', async () => {
  await seed((db) => setDoc(doc(db, 'users/2021'), { ...publishDoc('부분주입'), updatedAt: new Date() }));
  await assertFails(updateDoc(doc(anonB, 'users/2021'), { addedBy: 'admin' }));
});

// 의도된 차단(2026-08-25 규칙 수정) — 본인 가입 행을 관리자 행으로 바꾸는 것이 곧 위 연쇄의 ①이다.
// 관리자 화면은 어차피 addedBy != null 인 행만 목록에 올린다(AdminViewModel.members).
await t('users: 이미 본인 가입한 행 위에 adminUpsert', async () => {
  await seed((db) => setDoc(doc(db, 'users/2022'), { ...publishDoc('본인가입'), updatedAt: new Date() }));
  await assertFails(setDoc(doc(anonB, 'users/2022'), adminDoc('본인가입')));
});

await t('rosterOverrides: 문서ID 와 uid/date 불일치(유령 문서)', () =>
  assertFails(setDoc(doc(anonA, 'rosterOverrides/9999_2026-08-11'),
    overrideDoc('1001', '2026-08-11'))));

await t('rosterOverrides: date 형식이 날짜가 아님', () =>
  assertFails(setDoc(doc(anonA, 'rosterOverrides/1001_아무거나'),
    overrideDoc('1001', '아무거나'))));

await t('rosterOverrides: 임의 필드 추가', () =>
  assertFails(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-13'),
    { ...overrideDoc('1001', '2026-08-13'), memo: '개인메모 유출' })));

await t('rosterOverrides: dutyRaw 가 문자열이 아님', () =>
  assertFails(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-14'),
    { uid: '1001', date: '2026-08-14', dutyRaw: 14, originalDutyRaw: '' })));

await t('rosterOverrides: dutyRaw 33자 (상한 32 초과)', () =>
  assertFails(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-21'),
    { uid: '1001', date: '2026-08-21', dutyRaw: 'x'.repeat(33), originalDutyRaw: '' })));

await t('rosterOverrides: dutyRaw 에 초장문 삽입', () =>
  assertFails(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-15'),
    { uid: '1001', date: '2026-08-15', dutyRaw: 'x'.repeat(5000), originalDutyRaw: '' })));

// C — 소유권은 익명 인증으로 확인 불가. 규칙이 걸 수 있는 유일한 조건이 문서ID↔내용 정합성이라
// delete 에도 create/update 와 같은 검사를 건다. **남의 근무변경 삭제 자체는 여전히 막지 못한다**
// (되돌리기 = 삭제라 delete 를 닫을 수 없다). 남은 한계는 firestore.rules 주석 참고.
await t('rosterOverrides: 내용이 문서ID 와 안 맞는 문서 삭제(콘솔 주입분)', async () => {
  await seed((db) => setDoc(doc(db, 'rosterOverrides/1001_2026-08-22'),
    { uid: '9999', date: '2026-08-22', dutyRaw: '14', originalDutyRaw: '' }));
  await assertFails(deleteDoc(doc(anonA, 'rosterOverrides/1001_2026-08-22')));
});

await t('rosterOverrides: 로그인 안 한 상태에서 읽기', () =>
  assertFails(getDocs(collection(nobody, 'rosterOverrides'))));

await t('그 외 컬렉션(schedules) 읽기 차단', () =>
  assertFails(getDoc(doc(anonA, 'schedules/x'))));

await t('그 외 컬렉션(schedules) 쓰기 차단', () =>
  assertFails(setDoc(doc(anonA, 'schedules/x'), { a: 1 })));

await t('그 외 컬렉션(patterns) 쓰기 차단', () =>
  assertFails(setDoc(doc(anonA, 'patterns/x'), { a: 1 })));

await env.cleanup();
console.log(`\n합계: ${pass} OK / ${fail} FAIL\n`);
process.exit(fail ? 1 : 0);
