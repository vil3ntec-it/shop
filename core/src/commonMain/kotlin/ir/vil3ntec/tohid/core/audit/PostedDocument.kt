package ir.vil3ntec.tohid.core.audit

/**
 * SEC-03 — سندِ ثبت‌شده ویرایش نمی‌شود؛ اصلاح فقط با سندِ معکوس.
 *
 * سندِ v1: `{ id, series, no, type, status, postedAt, lines:[{qty, unitPrice, amount…}],
 * payments:[{amount…}], total, discount, … }` — همهٔ مبلغ‌ها صحیح به کوچک‌ترین واحد. سندِ
 * `status = "posted"` هرگز عوض نمی‌شود؛ اصلاح یعنی سندِ تازه‌ای با `reverses = <id>` که مقدارها و
 * مبلغ‌هایش قرینه‌اند. `unitPrice` قرینه نمی‌شود: مقدارِ منفی × قیمتِ مثبت.
 *
 * ⛔ همان قاعدهٔ `license/hash-chain.js` (reverseDoc/guardEdit) و همان بردارهای
 * `license/test-vectors/hashchain-v1.json` — ترتیبِ خطاها هم یکی است:
 * not_posted، is_reversal، already_reversed، bad_opts، خطای قالب، not_integer.
 *
 * سند همان درختِ JSON ِ ساده‌ی [HashChain] است (`Map<String, *>`)؛ Kotlin خالص.
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

  fun isPosted(doc: Map<String, Any?>?): Boolean = doc?.get("status") == "posted"

  fun guardEdit(before: Map<String, Any?>?, after: Map<String, Any?>): EditCheck {
    if (before == null || !isPosted(before)) return EditCheck(true)
    if (HashChain.canonicalJson(before) == HashChain.canonicalJson(after)) return EditCheck(true)
    return EditCheck(false, "posted_immutable")
  }

  fun findReversal(docs: List<Map<String, Any?>>, id: String?): Map<String, Any?>? =
    if (id == null) null else docs.firstOrNull { it["reverses"] == id }

  private fun negated(o: Map<*, *>): Map<String, Any?> {
    val m = LinkedHashMap<String, Any?>()
    for ((k, v) in o) m[k as? String ?: throw HashChainException("bad_value", "کلیدِ شیء باید رشته باشد")] = v
    for (k in NEGATED_KEYS) {
      val v = m[k] ?: continue
      if (v !is Number) throw HashChainException("not_integer", "$k باید صحیح باشد")
      val n = HashChain.integerValue(v)
      m[k] = if (n == 0L) 0L else -n
    }
    return m
  }

  /** سندِ معکوسِ [orig]؛ خودِ [orig] دست نمی‌خورد. */
  fun reverse(
    orig: Map<String, Any?>,
    req: ReversalRequest,
    existing: List<Map<String, Any?>> = emptyList(),
  ): Map<String, Any?> {
    if (!isPosted(orig)) throw HashChainException("not_posted", "فقط سندِ ثبت‌شده معکوس می‌شود")
    if (orig["reverses"] != null) throw HashChainException("is_reversal", "سندِ معکوس دوباره معکوس نمی‌شود")
    if (findReversal(existing, orig["id"] as? String) != null) throw HashChainException("already_reversed")
    if (req.id.isBlank() || req.reason.isBlank() || req.by.isBlank() || req.no < 1 ||
      req.no > HashChain.MAX_SAFE || req.postedAt > HashChain.MAX_SAFE || req.postedAt < -HashChain.MAX_SAFE
    ) {
      throw HashChainException("bad_opts", "id، no، postedAt، reason و by لازم‌اند")
    }
    HashChain.canonicalJson(orig) // فقط سندِ معتبر

    val m = LinkedHashMap(negated(orig))
    for (a in LINE_ARRAYS) {
      val arr = m[a] as? List<*> ?: continue
      m[a] = arr.map { if (it is Map<*, *>) negated(it) else it }
    }
    m["id"] = req.id
    m["no"] = req.no
    m["postedAt"] = req.postedAt
    m["status"] = "posted"
    val origId = orig["id"]
    if (origId != null) m["reverses"] = origId else m.remove("reverses")
    m["reason"] = req.reason
    m["by"] = req.by
    return m
  }
}
