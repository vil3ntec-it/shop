package ir.vil3ntec.tohid.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  نامِ تابعِ backtick‌دار روی JVM این نویسه‌ها را نمی‌پذیرد: `. ; [ ] / < > : \`. کامپایلِ آزمونِ اندروید و
 *  کامپیوتر با «Name contains illegal characters» می‌شکند — همان که بتای main را خواباند (#708 ⇐ #712).
 *  کدِ اندروید جز در CI کامپایل نمی‌شود، ولی core همه‌جا؛ پس همین‌جا همهٔ پرونده‌های Kotlin ِ مخزن سنجیده می‌شوند.
 *  پرونده‌های اندروید ورودیِ اعلام‌شدهٔ `jvmTest` نیستند؛ روی دستگاهِ خودتان با `--rerun` اجرا کنید
 *  (`gradle -p core jvmTest --rerun --tests '*KotlinTestNamesTest'`). CI همیشه از نو می‌سازد.
 */
class KotlinTestNamesTest {

  private val illegal = Regex("""[.;\[\]/<>:\\]""")
  private val backtickFun = Regex("""\bfun\s+`([^`]*)`""")

  private fun repo(): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      if (File(dir, "android-native").isDirectory && File(dir, "core/src").isDirectory) return dir
      dir = dir.parentFile
    }
    error("ریشهٔ مخزن پیدا نشد")
  }

  /** نام‌های ممنوعِ یک متنِ Kotlin */
  private fun bad(text: String): List<String> =
    backtickFun.findAll(text).map { it.groupValues[1] }.filter { illegal.containsMatchIn(it) }.toList()

  @Test fun `هیچ نامِ تابعِ backtick‌دار نویسهٔ ممنوعِ JVM ندارد`() {
    val root = repo()
    val found = listOf("android-native", "desktop", "core").flatMap { top ->
      File(root, top).walkTopDown()
        .onEnter { it.name != "build" && !it.name.startsWith(".") }
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { f -> bad(f.readText()).map { "${f.relativeTo(root).invariantSeparatorsPath}: $it" } }
        .toList()
    }
    assertTrue(found.isEmpty(), "نامِ ممنوع — نویسه‌های . ; [ ] / < > : \\ را بردارید:\n" + found.joinToString("\n"))
  }

  @Test fun `سنجه نامِ ممنوع را می‌گیرد و نامِ درست را رها می‌کند`() {
    val tick = "`"
    val src = "fun ${tick}DLV-07 و DLV-17${':'} برچسب${tick}() {}\nfun ${tick}DLV-07 — درست${tick}() {}\nfun ${tick}a${'.'}b${tick}() {}"
    assertEquals(listOf("DLV-07 و DLV-17: برچسب", "a.b"), bad(src))
  }
}
