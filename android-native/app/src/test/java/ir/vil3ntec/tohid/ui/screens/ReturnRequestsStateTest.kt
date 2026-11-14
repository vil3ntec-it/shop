package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderReturnRequests
import ir.vil3ntec.tohid.data.repo.ReturnRequestsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  RET-11 — حالِ «درخواست‌های برگشت» ِ کارمند: پنهان تا سرور فهرست نداده (۴۰۳ ِ پرچمِ خاموش)، پذیرفتن فقط از «در انتظار»
 *  و سپس برگهٔ برگشت، رد فقط با دلیلِ درست، «ثبت شد» فقط با سندِ همین درخواست، و ۴۰۹ ⇒ فهرست از نو.
 */
class ReturnRequestsStateTest {

  private val rid = "01ARZ3NDEKTSV4RRFFQ69G5FAV"

  private fun req(status: String = "pending") = OrderReturnRequests.Request(
    id = rid, orderId = "01ORDER", orderNo = 12, status = status, reason = "damaged", text = "", method = "drop_off",
    lines = listOf(OrderReturnRequests.Line(1, "p1", "برنج", "کیلو", 1000, 2500)), photos = emptyList(), decisionNote = null,
    createdAt = 0, decidedAt = null, completedAt = null, returnDocId = null,
  )

  private class Fake : ReturnRequestsSource {
    var onList: (String) -> ApiResult<Pair<List<OrderReturnRequests.Request>, Int>> = { ApiResult.Success(emptyList<OrderReturnRequests.Request>() to 0) }
    var onAccept: (String) -> ApiResult<OrderReturnRequests.Request> = { ApiResult.Failure(ApiFailure.InvalidResponse()) }
    var onReject: (String, String) -> ApiResult<OrderReturnRequests.Request> = { _, _ -> ApiResult.Failure(ApiFailure.InvalidResponse()) }
    var onComplete: (String, String) -> ApiResult<OrderReturnRequests.Request> = { _, _ -> ApiResult.Failure(ApiFailure.InvalidResponse()) }
    val calls = mutableListOf<String>()
    override suspend fun list(filter: String) = onList(filter).also { calls += "list?$filter" }
    override suspend fun accept(id: String) = onAccept(id).also { calls += "accept $id" }
    override suspend fun reject(id: String, note: String) = onReject(id, note).also { calls += "reject $id $note" }
    override suspend fun complete(id: String, docId: String) = onComplete(id, docId).also { calls += "complete $id $docId" }
    override suspend fun photo(id: String, photoId: String): ApiResult<ByteArray> = ApiResult.Success(ByteArray(0))
  }

  private fun ctl(f: Fake) = ReturnRequestsController(f, CoroutineScope(Dispatchers.Unconfined))

  @Test
  fun `پرچمِ خاموش ⇒ پنهان و هیچ نوشتنی`() = runBlocking {
    val f = Fake().apply { onList = { ApiResult.Failure(ApiFailure.Forbidden("خاموش", OrderReturnRequests.OFF)) } }
    val c = ctl(f)
    c.refresh()
    assertFalse(c.board.visible)
    assertNull(c.listError)
  }

  @Test
  fun `پذیرفتن فقط از در انتظار و سپس برگهٔ برگشت با همان درخواست`() = runBlocking {
    val f = Fake().apply {
      onList = { ApiResult.Success(listOf(req()) to 1) }
      onAccept = { ApiResult.Success(req("accepted")) }
    }
    val c = ctl(f)
    c.refresh()
    assertTrue(c.board.visible)
    assertEquals(1, c.board.waiting)
    var opened: OrderReturnRequests.Request? = null
    c.accept(req()) { opened = it }?.join()
    assertEquals("accepted", opened?.status)
    assertEquals(0, c.board.waiting)
    assertEquals(true, c.note?.first)
    assertNull("پذیرفته دوباره پذیرفته نمی‌شود", c.accept(req("accepted")) { opened = null })
    assertEquals(listOf("list?pending", "accept $rid"), f.calls)
  }

  @Test
  fun `رد فقط با دلیلِ درست و ثبت‌شد فقط با سندِ همین درخواست`() = runBlocking {
    val f = Fake().apply {
      onReject = { _, _ -> ApiResult.Success(req("rejected")) }
      onComplete = { _, _ -> ApiResult.Success(req("completed")) }
    }
    val c = ctl(f)
    assertNull(c.reject(req(), " "))
    c.reject(req(), "کالا باز شده")?.join()
    assertNull("ثبت‌شد پیش از پذیرفتن نه", c.complete(req(), req().docId))
    assertNull("سندِ دیگر نه", c.complete(req("accepted"), "o-01ORDER-rq-01ARZ3NDEKTSV4RRFFQ69G5FAW"))
    c.complete(req("accepted"), "o-01ORDER-rq-$rid")?.join()
    assertEquals(listOf("reject $rid کالا باز شده", "complete $rid o-01ORDER-rq-$rid"), f.calls)
  }

  @Test
  fun `۴۰۹ ⇒ پیامِ سرور و فهرست از نو`() = runBlocking {
    val f = Fake().apply { onAccept = { ApiResult.Failure(ApiFailure.Conflict("بسته شده", "request_closed")) } }
    val c = ctl(f)
    c.accept(req()) { }?.join()
    assertEquals(false, c.note?.first)
    assertEquals(listOf("accept $rid", "list?pending"), f.calls)
  }
}
