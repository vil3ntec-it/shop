/*
 *  برنامهٔ خریدِ مشتری روی آیفون (O-04 «ب»، مرحلهٔ ۲) — پوستهٔ Compose Multiplatform ِ همان صفحه‌های
 *  `customer-android/app/src/main/java/.../customer/ui` (بی android.*). ساختِ جدا، مثلِ `ios/shared` ِ برنامهٔ دکان:
 *  نه اندروید و نه کامپیوتر این را می‌خوانند. فقط کارهای «(گزارش)» ِ `.github/workflows/ios-spike.yml` می‌سازندش.
 */
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
  }
}
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
  }
}
rootProject.name = "TohidCustomerIos"

//  core — همان منطقِ خالصِ مشترک (کارخواه، سبد، ثبت، گفت‌وگو، نشانی)؛ هدف‌های iOS ِ آن با `-Pios=true`
includeBuild("../../core")
