package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.i18n.StrConflictInbox
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.ConflictInboxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 *  «اصلاح و ثبت» ِ صندوقِ سندهای ردشده: سبدِ صندوق با قلم‌های همان فاکتور پر می‌شود و کلیدِ همان سبد به ردیفِ صندوق
 *  بسته می‌ماند ([begin]). فاکتور که با همان کلید ثبت شد (`CartStore.post`، گوشی و کامپیوتر هر دو) ردیف با شناسهٔ
 *  فاکتورِ تازه «اصلاح شد» می‌شود ([posted]) — نه پیش از آن: سبدِ رهاشده تعارض را نمی‌بندد.
 *
 *  ⛔ پیوند روی دیسکِ همین دستگاه است (بستنِ برنامه وسطِ اصلاح گمش نمی‌کند)؛ بستنِ ناموفق (بی شبکه) می‌ماند و با ثبتِ
 *     بعدیِ هر فاکتور یا بازکردنِ صندوقِ سندها دوباره امتحان می‌شود ([retry]). سرور «اصلاح» ِ تکراری را همان پاسخ می‌دهد.
 */
object ConflictFix {
  private const val PREFS = "tohid-conflict-fix"
  private const val DONE = "done:"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** آخرین ردیفی که بسته شد — صفحه پیام می‌دهد و فهرست را تازه می‌کند */
  private val _closed = MutableStateFlow<String?>(null)
  val closed: StateFlow<String?> = _closed.asStateFlow()

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** سبد با کلیدِ [cartKey] برای ردیفِ [conflictId] پر شد */
  fun begin(context: Context, cartKey: String, conflictId: String) {
    prefs(context).edit().putString(cartKey, conflictId).apply()
  }

  /** آیا این ردیف همین حالا در صندوق باز است */
  fun pending(context: Context, conflictId: String): Boolean = prefs(context).all.values.contains(conflictId)

  /** `CartStore.post`: فاکتورِ [saleId] با کلیدِ [cartKey] روی دیسک نشست */
  fun posted(context: Context, cartKey: String, saleId: String) {
    val p = prefs(context)
    val id = p.getString(cartKey, null) ?: return retry(context)
    //  کلیدِ سبد ⇒ «بستن با این فاکتور»؛ بستن که شد پاک می‌شود
    p.edit().remove(cartKey).putString(DONE + id, saleId).apply()
    retry(context)
  }

  /** بستن‌های مانده (بی شبکه، سرورِ موقتاً در دسترس نبود) */
  fun retry(context: Context) {
    val app = context.applicationContext
    val left = prefs(app).all.filterKeys { it.startsWith(DONE) }.mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(DONE) to it } }
    if (left.isEmpty() || !runCatching { Backend.isReady(app) }.getOrDefault(false)) return
    scope.launch {
      val repo = ConflictInboxRepository(Backend.api(app))
      for ((id, saleId) in left) {
        when (val r = repo.dismiss(id, StrConflictInbox.fixReason, saleId)) {
          is ApiResult.Success -> { prefs(app).edit().remove(DONE + id).apply(); _closed.value = id }
          //  بسته بود (صاحبِ دیگر کنار گذاشت، یا همین پیش‌تر) ⇒ دیگر کاری نیست؛ قطعِ گذرا ⇒ بارِ بعد
          is ApiResult.Failure -> if (!r.error.retryable) prefs(app).edit().remove(DONE + id).apply()
        }
      }
    }
  }
}
