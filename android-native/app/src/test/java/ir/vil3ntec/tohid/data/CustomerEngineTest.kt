package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** پروندهٔ مشتری — صورت‌حساب با فاکتورِ پشتِ هر ردیف، فاکتورها و اقلام، خریدارِ نقدی هم */
class CustomerEngineTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)
  private var seq = 0
  private fun newId() = "x${seq++}"
  private val d0 = ShopData(
    products = listOf(rice),
    debtors = listOf(Debtor(id = "c1", name = "احمد"), Debtor(id = "c2", name = "نقدی‌خر")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 100.0)),
  )

  private fun sell(d: ShopData, who: String, qty: Double, credit: Boolean, day: String) =
    (SalesEngine.record(
      d, listOf(SalesEngine.CartLine("p1", qty)),
      SalesEngine.Checkout(payment = if (credit) SalesEngine.Payment.CREDIT else SalesEngine.Payment.CASH, debtorId = who),
      day, 0, ::newId,
    ) as SalesEngine.Result.Ok).data

  @Test fun `نسیه و نقدی هر دو در پرونده و ردیفِ صورت‌حساب به فاکتور وصل است`() {
    var d = sell(d0, "c1", 2.0, credit = true, day = "2026-10-01")
    d = sell(d, "c2", 1.0, credit = false, day = "2026-10-02")
    d = sell(d, "c1", 1.0, credit = false, day = "2026-10-03")

    val l = CustomerEngine.ledger(d, "c1")
    assertEquals(1, l.size)
    assertEquals(600.0, l[0].give, 0.0)
    assertEquals(d.sales.first { it.debtorId == "c1" && it.paymentMethod == "credit" }.id, l[0].saleId)

    val s1 = CustomerEngine.summary(d, d.debtors[0])
    assertEquals(600.0, s1.balance, 0.0)
    assertEquals(900.0, s1.bought, 0.0)
    assertEquals(2, s1.invoices)
    assertEquals("2026-10-03", s1.lastPurchase)

    //  خریدارِ نقدی بی‌قرض هم پرونده دارد
    val s2 = CustomerEngine.summary(d, d.debtors[1])
    assertEquals(0.0, s2.balance, 0.0)
    assertEquals(1, s2.invoices)
    assertNull(s2.debtDays)

    assertEquals(listOf("2026-10-03", "2026-10-01"), CustomerEngine.invoices(d, "c1").map { it.date })
    assertEquals(listOf(1.0, 2.0), CustomerEngine.items(d, "c1").map { it.quantity })
    assertEquals("برنج", CustomerEngine.items(d, "c1")[0].name)
  }

  @Test fun `برگه‌های اکسل`() {
    val d = sell(d0, "c1", 2.0, credit = true, day = "2026-10-01")
    val led = CustomerEngine.ledgerSheet(d, d.debtors[0], "2026-10-12")
    assertEquals(listOf("1", "2026-10-01"), led.rows[1].take(2))
    assertEquals("600", led.rows[1][6])
    val inv = CustomerEngine.invoicesSheet(d, d.debtors[0], "2026-10-12")
    assertEquals("نسیه", inv.rows[1][8])
    assertEquals(2, CustomerEngine.itemsSheet(d, d.debtors[0], "2026-10-12").rows.size)
    assertEquals(12, CustomerEngine.invoiceNumberOf("فروش نسیه — فاکتور #12"))
  }
}
