package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.money.Qty
import ir.vil3ntec.tohid.core.sync.v3.Hlc
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.abs
import kotlin.math.floor

/**
 *  جدول‌ها و سیاستِ ادغامِ Sync v3 — قرینهٔ `server/src/lib/sync-v3-schema.js`
 *  (آزمون: `tables`ِ `core/test-vectors/sync-v3-convert-v1.json` که سرور می‌سازد).
 */
data class V3TableSpec(
  val name: String,
  val policy: String,
  val money: List<String> = emptyList(),
  val qty: List<String> = emptyList(),
  val counters: List<String> = emptyList(),
  val derived: List<String> = emptyList(),
  val localOnly: List<String> = emptyList(),
  val cancel: String = "",
  val v3only: Boolean = false,
) {
  val kinds: List<String> = V3Schema.KINDS.getValue(policy)
    .filter { (it != "cancel" || cancel.isNotEmpty()) && (it != "delta" || counters.isNotEmpty()) }
  val intFields: Set<String> = money.map { it + "Minor" }.toSet() + qty.map { it + "Milli" }
  val legacyNames: Set<String> = (money + qty).toSet()
}

object V3Schema {
  val KINDS: Map<String, List<String>> = mapOf(
    "master" to listOf("insert", "update", "delete", "delta"),
    "doc" to listOf("insert", "delta", "cancel"),
    "log" to listOf("insert"),
    "moves" to listOf("insert"),
    "count" to listOf("insert"),
  )

  val TABLES: Map<String, V3TableSpec> = listOf(
    V3TableSpec("products", "master", money = listOf("purchasePrice", "salePrice", "wholesalePrice"),
      qty = listOf("minStock"), derived = listOf("stock")),
    V3TableSpec("debtors", "master", derived = listOf("balance", "totalDebt")),
    V3TableSpec("suppliers", "master", derived = listOf("balance")),
    V3TableSpec("expenses", "master", money = listOf("amount")),
    V3TableSpec("productCategories", "master"),
    V3TableSpec("productUnits", "master"),
    V3TableSpec("expenseCategories", "master"),
    V3TableSpec("sales", "doc",
      money = listOf("total", "discount", "finalTotal", "paidAmount", "remaining", "debtGiven", "debtSettled"),
      counters = listOf("paidAmountMinor", "remainingMinor", "debtGivenMinor", "debtSettledMinor"),
      cancel = "sales.cancel", localOnly = listOf("syncStatus")),
    V3TableSpec("saleItems", "doc", money = listOf("unitPrice", "purchasePrice", "totalPrice"),
      qty = listOf("quantity", "returnedQty"), counters = listOf("returnedQtyMilli")),
    V3TableSpec("returns", "doc", money = listOf("amount"), qty = listOf("quantity"), cancel = "data.delete.any"),
    V3TableSpec("transactions", "doc", money = listOf("amount"), cancel = "data.delete.any"),
    V3TableSpec("purchases", "doc", money = listOf("purchasePrice", "totalAmount", "paidAmount", "debt"),
      qty = listOf("quantity"), counters = listOf("paidAmountMinor", "debtMinor"), cancel = "data.delete.any"),
    V3TableSpec("supplierPayments", "doc", money = listOf("amount"), cancel = "data.delete.any"),
    V3TableSpec("warehouseEntries", "doc", money = listOf("price"), qty = listOf("cartons", "perCarton", "units"),
      cancel = "data.delete.any"),
    V3TableSpec("priceHistory", "log", money = listOf("oldPrice", "newPrice")),
    V3TableSpec("auditLog", "log", money = listOf("amount")),
    //  گاوصندوق (PAY-18): مبلغ از اول `amountMinor`ِ صحیح است، تبدیل ندارد
    V3TableSpec("safeEntries", "log"),
    V3TableSpec("stockMovements", "moves", qty = listOf("qty")),
    V3TableSpec("stockCounts", "count", qty = listOf("countedQty"), v3only = true),
  ).associateBy { it.name }

  fun specOf(table: String?): V3TableSpec? = table?.let { TABLES[it] }
}

/**
 *  مبدلِ «دفترِ امروز ⇒ op ِ v3» و برعکس — قرینهٔ `fromV2`ِ سرور و `license/sync-v3-engine.js`.
 *
 *  ⛔ **یک قاعدهٔ گردکردن.** مبلغ فقط با `Money.fromLegacy` و مقدار فقط با
 *  `Qty.fromLegacy` (همان `Math.round`ِ سرور؛ بردارهای `money-v1.json`) تبدیل
 *  می‌شود. نامفهوم یا بیرون از بازهٔ دقیق (۲^۵۳) ⇒ همان نامِ قدیم می‌ماند و op
 *  «بومی» نیست ⇒ با `format: "v2"` می‌رود تا خودِ سرور تصمیم بگیرد.
 */
object V3Convert {
  const val DEFAULT_CANCEL_REASON = "لغو از نسخهٔ قدیمیِ برنامه"
  const val DELETE_CANCEL_REASON = "حذف از نسخهٔ قدیمیِ برنامه"
  private const val MAX_SAFE = 9_007_199_254_740_991L
  private const val MAX_FIELDS_BYTES = 64 * 1024
  private val OP_ID_RE = Regex("[A-Za-z0-9_.-]{1,80}")
  private val ALL_KINDS = listOf("insert", "update", "delete", "delta", "cancel")
  //  همان عددهایی که `Number(string)`ِ JS می‌فهمد (بی 0x و بی پسوندِ d/f ِ جاوا)
  private val JS_DECIMAL = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

  data class Result(
    val ok: Boolean,
    val ops: List<JsonObject>,
    val dropped: List<String>,
    val native: Boolean,
    val reason: String,
  )

  private fun isNumber(e: JsonElement?): Boolean =
    e is JsonPrimitive && e !is JsonNull && !e.isString && e.content.toDoubleOrNull()?.isFinite() == true

  private fun isInc(v: JsonElement): Boolean =
    v is JsonObject && v.size == 1 && isNumber(v["\$inc"])

  /** عددِ JS (یا رشتهٔ عددی) ⇒ Double؛ نامفهوم ⇒ null */
  private fun legacyNumber(v: JsonElement): Double? {
    val p = v as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    val text = if (p.isString) p.content.trim().also { if (it.isEmpty() || !JS_DECIMAL.matches(it)) return null } else p.content
    return text.toDoubleOrNull()?.takeIf { it.isFinite() }
  }

  fun toMinor(v: JsonElement): Long? {
    val n = legacyNumber(v) ?: return null
    return runCatching { Money.fromLegacy(n, Currency.AFN).minor }.getOrNull()?.takeIf { abs(it) <= MAX_SAFE }
  }

  fun toMilli(v: JsonElement): Long? {
    val n = legacyNumber(v) ?: return null
    return runCatching { Qty.fromLegacy(n).milli }.getOrNull()?.takeIf { abs(it) <= MAX_SAFE }
  }

  private class Fields(val set: LinkedHashMap<String, JsonElement>, val inc: LinkedHashMap<String, JsonElement>, val dropped: List<String>)

  private fun convertFields(spec: V3TableSpec, fields: JsonObject?): Fields {
    val set = LinkedHashMap<String, JsonElement>()
    val inc = LinkedHashMap<String, JsonElement>()
    val dropped = ArrayList<String>()
    for ((k, v) in fields ?: JsonObject(emptyMap())) {
      if (k == "id") continue
      if (k in spec.derived || k in spec.localOnly) { dropped += k; continue }
      val unit: Pair<String, (JsonElement) -> Long?>? = when (k) {
        in spec.money -> "Minor" to ::toMinor
        in spec.qty -> "Milli" to ::toMilli
        else -> null
      }
      if (isInc(v)) {
        val d = (v as JsonObject).getValue("\$inc")
        if (unit != null) {
          val n = unit.second(d)
          if (n != null) { inc[k + unit.first] = JsonPrimitive(n); continue }
        } else { inc[k] = d; continue }
        set[k] = v
        continue
      }
      if (unit != null) {
        if (v is JsonNull) { set[k + unit.first] = JsonNull; continue }
        val n = unit.second(v)
        if (n != null) { set[k + unit.first] = JsonPrimitive(n); continue }
      }
      set[k] = v
    }
    return Fields(set, inc, dropped)
  }

  /** opِ شکلِ v1 (تفاضلِ دفتر) ⇒ opهای v3 — همان پسوندها و همان tx ِ `fromV2` */
  fun fromLegacyOp(op: SyncOp, hlc: String): Result {
    val spec = V3Schema.specOf(op.table)
    if (spec == null || spec.v3only) return Result(false, emptyList(), emptyList(), false, "unknown_table")
    val conv = if (op.type == SyncOp.DELETE) Fields(LinkedHashMap(), LinkedHashMap(), emptyList()) else convertFields(spec, op.fields)
    val set = conv.set
    val counterInc = LinkedHashMap<String, JsonElement>()
    for ((k, n) in conv.inc) {
      if (k in spec.counters) counterInc[k] = n else set[k] = buildJsonObject { put("\$inc", n) }
    }
    val parts = ArrayList<Pair<String, Map<String, JsonElement>>>()
    when {
      op.type == SyncOp.INSERT -> parts += "i" to mapOf("kind" to JsonPrimitive("insert"), "fields" to JsonObject(set))
      op.type == SyncOp.DELETE -> parts += if (spec.cancel.isNotEmpty()) {
        "c" to mapOf("kind" to JsonPrimitive("cancel"), "reason" to JsonPrimitive(DELETE_CANCEL_REASON), "fromDelete" to JsonPrimitive(true))
      } else {
        "x" to mapOf("kind" to JsonPrimitive("delete"))
      }
      spec.policy == "master" -> if (set.isNotEmpty()) parts += "u" to mapOf("kind" to JsonPrimitive("update"), "fields" to JsonObject(set))
      spec.cancel.isNotEmpty() && (set["status"] as? JsonPrimitive)?.let { it.isString && it.content == "cancelled" } == true -> {
        val given = (set["cancelReason"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
        val reason = if (given.isNotEmpty()) given.take(200) else DEFAULT_CANCEL_REASON
        set.remove("status")
        set.remove("cancelReason")
        parts += "c" to mapOf("kind" to JsonPrimitive("cancel"), "reason" to JsonPrimitive(reason))
      }
    }
    if (counterInc.isNotEmpty()) parts += "d" to mapOf("kind" to JsonPrimitive("delta"), "fields" to JsonObject(counterInc))
    if (op.type == SyncOp.UPDATE && spec.policy != "master" && set.isNotEmpty()) {
      parts += "e" to mapOf("kind" to JsonPrimitive("update"), "fields" to JsonObject(set))
    }
    val ops = parts.map { (suffix, p) ->
      buildJsonObject {
        put("tx", JsonPrimitive(op.opId))
        put("hlc", JsonPrimitive(hlc))
        put("table", JsonPrimitive(op.table))
        put("row_id", JsonPrimitive(op.rowId))
        for ((k, v) in p) put(k, v)
        put("op_id", JsonPrimitive(if (parts.size == 1) op.opId else "${op.opId}.$suffix"))
      }
    }
    val reason = ops.firstNotNullOfOrNull { nativeReason(it).ifEmpty { null } }.orEmpty()
    return Result(true, ops, conv.dropped, reason.isEmpty(), reason)
  }

  private fun isSafeInteger(e: JsonElement?): Boolean {
    if (!isNumber(e)) return false
    val d = (e as JsonPrimitive).content.toDouble()
    return d == floor(d) && abs(d) <= MAX_SAFE.toDouble()
  }

  private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

  /** `''` یا دلیلِ ردِ همین op به‌عنوانِ v3ِ بومی — قرینهٔ `validate(raw,'v3')`ِ سرور */
  fun nativeReason(r: JsonObject): String {
    val opId = str(r, "op_id").orEmpty()
    if (!OP_ID_RE.matches(opId)) return "bad_op_id"
    val spec = V3Schema.specOf(str(r, "table")) ?: return "unknown_table"
    val rowId = str(r, "row_id")
    if (rowId.isNullOrEmpty() || rowId.length > 80) return "bad_row_id"
    if (opId.startsWith("srv.") || rowId.startsWith("srv.")) return "reserved_id"
    val kind = str(r, "kind")
    if (kind !in ALL_KINDS) return "bad_kind"
    if (kind !in spec.kinds) return if (spec.policy == "master") "bad_kind" else "append_only"
    if (Hlc.canonical(str(r, "hlc")).isEmpty()) return "bad_hlc"
    val fields = r["fields"]
    if (kind == "delete" || kind == "cancel") {
      if (fields != null && fields !is JsonNull && !(fields is JsonObject && fields.isEmpty())) return "unexpected_fields"
    } else {
      if (fields !is JsonObject) return "bad_fields"
      if (fields.isEmpty()) return "empty_fields"
      for ((k, v) in fields) {
        if (k.isEmpty() || k.length > 64 || k.startsWith("$") || k == "id") return "bad_field_name"
        if (kind == "delta") {
          if (k !in spec.counters) return "not_a_counter"
          if (!isSafeInteger(v)) return "not_integer"
          continue
        }
        if (k in spec.legacyNames) return "legacy_field"
        if (k in spec.derived) return "derived_field"
        if (v is JsonObject && v.keys.any { it.startsWith("$") }) return "bad_value"
        if (k in spec.intFields && v !is JsonNull && !isSafeInteger(v)) return "not_integer"
      }
      if (fields.toString().encodeToByteArray().size > MAX_FIELDS_BYTES) return "fields_too_large"
      if (kind == "insert") {
        val productOk = !str(fields, "productId").isNullOrEmpty()
        if (spec.policy == "moves" && (!productOk || !isSafeInteger(fields["qtyMilli"]))) return "bad_move"
        if (spec.policy == "count" && (!productOk || !isSafeInteger(fields["countedQtyMilli"]))) return "bad_count"
      }
    }
    if (kind == "cancel" && str(r, "reason")?.trim().isNullOrEmpty()) return "reason_required"
    val tx = r["tx"]
    if (tx != null && tx !is JsonNull && (tx as? JsonPrimitive)?.content.orEmpty().length > 80) return "bad_tx"
    return ""
  }

  /** عددِ دفتر به شکلِ JS: ۱۲۰ نه ۱۲۰٫۰ */
  private fun jsNumber(d: Double): JsonPrimitive =
    if (d == floor(d) && abs(d) < 9.007199254740992E15) JsonPrimitive(d.toLong()) else JsonPrimitive(d)

  private val UNIT_SUFFIX = Regex("(.*)(Minor|Milli)")

  /** دادهٔ v3 (`…Minor`/`…Milli`) ⇒ نام و واحدِ دفترِ امروز؛ تقسیمِ دقیق، بی گردکردن */
  fun dataToLegacy(spec: V3TableSpec, data: JsonObject): JsonObject {
    val out = LinkedHashMap<String, JsonElement>()
    for ((k, v) in data) {
      val m = UNIT_SUFFIX.matchEntire(k)
      val base = m?.groupValues?.get(1)
      val minor = m?.groupValues?.get(2) == "Minor"
      if (m != null && base != null && base in (if (minor) spec.money else spec.qty)) {
        out[base] = when {
          v is JsonNull -> JsonNull
          isNumber(v) -> jsNumber((v as JsonPrimitive).content.toDouble() / (if (minor) 100.0 else 1000.0))
          else -> v
        }
        continue
      }
      out[k] = v
    }
    return JsonObject(out)
  }

  /** یک opِ رسیده از `pull` ⇒ opِ شکلِ v1 برای `LedgerDiff.applyRemote`؛ null یعنی ربطی به دفتر ندارد */
  fun effectToLegacy(item: JsonObject): SyncOp? {
    val spec = V3Schema.specOf(str(item, "table")) ?: return null
    if (spec.v3only) return null
    val e = item["effect"] as? JsonObject ?: return null
    val opId = str(item, "op_id").orEmpty()
    val rowId = str(item, "row_id") ?: return null
    if ((e["deleted"] as? JsonPrimitive)?.content == "true") return SyncOp(opId, 0L, spec.name, rowId, SyncOp.DELETE, null)
    val fields = LinkedHashMap<String, JsonElement>()
    (e["set"] as? JsonObject)?.let { fields.putAll(dataToLegacy(spec, it)) }
    (e["inc"] as? JsonObject)?.let { inc ->
      for ((k, v) in dataToLegacy(spec, inc)) fields[k] = buildJsonObject { put("\$inc", v) }
    }
    if (fields.isEmpty()) return null
    val type = if (str(item, "kind") == "insert") SyncOp.INSERT else SyncOp.UPDATE
    return SyncOp(opId, 0L, spec.name, rowId, type, JsonObject(fields))
  }

  /** پاسخِ `/snapshot`ِ v3 ⇒ همان شکلی که `LedgerDiff.fromSnapshot` می‌خواند */
  fun snapshotToLegacy(body: JsonObject): JsonObject {
    val src = body["tables"] as? JsonObject ?: JsonObject(emptyMap())
    return buildJsonObject {
      put("tables", buildJsonObject {
        for ((name, rows) in src) {
          val spec = V3Schema.specOf(name) ?: continue
          if (spec.v3only || rows !is JsonArray) continue
          put(name, buildJsonArray {
            for (r in rows) {
              val o = r as? JsonObject ?: continue
              add(buildJsonObject {
                o["id"]?.let { put("id", it) }
                put("data", dataToLegacy(spec, o["data"] as? JsonObject ?: JsonObject(emptyMap())))
              })
            }
          })
        }
      })
      body["cursor"]?.let { put("cursor", it) }
    }
  }
}
