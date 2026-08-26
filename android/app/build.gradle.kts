plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * فایل‌های خود برنامه (همان چیزی که در سایت است) داخل بسته کپی می‌شوند.
 *
 * منبع یکی است: index.html و license/ و sounds/ و icons/ همان‌هایی هستند
 * که روی سایت اجرا می‌شوند. پس نسخه‌ی گوشی و نسخه‌ی وب هرگز از هم جدا
 * نمی‌افتند و «یک نقطه» هم فرق نمی‌کنند.
 */
val webRoot: File = rootProject.projectDir.parentFile
val webAppDir = layout.buildDirectory.dir("generated/webapp")
val copyWebApp = tasks.register<Copy>("copyWebApp") {
    from(webRoot) {
        include("index.html", "sw.js", "manifest.webmanifest")
        include("license/**", "icons/**", "sounds/**", "fonts/**")
    }
    into(webAppDir)
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

        // آدرس پیش‌فرض سرور. با -PshopServerUrl=... یا متغیر محیطی
        // SHOP_SERVER_URL هنگام ساخت پر می‌شود؛ کاربر هم می‌تواند از داخل
        // برنامه عوضش کند. هیچ آدرسی در کد قفل نیست.
        val defaultServerUrl: String =
            (project.findProperty("shopServerUrl") as String?)
                ?: System.getenv("SHOP_SERVER_URL")
                ?: ""
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$defaultServerUrl\"")
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
        buildConfig = true
    }

    sourceSets["main"].assets.srcDir(webAppDir)

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

}

tasks.named("preBuild") { dependsOn(copyWebApp) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(copyWebApp) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // WebViewAssetLoader: فایل‌های داخل برنامه را با یک نشانی امن سرو می‌کند
    implementation(libs.androidx.webkit)

    // برای گرفتن به‌روزرسانی از گیت‌هاب
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
