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
   *  کلیدِ امضا — از محیط، نه از داخلِ مخزن.
   *
   *  تا امروز خودِ فایلِ کلید و رمزش هر دو در مخزن بودند. هر کسی که به
   *  مخزن دسترسی داشت می‌توانست نسخه‌ای بسازد که گوشی‌ها آن را «همان
   *  برنامه» بدانند و روی نصبِ کاربر بنشیند. کلیدِ امضا تنها چیزی است
   *  که اندروید برای شناختنِ سازنده دارد.
   *
   *  حالا از متغیرهای محیط خوانده می‌شود. اگر نبودند، همان فایلِ قبلی
   *  استفاده می‌شود — تا روزی که کلید هنوز به CI منتقل نشده، هیچ ساختی
   *  نشکند. برداشتنِ آن فایل، آخرین گام است نه اولین.
   */
  //  متغیرِ تنظیم‌نشده در GitHub Actions **رشتهٔ خالی** می‌شود، نه
  //  ناموجود. پس `?:` تنها کافی نیست و خالی هم باید «نبود» حساب شود —
  //  وگرنه رمزِ خالی به کلید داده می‌شود و امضا می‌شکند.
  fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

  val keystorePath: String = env("TOHID_KEYSTORE") ?: "tohid-release.jks"
  val keystorePass: String = env("TOHID_KEYSTORE_PASSWORD") ?: "tohid-shop"
  val keystoreAlias: String = env("TOHID_KEY_ALIAS") ?: "tohid"
  val keystoreKeyPass: String = env("TOHID_KEY_PASSWORD") ?: keystorePass

  /*
   *  اثرِ انگشتِ گواهی، در زمانِ ساخت خوانده می‌شود.
   *
   *  برنامه هنگامِ اجرا امضای خودش را با همین می‌سنجد؛ نسخهٔ دست‌کاری‌شده
   *  اجرا می‌شود ولی اشتراک نمی‌گیرد. اگر خواندن نشد، خالی می‌ماند و
   *  بررسی خاموش می‌شود — ساختِ برنامه نباید به این گیر کند.
   */
  val signingFingerprint: String = runCatching {
    val store = KeyStore.getInstance("JKS")
    file(keystorePath).inputStream().use { input ->
      store.load(input, keystorePass.toCharArray())
    }
    val cert = store.getCertificate(keystoreAlias)
    MessageDigest.getInstance("SHA-256")
      .digest(cert.encoded)
      .joinToString("") { byte -> "%02x".format(byte) }
  }.getOrDefault("")

  signingConfigs {
    create("release") {
      storeFile = file(keystorePath)
      storePassword = keystorePass
      keyAlias = keystoreAlias
      keyPassword = keystoreKeyPass

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

  // یادآوریِ روزانه. کارِ زمان‌بندی‌شده باید بعد از خاموش و روشن شدنِ
  // گوشی هم سرِ جایش باشد؛ `WorkManager` همان را تضمین می‌کند.
  implementation("androidx.work:work-runtime-ktx:2.9.1")

  // اثر انگشت. `BiometricPrompt` اکتیویتیِ `FragmentActivity` می‌خواهد،
  // پس `fragment-ktx` هم لازم است — اکتیویتیِ برنامه از آن ارث می‌برد.
  implementation("androidx.biometric:biometric:1.1.0")
  implementation("androidx.fragment:fragment-ktx:1.8.5")

  // ورود با گوگل. `credentials` راهِ امروزیِ اندروید است و
  // `googleid` همان چیزی که توکنِ گوگل را می‌دهد؛ سرور خودش آن توکن را
  // می‌سنجد، پس هیچ رازی داخلِ برنامه نیست.
  implementation("androidx.credentials:credentials:1.3.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
  implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

  testImplementation("junit:junit:4.13.2")
}

/**
 *  شمارهٔ نسخه را چاپ می‌کند تا گردشِ کار همان عددی را روی فایل بنویسد که
 *  واقعاً داخلِ APK است — نه عددی که خودش جداگانه حساب کرده باشد.
 */
tasks.register("printVersionName") {
  doLast { println(android.defaultConfig.versionName) }
}
