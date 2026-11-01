package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.parseNumber

/** نوشته‌های «قلمِ آزاد» */
data class FreeLineForm(val label: String = "", val priceText: String = "", val qtyText: String = "1")

/**
 *  قلمِ آزاد (`sale/free-line`) — چیزی که در فهرستِ کالاها نیست: همان قاعدهٔ
 *  `FreeLineDialog` ِ پیشین (نام، قیمتِ بیش از صفر، تعدادِ بیش از صفر) و همان
 *  `SalesEngine.addFreeLine`؛ جمعِ پیش‌نمایش `lineTotal` ِ موتور.
 */
object FreeLineModel {
  const val LABEL = "label"
  const val PRICE = "price"
  const val QTY = "qty"

  fun price(f: FreeLineForm): Double? = parseNumber(f.priceText)
  fun quantity(f: FreeLineForm): Double? = parseNumber(f.qtyText)

  /** خطای هر کادر (کلید: LABEL، PRICE، QTY) — خالی ⇒ درست */
  fun errors(f: FreeLineForm): Map<String, String> = buildMap {
    if (f.label.isBlank()) put(LABEL, StrSale.nameNeeded)
    when (val p = price(f)) {
      null -> put(PRICE, if (f.priceText.isBlank()) StrSale.positiveNeeded else StrSale.badNumberShort)
      else -> if (p <= 0) put(PRICE, StrSale.positiveNeeded)
    }
    when (val q = quantity(f)) {
      null -> put(QTY, StrSale.badNumberShort)
      else -> if (q <= 0) put(QTY, StrSale.positiveNeeded)
    }
  }

  fun total(f: FreeLineForm): Double = SalesEngine.lineTotal(price(f) ?: 0.0, quantity(f)?.takeIf { it > 0 } ?: 0.0)

  /** سبدِ تازه با همین قلم (درست نبود ⇒ همان سبد) */
  fun add(lines: List<SalesEngine.CartLine>, f: FreeLineForm, newId: () -> String): List<SalesEngine.CartLine> {
    if (errors(f).isNotEmpty()) return lines
    return SalesEngine.addFreeLine(lines, f.label.trim(), price(f) ?: 0.0, quantity(f) ?: 1.0, newId)
  }
}
