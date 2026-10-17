/*
 *  ابزارِ مهاجرتِ دادهٔ فروشگاه — پروژهٔ JVMِ مستقل.
 *
 *  به هیچ برنامه‌ای وصل نیست: نه APK، نه نصب‌کنندهٔ کامپیوتر، نه سرور.
 *  فقط این‌جا ساخته و آزموده می‌شود (`.github/workflows/shop-migrate.yml`).
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

rootProject.name = "shop-migrate"
