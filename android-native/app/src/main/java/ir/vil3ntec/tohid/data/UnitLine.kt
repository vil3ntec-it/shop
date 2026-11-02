package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.UnitSale
import kotlin.math.abs

/**
 *  S05 (POS-09، PRD-07) — واحدهای ردیفِ صندوق برای یک کالا، برای `CartLineDialog` (برگهٔ ردیفِ گوشی و F3/Ctrl+U ِ
 *  کامپیوتر). قاعده در `core` (`UnitSale`)؛ این‌جا فقط کالا و ردیفِ سبد. پشتِ `TillGoodsPref.units` (پیش‌فرض خاموش).
 */
object UnitLine {

  /**
   *  @param startAt واحدی که برگه با آن باز می‌شود
   *  @param startCount تعدادِ نخستِ کادر به همان واحد
   *  @param packPriced قیمتِ دستیِ ردیف همان قیمتِ لیستِ واحدِ نخست است (دستی شمرده نمی‌شود)
   */
  data class Plan(val units: List<UnitSale.Unit>, val wholeOnly: Boolean, val startAt: Int, val startCount: Double, val packPriced: Boolean)

  fun units(p: Product): List<UnitSale.Unit> =
    UnitSale.units(p.unit, p.saleUnit, p.unitFactor, p.packs.orEmpty().map(ShopStore::coreOf))

  /**
   *  برنامهٔ برگهٔ ردیف؛ کالای تک‌واحد ⇒ `null` (همان برگهٔ پیشین). واحدِ نخست آن است که مقدارِ ردیف را بی اعشار
   *  می‌گیرد؛ [next] (Ctrl+U) ⇒ واحدِ بعدی، و تعدادِ نخست اگر به آن واحد درست نشود «۱» است.
   */
  fun plan(p: Product, wholeOnly: Boolean, quantity: Double, unitPrice: Double?, next: Boolean): Plan? {
    val units = units(p)
    if (units.size < 2) return null
    val fit = units.indexOfFirst { u -> unitPrice != null && UnitSale.lineUnitPrice(u)?.let { abs(it - unitPrice) < 1e-6 } == true }
      .takeIf { it >= 0 } ?: UnitSale.fitting(units, quantity)
    val at = if (next) UnitSale.next(units, fit) else fit
    val count = UnitSale.fromBase(quantity, units[at])
    val whole = abs(count - kotlin.math.round(count)) < 1e-9 && count > 0
    val priced = unitPrice != null && UnitSale.lineUnitPrice(units[fit])?.let { abs(it - unitPrice) < 1e-6 } == true
    return Plan(units, wholeOnly, at, if (whole || at == 0) count else 1.0, priced)
  }

  /** «کارتن (۱۲ دانه)» */
  fun label(u: UnitSale.Unit, base: String): String =
    if (u.sizeMilli == 1000L) u.name.ifBlank { "واحد" }
    else "${u.name} (${ir.vil3ntec.tohid.core.product.PackBarcodes.sizeText(u.sizeMilli)}${if (base.isBlank()) "" else " $base"})"
}
