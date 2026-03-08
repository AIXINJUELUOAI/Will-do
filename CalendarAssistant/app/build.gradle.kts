plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // 🔥 启用序列化插件
    kotlin("plugin.serialization")
}

android {
    namespace = "com.antgskds.calendarassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.antgskds.calendarassistant"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.2.1 Beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // Build fingerprint for anti-piracy verification
        buildConfigField("String", "CODE_AUTHOR", "\"AIXINJUELUOAI\"")
        buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
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
        // 🔥 开启脱糖，支持 Java 8 时间 API
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // === 基础库 (使用默认生成的引用) ===
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ✅✅✅ 新增关键依赖：修复 MainActivity 中的 viewModel() 报错
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // 补充图标库
    implementation("androidx.compose.material:material-icons-extended:1.5.4")

    // === 脱糖库 (Time API 必需) ===
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // === JSON 序列化 (核心数据地基) ===
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // === Ktor 网络库 (AI 请求用) ===
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-core:2.3.7")

    // === ML Kit (OCR 识别) ===
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // === 测试库 ===
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // 🔥 添加这一行：Jetpack Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.0")
}