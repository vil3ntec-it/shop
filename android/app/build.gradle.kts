plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// نسخه از تگ گیت‌هاب می‌آید؛ در نبود آن مقدار پیش‌فرض استفاده می‌شود
val versionNameFromCi: String = System.getenv("TOHID_VERSION_NAME") ?: "1.0.0"
val signingEnabled: Boolean = System.getenv("TOHID_SIGNING") == "true"

/**
 * versionCode از خودِ نسخه ساخته می‌شود: 1.2.3 → 1_002_003
 *
 * چرا مهم است: اندروید فقط وقتی اجازه‌ی نصب روی نسخه‌ی قبلی را می‌دهد که
 * versionCode بزرگ‌تر باشد. اگر ثابت بماند، به‌روزرسانی اصلاً نصب نمی‌شود.
 * این فرمول تا نسخه‌ی 2147.999.999 جا دارد — عملاً برای همیشه کافی است.
 */
fun versionCodeOf(name: String): Int {
    val parts = name.trim().removePrefix("v").split('.', '-')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    require(minor < 1000 && patch < 1000) { "شماره نسخه باید کمتر از ۱۰۰۰ باشد: $name" }
    return major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "af.tohid.shop"
    compileSdk = 35

    defaultConfig {
        applicationId = "af.tohid.shop"
        minSdk = 24                 // اندروید ۷ به بالا — تقریباً همه گوشی‌های در گردش
        targetSdk = 35
        versionCode = versionCodeOf(versionNameFromCi)
        versionName = versionNameFromCi
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // مسیر پیش‌فرض سرور؛ کاربر می‌تواند در برنامه عوضش کند
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"\"")
        // مخزن گیت‌هاب برای بررسی نسخه جدید
        buildConfigField("String", "UPDATE_REPO", "\"vil3ntec-it/shop\"")
    }

    signingConfigs {
        if (signingEnabled) {
            create("release") {
                storeFile = file("release.keystore")
                storePassword = System.getenv("TOHID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TOHID_KEY_ALIAS")
                keyPassword = System.getenv("TOHID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // بدون کلید رسمی، خروجی با امضای debug ساخته می‌شود تا بیلد نشکند
            signingConfig = if (signingEnabled) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

}

ksp { arg("room.schemaLocation", "${'$'}projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}
