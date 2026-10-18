package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportCoreVectorsTest
import ir.vil3ntec.tohid.core.report.ReportLedger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  مرجعِ رفتارِ امروز: هستهٔ گزارش در برابرِ موتورهای واقعیِ برنامه، روی همان دفترِ
 *  آزمون‌های مرجع. هیچ موتوری عوض نشده؛ این آزمون فقط برابری‌ها را می‌پاید و
 *  تفاوت‌های عمدی را با عدد ثبت می‌کند (شرحش در PR).
 */
class ReportCoreParityTest {
  private val raw = Json.parseToJsonElement(ReportCoreVectorsTest.vectorFile().readText())
    .jsonObject.getValue("ledgers").jsonObject.getValue("kabul").jsonObject
  private val d: ShopData = Json { ignoreUnknownKeys = true }.decodeFromJsonElement(ShopData.serializer(), raw)
  private val l = ReportLedger.parse(raw)
  private fun minor(x: Double) = Math.round(x * 100)

  @Test
  fun `بی ساعت و با بهای ثبت‌شده همان ReportEngine_sales است`() {
    for ((from, to) in listOf("0000-01-01" to "9999-12-31", "2026-10-14" to "2026-10-14", "2026-10-07" to "2026-10-13")) {
      val old = ReportEngine.sales(d, from, to)
      val t = ReportCore.sales(l, from, to).totals
      assertEquals(from, old.count, t.invoices)
      assertEquals(from, minor(old.gross), t.gross)
      assertEquals(from, minor(old.discount), t.discount)
      assertEquals(from, minor(old.net), t.sales) // «net»ِ امروز = فروش پیش از برگشتی
      assertEquals(from, minor(old.returnAmount), t.returns)
      assertEquals(from, minor(old.grossProfit), t.grossProfit)
    }
  }

  @Test
  fun `ماندهٔ قرض‌دار همان ReportEngine_debtors و فروشِ هر کاربر همان bySeller`() {
    val core = ReportCore.debts(l, "9999-12-31").customers.rows.associateBy { it.id }
    ReportEngine.debtors(d).forEach { st -> assertEquals(st.debtor.id, minor(st.remaining), core[st.debtor.id]?.balance ?: 0L) }
    val users = ReportCore.sales(l, "2026-10-01", "2026-10-14").user.associateBy { it.key.ifEmpty { "نامعلوم" } }
    ReportEngine.bySeller(d, "2026-10-01", "2026-10-14").forEach { s ->
      assertEquals(s.seller, s.count, users.getValue(s.seller).invoices)
      assertEquals(s.seller, minor(s.net), users.getValue(s.seller).sales)
    }
  }

  /**
   *  تفاوتِ عمدی با DashboardEngine: داشبورد برگشتیِ هر فاکتور را از روزِ **فاکتور** کم
   *  می‌کند (پس عددِ روزِ بسته‌شده بعداً عوض می‌شود)؛ هسته آن را در روزِ **خودِ برگشتی**
   *  می‌نشاند، مثلِ ReportEngine.sales. برگشتیِ r1 (۱۴۰ افغانی، بها ۱۳۰) فاکتورِ ۱۳ام است.
   */
  @Test
  fun `داشبورد برگشتی را به روزِ فاکتور می‌برد، هسته به روزِ خودش`() {
    val now = java.time.Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()
    val b14 = DashboardEngine.board(d, "2026-10-14", 1, now)
    val c14 = ReportCore.today(l, "2026-10-14").today
    assertEquals(62950L, minor(b14.todaySales))
    assertEquals(19950L, minor(b14.profit.value))
    assertEquals(48950L, c14.netSales) // ۶۲۹.۵ − ۱۴۰
    assertEquals(18950L, c14.grossProfit) // ۱۹۹.۵ − (۱۴۰ − ۱۳۰)
    val b13 = DashboardEngine.board(d, "2026-10-13", 1, now)
    assertEquals(56000L, minor(b13.todaySales)) // ۷۰۰ − ۱۴۰
    assertEquals(70000L, ReportCore.today(l, "2026-10-13").today.netSales)
  }
}
