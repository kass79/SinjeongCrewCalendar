import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase 연동 시(google-services.json 존재) 자동 활성화 — 오프라인 체험판은 없이 빌드
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.sinjeong.crewcalendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sinjeong.crewcalendar"
        minSdk = 26
        // targetSdk 36 (Android 16) — 플레이 2026-08-31 요구치. edge-to-edge는 이미 opt-in
        // 상태(enableEdgeToEdge)라 강제 전환의 영향이 없다. 자세한 조사는 docs/project-notes.md.
        targetSdk = 36
        versionCode = 94
        versionName = "1.6.82"
        vectorDrawables { useSupportLibrary = true }
    }

    // 플레이스토어 업로드용 서명 (keystore.properties는 git 제외 — 분실 주의!)
    val ksProps = rootProject.file("keystore.properties")
    if (ksProps.exists()) {
        val p = Properties().apply { ksProps.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // ponytail: 미니파이 끔 — R8 규칙 검증 전엔 안정성 우선, 용량 줄일 때 다시 켜기
            isMinifyEnabled = false
            isShrinkResources = false
            if (ksProps.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // java.time on minSdk 26
    }
    kotlinOptions { jvmTarget = "17" }
    // buildConfig: 설정 화면의 앱 버전 표시(BuildConfig.VERSION_NAME/VERSION_CODE)에 필요.
    // AGP 8부터 기본값이 false라 켜 주지 않으면 BuildConfig 클래스가 아예 생성되지 않는다.
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,INDEX.LIST}"
    }
}

// grpc 버전 강제(force)는 google-api-client 계열이 grpc-api만 1.66으로 끌어올려 Firestore와
// 어긋나던 문제 때문이었다. v1.6.26에서 그 의존성을 통째로 뺐으므로 강제도 같이 걷어낸다
// (Firebase BOM이 주는 grpc 조합을 그대로 쓴다).

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // firebase-analytics 제거(v1.6.18): 코드 호출 0건인데 매니페스트 병합으로
    // AD_ID·ACCESS_ADSERVICES_* 광고 권한이 딸려와 "광고 ID 수집" 신고 의무가 생겼다.
    // 광고 없는 사내앱이라 데이터 세이프티를 단순하게 유지하려고 뺀다. 필요하면 되살릴 것.
    //
    // firebase-messaging 제거(v1.6.33): 같은 이유의 재발. updateFcmToken이 로컬·원격 양쪽
    // `= Unit`이라 토큰을 어디에도 저장하지 않았고 getToken·subscribeToTopic 호출도 0건 —
    // 즉 **특정 사용자에게 푸시를 보낼 방법 자체가 없는데** SDK만 기기 설치 ID를 구글로 계속
    // 보내 데이터 세이프티 "기기 또는 기타 ID" 신고 의무와 c2dm.RECEIVE 병합 권한이 남았다.
    // 나중에 푸시가 실제로 필요해지면 그때 토큰 저장·발송 경로까지 갖춰 제대로 다시 넣을 것
    // (libs.versions.toml의 카탈로그 항목은 남겨 뒀다 — 한 줄이면 되살아난다).

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // 구글 캘린더 동기화 의존성 전부 제거(v1.6.26): credentials·googleid·play-services-auth·
    // google-api-client-android·google-api-services-calendar. GoogleCalendarSyncManager가
    // 어느 화면에서도 안 불려 죽은 코드였고, 이 5개가 앱 용량의 큰 몫이었다.
    // 로그인은 Firebase 익명 인증이라 구글 로그인 부품이 필요 없다 — 되살리려면 설정에 진입점부터.

    // Widget (Glance)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // 주간식단표 글자인식(v1.6.80) — **다운로드형** ML Kit 한국어 인식기.
    // 모델은 구글 플레이 서비스가 들고 있어 APK 에는 얇은 접착제만 들어간다(번들판은 +4MB).
    // 대신 관리자가 처음 쓸 때 모델이 내려받아지는 짧은 대기가 있고, GMS 없는 기기에선 안 된다 —
    // 이 앱은 이미 Firebase(로그인·공유) 때문에 GMS 전제라 새로 생기는 제약이 아니다.
    implementation(libs.mlkit.text.korean)

    // 주간식단표 **한글파일(.hwp)** 읽기(v1.6.81 ④) — 표 칸을 직접 읽으므로 글자인식 없이 정확하다.
    // Apache-2.0 · 0.96MB · 의존성 0개(POI 를 끌어오지 않는다) · 자바 7 바이트코드라 디슈가링 불필요.
    // ⚠ `HWPReader.fromBase64String`만 안드로이드에 없는 javax.xml.bind 를 쓴다 — 부르지 않는다
    //    (`data/menu/MenuHwp.kt` 주석). 나머지 726개 클래스는 java.*·javax.crypto 만 쓴다.
    // **.hwpx 는 이 라이브러리가 필요 없다** — zip+XML 이라 java.util.zip + SAX 로 읽는다(APK 증가 0).
    implementation(libs.hwplib)

    // 주간식단표 **PDF 글자층 직접 추출**(v1.6.82 ②-1). Apache-2.0.
    // ⚠ `PDFBoxResourceLoader.init(context)` 를 앱 시작 때 한 번 불러야 한다(`SinjeongApp`).
    implementation(libs.pdfbox.android)

    // 주간식단표 **사진** → 클라우드 AI 인식(v1.6.82 ②-4). Gemini Developer API 백엔드라
    // **Spark(무료) 요금제 그대로** 돈다. 앱에 API 키가 들어가지 않는다.
    // ⚠ 2026-11-02 부터 **App Check 필수** — 그 전에 켜지 않으면 사진 인식만 멈춘다
    //   (PDF·한글파일·붙여넣기는 클라우드와 무관하므로 영향 없다). docs/project-notes.md 참고.
    implementation(libs.firebase.ai)

    // Background sync
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
