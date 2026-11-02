package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.core.pos.Keypad
import ir.vil3ntec.tohid.core.pos.TouchMode
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.pos.PosCartOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S18 — حالتِ لمسی (POS-31، POS-06، POS-07) روی قاعده‌های هسته (`TouchMode`، `Keypad`، `CashKeys`) و افزودنِ کاشیِ
 *  صندوقِ کامپیوتر (`PosCartOps.addStep`)؛ در برنامه پشتِ تنظیمِ خاموشِ همین دستگاه (`PosTouchPrefs`).
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • صفحه‌کلیدِ **متنیِ** روی‌صفحه (دری، پشتو، لاتین) نیست ⇒ گامِ ۳ (جست‌وجوی کالا با نام) و گامِ ۴ (ثبتِ مشتریِ تازه با
 *     نامِ دری) بی کیبوردِ فیزیکی شدنی نیستند.
 *   • آزمونِ عملی روی کامپیوترِ لمسیِ واقعی (و شمارشِ لمس روی صفحهٔ واقعی) انجام نشده؛ شمارشِ زیر روی همان قاعده‌هاست.
 */
class ScenarioS18Test {

  private val items = listOf(
    Product(id = "p1", name = "نان", unit = "عدد", salePrice = 20.0, category = "نان"),
    Product(id = "p2", name = "شیر", unit = "عدد", salePrice = 70.0, category = "لبنیات"),
    Product(id = "p3", name = "پنیر", unit = "عدد", salePrice = 260.0, category = "لبنیات"),
  )
  private val d = ShopData(products = items, warehouseEntries = items.map { WarehouseEntry(id = "w${it.id}", productId = it.id, units = 30.0) })

  @Test
  fun `S18 — حالتِ لمسی روشن ⇒ هدفِ ۴۸ و صفحه‌کلیدِ عددی و دکمهٔ صفحه`() {
    assertFalse("تنظیمِ خاموش ⇒ همان صندوقِ موشی", TouchMode.touch(desktop = true, on = false, active = true))
    assertTrue(TouchMode.touch(desktop = true, on = true, active = true))
    assertTrue(TouchMode.touchExtras(desktop = true, on = true, active = true))
    assertTrue(TouchMode.TOUCH_TARGET_DP >= 48)
    //  فهرستِ بلند: ۱۰۰ کالا، ۱۲ تا پیدا ⇒ «صفحهٔ بعد» ۱۲ جلو، آخرِ فهرست خاموش
    assertEquals(12, TouchMode.page(0, 12, 100, forward = true))
    assertEquals(96, TouchMode.page(84, 12, 100, forward = true))
    assertEquals(84, TouchMode.page(96, 12, 100, forward = false))
    assertFalse(TouchMode.canPage(88, 12, 100, forward = true))
  }

  private var seq = 0

  @Test
  fun `S18 — فروشِ ۳ قلمی فقط با لمس و حداکثر ۱۰ لمس`() {
    var touches = 0
    var cart = emptyList<SalesEngine.CartLine>()
    //  شبکهٔ کالاها: هر کاشی یک لمس
    for (p in items) { cart = PosCartOps.addStep(cart, p, ShopStore.stock(d, p.id)).cart; touches++ }
    //  صفحه‌کلیدِ عددی: «۲» برای تعدادِ نان (یک لمس رقم + یک لمسِ ثبت)
    var k = Keypad.State(mode = Keypad.Mode.QTY)
    k = Keypad.press(k, Keypad.Key.D2); touches++
    val qty = Keypad.entry(k) as Keypad.Entry.Qty
    cart = SalesEngine.setCartQty(cart, "p1", qty.milli / 1000.0); touches++
    val total = SalesEngine.cartTotal(d, cart)
    assertEquals(2 * 20.0 + 70.0 + 260.0, total, 0.0)
    //  پرداخت: دکمهٔ پرداخت، اسکناسِ ۵۰۰، ثبت
    touches++
    val cash = CashKeys.addNote("", 50_000)!!
    touches++
    assertTrue(CashKeys.cashMinor(cash)!! >= (total * 100).toLong())
    touches++
    val sold = SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-11-02", 1_000, { "t${seq++}" })
    assertTrue(sold is SalesEngine.Result.Ok)
    assertTrue("لمس‌ها: $touches", touches <= 10)
  }
}
