package com.sinjeong.crewcalendar.domain.model

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * **.hwpx 읽기 — 새 라이브러리 없이 자바 표준만 쓴다** (v1.6.81 ④).
 *
 * hwpx는 그냥 **zip 안의 XML**이다(`mimetype` = `application/hwp+zip`).
 * 본문은 `Contents/section0.xml`, `…section1.xml` … 에 들어 있고 표는 이렇게 생겼다:
 *
 * ```
 * hs:sec > hp:p > hp:run > hp:tbl > hp:tr > hp:tc
 *   hp:tc > hp:subList > hp:p > hp:run > hp:t      ← 글자
 *   hp:tc > hp:cellAddr  @colAddr @rowAddr         ← 격자 주소
 *   hp:tc > hp:cellSpan  @colSpan @rowSpan         ← 병합
 * ```
 *
 * `java.util.zip` + `javax.xml.parsers`(SAX) 둘 다 안드로이드에 기본으로 있으므로
 * **APK 증가 0바이트**다. `kr.dogfoot:hwpxlib`(1.24MB)를 안 쓴 이유가 이것이다 —
 * 그 라이브러리는 `File`만 받아서 `content://`를 캐시로 복사하는 코드가 어차피 더 붙는다.
 * (**.hwp**(5.0 바이너리)는 얘기가 다르다 — 복합문서 컨테이너라 표준만으로는 못 읽는다.
 * 그쪽만 `kr.dogfoot:hwplib`을 쓴다. `MenuHwp` 주석 참고.)
 *
 * ## 함정 셋 (다 처리했다)
 *
 * 1. **`hp:subList`(글자)가 `hp:cellAddr`(주소)보다 먼저 나온다.** 그래서 글자를 먼저 모아 두고
 *    주소는 나중에 채운 뒤 `</hp:tc>`에서 한 칸으로 굳힌다.
 * 2. **`hp:t` 안에 자식 요소가 낀다**(`hp:markpenBegin`·`hp:tab` …) — 글자가 조각난다.
 *    그래서 깊이 세기(`tDepth`)로 "`hp:t` 안인지"만 보고 자식 요소에서 버퍼를 비우지 않는다.
 * 3. **표 안에 표**가 있을 수 있다 — 표·칸을 각각 스택으로 쌓아 안쪽 표가 바깥 칸의 글자를
 *    훔쳐가지 않게 한다.
 */
object MenuHwpx {

    fun read(input: InputStream): MenuDoc {
        val sections = sortedMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val n = e.name
                // `Contents/section0.xml` … 이름 순서가 곧 문서 순서다(10개를 넘길 일이 없다)
                if (n.startsWith("Contents/section", ignoreCase = true) &&
                    n.endsWith(".xml", ignoreCase = true)
                ) sections[n] = zin.readBytes()
                zin.closeEntry()
            }
        }
        val tables = mutableListOf<List<DocCell>>()
        val text = StringBuilder()
        val factory = SAXParserFactory.newInstance()
        for (bytes in sections.values) {
            val h = SectionHandler()
            runCatching { factory.newSAXParser().parse(ByteArrayInputStream(bytes), h) }
            tables += h.tables
            text.append(h.text)
        }
        return MenuDoc(tables, text.toString())
    }

    /** 이 바이트가 zip(= hwpx 후보)인가 — `PK` */
    fun looksLikeZip(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            head[2] == 0x03.toByte() && head[3] == 0x04.toByte()

    private class CellBuilder {
        var row = 0
        var col = 0
        var rowSpan = 1
        var colSpan = 1
        val sb = StringBuilder()
        fun build() = DocCell(row, col, rowSpan, colSpan, sb.toString().trim())
    }

    /** SAX 는 네임스페이스를 끈 채로 쓴다 — `hp:tbl` 같은 qName 에서 접두어만 떼면 된다 */
    private fun local(qName: String) = qName.substringAfterLast(':')

    private class SectionHandler : DefaultHandler() {
        val tables = mutableListOf<List<DocCell>>()
        val text = StringBuilder()
        private val tableStack = ArrayDeque<MutableList<DocCell>>()
        private val cellStack = ArrayDeque<CellBuilder>()
        private var tDepth = 0

        override fun startElement(uri: String?, l: String?, qName: String, a: Attributes) {
            when (local(qName)) {
                "tbl" -> tableStack.addLast(mutableListOf())
                "tc" -> cellStack.addLast(CellBuilder())
                "cellAddr" -> cellStack.lastOrNull()?.let { c ->
                    a.getValue("colAddr")?.toIntOrNull()?.let { c.col = it }
                    a.getValue("rowAddr")?.toIntOrNull()?.let { c.row = it }
                }
                "cellSpan" -> cellStack.lastOrNull()?.let { c ->
                    a.getValue("colSpan")?.toIntOrNull()?.let { c.colSpan = it }
                    a.getValue("rowSpan")?.toIntOrNull()?.let { c.rowSpan = it }
                }
                "t" -> tDepth++
            }
        }

        override fun endElement(uri: String?, l: String?, qName: String) {
            when (local(qName)) {
                "t" -> if (tDepth > 0) tDepth--
                // 문단 하나 = 한 줄. 메뉴가 줄마다 하나씩이라 이 줄바꿈이 곧 21칸 안의 줄이 된다.
                "p" -> {
                    cellStack.lastOrNull()?.sb?.append('\n')
                    text.append('\n')
                }
                "tc" -> cellStack.removeLastOrNull()?.let { b ->
                    tableStack.lastOrNull()?.add(b.build())
                }
                "tbl" -> tableStack.removeLastOrNull()
                    ?.takeIf { it.isNotEmpty() }?.let { tables += it }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (tDepth <= 0) return
            val s = String(ch, start, length)
            cellStack.lastOrNull()?.sb?.append(s)
            text.append(s)
        }
    }
}
