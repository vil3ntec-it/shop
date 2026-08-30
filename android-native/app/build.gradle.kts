import java.security.KeyStore
import java.security.MessageDigest

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

  /*
   *  اثرِ انگشتِ گواهیِ امضا، همین‌جا در زمانِ ساخت خوانده می‌شود.
   *
   *  برنامه هنگامِ اجرا امضای خودش را با همین مقایسه می‌کند. کسی که
   *  فایل را باز کند، قفلِ اشتراک را بردارد و دوباره ببندد، مجبور است
   *  با کلیدِ خودش امضا کند — و آن امضا با این یکی نمی‌خواند. پس نسخهٔ
   *  دست‌کاری‌شده می‌تواند اجرا شود، ولی اشتراک به آن داده نمی‌شود.
   *
   *  اگر خواندنِ کلید به هر دلیلی نشد، مقدار خالی می‌ماند و بررسی
   *  خاموش می‌شود؛ نباید ساختِ برنامه به این گیر کند.
   */
  val signingFingerprint: String = runCatching {
    val store = KeyStore.getInstance("JKS")
    file("tohid-release.jks").inputStream().use { input ->
      store.load(input, "tohid-shop".toCharArray())
    }
    val cert = store.getCertificate("tohid")
    MessageDigest.getInstance("SHA-256")
      .digest(cert.encoded)
      .joinToString("") { byte -> "%02x".format(byte) }
  }.getOrDefault("")

  signingConfigs {
    create("release") {
      storeFile = file("tohid-release.jks")
      storePassword = "tohid-shop"
      keyAlias = "tohid"
      keyPassword = "tohid-shop"

      /*
       *  طرحِ امضا — صریح نوشته شده، نه به امیدِ پیش‌فرض.
       *
       *  v1 همان امضای قدیمیِ داخلِ فایلِ zip است و روی اندروید ۷ به
       *  بالا لازم نیست (کمینهٔ ما ۲۴ است). خاموش‌کردنش یک راهِ شناختهٔ
       *  دستکاری را می‌بندد: فایلی که فقط v1 دارد را می‌شود طوری عوض
       *  کرد که امضا هنوز درست به نظر برسد.
       *
       *  v2 و v3 کلِ فایل را امضا می‌کنند. v3 همان چیزی است که اجازه
       *  می‌دهد کلید روزی عوض شود بدونِ اینکه نصبِ روی نسخهٔ قبلی
       *  بشکند.
       */
      enableV1Signing = false
      enableV2Signing = true
      enableV3Signing = true
    }
  }

  buildTypes {
    debug {
      // در نسخهٔ آزمایشی بررسیِ امضا خاموش است، وگرنه هنگامِ توسعه
      // اشتراک هیچ‌وقت باز نمی‌شود
      buildConfigField("String", "SIGNING_SHA256", "\"\"")
    }
    release {
      signingConfig = signingConfigs.getByName("release")
      buildConfigField("String", "SIGNING_SHA256", "\"$signingFingerprint\"")
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
