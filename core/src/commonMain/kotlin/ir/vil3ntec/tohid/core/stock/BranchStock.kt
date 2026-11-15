package ir.vil3ntec.tohid.core.stock

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 *  POS-16/S17 — «موجودیِ همین کالا در شعبه‌های دیگر» از صندوق یا قیمت‌پرسی (فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰): همتای کلاینتِ
 *  `GET /api/shop/catalog/products/:id/other-branches` (`server/src/lib/other-branches.js`) و مجوزِ `stock.view_other_branches`
 *  (`PUT /api/shop/branches/members/:id/perms`، فقط صاحب). core شبکه ندارد: مسیر، خواندنِ پاسخ و قاعدهٔ «خاموش».
 *
 *  ⛔ فقط عدد: هر ردیف `{branchId, branchName, qty}`؛ هر کلیدِ دیگر خوانده نمی‌شود. کدام شعبه‌ها را فقط سرور می‌گوید (دسترسی و
 *     مجوز)؛ این‌جا هیچ شعبه‌ای فرستاده نمی‌شود جز سرآیندِ شعبهٔ همین دستگاه ([BranchTransfer.headers]).
 *  ⛔ پرچمِ `shop.branches` (یا `shop.catalog`) خاموش ⇒ سرور همان درِ ناشناخته (۴۰۴ `not_found`) ⇒ [off] ⇒ هیچ سطری، و برنامه تا
 *     [OFF_RECHECK_MS] دوباره نمی‌پرسد.
 *  منطقِ خالص (iOS هم)؛ آزمون: `BranchStockTest` (commonTest).
 */
object BranchStock {

  const val PERM = "stock.view_other_branches"
  const val OFF_RECHECK_MS = 10 * 60_000L
  private val BRANCH_RE = Regex("^brn_[0-9a-f]{24}$")

  /** موجودیِ آزادِ همان کالا در یک شعبهٔ دیگر (هزارم، هرگز منفی) */
  data class Qty(val branchId: String, val branchName: String, val qtyMilli: Long)

  private fun enc(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
      val c = b.toInt() and 0xFF
      val ok = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c == '_'.code || c == '-'.code || c == '.'.code
      if (ok) append(c.toChar()) else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0x0F])
    }
  }

  /** مسیر از ریشهٔ `/api` */
  fun path(productId: String): String = "/shop/catalog/products/${enc(productId)}/other-branches"

  fun call(branch: String, productId: String): BranchTransfer.Call = BranchTransfer.Call("GET", path(productId), mapOf(
    BranchTransfer.HEADER_BRANCH to BranchTransfer.branchHeader(branch),
  ))

  /** پاسخ ⇒ ردیف‌ها؛ ردیفِ بدشکل کنار، عددِ منفی ⇒ صفر */
  fun parse(body: JsonObject?): List<Qty> {
    val rows = body?.get("branches") as? JsonArray ?: return emptyList()
    return rows.mapNotNull { e ->
      val o = e as? JsonObject ?: return@mapNotNull null
      val id = (o["branchId"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return@mapNotNull null
      if (id != BranchTransfer.MAIN && !BRANCH_RE.matches(id)) return@mapNotNull null
      val name = (o["branchName"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()
      val qty = (o["qty"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return@mapNotNull null
      Qty(id, name, qty.coerceAtLeast(0L))
    }
  }

  /** پاسخِ «درِ ناشناخته» = پرچمِ خاموش (نه خطای شبکه، نه ۴۰۳ ِ شعبه) */
  fun off(status: Int, code: String?): Boolean = status == 404 && code == "not_found"

  /** پس از [off] تا کی نپرسد؛ ۰ ⇒ همین حالا */
  fun nextAsk(offAt: Long, now: Long): Long = if (offAt <= 0L) 0L else (offAt + OFF_RECHECK_MS - now).coerceAtLeast(0L)

  /** مجوزِ عضو برای صفحهٔ «شعبه‌ها» ِ صاحب */
  fun grantPath(memberId: String): String = "/shop/branches/members/${enc(memberId)}/perms"

  fun grantBody(on: Boolean): JsonObject = buildJsonObject { put("perms", buildJsonArray { if (on) add(JsonPrimitive(PERM)) }) }

  /** آیا عضوِ فهرستِ `GET /shop/branches/members` این مجوز را دارد */
  fun granted(member: JsonObject?): Boolean =
    (member?.get("perms") as? JsonArray)?.any { (it as? JsonPrimitive)?.contentOrNull == PERM } == true
}
