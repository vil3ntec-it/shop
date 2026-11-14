package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.stock.BranchTransfer.Direction
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Draft
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Line
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Problem
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Result
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Status
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INV-11/S21 میانِ شعبه‌ها: شکلِ درخواست با سرآیندِ شعبه و کلید، پیش‌سنجیِ همان قاعده‌های سرور، خواندنِ پاسخ. */
class BranchTransferTest {

  private val a = "brn_" + "a".repeat(24)
  private val b = "brn_" + "b".repeat(24)
  private val id = "bxf_" + "1".repeat(24)

  /** همان شکلِ پاسخِ `server/src/lib/branch-transfers.js` (`view`) */
  private fun body(direction: String = "in", status: String = "partial") = Json.parseToJsonElement(
    """{"transfer":{"id":"$id","direction":"$direction","fromBranch":{"id":"$a","name":"الف","main":false},
      "toBranch":{"id":"$b","name":"ب","main":false},"from":"shop_floor","to":"warehouse","status":"$status",
      "lines":[{"productId":"tea","sentMilli":3000,"receivedMilli":1000,"shortMilli":0,"pendingMilli":2000},
               {"productId":"rice","sentMilli":2000,"receivedMilli":null,"shortMilli":0,"pendingMilli":2000}],
      "shortMilli":0,"pendingMilli":4000,"note":"بارِ یک","reason":"","createdBy":"usr_1","createdAt":10,"closedBy":"","closedAt":null},
      "steps":[{"kind":"send","side":"from","statusFrom":"","statusTo":"in_transit","lines":[{"productId":"tea","qtyMilli":3000}],
                "note":"بارِ یک","final":true,"by":"usr_1","at":10}]}""",
  ).jsonObject

  private fun ok(r: Result) = assertIs<Result.Ok>(r).call
  private fun bad(r: Result) = assertIs<Result.Bad>(r).problem

  @Test fun readsServerViewAndSteps() {
    val (t, steps) = BranchTransfer.detailOf(body())!!
    assertEquals(id, t.id)
    assertEquals(Direction.IN, t.direction)
    assertEquals(Status.PARTIAL, t.status)
    assertEquals(a, t.fromBranch.id)
    assertEquals("ب", t.toBranch.name)
    assertEquals(4000, t.pendingMilli)
    assertNull(t.lines[1].receivedMilli)
    assertEquals(listOf("send"), steps.map { it.kind })
    assertEquals(listOf(Line("tea", 3000)), steps[0].lines)
    //  بدشکل ⇒ null، نه انتقالِ نیمه
    assertNull(BranchTransfer.transferOf(Json.parseToJsonElement("""{"id":"x","status":"partial"}""").jsonObject))
    val mainEnd = Json.parseToJsonElement("""{"id":"","name":"شعبهٔ اصلی","main":true}""").jsonObject
    assertEquals(BranchTransfer.End("", "شعبهٔ اصلی", true), BranchTransfer.endOf(mainEnd))
    assertNull(BranchTransfer.endOf(Json.parseToJsonElement("""{"id":"brn_xyz"}""").jsonObject))
  }

  @Test fun sendCarriesBranchHeaderKeyAndDestinationOnlyInBody() {
    val key = BranchTransfer.newKey(Random(7))
    assertNotEquals(key, BranchTransfer.newKey(Random(8)))
    val d = Draft(key, toBranch = b, from = "shop_floor", to = "warehouse").with("tea", 1000).with("rice", 500).with("tea", 2000)
    assertEquals(listOf(Line("tea", 2000), Line("rice", 500)), d.lines)
    val c = ok(BranchTransfer.send(a, d))
    assertEquals("POST", c.method)
    assertEquals("/shop/catalog/branch-transfers", c.path)
    assertEquals(mapOf("X-Branch-Id" to a, "Idempotency-Key" to key), c.headers)
    assertEquals(b, c.body!!["toBranch"]!!.jsonPrimitive.content)
    assertEquals(2000L, c.body!!["lines"]!!.jsonArray[0].jsonObject["qtyMilli"]!!.jsonPrimitive.long)
    //  شعبهٔ اصلی در سرآیند `main`، در بدنه ''
    val toMain = ok(BranchTransfer.send(a, d.copy(toBranch = BranchTransfer.MAIN)))
    assertEquals("", toMain.body!!["toBranch"]!!.jsonPrimitive.content)
    assertEquals("main", ok(BranchTransfer.send(BranchTransfer.MAIN, d)).headers["X-Branch-Id"])
  }

  @Test fun sendRefusesWhatTheServerRefuses() {
    val d = Draft("k-valid-123", toBranch = b, lines = listOf(Line("tea", 1000)))
    assertEquals(Problem.NO_DESTINATION, bad(BranchTransfer.send(a, d.copy(toBranch = null))))
    assertEquals(Problem.NO_DESTINATION, bad(BranchTransfer.send(a, d.copy(toBranch = "brn_xyz"))))
    assertEquals(Problem.SAME_BRANCH, bad(BranchTransfer.send(b, d)))
    assertEquals(Problem.BAD_LOCATION, bad(BranchTransfer.send(a, d.copy(from = "in_transit"))))
    assertEquals(Problem.EMPTY, bad(BranchTransfer.send(a, d.copy(lines = emptyList()))))
    assertEquals(Problem.TOO_MANY, bad(BranchTransfer.send(a, d.copy(lines = (1..101).map { Line("p$it", 1) }))))
    assertEquals(Problem.BAD_QTY, bad(BranchTransfer.send(a, d.copy(lines = listOf(Line("tea", 0))))))
    assertEquals(Problem.DUPLICATE, bad(BranchTransfer.send(a, d.copy(lines = listOf(Line("tea", 1), Line("tea", 2))))))
    assertEquals(Problem.BAD_KEY, bad(BranchTransfer.send(a, d.copy(key = "short"))))
    assertEquals("bad_destination", Problem.NO_DESTINATION.code)
  }

  @Test fun receiveAndRejectOnlyAtDestinationWhileOpen() {
    val t = BranchTransfer.detailOf(body())!!.first
    assertTrue(BranchTransfer.canAct(t, b))
    assertFalse(BranchTransfer.canAct(t, a))
    val out = BranchTransfer.detailOf(body(direction = "out"))!!.first
    assertFalse(BranchTransfer.canAct(out, b))
    assertEquals(Problem.NOT_DESTINATION, bad(BranchTransfer.receive(a, t, "k-valid-123", emptyMap(), final = true)))
    assertEquals(Problem.CLOSED, bad(BranchTransfer.reject(b, BranchTransfer.detailOf(body(status = "received"))!!.first, "k-valid-123", "x")))
    //  ناقص: فقط همین گام؛ خالی رد؛ بیش از ماندهٔ در مسیر رد؛ کالای بیرون رد
    val part = ok(BranchTransfer.receive(b, t, "k-valid-123", mapOf("tea" to 1000), final = false))
    assertEquals("/shop/catalog/branch-transfers/$id/receive", part.path)
    assertEquals(b, part.headers["X-Branch-Id"])
    assertEquals("false", part.body!!["final"]!!.jsonPrimitive.content)
    assertEquals(Problem.NOTHING_RECEIVED, bad(BranchTransfer.receive(b, t, "k-valid-123", mapOf("tea" to 0), final = false)))
    assertEquals(Problem.OVER_RECEIVED, bad(BranchTransfer.receive(b, t, "k-valid-123", mapOf("tea" to 2001), final = false)))
    assertEquals(Problem.NOT_IN_TRANSFER, bad(BranchTransfer.receive(b, t, "k-valid-123", mapOf("soap" to 1), final = false)))
    //  پایانی: نیامده = «بقیه رسید»؛ کسری فقط با دلیل
    assertTrue(BranchTransfer.shortages(t, emptyMap()).isEmpty())
    assertEquals(listOf(Line("tea", 500)), BranchTransfer.shortages(t, mapOf("tea" to 1500)))
    assertEquals(Problem.REASON_REQUIRED, bad(BranchTransfer.receive(b, t, "k-valid-123", mapOf("tea" to 1500), final = true)))
    val fin = ok(BranchTransfer.receive(b, t, "k-valid-123", mapOf("tea" to 1500), final = true, reason = " شکسته "))
    assertEquals("شکسته", fin.body!!["reason"]!!.jsonPrimitive.content)
    ok(BranchTransfer.receive(b, t, "k-valid-123", emptyMap(), final = true))
    //  رد: دلیل اجباری
    assertEquals(Problem.REASON_REQUIRED, bad(BranchTransfer.reject(b, t, "k-valid-123", "  ")))
    assertEquals("/shop/catalog/branch-transfers/$id/reject", ok(BranchTransfer.reject(b, t, "k-valid-123", "اشتباه")).path)
  }

  @Test fun listAndDestinationsCalls() {
    assertEquals("/shop/catalog/branch-transfers?direction=in&status=open", BranchTransfer.list(b, Direction.IN, openOnly = true).path)
    assertEquals("/shop/catalog/branch-transfers", BranchTransfer.list(b).path)
    assertEquals("/shop/catalog/branch-transfers/destinations", BranchTransfer.destinations(a).path)
    assertEquals("/shop/catalog/branch-transfers/a%2Fb", BranchTransfer.detail(a, "a/b").path)
    val list = JsonObject(mapOf("transfers" to Json.parseToJsonElement("[${body()["transfer"]}, {\"id\":\"bad\"}]")))
    assertEquals(listOf(id), BranchTransfer.listOf(list).map { it.id })
    val dest = Json.parseToJsonElement(
      """{"branch":"$a","destinations":[{"id":"","name":"شعبهٔ اصلی","main":true},{"id":"$b","name":"ب","main":false},{"id":"x"}]}""",
    ).jsonObject
    assertEquals(listOf("", b), BranchTransfer.destinationsOf(dest).map { it.id })
    assertEquals("transfer_closed", BranchTransfer.errorCode(Json.parseToJsonElement("""{"error":{"code":"transfer_closed"}}""").jsonObject))
    assertNull(BranchTransfer.errorCode(dest))
  }
}
