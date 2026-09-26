import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

/*
 *  برنامهٔ کامپیوترِ فروشگاه — همان برنامهٔ اندروید، بومی روی ویندوز و
 *  مک و لینوکس.
 *
 *  ── چرا این شکل ──────────────────────────────────────────────────────
 *  برنامهٔ اندروید سراسر Kotlin و Jetpack Compose است. Compose Desktop
 *  همان Compose است که به‌جای اندروید روی JVM و Skia می‌نشیند: نه
 *  WebView، نه مرورگر، نه HTML. پس **همان فایل‌های** `android-native`
 *  این‌جا کامپایل می‌شوند — هیچ کپی‌ای از منطق نیست و هر تغییری در
 *  برنامهٔ گوشی خودبه‌خود به کامپیوتر هم می‌رسد.
 *
 *  آن‌چه روی کامپیوتر معنا ندارد (دوربین، لرزش، WorkManager، رمزگذاریِ
 *  کیستورِ اندروید، ورود با گوگلِ Credential Manager…) دو راه دارد:
 *
 *    ۱) `src/shim/` — همان نام‌های `android.*` با پیاده‌سازیِ کامپیوتری
 *       (تنظیمات روی دیسک، کارهای دوره‌ای داخلِ همان فرآیند، اعلانِ
 *       سینیِ سیستم، انتخابِ فایل…). فایلِ اندروید دست‌نخورده کامپایل
 *       می‌شود.
 *    ۲) فهرستِ `replaced` پایین — فایل‌هایی که جایگزینِ کامپیوتری دارند،
 *       با **همان** API، در `src/main/kotlin`.
 *
 *  ⛔ نشانیِ سرور و شناسهٔ برنامه از `android-native/gradle.properties`
 *  خوانده می‌شوند — جای سومی برایشان ساخته نشد (قاعدهٔ «یک سرور، یک
 *  حساب»).
 *  ──────────────────────────────────────────────────────────────────
 */
plugins {
  kotlin("jvm") version "2.2.20"
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
  id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
  id("org.jetbrains.compose") version "1.9.3"
}

val android = rootDir.resolve("../android-native")
val androidSrc = android.resolve("app/src/main/java")
val androidRes = android.resolve("app/src/main/res")

val androidProps = Properties().apply {
  android.resolve("gradle.properties").inputStream().use { load(it) }
}

fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
fun prop(name: String): String = (androidProps.getProperty(name) ?: "").trim()

/*
 *  شمارهٔ نسخه: MAJOR.MINOR.BUILD — همان قالبی که نصابِ ویندوز (MSI)
 *  و مک می‌پذیرند. BUILD از شمارهٔ ساختِ CI می‌آید.
 */
val versionBase = "1.0"
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0
val appVersion = "$versionBase.$buildNumber"
version = appVersion

val apiBase = env("TOHID_API_BASE") ?: prop("tohid.apiBase")
val appId = env("TOHID_APP_ID") ?: prop("tohid.appId").ifBlank { "shop" }
val licenseKey = env("TOHID_LICENSE_PUBLIC_KEY") ?: prop("tohid.licenseKey")

/*
 *  فایل‌هایی از اندروید که این‌جا جایگزین دارند. جایگزین‌ها در
 *  `src/main/kotlin` با پسوندِ `Desk` هستند تا الگوی حذف آن‌ها را نگیرد.
 */
val replaced = listOf(
  "MainActivity.kt",
  "core/net/TokenStore.kt",
  "data/Fingerprint.kt",
  "data/GoogleSignIn.kt",
  "data/Migration.kt",
  "scan/BarcodeScanner.kt",
  "sync/Integrity.kt",
  "update/Updater.kt",
).map { "ir/vil3ntec/tohid/$it" }

val generated = layout.buildDirectory.dir("generated/tohid")

/*
 *  `BuildConfig` و `R` — همان دو چیزی که گریدلِ اندروید می‌سازد.
 */
val generateConfig by tasks.registering {
  val out = generated
  inputs.property("apiBase", apiBase)
  inputs.property("appId", appId)
  inputs.property("version", appVersion)
  inputs.property("licenseKey", licenseKey)
  outputs.dir(out)
  doLast {
    val dir = out.get().asFile.resolve("ir/vil3ntec/tohid").apply { mkdirs() }
    fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
    dir.resolve("BuildConfig.kt").writeText(
      """
      |// ساخته‌شده به دستِ build.gradle.kts — دستی ویرایش نکنید.
      |package ir.vil3ntec.tohid
      |
      |object BuildConfig {
      |  const val DEBUG: Boolean = false
      |  const val APPLICATION_ID: String = "ir.vil3ntec.tohid"
      |  const val VERSION_NAME: String = ${q(appVersion)}
      |  const val VERSION_CODE: Int = ${100 + buildNumber}
      |  const val API_BASE: String = ${q(apiBase)}
      |  const val APP_ID: String = ${q(appId)}
      |  const val LICENSE_PUBLIC_KEY: String = ${q(licenseKey)}
      |  const val SIGNING_SHA256: String = ""
      |  /** این نسخه روی کامپیوتر است — برای جاهایی که باید بدانند. */
      |  const val DESKTOP: Boolean = true
      |}
      |""".trimMargin()
    )
  }
}

/*
 *  نوشته‌های «گوشی» و «دوربین» روی کامپیوتر.
 *
 *  کدِ اندروید دست نمی‌خورد؛ فایل‌هایش هنگامِ ساخت از این جدول می‌گذرند و
 *  به `build/generated/android-src` می‌روند. ترتیب مهم است: عبارتِ بلندتر
 *  اول، تا تکهٔ کوتاه‌ترش آن را نشکند.
 *
 *  ⚠️ عبارتی که دیگر در کدِ اندروید پیدا نشود **هشدار** می‌دهد — یعنی
 *  نوشتهٔ برنامهٔ گوشی عوض شده و این جدول باید با آن برود.
 */
val phrases: List<Pair<String, String>> = listOf(
  "در حال آماده‌سازی دوربین…" to "در حال آماده‌سازی بارکدخوان…",
  "کالا را جلوی دوربین بگیرید تا خودکار به سبد اضافه شود" to "کالا را با بارکدخوان اسکن کنید تا خودکار به سبد اضافه شود",
  "بارکد را جلوی دوربین بگیرید — با اولین خواندن، کادر پر می‌شود" to "بارکد را با بارکدخوان اسکن کنید — با اولین خواندن، کادر پر می‌شود",
  "دوربین بعد از چند دقیقه بی‌کاری خاموش شد" to "بارکدخوان بعد از چند دقیقه بی‌کاری خاموش شد",
  "دوربین متوقف شد" to "بارکدخوان متوقف شد",
  "توقف دوربین" to "توقف بارکدخوان",
  "شروع دوربین" to "شروع بارکدخوان",
  "دوربین خاموش است" to "بارکدخوان خاموش است",
  "روشن کردن دوربین" to "روشن کردن بارکدخوان",
  "دوربین آماده است — بارکدخوان کار می‌کند" to "بارکدخوان آماده است",
  "دوربین بارکدخوان" to "بارکدخوان",
  "برنامه برای اسکن بارکد محصولات به دوربین نیاز دارد." to
    "بارکدخوانِ USB یا بلوتوث را به کامپیوتر وصل کنید؛ مثلِ صفحه‌کلید کار می‌کند و نصب لازم ندارد. بی بارکدخوان، کد را در کادرِ فروش بنویسید.",
  "آزمایش دوربین" to "آزمایش بارکدخوان",
  "title = \"دوربین\"," to "title = \"عکسِ آماده\",",
  "گرفتنِ عکسِ تازه" to "عکسی که با گوشی یا وب‌کم گرفته‌اید",
  "انتخاب از عکس‌های گوشی" to "انتخاب از پوشه‌های کامپیوتر",
  "\"سیم\" to \"usb\"" to "\"چاپگر سیستم\" to \"usb\"",
  "چاپگرِ سیمی پیدا نشد. کابل را وصل کنید و همین برگه را دوباره باز کنید." to
    "چاپگری در سیستم نصب نیست. چاپگر را در تنظیماتِ ویندوز یا مک اضافه کنید و همین برگه را دوباره باز کنید.",
  "اولین بار اندروید اجازه می‌خواهد؛ «همیشه» را بزنید تا هر بار نپرسد." to
    "چاپگرهای نصب‌شده در سیستم؛ چاپگرِ حرارتیِ USB و بلوتوثیِ جفت‌شده همین‌جا دیده می‌شوند.",
  "چاپگری پیدا نشد. اول چاپگر را در تنظیمات بلوتوث گوشی جفت کنید، بعد اینجا برگردید." to
    "روی کامپیوتر، چاپگرِ بلوتوثی را در تنظیماتِ سیستم جفت و نصب کنید؛ از برگهٔ «چاپگر سیستم» دیده می‌شود.",
  "اگر گوشی «کاهش حرکت» یا حالت ذخیرهٔ باتری روشن باشد" to "اگر سیستم «کاهش حرکت» را روشن کرده باشد",
  "مثل تنظیم گوشی" to "مثل تنظیم سیستم",
  "مثل گوشی" to "مثل سیستم",
  "برنامهٔ اندروید" to "برنامهٔ کامپیوتر",
  "نتِ گوشی" to "اینترنتِ کامپیوتر",
  "گوشی گم شد؟" to "دستگاهی گم شد؟",
  "هر کسی گوشی را برمی‌داشت" to "هر کسی پشتِ کامپیوتر می‌نشست",
  "روی گوشی و روی سایت" to "روی گوشی، کامپیوتر و سایت",
  "روی هر گوشی" to "روی هر دستگاه",
  "گوشی دوم" to "دستگاهِ دوم",
  "گوشیِ دیگر" to "دستگاهِ دیگر",
  "تنظیمات گوشی" to "تنظیمات سیستم",
  "همین گوشی" to "همین کامپیوتر",
  "خودِ گوشی" to "خودِ کامپیوتر",
  "روی گوشی" to "روی کامپیوتر",
)

val androidSources by tasks.registering {
  val out = layout.buildDirectory.dir("generated/android-src")
  inputs.dir(androidSrc)
  inputs.property("phrases", phrases.toString())
  inputs.property("replaced", replaced)
  outputs.dir(out)
  doLast {
    val dest = out.get().asFile
    dest.deleteRecursively()
    val used = HashSet<String>()
    androidSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
      val rel = f.relativeTo(androidSrc).invariantSeparatorsPath
      if (rel in replaced) return@forEach
      var text = f.readText(Charsets.UTF_8)
      for ((from, to) in phrases) if (text.contains(from)) { used += from; text = text.replace(from, to) }
      dest.resolve(rel).apply { parentFile.mkdirs() }.writeText(text, Charsets.UTF_8)
    }
    phrases.map { it.first }.filter { it !in used }.forEach {
      logger.warn("⚠️ عبارتِ جدولِ کامپیوتر دیگر در کدِ اندروید نیست: $it")
    }
  }
}

sourceSets {
  main {
    kotlin.srcDir(androidSources)
    kotlin.srcDir("src/shim/kotlin")
    kotlin.srcDir(generateConfig)
    resources.srcDir(layout.buildDirectory.dir("generated/res"))
  }
  test {
    kotlin.srcDir(android.resolve("app/src/test/java"))
  }
}

/*
 *  قلم‌ها، نشان و صدای بوق — از همان `res`ِ اندروید، بی کپی در مخزن.
 */
val copyRes by tasks.registering(Sync::class) {
  from(androidRes.resolve("font")) { into("font") }
  from(androidRes.resolve("raw")) { into("raw") }
  from(androidRes.resolve("drawable-xxxhdpi")) { into("drawable") }
  into(layout.buildDirectory.dir("generated/res/tohid"))
}
tasks.named("processResources") { dependsOn(copyRes) }

dependencies {
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation(compose.materialIconsExtended)
  //  `LifecycleEventEffect` و `LocalLifecycleOwner` — همان API اندروید
  implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
  //  همان کلاینتِ سوکتِ زندهٔ اندروید (`LiveSocket`)
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  //  `org.json` روی اندروید درونِ سیستم است؛ این‌جا باید آورده شود
  implementation("org.json:json:20250517")

  testImplementation("junit:junit:4.13.2")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    //  همان هشدارهای اندروید؛ این‌جا خطا نمی‌شوند
    suppressWarnings.set(true)
    optIn.addAll(
      "androidx.compose.material3.ExperimentalMaterial3Api",
      "androidx.compose.foundation.ExperimentalFoundationApi",
      "androidx.compose.foundation.layout.ExperimentalLayoutApi",
    )
  }
}

compose.desktop {
  application {
    mainClass = "ir.vil3ntec.tohid.desktop.MainKt"
    jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    nativeDistributions {
      targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
      packageName = "Tohid"
      packageVersion = appVersion
      description = "توحید — دفترِ فروشگاه"
      vendor = "VIL3NTEC"
      copyright = "© VIL3NTEC"
      //  ماژول‌های JDK که برنامه لازم دارد (شبکه، چاپ، رمزنگاری، SQL نه)
      modules("java.net.http", "java.desktop", "java.prefs", "jdk.crypto.ec", "jdk.unsupported", "java.naming")
      windows {
        menu = true
        menuGroup = "Tohid"
        shortcut = true
        dirChooser = true
        perUserInstall = true
        //  ⛔ هرگز عوض نشود: ویندوز با همین شناسه می‌فهمد نسخهٔ تازه
        //  روی همان برنامه نصب می‌شود، نه کنارش.
        upgradeUuid = "6f1e0c52-7a3b-4d8e-9c1f-3b2a5d7e9f10"
        iconFile.set(project.file("icons/tohid.ico"))
      }
      macOS {
        bundleID = "ir.vil3ntec.tohid.desktop"
        iconFile.set(project.file("icons/tohid.icns"))
      }
      linux {
        iconFile.set(project.file("icons/tohid.png"))
        shortcut = true
      }
    }
  }
}

tasks.register("printVersionName") {
  doLast { println(appVersion) }
}

/**
 *  همهٔ وابستگی‌ها را پایین می‌آورد — CI پیش از ساخت صدایش می‌زند تا
 *  بستهٔ «ساختِ آفلاین» (`androidx.*` از مخزنِ گوگل) کامل باشد، حتی اگر
 *  کامپایل جایی بشکند.
 */
tasks.register("resolveDeps") {
  doLast {
    listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
      .forEach { configurations.getByName(it).resolve() }
  }
}

/** برای اجرای مستقیم با `java -cp` (سنجشِ چشمی در CI) */
tasks.register("printRuntimeClasspath") {
  dependsOn("classes")
  doLast { println((sourceSets.main.get().runtimeClasspath).asPath) }
}
