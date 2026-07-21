package com.sinjeong.crewcalendar.domain.model

/** 사업소 부서·시설 연락처 (비상연락망 2026-06-16 기준, 개인번호 제외). */
object BundledContacts {
    data class Contact(val name: String, val number: String, val ext: Boolean) // ext=구내(내선)
    data class Section(val title: String, val items: List<Contact>)

    // 직통(ext=false)은 휴대폰에서 걸 수 있게 02 지역번호 포함. 구내(ext=true)는 사업소 유선 전용.
    val SECTIONS: List<Section> = listOf(
        Section("사무실", listOf(
            Contact("소장실", "02-6110-6840", false),
            Contact("관리과", "02-6110-6842", false),
            Contact("운용과", "02-6110-6845", false),
            Contact("지도과", "02-6110-6850", false),
        )),
        Section("관제", listOf(
            Contact("기지관제", "02-6110-6621", false),
            Contact("기지관제(2)", "02-6110-6622", false),
            Contact("기지 현장", "6620", true),
            Contact("2호선 관제(일반)", "02-6110-5905", false),
        )),
        Section("주박지 (구내)", listOf(
            Contact("신도림 A침실", "2844", true),
            Contact("신도림 B침실", "1394", true),
            Contact("홍대입구", "2894", true),
        )),
        Section("승무 내선 (구내)", listOf(
            Contact("대림 지도", "8505", true),
            Contact("대림 운용", "8513", true),
            Contact("동대문 지도", "8710", true),
            Contact("동대문 운용", "8705", true),
            Contact("군자 운용", "6230", true),
        )),
        Section("노동조합", listOf(
            Contact("신정지회", "02-6110-6859", false),
            Contact("신정지부", "02-6110-6948", false),
        )),
    )
}
