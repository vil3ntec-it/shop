package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 *  پرچمِ ادغامِ سه‌طرفهٔ دفتر — `shop.ledger_merge` در `live_config` ِ سرور، **پیش‌فرض خاموش**
 *  (RULES: رفتارِ تازه در پول، قالبِ داده یا همگام‌سازی فقط پشتِ پرچم؛ PR #407، ذخیرهٔ کهنه).
 *
 *  خاموش ⇒ همان رفتارِ `main`: هر `ShopStore` فقط دفترِ حافظهٔ خودش را دارد، نوشتنِ همتا گرفته
 *  نمی‌شود، دیسک پیش از نوشتن سنجیده نمی‌شود و `save` دفترِ صفحه را کامل می‌نویسد (`LedgerFollow`).
 *  روشن ⇒ همتاها، سنجشِ دیسک و ادغامِ سه‌طرفه (`LedgerMerge`).
 *
 *  تنها کارِ شبکه پرسیدنِ خودِ پرچم است (`GET /shop/ledger-merge/config`)، با اتصالِ سوکتِ زنده
 *  و حداکثر هر [CHECK_MS] یک بار — همان الگوی `ReceiptFlag`. مقدار به دکان بسته است.
 */
object LedgerMergeFlag {

  private const val PREFS = "tohid-ledger-merge"
  private const val CONFIG = "/shop/ledger-merge/config"

  /** هر چند وقت یک بار «روشن است؟» دوباره پرسیده شود */
  const val CHECK_MS = 10 * 60_000L

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private fun key(context: Context) = "on:" + LedgerOwner.currentShop(context).ifBlank { "-" }

  /** هر خطا ⇒ خاموش (همان رفتارِ `main`) */
  fun enabled(context: Context): Boolean = runCatching { prefs(context).getBoolean(key(context), false) }.getOrDefault(false)

  /** مقدارِ پرسیده‌شده از سرور برای دکانِ همین حالا (و آزمون‌ها) */
  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(key(context), on).apply()
  }

  /** اتصالِ سوکتِ زنده (`hello`): پرچم را بپرس (نه زودتر از [CHECK_MS]) */
  fun kick(context: Context, now: Long = System.currentTimeMillis()) {
    val app = context.applicationContext
    val at = "${key(app)}:at"
    if (now - prefs(app).getLong(at, 0L) < CHECK_MS) return
    prefs(app).edit().putLong(at, now).apply()
    scope.launch {
      val on = runCatching { Backend.api(app).get(CONFIG)["enabled"]?.jsonPrimitive?.booleanOrNull == true }.getOrNull()
        ?: return@launch
      set(app, on)
    }
  }
}
