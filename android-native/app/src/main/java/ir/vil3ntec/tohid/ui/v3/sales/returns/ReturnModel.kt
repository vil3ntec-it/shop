package ir.vil3ntec.tohid.ui.v3.sales.returns

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.qty
import kotlin.math.roundToLong

/** یک قلمِ قابلِ برگشت */
data class ReturnLine(val item: SaleItem, val name: String, val unit: String, val left: Double, val step: Double)

/**
 *  مرجوعیِ ساده (`sales/{id}/return` وقتی «برگشت و تعویض» خاموش است) — خالص: قلم‌های
 *  قابلِ برگشت (`SalesEngine.returnable`)، پلهٔ −/+ همان سبد (`cartStep`)، و پیش‌نمایشِ مبلغ با
 *  **همان فرمولِ ثبت** (`recordReturn`: قیمتِ همان فاکتور × سهمِ پس از تخفیفِ فاکتور) تا عددی
 *  که دیده می‌شود همان باشد که ثبت می‌شود. ثبت با خودِ موتور است.
 */
object ReturnModel {

  fun lines(d: ShopData, sale: Sale): List<ReturnLine> {
    val products = d.products.associateBy { it.id }
    return d.saleItems.filter { it.saleId == sale.id && SalesEngine.returnable(it) > 0 }.map { item ->
      val unit = products[item.productId]?.unit.orEmpty()
      ReturnLine(item, SalesEngine.itemName(d, item), unit, SalesEngine.returnable(item), SalesEngine.cartStep(unit))
    }
  }

  fun leftText(l: ReturnLine): String = StrSale.returnableLeft(qty(l.left) + if (l.unit.isNotBlank()) " ${l.unit}" else "")

  /** نوشتهٔ کادر ⇒ مقدار (خالی/نادرست ⇒ ۰)، در بازهٔ مجاز */
  fun value(text: String?, l: ReturnLine): Double = (parseNumber(text.orEmpty()) ?: 0.0).coerceIn(0.0, l.left)

  /** −/+ با پلهٔ همان واحد، در بازهٔ ۰ تا قابلِ برگشت */
  fun bump(text: String?, l: ReturnLine, sign: Int): String {
    val next = ((parseNumber(text.orEmpty()) ?: 0.0) + sign * l.step).coerceIn(0.0, l.left)
    val rounded = (next * 1000).roundToLong() / 1000.0
    return if (rounded <= 0) "" else qty(rounded)
  }

  /** مقدارِ بیش از قابلِ برگشت ⇒ خطای درجا (موتور هم کوتاهش می‌کند) */
  fun error(text: String?, l: ReturnLine): String? {
    val v = text?.takeIf { it.isNotBlank() }?.let(::parseNumber) ?: return if (text.isNullOrBlank()) null else StrSale.badNumberShort
    return if (v > l.left + 1e-9) StrSale.overReturnable else null
  }

  /** مبلغِ برگشت — همان فرمولِ ثبت ([SalesEngine.returnAmount]) */
  fun amount(sale: Sale, l: ReturnLine, q: Double): Double = SalesEngine.returnAmount(sale, l.item, q)

  fun total(sale: Sale, lines: List<ReturnLine>, texts: Map<String, String>): Double =
    lines.sumOf { amount(sale, it, value(texts[it.item.id], it)) }

  /** آنچه به `recordReturn` می‌رود: فقط مقدارهای بیش از صفر */
  fun quantities(texts: Map<String, String>): Map<String, Double> =
    texts.mapNotNull { (id, t) -> parseNumber(t)?.takeIf { it > 0 }?.let { id to it } }.toMap()
}
