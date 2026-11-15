package ir.vil3ntec.tohid.desktop.offline

import ir.vil3ntec.tohid.core.hub.HubPair
import ir.vil3ntec.tohid.core.sync.PushSince
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.desktop.hub.HubCloudHttp
import ir.vil3ntec.tohid.desktop.hub.HubServer
import ir.vil3ntec.tohid.desktop.hub.HubTls
import ir.vil3ntec.tohid.platform.HubExchangeV1
import ir.vil3ntec.tohid.platform.HubPairClient
import ir.vil3ntec.tohid.platform.HubPeerLink
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Base64

/**
 *  S29 / SYN-09 — دو گوشیِ ساختگی + هابِ واقعیِ کامپیوتر (TLS ِ سنجاق‌شده) + سرورِ واقعیِ مخزن
 *  (`server/test/e2e/lib/offline30-backend.mjs`، همان `OfflineBackend` ِ `Offline30DaysDesktopTest`/`ScenarioS76ServerDesktopTest`).
 *
 *  هر گوشی همان سه تکهٔ موتورِ v1 روی دیسکِ خودش (`ShopDevice`) و همان `HubPeerLink`/`HubExchangeV1` ِ برنامه؛ هاب همان
 *  `HubServer` با `HubCloudHttp` (همان `POST /sync/v1/push` ِ برنامه با توکنِ خودِ هر گوشی) — بی هیچ تغییری در سرور.
 *  اینترنت با پراکسیِ «شبکهٔ دکان» قطع می‌شود؛ هاب روی loopback در دسترس می‌ماند.
 */
class HubLanServerDesktopTest {

  private val work = File(System.getProperty("java.io.tmpdir"), "hub-lan-${System.nanoTime().toString(36)}")
  private val seal: (String) -> String = { "S1:" + Base64.getEncoder().encodeToString(it.toByteArray().reversedArray()) }
  private val open: (String) -> String? = { s -> if (s.startsWith("S1:")) String(Base64.getDecoder().decode(s.substring(3)).reversedArray()) else null }
  private val sid = "srv_lan_hub_test_0001"
  private val hubs = ArrayList<HubServer>()

  @After fun clean() {
    hubs.forEach { runCatching { it.stop() } }
    work.deleteRecursively()
  }

  private class MemStore : HubExchangeV1.Store {
    override var since = 0L
    override var epoch = ""
    val cloud = HashSet<String>()
    override fun seenFromCloud(ids: Collection<String>) = ids.filterTo(HashSet()) { it in cloud }
    override fun sawFromCloud(ids: Collection<String>) { cloud += ids }
  }

  /** گوشی + پیوندِ هاب */
  private inner class Phone(val d: ShopDevice, val version: String) {
    val store = MemStore()
    var link: HubPeerLink? = null
    init { d.onPulled = { ops -> store.sawFromCloud(ops.map { it.opId }) } }
    fun identity() = mapOf("Authorization" to "Bearer ${d.tokens.accessToken}", "X-Device" to d.deviceId, "X-App" to "shop", "X-App-Version" to version)
    fun pair(h: HubServer, code: String): HubPairClient.Result {
      val r = HubPairClient.withCode("https://127.0.0.1:${h.port}", code, identity())
      if (r is HubPairClient.Result.Ok) link = HubPeerLink(r.paired.url, r.paired.pin, r.paired.key, r.paired.serverId, { identity() })
      return r
    }
    /** همان `SyncV1Engine.hubExchange`: سرور نرسید ⇒ صف به هاب، opهای دیگران از هاب */
    fun viaHub(): HubExchangeV1.Outcome = HubExchangeV1(link!!, store).exchange(
      d.deviceId, d.queue.all(), d.queue.size(), { true }, LedgerDiff.wireSchema(d.state.serverSchema),
      PushSince.since(d.state.pushSince, d.state.cursor), d.state.cursor,
    ) { ops -> d.absorbFromHub(ops) }
  }

  private fun withShop(body: suspend (be: OfflineBackend, shopId: String, A: Phone, B: Phone) -> Unit) {
    val root = OfflineBackend.repoRoot()
    val missing = OfflineBackend.available(root)
    assumeTrue("سرورِ واقعی در دسترس نیست: $missing", missing == null)
    work.mkdirs()
    OfflineBackend.start(root!!, File(work, "backend.log")).use { be ->
      runBlocking {
        val setup = be.call("POST", "/setup", obj("subscriptionDays" to 30))
        val o = setup.obj("owner")
        val s = setup.obj("staff")
        val A = ShopDevice("hub-owner", o.str("deviceId"), o.str("userId"), "صاحب", setup.str("api"), MemTokens(o.str("access"), o.str("refresh")), File(work, "A"), "1.0.9999")
        val B = ShopDevice("hub-staff", s.str("deviceId"), s.str("userId"), "علی", setup.str("api"), MemTokens(s.str("access"), s.str("refresh")), File(work, "B"), "3.2.9999")
        A.firstSync()
        B.firstSync()
        body(be, setup.str("shopId"), Phone(A, "1.0.9999"), Phone(B, "3.2.9999"))
      }
    }
  }

  private fun hub(be: OfflineBackend, shopId: String, dir: File = File(work, "hub")): HubServer {
    val api = be.api
    val tls = HubTls.loadOrCreate(dir, seal, open)
    val cloud = HubCloudHttp(base = { api })
    val h = HubServer(dir, tls, sid, shopId, cloud, seal, open, autoForward = false, v1Cloud = cloud)
    h.start(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    hubs += h
    return h
  }

  private fun ticket(h: HubServer): String {
    assertTrue(h.v1!!.newTicket("owner", "صاحب") is HubPair.Issue.Ok)
    return h.v1!!.status().ticket!!.code
  }

  private fun ShopDevice.put(id: String, name: String, price: Double): Int {
    val now = System.currentTimeMillis()
    val have = ledger.products.firstOrNull { it.id == id }
    val next = if (have == null) ledger.copy(products = ledger.products + Product(id = id, name = name, unit = "کیلو", salePrice = price, createdAt = now))
      else ledger.copy(products = ledger.products.map { if (it.id == id) it.copy(salePrice = price) else it })
    return save(next)
  }

  private fun ShopDevice.priceOf(id: String): Double? = ledger.products.firstOrNull { it.id == id }?.salePrice

  private data class Oplog(val total: Int, val distinct: Int, val duplicates: Int, val ids: Set<String>)

  private fun OfflineBackend.oplog(): Oplog {
    val o = call("GET", "/oplog")
    return Oplog(
      (o["total"] as JsonPrimitive).content.toInt(), (o["distinct"] as JsonPrimitive).content.toInt(),
      (o["duplicates"] as JsonArray).size, (o["ids"] as JsonArray).mapTo(HashSet()) { (it as JsonPrimitive).content },
    )
  }

  @Test fun `بی اینترنت دو گوشی از راهِ هاب رد و بدل می‌کنند و با برگشتِ اینترنت سرور هر op را دقیقاً یک بار دارد`() = withShop { be, shopId, A, B ->
    val h = hub(be, shopId)
    //  پیش از جفت شدن: گوشی هیچ از هاب نمی‌بیند
    val stranger = HubPeerLink("https://127.0.0.1:${h.port}", HubTls.loadOrCreate(File(work, "hub"), seal, open).pin, "00".repeat(32), sid, { B.identity() })
    assertEquals(null, stranger.feed(0, 0))
    assertEquals(403, stranger.relay(kotlinx.serialization.json.buildJsonObject { }).status)

    //  جفت کردن با کدِ یک‌بارهٔ صاحب؛ بازپخشِ همان کد رد
    val codeA = ticket(h)
    val ra = A.pair(h, codeA)
    assertTrue(ra.toString(), ra is HubPairClient.Result.Ok)
    assertEquals(HubPairClient.Result.Failed("hub_pair_used"), B.pair(h, codeA))
    assertTrue(B.pair(h, ticket(h)) is HubPairClient.Result.Ok)

    //  با اینترنت: کالا ساخته و همه‌جا یکی
    assertEquals(1, A.d.put("rice", "برنج", 10.0))
    A.d.sync().getOrThrow()
    B.d.sync().getOrThrow()
    assertEquals(10.0, B.d.priceOf("rice"))
    val base = be.oplog()

    //  ── اینترنتِ دکان قطع ──
    be.net(up = false)
    A.d.put("rice", "برنج", 20.0)
    A.d.put("tea", "چای", 5.0)
    assertTrue(A.d.sync().isFailure)
    val outA = A.viaHub()
    assertEquals(A.d.queue.size(), outA.relayed)
    assertTrue(outA.relayed >= 2)
    //  گوشیِ B بی اینترنت کارهای A را از هاب می‌گیرد
    assertTrue(B.d.sync().isFailure)
    B.d.put("salt", "نمک", 3.0)
    val outB = B.viaHub()
    assertEquals(outA.relayed, outB.received)
    assertEquals(20.0, B.d.priceOf("rice"))
    assertEquals(5.0, B.d.priceOf("tea"))
    //  … و A کارِ B را
    val backA = A.viaHub()
    assertEquals(B.d.queue.size(), backA.received)
    assertEquals(3.0, A.d.priceOf("salt"))
    //  رد و بدلِ دوباره هیچ op ِ تازه‌ای نمی‌نشاند و صفِ هیچ گوشی را خالی نمی‌کند
    assertEquals(0, B.viaHub().received)
    assertTrue(A.d.queue.size() > 0 && B.d.queue.size() > 0)
    //  هاب چیزی در دفترِ سرور ننوشته است (هم مرجع نیست، هم اینترنت نیست)
    assertEquals(base.total, be.oplog().total)
    assertTrue(h.forwardV1Now()!!.retry)
    assertEquals(base.total, be.oplog().total)

    //  ── اینترنت برگشت: اول هاب، سپس خودِ گوشی‌ها (همان opها ⇒ «duplicate») ──
    be.net(up = true)
    val pass = h.forwardV1Now()!!
    assertTrue(pass.toString(), !pass.retry && pass.forwarded >= 2 && pass.authFailed.isEmpty())
    val afterHub = be.oplog()
    val madeOffline = (A.d.made + B.d.made).filter { it !in base.ids }
    assertTrue("هاب همه را رساند", afterHub.ids.containsAll(madeOffline))
    A.d.sync().getOrThrow()
    B.d.sync().getOrThrow()
    A.d.sync().getOrThrow()
    val end = be.oplog()
    assertEquals(0, end.duplicates)
    assertEquals(end.total, end.distinct)
    assertEquals(afterHub.total, end.total)
    assertEquals((A.d.made + B.d.made).toSet(), end.ids)
    assertTrue("گوشی‌ها پس از هاب «duplicate» گرفتند", A.d.duplicates + B.d.duplicates >= madeOffline.size)
    //  هم‌گرایی: صف‌ها خالی، دفترِ دو گوشی یکی و برابرِ سرور
    assertEquals(0, A.d.queue.size())
    assertEquals(0, B.d.queue.size())
    assertEquals(A.d.ledger.products.sortedBy { it.id }, B.d.ledger.products.sortedBy { it.id })
    assertEquals(20.0, A.d.priceOf("rice"))
    assertEquals(setOf("rice", "tea", "salt"), A.d.ledger.products.mapTo(HashSet()) { it.id })
  }

  @Test fun `بی هاب گوشی همان راهِ مستقیم را دارد و هیچ op ای گم یا دو بار نمی‌شود`() = withShop { be, shopId, A, _ ->
    val h = hub(be, shopId)
    assertTrue(A.pair(h, ticket(h)) is HubPairClient.Result.Ok)
    h.stop()
    be.net(up = false)
    A.d.put("rice", "برنج", 7.0)
    assertTrue(A.d.sync().isFailure)
    val out = A.viaHub()
    assertEquals(0, out.relayed)
    assertTrue(out.refused.isNotEmpty())
    assertEquals(1, A.d.queue.size())
    be.net(up = true)
    A.d.sync().getOrThrow()
    assertEquals(0, A.d.queue.size())
    val log = be.oplog()
    assertEquals(0, log.duplicates)
    assertTrue(log.ids.containsAll(A.d.made))
  }
}
