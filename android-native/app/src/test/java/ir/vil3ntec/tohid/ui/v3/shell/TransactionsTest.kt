package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionsTest {
  private val d = ShopData(
    products = listOf(Product("p1", name = "چای", unit = "بسته"), Product("p2", name = "قند")),
    debtors = listOf(Debtor("d1", name = "احمد")),
    suppliers = listOf(Supplier("s1", name = "پخش‌کنندهٔ کابل")),
    sales = listOf(
      Sale("a", invoiceNumber = 1, finalTotal = 500.0, createdAt = 100, date = "2026-10-20"),
      Sale("b", invoiceNumber = 2, finalTotal = 900.0, debtorId = "d1", createdAt = 300, date = "2026-10-21", channel = "online"),
      Sale("c", invoiceNumber = 3, finalTotal = 50.0, createdAt = 50, date = "2026-10-19", status = "cancelled"),
    ),
    saleItems = listOf(
      SaleItem("i1", saleId = "a", productId = "p1", quantity = 2.0, unitPrice = 150.0, totalPrice = 300.0),
      SaleItem("i2", saleId = "a", productId = "p2", quantity = 1.0, unitPrice = 200.0, totalPrice = 200.0, returnedQty = 1.0),
    ),
    purchases = listOf(
      Purchase("u1", productId = "p1", supplierId = "s1", quantity = 10.0, purchasePrice = 100.0, totalAmount = 1000.0, createdAt = 200, date = "2026-10-20", invoiceNo = 7),
      Purchase("u2", productId = "p2", supplierId = "s1", quantity = 5.0, purchasePrice = 60.0, totalAmount = 300.0, createdAt = 210, date = "2026-10-20", invoiceNo = 7),
      Purchase("u3", productId = "p2", quantity = 1.0, totalAmount = 60.0, createdAt = 10, date = "2026-10-18"),
    ),
  )

  @Test
  fun `فروش، سفارش و خرید یک فهرست، تازه‌ترین بالا`() {
    val all = Transactions.list(d)
    assertEquals(listOf("s:b", "p:u1", "s:a", "s:c", "p:u3"), all.map { it.key })
    assertEquals(Transactions.Kind.ONLINE, all[0].kind)
    assertEquals("احمد", all[0].who)
    assertTrue(all[0].incoming)
    assertFalse(all[1].incoming)
    assertTrue(all[3].cancelled)
  }

  @Test
  fun `خریدِ چندردیفه یک تراکنش با جمع و تأمین‌کننده`() {
    val p = Transactions.list(d, Transactions.Kind.PURCHASE).first()
    assertEquals(1300.0, p.amount, 0.0)
    assertEquals(2, p.itemCount)
    assertEquals("پخش‌کنندهٔ کابل", p.who)
    assertEquals("purchasing/suppliers/s1", p.route)
    assertEquals(listOf("چای", "قند"), Transactions.lines(d, p).map { it.name })
  }

  @Test
  fun `اقلامِ فاکتور با قیمتِ همان لحظه و مرجوعی`() {
    val a = Transactions.list(d).first { it.key == "s:a" }
    assertEquals("sales/a", a.route)
    assertEquals(2, a.itemCount)
    val lines = Transactions.lines(d, a)
    assertEquals(listOf("چای", "قند"), lines.map { it.name })
    assertEquals("بسته", lines[0].unit)
    assertEquals(150.0, lines[0].unitPrice, 0.0)
    assertEquals(1.0, lines[1].returned, 0.0)
  }

  @Test
  fun `بی دیدنِ عددهای مالی خریدی نمی‌آید`() {
    assertTrue(Transactions.list(d, purchases = false).none { it.kind == Transactions.Kind.PURCHASE })
  }

  @Test
  fun `امروز فقط تراکنش‌های همین روز، حداکثر n تا`() {
    assertEquals(listOf("p:u1", "s:a"), Transactions.today(d, "2026-10-20").map { it.key })
    assertEquals(listOf("s:a"), Transactions.today(d, "2026-10-20", purchases = false).map { it.key })
    assertEquals(listOf("p:u1"), Transactions.today(d, "2026-10-20", n = 1).map { it.key })
    assertTrue(Transactions.today(d, "2026-10-25").isEmpty())
  }
}
