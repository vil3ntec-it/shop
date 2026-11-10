import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 *  core — منطقِ خالص، بی UI، بی شبکه، بی android.* (بخشِ ۳.۳ِ مشخصات).
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • `src/commonMain` — Kotlin خالص (Money، Qty، Hlc، تقویم‌ها، Ulid، موتورها…).
 *    مرزِ سکو (تصادفیِ امن، SHA-256، ساعت، قفل) فقط `core/platform/Platform.kt`
 *    است: actual ِ JVM در `jvmMain` همان JDK ِ پیشین، actual ِ iOS در `iosMain`.
 *  • `src/jvmMain`    — آنچه هنوز به JDK تکیه دارد: `PinCrypto` و `ApproverVault`
 *    (AES-GCM و ECDSA P-256 روی iOS API ِ عمومیِ C ندارند؛ docs/IOS-PLAN-fa.md).
 *    اندروید و کامپیوتر هر دو همین هدفِ jvm را می‌گیرند.
 *  • `src/commonTest` — بردارهای مرجعِ فایل‌های commonMain، روی JVM و شبیه‌سازِ iOS.
 *  • نامِ بسته‌ها همان است که در `android-native` بود؛ جابه‌جایی به این‌جا
 *    هیچ import یا رفتاری را عوض نمی‌کند.
 *  • نسخهٔ Kotlin همان `android-native/build.gradle.kts` و
 *    `desktop/build.gradle.kts` است — سه‌تا با هم عوض می‌شوند.
 *  • آزمون‌ها فعلاً همان‌جا که بودند (`android-native/app/src/test`) می‌مانند و
 *    در هر دو برنامه روی همین کتابخانه اجرا می‌شوند؛ انتقالشان به
 *    `commonTest` قدمِ ۱ِ مسیرِ انتقال است. آزمونِ کدِ تازهٔ commonMain از همین
 *    حالا در `src/commonTest` است (`:core:jvmTest`؛ CI: «توحید بومی»).
 *  ──────────────────────────────────────────────────────────────────
 */
plugins {
  kotlin("multiplatform") version "2.2.20"
  //  مدل‌های قراردادِ بازار (`market/`) — فقط تجزیهٔ JSON؛ نسخهٔ کتابخانه همان
  //  `android-native/app` (۱.۷.۳) تا گرافِ وابستگیِ اندروید عوض نشود؛ کامپیوتر ۱.۹.۰ دارد و همان می‌ماند.
  kotlin("plugin.serialization") version "2.2.20"
}

group = "ir.vil3ntec.tohid"
version = "1.0"

//  آزمایشِ iOS (spike): هدف‌های iOS فقط با `-Pios=true` ساخته می‌شوند (کارِ
//  `.github/workflows/ios-spike.yml` روی macOS). بی این پرچم هیچ‌چیز این‌جا
//  پیکربندی نمی‌شود، پس گرافِ کارها و خروجیِ jvmِ اندروید/کامپیوتر همان است.
val iosSpike = providers.gradleProperty("ios").orNull == "true"

//  نگهبانِ commonMain (گامِ ۰ِ docs/IOS-PLAN-fa.md): با `-Pnative=true` یک هدفِ
//  Kotlin/Native روی لینوکس (`linuxX64`، بی Xcode) هم ساخته می‌شود. چون این هدف JDK
//  ندارد، هر `java.*`/`javax.*` ِ تازه در commonMain همان‌جا خطای کامپایل می‌شود
//  (کارِ `core-native` در `.github/workflows/ios-spike.yml`). بی پرچم هیچ‌چیز عوض نمی‌شود.
val nativeGuard = providers.gradleProperty("native").orNull == "true"

kotlin {
  jvm {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
  }
  //  آزمون‌های commonTest (Kotlin ِ خالص، بی دستگاه): روی jvm با `jvmTest` و در آیفون با iosTest.
  //  فقط kotlin-test از Maven Central — همان قاعدهٔ «بی مخزنِ گوگل» ِ settings.gradle.kts.
  sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
  //  بردارهای مرجعِ مشترکِ JS/Kotlin (`core/test-vectors/*.json`) روی JVM — فقط آزمون؛ همان نسخهٔ برنامه، از Maven Central
  sourceSets.jvmTest.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") }
  sourceSets {
    commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") }
    //  آزمونِ قراردادِ بازار (`MarketContractTest`) — همان `test-vectors/market-public-v1.json` ِ آزمونِ سرور
    jvmTest.dependencies { implementation(kotlin("test")) }
  }
  if (iosSpike) {
    iosArm64()
    iosSimulatorArm64()
  }
  if (nativeGuard) linuxX64()
}

/*
 *  OPS-05 — «تازه‌ها»: `docs/WHATS-NEW-fa.md` (تنها منبع، خواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۲) هنگامِ ساخت به
 *  `WhatsNewBundled.kt` (همان بسته‌ی `core/guide`) می‌رود تا برنامه بی شبکه همان متنی را نشان دهد که با همین کد
 *  ساخته شد. خواندن و سنجیدنِ متن فقط در `WhatsNew.parse` است؛ این‌جا فقط بسته‌بندی. تکه‌های ۸۰۰۰ نویسه‌ای
 *  (سقفِ رشتهٔ ثابتِ JVM ۶۴ کیلوبایت است) و جانشینِ نیمه (surrogate) با `\u` تا تکه‌ها جدا هم درست بمانند.
 */
val whatsNewSource = file("../docs/WHATS-NEW-fa.md")
val generateWhatsNew by tasks.registering {
  val src = whatsNewSource
  val out = layout.buildDirectory.dir("generated/whatsnew/kotlin")
  inputs.file(src).withPropertyName("whatsNew").withPathSensitivity(PathSensitivity.NONE)
  outputs.dir(out)
  doLast {
    check(src.isFile) { "docs/WHATS-NEW-fa.md نیست — «تازه‌ها» ی برنامه از همین پرونده ساخته می‌شود" }
    val text = src.readText(Charsets.UTF_8).replace("\r\n", "\n")
    fun lit(s: String): String = buildString {
      append('"')
      for (c in s) when {
        c == '\\' -> append("\\\\")
        c == '"' -> append("\\\"")
        c == '$' -> append("\\$")
        c == '\n' -> append("\\n")
        c == '\t' -> append("\\t")
        c < ' ' || c.isSurrogate() || c == ' ' || c == ' ' -> append("\\u" + c.code.toString(16).padStart(4, '0'))
        else -> append(c)
      }
      append('"')
    }
    val chunks = text.chunked(8000).joinToString(",\n") { "    " + lit(it) }
    val dir = out.get().asFile.resolve("ir/vil3ntec/tohid/core/guide").apply { mkdirs() }
    dir.resolve("WhatsNewBundled.kt").writeText(
      "// ساخته‌شده از docs/WHATS-NEW-fa.md به دستِ core/build.gradle.kts (generateWhatsNew) — دستی ویرایش نکنید.\n" +
        "package ir.vil3ntec.tohid.core.guide\n\n" +
        "internal object WhatsNewBundled {\n" +
        "  val text: String = arrayOf(\n$chunks,\n  ).joinToString(\"\")\n" +
        "}\n",
      Charsets.UTF_8,
    )
  }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateWhatsNew) }

//  همان آزمون JSONها را هنگامِ اجرا می‌خواند؛ بی این ورودی، تغییرِ فقط JSON آزمون را
//  UP-TO-DATE (سبزِ کهنه) رد می‌کرد
tasks.named<Test>("jvmTest") {
  inputs.files(fileTree("test-vectors"), fileTree("../license/test-vectors"))
    .withPropertyName("testVectors")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  //  `WhatsNewFileTest`: «تازه‌ها» و خطِ نسخهٔ برنامه
  inputs.files(whatsNewSource, file("../android-native/app/build.gradle.kts"))
    .withPropertyName("whatsNewFiles")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}

//  `cd core && ../desktop/gradlew test` — پروژهٔ چندسکویی کارِ `test` ندارد؛ همان `jvmTest`
//  (آزمون‌های `src/jvmTest`، مثلِ `MarketContractTest`). در گرافِ برنامه‌ها اثری ندارد.
tasks.register("test") {
  group = "verification"
  description = "آزمون‌های jvmِ core"
  dependsOn("jvmTest")
}

//  شکستِ آزمون در CI با پیام، علت و stack (پیش‌فرضِ SHORT ِ Gradle فقط «AssertionError at X.kt:113» می‌نوشت؛
//  run 41645079134 ِ `ResumableDownloadServerTest`). فقط گزارشِ آزمون؛ در ساخت و انتشار اثری ندارد.
//  شاهدِ CI برای STATUS (RULES «CI و آزمون»): هر آزمون یک خطِ «KTEST PASS|FAIL|SKIP <کلاسِ کامل> > <نامِ آزمون>».
//  رخدادِ PASSED ِ خودِ Gradle نه: دو خط برای هر آزمون، و «ساخت نسخهٔ بومی» تا ~۶۶۰۰ آزمون دارد.
tasks.withType<Test>().configureEach {
  testLogging {
    events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    exceptionFormat = TestExceptionFormat.FULL
    showExceptions = true
    showCauses = true
    showStackTraces = true
  }
  addTestListener(object : TestListener {
    override fun beforeSuite(suite: TestDescriptor) {}
    override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
    override fun beforeTest(testDescriptor: TestDescriptor) {}
    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
      val r = when (result.resultType) {
        TestResult.ResultType.SUCCESS -> "PASS"
        TestResult.ResultType.FAILURE -> "FAIL"
        else -> "SKIP"
      }
      println("KTEST $r ${testDescriptor.className} > ${testDescriptor.name}")
    }
  })
}
