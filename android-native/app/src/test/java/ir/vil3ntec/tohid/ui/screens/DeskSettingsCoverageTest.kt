package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  تنظیماتِ کامپیوتر (`DeskSettings`) همان `SettingsScreen` ِ گوشی را می‌کشد، ولی `SettingsSection`
 *  فقط بخشی را رسم می‌کند که عنوانش در فهرستِ `AppRoot` است (`if (pick != title) return`). بخشِ
 *  جاافتاده روی کامپیوتر هرگز روشن‌شدنی نیست — همان که سقفِ اعتبار (#451) و قاعده‌های پایانِ فاکتور
 *  (#503) را روی صندوقِ کامپیوتر کدِ مرده کرده بود.
 *
 *  هر `SettingsSection(…)` ِ برنامه (هر پرونده) با آن فهرست سنجیده می‌شود: یا در فهرست است یا در
 *  [notOnDesktop] با دلیل؛ و هر ردیفِ فهرست بخشی واقعی است (ردیفِ بی بخش صفحهٔ خالی می‌دهد).
 *  آزمونِ متنی است چون `pick` در ترکیب سنجیده می‌شود و آزمونِ JVM آن را نمی‌سازد.
 */
class DeskSettingsCoverageTest {

  private val root: File =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map(::File).firstOrNull { File(it, "ir/vil3ntec/tohid/ui/AppRoot.kt").isFile }
      ?: error("ریشهٔ کد پیدا نشد (cwd=${File(".").absoluteFile})")

  /** بخش‌هایی که عمداً روی کامپیوتر نیستند — هر کدام با دلیل */
  private val notOnDesktop = mapOf(
    "StrSettings.cameraScanner" to "کامپیوتر دوربین ندارد: بارکدخوانِ USB/بلوتوث یا گوشیِ جفت‌شده (DeskSaleScreen)",
  )

  /** `ir.vil3ntec.tohid.ui.screens.X` و `X` یکی‌اند: پیشوندِ بسته (بخش‌های کوچک‌حرف) کنار می‌رود */
  private fun norm(expr: String): String = expr.trim().replace(Regex("""^(?:[a-z][a-z0-9_]*\.)+"""), "")

  /** عنوانِ هر بخشِ تنظیمات ⇒ پرونده‌اش */
  private fun sections(): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val call = Regex("""(?<![\w])SettingsSection\(""")
    val title = Regex("""\btitle\s*=\s*([^,\n]+),""")
    root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
      val text = f.readText()
      for (m in call.findAll(text)) {
        if (text.substring(maxOf(0, m.range.first - 4), m.range.first) == "fun ") continue
        val window = text.substring(m.range.last, minOf(text.length, m.range.last + 600))
        val t = title.find(window) ?: error("${f.name}: SettingsSection بی `title =`")
        out[norm(t.groupValues[1])] = f.name
      }
    }
    return out
  }

  private fun deskList(): List<String> {
    val app = File(root, "ir/vil3ntec/tohid/ui/AppRoot.kt").readText()
    val from = app.indexOf("DeskSettings(")
    val to = app.indexOf("{ SettingsScreen(", from)
    assertTrue("فهرستِ DeskSettings در AppRoot پیدا نشد", from >= 0 && to > from)
    return Regex("""([\w.]+)\s+to\s+[\w.]*Icons\.Filled\.\w+""").findAll(app.substring(from, to)).map { norm(it.groupValues[1]) }.toList()
  }

  @Test fun `هر بخشِ تنظیماتِ گوشی روی کامپیوتر هم جایی دارد — یا استثنای با دلیل`() {
    val phone = sections()
    assertTrue("بخش‌های تنظیمات پیدا نشدند", phone.size >= 15)
    val desk = deskList().toSet()
    val missing = phone.filterKeys { it !in desk && it !in notOnDesktop }
    assertTrue("روی کامپیوتر روشن‌شدنی نیستند (به فهرستِ DeskSettings ِ AppRoot بیفزایید یا با دلیل استثنا کنید): $missing", missing.isEmpty())
  }

  @Test fun `هر ردیفِ فهرستِ کامپیوتر بخشی واقعی است و استثناها کهنه نیستند`() {
    val phone = sections().keys
    val desk = deskList()
    assertTrue("ردیفِ تکراری: $desk", desk.size == desk.toSet().size)
    val dead = desk.filter { it !in phone }
    assertTrue("ردیفِ بی بخش (صفحهٔ خالی): $dead", dead.isEmpty())
    val stale = notOnDesktop.keys.filter { it !in phone || it in desk }
    assertTrue("استثنای کهنه: $stale", stale.isEmpty())
    assertTrue(notOnDesktop.values.all { it.isNotBlank() })
  }

  @Test fun `سقفِ اعتبار و قاعده‌های پایانِ فاکتور روی کامپیوتر روشن‌شدنی‌اند`() {
    val desk = deskList()
    for (x in listOf("StrShell.creditGate", "StrSale.checkoutRulesTitle")) assertTrue("$x در فهرستِ کامپیوتر نیست", x in desk)
  }
}
