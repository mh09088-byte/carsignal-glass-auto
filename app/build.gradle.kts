plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// release 서명 정보는 .env(환경변수)에서만 읽는다. 없으면 debug 키로 서명해 누구나 실행 가능한 APK를 만든다.
val keystorePath: String? = System.getenv("GLASSAUTO_KEYSTORE")
val keystorePass: String? = System.getenv("GLASSAUTO_KEYSTORE_PASSWORD")
val keyAliasName: String? = System.getenv("GLASSAUTO_KEY_ALIAS")
val keyPass: String? = System.getenv("GLASSAUTO_KEY_PASSWORD")
val hasReleaseKey = listOf(keystorePath, keystorePass, keyAliasName, keyPass).all { !it.isNullOrBlank() } &&
    file(keystorePath!!).exists()

android {
    namespace = "io.github.glassauto"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.glassauto"
        minSdk = 28            // Pleos Connect 요구사항: API 28 이상
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePass
                keyAlias = keyAliasName
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Pleos Connect Vehicle SDK — 기어·좌석 상태 조회, 스마트글라스·조명 제어
    implementation("ai.pleos.playground:Vehicle:2.3.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")   // 단위 테스트에서 실제 JSON 파서 사용 (Android 스텁 대체)
}
