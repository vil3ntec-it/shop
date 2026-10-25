package ir.vil3ntec.tohid.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  جدولِ `phrases` ِ `desktop/build.gradle.kts` — همان کاری که کارِ `androidSources`
 *  پیش از کامپایلِ کامپیوتر می‌کند (عبارت‌ها به ترتیب، هر کدام روی خروجیِ قبلی)،
 *  این‌جا روی متنِ کدِ اندروید اجرا و سنجیده می‌شود.
 */
class DesktopPhrasesTest {

  private val phrases = DesktopPhrases.load()

  private val sources: List<File> =
    DesktopPhrases.repo().resolve("android-native/app/src/main/java").walkTopDown()
      .filter { it.isFile && it.extension == "kt" }.toList()

  /** همان حلقهٔ `androidSources` */
  private fun rewrite(text: String): String {
    var out = text
    for ((from, to) in phrases) if (out.contains(from)) out = out.replace(from, to)
    return out
  }

  @Test
  fun `جدول خوانده می‌شود`() {
    assertTrue("جدولِ phrases خالی است", phrases.size >= 30)
    assertTrue("کدِ اندروید پیدا نشد", sources.size > 100)
  }

  /** هشدارِ «عبارتِ جدول دیگر در کدِ اندروید نیست» حالا آزمون است: نوشتهٔ گوشی عوض شد ⇒ جدول هم */
  @Test
  fun `هر عبارتِ جدول هنوز در کدِ اندروید هست`() {
    val all = sources.joinToString("\n") { it.readText() }
    val gone = phrases.map { it.first }.filterNot { all.contains(it) }
    assertEquals("این عبارت‌ها دیگر در کدِ اندروید نیستند؛ جدولِ کامپیوتر را با آن ببرید", emptyList<String>(), gone)
  }

  /** مقصدِ هیچ عبارتی را عبارتِ پسین دوباره بازنویسی نمی‌کند */
  @Test
  fun `هیچ مقصدی به دستِ عبارتِ بعدی دوباره عوض نمی‌شود`() {
    val chained = phrases.withIndex().flatMap { (i, p) ->
      phrases.drop(i + 1).filter { p.second.contains(it.first) }.map { "${p.second} ⇐ ${it.first}" }
    }
    assertEquals(emptyList<String>(), chained)
  }

  /** نوشته‌های کد (رشته‌ها)، بی توضیح‌ها — توضیحِ KDoc هم بازنویسی می‌شود ولی دیده نمی‌شود */
  private fun literals(code: String): List<String> =
    code.lines()
      .filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
      .flatMap { line -> Regex(""""(?:[^"\\]|\\.)*"""").findAll(line).map { it.value }.toList() }

  @Test
  fun `بازنویسیِ کامپیوتر هیچ‌جا «کامپیوتر، کامپیوتر» نمی‌سازد`() {
    val bad = sources.flatMap { f -> literals(rewrite(f.readText())).filter { it.contains("کامپیوتر، کامپیوتر") }.map { "${f.name}: $it" } }
    assertEquals(emptyList<String>(), bad)
    //  و بدونِ اصلاحِ جدول، همان رشته خراب می‌شد (آزمون واقعاً چیزی را می‌گیرد)
    assertTrue(literals(rewrite("x(\"روی گوشی، کامپیوتر و سایت\")")).single().contains("کامپیوتر، کامپیوتر"))
    //  همان جمله‌ای که خراب می‌شد
    assertEquals(
      "همین ایمیل در گوشی، کامپیوتر و سایت یک حساب است.",
      rewrite("همین ایمیل روی گوشی و روی سایت یک حساب است."),
    )
  }
}
