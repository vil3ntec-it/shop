package ir.vil3ntec.tohid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.text.SynonymList
import ir.vil3ntec.tohid.core.text.SynonymTable
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.components.pos.CatalogSearch
import kotlinx.coroutines.delay

/**
 *  POS-04 (۴-۱ «ب») — فهرستِ مرکزیِ مترادف برای جست‌وجوی صندوق، روی همین دستگاه.
 *
 *  همان الگوی تعطیلات (`ShopDayChoice.keepHolidaysFresh`، LOC-04): `GET /api/search/synonyms` (بی‌توکن، بی شناسهٔ دستگاه)
 *  روزی یک بار در پوستهٔ برنامه و `Main.kt` ِ کامپیوتر؛ نسخه در پروندهٔ جدای `tohid-synonyms` می‌ماند — نسخهٔ محلیِ دادهٔ
 *  سرور، نه تنظیمِ دکان — و صندوق بی‌شبکه هم با همان می‌گردد. هنوز نگرفته یا پاسخِ خراب ⇒ فهرستِ تهی = جست‌وجوی پیشین.
 *  فهرست را صاحبِ پلتفرم در پنلِ مرکزی می‌نویسد (هیچ فرهنگِ ثابتی در برنامه نیست). خواندن و سنجش: `core/text/SynonymList`.
 */
object TillSynonyms {

  private const val PREFS = "tohid-synonyms"
  private const val TABLE = "table"
  private const val AT = "fetched_at"

  /** آخرین فهرستِ خوانده‌شده (نسخه و گروه‌ها) */
  var table by mutableStateOf(SynonymTable.EMPTY)
    private set

  /** همان فهرست، نرمال‌شده برای `CatalogSearch.search` — هر بار که [table] عوض شود از نو */
  var search by mutableStateOf(CatalogSearch.Synonyms.NONE)
    private set

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun use(t: SynonymTable) {
    table = t
    search = CatalogSearch.Synonyms.of(t.groups)
  }

  /** نسخهٔ ذخیره‌شده ⇐ حافظه؛ هر خرابی (متنِ بد، نوعِ دیگر زیرِ همان کلید) ⇒ فهرستِ تهی، هرگز پرتاب */
  fun load(context: Context) {
    use(runCatching { SynonymList.parse(prefs(context).getString(TABLE, null)) }.getOrDefault(SynonymTable.EMPTY))
  }

  /**
   *  اگر یک روز گذشته: گرفتن، سنجیدن، ذخیره و به‌کار بستن ⇒ `true`. پیش از وقت، بی‌شبکه یا پاسخِ بدشکل ⇒ `false` و
   *  نسخهٔ پیشین دست نمی‌خورد (همان ساعتِ بعد دوباره).
   */
  suspend fun refresh(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
    val p = runCatching { prefs(context) }.getOrNull() ?: return false
    if (!SynonymList.due(runCatching { p.getLong(AT, 0L) }.getOrDefault(0L), nowMs)) return false
    val body = runCatching { Backend.api(context).getPublic(ApiEndpoints.Search.SYNONYMS, anonymous = true) }.getOrNull()
      ?: return false
    val t = SynonymList.of(body) ?: return false
    p.edit().putString(TABLE, SynonymList.encode(t)).putLong(AT, nowMs).apply()
    use(t)
    return true
  }

  /** تا برنامه باز است: هر ساعت می‌سنجد و روزی یک بار می‌گیرد (`LaunchedEffect` ِ پوسته؛ با بستنِ صفحه می‌ایستد) */
  suspend fun keepFresh(context: Context) {
    while (true) {
      refresh(context)
      delay(SynonymList.CHECK_MS)
    }
  }
}
