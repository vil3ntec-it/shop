package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.market.StockBadge
import ir.vil3ntec.tohid.core.money.Rounding
import kotlinx.serialization.Serializable

/** یک قلمِ سبد روی همین دستگاه؛ `priceMinor` قیمتی است که مشتری دید (سرور دوباره می‌سنجد). */
@Serializable
data class CartLine(
  val productId: String,
  val name: String,
  val unit: String = "",
  val priceMinor: Long,
  val qtyMilli: Long,
  val maxQtyMilli: Long? = null,
  val photo: Boolean = false,
  val photoVersion: String? = null,
  /** دیگر در ویترین نیست (تازه‌سازی یا ۴۰۹ ِ `product_unavailable`) ⇒ تا برداشته نشود ثبت بسته است */
  val unavailable: Boolean = false,
)

/** سبدِ یک دکان (هر دکان سفارشِ جدا دارد، مثلِ «سبدهای این دستگاه» ِ بازارچه). */
@Serializable
data class Cart(val slug: String, val lines: List<CartLine> = emptyList(), val updatedAt: Long = 0)

/**
 *  قاعده‌های سبد — همان `store.js` ِ وب و سرور: پلهٔ «+ / −» یک واحد (۱۰۰۰ هزارم)، سقفِ هر کالا
 *  (`maxQtyMilli`)، گردِ هر قلم به افغانیِ کامل (`Rounding.lineTotalMinor` = `order-states.lineTotalMinor` ِ سرور)
 *  و حداکثر ۱۰۰ قلم (`MAX_CHECKOUT_LINES`).
 */
object CartRules {
  const val STEP_MILLI = 1000L
  const val MAX_LINES = 100
  const val MAX_QTY_MILLI = 10_000_000L
  private const val AFN_STEP_MINOR = 100L

  fun lineTotal(qtyMilli: Long, priceMinor: Long): Long = Rounding.lineTotalMinor(qtyMilli, priceMinor, AFN_STEP_MINOR)

  fun lineTotal(line: CartLine): Long = lineTotal(line.qtyMilli, line.priceMinor)

  fun subtotal(cart: Cart?): Long = cart?.lines?.sumOf { lineTotal(it) } ?: 0

  /** شمارِ قلم‌ها (نه مقدار): «۳ قلم» */
  fun count(cart: Cart?): Int = cart?.lines?.size ?: 0

  fun qtyOf(cart: Cart?, productId: String): Long = cart?.lines?.firstOrNull { it.productId == productId }?.qtyMilli ?: 0

  fun hasUnavailable(cart: Cart?): Boolean = cart?.lines?.any { it.unavailable } == true

  /** کالای بی قیمت، «ناموجود» یا «قابلِ سفارش نیست» به سبد نمی‌رود (۳۲.۳: نامعلوم «در حالِ بررسی» است و می‌رود). */
  fun canOrder(p: PublicProduct): Boolean =
    p.priceMinor != null && p.stock?.status != StockBadge.OUT_OF_STOCK && p.stock?.status != StockBadge.UNAVAILABLE

  /** «+» آیا سقف را می‌شکند؟ */
  fun atMax(qtyMilli: Long, maxQtyMilli: Long?): Boolean =
    qtyMilli + STEP_MILLI > (maxQtyMilli?.takeIf { it > 0 } ?: MAX_QTY_MILLI)

  /** یکی بیشتر از [p]؛ سقف/کالای ناسفارشی/سبدِ پُر ⇒ همان سبد (برابرِ `===`). */
  fun add(cart: Cart, p: PublicProduct, at: Long): Cart {
    if (!canOrder(p)) return cart
    val cur = qtyOf(cart, p.id)
    if (atMax(cur, p.maxQtyMilli)) return cart
    if (cur == 0L && cart.lines.size >= MAX_LINES) return cart
    val line = CartLine(
      productId = p.id, name = p.name, unit = p.unit, priceMinor = p.priceMinor!!, qtyMilli = cur + STEP_MILLI,
      maxQtyMilli = p.maxQtyMilli, photo = p.photo, photoVersion = p.photoVersion,
    )
    return put(cart, line, at)
  }

  /** مقدارِ تازهٔ یک قلمِ موجود (۰ یا کمتر ⇒ برداشته)؛ بالای سقف ⇒ همان سقف. */
  fun setQty(cart: Cart, productId: String, qtyMilli: Long, at: Long): Cart {
    val line = cart.lines.firstOrNull { it.productId == productId } ?: return cart
    if (qtyMilli <= 0) return remove(cart, productId, at)
    val cap = line.maxQtyMilli?.takeIf { it > 0 } ?: MAX_QTY_MILLI
    val q = minOf(qtyMilli, cap)
    if (q == line.qtyMilli) return cart
    return put(cart, line.copy(qtyMilli = q), at)
  }

  fun step(cart: Cart, productId: String, up: Boolean, at: Long): Cart =
    setQty(cart, productId, qtyOf(cart, productId) + if (up) STEP_MILLI else -STEP_MILLI, at)

  fun remove(cart: Cart, productId: String, at: Long): Cart =
    if (cart.lines.none { it.productId == productId }) cart
    else cart.copy(lines = cart.lines.filter { it.productId != productId }, updatedAt = at)

  private fun put(cart: Cart, line: CartLine, at: Long): Cart {
    val i = cart.lines.indexOfFirst { it.productId == line.productId }
    val lines = if (i < 0) cart.lines + line else cart.lines.toMutableList().also { it[i] = line }
    return cart.copy(lines = lines, updatedAt = at)
  }

  /** نتیجهٔ تازه‌سازی: سبدِ تازه، شمارِ قیمتِ عوض‌شده و شمارِ دیگر-نیست. */
  data class Refreshed(val cart: Cart, val repriced: Int, val gone: Int)

  /**
   *  سبد با کاتالوگِ امروز (`GET /products?ids=`): قیمت، نام، سقف و عکس تازه می‌شود؛ کالایی که در پاسخ نیست یا
   *  دیگر سفارش‌پذیر نیست «خط‌خورده» می‌ماند (حذفِ بی‌صدا نه — مشتری خودش برمی‌دارد). مقدارِ بالای سقفِ تازه کم می‌شود.
   */
  fun refresh(cart: Cart, fresh: List<PublicProduct>, at: Long): Refreshed {
    val byId = fresh.associateBy { it.id }
    var repriced = 0
    var gone = 0
    val lines = cart.lines.map { l ->
      val p = byId[l.productId]
      if (p == null || !canOrder(p)) {
        if (!l.unavailable) gone++
        l.copy(unavailable = true)
      } else {
        if (p.priceMinor != l.priceMinor) repriced++
        val cap = p.maxQtyMilli?.takeIf { it > 0 }
        l.copy(
          name = p.name, unit = p.unit, priceMinor = p.priceMinor!!, maxQtyMilli = p.maxQtyMilli,
          qtyMilli = if (cap != null && l.qtyMilli > cap) cap else l.qtyMilli,
          photo = p.photo, photoVersion = p.photoVersion, unavailable = false,
        )
      }
    }
    val next = if (lines == cart.lines) cart else cart.copy(lines = lines, updatedAt = at)
    return Refreshed(next, repriced, gone)
  }

  /** ۴۰۹ ِ `price_changed`: قیمتِ هر قلم همان برگهٔ امروزِ سرور؛ برمی‌گرداند کدام قلم‌ها عوض شدند (پیش ⇐ پس). */
  fun applyQuote(cart: Cart, quote: PriceQuote, at: Long): Pair<Cart, List<Triple<String, Long, Long>>> {
    val byId = quote.lines.associateBy { it.productId }
    val changed = mutableListOf<Triple<String, Long, Long>>()
    val lines = cart.lines.map { l ->
      val q = byId[l.productId] ?: return@map l
      if (q.unitPriceMinor == l.priceMinor) l
      else {
        changed += Triple(l.name, l.priceMinor, q.unitPriceMinor)
        l.copy(priceMinor = q.unitPriceMinor)
      }
    }
    return (if (changed.isEmpty()) cart else cart.copy(lines = lines, updatedAt = at)) to changed
  }

  /** ۴۰۹ ِ `product_unavailable`: همان قلم‌ها خط‌خورده */
  fun markUnavailable(cart: Cart, ids: Collection<String>, at: Long): Cart {
    if (ids.isEmpty() || cart.lines.none { it.productId in ids && !it.unavailable }) return cart
    return cart.copy(lines = cart.lines.map { if (it.productId in ids) it.copy(unavailable = true) else it }, updatedAt = at)
  }

  /** ۴۰۰ ِ `qty_over_limit`: سقفِ تازه و مقدارِ همان سقف */
  fun capAt(cart: Cart, productId: String, maxQtyMilli: Long, at: Long): Cart {
    val l = cart.lines.firstOrNull { it.productId == productId } ?: return cart
    val q = minOf(l.qtyMilli, maxQtyMilli)
    return put(cart, l.copy(maxQtyMilli = maxQtyMilli, qtyMilli = q), at).let { if (q <= 0) remove(it, productId, at) else it }
  }
}
