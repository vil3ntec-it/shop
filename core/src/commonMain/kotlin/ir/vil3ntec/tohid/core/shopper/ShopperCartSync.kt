package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.StockBadge

/*
 *  CHK-03 — سبدِ حسابِ مشتری در برنامهٔ خرید: ماشینِ کوچک و خالصِ هم‌گامی که [AccountRules] برای گام‌های سبد به کار می‌برد
 *  (درهای `GET`/`PUT /:slug/account/cart` و `POST …/cart/merge` ِ `server/src/lib/store-cart.js`؛ بردارِ مشترکِ
 *  `core/test-vectors/account-cart-v1.json`):
 *    ادغام ── یک بار در هر نشست و فقط وقتی سبدِ مهمان هست (بیشینه، S140)؛ سبدِ خالی ⇒ همان یک خواندن. پس از پاسخ سبدِ دستگاه
 *             آینهٔ سبدِ حساب است ([AccountSync.cartM]) و دیگر هرگز ادغام نمی‌شود.
 *    نوشتن ── تغییرهای پشتِ‌هم پس از [DEBOUNCE_MS] سکوت یک `PUT` با آخرین نسخه. ۴۰۹ `cart_changed` ⇒ همان تغییرِ دستگاه
 *             ([rebase]) روی سبدِ تازهٔ درونِ ۴۰۹ و فقط [CONFLICT_RETRIES] بار پشتِ‌هم؛ بیشتر ⇒ دورِ بعد با درنگ (بی حلقه).
 *    خطا ──── بی اینترنت، ۵xx، ۴۲۹ ⇒ سبدِ دستگاه می‌ماند و دور با درنگِ فزاینده ([backoffMs]، `Retry-After` پذیرفته) دوباره؛
 *             هیچ خطایی از این دور به صفحه نمی‌رود.
 *    سرور ─── نام، قیمت، «هست؟»، موجودی و سقف همیشه از پاسخِ امروزِ سرور ([adopt])؛ شمارِ قیمتِ تازه و «دیگر نیست» برای پیام.
 *    خروج ─── سبدی که آینهٔ حساب بود از دستگاه پاک می‌شود ([forget])؛ سبدِ مهمانِ ادغام‌نشده (پرچمِ خاموش، بی نشست) می‌ماند.
 *  ⛔ پرچمِ خاموشِ `shop.storefront.cart` ⇒ سرور این درها را ندارد (`next('route')` ⇒ ۴۰۱ ِ درِ ناشناخته، نه `session_required`)
 *     ⇒ [doorClosed]: فقط سبدِ دستگاه، هرگز خروج از حساب.
 */
object CartSync {
  const val DEBOUNCE_MS = 800L
  const val CONFLICT_RETRIES = 1
  const val RETRY_MIN_MS = 2_000L
  const val RETRY_MAX_MS = 120_000L
  /** سقفِ `Retry-After` ِ پذیرفته (همان پنجرهٔ ربع‌ساعتهٔ سطل‌های ویترین) و سقفِ هر درنگ (ساعتِ عوض‌شدهٔ گوشی) */
  const val RETRY_AFTER_MAX_MS = 15 * 60_000L
  const val CART_CHANGED = "cart_changed"
  const val CART_FULL = "cart_full"
  const val SESSION_REQUIRED = "session_required"

  /** نتیجهٔ [adopt]: سبدِ تازهٔ دستگاه و شمارِ قلم‌هایی که قیمتشان عوض شد یا دیگر سفارش‌پذیر نیستند */
  data class Adopted(val cart: Cart, val repriced: Int, val gone: Int)

  /** n-امین شکستِ پشتِ‌هم (۱…) ⇒ درنگ: ۲، ۴، ۸ … تا ۱۲۰ ثانیه؛ `Retry-After` ِ بزرگ‌تر (تا ربع ساعت) پذیرفته */
  fun backoffMs(fails: Int, retryAfterSeconds: Int? = null): Long {
    val n = fails.coerceIn(1, 7)
    val exp = minOf(RETRY_MIN_MS shl (n - 1), RETRY_MAX_MS)
    val hint = (retryAfterSeconds ?: 0).toLong().coerceIn(0L, RETRY_AFTER_MAX_MS / 1000) * 1000
    return maxOf(exp, hint)
  }

  /** گذرا ⇒ بعد دوباره: بی اینترنت، ۴۰۸، ۴۲۹، ۵xx، پاسخِ خراب، و ۴۰۹ ِ سبد پس از تلاشِ دوباره */
  fun retryable(e: ShopperError): Boolean =
    e.network || e.status == 408 || e.status == 429 || e.status >= 500 || e.code == ShopperError.BAD_RESPONSE || e.code == CART_CHANGED

  /** درِ سبد در این دکان نیست: پرچمِ خاموش (۴۰۱ بی `session_required`) یا ویترینِ بسته (۴۰۴) — نه پاسخِ خرابِ میانه */
  fun doorClosed(e: ShopperError): Boolean =
    e.code != ShopperError.BAD_RESPONSE && (e.status == 404 || (e.status == 401 && e.code != SESSION_REQUIRED))

  /**
   *  تغییرِ در صفِ دستگاه روی سبدِ تازهٔ سرور: هر کالایی که دستگاه نسبت به [base] (سبدی که آخرین بار از سرور دید یا فرستاد)
   *  افزوده، کم/زیاد کرده یا برداشته همان مقدارِ دستگاه را می‌گیرد؛ هر کالای دیگر همان [fresh] است (کارِ دستگاهِ دیگر گم
   *  نمی‌شود). یادداشت از سرور (برنامه یادداشت نمی‌نویسد). بیش از ۱۰۰ قلم ⇒ اول قلم‌های دستگاه.
   */
  fun rebase(base: List<AccountItem>, local: List<AccountItem>, fresh: List<AccountItem>): List<AccountItem> {
    val b = base.associateBy { it.productId }
    val l = local.associateBy { it.productId }
    val f = fresh.associateBy { it.productId }
    val ids = (fresh.map { it.productId } + local.map { it.productId }).distinct()
    val out = ids.mapNotNull { id ->
      val mine = l[id]
      val theirs = f[id]
      val touched = mine?.qtyMilli != b[id]?.qtyMilli
      val q = (if (touched) mine?.qtyMilli else theirs?.qtyMilli) ?: return@mapNotNull null
      AccountItem(id, q, (theirs ?: mine)?.note?.takeIf { it.isNotBlank() })
    }
    if (out.size <= CartRules.MAX_LINES) return out
    return (out.filter { it.productId in l } + out.filter { it.productId !in l }).take(CartRules.MAX_LINES)
  }

  /** قلمِ سبدِ حساب امروز سفارش‌پذیر است؟ (همان [CartRules.canOrder]: در ویترین، با قیمت، نه «ناموجود»/«قابلِ سفارش نیست») */
  fun orderable(l: AccountCartLine): Boolean =
    l.available && l.priceMinor != null && l.stock?.status != StockBadge.OUT_OF_STOCK && l.stock?.status != StockBadge.UNAVAILABLE

  /**
   *  سبدِ دستگاه از [items] (پس از [rebase]) و پاسخِ امروزِ سرور [c]: نام، واحد، قیمت، سقف و «هست؟» از سرور؛ قلمی که سرور
   *  هنوز ندارد (افزودهٔ دستگاه در میانهٔ درخواست) همان قلمِ دستگاه؛ مقدارِ بالای سقفِ تازه همان سقف؛ عکسِ قلمِ هم‌شناسه می‌ماند.
   *  بی تغییر ⇒ همان [cur] (زمانِ تغییرِ سبد دست نمی‌خورد).
   */
  fun adopt(cur: Cart, items: List<AccountItem>, c: AccountCart, at: Long): Adopted {
    val old = cur.lines.associateBy { it.productId }
    val srv = c.lines.associateBy { it.productId }
    var repriced = 0
    var gone = 0
    val lines = items.mapNotNull { i ->
      val o = old[i.productId]
      val l = srv[i.productId] ?: return@mapNotNull o?.copy(qtyMilli = i.qtyMilli)
      val off = !orderable(l)
      val price = l.priceMinor ?: o?.priceMinor ?: 0L
      if (o != null && !off && !o.unavailable && price != o.priceMinor) repriced++
      if (off && (o == null || !o.unavailable)) gone++
      val cap = l.maxQtyMilli?.takeIf { it > 0 }
      CartLine(
        productId = i.productId, name = l.name ?: o?.name.orEmpty(), unit = l.unit ?: o?.unit.orEmpty(),
        priceMinor = price, qtyMilli = if (cap != null && i.qtyMilli > cap) cap else i.qtyMilli, maxQtyMilli = l.maxQtyMilli,
        photo = o?.photo ?: false, photoVersion = o?.photoVersion, unavailable = off,
      )
    }
    return Adopted(if (lines == cur.lines) cur else Cart(cur.slug, lines, at), repriced, gone)
  }

  /** خروج: سبدی که آینهٔ حساب بود ([AccountSync.cartM]) از دستگاه می‌رود؛ سبدِ مهمانِ ادغام‌نشده می‌ماند */
  fun forget(s: ShopperState): ShopperState {
    val mirrored = s.sync.filterValues { it.cartM }.keys
    if (mirrored.isEmpty()) return s
    return s.copy(carts = s.carts.filter { it.slug !in mirrored })
  }

  /**
   *  چند میلی‌ثانیه پیش از دورِ تازهٔ [slug] صبر شود: درنگِ پس از شکست ([AccountSync.retryAt]) و سکوتِ [DEBOUNCE_MS] پس از
   *  آخرین تغییرِ سبدی که هنوز نرفته. بی هم‌گامی (بی نشست) ⇒ ۰.
   */
  fun waitMs(s: ShopperState, slug: String, now: Long): Long {
    val st = s.sync[slug] ?: return 0
    val backoff = (st.retryAt - now).coerceIn(0L, RETRY_AFTER_MAX_MS)
    val cart = ShopperMemory.cartOf(s, slug)
    val pending = st.cartM && AccountRules.mark(AccountRules.cartItems(cart, st.cartNotes)) != st.cartK
    val quiet = if (pending) (cart.updatedAt + DEBOUNCE_MS - now).coerceIn(0L, DEBOUNCE_MS) else 0L
    return maxOf(backoff, quiet)
  }

  /** پس از هر پاسخ: موفق ⇒ شمارِ شکست صفر؛ شکستِ گذرا که دور را ایستاند ⇒ درنگِ بعدی (هر دور، هر کار) */
  internal fun paced(out: AccountOutcome, slug: String, e: ShopperError?, at: Long): AccountOutcome {
    if (out.signedOut || out.cartOff) return out
    val st = out.state.sync[slug] ?: return out
    val next = when {
      e == null -> if (st.fails == 0 && st.retryAt == 0L) return out else st.copy(fails = 0, retryAt = 0)
      e != null && !out.go && retryable(e) -> st.copy(fails = st.fails + 1, retryAt = at + backoffMs(st.fails + 1, e.retryAfterSeconds))
      else -> return out
    }
    return out.copy(state = out.state.copy(sync = out.state.sync + (slug to next)))
  }
}
