package com.sinjeong.crewcalendar.data.menu

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.sinjeong.crewcalendar.domain.model.MenuTable
import com.sinjeong.crewcalendar.domain.model.WeeklyMenu
import org.json.JSONObject
import java.time.LocalDate

/**
 * **사진 → 클라우드 AI 인식** (v1.6.82 ②-4).
 *
 * 사용자: *"사진으로 찍을수도 있지.. 난 더 좋은 품질을 원해."*
 *
 * ## 왜 기기 안 글자인식이 아니라 클라우드인가
 *
 * 한글파일·PDF 는 글자가 파일 안에 그대로 있어 100% 다. 남은 건 **벽에 붙은 표를 찍은 사진** 인데,
 * 코팅된 종이라 빛이 반사되고 비스듬히 찍히고 그늘이 진다. 기기 안 글자인식(ML Kit)은 낱말을
 * 읽어 줄 뿐 **표를 이해하지 못해서**, 반사로 머리글 한 줄이 날아가면 21칸이 통째로 어긋난다.
 * 큰 모델은 사진을 보고 *표로* 읽는다 — 기울어도 일부가 가려도 칸을 맞춘다.
 *
 * ## 돈이 드는가 — **안 든다**
 *
 * [GenerativeBackend.googleAI] = Gemini Developer API 백엔드는 **Spark(무료) 요금제 그대로** 쓴다
 * (Vertex 백엔드만 Blaze 필수). 관리자 한 사람이 주 1회 사진 한 장을 넣는 쓰임이라 무료 한도
 * (Firebase AI Logic 100 요청/분)에 닿을 일이 없다. **API 키는 앱에 들어가지 않는다** —
 * Firebase 가 프로젝트의 서비스 계정으로 인가한다.
 *
 * ## 켜지지 않은 상태에서도 앱은 멀쩡해야 한다
 *
 * Firebase 콘솔에서 **AI Logic 을 한 번 켜 줘야** 이 경로가 산다(사용자 몫 — 콘솔 클릭 3번).
 * 안 켜져 있으면 여기서 예외가 나고 화면은 **기기 안 글자인식으로 되돌아간다.** 그래서
 * PDF·한글파일·붙여넣기는 이 파일과 **아무 상관이 없다** — 인터넷이 끊겨도 100% 그대로 동작한다.
 *
 * ⚠ **2026-11-02 부터 App Check 가 필수**다. 그 전에 켜지 않으면 이 경로만 멈춘다(다른 경로는 무사).
 *   켜는 절차는 docs/project-notes.md v1.6.82 절에 적어 뒀다.
 */
object MenuAi {

    /** 사진 한 장이면 되는 일에 큰 모델을 부르지 않는다. flash 가 표 읽기에 충분하고 빠르다 */
    private const val MODEL = "gemini-2.5-flash"

    /** 올려 보내기 전 긴 변을 이만큼으로 줄인다 — 글자는 그대로 읽히고 왕복이 몇 배 빨라진다 */
    private const val MAX_EDGE = 1600

    private val PROMPT = """
        이 사진은 한국 회사 구내식당의 **주간 식단표**다. 7일(월~일) × 3끼니(조식·중식·석식) = 21칸 표다.
        표를 읽어 JSON 으로만 답하라.

        규칙:
        - days 는 **월요일부터 일요일까지 정확히 7개**다. 표에 요일 머리글이 있으면 그것을 따르라.
        - 각 끼니 칸의 메뉴는 **줄바꿈(\n) 으로 구분**해 한 문자열에 담아라. 표에 적힌 줄을 그대로 지켜라.
        - 사진에 없는 칸은 빈 문자열로 두어라. **없는 메뉴를 지어내지 마라.**
        - 글자가 흐리면 가장 그럴듯한 한국어 메뉴 이름으로 읽되, 아예 못 읽으면 비워라.
        - weekStart 는 표의 기간 문구(예: "※ 기간 : '26. 8. 31 ~ '26. 9. 06")에서 읽은 **첫날**을
          yyyy-MM-dd 로. 못 찾으면 빈 문자열.
    """.trimIndent()

    private val SCHEMA = Schema.obj(
        mapOf(
            "weekStart" to Schema.string(description = "표 기간의 첫날 yyyy-MM-dd, 없으면 빈 문자열"),
            "days" to Schema.array(
                Schema.obj(
                    mapOf(
                        "breakfast" to Schema.string(description = "조식 메뉴, 줄바꿈으로 구분"),
                        "lunch" to Schema.string(description = "중식 메뉴, 줄바꿈으로 구분"),
                        "dinner" to Schema.string(description = "석식 메뉴, 줄바꿈으로 구분"),
                    ),
                ),
                description = "월요일부터 일요일까지 7개",
            ),
        ),
    )

    /** @return (21칸, 기간에서 읽은 주 시작일) */
    suspend fun read(photo: Bitmap): Pair<List<String>, LocalDate?> {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL,
            generationConfig = generationConfig {
                // 표를 그대로 옮겨 적는 일이라 창의력이 필요 없다. 0 이면 같은 사진에 같은 답이 나온다.
                temperature = 0f
                responseMimeType = "application/json"
                responseSchema = SCHEMA
            },
        )
        val answer = model.generateContent(
            content { image(shrink(photo)); text(PROMPT) },
        ).text ?: error("응답이 비었습니다")
        return parse(answer)
    }

    /** 모델 답(JSON) → 21칸. 형식이 어긋나면 채울 수 있는 만큼만 채운다 */
    internal fun parse(json: String): Pair<List<String>, LocalDate?> {
        val root = JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val cells = MutableList(WeeklyMenu.CELLS) { "" }
        val days = root.optJSONArray("days")
        val keys = listOf("breakfast", "lunch", "dinner")
        if (days != null) for (d in 0 until minOf(days.length(), WeeklyMenu.DAYS)) {
            val o = days.optJSONObject(d) ?: continue
            keys.forEachIndexed { m, k ->
                cells[d * WeeklyMenu.MEALS + m] = MenuTable.tidy(o.optString(k, ""))
            }
        }
        val week = root.optString("weekStart").takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return cells to week
    }

    /** 긴 변 [MAX_EDGE] 로 줄인다. 이미 작으면 그대로 */
    private fun shrink(src: Bitmap): Bitmap {
        val edge = maxOf(src.width, src.height)
        if (edge <= MAX_EDGE) return src
        val ratio = MAX_EDGE.toFloat() / edge
        return Bitmap.createScaledBitmap(
            src, (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1), true,
        )
    }
}
