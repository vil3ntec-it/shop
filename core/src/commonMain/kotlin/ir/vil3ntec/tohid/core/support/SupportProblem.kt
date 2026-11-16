package ir.vil3ntec.tohid.core.support

/**
 *  «از پشتیبانی بخواه» — فهرستِ یکتای مشکل‌هایی که کاربر باید از پشتیبانی بخواهد؛ **آینهٔ**
 *  `server/src/lib/support-problems.json` (سنجه: `SupportProblemMirrorTest` و `server/test/support-requests.test.js`).
 *
 *  - کد = «پایه» یا «پایه:پارامتر» (`flag_off:shop.orders`، `plan_required:delivery`، `subscription_expired`). سرور فقط همین‌ها
 *    را می‌پذیرد؛ کد و پارامتر هرگز روی صفحهٔ کاربر نمی‌آیند (قاعدهٔ «هیچ جزئیاتِ فنی در صفحه») — صفحه فقط `title` را می‌گوید.
 *  - `facts` واقعیت‌های مجازِ هر مشکل‌اند، هر کدام با شکلِ سخت (`FACTS`): نسخه، سکو، نامکِ صفحه، کدِ کوتاه، شمار. هیچ نشانی،
 *    ایمیل، توکن، کارت یا متنِ آزاد از [facts] نمی‌گذرد.
 *  - `fix` کارِ «درستش کن» ِ مدیر در پنلِ مرکزی است — برنامه فقط می‌فرستد، تصمیم با پشتیبانی.
 *  خالص و بی وابستگیِ سکو (راهِ آیفون).
 */
object SupportProblem {

  data class Text(val fa: String, val en: String, val ps: String)

  enum class Fix(val id: String) {
    ENABLE_FLAG("enable_flag"),
    GRANT_SUBSCRIPTION("grant_subscription"),
    RAISE_DEVICE_LIMIT("raise_device_limit"),
    RESEND_EMAIL_CODE("resend_email_code"),
    SHOW_SYNC_ERROR("show_sync_error"),
    NONE("none"),
  }

  data class Def(
    val code: String,
    val params: List<String>,
    val needsShop: Boolean,
    val fix: Fix,
    val facts: List<String>,
    val title: Text,
  )

  const val FLAG_OFF = "flag_off"
  const val PLAN_REQUIRED = "plan_required"
  const val SUBSCRIPTION_EXPIRED = "subscription_expired"
  const val DEVICE_LIMIT = "device_limit"
  const val SYNC_NO_SHOP = "sync_no_shop"
  const val SYNC_REFUSED = "sync_refused"
  const val EMAIL_CODE = "email_code_not_arriving"
  const val OTHER = "other"

  /** شکلِ هر واقعیت: `version` (2.14.5)، `platform` (android|desktop|ios|web)، `slug` ([a-z0-9][a-z0-9_.:-]{0,39})، `count` (۰ تا ۱٬۰۰۰٬۰۰۰) */
  val FACTS: Map<String, String> = linkedMapOf(
    "appVersion" to "version",
    "platform" to "platform",
    "screen" to "slug",
    "syncError" to "slug",
    "rejected" to "count",
    "devices" to "count",
    "maxDevices" to "count",
  )

  /** نامِ خواندنیِ هر پرچم/قابلیت، به‌جای {name} در متن‌ها */
  val NAMES: Map<String, Text> = linkedMapOf(
    "shop.orders" to Text("سفارشِ آنلاین", "Online orders", "آنلاین فرمایښتونه"),
    "shop.storefront" to Text("ویترین و بازارچه", "Storefront and marketplace", "ویترین او بازار"),
    "pay.hesabpay" to Text("پرداخت با حساب‌پی", "HesabPay payments", "د حساب‌پې تادیه"),
    "shop.delivery" to Text("تحویل و پیک", "Delivery and couriers", "سپارل او پیک"),
    "shop.chat" to Text("گفت‌وگو با مشتری", "Customer chat", "له پېرودونکي سره خبرې"),
    "shop.order_notify" to Text("خبرِ سفارشِ تازه", "New-order alerts", "د نوي فرمایښت خبرتیا"),
    "sales" to Text("فروش (صندوق)", "Sales (till)", "پلور (صندوق)"),
    "debtors" to Text("قرض‌داران", "Debtors", "پوروړي"),
    "barcode" to Text("اسکنرِ بارکد", "Barcode scanner", "د بارکوډ سکنر"),
    "multi_device" to Text("چند کاربر روی یک دکان", "Several users on one shop", "په یوه دوکان کې څو کاروونکي"),
    "online_orders" to Text("سفارشِ آنلاین", "Online orders", "آنلاین فرمایښتونه"),
    "storefront" to Text("ویترینِ اینترنتی", "Online storefront", "انټرنېټي ویترین"),
    "delivery" to Text("تحویل و پیک", "Delivery and couriers", "سپارل او پیک"),
  )

  private val BASE_FACTS = listOf("appVersion", "platform", "screen")

  val ALL: List<Def> = listOf(
    Def(
      FLAG_OFF, listOf("shop.orders", "shop.storefront", "pay.hesabpay", "shop.delivery", "shop.chat", "shop.order_notify"), true, Fix.ENABLE_FLAG, BASE_FACTS,
      Text("{name} برای این دکان خاموش است", "{name} is turned off for this shop", "{name} د دې دوکان لپاره بند دی"),
    ),
    Def(
      PLAN_REQUIRED, listOf("sales", "debtors", "barcode", "multi_device", "online_orders", "storefront", "delivery"), true, Fix.GRANT_SUBSCRIPTION, BASE_FACTS,
      Text("{name} در اشتراکِ این دکان نیست", "{name} is not in this shop's plan", "{name} د دې دوکان په ګډون کې نشته"),
    ),
    Def(
      SUBSCRIPTION_EXPIRED, emptyList(), true, Fix.GRANT_SUBSCRIPTION, BASE_FACTS,
      Text("اشتراکِ این دکان تمام شده است", "This shop's subscription has ended", "د دې دوکان ګډون پای ته رسېدلی"),
    ),
    Def(
      DEVICE_LIMIT, emptyList(), true, Fix.RAISE_DEVICE_LIMIT, BASE_FACTS + listOf("devices", "maxDevices"),
      Text("جای دستگاهِ تازه در اشتراک نیست", "There is no room for another device in the plan", "په ګډون کې د بلې وسیلې ځای نشته"),
    ),
    Def(
      SYNC_NO_SHOP, emptyList(), false, Fix.SHOW_SYNC_ERROR, BASE_FACTS + "syncError",
      Text("همگام‌سازی دکانی برای این حساب پیدا نمی‌کند", "Sync can't find a shop for this account", "همغږي د دې حساب لپاره دوکان نه مومي"),
    ),
    Def(
      SYNC_REFUSED, emptyList(), true, Fix.SHOW_SYNC_ERROR, BASE_FACTS + listOf("syncError", "rejected"),
      Text("سرور چند تغییرِ این دستگاه را نپذیرفت", "Some changes from this device were not accepted", "د دې وسیلې ځینې بدلونونه ونه منل شول"),
    ),
    Def(
      EMAIL_CODE, emptyList(), false, Fix.RESEND_EMAIL_CODE, BASE_FACTS,
      Text("کدِ ورود به ایمیل نمی‌رسد", "The sign-in code isn't arriving by email", "د ننوتلو کوډ برېښنالیک ته نه رسېږي"),
    ),
    Def(
      OTHER, emptyList(), false, Fix.NONE, BASE_FACTS,
      Text("کمک از پشتیبانی", "Help from support", "له ملاتړ څخه مرسته"),
    ),
  )

  private val BY_CODE: Map<String, Def> = ALL.associateBy { it.code }

  fun of(code: String): Def? = BY_CODE[code]

  /** «پایه:پارامتر» (یا «پایه») ⇒ تعریف و پارامتر؛ ناشناخته یا پارامترِ نامجاز ⇒ null (همان ۴۰۰ ِ سرور) */
  fun parse(full: String): Pair<Def, String>? {
    val s = full.trim()
    val i = s.indexOf(':')
    val def = of(if (i >= 0) s.substring(0, i) else s) ?: return null
    val param = if (i >= 0) s.substring(i + 1) else ""
    val ok = if (def.params.isEmpty()) param.isEmpty() else param in def.params
    return if (ok) def to param else null
  }

  /** کدِ کامل برای فرستادن; پارامترِ نامجاز ⇒ IllegalArgumentException (خطای برنامه‌نویس، نه کاربر) */
  fun code(problem: String, param: String = ""): String {
    val full = if (param.isEmpty()) problem else "$problem:$param"
    require(parse(full) != null) { "مشکلِ ناشناخته: $full" }
    return full
  }

  /** سرخطِ خواندنیِ همان مشکل؛ `pick` زبان را برمی‌گزیند (برنامه: `t(fa, en, ps)`) */
  fun title(full: String, pick: (Text) -> String): String {
    val (def, param) = parse(full) ?: return pick(of(OTHER)!!.title)
    val name = NAMES[param]?.let(pick).orEmpty()
    return pick(def.title).replace("{name}", name)
  }

  private val VERSION = Regex("""^v?\d{1,4}(\.\d{1,5}){0,3}([-+][A-Za-z0-9.]{1,20})?$""")
  private val SLUG = Regex("""^[a-z0-9][a-z0-9_.:-]{0,39}$""")
  private val PLATFORMS = setOf("android", "desktop", "ios", "web")

  /** فقط واقعیت‌های مجازِ همین مشکل، با شکلِ خودشان — همان `cleanContext` ِ سرور؛ بقیه بی‌صدا می‌افتند */
  fun facts(def: Def, raw: Map<String, Any?>): Map<String, Any> {
    val out = LinkedHashMap<String, Any>()
    for (k in def.facts) {
      val v = raw[k] ?: continue
      val ok: Any? = when (FACTS[k]) {
        "version" -> (v as? String)?.trim()?.takeIf { VERSION.matches(it) }
        "platform" -> (v as? String)?.takeIf { it in PLATFORMS }
        "slug" -> (v as? String)?.takeIf { SLUG.matches(it) }
        "count" -> (v as? Number)?.toLong()?.takeIf { v !is Double && v !is Float && it in 0..1_000_000 }
        else -> null
      }
      if (ok != null) out[k] = ok
    }
    return out
  }

  /**
   *  صفحهٔ قفلِ بخشِ پولی (`LockedFeature` ِ برنامه): اشتراکی که تمام شده ⇒ `subscription_expired`؛ وگرنه (اشتراک هست ولی همین بخش
   *  در آن نیست، یا سرور نپذیرفت) ⇒ `plan_required:<قابلیتِ همان بخش>` تا پشتیبانی بداند کدام قابلیت را بدهد. قابلیتِ ناشناخته ⇒
   *  همان `subscription_expired` (کدِ نامجاز هرگز ساخته نمی‌شود).
   */
  fun forLocked(feature: String?, expired: Boolean): String =
    if (!expired && feature != null && parse("$PLAN_REQUIRED:$feature") != null) code(PLAN_REQUIRED, feature) else SUBSCRIPTION_EXPIRED

  /**
   *  دروازهٔ «چرا سفارشِ آنلاین نمی‌رسد» (`lib/order-arrival.js`) که کارش پشتیبانی است ⇒ کدِ درخواست؛ دروازه‌ای که کارِ خودِ
   *  دکان است (ویترین، کالا، اشتراک) ⇒ null.
   */
  fun forOrderBlocker(blocker: String): String? = when (blocker) {
    "orders_off" -> code(FLAG_OFF, "shop.orders")
    "storefront_off" -> code(FLAG_OFF, "shop.storefront")
    "alerts_off" -> code(FLAG_OFF, "shop.order_notify")
    "shop_inactive", "storefront_suspended" -> OTHER
    else -> null
  }
}
