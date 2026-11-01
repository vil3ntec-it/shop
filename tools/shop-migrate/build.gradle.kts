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
  //  `ShopData.safeEntries` (#364): مدلِ گاوصندوق در پروندهٔ جدای خودش است
  "ir/vil3ntec/tohid/data/SafeEntry.kt",
  //  مدل‌های خالصی که `Models.kt` به آن‌ها اشاره می‌کند: کیت، قیمتِ زمان‌بندی‌شده، تنوع، سفارشِ ویژه
  "ir/vil3ntec/tohid/data/ModelParts.kt",
  "ir/vil3ntec/tohid/Jalali.kt",
  //  LOC-03: `todayIso` روزِ کاری را از `ShopDay` می‌گیرد؛ `ShopDayChoice` (حالتِ Compose و Context)
  //  این‌جا جانشینِ JVMِ خودش را در src/main دارد — همیشه نیمه‌شب، پس خروجی همان دیروز است
  "ir/vil3ntec/tohid/core/calendar/ShopDay.kt",
  "ir/vil3ntec/tohid/Format.kt",
  //  Jalali.kt از LOC-01 به هستهٔ تقویم و متن تکیه دارد؛ LocaleChoice (حالتِ Compose)
  //  این‌جا جانشینِ JVMِ خودش را در src/main دارد
  "ir/vil3ntec/tohid/LocaleText.kt",
  "ir/vil3ntec/tohid/core/calendar/SolarHijri.kt",
  //  LocaleText.kt تاریخِ قمری را از `LunarHijri` (کاتلینِ خالصِ core) می‌گیرد
  "ir/vil3ntec/tohid/core/calendar/LunarHijri.kt",
  "ir/vil3ntec/tohid/core/calendar/CivilDays.kt",
  "ir/vil3ntec/tohid/core/text/LocaleFormat.kt",
  "ir/vil3ntec/tohid/core/text/TextNormalizer.kt",
  //  LocaleFormat گرد کردن را از `mathRound` (برگردانِ دقیقِ Math.round، کاتلینِ خالص) می‌گیرد
  "ir/vil3ntec/tohid/core/money/MathRound.kt",
  //  Format.kt و Jalali.kt نوشته‌ها و تاریخِ نمایشی را از جدولِ سه‌زبانه می‌گیرند (کاتلینِ خالص)؛
  //  ابزار همیشه فارسی است (LocaleChoice ِ src/main)، پس خروجی همان دیروز است
  "ir/vil3ntec/tohid/core/i18n/I18n.kt",
  "ir/vil3ntec/tohid/core/i18n/Str.kt",
)
//  Ulid.kt از قدم صفرِ ۳.۳ (#223) در ماژولِ مشترکِ core است، با همان بسته؛ از ۱۴۰۵/۰۸/۰۸ در commonMain.
//  مرزِ سکوی آن (`core/platform`: expect/actual) این‌جا جانشینِ JVMِ خودش را در src/main دارد،
//  چون این پروژه KMP نیست — همان API ِ JDK که actual ِ JVM ِ core دارد.
val coreRoot = repoRoot.resolve("core/src/commonMain/kotlin")
val coreFiles = listOf(
  "ir/vil3ntec/tohid/sync/v1/Ulid.kt",
)

//  فایلی از legacyFiles که از android-native به ماژولِ مشترکِ core رفته (با همان مسیرِ بسته) از
//  همان‌جا خوانده می‌شود — جابه‌جاییِ یک هسته دیگر این ابزار را نمی‌شکند
val legacyRoots = listOf(
  legacyRoot,
  repoRoot.resolve("core/src/commonMain/kotlin"),
  repoRoot.resolve("core/src/jvmMain/kotlin"),
)
fun legacyRootOf(file: String): File? = legacyRoots.firstOrNull { it.resolve(file).isFile }

val legacySources by tasks.registering(Sync::class) {
  inputs.files(legacyFiles.mapNotNull { f -> legacyRootOf(f)?.resolve(f) } + coreFiles.map { coreRoot.resolve(it) })
  doFirst {
    val missing = legacyFiles.filter { legacyRootOf(it) == null } +
      coreFiles.filterNot { coreRoot.resolve(it).isFile }
    check(missing.isEmpty()) {
      "فایلِ مدلِ قدیم جابه‌جا شده؛ فهرستِ legacyFiles/coreFiles را درست کنید: $missing"
    }
  }
  legacyFiles.forEach { f -> legacyRootOf(f)?.let { root -> from(root) { include(f) } } }
  from(coreRoot) { coreFiles.forEach { include(it) } }
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
