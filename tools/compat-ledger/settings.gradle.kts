/*
 *  compat-ledger — سازندهٔ نمونه‌های «سازگاریِ دفتر» (`android-native/app/src/test/resources/compat/`).
 *  مدلِ دفترِ یک نسخهٔ مشخص (`git show <ref>:…/Models.kt` و همراه‌هایش) را می‌سازد و همان رمزگذار/رمزگشای
 *  `ShopStore` را رویش می‌دواند؛ به هیچ برنامه‌ای وصل نیست. اجرا: `bash tools/compat-ledger/regen.sh`.
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

rootProject.name = "compat-ledger"
