package com.sinjeong.crewcalendar.presentation.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.location.LocationManagerCompat
import com.sinjeong.crewcalendar.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/*
 * 현재 날씨 (v1.6.36 / v1.6.37 부터 현재 위치 기준).
 * v1.6.59 부터 달력 빈 칸 카드가 아니라 **헤더 칩**([WeatherChip])으로 그린다 — 조회 로직은 그대로다.
 *
 * v1.6.37: 격자를 상수로 박지 않고 **기기의 대략적 위치(COARSE)를 격자로 변환**해 조회한다.
 * 권한이 없거나 위치를 못 얻으면 신정차량기지 격자로 조용히 폴백한다 — 날씨는 늘 보인다.
 *
 * 기상청 공공데이터 단기예보 조회서비스 `VilageFcstInfoService_2.0` 의
 * **초단기예보(getUltraSrtFcst)** 한 번으로 기온·하늘상태·강수형태를 모두 받는다.
 * (초단기실황 getUltraSrtNcst 는 SKY 를 안 주고, 단기예보 getVilageFcst 는 T1H 대신 TMP 라
 *  같은 값을 얻으려면 두 번 불러야 한다 → 한 번으로 끝나는 초단기예보를 쓴다.)
 *
 * ⚠ 이 앱은 **지하 터널에서 쓴다.** 실패는 정상 상태다 —
 *   오프라인·타임아웃·파싱 실패는 전부 삼키고 아무것도 그리지 않는다. 스피너도 없다.
 */

/** 무료 공공데이터 키 — 사용자 확인 후 코드 포함(공개 저장소 인지). URL 인코딩된 형태 그대로다. */
private const val SERVICE_KEY =
    "XMXiJ0ib%2BxhyschjrOEKEH%2BLiBVAOtF9twd2uxLnqsmJpcc4CHQINyj%2Fs9P3HVn9IjVM3q3ROhpLUEga28xQSw%3D%3D"

/**
 * 위치를 못 쓸 때의 폴백 격자 — 신정차량기지(서울 양천구 신정동, 대략 N37.5145 / E126.8455).
 *
 * 권한 거부·위치서비스 꺼짐·좌표 획득 실패는 **전부 여기로 떨어진다.** 사업소가 고정이라
 * 최소한 출퇴근 동네 날씨는 늘 맞는다. v1.6.36 까지는 이 값이 유일한 격자였다.
 * 실호출로 확인된 값이다 — 58,126 이면 resultCode 00 에 T1H/SKY/PTY 가 정상으로 온다.
 */
private val SINJEONG = 58 to 126

/**
 * 기상청 동네예보 격자 변환(Lambert Conformal Conic, 기상청 공개 `dfs_xy_conv` 와 동일식).
 *
 * 상수는 기상청 고시값 — 지구반경 RE 6371.00877km / 격자간격 5km / 표준위도 30·60 /
 * 기준점 경위도 126·38 / 기준점 격자 43·136. 외부 라이브러리 없이 수식 그대로 옮겼다.
 *
 * 검산(WeatherTest 가 잠근다): 신정차량기지 → 58,126 · 서울시청 → 60,127 ·
 * 강남구청 → 61,126 · 노원구청 → 61,129 · 인천 중구 → 54,125 · 부산 → 98,76 · 제주 → 53,38.
 * 전부 기상청 공개 격자표와 일치한다.
 *
 * 격자가 5km라 **좌표가 조금 틀려도 같은 칸**이다(양천구 전역·영등포·강서가 모두 58,126).
 * 정밀 위치(FINE)가 필요 없는 이유이고, COARSE 만 받는 근거다.
 *
 * 국내 격자(1..149 × 1..253) 밖이면 **null** — 해외 좌표를 그대로 보내면
 * `resultCode 10 파라미터가 잘못되엇습니다` 가 와서 날씨가 통째로 안 뜬다.
 * (v1.6.37 에뮬 확인에서 기본 좌표인 미국 마운틴뷰가 1402,1265 로 나와 실제로 잡았다.)
 */
internal fun toGrid(lat: Double, lon: Double): Pair<Int, Int>? {
    val re = 6371.00877 / 5.0
    val slat1 = 30.0 * PI / 180.0
    val slat2 = 60.0 * PI / 180.0
    val sn = ln(cos(slat1) / cos(slat2)) /
        ln(tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5))
    val sf = tan(PI * 0.25 + slat1 * 0.5).pow(sn) * cos(slat1) / sn
    val ro = re * sf / tan(PI * 0.25 + (38.0 * PI / 180.0) * 0.5).pow(sn)
    val ra = re * sf / tan(PI * 0.25 + (lat * PI / 180.0) * 0.5).pow(sn)
    var theta = lon * PI / 180.0 - 126.0 * PI / 180.0
    if (theta > PI) theta -= 2.0 * PI
    if (theta < -PI) theta += 2.0 * PI
    theta *= sn
    val nx = floor(ra * sin(theta) + 43 + 0.5).toInt()
    val ny = floor(ro - ra * cos(theta) + 136 + 0.5).toInt()
    return if (nx in 1..149 && ny in 1..253) nx to ny else null
}

/** 대략적 위치 권한이 있나. 없으면 [SINJEONG] 폴백으로 조용히 간다. */
private fun hasCoarse(ctx: Context) =
    ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 기기 위치 한 번 읽기. **GPS 를 켜 둔 채 기다리지 않는다** — 배터리 때문이다.
 *
 * 1) 켜져 있는 제공자들의 **마지막 알려진 위치** 중 가장 최근 것. 배터리 소모 0.
 * 2) 그게 없거나 6시간보다 오래됐으면 **딱 한 번** 갱신 요청(2초 제한, 넘으면 취소).
 *    이동통신망(NETWORK)을 먼저 쓰고 없을 때만 GPS — COARSE 면 어차피 뭉갠 좌표가 온다.
 * 3) 그래도 없으면 null → 폴백.
 */
private fun locate(lm: LocationManager): Location? {
    val on = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
    val known = on.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (known != null && System.currentTimeMillis() - known.time < 6 * 3600_000L) return known

    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .firstOrNull { it in on } ?: return known
    return runCatching {
        val got = AtomicReference<Location?>()
        val done = CountDownLatch(1)
        val cancel = CancellationSignal()
        LocationManagerCompat.getCurrentLocation(lm, provider, cancel, { it.run() }) {
            got.set(it); done.countDown()
        }
        if (!done.await(2, TimeUnit.SECONDS)) cancel.cancel()
        got.get()
    }.getOrNull() ?: known
}

/**
 * 지금 그릴 격자. **좌표는 여기서 격자로 바꾸고 곧바로 버린다** —
 * 저장하지도, 서버로 보내지도 않는다(기상청에 나가는 것도 5km 격자 번호뿐이다).
 */
private fun gridOf(ctx: Context): Pair<Int, Int> = runCatching {
    if (!hasCoarse(ctx)) return SINJEONG
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return SINJEONG
    val loc = locate(lm) ?: return SINJEONG
    // 해외 좌표면 [toGrid] 가 null → 폴백(기상청은 국내 격자만 답한다).
    toGrid(loc.latitude, loc.longitude) ?: SINJEONG
}.getOrDefault(SINJEONG)

/**
 * 초단기예보 아이콘 5종. 소나기·진눈깨비는 비로 접어 넣는다(20dp에서 구분이 안 읽힌다).
 *
 * ⚠ 여기에 `R.drawable.…` 을 들고 있으면 안 된다. AGP 8 부터 R 필드가 상수가 아니라
 * 진짜 필드라 인라인되지 않고, 유닛테스트 클래스패스엔 `R$drawable` 이 없어
 * enum 초기화 자체가 `NoClassDefFoundError` 로 죽는다(실제로 겪음).
 * drawable 매핑은 그릴 때([WeatherChip])만 한다.
 */
internal enum class Wx { SUNNY, PARTLY, CLOUDY, RAIN, SNOW }

internal data class Weather(val wx: Wx, val tempC: Int)

private fun field(item: String, key: String) =
    Regex("\"$key\":\"([^\"]*)\"").find(item)?.groupValues?.get(1)

/**
 * 초단기예보 JSON → (아이콘, 기온). **네트워크와 분리된 순수 함수** — 테스트는 이쪽만 본다.
 *
 * 응답은 category(T1H·SKY·PTY·RN1·REH…) × fcstTime(1시간 간격 6개)이 평면 배열로 섞여 온다.
 * 가장 이른 fcstDate+fcstTime 을 "지금"으로 보고 그 시각의 세 값만 뽑는다.
 * 무엇 하나라도 없거나 모양이 다르면 null — 이상한 값을 그리느니 안 그리는 게 낫다.
 *
 * ⚠ `org.json.JSONObject` 를 **일부러 안 쓴다.** 그 클래스는 유닛테스트에서 android.jar 스텁이라
 * 부르는 순간 `RuntimeException: Stub!` 이고, 이 저장소의 JUnitCore 실행 클래스패스엔 아예 없다
 * (PatternTest KDoc 참고). 새 의존성 추가는 금지 → 순수 JVM 정규식으로 끊는다.
 * 예보 항목은 중첩이 없는 평평한 객체라 이 정도로 충분하고, 모양이 조금이라도 다르면
 * 매칭이 실패해 null 로 떨어진다 — 실패 쪽으로 안전하다.
 */
internal fun parseUltraSrtFcst(json: String): Weather? = runCatching { parseOrThrow(json) }
    .getOrNull()   // ⚠ 여기서 android.util.Log 를 부르면 안 된다 — 유닛테스트에선 스텁이라 즉사한다. 로그는 fetch() 가 남긴다.

/** 실패 원인을 예외로 그대로 내보내는 알맹이. [fetch] 의 실패 로그가 이걸 한 번 더 불러 사유를 적는다. */
private fun parseOrThrow(json: String): Weather {
    // ⚠ 닫는 중괄호도 **반드시 escape** 한다. JVM 정규식은 맨 `}` 를 그냥 리터럴로 받지만
    // 안드로이드(API 34+)는 ICU 엔진이라 `PatternSyntaxException` 을 던진다.
    // 유닛테스트(JVM)에선 통과하고 실기기에서만 죽는 자리 — v1.6.36 에뮬 확인에서 실제로 잡았다.
    val rows = Regex("\\{[^{}]*\\}").findAll(json).mapNotNull { m ->
        val o = m.value
        // category 가 없는 객체(header 등)는 여기서 걸러진다.
        val cat = field(o, "category") ?: return@mapNotNull null
        val at = (field(o, "fcstDate") ?: return@mapNotNull null) +
            (field(o, "fcstTime") ?: return@mapNotNull null)
        Triple(at, cat, field(o, "fcstValue") ?: return@mapNotNull null)
    }.toList()

    val slot = rows.minOf { it.first }   // 가장 이른 예보 시각 = "지금". 비면 예외 → null
    fun at(cat: String) = rows.firstOrNull { it.first == slot && it.second == cat }?.third

    val pty = at("PTY")?.toInt() ?: 0
    val wx = when {
        pty == 3 || pty == 7 -> Wx.SNOW              // 눈 / 눈날림
        pty != 0 -> Wx.RAIN                          // 비·비눈·소나기·빗방울
        // 비가 안 올 때만 하늘상태를 본다. SKY 가 없으면 예외 → 아무것도 안 그린다.
        else -> when (at("SKY")!!.toInt()) {
            1 -> Wx.SUNNY
            3 -> Wx.PARTLY
            else -> Wx.CLOUDY                        // 4=흐림
        }
    }
    return Weather(wx, at("T1H")!!.toDouble().roundToInt())
}

/**
 * 프로세스 메모리 캐시. 회전·리컴포지션·월 이동에 재호출되지 않게 막는 유일한 장치다
 * (Composable 쪽 `remember` 는 회전에서 날아가므로 여기서 잡아야 한다).
 * SharedPreferences 까지 갈 이유가 없다 — 45분 지난 기온은 어차피 다시 받아야 한다.
 */
private object WxCache {
    /** 이 값이 어느 격자의 날씨인지. 격자가 바뀌면(=동네가 바뀌면) 통째로 버린다. */
    @Volatile var grid: Pair<Int, Int>? = null
    @Volatile var value: Weather? = null
    @Volatile var freshUntilMs = 0L
}

/**
 * 캐시가 살아 있으면 그대로, 아니면 한 번 받아 온다. 반드시 IO 스레드에서 부를 것.
 * 실패해도 **직전 성공값을 버리지 않는다** — 터널에 들어갔다고 화면에서 날씨가 사라질 이유는 없다.
 *
 * 단 **격자가 달라지면 옛 값은 즉시 버린다**(v1.6.37) — 다른 동네 기온을 45분 동안
 * 들고 있으면 안 된다. 캐시 키를 격자로 잡았으니 "위치가 크게 바뀌면 갱신"이 저절로 된다.
 * 5km 안에서 움직이면 격자가 같아 종전처럼 45분 캐시가 그대로 먹는다.
 */
private fun currentWeather(ctx: Context): Weather? {
    val grid = gridOf(ctx)
    val now = System.currentTimeMillis()
    if (grid == WxCache.grid && now < WxCache.freshUntilMs) return WxCache.value
    if (grid != WxCache.grid) {
        WxCache.grid = grid
        WxCache.value = null
        Log.i("Weather", "격자 ${grid.first},${grid.second}")
    }
    val fetched = fetch(grid.first, grid.second)
    // 성공 45분(발표 주기 1시간보다 짧게) / 실패 5분 — 지하에서 매번 3초 타임아웃을 물지 않게.
    WxCache.freshUntilMs = now + if (fetched != null) 45 * 60_000L else 5 * 60_000L
    if (fetched != null) WxCache.value = fetched
    return WxCache.value
}

/**
 * 초단기예보는 매시 30분 발표 + 약 45분 뒤 제공 → `지금-45분` 의 시(hour)가 곧 쓸 수 있는 발표회차다.
 * 예) 10:40 이면 09:30 회차(10:30 회차는 10:45 부터), 10:50 이면 10:30 회차.
 * 시각은 KST 고정 — 기기 시간대가 어긋나도 엉뚱한 base_date 로 빈 응답을 받지 않게 한다.
 */
private fun fetch(nx: Int, ny: Int): Weather? = runCatching {
    val base = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(45)
    val url = URL(
        "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtFcst" +
            "?serviceKey=$SERVICE_KEY&dataType=JSON&numOfRows=60&pageNo=1" +
            "&base_date=${base.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)}" +
            "&base_time=%02d30".format(base.hour) +
            "&nx=$nx&ny=$ny"
    )
    (url.openConnection() as HttpURLConnection).run {
        connectTimeout = 3000
        readTimeout = 3000
        try {
            inputStream.bufferedReader().use { it.readText() }
        } finally {
            disconnect()
        }
    }
}.getOrElse { Log.w("Weather", "조회 실패: ${it.message}"); null }
    // 파싱이 실패하면 응답 앞머리를 같이 남긴다 — 조용히 사라지는 기능이라 로그가 없으면 원인을 못 찾는다.
    // (응답 본문엔 서비스키가 안 들어간다)
    ?.let { body ->
        parseUltraSrtFcst(body).also {
            // 실패하면 **사유와 응답 앞머리를 같이** 남긴다 — 조용히 사라지는 기능이라
            // 로그가 없으면 안 뜨는 이유를 영영 못 찾는다(응답 본문엔 서비스키가 안 들어간다).
            if (it == null) Log.w(
                "Weather",
                "응답 파싱 실패 (${body.length}B) " +
                    "${runCatching { parseOrThrow(body) }.exceptionOrNull()}: ${body.take(120)}",
            )
        }
    }

/**
 * 날씨 상태별 칩 배경/글자색. **`Wx` 5종을 그대로 쓴다** — 새 조건 체계를 만들지 않는다.
 *
 * 사용자 주문은 "태양·비·구름·번개 느낌으로 알아서 배경색"이었다. 초단기예보 PTY 에는
 * 낙뢰 항목이 따로 없어(1비·2비눈·3눈·4소나기·5빗방울·6빗방울눈날림·7눈날림) **번개는 별도 상태가
 * 아니다** — 비 계열을 짙은 남색으로 잡아 그 느낌을 대신한다.
 *
 * 라이트/다크 각각 명암비를 계산해 고른 값이다(전부 6:1 이상, WCAG AA 4.5:1 여유 있게 통과).
 * 다크모드에서 글자가 안 보인 사고(v1.6.40 오늘칸)를 반복하지 않으려고 배경·글자를 **짝으로** 정한다.
 */
private fun wxColors(wx: Wx, dark: Boolean): Pair<Color, Color> = when (wx) {
    //                                  라이트 (배경, 글자)                        다크 (배경, 글자)
    Wx.SUNNY -> if (dark) Color(0xFF4A3800) to Color(0xFFFFD54F) else Color(0xFFFFECB3) to Color(0xFF6D4C00)
    Wx.PARTLY -> if (dark) Color(0xFF2E3B4A) to Color(0xFFB9CFE6) else Color(0xFFDDE7F2) to Color(0xFF33506B)
    Wx.CLOUDY -> if (dark) Color(0xFF35393E) to Color(0xFFCBD1D8) else Color(0xFFE3E5E8) to Color(0xFF45494E)
    Wx.RAIN -> if (dark) Color(0xFF12395E) to Color(0xFFA8CFF5) else Color(0xFFCFE4FB) to Color(0xFF10457E)
    Wx.SNOW -> if (dark) Color(0xFF23414D) to Color(0xFFB5D8E8) else Color(0xFFE6F1F8) to Color(0xFF2A5566)
}

/**
 * 헤더 `휴N개` 옆에 붙는 현재 날씨 칩(v1.6.59). 달력 빈 칸에 있던 `WeatherCell` 을 대신한다 —
 * **조회·권한·격자 로직은 그대로**이고 표시 위치와 겉모습만 바뀌었다.
 *
 * 날씨가 없으면 **아무것도 그리지 않는다**(자리도 안 먹는다). 달력 칸일 땐 빈 칸이 7열 격자를
 * 밀기 때문에 "연결되면 표시" 안내를 넣었지만(v1.6.41 ③), 헤더에선 칩이 없어도 줄이 자연스럽게
 * 좁아질 뿐이라 안내할 대상이 없다.
 *
 * **위치 권한은 여기서, 날씨가 화면에 뜬 뒤에 딱 한 번 묻는다**(v1.6.37).
 * 앱을 켜자마자 묻지 않는 이유 — 팝업만 먼저 뜨면 무엇 때문에 위치를 달라는지 알 수가 없다.
 * 날씨(신정 폴백)가 이미 그려진 상태에서 물어야 "아 저것 때문이구나"가 된다.
 * 거부하면 `wx_loc_asked` 가 남아 **다시는 묻지 않는다** — 폴백으로 계속 잘 보이니 조를 이유가 없다.
 */
@Composable
fun WeatherChip(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    // 초기값을 캐시에서 읽는다 → 회전·월 이동 시 깜빡임 없이 바로 그려진다.
    var weather by remember { mutableStateOf(WxCache.value) }
    var granted by remember { mutableStateOf(hasCoarse(ctx)) }
    // 허용되면 granted 가 바뀌어 아래 LaunchedEffect 가 한 번 더 돈다 → 현재 위치 격자로 재조회.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    // ⚠ 마지막 안전망. 날씨 때문에 달력이 죽는 일은 없어야 한다 —
    // v1.6.36 에뮬 확인에서 정규식 하나(ICU 문법)로 실제 FATAL 이 났다.
    LaunchedEffect(granted) {
        weather = withContext(Dispatchers.IO) { runCatching { currentWeather(ctx) }.getOrNull() }
        // 날씨가 실제로 떴을 때만 묻는다. 오프라인이라 칩이 없으면 물을 맥락 자체가 없다.
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (weather != null && !granted && !prefs.getBoolean("wx_loc_asked", false)) {
            prefs.edit().putBoolean("wx_loc_asked", true).apply()
            runCatching { ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
        }
    }

    val w = weather ?: return
    val (icon, label) = when (w.wx) {
        Wx.SUNNY -> R.drawable.ic_wx_sunny to "맑음"
        Wx.PARTLY -> R.drawable.ic_wx_partly to "구름많음"
        Wx.CLOUDY -> R.drawable.ic_wx_cloudy to "흐림"
        Wx.RAIN -> R.drawable.ic_wx_rain to "비"
        Wx.SNOW -> R.drawable.ic_wx_snow to "눈"
    }
    // 앱 안 다크 토글이 시스템과 다를 수 있어 isSystemInDarkTheme 대신 실제 배경 밝기로 판단한다.
    val (bg, fg) = wxColors(w.wx, MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    // 모양은 옆의 RestCountChip 과 같은 알약 + 같은 글자 크기 — 헤더에서 한 쌍으로 읽히게.
    // 높이를 고정하지 않는다(글자배율이 커지면 같이 큰다 — 하단 탭에서 겪은 잘림의 원인이 고정 높이였다).
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(999.dp), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ⚠ Icon 이 아니라 Image — tint 가 먹으면 파스텔 아이콘 색이 통째로 날아간다(ic_deadhead_* 와 같은 함정).
            // 날씨 상태는 아이콘 모양에만 담기므로 contentDescription 이 있어야 화면낭독기에서 읽힌다.
            Image(painterResource(icon), label, Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text(
                "${w.tempC}°",
                fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false,
            )
        }
    }
}
