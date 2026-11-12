package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  کدام تنظیمِ `tohid-policy`، `tohid-receipt` و `tohid-modules` **مالِ دکان** است (به همهٔ دستگاه‌ها می‌رود) و کدام
 *  **مالِ همین دستگاه** (چاپگر، کشو، بارکدخوان، صفحه، آموزش) — `docs/DATA-SAFETY-fa.md` §۶، ۱۴۰۵/۰۸/۱۸.
 *
 *  ── قاعدهٔ دسته‌بندی ─────────────────────────────────────────────
 *  • **دکان** ([Scope.SHOP]): قاعده‌ای که پول، موجودی، برگشت، مالیات، روزِ کاری یا کنترلِ کارمند را تعیین می‌کند، و
 *    قابلیتِ کسب‌وکار که روشن بودنش روی یک دستگاه و خاموشی‌اش روی دیگری دکان را دوپاره می‌کرد (کارتِ هدیه، امانت،
 *    پیش‌فاکتور، فهرستِ قیمت…). دو صندوقِ یک دکان نباید دو گردکردنِ نقد یا دو قاعدهٔ برگشت داشته باشند.
 *  • **دستگاه** ([Scope.DEVICE]): سخت‌افزار و جای همین دستگاه (کشو و شیفتِ برگزیده، چاپِ برچسب، کارت/بارکدخوان، جای
 *    دیسکِ چت)، صفحه و گزارش‌های فقط‌خواندنی، پیشنهادها، «تازه‌ها»، حالتِ آموزشی و پرچم‌های آزمایشیِ ماژول.
 *  • **سرور** ([Scope.SERVER]): نسخهٔ محلیِ چیزی که منبعش خودِ سرور است (قاعدهٔ یکسانِ امتیاز PRM-13، فاکتورِ نسخهٔ
 *    ۲، سربرگِ رسید) — با درِ خودش می‌آید، نه از این‌جا.
 *  • ⛔ کلیدِ تازه‌ای که در این فهرست نیست **هرگز** همگام نمی‌شود (پیش‌فرضِ امن: مالِ همین دستگاه) و آزمونِ
 *    `ShopSettingsSyncTest` سرخ می‌شود تا دسته بگیرد.
 *
 *  نوعِ هر کلید ([Key.type]) همان است که خواننده‌اش می‌خواند (`getLong`، `getInt`…): مقدارِ نوعِ دیگر هرگز
 *  نشانده نمی‌شود (`getLong` روی `Int` در اندروید پرتاب می‌کند).
 */
object ShopSettingsKeys {

  const val POLICY = "tohid-policy"
  const val RECEIPT = "tohid-receipt"
  const val MODULES = "tohid-modules"
  val STORES = listOf(POLICY, RECEIPT, MODULES)

  enum class Scope { SHOP, DEVICE, SERVER }

  /** [type]: `b` بولی، `s` رشته، `i` Int، `l` Long، `f` Float، `set` مجموعهٔ رشته — همان قالبِ `ShopSettingsBundle` */
  class Key(val store: String, val name: String, val type: String, val scope: Scope) {
    val id: String get() = "$store/$name"
  }

  private fun shop(name: String, type: String) = Key(POLICY, name, type, Scope.SHOP)
  private fun device(name: String, type: String, store: String = POLICY) = Key(store, name, type, Scope.DEVICE)

  val KEYS: List<Key> = listOf(
    //  پول و صندوق
    shop("cash_rounding", "l"),                 // CashRoundPref: گردکردنِ نقد
    shop("auto_fees", "s"),                     // AutoFeesPref: کارمزدِ خودکار
    shop("checkout_block_zero_total", "b"),     // CheckoutRules
    shop("checkout_customer_from_minor", "l"),
    shop("line_pricing", "b"),                  // LinePolicy: ویرایشِ قیمتِ ردیف و سقفش
    shop("line_price_direction", "s"),
    shop("free_line_cap_minor", "l"),
    shop("pay_v2", "b"),                        // ShopPolicy: پرداختِ ترکیبی و صندوقِ شیفت
    shop("credit_gate", "b"),                   // ShopPolicy: سقفِ قرض
    shop("block_expired", "b"),                 // ShopPolicy: فروشِ تاریخ‌گذشته
    shop("blind_count", "b"),                   // ShopPolicy: شمارشِ کورِ شیفت برای کارمند
    shop("pay_fix", "b"),                       // POS-40: اصلاحِ روشِ پرداخت
    shop("till_receive", "b"),                  // POS-39: دریافت در صندوق (F7)
    shop("line_seller", "b"),                   // POS-23/STF-08: فروشندهٔ هر ردیف (پایهٔ پورسانت)
    shop("quick_register", "b"),                // TillGoodsPref
    shop("unit_sale", "b"),
    shop("whole_packs", "b"),
    shop("price_lists", "b"),                   // فهرستِ قیمت در صندوق
    shop("customer_prices", "b"),               // POS-18: قیمتِ خاصِ مشتری (`CustomerPricePrefs`)
    shop("gift_cards", "b"),                    // PAY-24
    shop("layaway", "b"),                       // امانت
    shop("quotes", "b"),                        // پیش‌فاکتور
    shop("special_orders", "b"),
    shop("safe_book", "b"),                     // PAY-18: گاوصندوق
    shop("fx_tender", "b"),                     // PAY-10: پرداخت با ارزِ نقد (`FxTillPref`)
    shop("fx_rates", "s"),                      // PAY-10: جدولِ نرخِ خودِ دکان (JSON)
    shop("pay_methods_off", "set"),             // PAY-06: روش‌هایی که دکان از پنلِ پرداخت برداشته (`PayMethodsPref`)
    //  برگشت
    shop("returns_v2", "b"),                    // ReturnSettings
    shop("return_limit", "f"),
    shop("return_reasons", "s"),
    shop("return_blocked", "s"),                // ReturnPolicy
    shop("return_window_days", "i"),
    //  کالا، انبار و تاریخ
    shop("expiry_approval", "b"),               // ExpiryPref
    shop("expiry_near_days", "i"),
    shop("count_blind", "b"),                   // INV-06/07/08: انبارگردانیِ کور
    shop("stock_places", "b"),                  // INV-20/21
    shop("stock_repack", "b"),
    shop("stock_bom", "b"),                     // INV-21: دستورِ ساخت و سریِ تولید
    //  مشتری و وفاداری (قاعدهٔ همین دستگاه؛ قاعدهٔ سرور PRM-13 جدا: `loyalty_shop:`)
    shop("loyalty", "b"),
    shop("loyalty_every_afn", "l"),
    shop("loyalty_point_afn", "l"),
    shop("loyalty_max_share", "i"),
    shop("loyalty_silver_afn", "l"),
    shop("loyalty_gold_afn", "l"),
    shop("loyalty_expiry_months", "i"),
    shop("debt_reminder_template", "s"),
    //  مالیات، روزِ کاری و کنترل
    shop("tax_quarter", "b"),
    shop("tax_quarter_rate_bp", "i"),
    shop("tax_quarter_start_month", "i"),
    shop("tax_quarter_due_months", "i"),
    shop("tax_quarter_due_day", "i"),
    shop("tax_quarter_remind_days", "i"),
    shop("sale_tax", "s"),                      // POS-37: مالیاتِ فاکتور («invoice»/«line»، `SaleTaxPref`)
    shop("sale_tax_bp", "i"),
    shop("sale_tax_rates", "set"),
    shop("day_end_hour", "i"),                  // ShopDayChoice: پایانِ روزِ کاری (گزارش‌ها)
    shop("week_start", "i"),                    // ShopDayChoice: آغازِ هفتهٔ دکان (LOC-03)
    shop("closed_days", "s"),                   // ShopDayChoice: روزهای تعطیلِ هفتگی (LOC-03)
    shop("fraud_trail", "b"),                   // RPT-09: سابقهٔ ضدتقلب

    //  ── مالِ همین دستگاه ──
    device("multi_drawer", "b"),                // PAY-17: کشو و شیفتِ برگزیدهٔ همین دستگاه
    device("multi_drawer_shift", "s"),
    device("label_tools", "b"),                 // چاپِ برچسب (چاپگرِ همین دستگاه)
    device("customer_card", "b"),               // کارت/بارکدخوانِ صندوق
    device("count_tools", "b"),                 // پنجرهٔ اسکنِ انبارگردانی
    device("chat_media_guard", "b"),            // سنجشِ جای دیسکِ همین دستگاه
    device("training_mode", "b"),               // OPS-07: آموزش روی همین دستگاه
    device("training_staff", "b"),
    device("whats_new", "b"),                   // «تازه‌ها»
    device("report_keys", "b"),                 // کلیدهای صفحهٔ گزارش
    device("crm_reports", "b"),                 // گزارش‌ها (فقط خواندنی؛ پاک‌سازیِ ادغام روی یک دستگاه بس است)
    device("doc_gaps", "b"),
    device("shrink_report", "b"),
    device("simple_ask", "b"),
    device("stock_insight", "b"),
    device("stock_levels", "b"),                // دو کادرِ فرمِ کالا (داده‌اش همگام می‌شود)
    device("product_tools", "b"),               // ابزارهای فرم و ورود از فایل
    device("product_suggest", "b"),
    device("ai_forecast", "b"),
    device("ai_anomaly", "b"),
    device("on", "set", MODULES),               // ModuleFlags: پرچم‌های آزمایشیِ همین دستگاه
  )

  /** کلیدهایی که نامِ دکان یا نشانه در خودشان دارند — همه نسخهٔ محلیِ سرور */
  val PREFIXES: List<Triple<String, String, Scope>> = listOf(
    Triple(POLICY, "loyalty_shop:", Scope.SERVER),   // LoyaltyShop (PRM-13) و `:at`
    Triple(RECEIPT, "v2:", Scope.SERVER),            // ReceiptFlag و `:at`
    Triple(RECEIPT, "debt-minor:", Scope.SERVER),
    Triple(RECEIPT, "profile:", Scope.SERVER),       // ReceiptProfileStore
  )

  private val byId: Map<String, Key> = KEYS.associateBy { it.id }

  /** دستهٔ کلید؛ `null` ⇒ ناشناخته (هرگز همگام نمی‌شود) */
  fun scopeOf(store: String, key: String): Scope? =
    byId["$store/$key"]?.scope ?: PREFIXES.firstOrNull { it.first == store && key.startsWith(it.second) }?.third

  /** کلیدِ «مالِ دکان» یا `null` */
  fun synced(store: String, key: String): Key? = byId["$store/$key"]?.takeIf { it.scope == Scope.SHOP }

  val SYNCED: List<Key> = KEYS.filter { it.scope == Scope.SHOP }

  /* ------------------------------ مقدار ------------------------------ */

  /** مقدارِ یک کلید با نوعش — `null` در نقشه یعنی «نیست/برداشته» */
  data class Value(val t: String, val v: JsonElement) {
    /** برابری با نوع: عددِ `2.0` ِ Float همان `2` ِ سرور است */
    fun same(o: Value?): Boolean {
      if (o == null || o.t != t) return false
      return when (t) {
        "f" -> num(v)?.toFloat() == num(o.v)?.toFloat()
        "i", "l" -> (v as? JsonPrimitive)?.longOrNull == (o.v as? JsonPrimitive)?.longOrNull
        "set" -> strings(v) == strings(o.v)
        else -> v == o.v
      }
    }
  }

  private fun num(e: JsonElement): Double? = (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
  private fun strings(e: JsonElement): List<String>? =
    (e as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }?.distinct()?.sorted()

  /** مقدارِ `SharedPreferences` ⇒ [Value] اگر همان نوعِ [key] است */
  fun encode(key: Key, raw: Any?): Value? = when {
    raw is Boolean && key.type == "b" -> Value("b", JsonPrimitive(raw))
    raw is String && key.type == "s" -> Value("s", JsonPrimitive(raw))
    raw is Int && key.type == "i" -> Value("i", JsonPrimitive(raw))
    raw is Long && key.type == "l" -> Value("l", JsonPrimitive(raw))
    raw is Float && key.type == "f" && raw.isFinite() -> Value("f", JsonPrimitive(raw))
    raw is Set<*> && key.type == "set" && raw.all { it is String } ->
      Value("set", JsonArray(raw.map { it as String }.sorted().map { JsonPrimitive(it) }))
    else -> null
  }

  /** مقدارِ سرور ⇒ [Value] ِ درستِ همان کلید، یا `null` (نوعِ دیگر یا شکلِ نادرست: نادیده) */
  fun decode(key: Key, t: String, v: JsonElement?): Value? {
    if (t != key.type || v == null || v is JsonNull) return null
    val p = v as? JsonPrimitive
    return when (t) {
      "b" -> p?.takeIf { !it.isString }?.booleanOrNull?.let { Value(t, JsonPrimitive(it)) }
      "s" -> p?.takeIf { it.isString }?.let { Value(t, JsonPrimitive(it.content)) }
      "i" -> p?.takeIf { !it.isString }?.longOrNull?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.let { Value(t, JsonPrimitive(it.toInt())) }
      "l" -> p?.takeIf { !it.isString }?.longOrNull?.let { Value(t, JsonPrimitive(it)) }
      "f" -> p?.let { num(it) }?.toFloat()?.takeIf { it.isFinite() }?.let { Value(t, JsonPrimitive(it)) }
      "set" -> strings(v)?.let { s -> Value(t, JsonArray(s.map { JsonPrimitive(it) })) }
      else -> null
    }
  }

  /** نوشتنی برای `SharedPreferences.Editor` — `null` یعنی برداشتن */
  fun raw(value: Value?): Any? = value?.let {
    val p = it.v as? JsonPrimitive
    when (it.t) {
      "b" -> p?.booleanOrNull
      "s" -> p?.content
      "i" -> p?.longOrNull?.toInt()
      "l" -> p?.longOrNull
      "f" -> p?.let { x -> num(x) }?.toFloat()
      "set" -> strings(it.v)?.toSet()
      else -> null
    }
  }
}

/**
 *  نقشهٔ یک دورِ همگام‌سازیِ تنظیماتِ دکان — منطقِ خالص، بی `Context` (`ShopSettingsSyncTest`).
 *
 *  ── قاعده ────────────────────────────────────────────────────────
 *  • «سایه» آخرین مقدارِ همگام‌شدهٔ هر کلید روی همین دستگاه است؛ مقدارِ محلیِ متفاوت با سایه = تغییرِ همین دستگاه.
 *  • تعارض: آخرین نویسنده **با ساعتِ سرور** برنده، هر کلید جدا — تغییرِ این دستگاه (اگر صاحب/مدیر است) فرستاده
 *    می‌شود و چون پس از ردیفِ سرور می‌رسد برنده است؛ بقیهٔ کلیدها مقدارِ سرور را می‌گیرند. ساعتِ دستگاه هیچ‌جا نیست.
 *  • نخستین بار (بی سایه و بی cursor): کلیدی که سرور دارد ⇒ مقدارِ سرور (دستگاهِ دوم تنظیمِ دکان را می‌گیرد، نه
 *    این‌که پیش‌فرض‌های خودش را روی همه بنویسد)؛ کلیدی که سرور ندارد ⇒ فرستاده می‌شود (نخستین دستگاه دکان را می‌کارد).
 *  • کارمند (نه صاحب، نه مدیر) هرگز نمی‌فرستد؛ تغییرِ محلی‌اش روی کلیدی که سرور دارد به مقدارِ سرور برمی‌گردد.
 *  • فقط [ShopSettingsKeys.SYNCED]؛ ردیفِ سرور با کلیدِ ناشناخته یا نوعِ دیگر نادیده (هرگز تنظیمِ دستگاه را نمی‌نشاند).
 */
object ShopSettingsPlan {

  class Remote(val store: String, val key: String, val t: String, val v: JsonElement?, val rev: Long)

  /**
   *  @property apply مقدارِ تازهٔ محلی (`null` = برداشتن)
   *  @property push فرستادنی (`null` = برداشتن)
   *  @property shadow سایهٔ تازه (فرستاده‌ها پس از موفقیت جدا افزوده می‌شوند)
   */
  class Plan(
    val apply: Map<String, ShopSettingsKeys.Value?>,
    val push: Map<String, ShopSettingsKeys.Value?>,
    val shadow: Map<String, ShopSettingsKeys.Value?>,
  )

  fun plan(
    local: Map<String, ShopSettingsKeys.Value?>,
    shadow: Map<String, ShopSettingsKeys.Value?>,
    remote: List<Remote>,
    first: Boolean,
    canWrite: Boolean,
  ): Plan {
    val keys = ShopSettingsKeys.SYNCED.associateBy { it.id }
    //  آخرین ردیفِ سرور برای هر کلید (به ترتیبِ rev)؛ «del» ⇒ null
    val server = LinkedHashMap<String, ShopSettingsKeys.Value?>()
    for (r in remote.sortedBy { it.rev }) {
      val k = ShopSettingsKeys.synced(r.store, r.key) ?: continue
      if (r.t == "del") { server[k.id] = null; continue }
      server[k.id] = ShopSettingsKeys.decode(k, r.t, r.v) ?: continue
    }
    val apply = LinkedHashMap<String, ShopSettingsKeys.Value?>()
    val push = LinkedHashMap<String, ShopSettingsKeys.Value?>()
    val nextShadow = LinkedHashMap(shadow.filterKeys { it in keys })
    for (id in keys.keys) {
      val mine = local[id]
      val hasServer = id in server
      val dirty = if (first) mine != null && !hasServer else !same(mine, shadow[id])
      when {
        dirty && canWrite -> push[id] = mine
        //  کارمندِ بی اجازهٔ نوشتن هم: مقدارِ سرور
        hasServer -> {
          val theirs = server[id]
          if (!same(mine, theirs)) apply[id] = theirs
          nextShadow[id] = theirs
        }
        //  کارمند، بی ردیفِ تازه: سایه همان آخرین مقدارِ سرور است ⇒ برمی‌گردد
        dirty && id in shadow -> apply[id] = shadow[id]
        //  نه تغییرِ محلی (یا بی اجازه و بی مقدارِ سرور)، نه ردیفِ سرور ⇒ هیچ
        else -> Unit
      }
    }
    return Plan(apply, push, nextShadow)
  }

  private fun same(a: ShopSettingsKeys.Value?, b: ShopSettingsKeys.Value?): Boolean =
    if (a == null || b == null) a == null && b == null else a.same(b)
}
