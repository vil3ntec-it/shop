package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.pos.KeypadInput

/** کالایی که صفحه‌کلیدِ مقدار برایش باز است */
data class KeypadItem(
  val product: Product,
  /** موجودیِ فروختنی همین لحظه */
  val stock: Double,
  /** مقدارِ کنونی در سبد (۰ ⇒ هنوز نیست) */
  val inCart: Double,
) {
  /** وزنی/پیمانه‌ای (کیلو، لیتر، …) ⇒ اعشار و مقدارهای آمادهٔ ۰٫۲۵ تا ۲ */
  val weighed: Boolean get() = SalesEngine.isFractionalUnit(product.unit)
}

/** کارِ دکمهٔ پایینِ صفحه‌کلید */
enum class KeypadAction { ADD, SET, REMOVE }

/**
 *  صفحه‌کلیدِ مقدار/وزن (`sale/keypad/{productId}`) — همان رفتارِ `PhoneQtyKeypad` ِ پیشین،
 *  خالص: مقدار با رقم‌ها (`KeypadInput`)، خطای اعشار/موجودی پیش از ثبت (`quantityProblem`،
 *  `shortageMessage`)، و «ثبت» مقدارِ همان کالا در سبد را **همین عدد** می‌کند.
 */
object KeypadModel {

  fun item(d: ShopData, productId: String, lines: List<SalesEngine.CartLine>): KeypadItem? {
    val p = d.products.find { it.id == productId } ?: return null
    return KeypadItem(p, ShopStore.stock(d, p.id), lines.filter { it.productId == p.id }.sumOf { it.quantity })
  }

  fun start(item: KeypadItem): String = if (item.inCart > 0) qty(item.inCart) else ""

  fun presets(item: KeypadItem): List<String> = if (item.weighed) listOf("0.25", "0.5", "1", "2") else listOf("1", "2", "5", "10")

  /** ۳×۴ رقم: آخرین ردیف «.»/خالی، «۰» و «⌫» */
  fun keys(item: KeypadItem): List<Char> = "123456789".toList() + listOf(if (item.weighed) '.' else ' ', '0', KeypadInput.BACKSPACE)

  fun press(text: String, key: Char, item: KeypadItem): String = KeypadInput.press(text, key, item.weighed)

  fun value(text: String): Double = KeypadInput.value(text)

  /** خطای این مقدار برای همین کالا؛ null ⇒ درست */
  fun problem(item: KeypadItem, q: Double): String? {
    if (q <= 0) return null
    val p = item.product
    SalesEngine.quantityProblem(p, q)?.let { return it }
    return when {
      item.stock <= 0 -> SalesEngine.shortageMessage(p, q, item.stock)
      q > item.stock -> StrSale.onlyInStock(CartModel.stockLeft(p, item.stock))
      else -> null
    }
  }

  fun action(item: KeypadItem, q: Double): KeypadAction = when {
    item.inCart > 0 && q <= 0 -> KeypadAction.REMOVE
    item.inCart > 0 -> KeypadAction.SET
    else -> KeypadAction.ADD
  }

  fun canConfirm(item: KeypadItem, q: Double): Boolean = problem(item, q) == null && (q > 0 || item.inCart > 0)

  /** مبلغِ همین مقدار — `lineTotal` ِ موتور */
  fun amount(item: KeypadItem, q: Double): Double = SalesEngine.lineTotal(item.product.salePrice, q)

  /** سبدِ تازه: کالای در سبد ⇒ همین مقدار (۰ ⇒ برداشته)؛ تازه ⇒ افزوده */
  fun apply(lines: List<SalesEngine.CartLine>, item: KeypadItem, q: Double): List<SalesEngine.CartLine> = when {
    lines.any { it.productId == item.product.id } -> SalesEngine.setCartQty(lines, item.product.id, q)
    q > 0 -> SalesEngine.addToCart(lines, item.product.id, q)
    else -> lines
  }
}
