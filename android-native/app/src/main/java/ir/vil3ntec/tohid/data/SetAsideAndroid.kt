package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.sync.RejectedSettle
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.ConflictInboxRepository
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.sync.v3.SyncV3Runner

/**
 *  سوی سکوی «سندِ کنارگذاشته» (`SetAside` منطقِ خالص است): کارگرِ همگام‌سازی، شبکه، ساعت و قفلِ موتور.
 *  گوشی و کامپیوتر همین را دارند (کامپیوتر همان کدِ اندروید است)؛ آیفون همان shim ِ `Context` ِ پرونده‌های دیگر.
 */
object SetAsideSync {
  /** کمترین فاصلهٔ دو پرسشِ `/mine` از کارگرِ همگام‌سازی (جز وقتی [due] است) */
  const val MIN_MS = 5 * 60_000L

  @Volatile private var lastAt = 0L

  /** صفحهٔ تاریخچه تصمیمی دید که هنوز این‌جا ننشسته ⇒ دورِ بعدِ کارگر بی صبر می‌پرسد */
  @Volatile var due = false

  /**
   *  پس از هر دورِ همگام‌سازی (`SyncV1Worker`): ردِ بازی هست ⇒ `/mine` (هر [MIN_MS] یک بار، یا همان دم با [due]) ⇒
   *  هر تصمیمِ «کنار گذاشته/اصلاح‌شده» که مالِ همین دستگاه است زیرِ قفلِ موتور می‌نشیند (`SyncV1Engine.setAside`،
   *  `SyncV3Runner.setAside` ⇒ `sync/v1/SetAsideCommit`).
   *  @return چند سند کنار گذاشته شد
   */
  suspend fun afterSync(context: Context, store: ShopStore): Int {
    val app = context.applicationContext
    if (store.training || !TrainingMode.networkAllowed() || !runCatching { Backend.isReady(app) }.getOrDefault(false)) return 0
    val v3 = SyncChoice.isV3(app)
    val book = runCatching {
      if (v3) SetAside.v3Book(SyncV3Runner.of(app).rejected()) else SetAside.v1Book(SyncV1Engine.of(app).droppedOps())
    }.getOrDefault(emptyList())
    //  DATA-SAFETY §۷: نشانِ بیرون از دفتر (`SetAsideMarks`) هم «بسته» است — دفتری که `setAside` را از دست داد هر ۵ دقیقه نمی‌پرسد
    val marked = runCatching { if (v3) SyncV3Runner.of(app).settledOps() else ir.vil3ntec.tohid.sync.v1.OpQueue(app, SyncV1Engine.of(app).currentKey()).settledMarks().read().ops }.getOrDefault(emptySet())
    if (book.isEmpty() || !SetAside.openRejected(book, store.ledgerNow(), marked)) return 0
    val t = System.currentTimeMillis()
    if (!due && t - lastAt < MIN_MS) return 0
    lastAt = t
    due = false
    val mine = when (val r = ConflictInboxRepository(Backend.api(app)).mine()) {
      is ApiResult.Success -> r.value
      is ApiResult.Failure -> return 0
    }
    val decisions = mine.filter(RejectedSettle::actionable).map { m -> m.settle?.let { m.copy(settle = SetAside.legacyBase(it)) } ?: m }
    if (decisions.isEmpty()) return 0
    if (v3) return SyncV3Runner.of(app).setAside(store, decisions, book)
    return SyncV1Engine.of(app).setAside(decisions, book, read = { store.ledgerNow() }, write = { next -> store.applyFromServerNow(next) })
  }
}
