/*
 *  برنامهٔ کامپیوترِ فروشگاه — Compose Desktop.
 *
 *  ⚠️ `TOHID_OFFLINE_M2`: پوشهٔ یک مخزنِ Maven با بسته‌های `androidx.*`
 *  (همان بستهٔ «ساختِ آفلاین» که CI می‌سازد)، برای شبکه‌ای که مخزنِ گوگل
 *  را نمی‌رساند. اگر تنظیم شده باشد، **اول** همان خوانده می‌شود.
 */
val offline = System.getenv("TOHID_OFFLINE_M2")?.takeIf { it.isNotBlank() }

pluginManagement {
  repositories {
    System.getenv("TOHID_OFFLINE_M2")?.takeIf { it.isNotBlank() }?.let { maven(it) }
    gradlePluginPortal()
    mavenCentral()
    google()
  }
}
dependencyResolutionManagement {
  repositories {
    offline?.let { maven(it) }
    mavenCentral()
    google()
  }
}
rootProject.name = "TohidDesktop"
