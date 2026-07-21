"""staff_empno.py → BundledStaff.kt (이름+사번 명단, 로그인 검증용)."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
sys.stdout.reconfigure(encoding='utf-8')
from staff_empno import DRIVERS, CONDUCTORS, OFFICE

def emit(name, rows):
    lines = ["    val " + name + ": List<Staff> = listOf("]
    row = []
    for nm, rank, no in rows:
        row.append('S("' + nm + '","' + no + '")')
        if len(row) == 3:
            lines.append("        " + ", ".join(row) + ",")
            row = []
    if row:
        lines.append("        " + ", ".join(row) + ",")
    lines.append("    )")
    return "\n".join(lines)

HEAD = '''package com.sinjeong.crewcalendar.domain.model

/**
 * 사업소 직원 명단 (직원 직급·사번표 2026-02-09 기준).
 * 로그인 시 이름+사번이 이 명단과 일치해야 통과 -> 사칭 방지.
 * 동명이인은 사번으로 구분(표시명 뒤 b는 원본 문서 중복 표기).
 */
private typealias S = Pair<String, String>   // (이름, 사번)

object BundledStaff {

'''

TAIL = '''

    data class Staff(val name: String, val empNo: String, val isConductor: Boolean)

    private val ALL: List<Staff> by lazy {
        DRIVERS.map { Staff(it.first, it.second, false) } +
            CONDUCTORS.map { Staff(it.first, it.second, true) } +
            OFFICE.map { Staff(it.first, it.second, false) }
    }

    /** 이름+사번이 명단에 있으면 Staff 반환, 없으면 null (앞뒤 공백 무시, 표시용 b 접미 무시) */
    fun validate(name: String, empNo: String): Staff? {
        val n = name.trim()
        val e = empNo.trim()
        return ALL.firstOrNull { it.name.trimEnd('b') == n && it.empNo == e }
    }
}
'''

out = HEAD + emit("DRIVERS", DRIVERS) + "\n\n" + emit("CONDUCTORS", CONDUCTORS) + "\n\n" + emit("OFFICE", OFFICE) + TAIL
dst = r"C:\Users\admin\Downloads\SinjeongCrewCalendar\app\src\main\java\com\sinjeong\crewcalendar\domain\model\BundledStaff.kt"
open(dst, "w", encoding="utf-8").write(out)
print("written", dst, "| drivers", len(DRIVERS), "cond", len(CONDUCTORS), "office", len(OFFICE))
