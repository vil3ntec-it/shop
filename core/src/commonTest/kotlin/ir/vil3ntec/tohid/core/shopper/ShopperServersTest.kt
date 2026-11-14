package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.server.ServerProfile
import ir.vil3ntec.tohid.core.server.ServerProfiles
import ir.vil3ntec.tohid.core.server.ServerRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 *  برنامهٔ خریدِ مشتری با چند نشانیِ یک سرور (`ShopperRoutedHttp`، SERVERS §۳): ثبتِ سفارش با کلیدِ یکتایی روی
 *  نشانیِ دیگر دوباره می‌رود، کارِ بی کلید نه؛ نشانیِ دستیِ «تنظیمات» همان رفتارِ امروز؛ پرسیدنِ هویت مثلِ برنامهٔ دکان.
 */
class ShopperServersTest {

  private val build = "https://api.vill3n.top"
  private val alt = "https://b.example.com"
  private val idA = "srv_0123456789abcdef0123"
  private val idB = "srv_fedcba9876543210fedc"

  private class Kv : ShopperKeyValue {
    val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
  }

  private fun infoJson(id: String) = "{\"kind\":\"tohid-server\",\"serverId\":\"$id\",\"wire\":{\"min\":1,\"max\":1}}"

  /** فرستندهٔ ساختگی: هر نشانی پایین است یا پاسخِ همان سرور را می‌دهد */
  private class FakeNet(val down: MutableSet<String>, val ids: Map<String, String>) : ShopperSender {
    val sent = ArrayList<Pair<String, ShopperRequest>>()
    override fun send(base: String, request: ShopperRequest): ShopperResponse {
      sent += base to request
      if (base in down) throw IllegalStateException("unreachable $base")
      if (request.path == "/api/v1/config/server") {
        val id = ids[base] ?: return ShopperResponse(404, "")
        return ShopperResponse(200, "{\"kind\":\"tohid-server\",\"serverId\":\"$id\",\"wire\":{\"min\":1,\"max\":1}}")
      }
      return ShopperResponse(200, "{\"from\":\"$base\"}")
    }
  }

  private fun kvWith(urls: List<String>, id: String?) = Kv().apply {
    map[ServerRouter.K_PROFILE] = ServerProfiles(
      ServerProfiles.DEFAULT_ID,
      listOf(ServerProfile(id = ServerProfiles.DEFAULT_ID, name = "سرور", urls = urls, serverId = id)),
    ).encode()
  }

  private val checkout = ShopperRequest("POST", "/api/store/x/checkout", mapOf("Idempotency-Key" to "k-1"), "{}")
  private val verify = ShopperRequest("POST", "/api/store/x/verify/start", emptyMap(), "{}")
  private val catalog = ShopperRequest("GET", "/api/store/x/products")

  @Test
  fun mayResendOnlyWhenRepeatHasNoSecondEffect() {
    val e = IllegalStateException("x")
    assertTrue(ShopperServers.mayResend(catalog, e) { false })
    assertTrue(ShopperServers.mayResend(ShopperRequest("HEAD", "/"), e) { false })
    assertTrue(ShopperServers.mayResend(checkout, e) { false })
    assertTrue(ShopperServers.mayResend(ShopperRequest("POST", "/", mapOf("idempotency-key" to "k")), e) { false })
    assertFalse(ShopperServers.mayResend(verify, e) { false })
    assertTrue(ShopperServers.mayResend(verify, e) { true }, "اصلاً نرفت ⇒ هر درخواستی")
  }

  @Test
  fun checkoutWithIdempotencyKeyIsResentOnSameServerAlternate() {
    val net = FakeNet(mutableSetOf(build), mapOf(alt to idA))
    val http = ShopperServers.routed(build, kvWith(listOf(build, alt), idA), { null }, net, { 0L })
    val r = http.send(checkout)
    assertEquals(200, r.status)
    assertEquals("{\"from\":\"$alt\"}", r.body)
    assertEquals(listOf(build, alt, alt), net.sent.map { it.first })
    assertEquals("/api/v1/config/server", net.sent[1].second.path)
    assertTrue(net.sent[1].second.timeoutMs in 1..10_000, "سنجش با مهلتِ کوتاه")
    assertEquals(alt, http.base())
  }

  @Test
  fun codeRequestThatMayHaveArrivedIsNotSentTwice() {
    val net = FakeNet(mutableSetOf(build), mapOf(alt to idA))
    val http = ShopperServers.routed(build, kvWith(listOf(build, alt), idA), { null }, net, { 0L })
    val e = runCatching { http.send(verify) }.exceptionOrNull()
    assertTrue(e is IllegalStateException)
    assertEquals(1, net.sent.count { it.second.path == verify.path }, "کدِ دوم فرستاده نشد")
    //  درخواستِ بعدی از همان آغاز روی نشانیِ سالم
    assertEquals("{\"from\":\"$alt\"}", http.send(catalog).body)
  }

  @Test
  fun foreignServerBehindAlternateNeverReceivesTheRequest() {
    val net = FakeNet(mutableSetOf(build), mapOf(alt to idB))
    val http = ShopperServers.routed(build, kvWith(listOf(build, alt), idA), { null }, net, { 0L })
    assertTrue(runCatching { http.send(checkout) }.isFailure)
    assertTrue(net.sent.none { it.first == alt && it.second.path == checkout.path }, "⛔ نشست و سفارش به سرورِ دیگر نرفت")
    assertEquals(build, http.base())
  }

  @Test
  fun manualServerFromSettingsBypassesRoutingExactlyLikeToday() {
    val kv = kvWith(listOf(build, alt), idA)
    val net = FakeNet(mutableSetOf("https://dev.example.com"), mapOf(alt to idA))
    val http = ShopperServers.routed(build, kv, { "https://dev.example.com" }, net, { 0L })
    val before = kv.map.toMap()
    assertTrue(runCatching { http.send(catalog) }.isFailure)
    assertEquals(listOf("https://dev.example.com"), net.sent.map { it.first })
    assertEquals("https://dev.example.com", http.base())
    http.refreshIfDue()
    assertEquals(before, kv.map, "نشانیِ دستی ⇒ هیچ سنجش و هیچ نوشتنی")
  }

  @Test
  fun askReadsConfigServerThenLegacyHealthAndNothingElse() {
    val answers = HashMap<String, ShopperResponse>()
    val asked = ArrayList<ShopperRequest>()
    val ask = ShopperServers.ask { _, req -> asked += req; answers[req.path] ?: ShopperResponse(500, "") }
    answers["/api/v1/config/server"] = ShopperResponse(200, infoJson(idA))
    assertEquals(idA, ask(alt, 1500)?.serverId)
    assertEquals(1500, asked.last().timeoutMs)
    assertEquals("GET", asked.last().method)
    answers["/api/v1/config/server"] = ShopperResponse(404, "")
    answers["/api/health"] = ShopperResponse(200, "{\"service\":\"vill3n-auth\",\"server\":\"online\",\"version\":\"1\"}")
    val legacy = ask(alt, 1500)
    assertTrue(legacy?.legacy == true)
    assertNull(legacy?.serverId)
    answers["/api/v1/config/server"] = ShopperResponse(500, "")
    assertNull(ask(alt, 1500))
    answers["/api/v1/config/server"] = ShopperResponse(200, "{\"kind\":\"other\"}")
    assertNull(ask(alt, 1500), "سرورِ خانوادهٔ دیگر")
    answers["/api/v1/config/server"] = ShopperResponse(200, " ".repeat(70_000) + infoJson(idA))
    assertNull(ask(alt, 1500), "پاسخِ بیش از اندازه")
    val boom = IllegalStateException("x")
    assertNull(ShopperServers.ask { _, _ -> throw boom }(alt, 1500))
  }

  @Test
  fun plainRouterWithoutAlternatesIsTheSameSingleSend() {
    val net = FakeNet(mutableSetOf(build), emptyMap())
    val kv = Kv()
    val http = ShopperServers.routed(build, kv, { null }, net, { 0L })
    val e = runCatching { http.send(checkout) }.exceptionOrNull()
    assertTrue(e is IllegalStateException)
    assertEquals(listOf(build), net.sent.map { it.first })
    assertTrue(kv.map.isEmpty())
    assertSame(checkout, net.sent.single().second, "همان درخواست، بی دستکاری")
  }
}
