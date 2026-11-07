package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.DocGapsSheet
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RPT-17 — «شماره‌های جاافتاده» در «گزارش‌ها» ِ نسخهٔ ۳ (ممیزیِ دورِ هفتم: پس از پاک شدنِ رابطِ قدیم زبانه فقط در «گزارشِ
 *  تازه» ِ کامپیوتر مانده بود). اکنون گوشی (`ReportsScreen` ⇐ `ReportExtra.GAPS` ⇐ `gapsSection`) و کامپیوتر (`ReportsDesk` ⇐
 *  `DeskRep.GAPS` ⇐ کارت‌های `DocGapsSheet.report` و جدولِ `ReportCsv.of("gaps")`) هر دو، پشتِ همان تنظیمِ `DocGapsPref`؛
 *  اکسل/چاپِ هر دو همان برگهٔ `DocGapsSheet.sheet` است.
 */
class DocGapsV3WiringTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")

  /** A1: ۱، ۲، ۴ ⇒ ۳ جاافتاده؛ خرید: #۱ و #۳ ⇒ #۲ جاافتاده */
  private val d = ShopData(
    sales = listOf(
      Sale(id = "s1", invoiceNumber = 1, series = "A1", status = "completed", date = "2026-10-20"),
      Sale(id = "s2", invoiceNumber = 2, series = "A1", status = "completed", date = "2026-10-20"),
      Sale(id = "s4", invoiceNumber = 4, series = "A1", status = "completed", date = "2026-10-21"),
    ),
    purchases = listOf(Purchase(id = "p1", invoiceNo = 1, date = "2026-10-01"), Purchase(id = "p3", invoiceNo = 3, date = "2026-10-03")),
  )

  @Test
  fun `برگهٔ گزارشِ نسخهٔ ۳ همان برگهٔ شماره‌های جاافتاده است و جاافتاده‌ها را نشان می‌دهد`() {
    I18n.source = { Lang.FA }
    assertEquals("gaps", ReportExtra.GAPS.csv)
    val sheet = ReportCsv.of(ReportExtra.GAPS.csv, d, "2026-10-01", "2026-10-31", "2026-10-31")
    assertEquals(DocGapsSheet.sheet(d, "2026-10-31").rows, sheet.rows)
    val body = sheet.rows.drop(1)
    assertEquals(listOf("A1-3", "#2"), body.map { it[2] })
    assertTrue(body.all { it[5] == StrReports.statusMissing })
    //  کارت‌های بالای برگهٔ کامپیوتر از همان گزارش
    assertEquals(2, DocGapsSheet.report(d).missing)
  }

  @Test
  fun `زبانه در گوشی و کامپیوترِ نسخهٔ ۳، هر دو پشتِ همان تنظیم`() {
    val phone = src("ui/v3/analytics/ReportsScreen.kt")
    assertTrue(phone.contains("if (DocGapsPref.on(context)) add(ReportExtra.GAPS)"))
    assertTrue(phone.contains("ReportExtra.GAPS -> ReportList(list) { gapsSection(this, d, today) }"))
    assertTrue(phone.contains("fun sheet() = ReportCsv.of(tab.csv, d, from, to, today)"))
    val desk = src("ui/v3/analytics/ReportsDesk.kt")
    assertTrue(desk.contains("if (DocGapsPref.on(context)) add(DeskRep.GAPS)"))
    assertTrue(desk.contains("GAPS(\"gaps\", false)"))
    assertTrue(desk.contains("val g = remember(d) { DocGapsSheet.report(d) }"))
    assertTrue(desk.contains("ReportCsv.of(rep.key, d, from, to, today, reminderTemplate)"))
    //  مسیرِ «گزارش‌ها» ِ نسخهٔ ۳: کامپیوتر ⇐ ReportsDesk، گوشی ⇐ ReportsScreen
    val routes = src("ui/v3/analytics/AnalyticsRoutes.kt")
    assertTrue(routes.contains("if (desk) ReportsDesk(data, snackbar) { nav.open(it) }"))
    assertTrue(routes.contains("else ReportsScreen("))
  }
}
