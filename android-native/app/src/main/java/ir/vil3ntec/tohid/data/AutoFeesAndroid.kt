package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.sale.AutoFees
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 *  POS-36 — قاعده‌های هزینهٔ خودکار روی **همین دستگاه** (`tohid-policy` ⇐ `auto_fees`، کنارِ سیاست‌های دیگرِ
 *  صندوق)؛ فایلِ مرزیِ پلتفرم. پیش‌فرض **بی قاعده** = همان سبدِ امروز. خواندن یک بار و در حافظه، چون
 *  `CartStore.set` با هر اسکن صدایش می‌زند.
 */
object AutoFeesPref {

  @Serializable
  private data class Stored(val label: String = "", val amountMinor: Long = 0, val productIds: List<String> = emptyList())

  private const val PREFS = "tohid-policy"
  private const val KEY = "auto_fees"
  private val codec = ListSerializer(Stored.serializer())
  private val json = Json { ignoreUnknownKeys = true }

  @Volatile private var cache: List<AutoFees.Rule>? = null

  fun rules(context: Context): List<AutoFees.Rule> = cache ?: runCatching {
    val text = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    if (text.isNullOrBlank()) emptyList()
    else json.decodeFromString(codec, text).filter { it.label.isNotBlank() && it.amountMinor > 0 }
      .take(AutoFees.MAX_RULES).map { AutoFees.Rule(it.label, it.amountMinor, it.productIds) }
  }.getOrDefault(emptyList()).also { cache = it }

  fun set(context: Context, rules: List<AutoFees.Rule>) {
    val e = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
    if (rules.isEmpty()) e.remove(KEY) else e.putString(KEY, json.encodeToString(codec, rules.map { Stored(it.label, it.amountMinor, it.productIds) }))
    e.apply()
    cache = rules
  }
}
