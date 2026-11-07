/*
 *  ⚠️ همان نسخه‌های `android-native/build.gradle.kts` (AGP 8.7.3، Kotlin 2.2.20): هر دو برنامه یک `core`
 *  دارند؛ یکی را بی دیگری بالا یا پایین نبرید (D-22: AGP روی 8.x می‌ماند).
 */
plugins {
  id("com.android.application") version "8.7.3" apply false
  id("org.jetbrains.kotlin.android") version "2.2.20" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
