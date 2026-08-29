plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "ir.vil3ntec.tohid"
  compileSdk = 35

  /*
   *  شمارهٔ نسخه.
   *
   *  تا حالا ثابت بود («۳٫۲٫۰») و هر ساختی همان شماره را می‌گرفت؛ پس
   *  به‌روزرسانیِ داخلِ برنامه هیچ‌وقت چیزی «تازه‌تر» پیدا نمی‌کرد و کاربر
   *  همیشه «نسخهٔ شما به‌روز است» می‌دید.
   *
   *  حالا آخرین رقم از شمارهٔ ساختِ CI می‌آید. روی رایانهٔ خودی صفر است.
   */
  val versionBase = "3.2"
  val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0

  defaultConfig {
    // همان بستهٔ نسخهٔ قبلی: نصب می‌شود *روی* آن، و داده‌های قدیمی
    // سرِ جایشان می‌مانند تا وارد شوند.
    applicationId = "ir.vil3ntec.tohid"
    minSdk = 24
    targetSdk = 35
    versionCode = 100 + buildNumber
    versionName = "$versionBase.$buildNumber"
    resourceConfigurations += listOf("fa", "en")

    ndk {
      // فقط پردازندهٔ گوشی‌های واقعی. نسخه‌های x86 فقط به دردِ
      // شبیه‌سازِ رایانه می‌خورند و ۱۲ مگابایت الکی به فایلِ نصبی
      // اضافه می‌کنند — یعنی همان‌قدر اینترنت، هر بار.
      abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
  }

  signingConfigs {
    create("release") {
      storeFile = file("tohid-release.jks")
      storePassword = "tohid-shop"
      keyAlias = "tohid"
      keyPassword = "tohid-shop"
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true; buildConfig = true }
}

dependencies {
  val compose = platform("androidx.compose:compose-bom:2024.12.01")
  implementation(compose)
  androidTestImplementation(compose)

  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.navigation:navigation-compose:2.8.5")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.webkit:webkit:1.12.1")

  // دوربین و خواندنِ بارکد — مدلِ خواندن داخلِ خودِ برنامه است، پس در
  // دکانی که اینترنت ندارد هم کار می‌کند
  implementation("androidx.camera:camera-core:1.4.1")
  implementation("androidx.camera:camera-camera2:1.4.1")
  implementation("androidx.camera:camera-lifecycle:1.4.1")
  implementation("androidx.camera:camera-view:1.4.1")
  implementation("com.google.mlkit:barcode-scanning:17.3.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

  testImplementation("junit:junit:4.13.2")
}

/**
 *  شمارهٔ نسخه را چاپ می‌کند تا گردشِ کار همان عددی را روی فایل بنویسد که
 *  واقعاً داخلِ APK است — نه عددی که خودش جداگانه حساب کرده باشد.
 */
tasks.register("printVersionName") {
  doLast { println(android.defaultConfig.versionName) }
}
