package ir.vil3ntec.tohid.core.audit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * SEC-03 — سندِ ثبت‌شده ویرایش نمی‌شود؛ اصلاح فقط با سندِ معکوس.
 *
 * سندِ v1: `{ id, series, no, type, status, postedAt, lines:[{qty, unitPrice,
 * amount…}], payments:[{amount…}], total, discount, … }` — همهٔ مبلغ‌ها صحیح
 * به کوچک‌ترین واحد. سندِ `status = "posted"` هرگز عوض نمی‌شود؛ اصلاح یعنی
 * سندِ تازه‌ای با `reverses = <id>` که مقدارها و مبلغ‌هایش قرینه‌اند.
 * `unitPrice` قرینه نمی‌شود: مقدارِ منفی × قیمتِ مثبت.
 *
 * ⛔ همان قاعدهٔ `license/hash-chain.js` (reverseDoc/guardEdit) و همان بردارهای
 * `license/test-vectors/hashchain-v1.json` — ترتیبِ خطاها هم یکی است:
 * not_posted، is_reversal، already_reversed، bad_opts، خطای قالب، not_integer.
 *
 * ⚠️ هنوز به صندوق وصل نیست؛ فقط منطق و آزمون (پرچمِ `hashchain.*` خاموش).
 */
data class EditCheck(val ok: Boolean, val code: String? = null)

data class ReversalRequest(
  val id: String,
  val no: Long,
  val postedAt: Long,
  val reason: String,
  val by: String,
)

object PostedDocument {
  val NEGATED_KEYS: List<String> = listOf("qty", "amount", "total", "subtotal", "discount", "tax", "paid", "change")
  val LINE_ARRAYS: List<String> = listOf("lines", "payments")

  private fun str(o: JsonObject?, key: String): String? =
    (o?.get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

  fun isPosted(doc: JsonObject?): Boolean = str(doc, "status") == "posted"

  fun guardEdit(before: JsonObject?, after: JsonObject): EditCheck {
    if (!isPosted(before)) return EditCheck(true)
    if (HashChain.canonicalJson(before!!) == HashChain.canonicalJson(after)) return EditCheck(true)
    return EditCheck(false, "posted_immutable")
  }

  fun findReversal(docs: List<JsonObject>, id: String?): JsonObject? =
    if (id == null) null else docs.firstOrNull { str(it, "reverses") == id }

  private fun negated(o: JsonObject): JsonObject {
    val m = LinkedHashMap<String, JsonElement>(o)
    for (k in NEGATED_KEYS) {
      val v = m[k] ?: continue
      if (v is JsonNull) continue
      val p = v as? JsonPrimitive
      if (p == null || p.isString || p.content == "true" || p.content == "false") {
        throw HashChainException("not_integer", "$k باید صحیح باشد")
      }
      val n = HashChain.integerValue(p.content)
      m[k] = JsonPrimitive(if (n == 0L) 0L else -n)
    }
    return JsonObject(m)
  }

  /** سندِ معکوسِ [orig]؛ خودِ [orig] دست نمی‌خورد. */
  fun reverse(orig: JsonObject, req: ReversalRequest, existing: List<JsonObject> = emptyList()): JsonObject {
    if (!isPosted(orig)) throw HashChainException("not_posted", "فقط سندِ ثبت‌شده معکوس می‌شود")
    val rv = orig["reverses"]
    if (rv != null && rv !is JsonNull) throw HashChainException("is_reversal", "سندِ معکوس دوباره معکوس نمی‌شود")
    if (findReversal(existing, str(orig, "id")) != null) throw HashChainException("already_reversed")
    if (req.id.isBlank() || req.reason.isBlank() || req.by.isBlank() || req.no < 1 ||
      req.no > HashChain.MAX_SAFE || req.postedAt > HashChain.MAX_SAFE || req.postedAt < -HashChain.MAX_SAFE
    ) {
      throw HashChainException("bad_opts", "id، no، postedAt، reason و by لازم‌اند")
    }
    HashChain.canonicalJson(orig) // فقط سندِ معتبر

    val m = LinkedHashMap<String, JsonElement>(negated(orig))
    for (a in LINE_ARRAYS) {
      val arr = m[a] as? JsonArray ?: continue
      m[a] = JsonArray(arr.map { if (it is JsonObject) negated(it) else it })
    }
    m["id"] = JsonPrimitive(req.id)
    m["no"] = JsonPrimitive(req.no)
    m["postedAt"] = JsonPrimitive(req.postedAt)
    m["status"] = JsonPrimitive("posted")
    val origId = orig["id"]
    if (origId != null) m["reverses"] = origId else m.remove("reverses")
    m["reason"] = JsonPrimitive(req.reason)
    m["by"] = JsonPrimitive(req.by)
    return JsonObject(m)
  }
}
