plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "ir.vil3ntec.tohid.admin"
  compileSdk = 35

  //  شماره‌ی نسخه از شماره‌ی ساخت CI می‌آید، مثل برنامه‌ی مشتری
  val versionBase = "1.0"
  val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0

  defaultConfig {
    //  بسته‌ی جدا از برنامه‌ی مشتری: هر دو کنار هم روی یک گوشی نصب می‌شوند
    applicationId = "ir.vil3ntec.tohid.admin"
    minSdk = 24
    targetSdk = 35
    versionCode = 100 + buildNumber
    versionName = "$versionBase.$buildNumber"
    resourceConfigurations += listOf("fa", "en")
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
  }

  /*
   *  کلید امضا — همان کلید برنامه‌ی مشتری، از محیط.
   *
   *  بسته‌ی این برنامه جداست، پس یک کلید برای هر دو مشکلی ندارد و یک
   *  چیز کمتر برای گم کردن است.
   */
  fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

  val keystorePath: String = env("TOHID_KEYSTORE") ?: "../../android-native/app/tohid-release.jks"
  val keystorePass: String = env("TOHID_KEYSTORE_PASSWORD") ?: "tohid-shop"
  val keystoreAlias: String = env("TOHID_KEY_ALIAS") ?: "tohid"
  val keystoreKeyPass: String = env("TOHID_KEY_PASSWORD") ?: keystorePass

  signingConfigs {
    create("release") {
      storeFile = file(keystorePath)
      storePassword = keystorePass
      keyAlias = keystoreAlias
      keyPassword = keystoreKeyPass
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true; buildConfig = true }
}

dependencies {
  val compose = platform("androidx.compose:compose-bom:2024.12.01")
  implementation(compose)
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
  implementation("androidx.security:security-crypto:1.0.0")

  testImplementation("junit:junit:4.13.2")
}
