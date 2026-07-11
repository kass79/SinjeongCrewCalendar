package com.sinjeong.crewcalendar.domain.model

/** 본선 전반/후반 사업시각 (25.03.04 시각표 파싱) — [전반시작, 전반종료, 후반시작, 후반종료] */
object MainLegs {
    val WEEKDAY: Map<Int, List<String>> = mapOf(
        1 to listOf("7:12", "9:40", "14:11", "15:56"),
        2 to listOf("7:23", "10:40", "13:39", "15:23"),
        3 to listOf("7:18", "10:33", "14:06", "15:51"),
        4 to listOf("7:30", "10:45", "15:36", "17:21"),
        5 to listOf("7:47", "10:55", "15:20", "17:05"),
        6 to listOf("7:52", "11:00", "15:26", "17:26"),
        7 to listOf("7:41", "9:19", "14:01", "17:16"),
        8 to listOf("7:57", "11:05", "15:41", "17:25"),
        9 to listOf("8:02", "11:10", "15:51", "17:36"),
        10 to listOf("7:55", "11:09", "17:10", "18:55"),
        11 to listOf("8:05", "11:22", "16:57", "18:42"),
        12 to listOf("8:07", "11:16", "16:50", "18:35"),
        13 to listOf("8:21", "10:43", "16:47", "17:55"),
        14 to listOf("8:32", "10:58", "17:54", "19:03"),
        15 to listOf("8:34", "10:10", "14:43", "17:59"),
        16 to listOf("8:35", "10:18", "17:15", "19:14"),
        17 to listOf("8:42", "11:43", "17:24", "19:14"),
        18 to listOf("9:00", "10:36", "15:08", "18:24"),
        19 to listOf("9:21", "12:21", "17:01", "18:46"),
        20 to listOf("9:49", "12:51", "17:06", "18:51"),
        21 to listOf("10:40", "13:39", "18:07", "20:08"),
        22 to listOf("10:55", "13:56", "18:20", "20:56"),
        23 to listOf("11:00", "14:01", "17:40", "20:38"),
        24 to listOf("11:05", "14:06", "18:27", "21:10"),
        25 to listOf("11:10", "14:11", "18:31", "20:16"),
        26 to listOf("11:43", "14:43", "18:48", "20:33"),
        27 to listOf("12:21", "15:20", "18:59", "20:45"),
        28 to listOf("12:51", "15:51", "18:59", "20:44"),
        29 to listOf("13:56", "16:57", "19:10", "20:55"),
    )

    val HOLIDAY: Map<Int, List<String>> = mapOf(
        1 to listOf("7:10", "9:37", "13:33", "14:59"),
        2 to listOf("7:52", "10:49", "13:33", "15:33"),
        3 to listOf("8:14", "10:29", "15:59", "18:08"),
        4 to listOf("8:30", "11:38", "14:07", "16:08"),
        5 to listOf("8:25", "11:22", "14:03", "16:53"),
        6 to listOf("8:28", "10:08", "14:24", "16:24"),
        7 to listOf("8:42", "11:44", "14:24", "17:23"),
        8 to listOf("8:58", "12:12", "14:29", "16:28"),
        9 to listOf("8:46", "11:44", "14:29", "16:29"),
        10 to listOf("9:04", "12:03", "14:48", "16:48"),
        11 to listOf("9:14", "12:14", "14:59", "16:58"),
        12 to listOf("9:28", "12:41", "15:03", "17:03"),
        13 to listOf("9:52", "12:55", "15:28", "17:29"),
        14 to listOf("10:17", "13:27", "15:38", "18:30"),
        15 to listOf("10:39", "13:50", "15:40", "17:39"),
        16 to listOf("10:49", "13:48", "15:54", "17:52"),
        17 to listOf("11:22", "14:22", "15:58", "18:48"),
        18 to listOf("11:38", "14:39", "16:18", "18:19"),
        19 to listOf("11:39", "14:39", "16:28", "19:25"),
        20 to listOf("11:44", "14:44", "16:47", "19:42"),
        21 to listOf("11:44", "13:52", "16:23", "18:29"),
        22 to listOf("12:03", "15:03", "16:33", "19:28"),
        23 to listOf("12:14", "15:14", "17:22", "19:20"),
        24 to listOf("12:41", "15:43", "17:38", "19:37"),
        25 to listOf("12:55", "15:55", "17:59", "19:57"),
    )

    /** 야간: 조합별 (휴휴 33~35는 운휴대기라 사업시각 없음) */
    val NIGHT: Map<Int, Map<NightCombo, List<String>>> = mapOf(
        33 to mapOf(NightCombo.PP to listOf("17:21", "20:35", "5:52", "6:30"), NightCombo.PH to listOf("17:21", "20:35", "5:54", "6:37"), NightCombo.HP to listOf("16:14", "19:26", "5:52", "6:30")),
        34 to mapOf(NightCombo.PP to listOf("17:44", "20:45", "5:40", "7:33"), NightCombo.PH to listOf("17:44", "20:45", "5:34", "7:30"), NightCombo.HP to listOf("16:38", "19:36", "5:40", "7:33")),
        35 to mapOf(NightCombo.PP to listOf("18:09", "21:09", "7:39", "8:47"), NightCombo.PH to listOf("18:09", "21:09", "6:07", "7:53"), NightCombo.HP to listOf("17:14", "20:12", "5:43", "7:42")),
        36 to mapOf(NightCombo.PP to listOf("18:36", "21:36", "5:55", "7:56"), NightCombo.PH to listOf("18:36", "21:36", "6:15", "8:00"), NightCombo.HH to listOf("16:14", "19:26", "6:46", "8:29"), NightCombo.HP to listOf("17:24", "20:23", "5:55", "7:56")),
        37 to mapOf(NightCombo.PP to listOf("18:40", "21:55", "6:48", "8:36"), NightCombo.PH to listOf("18:40", "21:55", "6:11", "6:54"), NightCombo.HH to listOf("16:38", "19:36", "5:39", "7:31"), NightCombo.HP to listOf("18:04", "21:09", "6:00", "7:45")),
        38 to mapOf(NightCombo.PP to listOf("19:53", "21:29", "5:43", "7:42"), NightCombo.PH to listOf("19:53", "21:29", "5:39", "7:31"), NightCombo.HH to listOf("17:14", "20:12", "5:34", "7:30"), NightCombo.HP to listOf("19:36", "22:35", "6:01", "8:03")),
        39 to mapOf(NightCombo.PP to listOf("20:01", "23:15", "6:00", "7:45"), NightCombo.PH to listOf("20:01", "23:15", "6:07", "6:45"), NightCombo.HH to listOf("17:24", "20:23", "6:11", "8:07"), NightCombo.HP to listOf("19:42", "22:42", "6:17", "8:10")),
        40 to mapOf(NightCombo.PP to listOf("20:18", "23:19", "6:01", "8:03"), NightCombo.PH to listOf("20:18", "23:19", "6:24", "8:07"), NightCombo.HH to listOf("18:04", "21:09", "6:58", "8:40"), NightCombo.HP to listOf("19:05", "20:35", "6:27", "8:20")),
        41 to mapOf(NightCombo.PP to listOf("20:29", "23:30", "6:17", "8:10"), NightCombo.PH to listOf("20:29", "23:30", "6:46", "8:29"), NightCombo.HH to listOf("19:36", "22:35", "5:54", "7:53"), NightCombo.HP to listOf("19:22", "22:23", "6:48", "8:36")),
        42 to mapOf(NightCombo.PP to listOf("20:30", "23:31", "6:27", "8:20"), NightCombo.PH to listOf("20:30", "23:31", "6:58", "8:40"), NightCombo.HH to listOf("19:42", "22:42", "6:07", "8:00"), NightCombo.HP to listOf("19:27", "22:30", "7:00", "8:49")),
        43 to mapOf(NightCombo.PP to listOf("20:40", "23:42", "7:01", "8:57"), NightCombo.PH to listOf("20:40", "23:42", "6:58", "8:43"), NightCombo.HH to listOf("19:05", "22:13", "6:58", "8:43"), NightCombo.HP to listOf("20:12", "21:41", "7:39", "8:47")),
        44 to mapOf(NightCombo.PP to listOf("20:45", "0:00", "7:27", "9:15"), NightCombo.PH to listOf("20:45", "24:00", "7:15", "8:57"), NightCombo.HH to listOf("19:22", "22:23", "7:15", "8:57"), NightCombo.HP to listOf("20:23", "21:52", "7:01", "8:57")),
        45 to mapOf(NightCombo.PP to listOf("21:03", "0:20", "7:00", "8:49"), NightCombo.PH to listOf("21:03", "24:20", "7:16", "9:01"), NightCombo.HH to listOf("19:27", "22:30", "7:16", "9:01"), NightCombo.HP to listOf("20:35", "22:13", "7:02", "8:50")),
        46 to mapOf(NightCombo.PP to listOf("21:09", "0:17", "7:02", "8:50"), NightCombo.PH to listOf("21:09", "24:17", "7:38", "9:19"), NightCombo.HH to listOf("20:12", "23:17", "7:38", "9:19"), NightCombo.HP to listOf("21:41", "23:17", "7:48", "9:36")),
        47 to mapOf(NightCombo.PP to listOf("21:36", "0:50", "7:48", "9:36"), NightCombo.PH to listOf("21:36", "24:50", "7:45", "9:29"), NightCombo.HH to listOf("20:23", "23:33", "7:45", "9:29"), NightCombo.HP to listOf("21:52", "23:33", "7:27", "9:15")),
        48 to mapOf(NightCombo.PP to listOf("23:19", "25:00", "5:30", "7:18"), NightCombo.PH to listOf("23:19", "25:00", "5:30", "7:16"), NightCombo.HH to listOf("22:23", "24:00", "5:30", "7:16"), NightCombo.HP to listOf("22:23", "24:00", "5:30", "7:18")),
        49 to mapOf(NightCombo.PP to listOf("23:30", "25:00", "5:30", "7:15"), NightCombo.PH to listOf("23:30", "25:00", "5:30", "7:28"), NightCombo.HH to listOf("22:30", "24:00", "5:30", "7:28"), NightCombo.HP to listOf("22:30", "24:00", "5:30", "7:15")),
        50 to mapOf(NightCombo.PP to listOf("23:31", "25:00", "5:30", "7:17"), NightCombo.PH to listOf("23:31", "25:00", "5:30", "7:28"), NightCombo.HH to listOf("22:35", "24:00", "5:30", "7:28"), NightCombo.HP to listOf("22:35", "24:00", "5:30", "7:17")),
        51 to mapOf(NightCombo.PP to listOf("23:42", "25:00", "5:30", "7:27"), NightCombo.PH to listOf("23:42", "25:00", "5:30", "7:25"), NightCombo.HH to listOf("22:42", "24:00", "5:30", "7:25"), NightCombo.HP to listOf("22:42", "24:00", "5:30", "7:27")),
    )

    fun forDay(number: Int, holiday: Boolean): List<String>? =
        (if (holiday) HOLIDAY else WEEKDAY)[number]

    fun forNight(number: Int, combo: NightCombo): List<String>? =
        NIGHT[number]?.get(combo)
}
