package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.money.Rounding
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 *  یک قلمِ سبد — قیمت همان که خریدار دیده است؛ پیش از ثبت با قیمتِ امروز
 *  تازه می‌شود (`/products?ids=`) و سرور هنگامِ ثبت دوباره می‌سنجد (CHK-17).
 */
@Serializable
data class CartLine(
  val productId: String,
  val name: String,
  val unit: String = "",
  val priceMinor: Long,
  val qtyMilli: Long,
  val maxQtyMilli: Long? = null,
  val photo: Boolean = false,
  /** دیگر در ویترین نیست یا ناموجود است ⇒ تا برداشته نشود ثبت نمی‌شود */
  val unavailable: Boolean = false,
) {
  /** همان گردکردنِ سرور: هر قلم به افغانیِ کامل (`order-states.lineTotalMinor`) */
  val lineTotalMinor: Long get() = Rounding.lineTotalMinor(qtyMilli, priceMinor, AFN_STEP)

  companion object {
    const val AFN_STEP = 100L
  }
}

/** سبدِ مهمانِ یک دکان — هر دکان جدا، مثلِ ویترینِ وب (`store-cart:<slug>`). */
@Serializable
data class ShopCart(
  val slug: String,
  val title: String = "",
  val accent: String = "",
  val lines: List<CartLine> = emptyList(),
  val updatedAt: Long = 0,
) {
  val subtotalMinor: Long get() = lines.sumOf { it.lineTotalMinor }

  /** شمارِ واحدها برای نشانِ سبد (۲ کیلو + ۳ عدد ⇒ ۵) */
  val unitCount: Long get() = lines.sumOf { it.qtyMilli } / QTY_STEP

  val hasUnavailable: Boolean get() = lines.any { it.unavailable }

  fun line(productId: String): CartLine? = lines.firstOrNull { it.productId == productId }

  fun qtyOf(productId: String): Long = line(productId)?.qtyMilli ?: 0
}

/** دکانِ سبد — برای نامِ دکان در فهرستِ سبدهای باز */
data class CartShop(val slug: String, val title: String, val accent: String = "")

sealed class AddOutcome {
  data class Added(val qtyMilli: Long) : AddOutcome()

  /** سقفِ هر سفارشِ این کالا رسید — همان قاعدهٔ سرور (`qty_over_limit`) */
  data class AtLimit(val maxQtyMilli: Long) : AddOutcome()

  /** ناموجود، «قابلِ سفارش نیست» یا بی‌قیمت */
  object NotOrderable : AddOutcome()

  /** سبد به سقفِ قلم‌های یک سفارش رسید (`MAX_CHECKOUT_LINES` ِ سرور) */
  data class CartFull(val maxLines: Int) : AddOutcome()
}

/** نتیجهٔ تازه کردنِ سبد با قیمت و موجودیِ امروز */
data class RefreshNotice(val priceChanged: Int, val gone: Int) {
  val isQuiet: Boolean get() = priceChanged == 0 && gone == 0
}

/** قیمتی که میانِ دیدن و ثبت عوض شد */
data class PriceChange(val productId: String, val name: String, val beforeMinor: Long, val afterMinor: Long)

const val QTY_STEP = 1000L

/** همان سقف‌های `parseCheckout` ِ سرور: قلم‌های یک سفارش و مقدارِ هر قلم */
const val MAX_CHECKOUT_LINES = 100
const val MAX_QTY_MILLI = StorefrontOwner.MAX_QTY_MILLI

/** سقفِ مقدارِ یک قلم: سقفِ خودِ کالا اگر هست، و هرگز بیش از سقفِ سرور */
fun qtyCap(maxQtyMilli: Long?): Long = maxQtyMilli?.let { minOf(it, MAX_QTY_MILLI) } ?: MAX_QTY_MILLI

/**
 *  سبدهای مهمانِ همهٔ دکان‌ها روی همین دستگاه.
 *
 *  ⛔ پول و مقدار عدد صحیح‌اند و جمع با همان گردکردنِ سرور حساب می‌شود، تا
 *  «جمعِ دیده‌شده» (`expectedTotalMinor`) با سرور یکی باشد.
 *  هر تغییر همان دم روی حافظه می‌نشیند؛ بسته شدنِ برنامه سبد را نمی‌برد.
 */
class ShopperCarts(
  private val storage: ShopperStorage,
  private val now: () -> Long = { System.currentTimeMillis() },
) {
  private val serializer = ListSerializer(ShopCart.serializer())
  private val carts = MutableStateFlow(
    storage.load(ShopperStorage.CARTS, serializer).orEmpty()
      .filter { it.slug.isNotBlank() && it.lines.isNotEmpty() }
      .associateBy { it.slug },
  )

  val state: StateFlow<Map<String, ShopCart>> = carts.asStateFlow()

  fun cart(slug: String): ShopCart? = carts.value[slug]

  /** سبدهای نیمه‌کاره، تازه‌ترین اول */
  fun openCarts(): List<ShopCart> = carts.value.values.sortedByDescending { it.updatedAt }

  @Synchronized
  fun add(shop: CartShop, product: StoreProduct): AddOutcome {
    val price = product.priceMinor
    if (price == null || !product.stock.orderable) return AddOutcome.NotOrderable
    val existing = cart(shop.slug)
    val current = existing?.qtyOf(product.id) ?: 0
    if (current == 0L && (existing?.lines?.size ?: 0) >= MAX_CHECKOUT_LINES) return AddOutcome.CartFull(MAX_CHECKOUT_LINES)
    val max = product.maxQtyMilli
    if (current + QTY_STEP > qtyCap(max)) return AddOutcome.AtLimit(qtyCap(max))
    val line = CartLine(
      productId = product.id, name = product.name, unit = product.unit, priceMinor = price,
      qtyMilli = current + QTY_STEP, maxQtyMilli = max, photo = product.photo,
    )
    edit(shop.slug, shop) { lines -> lines.upsert(line) }
    return AddOutcome.Added(line.qtyMilli)
  }

  /** مقدارِ تازه؛ از سقف بیشتر ⇒ همان سقف، صفر یا کمتر ⇒ برداشتن */
  @Synchronized
  fun setQty(slug: String, productId: String, qtyMilli: Long) {
    val line = cart(slug)?.line(productId) ?: return
    val clamped = minOf(qtyMilli, qtyCap(line.maxQtyMilli))
    edit(slug) { lines ->
      if (clamped <= 0) lines.filterNot { it.productId == productId } else lines.upsert(line.copy(qtyMilli = clamped))
    }
  }

  /** خواندن و نوشتن زیرِ همان قفل — دو ضربهٔ هم‌زمان دو واحد می‌شود، نه یکی */
  @Synchronized
  fun increment(slug: String, productId: String) = setQty(slug, productId, (cart(slug)?.qtyOf(productId) ?: 0) + QTY_STEP)

  @Synchronized
  fun decrement(slug: String, productId: String) = setQty(slug, productId, (cart(slug)?.qtyOf(productId) ?: 0) - QTY_STEP)

  @Synchronized
  fun remove(slug: String, productId: String) = edit(slug) { lines -> lines.filterNot { it.productId == productId } }

  /** برداشتنِ همهٔ قلم‌های ناموجود */
  @Synchronized
  fun removeUnavailable(slug: String) = edit(slug) { lines -> lines.filterNot { it.unavailable } }

  @Synchronized
  fun clear(slug: String) = edit(slug) { emptyList() }

  /**
   *  تازه کردن با پاسخِ `/products?ids=`: قیمت، نام، واحد و سقفِ امروز؛ نبودن یا
   *  ناموجودی ⇒ «ناموجود». قیمتِ عوض‌شده جایگزین می‌شود و شمرده می‌شود تا
   *  صفحه بگوید (S135).
   */
  @Synchronized
  fun applyRefresh(slug: String, fresh: List<StoreProduct>): RefreshNotice {
    val cart = cart(slug) ?: return RefreshNotice(0, 0)
    val byId = fresh.associateBy { it.id }
    var changed = 0
    var gone = 0
    val lines = cart.lines.map { line ->
      val p = byId[line.productId]
      val price = p?.priceMinor
      if (p == null || price == null || !p.stock.orderable) {
        if (!line.unavailable) gone++
        line.copy(unavailable = true)
      } else {
        if (price != line.priceMinor) changed++
        line.copy(
          name = p.name, unit = p.unit, priceMinor = price, maxQtyMilli = p.maxQtyMilli,
          photo = p.photo, unavailable = false,
          qtyMilli = minOf(line.qtyMilli, qtyCap(p.maxQtyMilli)),
        )
      }
    }
    edit(slug) { lines }
    return RefreshNotice(changed, gone)
  }

  /** تازه کردن از سرور؛ نشد ⇒ null و سبد همان می‌ماند (سرور هنگامِ ثبت می‌سنجد) */
  suspend fun refresh(slug: String, api: ShopperApi): RefreshNotice? {
    val ids = cart(slug)?.lines?.map { it.productId }.orEmpty()
    if (ids.isEmpty()) return RefreshNotice(0, 0)
    return api.productsByIds(slug, ids).valueOrNull()?.let { applyRefresh(slug, it) }
  }

  /** برگهٔ قیمتِ امروزِ سرور (`price_changed`) روی سبد — تغییرها برای نمایش */
  @Synchronized
  fun applyQuote(slug: String, quote: Quote): List<PriceChange> {
    val cart = cart(slug) ?: return emptyList()
    val byId = quote.lines.associateBy { it.productId }
    val changes = mutableListOf<PriceChange>()
    val lines = cart.lines.map { line ->
      val q = byId[line.productId] ?: return@map line
      if (q.unitPriceMinor == line.priceMinor) return@map line
      changes += PriceChange(line.productId, q.name.ifBlank { line.name }, line.priceMinor, q.unitPriceMinor)
      line.copy(priceMinor = q.unitPriceMinor)
    }
    edit(slug) { lines }
    return changes
  }

  @Synchronized
  fun markUnavailable(slug: String, ids: Collection<String>) {
    if (ids.isEmpty()) return
    edit(slug) { lines -> lines.map { if (it.productId in ids) it.copy(unavailable = true) else it } }
  }

  /** سقفی که سرور گفت (`qty_over_limit`) */
  @Synchronized
  fun applyLimit(slug: String, productId: String, maxQtyMilli: Long) {
    val line = cart(slug)?.line(productId) ?: return
    edit(slug) { lines ->
      lines.upsert(line.copy(maxQtyMilli = maxQtyMilli, qtyMilli = minOf(line.qtyMilli, maxQtyMilli)))
    }
  }

  /** «خریدِ دوباره»: فقط قلم‌های موجود با قیمتِ امروز، روی سبدِ همان دکان */
  @Synchronized
  fun fillFromReorder(shop: CartShop, reorder: Reorder): Int {
    val have = cart(shop.slug)?.lines?.map { it.productId }?.toSet().orEmpty()
    val room = (MAX_CHECKOUT_LINES - have.size).coerceAtLeast(0)
    val fresh = reorder.lines.filter { it.available && it.priceMinor != null && it.qtyMilli > 0 }
    //  سقفِ قلم‌ها: آن‌چه از قبل در سبد است جا دارد؛ قلمِ تازه تا جا هست
    val ok = fresh.filter { it.productId in have } + fresh.filterNot { it.productId in have }.take(room)
    if (ok.isEmpty()) return 0
    edit(shop.slug, shop) { lines ->
      ok.fold(lines) { acc, r ->
        acc.upsert(
          CartLine(
            productId = r.productId, name = r.name, unit = r.unit, priceMinor = r.priceMinor ?: 0,
            qtyMilli = minOf(r.qtyMilli, qtyCap(r.maxQtyMilli)), maxQtyMilli = r.maxQtyMilli,
          ),
        )
      }
    }
    return ok.size
  }

  private fun edit(slug: String, shop: CartShop? = null, change: (List<CartLine>) -> List<CartLine>) {
    val old = carts.value[slug]
    val lines = change(old?.lines.orEmpty())
    val next = carts.value.toMutableMap()
    if (lines.isEmpty()) next.remove(slug)
    else next[slug] = ShopCart(
      slug = slug,
      title = shop?.title ?: old?.title.orEmpty(),
      accent = shop?.accent ?: old?.accent.orEmpty(),
      lines = lines,
      updatedAt = now(),
    )
    carts.value = next
    storage.save(ShopperStorage.CARTS, serializer, next.values.toList())
  }

  private fun List<CartLine>.upsert(line: CartLine): List<CartLine> {
    val i = indexOfFirst { it.productId == line.productId }
    return if (i < 0) this + line else toMutableList().also { it[i] = line }
  }
}
