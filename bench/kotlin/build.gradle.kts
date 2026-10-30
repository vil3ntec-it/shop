import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 *  bench/kotlin — ریزسنجشِ JVMِ موتورهای مشترکِ اندروید و کامپیوتر.
 *
 *  ‌• موتورها کپی نمی‌شوند و بازنویسی هم نمی‌شوند: هر بسته‌ای که هنوز در
 *    `android-native/.../core/{stock,report,audit,…}` است پیش از ساخت به
 *    `build/generated/engines` همگام می‌شود، و هر بسته‌ای که به کتابخانهٔ مشترکِ
 *    `core` (KMP) رفته (money، text، calendar، …) از خودِ همان کتابخانه می‌آید
 *    (`includeBuild("../../core")`، همان راهِ اندروید و کامپیوتر) — پس سنجه همیشه
 *    همان کدی است که در برنامه اجرا می‌شود و جابه‌جاییِ بسته به `core` ساخت را
 *    نمی‌شکند (PERF-01: کارِ «موتورهای Kotlin» ِ perf.yml از ۱۴۰۵/۰۸/۰۴ همین‌جا قرمز بود).
 *  ‌• نسخهٔ Kotlin همان `android-native`، `desktop` و `core` است (2.2.20).
 *  ‌• اجرا:  node bench/kotlin/ledger.mjs <ledger.json>   (دادهٔ مرجعِ قطعی)
 *           ./desktop/gradlew -p bench/kotlin run --args="<ledger.json> [--json out.json]"
 */
plugins {
  kotlin("jvm") version "2.2.20"
  kotlin("plugin.serialization") version "2.2.20"
  application
}

val repoRoot = rootDir.resolve("../..")
val engineRoot = repoRoot.resolve("android-native/app/src/main/java")
val coreSrc = repoRoot.resolve("core/src")
val engineDirs = listOf(
  "ir/vil3ntec/tohid/core/money",
  "ir/vil3ntec/tohid/core/stock",
  "ir/vil3ntec/tohid/core/report",
  "ir/vil3ntec/tohid/core/text",
  "ir/vil3ntec/tohid/core/audit",
  "ir/vil3ntec/tohid/core/calendar",
)
/** بسته در کدام مجموعهٔ منبعِ `core` است (commonMain، jvmMain، …)؛ نبود ⇒ تهی. */
fun inCore(dir: String): List<String> =
  coreSrc.listFiles().orEmpty().filter { it.resolve("kotlin/$dir").isDirectory }.map { it.name }.sorted()

val engineSources by tasks.registering(Sync::class) {
  inputs.files(engineDirs.map { engineRoot.resolve(it) })
  doFirst {
    //  هر بسته یا هنوز در اندروید است یا (همه یا بخشی‌اش) به `core` رفته؛ هیچ‌کدام ⇒
    //  جابه‌جاییِ ناشناخته. کلاسِ تکراری میانِ دو جا را خودِ کامپایلر می‌گیرد.
    val missing = engineDirs.filter { !engineRoot.resolve(it).isDirectory && inCore(it).isEmpty() }
    check(missing.isEmpty()) { "پوشهٔ موتور نه در android-native است نه در core؛ فهرستِ engineDirs را درست کنید: $missing" }
  }
  from(engineRoot) { engineDirs.forEach { include("$it/*.kt") } }
  into(layout.buildDirectory.dir("generated/engines"))
}

sourceSets {
  main {
    kotlin.srcDir(engineSources)
  }
}

dependencies {
  //  همان مختصاتی که android-native و desktop می‌گیرند (`core/settings.gradle.kts`)
  implementation("ir.vil3ntec.tohid:core")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
  mainClass.set("ir.vil3ntec.tohid.bench.EngineBenchKt")
  applicationDefaultJvmArgs = listOf("-Xmx2500m", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}
