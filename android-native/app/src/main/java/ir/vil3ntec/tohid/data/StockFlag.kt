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
 *  پرچمِ موتورِ تازهٔ موجودی و بها — `shop.stock_v2` در `live_config` ِ سرور، **پیش‌فرض خاموش**
 *  (RULES، «⛔ استثنا»). مقدار به حسابِ دکان بسته است و در [StockLedger.serverFlag] می‌نشیند.
 *
 *  خاموش ⇒ کارتِ «موجودی و بها» در تنظیمات پنهان است و حتی ردیفِ `stock_mode=on` (از دستگاهِ
 *  دیگر یا پیش از این پرچم) موتورِ امروز را عوض نمی‌کند. تنها کارِ شبکه پرسیدنِ خودِ پرچم است
 *  (`GET /shop/stock-ledger/config`)، با اتصالِ سوکتِ زنده و حداکثر هر [CHECK_MS] یک بار — همان
 *  الگوی `ReceiptFlag`. بی اینترنت آخرین مقدارِ همان دکان می‌ماند.
 */
object StockFlag {

  private const val PREFS = "tohid-stock"
  private const val CONFIG = "/shop/stock-ledger/config"

  const val CHECK_MS = 10 * 60_000L

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private fun key(context: Context) = "v2:" + LedgerOwner.currentShop(context).ifBlank { "-" }

  fun enabled(context: Context): Boolean = prefs(context).getBoolean(key(context), false)

  /** مقدارِ ذخیره‌شدهٔ دکانِ فعال را در [StockLedger.serverFlag] بگذار (شروع، عوض شدنِ دکان)؛ INV-10: `shop.count_absorb`
   *  (کلیدِ `countAbsorb` ِ همان پاسخ؛ نیامده ⇒ خاموش) ⇒ [CountAbsorbWiring.serverFlag]؛ PRD-08: `shop.qty_exact` (کلیدِ
   *  `qtyExact`؛ نیامده ⇒ خاموش) ⇒ [QtyExactWiring.serverFlag]؛ POS-37: `shop.sale_tax` (کلیدِ `saleTax`) ⇒ [SaleTaxWiring.serverFlag] */
  fun load(context: Context): Boolean = enabled(context).also {
    StockLedger.serverFlag = it
    CountAbsorbWiring.serverFlag = prefs(context).getBoolean("${key(context)}:absorb", false)
    QtyExactWiring.serverFlag = prefs(context).getBoolean("${key(context)}:qty", false)
    SaleTaxWiring.serverFlag = prefs(context).getBoolean("${key(context)}:tax", false) // POS-37
  }

  /** اتصالِ سوکتِ زنده (`hello`): پرچم را بپرس (نه زودتر از [CHECK_MS]) */
  fun kick(context: Context, now: Long = System.currentTimeMillis()) {
    val app = context.applicationContext
    load(app)
    val at = "${key(app)}:at"
    if (now - prefs(app).getLong(at, 0L) < CHECK_MS) return
    prefs(app).edit().putLong(at, now).apply()
    scope.launch {
      val body = runCatching { Backend.api(app).get(CONFIG) }.getOrNull() ?: return@launch
      val on = body["enabled"]?.jsonPrimitive?.booleanOrNull == true
      val absorb = body["countAbsorb"]?.jsonPrimitive?.booleanOrNull == true
      val qty = body["qtyExact"]?.jsonPrimitive?.booleanOrNull == true
      val tax = body["saleTax"]?.jsonPrimitive?.booleanOrNull == true // POS-37: `shop.sale_tax`
      prefs(app).edit().putBoolean(key(app), on).putBoolean("${key(app)}:absorb", absorb).putBoolean("${key(app)}:qty", qty)
        .putBoolean("${key(app)}:tax", tax).apply()
      load(app)
    }
  }
}
