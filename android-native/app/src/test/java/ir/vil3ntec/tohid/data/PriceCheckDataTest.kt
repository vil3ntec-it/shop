package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  قیمت‌پرسی بی فروش (POS-15) — از دفتر: موجودی همان `ShopStore.stock`، انقضا همان FEFO ِ `ExpiryEngine`،
 *  آخرین خرید از `purchases` با نامِ تأمین‌کننده. هیچ چیز نوشته نمی‌شود.
 */
class PriceCheckDataTest {

  private val milk = Product(id = "milk", name = "شیر", unit = "دانه", salePrice = 50.0, purchasePrice = 40.0)
  private val d = ShopData(
    products = listOf(milk, Product(id = "tea", name = "چای", salePrice = 100.0)),
    suppliers = listOf(Supplier(id = "s1", name = "لبنیاتِ کابل"), Supplier(id = "s2", name = "پخشِ شمال")),
    purchases = listOf(
      Purchase(id = "p1", productId = "milk", supplierId = "s1", quantity = 10.0, purchasePrice = 38.0, date = "2026-10-01", createdAt = 1),
      Purchase(id = "p2", productId = "milk", supplierId = "s2", quantity = 6.0, purchasePrice = 41.0, date = "2026-10-25", createdAt = 2),
      Purchase(id = "p3", productId = "tea", supplierId = "s1", quantity = 1.0, purchasePrice = 70.0, date = "2026-10-30", createdAt = 3),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "milk", units = 10.0, date = "2026-10-01", expiryDate = "2026-10-29"),
      WarehouseEntry(id = "w2", productId = "milk", units = 6.0, date = "2026-10-25", expiryDate = "2026-11-10"),
    ),
    sales = listOf(Sale(id = "x1", date = "2026-10-26")),
    saleItems = listOf(SaleItem(id = "i1", saleId = "x1", productId = "milk", quantity = 7.0)),
  )

  @Test fun `قیمت، موجودی، آخرین خرید و انقضای FEFO`() {
    val v = PriceCheckData.of(d, milk, "2026-10-31")
    assertEquals(9.0, v.stock, 0.0)
    assertEquals("2026-10-25", v.card.last?.date)
    assertEquals(4_100L, v.card.last?.unitMinor)
    assertEquals("پخشِ شمال", v.supplier)
    //  FEFO: از ۹ ِ مانده، ۶ از محمولهٔ دیرتر و ۳ از محمولهٔ گذشته
    assertEquals("2026-11-10", v.card.nearest?.expiry)
    assertEquals(10, v.card.nearestDays)
    assertTrue(v.card.near)
    assertEquals(3_000L, v.card.expiredMilli)
    //  (۵۰ − ۴۰) ÷ ۵۰ = ۲۰٪
    assertEquals(2_000L, v.card.marginBp)
  }

  @Test fun `کالای بی خرید و بی انقضا — کارتِ خالی، دفتر دست‌نخورده`() {
    val tea = d.products.first { it.id == "tea" }
    val before = d
    val v = PriceCheckData.of(d, tea.copy(id = "none"), "2026-10-31")
    assertNull(v.card.last)
    assertNull(v.card.nearest)
    assertNull(v.supplier)
    assertNull(v.card.marginBp)
    assertEquals(before, d)
  }

  @Test fun `سطرهای کارت برای گوشی و کامپیوتر یکی و قیمتِ خرید فقط با دیدنِ پول`() {
    val v = PriceCheckData.of(d, milk, "2026-10-31")
    val S = ir.vil3ntec.tohid.core.i18n.StrPriceCheck
    val shown = PriceCheckData.lines(v, showCost = false)
    assertTrue(shown.none { it.label == S.cost })
    assertTrue("فیِ خرید پنهان", shown.single { it.label == S.lastPurchase }.value.let { !it.contains("41") && it.contains("پخشِ شمال") })
    assertEquals("9 دانه", shown.single { it.label == S.stock }.value)
    assertEquals(PriceCheckData.Tone.WARN, shown.single { it.label == S.expiry }.tone)
    assertEquals(PriceCheckData.Tone.DANGER, shown.last().tone)
    val boss = PriceCheckData.lines(v, showCost = true)
    assertTrue(boss.any { it.label == S.cost && it.value.startsWith("40") })
    assertTrue(boss.single { it.label == S.lastPurchase }.value.contains("41"))
  }

  @Test fun `برگهٔ قیمت‌پرسیِ گوشی کالا را با نام، بارکد یا انتخاب پیدا می‌کند`() {
    val withCode = d.copy(products = d.products.map { if (it.id == "milk") it.copy(barcodes = listOf("6260001")) else it })
    val index = ShopStore.barcodeIndex(withCode)
    assertEquals("milk", PriceCheckData.pick(withCode, index, "6260001", null))
    assertEquals("tea", PriceCheckData.pick(withCode, index, "چای", null))
    assertNull("خالی", PriceCheckData.pick(withCode, index, "  ", null))
    assertEquals("milk", PriceCheckData.pick(withCode, index, "چای", "milk"))
    assertTrue(PriceCheckData.matches(withCode, "").isEmpty())
  }
}
