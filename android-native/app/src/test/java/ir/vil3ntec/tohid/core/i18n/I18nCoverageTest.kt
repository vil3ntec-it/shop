package ir.vil3ntec.tohid.core.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 *  پوششِ جدولِ نوشته‌ها (`Str*`) در انگلیسی و پشتو — قابلِ سنجش.
 *
 *  همهٔ ویژگی‌ها و تابع‌های هر جدول صدا زده می‌شوند و `I18n.audit` هر ردیف را
 *  می‌گیرد. ردیفی که انگلیسی یا پشتو ندارد **جاافتاده** است (صفحه فارسی‌اش را
 *  نشان می‌دهد)؛ فهرستش در خروجیِ آزمون چاپ می‌شود و شمارش فقط می‌تواند کم شود.
 */
class I18nCoverageTest {

  /** سقفِ جاافتاده‌ها — فقط کم شود، زیاد نه */
  private val maxMissing = mapOf(Lang.EN to 0, Lang.PS to 0)

  @After fun reset() {
    I18n.source = { Lang.FA }
    I18n.desktop = false
    I18n.audit = null
  }

  private class Row(val key: String, val entry: I18n.Entry, val outputs: Map<Lang, String>, val args: List<String>)

  private fun methods(table: Any): List<Method> =
    table.javaClass.declaredMethods
      .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
      .filter { it.returnType == String::class.java }
      .sortedBy { it.name }

  private fun keyOf(table: Any, m: Method): String {
    val n = m.name.removePrefix("get").let { if (m.parameterCount == 0) it.replaceFirstChar(Char::lowercaseChar) else m.name }
    return table.javaClass.simpleName + "." + n
  }

  /** آرگومانِ نگهبان: عددی (تا `pl` هم کار کند) و یکتا، تا جای خالیِ هر زبان سنجیده شود */
  private fun argsFor(m: Method): List<Any> = m.parameterTypes.mapIndexed { i, type ->
    when (type) {
      Int::class.javaPrimitiveType, Int::class.javaObjectType -> 7301 + i
      Long::class.javaPrimitiveType, Long::class.javaObjectType -> 7301L + i
      else -> (7301 + i).toString()
    }
  }

  private fun rows(): List<Row> {
    val out = ArrayList<Row>()
    for (table in I18nTables.all) for (m in methods(table)) {
      val args = argsFor(m)
      var entry: I18n.Entry? = null
      I18n.audit = { entry = it }
      val outputs = Lang.entries.associateWith { lang ->
        I18n.source = { lang }
        m.invoke(table, *args.toTypedArray()) as String
      }
      I18n.audit = null
      assertTrue("${keyOf(table, m)} از t/td نمی‌خواند", entry != null)
      out += Row(keyOf(table, m), entry!!, outputs, args.map { it.toString() })
    }
    return out
  }

  @Test
  fun `پوششِ هر زبان شمرده و چاپ می‌شود — جاافتاده از سقف بیشتر نیست`() {
    val all = rows()
    assertTrue("جدولی پیدا نشد", all.size > 500)
    val missEn = all.filter { it.entry.en == null }.map { it.key }
    val missPs = all.filter { it.entry.ps == null }.map { it.key }
    println("i18n: ${all.size} keys · en ${all.size - missEn.size}/${all.size} · ps ${all.size - missPs.size}/${all.size}")
    if (missEn.isNotEmpty()) println("i18n missing en: $missEn")
    if (missPs.isNotEmpty()) println("i18n missing ps: $missPs")
    assertTrue("انگلیسیِ جاافتاده: $missEn", missEn.size <= maxMissing.getValue(Lang.EN))
    assertTrue("پشتوی جاافتاده: $missPs", missPs.size <= maxMissing.getValue(Lang.PS))
  }

  @Test
  fun `جاافتاده یعنی فارسی — نه کرش و نه نامِ کلید`() {
    I18n.source = { Lang.EN }
    assertEquals("ذخیره", t("ذخیره", null, "خوندي کول"))
    I18n.source = { Lang.PS }
    assertEquals("ذخیره", t("ذخیره", "Save", null))
    assertEquals("خوندي کول", t("ذخیره", "Save", "خوندي کول"))
    //  خروجی همیشه یکی از نوشته‌های خودِ ردیف است: ترجمه، یا اگر نبود فارسی — هرگز نامِ کلید
    for (r in rows()) {
      val e = r.entry
      assertEquals(r.key, e.fa, r.outputs[Lang.FA])
      assertEquals(r.key, e.en ?: e.fa, r.outputs[Lang.EN])
      assertEquals(r.key, e.ps ?: e.fa, r.outputs[Lang.PS])
      for ((lang, text) in r.outputs) assertTrue("${r.key} در $lang خالی است", text.isNotBlank())
    }
  }

  @Test
  fun `جای خالیِ هر تابع در هر سه زبان پر می‌شود`() {
    for (r in rows()) for (arg in r.args) for ((lang, text) in r.outputs) {
      assertTrue("${r.key} ($lang) «$arg» را نشان نمی‌دهد: $text", text.contains(arg))
    }
  }

  @Test
  fun `انگلیسی حرفِ فارسی ندارد و پشتو با خطِ عربی است`() {
    val arabicLetter = Regex("[\\u0621-\\u064A\\u066E-\\u06D3\\u06FA-\\u06FF]")
    for (r in rows()) {
      val en = r.entry.en ?: continue
      assertFalse("${r.key}: انگلیسی حرفِ عربی/فارسی دارد: $en", arabicLetter.containsMatchIn(en))
      val ps = r.entry.ps ?: continue
      if (arabicLetter.containsMatchIn(r.entry.fa)) {
        assertTrue("${r.key}: پشتو به خطِ عربی نیست: $ps", arabicLetter.containsMatchIn(ps))
      }
    }
  }

  @Test
  fun `زبان بی بازکردنِ دوباره عوض می‌شود`() {
    I18n.source = { Lang.FA }
    assertEquals("انصراف", Str.cancel)
    I18n.source = { Lang.EN }
    assertEquals("Cancel", Str.cancel)
    I18n.source = { Lang.PS }
    assertEquals("لغوه", Str.cancel)
    assertEquals("3 ورځې", Str.days(3))
    assertEquals("1 ورځ", Str.days(1))
    I18n.source = { Lang.EN }
    assertEquals("1 day", Str.days(1))
    assertEquals("12 days", Str.days("12"))
  }

  /* ------------------------------ کامپیوتر ------------------------------ */

  @Test
  fun `نوشته‌ای که به گوشی و دوربین بند است نسخهٔ کامپیوتریِ انگلیسی و پشتو دارد`() {
    val desk = rows().filter { it.entry.enDesk != null || it.entry.psDesk != null }
    assertTrue("هیچ نوشتهٔ دوتایی پیدا نشد", desk.size >= 10)
    for (r in desk) {
      val e = r.entry
      assertNotEquals("${r.key}: انگلیسیِ کامپیوتر همان گوشی است", e.en, e.enDesk)
      assertNotEquals("${r.key}: پشتوی کامپیوتر همان گوشی است", e.ps, e.psDesk)
      assertTrue("${r.key}: انگلیسیِ کامپیوتر از کامپیوتر/سیستم/بارکدخوان نمی‌گوید: ${e.enDesk}",
        listOf("computer", "system", "scanner", "device").any { e.enDesk!!.contains(it, ignoreCase = true) })
      assertTrue("${r.key}: پشتوی کامپیوتر از کمپیوټر/سیستم/لوستونکی نمی‌گوید: ${e.psDesk}",
        listOf("کمپیوټر", "سیستم", "لوستونک", "وسیل").any { e.psDesk!!.contains(it) })
    }
    //  همان نوشته در حالتِ کامپیوتر
    I18n.desktop = true
    I18n.source = { Lang.EN }
    assertEquals("The computer's internet is off", StrNav.phoneOffline)
    I18n.desktop = false
    assertEquals("The phone's internet is off", StrNav.phoneOffline)
  }

  /**
   *  جدولِ `phrases` ِ `desktop/build.gradle.kts` فارسیِ همین جدول‌ها را هم
   *  پیش از کامپایلِ کامپیوتر برمی‌گرداند. هر ردیفی که فارسی‌اش یکی از آن
   *  عبارت‌ها را دارد ولی نسخهٔ کامپیوتریِ انگلیسی/پشتو ندارد، روی کامپیوتر
   *  به انگلیسی «phone» می‌گفت. (روی کامپیوتر، فارسی پیشاپیش برگشته و این
   *  آزمون چیزی برای گرفتن ندارد — سنجشِ اصلی در آزمونِ اندروید است.)
   */
  @Test
  fun `هر نوشتهٔ دارای عبارتِ جدولِ کامپیوتر با td نوشته شده`() {
    val phrases = DesktopPhrases.load().map { it.first }
    val bad = rows().filter { r -> r.entry.enDesk == null && phrases.any { r.entry.fa.contains(it) } }.map { it.key }
    assertTrue("این‌ها روی کامپیوتر از گوشی/دوربین می‌گویند؛ با td بنویسید: $bad", bad.isEmpty())
  }

  @Test
  fun `هر شیءِ Str در فهرستِ I18nTables هست`() {
    val dir = DesktopPhrases.repo().resolve("android-native/app/src/main/java/ir/vil3ntec/tohid/core/i18n")
    val declared = dir.listFiles().orEmpty().filter { it.extension == "kt" }
      .flatMap { f -> Regex("""^object (Str\w*)""", RegexOption.MULTILINE).findAll(f.readText()).map { it.groupValues[1] }.toList() }
      .toSet()
    assertEquals(declared, I18nTables.all.map { it.javaClass.simpleName }.toSet())
  }
}

/** خواندنِ جدولِ `phrases` از `desktop/build.gradle.kts` (مسیر از اندروید، کامپیوتر یا ریشهٔ مخزن) */
internal object DesktopPhrases {
  fun repo(): File {
    val candidates = listOfNotNull(System.getProperty("tohid.repo"), ".", "..", "../..", "../../..")
    return candidates.map { File(it).absoluteFile }.firstOrNull { File(it, "desktop/build.gradle.kts").isFile }
      ?: error("ریشهٔ مخزن پیدا نشد (cwd=${File(".").absoluteFile})")
  }

  fun load(): List<Pair<String, String>> {
    val text = File(repo(), "desktop/build.gradle.kts").readText()
    val block = text.substring(text.indexOf("val phrases"), text.indexOf("val androidSources"))
    val lit = """"((?:[^"\\]|\\.)*)""""
    return Regex("""$lit\s+to\s*\n?\s*$lit""").findAll(block)
      .map { unescape(it.groupValues[1]) to unescape(it.groupValues[2]) }.toList()
  }

  private fun unescape(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")
}
