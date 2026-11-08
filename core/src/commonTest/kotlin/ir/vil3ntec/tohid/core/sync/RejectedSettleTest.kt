package ir.vil3ntec.tohid.core.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 *  برنامهٔ هم‌سو شدنِ دستگاهِ سازنده پس از «کنار بگذار»/«اصلاح و ثبت» (`RejectedSettle.plan`) — بی دستگاه و بی دیسک:
 *  درج ⇒ بیرون (کامل در `rows`)، `$inc` ⇒ دلتای وارونه، فیلدِ ساده ⇒ مقدارِ امروزِ سرور فقط اگر از آن پس عوض نشده،
 *  ردیفِ حذف‌شده ⇒ ردیفِ سرور؛ تصمیمِ باز/دوباره‌ثبت/تکراری/دستگاهِ دیگر/بستهٔ ناقص ⇒ هیچ.
 */
class RejectedSettleTest {

  private fun p(v: Any?): JsonElement = RejectedSettle.toJson(v)
  private fun row(vararg kv: Pair<String, Any?>): JsonObject = JsonObject(kv.associate { it.first to p(it.second) })
  private fun op(id: String, table: String, rowId: String, type: String, fields: JsonObject? = null) = buildJsonObject {
    put("op_id", JsonPrimitive(id)); put("table", JsonPrimitive(table)); put("row_id", JsonPrimitive(rowId)); put("type", JsonPrimitive(type))
    if (fields != null) put("fields", fields)
  }
  private fun inc(n: Number) = buildJsonObject { put("\$inc", p(n)) }
  private fun tree(vararg t: Pair<String, List<JsonObject>>) = JsonObject(t.associate { it.first to JsonArray(it.second) })

  private fun mine(
    status: String, ops: List<JsonObject>, base: Map<String, Map<String, Any?>?> = emptyMap(), truncated: Boolean = false,
    table: String = "sales", rowId: String = "s-1", id: String = "cf_1",
  ) = ConflictInbox.Mine(
    id = id, table = table, rowId = rowId, opId = (ops.first()["op_id"] as JsonPrimitive).content, device = "pos-1",
    reason = "approval_required", status = status, at = 1, resolvedAt = 2, note = "مشتری نخواست", fixedDoc = "",
    settle = ConflictInbox.Settle(
      "v1", ops.map { ConflictInbox.SettleOp((it["op_id"] as JsonPrimitive).content, (it["table"] as JsonPrimitive).content, (it["row_id"] as JsonPrimitive).content, (it["type"] as JsonPrimitive).content) },
      base, truncated,
    ),
  )

  //  فروشِ نسیهٔ ردشده: فاکتور، قلم، حرکتِ انبار، قرض
  private val sale = row("id" to "s-1", "invoiceNumber" to 41, "finalTotal" to 1200, "status" to "completed", "debtorId" to "d-1")
  private val item = row("id" to "s-1-i", "saleId" to "s-1", "productId" to "sugar", "quantity" to 3, "returnedQty" to 0)
  private val move = row("id" to "s-1-m", "productId" to "sugar", "qty" to -3, "refId" to "s-1")
  private val give = row("id" to "s-1-g", "debtorId" to "d-1", "type" to "give", "amount" to 1200)
  private val saleOps = listOf(
    op("o1", "sales", "s-1", "insert", sale), op("o2", "saleItems", "s-1-i", "insert", item),
    op("o3", "stockMovements", "s-1-m", "insert", move), op("o4", "transactions", "s-1-g", "insert", give),
  )
  private val ledger = tree("sales" to listOf(sale), "saleItems" to listOf(item), "stockMovements" to listOf(move), "transactions" to listOf(give))

  private fun ok(r: RejectedSettle.Result): RejectedSettle.Plan = assertIs<RejectedSettle.Result.Ok>(r).plan
  private fun undoOf(plan: RejectedSettle.Plan) = plan.undo.map { listOf(it["table"], it["row_id"], it["type"]).joinToString("/") { e -> (e as JsonPrimitive).content } }

  @Test fun `کنار گذاشتنِ فروش همهٔ ردیف‌ها را بیرون می‌برد و کامل نگه می‌دارد`() {
    val plan = ok(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, saleOps), saleOps, ledger, emptySet()))
    assertEquals(listOf("sales/s-1/delete", "saleItems/s-1-i/delete", "stockMovements/s-1-m/delete", "transactions/s-1-g/delete"), undoOf(plan))
    assertEquals(listOf(sale), (plan.rows["sales"] as JsonArray).toList())
    assertEquals(listOf(give), (plan.rows["transactions"] as JsonArray).toList())
    assertEquals(4, plan.ops.size)
    assertEquals("s-1", plan.saleId)
    assertTrue(plan.kept.isEmpty())
    assertTrue(plan.undo.all { (it["op_id"] as JsonPrimitive).content.startsWith(RejectedSettle.UNDO_PREFIX) })
    //  «اصلاح» همان برنامه
    assertEquals(undoOf(plan), undoOf(ok(RejectedSettle.plan(mine(ConflictInbox.FIXED, saleOps), saleOps, ledger, emptySet()))))
  }

  @Test fun `باز، دوباره‌ثبت، تکراری، دستگاهِ دیگر و بستهٔ ناقص هیچ برنامه‌ای ندارند`() {
    fun none(r: RejectedSettle.Result) = assertIs<RejectedSettle.Result.None>(r).why
    assertEquals(RejectedSettle.Skip.NOT_ACTIONABLE, none(RejectedSettle.plan(mine(ConflictInbox.OPEN, saleOps), saleOps, ledger, emptySet())))
    assertEquals(RejectedSettle.Skip.NOT_ACTIONABLE, none(RejectedSettle.plan(mine(ConflictInbox.RESUBMITTED, saleOps), saleOps, ledger, emptySet())))
    assertEquals(RejectedSettle.Skip.DONE, none(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, saleOps), saleOps, ledger, setOf("cf_1"))))
    assertEquals(RejectedSettle.Skip.NOT_HERE, none(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, saleOps), emptyList(), ledger, emptySet())))
    assertEquals(RejectedSettle.Skip.TRUNCATED, none(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, saleOps, truncated = true), saleOps, ledger, emptySet())))
    assertEquals(RejectedSettle.Skip.NO_BUNDLE, none(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, saleOps).copy(settle = null), saleOps, ledger, emptySet())))
    //  opِ درجی که از دفترِ کنار بیرون رفته با جایش برگرداندنی است؛ ویرایشِ گم‌شده نه
    assertEquals(4, ok(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, saleOps), saleOps.take(1), ledger, emptySet())).undo.size)
    val upd = saleOps + op("o5", "sales", "s-0", "update", row("debtSettled" to inc(100)))
    assertEquals(RejectedSettle.Skip.MISSING_OPS, none(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, upd), saleOps, ledger, emptySet())))
  }

  @Test fun `برگشتِ ردشده شمارنده‌ها را با دلتای وارونه برمی‌گرداند`() {
    val old = row("id" to "s-0", "finalTotal" to 1000, "debtSettled" to 300, "status" to "completed")
    val line = row("id" to "s-0-i", "saleId" to "s-0", "quantity" to 2, "returnedQty" to 2)
    val ret = row("id" to "r-1", "saleId" to "s-0", "amount" to 1000)
    val ops = listOf(
      op("r1", "returns", "r-1", "insert", ret),
      op("r2", "saleItems", "s-0-i", "update", row("returnedQty" to inc(2))),
      op("r3", "sales", "s-0", "update", row("debtSettled" to inc(300))),
    )
    val l = tree("sales" to listOf(old), "saleItems" to listOf(line), "returns" to listOf(ret))
    val base = mapOf("saleItems/s-0-i" to mapOf("returnedQty" to 0), "sales/s-0" to mapOf("debtSettled" to 0))
    val plan = ok(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, ops, base, table = "returns", rowId = "r-1"), ops, l, emptySet()))
    val byRow = plan.undo.associateBy { (it["table"] as JsonPrimitive).content + "/" + (it["row_id"] as JsonPrimitive).content }
    assertEquals(inc(-2), (byRow.getValue("saleItems/s-0-i")["fields"] as JsonObject)["returnedQty"])
    assertEquals(inc(-300), (byRow.getValue("sales/s-0")["fields"] as JsonObject)["debtSettled"])
    assertEquals("delete", (byRow.getValue("returns/r-1")["type"] as JsonPrimitive).content)
    assertEquals("s-0", plan.saleId)
  }

  @Test fun `لغوِ ردشده وضعیت را به مقدارِ سرور برمی‌گرداند، مگر از آن پس این‌جا عوض شده باشد`() {
    val s = row("id" to "s-2", "status" to "cancelled", "cancelReason" to "اشتباه", "finalTotal" to 300)
    val mv = row("id" to "s-2-m", "productId" to "rice", "qty" to 1, "refId" to "s-2", "type" to "sale_cancel")
    val ops = listOf(op("c1", "sales", "s-2", "update", row("status" to "cancelled", "cancelReason" to "اشتباه")), op("c2", "stockMovements", "s-2-m", "insert", mv))
    val base = mapOf("sales/s-2" to mapOf("status" to "completed", "cancelReason" to ""))
    val plan = ok(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, ops, base, rowId = "s-2"), ops, tree("sales" to listOf(s), "stockMovements" to listOf(mv)), emptySet()))
    val upd = plan.undo.single { (it["type"] as JsonPrimitive).content == "update" }
    assertEquals(row("status" to "completed", "cancelReason" to ""), upd["fields"])
    assertTrue(plan.kept.isEmpty())
    //  کاربر پس از رد دلیل را عوض کرد ⇒ همان فیلد دست نمی‌خورد و دیده می‌شود
    val edited = tree("sales" to listOf(row("id" to "s-2", "status" to "cancelled", "cancelReason" to "دلیلِ تازه")), "stockMovements" to listOf(mv))
    val p2 = ok(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, ops, base, rowId = "s-2"), ops, edited, emptySet()))
    assertEquals(row("status" to "completed"), p2.undo.single { (it["type"] as JsonPrimitive).content == "update" }["fields"])
    assertEquals(listOf("sales/s-2.cancelReason"), p2.kept)
    //  سرور مقداری نداد ⇒ برگردانده نمی‌شود (نه حدس)
    val p3 = ok(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, ops, rowId = "s-2"), ops, tree("sales" to listOf(s), "stockMovements" to listOf(mv)), emptySet()))
    assertEquals(listOf("sales/s-2.status", "sales/s-2.cancelReason"), p3.kept)
  }

  @Test fun `ردیفِ حذف‌شده با ردیفِ سرور برمی‌گردد؛ ردیفی که سرور هم دارد بیرون نمی‌رود`() {
    val ops = listOf(op("x1", "sales", "s-3", "delete"))
    val plan = ok(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, ops, mapOf("sales/s-3" to mapOf("finalTotal" to 500, "status" to "completed")), rowId = "s-3"), ops, tree("sales" to emptyList()), emptySet()))
    assertEquals(listOf("sales/s-3/insert"), undoOf(plan))
    assertEquals(row("finalTotal" to 500, "status" to "completed"), plan.undo.single()["fields"])
    //  درجی که سرور همان شناسه را دارد ⇒ بیرون نمی‌رود
    val p2 = ok(RejectedSettle.plan(mine(ConflictInbox.DISMISSED, saleOps, mapOf("transactions/s-1-g" to mapOf("amount" to 1200))), saleOps, ledger, emptySet()))
    assertTrue("transactions/s-1-g/delete" !in undoOf(p2))
    assertEquals(listOf("transactions/s-1-g"), p2.kept)
  }

  @Test fun `opهای کنارگذاشته دیگر ردِ باز نیستند`() {
    assertEquals(setOf("o1", "o2"), RejectedSettle.settledOps(listOf(saleOps.take(1), saleOps.drop(1).take(1))))
  }
}
