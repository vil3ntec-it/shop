package ir.vil3ntec.tohid.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  LOC-06: صفحه‌ای که به جدولِ سه‌زبانه (`Str*`) کوچ کرده، متنِ ثابتِ فارسی ندارد.
 *
 *  `I18nCoverageTest` فقط جدول‌ها را می‌سنجد؛ این آزمون خودِ صفحه را. نشانِ کوچ یک خط
 *  در خودِ پرونده است: [MARK]. همهٔ پرونده‌های برنامه که این نشان را دارند گشته می‌شوند
 *  و هر رشته‌ای که حرفِ فارسی/پشتو دارد گرفته می‌شود — بیرون از توضیح، و از جمله
 *  رشته‌های درونِ `${…}`. استثنا فقط خطی است که [DATA] دارد: متنی که ذخیره یا همگام
 *  می‌شود (دلیلِ لغو، نامِ پیش‌فرض…) و نباید با زبانِ صفحه عوض شود. «؋» و «،» حرف نیستند.
 *
 *  هر PRِ ترجمه همین پرونده را بایت‌به‌بایت یکسان دارد و فقط نشان به صفحه‌های خودش می‌افزاید.
 */
class ScreenTextTest {

  private val letter = Regex("[\\u0621-\\u064A\\u066E-\\u06D3\\u06FA-\\u06FF]")

  private fun marked(): List<File> =
    DesktopPhrases.repo().resolve("android-native/app/src/main/java/ir/vil3ntec/tohid")
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" && it.readText().contains(MARK) }
      .sortedBy { it.path }
      .toList()

  @Test
  fun `صفحهٔ سه‌زبانه متنِ ثابتِ فارسی ندارد`() {
    val files = marked()
    assertTrue("هیچ پرونده‌ای نشانِ «$MARK» ندارد", files.isNotEmpty())
    val bad = files.flatMap { f ->
      val lines = f.readLines()
      KotlinLiterals.of(f.readText())
        .filter { (n, s) -> letter.containsMatchIn(s) && !lines[n - 1].contains(DATA) }
        .map { (n, s) -> "${f.name}:$n «$s»" }
    }
    println("i18n screens: ${files.size} — ${files.joinToString(" ") { it.name }}")
    assertTrue(
      "متنِ ثابتِ فارسی در صفحهٔ سه‌زبانه — به جدولِ Str* ببرید، یا اگر داده است «$DATA …» بگذارید:\n" + bad.joinToString("\n"),
      bad.isEmpty(),
    )
  }

  @Test
  fun `رشته‌ها بی توضیح و با درونِ قالب خوانده می‌شوند`() {
    val src = listOf(
      "val a = \"x\" // \"در توضیح\"",
      "/* \"بلوک\" /* تو در تو */ \"هنوز توضیح\" */",
      "val b = \"سلام \${if (c) \"بله\" else \"y\"} و \$d\"",
      "val ch = '\"'; val q = '\\''",
      "val r = \"\"\"خام \"نقل\" \"\"\"",
      "val e = \"a\\\"ب\" + \"{\" + \"}\"",
      "fun f() { listOf(1).map { \"درونِ لامبدا\" } }",
    ).joinToString("\n")
    assertEquals(
      listOf(
        1 to "x",
        3 to "بله", 3 to "y", 3 to "سلام  و \$d",
        5 to "خام \"نقل\" ",
        6 to "a\\\"ب", 6 to "{", 6 to "}",
        7 to "درونِ لامبدا",
      ),
      KotlinLiterals.of(src),
    )
  }

  @Test
  fun `داده و نماد آزادند ولی متنِ صفحه نه`() {
    assertTrue(letter.containsMatchIn("مصرف تازه"))
    assertTrue(letter.containsMatchIn("خوندي کول"))
    assertTrue(!letter.containsMatchIn("1,250 ؋"))
    assertTrue(!letter.containsMatchIn("، · — «»"))
  }

  companion object {
    const val MARK = "// i18n: سه‌زبانه"
    const val DATA = "// داده:"
  }
}

/** رشته‌های یک پروندهٔ کاتلین با شمارهٔ خطِ شروع — بی توضیح‌ها؛ رشته‌های درونِ `${…}` جدا می‌آیند */
internal object KotlinLiterals {

  private class Frame(val kind: Char, val line: Int = 0) {
    var depth = 0
    val buf = StringBuilder()
  }

  fun of(src: String): List<Pair<Int, String>> {
    val out = ArrayList<Pair<Int, String>>()
    val stack = ArrayDeque<Frame>().apply { addLast(Frame('c')) }
    var i = 0
    var line = 1
    fun at(k: Int) = if (i + k < src.length) src[i + k] else '\u0000'
    while (i < src.length) {
      val ch = src[i]
      val f = stack.last()
      if (f.kind == 'c') {
        when {
          ch == '/' && at(1) == '/' -> { while (i < src.length && src[i] != '\n') i++; continue }
          ch == '/' && at(1) == '*' -> {
            var depth = 0
            do {
              when {
                src.startsWith("/*", i) -> { depth++; i += 2 }
                src.startsWith("*/", i) -> { depth--; i += 2 }
                else -> { if (src[i] == '\n') line++; i++ }
              }
            } while (depth > 0 && i < src.length)
            continue
          }
          src.startsWith("\"\"\"", i) -> { stack.addLast(Frame('r', line)); i += 3; continue }
          ch == '"' -> { stack.addLast(Frame('s', line)); i++; continue }
          ch == '\'' -> {
            i += if (at(1) == '\\') 3 else 2
            while (i < src.length && src[i] != '\'') i++
            i++
            continue
          }
          ch == '{' -> f.depth++
          ch == '}' -> if (f.depth == 0 && stack.size > 1) { stack.removeLast(); i++; continue } else f.depth--
        }
      } else {
        val raw = f.kind == 'r'
        when {
          !raw && ch == '\\' -> { f.buf.append(ch).append(at(1)); i += 2; continue }
          ch == '$' && at(1) == '{' -> { stack.addLast(Frame('c')); i += 2; continue }
          raw && src.startsWith("\"\"\"", i) -> { stack.removeLast(); out += f.line to f.buf.toString(); i += 3; continue }
          !raw && ch == '"' -> { stack.removeLast(); out += f.line to f.buf.toString(); i++; continue }
          else -> f.buf.append(ch)
        }
      }
      if (ch == '\n') line++
      i++
    }
    return out
  }
}
