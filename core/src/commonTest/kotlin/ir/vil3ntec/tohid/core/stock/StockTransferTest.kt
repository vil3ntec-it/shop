package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.stock.StockTransfer.Line
import ir.vil3ntec.tohid.core.stock.StockTransfer.Outcome
import ir.vil3ntec.tohid.core.stock.StockTransfer.Problem
import ir.vil3ntec.tohid.core.stock.StockTransfer.Result
import ir.vil3ntec.tohid.core.stock.StockTransfer.Scan
import ir.vil3ntec.tohid.core.stock.StockTransfer.Status
import ir.vil3ntec.tohid.core.stock.StockTransfer.TLine
import ir.vil3ntec.tohid.core.stock.StockTransfer.Transfer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INV-11: پیش‌نویس ⇒ ارسال ⇒ دریافتِ ناقص/پایانی یا رد؛ دریافت با اسکن؛ نمای «سرور + صف» بی دو بار شمردن. */
class StockTransferTest {

  private fun ok(r: Result) = assertIs<Result.Ok>(r).step

  private val server = Transfer(
    id = "xfr_1", sendKey = "k-send-1", from = "shop_floor", to = "warehouse", status = Status.IN_TRANSIT,
    lines = listOf(TLine("tea", 6000, 0, 6000, 0), TLine("rice", 2500, 0, 2500, 0)), createdAt = 10,
  )
  private val owners = mapOf("626001" to "tea", "626002" to "rice", "626009" to "soap")

  private fun scan(t: Transfer, counted: Map<String, Long>, code: String) =
    StockTransfer.scan(t, counted, code, owners::get) { it == "rice" }

  @Test fun draftAddsMergesAndValidates() {
    var d = StockTransfer.Draft("k-1")
    assertEquals(Problem.EMPTY, StockTransfer.validate(d))
    d = d.plus("tea", 1000).plus("tea", 1000).plus("rice", 500)
    assertEquals(listOf(Line("tea", 2000), Line("rice", 500)), d.lines)
    assertEquals(listOf(Line("rice", 500)), d.with("tea", 0).lines)
    assertNull(StockTransfer.validate(d))
    assertEquals(Problem.SAME_PLACE, StockTransfer.validate(d.copy(to = "shop_floor")))
    assertEquals(Problem.BAD_PLACE, StockTransfer.validate(d.copy(to = "in_transit")))
    assertEquals(Problem.NO_KEY, StockTransfer.validate(d.copy(key = " ")))
    assertEquals("warehouse" to "shop_floor", d.swapped().let { it.from to it.to })
    assertEquals(Problem.TOO_MANY, StockTransfer.validate(StockTransfer.Draft("k", lines = (1..101).map { Line("p$it", 1000) })))
    //  پیش‌نویس پس از بستنِ برنامه همان است (همان کلید ⇒ همان ارسال)
    val kept = d.copy(note = "قفسهٔ ۲")
    assertEquals(kept, StockTransfer.draftOf(Json.parseToJsonElement(StockTransfer.draftJson(kept).toString()).jsonObject))
    assertNull(StockTransfer.draftOf(JsonObject(emptyMap())))
  }

  @Test fun sendStepIsTheServerShapeAndRoundTrips() {
    val step = ok(StockTransfer.send(StockTransfer.Draft("k-send-1", lines = listOf(Line("tea", 2000)), note = "  به انبار  "), at = 5))
    val x = step.xfer()
    assertEquals(
      """{"v":1,"step":"send","from":"shop_floor","to":"warehouse","note":"به انبار","lines":[{"productId":"tea","qtyMilli":2000}]}""",
      x.toString(),
    )
    assertEquals(step, StockTransfer.stepOf("k-send-1", 5, Json.parseToJsonElement(x.toString()).jsonObject))
    assertNull(StockTransfer.stepOf("k", 5, JsonObject(emptyMap())))
    //  گامِ دریافتِ ارسالِ در صف شناسهٔ سرور ندارد ⇒ با کلیدِ ارسال
    val rx = ok(StockTransfer.receive(server.copy(id = null), "k-r", mapOf("tea" to 1000), final = false, reason = "", at = 6)).xfer()
    assertEquals("""{"v":1,"step":"receive","sendKey":"k-send-1","final":false,"lines":[{"productId":"tea","qtyMilli":1000}]}""", rx.toString())
  }

  @Test fun scanCountsOneUnitAndRefusesWhatDoesNotBelong() {
    var c = emptyMap<String, Long>()
    val (c1, r1) = scan(server, c, " ۶۲۶۰۰۱ ")
    assertEquals(Scan.Counted("tea", 1000), r1); c = c1
    assertEquals(Scan.Unknown("999"), scan(server, c, "999").second)
    assertEquals(Scan.Weighed("rice"), scan(server, c, "626002").second)
    assertEquals(Scan.NotInTransfer("soap"), scan(server, c, "626009").second)
    //  بیش از ماندهٔ در مسیر شمرده نمی‌شود
    val full = mapOf("tea" to 6000L)
    val (same, over) = scan(server, full, "626001")
    assertEquals(Scan.Over("tea", 6000), over)
    assertEquals(full, same)
    assertEquals(mapOf("tea" to 6000L, "rice" to 2500L), StockTransfer.allArrived(server))
  }

  @Test fun receiveNeedsReasonForShortAndNeverOverReceives() {
    assertEquals(Problem.NOTHING, assertIs<Result.Bad>(StockTransfer.receive(server, "k", emptyMap(), false, "", 1)).problem)
    assertEquals(Problem.OVER, assertIs<Result.Bad>(StockTransfer.receive(server, "k", mapOf("tea" to 6001), false, "", 1)).problem)
    assertEquals(Problem.BAD_QTY, assertIs<Result.Bad>(StockTransfer.receive(server, "k", mapOf("soap" to 1), false, "", 1)).problem)
    //  پایانی با کسری ⇒ دلیل لازم؛ نشمرده یعنی «هیچ نرسید» و صریح فرستاده می‌شود
    assertEquals(Problem.REASON, assertIs<Result.Bad>(StockTransfer.receive(server, "k", mapOf("tea" to 6000), true, " ", 1)).problem)
    val fin = ok(StockTransfer.receive(server, "k", mapOf("tea" to 6000), true, "برنج نرسید", 1))
    assertEquals(listOf(Line("tea", 6000), Line("rice", 0)), fin.lines)
    assertEquals(listOf(Line("rice", 2500)), StockTransfer.shortages(server, mapOf("tea" to 6000)))
    //  بسته یا گامِ پایانیِ در صف ⇒ نه دریافت، نه رد
    assertEquals(Problem.CLOSED, assertIs<Result.Bad>(StockTransfer.receive(server.copy(status = Status.RECEIVED), "k", mapOf("tea" to 1), false, "", 1)).problem)
    assertEquals(Problem.CLOSED, assertIs<Result.Bad>(StockTransfer.reject(server, "k", "اشتباه", 1, queuedFinal = true)).problem)
    assertEquals(Problem.REASON, assertIs<Result.Bad>(StockTransfer.reject(server, "k", "", 1)).problem)
  }

  @Test fun projectionShowsQueuedStepsOnceAndRefusalsLoudly() {
    val queuedSend = ok(StockTransfer.send(StockTransfer.Draft("k-send-2", lines = listOf(Line("tea", 3000))), at = 20))
    val part = ok(StockTransfer.receive(server, "k-part", mapOf("tea" to 4000), false, "", 21))
    val onQueued = ok(StockTransfer.receive(server.copy(id = null, sendKey = "k-send-2", lines = listOf(TLine("tea", 3000, 0, 3000, 0))), "k-r2", mapOf("tea" to 1000), true, "یکی شکست… نه، دو تا", 22))
    val refusedSend = ok(StockTransfer.send(StockTransfer.Draft("k-send-3", lines = listOf(Line("rice", 9000))), at = 23))
    val steps = listOf(queuedSend, part, onQueued, refusedSend)

    val known = mapOf("k-send-3" to Outcome("k-send-3", false, "insufficient_stock", "موجودی کافی نیست", ""))
    val v = StockTransfer.project(listOf(server), steps, known)
    val byKey = v.transfers.associateBy { it.sendKey }
    //  ناقصِ در صف روی نمای سرور
    val s1 = byKey.getValue("k-send-1")
    assertEquals(Status.PARTIAL, s1.status); assertTrue(s1.queued)
    assertEquals(listOf(4000L, 0L), s1.lines.map { it.receivedMilli }); assertEquals(4500, s1.pendingMilli)
    //  ارسالِ در صف و دریافتِ پایانی‌اش با کسری
    val s2 = byKey.getValue("k-send-2")
    assertNull(s2.id); assertEquals(Status.RECEIVED, s2.status); assertEquals(2000, s2.shortMilli)
    //  ردشده دیده می‌شود، نه گم
    assertEquals(Status.REFUSED, byKey.getValue("k-send-3").status)
    assertEquals(listOf("k-send-3"), v.refusals.map { it.step.key })
    assertEquals(listOf("k-r2", "k-part", "k-send-2"), StockTransfer.unresolved(steps, known))
    assertTrue(StockTransfer.queuedFinal(s2.copy(status = Status.IN_TRANSIT), steps, known))
    assertFalse(StockTransfer.queuedFinal(server, steps, known))

    //  سرور هر دو را پذیرفت و نمایش تازه دارد ⇒ اثرِ ناقص دوباره اعمال نمی‌شود
    val fresh = server.copy(status = Status.PARTIAL, lines = listOf(TLine("tea", 6000, 4000, 2000, 0), TLine("rice", 2500, 0, 2500, 0)))
    val all = known + ("k-part" to Outcome("k-part", true, "", "", "xfr_1"))
    val again = StockTransfer.project(listOf(fresh), listOf(part), all).transfers.single()
    assertEquals(fresh, again)
  }

  @Test fun serverListParses() {
    val body = Json.parseToJsonElement(
      """{"transfers":[{"id":"xfr_1","sendKey":"k-send-1","from":"shop_floor","to":"warehouse","status":"partial","note":"",
        "reason":"","createdAt":10,"lines":[{"productId":"tea","sentMilli":6000,"receivedMilli":4000,"pendingMilli":2000,"shortMilli":0},
        {"productId":"rice","sentMilli":2500,"receivedMilli":null,"pendingMilli":2500,"shortMilli":0}]},{"id":"x","status":"lost"}],
        "steps":[{"key":"k-part","outcome":"applied","code":"","message":"","transferId":"xfr_1"}]}""",
    ).jsonObject
    val (ts, os) = StockTransfer.listOf(body)
    assertEquals(1, ts.size)
    assertEquals(listOf(TLine("tea", 6000, 4000, 2000, 0), TLine("rice", 2500, 0, 2500, 0)), ts[0].lines)
    assertTrue(os.getValue("k-part").applied)
    //  نگه‌داشتنِ نمای سرور برای بی‌اینترنت همان را برمی‌گرداند
    val cached = Json.parseToJsonElement("""{"transfers":${StockTransfer.transfersJson(ts)},"steps":${StockTransfer.outcomesJson(os)}}""").jsonObject
    assertEquals(ts to os, StockTransfer.listOf(cached))
  }
}
