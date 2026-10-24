package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  تخفیف‌های خودکار در صندوق — پلِ `PromoCore` و `SalesEngine`.
 *
 *  ۱. کلیدِ دکان خاموش ⇒ همان سبد، همان جمع، همان فاکتور (آزمونِ مرجع).
 *  ۲. روشن ⇒ قیمتِ ردیف پایین می‌آید، `Sale.discount` صفر می‌ماند (شاگرد هم
 *     ثبت می‌کند و Sync ردش نمی‌کند)، جمعِ ردیف‌ها = جمعِ فاکتور.
 *  ۳. قالبِ JSON رفت‌وبرگشت دارد.
 */
class PromoEngineTest {

  private val tea = Product(id = "tea", name = "چای سبز", unit = "عدد", category = "نوشیدنی", purchasePrice = 100.0, salePrice = 300.0)
  private val rice = Product(id = "rice", name = "برنج", unit = "کیلوگرم", category = "خوراکی", purchasePrice = 0.0, salePrice = 115.0)
  private val d = ShopData(
    products = listOf(tea, rice),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "tea", units = 50.0),
      WarehouseEntry(id = "w2", productId = "rice", units = 50.0),
    ),
  )
  private val cart = listOf(SalesEngine.CartLine("tea", 2.0), SalesEngine.CartLine("rice", 1.5))

  private val r1 = PromoCore.Rule(
    id = "r1", name = "ده درصدِ نوشیدنی", kind = "percent", bp = 1000,
    scope = PromoCore.Scope(categories = listOf("نوشیدنی")), channels = listOf("pos"),
  )
  private val r2 = PromoCore.Rule(id = "r2", name = "کدِ عید", kind = "percent", bp = 500, code = "EID5", stack = "combine")
  private val on = PromoEngine.Snapshot(enabled = true, rules = listOf(r1, r2))

  private var seq = 0
  private fun newId(): String = "t${seq++}"
  private fun record(c: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout = SalesEngine.Checkout()): SalesEngine.Result.Ok {
    seq = 0
    val r = SalesEngine.record(d, c, checkout, today = "2026-10-19", now = 1_760_000_000_000, newId = ::newId)
    assertTrue("$r", r is SalesEngine.Result.Ok)
    return r as SalesEngine.Result.Ok
  }
  private fun eval(s: PromoEngine.Snapshot, coupons: List<String> = emptyList(), c: List<SalesEngine.CartLine> = cart) =
    PromoEngine.evaluate(d, c, s, coupons, "2026-10-19", 600)

  @Test fun `خاموش با قاعده‌های فعال ⇒ همان سبد، همان جمع و همان فاکتورِ امروز`() {
    val off = on.copy(enabled = false)
    val o = eval(off, listOf("EID5"))
    assertNull(o)
    assertSame(cart, PromoEngine.priced(cart, o))
    val checkout = SalesEngine.Checkout(discountType = SalesEngine.DiscountType.PERCENT, discountValue = 10.0)
    assertEquals(SalesEngine.totals(d, cart, checkout), SalesEngine.totals(d, PromoEngine.priced(cart, o), checkout))
    val today = record(cart, checkout)
    val withOff = record(PromoEngine.priced(cart, o), checkout)
    assertEquals(today.data.sales, withOff.data.sales)
    assertEquals(today.data.saleItems, withOff.data.saleItems)
    assertEquals(773.0, today.data.sales.last().total, 0.0)
  }

  @Test fun `روشن ولی بی قاعدهٔ فعال یا سبدِ خالی ⇒ هیچ`() {
    assertNull(eval(on.copy(rules = listOf(r1.copy(active = false)))))
    assertNull(eval(on.copy(rules = emptyList())))
    assertNull(eval(on, c = emptyList()))
  }

  @Test fun `روشن — ده درصد روی نوشیدنی، کدِ ترکیبی؛ فاکتور با قیمتِ خالصِ ردیف و discount صفر`() {
    val auto = eval(on)!!
    assertEquals(773.0, auto.gross, 0.0)
    assertEquals(60.0, auto.discount, 0.0)
    assertEquals(listOf(PromoEngine.Applied("r1", "ده درصدِ نوشیدنی", 60.0)), auto.applied)

    val o = eval(on, listOf("eid-5"))!!
    assertEquals(99.0, o.discount, 0.0)
    assertEquals(listOf(PromoCore.CodeStatus("EID5", "applied")), o.codes)
    val priced = PromoEngine.priced(cart, o)
    assertEquals(674.0, SalesEngine.cartTotal(d, priced), 0.0)

    val sale = record(priced)
    val s = sale.data.sales.last()
    assertEquals(674.0, s.total, 0.0)
    assertEquals(0.0, s.discount, 0.0)
    assertEquals(674.0, s.finalTotal, 0.0)
    val items = sale.data.saleItems.associateBy { it.productId }
    assertEquals(510.0, items.getValue("tea").totalPrice, 0.0)
    assertEquals(255.0, items.getValue("tea").unitPrice, 1e-9)
    assertEquals(164.0, items.getValue("rice").totalPrice, 0.0)
    assertEquals(s.total, sale.data.saleItems.sumOf { it.totalPrice }, 0.0)
    //  مقدارها همان سبد است — فقط قیمت پایین آمده
    assertEquals(2.0, items.getValue("tea").quantity, 0.0)
    assertEquals(1.5, items.getValue("rice").quantity, 0.0)
  }

  @Test fun `تخفیفِ دستیِ فاکتور روی جمعِ پس از تخفیفِ خودکار`() {
    val priced = PromoEngine.priced(cart, eval(on)!!)
    val t = SalesEngine.totals(d, priced, SalesEngine.Checkout(discountType = SalesEngine.DiscountType.PERCENT, discountValue = 10.0))
    assertEquals(713.0, t.subtotal, 0.0)
    assertEquals(71.0, t.discount, 0.0)
    assertEquals(642.0, t.finalTotal, 0.0)
  }

  @Test fun `ردیفِ با قیمتِ دستی و قلمِ آزاد تخفیفِ خودکار نمی‌گیرند`() {
    val c = listOf(
      SalesEngine.CartLine("tea", 2.0, unitPrice = 280.0),
      SalesEngine.CartLine("free-1", 1.0, unitPrice = 50.0, label = "کیسه"),
    )
    val all = on.copy(rules = listOf(r1.copy(scope = PromoCore.Scope())))
    val o = eval(all, c = c)!!
    assertEquals(0.0, o.discount, 0.0)
    assertEquals(c, o.cart)
    assertEquals(610.0, o.gross, 0.0)
  }

  @Test fun `کفِ بها — ۹۰٪ روی چای فقط تا بهای خرید`() {
    val o = eval(on.copy(rules = listOf(r1.copy(bp = 9000))))!!
    //  ۲ × ۳۰۰ = ۶۰۰، بها ۲ × ۱۰۰ = ۲۰۰ ⇒ حداکثر ۴۰۰
    assertEquals(400.0, o.discount, 0.0)
    val below = eval(on.copy(rules = listOf(r1.copy(bp = 9000, allowBelowCost = true))))!!
    assertEquals(540.0, below.discount, 0.0)
  }

  @Test fun `عددِ بیرون از بازهٔ امنِ هسته ⇒ بی تخفیف، نه خطا`() {
    val huge = d.copy(products = listOf(tea.copy(salePrice = 1e14)))
    assertNull(PromoEngine.evaluate(huge, cart, on, emptyList(), "2026-10-19", 600))
  }

  @Test fun `JSON — قاعده و عکسِ حال رفت‌وبرگشت دارند`() {
    val full = r1.copy(
      window = PromoCore.Window(from = "2026-10-01", to = "2026-10-31", days = listOf(5L, 6L), start = 1320, end = 120, except = listOf("2026-10-30")),
      tiers = listOf(PromoCore.Tier(min = 3000, bp = 500)), prices = listOf(PromoCore.Price("tea", 25000)),
      code = "SALE10", maxUses = 3, allowBelowCost = true, priority = 7, stack = "exclusive",
    )
    assertEquals(full, PromoCore.ruleOf(PermPolicy.tree(PromoJson.toJson(full))))
    val snap = PromoEngine.Snapshot(enabled = true, rules = listOf(full, r2), manage = true, belowCost = false, at = 42)
    assertEquals(snap, PromoJson.snapshotOf(PromoJson.toJson(snap), 42))
    assertEquals(PromoEngine.Snapshot(at = 7), PromoJson.snapshotOf(kotlinx.serialization.json.JsonObject(emptyMap()), 7))
  }
}
