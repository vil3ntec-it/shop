package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import ir.vil3ntec.tohid.ui.v3.online.storefront.ProductStorefront
import ir.vil3ntec.tohid.ui.v3.online.storefront.ProductStorefrontState
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  GATE-NEW-02 روی رابطِ INV-01@32 — سمتِ برنامه: پاسخ‌های **زندهٔ** درِ صفحهٔ کالا (`GET /api/shop/storefront/products/:id`) در
 *  سناریوهای INV ِ `server/test/catalog-stock-page.test.js` (نرسیده، همگام‌شده، منتشرشده، دکانِ دیگر، غیرفعال، حذف‌شده، دستگاهِ
 *  کهنه) در `core/test-vectors/product-page-inv-v1.json` نگه داشته می‌شوند (آزمونِ سرور هر بار با پاسخِ زنده می‌سنجدشان). این‌جا
 *  همان بدنه و وضعیت از سرورِ ساختگی، با کلاینت و `StorefrontRepository` ِ واقعی، به `ProductStorefront` می‌رسد و صفحه همان
 *  وضعی را می‌سازد که سناریو می‌خواهد: نرسیده/حذف‌شده/دکانِ دیگر ⇒ «هنوز به سرور نرسیده» و کلیدِ خاموش؛ بقیه ⇒ همان
 *  شناسه، وضعِ انتشار و زیرنویسِ درست (غیرفعال در دفتر یا قیمتِ دفتر).
 */
class ProductStorefrontInvVectorsTest {

  private lateinit var server: TestHttpServer

  @Before fun up() {
    I18n.source = { Lang.FA }
    server = TestHttpServer()
  }

  @After fun down() { server.close() }

  private fun vectors(): JsonObject {
    var dir: File? = File(".").absoluteFile
    var file: File? = null
    while (dir != null && file == null) {
      file = File(dir, "core/test-vectors/product-page-inv-v1.json").takeIf { it.isFile }
      dir = dir.parentFile
    }
    assertNotNull("core/test-vectors/product-page-inv-v1.json پیدا نشد (cwd=${File(".").absoluteFile})", file)
    return Json.parseToJsonElement(file!!.readText()).jsonObject
  }

  private fun repo(): StorefrontRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = 0
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return StorefrontRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  @Test fun everyInvScenarioOfTheProductPageDoorBuildsTheStateTheScenarioExpects() {
    val cases = vectors()["cases"]!!.jsonArray.map { it.jsonObject }
    assertEquals("هفت سناریوی صفحهٔ کالا", 7, cases.size)
    val repo = repo()
    for (c in cases) {
      val name = c["name"]!!.jsonPrimitive.content
      val path = c["path"]!!.jsonPrimitive.content
      val id = path.substringAfterLast('/')
      server.on(path) { c["status"]!!.jsonPrimitive.int to c["body"].toString() }
      val state = ProductStorefront.of(runBlocking { repo.product(id) })
      val expect = c["expect"]!!.jsonObject
      when (expect["state"]!!.jsonPrimitive.content) {
        "NotSynced" -> {
          assertEquals(name, ProductStorefrontState.NotSynced, state)
          assertTrue("$name: دوباره پرسیده می‌شود", ProductStorefront.shouldLoad(state))
          assertFalse("$name: کلید خاموش", ProductStorefront.canToggle(state, busy = false))
        }
        "Ready" -> {
          assertTrue("$name ⇒ $state", state is ProductStorefrontState.Ready)
          val p = (state as ProductStorefrontState.Ready).p
          assertEquals(name, id, p.id)
          assertEquals(name, expect["published"]!!.jsonPrimitive.boolean, p.published)
          assertEquals(name, expect["active"]!!.jsonPrimitive.boolean, p.active)
          assertTrue("$name: کلید روشن", ProductStorefront.canToggle(state, busy = false))
          assertFalse("$name: خوانده‌شده دوباره پرسیده نمی‌شود", ProductStorefront.shouldLoad(state))
          val subtitle = when (expect["subtitle"]!!.jsonPrimitive.content) {
            "inactive" -> StrOnline.sfInactive
            else -> StrOnline.ledgerPrice(StorefrontModel.ledgerPrice(p))
          }
          assertEquals(name, subtitle, ProductStorefront.subtitle(p))
        }
        else -> throw AssertionError("$name: وضعِ ناشناخته")
      }
    }
  }
}
