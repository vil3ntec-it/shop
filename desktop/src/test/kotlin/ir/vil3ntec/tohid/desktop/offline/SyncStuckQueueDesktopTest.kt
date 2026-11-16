package ir.vil3ntec.tohid.desktop.offline

import androidx.work.WorkerParameters
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.server.ServerProfile
import ir.vil3ntec.tohid.core.server.ServerProfiles
import ir.vil3ntec.tohid.data.AccountBind
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.platform.ServerSwitchAndroid
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.ui.screens.ServerPulse
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 *  عاملِ e-syncdiag — «سرور وصل است» ولی «همگام‌سازی ناموفق»، با «در صف: ۴ تغییر» که یک روز نمی‌روند.
 *
 *  سرتاسری روی **سرورِ واقعیِ مخزن** (همان `newinstall-backend.mjs` ِ `NewInstallSyncDesktopTest`) و **همان موتور**
 *  (`SyncV1Engine`، `ShopStore`، `SyncV1Worker.doWork`): هیچ op ِ بدی سرِ صف بقیه را نگه نمی‌دارد — یا پذیرفته می‌شود، یا با
 *  دلیل به دفترِ کنار (`OpQueue.drop`) می‌رود و بقیه می‌روند؛ هیچ opی گم یا دوتایی نمی‌شود (`oplog` ِ سرور).
 *
 *  بی node یا `server/node_modules` ⇒ رد با پیام.
 */
class SyncStuckQueueDesktopTest {

  private val ctx = DesktopContext
  private val engine get() = SyncV1Engine.of(ctx)

  @Before fun fresh() {
    assumeTrue(skip ?: "", skip == null)
    NewInstallSyncDesktopTest.wipe()
    pointAtServer()
    engine.rebind(force = true)
  }

  /** (۱) op ای که سرور نمی‌شناسد (جدولِ ناشناخته، همان «سرورِ قدیمی‌تر از برنامه») سرِ صف ⇒ رد با دلیل، بقیه می‌روند */
  @Test fun `۱ — op ِ ردشده سرِ صف بقیهٔ صف را نمی‌بندد`() = runBlocking {
    val (store, shop) = ready("h")
    val bad = SyncOp("op-unknown-${tag()}", System.currentTimeMillis(), "noSuchTable", "x-1", SyncOp.INSERT, buildJsonObject { put("name", "؟") })
    headOfQueue(bad)
    val good = saveProducts(store, 2)
    assertEquals("op ِ بد سرِ صف", bad.opId, engine.queuedOps().first().opId)
    rounds(store)
    val v1 = engine.status.value
    println("۱: صف=${v1.queued} ردشده=${v1.dropped} خطا=«${v1.lastError}» ${engine.droppedOps().map { it["reason"] }}")
    assertEquals("صف تا ته رفت", 0, v1.queued)
    assertEquals("خطایی نماند", "", v1.lastError)
    assertEquals("op ِ بد در دفترِ کنار، با دلیلِ سرور", listOf("unknown_table"), reasonsOf(bad.opId))
    val server = snapshot()
    for (id in good) assertTrue("پس از op ِ بد رسید: $id", id in server)
    assertNoDuplicates(shop)
    assertEquals(SyncModel.Problem.REJECTED, diagnosis().problem)
    //  کارتِ وضعیت چرایش را می‌گوید، بی «جزئیات»
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrSyncPlain.reasonRejectedOld(1), reason())
  }

  /**
   *  (۲) یک تغییرِ بزرگ‌تر از سقفِ بدنهٔ سرور (۲ مگابایت ⇒ ۴۱۳ برای **کلِ** درخواست): پیش از e-syncdiag دسته‌ای که فقط همان op را
   *  داشت هر دور ۴۱۳ می‌گرفت، دور می‌شکست و opهای پشتِ آن هرگز نمی‌رفتند. حالا تنها op ِ ۴۱۳ ⇒ دفترِ کنار (`body_too_large`).
   */
  @Test fun `۲ — op ِ بزرگ‌تر از سقفِ سرور (۴۱۳) صف را تا ابد نمی‌بندد`() = runBlocking {
    val (store, shop) = ready("t")
    val huge = SyncOp("op-huge-${tag()}", System.currentTimeMillis(), "products", "p-huge-${tag()}", SyncOp.INSERT,
      buildJsonObject { put("name", "کالای بزرگ"); put("note", "x".repeat(2_300_000)) })
    headOfQueue(huge)
    val good = saveProducts(store, 2)
    rounds(store)
    val v1 = engine.status.value
    println("۲: صف=${v1.queued} ردشده=${v1.dropped} خطا=«${v1.lastError}» ${engine.droppedOps().map { it["reason"] }}")
    assertEquals("صف تا ته رفت", 0, v1.queued)
    assertEquals("خطایی نماند", "", v1.lastError)
    assertEquals("op ِ بزرگ در دفترِ کنار، با دلیل", listOf("body_too_large"), reasonsOf(huge.opId))
    val server = snapshot()
    for (id in good) assertTrue("پس از op ِ بزرگ رسید: $id", id in server)
    assertTrue("op ِ بزرگ روی سرور نیست", huge.rowId !in server)
    assertNoDuplicates(shop)
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrSyncPlain.reasonRejectedLarge(1), reason())
  }

  /**
   *  (۴) «همگام‌سازی حالا» و دورِ پس از هر ذخیره (`SyncV1Worker.now`) — روی گوشی با androidx.work 2.9.1 همیشه پرتاب می‌کرد
   *  («Expedited jobs cannot be delayed») و فقط کارِ دوره‌ای می‌رفت. حالا همان کارِ یک‌باره، بی `doWork` ِ دستی، صف را می‌برد.
   */
  @Test fun `۴ — ذخیره و «همگام‌سازی حالا» خودشان دورِ همگام‌سازی را راه می‌اندازند`() = runBlocking {
    val (store, _) = ready("n")
    val ids = saveProducts(store, 1)
    SyncV1Worker.now(ctx)
    val until = System.currentTimeMillis() + 60_000
    while ((engine.queueSize() > 0 || engine.status.value.lastOkAt == 0L) && System.currentTimeMillis() < until) kotlinx.coroutines.delay(200)
    assertEquals("صف با کارِ یک‌باره رفت: «${engine.status.value.lastError}»", 0, engine.queueSize())
    assertTrue("کالا با کارِ یک‌باره به سرور رسید", ids.single() in snapshot())
    assertEquals(SyncModel.Problem.NONE, diagnosis().problem)
  }

  /**
   *  (۳) دستهٔ ۷۱: صفِ نرفتهٔ برنامهٔ قدیم (`BackupRestore.carryOf`) با `rebuildFromServer(next, pushedStaging, carry)` جلوی صف
   *  می‌رود — با شناسهٔ دستگاهِ تازه. پذیرفته ⇒ روی سرور و دفتر؛ ردشده ⇒ دفترِ کنار با دلیل؛ هیچ‌کدام در صف نمی‌ماند. بارِ دوم
   *  همان کار (برنامهٔ قدیم یا بازگردانیِ دوباره) ⇒ «تکراری»، بی op ِ تازه.
   */
  @Test fun `۳ — کارِ حمل‌شده از برنامهٔ قدیم پذیرفته یا با دلیل رد می‌شود، هرگز در صف گیر نمی‌ماند`() = runBlocking {
    val (store, shop) = ready("c")
    val now = System.currentTimeMillis()
    val pid = "p-carry-${tag()}"
    val ok = SyncOp("op-carry-ok-${tag()}", now - 86_400_000, "products", pid, SyncOp.INSERT, buildJsonObject {
      put("name", "کالای برنامهٔ قدیم"); put("unit", "عدد"); put("salePrice", 90); put("createdAt", now - 86_400_000)
    })
    val bad = SyncOp("op-carry-bad-${tag()}", now - 86_400_000, "noSuchTable", "x-c", SyncOp.INSERT, buildJsonObject { put("name", "؟") })
    val r = store.restoreBackup(store.ledgerNow(), listOf(ok, bad))
    println("۳: بازگردانی=${r.ok} «${r.message}» صف=${engine.queueSize()} ردشده=${engine.status.value.dropped}")
    assertTrue("بازگردانی با کارِ حمل‌شده: ${r.message}", r.ok)
    assertEquals("هیچ op ِ حمل‌شده‌ای در صف نماند", 0, engine.queueSize())
    assertEquals("حمل‌شدهٔ ناپذیرفته با دلیل در دفترِ کنار", listOf("unknown_table"), reasonsOf(bad.opId))
    assertTrue("حمل‌شدهٔ پذیرفته روی سرور", pid in snapshot())
    assertEquals("حمل‌شدهٔ پذیرفته در دفترِ تازه", 1, store.data.value.products.count { it.id == pid })
    rounds(store)
    assertEquals(0, engine.status.value.queued)
    assertEquals("", engine.status.value.lastError)
    //  بارِ دوم (بازگردانیِ دوبارهٔ همان پشتیبان) ⇒ سرور «تکراری»
    val before = ctl("/oplog", obj("shopId" to shop))
    val again = store.restoreBackup(store.ledgerNow(), listOf(ok))
    assertTrue(again.message, again.ok)
    assertEquals(0, engine.queueSize())
    val after = ctl("/oplog", obj("shopId" to shop))
    assertEquals("بارِ دوم هیچ op ِ تازه‌ای نساخت", (before["ops"] as JsonPrimitive).content, (after["ops"] as JsonPrimitive).content)
    assertNoDuplicates(shop)
    assertEquals(1, store.data.value.products.count { it.id == pid })
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrSyncPlain.reasonRejectedOld(1), reason())
  }

  /* ─────────────────────────── ابزار ─────────────────────────── */

  private fun tag() = System.nanoTime().toString(36)

  /** دکانِ پُر، ورود و چند دورِ سبز — همان آغازِ `NewInstallSyncDesktopTest` */
  private suspend fun ready(t: String): Pair<ShopStore, String> {
    val s = ctl("/seed", obj("email" to "stuck-$t-${System.nanoTime()}@example.com", "products" to 2))
    val store = ShopStore(ctx).also { it.load() }
    val r = Backend.auth(ctx).login(s.str("email"), s.str("password"))
    val session = (r as? ApiResult.Success)?.value ?: error("ورود نشد: $r")
    session.shop?.role?.let { ShopRole.remember(ctx, it) }
    AccountBind.signedIn(ctx, store, session.user.id, session.shop?.id.orEmpty(), session.user.name)
    SyncStore(ctx).rememberAccount(session.user, session.user.name)
    AccountBind.bootstrapSoon(ctx, store)?.join()
    (Backend.shop(ctx).current() as? ApiResult.Success)?.value?.let { st ->
      if (st.hasShop) {
        ShopRole.rememberShopId(ctx, st.shop?.id)
        LedgerOwner.shopChanged(ctx, store, st.shop?.id.orEmpty())
        AccountBind.healOnStart(ctx, store)
      }
    }
    rounds(store)
    assertEquals("آغازِ سبز: «${engine.status.value.lastError}»", "", engine.status.value.lastError)
    assertEquals(0, engine.status.value.queued)
    return store to s.str("shopId")
  }

  /** op ِ ساخته‌شده **سرِ** صف (بی سایه: همان opی که از دستگاه یا پشتیبانِ دیگر آمده)؛ صفِ موتور از نو خوانده می‌شود */
  private fun headOfQueue(op: SyncOp) {
    OpQueue(ctx, engine.currentKey()).pushFront(listOf(op))
    engine.rebind(force = true)
  }

  private suspend fun saveProducts(store: ShopStore, n: Int): List<String> = (1..n).map { i ->
    val id = "p-after-$i-${tag()}"
    val d = store.data.value
    store.save(d, d.copy(products = d.products + Product(id, name = "کالای پس از گره $i", unit = "عدد", salePrice = 40.0 * i, createdAt = System.currentTimeMillis())))
    id
  }

  private suspend fun rounds(store: ShopStore, n: Int = 3) {
    repeat(n) { SyncV1Worker(ctx, WorkerParameters()).doWork() }
    store.load()
  }

  private fun reasonsOf(opId: String): List<String> = engine.droppedOps()
    .filter { ((it["op"] as? JsonObject)?.get("op_id") as? JsonPrimitive)?.content == opId }
    .map { (it["reason"] as JsonPrimitive).content }

  private fun assertNoDuplicates(shopId: String) {
    val log = ctl("/oplog", obj("shopId" to shopId))
    assertEquals("هیچ op_id ِ دوتایی روی سرور", "0", (log["duplicates"] as JsonPrimitive).content)
  }

  private fun diagnosis(): SyncModel.Diagnosis {
    val v1 = engine.status.value
    return SyncModel.diagnose(
      v1.lastError.ifBlank { AutoSync.lastError.orEmpty() }, v1.holding, v1.dropped, AutoSync.needsShop, ServerPulse.State.UP,
      if (v1.lastError.isNotBlank()) v1.outage else null, code = if (v1.lastError.isNotBlank()) v1.errorCode else "", queued = v1.queued,
      status = if (v1.lastError.isNotBlank()) v1.errorStatus else null,
    )
  }

  /** خطِ «چه شد» ِ کارتِ وضعیت با دلیل‌های دفترِ کنار — همان `SyncScreen` */
  private fun reason(): String? = SyncModel.reasonLine(diagnosis(), SyncModel.rejectedReasons(engine.droppedOps()))?.text

  private suspend fun snapshot(): Set<String> {
    val body = Backend.api(ctx).get("/sync/v1/snapshot")
    val tables = body["tables"] as? JsonObject ?: return emptySet()
    return tables.values.flatMap { t -> (t as? JsonArray).orEmpty().mapNotNull { ((it as? JsonObject)?.get("row_id") ?: (it as? JsonObject)?.get("id"))?.let { p -> (p as JsonPrimitive).content } } }.toSet()
  }

  private fun pointAtServer() {
    val p = ServerProfile(id = "p-stuck", name = "سرورِ آزمون", urls = listOf(api), serverId = serverId, lan = true)
    val base = ServerSwitchAndroid.profiles(ctx)
    val added = (base.add(p) as? ServerProfiles.Change.Ok)?.profiles ?: base
    ServerSwitchAndroid.save(ctx, (added.activate(p.id) as ServerProfiles.Change.Ok).profiles)
    ServerSwitchAndroid.setEnabledRaw(ctx, true)
    ServerSwitchAndroid.recheck(ctx)
    check(ServerSwitchAndroid.activeUrl(ctx) == api) { "سرورِ آزمون برگزیده نشد: ${ServerSwitchAndroid.activeUrl(ctx)}" }
  }

  companion object {
    private var proc: Process? = null
    private var api = ""
    private var ctlUrl = ""
    private var serverId = ""
    private var skip: String? = null
    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).proxy(HttpClient.Builder.NO_PROXY).connectTimeout(Duration.ofSeconds(10)).build()

    @BeforeClass @JvmStatic fun up() {
      val root = OfflineBackend.repoRoot()?.takeIf { File(it, "server/test/e2e/lib/newinstall-backend.mjs").isFile }
      skip = OfflineBackend.available(root)
      if (skip != null) return
      val work = File(System.getProperty("java.io.tmpdir"), "stuck-${System.nanoTime()}").apply { mkdirs() }
      val out = File(work, "backend.json")
      val log = File(work, "backend.log")
      val p = ProcessBuilder("node", File(root, "server/test/e2e/lib/newinstall-backend.mjs").path, out.path)
        .directory(File(root, "server")).redirectErrorStream(true).redirectOutput(log).start()
      proc = p
      val until = System.currentTimeMillis() + 180_000
      while (!out.isFile || out.length() == 0L) {
        check(p.isAlive) { "سرورِ آزمون بالا نیامد:\n" + log.readText().takeLast(3000) }
        check(System.currentTimeMillis() < until) { "سرورِ آزمون در ۳ دقیقه بالا نیامد" }
        Thread.sleep(100)
      }
      val o = Json.parseToJsonElement(out.readText()).jsonObject
      api = o.str("api")
      ctlUrl = o.str("ctl")
      val info = http.send(HttpRequest.newBuilder(URI.create("$api/api/v1/config/server")).GET().build(), HttpResponse.BodyHandlers.ofString())
      serverId = Json.parseToJsonElement(info.body()).jsonObject.str("serverId")
    }

    @AfterClass @JvmStatic fun down() {
      runCatching { ctl("/stop", obj()) }
      proc?.let { p -> runCatching { p.outputStream.close() }; if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }
      if (skip == null) runCatching { NewInstallSyncDesktopTest.wipe(); SyncV1Engine.of(DesktopContext).rebind(force = true) }
    }

    fun ctl(path: String, body: JsonObject): JsonObject {
      val req = HttpRequest.newBuilder(URI.create(ctlUrl + path)).timeout(Duration.ofMinutes(3)).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
      val res = http.send(req, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
      check(res.statusCode() == 200) { "ctl $path: ${res.statusCode()} ${res.body().take(1500)}" }
      return Json.parseToJsonElement(res.body()).jsonObject
    }
  }
}
