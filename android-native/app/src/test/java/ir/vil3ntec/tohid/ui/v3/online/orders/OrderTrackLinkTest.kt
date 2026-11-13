package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 *  TEN-03 — «باطل کردنِ لینکِ پیگیری» در جزئیاتِ سفارش (`POST /api/shop/orders/:id/track-link/revoke`، پرچمِ
 *  `shop.storefront.link_expiry`، فقط صاحب و مدیر با `orders.contact`): دکمه فقط با کلیدِ `trackLinkRevoke` ِ `/config`
 *  و فقط برای سفارشِ ویترین؛ پاسخ لینکِ تازهٔ کامل (و واتس‌اپ ِ مشتری اگر نقش شماره را می‌بیند) برای فرستادنِ دوباره.
 */
class OrderTrackLinkTest {

  private lateinit var server: TestHttpServer

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  private fun repo(): OrdersRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = Long.MAX_VALUE
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return OrdersRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  private fun cfg(json: String): OrdersConfigDto = ApiJson.decode(Json.parseToJsonElement(json).jsonObject)
  private fun order(source: String): OrderDto = ApiJson.decode(Json.parseToJsonElement("""{"id":"O1","orderNo":7,"source":"$source","status":"new"}""").jsonObject)

  @Test fun `دکمه فقط با کلیدِ سرور و فقط برای سفارشِ ویترین`() {
    val on = cfg("""{"trackLinkRevoke":true}""")
    val off = cfg("""{}""")
    assertTrue(on.trackLinkRevoke)
    assertFalse("سرورِ کهنه یا پرچمِ خاموش یا نقشِ دیگر ⇒ کلید نیست", off.trackLinkRevoke)
    assertTrue(OrderTrackLink.visible(order("storefront"), on))
    assertFalse(OrderTrackLink.visible(order("storefront"), off))
    assertFalse("سفارشِ تلفنی لینکِ پیگیری ندارد", OrderTrackLink.visible(order("phone"), on))
  }

  @Test fun `باطل کردن به همان درِ سرور می‌رود و لینکِ تازه را برمی‌گرداند`(): Unit = runBlocking {
    val seen = Collections.synchronizedList(ArrayList<TestHttpServer.Request>())
    server.on("/api/v1/shop/orders/O1/track-link/revoke") { r ->
      seen += r
      200 to """{"ok":true,"token":"tk2","link":"https://s.example/store/s1#/o/tk2","whatsappUrl":"https://wa.me/93790000000?text=x"}"""
    }
    val r = repo().revokeTrackLink("O1")
    assertTrue(r is ApiResult.Success)
    val out = (r as ApiResult.Success).value
    assertEquals("https://s.example/store/s1#/o/tk2", out.link)
    assertEquals("https://wa.me/93790000000?text=x", out.whatsappUrl)
    assertEquals("POST", seen.single().method)
  }

  @Test fun `ردِ سرور (نقش، پرچمِ خاموش‌شده) خطاست و لینکی ساخته نمی‌شود`(): Unit = runBlocking {
    server.on("/api/v1/shop/orders/O1/track-link/revoke") { 403 to """{"error":{"code":"link_policy_disabled","message":"باطل کردنِ لینکِ پیگیری برای این دکان روشن نیست"}}""" }
    val r = repo().revokeTrackLink("O1")
    assertTrue(r is ApiResult.Failure)
  }
}
