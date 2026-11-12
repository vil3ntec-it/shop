package ir.vil3ntec.tohid.core.shopper

/**
 *  RPT-14 — تپشِ بالای قیف (بازدید ⇐ دیدنِ کالا ⇐ سبد) از برنامهٔ خرید، برای «گزارشِ فروشِ اینترنتی» ِ صاحب‌کار.
 *
 *  همان درِ ویترینِ وب (`store.js` ‹`beat`›، `POST /api/store/:slug/funnel/:step`، [ShopperApi.funnel]) و همان قاعده:
 *   • فقط وقتی دکان `features.funnel` دارد (پرچمِ پیش‌فرض خاموشِ `shop.orders.online_report`)؛ خاموش ⇒ هیچ درخواست.
 *   • یک بار برای هر گام در هر **بازکردنِ دکان** (رسیدنِ پروفایل، [ready]؛ همان «بارِ صفحه» ِ وب). سرور خودش هر بازدیدکننده را در روز
 *     یک بار می‌شمارد و گامِ n گام‌های پیش از آن را هم می‌نشاند؛ این‌جا فقط درخواستِ بیهوده فرستاده نمی‌شود.
 *   • گامی که پیش از رسیدنِ دکان رخ داد (پیوندِ مستقیمِ کالا) گم نمی‌شود: با رسیدنِ دکان ([ready]) پسِ «بازدید» می‌رود —
 *     اگر پرچم روشن بود؛ وگرنه دور ریخته می‌شود.
 *   • هیچ دادهٔ مشتری (نه شماره، نه شناسهٔ دستگاه، نه کالا) فرستاده نمی‌شود؛ شکستِ شبکه دوباره فرستاده نمی‌شود.
 *
 *  فقط روی نخِ صفحه صدا زده می‌شود (بی قفل)؛ فرستادن با [ShopperApi.funnel] روی نخِ شبکه.
 */
class ShopperFunnel {

  enum class Step(val wire: String) { VISIT("visit"), VIEW("view"), CART("cart") }

  private val sent = HashSet<Pair<String, Step>>()
  private val waiting = LinkedHashSet<Pair<String, Step>>()

  /**
   *  پروفایلِ دکان رسید — یعنی دکان (دوباره) باز شد: گام‌های همین دکان از نو، و همین حالا «بازدید» و هر گامِ منتظرِ همین
   *  دکان (به ترتیب) می‌روند. پرچمِ خاموش ⇒ هیچ، و منتظرها دور ریخته می‌شوند. دکان‌های دیگر دست نمی‌خورند.
   */
  fun ready(slug: String, page: StorePage): List<Step> {
    val queued = waiting.filter { it.first == slug }.map { it.second }
    waiting.removeAll { it.first == slug }
    sent.removeAll { it.first == slug }
    if (!page.features.funnel) return emptyList()
    return (listOf(Step.VISIT) + queued).distinct().onEach { sent.add(slug to it) }
  }

  /**
   *  [step] برای [slug] همین حالا برود؟ [page] پروفایلِ همین دکان، یا null وقتی هنوز نرسیده (⇒ منتظر تا [ready]).
   *  true یعنی نشان خورد و صدازننده یک بار می‌فرستد.
   */
  fun take(slug: String, page: StorePage?, step: Step): Boolean {
    if (page == null) {
      waiting.add(slug to step)
      return false
    }
    return page.features.funnel && sent.add(slug to step)
  }
}
