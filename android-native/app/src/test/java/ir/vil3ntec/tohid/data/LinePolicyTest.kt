package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.pos.LinePricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  قیمتِ ردیف در صندوق (POS-08، POS-10، POS-20، POS-21) — پلِ `LinePricing` و سبدِ `SalesEngine`.
 *
 *  یافتهٔ واقعی: سقفِ تخفیفِ نقش فقط تخفیفِ فاکتور را می‌سنجید؛ شاگردی که تخفیف برایش «ممنوع»
 *  است قیمتِ دستیِ ردیف را از ۱۰۰۰ به ۵۰۰ می‌گذاشت و دروازه «آزاد» می‌گفت.
 */
class LinePolicyTest {

  private val d = ShopData(
    products = listOf(
      Product(id = "rice", name = "برنج", salePrice = 1000.0),
      Product(id = "tea", name = "چای", salePrice = 115.0),
    ),
  )

  private fun totals(subtotal: Double, discount: Double = 0.0) =
    SalesEngine.Totals(subtotal = subtotal, discountValue = discount, discount = discount, finalTotal = subtotal - discount)

  private fun decision(role: String, b: DiscountGate.Basis, layers: List<Any?> = emptyList()) =
    PermCore.evaluate(role, layers, "sales.discount", DiscountGate.req(b.discount, b.subtotal)).decision

  @Test fun `قیمتِ دستیِ زیرِ فهرست کاهش است؛ بالاتر، قلمِ آزاد و کالای پاک‌شده نه`() {
    val cart = listOf(
      SalesEngine.CartLine("rice", 2.0, unitPrice = 500.0),   // ۲۰۰۰ ⇒ ۱۰۰۰: کاهشِ ۱۰۰۰
      SalesEngine.CartLine("tea", 1.5, unitPrice = 120.0),    // گران‌تر: ۰
      SalesEngine.CartLine("free-1", 1.0, unitPrice = 5.0, label = "کیسه"),
      SalesEngine.CartLine("gone", 1.0, unitPrice = 1.0),
      SalesEngine.CartLine("tea", 1.0),                       // بی قیمتِ دستی
    )
    assertEquals(100_000L, DiscountGate.reductionMinor(d, cart))
    //  قلمِ آزاد فهرست ندارد — حتی اگر شناسهٔ ردیفش با کالایی یکی باشد
    assertEquals(0L, DiscountGate.reductionMinor(d, listOf(SalesEngine.CartLine("rice", 1.0, unitPrice = 5.0, label = "کیسه"))))
    //  همان گردِ `lineTotal`: ۱٫۵ × ۱۱۵ = ۱۷۳، ۱٫۵ × ۱۱۰ = ۱۶۵
    assertEquals(800L, DiscountGate.reductionMinor(d, listOf(SalesEngine.CartLine("tea", 1.5, unitPrice = 110.0))))
  }

  @Test fun `ردیفِ تخفیفِ خودکار دلخواهِ صندوقدار نیست و شمرده نمی‌شود`() {
    val priced = listOf(SalesEngine.CartLine("rice", 1.0, unitPrice = 900.0))
    val promo = PromoEngine.Outcome(
      gross = 1000.0, discount = 100.0, applied = emptyList(), codes = emptyList(), cart = priced,
      tags = mapOf("rice" to PromoEngine.LineTag(100_000, listOf(SalePromo("p1", 1, "ده درصد", 10_000)))),
    )
    assertEquals(0L, DiscountGate.reductionMinor(d, priced, promo))
    assertEquals(10_000L, DiscountGate.reductionMinor(d, priced, null))
  }

  @Test fun `پرچمِ خاموش — دروازه همان عددهای امروز`() {
    val cart = listOf(SalesEngine.CartLine("rice", 1.0, unitPrice = 500.0))
    val t = totals(500.0, 20.0)
    assertEquals(DiscountGate.Basis(20.0, 500.0), DiscountGate.basis(false, d, cart, null, t))
  }

  @Test fun `شاگردِ بی تخفیف با قیمتِ دستی دیگر از دروازه نمی‌گذرد`() {
    val cart = listOf(SalesEngine.CartLine("rice", 1.0, unitPrice = 500.0))
    val t = totals(500.0)
    val today = DiscountGate.basis(false, d, cart, null, t)
    val line = DiscountGate.basis(true, d, cart, null, t)
    assertEquals(DiscountGate.Basis(500.0, 1000.0), line)
    //  پیش از این: صفر تخفیف ⇒ «آزاد»
    assertEquals(DiscountGate.State.FREE, DiscountGate.state(decision("staff", today), "s-1", today.discount, today.subtotal, null))
    //  با پرچم: ۵۰٪ تخفیف ⇒ «ممنوع» برای شاگرد، «نیازمندِ تأیید» برای مدیرِ با سقفِ ۱۰٪
    assertEquals(DiscountGate.State.DENIED, DiscountGate.state(decision("staff", line), "s-1", line.discount, line.subtotal, null))
    val capped = listOf(mapOf("sales.discount" to mapOf("caps" to mapOf("maxPct" to 10.0))))
    assertEquals(DiscountGate.State.NEEDS_APPROVAL, DiscountGate.state(decision("manager", line, capped), "s-1", line.discount, line.subtotal, null))
    //  صاحبِ دکان همان «آزاد»
    assertEquals(DiscountGate.State.FREE, DiscountGate.state(decision("owner", line), "s-1", line.discount, line.subtotal, null))
  }

  @Test fun `برگهٔ تأییدِ صندوق نیازِ سرور را هم می‌پوشاند`() {
    //  فهرستِ ۱۰۰۰، ردیف ۹۰۰ و تخفیفِ فاکتورِ ۹۰ ⇒ صندوق ۱۹۰ از ۱۰۰۰؛ سرور ۹۰ از ۹۰۰
    val cart = listOf(SalesEngine.CartLine("rice", 1.0, unitPrice = 900.0))
    val b = DiscountGate.basis(true, d, cart, null, totals(900.0, 90.0))
    assertEquals(DiscountGate.Basis(190.0, 1000.0), b)
    val client = DiscountGate.need("s-1", b.discount, b.subtotal)
    val server = DiscountGate.need("s-1", 90.0, 900.0)
    assertTrue(client.pctBp!! >= server.pctBp!!)
    assertTrue(client.amount!! >= server.amount!!)
  }

  @Test fun `عددِ بیرون از بازهٔ امن آزاد نمی‌شود`() {
    val cart = listOf(SalesEngine.CartLine("rice", 1e15, unitPrice = 1.0))
    val b = DiscountGate.basis(true, d, cart, null, totals(100.0))
    assertEquals(DiscountGate.Basis(100.0, 100.0), b)
    //  همهٔ فاکتور تخفیف ⇒ مدیرِ با سقفِ ۱۰٪ بی تأیید نمی‌گذرد
    val capped = listOf(mapOf("sales.discount" to mapOf("caps" to mapOf("maxPct" to 10.0))))
    assertEquals("approve", decision("manager", b, capped))
  }

  @Test fun `فقط صاحبِ دکان از قاعدهٔ جهت و سقفِ قلمِ آزاد آزاد است`() {
    val dir = LinePricing.Direction.NONE
    assertTrue(LinePolicy.policy(true, ShopRole.OWNER, dir, 0, true).exempt)
    assertFalse(LinePolicy.policy(true, ShopRole.MANAGER, dir, 0, true).exempt)
    assertFalse(LinePolicy.policy(true, ShopRole.STAFF, dir, 0, false).mayReduce)
    val staff = LinePolicy.policy(true, ShopRole.STAFF, dir, 0, false)
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, LinePricing.checkPrice(staff, 100_000, 50_000))
    assertEquals(LinePricing.FREE_LINE_CLOSED, LinePricing.checkFreeLine(staff, 100))
  }

  @Test fun `کادرِ ردیف — خاموش همان کادرِ پیشین، روشن تخفیفِ ردیف و رد`() {
    val off = LinePricing.Policy()
    //  خاموش: نه کادرِ تخفیف، نه رد، حتی با نوشتهٔ بی‌معنا یا عددِ بسیار بزرگ
    assertEquals(LinePolicy.Dialog(false, null, null, null), LinePolicy.edit(off, false, 100.0, 100.0, 50.0, 3.0, true, "x"))
    assertEquals(LinePolicy.Dialog(false, null, null, null), LinePolicy.edit(off, false, 100.0, 100.0, 1e20, 1e15, true, ""))
    val on = LinePricing.Policy(on = true)
    //  ۱۰٪ از ۳ × ۱۰۰ ⇒ ۲۷۰، قیمتِ واحد ۹۰
    assertEquals(LinePolicy.Dialog(true, null, 270.0, 90.0), LinePolicy.edit(on, false, 100.0, 100.0, null, 3.0, true, "۱۰"))
    assertEquals(StrLine.tooLarge, LinePolicy.edit(on, false, 100.0, 100.0, 1e20, 1e15, true, "").problem)
    assertEquals(StrSale.badNumber(StrLine.lineDiscount), LinePolicy.edit(on, false, 100.0, 100.0, null, 3.0, true, "1.2.3").problem)
    //  «ممنوع از تخفیف»: کادرِ تخفیف پنهان و کاهشِ قیمت رد
    val staff = LinePolicy.policy(true, ShopRole.STAFF, LinePricing.Direction.BOTH, null, false)
    val cut = LinePolicy.edit(staff, false, 100.0, 100.0, 80.0, 1.0, true, "")
    assertFalse(cut.canDiscount)
    assertEquals(StrLine.reductionDenied, cut.problem)
    assertTrue(LinePolicy.edit(staff, false, 100.0, 100.0, 120.0, 1.0, true, "").ok)
  }

  @Test fun `هر کدِ رد متنِ روشن دارد`() {
    val p = LinePricing.Policy(on = true, freeLineCapMinor = 5_000)
    for (code in listOf(
      LinePricing.PRICE_UP_BLOCKED, LinePricing.PRICE_DOWN_BLOCKED, LinePricing.REDUCTION_DENIED,
      LinePricing.FREE_LINE_CLOSED, LinePricing.FREE_LINE_OVER_CAP,
    )) assertNotNull(code, LinePolicy.message(code, p))
    assertEquals(null, LinePolicy.message(null, p))
    assertTrue(LinePolicy.message(LinePricing.FREE_LINE_OVER_CAP, p)!!.contains("50"))
  }
}
