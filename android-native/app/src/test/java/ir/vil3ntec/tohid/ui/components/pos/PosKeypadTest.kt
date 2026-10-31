package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrTouch
import ir.vil3ntec.tohid.core.pos.Keypad
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  صفحه‌کلیدِ عددیِ لمسی روی سبد (POS-07) — همان راهِ کادرِ «ردیفِ سبد»: تعداد، قیمتِ دستی با
 *  قاعدهٔ جهت، و تخفیفِ ردیف فقط با پرچمِ «قیمت و تخفیفِ ردیف».
 */
class PosKeypadTest {

  private val rice = Product(id = "rice", name = "rice", salePrice = 1000.0)
  private val cart = listOf(
    SalesEngine.CartLine("rice", 2.0),
    SalesEngine.CartLine("free-1", 1.0, unitPrice = 50.0, label = "bag"),
  )
  private val off = LinePricing.Policy()
  private val on = LinePricing.Policy(on = true)

  private fun done(r: PosKeypad.Applied): List<SalesEngine.CartLine> {
    assertTrue("پذیرفته نشد: $r", r is PosKeypad.Applied.Done)
    return (r as PosKeypad.Applied.Done).cart
  }

  @Test fun `تعداد به همان ردیف می‌نشیند و ردیفِ دیگر دست نمی‌خورد`() {
    val next = done(PosKeypad.apply(Keypad.Entry.Qty(2_500), cart, "rice", rice, off))
    assertEquals(2.5, next[0].quantity, 0.0)
    assertEquals(cart[1], next[1])
  }

  @Test fun `بی ردیفِ انتخاب‌شده هیچ چیز عوض نمی‌شود`() {
    assertEquals(PosKeypad.Applied.Problem(StrTouch.pickLine), PosKeypad.apply(Keypad.Entry.Qty(1_000), cart, null, null, off))
    assertEquals(PosKeypad.Applied.Problem(StrTouch.pickLine), PosKeypad.apply(Keypad.Entry.Qty(1_000), cart, "gone", null, off))
    assertEquals(PosKeypad.Applied.Unchanged, PosKeypad.apply(Keypad.Entry.Empty, cart, "rice", rice, off))
    assertEquals(PosKeypad.Applied.Problem(StrTouch.zero), PosKeypad.apply(Keypad.Entry.Invalid(Keypad.ZERO), cart, "rice", rice, off))
  }

  @Test fun `پرچمِ خاموش — قیمتِ دستی آزاد، مثلِ کادرِ ردیف، و تخفیفِ ردیف بسته`() {
    val next = done(PosKeypad.apply(Keypad.Entry.Price(80_000), cart, "rice", rice, off))
    assertEquals(800.0, next[0].unitPrice!!, 0.0)
    assertEquals(
      PosKeypad.Applied.Problem(StrTouch.discountOff),
      PosKeypad.apply(Keypad.Entry.Discount(true, 1_000), cart, "rice", rice, off),
    )
  }

  @Test fun `قاعدهٔ جهت همان کادرِ ردیف را می‌گیرد`() {
    val upOnly = LinePricing.Policy(on = true, direction = LinePricing.Direction.UP)
    assertEquals(
      PosKeypad.Applied.Problem(StrLine.priceDownBlocked),
      PosKeypad.apply(Keypad.Entry.Price(90_000), cart, "rice", rice, upOnly),
    )
    assertEquals(1_200.0, done(PosKeypad.apply(Keypad.Entry.Price(120_000), cart, "rice", rice, upOnly))[0].unitPrice!!, 0.0)
  }

  @Test fun `تخفیفِ ردیف به قیمتِ واحد می‌نشیند — درصد و مبلغ`() {
    //  ۲ × ۱۰۰۰ = ۲۰۰۰؛ ۱۰٪ ⇒ ۱۸۰۰ ⇒ ۹۰۰ هر واحد
    assertEquals(900.0, done(PosKeypad.apply(Keypad.Entry.Discount(true, 1_000), cart, "rice", rice, on))[0].unitPrice!!, 0.0)
    //  ۳۰۰ افغانی از ۲۰۰۰ ⇒ ۸۵۰ هر واحد
    assertEquals(850.0, done(PosKeypad.apply(Keypad.Entry.Discount(false, 30_000), cart, "rice", rice, on))[0].unitPrice!!, 0.0)
  }

  @Test fun `کاهش برای کسی که تخفیف برایش ممنوع است رد می‌شود؛ صاحب آزاد`() {
    val denied = LinePricing.Policy(on = true, mayReduce = false)
    assertEquals(
      PosKeypad.Applied.Problem(StrTouch.discountOff),
      PosKeypad.apply(Keypad.Entry.Discount(true, 1_000), cart, "rice", rice, denied),
    )
    assertEquals(
      PosKeypad.Applied.Problem(StrLine.reductionDenied),
      PosKeypad.apply(Keypad.Entry.Price(50_000), cart, "rice", rice, denied),
    )
    val owner = denied.copy(exempt = true)
    assertEquals(500.0, done(PosKeypad.apply(Keypad.Entry.Price(50_000), cart, "rice", rice, owner))[0].unitPrice!!, 0.0)
  }

  @Test fun `قلمِ آزاد — تخفیف روی قیمتِ خودش`() {
    val next = done(PosKeypad.apply(Keypad.Entry.Discount(true, 2_000), cart, "free-1", null, on))
    assertEquals(40.0, next[1].unitPrice!!, 0.0)
    assertEquals(cart[0], next[0])
  }
}
