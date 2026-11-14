package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.core.pos.PriceApproval
import ir.vil3ntec.tohid.data.LinePolicy
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductPack
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سناریوی S45 (§۷.۵، STF-02/STF-03) در **گوشیِ نسخهٔ ۳** — برگهٔ ردیفِ سبد (`sale/line/{i}` ⇐ `LineSheet` ⇐ `LineModel`):
 *  با «قیمت و تخفیفِ ردیف» ِ روشن همان قاعده و پیام‌های کادرِ ردیفِ کامپیوتر (`LinePolicy.checked`)، ردِ قاعدهٔ جهت یا سقفِ
 *  قلمِ آزاد «درخواستِ تأیید» دارد و تأیید **فقط همان قاعده را برای همان قیمت** برمی‌دارد؛ «تخفیف ممنوع» برداشتنی نیست؛
 *  `LineModel.apply` بی تأیید سبد را دست نمی‌زند (پیش از دورِ `claude/e-rows-31w` قیمت را بی هیچ قاعده‌ای می‌نشاند).
 *  پنجرهٔ واقعیِ Compose ِ همین برگه: `ScenarioS45PhoneUiTest` (کامپیوتر)؛ سمتِ کامپیوتر: `ScenarioS45PriceTest`.
 */
class ScenarioS45PhoneTest {

  private val tea = Product(id = "p-tea", name = "چای", unit = "دانه", salePrice = 100.0)
  private val d = ShopData(products = listOf(tea))
  private fun line(price: Double? = null) = SalesEngine.CartLine("p-tea", 2.0, unitPrice = price)
  private val cart = listOf(line())
  private val info = LineModel.info(d, line())
  private val start = LineModel.start(info)
  private fun priced(text: String) = start.copy(priceText = text, priceTouched = true)

  private fun policy(role: String = ShopRole.MANAGER, dir: LinePricing.Direction = LinePricing.Direction.UP, reduce: Boolean = true, cap: Long? = null) =
    LinePolicy.policy(on = true, role = role, direction = dir, freeLineCapMinor = cap, mayDiscount = reduce)

  private fun grant(code: String, price: Double) = LineModel.grantOf(code, "مدیر", ShopRole.MANAGER, "مشتریِ همیشگی", "علی", info, price)

  @Test fun `سیاستِ خاموش همان برگهٔ پیشین است`() {
    val off = LinePricing.Policy()
    val c = LineModel.check(off, priced("80"), info, start, canAsk = true)
    assertNull(c.edit.problem)
    assertNull(c.askCode)
    assertEquals(80.0, LineModel.apply(cart, info, priced("80"), start).single().unitPrice!!, 0.0)
  }

  @Test fun `قاعدهٔ جهت همان پیامِ کامپیوتر و درخواستِ تأیید دارد و بی تأیید سبد دست نمی‌خورد`() {
    val p = policy()
    val c = LineModel.check(p, priced("80"), info, start, canAsk = true)
    assertEquals(StrLine.priceDownBlocked, c.edit.problem)
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, c.askCode)
    assertNull("بی درِ تأیید (`PriceAsk`) دکمه‌ای نیست", LineModel.check(p, priced("80"), info, start, canAsk = false).askCode)
    assertSame("ردِ قاعده ⇒ همان سبد", cart, LineModel.apply(cart, info, priced("80"), start, p))
    //  بالا بردن با «فقط بالا» آزاد است
    assertEquals(120.0, LineModel.apply(cart, info, priced("120"), start, p).single().unitPrice!!, 0.0)
  }

  @Test fun `تأیید فقط همان قاعده را برای همان قیمت برمی‌دارد`() {
    val p = policy()
    val g = grant(LinePricing.PRICE_DOWN_BLOCKED, 80.0)
    assertEquals(10_000L, g.listMinor)
    assertEquals(8_000L, g.priceMinor)
    val ok = LineModel.check(p, priced("80"), info, start, g, canAsk = true)
    assertNull(ok.edit.problem)
    assertNull("تأییدشده دیگر دکمهٔ درخواست ندارد", ok.askCode)
    assertSame(g, ok.used)
    assertEquals(80.0, LineModel.apply(cart, info, priced("80"), start, p, g).single().unitPrice!!, 0.0)
    //  قیمت پس از تأیید عوض شد ⇒ تأیید دیگر نمی‌خورد
    val moved = LineModel.check(p, priced("70"), info, start, g, canAsk = true)
    assertEquals(StrLine.priceDownBlocked, moved.edit.problem)
    assertNull(moved.granted)
    assertSame(cart, LineModel.apply(cart, info, priced("70"), start, p, g))
    //  تأییدِ بی‌نیاز (قیمتِ آزاد) سابقه نمی‌سازد
    assertNull(LineModel.check(p, priced("120"), info, start, grant(LinePricing.PRICE_DOWN_BLOCKED, 120.0)).used)
  }

  @Test fun `تخفیف ممنوع برداشتنی نیست و صاحب معاف است`() {
    val staff = policy(role = ShopRole.STAFF, dir = LinePricing.Direction.BOTH, reduce = false)
    val c = LineModel.check(staff, priced("80"), info, start, canAsk = true)
    assertEquals(StrLine.reductionDenied, c.edit.problem)
    assertNull("«تخفیف ممنوع» درخواستِ تأیید ندارد", c.askCode)
    assertSame(cart, LineModel.apply(cart, info, priced("80"), start, staff, grant(LinePricing.REDUCTION_DENIED, 80.0)))
    val owner = policy(role = ShopRole.OWNER, dir = LinePricing.Direction.NONE, reduce = false)
    assertNull(LineModel.check(owner, priced("80"), info, start, canAsk = true).edit.problem)
    assertEquals(80.0, LineModel.apply(cart, info, priced("80"), start, owner).single().unitPrice!!, 0.0)
  }

  @Test fun `قیمتِ دست‌نخورده و برگشت به قیمتِ کالا سنجیده نمی‌شوند`() {
    val none = policy(dir = LinePricing.Direction.NONE)
    //  ردیفی که از پیش قیمتِ دستیِ ۹۰ دارد (پیش از روشن شدنِ قاعده) و فقط تعدادش عوض می‌شود
    val old = LineModel.info(d, line(90.0))
    val oldStart = LineModel.start(old)
    val qty = oldStart.copy(qtyText = "3")
    assertNull(LineModel.check(none, qty, old, oldStart).edit.problem)
    assertEquals(3.0, LineModel.apply(listOf(line(90.0)), old, qty, oldStart, none).single().quantity, 0.0)
    val reset = LineModel.resetPrice(oldStart, old)
    assertNull(LineModel.check(none, reset, old, oldStart).edit.problem)
    assertNull(LineModel.apply(listOf(line(90.0)), old, reset, oldStart, none).single().unitPrice)
  }

  @Test fun `قیمتِ لیستِ بسته تغییرِ دستی نیست ولی قیمتِ نوشته‌شده سنجیده می‌شود`() {
    val oil = Product(id = "oil", name = "روغن", unit = "دانه", salePrice = 150.0, packs = listOf(ProductPack("کارتن", 12_000, priceMinor = 170_000)))
    val od = ShopData(products = listOf(oil))
    val ol = SalesEngine.CartLine("oil", 12.0)
    val oi = LineModel.info(od, ol, units = true)
    val os = LineModel.start(oi)
    val carton = LineModel.switchTo(os, oi, 1)
    val none = policy(dir = LinePricing.Direction.NONE)
    assertNull(LineModel.typedPrice(carton, oi, os))
    assertNull("کارتن با قیمتِ لیستِ خودش", LineModel.check(none, carton, oi, os).edit.problem)
    val typed = carton.copy(priceText = "120", priceTouched = true)
    assertEquals(120.0, LineModel.typedPrice(typed, oi, os)!!, 0.0)
    assertEquals(StrLine.priceDownBlocked, LineModel.check(none, typed, oi, os).edit.problem)
  }

  @Test fun `با کارتنِ قیمت‌دار قاعدهٔ فقط پایین با قیمتِ همان کارتن سنجیده می‌شود`() {
    //  تصمیمِ ۱۴۰۵/۰۸/۲۳ (#۲۱): پایه ۱۰۰، کارتنِ ۱۲ دانه‌ای ۹۶۰ (= ۸۰)، «فقط پایین» ⇒ ۹۵ برای کارتن تأیید می‌خواهد
    val oil = Product(id = "oil", name = "روغن", unit = "دانه", salePrice = 100.0, packs = listOf(ProductPack("کارتن", 12_000, priceMinor = 96_000)))
    val od = ShopData(products = listOf(oil))
    val ol = SalesEngine.CartLine("oil", 12.0, unitPrice = 80.0)
    val oi = LineModel.info(od, ol, units = true)
    val os = LineModel.start(oi)
    assertEquals("برگه با کارتن باز می‌شود", 1, os.unitAt)
    val down = policy(dir = LinePricing.Direction.DOWN)
    val up95 = os.copy(priceText = "95", priceTouched = true)
    val c = LineModel.check(down, up95, oi, os, canAsk = true)
    assertEquals(StrLine.priceUpBlocked, c.edit.problem)
    assertEquals(LinePricing.PRICE_UP_BLOCKED, c.askCode)
    val oc = listOf(ol)
    assertSame("بی تأیید سبد دست نمی‌خورد", oc, LineModel.apply(oc, oi, up95, os, down))
    //  زیرِ قیمتِ کارتن آزاد است؛ با دانه (واحدِ پایه) همان ۹۵ پایین آمدن از ۱۰۰ است
    assertNull(LineModel.check(down, os.copy(priceText = "75", priceTouched = true), oi, os).edit.problem)
    val piece = LineModel.switchTo(os, oi, 0).copy(priceText = "95", priceTouched = true)
    assertNull(LineModel.check(down, piece, oi, os).edit.problem)
    //  تأییدِ مدیر همان قیمت را می‌نشاند و سابقه «از ۸۰» می‌نویسد، نه «از ۱۰۰»
    val g = LineModel.grantOf(LinePricing.PRICE_UP_BLOCKED, "مدیر", ShopRole.MANAGER, "کارتنِ آخر", "علی", oi, 95.0, up95)
    assertEquals(8_000L, g.listMinor)
    assertEquals(95.0, LineModel.apply(listOf(ol), oi, up95, os, down, g).single().unitPrice!!, 0.0)
    assertEquals(80.0, LineModel.ruleList(up95, oi), 0.0)
  }

  @Test fun `قلمِ آزاد سقف دارد هم در افزودن هم در ویرایش`() {
    val capped = policy(cap = 50_000L)
    val form = FreeLineForm("کیسه", "600", "1")
    assertEquals(StrLine.freeLineOverCap(money(500.0)), FreeLineModel.errors(form, capped)[FreeLineModel.PRICE])
    assertEquals(emptyList<SalesEngine.CartLine>(), FreeLineModel.add(emptyList(), form, capped) { "f1" })
    assertTrue(FreeLineModel.errors(form).isEmpty())
    assertTrue("صاحب معاف", FreeLineModel.errors(form, policy(role = ShopRole.OWNER, cap = 0L)).isEmpty())
    assertEquals(StrLine.freeLineClosed, FreeLineModel.errors(form, policy(cap = 0L))[FreeLineModel.PRICE])

    val free = SalesEngine.CartLine("f1", 1.0, unitPrice = 400.0, label = "کیسه")
    val fi = LineModel.info(ShopData(), free)
    val fs = LineModel.start(fi)
    val over = fs.copy(priceText = "600", priceTouched = true)
    val c = LineModel.check(capped, over, fi, fs, canAsk = true)
    assertEquals(StrLine.freeLineOverCap(money(500.0)), c.edit.problem)
    assertEquals(LinePricing.FREE_LINE_OVER_CAP, c.askCode)
    val g = LineModel.grantOf(c.askCode!!, "مدیر", ShopRole.MANAGER, "کارتنِ بزرگ", "علی", fi, 600.0)
    assertEquals(600.0, LineModel.apply(listOf(free), fi, over, fs, capped, g).single().unitPrice!!, 0.0)
    assertEquals(PriceApproval.AUDIT_TYPE, LinePolicy.approvalAudit(g, fi.name, null, 1L, "2026-11-12").type)
  }
}
