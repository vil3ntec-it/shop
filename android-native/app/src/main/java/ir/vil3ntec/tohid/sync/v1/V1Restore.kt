package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.core.sync.RestoreReplay
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

/**
 *  §۹.۵ برای Sync v1 — «سرور از پشتیبان برگشت» (شرح سرِ `core/sync/RestoreReplay.kt`)،
 *  جدا از اندروید تا روی JVM سنجیده شود. `SyncV1Engine.runOnce` فقط ترتیبِ شبکه را دارد:
 *
 *      دوران (هر دور) ⇒ `check` ⇒ Push تا خالی ⇒ اگر «در انتظار» و صف خالی:
 *      Snapshot ⇒ `rebuild` ⇒ Pull (در انتظار ⇒ هیچ Pullی روی دفترِ کهنه)
 *
 *  ⛔ صف، سایه و cursor فقط از راه‌های F0-05: opِ دوباره همان op_id را دارد
 *     (`Queue.pushFront`) و دفتر + سایه + cursor با ژورنالِ `SyncCommit.rebuild`.
 *  ⛔ دوران و «در انتظار» با هم و همزمان (`State.commitRestore`): بسته شدن پس از آن ⇒ دورِ
 *     بعد کار را از همان‌جا ادامه می‌دهد؛ پیش از آن ⇒ دورِ بعد بازیابی را دوباره می‌بیند و
 *     `pushFront` opِ در صف را دوباره نمی‌آورد.
 */
class V1Restore internal constructor(
  private val state: State,
  private val queue: Queue,
  private val book: ReplayBook,
  private val commit: SyncCommit,
) {

  interface State {
    val epoch: String
    val cursor: Long
    val restorePending: Boolean
    /** دوران و «در انتظارِ ساختنِ دوباره» با هم، با `commit()` */
    fun commitRestore(epoch: String, pending: Boolean): Boolean
    /** فقط دوران (بی بازیابی) */
    fun adoptEpoch(epoch: String)
  }

  interface Queue {
    fun all(): List<SyncOp>
    fun pushFront(ops: List<SyncOp>): Int
  }

  /**
   *  دورانِ سرور (null ⇒ سرورِ کهنه یا خاموش ⇒ هیچ). بازیابی ⇒ opهای پذیرفته‌شدهٔ خودِ
   *  دستگاه بالای پایه جلوی صف و «در انتظار».
   *  @return بازیابی آغاز شد
   */
  fun check(server: RestoreReplay.Epoch?): Boolean {
    val plan = RestoreReplay.plan(state.epoch, state.cursor, server) { book.hasAbove(it) }
    if (!plan.restore) {
      if (plan.epoch != state.epoch) state.adoptEpoch(plan.epoch)
      return false
    }
    queue.pushFront(book.replayAbove(plan.base).mapNotNull { SyncOp.fromJson(it.body) })
    if (!state.commitRestore(plan.epoch, true)) throw IOException("دورانِ همگام‌سازی روی دیسک ننشست")
    return true
  }

  /** پذیرفته‌های یک دسته (applied/duplicate، با `server_seq`) — **پیش از** `ack`ِ صف */
  fun accepted(batch: List<SyncOp>, results: List<OpResult>) {
    val seqOf = results.filter { it.accepted && it.seq > 0 }.associate { it.opId to it.seq }
    if (seqOf.isEmpty()) return
    book.sent(batch.filter { it.opId in seqOf }.map { ReplayBook.Sent(it.opId, seqOf.getValue(it.opId), it.toJson()) })
  }

  /**
   *  ردِ opی که پیش‌تر پذیرفته شده بود (دوباره‌فرستاده پس از بازیابی) — «انجام‌شده»، نه
   *  ردشده‌ای که در دفترِ ردشده‌ها به کاربر نشان داده شود.
   */
  fun isReplay(opId: String): Boolean = book.wasSent(opId)

  /**
   *  پس از خالی شدنِ صف: دفتر = Snapshot + opهای همین حالا در صف؛ سایه و cursor با آن؛ بعد
   *  «در انتظار» برداشته می‌شود. زیرِ قفلِ `record()` صدا زده می‌شود.
   *  @return ساخته شد (Snapshot ِ ناقص ⇒ نه؛ دورِ بعد دوباره)
   */
  fun rebuild(snapshot: JsonObject, current: ShopData, write: (ShopData) -> Unit): Boolean {
    val cursor = (snapshot["cursor"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return false
    val server = LedgerDiff.fromSnapshot(snapshot, LedgerDiff.toJson(current))
    commit.rebuild(server, queue.all(), current, cursor, write)
    if (!state.commitRestore(state.epoch, false)) throw IOException("دورانِ همگام‌سازی روی دیسک ننشست")
    return true
  }
}
