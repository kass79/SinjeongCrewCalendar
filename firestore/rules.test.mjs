// 신정승무 캘린더 — 루트 firestore.rules 단위 테스트 (허용 11 / 차단 23 = 34건)
//
// 규칙을 고치면 반드시 이걸 돌려라. 통과 = "앱이 안 멈춘다"의 근거다.
// 앱이 실제로 보내는 페이로드는 data/remote/FirestoreRepositories.kt 에서 그대로 베꼈다.
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
const publishDoc = (name) => ({          // FirestoreUserRepository.publish
  name, role: 'CONDUCTOR', patternId: 'bundled-main',
  patternOffset: 0, updatedAt: serverTimestamp(),
});
const adminDoc = (name) => ({            // FirestoreRosterRepository.adminUpsert
  name, role: 'DRIVER_BRANCH', patternId: 'bundled-branch',
  patternOffset: 7, addedBy: 'admin', updatedAt: serverTimestamp(),
});
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

await t('rosterOverrides: dutyRaw 에 초장문 삽입', () =>
  assertFails(setDoc(doc(anonA, 'rosterOverrides/1001_2026-08-15'),
    { uid: '1001', date: '2026-08-15', dutyRaw: 'x'.repeat(5000), originalDutyRaw: '' })));

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
