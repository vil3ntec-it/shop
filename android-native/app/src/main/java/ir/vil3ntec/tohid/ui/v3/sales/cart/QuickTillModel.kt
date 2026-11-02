package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.core.i18n.StrQuickRegister
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.QuickRegister
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths

/**
 *  S03 (POS-17) روی صندوقِ گوشیِ نسخهٔ ۳ (صندوق، فروشِ سریع و برگهٔ اسکن): بارکدِ ناشناس ⇒ پیام و کارِ بعدی، با
 *  همان قاعدهٔ صندوقِ کامپیوتر (`QuickRegister.next`، خوانده با `quickNext`):
 *  - «ثبتِ سریع» روشن (`TillGoodsPref.quickRegister`، پیش‌فرض خاموش) و نقشِ مجاز ⇒ برگهٔ `sale/quick-register?code=`
 *    در خودِ صندوق (`QuickRegisterRoute`)؛ ثبت همان `QuickRegister.register` و `saveQuick` ِ کامپیوتر؛
 *  - نقشِ بی‌مجوز ⇒ فقط «قلمِ آزاد»، اگر سقفش بسته نیست؛ وگرنه فقط پیام؛
 *  - خاموش ⇒ همان «ثبتِ کالا» ⇐ فرمِ کالا (`products/new?barcode=`).
 */
object QuickTillModel {

  /** مسیرِ کارِ بعدی؛ `null` ⇒ کاری نیست */
  fun path(next: QuickRegister.Next, code: String): String? = when (next) {
    QuickRegister.Next.PRODUCT_FORM -> ProductsPaths.new(barcode = code)
    QuickRegister.Next.QUICK -> SalesPaths.quickRegister(code)
    QuickRegister.Next.FREE_LINE -> SalesPaths.FREE_LINE
    QuickRegister.Next.NONE -> null
  }

  /** برچسبِ دکمهٔ همان کار */
  fun label(next: QuickRegister.Next): String? = when (next) {
    QuickRegister.Next.PRODUCT_FORM -> StrSale.registerItemAlt
    QuickRegister.Next.QUICK -> StrQuickRegister.action
    QuickRegister.Next.FREE_LINE -> StrSale.freeItem
    QuickRegister.Next.NONE -> null
  }

  /** پاسخِ سبد به بارکدِ ناشناس */
  fun unknown(code: String, next: QuickRegister.Next): CartNote {
    val path = path(next, code)
    return CartNote(StrSale.unknownBarcode, V3Tone.WARNING, action = label(next).takeIf { path != null }, path = path)
  }
}
