package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  F0-03 — صورت‌حسابِ مشتری فاکتورِ درست را پیدا می‌کند وقتی دو دستگاه هر
 *  دو «#۵» زده‌اند: `A1-5` و `B1-5` دو فاکتورِ جدااند. شرح‌های پیش از سری
 *  («فاکتور #12») مثلِ قبل خوانده می‌شوند.
 */
class CustomerSeriesTest {

  @Test
  fun `شرح با سری و بی سری خوانده می‌شود`() {
    assertEquals(12, CustomerEngine.invoiceNumberOf("فروش نسیه — فاکتور #12"))
    assertEquals("12", CustomerEngine.invoiceLabelOf("فروش نسیه — فاکتور #12"))
    assertEquals(12, CustomerEngine.invoiceNumberOf("فروش نسیه — فاکتور #B1-12"))
    assertEquals("B1-12", CustomerEngine.invoiceLabelOf("فروش نسیه — فاکتور #B1-12"))
    assertNull(CustomerEngine.invoiceLabelOf("رسید نقدی"))
  }

  @Test
  fun `دو فاکتورِ هم‌شماره از دو دستگاه به فاکتورِ درست وصل می‌شوند`() {
    val d = ShopData(
      debtors = listOf(Debtor(id = "c1", name = "کریم")),
      sales = listOf(
        Sale(id = "s-phone", invoiceNumber = 5, series = "A1", debtorId = "c1", date = "2026-10-01"),
        Sale(id = "s-desk", invoiceNumber = 5, series = "B1", debtorId = "c1", date = "2026-10-02"),
      ),
      transactions = listOf(
        DebtTransaction(id = "t1", debtorId = "c1", type = "give", amount = 100.0, date = "2026-10-01", notes = "فروش نسیه — فاکتور #A1-5"),
        DebtTransaction(id = "t2", debtorId = "c1", type = "give", amount = 200.0, date = "2026-10-02", notes = "فروش نسیه — فاکتور #B1-5"),
      ),
    )
    val lines = CustomerEngine.ledger(d, "c1")
    assertEquals(listOf("s-phone", "s-desk"), lines.map { it.saleId })
    assertEquals(listOf("A1-5", "B1-5"), lines.map { it.invoiceLabel })
  }
}
