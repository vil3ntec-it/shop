package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 *  پرچمِ فاکتورِ نسخهٔ ۲ — `shop.receipt_v2` در `live_config` ِ سرور، **پیش‌فرض خاموش**
 *  (RULES: رفتارِ تازه در پول، صندوق، قالبِ داده یا همگام‌سازی فقط پشتِ پرچم).
 *
 *  خاموش ⇒ همان رفتارِ پیشین: همان چیدمانِ فاکتور (`Receipt.render`، `HwPages.saleBytes`)،
 *  بی توکنِ QR روی فروش (`Receipts.newToken`) و بی پرسیدنِ سربرگ و لوگو. تنها کارِ شبکه
 *  پرسیدنِ خودِ پرچم است (`GET /shop/receipt-profile/config`)، با اتصالِ سوکتِ زنده و حداکثر
 *  هر [CHECK_MS] یک بار — همان الگوی `OrdersLive`. مقدار به حسابِ دکان بسته است.
 */
object ReceiptFlag {

  private const val PREFS = "tohid-receipt"
  private const val CONFIG = "/shop/receipt-profile/config"

  /** هر چند وقت یک بار «روشن است؟» دوباره پرسیده شود */
  const val CHECK_MS = 10 * 60_000L

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  internal fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  internal fun key(context: Context) = "v2:" + LedgerOwner.currentShop(context).ifBlank { "-" }

  /** پیشوندِ کلیدِ پرچمِ `shop.debt_minor` (همان پاسخِ [CONFIG]، `debtMinor`؛ خوانندهٔ `DebtMinorFlag`) */
  internal const val DEBT_MINOR = "debt-minor:"

  fun enabled(context: Context): Boolean = prefs(context).getBoolean(key(context), false)

  /** اتصالِ سوکتِ زنده (`hello`): پرچم را بپرس (نه زودتر از [CHECK_MS])؛ روشن ⇒ سربرگ هم تازه شود */
  fun kick(context: Context, now: Long = System.currentTimeMillis()) {
    val app = context.applicationContext
    val at = "${key(app)}:at"
    if (now - prefs(app).getLong(at, 0L) < CHECK_MS) {
      if (enabled(app)) scope.launch { ReceiptProfileStore.refresh(app) }
      return
    }
    prefs(app).edit().putLong(at, now).apply()
    scope.launch {
      //  همان پاسخ پرچمِ `shop.debt_minor` را هم دارد (`debtMinor`)؛ سرورِ پیشین آن را ندارد ⇒ خاموش
      val (on, debtMinor) = runCatching {
        val body = Backend.api(app).get(CONFIG)
        (body["enabled"]?.jsonPrimitive?.booleanOrNull == true) to ((body["debtMinor"] as? JsonPrimitive)?.booleanOrNull == true)
      }.getOrNull() ?: return@launch
      prefs(app).edit().putBoolean(key(app), on).putBoolean(DEBT_MINOR + key(app), debtMinor).apply()
      if (on) ReceiptProfileStore.refresh(app)
    }
  }
}
