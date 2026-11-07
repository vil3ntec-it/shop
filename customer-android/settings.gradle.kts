/*
 *  برنامهٔ خریدِ مشتری (O-04 گزینهٔ «ب»، مرحلهٔ ۱) — اندروید. برنامهٔ جدا از برنامهٔ دکان (بستهٔ
 *  `ir.vil3ntec.tohid.customer`)؛ هیچ کدی از `android-native` نمی‌گیرد و هیچ چیزِ آن را عوض نمی‌کند.
 *  منطق، کارخواهِ API و نوشته‌ها در `core/src/commonMain/.../core/shopper` است (برای آیفون هم)؛ این‌جا فقط
 *  صفحه‌های Compose و مرزِ اندروید (`*Android.kt`، `MainActivity.kt`).
 *
 *  ساخت (اینترنت و Android SDK می‌خواهد؛ همان نسخه‌های `android-native`):
 *      cd customer-android && gradle --no-daemon testDebugUnitTest assembleDebug
 *  فقط ساختِ debug؛ امضا و انتشار مرحلهٔ بعد است (docs/MAINTAINER-fa.md §۱).
 */
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories { google(); mavenCentral() }
}
rootProject.name = "TohidCustomer"
include(":app")
includeBuild("../core")
