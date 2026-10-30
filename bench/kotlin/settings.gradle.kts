/*
 *  bench/kotlin — سنجشِ JVMِ موتورهای Kotlinِ خالص (PERF-01؛ بخشِ ۹.۲).
 *
 *  پروژهٔ مستقل؛ به هیچ برنامه‌ای وصل نیست و چیزی منتشر نمی‌کند.
 *  فقط از Maven Central می‌خواند.
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

rootProject.name = "engine-bench"

//  core — منطقِ خالصِ مشترکِ اندروید و کامپیوتر (`ir.vil3ntec.tohid:core`)؛ بسته‌هایی که از
//  android-native به آن رفته‌اند (money، text، calendar) از همین‌جا می‌آیند
includeBuild("../../core")
