package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** صندوق — نقدِ مورد انتظار هر پول را یک بار حساب می‌کند */
class CashShiftTest {

  private var seq = 0
  private fun newId(): String = "c${seq++}"

  @Test
  fun `باز، فروش نقدی و نسیه، رسید، مرجوعی، مصرف، بستن`() {
    var d = ShopData()
    d = (CashShift.open(d, 1000.0, "علی", "2026-10-12", 100, ::newId) as CashShift.Result.Ok).data
    assertTrue(CashShift.open(d, 5.0, "", "2026-10-12", 101, ::newId) is CashShift.Result.Failed)
    d = d.copy(
      sales = listOf(
        Sale(id = "s1", finalTotal = 500.0, createdAt = 110),                                  // نقدی
        Sale(id = "s2", finalTotal = 800.0, debtGiven = 600.0, paymentMethod = "credit", createdAt = 120), // ۲۰۰ پیش‌پرداخت
        Sale(id = "s3", finalTotal = 999.0, status = "cancelled", createdAt = 130),
        Sale(id = "old", finalTotal = 7777.0, createdAt = 50),                                  // پیش از باز شدن
      ),
      transactions = listOf(
        DebtTransaction("t1", "c", "receive", 300.0, createdAt = 140),
        DebtTransaction("t2", "c", "give", 600.0, createdAt = 120),
        DebtTransaction("t3", "c", "receive", 100.0, createdAt = 150),   // بخشِ بدهیِ مرجوعی
      ),
      saleReturns = listOf(SaleReturn(id = "r1", amount = 150.0, createdAt = 150)),  // ۵۰ نقد پس داده شد
      expenses = listOf(Expense(id = "e1", amount = 70.0, createdAt = 160)),
    )
    val t = CashShift.totalsOf(d, CashShift.current(d)!!, 200)
    //  ۱۰۰۰ + ۵۰۰ + ۲۰۰ + ۳۰۰ + ۱۰۰ − ۱۵۰ − ۷۰
    assertEquals(1880.0, t.expected, 1e-9)
    d = (CashShift.close(d, 1850.0, "", "علی", "2026-10-12", 210, ::newId) as CashShift.Result.Ok).data
    assertEquals(null, CashShift.current(d))
    assertTrue(d.auditLog.last().notes.contains("کسری 30"))
  }
}
