package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.i18n.StrTouch
import ir.vil3ntec.tohid.core.pos.Keypad
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.data.LinePolicy
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine

// i18n: سه‌زبانه

/**
 *  اثرِ صفحه‌کلیدِ عددیِ لمسی روی ردیفِ انتخاب‌شدهٔ سبد (POS-07؛ ورودی در `core/…/pos/Keypad.kt`).
 *
 *  ⛔ همان راهِ کادرِ «ردیفِ سبد» (`CartLineDialog`): تعداد با `SalesEngine.setCartQty`، و قیمت و
 *  تخفیف با `LinePolicy.edit` و `SalesEngine.setLinePrice` — پس قاعدهٔ جهت، سقفِ قلمِ آزاد، «تخفیف
 *  ممنوع» و سنجشِ پایانیِ سقفِ تخفیف (`DiscountGate.basis`) دقیقاً همان‌اند. تخفیفِ ردیف فقط با تنظیمِ
 *  روشنِ «قیمت و تخفیفِ ردیف»، مثلِ همان کادر.
 *
 *  خالص — بی `Context`؛ آزمون: `PosKeypadTest`.
 */
object PosKeypad {

  sealed interface Applied {
    /** سبدِ تازه */
    data class Done(val cart: List<SalesEngine.CartLine>) : Applied
    /** پذیرفته نشد؛ متن برای صفحه */
    data class Problem(val text: String) : Applied
    /** کاری نبود (کادرِ خالی، یا تخفیفی که روی ردیفِ صفر چیزی کم نمی‌کند) */
    data object Unchanged : Applied
  }

  /** کلیدِ «تخفیف» — همان شرطِ کادرِ ردیف (`LinePolicy.edit`): پرچمِ روشن و کاهش برای کاربر ممنوع نیست */
  fun canDiscount(p: LinePricing.Policy): Boolean = p.on && (p.exempt || p.mayReduce)

  fun message(code: String): String = when (code) {
    Keypad.ZERO -> StrTouch.zero
    Keypad.PERCENT_OVER -> StrTouch.percentOver
    else -> StrTouch.badNumber
  }

  /**
   *  @param id شناسهٔ ردیفِ انتخاب‌شده (`CartLine.productId`)؛ `null` ⇒ «نخست ردیفی را انتخاب کنید»
   *  @param product کالای همان ردیف؛ قلمِ آزاد ⇒ `null`
   */
  fun apply(
    entry: Keypad.Entry,
    cart: List<SalesEngine.CartLine>,
    id: String?,
    product: Product?,
    policy: LinePricing.Policy,
  ): Applied {
    if (entry is Keypad.Entry.Empty) return Applied.Unchanged
    if (entry is Keypad.Entry.Invalid) return Applied.Problem(message(entry.code))
    val line = id?.let { i -> cart.find { it.productId == i } } ?: return Applied.Problem(StrTouch.pickLine)
    //  همان پایه‌های `DeskSale` برای کادرِ ردیف
    val base = if (line.free) (line.unitPrice ?: 0.0) else (product?.salePrice ?: 0.0)
    val current = line.unitPrice ?: base
    return when (entry) {
      is Keypad.Entry.Qty -> Applied.Done(SalesEngine.setCartQty(cart, line.productId, entry.milli / 1000.0))
      is Keypad.Entry.Price -> {
        val price = entry.minor / 100.0
        val e = LinePolicy.edit(policy, line.free, base, current, price, line.quantity, false, "")
        val problem = e.problem
        if (problem != null) Applied.Problem(problem)
        else Applied.Done(SalesEngine.setLinePrice(cart, line.productId, e.netUnit ?: price))
      }
      is Keypad.Entry.Discount -> {
        if (!canDiscount(policy)) return Applied.Problem(StrTouch.discountOff)
        //  ردیفِ قیمت‌دستی (یا قلمِ آزاد): تخفیف روی همان قیمتِ امروزش، مثلِ کادرِ ردیف با «قیمتِ دستی»
        val manual = if (line.free || line.unitPrice != null) current else null
        val e = LinePolicy.edit(
          policy, line.free, base, current, manual, line.quantity, entry.percent, Keypad.plain(entry.value, 2),
        )
        val problem = e.problem
        val net = e.netUnit
        when {
          problem != null -> Applied.Problem(problem)
          net == null -> Applied.Unchanged
          else -> Applied.Done(SalesEngine.setLinePrice(cart, line.productId, net))
        }
      }
      else -> Applied.Unchanged
    }
  }
}
