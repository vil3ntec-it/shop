package ir.vil3ntec.tohid.core.shopper

import kotlinx.serialization.Serializable

/** مرزِ نگه‌داری روی دستگاه (SharedPreferences ِ اندروید، NSUserDefaults ِ آیفون، نقشه در آزمون). */
interface ShopperKeyValue {
  fun get(key: String): String?
  fun put(key: String, value: String?)
}

/** سفارشی که از همین دستگاه ثبت شد — برای «سفارش‌های من» و پیگیری با توکن. */
@Serializable
data class SavedOrder(val slug: String, val token: String, val orderNo: Long, val totalMinor: Long, val at: Long, val storeTitle: String = "")

@Serializable
data class RecentShop(val slug: String, val title: String, val at: Long)

/**
 *  هر چه برنامهٔ خرید روی دستگاه نگه می‌دارد (مثلِ `localStorage` ِ وب). سبد، ♥ و «فهرستِ خرید» فقط با نشستِ حساب و از
 *  راهِ [AccountRules] با حساب هم‌گام می‌شوند؛ ⛔ خودِ نشست (۱۸۰ روزه) این‌جا نیست: نگه‌دارِ رمزشدهٔ سکو (Keystore ِ اندروید؛
 *  بی آن فقط در حافظهٔ همان اجرا). بلیتِ شماره کوتاه‌عمر است (`ticketTtlMs` ِ سرور). نشانی‌ها فقط همین‌جایند ([AddressBook]).
 */
@Serializable
data class ShopperState(
  val carts: List<Cart> = emptyList(),
  val orders: List<SavedOrder> = emptyList(),
  val recent: List<RecentShop> = emptyList(),
  val ticket: PhoneTicket? = null,
  /** فقط برای پُر کردنِ برگهٔ بعدی: نام و آخرین نشانی (همان `store-customer` ِ وب) */
  val name: String = "",
  val addressText: String = "",
  val landmark: String = "",
  /** کلیدِ یکتاییِ ثبتِ نیمه‌کارهٔ هر دکان ([CheckoutKeys]) */
  val keys: Map<String, KeyMemo> = emptyMap(),
  val lang: String = "fa",
  /** system · light · dark */
  val theme: String = "system",
  /** CHK-09: نشانی‌های نام‌دار با پین (حداکثر ۱۰، [AddressRules.MAX]) */
  val addresses: List<SavedAddress> = emptyList(),
  /** MKT-08: سفارش‌های تکرارشونده ([RepeatPlans]) */
  val plans: List<RepeatPlan> = emptyList(),
  /** MKT-02: شناسهٔ تصادفیِ همین نصب (۳۲ hex) — فقط وقتی دکان سدِ «یک بار برای هر دستگاه» دارد فرستاده می‌شود */
  val deviceId: String = "",
  /** MKT-06: آخرین خریدِ هر کالا از همین دستگاه برای «وقتِ دوباره خریدن؟» ([Refills]، سقفِ ۶۰) */
  val bought: List<Bought> = emptyList(),
  /** WEB-11: ♥ ِ هر دکان (تازه‌ترین اول، حداکثر ۲۰۰) و «فهرستِ خرید» (فقط شناسه و مقدار) — [AccountRules] */
  val favs: Map<String, List<String>> = emptyMap(),
  val lists: Map<String, List<ListItem>> = emptyMap(),
  /** هم‌گامیِ هر دکان با حسابِ مشتری در نشستِ کنونی (صف‌ها و نسخه‌ها؛ خودِ نشست در نگه‌دارِ رمزشده است، نه این‌جا) */
  val sync: Map<String, AccountSync> = emptyMap(),
)

/** خواندن و نوشتنِ [ShopperState] با سقف‌های روشن (هیچ فهرستی بی‌پایان بزرگ نمی‌شود). */
object ShopperMemory {
  const val KEY = "shopper.state.v1"
  const val MAX_CARTS = 20
  const val MAX_ORDERS = 30
  const val MAX_RECENT = 10

  /** خراب یا نبود ⇒ حالتِ تهی (برنامه هرگز با دادهٔ خرابِ دستگاه نمی‌افتد) */
  fun load(kv: ShopperKeyValue): ShopperState = try {
    kv.get(KEY)?.let { ShopperJson.decodeFromString(ShopperState.serializer(), it) } ?: ShopperState()
  } catch (_: Exception) {
    ShopperState()
  }

  fun save(kv: ShopperKeyValue, s: ShopperState) = kv.put(KEY, ShopperJson.encodeToString(ShopperState.serializer(), s))

  fun cartOf(s: ShopperState, slug: String): Cart = s.carts.firstOrNull { it.slug == slug } ?: Cart(slug)

  /** سبدِ تهی برداشته می‌شود؛ تازه‌ترین اول */
  fun withCart(s: ShopperState, cart: Cart): ShopperState {
    val rest = s.carts.filter { it.slug != cart.slug }
    return s.copy(carts = (if (cart.lines.isEmpty()) rest else listOf(cart) + rest).take(MAX_CARTS))
  }

  fun withOrder(s: ShopperState, o: SavedOrder): ShopperState =
    s.copy(orders = (listOf(o) + s.orders.filter { it.token != o.token }).take(MAX_ORDERS))

  fun withRecent(s: ShopperState, r: RecentShop): ShopperState =
    s.copy(recent = (listOf(r) + s.recent.filter { it.slug != r.slug }).take(MAX_RECENT))

  /** S3: دکان نشانی‌اش را عوض کرد ⇒ سبد، سفارش‌ها و دکان‌های اخیر به نشانیِ تازه */
  fun moved(s: ShopperState, from: String, to: String): ShopperState = s.copy(
    carts = s.carts.map { if (it.slug == from) it.copy(slug = to) else it }.distinctBy { it.slug },
    orders = s.orders.map { if (it.slug == from) it.copy(slug = to) else it },
    recent = s.recent.map { if (it.slug == from) it.copy(slug = to) else it }.distinctBy { it.slug },
    keys = s.keys.mapKeys { if (it.key == from) to else it.key },
    plans = RepeatPlans.moved(s.plans, from, to),
    bought = Refills.moved(s.bought, from, to),
    favs = s.favs.mapKeys { if (it.key == from) to else it.key },
    lists = s.lists.mapKeys { if (it.key == from) to else it.key },
    sync = s.sync.mapKeys { if (it.key == from) to else it.key },
  )

  /** همان الگوی `deviceId` ِ وب (`^[A-Za-z0-9_-]{8,128}$`)؛ نبود ⇒ تازه از [random] (مولّدِ امنِ سکو) */
  fun withDevice(s: ShopperState, random: () -> ByteArray): ShopperState {
    if (Regex("^[A-Za-z0-9_-]{8,128}$").matches(s.deviceId)) return s
    val hex = random().take(16).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return if (hex.length == 32) s.copy(deviceId = hex) else s
  }
}

/** نشانیِ دکان از هر چه مشتری می‌چسباند یا QR می‌دهد: `golestan`، `…/store/golestan`، `…/store/golestan#/p/rice`. */
object ShopAddress {
  /** همان `SLUG_RE` ِ `server/src/lib/storefront.js` */
  private val SLUG = Regex("^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$")
  private val IN_PATH = Regex("/store/([A-Za-z0-9-]{3,32})(?:[/?#]|$)")

  fun slugOf(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val raw = IN_PATH.find(t)?.groupValues?.get(1) ?: t.removePrefix("@")
    val s = raw.lowercase()
    return s.takeIf { SLUG.matches(it) }
  }
}
