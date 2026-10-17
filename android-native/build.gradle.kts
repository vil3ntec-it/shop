/*
 *  ⚠️ نسخهٔ Kotlin همان است که `desktop/build.gradle.kts` دارد (قدم صفرِ
 *  ۳.۳ِ مشخصات). دو برنامه یک کد را کامپایل می‌کنند؛ یکی را بدون دیگری
 *  بالا یا پایین نبرید. AGP روی 8.x می‌ماند (ارتقا به AGP 9 در فاز ۱
 *  ممنوع است — D-22).
 */
plugins {
  id("com.android.application") version "8.7.3" apply false
  id("org.jetbrains.kotlin.android") version "2.2.20" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
  id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
}
