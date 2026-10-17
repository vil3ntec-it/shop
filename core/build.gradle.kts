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
}

group = "ir.vil3ntec.tohid"
version = "1.0"

kotlin {
  jvm {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
  }
}
