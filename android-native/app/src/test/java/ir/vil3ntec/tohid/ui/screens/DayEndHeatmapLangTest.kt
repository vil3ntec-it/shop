package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrDayEnd
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.data.DayEnd
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  LOC-06 — تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ (#۲۴): سرستون‌ها و برچسب‌های خروجیِ اکسلِ «پایانِ روز» (`DayEndModel`) و «ساعت و روز»
 *  (`HeatmapModel`) **به زبانِ برنامه**. خروجی همان `ReportFreshModel.exportRows` ِ دکمهٔ اکسل/چاپ است. هر چهار زبان: فارسی
 *  بایت‌به‌بایت همان متنِ پیشین، انگلیسی بی حرفِ فارسی، پشتو و دری با خطِ خودشان؛ زبان بی ساختنِ دوبارهٔ برنامه عوض می‌شود.
 */
class DayEndHeatmapLangTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private val day = "2026-10-31"
  private val sat1820 = 1_793_454_600_000L // شنبه ۲۰۲۶-۱۰-۳۱ ۱۸:۲۰ (کابل)
  private val d = ShopData(sales = listOf(Sale(id = "a", finalTotal = 100.0, createdAt = sat1820, date = day)))

  private fun dayEndRows(lang: Lang): List<List<String>> {
    I18n.source = { lang }
    val r = DayEnd.report(d, day, ReportClock.fixed(270, 0), sat1820 + 60_000)
    return ReportFreshModel.exportRows(DayEndModel.tables(r, 270))
  }

  private fun heatRows(lang: Lang): List<List<String>> {
    I18n.source = { lang }
    return ReportFreshModel.exportRows(HeatmapModel.tables(HeatmapModel.build(d, ReportBoard.Range(day, day), 270, 0)))
  }

  private val arabicScript = Regex("[\\u0600-\\u06FF]")

  /** متنِ پیشینِ دو مدل، پیش از جدول — فارسی نباید یک بایت عوض شود */
  private val faMethods = listOf("روش", "فروش", "برگشتی", "رسید", "خالص شیفت‌ها", "مورد انتظار", "شمرده", "اختلاف", "خالص دفتر", "بیرون از شیفت‌ها")
  private val faShifts = listOf("شیفت", "وضعیت", "اول صندوق", "خالص", "ورود نقد", "خروج نقد", "نقد مورد انتظار", "نقد شمرده", "اختلاف نقد", "نیازمند بررسی")

  @Test fun `فارسی بایت‌به‌بایت همان سرستون‌ها و برچسب‌های پیشین است`() {
    val rows = dayEndRows(Lang.FA)
    assertEquals("پایان روز به تفکیک روش", rows[0].single())
    assertEquals(faMethods, rows[1])
    assertTrue(rows.contains(listOf("شیفت‌های روز")))
    assertTrue(rows.contains(faShifts))
    assertEquals("جمع", rows.first { it.size == faMethods.size && it[0] == "جمع" }[0])
    val heat = heatRows(Lang.FA)
    assertEquals(listOf("شمارِ فروش: روز × ساعت"), heat[0])
    assertEquals("روز", heat[1][0])
    assertEquals("جمع", heat[1].last())
    assertTrue(heat.any { it.firstOrNull() == "شنبه" })
    assertTrue(heat.contains(listOf("ساعت", "شمارِ فروش", "خالصِ مبلغ")))
    assertTrue(heat.contains(listOf("روز", "شمارِ فروش", "خالصِ مبلغ")))
    assertEquals("پایان روز", DayEndModel.TAB.label)
    assertEquals("ساعت و روز", HeatmapModel.TAB.label)
  }

  @Test fun `انگلیسی سرستون و برچسبِ فارسی ندارد`() {
    val rows = dayEndRows(Lang.EN)
    assertEquals("End of day by payment method", rows[0].single())
    assertEquals(listOf("Method", "Sales", "Refunds", "Receipts", "Shifts net", "Expected", "Counted", "Variance", "Ledger net", "Outside shifts"), rows[1])
    assertTrue(rows.contains(listOf("Shift", "Status", "Opening float", "Net", "Cash in", "Cash out", "Expected cash", "Counted cash", "Cash variance", "Needs review")))
    val heat = heatRows(Lang.EN)
    assertEquals(listOf("Sales count: day × hour"), heat[0])
    assertTrue(heat.any { it.firstOrNull() == "Saturday" })
    assertTrue(heat.contains(listOf("Hour", "Sales count", "Net amount")))
    //  هیچ سرستون یا عنوانی با خطِ فارسی نیست (ردیف‌های داده — نامِ روشِ پرداخت — جدا و بیرون از این سنجش)
    val heads = listOf(rows[0], rows[1], rows.first { it.firstOrNull() == "Shift" }, listOf("Shifts of the day")) +
      heat.filter { it.firstOrNull() in setOf("Day", "Hour", "Sales count: day × hour", "By hour", "By weekday") }
    assertTrue(rows.contains(listOf("Shifts of the day")))
    assertEquals(6, heat.count { it.firstOrNull() in setOf("Day", "Hour", "Sales count: day × hour", "By hour", "By weekday") })
    assertFalse(heads.flatten().joinToString(" | "), heads.flatten().any { arabicScript.containsMatchIn(it) })
    assertEquals("End of day", DayEndModel.TAB.label)
    assertEquals("Hour & day", HeatmapModel.TAB.label)
    assertEquals("Total", heat[1].last())
  }

  @Test fun `پشتو و دری سرستونِ خودشان را دارند`() {
    val ps = dayEndRows(Lang.PS)
    assertEquals(listOf("طریقه", "پلور", "بېرته ورکړې", "رسیدونه", "د شفټونو خالص", "تمه شوې", "شمېرل شوې", "توپیر", "د دفتر خالص", "له شفټونو بهر"), ps[1])
    assertEquals(listOf("د پلور شمېر: ورځ × ساعت"), heatRows(Lang.PS)[0])
    assertEquals("ټولټال", heatRows(Lang.PS)[1].last())
    //  دری: همان جدول از لایهٔ واژه؛ خطِ عربی و همان شمارِ ستون، و همان مقدارِ جدول در همان زبان
    val prs = dayEndRows(Lang.PRS)
    assertEquals(faMethods.size, prs[1].size)
    assertTrue(prs[1].all { arabicScript.containsMatchIn(it) })
    I18n.source = { Lang.PRS }
    assertEquals(StrDayEnd.colMethod, prs[1][0])
    assertEquals(StrDayEnd.methodsTitle, prs[0].single())
    assertEquals(StrDayEnd.heatGridTitle, heatRows(Lang.PRS)[0].single())
  }

  @Test fun `زبان بی ساختنِ دوبارهٔ برنامه عوض می‌شود و برچسب‌های ریز و وضعیت هم`() {
    val fa = dayEndRows(Lang.FA)[1]
    val en = dayEndRows(Lang.EN)[1]
    assertNotEquals(fa, en)
    I18n.source = { Lang.EN }
    assertEquals("Today", DayEndModel.dayLabel(day, day) { it })
    assertEquals("Invoice", DayEndModel.docLabel(d, "sale:zzz"))
    assertEquals("Gift card", DayEndModel.docLabel(d, "gci:1"))
    assertEquals("2 shifts are still open (X)", StrDayEnd.statusOpen(2))
    assertEquals("Sunday", HeatmapModel.dayName(7))
    I18n.source = { Lang.FA }
    assertEquals("امروز", DayEndModel.dayLabel(day, day) { it })
    assertEquals("کارتِ هدیه", DayEndModel.docLabel(d, "gci:1"))
    assertEquals("2 شیفت هنوز باز است (X)", StrDayEnd.statusOpen(2))
    assertEquals("یکشنبه", HeatmapModel.dayName(7))
  }
}
