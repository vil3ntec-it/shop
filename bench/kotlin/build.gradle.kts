import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 *  bench/kotlin — ریزسنجشِ JVMِ موتورهای مشترکِ اندروید و کامپیوتر.
 *
 *  ‌• موتورها کپی نمی‌شوند و بازنویسی هم نمی‌شوند: همان فایل‌های
 *    `android-native/.../core/{stock,report,audit}` و `core/src/commonMain/.../core/{money,text,calendar}` پیش از
 *    ساخت به `build/generated/engines` همگام می‌شوند — پس سنجه همیشه همان کدی
 *    است که در برنامه اجرا می‌شود.
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
//  money، text و calendar به `core` (commonMain ِ KMP) رفته‌اند؛ هر پوشه از همان ریشه‌ای که دارَدَش
val engineRoots = listOf(
  repoRoot.resolve("android-native/app/src/main/java"),
  repoRoot.resolve("core/src/commonMain/kotlin"),
)
val engineDirs = listOf(
  "ir/vil3ntec/tohid/core/money",
  "ir/vil3ntec/tohid/core/stock",
  "ir/vil3ntec/tohid/core/report",
  "ir/vil3ntec/tohid/core/text",
  "ir/vil3ntec/tohid/core/audit",
  "ir/vil3ntec/tohid/core/calendar",
)

val engineSources by tasks.registering(Sync::class) {
  inputs.files(engineRoots.flatMap { root -> engineDirs.map { root.resolve(it) } })
  doFirst {
    val missing = engineDirs.filter { d -> engineRoots.none { it.resolve(d).isDirectory } }
    check(missing.isEmpty()) { "پوشهٔ موتور جابه‌جا شده؛ فهرستِ engineDirs را درست کنید: $missing" }
  }
  engineRoots.forEach { root -> from(root) { engineDirs.forEach { include("$it/*.kt") } } }
  into(layout.buildDirectory.dir("generated/engines"))
}

sourceSets {
  main {
    kotlin.srcDir(engineSources)
  }
}

dependencies {
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
