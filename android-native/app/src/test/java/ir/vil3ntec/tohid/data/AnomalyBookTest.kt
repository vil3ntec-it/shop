package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.risk.Anomalies
import ir.vil3ntec.tohid.core.risk.Anomalies.Kind
import ir.vil3ntec.tohid.core.risk.CashierAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI-08 — دفترِ دکان ⇐ ورودیِ `Anomalies`؛ تنظیمِ خاموش ⇒ هیچ (همان کادرِ RSK-10) */
class AnomalyBookTest {
  private val today = "2026-10-26"
  private val t = CivilDays.jdnOfIso(today)!!

  /** کابل +۴:۳۰؛ دقیقهٔ محلیِ [m] در روزی دور از صفر */
  private val offset = 270
  private fun at(m: Int): Long = (1440L * 20_000 + m - offset) * 60_000

  private val d = ShopData(
    products = listOf(Product(id = "p1", name = "چای", unit = "عدد", purchasePrice = 50.0)),
    sales = listOf(
      Sale(id = "s1", invoiceNumber = 7, date = today, seller = "ali", finalTotal = 80.0, createdAt = at(150)),
      Sale(id = "s2", invoiceNumber = 8, date = today, seller = "sara", finalTotal = 1_000.0, createdAt = at(720)),
      Sale(id = "gone", invoiceNumber = 9, date = today, seller = "ali", status = "cancelled", finalTotal = 10.0, createdAt = at(180)),
    ),
    saleItems = listOf(
      //  ۲ × ۵۰ = ۱۰۰ ؋ بها، فروش ۸۰ ⇒ زیرِ بها
      SaleItem(id = "i1", saleId = "s1", productId = "p1", quantity = 2.0, totalPrice = 80.0, purchasePrice = 50.0),
      SaleItem(id = "i2", saleId = "s2", productId = "p1", quantity = 20.0, totalPrice = 1_000.0, purchasePrice = 50.0),
      //  تخفیفِ خودکار و فاکتورِ لغوشده بیرون
      SaleItem(id = "i3", saleId = "s2", productId = "p1", quantity = 1.0, totalPrice = 0.0, purchasePrice = 50.0, promo = listOf(SalePromo(id = "x", discountMinor = 5_000))),
      SaleItem(id = "i4", saleId = "gone", productId = "p1", quantity = 1.0, totalPrice = 10.0, purchasePrice = 50.0),
    ),
    saleReturns = listOf(SaleReturn(id = "r1", saleId = "s2", amount = 500.0, date = today)),
    stockMovements = listOf(
      //  ۳۰ × ۵۰ = ۱٬۵۰۰ ؋ > ۱٬۰۰۰ ⇒ یافته؛ افزایش و «فروش» نه
      StockMovement(id = "m1", productId = "p1", type = "adjustment", qty = -30.0, date = today, userId = "sara"),
      StockMovement(id = "m2", productId = "p1", type = "adjustment", qty = 40.0, date = today),
      StockMovement(id = "m3", productId = "p1", type = "sale", qty = -90.0, date = today),
    ),
    //  صندوق: اولِ صندوق ۱٬۰۰۰، هیچ نقدی در پنجره، شمرده ۸۰۰ ⇒ کسریِ ۲۰۰
    auditLog = listOf(
      AuditEntry(id = "o1", type = CashShift.OPEN, date = today, amount = 1_000.0, createdAt = 1_000, by = "ali"),
      AuditEntry(id = "c1", type = CashShift.CLOSE, date = today, refId = "o1", amount = 800.0, createdAt = 2_000, by = "ali"),
    ),
  )

  private val alerts = listOf(CashierAlerts.Alert("ali", CashierAlerts.Kind.VOID, 6, 10, 2, 20, 600))

  @Test fun `تنظیمِ خاموش هیچ نمی‌سازد`() {
    assertNull(AnomalyBook.view(false, d, today, today, alerts, offset, today))
  }

  @Test fun `روشن همهٔ قاعده‌ها و آمار را از دفتر می‌سازد`() {
    val all = AnomalyBook.view(true, d, today, today, alerts, offset, today)!!
    assertEquals(
      setOf(Kind.CASHIER_VOID, Kind.SHIFT_DIFF, Kind.STOCK_LOSS, Kind.BELOW_COST, Kind.ODD_HOUR, Kind.DAY_RETURNS, Kind.DAY_ADJUST),
      all.map { it.kind }.toSet(),
    )
    val below = all.single { it.kind == Kind.BELOW_COST }
    assertEquals("s1", below.ref)
    assertEquals(2_000L, below.evidence["loss"])
    assertTrue(below.reason.startsWith("فاکتورِ #7 (ali)"))
    val odd = all.single { it.kind == Kind.ODD_HOUR }
    assertEquals("ali", odd.subject)
    assertEquals(150L, odd.evidence["firstMinute"])
    assertEquals(1L, odd.evidence["count"])
    val shift = all.single { it.kind == Kind.SHIFT_DIFF }
    assertEquals(-20_000L, shift.evidence["diff"])
    assertEquals(150_000L, all.single { it.kind == Kind.STOCK_LOSS }.evidence["value"])
    //  برگشتیِ ۵۰۰ ؋ در ۳۰ روزِ بی برگشت ⇒ «بالا»؛ پهنای کمینه ۲٪ ِ فروشِ روزانه، دست‌کم ۱ ؋
    val ret = all.single { it.kind == Kind.DAY_RETURNS }
    assertEquals(t, ret.dayJdn)
    assertEquals(Anomalies.Severity.HIGH, ret.severity)
    //  کمِ «اصلاح» ِ ۱٬۵۰۰ ؋ در روزی که بقیهٔ ۳۰ روز صفر است ⇒ بیرون از معمول هم هست
    assertEquals(150_000L, all.single { it.kind == Kind.DAY_ADJUST }.evidence["value"])
    assertTrue(all.all { it.reason.endsWith(Anomalies.REVIEW) })
  }

  @Test fun `بازه و پایهٔ آمار`() {
    val (input, policy) = AnomalyBook.prepare(d, "0000-01-01", today, emptyList(), offset, today)
    //  «همهٔ تاریخ‌ها» تا یک سال؛ پایهٔ آمار همان بازه (بزرگ‌تر از ۳۰ روز)
    assertEquals(t - AnomalyBook.MAX_RANGE_DAYS + 1, input.fromJdn)
    assertEquals(input.fromJdn, input.series.first().startJdn)
    assertEquals(100L, policy.minSpreadMinor)
    //  یک روز ⇒ پایهٔ ۳۰ روزِ پیش از پایان؛ تخفیفِ هر فروشنده سریِ جدا ندارد وقتی تخفیفی نیست
    val (one, _) = AnomalyBook.prepare(d, today, today, emptyList(), offset, today)
    assertEquals(t, one.fromJdn)
    assertEquals(t - AnomalyBook.BASELINE_DAYS + 1, one.series.first().startJdn)
    assertEquals(AnomalyBook.BASELINE_DAYS, one.series.first().values.size)
    assertEquals(3, one.series.size)
  }
}
