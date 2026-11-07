import java.util.Properties
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/*
 *  برنامهٔ خریدِ مشتری روی آیفون — همان صفحه‌های Compose ِ `customer-android` (هیچ صفحه‌ای این‌جا دوباره نوشته
 *  نمی‌شود). برخلافِ برنامهٔ دکان (`ios/shared`، کپی + shimهای JVM) این صفحه‌ها از روزِ اول بی `android.*` و بی `java.*`
 *  نوشته شده‌اند، پس مستقیم همان پوشه کامپایل می‌شود؛ فقط مرزهای سکو (`*Android.kt`، `MainActivity.kt`) بیرون‌اند و
 *  همتای iOS ِ آن‌ها در `src/iosMain/kotlin/.../customer/ios` است (`*Ios.kt`: شبکه، نگه‌داری، عکس، موقعیت، گزینشِ عکس).
 *
 *  ⛔ هدف‌های iOS فقط با `-Pios=true` (مثلِ core و `ios/shared`)؛ بی پرچم فقط jvm ِ خالی (پیکربندی بی Xcode).
 *  ⛔ نسخه‌های Kotlin/Compose همان `ios/shared/build.gradle.kts` و `desktop/` — با هم عوض می‌شوند.
 *  ⛔ امضا، شناسهٔ تیم و انتشار نیست (اجازهٔ صاحب‌کار، `docs/IOS-PLAN-fa.md` §۰.۶)؛ فقط شبیه‌ساز، بی امضا.
 */
plugins {
  kotlin("multiplatform") version "2.2.20"
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
  id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
  id("org.jetbrains.compose") version "1.9.3"
}

val ios = providers.gradleProperty("ios").orNull == "true"
val sharedUi = rootDir.resolve("../../customer-android/app/src/main/java")

/*
 *  نشانیِ سرور و نسخه از همان `customer-android` (قاعدهٔ «یک سرور، یک حساب»): `tohid.apiBase` ِ gradle.properties و
 *  `versionName` ِ app/build.gradle.kts ⇐ `CustomerIosConfig` ِ ساخته‌شده. دستی ویرایش نشود.
 */
val androidApp = rootDir.resolve("../../customer-android")
val generateConfig by tasks.registering {
  val out = layout.buildDirectory.dir("generated/ios-config")
  val props = Properties().apply { androidApp.resolve("gradle.properties").inputStream().use { load(it) } }
  val apiBase = (props.getProperty("tohid.apiBase") ?: "").trim().trimEnd('/')
  val version = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(androidApp.resolve("app/build.gradle.kts").readText())?.groupValues?.get(1) ?: "0.0.0"
  inputs.property("apiBase", apiBase)
  inputs.property("version", version)
  outputs.dir(out)
  doLast {
    val dir = out.get().asFile.resolve("ir/vil3ntec/tohid/customer/ios").apply { mkdirs() }
    fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
    dir.resolve("CustomerIosConfig.kt").writeText(
      "// ساخته‌شده به دستِ customer-ios/shared/build.gradle.kts — دستی ویرایش نکنید.\n" +
        "package ir.vil3ntec.tohid.customer.ios\n\n" +
        "internal object CustomerIosConfig {\n  const val API_BASE: String = ${q(apiBase)}\n  const val VERSION_NAME: String = ${q(version)}\n}\n",
      Charsets.UTF_8,
    )
  }
}

/*
 *  سرآیندِ ObjC ِ چارچوب همهٔ API ِ عمومیِ کدِ مشترک (و ردهٔ core ِ به‌کاررفته) را دارد؛ ثابتی هم‌نامِ ماکروی C
 *  (‎`DEBUG`‎، ‎`NAME_MAX`‎…) Clang ِ Xcode را می‌شکند — همان نگهبانِ `ios/shared/build.gradle.kts` (`guardHeaderMacros`).
 */
val headerMacros = listOf("DEBUG", "NULL", "NAME_MAX", "PATH_MAX", "MAX_INPUT", "MAX_CANON", "LINE_MAX", "RELEASE", "YES", "NO", "TRUE", "FALSE", "EOF")

fun guardHeaderMacros(framework: File) {
  val header = framework.resolve("Headers/TohidCustomer.h")
  if (!header.isFile) return
  val text = header.readText(Charsets.UTF_8)
  if ("tohid: macro guard" in text) return
  val hit = headerMacros.filter { Regex("\\b$it\\s+__attribute__\\(\\(swift_name\\(").containsMatchIn(text) }
  if (hit.isEmpty()) return
  val lines = text.lines().toMutableList()
  val at = lines.indexOfLast { it.startsWith("#import ") } + 1
  lines.addAll(at, listOf("", "// tohid: macro guard — ${hit.joinToString()}") + hit.flatMap { listOf("#pragma push_macro(\"$it\")", "#undef $it") })
  lines.addAll(hit.reversed().map { "#pragma pop_macro(\"$it\")" })
  header.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
}

composeCompiler {
  targetKotlinPlatforms.set(setOf(KotlinPlatformType.native))
}

kotlin {
  jvm()
  if (ios) {
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
      it.binaries.framework {
        baseName = "TohidCustomer"
        isStatic = true
        binaryOption("bundleId", "ir.vil3ntec.tohid.customer.TohidCustomer")
        linkTaskProvider.configure { doLast { guardHeaderMacros(outputFile.get()) } }
      }
    }
  }
  sourceSets {
    if (ios) {
      iosMain {
        kotlin.srcDir(sharedUi)
        kotlin.srcDir(generateConfig)
        //  مرزِ اندروید (همان فهرستِ کامپایلِ Compose Desktop ِ آزمونِ محلی)؛ هر فایلِ تازهٔ صفحه خودبه‌خود به آیفون می‌رسد
        kotlin.exclude("**/*Android.kt", "**/MainActivity.kt")
        dependencies {
          implementation("ir.vil3ntec.tohid:core:1.0")
          implementation(compose.runtime)
          implementation(compose.foundation)
          implementation(compose.material)
          implementation(compose.ui)
          implementation("org.jetbrains.compose.material3:material3:1.9.0")
          implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
          implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
          implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
      }
    }
  }
  compilerOptions {
    optIn.addAll(
      "androidx.compose.material3.ExperimentalMaterial3Api",
      "androidx.compose.foundation.layout.ExperimentalLayoutApi",
      "kotlinx.cinterop.ExperimentalForeignApi",
      "kotlinx.cinterop.BetaInteropApi",
    )
  }
}
