package ir.vil3ntec.tohid.ui.v3.shell

import android.content.Context
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.EventsRepository
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.SubscriptionPulse

/**
 *  خبرهای دکان (`events.feed`) — یک بار برای زنگِ خانه و مرکزِ هشدارها، همان رفتارِ زنگِ
 *  قدیم: فقط با حسابِ وارد، خبرِ خودِ من نه، و **شکستِ بی‌صدا** (نبودنِ اینترنت فقط
 *  `OFFLINE` می‌شود و فهرستِ پیشین می‌ماند). «دیده‌شده» همان معنای `Watchman.lastNewsAt`
 *  است — زمانِ تازه‌ترین خبری که کاربر دیده — روی همین دستگاه؛ سرور عوض نمی‌شود.
 */
object ShopFeed {
  enum class State { IDLE, LOADING, OK, OFFLINE }

  private const val PREFS = "tohid-shell"
  private const val SEEN = "news_seen_at"
  /** همان سقفِ زنگِ قدیم */
  private const val KEEP = 12

  val events = mutableStateOf<List<EventsRepository.Event>>(emptyList())
  val state = mutableStateOf(State.IDLE)
  val seenAt = mutableLongStateOf(-1L)
  /** شناسهٔ حسابِ همین دستگاه — خبرِ خودم برای خودم خبر نیست */
  val me = mutableStateOf("")

  fun load(context: Context) {
    if (seenAt.longValue >= 0) return
    seenAt.longValue = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SEEN, 0L) }.getOrDefault(0L)
    me.value = runCatching { SyncStore(context).accountId }.getOrDefault("")
  }

  suspend fun refresh(context: Context) {
    load(context)
    if (!Backend.isReady(context)) { state.value = State.OFFLINE; return }
    state.value = State.LOADING
    //  حالِ اشتراک از زبانِ سرور — کش‌شده؛ تازه بود از سرور نمی‌پرسد (همان کارِ زنگِ قدیم)
    runCatching { SubscriptionPulse.refresh(context) }
    Backend.events(context).feed()
      .onSuccess { feed ->
        events.value = feed.events.filter { it.userId != me.value }.take(KEEP)
        state.value = State.OK
      }
      .onFailure { state.value = State.OFFLINE }
  }

  /** فهرستِ خبر دیده شد ⇐ نقطهٔ زنگ تا خبرِ تازه‌تر خاموش */
  fun markSeen(context: Context) {
    val newest = events.value.maxOfOrNull { it.at } ?: return
    if (newest <= seenAt.longValue) return
    seenAt.longValue = newest
    runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(SEEN, newest).apply() }
  }

  val unseen: Boolean get() = NewsModel.hasUnseen(events.value, me.value, seenAt.longValue.coerceAtLeast(0L))
}
