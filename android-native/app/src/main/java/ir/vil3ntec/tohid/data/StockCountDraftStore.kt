package ir.vil3ntec.tohid.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 *  پیش‌نویسِ شمارشِ موجودی (INV-05/09/10) — فقط روی همین دستگاه، تا شمارشِ
 *  طولانی با بسته شدنِ برنامه گم نشود. چیزی از آن به دفتر نمی‌رود تا «ثبت» زده
 *  نشود؛ پس همگام هم نمی‌شود و در پشتیبان هم نیست.
 */
object StockCountDraftStore {
  private const val PREFS = "tohid-stock"
  private const val KEY = "count_draft_v1"
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context): StockActions.CountDraft? = runCatching {
    prefs(context).getString(KEY, null)?.let { json.decodeFromString(StockActions.CountDraft.serializer(), it) }
  }.getOrNull()

  fun save(context: Context, draft: StockActions.CountDraft?) {
    val e = prefs(context).edit()
    if (draft == null) e.remove(KEY) else e.putString(KEY, json.encodeToString(StockActions.CountDraft.serializer(), draft))
    e.apply()
  }
}
