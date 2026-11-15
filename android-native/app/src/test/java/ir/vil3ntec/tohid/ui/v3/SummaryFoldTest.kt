package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  ۶۵s (صاحب‌کار: «همه سربرگ‌ها وسط باشن و کشویی») — بخشِ بی‌رابطِ کارتِ خلاصه:
 *   • «بسته» ِ هر صفحه کلیدِ خودش را در انبارِ نمایشیِ همین دستگاه دارد؛ نبودن ⇒ باز؛ مقدارِ ناشناخته ⇒ باز؛
 *   • هر خلاصهٔ بالای صفحه (`V3Summary(`، `HeroFigure(`) در کدِ برنامه `collapseKey` دارد و کلیدها یکتا هستند
 *     (دو صفحه یک حالت را با هم عوض نکنند). رابط و جا در `SummaryCardUiTest` (کامپیوتر) سنجیده می‌شود.
 */
class SummaryFoldTest {

  private class Mem : ViewModePref.Store {
    val map = mutableMapOf<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
  }

  @Test fun `پیش‌فرض باز است و هر صفحه حالتِ خودش را دارد`() {
    val s = Mem()
    assertFalse(SummaryFold.folded(s, "expenses"))
    SummaryFold.save(s, "expenses", true)
    assertTrue(SummaryFold.folded(s, "expenses"))
    assertFalse("صفحهٔ دیگر دست نخورد", SummaryFold.folded(s, "sales"))
    SummaryFold.save(s, "expenses", false)
    assertFalse(SummaryFold.folded(s, "expenses"))
    assertEquals(mapOf("summary_folded:expenses" to "0"), s.map)
  }

  @Test fun `کلید پاک‌سازی می‌شود و مقدارِ ناشناخته یعنی باز`() {
    assertEquals("summary_folded:purchasing/suppliers", SummaryFold.key("purchasing/suppliers"))
    assertEquals("summary_folded:a_b", SummaryFold.key(" a b "))
    val s = Mem().apply { map["summary_folded:x"] = "yes" }
    assertFalse(SummaryFold.folded(s, "x"))
    //  انبارِ خراب صفحه را نمی‌اندازد
    val broken = object : ViewModePref.Store {
      override fun get(key: String): String? = error("disk")
      override fun put(key: String, value: String) = error("disk")
    }
    assertFalse(SummaryFold.folded(broken, "x"))
    SummaryFold.save(broken, "x", true)
  }

  private val main: File = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
    .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }

  /** متنِ آرگومان‌های هر فراخوانیِ [name]( در پرونده (پرانتزها جفت می‌شوند) */
  private fun calls(text: String, name: String): List<String> {
    val out = mutableListOf<String>()
    Regex("""(?<!\w)$name\(""").findAll(text).forEach { m ->
      if (text.substring(maxOf(0, m.range.first - 4), m.range.first).endsWith("fun ")) return@forEach
      var depth = 0
      var i = m.range.last
      while (i < text.length) {
        when (text[i]) { '(' -> depth++; ')' -> { depth--; if (depth == 0) break } }
        i++
      }
      out += text.substring(m.range.last + 1, i)
    }
    return out
  }

  @Test fun `هر خلاصهٔ بالای صفحه تاشوست و کلیدش یکتاست`() {
    val keys = mutableListOf<String>()
    val missing = mutableListOf<String>()
    main.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "V3Summary.kt" && it.name != "AnalyticsParts.kt" }.forEach { f ->
      val text = f.readText()
      (calls(text, "V3Summary") + calls(text, "HeroFigure")).forEach { args ->
        val key = Regex("""collapseKey = ([^,\n)]+)""").find(args)?.groupValues?.get(1)?.trim()
        if (key == null) missing += f.name else keys += key
      }
    }
    assertTrue("خلاصهٔ بی collapseKey: $missing", missing.isEmpty())
    assertTrue("دست‌کم ده خلاصهٔ تاشو: $keys", keys.size >= 10)
    assertEquals("کلیدِ تکراری: $keys", keys.size, keys.toSet().size)
  }
}
