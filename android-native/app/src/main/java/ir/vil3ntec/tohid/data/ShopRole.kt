package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  نقشِ این حساب در دکان — و آنچه از آن برمی‌آید.
 *
 *  ── چرا لازم شد ────────────────────────────────────────────────────
 *  سرور نقش را همیشه می‌دانست و اجازه‌ها را هم اجرا می‌کرد، ولی
 *  **برنامه** نمی‌دانست. یعنی شاگرد همان صفحه‌هایی را می‌دید که صاحب
 *  دکان: تنظیمات باز بود، کلیدِ حساب و کدِ شاگرد جلویش بود، و سود و
 *  زیانِ دکان را می‌خواند.
 *
 *  قرارِ صاحب دکان روشن است: شاگرد بفروشد، ببیند، کار کند — ولی
 *  تنظیمات را اصلاً باز نکند و عددهای مالی را نبیند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  این فقط برای **ظاهر** است. سدِ واقعی سمتِ سرور است و آنجا سرِ جایش
 *  هست؛ اینجا کاری می‌کند که شاگرد اصلاً به درِ بسته نخورد.
 */
object ShopRole {

  private const val PREFS = "tohid-ledger"   // کنارِ صاحبِ دفتر، تا با هم پاک شوند
  private const val KEY = "shop_role"
  private const val SHOP_ID = "shop_id"
  //  `policy`ِ سرور (STF-01..03) — لایه‌های دکان و همین کاربر، به همان شکلِ JSON
  private const val POLICY = "shop_policy"

  /** نقش‌هایی که سرور می‌شناسد */
  const val OWNER = "owner"
  const val MANAGER = "manager"
  const val STAFF = "staff"
  /**
   *  پیک (DLV-01، v3 §۳.۱۱) — توکنش فقط درهای `/api/courier/…` را باز می‌کند، پس
   *  برنامه به‌جای دکان «حالتِ پیک» را باز می‌کند و همگام‌سازیِ دفتر را
   *  اصلاً راه نمی‌اندازد (سرور هم `403 courier_scope` می‌دهد).
   */
  const val COURIER = "courier"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /**
   *  نقشِ ذخیره‌شده.
   *
   *  خالی یعنی هنوز از سرور نپرسیده‌ایم. آن‌وقت **صاحب** فرض می‌شود، نه
   *  شاگرد: کسی که آفلاین و بی‌حساب کار می‌کند دکانِ خودش را دارد و
   *  نباید تنظیماتِ خودش قفل باشد.
   */
  fun current(context: Context): String =
    prefs(context).getString(KEY, "").orEmpty().ifBlank { OWNER }

  fun remember(context: Context, role: String) {
    val clean = role.trim().lowercase()
    if (clean.isBlank()) return
    val p = prefs(context)
    //  نقش عوض شد ⇒ لایه‌های قبلی دیگر مالِ این نقش نیستند (تا پرسشِ بعدیِ سرور)
    val edit = p.edit().putString(KEY, clean)
    if (p.getString(KEY, null) != clean) edit.remove(POLICY)
    edit.apply()
    courierMode.value = clean == COURIER
    policyVersion.value++
  }

  /** شناسهٔ دکان — «برگهٔ امضاشده»ی تأیید (STF-02) به آن بسته است */
  fun rememberShopId(context: Context, id: String?) {
    val clean = id?.trim().orEmpty()
    if (clean.isNotEmpty()) prefs(context).edit().putString(SHOP_ID, clean).apply()
  }

  fun shopId(context: Context): String = prefs(context).getString(SHOP_ID, "").orEmpty()

  /** `policy`ِ پاسخِ `GET /api/shop`؛ null یعنی «بی لایه» = همان رفتارِ امروزِ نقش */
  fun rememberPolicy(context: Context, policyJson: String?) {
    val e = prefs(context).edit()
    if (policyJson.isNullOrBlank()) e.remove(POLICY) else e.putString(POLICY, policyJson)
    e.apply()
    policyVersion.value++
  }

  fun forget(context: Context) {
    prefs(context).edit().remove(KEY).remove(POLICY).remove(SHOP_ID).apply()
    courierMode.value = false
    policyVersion.value++
  }

  /**
   *  تصمیم با هستهٔ مشترک (`core/perm/PermCore`) — همان که سرور و وب دارند.
   *  بی لایه دقیقاً همان `!isStaff`ِ پیشین است (آزمونِ مرجع: `PermCoreTest`).
   */
  fun allows(context: Context, key: String): Boolean =
    PermPolicy.allows(current(context), prefs(context).getString(POLICY, null), key)

  /** حالتِ پیک: نقشِ ذخیره‌شده «پیک» است */
  fun isCourier(context: Context): Boolean =
    prefs(context).getString(KEY, "").orEmpty() == COURIER

  /**
   *  «الان پیک است؟» — زنده، تا صفحهٔ اصلی از هر راهی که نقش عوض شد (ورود
   *  با کد، پیوستن از صفحهٔ ساختِ دکان، پرسشِ دوبارهٔ سرور) همان لحظه به
   *  حالتِ پیک برود یا از آن بیرون بیاید.
   */
  val courierMode = kotlinx.coroutines.flow.MutableStateFlow(false)

  /** هر بار نقش یا `policy` عوض شد یک بالا می‌رود — صفحه‌ای که مجوز را `remember` کرده دوباره می‌سنجد */
  val policyVersion = kotlinx.coroutines.flow.MutableStateFlow(0)

  /** شاگرد است — نه صاحب، نه مدیر */
  fun isStaff(context: Context): Boolean = current(context) == STAFF

  /**
   *  حق دیدنِ عددهای مالی: درآمد، فایده، ضرر.
   *
   *  شاگرد می‌فروشد ولی نمی‌داند دکان چقدر سود می‌کند. این خواستهٔ
   *  صاحب دکان است، نه یک تصمیمِ فنی.
   */
  fun canSeeMoney(context: Context): Boolean = allows(context, "reports.money")

  /** حق باز کردنِ تنظیمات — همان `settings.write`ِ سرور */
  fun canOpenSettings(context: Context): Boolean = allows(context, "settings.write")

  /**
   *  تخفیف و لغوِ فاکتور — همان `sales.discount` و `sales.cancel`ِ سرور
   *  (`server/src/lib/permissions.js`). سرور opِ شاگرد را رد می‌کند؛ این‌جا
   *  فقط دکمه پنهان می‌شود تا opِ ردشده‌ای ساخته نشود.
   */
  fun canDiscount(context: Context): Boolean = allows(context, "sales.discount")
  fun canCancelSale(context: Context): Boolean = allows(context, "sales.cancel")

  /**
   *  تصمیمِ کامل برای یک کارِ عددی (STF-02/03): allow، approve («درخواستِ
   *  تأیید») یا deny — همان `evaluate`ی که سرور روی op می‌دواند.
   *  `req` مثلاً `{"pct": 20.0, "amount": 2000.0}` (مبلغ به Minor).
   */
  fun decide(context: Context, key: String, req: Map<String, Double>? = null): ir.vil3ntec.tohid.core.perm.PermCore.Decision =
    ir.vil3ntec.tohid.core.perm.PermCore.evaluate(
      current(context), PermPolicy.layers(prefs(context).getString(POLICY, null)), key, req,
    )

  /** کادرِ تخفیف دیده شود؟ هر چیزی جز «ممنوع» — «نیازمند تأیید» یعنی با PINِ مدیر */
  fun mayDiscount(context: Context): Boolean = decide(context, "sales.discount").decision != "deny"

  /** سابقهٔ عملیات — همان `audit.view`ِ سرور */
  fun canSeeAudit(context: Context): Boolean = allows(context, "audit.view")

  /** لایه‌های `policy` برای تصمیمِ عددی (RET-10: `ReturnFlow.permDecision`) */
  fun layers(context: Context): List<Any?> = PermPolicy.layers(prefs(context).getString(POLICY, null))

  /**
   *  باز کردنِ دستیِ کشو بی فروش (`no_sale`) — `drawer.open`ِ هسته. شاگرد
   *  پیش‌فرض نه؛ «نیازمندِ تأیید» هم این‌جا یعنی نه (برگهٔ تأیید هنوز در
   *  برنامه نیست)، همان کشوی بی فروشِ #286.
   */
  fun canOpenDrawer(context: Context): Boolean = allows(context, "drawer.open")
}
