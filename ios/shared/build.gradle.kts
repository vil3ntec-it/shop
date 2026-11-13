import java.util.Properties
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

/*
 *  رابطِ کاربریِ مشترکِ آیفون — همان کدِ Compose ِ اندروید، روی iOS با Compose Multiplatform.
 *
 *  ── چرا این شکل (همان الگوی desktop/build.gradle.kts) ───────────────
 *  هیچ صفحه‌ای این‌جا دوباره نوشته نمی‌شود. فایل‌های `android-native/app/src/main/java`
 *  هنگامِ ساخت به `build/generated/ios-src` کپی و برای iOS کامپایل می‌شوند:
 *
 *    ۱) `ios-exclude.txt` — فایل‌هایی که هنوز به JVM/اندروید تکیه دارند (Context،
 *       SharedPreferences، java.io.File، شبکهٔ JDK، چاپ…) و در iOS نیستند. هر سطر دلیل دارد؛
 *       کوچک کردنِ این فهرست کارِ گامِ ۲ِ docs/IOS-PLAN-fa.md است.
 *    ۲) `src/iosMain/kotlin` — shimهای iOS (مثلِ `desktop/src/shim`): نام‌های JVM ای که
 *       کدِ مشترک صدا می‌زند — `java.*` (File، Calendar، URLEncoder…) و `android.*` (Context،
 *       SharedPreferences، Intent…) با همان نام، و `System`/`Math`/`synchronized`/`String.format` در
 *       `ir.vil3ntec.tohid.ios.jvm` — با پیاده‌سازیِ Foundation/UIKit. سرِ هر فایلِ کپی‌شده یک خطِ
 *       import افزوده می‌شود و `@Synchronized` به `synchronized` برمی‌گردد؛ متنِ دیگرِ فایل دست نمی‌خورد.
 *       فایل‌های `*Ios.kt` جایگزینِ همان API اند (مثلِ «replaced» ِ کامپیوتر).
 *    ۳) ریشهٔ کوچکِ آیفون (`ir.vil3ntec.tohid.ios.IosRoot`) فقط composableهای مشترک را
 *       کنارِ هم می‌گذارد؛ `MainViewController()` همان را به SwiftUI می‌دهد (`ios/iosApp`).
 *
 *  ⛔ اندروید و کامپیوتر این ساخت را نمی‌خوانند. هدف‌های iOS فقط با `-Pios=true` (مثلِ core)؛
 *  بی پرچم هیچ هدفی پیکربندی نمی‌شود.
 *  ⛔ نسخه‌های Kotlin/Compose همان `desktop/build.gradle.kts` — با هم عوض می‌شوند.
 *  ──────────────────────────────────────────────────────────────────
 */
plugins {
  kotlin("multiplatform") version "2.2.20"
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
  id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
  id("org.jetbrains.compose") version "1.9.3"
}

val ios = providers.gradleProperty("ios").orNull == "true"

val android = rootDir.resolve("../../android-native")
val androidSrc = android.resolve("app/src/main/java")
val androidProps = Properties().apply {
  android.resolve("gradle.properties").inputStream().use { load(it) }
}
fun prop(name: String): String = (androidProps.getProperty(name) ?: "").trim()
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

/*
 *  فهرستِ بیرون‌مانده: هر سطر یک مسیر زیرِ `ir/vil3ntec/tohid/` (فایل، یا پوشه با `/` ِ پایانی)،
 *  پس از `#` دلیل. هر فایلِ تازه‌ای که در این فهرست نیست خودبه‌خود به iOS می‌رسد — پس
 *  کدِ تازهٔ وابسته به JVM همین‌جا (کارِ `ios-ui-klib`) دیده می‌شود.
 */
val excludeFile = file("ios-exclude.txt")
val excluded: List<String> = excludeFile.readLines(Charsets.UTF_8)
  .map { it.substringBefore('#').trim() }
  .filter { it.isNotEmpty() }
  .map { "ir/vil3ntec/tohid/$it" }

/*
 *  سرِ هر فایلِ کپی‌شده: shimهای iOS و `Dispatchers.IO` (در Kotlin/Native یک extension است و
 *  import می‌خواهد). import ِ ستاره‌دار پس از importهای صریح و همان بسته می‌آید، پس هیچ نامی
 *  که فایل خودش دارد را نمی‌پوشاند.
 */
val injected = "import ir.vil3ntec.tohid.ios.jvm.*\nimport kotlinx.coroutines.IO\n"

/*
 *  `@Synchronized` (قفلِ JVM روی تابع) در Kotlin/Native نیست. همان معنا با `synchronized` ِ shim
 *  (یک قفلِ بازگشتیِ سراسری، ‎ios/jvm/Jvm.kt‎):
 *    `@Synchronized fun f(…) { BODY }`  ⇐  `fun f(…) { synchronized(Unit) { BODY } }`
 *    `@Synchronized fun f(…) = EXPR`    ⇐  `fun f(…) = synchronized(Unit) { EXPR }`
 *  `return` درونِ BODY همان بازگشت از f است (shim ِ inline با قراردادِ EXACTLY_ONCE). متنِ اندروید
 *  دست نمی‌خورد؛ فقط کپیِ iOS. شکلِ ناشناخته ⇐ شکستنِ ساخت، نه کدِ بی‌قفل.
 */
fun unsynchronize(src: String, where: String): String {
  val sb = StringBuilder(src)
  val tag = "@Synchronized"
  var from = 0
  while (true) {
    val at = sb.indexOf(tag, from)
    if (at < 0) break
    if (skipCode(sb, 0, at) != at) { from = at + tag.length; continue }
    sb.delete(at, at + tag.length)
    val funAt = sb.indexOf("fun ", at)
    //  میانِ نشان و `fun` فقط واژهٔ دسترسی/override (`private`، `override`…)
    require(funAt >= 0 && Regex("[\\s\\w]*").matches(sb.substring(at, funAt))) { "$where: @Synchronized بی fun" }
    var i = funAt
    var depth = 0
    while (i < sb.length) {
      val c = sb[i]
      if (c == '(' || c == '<') depth++ else if (c == ')' || (c == '>' && sb[i - 1] != '-')) depth--
      else if (depth == 0 && (c == '{' || c == '=')) break
      i++
    }
    if (sb[i] == '{') {
      val close = codeEnd(sb, i + 1, closing = '}')
      sb.insert(close, "} ")
      sb.insert(i + 1, " synchronized(Unit) {")
    } else {
      val end = codeEnd(sb, i + 1, closing = null)
      sb.insert(end, " }")
      sb.insert(i + 1, " synchronized(Unit) {")
    }
    from = at
  }
  return sb.toString()
}

/** از `start` جلو می‌رود و رشته/نویسه/توضیح را می‌پَرد؛ نخستین جایی که به `stop` برسد یا متن تمام شود */
fun skipCode(t: CharSequence, start: Int, stop: Int): Int {
  var i = start
  while (i < stop) i = step(t, i)
  return i
}

/** یک گامِ پیمایش: اگر در i رشته یا توضیح آغاز شود، پایانش؛ وگرنه i+1 */
fun step(t: CharSequence, i: Int): Int {
  fun at(k: Int, s: String) = t.length >= k + s.length && t.subSequence(k, k + s.length) == s
  return when {
    at(i, "//") -> { var k = i; while (k < t.length && t[k] != '\n') k++; k }
    at(i, "/*") -> { var k = i + 2; var d = 1; while (k < t.length && d > 0) { if (at(k, "/*")) { d++; k += 2 } else if (at(k, "*/")) { d--; k += 2 } else k++ }; k }
    at(i, "\"\"\"") -> { var k = i + 3; while (k < t.length && !at(k, "\"\"\"")) { k = if (at(k, "\${")) codeEnd(t, k + 2, '}') + 1 else k + 1 }; while (at(k + 1, "\"\"\"")) k++; k + 3 }
    t[i] == '"' -> { var k = i + 1; while (k < t.length && t[k] != '"') { k = when { t[k] == '\\' -> k + 2; at(k, "\${") -> codeEnd(t, k + 2, '}') + 1; else -> k + 1 } }; k + 1 }
    t[i] == '\'' -> { var k = i + 1; while (k < t.length && t[k] != '\'') k += if (t[k] == '\\') 2 else 1; k + 1 }
    else -> i + 1
  }
}

/**
 *  پایانِ کد از `start`: با `closing` جای همان بست در عمقِ صفر؛ بی آن (بدنهٔ `=`) پایانِ سطری که
 *  عمقش صفر است و سطرِ بعد با عملگر ادامه نمی‌یابد — پیش از توضیح و فاصلهٔ پایانِ سطر.
 */
fun codeEnd(t: CharSequence, start: Int, closing: Char?): Int {
  var i = start
  var depth = 0
  var lastCode = start
  while (i < t.length) {
    val c = t[i]
    //  `= ⏎ EXPR`: تا نخستین تکهٔ کد، پایانِ سطر پایانِ بدنه نیست
    if (c == '\n' && closing == null && depth == 0 && lastCode > start) {
      val rest = t.subSequence(i + 1, t.length).trimStart()
      val more = listOf(".", "?.", "?:", "&&", "||", "+", "-", "*", "/", "%", "=").any { rest.startsWith(it) && !rest.startsWith("//") && !rest.startsWith("/*") }
      if (!more) return lastCode
    }
    if (c == '(' || c == '{' || c == '[') depth++
    if (c == ')' || c == '}' || c == ']') {
      if (depth == 0 && c == closing) return i
      depth--
    }
    val next = step(t, i)
    val token = t.subSequence(i, next)
    if (!token.startsWith("//") && !token.startsWith("/*") && !token.isBlank()) lastCode = next
    i = next
  }
  require(closing == null) { "پایانِ بدنهٔ @Synchronized پیدا نشد" }
  return lastCode
}

/*
 *  importهای صریحِ تابع‌های فقط-JVM ِ کتابخانه‌های Kotlin (`kotlin.*` که ما در آن بسته چیزی نمی‌گذاریم، و
 *  `encodeToStream` ِ kotlinx.serialization) به همان نام در shim برمی‌گردند — فقط در کپیِ iOS.
 */
val importRewrites = mapOf(
  "kotlin.concurrent.withLock" to "ir.vil3ntec.tohid.ios.jvm.withLock",
  "kotlinx.serialization.json.encodeToStream" to "ir.vil3ntec.tohid.ios.jvm.encodeToStream",
)

val coreJvmSrc = rootDir.resolve("../../core/src/jvmMain/kotlin")
val coreJvm = listOf("ir/vil3ntec/tohid/core/perm/PinCrypto.kt", "ir/vil3ntec/tohid/core/perm/ApproverVault.kt")

/** چند الگوی فقط-JVM که نامِ shim نمی‌گیرند و فقط در کپیِ iOS به همتای shim برمی‌گردند */
val textRewrites = mapOf(
  //  کشِ LRU: زیرردهٔ بی‌نامِ LinkedHashMap ِ نهاییِ Kotlin/Native ⇐ ‎ios/jvm/LruMap.kt‎
  "object : LinkedHashMap<" to "object : ir.vil3ntec.tohid.ios.jvm.LinkedHashMapJvm<",
)

val iosSources by tasks.registering {
  val out = layout.buildDirectory.dir("generated/ios-src")
  inputs.dir(androidSrc)
  inputs.files(coreJvm.map { coreJvmSrc.resolve(it) })
  inputs.file(excludeFile)
  inputs.property("injected", injected)
  inputs.property("importRewrites", importRewrites.toString())
  inputs.property("textRewrites", textRewrites.toString())
  outputs.dir(out)
  doLast {
    val dest = out.get().asFile
    dest.deleteRecursively()
    val used = HashSet<String>()
    var kept = 0
    androidSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
      val rel = f.relativeTo(androidSrc).invariantSeparatorsPath
      val hit = excluded.firstOrNull { if (it.endsWith("/")) rel.startsWith(it) else rel == it }
      if (hit != null) { used += hit; return@forEach }
      val raw = f.readText(Charsets.UTF_8)
      val text = (if ("@Synchronized" in raw) unsynchronize(raw, rel) else raw).let { s ->
        val withImports = importRewrites.entries.fold(s) { acc, (from, to) -> acc.replace("import $from\n", "import $to\n") }
        textRewrites.entries.fold(withImports) { acc, (from, to) -> acc.replace(from, to) }
      }
      val pkg = Regex("(?m)^package [\\w.]+[ \\t]*$").find(text)
      val body = if (pkg == null) text else text.substring(0, pkg.range.last + 1) + "\n" + injected + text.substring(pkg.range.last + 1)
      dest.resolve(rel).apply { parentFile.mkdirs() }.writeText(body, Charsets.UTF_8)
      kept++
    }
    //  دو فایلِ `core/src/jvmMain` (کلیدِ تأییدکننده: PBKDF2، AES-GCM، ECDSA) که core برای iOS ندارد: همان
    //  متن روی shimهای `java.security`/`javax.crypto` ِ iOS — مثلِ بقیهٔ کپی‌ها، دست‌نخورده
    coreJvm.forEach { rel ->
      val f = coreJvmSrc.resolve(rel)
      val text = f.readText(Charsets.UTF_8)
      val pkg = Regex("(?m)^package [\\w.]+[ \\t]*$").find(text)!!
      dest.resolve("core-jvm/$rel").apply { parentFile.mkdirs() }
        .writeText(text.substring(0, pkg.range.last + 1) + "\n" + injected + text.substring(pkg.range.last + 1), Charsets.UTF_8)
      kept++
    }
    excluded.filter { it !in used }.forEach {
      logger.warn("⚠️ سطرِ ios-exclude.txt دیگر فایلی ندارد: ${it.removePrefix("ir/vil3ntec/tohid/")}")
    }
    logger.lifecycle("iOS: $kept فایلِ مشترک کپی شد، ${used.size} سطرِ بیرون‌مانده به کار رفت")
  }
}

/*
 *  `BuildConfig` — همان فیلدهای اندروید و کامپیوتر؛ نشانیِ سرور و شناسهٔ برنامه از
 *  `android-native/gradle.properties` (قاعدهٔ «یک سرور، یک حساب»).
 *  کلیدِ عمومیِ مجوز (`LICENSE_PUBLIC_KEY`) از همان منبعِ کامپیوتر (`desktop/build.gradle.kts`):
 *  `TOHID_LICENSE_PUBLIC_KEY` یا `tohid.licenseKey`؛ با `-Pdev=true` (سرورِ محلی، کلیدِ تازه در هر بار) خالی.
 *  سنجاق‌نشده ⇐ `SyncStore.publicKey` کلید را از NSUserDefaults می‌خواند که ویرایش‌پذیر است و در پشتیبان هست.
 *  `UPDATE_PUBLIC_KEY` خالی: آیفون به‌روزرسانیِ درون‌برنامه‌ای ندارد (قاعدهٔ ۲.۵.۲ ِ اپل). `SIGNING_SHA256`
 *  (اثرِ انگشتِ گواهیِ امضای APK برای `Integrity.isGenuine`) خالی، مثلِ کامپیوتر: آیفون APK ندارد و امضای برنامه را خودِ iOS می‌سنجد.
 */
val dev = (project.findProperty("dev") as String?) == "true"
val licenseKey = env("TOHID_LICENSE_PUBLIC_KEY") ?: prop("tohid.licenseKey")
if (ios && !dev && licenseKey.isBlank()) {
  logger.warn(
    "\n" +
      "──────────────────────────────────────────────────────────────\n" +
      "  هشدار: کلیدِ عمومیِ مجوز در این نسخهٔ آیفون سنجاق نشده است.\n" +
      "  برنامه ناچار کلید را از سرور می‌گیرد و روی گوشی نگه می‌دارد؛\n" +
      "  کسی که به فایل‌های برنامه دست داشته باشد می‌تواند عوضش کند.\n" +
      "  کلید را از GET /api/v1/license/public-key بگیرید و بگذارید در\n" +
      "  android-native/gradle.properties:  tohid.licenseKey=…\n" +
      "  یا متغیرِ محیطِ TOHID_LICENSE_PUBLIC_KEY (GitHub → Settings → Variables)\n" +
      "──────────────────────────────────────────────────────────────"
  )
}

val generateConfig by tasks.registering {
  val out = layout.buildDirectory.dir("generated/ios-config")
  val apiBase = prop("tohid.apiBase")
  val appId = prop("tohid.appId").ifBlank { "shop" }
  val pinnedKey = if (dev) "" else licenseKey
  inputs.property("apiBase", apiBase)
  inputs.property("appId", appId)
  inputs.property("licenseKey", pinnedKey)
  outputs.dir(out)
  doLast {
    val dir = out.get().asFile.resolve("ir/vil3ntec/tohid").apply { mkdirs() }
    fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
    dir.resolve("BuildConfig.kt").writeText(
      """
      |// ساخته‌شده به دستِ ios/shared/build.gradle.kts — دستی ویرایش نکنید.
      |package ir.vil3ntec.tohid
      |
      |object BuildConfig {
      |  const val DEBUG: Boolean = false
      |  const val STORE: String = "appstore"
      |  const val APPLICATION_ID: String = "ir.vil3ntec.tohid"
      |  const val VERSION_NAME: String = "1.0"
      |  const val VERSION_CODE: Int = 100
      |  const val API_BASE: String = ${q(apiBase)}
      |  const val APP_ID: String = ${q(appId)}
      |  const val LICENSE_PUBLIC_KEY: String = ${q(pinnedKey)}
      |  const val UPDATE_PUBLIC_KEY: String = ""
      |  const val SIGNING_SHA256: String = ""
      |  const val PREVIEW: Boolean = false
      |  const val DESKTOP: Boolean = false
      |}
      |""".trimMargin(),
      Charsets.UTF_8,
    )
  }
}

/*
 *  سرآیندِ ObjC ِ چارچوب (`TohidShared.h`) همهٔ API ِ عمومیِ کدِ مشترک را با همان نام‌ها دارد؛ چند ثابتِ Kotlin
 *  هم‌نامِ ماکروهای C‌اند و Clang ِ Xcode پیش از خواندنِ اعلان جایشان را عوض می‌کند (‎`DEBUG`‎ ِ پیکربندیِ Debug،
 *  ‎`NAME_MAX`‎ ِ <limits.h>، ‎`NULL`‎) ⇐ «expected member name» و شکستِ xcodebuild (CI ِ گامِ ۲). نام‌ها عوض نمی‌شوند
 *  (کدِ اندروید دست نمی‌خورد)؛ فقط درونِ همین سرآیند آن ماکروها کنار می‌روند و پس از آن برمی‌گردند
 *  (`#pragma push_macro`/`pop_macro`). فقط ماکروهایی که واقعاً نامِ اعلانی در سرآیندند.
 */
val headerMacros = listOf("DEBUG", "NULL", "NAME_MAX", "PATH_MAX", "MAX_INPUT", "MAX_CANON", "LINE_MAX", "RELEASE", "YES", "NO", "TRUE", "FALSE", "EOF")

fun guardHeaderMacros(framework: File) {
  val header = framework.resolve("Headers/TohidShared.h")
  if (!header.isFile) return
  val text = header.readText(Charsets.UTF_8)
  if ("tohid: macro guard" in text) return
  val hit = headerMacros.filter { Regex("\\b$it\\s+__attribute__\\(\\(swift_name\\(").containsMatchIn(text) }
  if (hit.isEmpty()) return
  val lines = text.lines().toMutableList()
  val at = lines.indexOfLast { it.startsWith("#import ") } + 1
  lines.addAll(at, listOf("", "// tohid: macro guard — ${hit.joinToString()}") + hit.flatMap { listOf("#pragma push_macro(\"$it\")", "#undef $it") })
  lines.addAll(hit.reversed().map { "#pragma pop_macro(\"$it\")" })
  header.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
  logger.lifecycle("iOS: سرآیندِ TohidShared.h — ماکروهای کنارگذاشته: ${hit.joinToString()}")
}

/*
 *  هدفِ jvm فقط برای آزمونِ منطقِ خالصِ `src/commonMain` (حالتِ GCM ِ دفترِ رمزدار) در برابرِ خودِ JDK —
 *  همان بایت‌ها، روی لینوکس، بی Xcode (`jvmTest`). نه برنامه‌ای می‌سازد، نه Compose دارد.
 */
/*
 *  گامِ ۸ (docs/IOS-PLAN-fa.md §۰.۱۳): آنچه `test.kexe` ِ شبیه‌ساز برای کشیدنِ رابطِ برنامه کنارِ خودش می‌خواهد — همان
 *  پرونده‌های مرحلهٔ Resources ِ `ios/iosApp` (قلم‌ها، نشانِ برنامه، بوقِ اسکن) — و Info.plist ِ آزمون.
 */
val testBundleFiles = listOf(
  "font/vazirmatn_regular.ttf", "font/vazirmatn_medium.ttf", "font/vazirmatn_semibold.ttf", "font/vazirmatn_bold.ttf",
  "drawable-xxxhdpi/logo_mark.png", "raw/scan_beep.mp3",
).map { android.resolve("app/src/main/res/$it") }
val testInfoPlist = file("e2e/test-info.plist")
val testEntitlements = file("e2e/test.entitlements")

/*
 *  سرورِ آزمونِ سرتاسری و پوشهٔ عکس‌ها (CI: `ios/e2e/backend.mjs`) ⇐ فرآیندِ آزمون روی شبیه‌ساز. `simctl spawn` فقط
 *  متغیرهای `SIMCTL_CHILD_*` را به فرآیندِ درونِ شبیه‌ساز می‌دهد (بی پیشوند).
 */
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
  listOf("TOHID_E2E_BACKEND", "TOHID_E2E_SHOTS").forEach { k ->
    providers.environmentVariable(k).orNull?.let { environment("SIMCTL_CHILD_$k", it) }
  }
}

composeCompiler {
  targetKotlinPlatforms.set(setOf(KotlinPlatformType.native))
}

kotlin {
  jvm()
  if (ios) {
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
      it.binaries.framework {
        baseName = "TohidShared"
        isStatic = true
        binaryOption("bundleId", "ir.vil3ntec.tohid.TohidShared")
        //  سرآیندِ ObjC بی برخورد با ماکروهای C (پایینِ همین فایل، `guardHeaderMacros`)
        linkTaskProvider.configure { doLast { guardHeaderMacros(outputFile.get()) } }
      }
      //  گامِ ۸ (آزمونِ سرتاسریِ شبیه‌ساز، `src/iosTest/…/e2e`): `test.kexe` هم «بستهٔ برنامه» ای دارد که ResFont و
      //  PainterRes از آن می‌خوانند (`NSBundle.mainBundle` ِ فرآیندِ بی بسته = پوشهٔ خودِ kexe) و Info.plist ِ
      //  کوچکِ خودش (`e2e/test-info.plist`، درونِ بخشِ __info_plist): ATS برای سرورِ آزمونِ http روی 127.0.0.1؛ و اجازهٔ
      //  کلیددان (`e2e/test.entitlements`، بخشِ __entitlements ِ ساختِ شبیه‌ساز) برای توکنِ ورود و کلیدِ دفتر.
      it.binaries.withType<TestExecutable>().configureEach {
        linkerOpts("-sectcreate", "__TEXT", "__info_plist", testInfoPlist.absolutePath)
        linkerOpts("-sectcreate", "__TEXT", "__entitlements", testEntitlements.absolutePath)
        linkTaskProvider.configure {
          inputs.file(testInfoPlist)
          inputs.file(testEntitlements)
          inputs.files(testBundleFiles)
          doLast { testBundleFiles.forEach { f -> f.copyTo(outputDirectory.resolve(f.name), overwrite = true) } }
        }
      }
    }
  }
  sourceSets {
    commonTest.dependencies { implementation(kotlin("test")) }
    if (ios) {
      iosMain {
        kotlin.srcDir(iosSources)
        kotlin.srcDir(generateConfig)
        dependencies {
          implementation("ir.vil3ntec.tohid:core:1.0")
          implementation(compose.runtime)
          implementation(compose.foundation)
          implementation(compose.material)
          implementation(compose.ui)
          implementation(compose.animation)
          //  همان نسخه‌های desktop/build.gradle.kts
          implementation("org.jetbrains.compose.material3:material3:1.9.0")
          implementation(compose.materialIconsExtended)
          implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
          implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
          implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
      }
      //  گامِ ۸: رانندهٔ Compose برای آزمونِ سرتاسری (زدن، نوشتن، عکس؛ `runSkikoComposeUiTest`) — همان نسخهٔ Compose
      iosTest.dependencies { implementation("org.jetbrains.compose.ui:ui-test:1.9.3") }
    }
  }
  compilerOptions {
    //  همان هشدارهای اندروید؛ این‌جا خطا نمی‌شوند (مثلِ کامپیوتر)
    suppressWarnings.set(true)
    optIn.addAll(
      "androidx.compose.material3.ExperimentalMaterial3Api",
      "androidx.compose.foundation.ExperimentalFoundationApi",
      "androidx.compose.foundation.layout.ExperimentalLayoutApi",
      "kotlinx.cinterop.ExperimentalForeignApi",
      "kotlinx.cinterop.BetaInteropApi",
      //  `assert` در Kotlin/Native آزمایشی است؛ در اندروید و کامپیوتر نه
      "kotlin.experimental.ExperimentalNativeApi",
    )
  }
}
