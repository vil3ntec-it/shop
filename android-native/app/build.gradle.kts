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

  /*
   *  کدام فروشگاه — `-Pstore=bazaar` نسخهٔ کافه‌بازار را می‌سازد.
   *
   *  ⛔ قانونِ بازار: «برنامه‌ای که از بازار گرفته شده فقط از بازار به‌روز
   *  می‌شود» و لینکِ پرداختِ بیرونی ممنوع است. پس نسخهٔ بازار:
   *    • اجازهٔ `REQUEST_INSTALL_PACKAGES` ندارد (مانیفستِ ساخته‌شده بی آن)،
   *    • به‌روزرسانیِ داخلی و دکمه‌هایش را ندارد (`BuildConfig.STORE`)،
   *    • دکمهٔ خرید با واتساپ را ندارد.
   *  بقیه همان برنامه است — همان بسته، همان حساب، همان دفتر.
   *  بی این پرچم ⇒ «direct»: همان APKِ سایت و گیت‌هاب، مثلِ همیشه.
   */
  val store = (project.findProperty("store") as String?)?.trim()?.ifBlank { null } ?: "direct"
  if (store == "bazaar") {
    val source = file("src/main/AndroidManifest.xml")
    val stripped = layout.buildDirectory.file("store-manifest/AndroidManifest.xml").get().asFile
    stripped.parentFile.mkdirs()
    stripped.writeText(
      source.readText().lines()
        .filterNot { it.contains("android.permission.REQUEST_INSTALL_PACKAGES") }
        .joinToString("\n")
    )
    sourceSets["main"].manifest.srcFile(stripped)
  }

  //  متغیرِ تنظیم‌نشده در GitHub Actions **رشتهٔ خالی** می‌شود، نه
  //  ناموجود. پس `?:` تنها کافی نیست و خالی هم باید «نبود» حساب شود —
  //  وگرنه رمزِ خالی به کلید داده می‌شود و امضا می‌شکند.
  fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

  /*
   *  نشانیِ سرور — یک بار، در خودِ مخزن.
   *
   *  تا امروز فقط از متغیرِ محیطی می‌آمد، یعنی از یک تنظیمِ CI. اگر آن
   *  تنظیم نبود — یا کسی روی رایانه‌ی خودش بیلد می‌گرفت — نسخه بدونِ
   *  سرور ساخته می‌شد و هر کاربری باید نشانی را دستی تایپ می‌کرد.
   *  برنامه‌ی حرفه‌ای این کار را نمی‌کند: سرور باید از لحظه‌ی نصب
   *  سرِ جایش باشد.
   *
   *  حالا مقدار در `gradle.properties` است و در مخزن می‌ماند، پس هر
   *  بیلدی — محلی یا CI — آن را دارد. متغیرِ محیطی هنوز کار می‌کند و
   *  بالاتر می‌نشیند، برای وقتی که بخواهید یک نسخه‌ی خاص را به سرورِ
   *  دیگری ببرید بی‌آنکه چیزی در مخزن عوض شود.
   */
  fun apiBase(envName: String, propName: String): String =
    env(envName)
      ?: (project.findProperty(propName) as String?)?.takeIf { it.isNotBlank() }
      ?: ""

  /*
   *  شناسه‌ی این برنامه نزدِ سرورِ مرکزی.
   *
   *  سرور یکی است و چند برنامه از آن احراز هویت می‌گیرند؛ هر درخواست
   *  می‌گوید مالِ کدام است. همان راهِ نشانیِ سرور را می‌رود: متغیرِ
   *  محیطی بالاتر از `gradle.properties` می‌نشیند.
   *
   *  اگر هیچ‌کدام نبود `shop` می‌ماند — همان چیزی که این برنامه از
   *  روزِ اول بوده. پس بیلدِ کسی که `gradle.properties`ِ قدیمی دارد
   *  هم درست کار می‌کند.
   */
  val appId: String =
    env("TOHID_APP_ID")
      ?: (project.findProperty("tohid.appId") as String?)?.takeIf { it.isNotBlank() }
      ?: "shop"

  /*
   *  کلیدِ عمومیِ مجوز — همان که سرور با جفتِ خصوصی‌اش مجوز را امضا می‌کند.
   *
   *  ── چرا باید داخلِ خودِ برنامه باشد ───────────────────────────────
   *  تا دیروز برنامه این کلید را از سرور می‌گرفت و روی گوشی ذخیره
   *  می‌کرد. یعنی هر کسی که به فایل‌های برنامه دست داشت، می‌توانست
   *  کلیدِ خودش را بگذارد و مجوزی که خودش امضا کرده بود جای مجوزِ
   *  واقعی بنشیند — اشتراکِ همیشگی، بی هیچ پرداختی.
   *
   *  کلیدِ **عمومی** مخفی نیست و پنهان‌کردنش هم لازم نیست؛ مهم این است
   *  که عوض نشود. پس داخلِ نسخه می‌نشیند و کلیدِ روی گوشی فقط وقتی
   *  خوانده می‌شود که اینجا خالی باشد (نسخه‌ی خودی).
   *
   *  مقدارش را از `GET /api/v1/license/public-key` بگیرید و در
   *  gradle.properties بگذارید:  tohid.licenseKey=MFkwEwYHKoZI…
   *  ──────────────────────────────────────────────────────────────────
   */
  fun licenseKey(): String =
    env("TOHID_LICENSE_PUBLIC_KEY")
      ?: (project.findProperty("tohid.licenseKey") as String?)?.takeIf { it.isNotBlank() }
      ?: ""

  /*
   *  کلیدِ عمومیِ امضای به‌روزرسانی (P-256، base64 از SPKI) — شرح سرِ
   *  `update/ReleaseSignature.kt`. اگر خالی باشد برنامه فقط جمعِ کنترلی را
   *  می‌سنجد؛ اگر پر باشد، نصبیِ بی امضای همین کلید نصب نمی‌شود.
   */
  fun updateKey(): String =
    env("TOHID_UPDATE_PUBLIC_KEY")
      ?: (project.findProperty("tohid.updateKey") as String?)?.takeIf { it.isNotBlank() }
      ?: ""

  defaultConfig {
    // همان بستهٔ نسخهٔ قبلی: نصب می‌شود *روی* آن، و داده‌های قدیمی
    // سرِ جایشان می‌مانند تا وارد شوند.
    applicationId = "ir.vil3ntec.tohid"
    minSdk = 24
    targetSdk = 35
    versionCode = 100 + buildNumber
    versionName = "$versionBase.$buildNumber"
    buildConfigField("String", "STORE", "\"$store\"")
    //  نسخهٔ پیش‌نمایش (buildTypeِ `preview` در پایین) — در debug و release همیشه false
    buildConfigField("boolean", "PREVIEW", "false")

    /*
     *  نشانیِ سرور، در زمانِ ساخت — نه در رابط کاربری.
     *
     *  تا امروز یک کادر در تنظیمات بود و هر کسی که برنامه را باز می‌کرد
     *  نشانیِ سرور را می‌دید و می‌توانست عوضش کند.
     *
     *  این نشانی **راز نیست**: هر کسی که برنامه را باز کند به هر حال با
     *  همین دامنه حرف می‌زند و از روی ترافیک دیده می‌شود. پس در گیت‌هاب
     *  هم `variable` است نه `secret`. آنچه راز است — رمزِ دیتابیس، کلیدِ
     *  پیامک، توکنِ واتساپ، رازِ مدیر — هیچ‌کدام هرگز به اینجا نمی‌آیند.
     *
     *  چیزی که اینجا می‌نشیند باید **دامنهٔ عمومیِ HTTPS** باشد، نه IP و
     *  پورتِ داخلیِ سرور: `http://192.168.1.10:8080` نشانیِ رایانهٔ خودتان
     *  است و از بیرونِ خانه به آن نمی‌رسد.
     */
    resourceConfigurations += listOf("fa", "en")

    ndk {
      // فقط پردازندهٔ گوشی‌های واقعی. نسخه‌های x86 فقط به دردِ
      // شبیه‌سازِ رایانه می‌خورند و ۱۲ مگابایت الکی به فایلِ نصبی
      // اضافه می‌کنند — یعنی همان‌قدر اینترنت، هر بار.
      abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
  }

  /*
   *  کلیدِ امضا — فقط از محیط (secretِ CI یا متغیرهای خودِ سازنده)؛ هیچ پیش‌فرضی در مخزن نیست.
   *
   *  ⛔ SEC-ART-05: تا ۱۴۰۵/۰۸/۱۰ فایلِ کلید و رمزش هر دو در مخزن بودند و مخزن **عمومی** است — هر
   *  کسی می‌توانست نسخه‌ای بسازد که گوشی‌ها «همان برنامه» بدانند (رخداد؛ KEYSTORE.md). دیگر هیچ برگشتی
   *  به فایلی در مخزن یا رمزِ پیش‌فرض نیست: ساختِ release/preview بی این متغیرها شکست می‌خورد
   *  (پایین، `packageRelease`). debug و آزمون‌های واحد بی کلید کار می‌کنند.
   */
  val keystorePath: String? = env("TOHID_KEYSTORE")
  val keystorePass: String? = env("TOHID_KEYSTORE_PASSWORD")
  val keystoreAlias: String? = env("TOHID_KEY_ALIAS")
  val keystoreKeyPass: String? = env("TOHID_KEY_PASSWORD") ?: keystorePass
  val releaseSigning: Boolean = keystorePath != null && keystorePass != null && keystoreAlias != null &&
    file(keystorePath).isFile

  /*
   *  اثرِ انگشتِ گواهی، در زمانِ ساخت خوانده می‌شود.
   *
   *  برنامه هنگامِ اجرا امضای خودش را با همین می‌سنجد؛ نسخهٔ دست‌کاری‌شده
   *  اجرا می‌شود ولی اشتراک نمی‌گیرد. ⛔ اگر کلید هست ولی خوانده نشد، ساخت می‌شکند: «خالی» یعنی
   *  `Integrity.isGenuine()` همیشه true — همان بازِ بی‌صدایی که نباید باشد. بی کلید (debug) خالی است.
   */
  val signingFingerprint: String = if (!releaseSigning) "" else {
    val store = KeyStore.getInstance("JKS") // keystore.type.compat ⇒ PKCS12 هم خوانده می‌شود
    file(keystorePath!!).inputStream().use { input -> store.load(input, keystorePass!!.toCharArray()) }
    val cert = store.getCertificate(keystoreAlias)
      ?: throw GradleException("TOHID_KEY_ALIAS در انبارِ کلیدِ TOHID_KEYSTORE نیست")
    MessageDigest.getInstance("SHA-256")
      .digest(cert.encoded)
      .joinToString("") { byte -> "%02x".format(byte) }
  }

  signingConfigs {
    create("release") {
      if (releaseSigning) {
        storeFile = file(keystorePath!!)
        storePassword = keystorePass
        keyAlias = keystoreAlias
        keyPassword = keystoreKeyPass
      }

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

  //  ⛔ SEC-ART-05: بسته‌بندیِ release/preview بی کلیدِ امضا شکست می‌خورد — نه APKِ بی‌امضا، نه کلیدِ دیگر
  val missingSigning = !releaseSigning
  project.tasks.matching { it.name.matches(Regex("(package|bundle)(Release|Preview)")) }.configureEach {
    doFirst {
      if (missingSigning) {
        throw GradleException(
          "⛔ کلیدِ امضا نیست: TOHID_KEYSTORE (فایل)، TOHID_KEYSTORE_PASSWORD و TOHID_KEY_ALIAS را بدهید " +
            "(در CI از secretها؛ android-native/KEYSTORE.md). کلیدی در مخزن نیست و نباید باشد."
        )
      }
    }
  }

  buildTypes {
    debug {
      // در نسخهٔ آزمایشی بررسیِ امضا خاموش است، وگرنه هنگامِ توسعه
      // اشتراک هیچ‌وقت باز نمی‌شود
      buildConfigField("String", "SIGNING_SHA256", "\"\"")
      //  نسخه‌ی خودی: نشانی‌ی رایانه‌ی خودتان، و اگر خالی بود کادرِ
      //  نشانی در برنامه پیدا می‌شود تا دستی بزنید
      buildConfigField("String", "API_BASE", "\"${apiBase("TOHID_API_BASE_DEV", "tohid.apiBase.dev")}\"")
      buildConfigField("String", "APP_ID", "\"$appId\"")
      //  نسخه‌ی خودی کلیدِ سنجاق‌شده ندارد: سرورِ محلی هر بار کلیدِ تازه
      //  می‌سازد و سنجاق‌کردنش یعنی هیچ مجوزی قبول نشود
      buildConfigField("String", "LICENSE_PUBLIC_KEY", "\"\"")
      buildConfigField("String", "UPDATE_PUBLIC_KEY", "\"\"")
    }
    release {
      signingConfig = signingConfigs.getByName("release")
      buildConfigField("String", "SIGNING_SHA256", "\"$signingFingerprint\"")
      /*
       *  نسخه‌ی دستِ کاربر: فقط دامنه‌ی عمومی.
       *
       *  کادرِ «آدرس سرور» از برنامه برداشته شده، پس این تنها راهِ
       *  رسیدنِ نشانی به برنامه است. اگر خالی بماند، نسخه‌ای ساخته
       *  می‌شود که حساب و همگام‌سازی در آن کار نمی‌کند — بقیه‌ی برنامه
       *  آفلاین کار می‌کند، ولی این را باید **دانسته** انتخاب کرد، نه
       *  با فراموش کردنِ یک متغیر. پس بلند هشدار داده می‌شود.
       */
      val releaseBase = apiBase("TOHID_API_BASE", "tohid.apiBase")
      if (releaseBase.isBlank()) {
        logger.warn(
          "\n" +
            "──────────────────────────────────────────────────────────────\n" +
            "  هشدار: این نسخه به هیچ سروری بسته نشده است.\n" +
            "  حساب، اشتراک و همگام‌سازی در آن کار نمی‌کنند.\n" +
            "  دامنه را در android-native/gradle.properties بگذارید:\n" +
            "      tohid.apiBase=https://api.example.com\n" +
            "  یا در GitHub → Settings → Variables متغیرِ TOHID_API_BASE.\n" +
            "──────────────────────────────────────────────────────────────"
        )
      }
      buildConfigField("String", "API_BASE", "\"$releaseBase\"")
      buildConfigField("String", "APP_ID", "\"$appId\"")

      val pinnedKey = licenseKey()
      if (pinnedKey.isBlank() && releaseBase.isNotBlank()) {
        logger.warn(
          "\n" +
            "──────────────────────────────────────────────────────────────\n" +
            "  هشدار: کلیدِ عمومیِ مجوز در این نسخه سنجاق نشده است.\n" +
            "  برنامه ناچار کلید را از سرور می‌گیرد و روی گوشی نگه می‌دارد؛\n" +
            "  کسی که به فایل‌های برنامه دست داشته باشد می‌تواند عوضش کند.\n" +
            "  کلید را از GET /api/v1/license/public-key بگیرید و بگذارید در\n" +
            "  android-native/gradle.properties:  tohid.licenseKey=…\n" +
            "  یا در GitHub → Settings → Variables: TOHID_LICENSE_PUBLIC_KEY\n" +
            "──────────────────────────────────────────────────────────────"
        )
      }
      buildConfigField("String", "LICENSE_PUBLIC_KEY", "\"$pinnedKey\"")
      buildConfigField("String", "UPDATE_PUBLIC_KEY", "\"${updateKey()}\"")

      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    /*
     *  نسخهٔ پیش‌نمایش — `assemblePreview`، فقط از `tohid-native-preview.yml`.
     *
     *  همان release (کلید، سرور، R8، کلیدهای سنجاق‌شده)، ولی بستهٔ جدا
     *  (`ir.vil3ntec.tohid.preview`): کنارِ برنامهٔ اصلی نصب می‌شود و به داده‌اش
     *  دست نمی‌زند. versionCode همان «۱۰۰ + buildNumber» است و buildNumber را
     *  ورک‌فلوِ پیش‌نمایش از شمارهٔ اجرای خودش می‌دهد؛ چون بسته جداست، با
     *  versionCodeِ انتشار هیچ ربطی ندارد. نام: `src/preview/res/values/strings.xml`.
     *  به‌روزرسانی فقط از برچسبِ `tohid-native-preview` (`Updater.fromPreview`).
     */
    create("preview") {
      initWith(getByName("release"))
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-preview"
      signingConfig = signingConfigs.getByName("release")
      buildConfigField("boolean", "PREVIEW", "true")
      matchingFallbacks += listOf("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures { compose = true; buildConfig = true }
}

/*
 *  همان `kotlinOptions { jvmTarget = "17" }`ِ پیشین — Kotlin 2.2 آن بلوک را
 *  کنار گذاشته و جایش `compilerOptions` است (همان شکلِ `desktop/build.gradle.kts`).
 */
kotlin {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
  //  قدم صفرِ ۳.۳: همان Compose که کامپیوتر دارد. Compose Multiplatform 1.9.3
  //  (`desktop/build.gradle.kts`) روی اندروید به androidx.compose 1.9.4 می‌رسد و
  //  material3ِ 1.9.0اش به androidx material3 1.4.0؛ BOMِ 2025.10.01 دقیقاً همین‌هاست.
  //  نسخه را فقط هم‌پای `org.jetbrains.compose`ِ کامپیوتر عوض کنید.
  val compose = platform("androidx.compose:compose-bom:2025.10.01")
  implementation(compose)
  androidTestImplementation(compose)

  //  منطقِ خالصِ مشترک با کامپیوتر — buildِ `../core` (includeBuild در settings.gradle.kts)
  implementation("ir.vil3ntec.tohid:core:1.0")

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

  /*
   *  سوکتِ زندهٔ Sync v1 — تنها وابستگیِ تازهٔ این کار.
   *
   *  ── چرا ─────────────────────────────────────────────────────────
   *  اندروید کلاینتِ WebSocket ندارد و `HttpURLConnection` — که کلِ
   *  لایهٔ شبکهٔ این برنامه روی آن است — نمی‌تواند ارتقا به WebSocket
   *  بدهد. دو راه بود: نوشتنِ RFC 6455 با دست روی `SSLSocket`، یا
   *  همین. دستی‌اش حدود صد و پنجاه خطِ ظریف است (ماسک، قطعه‌بندی،
   *  ping/pong، بستنِ تمیز) که یک اشتباهش سوکتی می‌سازد که «گاهی» کار
   *  می‌کند — و بندِ ۲۱٫۳ پرامپت هم صریح همین کتابخانه را نام برده.
   *
   *  ⚠️ **فقط برای سوکت است.** هیچ مسیرِ HTTPی به آن نرفت؛
   *  `HttpEngine` همان است که بود.
   */
  implementation("com.squareup.okhttp3:okhttp:4.12.0")

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
  //  نگهداریِ رمزشدهٔ توکن‌ها. توکنِ دسترسی یعنی «هرکس این را دارد،
  //  همان کاربر است»؛ کنارِ بقیهٔ تنظیمات در حافظهٔ ساده جایش نبود.
  //  برنامهٔ مدیریت از روزِ اول همین را داشت.
  implementation("androidx.security:security-crypto:1.0.0")

  implementation("androidx.credentials:credentials:1.3.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
  implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

  testImplementation("junit:junit:4.13.2")
  //  `org.json`ِ اندروید در آزمونِ واحد فقط پوسته است («not mocked»)؛
  //  `ReleaseFeedTest` پاسخِ انتشارها را واقعاً می‌خواند. همان نسخهٔ کامپیوتر.
  testImplementation("org.json:json:20250517")
}

/**
 *  شمارهٔ نسخه را چاپ می‌کند تا گردشِ کار همان عددی را روی فایل بنویسد که
 *  واقعاً داخلِ APK است — نه عددی که خودش جداگانه حساب کرده باشد.
 */
tasks.register("printVersionName") {
  doLast { println(android.defaultConfig.versionName) }
}
