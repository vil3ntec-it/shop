import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 *  shop-migrate — فاز ۱، «مهاجرت داده» قدمِ ۱: اجرای آزمایشی.
 *
 *  ‌• `src/main/sqldelight`: طرحِ SQLite (SQLDelight) برای موجودیت‌های اصلیِ
 *    بخشِ ۳.۴ — هنوز به برنامه وصل نیست.
 *  ‌• `dry-run`: پشتیبانِ امروز (ZIP یا JSON) را می‌خواند، به مدلِ تازه در یک
 *    پایگاهِ **درون‌حافظه** می‌برد و گزارشِ تطبیقِ «محاسبهٔ قدیم ⇄ مدلِ تازه»
 *    را چاپ می‌کند. هیچ فایلی نمی‌نویسد.
 *
 *  مدلِ دادهٔ قدیم (`Models.kt`)، تقویم (`Jalali.kt` + `Format.kt`) از خودِ
 *  `android-native` و `Ulid.kt` از `core/` کپی می‌شوند، نه بازنویسی — تا خواندنِ پشتیبان دقیقاً
 *  همانِ برنامه باشد.
 */
plugins {
  kotlin("jvm") version "2.2.20"
  kotlin("plugin.serialization") version "2.2.20"
  id("app.cash.sqldelight") version "2.1.0"
  application
}

val repoRoot = rootDir.resolve("../..")
val legacyRoot = repoRoot.resolve("android-native/app/src/main/java")
val legacyFiles = listOf(
  "ir/vil3ntec/tohid/data/Models.kt",
  "ir/vil3ntec/tohid/Jalali.kt",
  "ir/vil3ntec/tohid/Format.kt",
  //  Jalali.kt از LOC-01 به هستهٔ تقویم و متن تکیه دارد؛ LocaleChoice (حالتِ Compose)
  //  این‌جا جانشینِ JVMِ خودش را در src/main دارد
  "ir/vil3ntec/tohid/LocaleText.kt",
)
//  از ماژولِ مشترکِ core، با همان بسته‌ها: Ulid.kt از قدم صفرِ ۳.۳ (#223)؛ تقویم و متن از
//  جابه‌جاییِ هسته‌های خالص (تقویم به `Math.floorDiv`ِ JDK تکیه دارد، پس jvmMain)
val coreFiles: Map<File, List<String>> = mapOf(
  repoRoot.resolve("core/src/jvmMain/kotlin") to listOf(
    "ir/vil3ntec/tohid/sync/v1/Ulid.kt",
    "ir/vil3ntec/tohid/core/calendar/SolarHijri.kt",
    "ir/vil3ntec/tohid/core/calendar/CivilDays.kt",
  ),
  repoRoot.resolve("core/src/commonMain/kotlin") to listOf(
    "ir/vil3ntec/tohid/core/text/LocaleFormat.kt",
    "ir/vil3ntec/tohid/core/text/TextNormalizer.kt",
  ),
)
val coreSourceFiles = coreFiles.flatMap { (root, files) -> files.map { root.resolve(it) } }

val legacySources by tasks.registering(Sync::class) {
  inputs.files(legacyFiles.map { legacyRoot.resolve(it) } + coreSourceFiles)
  doFirst {
    val missing = legacyFiles.filterNot { legacyRoot.resolve(it).isFile } + coreSourceFiles.filterNot { it.isFile }
    check(missing.isEmpty()) {
      "فایلِ مدلِ قدیم جابه‌جا شده؛ فهرستِ legacyFiles/coreFiles را درست کنید: $missing"
    }
  }
  from(legacyRoot) { legacyFiles.forEach { include(it) } }
  coreFiles.forEach { (root, files) -> from(root) { files.forEach { include(it) } } }
  into(layout.buildDirectory.dir("generated/legacy-src"))
}

sourceSets {
  main {
    kotlin.srcDir(legacySources)
  }
}

sqldelight {
  databases {
    create("ShopDb") {
      packageName.set("ir.vil3ntec.tohid.db")
    }
  }
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
  implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
  testImplementation(kotlin("test"))
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
  mainClass.set("ir.vil3ntec.tohid.migrate.MainKt")
  applicationName = "shop-migrate"
}

tasks.test {
  useJUnitPlatform()
}
