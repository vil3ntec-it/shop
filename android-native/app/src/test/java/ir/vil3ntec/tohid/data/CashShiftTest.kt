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

  @Test
  fun `فروش و مرجوعیِ اینترنتیِ سرور (سریِ W، کانالِ online) پولِ دخل نیست`() {
    var d = ShopData()
    d = (CashShift.open(d, 1000.0, "", "2026-10-19", 100, ::newId) as CashShift.Result.Ok).data
    d = d.copy(
      sales = listOf(
        Sale(id = "s1", finalTotal = 500.0, createdAt = 110),                                       // نقدیِ صندوق
        //  پرداختِ آنلاین و نقد هنگامِ تحویل (دستِ پیک تا تسویه) — هیچ‌کدام در دخل نیست
        Sale(id = "w-o1", finalTotal = 900.0, paymentMethod = "online", paidAmount = 900.0,
          series = "W", channel = "online", createdAt = 120),
        Sale(id = "w-o2", finalTotal = 700.0, paymentMethod = "cod", paidAmount = 700.0,
          series = "W", channel = "online", createdAt = 130),
        //  فاکتورِ پیش از این اصلاح با `paymentMethod = cash` هم بیرون می‌ماند (سری W)
        Sale(id = "w-o3", finalTotal = 300.0, paymentMethod = "cash", paidAmount = 300.0, series = "W", createdAt = 131),
        //  فقط کانال (بی سری) هم کافی است
        Sale(id = "w-o4", finalTotal = 50.0, channel = "online", createdAt = 132),
      ),
      saleReturns = listOf(
        SaleReturn(id = "w-o1-1-r", saleId = "w-o1", saleItemId = "w-o1-1", amount = 900.0, createdAt = 140, channel = "online"),
        //  ردیفِ سرور پیش از نشانِ `channel`: شناسهٔ ثابتِ `w-…` بر فاکتورِ اینترنتی
        SaleReturn(id = "w-o2-1-r", saleId = "w-o2", saleItemId = "w-o2-1", amount = 700.0, createdAt = 141),
        //  مرجوعیِ دستیِ همین صندوق برای فاکتورِ W: نقد از دخل رفت ⇒ کم می‌شود
        SaleReturn(id = "01JDEVICERETURN0000000000", saleId = "w-o3", amount = 100.0, createdAt = 142),
        SaleReturn(id = "r-pos", saleId = "s1", amount = 20.0, createdAt = 143),
      ),
    )
    val t = CashShift.totalsOf(d, CashShift.current(d)!!, 200)
    assertEquals(500.0, t.sales, 1e-9)
    assertEquals(120.0, t.returns, 1e-9)
    //  ۱۰۰۰ + ۵۰۰ − ۱۰۰ − ۲۰
    assertEquals(1380.0, t.expected, 1e-9)
    assertTrue(CashShift.isOnline(d.sales[1]))
    assertTrue(!CashShift.isOnline(d.sales[0]))
  }
}
