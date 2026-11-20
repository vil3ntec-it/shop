package ir.vil3ntec.tohid.desktop.offline

import androidx.work.WorkerParameters
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.server.ServerProfile
import ir.vil3ntec.tohid.core.server.ServerProfiles
import ir.vil3ntec.tohid.data.AccountBind
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.platform.ServerSwitchAndroid
import ir.vil3ntec.tohid.scenario.FakeLivePort
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.ui.screens.ServerPulse
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
 *  عاملِ e-newinstall — «برنامهٔ تازه (`com.vill3n.dailyshop`) کنارِ برنامهٔ قدیم (`ir.vil3ntec.tohid`) نصب شد؛ وارد شدم ولی
 *  هیچ تنظیم و کالایی نیامد، همگام‌سازی: آخرین تلاش ناموفق، در صف: ۴ تغییر، «همگام‌سازی کامل نشد»».
 *
 *  سرتاسری روی **سرورِ واقعیِ مخزن** (`server/test/e2e/lib/newinstall-backend.mjs`، PGlite ِ در حافظه) و **همان موتورِ
 *  برنامه**: `SyncV1Engine` ِ یگانه، `ShopStore`، `AccountBind` (همان درِ ورود)، `SyncV1Worker.doWork` (همان دورِ پس‌زمینه).
 *  برنامهٔ قدیم یک دستگاهِ پروتکل است که دکانِ پُر را پیش‌تر به سرور فرستاده؛ «نصبِ تازه» یعنی پوشهٔ دادهٔ خالی و شناسهٔ
 *  دستگاهِ تازه. نشانیِ سرور از درِ «سرورها» (`ServerSwitchAndroid`، همان `ServerFailoverDesktopTest`).
 *
 *  بی node یا `server/node_modules` ⇒ رد با پیام (همان `assume` ِ `Offline30DaysDesktopTest`).
 */
class NewInstallSyncDesktopTest {

  private val ctx = DesktopContext
  private val engine get() = SyncV1Engine.of(ctx)

  @Before fun fresh() {
    assumeTrue(skip ?: "", skip == null)
    freshInstall()
  }

  /* ─────────────────────────── سناریوها ─────────────────────────── */

  /** (الف) حسابِ موجود با دکانِ پُر؛ نصبِ تازه با همان حساب وارد می‌شود ⇒ همه‌چیز می‌آید، همگام‌سازی سبز */
  @Test fun `الف — نصبِ تازه با همان حساب، کالا، موجودی و مشتری می‌آیند و همگام‌سازی کامل می‌شود`() = runBlocking {
    val shop = seed("a")
    val store = ShopStore(ctx).also { it.load() }
    signIn(store, shop)
    rounds(store)
    assertPulled(store, shop)
    assertGreen()
  }

  @Test fun `S76 — موتور واقعی پس از خبرهای تکراری و کهنه آخرین قیمت سرور را نگه می‌دارد`() = runBlocking {
    val shop = seed("s76")
    val store = ShopStore(ctx).also { it.load() }
    signIn(store, shop)
    rounds(store)
    val product = (shop.seed["products"] as JsonArray).first() as JsonPrimitive
    val id = product.content
    fun price(): Double = store.ledgerNow().products.single { it.id == id }.salePrice
    fun changePrice(value: Int): Long =
      ctl("/product-price", obj("shopId" to shop.shopId, "productId" to id, "price" to value)).str("cursor").toLong()

    val port = FakeLivePort()
    val pulled = mutableListOf<Long>()
    val live = port.socket {
      runBlocking { engine.runOnce(store::ledgerNow, store::applyFromServerNow).getOrThrow() }
      pulled += engine.state.cursor
    }
    try {
      live.connect()
      port.last.opens()
      port.last.says("""{"event":"hello","cursor":0}""")
      assertEquals(100.0, price(), 0.0)

      val early = changePrice(120)
      port.last.says("""{"event":"changed","cursor":$early}""")
      assertEquals(120.0, price(), 0.0)
      val missed = changePrice(150)
      val latest = changePrice(180)
      val serverBefore = ctl("/oplog", obj("shopId" to shop.shopId)).str("ops")
      for (cursor in listOf(latest, latest, missed, early)) {
        port.last.says("""{"event":"changed","cursor":$cursor}""")
        assertEquals(180.0, price(), 0.0)
        assertEquals(latest, engine.state.cursor)
      }
      assertEquals(6, pulled.size)
      assertEquals(pulled.sorted(), pulled)
      assertEquals(0, engine.queueSize())
      assertEquals(serverBefore, ctl("/oplog", obj("shopId" to shop.shopId)).str("ops"))
    } finally {
      live.disconnect()
    }
  }

  /** (ب) نصبِ تازه پیش از ورود چند کار روی همین دستگاه کرد (چند op در صف)؛ بعد با همان حساب وارد شد */
  @Test fun `ب — کارِ پیش از ورود روی نصبِ تازه، بعد ورود با همان حساب، هر دو روی سرور و دفتر`() = runBlocking {
    val shop = seed("b")
    val store = ShopStore(ctx).also { it.load() }
    val local = guestWork(store)
    signIn(store, shop)
    rounds(store)
    assertPulled(store, shop)
    for (id in local) assertTrue("ردیفِ پیش از ورود در دفتر ماند: $id", store.data.value.products.any { it.id == id } || store.data.value.debtors.any { it.id == id })
    assertGreen()
    val server = snapshot()
    for (id in local) assertTrue("ردیفِ پیش از ورود به سرور رسید: $id", id in server)
  }

  /** (ج) «ادامه بدون حساب» و کار؛ روزِ بعد ورود — همان (ب) با دکانِ خالیِ مهمان */
  @Test fun `ج — ادامه بدون حساب و بعد ورود، همگام‌سازی کامل می‌شود و صفِ مهمان گم نمی‌شود`() = runBlocking {
    val shop = seed("c")
    val store = ShopStore(ctx).also { it.load() }
    val local = guestWork(store)
    //  مهمان: دورِ پس‌زمینه هیچ کاری نمی‌کند (نشست نیست)
    rounds(store, 1)
    signIn(store, shop)
    rounds(store)
    assertPulled(store, shop)
    assertGreen()
    val server = snapshot()
    for (id in local) assertTrue("ردیفِ مهمان به سرور رسید: $id", id in server)
  }

  /** (د) حسابِ تازهٔ بی دکان ⇒ تشخیص «دکان انتخاب نشده»، نه «همگام‌سازی کامل نشد» */
  @Test fun `د — حسابِ تازهٔ بی دکان، تشخیص دکان، نه خطای ناشناخته`() = runBlocking {
    val acct = ctl("/account", obj("email" to "new-d-${System.nanoTime()}@example.com"))
    val store = ShopStore(ctx).also { it.load() }
    guestWork(store)
    signIn(store, Shop(acct.str("email"), acct.str("password"), acct.str("userId"), "", 0))
    rounds(store)
    val v1 = engine.status.value
    val d = diagnosis()
    println("د: lastError=«${v1.lastError}» code=${v1.errorCode} outage=${v1.outage} queued=${v1.queued} problem=${d.problem}")
    //  پیش از درمان: کدِ ماشینی دور ریخته می‌شد و حکمِ AUTH ِ هر ۴۰۳ «نشستِ حساب تمام شده» می‌گفت (پس از بازگشایی: «همگام‌سازی کامل نشد»)
    assertEquals("کدِ سرور کنارِ خطا ماند", "no_shop", v1.errorCode)
    assertEquals("بی دکان ⇒ NO_SHOP: ${v1.lastError}", SyncModel.Problem.NO_SHOP, d.problem)
    assertEquals(SyncModel.Fix.SHOP, d.fix)
    //  «بستن و باز کردنِ برنامه»: رشتهٔ شکستِ در حافظه نیست، کد ماندگار است
    engine.rebind(force = true)
    assertEquals(SyncModel.Problem.NO_SHOP, diagnosis().problem)
    //  دفترِ همین دستگاه دست نخورد (کارِ مهمان سرِ جایش)
    assertTrue(store.data.value.products.size >= 2)
  }

  /** همان تشخیصِ صفحهٔ همگام‌سازی (`SyncScreen`): خطا، کد و حکمِ موتور */
  private fun diagnosis(): SyncModel.Diagnosis {
    val v1 = engine.status.value
    return SyncModel.diagnose(
      v1.lastError.ifBlank { AutoSync.lastError.orEmpty() }, v1.holding, v1.dropped, AutoSync.needsShop, ServerPulse.State.UP,
      if (v1.lastError.isNotBlank()) v1.outage else null, code = if (v1.lastError.isNotBlank()) v1.errorCode else "", queued = v1.queued,
    )
  }

  /**
   *  (ه) برنامهٔ قدیم کارِ همگام‌نشده دارد ⇒ «پشتیبان و بازگردانی» در قدیم ⇐ «بازگردانی» در تازه (هر دو شناسهٔ نصب، همان
   *  کد): دفتر و سرور همان کارها را دارند و وقتی برنامهٔ قدیم بعداً همان صف را می‌فرستد، سرور «تکراری» می‌گوید — نه ردیفِ
   *  دوتایی، نه opِ دوباره. تنظیمِ دکانِ برنامهٔ قدیم از پشتیبانِ ابری می‌آید.
   */
  @Test fun `ه — کارِ نرفتهٔ برنامهٔ قدیم، پشتیبان در قدیم ⇐ بازگردانی در تازه، سرور یک بار و بی تکرار`() = runBlocking {
    val shop = seed("e")
    //  ── برنامهٔ قدیم (`ir.vil3ntec.tohid`) روی همین پوشه، پیش از «نصبِ تازه»
    val old = ShopStore(ctx).also { it.load() }
    signIn(old, shop)
    rounds(old)
    assertPulled(old, shop)
    ctx.getSharedPreferences("tohid-receipt", 0).edit().putString("e2e_footer", "سپاس از خرید شما").commit()
    assertTrue("پشتیبانِ ابریِ برنامهٔ قدیم رفت", ir.vil3ntec.tohid.data.AutoBackup.push(ctx, manual = true))
    //  سرور از این‌جا در دسترس نیست (همان «نشد» ِ برنامهٔ قدیمِ صاحب): کارها در صف می‌مانند
    serverReachable(false)
    val n = System.nanoTime().toString(36)
    val offlineProduct = "p-offline-$n"
    val offlineDebtor = "d-offline-$n"
    var d = old.data.value
    old.save(d, d.copy(products = d.products + Product(offlineProduct, name = "کالای بی‌همگام", unit = "عدد", salePrice = 90.0, createdAt = System.currentTimeMillis())))
    d = old.data.value
    old.save(d, d.copy(debtors = d.debtors + Debtor(offlineDebtor, name = "مشتریِ بی‌همگام", createdAt = System.currentTimeMillis())))
    rounds(old, 1)
    val pendingOld = engine.queuedOps()
    assertTrue("برنامهٔ قدیم کارِ نرفته دارد: ${pendingOld.size}", pendingOld.size >= 2)
    val oldDevice = engine.deviceId()
    val oldToken = Backend.tokens(ctx).accessToken.orEmpty()
    val backup = java.io.ByteArrayOutputStream()
    ir.vil3ntec.tohid.data.BackupRestore.writeFull(ctx, old, "دکانِ قدیمی") { backup }

    //  ── نصبِ تازه (`com.vill3n.dailyshop`): پوشهٔ خالی، همان حساب
    freshInstall()
    val store = ShopStore(ctx).also { it.load() }
    signIn(store, shop)
    rounds(store)
    assertPulled(store, shop)
    assertGreen()
    assertEquals("تنظیمِ دکانِ برنامهٔ قدیم از پشتیبانِ ابری آمد", "سپاس از خرید شما", ctx.getSharedPreferences("tohid-receipt", 0).getString("e2e_footer", null))
    assertTrue("کارِ نرفته هنوز روی سرور نیست", offlineProduct !in snapshot())

    val opened = ir.vil3ntec.tohid.data.BackupRestore.open(ctx, store, backup.toByteArray().inputStream()).getOrThrow()
    val carry = ir.vil3ntec.tohid.data.BackupRestore.carryOf(ctx, opened)
    assertEquals("صفِ نرفتهٔ برنامهٔ قدیم همراهِ پشتیبان", pendingOld.map { it.opId }.toSet(), carry.map { it.opId }.toSet())
    val r = ir.vil3ntec.tohid.data.BackupRestore.confirm(ctx, store, opened.data, carry)
    assertTrue("بازگردانی: ${r.message}", r.ok)
    rounds(store)
    val after = store.data.value
    assertTrue("کالای بی‌همگام در برنامهٔ تازه", after.products.count { it.id == offlineProduct } == 1)
    assertTrue("مشتریِ بی‌همگام در برنامهٔ تازه", after.debtors.count { it.id == offlineDebtor } == 1)
    val server = snapshot()
    assertTrue("کالای بی‌همگام روی سرور", offlineProduct in server)
    assertTrue("مشتریِ بی‌همگام روی سرور", offlineDebtor in server)
    assertPulled(store, shop)
    assertGreen()

    //  ── برنامهٔ قدیم بعداً به سرور می‌رسد و همان صف را می‌فرستد ⇒ همه «تکراری»، هیچ op ِ تازه
    val before = ctl("/oplog", obj("shopId" to shop.shopId))
    val res = rawPush(oldToken, oldDevice, pendingOld)
    val statuses = (res["results"] as JsonArray).map { ((it as JsonObject)["status"] as JsonPrimitive).content }
    assertTrue("برنامهٔ قدیم: همه تکراری — $statuses", statuses.isNotEmpty() && statuses.all { it == "duplicate" })
    val later = ctl("/oplog", obj("shopId" to shop.shopId))
    assertEquals("هیچ op ِ تازه‌ای از برنامهٔ قدیم", before.str("ops"), later.str("ops"))
    assertEquals("هیچ op_id ِ دوتایی", "0", later.str("duplicates"))
    rounds(store)
    assertEquals("کالا یک بار", 1, store.data.value.products.count { it.id == offlineProduct })
    assertGreen()
  }

  /* ─────────────────────────── ابزار ─────────────────────────── */

  /** سرورِ آزمون «در دسترس نیست» از نگاهِ همین دستگاه (پروفایلِ بسته ⇒ `AppConfig.isConfigured` نه) — یا دوباره باز */
  private fun serverReachable(on: Boolean) {
    ctx.getSharedPreferences("tohid-servers", 0).edit().putBoolean("blocked:p-newinstall", !on).commit()
    ServerSwitchAndroid.setEnabledRaw(ctx, true)
  }

  /** push ِ خامِ برنامهٔ قدیم با نشستِ خودش و شناسهٔ دستگاهِ خودش — همان بدنهٔ `SyncV1Client.pushBody` */
  private fun rawPush(token: String, device: String, ops: List<ir.vil3ntec.tohid.sync.v1.SyncOp>): JsonObject {
    val body = ir.vil3ntec.tohid.sync.v1.SyncV1Client.pushBody(device, ops, ops.size, caps = true, schema = ir.vil3ntec.tohid.sync.v1.LedgerDiff.BASE_SCHEMA, since = null)
    val req = HttpRequest.newBuilder(URI.create("$api/api/v1/sync/v1/push")).timeout(Duration.ofMinutes(1))
      .header("Content-Type", "application/json").header("Authorization", "Bearer $token").header("X-App", "shop")
      .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
    val res = http.send(req, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
    check(res.statusCode() == 200) { "push ِ برنامهٔ قدیم: ${res.statusCode()} ${res.body().take(600)}" }
    return Json.parseToJsonElement(res.body()).jsonObject
  }

  data class Shop(val email: String, val password: String, val userId: String, val shopId: String, val serverRows: Int, val seed: JsonObject = JsonObject(emptyMap()))

  private fun seed(tag: String): Shop {
    val s = ctl("/seed", obj("email" to "owner-$tag-${System.nanoTime()}@example.com", "products" to 3))
    val rows = (s["rows"] as JsonObject).values.sumOf { (it as JsonPrimitive).content.toInt() }
    return Shop(s.str("email"), s.str("password"), s.str("userId"), s.str("shopId"), rows, s)
  }

  /** «ادامه بدون حساب» و چند کار: کالا، کالای دوم، مشتری (نخستین ذخیره پایهٔ سایه است، بقیه op) */
  private suspend fun guestWork(store: ShopStore): List<String> {
    val n = System.nanoTime().toString(36)
    val ids = listOf("p-local-1-$n", "p-local-2-$n", "d-local-$n")
    var d = store.data.value
    store.save(d, d.copy(products = d.products + Product(ids[0], name = "کالای محلی ۱", unit = "عدد", salePrice = 50.0, createdAt = System.currentTimeMillis())))
    d = store.data.value
    store.save(d, d.copy(products = d.products + Product(ids[1], name = "کالای محلی ۲", unit = "عدد", salePrice = 70.0, createdAt = System.currentTimeMillis())))
    d = store.data.value
    store.save(d, d.copy(debtors = d.debtors + Debtor(ids[2], name = "مشتریِ محلی", createdAt = System.currentTimeMillis())))
    println("مهمان: صف=${engine.queueSize()} کلید=${engine.currentKey()}")
    return ids
  }

  /** همان درِ ورود (`AuthController.finish`): نشست ⇒ `AccountBind.signedIn` ⇒ حساب ⇒ `bootstrapSoon` ⇒ `afterSignIn` */
  private suspend fun signIn(store: ShopStore, shop: Shop) {
    val r = Backend.auth(ctx).login(shop.email, shop.password)
    val session = (r as? ApiResult.Success)?.value ?: error("ورود نشد: $r")
    session.shop?.role?.let { ShopRole.remember(ctx, it) }
    AccountBind.signedIn(ctx, store, session.user.id, session.shop?.id.orEmpty(), session.user.name)
    SyncStore(ctx).rememberAccount(session.user, session.user.name)
    AccountBind.bootstrapSoon(ctx, store)?.join()
    //  `shell.afterSignIn` ⇒ `AppRoot.askShop`: دکانِ حساب از سرور
    (Backend.shop(ctx).current() as? ApiResult.Success)?.value?.let { st ->
      if (st.hasShop) {
        ShopRole.rememberShopId(ctx, st.shop?.id)
        LedgerOwner.shopChanged(ctx, store, st.shop?.id.orEmpty())
        AccountBind.healOnStart(ctx, store)
      }
    }
  }

  /** دورهای کارگرِ پس‌زمینه (`SyncV1Worker.doWork`) */
  private suspend fun rounds(store: ShopStore, n: Int = 3) {
    repeat(n) { SyncV1Worker(ctx, WorkerParameters()).doWork() }
    store.load()
  }

  private fun assertPulled(store: ShopStore, shop: Shop) {
    val d = store.data.value
    val seeded = shop.seed["rows"] as JsonObject
    fun want(k: String) = (seeded[k] as JsonPrimitive).content.toInt()
    println("دفتر: کالا=${d.products.size} ورود=${d.warehouseEntries.size} مشتری=${d.debtors.size} فروش=${d.sales.size} | سرور=$seeded | صف=${engine.queueSize()} خطا=«${engine.status.value.lastError}»")
    for (id in (shop.seed["products"] as JsonArray).map { (it as JsonPrimitive).content }) {
      assertTrue("کالای دکان روی نصبِ تازه آمد: $id", d.products.any { it.id == id })
    }
    assertTrue("ورودِ انبار آمد", d.warehouseEntries.size >= want("warehouseEntries"))
    assertTrue("مشتری آمد", d.debtors.any { it.id == shop.seed.str("debtor") })
    assertTrue("فروش آمد", d.sales.size >= want("sales"))
  }

  private fun assertGreen() {
    val v1 = engine.status.value
    val d = diagnosis()
    assertEquals("همگام‌سازی بی خطا: «${v1.lastError}» (${d.problem})", "", v1.lastError)
    assertEquals("صف خالی", 0, v1.queued)
    assertTrue("آخرین همگام‌سازیِ موفق ثبت شد", v1.lastOkAt > 0)
    assertEquals(SyncModel.Problem.NONE, d.problem)
  }

  /** شناسهٔ همهٔ ردیف‌های Snapshot ِ سرور برای نشستِ همین حالا */
  private suspend fun snapshot(): Set<String> {
    val body = Backend.api(ctx).get("/sync/v1/snapshot")
    val tables = body["tables"] as? JsonObject ?: return emptySet()
    return tables.values.flatMap { t -> (t as? JsonArray).orEmpty().mapNotNull { ((it as? JsonObject)?.get("row_id") ?: (it as? JsonObject)?.get("id"))?.let { p -> (p as JsonPrimitive).content } } }.toSet()
  }

  /**
   *  «نصبِ تازه»: پوشهٔ دادهٔ برنامه خالی — دفتر، صف، سایه، ژورنال و همهٔ ترجیحات (نشست، شناسهٔ دستگاه، دکانِ روی میز)؛
   *  همان شیءهای ترجیح (`DesktopPrefs`) پاک می‌شوند، نه عوض، تا تکه‌های برنامه که نگهشان داشته‌اند همان را ببینند.
   */
  private fun freshInstall() {
    wipe()
    pointAtServer()
    engine.rebind(force = true)
  }

  private fun pointAtServer() {
    val p = ServerProfile(id = "p-newinstall", name = "سرورِ آزمون", urls = listOf(api), serverId = serverId, lan = true)
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
    //  HTTP/1.1: پیش‌فرضِ `HttpClient` درخواستِ `h2c` (Upgrade) است و سرورِ node (با سوکتِ زندهٔ `ws`) آن اتصال را می‌بندد
    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).proxy(HttpClient.Builder.NO_PROXY).connectTimeout(Duration.ofSeconds(10)).build()

    @BeforeClass @JvmStatic fun up() {
      val root = OfflineBackend.repoRoot()?.takeIf { File(it, "server/test/e2e/lib/newinstall-backend.mjs").isFile }
      skip = OfflineBackend.available(root)
      if (skip != null) return
      val work = File(System.getProperty("java.io.tmpdir"), "newinstall-${System.nanoTime()}").apply { mkdirs() }
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

    /**
     *  پوشهٔ دادهٔ برنامه خالی (همان «نصبِ تازه»): دفتر، صف، سایه، ژورنال و همهٔ ترجیحات (نشست، شناسهٔ دستگاه، دکانِ روی میز،
     *  «سرورها»)؛ همان شیءهای ترجیح (`DesktopPrefs`) پاک می‌شوند، نه عوض، تا تکه‌های برنامه که نگهشان داشته‌اند همان را ببینند.
     */
    fun wipe() {
      val ctx = DesktopContext
      runCatching { androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork("tohid-sync-v1-now") }
      File(ctx.filesDir.parentFile, "shared_prefs").listFiles().orEmpty().filter { it.name.endsWith(".json") }.forEach { f ->
        ctx.getSharedPreferences(f.name.removeSuffix(".json"), 0).edit().clear().commit()
      }
      ctx.filesDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
      //  حافظهٔ سایهٔ هر کلید (همان «باز شدنِ دوبارهٔ برنامه») — نصبِ تازه هیچ سایه‌ای از نصبِ پیشین ندارد
      ir.vil3ntec.tohid.sync.v1.ShadowStore.restartForTest()
      ServerSwitchAndroid.setEnabledRaw(ctx, false)
    }

    @AfterClass @JvmStatic fun down() {
      runCatching { ctl("/stop", obj()) }
      proc?.let { p -> runCatching { p.outputStream.close() }; if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }
      //  آزمون‌های بعدیِ همین فرایند (پشتیبان، بازیابی…) با پوشهٔ خالی و بی نشست شروع کنند — همان حالِ پیش از این کلاس
      if (skip == null) runCatching { wipe(); SyncV1Engine.of(DesktopContext).rebind(force = true) }
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
