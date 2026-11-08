package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.core.sync.RejectedSettle
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.ConflictInboxRepository
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.sync.v3.SyncV3Runner
import ir.vil3ntec.tohid.sync.v3.V3Convert
import ir.vil3ntec.tohid.sync.v3.V3Entry
import ir.vil3ntec.tohid.sync.v3.V3Schema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  دستگاهِ سازنده پس از «کنار بگذار» یا «اصلاح و ثبت» ِ صاحب/مدیر با سرور هم‌سو می‌شود (برنامهٔ خالص در
 *  `core/sync/RejectedSettle`؛ ژورنالِ `SyncCommit.setAside`). سندِ ردشده از مجموعه‌های همگام بیرون می‌آید و **کامل**
 *  در `ShopData.setAside` می‌ماند — دیدنی در تاریخچه («سرور نپذیرفت — کنار گذاشته شد: …»)، بیرون از موجودی، قرض،
 *  دخل، گزارش و هر فرستادنِ دوباره، و هرگز پاک. با «اصلاح» فاکتورِ تازهٔ صاحب از همگام‌سازیِ عادی می‌رسد و سندِ کهنه
 *  دیگر شمرده نمی‌شود (هر ترتیبی: اول فاکتورِ تازه یا اول کنار گذاشتن ⇒ همان دفتر).
 *
 *  ⛔ «دوباره ثبت» همان #1003 است (ردیف‌ها روی سرور نشستند) — این‌جا هیچ کاری ندارد.
 *  ⛔ پس از دورِ همگام‌سازی ([afterSync]) و فقط وقتی ردِ «بازِ» این دستگاه هست: دستگاهی که چیزی رد نشده هیچ نمی‌پرسد.
 */
object SetAside {
  /** کمترین فاصلهٔ دو پرسشِ `/mine` از کارگرِ همگام‌سازی (جز وقتی [due] است) */
  const val MIN_MS = 5 * 60_000L

  @Volatile private var lastAt = 0L

  /** صفحهٔ تاریخچه تصمیمی دید که هنوز این‌جا ننشسته ⇒ دورِ بعدِ کارگر بی صبر می‌پرسد */
  @Volatile var due = false

  // ---------- خالص ----------

  /** نشانِ «کنار گذاشته شد» با همهٔ برنامه — دستِ آخرِ `SyncCommit.setAside` (در همان نوشتن) */
  fun record(d: ShopData, plan: RejectedSettle.Plan, now: Long): ShopData {
    if (d.setAside.any { it.id == plan.id }) return d
    val doc = SetAsideDoc(
      id = plan.id, status = plan.status, note = plan.note, reason = plan.reason, fixedDoc = plan.fixedDoc,
      table = plan.table, rowId = plan.rowId, saleId = plan.saleId, resolvedAt = plan.resolvedAt, settledAt = now,
      ops = plan.ops, rows = plan.rows, undo = plan.undo, kept = plan.kept,
    )
    return d.copy(setAside = d.setAside + doc)
  }

  /** opهای ردشده‌ای که دیگر «ردِ باز» نیستند (دفترِ کنار دست نمی‌خورد) */
  fun settledOps(d: ShopData): Set<String> = RejectedSettle.settledOps(d.setAside.map { it.ops })

  /** شناسهٔ ردیف‌های صندوقی که روی همین دفتر کنار گذاشته شدند */
  fun doneIds(d: ShopData): Set<String> = d.setAside.mapTo(HashSet()) { it.id }

  /**
   *  مقدارِ امروزِ سرور به نام و واحدِ دفتر: بستهٔ v1 همان است؛ بستهٔ v3 (`…Minor`/`…Milli`) از
   *  `V3Convert.dataToLegacy` ِ همان جدول.
   */
  fun legacyBase(s: ConflictInbox.Settle): ConflictInbox.Settle {
    if (s.path != "v3") return s
    val base = s.base.mapValues { (k, row) ->
      val spec = V3Schema.specOf(k.substringBefore('/')) ?: return@mapValues row
      row?.let { r -> plainOf(V3Convert.dataToLegacy(spec, RejectedSettle.toJson(r) as? JsonObject ?: return@let r)) }
    }
    return s.copy(base = base)
  }

  private fun plainOf(o: JsonObject): Map<*, *>? = ir.vil3ntec.tohid.data.repo.ApprovalRequestsRepository.plain(o)

  /** دفترِ کنارِ صفِ v1: `{op, reason, at}` ⇒ op */
  fun v1Book(book: List<JsonObject>): List<JsonObject> = book.mapNotNull { it["op"] as? JsonObject }

  /**
   *  ردشده‌های صفِ v3 ⇒ opِ شکلِ دفتر: v2 همان op ِ خودش؛ v3 هر op ِ درونی با شناسهٔ خودش (همان شناسه‌های بستهٔ
   *  سرور) — درج، ویرایش، شمارنده (`delta`)، لغو (وضعیت و دلیل) و حذف.
   */
  fun v3Book(entries: List<V3Entry>): List<JsonObject> = entries.flatMap { e ->
    if (e.fmt != V3Entry.V3) listOfNotNull(e.op) else e.ops.mapNotNull(::legacyOf)
  }

  private fun s(o: JsonObject, k: String): String = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

  internal fun legacyOf(op: JsonObject): JsonObject? {
    val table = s(op, "table")
    val spec = V3Schema.specOf(table) ?: return null
    val kind = s(op, "kind")
    val f = op["fields"] as? JsonObject ?: JsonObject(emptyMap())
    fun one(k: String, v: JsonElement): Pair<String, JsonElement> =
      V3Convert.dataToLegacy(spec, JsonObject(mapOf(k to v))).entries.first().toPair()
    val (type, fields) = when (kind) {
      "insert" -> RejectedSettle.INSERT to V3Convert.dataToLegacy(spec, f)
      "update" -> RejectedSettle.UPDATE to JsonObject(f.entries.associate { (k, v) ->
        val inc = (v as? JsonObject)?.get("\$inc")
        if (inc != null) one(k, inc).let { (lk, lv) -> lk to buildJsonObject { put("\$inc", lv) } } else one(k, v)
      })
      "delta" -> RejectedSettle.UPDATE to JsonObject(f.entries.associate { (k, v) -> one(k, v).let { (lk, lv) -> lk to buildJsonObject { put("\$inc", lv) } } })
      "cancel" -> RejectedSettle.UPDATE to buildJsonObject {
        put("status", JsonPrimitive("cancelled"))
        put("cancelReason", op["reason"] ?: JsonPrimitive(""))
      }
      "delete" -> RejectedSettle.DELETE to null
      else -> return null
    }
    return buildJsonObject {
      put("op_id", op["op_id"] ?: JsonPrimitive(""))
      put("table", JsonPrimitive(table))
      put("row_id", op["row_id"] ?: JsonPrimitive(""))
      put("type", JsonPrimitive(type))
      if (fields != null) put("fields", fields)
    }
  }

  /** آیا هنوز opِ ردشدهٔ «بازی» روی این دستگاه هست */
  fun openRejected(book: List<JsonObject>, d: ShopData): Boolean {
    val done = settledOps(d)
    return book.any { o -> (o["op_id"] as? JsonPrimitive)?.content?.let { it !in done } == true }
  }

  // ---------- سکو ----------

  /**
   *  پس از هر دورِ همگام‌سازی (`SyncV1Worker`): ردِ بازی هست ⇒ `/mine` (هر [MIN_MS] یک بار، یا همان دم با [due]) ⇒
   *  هر تصمیمِ «کنار گذاشته/اصلاح‌شده» که مالِ همین دستگاه است زیرِ قفلِ موتور می‌نشیند.
   *  @return چند سند کنار گذاشته شد
   */
  suspend fun afterSync(context: Context, store: ShopStore): Int {
    val app = context.applicationContext
    if (store.training || !TrainingMode.networkAllowed() || !runCatching { Backend.isReady(app) }.getOrDefault(false)) return 0
    val v3 = SyncChoice.isV3(app)
    val book = runCatching { if (v3) v3Book(SyncV3Runner.of(app).rejected()) else v1Book(SyncV1Engine.of(app).droppedOps()) }.getOrDefault(emptyList())
    if (book.isEmpty() || !openRejected(book, store.ledgerNow())) return 0
    val t = System.currentTimeMillis()
    if (!due && t - lastAt < MIN_MS) return 0
    lastAt = t
    due = false
    val mine = when (val r = ConflictInboxRepository(Backend.api(app)).mine()) {
      is ApiResult.Success -> r.value
      is ApiResult.Failure -> return 0
    }
    val decisions = mine.filter(RejectedSettle::actionable).map { m -> m.settle?.let { m.copy(settle = legacyBase(it)) } ?: m }
    if (decisions.isEmpty()) return 0
    if (v3) return SyncV3Runner.of(app).setAside(store, decisions, book)
    return SyncV1Engine.of(app).setAside(decisions, book, read = { store.ledgerNow() }, write = { next -> store.applyFromServerNow(next) })
  }
}
