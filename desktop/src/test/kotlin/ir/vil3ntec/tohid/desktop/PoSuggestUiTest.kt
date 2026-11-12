package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.core.purchase.DemandForecast
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.PurchaseSuggest
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.data.repo.PurchaseOrderRepository
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.PoSuggest
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 *  INV-13 — صفحهٔ «پیشنهادِ سفارش» (`PoSuggest`، گوشی و کامپیوتر) در برابرِ یک سرورِ واقعیِ کوچک: هر کالای نیازمند با دلیل و نشانِ
 *  اطمینانِ همان `DemandForecast` دیده می‌شود؛ «همه» کالای بی‌نیاز را هم نشان می‌دهد؛ «ساختِ پیش‌نویس» فقط پیش‌نویس می‌سازد (هرگز
 *  «فرستادن») و دو بار زدن همان کلیدِ یکتا را می‌فرستد؛ بی مجوزِ مدیریت دکمه‌ای نیست.
 */
@OptIn(ExperimentalTestApi::class)
class PoSuggestUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var server: TestHttpServer
  private val posts = Collections.synchronizedList(ArrayList<TestHttpServer.Request>())

  @Before fun start() {
    server = TestHttpServer()
    server.on("/api/v1/shop/purchase-orders") { r ->
      if (r.method == "POST") { posts += r; 201 to """{"order":{"id":"po9","no":9,"supplierId":"s1","state":"draft","status":"draft"}}""" }
      else 200 to """{"orders":[],"next":null}"""
    }
  }

  @After fun stop() = server.close()

  private val today = todayIso()
  private val t = CivilDays.jdnOfIso(today)!!
  private val d = ShopData(
    products = listOf(
      Product(id = "pA", name = "چای", unit = "عدد", purchasePrice = 100.0, supplierId = "s1"),
      Product(id = "pE", name = "نمک", unit = "عدد", purchasePrice = 10.0, supplierId = "s1"),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "یک")),
    sales = (0 until 30).map { k -> Sale(id = "s$k", date = CivilDays.isoOf(t - k), status = "completed") },
    saleItems = (0 until 30).map { k -> SaleItem(id = "i$k", saleId = "s$k", productId = "pA", quantity = 2.0) } +
      SaleItem(id = "ie", saleId = "s0", productId = "pE", quantity = 1.0),
    //  چای: ۶۰ ورودی − ۶۰ فروش = صفر ⇒ نیازمند؛ نمک: ۵۰۰ ورودی ⇒ سال‌ها بس است
    warehouseEntries = listOf(WarehouseEntry(id = "wA", productId = "pA", units = 60.0), WarehouseEntry(id = "wE", productId = "pE", units = 500.0)),
  )

  private val toasts = ArrayList<String>()
  private val created = ArrayList<PurchaseOrderDto>()

  private fun show(manage: Boolean) {
    I18n.source = { Lang.FA }
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = 0
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    val repo = PurchaseOrderRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          Column { PoSuggest(d, repo, PoPermsDto(manage = manage, receive = true), { toasts += it }, { created += it }) }
        }
      }
    }
    rule.waitUntil(10_000) { rule.onAllNodesWithText("چای").fetchSemanticsNodes().isNotEmpty() }
  }

  @Test fun dueItemShowsReasonAndConfidenceAndOneKeyMakesOnlyADraftWithOneIdempotencyKey() {
    show(manage = true)
    val row = PurchaseSuggest.rows(d, emptyList(), today).first { it.product.id == "pA" }
    assertTrue(row.due)
    rule.onNodeWithText(DemandForecast.why(row.forecast, LocaleFormat.DIGITS_LATN)).assertExists()
    rule.onNodeWithText(DemandForecast.confidenceText(row.forecast.confidence)).assertExists()
    //  نمک بی‌نیاز است ⇒ در «نیازمندها» نیست
    assertTrue(rule.onAllNodesWithText("نمک").fetchSemanticsNodes().isEmpty())
    val button = rule.onNodeWithText(StrPurchasePages.createDraftN(1))
    button.performClick()
    rule.waitUntil(10_000) { created.isNotEmpty() }
    button.performClick()
    rule.waitUntil(10_000) { created.size == 2 }
    assertEquals(listOf("po9", "po9"), created.map { it.id })
    assertEquals(StrPurchasePages.draftCreatedNotSent(9), toasts.first())
    //  دو زدن ⇒ همان یک کلید (سرور دومی را تکرار می‌شناسد)، فقط پیش‌نویس؛ هیچ «فرستادن»
    assertEquals(2, posts.size)
    assertEquals(1, posts.map { it.headers["idempotency-key"] }.toSet().size)
    assertTrue(posts.all { it.path == "/api/v1/shop/purchase-orders" })
    val lines = Json.parseToJsonElement(posts.first().body).jsonObject
    assertEquals("s1", lines["supplierId"]!!.jsonPrimitive.content)
    assertEquals(0, server.hits("/api/v1/shop/purchase-orders/po9/send"))
  }

  @Test fun allShowsItemsThatDoNotNeedOrderingAndNoManageMeansNoButton() {
    show(manage = false)
    assertTrue(rule.onAllNodes(hasText(StrPurchasePages.createDraftN(1))).fetchSemanticsNodes().isEmpty())
    rule.onNodeWithText(StrPurchasePages.allDaysLeft).performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithText("نمک").fetchSemanticsNodes().isNotEmpty() }
    assertTrue(posts.isEmpty())
  }
}
