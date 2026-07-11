package com.sinjeong.crewcalendar.domain.model

/**
 * 행로표(신정승무사업소 근무표, 확인일 2023.10.04 — 시각과 대조해 현행본 확인됨) 열번·근무시간 데이터.
 * 본선(1~29 주간, 33~51 야간)만 확보됨 — 지선 행로표는 미입수.
 * 야간은 평평/휴휴/휴평만 확보 (평휴 파일 미입수).
 *
 * firstHalf/secondHalf: 열번을 "·"로 구분해 순서대로 나열 (회송·편승 등 특기사항 포함).
 * totalWorkTime: 근무표의 "계" 값 (운전+준비+대기+편승+정리+야간 합산) 그대로.
 */
data class TrainAssignment(
    val firstHalf: String,
    val secondHalf: String,
    val totalWorkTime: String,
)

object RouteTable {

    /** 본선 주간 평일 (1~29) */
    val MAIN_DAY_WEEKDAY: Map<Int, TrainAssignment> = mapOf(
        1 to TrainAssignment("2045·2109·1923", "군자기지 편승·2265·2299", "10:04"),
        2 to TrainAssignment("6939·2058·2112·2162", "2230·2260", "9:23"),
        3 to TrainAssignment("2049·2113·2173·5930", "2263·2297", "9:50"),
        4 to TrainAssignment("2057·2121·5922", "2261·2295·2333", "10:51"),
        5 to TrainAssignment("5949·2073·2137·2187", "2264·2296", "10:38"),
        6 to TrainAssignment("5951·2077·2141·2191", "6941·2272·2306(동대문교대)", "10:54"),
        7 to TrainAssignment("2060·2114·2164·6928", "2297·2335", "10:45"),
        8 to TrainAssignment("5953·2081·2145·2193", "2299·2337", "10:44"),
        9 to TrainAssignment("5955·2085·2149·2197", "2303·2343", "10:44"),
        10 to TrainAssignment("2075·2139·2189·5932", "2337·2385", "11:19"),
        11 to TrainAssignment("2083·2147·2195·5934", "2331·2379", "11:21"),
        12 to TrainAssignment("동대문교대·2074·2128·2176·6930", "2296·2342", "11:13"),
        13 to TrainAssignment("2082·2136·2929(군자입출고)", "1942·2353", "10:34"),
        14 to TrainAssignment("2101·2163·1935(군자입출고)", "2952·2360", "10:40"),
        15 to TrainAssignment("2090·2144·6922", "2277·2311·2355", "10:29"),
        16 to TrainAssignment("2103·2165·5928", "6945·2314·2366", "10:47"),
        17 to TrainAssignment("2107·2169·2209·대림역승객취급·5961·2347", "2395", "11:11"),
        18 to TrainAssignment("2106·2158·6926", "2260·2292·2336", "10:29"),
        19 to TrainAssignment("2118·2168·2204", "2333·2381", "10:20"),
        20 to TrainAssignment("대림승무원교대·2153·2201·2235", "2335·2383", "10:07"),
        21 to TrainAssignment("2162·2200·2230", "6953·2344·2398", "10:32"),
        22 to TrainAssignment("2187·2225·2259", "2342·2396·6936", "10:47"),
        23 to TrainAssignment("2191·2227·2261", "2353·5936(대림교대)", "10:23"),
        24 to TrainAssignment("2193·2229·2263", "2379·5942·2427", "10:50"),
        25 to TrainAssignment("2197·2231·2265", "2381·2429", "10:11"),
        26 to TrainAssignment("2209·2243·2277", "2360·2414", "9:55"),
        27 to TrainAssignment("2204·2234·2264", "2395·2443", "9:30"),
        28 to TrainAssignment("2235·2269·2303", "2366·2420", "8:58"),
        29 to TrainAssignment("2259·2293·2331", "2401·2447", "8:51"),
    )

    /** 본선 주간 휴일·토 (1~25, 26~29는 휴일 운휴) */
    val MAIN_DAY_HOLIDAY: Map<Int, TrainAssignment> = mapOf(
        1 to TrainAssignment("2039·2073·1925(군자입고)", "군자→성수 편승·2224", "9:09"),
        2 to TrainAssignment("2056·2088·2124", "2200·2238", "8:47"),
        3 to TrainAssignment("2064·2096·2921(군자입출고)", "2934·2304", "10:28"),
        4 to TrainAssignment("5933·2071·2109·2145", "2214·2252", "9:14"),
        5 to TrainAssignment("2068·2102·2138", "5943·2231·2269", "9:36"),
        6 to TrainAssignment("2067·2103·5922", "2222·2260", "9:01"),
        7 to TrainAssignment("2074·2110·2148", "2217·5926·2255", "9:30"),
        8 to TrainAssignment("5935·2083·2121·2157(대림역교대후편승)", "2224·2262", "9:24"),
        9 to TrainAssignment("2075·2111·2147", "2219·2259", "8:48"),
        10 to TrainAssignment("2082·2118·2156", "2232·2270", "8:49"),
        11 to TrainAssignment("2087·2123·2159", "2233·2271", "8:49"),
        12 to TrainAssignment("6935·2098·2134·2172", "2238·2276", "9:18"),
        13 to TrainAssignment("5939·2105·2139·2175", "2248·2286", "9:09"),
        14 to TrainAssignment("5941·2115·2151·2187(대림역교대후편승)", "2252·2290·6924", "9:53"),
        15 to TrainAssignment("6937·2126·2164·2202(대림역교대후편승)", "2249·2287", "9:20"),
        16 to TrainAssignment("2124·2162·2200", "2260·2296", "8:47"),
        17 to TrainAssignment("2138·2176·2214", "2262·2298·6926", "9:20"),
        18 to TrainAssignment("2145·2181·2217", "2270·2310", "8:51"),
        19 to TrainAssignment("동대문교대·2146·2184·2222", "2271·5930·2305", "9:27"),
        20 to TrainAssignment("2147·2183·2219", "6939·2308·2342", "9:46"),
        21 to TrainAssignment("2148·2186", "성수교대·군자편승·1942·2307", "8:09"),
        22 to TrainAssignment("2156·2194·2232", "2276·2316·6928", "9:26"),
        23 to TrainAssignment("2159·2195·2233", "2296·2334", "8:48"),
        24 to TrainAssignment("2172·2210·2248", "2304·2340", "8:50"),
        25 to TrainAssignment("2175·2211·2249", "2307·2341", "8:48"),
    )

    /** 본선 야간 4종 조합별 [전반, 후반, 총근무시간] — 평휴(PH)는 자료 미입수 */
    val MAIN_NIGHT: Map<Int, Map<NightCombo, TrainAssignment>> = mapOf(
        33 to mapOf(
            NightCombo.PP to TrainAssignment("2343·2391·2439·5944·5925(2015열차연결)", "6:00~6:30 회송", "10:56"),
            NightCombo.HH to TrainAssignment("운휴대기(33·34·35 공통)", "-", "10:30"),
            NightCombo.HP to TrainAssignment("2259·2295·2329·5932", "2015열차교대·5925", "10:55"),
        ),
        34 to mapOf(
            NightCombo.PP to TrainAssignment("2355·2403·2449", "5923·2011·2049", "11:14"),
            NightCombo.HH to TrainAssignment("운휴대기(33·34·35 공통)", "-", "10:30"),
            NightCombo.HP to TrainAssignment("2269·2303·2339", "5923·2011·2049", "11:11"),
        ),
        35 to mapOf(
            NightCombo.PP to TrainAssignment("2336·2390·2440", "군자편승·군자출고·1936·2101", "11:14"),
            NightCombo.HH to TrainAssignment("운휴대기(33·34·35 공통)", "-", "10:30"),
            NightCombo.HP to TrainAssignment("2286·2326·2358", "6925·2014·2052", "11:18"),
        ),
        36 to mapOf(
            NightCombo.PP to TrainAssignment("2383·2431·2465", "6927·2018·2060", "11:21"),
            NightCombo.HH to TrainAssignment("2259·2295·2329·5932", "2032·2064", "11:30"),
            NightCombo.HP to TrainAssignment("2287·2321·2355", "6927·2018·2060", "11:20"),
        ),
        37 to mapOf(
            NightCombo.PP to TrainAssignment("2385·2433·2467·5948", "2032·2082", "11:38"),
            NightCombo.HH to TrainAssignment("2269·2303·2339", "5923·2011·2041", "11:10"),
            NightCombo.HP to TrainAssignment("2310·2344·2376·6932", "출고열차#5925교대·2015·2057", "11:25"),
        ),
        38 to mapOf(
            NightCombo.PP to TrainAssignment("2398·2446·6942", "5925·2014·2052", "10:41"),
            NightCombo.HH to TrainAssignment("2286·2326·2358", "6923·2012·2042", "11:14"),
            NightCombo.HP to TrainAssignment("2339·2369·2399", "6929·2020·2064", "11:20"),
        ),
        39 to mapOf(
            NightCombo.PP to TrainAssignment("2429·2463·2493·5952", "33DIA#5925출고교대·2015·2057", "11:34"),
            NightCombo.HH to TrainAssignment("2287·2321·2355", "6931·2024·2056", "11:16"),
            NightCombo.HP to TrainAssignment("2341·2371·2401", "5929·2023·2075", "11:12"),
        ),
        40 to mapOf(
            NightCombo.PP to TrainAssignment("2414·2458·2488", "6929·2020·2064", "11:23"),
            NightCombo.HH to TrainAssignment("2310·2344·2376·6932", "2036·2068", "11:23"),
            NightCombo.HP to TrainAssignment("2334·2366", "5931·2027·2083", "9:44"),
        ),
        41 to mapOf(
            NightCombo.PP to TrainAssignment("2420·2462·2492", "5929·2023·2075", "11:14"),
            NightCombo.HH to TrainAssignment("2339·2369·2399", "6927·2018·2050", "11:17"),
            NightCombo.HP to TrainAssignment("2340·2372·2404", "2032·2082", "10:39"),
        ),
        42 to mapOf(
            NightCombo.PP to TrainAssignment("2443·2475·2501", "5931·2027·2083", "11:15"),
            NightCombo.HH to TrainAssignment("2341·2371·2401", "5929·2021·2051", "11:12"),
            NightCombo.HP to TrainAssignment("2342·2374·2406", "2038·2090", "10:42"),
        ),
        43 to mapOf(
            NightCombo.PP to TrainAssignment("2447·2479·2505", "5937·2043·2107", "11:18"),
            NightCombo.HH to TrainAssignment("2334·2366·2398·6936", "2035·2067", "11:28"),
            NightCombo.HP to TrainAssignment("신도림교대후 군자기지편승·후반군자출고·2390·2418", "1936·2101", "9:43"),
        ),
        44 to mapOf(
            NightCombo.PP to TrainAssignment("2449·2481·2507·5956", "2052·2106", "11:22"),
            NightCombo.HH to TrainAssignment("2340·2372·2404", "2042·2074", "10:33"),
            NightCombo.HP to TrainAssignment("2355·2385", "5937·2043·2107", "9:44"),
        ),
        45 to mapOf(
            NightCombo.PP to TrainAssignment("대림승무원교대·2453·2485·2511·5958", "2038·2090", "11:41"),
            NightCombo.HH to TrainAssignment("2342·2374·2406", "2041·2075", "10:38"),
            NightCombo.HP to TrainAssignment("2366·2398·6936", "2039·2103", "10:01"),
        ),
        46 to mapOf(
            NightCombo.PP to TrainAssignment("2440·2474·2504·6952", "2039·2103", "11:31"),
            NightCombo.HH to TrainAssignment("2358·2390·2418·6942", "2050·2082", "11:05"),
            NightCombo.HP to TrainAssignment("2390·2418·6942", "2064·2118", "9:43"),
        ),
        47 to mapOf(
            NightCombo.PP to TrainAssignment("2465·2495·2519·5962", "2064·2118", "11:21"),
            NightCombo.HH to TrainAssignment("2355·2385·2413·5938", "2051·2087", "11:13"),
            NightCombo.HP to TrainAssignment("2385·2413·5938", "2052·2106", "9:49"),
        ),
        48 to mapOf(
            NightCombo.PP to TrainAssignment("2488·2514·홍대입구역주박·2004", "2032", "9:05"),
            NightCombo.HH to TrainAssignment("2404·2430·홍대입구역주박·2004", "2032", "9:59"),
            NightCombo.HP to TrainAssignment("2404·2430·홍대입구역주박·2004", "2032", "10:01"),
        ),
        49 to mapOf(
            NightCombo.PP to TrainAssignment("2492·2516·신도림역주박·2006", "2038", "8:51"),
            NightCombo.HH to TrainAssignment("2406·2432·신도림역주박·2006", "2036", "10:03"),
            NightCombo.HP to TrainAssignment("2406·2432·신도림역주박·2006", "2038", "9:51"),
        ),
        50 to mapOf(
            NightCombo.PP to TrainAssignment("2501·2523·신도림역주박·2005", "2039", "8:52"),
            NightCombo.HH to TrainAssignment("2399·2423·신도림역주박·2005", "2035", "9:59"),
            NightCombo.HP to TrainAssignment("2399·2423·신도림역주박·2005", "2039", "9:48"),
        ),
        51 to mapOf(
            NightCombo.PP to TrainAssignment("2505·2525·홍대입구역주박·2009", "2045", "8:50"),
            NightCombo.HH to TrainAssignment("2401·2425·홍대입구역주박·2009", "2039", "9:48"),
            NightCombo.HP to TrainAssignment("2401·2425·홍대입구역주박·2009", "2045", "9:50"),
        ),
    )

    /** 33~35(평평/휴평)·전 다이아(휴휴) 등 "운휴대기" — 열차 운행 없이 대기만 하는 근무 여부 */
    fun isStandbyOnly(number: Int, combo: NightCombo): Boolean =
        number in 33..35 && combo == NightCombo.HH

    fun forMainDay(number: Int, holiday: Boolean): TrainAssignment? =
        (if (holiday) MAIN_DAY_HOLIDAY else MAIN_DAY_WEEKDAY)[number]

    fun forMainNight(number: Int, combo: NightCombo): TrainAssignment? =
        MAIN_NIGHT[number]?.get(combo)
}
