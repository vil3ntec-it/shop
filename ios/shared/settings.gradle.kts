/*
 *  رابطِ کاربریِ مشترکِ آیفون — Compose Multiplatform روی iOS (گامِ ۱ِ docs/IOS-PLAN-fa.md).
 *
 *  ساختِ جدا، مثلِ `desktop/`: نه اندروید و نه کامپیوتر این را نمی‌خوانند، پس گراف و خروجیِ
 *  آن دو دست نمی‌خورد. فقط کارهای `ios-ui-klib` و `ios-app` در `.github/workflows/ios-spike.yml` می‌سازندش.
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
rootProject.name = "TohidIos"

//  core — همان منطقِ خالصِ مشترک (`ir.vil3ntec.tohid:core`)؛ هدف‌های iOSِ آن هم با `-Pios=true` روشن می‌شوند
includeBuild("../../core")
