package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.data.repo.ApprovalRequestsRepository
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.sync.v3.SyncV3Runner
import ir.vil3ntec.tohid.sync.v3.V3Entry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  opهایی که سرور از **همین دستگاه** نپذیرفت — همان دفترِ کنارِ صفِ v1 (`OpQueue.dropped`، «…-dropped.json») یا
 *  `rejected` ِ صفِ v3 — به شکلِ `ConflictInbox.Rejected` برای نشانِ «سرور نپذیرفت: …» روی همان فاکتور.
 *  فقط خواندن؛ هیچ چیزی از دفترها پاک یا عوض نمی‌شود. خواندنِ ناموفق ⇒ فهرستِ خالی (نشانی نیست، نه خطا).
 */
object RejectedDocs {

  /** @param settled opهایی که صاحب «کنار گذاشت»/«اصلاح کرد» و روی همین دفتر نشستند (`SetAside.settledOps`) — دیگر «ردِ باز» نیستند */
  fun local(context: Context, settled: Set<String> = emptySet()): List<ConflictInbox.Rejected> = runCatching {
    if (SyncChoice.isV3(context)) v3(SyncV3Runner.of(context).rejected()) else v1(OpQueue(context, SyncV1Engine.of(context).currentKey()).dropped())
  }.getOrDefault(emptyList()).filter { it.opId !in settled }

  private fun str(o: JsonObject?, k: String): String = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
  private fun long(o: JsonObject?, k: String): Long = (o?.get(k) as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

  private fun one(op: JsonObject?, reason: String, at: Long): ConflictInbox.Rejected? {
    val table = str(op, "table").ifEmpty { str(op, "table_name") }
    val rowId = str(op, "row_id")
    if (op == null || table.isEmpty() || rowId.isEmpty()) return null
    val fields = ApprovalRequestsRepository.plain(op["fields"])
    return ConflictInbox.Rejected(str(op, "op_id"), table, rowId, ConflictInbox.saleOf(table, rowId, fields), reason.ifEmpty { "rejected" }, at)
  }

  /** دفترِ کنارِ صفِ v1: `{op, reason, at}` */
  internal fun v1(book: List<JsonObject>): List<ConflictInbox.Rejected> =
    book.mapNotNull { e -> one(e["op"] as? JsonObject, str(e, "reason"), long(e, "at")) }

  /** v3: هر ورودی چند op با دلیلِ هر کدام؛ صفِ v2 یک op */
  internal fun v3(entries: List<V3Entry>): List<ConflictInbox.Rejected> = entries.flatMap { e ->
    val ops = if (e.fmt == V3Entry.V3) e.ops else listOfNotNull(e.op)
    ops.mapIndexedNotNull { i, op -> one(op, e.reasons.getOrNull(i) ?: e.reasons.firstOrNull().orEmpty(), e.rejectedAt) }
  }
}
