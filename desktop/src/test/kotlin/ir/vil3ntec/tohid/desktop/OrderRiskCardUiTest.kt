package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrRisk
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderRiskCardV3
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderRiskQueueCardV3
import ir.vil3ntec.tohid.ui.v3.online.orders.rememberRiskQueue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  RSK-03 و RSK-04 «دیدن روی دستگاه» — همان کارت‌های برنامه (`OrderRiskCardV3`، `OrderRiskQueueCardV3`) روی صفحهٔ واقعیِ
 *  Compose ِ کامپیوتر، با `OrdersRepository` ⇐ `ApiClient` ⇐ `HttpEngine` ِ واقعی و یک سرورِ HTTP ِ کوچک که همان شکلِ پاسخِ
 *  `GET /orders/:id/risk`، `GET /orders/risk/queue` و `POST /orders/risk/lists` ِ سرور را می‌دهد (همان بردارِ `OrderRiskTest`):
 *   • کارت پیش از ارسال: سابقهٔ همین شماره و هر هشدار، و هیچ‌جا خودِ شماره؛ بی دسترسیِ فهرست ⇒ بی دکمه؛
 *   • یک ۵۰۳ ِ گذرا را `HttpEngine` خودش دوباره می‌پرسد (کارت می‌آید)؛ سرورِ ازدست‌رفته ⇒ «خوانده نشد» با «دوباره» (نه
 *     «بی سابقه»)؛ «دوباره» ⇒ همان کارت؛ پرچمِ خاموش ⇒ هیچ چیز؛
 *   • RSK-04: «بستنِ این شماره» ⇒ برگه؛ «ذخیره» بی دلیل ⇒ خطای دلیل و هیچ درخواستی؛ با دلیل ⇒ یک POST با همان شماره، دلیل و
 *     انقضای پیش‌فرضِ ۳۰ روز، برگه بسته و کارت دوباره خوانده؛
 *   • کارتِ صف: فقط سفارشِ دارای هشدار، با متنِ هشدار؛ لمس ⇒ همان سفارش.
 *  منطق و کدِ کارت دست نخورد — فقط آزمون.
 */
class OrderRiskCardUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var server: TestHttpServer
  private val riskPath = "/api/v1/shop/orders/o1/risk"
  private val listsPath = "/api/v1/shop/orders/risk/lists"
  private val queuePath = "/api/v1/shop/orders/risk/queue"
  private val phone = "+93700111222"

  private val view = """{"enabled":true,"policy":{"newCustomerMaxTotalMinor":1000000},"known":true,
    "history":{"orders":6,"completed":3,"open":2,"rejectedByShop":0,"cancelledByCustomer":2,"cancelledByShop":1,"noShow":1,
      "deliveryFailed":1,"returnedToShop":0,"returnsOrRefunds":1,"firstOrderAt":100,"lastOrderAt":900},
    "flags":[{"code":"returns_before","severity":"info","count":1},{"code":"delivery_failed_before","severity":"warn","count":1},
      {"code":"no_show_before","severity":"warn","count":1}]}"""

  @Before fun start() {
    I18n.source = { Lang.FA }
    server = TestHttpServer()
  }

  @After fun stop() { server.close() }

  private fun repo(): OrdersRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = 0
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return OrdersRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  private fun show(content: @Composable () -> Unit) {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) { TohidTheme(ThemeChoice.LIGHT) { content() } }
    }
  }

  private fun count(text: String, substring: Boolean = false) =
    rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

  private fun waitFor(text: String) = rule.waitUntil(10_000) { count(text) > 0 }

  @Test fun cardShowsHistoryAndEveryWarningBeforeSendingButNeverThePhone() {
    server.on(riskPath) { 200 to view }
    show { OrderRiskCardV3("o1", repo(), canList = false, phone = phone) }
    waitFor(StrRisk.orderRiskTitle)
    rule.onNodeWithText(StrRisk.orderRiskHistory(6, 3, 2 + 1 + 0, 2)).assertExists()
    rule.onNodeWithText(StrRisk.flagDeliveryFailed(1)).assertExists()
    rule.onNodeWithText(StrRisk.flagNoShow(1)).assertExists()
    rule.onNodeWithText(StrRisk.flagReturns(1)).assertExists()
    assertEquals(0, count("700111222", substring = true))
    //  بی `orders.contact` هیچ دکمهٔ فهرستی نیست
    assertEquals(0, count(StrRisk.blockNumber))
    assertEquals(0, count(StrRisk.listsTitle))
  }

  @Test fun failedReadSaysNotLoadedAndRetryBringsTheCard() {
    //  سرور تا «دوباره» در دسترس نیست (همهٔ تلاش‌های خودکارِ `HttpEngine` هم ۵۰۳)
    var down = true
    server.on(riskPath) { if (down) 503 to """{"error":"unavailable"}""" else 200 to view }
    show { OrderRiskCardV3("o1", repo()) }
    rule.waitUntil(30_000) { count(StrRisk.orderRiskFailed) > 0 }
    assertEquals(0, count(StrRisk.orderRiskTitle))
    down = false
    rule.onNodeWithText(StrKit.retry).performClick()
    waitFor(StrRisk.orderRiskTitle)
    assertEquals(0, count(StrRisk.orderRiskFailed))
    rule.onNodeWithText(StrRisk.flagDeliveryFailed(1)).assertExists()
  }

  @Test fun oneServerBlipIsRetriedQuietlyAndTheCardStillShows() {
    var n = 0
    server.on(riskPath) { if (n++ == 0) 503 to """{"error":"unavailable"}""" else 200 to view }
    show { OrderRiskCardV3("o1", repo()) }
    waitFor(StrRisk.orderRiskTitle)
    assertEquals(0, count(StrRisk.orderRiskFailed))
    assertEquals(2, server.hits(riskPath))
  }

  @Test fun flagOffOnTheServerDrawsNothing() {
    server.on(riskPath) { 200 to """{"enabled":false}""" }
    show { OrderRiskCardV3("o1", repo(), canList = true, phone = phone) }
    rule.waitUntil(10_000) { server.hits(riskPath) >= 1 }
    rule.waitForIdle()
    assertEquals(0, count(StrRisk.orderRiskTitle))
    assertEquals(0, count(StrRisk.orderRiskFailed))
    assertEquals(0, count(StrRisk.blockNumber))
  }

  @Test fun ownerBlocksThisNumberWithReasonAndDefaultExpiryFromTheCard() {
    server.on(riskPath) { 200 to view }
    var body = ""
    server.on(listsPath) { r -> body = r.body; 201 to """{"ok":true}""" }
    show { OrderRiskCardV3("o1", repo(), canList = true, phone = phone) }
    waitFor(StrRisk.blockNumber)
    rule.onNodeWithText(StrRisk.blockNumber).performClick()
    waitFor(StrRisk.entryReason)

    //  بی دلیل ⇒ خطای همان میدان و هیچ درخواستی
    rule.onNodeWithText(StrKit.save).performClick()
    waitFor(StrRisk.entryReasonError)
    assertEquals(0, server.hits(listsPath))

    //  شماره از همان سفارش است ⇒ فقط دو میدان: دلیل و روزهای انقضا (به همین ترتیب)
    val fields = rule.onAllNodes(hasSetTextAction())
    assertEquals(2, fields.fetchSemanticsNodes().size)
    fields[0].performTextInput("سه بار سرِ تحویل نبود")
    rule.onNodeWithText(StrKit.save).performClick()
    rule.waitUntil(10_000) { server.hits(listsPath) == 1 }
    val sent = Json.parseToJsonElement(body).jsonObject
    assertEquals("block", sent["list"]!!.jsonPrimitive.content)
    assertEquals("phone", sent["kind"]!!.jsonPrimitive.content)
    assertEquals(phone, sent["phone"]!!.jsonPrimitive.content)
    assertEquals("سه بار سرِ تحویل نبود", sent["reason"]!!.jsonPrimitive.content)
    assertEquals(30, sent["expiresInDays"]!!.jsonPrimitive.int)
    //  برگه بسته و کارت دوباره خوانده شد
    rule.waitUntil(10_000) { server.hits(riskPath) >= 2 && count(StrRisk.entryReason) == 0 }
    assertEquals(1, server.hits(listsPath))
  }

  @Test fun queueCardListsOnlyWarnedOrdersAndTapOpensThatOrder() {
    server.on(queuePath) {
      200 to """{"enabled":true,"items":[
        {"orderId":"o2","orderNo":42,"status":"new","createdAt":9,"flags":[{"code":"new_customer","severity":"info","count":0},
          {"code":"delivery_failed_before","severity":"warn","count":1}]},
        {"orderId":"o3","orderNo":43,"status":"accepted","flags":[{"code":"new_customer","severity":"info","count":0}]}],
        "scanned":2,"limit":20,"truncated":false}"""
    }
    val opened = mutableListOf<String>()
    val r = repo()
    show {
      val st = rememberRiskQueue(true, r, 0L, null)
      OrderRiskQueueCardV3(st, true) { opened += it }
    }
    waitFor(StrRisk.queueTitle(1))
    rule.onNodeWithText(StrOnline.orderTitle(42L)).assertExists()
    assertEquals(0, count(StrOnline.orderTitle(43L)))
    assertEquals(1, count(StrRisk.flagDeliveryFailed(1), substring = true))
    rule.onNodeWithText(StrOnline.orderTitle(42L)).performClick()
    rule.waitForIdle()
    assertEquals(listOf("o2"), opened)
  }
}
