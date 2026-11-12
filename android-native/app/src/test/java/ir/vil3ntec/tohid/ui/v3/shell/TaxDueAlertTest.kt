package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.i18n.StrTaxQuarter
import ir.vil3ntec.tohid.core.report.QuarterTax
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S49 / RPT-10 — یادآورِ سررسیدِ مالیاتِ سه‌ماهه روی خانهٔ نسخهٔ ۳. نوارِ پیشین (`TaxDueBanner`) هیچ فراخواننده‌ای
 *  نداشت و نسخهٔ ۳ فقط تنظیمِ «روزهای یادآوری» را داشت. حالا یک هشدارِ خانه (`HomeAlerts.Kind.TAX`): فقط با تنظیمِ
 *  روشن (`TaxQuarterPref.on`)، فقط برای نقشی که پولِ دکان را می‌بیند (`ShopRole.canSeeMoney`) و فقط درونِ پنجرهٔ
 *  `QuarterTax.reminder`؛ کارتِ کهربایی روی خانه (نه سرخ) و لمس ⇐ صفحهٔ «گزارشِ مالیاتِ سه‌ماهه» در تنظیمات.
 */
class TaxDueAlertTest {

  private fun s(y: Int, m: Int, d: Int): String = SolarHijri.fromSolar(y, m, d)!!

  /** سالِ مالی از حمل، سررسید ۱۵ ِ ماهِ پس از سه‌ماهه، یادآور از ۱۰ روز پیش (همان S49) */
  private val cfg = QuarterTax.Config(startMonth = 1, rateBp = 1_000, dueDay = 15, dueMonths = 1, remindDays = 10)

  private fun resolves(path: String) = assertNotNull("مسیرِ ثبت‌نشده: $path", V3Modules.table.resolve(path))

  @Test
  fun `یادآور فقط با تنظیمِ روشن، نقشِ پول‌بین و درونِ پنجره`() {
    val today = s(1405, 7, 5)
    assertEquals(QuarterTax.Due(QuarterTax.Quarter(1405, 2), s(1405, 7, 15), 10), HomeAlerts.taxDue(on = true, canSeeMoney = true, today = today, cfg = cfg))
    assertNull("تنظیمِ خاموش ⇒ هیچ", HomeAlerts.taxDue(on = false, canSeeMoney = true, today = today, cfg = cfg))
    assertNull("شاگرد پولِ دکان را نمی‌بیند", HomeAlerts.taxDue(on = true, canSeeMoney = false, today = today, cfg = cfg))
    assertNull("۱۱ روز پیش هنوز نه", HomeAlerts.taxDue(on = true, canSeeMoney = true, today = s(1405, 7, 4), cfg = cfg))
    assertNull("پس از سررسید نه", HomeAlerts.taxDue(on = true, canSeeMoney = true, today = s(1405, 7, 16), cfg = cfg))
    assertEquals(0, HomeAlerts.taxDue(on = true, canSeeMoney = true, today = s(1405, 7, 15), cfg = cfg)?.daysLeft)
  }

  @Test
  fun `هشدارِ خانه — کارتِ کهربایی روی خانه و مقصدِ تنظیمِ مالیات`() {
    val due = HomeAlerts.taxDue(on = true, canSeeMoney = true, today = s(1405, 7, 5), cfg = cfg)
    val a = HomeAlerts.build(HomeAlerts.Input(taxDue = due)).single()
    assertEquals(HomeAlerts.Kind.TAX, a.kind)
    assertEquals(StrTaxQuarter.reminderTitle(10), a.title)
    assertEquals(StrTaxQuarter.due(formatDate(s(1405, 7, 15))), a.body)
    assertEquals(StrTaxQuarter.open, a.action)
    assertEquals("settings/data/tax-quarter", a.route)
    assertEquals(ShellTargets.TAX_QUARTER, a.actionRoute)
    resolves(a.route)
    assertFalse("سرخ فقط برای مشکلِ واقعی", a.urgent)
    assertTrue("روی کارتِ خانه دیده می‌شود (جای نوارِ پیشین)", a.pressing)
    assertEquals(listOf(a), HomeAlerts.pressing(listOf(a)))
    //  روزِ سررسید
    val last = HomeAlerts.build(HomeAlerts.Input(taxDue = HomeAlerts.taxDue(on = true, canSeeMoney = true, today = s(1405, 7, 15), cfg = cfg))).single()
    assertEquals(StrTaxQuarter.reminderToday, last.title)
    //  بی یادآور (خاموش، شاگرد، بیرونِ پنجره) هیچ کارتی نیست
    assertTrue(HomeAlerts.build(HomeAlerts.Input(taxDue = null)).isEmpty())
  }

  @Test
  fun `ترتیب — پس از کالای تمام‌شده و پیش از قرض`() {
    val due = HomeAlerts.taxDue(on = true, canSeeMoney = true, today = s(1405, 7, 5), cfg = cfg)
    val a = HomeAlerts.build(HomeAlerts.Input(outOfStock = 1, overdueDebtors = 2, taxDue = due))
    assertEquals(listOf(HomeAlerts.Kind.STOCK, HomeAlerts.Kind.TAX, HomeAlerts.Kind.DEBTS), a.map { it.kind })
    //  کارتِ خانه: فوریِ سرخ اول می‌آید، یادآورِ مالیات «+۱» ِ همان کارت
    assertEquals(listOf(HomeAlerts.Kind.STOCK, HomeAlerts.Kind.TAX), HomeAlerts.pressing(a).map { it.kind })
  }

  @Test
  fun `مرکزِ هشدارها همان ردیف را با رنگِ هشدار نشان می‌دهد`() {
    val due = HomeAlerts.taxDue(on = true, canSeeMoney = true, today = s(1405, 7, 5), cfg = cfg)
    val g = ShopAlerts.build(HomeAlerts.build(HomeAlerts.Input(taxDue = due)), ShopAlerts.Extras())
    assertTrue(g.urgent.isEmpty())
    val item = g.rest.single()
    assertEquals(ShopAlerts.Kind.TAX, item.kind)
    assertEquals(V3Tone.WARNING, item.tone)
    assertEquals(ShellTargets.TAX_QUARTER, item.route)
    assertFalse("زنگ فقط برای فوری", ShopAlerts.bellDot(g, unseenNews = false))
  }

  @Test
  fun `خانه یادآور را از تنظیم و نقشِ همین دستگاه می‌خواند`() {
    fun src(rel: String) = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/$rel") }.first { it.isFile }.readText()
    val view = src("ui/v3/shell/HomeView.kt")
    assertTrue(view.contains("taxDue = rememberTaxDue(canSeeMoney, today)"))
    val read = src("ui/v3/shell/TaxDueAndroid.kt")
    assertTrue(read.contains("HomeAlerts.taxDue(on, canSeeMoney, today, cfg)"))
    assertTrue(read.contains("TaxQuarterPref.on(context)"))
    assertTrue(read.contains("TaxQuarterPref.config(context)"))
  }
}
