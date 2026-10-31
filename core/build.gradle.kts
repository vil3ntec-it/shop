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
  sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
  if (iosSpike) {
    iosArm64()
    iosSimulatorArm64()
  }
  sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
  if (nativeGuard) linuxX64()
}
