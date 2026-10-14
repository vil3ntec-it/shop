/*
 *  core — Kotlin خالص برای هر دو برنامه (قدم صفرِ بخشِ ۳.۳ِ مشخصات).
 *
 *  یک build جدا در ریشهٔ مخزن که `android-native` و `desktop` هر دو با
 *  `includeBuild("../core")` می‌گیرندش؛ مختصاتش `ir.vil3ntec.tohid:core` است.
 *  فقط از Maven Central می‌خواند — نه مخزنِ گوگل، نه androidx.
 */
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}
dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}
rootProject.name = "core"
