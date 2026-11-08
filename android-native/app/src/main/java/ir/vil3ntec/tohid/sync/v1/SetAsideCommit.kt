package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.core.sync.RejectedSettle
import ir.vil3ntec.tohid.data.SetAside
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonObject

/**
 *  نشاندنِ تصمیمِ «کنار بگذار»/«اصلاح و ثبت» روی دفترِ دستگاهِ سازنده — برای هر دو موتور (`SyncV1Engine.setAside`،
 *  `SyncV3Engine.setAside`)، **زیرِ قفلِ همان موتور** (فراخوان دارد). برای هر تصمیم:
 *   ۱) برنامه از دفترِ **همین لحظه** (`RejectedSettle.plan`) — تصمیمی که پیش‌تر نشسته (`ShopData.setAside`) برنامه ندارد؛
 *   ۲) opی از همان ردیف‌ها هنوز در صف است ⇒ این دور نه (اول برود؛ سرور همراهِ سندِ نگه‌داشته را به همان بسته می‌افزاید)؛
 *   ۳) opهای برگرداننده روی دفتر و سایه، و نشانِ «کنار گذاشته شد» در همان نوشتن (`SyncCommit.setAside`)؛ شکست در
 *      میانه ⇒ همان‌جا با دفترِ همین حالا تمام یا برگردانده می‌شود (همان `absorbRemote`).
 *  ⛔ هیچ op ای به صف نمی‌رود؛ cursor دست نمی‌خورد؛ تصمیمِ تکراری یا دیررس بی‌اثر است.
 */
internal object SetAsideCommit {

  /** @return چند سند کنار گذاشته شد */
  fun run(
    commit: SyncCommit,
    queuedRows: () -> Set<String>,
    decisions: List<ConflictInbox.Mine>,
    book: List<JsonObject>,
    read: () -> ShopData,
    write: (ShopData) -> Unit,
    now: Long,
  ): Int {
    if (commit.pending()) commit.recover(read())
    var n = 0
    for (m in decisions) {
      val cur = read()
      val plan = (RejectedSettle.plan(m, book, LedgerDiff.toJson(cur), SetAside.doneIds(cur)) as? RejectedSettle.Result.Ok)?.plan ?: continue
      if (queuedRows().any { it in plan.touched }) continue
      val undo = plan.undo.mapNotNull(SyncOp::fromJson)
      try {
        commit.setAside(undo, cur, { SetAside.record(it, plan, now) }, write)
      } catch (e: Throwable) {
        runCatching { commit.recover(read()) }
        throw e
      }
      n++
    }
    return n
  }
}
