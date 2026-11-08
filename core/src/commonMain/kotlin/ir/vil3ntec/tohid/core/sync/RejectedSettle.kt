package ir.vil3ntec.tohid.core.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull

/**
 *  هم‌سو شدنِ دستگاهِ سازنده پس از «کنار بگذار» یا «اصلاح و ثبت» ِ صندوقِ سندهای ردشده (پس از #1003).
 *
 *  ── اشکال ──────────────────────────────────────────────────────────────
 *  سندی که سرور نپذیرفت روی دستگاهِ سازنده همان‌طور در دفتر می‌ماند (فروش، قلم، حرکتِ انبار، قرض، سابقه). «دوباره ثبت»
 *  هم‌سو می‌کرد (همان ردیف‌ها روی سرور نشستند)، ولی پس از «کنار بگذار» یا «اصلاح و ثبت» موجودی، بدهی و جمعِ روزِ آن دستگاه
 *  برای همیشه با سرور فرق داشت — و با «اصلاح» فاکتورِ تازهٔ صاحب هم می‌رسید، پس همان فروش دو بار شمرده می‌شد.
 *
 *  ── حالا ────────────────────────────────────────────────────────────────
 *  [plan] از opهای ردشدهٔ **همین دستگاه** (دفترِ کنارِ صف) که شناسه‌شان در بستهٔ سرور است ([ConflictInbox.Settle.ops])
 *  opهای برگرداننده می‌سازد — به شکلِ opِ رسیده (`LedgerDiff.applyRemote`) که روی **دفتر و سایه هر دو** می‌نشیند، پس هیچ
 *  op ای به سرور نمی‌رود و هیچ چیزی دو بار کم یا زیاد نمی‌شود:
 *   • ردیفِ درج‌شدهٔ همان سند ⇒ از مجموعه‌های همگام بیرون (و **کامل** در [Plan.rows] برای سابقه — هرگز پاک نمی‌شود)؛
 *   • شمارندهٔ `$inc` (returnedQty، debtSettled، paidAmount…) ⇒ همان دلتا با علامتِ وارونه؛
 *   • فیلدِ ساده‌ای که op عوض کرد (وضعیتِ لغو) ⇒ مقدارِ امروزِ سرور ([ConflictInbox.Settle.base])، فقط اگر از آن پس
 *     روی همین دستگاه عوض نشده؛ ردیفِ حذف‌شده ⇒ ردیفِ سرور.
 *  آنچه برگرداندنی نیست در [Plan.kept] می‌ماند (دیدنی، نه بی‌صدا).
 *
 *  ⛔ هیچ داوری این‌جا نیست: فقط وقتی سرور گفت «کنار گذاشته» یا «اصلاح شد» ([actionable]). «دوباره ثبت» همان #1003 است
 *     (ردیف‌ها روی سرور نشستند و دستگاه opهای `rs_…` ِ خودش را pull نمی‌کند).
 *  ⛔ یک بار: ردیفِ صندوقی که در [done] است دوباره برنامه ندارد؛ برنامه و نشانِ «کنار گذاشته شد» در **یک** نوشتنِ دفتر
 *     می‌نشینند (ژورنالِ `SyncCommit`)، پس بسته شدنِ برنامه در میانه هرگز نیمه یا دو بار نمی‌گذارد.
 *  ⛔ بسته‌ای که سرور کامل ندارد (`truncated`) یا opِ غیرِ درجی که این‌جا پیدا نیست ⇒ هیچ برنامه‌ای (برگرداندنِ نیمه بدتر
 *     از نکردن است): [Skip] دلیل را می‌گوید.
 *  ورودی‌ها به شکلِ v1 ِ دفترند: op = `{op_id, table, row_id, type, fields}`، دفتر = `LedgerDiff.toJson`.
 */
object RejectedSettle {

  const val INSERT = "insert"
  const val UPDATE = "update"
  const val DELETE = "delete"

  /** نشانهٔ opهای برگرداننده (فقط سابقه؛ هرگز به سرور نمی‌روند) */
  const val UNDO_PREFIX = "sx_"

  /** فقط تصمیمی که سند را **بیرون** از دفترِ سرور گذاشت */
  fun actionable(m: ConflictInbox.Mine): Boolean = m.status == ConflictInbox.DISMISSED || m.status == ConflictInbox.FIXED

  data class Plan(
    val id: String,
    val status: String,
    val note: String,
    val reason: String,
    val fixedDoc: String,
    val table: String,
    val rowId: String,
    val saleId: String,
    val resolvedAt: Long,
    /** opهای ردشدهٔ همین دستگاه که مالِ همین سندند (کامل، برای سابقه) */
    val ops: List<JsonObject>,
    /** opهای برگرداننده به شکلِ opِ رسیده */
    val undo: List<JsonObject>,
    /** ردیف‌هایی که از دفتر بیرون می‌روند: نامِ مجموعه ⇒ ردیف‌های کامل */
    val rows: JsonObject,
    /** `"<جدول>/<شناسه>.<فیلد>"` که برگردانده نشد (سرور مقدار نداشت، یا از آن پس روی همین دستگاه عوض شد) */
    val kept: List<String>,
  ) {
    /** همهٔ ردیف‌هایی که برنامه به آن‌ها دست می‌زند (`"<جدول>/<شناسه>"`) */
    val touched: Set<String> get() = undo.mapTo(LinkedHashSet()) { str(it, "table") + "/" + str(it, "row_id") }
  }

  /** چرا برنامه‌ای نیست (برای سنجه و «حالِ همگام‌سازی») */
  enum class Skip { NOT_ACTIONABLE, DONE, NO_BUNDLE, TRUNCATED, NOT_HERE, MISSING_OPS }

  sealed interface Result {
    data class Ok(val plan: Plan) : Result
    data class None(val why: Skip) : Result
  }

  /**
   *  @param m ردیفِ `/mine` (با `settle`)
   *  @param book opهای ردشدهٔ همین دستگاه (هر دو صف؛ تکراری بی‌اثر)
   *  @param ledger دفترِ همین حالا (`LedgerDiff.toJson`)
   *  @param done شناسهٔ ردیف‌های صندوقی که پیش‌تر روی همین دفتر کنار گذاشته شدند
   */
  fun plan(m: ConflictInbox.Mine, book: List<JsonObject>, ledger: JsonObject, done: Set<String>): Result {
    if (!actionable(m)) return Result.None(Skip.NOT_ACTIONABLE)
    if (m.id in done) return Result.None(Skip.DONE)
    val s = m.settle ?: return Result.None(Skip.NO_BUNDLE)
    if (s.truncated) return Result.None(Skip.TRUNCATED)
    val bundle = s.ops.ifEmpty { return Result.None(Skip.NO_BUNDLE) }
    val mine = LinkedHashMap<String, JsonObject>()
    for (op in book) {
      val id = str(op, "op_id")
      if (id.isNotEmpty() && id !in mine) mine[id] = op
    }
    //  ⛔ فقط دستگاهِ سازنده: هیچ op ای از این بسته این‌جا نیست ⇒ دستگاهِ دیگرِ همان کاربر
    val local = bundle.mapNotNull { mine[it.id] }
    if (local.isEmpty()) return Result.None(Skip.NOT_HERE)
    //  opِ درجی که از دفترِ کنار بیرون رفته (سقفِ دفترِ کنار) هنوز با جایش برگرداندنی است؛ هر گونهٔ دیگر نه
    val missing = bundle.filter { it.id !in mine }
    if (missing.any { kindOf(it.type) != INSERT || it.table.isEmpty() || it.rowId.isEmpty() }) return Result.None(Skip.MISSING_OPS)

    val tables = rowsByTable(ledger)
    fun rowOf(table: String, rowId: String): JsonObject? = tables[table]?.get(rowId)

    val removed = LinkedHashSet<Pair<String, String>>()
    for (op in local) if (str(op, "type") == INSERT) removed += str(op, "table") to str(op, "row_id")
    for (o in missing) removed += o.table to o.rowId

    val inc = LinkedHashMap<Pair<String, String>, LinkedHashMap<String, Double>>()
    val set = LinkedHashMap<Pair<String, String>, LinkedHashMap<String, JsonElement>>()
    val revive = LinkedHashMap<Pair<String, String>, JsonObject>()
    val kept = ArrayList<String>()
    for (op in local.asReversed()) {
      val key = str(op, "table") to str(op, "row_id")
      if (key in removed) continue
      val where = key.first + "/" + key.second
      val base = s.base[where]
      when (str(op, "type")) {
        UPDATE -> for ((f, v) in (op["fields"] as? JsonObject).orEmpty()) {
          val delta = ((v as? JsonObject)?.get("\$inc") as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
          if (delta != null) {
            val acc = inc.getOrPut(key) { LinkedHashMap() }
            acc[f] = (acc[f] ?: 0.0) - delta
            continue
          }
          if (base == null || !base.containsKey(f)) { kept += "$where.$f"; continue }
          val server = base[f]
          //  از آن پس روی همین دستگاه عوض شده (ویرایشِ تازهٔ کاربر) ⇒ دست نمی‌خورد
          if (!same(rowOf(key.first, key.second)?.get(f), v)) { kept += "$where.$f"; continue }
          set.getOrPut(key) { LinkedHashMap() }[f] = toJson(server)
        }
        DELETE -> {
          if (rowOf(key.first, key.second) != null) continue
          val row = base?.let { toJson(it) as? JsonObject }
          if (row == null) kept += where else revive[key] = row
        }
      }
    }

    val undo = ArrayList<JsonObject>()
    val tag = m.id.filter { it.isLetterOrDigit() }.takeLast(24)
    fun op(table: String, rowId: String, type: String, fields: JsonObject?) = buildJsonObject {
      put("op_id", JsonPrimitive("$UNDO_PREFIX${tag}_${undo.size}"))
      put("ts", JsonPrimitive(0))
      put("table", JsonPrimitive(table))
      put("row_id", JsonPrimitive(rowId))
      put("type", JsonPrimitive(type))
      if (fields != null) put("fields", fields)
    }
    for ((key, fields) in revive) undo += op(key.first, key.second, INSERT, fields)
    for (key in LinkedHashSet(inc.keys + set.keys)) {
      if (rowOf(key.first, key.second) == null) continue
      val fields = LinkedHashMap<String, JsonElement>()
      set[key]?.let { fields.putAll(it) }
      inc[key]?.forEach { (f, d) -> if (d != 0.0) fields[f] = buildJsonObject { put("\$inc", number(d)) } }
      if (fields.isNotEmpty()) undo += op(key.first, key.second, UPDATE, JsonObject(fields))
    }
    val rows = LinkedHashMap<String, MutableList<JsonElement>>()
    for ((table, rowId) in removed) {
      val row = rowOf(table, rowId) ?: continue
      //  ردیفی که سرور هم دارد (همان شناسه از راهِ دیگر نشست) بیرون نمی‌رود — فقط ردیفِ تنهای همین دستگاه
      if (s.base["$table/$rowId"] != null) { kept += "$table/$rowId"; continue }
      rows.getOrPut(table) { ArrayList() } += row
      undo += op(table, rowId, DELETE, null)
    }

    val head = local.firstOrNull { str(it, "table") == m.table && str(it, "row_id") == m.rowId } ?: local.first()
    return Result.Ok(
      Plan(
        id = m.id, status = m.status, note = m.note, reason = m.reason, fixedDoc = m.fixedDoc,
        table = m.table.ifEmpty { str(head, "table") }, rowId = m.rowId.ifEmpty { str(head, "row_id") },
        saleId = saleOf(m.table, m.rowId, head, tables),
        resolvedAt = m.resolvedAt ?: 0L, ops = local, undo = undo,
        rows = JsonObject(rows.mapValues { JsonArray(it.value) }), kept = kept,
      ),
    )
  }

  /** v3 ِ بسته: `kind` (insert/update/cancel/delta/delete) ⇒ گونهٔ v1 */
  fun kindOf(type: String): String = when (type) {
    INSERT -> INSERT
    DELETE -> DELETE
    else -> UPDATE
  }

  /** opهای ردشده‌ای که برنامه‌ای کنارشان گذاشت — دفترِ کنار دست نمی‌خورد، فقط این‌ها دیگر «ردِ باز» نیستند */
  fun settledOps(done: List<List<JsonObject>>): Set<String> = done.flatMapTo(HashSet()) { ops -> ops.map { str(it, "op_id") } }

  // ---------- کمک ----------

  private fun str(o: JsonObject?, k: String): String = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

  private fun rowsByTable(tree: JsonObject): Map<String, Map<String, JsonObject>> =
    tree.mapNotNull { (k, v) ->
      val arr = v as? JsonArray ?: return@mapNotNull null
      k to arr.mapNotNull { it as? JsonObject }.associateBy { (it["id"] as? JsonPrimitive)?.content.orEmpty() }
    }.toMap()

  private fun saleOf(table: String, rowId: String, head: JsonObject, tables: Map<String, Map<String, JsonObject>>): String {
    val fields = head["fields"] as? JsonObject
    val t = table.ifEmpty { str(head, "table") }
    val r = rowId.ifEmpty { str(head, "row_id") }
    return when (t) {
      "sales" -> r
      "saleItems", "returns" -> str(fields, "saleId").ifEmpty { str(tables[t]?.get(r), "saleId") }
      "transactions" -> str(fields, "refId")
      else -> ""
    }
  }

  /** برابریِ مقدارِ دفتر و op: عدد با عدد (۱ و ۱.۰ یکی)، بقیه همان */
  private fun same(a: JsonElement?, b: JsonElement?): Boolean {
    val x = a ?: JsonNull
    val y = b ?: JsonNull
    if (x is JsonPrimitive && y is JsonPrimitive && !x.isString && !y.isString && x !is JsonNull && y !is JsonNull) {
      val dx = x.doubleOrNull
      val dy = y.doubleOrNull
      if (dx != null && dy != null) return dx == dy
      return x.booleanOrNull != null && x.booleanOrNull == y.booleanOrNull
    }
    return x == y
  }

  /** عددِ بی اعشار بی «.0» (همان `trim` ِ `applyRemote`) */
  private fun number(d: Double): JsonPrimitive {
    val l = d.toLong()
    return if (l.toDouble() == d) JsonPrimitive(l) else JsonPrimitive(d)
  }

  /** درختِ سادهٔ `ApprovalRequestsRepository.plain` ⇒ JSON */
  fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Int -> JsonPrimitive(v)
    is Long -> JsonPrimitive(v)
    is Double -> number(v)
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.mapNotNull { (k, x) -> (k as? String)?.let { it to toJson(x) } }.toMap())
    is List<*> -> JsonArray(v.map { toJson(it) })
    else -> JsonPrimitive(v.toString())
  }
}
