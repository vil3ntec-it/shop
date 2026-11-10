package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.image.ImageQuality
import ir.vil3ntec.tohid.core.image.ProductGallery
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 *  صفحهٔ «عکس‌های بیشتر و ویترین» (PRD-17/27) در برابرِ یک سرورِ واقعیِ کوچک با همان `GET /shop/images/mine` ِ
 *  `shopList` (سقفِ ۵۰ در هر صفحه، `id < cursor`، `next_cursor`). کالای با بیش از ۵۰ عکس: پیش‌تر فقط صفحهٔ نخست
 *  خوانده می‌شد ⇒ عکسِ ویترین (`promo`) «همان عکسِ کالا» دیده می‌شد و بارگذاریِ بعدیِ دستگاه جایش می‌نشست.
 */
class ProductGalleryRepositoryTest {

  private lateinit var server: TestHttpServer

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  private fun repo(): ProductGalleryRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = Long.MAX_VALUE
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return ProductGalleryRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  @Test fun `کالای با ۶۰ عکس — همهٔ صفحه‌ها خوانده می‌شود و عکسِ ویترینِ صفحهٔ دوم می‌ماند`() {
    val ids = (60 downTo 1).map { "i" + it.toString().padStart(3, '0') }
    val asked = Collections.synchronizedList(mutableListOf<String?>())
    server.on("/api/v1/shop/images/mine") { r ->
      asked += r.param("cursor")
      val limit = (r.param("limit")?.toIntOrNull() ?: 50).coerceIn(1, 50)
      val cursor = r.param("cursor")
      val page = ids.filter { cursor == null || it < cursor }.take(limit)
      val rows = page.joinToString(",") { """{"id":"$it","product_id":"p1","role":"${if (it == "i005") "promo" else "main"}","version":1,"urls":{}}""" }
      val next = if (page.size == limit) "\"" + page.last() + "\"" else "null"
      200 to """{"items":[$rows],"next_cursor":$next}"""
    }
    val items = runBlocking { repo().items("p1") }.valueOrNull()!!
    assertEquals(60, items.size)
    assertEquals(listOf(null, "i011"), asked.toList())
    val shown = ImageQuality.Shown("shop", "i005", 1, null, null, null)
    assertEquals(ProductGallery.Storefront.OWN, ProductGallery.storefrontOf(shown, items))
    assertTrue(ProductGallery.keepStorefrontPhoto(shown, items, "new1"))
  }
}
