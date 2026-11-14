package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.hasSetTextAction
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrReturnRequests
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderReturnRequests
import ir.vil3ntec.tohid.data.repo.ReturnRequestsSource
import ir.vil3ntec.tohid.ui.screens.ReturnRequestsController
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.online.orders.ReturnRequestCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  RET-11 — کارتِ «درخواستِ برگشت» روی صفحهٔ واقعیِ Compose ِ کامپیوتر (همان کدِ اندروید): در انتظار ⇒ «پذیرفتن و ثبتِ
 *  برگشت» برگهٔ برگشت را با همان درخواست باز می‌کند؛ «رد با دلیل» تا دلیلِ درست بسته؛ سندِ نشسته ⇒ فقط خبرِ «ثبت شد»؛
 *  بی اجازهٔ نوشتن ⇒ هیچ دکمه‌ای.
 */
class ReturnRequestCardUiTest {

  @get:Rule val rule = createComposeRule()

  private val rid = "01ARZ3NDEKTSV4RRFFQ69G5FAV"

  private fun req(status: String) = OrderReturnRequests.Request(
    id = rid, orderId = "01ORDER", orderNo = 12, status = status, reason = "damaged", text = "کیسه پاره بود", method = "courier",
    lines = listOf(OrderReturnRequests.Line(1, "p1", "برنج", "کیلو", 1000, 2500)), photos = emptyList(), decisionNote = null,
    createdAt = 1_800_000_000_000L, decidedAt = null, completedAt = null, returnDocId = null,
  )

  private class Fake : ReturnRequestsSource {
    val calls = ArrayList<String>()
    override suspend fun list(filter: String): ApiResult<Pair<List<OrderReturnRequests.Request>, Int>> = ApiResult.Success(emptyList<OrderReturnRequests.Request>() to 0)
    override suspend fun accept(id: String): ApiResult<OrderReturnRequests.Request> = error("پذیرفتن از برگه است، نه کارت")
    override suspend fun reject(id: String, note: String) = ApiResult.Success(OrderReturnRequests.Request(
      id, "01ORDER", 12, "rejected", "damaged", "", "drop_off", listOf(OrderReturnRequests.Line(1, "", "", "", 1, 1)), emptyList(), note, 0, 1, null, null,
    )).also { calls += "reject $id $note" }
    override suspend fun complete(id: String, docId: String) = ApiResult.Success(OrderReturnRequests.Request(
      id, "01ORDER", 12, "completed", "damaged", "", "drop_off", listOf(OrderReturnRequests.Line(1, "", "", "", 1, 1)), emptyList(), null, 0, 1, 2, docId,
    )).also { calls += "complete $id $docId" }
    override suspend fun photo(id: String, photoId: String): ApiResult<ByteArray> = ApiResult.Success(ByteArray(0))
  }

  @Before fun lang() { I18n.source = { Lang.FA } }

  private fun show(ctl: ReturnRequestsController, r: OrderReturnRequests.Request, manage: Boolean, posted: Boolean, onPost: (OrderReturnRequests.Request) -> Unit) =
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) { TohidTheme(ThemeChoice.LIGHT) { ReturnRequestCard(ctl, r, manage, posted, onPost) } }
    }

  private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

  @Test fun `در انتظار ⇒ پذیرفتن همان درخواست را به برگهٔ برگشت می‌دهد و رد تا دلیلِ درست بسته است`() {
    val fake = Fake()
    val ctl = ReturnRequestsController(fake, CoroutineScope(Dispatchers.Unconfined))
    val opened = ArrayList<String>()
    show(ctl, req("pending"), manage = true, posted = false) { opened += it.id }
    assertEquals(1, count(StrReturnRequests.order(12)))
    assertEquals(1, count(StrReturnRequests.status("pending")))
    assertEquals(1, count(StrReturnRequests.method("courier")))
    rule.onNodeWithText(StrReturnRequests.accept).performClick()
    assertEquals(listOf(rid), opened)
    rule.onNodeWithText(StrReturnRequests.reject).performClick()
    rule.onNodeWithText(StrReturnRequests.rejectSend).assertIsNotEnabled()
    rule.onAllNodes(hasSetTextAction())[0].performTextInput("کالا باز شده است")
    rule.onNodeWithText(StrReturnRequests.rejectSend).performClick()
    rule.waitForIdle()
    assertEquals(listOf("reject $rid کالا باز شده است"), fake.calls)
  }

  @Test fun `سندِ نشسته ⇒ فقط خبرِ ثبت شد با همان سند`() {
    val fake = Fake()
    val ctl = ReturnRequestsController(fake, CoroutineScope(Dispatchers.Unconfined))
    show(ctl, req("accepted"), manage = true, posted = true) { error("برگهٔ دوم باز نشود") }
    assertEquals(0, count(StrReturnRequests.post))
    assertEquals(0, count(StrReturnRequests.reject))
    rule.onNodeWithText(StrReturnRequests.report).performClick()
    rule.waitForIdle()
    assertEquals(listOf("complete $rid o-01ORDER-rq-$rid"), fake.calls)
  }

  @Test fun `بی اجازهٔ نوشتن هیچ دکمه‌ای نیست`() {
    val ctl = ReturnRequestsController(Fake(), CoroutineScope(Dispatchers.Unconfined))
    show(ctl, req("pending"), manage = false, posted = false) { error("نه") }
    assertEquals(0, count(StrReturnRequests.accept) + count(StrReturnRequests.reject))
    assertEquals(1, count(StrReturnRequests.reason("damaged")))
  }
}
