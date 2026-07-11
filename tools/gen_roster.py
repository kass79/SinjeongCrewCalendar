"""직원_26년7월.xlsx → BundledRoster.kt
각 직원의 7월 근무 행에서 순환 offset을 역산하고, 전체 행과 대조 검증."""
import sys
import openpyxl
sys.stdout.reconfigure(encoding='utf-8')

BRANCH = ["지3","지대2","지12","지12비","지휴5","지2","지8","지휴7","지5","지14","지14비","지휴2","지7","지11","지11비","지휴3","지6","지대11","지대11비","지휴6","지1","지10","지10비","지휴1","지대1","지4","지13","지13비","지휴4"]
MAIN = ["9","38","38비","휴21","12","28","휴19","대3","35","35비","휴4","17","49","49비","휴11","7","40","40비","휴13","2","대11","대11비","휴12","13","27","휴20","대5","34","34비","휴1","3","42","42비","휴9","5","50","50비","휴18","15","25","휴28","22","44","44비","휴3","16","43","43비","휴17","대1","33","33비","휴10","10","39","39비","휴22","6","26","휴25","대13","대13비","휴26","1","20","51","51비","휴5","대6","47","47비","휴16","18","23","휴29","4","36","36비","휴15","11","46","46비","휴2","14","37","37비","휴27","대2","29","휴24","8","휴14","21","대12","대12비","휴6","19","41","41비","휴8","24","48","48비","휴7","대4","45","45비","휴23"]

def norm(v):
    if v is None: return ""
    s = str(v).strip()
    if s.endswith(".0"): s = s[:-2]
    return s

def solve(cells, pattern):
    """cells[d-1] = 그 날 근무. 패턴에 존재하는 첫 셀로 offset 역산 → 전체 검증."""
    idx = {c: i for i, c in enumerate(pattern)}
    L = len(pattern)
    for d, c in enumerate(cells):
        if c in idx:
            off = (idx[c] - d) % L
            total = ok = 0
            for d2, c2 in enumerate(cells):
                if not c2: continue
                total += 1
                if pattern[(d2 + off) % L] == c2: ok += 1
            if total >= 5 and ok / total >= 0.85:
                return off, ok, total
            # 첫 매칭이 어긋나면 다음 셀로 재시도
    return None

wb = openpyxl.load_workbook(r"C:\Users\admin\Documents\카카오톡 받은 파일\직원_26년7월_2026_07_03_23_19_14.xlsx", data_only=True)
out = {"MAIN_DRIVER": [], "MAIN_CONDUCTOR": [], "BRANCH": []}
skipped = []

for sheet, group in [("기관사", "MAIN_DRIVER"), ("차장", "MAIN_CONDUCTOR"), ("지선", "BRANCH")]:
    ws = wb[sheet]
    for row in ws.iter_rows(min_row=3):  # 1행=날짜, 2행=요일
        name = norm(row[0].value)
        if not name: continue
        cells = [norm(c.value) for c in row[1:32]]
        if not any(cells):
            skipped.append((sheet, name, "근무 없음")); continue
        is_branch_row = any(c.startswith("지") for c in cells if c)
        if sheet == "기관사" and is_branch_row:
            continue  # 지선 시트에 중복 존재
        pattern = BRANCH if group == "BRANCH" else MAIN
        r = solve(cells, pattern)
        if r is None:
            skipped.append((sheet, name, "패턴 불일치")); continue
        off, ok, total = r
        out[group].append((name, off))

for g, people in out.items():
    people.sort(key=lambda x: x[0])
    print(g, len(people), "명")
print("건너뜀:", len(skipped), skipped[:8])

# Kotlin 생성
lines = [
    "package com.sinjeong.crewcalendar.domain.model",
    "",
    "/**",
    " * 사업소 전체 근무자 기본 데이터 (직원_26년7월.xlsx에서 순환 offset 역산).",
    " * 동료근무 매트릭스의 초기값 — 본인이 로그인해 근무선택하면 자기 행이 실시간 값으로 대체되고,",
    " * 서버 연동 후에는 로그인 사용자 데이터가 이 목록을 자동 갱신한다.",
    " */",
    "object BundledRoster {",
]
for g, people in out.items():
    lines.append(f"    val {g}: List<Pair<String, Int>> = listOf(")
    row = []
    for i, (name, off) in enumerate(people):
        row.append(f'"{name}" to {off}')
        if len(row) == 4 or i == len(people) - 1:
            lines.append("        " + ", ".join(row) + ",")
            row = []
    lines.append("    )")
    lines.append("")
lines.append("    fun forGroup(group: CrewGroup): List<Pair<String, Int>> = when (group) {")
lines.append("        CrewGroup.MAIN_DRIVER -> MAIN_DRIVER")
lines.append("        CrewGroup.MAIN_CONDUCTOR -> MAIN_CONDUCTOR")
lines.append("        CrewGroup.BRANCH -> BRANCH")
lines.append("    }")
lines.append("}")

dst = r"C:\Users\admin\Downloads\SinjeongCrewCalendar\app\src\main\java\com\sinjeong\crewcalendar\domain\model\BundledRoster.kt"
open(dst, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("written:", dst)
