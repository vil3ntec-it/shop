package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontPermsDto
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.online.storefront.ProductStorefront
import ir.vil3ntec.tohid.ui.v3.online.storefront.ProductStorefrontSection
import ir.vil3ntec.tohid.ui.v3.online.storefront.ProductStorefrontState
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontHub
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 *  INV-01@32 «تعریف و انتشار از یک صفحه» — بخشِ «ویترینِ اینترنتی» ِ صفحهٔ کالا (`products/{id}`) با `ShellContext` ِ واقعی
 *  ([RouteUiHarness]) و سرورِ ساختگی:
 *   • کالای تعریف‌شده‌ای که هنوز همگام نشده (۴۰۴ `product_not_found`) ⇒ «هنوز به سرور نرسیده»، کلید خاموش و **هیچ** درخواستِ
 *     انتشاری (همان ۴۰۴ ِ پیشین دیگر به کاربر نمی‌رسد)؛
 *   • پس از رسیدن ⇒ همان صفحه کلید را روشن می‌کند و انتشار همان بدنهٔ سرور را می‌فرستد؛ «در ویترین گذاشته شد» فقط پس از ۲۰۰؛
 *   • پایانِ همگام‌سازیِ موفق (`recheck`) فقط کالای نرسیده را دوباره می‌پرسد؛ خطای انتشار وضع را عوض نمی‌کند و «ثبت شد» نمی‌گوید؛
 *   • ویترینِ خاموش یا بی اجازه ⇒ نه بخش، نه درخواست.
 */
@OptIn(ExperimentalTestApi::class)
class ProductStorefrontUiTest {

  @get:Rule val rule = createComposeRule()

  private val data = MoneyDemo.full()
  private val product = data.products.first { it.id == "p-rice" }
  private val path = "/api/v1/shop/storefront/products/${product.id}"
  private val puts = CopyOnWriteArrayList<String>()
  @Volatile private var synced = false
  @Volatile private var putStatus = 200

  private fun dto(published: Boolean) = """{"id":"${product.id}","name":"${product.name}","unit":"${product.unit}","priceMinor":110000,""" +
    """"published":$published,"onlinePriceMinor":null,"maxQtyMilli":null,"active":true,"description":"","specs":[]}"""

  private val notFound = 404 to """{"error":{"code":"product_not_found","message":"این کالا در دفترِ دکان نیست"}}"""

  private fun serve(server: TestHttpServer) = server.on(path) { r ->
    when {
      r.method == "PUT" -> { puts += r.body; if (putStatus == 200) 200 to r.body else if (putStatus == 404) notFound else putStatus to """{"error":{"code":"server_fault","message":"سرور خطا داد"}}""" }
      synced -> 200 to dto(published = false)
      else -> notFound
    }
  }

  @Before fun up() {
    I18n.source = { Lang.FA }
    StorefrontGate.config.value = StorefrontConfigDto(flag = true, storefrontFlag = true, enabled = true, permissions = StorefrontPermsDto(manage = true))
    StorefrontHub.products.value = null
  }

  @After fun down() {
    StorefrontGate.config.value = null
    StorefrontHub.products.value = null
  }

  private fun switch() = rule.onNode(isToggleable() and hasText(StrOnline.sfInStore))

  @Test fun notSyncedProductKeepsTheSwitchOffAndSendsNothingThenPublishesFromTheSamePageOnceSynced() {
    RouteUiHarness(rule).use { h ->
      serve(h.server)
      StorefrontHub.products.value = emptyList()   //  فهرستِ ویترین پیش از همگام شدنِ کالا بار شده بود
      h.show("products/${product.id}", data)
      h.await(StrProducts.storefrontNotSyncedBody)
      switch().assertIsNotEnabled().assertIsOff()
      assertTrue(puts.isEmpty())

      //  رسید ⇒ «همین حالا همگام کن» یک پرسشِ بی‌درنگ هم می‌کند
      synced = true
      h.click(StrAccount.syncNow)
      h.await(StrOnline.ledgerPrice(OnlineFmt.afn(110000)))
      assertFalse(h.has(StrProducts.storefrontNotSyncedBody))
      switch().assertIsEnabled().assertIsOff()
      assertEquals("کالای تازه‌رسیده به فهرستِ بارشدهٔ ویترین افزوده شد (برگهٔ «جزئیات» برنمی‌گردد)",
        listOf(product.id), StorefrontHub.products.value!!.map { it.id })

      h.click(StrOnline.sfInStore)
      h.await(StrProducts.storefrontPublished)
      switch().assertIsOn()
      assertEquals(1, puts.size)
      val body = Json.parseToJsonElement(puts.single()).jsonObject
      assertTrue(body["published"]!!.jsonPrimitive.boolean)
      assertEquals("null", body["onlinePriceMinor"].toString())
      assertFalse("توضیح و مشخصات دست نمی‌خورند", "description" in body || "specs" in body)
      assertTrue(StorefrontHub.products.value!!.single().published)
    }
  }

  @Test fun aFailedPublishKeepsTheStateAndNeverSaysSaved() {
    RouteUiHarness(rule).use { h ->
      synced = true
      putStatus = 500
      serve(h.server)
      h.show("products/${product.id}", data)
      h.await(StrOnline.ledgerPrice(OnlineFmt.afn(110000)))
      h.click(StrOnline.sfInStore)
      h.await("سرور خطا داد", substring = true)
      switch().assertIsOff().assertIsEnabled()
      assertFalse(h.has(StrProducts.storefrontPublished))
      assertTrue(puts.isNotEmpty())
    }
  }

  @Test fun publishRacingADeleteFallsBackToNotSynced() {
    RouteUiHarness(rule).use { h ->
      synced = true
      putStatus = 404
      serve(h.server)
      h.show("products/${product.id}", data)
      h.await(StrOnline.ledgerPrice(OnlineFmt.afn(110000)))
      h.click(StrOnline.sfInStore)
      h.await(StrProducts.storefrontNotSyncedBody)
      switch().assertIsNotEnabled()
      assertFalse(h.has(StrProducts.storefrontPublished))
    }
  }

  @Test fun aSuccessfulSyncRechecksOnlyAProductThatHadNotArrived() {
    TestBackend().use { b ->
      serve(b.server)
      var recheck by mutableLongStateOf(0L)
      rule.setContent {
        CompositionLocalProvider(LocalContext provides DesktopContext) {
          TohidTheme(ThemeChoice.LIGHT) { ProductStorefrontSection(product.id, recheck, onSyncNow = {}, onDetails = {}, toast = {}) }
        }
      }
      rule.waitUntil(15_000) { rule.onAllNodes(hasText(StrProducts.storefrontNotSyncedBody)).fetchSemanticsNodes().isNotEmpty() }
      assertEquals(1, b.server.hits(path))
      recheck = 1L   //  همگام‌سازیِ موفق، هنوز نرسیده
      rule.waitUntil(15_000) { b.server.hits(path) == 2 }
      //  پاسخِ دوم (هنوز نرسیده) پیش از همگام‌سازیِ بعدی بنشیند — دو دورِ هم‌پوشان آزمونِ دیگری است
      Thread.sleep(500)
      rule.waitForIdle()
      synced = true
      recheck = 2L
      try {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText(StrOnline.ledgerPrice(OnlineFmt.afn(110000)))).fetchSemanticsNodes().isNotEmpty() }
      } catch (e: Throwable) {
        System.err.println("DEBUG hits=" + b.server.hits(path) + "\n" + rule.onRoot().printToString())
        throw e
      }
      assertEquals(3, b.server.hits(path))
      recheck = 3L   //  خوانده‌شده با هر همگام‌سازی دوباره خوانده نمی‌شود
      rule.waitForIdle()
      Thread.sleep(300)
      rule.waitForIdle()
      assertEquals(3, b.server.hits(path))
    }
  }

  @Test fun hiddenWithoutAStorefrontOrWithoutPermissionAndAsksNothing() {
    StorefrontGate.config.value = StorefrontConfigDto(flag = true, permissions = StorefrontPermsDto(manage = false))
    RouteUiHarness(rule).use { h ->
      serve(h.server)
      h.show("products/${product.id}", data)
      h.await(product.name)
      rule.waitForIdle()
      assertFalse(h.has(StrOnline.sfInStore))
      assertEquals(0, h.server.hits(path))
    }
  }

  @Test fun pureModelMapsTheServerAnswers() {
    val p = Json { ignoreUnknownKeys = true }.decodeFromString(StorefrontProductDto.serializer(), dto(published = false))
    assertEquals(ProductStorefrontState.Ready(p), ProductStorefront.of(ApiResult.Success(p)))
    assertEquals(ProductStorefrontState.NotSynced, ProductStorefront.of(ApiResult.Failure(ApiFailure.NotFound("x", "product_not_found"))))
    //  ۴۰۴ ِ دیگری (مسیرِ ناشناخته روی سرورِ کهنه) «نرسیده» نیست
    assertTrue(ProductStorefront.of(ApiResult.Failure(ApiFailure.NotFound("x", "not_found"))) is ProductStorefrontState.Failed)
    assertTrue(ProductStorefront.of(ApiResult.Failure(ApiFailure.Offline())) is ProductStorefrontState.Failed)
    assertTrue(ProductStorefront.shouldLoad(ProductStorefrontState.NotSynced))
    assertFalse(ProductStorefront.shouldLoad(ProductStorefrontState.Ready(p)))
    assertFalse(ProductStorefront.canToggle(ProductStorefrontState.NotSynced, busy = false))
    assertFalse(ProductStorefront.canToggle(ProductStorefrontState.Ready(p), busy = true))
    assertEquals(ProductStorefrontState.Ready(p.copy(published = true)) to null, ProductStorefront.afterSave(p, true, null))
    assertEquals(ProductStorefrontState.NotSynced to null, ProductStorefront.afterSave(p, true, ApiFailure.NotFound("x", "product_not_found")))
    val (kept, msg) = ProductStorefront.afterSave(p, true, ApiFailure.Forbidden("اشتراک لازم است", "subscription_required"))
    assertEquals(ProductStorefrontState.Ready(p), kept)
    assertEquals("اشتراک لازم است", msg)
    assertEquals(StrOnline.onlinePrice(OnlineFmt.afn(9000)), ProductStorefront.subtitle(p.copy(onlinePriceMinor = 9000)))
    assertEquals(StrOnline.sfInactive, ProductStorefront.subtitle(p.copy(active = false)))
    assertEquals(StrOnline.noPrice, ProductStorefront.subtitle(p.copy(priceMinor = null)))
    assertNull(StorefrontHub.products.value)
  }
}
