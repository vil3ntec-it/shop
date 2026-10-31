package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.marketing.CampaignLinks
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.core.storefront.CampaignLinksApi.Gate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  پیوندِ کمپین و QR (MKT-10/11) — پاسخ و بدنه همان است که
 *  `server/src/lib/campaigns.js` (`view`، `list`) و `routes/storefront-campaigns.js` می‌دهند.
 */
class CampaignLinksApiTest {
  private val A = CampaignLinksApi
  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val listJson = """
    {"days":30,"sources":["qr_wall","qr_receipt","social","messaging","sms","print","other"],
     "campaigns":[{"id":"01JCMP0000000000000000000A","clientId":"qr-wall","code":"ABCDEFGHJK","path":"/store/go/ABCDEFGHJK",
       "name":"QR ِ دیوار و پیشخوان","source":"qr_wall","archived":false,"createdAt":1760000000000,"updatedAt":1760000000000,
       "stats":{"visits":12,"orders":3,"doneOrders":2,"doneMinor":45000},"somethingNew":true},
      {"id":"01JCMP0000000000000000000B","clientId":"x1234","code":"0123456789","path":"/store/go/0123456789",
       "name":"تراکت","source":"billboard","archived":true,"createdAt":1,"updatedAt":2}]}
  """

  @Test fun `فهرستِ سرور خوانده می‌شود و کلید و منبعِ ناشناخته نمی‌شکند`() {
    val l = A.parseList(obj(listJson))
    assertEquals(30, l.days)
    assertEquals(2, l.campaigns.size)
    val wall = l.campaigns[0]
    assertEquals("qr-wall", wall.clientId)
    assertEquals(CampaignStatsDto(visits = 12, orders = 3, doneOrders = 2, doneMinor = 45000), wall.stats)
    assertEquals(CampaignLinks.Source.QR_WALL, CampaignLinks.Source.of(wall.source))
    val old = l.campaigns[1]
    assertTrue(old.archived)
    assertNull("پاسخِ ویرایش شمار ندارد", old.stats)
    assertEquals(CampaignLinks.Source.OTHER, CampaignLinks.Source.of(old.source))
    assertEquals("https://api.vill3n.top/store/go/ABCDEFGHJK", CampaignLinks.publicUrl("https://api.vill3n.top", wall.path))
  }

  @Test fun `بدنهٔ ساختن همان سه کلیدِ سرور است و نام پاک‌سازی می‌شود`() {
    val b = A.createBody("01JCLIENT00000000000000000", "  اینستاگرامِ \n دکان ", CampaignLinks.Source.SOCIAL)
    assertEquals(setOf("clientId", "name", "source"), b.keys)
    assertEquals("اینستاگرامِ دکان", b["name"]!!.jsonPrimitive.content)
    assertEquals("social", b["source"]!!.jsonPrimitive.content)
    assertFalse(A.archiveBody(false)["archived"]!!.jsonPrimitive.boolean)
  }

  @Test fun `حالتِ بخش از خطای سرور`() {
    assertEquals(Gate.OFF, A.gateOf(404, "not_found"))
    assertEquals(Gate.OFF, A.gateOf(403, "campaigns_disabled"))
    assertEquals(Gate.OFF, A.gateOf(403, "storefront_disabled"))
    assertEquals(Gate.NO_PERMISSION, A.gateOf(403, "forbidden"))
    assertEquals(Gate.ERROR, A.gateOf(500, "server_error"))
  }

  /* --------------------- مخزن، در برابرِ سرورِ واقعیِ HTTP --------------------- */

  private fun client(server: TestHttpServer): CampaignLinksRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = Long.MAX_VALUE
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return CampaignLinksRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  @Test fun `مخزن — ساختن با کلیدِ یکتایی و بایگانی با PUT روی همان پیوند`() {
    val server = TestHttpServer()
    try {
      val sent = mutableListOf<TestHttpServer.Request>()
      val one = """{"campaign":{"id":"01JCMP0000000000000000000A","clientId":"qr-wall","code":"ABCDEFGHJK","path":"/store/go/ABCDEFGHJK","name":"QR ِ دیوار","source":"qr_wall","archived":ARCH,"createdAt":1,"updatedAt":2}}"""
      server.on("/api/v1/shop/storefront/campaigns") { r -> sent += r; 201 to one.replace("ARCH", "false") }
      server.on("/api/v1/shop/storefront/campaigns/01JCMP0000000000000000000A") { r -> sent += r; 200 to one.replace("ARCH", "true") }
      server.on("/api/v1/shop/storefront/campaigns/config") { 200 to """{"flag":true,"enabled":true,"sources":[],"permissions":{"manage":true}}""" }
      val repo = client(server)

      val cfg = runBlocking { repo.config() }.valueOrNull()!!
      assertTrue(cfg.flag && cfg.enabled && cfg.permissions.manage)

      val made = runBlocking { repo.create(CampaignLinks.WALL_CLIENT_ID, "QR ِ دیوار", CampaignLinks.Source.QR_WALL) }
      assertEquals("ABCDEFGHJK", made.valueOrNull()!!.code)
      assertEquals("POST", sent[0].method)
      assertEquals("qr-wall", sent[0].headers["idempotency-key"])
      assertEquals("qr-wall", obj(sent[0].body)["clientId"]!!.jsonPrimitive.content)

      val arch = runBlocking { repo.setArchived("01JCMP0000000000000000000A", true) }
      assertTrue(arch.valueOrNull()!!.archived)
      assertEquals("PUT", sent[1].method)
      assertTrue(obj(sent[1].body)["archived"]!!.jsonPrimitive.boolean)
    } finally {
      server.close()
    }
  }

  @Test fun `مخزن — پرچمِ خاموش ۴۰۳ به حالتِ خاموش می‌رسد`() {
    val server = TestHttpServer()
    try {
      server.on("/api/v1/shop/storefront/campaigns") { 403 to """{"error":{"code":"campaigns_disabled","message":"پیوندِ کمپین برای این دکان روشن نیست"}}""" }
      val off = runBlocking { client(server).list() } as ApiResult.Failure
      assertEquals(Gate.OFF, A.gateOf(off.error.status, off.error.code))
    } finally {
      server.close()
    }
  }
}
