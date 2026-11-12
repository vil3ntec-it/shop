package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrTrust
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.data.repo.TrustRepository
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.online.storefront.TrustCasesContent
import ir.vil3ntec.tohid.ui.v3.online.storefront.TrustHub
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  ABUSE-01 — صفحهٔ «شکایت و گزارشِ خریداران» ِ برنامهٔ دکان (`TrustCasesContent`) روی سرورِ ساختگیِ 127.0.0.1 با همان
 *  شکلِ پاسخِ `routes/shop-trust.js`:
 *   • فهرست با «بی پاسخ»، دسته و «دربارهٔ کالا» با نامِ دفتر؛ برگهٔ پرونده نوشتهٔ خریدار، تصمیم و دلیل را نشان می‌دهد؛
 *   • پاسخِ خالی ⇒ خطای درجا و هیچ درخواستی؛ پاسخ ⇒ یک POST با همان متن و `clientId`؛ «فرستادن» ِ دوباره پس از ۵۰۳ همان کلید؛
 *   • اعتراض فقط برای «وارد است» در مهلت؛ کوتاه ⇒ خطا بی درخواست؛ درست ⇒ یک POST؛
 *   • بی اجازهٔ پاسخ (شاگرد) ⇒ نه کادرِ پاسخ، نه اعتراض — فقط پیام.
 */
class TrustCasesUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var server: TestHttpServer
  private val root = "/api/v1/shop/trust"
  private val now = 1_000_000_000L

  @Before fun start() {
    I18n.source = { Lang.FA }
    server = TestHttpServer()
    TrustHub.config.value = null
  }

  @After fun stop() { server.close(); TrustHub.config.value = null }

  private fun repo(): TrustRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = 0
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return TrustRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  private fun cfg(respond: Boolean) = """{"enabled":true,"appealDays":14,"permissions":{"respond":$respond}}"""
  private val list = """{"cases":[
    {"id":"tc_open","kind":"complaint","subject":"order","category":"wrong_item","status":"open","createdAt":${now - 3_600_000},"severity":"medium","evidenceCount":0},
    {"id":"tc_rep","kind":"report","subject":"product","productId":"rice","category":"counterfeit","status":"decided","outcome":"upheld","createdAt":${now - 7_200_000},"severity":"high","evidenceCount":1}]}"""
  private val openCase = """{"case":{"id":"tc_open","kind":"complaint","subject":"order","category":"wrong_item","status":"open",
    "createdAt":${now - 3_600_000},"severity":"medium","body":"روغن به‌جای برنج رسید","outcome":null,"decisionReason":"","appeal":null,
    "canAppeal":false,"appealDeadline":null,"evidenceCount":0,"sanctions":[],"history":[{"type":"filed","at":${now - 3_600_000},"actor":"customer"}]}}"""
  private val decided = """{"case":{"id":"tc_rep","kind":"report","subject":"product","productId":"rice","category":"counterfeit","status":"decided",
    "createdAt":${now - 7_200_000},"severity":"high","body":"","outcome":"upheld","decisionReason":"بسته‌بندی با کالای اصل نمی‌خواند","decidedAt":${now - 600_000},
    "appeal":null,"canAppeal":true,"appealDeadline":${now + 86_400_000},"evidenceCount":1,
    "sanctions":[{"id":"tsn_1","kind":"hide_listing","productId":"rice","reason":"x","createdAt":${now - 600_000},"active":true}],
    "history":[{"type":"filed","at":${now - 7_200_000},"actor":"customer"},{"type":"decided","at":${now - 600_000},"actor":"platform","body":"بسته‌بندی"}]}}"""

  private fun show() {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { TrustCasesContent(repo(), now = { now }, productName = { if (it == "rice") "برنجِ هندی" else null }) }
      }
    }
  }

  private fun count(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size
  private fun waitFor(text: String, substring: Boolean = false) = rule.waitUntil(10_000) { count(text, substring) > 0 }
  private fun body(r: TestHttpServer.Request) = Json.parseToJsonElement(r.body).jsonObject

  @Test fun listCaseAndReplyWithOneKeyAcrossRetry() {
    server.on("$root/config") { 200 to cfg(respond = true) }
    server.on("$root/cases") { 200 to list }
    server.on("$root/cases/tc_open") { 200 to openCase }
    val posts = mutableListOf<TestHttpServer.Request>()
    server.on("$root/cases/tc_open/responses") { r ->
      posts += r
      if (posts.size == 1) 503 to """{"error":{"code":"unavailable","message":"سرورِ آزمون لحظه‌ای در دسترس نیست"}}"""
      else 201 to openCase.replace("\"history\":[", "\"history\":[{\"type\":\"shop_response\",\"at\":$now,\"actor\":\"shop\",\"body\":\"پوزش؛ کالای درست را می‌فرستیم\"},")
    }
    show()
    waitFor(StrTrust.cmpWrongItem)
    rule.onNodeWithText(StrTrust.repCounterfeit).assertExists()
    assertTrue("نامِ کالا از دفتر", count(StrTrust.aboutProduct("برنجِ هندی"), substring = true) > 0)
    assertTrue(count(StrTrust.needsReply) == 1)

    rule.onNodeWithText(StrTrust.cmpWrongItem).performClick()
    waitFor("روغن به‌جای برنج رسید")
    rule.onNodeWithText(StrTrust.sendReply).performScrollTo().performClick()
    rule.waitForIdle()
    assertTrue(count(StrTrust.replyEmpty) > 0)
    assertEquals("پاسخِ خالی نرفت", 0, server.hits("$root/cases/tc_open/responses"))

    rule.onAllNodes(hasSetTextAction())[0].performTextInput("پوزش؛ کالای درست را می‌فرستیم")
    rule.onNodeWithText(StrTrust.sendReply).performScrollTo().performClick()
    //  ۵۰۳ (POST خودکار دوباره نمی‌رود) ⇒ پیامِ سرور و متن سرِ جایش؛ «فرستادن» ِ دوباره با همان کلید
    waitFor("سرورِ آزمون لحظه‌ای در دسترس نیست", substring = true)
    assertEquals(1, posts.size)
    rule.onNodeWithText(StrTrust.sendReply).performScrollTo().performClick()
    rule.waitUntil(10_000) { posts.size >= 2 }
    waitFor("پوزش؛ کالای درست را می‌فرستیم", substring = true)
    val keys = posts.map { body(it)["clientId"]!!.jsonPrimitive.content }
    assertEquals(2, posts.size)
    assertEquals("دو بار رفت، یک کلید", 1, keys.toSet().size)
    assertEquals("پوزش؛ کالای درست را می‌فرستیم", body(posts.last())["body"]!!.jsonPrimitive.content)
  }

  @Test fun appealOnlyForUpheldAndNeedsTenChars() {
    server.on("$root/config") { 200 to cfg(respond = true) }
    server.on("$root/cases") { 200 to list }
    server.on("$root/cases/tc_rep") { 200 to decided }
    val appeals = mutableListOf<TestHttpServer.Request>()
    server.on("$root/cases/tc_rep/appeal") { r -> appeals += r; 201 to decided.replace("\"appeal\":null,\"canAppeal\":true", "\"appeal\":{\"body\":\"x\",\"at\":$now},\"canAppeal\":false") }
    show()
    waitFor(StrTrust.repCounterfeit)
    rule.onNodeWithText(StrTrust.repCounterfeit).performClick()
    waitFor(StrTrust.reason("بسته‌بندی با کالای اصل نمی‌خواند"), substring = true)
    assertTrue(count(StrTrust.sanctionActive(StrTrust.snHide)) > 0)
    rule.onNodeWithText(StrTrust.sendAppeal).performScrollTo().performClick()
    rule.waitForIdle()
    assertTrue(count(StrTrust.appealShort) > 0)
    assertEquals(0, appeals.size)
    //  دو کادر: پاسخ، سپس دلیلِ اعتراض
    val fields = rule.onAllNodes(hasSetTextAction())
    assertEquals(2, fields.fetchSemanticsNodes().size)
    fields[1].performTextInput("کالا اصل است؛ فاکتورِ واردکننده داریم")
    rule.onNodeWithText(StrTrust.sendAppeal).performScrollTo().performClick()
    rule.waitUntil(10_000) { appeals.size == 1 }
    assertEquals("کالا اصل است؛ فاکتورِ واردکننده داریم", body(appeals.single())["body"]!!.jsonPrimitive.content)
    rule.waitUntil(10_000) { count(StrTrust.sendAppeal) == 0 }
  }

  @Test fun staffSeesCasesButNoReplyOrAppeal() {
    server.on("$root/config") { 200 to cfg(respond = false) }
    server.on("$root/cases") { 200 to list }
    server.on("$root/cases/tc_rep") { 200 to decided }
    show()
    waitFor(StrTrust.repCounterfeit)
    rule.onNodeWithText(StrTrust.repCounterfeit).performClick()
    waitFor(StrTrust.replyOnlyManagers)
    assertEquals(0, count(StrTrust.sendReply))
    assertEquals(0, count(StrTrust.sendAppeal))
  }
}
