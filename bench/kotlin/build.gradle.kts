import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 *  bench/kotlin — ریزسنجشِ JVMِ موتورهای مشترکِ اندروید و کامپیوتر.
 *
 *  ‌• موتورها کپی نمی‌شوند و بازنویسی هم نمی‌شوند: همان فایل‌های
 *    `core/.../{money,text,calendar}` و `android-native/.../core/{stock,report,audit}` پیش از
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
//  موتورهای خالص (money، text، calendar) به ماژولِ `core` رفته‌اند (#371) و از گامِ KMP در `commonMain` ِ آن‌اند
//  (هیچ expect/actual ندارند)؛ بقیه هنوز در اندرویدند.
val engineRoots = mapOf(
  repoRoot.resolve("core/src/commonMain/kotlin") to listOf(
    "ir/vil3ntec/tohid/core/money",
    "ir/vil3ntec/tohid/core/text",
    "ir/vil3ntec/tohid/core/calendar",
  ),
  repoRoot.resolve("android-native/app/src/main/java") to listOf(
    "ir/vil3ntec/tohid/core/stock",
    "ir/vil3ntec/tohid/core/report",
    "ir/vil3ntec/tohid/core/audit",
  ),
)

val engineSources by tasks.registering(Sync::class) {
  inputs.files(engineRoots.flatMap { (root, dirs) -> dirs.map { root.resolve(it) } })
  doFirst {
    val missing = engineRoots.flatMap { (root, dirs) -> dirs.filterNot { root.resolve(it).isDirectory } }
    check(missing.isEmpty()) { "پوشهٔ موتور جابه‌جا شده؛ فهرستِ engineRoots را درست کنید: $missing" }
  }
  engineRoots.forEach { (root, dirs) -> from(root) { dirs.forEach { include("$it/*.kt") } } }
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
