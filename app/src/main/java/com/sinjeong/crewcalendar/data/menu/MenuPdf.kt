package com.sinjeong.crewcalendar.data.menu

import android.content.Context
import com.sinjeong.crewcalendar.domain.model.MenuOcr
import com.sinjeong.crewcalendar.domain.model.OcrWord
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream

/**
 * **PDF 글자층 직접 읽기** (v1.6.82 ②-1). `com.tom-roush:pdfbox-android` 를 만지는 유일한 파일이다.
 *
 * ## 무엇이 틀려 있었나
 *
 * v1.6.80~81 은 PDF 를 [android.graphics.pdf.PdfRenderer] 로 **그림으로 그린 뒤 글자인식(ML Kit)** 에
 * 넣었다. 그런데 사업소가 주는 PDF(한글 2018 → 한컴 PDF)에는 **완성된 글자층이 그대로 들어 있다** —
 * 21칸 메뉴가 한 글자도 안 틀리고 좌표까지 붙어 있는데, 그걸 버리고 사진처럼 다시 읽고 있었다.
 * 빛 반사도 기울기도 없는 그림이라 인식이 잘 될 것 같지만, 실제로는 인식기의 낱말 쪼개기·오독이
 * 그대로 얹히고 무엇보다 **한국어 모델이 안 내려받아진 기기에서는 통째로 실패**한다.
 *
 * 지금은 PDF 안의 글자와 그 사각형([TextPosition])을 그대로 꺼내 [MenuOcr] 의 같은 좌표 배치기에
 * 넣는다. 인식이 낄 자리가 없어 **정확도가 100%** 이고, 인터넷도 플레이 서비스도 필요 없다.
 *
 * ## 왜 글자를 다시 이어 붙이는가 ([MenuOcr.groupRuns])
 *
 * PDF 글자층은 **글리프 하나가 항목 하나**다 — 머리글 `8월 31일(월)` 이 `8` `월` `31` `일` `(` `월` `)`
 * 일곱 조각으로 온다. 그대로 두면 `8월`의 `월` 이 요일 기준점으로 잡히고, 옆 칸 `9월 1일(화)`의
 * `월` 도 같이 잡혀 **기준점이 뒤엉킨다.** 그래서 한 줄 안에서 붙어 있는 글자를 먼저 낱말로 잇는다.
 */
object MenuPdf {

    private val PDF_HEAD = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte())

    fun looksLikePdf(head: ByteArray): Boolean =
        head.size >= 4 && PDF_HEAD.indices.all { head[it] == PDF_HEAD[it] }

    /** @return (낱말 목록, 1쪽 전체 글자). 글자층이 없는 PDF(스캔본)면 낱말이 비어 온다. */
    fun read(ctx: Context, input: InputStream): Pair<List<OcrWord>, String> {
        // 자원(글리프 목록·표준 폰트 메트릭)은 asset 에 있고 실제 읽기는 게을러서, 여기서 불러도
        // 앱 시작이 느려지지 않는다. 관리자만 쓰는 경로라 앱 시작 때 미리 부르지 않는다.
        PDFBoxResourceLoader.init(ctx.applicationContext)
        val glyphs = mutableListOf<OcrWord>()
        PDDocument.load(input).use { doc ->
            require(doc.numberOfPages > 0) { "빈 PDF입니다" }
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, positions: List<TextPosition>) {
                    for (tp in positions) {
                        val t = tp.unicode ?: continue
                        if (t.isBlank()) continue
                        // `*DirAdj` = 쪽 회전까지 보정된 좌표. y 는 **위에서 아래로** 커진다 —
                        // 사진 좌표계와 같아서 MenuOcr 이 그대로 받는다.
                        glyphs += OcrWord(
                            text = t,
                            left = tp.xDirAdj,
                            top = tp.yDirAdj - tp.heightDir,
                            right = tp.xDirAdj + tp.widthDirAdj,
                            bottom = tp.yDirAdj,
                        )
                    }
                    super.writeString(text, positions)
                }
            }
            stripper.sortByPosition = true
            stripper.startPage = 1
            stripper.endPage = 1        // 식단표는 늘 1쪽이다
            stripper.getText(doc)       // 돌려주는 글자는 안 쓴다 — 아래 주석 참고
            val runs = MenuOcr.groupRuns(dedupe(glyphs))
            // 기간 문구는 **이어 붙인 낱말**에서 읽는다. PDFBox 가 돌려주는 글자는 글리프 사이마다
            // 공백을 끼워 `’2  6 .  8 .   3 1` 이 되어 날짜로 안 읽힌다(실파일 실측).
            return runs to runs.joinToString("\n") { it.text }
        }
    }

    /**
     * 같은 글자를 **거의 같은 자리에 여러 번** 찍은 것을 하나로 (v1.6.82).
     *
     * 한글은 굵은 글씨를 진짜 굵은 폰트가 아니라 **같은 글자를 0.5pt 씩 밀어 세 번 찍어** 흉내낸다
     * (실파일 제목 `주 간 식 단 표` 가 3벌로 나온다). 표 안에 굵은 칸이 있으면 메뉴가
     * `잡곡밥잡곡밥잡곡밥` 이 되므로 여기서 걷어낸다.
     */
    private fun dedupe(glyphs: List<OcrWord>): List<OcrWord> {
        val out = mutableListOf<OcrWord>()
        for (g in glyphs) {
            val dup = out.any {
                it.text == g.text && kotlin.math.abs(it.left - g.left) < 2.5f &&
                    kotlin.math.abs(it.top - g.top) < 2.5f
            }
            if (!dup) out += g
        }
        return out
    }
}
