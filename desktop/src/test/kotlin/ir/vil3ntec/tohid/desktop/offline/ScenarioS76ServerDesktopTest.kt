package ir.vil3ntec.tohid.desktop.offline

import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.scenario.FakeLivePort
import ir.vil3ntec.tohid.sync.v1.LiveCredential
import ir.vil3ntec.tohid.sync.v1.LivePort
import ir.vil3ntec.tohid.sync.v1.LiveSocket
import ir.vil3ntec.tohid.sync.v1.LiveTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 *  S76 — رویدادِ زندهٔ تکراری یا خارج از ترتیب (§۱۹.۱۲) با **Pull ِ واقعی** از سرورِ واقعیِ مخزن (د۱۸؛ ماندهٔ `ScenarioS76Test`).
 *
 *  همان `LiveSocket` ِ برنامه، و هر رخداد Pull ِ واقعی می‌سازد: [ShopDevice] همان سه تکهٔ `SyncV1Engine.parts` روی دیسکِ جدای
 *  دستگاه (`SyncStateV1` روی SharedPreferences، `OpQueue`، ژورنالِ `SyncCommit`) و `SyncV1Client` ⇐ `HttpEngine` به سرور
 *  (`server/test/e2e/lib/offline30-backend.mjs`، PGlite). دو آزمون:
 *   ۱) سوکتِ جعلی ([FakeLivePort]) تا رخدادِ تکراری، کهنه و شکافِ cursor دقیق ساخته شود؛ Pull و دفتر واقعی.
 *   ۲) سوکتِ واقعیِ OkHttp به `/api/sync/v1/live` ِ همان سرور با بلیتِ واقعی: push ِ صاحب ⇒ رخدادِ `changed` ِ سرور ⇒ Pull ِ شاگرد.
 *  بی node یا `server/node_modules` ⇒ رد با پیام (همان `assume` ِ `Offline30DaysDesktopTest`).
 */
class ScenarioS76ServerDesktopTest {

  private val work = File(System.getProperty("java.io.tmpdir"), "s76-${System.nanoTime().toString(36)}")

  @After fun clean() {
    LiveSocket.current = null
    work.deleteRecursively()
  }

  private fun withServer(body: suspend (be: OfflineBackend, A: ShopDevice, B: ShopDevice, direct: String) -> Unit) {
    val root = OfflineBackend.repoRoot()
    val missing = OfflineBackend.available(root)
    assumeTrue("سرورِ واقعی در دسترس نیست: $missing", missing == null)
    work.mkdirs()
    OfflineBackend.start(root!!, File(work, "backend.log")).use { be ->
      runBlocking {
        val setup = be.call("POST", "/setup", obj("subscriptionDays" to 30))
        val o = setup.obj("owner")
        val s = setup.obj("staff")
        val A = ShopDevice("s76-owner", o.str("deviceId"), o.str("userId"), "صاحب", setup.str("api"), MemTokens(o.str("access"), o.str("refresh")), File(work, "A"), "1.0.9999")
        val B = ShopDevice("s76-staff", s.str("deviceId"), s.str("userId"), "علی", setup.str("api"), MemTokens(s.str("access"), s.str("refresh")), File(work, "B"), "3.2.9999")
        A.firstSync()
        B.firstSync()
        body(be, A, B, setup.str("direct"))
      }
    }
  }

  /** صاحب قیمتِ کالا را می‌نویسد و تا سرور می‌فرستد ⇒ cursor ِ سرور پس از آن */
  private suspend fun ShopDevice.price(id: String, value: Double): Long {
    val now = System.currentTimeMillis()
    val have = ledger.products.firstOrNull { it.id == id }
    val next = if (have == null) ledger.copy(products = ledger.products + Product(id = id, name = "برنج", unit = "کیلو", salePrice = value, createdAt = now))
      else ledger.copy(products = ledger.products.map { if (it.id == id) it.copy(salePrice = value) else it })
    assertEquals(1, save(next))
    sync().getOrThrow()
    assertEquals(0, queue.size())
    return state.cursor
  }

  private fun ShopDevice.priceOf(id: String): Double? = ledger.products.firstOrNull { it.id == id }?.salePrice

  @Test fun `S76 با Pull ِ واقعی - تکراری، کهنه و شکافِ cursor هر کدام یک Pull ِ واقعی؛ دفتر و cursor عقب نمی‌روند`() = withServer { _, A, B, _ ->
    var pulls = 0
    val cursors = mutableListOf<Long>()
    val port = FakeLivePort()
    val live = port.socket {
      pulls++
      runBlocking { B.sync().getOrThrow() }
      cursors += B.state.cursor
    }
    fun changed(c: Long) = """{"event":"changed","cursor":$c}"""

    live.connect()
    port.last.opens()
    port.last.says("""{"event":"hello","cursor":0}""")
    assertEquals(1, pulls)

    val c1 = A.price("rice", 10.0)
    port.last.says(changed(c1))
    assertEquals(10.0, B.priceOf("rice"))
    assertEquals(c1, B.state.cursor)

    //  تکرارِ همان رخداد و رخدادهای کهنه‌تر (cursor ِ پیام هرگز خوانده نمی‌شود)
    port.last.says(changed(c1))
    port.last.says(changed(c1))
    port.last.says(changed(c1 - 1))
    port.last.says(changed(1))
    assertEquals(10.0, B.priceOf("rice"))
    assertEquals(c1, B.state.cursor)

    //  شکاف: سه نوشتنِ صاحب، خبرِ دو تای اول هرگز نرسید؛ فقط خبرِ آخر — و پس از آن خبرِ دیرِ میانی
    val c2 = A.price("rice", 20.0)
    A.price("rice", 30.0)
    val c4 = A.price("rice", 40.0)
    port.last.says(changed(c4))
    assertEquals(40.0, B.priceOf("rice"))
    assertEquals(c4, B.state.cursor)
    port.last.says(changed(c2))
    assertEquals(40.0, B.priceOf("rice"))

    //  هر رخداد دقیقاً یک Pull، cursor ِ دستگاه هرگز پایین نیامد، Pull هیچ op ای نساخت، دفتر = Snapshot ِ سرور
    assertEquals(8, pulls)
    assertEquals(cursors.sorted(), cursors)
    assertEquals(0, B.queue.size())
    assertTrue("Pull op ساخت", B.made.isEmpty())
    assertEquals(A.ledger.products, B.ledger.products)
    assertEquals(listOf(true), port.lamp)
  }

  @Test fun `S76 با سوکت و Pull ِ واقعی - push ِ صاحب رخدادِ changed ِ سرور را به سوکتِ شاگرد می‌رساند و Pull دفترش را هم‌تراز می‌کند`() = withServer { _, A, B, direct ->
    val pullsRun = Executors.newSingleThreadExecutor()
    val timers = Executors.newSingleThreadScheduledExecutor()
    val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    val lamp = mutableListOf<Boolean>()
    @Volatile var pulls = 0
    val port = object : LivePort {
      override fun target(): LiveTarget = LiveTarget(B.tokens.accessToken.orEmpty(), direct, B.deviceId, "shop") { url, listener ->
        http.newWebSocket(Request.Builder().url(url).build(), listener)
      }
      override suspend fun credential(token: String): Pair<String, String>? =
        LiveCredential.from(200, B.api.post(ApiEndpoints.SyncV1.LIVE_TICKET).toString(), token)
      override fun now(): Long = System.currentTimeMillis()
      override fun later(ms: Long, run: () -> Unit) { timers.schedule(run, ms, TimeUnit.MILLISECONDS) }
      override fun live(on: Boolean) { synchronized(lamp) { lamp += on } }
      override fun publish() {}
      override fun message(event: String, text: String) {}
    }
    val live = LiveSocket(port, {
      pullsRun.execute { runBlocking { B.sync().getOrThrow() }; pulls++ }
    }, CoroutineScope(Dispatchers.IO))
    try {
      live.connect()
      waitFor("سوکتِ شاگرد وصل نشد") { live.connected && synchronized(lamp) { lamp.lastOrNull() == true } }
      //  hello ِ پس از وصل یک Pull
      waitFor("hello ِ سرور Pull نساخت") { pulls >= 1 }

      A.price("tea", 15.0)
      waitFor("رخدادِ زندهٔ سرور Pull ِ شاگرد را نساخت") { pullsRun.submit<Double?> { B.priceOf("tea") }.get() == 15.0 }
      val c = A.price("tea", 25.0)
      waitFor("رخدادِ دوم") { pullsRun.submit<Long> { B.state.cursor }.get() >= c }
      assertEquals(25.0, pullsRun.submit<Double?> { B.priceOf("tea") }.get())
      assertEquals(0, pullsRun.submit<Int> { B.queue.size() }.get())
    } finally {
      live.disconnect()
      pullsRun.shutdown(); pullsRun.awaitTermination(30, TimeUnit.SECONDS)
      timers.shutdownNow()
      http.dispatcher.executorService.shutdown()
      http.connectionPool.evictAll()
    }
  }

  private fun waitFor(what: String, ok: () -> Boolean) {
    val until = System.currentTimeMillis() + 20_000
    while (!ok()) {
      check(System.currentTimeMillis() < until) { what }
      Thread.sleep(50)
    }
  }
}
