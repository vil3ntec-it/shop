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

//  core ِ KMP مثلِ کامپیوتر و اندروید (`ir.vil3ntec.tohid:core`)، نه کپیِ پوشه‌به‌پوشه — موتورها هر روز به پوشهٔ
//  تازه‌ای از core تکیه می‌کنند (risk، orders، credit، …) و فهرستِ دستی بنچ را می‌شکست
includeBuild("../../core")
