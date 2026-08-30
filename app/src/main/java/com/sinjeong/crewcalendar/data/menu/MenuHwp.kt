package com.sinjeong.crewcalendar.data.menu

import com.sinjeong.crewcalendar.domain.model.DocCell
import com.sinjeong.crewcalendar.domain.model.MenuDoc
// ⚠ `object` 는 코틀린 예약어라 패키지 경로에서 **역따옴표로 감싸야** 한다(hwplib 의 실제 패키지명이다)
import kr.dogfoot.hwplib.`object`.bodytext.control.ControlTable
import kr.dogfoot.hwplib.`object`.bodytext.paragraph.Paragraph
import kr.dogfoot.hwplib.`object`.bodytext.paragraph.text.HWPCharNormal
import kr.dogfoot.hwplib.`object`.bodytext.paragraph.text.HWPCharType
import kr.dogfoot.hwplib.reader.HWPReader
import java.io.InputStream

/**
 * **.hwp(한글 5.0 바이너리) 읽기** — 여기가 `kr.dogfoot:hwplib`을 만지는 **유일한 파일**이다 (v1.6.81 ④).
 *
 * ## 왜 라이브러리를 붙였나 (hwpx와 달리)
 *
 * .hwp는 XML이 아니라 **복합문서(CFBF/OLE2) 컨테이너 + 자체 레코드 스트림 + raw deflate**다.
 * 안드로이드 표준에는 CFBF 판독기가 아예 없어서 손으로 짜면 **500~650줄**(디렉터리·FAT·미니FAT
 * 순회 + 레코드 헤더 비트필드 + 표 레코드 조립)이고, 그 뒤로 실파일마다 예외가 붙는다.
 * `kr.dogfoot:hwplib`은 **Apache-2.0 · 0.96MB · 의존성 0개**(POI를 끌고 오지 않고 필요한 부분만
 * 자기 패키지로 품고 있다)라 그 줄 수를 한 줄로 바꾼다. **실측 APK 증가는 docs/project-notes.md**에.
 *
 * ⚠ **`HWPReader.fromBase64String`은 절대 부르지 말 것** — 그 메서드 하나만 안드로이드에 없는
 * `javax.xml.bind.DatatypeConverter`를 쓴다. 여기서 쓰는 [HWPReader.fromInputStream]은 안 쓴다
 * (자바는 메서드 단위로 클래스를 찾으므로 안 부르면 아무 일도 안 일어난다).
 *
 * ## 표 → 격자
 *
 * 칸마다 `ListHeaderForCell`이 **격자 주소(행·열)와 병합 크기**를 그대로 들고 있다. 좌표로 추정하는
 * 사진 인식과 달리 **읽으면 곧 정답**이다. 21칸으로 앉히는 것은 `MenuTable`이 맡는다.
 */
object MenuHwp {

    /** 이 바이트가 복합문서(= .hwp 후보)인가 — `D0 CF 11 E0 A1 B1 1A E1` */
    private val CFBF = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )

    fun looksLikeHwp(head: ByteArray): Boolean =
        head.size >= CFBF.size && CFBF.indices.all { head[it] == CFBF[it] }

    fun read(input: InputStream): MenuDoc {
        val file = HWPReader.fromInputStream(input)
        val tables = mutableListOf<List<DocCell>>()
        val text = StringBuilder()
        for (section in file.bodyText.sectionList) {
            for (p in section.paragraphs) collect(p, tables, text)
        }
        return MenuDoc(tables, text.toString())
    }

    private fun collect(p: Paragraph, tables: MutableList<List<DocCell>>, text: StringBuilder) {
        text.append(paraText(p)).append('\n')
        // 표가 없는 문단이면 controlList 자체가 null 일 수 있다
        p.controlList?.forEach { control ->
            if (control !is ControlTable) return@forEach
            val cells = mutableListOf<DocCell>()
            for (row in control.rowList) for (cell in row.cellList) {
                val h = cell.listHeader
                val t = cell.paragraphList.joinToString("\n") { paraText(it) }.trim()
                cells += DocCell(h.rowIndex, h.colIndex, h.rowSpan, h.colSpan, t)
                // 기간 문구(`※ 기간 : …`)가 표 안에 들어 있는 문서가 있어 전체 글자에도 넣는다
                text.append(t).append('\n')
            }
            if (cells.isNotEmpty()) tables += cells
        }
    }

    /**
     * 문단 하나의 글자. **`Paragraph.getNormalString()`을 쓰지 않는다** — 그건 일반 글자만 모으고
     * **제어문자를 통째로 버려서**, 한 문단 안에서 `Shift+Enter`(강제 줄 나눔, 코드 10)로 줄을
     * 나눈 칸이 `흑미밥북어계란국비엔나볶음`처럼 **한 덩어리로 붙어 나온다.**
     * 식단표 칸은 메뉴를 줄로 나눠 적는 자리라 그 줄바꿈이 곧 21칸 안의 줄이다 — 살려야 한다.
     */
    private fun paraText(p: Paragraph): String {
        val t = p.text ?: return ""
        val sb = StringBuilder()
        for (ch in t.charList) {
            when {
                ch.type == HWPCharType.Normal ->
                    runCatching { sb.append((ch as HWPCharNormal).ch) }
                ch.isLineBreak -> sb.append('\n')
            }
        }
        return sb.toString()
    }
}
