plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // FCM(보호자 알림) 붙일 때 주석 해제 — 지금은 꺼둠
    // id("com.google.gms.google-services")
}

android {
    namespace = "com.angae.phishingdefender"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.angae.phishingdefender"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // ★ 핵심: XML 레이아웃을 코드에서 타입 안전하게 참조
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // 안드로이드 기본
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 액티비티 생명주기/권한 요청 편의
    implementation("androidx.activity:activity-ktx:1.9.2")

    // 코루틴 (탐지/알림 비동기 처리용 — 미리 깔아둠)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // FCM(보호자 알림) — 지금은 주석, 알림 단계에서 해제
    // implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    // implementation("com.google.firebase:firebase-messaging-ktx")

    // 순수 도메인 로직 단위테스트용 (에뮬 없이 KeywordDetector 테스트)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
