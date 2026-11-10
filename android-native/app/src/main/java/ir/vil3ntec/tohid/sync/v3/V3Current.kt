package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  SYN-05/06 — دستگاه پس از push همان می‌شود که سرور دارد. پاسخِ push ِ سرور برای op ای که «همان» ننشست
 *  `current` دارد (`server/src/lib/sync-v3-apply.js`): ساعتِ کج یا «آخرین نوشتن» نبرد (مقدارِ سرور)، حذفِ کهنه
 *  (ردیف ماند: دادهٔ کامل)، ویرایشِ کهنه‌تر از حذف (`deleted`)، و `correctedAt` ِ سندِ ساعت‌کج که سرور می‌افزاید
 *  ولی Pull opِ خودِ دستگاه را برایش نمی‌آورد. «تکراری» ِ پاسخِ گم‌شده همان را از حالِ امروزِ ردیف می‌گیرد.
 *
 *  بی این: دستگاهِ ساعت‌کج با قیمتِ خودش می‌فروخت تا دستگاهِ دیگری همان فیلد را عوض کند؛ حذف‌کنندهٔ کهنه با Pull ِ
 *  ویرایشِ تازه‌تر ردیفِ نیمه (فقط همان فیلد) می‌ساخت؛ فاکتور و حرکتِ خودِ دستگاه بی تاریخِ تصحیح‌شده (نگهبانِ INV-10).
 *
 *  ⛔ ردیفِ هنوز در صف (تغییرِ تازه‌ترِ کاربر)، فهرستِ نامی و سندِ مالی (جز `correctedAt`؛ «ویرایشِ سند گم نمی‌شود»)
 *  دست نمی‌خورند؛ ردیفِ نبوده فقط با دادهٔ کاملِ پاسخِ حذف ساخته می‌شود؛ فیلدی که op ِ بعدیِ همان دسته فرستاد نه.
 *  همزادِ `currentFixes` ِ `license/sync-v3-engine.js`؛ بردار: `core/test-vectors/v3-current-v1.json` (`V3CurrentVectorsTest`).
 */
object V3Current {
  private val DONE = setOf("applied", "partial", "conflict", "duplicate")

  /** یک op ِ همان دسته و پاسخش؛ [fields] نامِ v3 (یا نامِ v1 در ورودیِ v2) */
  data class Item(
    val table: String,
    val rowId: String,
    val kind: String,
    val fields: JsonObject?,
    val res: JsonObject?,
    val pending: Boolean,
    val exists: Boolean,
  )

  private fun text(o: JsonObject?, k: String): String? = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content

  /** یک op ⇒ op ِ v1 یا null (دست نزن) */
  fun fix(item: Item): SyncOp? {
    val spec = V3Schema.specOf(item.table) ?: return null
    val res = item.res ?: return null
    val cur = res["current"] as? JsonObject ?: return null
    if (spec.v3only || spec.name in LedgerDiff.LIST_COLLECTIONS || item.pending || text(res, "status").orEmpty() !in DONE) return null
    val master = spec.policy == "master"
    val opId = "cur." + text(res, "op_id").orEmpty()
    if ((cur["deleted"] as? JsonPrimitive)?.let { p -> !p.isString && p.content == "true" } == true) {
      return if (item.exists && master) SyncOp(opId, 0L, spec.name, item.rowId, SyncOp.DELETE, null) else null
    }
    var data = cur["data"] as? JsonObject ?: JsonObject(emptyMap())
    if (!master) {
      val at = (data["correctedAt"] as? JsonPrimitive)?.takeIf { p -> !p.isString && p.content.toLongOrNull() != null }
      data = if (at != null) JsonObject(mapOf("correctedAt" to at)) else JsonObject(emptyMap())
    }
    val fields = V3Convert.dataToLegacy(spec, data)
    if (fields.isEmpty() || (!item.exists && (!master || item.kind != "delete"))) return null
    return SyncOp(opId, 0L, spec.name, item.rowId, if (item.exists) SyncOp.UPDATE else SyncOp.INSERT, fields)
  }

  /** همهٔ opهای یک دسته به ترتیب ⇒ اصلاح‌ها؛ فیلدی که op ِ بعدیِ همان ردیف فرستاد برداشته می‌شود */
  fun fixes(items: List<Item>): List<SyncOp> {
    val out = ArrayList<SyncOp>()
    val later = HashMap<String, MutableSet<String>>()
    for (item in items.asReversed()) {
      val seen = later.getOrPut("${item.table}/${item.rowId}") { HashSet() }
      var f = if ("*" in seen) null else fix(item)
      val sent = f?.fields
      if (f != null && sent != null) f = f.copy(fields = JsonObject(sent.filterKeys { k -> k !in seen }))
      if (f != null && (if (f.type == SyncOp.DELETE) seen.isNotEmpty() else f.fields.orEmptyObject().isEmpty())) f = null
      if (f != null) out.add(0, f)
      if (item.kind == "delete") {
        seen += "*"
      } else {
        V3Schema.specOf(item.table)?.let { spec -> seen += V3Convert.dataToLegacy(spec, item.fields ?: JsonObject(emptyMap())).keys }
      }
    }
    return out
  }

  private fun JsonObject?.orEmptyObject(): JsonObject = this ?: JsonObject(emptyMap())

  /**
   *  ورودی‌های یک دسته و پاسخِ سرور ⇒ اصلاح‌ها (`SyncV3Engine.settle`، زیرِ قفلِ ثبت، پیش از بیرون رفتن از صف).
   *  @param left «جدول/ردیف» ِ آنچه پس از همین دسته در صف ماند; @param ledger دفترِ همین حالا (JSON)
   */
  fun fixes(v3: Boolean, entries: List<V3Entry>, results: List<JsonObject?>, left: Set<String>, ledger: JsonObject): List<SyncOp> {
    val items = ArrayList<Item>()
    var at = 0
    for (e in entries) {
      val n = e.size
      val mine = results.subList(minOf(at, results.size), minOf(at + n, results.size))
      at += n
      if (mine.size < n || mine.any { it == null || text(it, "status").orEmpty() !in DONE }) continue
      val pairs: List<Pair<JsonObject, JsonObject>> = if (v3) {
        e.ops.zip(mine.map { it!! })
      } else {
        val op = e.op ?: continue
        (mine[0]!!["converted"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { r ->
          JsonObject(mapOf("table" to JsonPrimitive(e.table), "row_id" to JsonPrimitive(e.rowId), "kind" to (op["type"] ?: JsonPrimitive("")),
            "fields" to (op["fields"] ?: JsonObject(emptyMap())))) to r
        }
      }
      for ((o, r) in pairs) {
        val table = text(o, "table").orEmpty()
        val rowId = text(o, "row_id").orEmpty()
        items += Item(table, rowId, text(o, "kind").orEmpty(), o["fields"] as? JsonObject, r, "$table/$rowId" in left, hasRow(ledger, table, rowId))
      }
    }
    return fixes(items)
  }

  private fun hasRow(ledger: JsonObject, table: String, rowId: String): Boolean =
    (ledger[table] as? JsonArray)?.any { (it as? JsonObject)?.let { r -> text(r, "id") == rowId } == true } == true
}
