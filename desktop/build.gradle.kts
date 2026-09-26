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
  "print/Receipt.kt",
  "print/ReportPrint.kt",
  "scan/BarcodeScanner.kt",
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

sourceSets {
  main {
    kotlin.srcDir(androidSrc)
    kotlin.srcDir("src/shim/kotlin")
    kotlin.srcDir(generateConfig)
    kotlin.exclude(replaced)
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
    jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dsun.java2d.uiScale.enabled=true")
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
