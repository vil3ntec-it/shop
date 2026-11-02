package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.promo.Loyalty
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 *  PRM-13 — «قاعدهٔ یکسانِ امتیاز» ِ دکان از سرور (`/api/shop/loyalty`، پشتِ پرچمِ خاموشِ `shop.loyalty_rule`) — همان
 *  الگوی `ReceiptFlag`: پرسیدن با اتصالِ سوکتِ زنده (`hello`)، صندوق و صفحهٔ تنظیم، حداکثر هر [CHECK_MS] یک بار؛ پاسخ به
 *  حسابِ دکان بسته است و روی همین دستگاه می‌ماند تا بی‌اینترنت هم همان قاعده به کار رود (`LoyaltyRules.resolve`).
 *
 *  پاسخِ `{enabled:false}` (پرچمِ خاموش) نسخهٔ نگه‌داشته را پاک می‌کند ⇒ هر دستگاه همان قاعدهٔ خودش؛ خطای شبکه و سرورِ
 *  پیشین (۴۰۴) هیچ چیز را عوض نمی‌کنند. نوشتن ([setRule]) فقط صاحب/مدیر — سرور می‌سنجد، نه فقط پنهان‌شدنِ دکمه.
 */
object LoyaltyShop {

  private const val PATH = "/shop/loyalty"
  private const val PREFS = "tohid-policy"

  /** هر چند وقت یک بار دوباره پرسیده شود */
  const val CHECK_MS = 10 * 60_000L

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private fun key(context: Context) = "loyalty_shop:" + LedgerOwner.currentShop(context).ifBlank { "-" }

  /** آخرین پاسخِ همین دکان روی همین دستگاه؛ نبود یا خرابی ⇒ `null` */
  fun cached(context: Context): LoyaltyRules.Shop? = runCatching {
    prefs(context).getString(key(context), null)?.let { LoyaltyRules.parse(Json.parseToJsonElement(it).jsonObject) }
  }.getOrNull()

  private fun keep(context: Context, s: LoyaltyRules.Shop?) {
    val e = prefs(context).edit()
    if (s == null) e.remove(key(context)) else e.putString(key(context), LoyaltyRules.serialize(s).toString())
    e.apply()
  }

  /** بپرس (نه زودتر از [CHECK_MS]، مگر [force]) */
  fun kick(context: Context, now: Long = System.currentTimeMillis(), force: Boolean = false) {
    val app = context.applicationContext
    val at = key(app) + ":at"
    if (!force && now - prefs(app).getLong(at, 0L) < CHECK_MS) return
    prefs(app).edit().putLong(at, now).apply()
    scope.launch { refresh(app) }
  }

  /** همین حالا از سرور؛ پاسخ ⇒ نگه‌داشته عوض می‌شود. خطا ⇒ همان قبلی و `false` */
  suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
    val body = runCatching { Backend.api(context).get(PATH) }.getOrNull() ?: return@withContext false
    keep(context, LoyaltyRules.parse(body))
    true
  }

  /** ذخیرهٔ قاعدهٔ یکسانِ دکان؛ `null` = نوشته شد، وگرنه پیامِ خطا (نقشِ دیگر، پرچمِ خاموش، نسخهٔ کهنه، بی‌اینترنت) */
  suspend fun setRule(context: Context, on: Boolean, rule: Loyalty.Rule): String? = withContext(Dispatchers.IO) {
    if (!rule.valid) return@withContext StrLoyalty.badRule
    val version = cached(context)?.version
    runCatching { Backend.api(context).put(PATH, LoyaltyRules.body(on, rule, version)) }.fold(
      onSuccess = { body -> keep(context, LoyaltyRules.parse(body)); null },
      onFailure = { it.message?.takeIf { m -> m.isNotBlank() } ?: StrLoyalty.shopSaveFailed },
    )
  }
}

/** مرزِ سکوی [LoyaltyRules.current]: قاعدهٔ در کارِ همین لحظه (دکانِ همین لحظه). `ShopStore` یک بار وصل می‌کند */
internal fun LoyaltyRules.bind(context: Context) {
  val app = context.applicationContext
  bindOnce { LoyaltyPref.effective(app) }
}
