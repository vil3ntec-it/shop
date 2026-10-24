package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** سنِ بدهیِ هر مشتری (CRM-04) و RFM (CRM-14) */
class CustomerInsightTest {

  private val asOf = "2026-10-19"

  private fun tx(id: String, who: String, give: Boolean, amount: Double, date: String) =
    DebtTransaction(id = id, debtorId = who, type = if (give) "give" else "receive", amount = amount, date = date)

  @Test fun `سنِ بدهی — اولین‌وارد‌اولین‌پرداخت، چهار خانه، پیش‌پرداخت جدا`() {
    val d = ShopData(
      debtors = listOf(Debtor(id = "c1", name = "احمد", phone = "+93700123456"), Debtor(id = "c2", name = "محمود"), Debtor(id = "c3", name = "بی‌حساب")),
      transactions = listOf(
        tx("t1", "c1", true, 1000.0, "2026-07-11"),   // ۱۰۰ روز
        tx("t2", "c1", true, 500.0, "2026-09-04"),    // ۴۵ روز
        tx("t3", "c1", false, 300.0, "2026-10-09"),   // از بردِ کهنه کم می‌شود
        tx("t4", "c1", true, 200.0, "2026-10-14"),    // ۵ روز
        tx("t5", "c2", false, 50.0, "2026-10-01"),    // پیش‌پرداخت
        tx("t6", "c1", true, 999.0, "2026-10-20"),    // پس از asOf ⇒ بیرون
      ),
    )
    val a = CustomerInsight.aging(d, asOf)
    assertEquals(listOf("c1", "c2"), a.lines.map { it.id })
    val c1 = a.lines[0]
    assertEquals(listOf(200.0, 500.0, 0.0, 700.0), c1.buckets)
    assertEquals(1400.0, c1.balance, 0.0)
    assertEquals(100L, c1.oldestDays)
    assertEquals(0.0, c1.credit, 0.0)
    val c2 = a.lines[1]
    assertEquals(-50.0, c2.balance, 0.0)
    assertEquals(50.0, c2.credit, 0.0)
    assertNull(c2.oldestDays)
    assertEquals(listOf(200.0, 500.0, 0.0, 700.0), a.totals)
    assertEquals(1350.0, a.balance, 0.0)
    //  همان ماندهٔ دفتر (بدونِ ردیفِ پس از asOf)
    assertEquals(ShopStore.debt(d, "c1") - 999.0, c1.balance, 0.0)

    assertEquals(c1, CustomerInsight.agingOf(d, "c1", asOf))
    assertNull(CustomerInsight.agingOf(d, "c3", asOf))

    val sheet = CustomerInsight.agingSheet(d, asOf)
    assertEquals("سن-بدهی-مشتریان-2026-10-19", sheet.name)
    assertEquals(listOf("نام", "شماره تماس", "مانده", "سنِ ۰ تا ۳۰", "سنِ ۳۱ تا ۶۰", "سنِ ۶۱ تا ۹۰", "سنِ بیش از ۹۰", "پیش‌پرداخت", "کهنه‌ترین (روز)"), sheet.rows[0])
    assertEquals(listOf("احمد", "+93700123456", "1400", "200", "500", "0", "700", "", "100"), sheet.rows[1])
    assertEquals(listOf("محمود", "", "-50", "0", "0", "0", "0", "50", ""), sheet.rows[2])
  }

  @Test fun `امتیازِ ۱ تا ۵ با صدکِ میانه؛ تنها و برابرها ۳`() {
    assertEquals(listOf(3), CustomerInsight.scores(listOf(10.0)))
    assertEquals(listOf(1, 2, 3, 4, 5), CustomerInsight.scores(listOf(1.0, 2.0, 3.0, 4.0, 5.0)))
    assertEquals(listOf(5, 1, 3, 4, 2), CustomerInsight.scores(listOf(50.0, 10.0, 30.0, 40.0, 20.0)))
    assertEquals(listOf(4, 4, 1), CustomerInsight.scores(listOf(5.0, 5.0, 1.0)))
    assertEquals(listOf(3, 3, 3), CustomerInsight.scores(listOf(2.0, 2.0, 2.0)))
    assertTrue(CustomerInsight.scores(emptyList()).isEmpty())
  }

  @Test fun `بخش‌ها از امتیاز و مرزهای مطلق`() {
    val S = CustomerInsight.Segment.values().associateBy { it.name }
    assertEquals(S["CHAMPION"], CustomerInsight.segmentOf(4, 4, 4, 3, 300))
    assertEquals(S["LOYAL"], CustomerInsight.segmentOf(3, 4, 2, 5, 300))
    assertEquals(S["NEW"], CustomerInsight.segmentOf(5, 1, 1, 2, 2))
    assertEquals(S["LOST"], CustomerInsight.segmentOf(1, 2, 1, 120, 300))
    assertEquals(S["AT_RISK"], CustomerInsight.segmentOf(2, 3, 3, 45, 300))
    assertEquals(S["SLEEPING"], CustomerInsight.segmentOf(2, 1, 1, 40, 400))
    assertEquals(S["REGULAR"], CustomerInsight.segmentOf(3, 3, 3, 40, 400))
    //  امتیازِ کم ولی خریدِ همین هفته ⇒ «از دست رفته» نیست
    assertEquals(S["REGULAR"], CustomerInsight.segmentOf(1, 1, 1, 6, 200))
  }

  @Test fun `RFM — لغوشده و بیرونِ بازه نه، مرجوعی کم، فاکتورِ مشتریِ ادغام‌شده به ماندگار`() {
    fun sale(id: String, who: String?, total: Double, date: String, status: String = "completed") =
      Sale(id = id, finalTotal = total, debtorId = who, date = date, status = status)
    val d = ShopData(
      debtors = listOf(
        Debtor(id = "a", name = "الف", phone = "+93700000001"), Debtor(id = "b", name = "ب"), Debtor(id = "c", name = "ج"),
        Debtor(id = "dd", name = "د"), Debtor(id = "e", name = "ه"), Debtor(id = "f", name = "و"),
        Debtor(id = "g", name = "الف (تکراری)", mergedInto = "a"),
      ),
      sales = listOf(
        sale("a1", "a", 500.0, "2026-08-20"), sale("a2", "a", 500.0, "2026-09-01"), sale("a3", "a", 500.0, "2026-09-15"),
        sale("a4", "a", 500.0, "2026-09-30"), sale("a5", "a", 500.0, "2026-10-10"), sale("a6", "a", 500.0, "2026-10-17"),
        sale("g1", "g", 100.0, "2026-10-01"),
        sale("b1", "b", 50.0, "2026-04-02"),
        sale("c1", "c", 100.0, "2026-10-09"),
        sale("d1", "dd", 200.0, "2026-05-22"), sale("d2", "dd", 200.0, "2026-06-15"), sale("d3", "dd", 200.0, "2026-07-20"),
        sale("d4", "dd", 200.0, "2026-08-20"),
        sale("e1", "e", 900.0, "2026-10-10", status = "cancelled"),
        sale("f1", "f", 900.0, "2025-09-01"),
        sale("n1", null, 700.0, "2026-10-18"),
      ),
      saleReturns = listOf(SaleReturn(id = "r1", saleId = "a2", amount = 100.0, date = "2026-09-02")),
    )
    val rows = CustomerInsight.rfm(d, asOf)
    assertEquals(listOf("a", "dd", "c", "b"), rows.map { it.id })
    val a = rows[0]
    assertEquals(7, a.frequency)
    assertEquals(3000.0, a.monetary, 0.0)
    assertEquals(2L, a.recencyDays)
    assertEquals("2026-08-20", a.firstPurchase)
    assertEquals("2026-10-17", a.lastPurchase)
    assertEquals("5-5-5", a.code)
    assertEquals(CustomerInsight.Segment.CHAMPION, a.segment)
    assertEquals("2-4-4" to CustomerInsight.Segment.AT_RISK, rows[1].code to rows[1].segment)
    assertEquals(60L, rows[1].recencyDays)
    assertEquals("4-2-2" to CustomerInsight.Segment.NEW, rows[2].code to rows[2].segment)
    assertEquals("1-2-1" to CustomerInsight.Segment.LOST, rows[3].code to rows[3].segment)
    assertEquals(200L, rows[3].recencyDays)

    assertEquals(
      listOf(CustomerInsight.Segment.CHAMPION to 1, CustomerInsight.Segment.NEW to 1, CustomerInsight.Segment.LOST to 1, CustomerInsight.Segment.AT_RISK to 1),
      CustomerInsight.segmentCounts(rows),
    )
    val sheet = CustomerInsight.rfmSheet(d, asOf)
    assertEquals(5, sheet.rows.size)
    assertEquals(listOf("الف", "+93700000001", "وفادارِ برتر", "5-5-5", "2", "7", "3000", "2026-08-20", "2026-10-17"), sheet.rows[1])
    //  بازهٔ کوتاه‌تر: فقط ۳۰ روزِ اخیر
    assertEquals(listOf("a", "c"), CustomerInsight.rfm(d, asOf, windowDays = 30).map { it.id })
  }
}
