plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

/*
 *  برنامهٔ خریدِ مشتری — مرحلهٔ ۱: فقط ساختِ debug (CI: کارِ «برنامهٔ خریدِ مشتری (گزارش)» در
 *  `.github/workflows/tohid-native.yml`). ⛔ امضا، نسخهٔ release ِ امضاشده و انتشار این‌جا نیست (مرحلهٔ بعد،
 *  با تصمیمِ صاحب‌کار و کلیدِ جدا). نسخه‌های وابستگی همان `android-native/app/build.gradle.kts` است.
 */
val apiBase: String = (System.getenv("TOHID_API_BASE")?.takeIf { it.isNotBlank() }
  ?: providers.gradleProperty("tohid.apiBase").orNull ?: "https://api.vill3n.top").trimEnd('/')

android {
  namespace = "ir.vil3ntec.tohid.customer"
  compileSdk = 35

  defaultConfig {
    applicationId = "ir.vil3ntec.tohid.customer"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"
    buildConfigField("String", "API_BASE", "\"$apiBase\"")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures { compose = true; buildConfig = true }
  testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
  //  همان BOM ِ برنامهٔ دکان (androidx.compose 1.9.x = Compose Multiplatform 1.9.3 ِ کامپیوتر و آیفون)
  val compose = platform("androidx.compose:compose-bom:2025.10.01")
  implementation(compose)
  implementation("ir.vil3ntec.tohid:core:1.0")
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-core")

  testImplementation("junit:junit:4.13.2")
}
