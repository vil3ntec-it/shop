import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 *  core — منطقِ خالص، بی UI، بی شبکه، بی android.* (بخشِ ۳.۳ِ مشخصات).
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • `src/commonMain` — Kotlin خالص (هدفِ نهایی: Money، Qty، Hlc، موتورها…).
 *  • `src/jvmMain`    — آنچه هنوز به JDK تکیه دارد (مثلِ `SecureRandom`ِ Ulid).
 *    اندروید و کامپیوتر هر دو همین هدفِ jvm را می‌گیرند.
 *  • نامِ بسته‌ها همان است که در `android-native` بود؛ جابه‌جایی به این‌جا
 *    هیچ import یا رفتاری را عوض نمی‌کند.
 *  • نسخهٔ Kotlin همان `android-native/build.gradle.kts` و
 *    `desktop/build.gradle.kts` است — سه‌تا با هم عوض می‌شوند.
 *  • آزمون‌ها فعلاً همان‌جا که بودند (`android-native/app/src/test`) می‌مانند و
 *    در هر دو برنامه روی همین کتابخانه اجرا می‌شوند؛ انتقالشان به
 *    `commonTest` قدمِ ۱ِ مسیرِ انتقال است.
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

kotlin {
  jvm {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
  }
  sourceSets {
    commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") }
    //  آزمونِ قراردادِ بازار (`MarketContractTest`) — همان `test-vectors/market-public-v1.json` ِ آزمونِ سرور
    jvmTest.dependencies { implementation(kotlin("test")) }
  }
  if (iosSpike) {
    iosArm64()
    iosSimulatorArm64()
    sourceSets.iosTest.dependencies { implementation(kotlin("test")) }
  }
}

//  `cd core && ../desktop/gradlew test` — پروژهٔ چندسکویی کارِ `test` ندارد؛ همان `jvmTest`
//  (آزمون‌های `src/jvmTest`، مثلِ `MarketContractTest`). در گرافِ برنامه‌ها اثری ندارد.
tasks.register("test") {
  group = "verification"
  description = "آزمون‌های jvmِ core"
  dependsOn("jvmTest")
}
