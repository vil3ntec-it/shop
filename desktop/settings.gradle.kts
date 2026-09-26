/*
 *  برنامهٔ کامپیوترِ فروشگاه — Compose Desktop.
 *
 *  ⚠️ مخزنِ گوگل از `maven.google.com` خوانده می‌شود نه `google()`: همان
 *  مخزن است، ولی بعضی شبکه‌ها `dl.google.com` را می‌بندند.
 */
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.google.com")
  }
}
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven("https://maven.google.com")
  }
}
rootProject.name = "TohidDesktop"
