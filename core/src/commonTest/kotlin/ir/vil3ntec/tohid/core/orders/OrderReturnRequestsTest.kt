package ir.vil3ntec.tohid.core.orders

import ir.vil3ntec.tohid.core.returns.ReturnReasons
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RET-11 — صندوقِ «درخواست‌های برگشت» ِ دکان: خواندنِ پاسخ، نشانی‌ها، سندِ ثابت، دلیلِ پیشنهادی و حالِ صفحه */
class OrderReturnRequestsTest {

  private val rid = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
  private val pid = "01ARZ3NDEKTSV4RRFFQ69G5FAW"

  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private fun row(id: String = rid, status: String = "pending", lines: String = """[{"lineNo":1,"productId":"rice","name":"برنج","unit":"کیلو","qtyMilli":2000,"boughtMilli":5000}]""") =
    """{"id":"$id","orderId":"ord1","orderNo":7,"status":"$status","reason":"wrong_item","text":"اشتباه","method":"courier",
       "lines":$lines,"photos":[{"id":"$pid","mime":"image/jpeg","width":320,"height":240},{"id":"bad"}],
       "decisionNote":"","createdAt":10,"decidedAt":null,"returnDocId":null}"""

  @Test
  fun `پاسخِ فهرست خوانده می‌شود و ردیفِ نادرست کنار می‌رود`() {
    val o = obj("""{"waiting":2,"requests":[${row()},${row(id = "x")},${row(status = "lost")},${row(lines = "[]")}]}""")
    val list = OrderReturnRequests.requests(o)
    assertEquals(1, list.size)
    val r = list[0]
    assertEquals(listOf(rid, "ord1", "pending", "wrong_item", "courier"), listOf(r.id, r.orderId, r.status, r.reason, r.method))
    assertEquals(OrderReturnRequests.Line(1, "rice", "برنج", "کیلو", 2000, 5000), r.lines.single())
    assertEquals(listOf(pid), r.photos.map { it.id })
    assertNull(r.decisionNote)
    assertTrue(r.pending && r.open)
    assertEquals(2, OrderReturnRequests.waiting(o))
  }

  @Test
  fun `سندِ برگشت همان شناسهٔ ثابتِ سرور است`() {
    assertEquals("o-ord1-rq-$rid", OrderReturnRequests.docIdOf("ord1", rid))
    assertEquals("o-ord1-rq-$rid", OrderReturnRequests.request(obj(row()))!!.docId)
  }

  @Test
  fun `نشانی فقط با شناسهٔ درست ساخته می‌شود`() {
    assertEquals("/shop/order-returns?status=pending", OrderReturnRequests.listPath("نامعلوم"))
    assertEquals("/shop/order-returns?status=all", OrderReturnRequests.listPath("all"))
    assertEquals("/shop/order-returns/$rid/accept", OrderReturnRequests.acceptPath(rid))
    assertNull(OrderReturnRequests.rejectPath("../x"))
    assertNull(OrderReturnRequests.photoPath(rid, "../../etc"))
    assertEquals("/shop/order-returns/$rid/photos/$pid", OrderReturnRequests.photoPath(rid, pid))
  }

  @Test
  fun `دلیلِ رد دو تا هزار نویسه`() {
    assertFalse(OrderReturnRequests.noteOk(" ن "))
    assertTrue(OrderReturnRequests.noteOk("کالا باز شده"))
    assertFalse(OrderReturnRequests.noteOk("ا".repeat(1001)))
  }

  @Test
  fun `دلیلِ پیشنهادی فقط وقتی در فهرستِ دکان است`() {
    assertEquals(ReturnReasons.DEFAULT[0], OrderReturnRequests.suggestedReason("damaged", ReturnReasons.DEFAULT))
    assertEquals(ReturnReasons.DEFAULT[2], OrderReturnRequests.suggestedReason("changed_mind", ReturnReasons.DEFAULT))
    assertNull(OrderReturnRequests.suggestedReason("damaged", listOf("فقط این", "دیگری")))
  }

  @Test
  fun `حالِ صفحه — پیدا شدن، پنهان شدن با ۴۰۳، و شمارِ در انتظار`() {
    var b = OrderReturnRequests.Board()
    assertFalse(b.visible)
    val r = OrderReturnRequests.request(obj(row()))!!
    b = OrderReturnRequests.loaded(b, "pending", listOf(r), 1)
    assertTrue(b.visible)
    assertEquals(1, b.waiting)
    b = OrderReturnRequests.updated(b, r.copy(status = "accepted"))
    assertEquals(0, b.waiting)
    assertTrue(b.list.isEmpty(), "پذیرفته از فهرستِ «در انتظار» بیرون می‌رود")
    assertEquals(b, OrderReturnRequests.failed(b, refused = false), "خطای گذرا ⇒ همان")
    assertFalse(OrderReturnRequests.writeRefused(b, OrderReturnRequests.OFF).visible, "پرچم میانِ کار خاموش ⇒ پنهان")
    val all = OrderReturnRequests.filter(b, "all")
    assertEquals(listOf("all", "0"), listOf(all.filter, all.list.size.toString()))
  }
}
