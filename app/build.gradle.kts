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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sinjeong.crewcalendar"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.5.1"
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
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,INDEX.LIST}"
    }
}

// google-api-client 계열이 grpc-api만 1.66으로 올려 Firestore(grpc 1.62)와 어긋나면
// 런타임 NoClassDefFoundError(io.grpc.InternalGlobalInterceptors)로 크래시 → 전 부품 버전 통일
configurations.all {
    resolutionStrategy.force(
        "io.grpc:grpc-api:1.62.2",
        "io.grpc:grpc-context:1.62.2",
    )
}

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
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google Sign-In (Credential Manager) + Calendar API
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android) { exclude(group = "org.apache.httpcomponents") }
    implementation(libs.google.api.calendar) { exclude(group = "org.apache.httpcomponents") }

    // Widget (Glance)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Background sync
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
