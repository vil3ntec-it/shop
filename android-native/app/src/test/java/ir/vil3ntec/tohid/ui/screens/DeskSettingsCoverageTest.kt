package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  هر بخشِ تنظیمات روی کامپیوتر هم جایی دارد. بخشِ جاافتاده روی کامپیوتر هرگز روشن‌شدنی نیست — همان که سقفِ
 *  اعتبار (#451) و قاعده‌های پایانِ فاکتور (#503) را روی صندوقِ کامپیوتر کدِ مرده کرده بود.
 *
 *  رابطِ قدیم (`AppRootLegacy` ⇐ `DeskSettingsLegacy`، فهرستِ دستی) ۱۴۰۵/۰۸/۱۱ پاک شد؛ تنظیماتِ کامپیوتر حالا
 *  همان نسخهٔ ۳ است: ستونِ کناریِ `DeskSettingsScreen` از `SettingsLayout.deskSections` و صفحهٔ هر بخش از
 *  `SettingsPages` (هر بخش دقیقاً یک صفحه: `SettingsLayoutTest`). پس این‌جا همان قاعده سنجیده می‌شود: هر بخش یا در
 *  ستونِ کامپیوتر است یا در [notOnDesktop] با دلیل، و هیچ ردیفِ کامپیوتر بخشِ ساختگی نیست.
 */
class DeskSettingsCoverageTest {

  /** بخش‌هایی که عمداً روی کامپیوتر نیستند — هر کدام با دلیل */
  private val notOnDesktop = mapOf(
    SettingsLayout.CAMERA to "کامپیوتر دوربین ندارد: بارکدخوانِ USB/بلوتوث یا گوشیِ جفت‌شده (DeskSaleScreen)",
    SettingsLayout.NAV_BAR to "نوارِ پایینِ گوشی؛ کامپیوتر ستونِ کناری دارد",
  )

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test fun `هر بخشِ تنظیمات روی کامپیوتر هم جایی دارد — یا استثنای با دلیل`() {
    val all = SettingsLayout.sections
    assertTrue("بخش‌های تنظیمات پیدا نشدند", all.size >= 15)
    val desk = SettingsLayout.deskSections.toSet()
    val missing = all.filter { it !in desk && it !in notOnDesktop }
    assertTrue("روی کامپیوتر روشن‌شدنی نیستند (به ستونِ کامپیوتر بیاورید یا با دلیل استثنا کنید): $missing", missing.isEmpty())
    //  ترتیبِ ستون همان ترتیبِ گروه‌هاست و بخشِ تازه بی‌راه نمی‌ماند
    assertEquals(SettingsLayout.deskSections, SettingsLayout.deskOrder(SettingsLayout.deskSections))
  }

  @Test fun `هر ردیفِ ستونِ کامپیوتر بخشی واقعی است و استثناها کهنه نیستند`() {
    val all = SettingsLayout.sections.toSet()
    val desk = SettingsLayout.deskSections
    assertTrue("ردیفِ تکراری: $desk", desk.size == desk.toSet().size)
    val dead = desk.filter { it !in all }
    assertTrue("ردیفِ بی بخش (صفحهٔ خالی): $dead", dead.isEmpty())
    assertEquals("استثناها همان بخش‌های فقط‌گوشی‌اند", SettingsLayout.DESK_HIDDEN, notOnDesktop.keys)
    val stale = notOnDesktop.keys.filter { it !in all || it in desk }
    assertTrue("استثنای کهنه: $stale", stale.isEmpty())
    assertTrue(notOnDesktop.values.all { it.isNotBlank() })
    //  ستونِ کامپیوتر همان فهرستِ `SettingsLayout` را می‌کشد، نه فهرستِ دستی
    val screen = src("ui/v3/settings/DeskSettingsScreen.kt")
    assertTrue(screen.contains("SettingsLayout."))
    assertTrue("فهرستِ دستیِ رابطِ قدیم برگشته است", !src("ui/AppRoot.kt").contains("DeskSettings("))
  }

  @Test fun `سقفِ اعتبار و قاعده‌های پایانِ فاکتور روی کامپیوتر روشن‌شدنی‌اند`() {
    for (x in listOf(SettingsLayout.CREDIT_GATE, SettingsLayout.CHECKOUT_RULES)) {
      assertTrue("$x در ستونِ کامپیوتر نیست", x in SettingsLayout.deskSections)
      assertTrue("$x روی کامپیوتر پنهان است", SettingsLayout.visible(x, desktop = true, serverOn = emptySet(), featuresOn = emptySet()))
    }
  }
}
