package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.i18n.StrQuickRegister
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.QuickRegister
import ir.vil3ntec.tohid.data.QuickRegister.Next
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.sales.cart.QuickTillModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  S03 (POS-17) روی صندوقِ گوشیِ نسخهٔ ۳ — بارکدِ ناشناس ⇒ همان قاعدهٔ صندوقِ کامپیوتر (`QuickRegister.next`) و
 *  مسیرِ ثبت‌شدهٔ همان کار (`QuickTillModel`). ثبت و افزودن به سبد خودش در `ScenarioS03Test` سنجیده است.
 */
class QuickTillModelTest {

  private val code = "6269999000042"

  @Test
  fun `قاعدهٔ راهِ بارکدِ ناشناس — خاموش، مجاز، قلمِ آزاد، هیچ`() {
    for (may in listOf(true, false)) for (free in listOf(true, false)) assertEquals(Next.PRODUCT_FORM, QuickRegister.next(false, may, free))
    assertEquals(Next.QUICK, QuickRegister.next(true, may = true, freeLineOpen = false))
    assertEquals(Next.QUICK, QuickRegister.next(true, may = true, freeLineOpen = true))
    assertEquals(Next.FREE_LINE, QuickRegister.next(true, may = false, freeLineOpen = true))
    assertEquals(Next.NONE, QuickRegister.next(true, may = false, freeLineOpen = false))
    //  نقش همان سنجشِ سرور: صندوق‌دار ثبت نمی‌کند، مدیر و صاحب (نقشِ خالی) می‌کنند
    assertEquals(Next.FREE_LINE, QuickRegister.next(true, QuickRegister.allowed("cashier"), true))
    assertEquals(Next.QUICK, QuickRegister.next(true, QuickRegister.allowed("manager"), false))
    assertEquals(Next.QUICK, QuickRegister.next(true, QuickRegister.allowed(""), false))
  }

  @Test
  fun `ثبتِ سریع روشن و مجاز — برگهٔ ثبتِ سریع در خودِ صندوق با همان بارکد`() {
    val note = QuickTillModel.unknown(code, Next.QUICK)
    assertEquals(StrSale.unknownBarcode, note.text)
    assertEquals(V3Tone.WARNING, note.tone)
    assertEquals(StrQuickRegister.action, note.action)
    val hit = V3Modules.table.resolve(note.path!!)
    assertNotNull(hit)
    assertEquals("sale/quick-register", hit!!.route.pattern)
    assertEquals("sale", hit.route.parent)
    assertEquals(code, hit.args.query("code"))
  }

  @Test
  fun `خاموش — همان ثبتِ کالا با فرمِ کالا و همان بارکد`() {
    val note = QuickTillModel.unknown(code, Next.PRODUCT_FORM)
    assertEquals(StrSale.registerItemAlt, note.action)
    val hit = V3Modules.table.resolve(note.path!!)!!
    assertEquals("products/new", hit.route.pattern)
    assertEquals(code, hit.args.query("barcode"))
  }

  @Test
  fun `بی‌مجوز — فقط قلمِ آزاد، و با سقفِ بسته هیچ دکمه‌ای`() {
    val free = QuickTillModel.unknown(code, Next.FREE_LINE)
    assertEquals(StrSale.freeItem, free.action)
    assertEquals("sale/free-line", V3Modules.table.resolve(free.path!!)!!.route.pattern)
    val none = QuickTillModel.unknown(code, Next.NONE)
    assertEquals(StrSale.unknownBarcode, none.text)
    assertNull(none.action)
    assertNull(none.path)
  }

  @Test
  fun `بارکدِ با نویسهٔ ویژه سالم به برگه می‌رسد`() {
    val odd = "AB-12/3 4?x=1&y"
    val hit = V3Modules.table.resolve(QuickTillModel.path(Next.QUICK, odd)!!)!!
    assertEquals("sale/quick-register", hit.route.pattern)
    assertEquals(odd, hit.args.query("code"))
  }
}
