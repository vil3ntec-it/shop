package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-10 — «تخفیفِ نزدیکِ انقضا» در صندوق: `PromoEngine.lines` ماندهٔ محموله‌ها را به FEFO از
 *  `ExpiryEngine.lots` روی ردیف می‌گذارد، فقط وقتی قاعدهٔ فعالِ همین نوع هست؛ پرچمِ `expiry` ِ سرور و
 *  `nearDays` رفت‌وبرگشت دارند و بی آن‌ها JSON همان پیشین است.
 */
class PromoEngineExpiryTest {

  private val today = "2026-10-19"
  private val milk = Product(id = "milk", name = "شیر", unit = "عدد", category = "لبنیات", purchasePrice = 50.0, salePrice = 100.0)
  private val tea = Product(id = "tea", name = "چای سبز", unit = "عدد", category = "نوشیدنی", purchasePrice = 100.0, salePrice = 300.0)
  private val d = ShopData(
    products = listOf(milk, tea),
    warehouseEntries = listOf(
      WarehouseEntry(id = "m1", productId = "milk", units = 3.0, expiryDate = "2026-10-15"),
      WarehouseEntry(id = "m2", productId = "milk", units = 2.0, expiryDate = "2026-10-22"),
      WarehouseEntry(id = "m3", productId = "milk", units = 10.0, expiryDate = "2026-12-01"),
      WarehouseEntry(id = "t1", productId = "tea", units = 50.0),
    ),
  )
  private val cart = listOf(SalesEngine.CartLine("milk", 4.0), SalesEngine.CartLine("tea", 1.0))
  private val near = PromoCore.Rule(id = "ex", name = "نزدیکِ انقضا", kind = "expiry", bp = 3000, nearDays = 7, channels = listOf("pos"))
  private val pct = PromoCore.Rule(id = "p", name = "ده درصد", kind = "percent", bp = 1000, channels = listOf("pos"))

  @Test fun `lines با تاریخ محموله‌های هر کالا را به FEFO می‌گذارد و بی تاریخ هیچ`() {
    val withLots = PromoEngine.lines(d, cart, today)
    assertEquals(
      listOf(PromoCore.Lot(-4, 3000), PromoCore.Lot(3, 2000), PromoCore.Lot(43, 10_000)),
      withLots.single { it.productId == "milk" }.lots,
    )
    assertEquals(emptyList<PromoCore.Lot>(), withLots.single { it.productId == "tea" }.lots)
    //  بی تاریخ ⇒ همان ورودیِ پیشین، فقط بی محموله
    assertEquals(withLots.map { it.copy(lots = emptyList()) }, PromoEngine.lines(d, cart))
    assertTrue(PromoEngine.lines(d, cart).all { it.lots.isEmpty() })
  }

  @Test fun `lines — ماندهٔ فروخته‌شده از زودترین محموله کم می‌شود`() {
    val sold = d.copy(
      sales = listOf(Sale(id = "s1", date = "2026-10-18")),
      saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "milk", quantity = 4.0, unitPrice = 100.0)),
    )
    //  ۱۵ − ۴ = ۱۱ ⇒ ۱۰ در دیرترین، ۱ در محمولهٔ ۳روزه، منقضی هیچ
    assertEquals(
      listOf(PromoCore.Lot(3, 1000), PromoCore.Lot(43, 10_000)),
      PromoEngine.lines(sold, cart, today).single { it.productId == "milk" }.lots,
    )
  }

  @Test fun `روشن — فقط ۲ واحدِ محمولهٔ ۳روزه تخفیف می‌گیرد، منقضی و دیرتر نه`() {
    val o = PromoEngine.evaluate(d, cart, PromoEngine.Snapshot(enabled = true, rules = listOf(near)), emptyList(), today, 600)!!
    //  ۳۰٪ از ۲ × ۱۰۰ = ۶۰
    assertEquals(60.0, o.discount, 0.0)
    assertEquals(listOf(PromoEngine.Applied("ex", "نزدیکِ انقضا", 60.0)), o.applied)
    assertEquals(85.0, o.cart.single { it.productId == "milk" }.unitPrice!!, 1e-9)
    assertEquals(null, o.cart.single { it.productId == "tea" }.unitPrice)
  }

  @Test fun `قاعدهٔ انقضای غیرفعال ⇒ همان نتیجهٔ بی آن`() {
    val base = PromoEngine.Snapshot(enabled = true, rules = listOf(pct))
    val withOff = base.copy(rules = listOf(pct, near.copy(active = false)))
    assertEquals(
      PromoEngine.evaluate(d, cart, base, emptyList(), today, 600),
      PromoEngine.evaluate(d, cart, withOff, emptyList(), today, 600),
    )
  }

  @Test fun `تاریخِ خرابِ محموله فروش را نمی‌خواباند، فقط بی تخفیفِ انقضا`() {
    val bad = d.copy(warehouseEntries = d.warehouseEntries + WarehouseEntry(id = "m4", productId = "milk", units = 1.0, expiryDate = "bad"))
    assertTrue(PromoEngine.lines(bad, cart, today).all { it.lots.isEmpty() })
    val o = PromoEngine.evaluate(bad, cart, PromoEngine.Snapshot(enabled = true, rules = listOf(near)), emptyList(), today, 600)!!
    assertEquals(0.0, o.discount, 0.0)
  }

  @Test fun `JSON — پرچمِ expiry و nearDays رفت‌وبرگشت دارند؛ خاموش کلیدِ تازه نمی‌نویسد`() {
    assertEquals(near, PromoCore.ruleOf(PermPolicy.tree(PromoJson.toJson(near))))
    assertTrue(PromoJson.toJson(near).toString().contains("\"nearDays\":7"))
    assertFalse(PromoJson.toJson(pct).toString().contains("nearDays"))
    val snap = PromoEngine.Snapshot(enabled = true, rules = listOf(pct), at = 42)
    assertFalse(PromoJson.toJson(snap).toString().contains("expiry"))
    assertEquals(false, PromoJson.snapshotOf(PromoJson.toJson(snap), 42).expiry)
    val flagged = snap.copy(expiry = true, rules = listOf(pct, near))
    assertEquals(flagged, PromoJson.snapshotOf(PromoJson.toJson(flagged), 42))
  }
}
