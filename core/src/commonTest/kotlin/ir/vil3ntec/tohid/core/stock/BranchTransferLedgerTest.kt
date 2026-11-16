package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.stock.BranchTransfer.Direction
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Draft
import ir.vil3ntec.tohid.core.stock.BranchTransfer.End
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Line
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Problem
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Result
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Status
import ir.vil3ntec.tohid.core.stock.BranchTransfer.TLine
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Transfer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 *  ۹۴۶ (پایانِ فازِ ۴ ِ شعبه‌ها): ماشینِ وضعیتِ کاملِ کلاینت (پیش‌نویس، ویرایش، ارسالِ پیش‌نویس، لغو) و آینهٔ دفترِ دستگاه
 *  (`BranchTransferLedger`): اثرِ هر وضعیت روی هر سو، شناسهٔ ثابت و «فقط تفاوت» ⇒ هرگز دو بار کم/زیاد نمی‌شود.
 */
class BranchTransferLedgerTest {

  private val a = "brn_" + "a".repeat(24)
  private val b = "brn_" + "b".repeat(24)
  private val c = "brn_" + "c".repeat(24)
  private val id = "bxf_" + "2".repeat(24)
  private val key = "bxKEY0123456789abcdefghij"

  private fun t(status: Status, dir: Direction = Direction.OUT, sentAt: Long? = 20, ls: List<TLine> = emptyList()) = Transfer(
    id = id, direction = dir, fromBranch = End(a, "الف", false), toBranch = End(b, "ب", false), from = "shop_floor", to = "warehouse",
    status = status, lines = ls.ifEmpty { listOf(TLine("tea", 3000, null, 3000, 0)) }, sentAt = sentAt,
  )

  @Test fun effectOfEveryStateOnEachSide() {
    //  پیش‌نویس و لغوِ پیش‌نویس ⇒ هیچ
    assertEquals(emptyMap(), BranchTransferLedger.effect(t(Status.DRAFT, sentAt = null), a))
    assertEquals(emptyMap(), BranchTransferLedger.effect(t(Status.CANCELLED, sentAt = null, ls = listOf(TLine("tea", 3000, null, 0, 0, 0))), a))
    //  در مسیر: الف −۳، ب هنوز هیچ
    val sent = t(Status.IN_TRANSIT)
    assertEquals(mapOf("tea" to -3000L), BranchTransferLedger.effect(sent, a))
    assertEquals(mapOf("tea" to 0L), BranchTransferLedger.effect(sent.copy(direction = Direction.IN), b))
    //  نیمه‌رسیده: ب +۱
    val part = t(Status.PARTIAL, ls = listOf(TLine("tea", 3000, 1000, 2000, 0)))
    assertEquals(mapOf("tea" to 1000L), BranchTransferLedger.effect(part, b))
    assertEquals(mapOf("tea" to -3000L), BranchTransferLedger.effect(part, a))
    //  رسید با کسری: الف −۳ (کسری برنمی‌گردد)، ب +۲٫۵
    val short = t(Status.RECEIVED, ls = listOf(TLine("tea", 3000, 2500, 0, 500)))
    assertEquals(mapOf("tea" to -3000L), BranchTransferLedger.effect(short, a))
    assertEquals(mapOf("tea" to 2500L), BranchTransferLedger.effect(short, b))
    //  رد پس از ۱ ⇒ ۲ به الف برگشت: الف −۱، ب +۱
    val rej = t(Status.REJECTED, ls = listOf(TLine("tea", 3000, 1000, 0, 0, 2000)))
    assertEquals(mapOf("tea" to -1000L), BranchTransferLedger.effect(rej, a))
    assertEquals(mapOf("tea" to 1000L), BranchTransferLedger.effect(rej, b))
    //  لغوِ فرستاده ⇒ همه برگشت: الف صفر
    val cancelled = t(Status.CANCELLED, ls = listOf(TLine("tea", 3000, null, 0, 0, 3000)))
    assertEquals(mapOf("tea" to 0L), BranchTransferLedger.effect(cancelled, a))
    //  شعبهٔ سوم هیچ
    assertEquals(emptyMap(), BranchTransferLedger.effect(sent, c))
    //  جمعِ کالا: الف + ب + کسری = صفر (کالا گم نمی‌شود و دو تا نمی‌شود)
    for (x in listOf(sent, part, short, rej, cancelled)) {
      val sum = BranchTransferLedger.effect(x, a).values.sum() + (BranchTransferLedger.effect(x, b).values.sum()) + x.shortMilli + x.pendingMilli
      assertEquals(0L, sum, "${x.status}")
    }
  }

  @Test fun planOnlyWritesTheDifferenceWithStableIds() {
    val sent = t(Status.IN_TRANSIT, ls = listOf(TLine("tea", 3000, null, 3000, 0), TLine("rice", 1000, null, 1000, 0)))
    val first = BranchTransferLedger.plan(sent, a, emptyMap())
    assertEquals(listOf("tea" to -3000L, "rice" to -1000L), first.map { it.productId to it.milli })
    assertTrue(first.all { it.side == BranchTransferLedger.OUT && it.rowId.startsWith("bxw-$id-out-") })
    assertEquals(first.map { it.rowId }.toSet().size, 2)
    //  دوباره خواندن با همان ردیف‌ها ⇒ هیچ (دو بار کم نمی‌شود)
    val have = first.associate { it.rowId to it.milli }
    assertEquals(emptyList(), BranchTransferLedger.plan(sent, a, have))
    //  رد پس از نیمه ⇒ همان ردیف‌ها با مقدارِ تازه، نه ردیفِ دوم
    val rej = sent.copy(status = Status.REJECTED, lines = listOf(TLine("tea", 3000, 1000, 0, 0, 2000), TLine("rice", 1000, 0, 0, 0, 1000)))
    val again = BranchTransferLedger.plan(rej, a, have)
    assertEquals(listOf("tea" to -1000L, "rice" to 0L), again.map { it.productId to it.milli })
    assertEquals(first.map { it.rowId }, again.map { it.rowId })
    //  گیرنده: ردیفِ «in» ِ جدا؛ صفرِ نبوده ساخته نمی‌شود
    assertEquals(emptyList(), BranchTransferLedger.plan(sent.copy(direction = Direction.IN), b, emptyMap()))
    val got = BranchTransferLedger.plan(rej.copy(direction = Direction.IN), b, emptyMap())
    assertEquals(listOf("tea" to 1000L), got.map { it.productId to it.milli })
    assertTrue(got.single().rowId.startsWith("bxw-$id-in-"))
    //  شناسهٔ ثابت برای هر کالا (هر نویسه‌ای) و کوتاه
    val weird = BranchTransferLedger.rowId(id, "out", "کالا/۱ با فاصله و \u0000")
    assertEquals(weird, BranchTransferLedger.rowId(id, "out", "کالا/۱ با فاصله و \u0000"))
    assertTrue(weird.length <= 64 && Regex("^[A-Za-z0-9_-]+$").matches(weird), weird)
    //  یک انتقال دو بار در فهرست ⇒ یک بار
    assertEquals(first, BranchTransferLedger.planAll(listOf(sent, sent), a, emptyMap()))
  }

  @Test fun draftEditSendAndCancelCalls() {
    val d = Draft(key, toBranch = b, lines = listOf(Line("tea", 2000)))
    val asDraft = assertIs<Result.Ok>(BranchTransfer.send(a, d, draft = true)).call
    assertEquals("true", asDraft.body!!["draft"]!!.jsonPrimitive.content)
    assertEquals(null, assertIs<Result.Ok>(BranchTransfer.send(a, d)).call.body!!["draft"])
    val draft = t(Status.DRAFT, sentAt = null)
    assertTrue(BranchTransfer.canEdit(draft, a) && BranchTransfer.canSend(draft, a) && BranchTransfer.canCancel(draft, a))
    //  ویرایش: PUT ِ همان مسیر با کلیدِ فرم
    val e = assertIs<Result.Ok>(BranchTransfer.edit(a, draft, BranchTransfer.draftOf(draft, key))).call
    assertEquals("PUT" to "/shop/catalog/branch-transfers/$id", e.method to e.path)
    assertEquals(key, e.headers[BranchTransfer.HEADER_KEY])
    val s = assertIs<Result.Ok>(BranchTransfer.sendDraft(a, draft, key)).call
    assertEquals("/shop/catalog/branch-transfers/$id/send", s.path)
    //  لغوِ پیش‌نویس بی دلیل؛ لغوِ فرستاده فقط با دلیل؛ نیمه‌رسیده ⇒ نه؛ مقصد ⇒ «فقط فرستنده»
    assertIs<Result.Ok>(BranchTransfer.cancel(a, draft, key))
    val sent = t(Status.IN_TRANSIT)
    assertEquals(Problem.REASON_REQUIRED, assertIs<Result.Bad>(BranchTransfer.cancel(a, sent, key)).problem)
    val c1 = assertIs<Result.Ok>(BranchTransfer.cancel(a, sent, key, "اشتباه")).call
    assertEquals("اشتباه", c1.body!!["reason"]!!.jsonPrimitive.content)
    val part = t(Status.PARTIAL, ls = listOf(TLine("tea", 3000, 1000, 2000, 0)))
    assertFalse(BranchTransfer.canCancel(part, a))
    assertEquals(Problem.PARTIAL_RECEIVED, assertIs<Result.Bad>(BranchTransfer.cancel(a, part, key, "x")).problem)
    assertEquals(Problem.NOT_SENDER, assertIs<Result.Bad>(BranchTransfer.cancel(b, sent.copy(direction = Direction.IN), key, "x")).problem)
    assertEquals(Problem.BAD_STATE, assertIs<Result.Bad>(BranchTransfer.sendDraft(a, sent, key)).problem)
    assertEquals(Problem.CLOSED, assertIs<Result.Bad>(BranchTransfer.edit(a, t(Status.CANCELLED), BranchTransfer.draftOf(draft, key))).problem)
    //  ویرایش هم همان پیش‌سنجیِ ارسال
    assertEquals(Problem.SAME_BRANCH, assertIs<Result.Bad>(BranchTransfer.edit(a, draft, BranchTransfer.draftOf(draft, key).copy(toBranch = a))).problem)
  }

  @Test fun readsDraftFieldsAndOldServers() {
    val v946 = Json.parseToJsonElement(
      """{"id":"$id","direction":"out","fromBranch":{"id":"$a","name":"الف","main":false},"toBranch":{"id":"","name":"","main":true},
        "from":"shop_floor","to":"warehouse","status":"cancelled","lines":[{"productId":"tea","sentMilli":3000,"receivedMilli":null,
        "shortMilli":0,"pendingMilli":0,"returnedMilli":3000}],"note":"","reason":"x","createdBy":"usr_1","createdAt":10,
        "sentBy":"usr_2","sentAt":15,"closedBy":"usr_1","closedAt":30}""",
    ).jsonObject
    val x = BranchTransfer.transferOf(v946)!!
    assertEquals(Status.CANCELLED, x.status)
    assertEquals(15L, x.sentAt)
    assertEquals("usr_2", x.sentBy)
    assertEquals(3000L, x.returnedMilli)
    assertEquals(mapOf("tea" to 0L), BranchTransferLedger.effect(x, a))
    //  پیش‌نویس: sentAt تهی
    val draft = Json.parseToJsonElement(v946.toString().replace("\"cancelled\"", "\"draft\"").replace("\"sentAt\":15", "\"sentAt\":null")).jsonObject
    assertEquals(null, BranchTransfer.transferOf(draft)!!.sentAt)
    //  سرورِ پیش از ۹۴۶ (بی sentAt): همان لحظهٔ ساختن
    val old = Json.parseToJsonElement(v946.toString().replace(",\"sentBy\":\"usr_2\",\"sentAt\":15", "").replace("\"cancelled\"", "\"in_transit\"")).jsonObject
    assertEquals(10L, BranchTransfer.transferOf(old)!!.sentAt)
  }

  @Test fun branchSettingsAndWhoSeesOtherBranches() {
    val s = BranchStock.settingsOf(Json.parseToJsonElement("""{"settings":{"staffOtherStock":true,"transferNegative":"true"}}""").jsonObject)
    assertEquals(BranchStock.Settings(staffOtherStock = true, transferNegative = false), s)
    assertEquals(BranchStock.Settings(), BranchStock.settingsOf(null))
    assertEquals("""{"transferNegative":true}""", BranchStock.settingsBody(transferNegative = true).toString())
    assertTrue(BranchStock.seesOthers("owner", BranchStock.Settings(), false))
    assertTrue(BranchStock.seesOthers("manager", BranchStock.Settings(), false))
    assertFalse(BranchStock.seesOthers("staff", BranchStock.Settings(), false))
    assertFalse(BranchStock.seesOthers("cashier", BranchStock.Settings(), false))
    assertTrue(BranchStock.seesOthers("staff", BranchStock.Settings(staffOtherStock = true), false))
    assertTrue(BranchStock.seesOthers("cashier", BranchStock.Settings(), granted = true))
  }
}
