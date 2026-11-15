package ir.vil3ntec.tohid.desktop.offline

import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.sync.v3.FileV3Store
import ir.vil3ntec.tohid.sync.v3.HttpV3Transport
import ir.vil3ntec.tohid.sync.v3.SyncV3Engine
import ir.vil3ntec.tohid.sync.v3.V3Convert
import ir.vil3ntec.tohid.sync.v3.V3Ledger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/**
 *  S26 (SYN-01، SYN-04) — «۳۰ روز آفلاین روی ۳ دستگاه، ۱۰۰ هزار op، بعد وصل روی 2G»: کامپیوترِ صاحب و دو گوشیِ شاگرد
 *  با همان موتورها و همان صف/سایه/ژورنالِ `Offline30DaysDesktopTest` ([ShopDevice])، روبه‌روی سرورِ واقعی، و هر کدام
 *  روی **خطِ 2G ِ خودش** (`server/test/e2e/lib/link-2g.mjs`: RTT ۶۰۰ms، ۵۰ kbit/s در هر سو، ۲٪ گم‌شدنِ بسته با RTO،
 *  ۲٪ بریدن در میانهٔ درخواست یا پاسخ + سه بریدنِ قطعی، مهلتِ خواندنِ ۳۰ ثانیه‌ایِ برنامه).
 *
 *  سنجه‌ها: هر op دقیقاً یک بار روی سرور؛ دفترِ هر سه دستگاه **بایت‌به‌بایت** (شکلِ مرتبِ `modelTree`) = Snapshot ِ سرور؛
 *  دسته ≤ ۲۰۰ op و ≤ ۲۵۶KB، `fields` ِ هر op ≤ ۶۴KB و هیچ درخواستِ دیگری بالای ۶۴KB؛ پس از هر بریدن ادامه از همان‌جا
 *  (هیچ op ِ پذیرفته‌ای دوباره نمی‌رود)؛ فاصلهٔ تلاشِ دوباره همان نماییِ `SyncV1Worker.now` (۱۰ ثانیه، دوبرابر) و هیچ
 *  تکرارِ پشتِ سرِ همِ push روی سیم جز یک تلاشِ بی‌صدای خودِ `HttpURLConnection`. زمانِ «2G» از ساعتِ خط (`vClockMs`).
 *
 *  ── اندازه ──────────────────────────────────────────────────────────────────────────────────────────────
 *   پیش‌فرض (همیشه، سبک): `S26_OPS` = ۱۲۰۰ و خطِ 2G با زمانِ واقعی (`S26_SPEED` = ۱). سنگین (دستی):
 *   `S26_OPS=100000 S26_SPEED=1e9` — خط بی انتظارِ واقعی ولی همان بایت/بسته/بریدن و همان ساعتِ 2G (مدلِ سنجیده با
 *   `server/test/link-2g.test.js` و همین آزمون با `S26_SPEED=1`)؛ `S26_SAVE_EVERY` (سنگین ۲۰): چند کارِ دکان در یک ثبت
 *   (`SyncCommit.record`) — هر ثبت کلِ دفتر و صف را می‌نویسد، پس با ۳۳ هزار op ِ صف ثبتِ تک‌تک ساعت‌ها طول می‌کشد.
 *   `S26_ONLY=s26|syn04|v3` فقط همان یکی؛ `S26_REPORT=<پرونده.json>` عددها را می‌نویسد؛ `S26_CLASSPATH=<پرونده>` classpath ِ آزمون را (اجرای سنگین با `java` بی قفلِ
 *   Gradle). با `cleanTest` تا «UP-TO-DATE» نشود.
 *  مرزِ راست‌گویی: شبیه‌سازیِ JVM روی کانتینر، نه گوشیِ واقعی؛ دو «گوشی» همان کدِ `HttpEngine` روی JVM‌اند
 *  (`HttpURLConnection` ِ JDK `Accept-Encoding: gzip` نمی‌فرستد؛ OkHttp ِ اندروید می‌فرستد).
 */
class S26Load2GDesktopTest {

  private val target = env("S26_OPS")?.toIntOrNull() ?: 1200
  private val heavy = target > 5_000
  private val days = env("S26_DAYS")?.toIntOrNull() ?: 30
  private val speed = env("S26_SPEED")?.toDoubleOrNull() ?: 1.0
  private val saveEvery = env("S26_SAVE_EVERY")?.toIntOrNull() ?: if (heavy) 20 else 1
  private val work = File(System.getProperty("java.io.tmpdir"), "s26-${System.nanoTime().toString(36)}")

  @After fun clean() { work.deleteRecursively() }

  /* ════════════════════════ کارِ دکان ════════════════════════ */

  /** چند کار، یک ثبت (`SyncCommit.record`) — [every] = ۱ همان رفتارِ برنامه؛ [also]: ثبتِ v3 ِ همان دفتر (`SyncV3Engine.record`) */
  private class Stager(val dev: ShopDevice, val every: Int, val also: ((ShopData) -> Unit)? = null) {
    var n = 0
    fun put(next: ShopData) { dev.ledger = next; if (++n >= every) flush() }
    fun flush() { if (n > 0) { dev.save(dev.ledger); also?.invoke(dev.ledger); n = 0 } }
  }

  private fun newId(): String = Ulid.next()

  private fun Stager.sell(rnd: Random, clock: ShopClock, owner: Boolean, debtors: List<String>) {
    val d = dev.ledger
    val cart = d.products.shuffled(rnd).take(1 + rnd.nextInt(3)).map { SalesEngine.CartLine(it.id, (1 + rnd.nextInt(3)).toDouble()) }
    val credit = rnd.nextInt(100) < 22
    val discount = owner && rnd.nextInt(100) < 35   //  شاگرد بی برگهٔ تأیید تخفیف نمی‌دهد
    val checkout = SalesEngine.Checkout(
      seller = dev.seller,
      discountType = if (rnd.nextBoolean()) SalesEngine.DiscountType.PERCENT else SalesEngine.DiscountType.AMOUNT,
      discountValue = if (!discount) 0.0 else (3 + rnd.nextInt(8)).toDouble(),
      payment = if (credit) SalesEngine.Payment.CREDIT else SalesEngine.Payment.CASH,
      paidAmount = if (credit) (rnd.nextInt(3) * 20).toDouble() else 0.0,
      debtorId = if (credit) debtors[rnd.nextInt(debtors.size)] else null,
    )
    (SalesEngine.record(d, cart, checkout, clock.today, clock.now, ::newId, series = dev.series) as? SalesEngine.Result.Ok)?.let { put(it.data) }
  }

  private fun Stager.giveBack(rnd: Random, clock: ShopClock) {
    val sale = dev.ledger.sales.filter { it.seller == dev.seller && it.status != "cancelled" }.takeLast(40).randomOrNull(rnd) ?: return
    val item = dev.ledger.saleItems.firstOrNull { it.saleId == sale.id && SalesEngine.returnable(it) >= 1.0 } ?: return
    (SalesEngine.recordReturn(dev.ledger, sale.id, mapOf(item.id to 1.0), "مشتری پس آورد", clock.today, clock.now, ::newId, by = dev.seller) as? SalesEngine.Result.Ok)?.let { put(it.data) }
  }

  private fun Stager.payDebt(rnd: Random, clock: ShopClock, debtors: List<String>) {
    val who = debtors.filter { ShopStore.debt(dev.ledger, it) > 10 }.randomOrNull(rnd) ?: return
    val amount = minOf(ShopStore.debt(dev.ledger, who), (10 + rnd.nextInt(10) * 10).toDouble())
    (DebtorEngine.addTransaction(dev.ledger, who, DebtorEngine.Kind.RECEIVE, amount, clock.today, "پرداختِ قرض", clock.today, clock.now, ::newId) as? DebtorEngine.Result.Ok)?.let { put(it.data) }
  }

  private fun Stager.receive(rnd: Random, clock: ShopClock) {
    val p = dev.ledger.products.random(rnd)
    val draft = WarehouseEngine.EntryDraft(productId = p.id, units = (20 + rnd.nextInt(60)).toDouble(), unit = p.unit, price = p.purchasePrice)
    (WarehouseEngine.addEntry(dev.ledger, draft, clock.today, clock.now, ::newId) as? WarehouseEngine.Result.Ok)?.let { put(it.data) }
  }

  private fun Stager.count(rnd: Random, clock: ShopClock) {
    val p = dev.ledger.products.random(rnd)
    val counted = maxOf(0.0, ShopStore.stock(dev.ledger, p.id) + (rnd.nextInt(5) - 2))
    (WarehouseEngine.stockCount(dev.ledger, mapOf(p.id to counted), "انبارگردانیِ شب", clock.today, clock.now, ::newId, kind = WarehouseEngine.AdjustKind.COUNT) as? WarehouseEngine.Result.Ok)?.let { put(it.data) }
  }

  /** «همان کالا از چند دستگاه»: قیمتِ یکی از پنج کالای پرفروش — هر سه دستگاه، بی خبر از هم */
  private fun Stager.reprice(rnd: Random, clock: ShopClock, hot: List<String>) {
    val id = hot[rnd.nextInt(hot.size)]
    val p = dev.ledger.products.firstOrNull { it.id == id } ?: error("${dev.label}: کالای پرفروشِ $id نیست (${dev.ledger.products.size} کالا)")
    val draft = WarehouseEngine.ProductDraft(name = p.name, category = p.category, unit = p.unit, purchasePrice = p.purchasePrice, salePrice = (100 + rnd.nextInt(60)).toDouble(), minStock = p.minStock)
    (WarehouseEngine.editProduct(dev.ledger, p.id, draft, clock.today, clock.now, ::newId) as? WarehouseEngine.Result.Ok)?.let { put(it.data) }
  }

  private fun Stager.shift(open: Boolean, clock: ShopClock) {
    val r = if (open) CashShift.open(dev.ledger, 1000.0, dev.seller, clock.today, clock.now, ::newId)
      else CashShift.close(dev.ledger, CashShift.totalsOf(dev.ledger, CashShift.current(dev.ledger)!!, clock.now).expected, "بستنِ شب", dev.seller, clock.today, clock.now, ::newId)
    (r as? CashShift.Result.Ok)?.let { put(it.data) }
  }

  private fun Stager.day(rnd: Random, clock: ShopClock, n: Int, owner: Boolean, debtors: List<String>, hot: List<String>, d: Int, startMin: Int) {
    clock.at(d, startMin)
    shift(true, clock)
    val step = (12 * 3600_000L) / (n + 2)
    for (i in 0 until n) {
      clock.tick(step)
      val roll = rnd.nextInt(100)
      when {
        roll < 52 -> sell(rnd, clock, owner, debtors)
        roll < 62 -> payDebt(rnd, clock, debtors)
        roll < 70 -> giveBack(rnd, clock)
        roll < 78 -> receive(rnd, clock)
        roll < 82 && owner -> count(rnd, clock)
        roll < 92 -> reprice(rnd, clock, hot)
        else -> sell(rnd, clock, owner, debtors)
      }
    }
    clock.tick(step)
    shift(false, clock)
    flush()
  }

  private fun seed(dev: ShopDevice, clock: ShopClock) {
    val names = listOf("برنج", "شکر", "روغن", "چای", "آرد", "نمک", "صابون", "رب", "عدس", "لوبیا")
    repeat(40) { i ->
      clock.tick(30_000)
      val draft = WarehouseEngine.ProductDraft(name = "${names[i % names.size]} ${i + 1}", category = "خوراکی", unit = "عدد", purchasePrice = 70.0, salePrice = 100.0 + i)
      val r = WarehouseEngine.addProduct(dev.ledger, draft, clock.now, ::newId) as WarehouseEngine.Result.Ok
      dev.save(r.data)
      val e = WarehouseEngine.addEntry(dev.ledger, WarehouseEngine.EntryDraft(productId = r.id, units = 5000.0, unit = "عدد", price = 70.0), clock.today, clock.now, ::newId) as WarehouseEngine.Result.Ok
      dev.save(e.data)
    }
    repeat(14) { i ->
      val r = DebtorEngine.add(dev.ledger, DebtorEngine.DebtorDraft(name = "مشتریِ ${i + 1}", phone = "07990000${10 + i}"), clock.now, ::newId) as DebtorEngine.Result.Ok
      dev.save(r.data)
    }
  }

  /* ════════════════════════ خطِ 2G و بازگشت ════════════════════════ */

  private fun linkCfg(seed: Int, v3: Boolean = false): JsonObject = buildJsonObject {
    put("rttMs", env("S26_RTT")?.toDoubleOrNull() ?: 600.0)
    put("upBps", (env("S26_KBPS")?.toDoubleOrNull() ?: 50.0) * 1000)
    put("downBps", (env("S26_KBPS")?.toDoubleOrNull() ?: 50.0) * 1000)
    put("loss", env("S26_LOSS")?.toDoubleOrNull() ?: 0.02)
    put("rtoMs", 1200)
    put("dropRate", env("S26_DROP")?.toDoubleOrNull() ?: 0.02)
    put("readTimeoutMs", 30_000)   //  `ApiConfig.READ_TIMEOUT_MS`
    put("sendBufBytes", 64 * 1024)
    put("speed", speed)
    put("seed", seed)
    //  سه بریدنِ قطعی روی هر خط: push پیش از رسیدن به سرور، پاسخِ push پس از اعمال، و پاسخِ pull
    put("forced", JsonArray(listOf(Triple("push", 2, "up"), Triple("push", 4, "down"), Triple("pull", 2, "down")).map { (k, n, dir) ->
      buildJsonObject { put("kind", if (v3) "v3$k" else k); put("nth", n); put("dir", dir); put("frac", if (dir == "up") 0.5 else 0.6) }
    }))
  }

  /** `SyncV1Worker.now`: `BackoffPolicy.EXPONENTIAL` از ۱۰ ثانیه (سقفِ ۵ دقیقه، `SyncV1Engine.MAX_BACKOFF_MS`) */
  private fun backoffMs(attempt: Int): Long = minOf(300_000L, 10_000L shl (attempt - 1).coerceIn(0, 10))

  private class Run(val dev: ShopDevice) {
    var syncs = 0
    val failures = ArrayList<String>()
    val waits = ArrayList<Long>()
    var backoffVirtualMs = 0L
    var backoffRealMs = 0L
    var wallMs = 0L
    var link: JsonObject = JsonObject(emptyMap())
  }

  /** یک دستگاه پس از بازگشت: دور تا صفِ خالی و pull ِ تا ته؛ شکست ⇒ همان فاصلهٔ نمایی */
  private suspend fun session(be: OfflineBackend, run: Run) {
    val t0 = System.nanoTime()
    var attempt = 0
    while (true) {
      run.syncs++
      val r = run.dev.sync()
      if (r.isSuccess) {
        attempt = 0
        if (run.dev.queue.size() == 0 && !run.dev.lastHasMore) break
        continue
      }
      run.failures += (r.exceptionOrNull()?.let { "${it::class.simpleName}: ${it.message?.take(60)}" } ?: "?")
      attempt++
      check(run.failures.size <= 60) { "${run.dev.label}: بیش از ۶۰ شکست — ${run.failures.takeLast(5)}" }
      val wait = backoffMs(attempt)
      run.waits += wait
      val real = (wait / speed).toLong()
      run.backoffVirtualMs += wait
      run.backoffRealMs += real
      delay(real)
    }
    run.wallMs = (System.nanoTime() - t0) / 1_000_000
    run.link = be.call("GET", "/links").obj(run.dev.label)
  }

  /* ════════════════════════ شکلِ مرتبِ دفتر (بایت‌به‌بایت) ════════════════════════ */

  private fun sorted(e: JsonElement): JsonElement = when (e) {
    is JsonObject -> JsonObject(e.entries.sortedBy { it.key }.associate { it.key to sorted(it.value) })
    is JsonArray -> JsonArray(e.map { sorted(it) })
    else -> e
  }

  /** هر ردیفِ همگام‌شدنی از درِ مدل (`modelTree`) با کلیدهای مرتب: «جدول/شناسه» ⇒ متن */
  private fun canonRows(tree: JsonObject): Map<String, String> {
    val model = LedgerDiff.modelTree(tree)
    val out = java.util.TreeMap<String, String>()
    for (t in LedgerDiff.COLLECTIONS + LedgerDiff.LIST_COLLECTIONS) for (r in (model[t] as? JsonArray).orEmpty()) {
      val id = ((r as? JsonObject)?.get("id") as? JsonPrimitive)?.content ?: r.toString()
      out["$t/$id"] = sorted(r).toString()
    }
    return out
  }

  /** همهٔ مجموعه‌ها، ردیف‌ها به ترتیبِ «جدول/شناسه» و کلیدها مرتب ⇒ sha256 و اندازه */
  private fun canon(tree: JsonObject): Pair<String, Int> {
    val sb = StringBuilder()
    for ((k, v) in canonRows(tree)) sb.append(k).append('=').append(v).append('\n')
    val bytes = sb.toString().toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } to bytes.size
  }

  /** «بایت‌به‌بایت» با شرحِ نخستین ردیف‌های ناهمسان */
  private fun assertSame(label: String, want: JsonObject, got: JsonObject) {
    val a = canonRows(want)
    val b = canonRows(got)
    if (a == b) return
    val keys = (a.keys + b.keys).filter { a[it] != b[it] }
    val first = keys.take(3).joinToString("\n") { "  $it\n    سرور: ${a[it]?.take(400)}\n    دستگاه: ${b[it]?.take(400)}" }
    throw AssertionError("$label: ${keys.size} ردیفِ ناهمسان از ${a.size}/${b.size}\n$first")
  }

  /* ════════════════════════ آزمون‌ها ════════════════════════ */

  @Test fun `S26 سه دستگاه ۳۰ روز بی اینترنت و بازگشت روی 2G ⇒ هر op یک بار و هر چهار دفتر یکسان`() =
    scenario("s26", target, gzip = false)

  @Test fun `SYN-04 همان خطِ 2G با پرچم‌های فشرده‌سازی ⇒ push ِ بزرگ فشرده و همان همگرایی`() =
    scenario("syn04", if (heavy && env("S26_GZIP") != null) target else 600, gzip = true)

  /**
   *  v3 (پیش‌فرض خاموش: `SYNC_V3_ENABLED` ِ سرور و `SyncChoice` ِ دستگاه): همان سه دستگاه و همان کار، با موتورِ واقعیِ
   *  `SyncV3Engine` + `HttpV3Transport` + `FileV3Store` ِ برنامه، و همان پرچمِ `sync.gzip_push`. روزِ ۳۱ برنامه از نو باز
   *  می‌شود (موتور و ترابری تازه — همان `SyncV3Runner` پس از بسته شدن). فاصلهٔ تلاش همان `V3Step.retryInMs` ِ موتور.
   */
  @Test fun `SYN-04 v3 پشتِ SYNC_V3_ENABLED روی همان خطِ 2G ⇒ هر op یک بار و همگرایی`() {
    only("v3")
    val root = OfflineBackend.repoRoot()
    val missing = OfflineBackend.available(root)
    assumeTrue("سرورِ واقعی در دسترس نیست: $missing", missing == null)
    work.mkdirs()
    OfflineBackend.start(root!!, File(work, "backend-v3.log"), mapOf("SYNC_V3_ENABLED" to "1")).use { be ->
      runBlocking { playV3(be, if (heavy && env("S26_V3") != null) target else 600) }
    }
  }

  private fun v3Engine(d: ShopDevice): SyncV3Engine = SyncV3Engine(
    HttpV3Transport(d.api), FileV3Store(File(work, "v3/${d.label}/state").apply { mkdirs() }), d.deviceId,
    object : V3Ledger { override fun read(): ShopData = d.ledger; override fun write(next: ShopData) { d.ledger = next } },
  ).load()

  private suspend fun v3Sync(e: SyncV3Engine, waits: MutableList<Long>? = null) {
    var k = 0
    while (true) {
      val st = e.sync(false)
      if (st.ok && e.queued() == 0) return
      check(!st.stopped && ++k < 60) { "v3: ${st} ${e.status()}" }
      if (!st.ok) { waits?.add(st.retryInMs); delay((st.retryInMs / speed).toLong()) }
    }
  }

  private suspend fun playV3(be: OfflineBackend, ops: Int) {
    val t0 = System.currentTimeMillis()
    val rnd = Random(263)
    val setup = be.call("POST", "/setup", obj("subscriptionDays" to 120, "extraStaff" to 1))
    val o = setup.obj("owner")
    val s = setup.obj("staff")
    val s2 = (setup["more"] as JsonArray)[0] as JsonObject
    val direct = setup.str("api")
    val clock = ShopClock(System.currentTimeMillis())
    val A = ShopDevice("owner-pc", o.str("deviceId"), o.str("userId"), "صاحب", direct, MemTokens(o.str("access"), o.str("refresh")), File(work, "v3/A"), "1.0.9999")
    val B = ShopDevice("staff-phone-1", s.str("deviceId"), s.str("userId"), "علی", direct, MemTokens(s.str("access"), s.str("refresh")), File(work, "v3/B"), "3.2.9999")
    val C = ShopDevice("staff-phone-2", s2.str("deviceId"), s2.str("userId"), "شاگرد 2", direct, MemTokens(s2.str("access"), s2.str("refresh")), File(work, "v3/C"), "3.2.9999")
    val devs = listOf(A, B, C)
    for (k in listOf("sync.gzip_push", "sync.gzip_pull")) be.call("POST", "/flag", obj("key" to k, "value" to true))
    be.serverDay(0.0)
    clock.at(0, 6 * 60)
    val engines = HashMap<ShopDevice, SyncV3Engine>()
    for (d in devs) {
      d.seatEmpty()   //  صفِ v1 ِ همین دستگاه هرگز نمی‌رود — فقط تا `save` ِ موتورها کار کند
      engines[d] = v3Engine(d).also { assertTrue("${d.label}: راه‌اندازیِ v3", it.initialize(false).ok) }
      d.series = ApiJson.text(d.api.post("/shop/series", buildJsonObject { put("deviceId", d.deviceId) }), "series")
    }
    seed(A, clock)
    engines.getValue(A).record(A.ledger)
    for (d in devs + A) v3Sync(engines.getValue(d))
    val debtors = A.ledger.debtors.map { it.id }
    val hot = A.ledger.products.take(5).map { it.id }
    for (d in devs) assertEquals("${d.label}: روزِ صفر همهٔ کالاها", 40, d.ledger.products.size)
    val online0 = engines.values.sumOf { it.queued() }
    assertEquals(0, online0)

    for (d in devs) d.online = false
    val share = mapOf(A to 0.45, B to 0.30, C to 0.25)
    val stagers = devs.associateWith { d -> Stager(d, saveEvery) { engines.getValue(d).record(it) } }
    var perAction = 4.0
    var actions = 0
    //  شمار از صفِ v3 (صفِ v1 ِ این دستگاه‌ها ردیف‌های گرفته‌شده از v3 را هم «تغییر» می‌بیند و هرگز نمی‌رود)
    fun offlineOps() = devs.sumOf { engines.getValue(it).queued() }
    for (day in 1..days) {
      val budget = (ops - offlineOps()).coerceAtLeast(0).toDouble() / (days - day + 1)
      for (d in devs) {
        val n = maxOf(1, (budget * share.getValue(d) / perAction).toInt())
        stagers.getValue(d).day(rnd, clock, n, d === A, debtors, hot, day, if (d === A) 7 * 60 else 8 * 60)
        actions += n
      }
      perAction = offlineOps().toDouble() / maxOf(1, actions)
    }
    val made = devs.associateWith { d -> engines.getValue(d).queue.outbox.flatMap { e -> e.ops.mapNotNull { (it["op_id"] as? JsonPrimitive)?.content } }.toSet() }
    val offlineTotal = made.values.sumOf { it.size }
    val offlineMs = System.currentTimeMillis() - t0

    /* ── روزِ ۳۱: برنامه از نو (موتور و ترابریِ تازه)، هر دستگاه روی خطِ خودش، هم‌زمان ──
     *  ساعتِ سرور جابه‌جا نمی‌شود: موتورِ v3 ساعتِ واقعیِ دستگاه را با `server_time` می‌سنجد (سقفِ ۵ دقیقه) و ساعتِ
     *  دکانِ این آزمون فقط در سندها جلو می‌رود ⇒ جابه‌جاییِ ۳۱ روزهٔ سرور «ساعتِ کج» ِ ساختگی می‌ساخت. */
    val waits = devs.associateWith { ArrayList<Long>() }
    for ((i, d) in devs.withIndex()) {
      engines[d] = v3Engine(d)
      d.apiUrl = be.call("POST", "/link", obj("id" to d.label, "cfg" to linkCfg(360 + i, v3 = true))).str("url")
      d.online = true
    }
    val tNet = System.currentTimeMillis()
    val walls = HashMap<ShopDevice, Long>()
    val linkAt = HashMap<ShopDevice, JsonObject>()
    coroutineScope {
      devs.map { d -> async(Dispatchers.IO) {
        val t = System.nanoTime()
        v3Sync(engines.getValue(d), waits.getValue(d))
        synchronized(walls) { walls[d] = (System.nanoTime() - t) / 1_000_000; linkAt[d] = be.call("GET", "/links").obj(d.label) }
      } }.awaitAll()
    }
    val parallelMs = System.currentTimeMillis() - tNet
    for (d in devs) be.call("POST", "/link-set", obj("id" to d.label, "cfg" to buildJsonObject { put("dropRate", 0.0) }))
    repeat(2) { for (d in devs) v3Sync(engines.getValue(d)) }

    val log = be.call("GET", "/oplog3")
    val ids = (log["ids"] as JsonArray).map { (it as JsonPrimitive).content }.toHashSet()
    assertEquals("v3: هیچ op_id ِ تکراری", "[]", log["duplicates"].toString())
    assertEquals(log.str("total"), log.str("distinct"))
    for (d in devs) {
      assertTrue("${d.label}: هر op ِ قطعیِ v3 روی سرور (${made.getValue(d).size})", made.getValue(d).isNotEmpty() && ids.containsAll(made.getValue(d)))
      assertEquals("${d.label}: صفِ v3 خالی", 0, engines.getValue(d).queued())
      assertEquals("${d.label}: ردشدهٔ v3", 0, engines.getValue(d).rejected().size)
    }
    for (d in devs) d.apiUrl = direct
    val server = LedgerDiff.fromSnapshot(V3Convert.snapshotToLegacy(A.api.get("/sync/v3/snapshot")), LedgerDiff.toJson(ShopData()))
    val want = canon(server)
    //  ویرایشِ هم‌زمانِ همان کالا: بازندهٔ LWW در صندوقِ تعارضِ v3 (SYN-07) می‌نشیند و دفترِ همان دستگاه تا «keep/revert» ِ
    //  صاحب مقدارِ خودش را نگه می‌دارد ⇒ هر ردیفِ ناهمسان باید یک تعارضِ باز داشته باشد؛ بقیه بایت‌به‌بایت
    val open = engines.getValue(A).fetchConflicts().orEmpty()
    val conflicted = open.mapTo(HashSet()) { "${(it["table"] as? JsonPrimitive)?.content}/${(it["row_id"] as? JsonPrimitive)?.content}" }
    val want3 = canonRows(server)
    val diverged = HashMap<String, Int>()
    for (d in devs) {
      val got = canonRows(LedgerDiff.toJson(d.ledger))
      val bad = (want3.keys + got.keys).filter { want3[it] != got[it] }
      diverged[d.label] = bad.size
      assertTrue("${d.label}: ردیفِ ناهمسانِ بی تعارضِ باز ${bad.filter { it !in conflicted }.take(3)}", bad.all { it in conflicted })
    }

    val report = ArrayList<String>()
    for (d in devs) {
      val l = linkAt.getValue(d)
      val kinds = l.obj("kinds")
      val push = kinds["v3push"] as? JsonObject ?: JsonObject(emptyMap())
      val pull = kinds["v3pull"] as? JsonObject ?: JsonObject(emptyMap())
      val wire = (l["log"] as JsonArray).map { it as JsonArray }.filter { (it[0] as JsonPrimitive).content == "v3push" }
      val big = wire.count { (it[4] as JsonPrimitive).content.toInt() > 1024 }
      val gz = wire.count { (it[7] as JsonPrimitive).content == "1" }
      assertTrue("${d.label}: بدنهٔ push ِ v3 ≤ ۲۵۶KB", (push.str("maxReqBody").toIntOrNull() ?: 0) <= SyncV3Engine.MAX_BYTES + 1024)
      val retryV = waits.getValue(d).sum()
      val v2g = l.str("vClockMs").toDouble() + retryV - retryV / speed
      report += "${d.label}: op ${made.getValue(d).size}، push ${push.str("n")} (بریده ${push.str("dropUp")}↑/${push.str("dropDown")}↓، مهلت ${push.str("timeouts")}، gzip $gz/$big، بدنه ${push.str("reqBody")}، برآوردِ gzip ${push.str("reqGzEst")}، بیشترین ${push.str("maxReqBody")})، " +
        "pull ${pull.str("n")} (بدنه ${pull.str("resBody")}، gzip ${pull.str("resGzipped")}، برآوردِ gzip ${pull.str("resGzEst")})؛ انتظار ${waits.getValue(d)}؛ سیم ↑${l.str("wireUp")} ↓${l.str("wireDown")}؛ " +
        "زمانِ 2G ${"%.1f".format(v2g / 60_000)} دقیقه (دیواری ${walls.getValue(d) / 1000}s)"
    }
    val json = buildJsonObject {
      put("name", "v3"); put("ops", offlineTotal); put("speed", speed); put("openConflicts", open.size)
      put("diverged", JsonObject(diverged.mapValues { JsonPrimitive(it.value) })); put("rows", want3.size)
      put("report", JsonArray(report.map { JsonPrimitive(it) }))
      put("links", JsonObject(linkAt.entries.associate { (d, v) -> d.label to JsonObject(v.filterKeys { it != "log" }) }))
    }.toString()
    env("S26_REPORT")?.let { File(it.replace(".json", "-v3.json")).writeText(json) }
    println(
      """
      |── S26/v3 (${be.db}، $offlineTotal op ِ قطعی، سرعتِ خط ×$speed، gzip_push روشن، برنامه از نو در روزِ ۳۱) ──
      |${report.joinToString("\n|")}
      |سرور: sync_v3_ops ${log.str("total")} یکتا ${log.str("distinct")}، تکراری ۰؛ تعارضِ باز ${open.size} (${open.groupingBy { (it["kind"] as? JsonPrimitive)?.content }.eachCount()})؛ ردیفِ ناهمسان با Snapshot ِ v3 (همه با تعارضِ باز): $diverged از ${want3.size} (sha ِ سرور ${want.first.take(12)})
      |زمان: قطعی ${offlineMs / 1000}s، همگام‌سازیِ هم‌زمان ${parallelMs / 1000}s
      """.trimMargin()
    )
  }

  /** `S26_ONLY=s26|syn04|v3` ⇒ فقط همان (اجرای سنگین) */
  private fun only(name: String) = assumeTrue("S26_ONLY=${env("S26_ONLY")}", env("S26_ONLY")?.let { it == name } ?: true)

  private fun scenario(name: String, ops: Int, gzip: Boolean) {
    only(name)
    //  اجرای سنگین بیرون از Gradle (`java -cp … org.junit.runner.JUnitCore`): همان classpath ِ آزمون در این پرونده
    (env("S26_CLASSPATH") ?: env("S26_REPORT")?.replace(".json", ".classpath"))?.let { File(it).writeText(System.getProperty("java.class.path")) }
    val root = OfflineBackend.repoRoot()
    val missing = OfflineBackend.available(root)
    assumeTrue("سرورِ واقعی در دسترس نیست: $missing", missing == null)
    work.mkdirs()
    OfflineBackend.start(root!!, File(work, "backend-$name.log")).use { be -> runBlocking { play(be, name, ops, gzip) } }
  }

  private suspend fun play(be: OfflineBackend, name: String, ops: Int, gzip: Boolean) {
    val t0 = System.currentTimeMillis()
    val rnd = Random(26)
    val setup = be.call("POST", "/setup", obj("subscriptionDays" to 120, "extraStaff" to 1))
    val o = setup.obj("owner")
    val s = setup.obj("staff")
    val s2 = (setup["more"] as JsonArray)[0] as JsonObject
    val direct = setup.str("api")
    val clock = ShopClock(System.currentTimeMillis())
    val A = ShopDevice("owner-pc", o.str("deviceId"), o.str("userId"), "صاحب", direct, MemTokens(o.str("access"), o.str("refresh")), File(work, "$name/A"), "1.0.9999")
    val B = ShopDevice("staff-phone-1", s.str("deviceId"), s.str("userId"), "علی", direct, MemTokens(s.str("access"), s.str("refresh")), File(work, "$name/B"), "3.2.9999")
    val C = ShopDevice("staff-phone-2", s2.str("deviceId"), s2.str("userId"), "شاگرد 2", direct, MemTokens(s2.str("access"), s2.str("refresh")), File(work, "$name/C"), "3.2.9999")
    val devs = listOf(A, B, C)
    if (gzip) for (k in listOf("sync.gzip_push", "sync.gzip_pull")) be.call("POST", "/flag", obj("key" to k, "value" to true))

    /* ── روزِ صفر، آنلاین ── */
    be.serverDay(0.0)
    clock.at(0, 6 * 60)
    for (d in devs) d.firstSync()
    for (d in devs) d.series = ApiJson.text(d.api.post("/shop/series", buildJsonObject { put("deviceId", d.deviceId) }), "series")
    seed(A, clock)
    for (d in devs) assertTrue(d.sync().isSuccess)
    val debtors = A.ledger.debtors.map { it.id }
    val hot = A.ledger.products.take(5).map { it.id }
    //  گشایشِ روزِ صفر: هر گوشی یک فروش، تا هر سه دستگاه pull ِ با op ِ دیگری را دیده باشند
    for (d in listOf(B, C)) Stager(d, 1).sell(rnd, clock, false, debtors)
    for (d in devs + A) assertTrue(d.sync().isSuccess)
    for (d in devs) assertTrue("${d.label}: روزِ صفر همهٔ کالاها (${d.ledger.products.size})", d.ledger.products.size == 40 && d.ledger.products.map { it.id }.containsAll(hot))
    val seedOps = devs.associateWith { it.made.size }

    /* ── ۳۰ روز بی اینترنت ── */
    for (d in devs) d.online = false
    val share = mapOf(A to 0.45, B to 0.30, C to 0.25)
    val stagers = devs.associateWith { Stager(it, saveEvery) }
    var perAction = 4.0
    var actions = 0
    fun offlineOps() = devs.sumOf { it.made.size - seedOps.getValue(it) }
    for (d in 1..days) {
      for (dev in devs) dev.boot()
      val budget = (ops - offlineOps()).coerceAtLeast(0).toDouble() / (days - d + 1)
      for (dev in devs) {
        val n = maxOf(1, (budget * share.getValue(dev) / perAction).toInt())
        stagers.getValue(dev).day(rnd, clock, n, dev === A, debtors, hot, d, if (dev === A) 7 * 60 else 8 * 60)
        actions += n
      }
      perAction = offlineOps().toDouble() / maxOf(1, actions)
    }
    while (offlineOps() < ops) {   //  روزِ آخر تا خودِ عدد
      val st = stagers.getValue(A)
      st.sell(rnd, clock, true, debtors); st.flush()
    }
    val queued = devs.associateWith { it.queue.size() }
    for (d in devs) assertEquals("${d.label}: صف همهٔ کارِ قطعی", d.made.size - seedOps.getValue(d), queued.getValue(d))
    val offlineMs = System.currentTimeMillis() - t0

    /* ── روزِ ۳۱: برنامه باز می‌شود، هر دستگاه روی خطِ 2G ِ خودش، هم‌زمان ── */
    be.serverDay(days + 1.0 + 9 / 24.0)
    val runs = devs.mapIndexed { i, d ->
      d.boot()
      d.apiUrl = be.call("POST", "/link", obj("id" to d.label, "cfg" to linkCfg(260 + i))).str("url")
      d.online = true
      Run(d)
    }
    val tNet = System.currentTimeMillis()
    coroutineScope { runs.map { r -> async(Dispatchers.IO) { session(be, r) } }.awaitAll() }
    val parallelMs = System.currentTimeMillis() - tNet
    //  دورِ پایانی (بی بریدن): هر دستگاه کارِ دو دستگاهِ دیگر را که پس از pull ِ خودش رسید می‌گیرد
    for (d in devs) be.call("POST", "/link-set", obj("id" to d.label, "cfg" to buildJsonObject { put("dropRate", 0.0) }))
    var rounds = 0
    do {
      rounds++
      val before = devs.sumOf { it.pulledOps }
      for (d in devs) { var k = 0; while (!(d.sync().isSuccess && !d.lastHasMore)) check(++k < 10) { "${d.label}: دورِ پایانی" } }
    } while (devs.sumOf { it.pulledOps } != before && rounds < 6)
    val links = be.call("GET", "/links")

    /* ── هر op یک بار ── */
    val log = be.call("GET", "/oplog")
    val ids = (log["ids"] as JsonArray).map { (it as JsonPrimitive).content }.toHashSet()
    assertEquals("هیچ op_id ِ تکراری", "[]", log["duplicates"].toString())
    assertEquals(log.str("total"), log.str("distinct"))
    for (d in devs) {
      assertEquals("${d.label}: هر op یک بار", d.made.size, log.obj("byDevice").str(d.deviceId).toInt())
      assertTrue("${d.label}: هیچ op جا نماند", ids.containsAll(d.made))
      assertEquals("${d.label}: هیچ op رد نشد ${d.queue.dropped().take(2)}", 0, d.refused)
      assertEquals(0, d.queue.size())
    }

    /* ── همگرایی: سه دفتر = Snapshot ِ سرور، بایت‌به‌بایت ── */
    for (d in devs) d.apiUrl = direct
    val server = LedgerDiff.fromSnapshot(A.api.get("/sync/v1/snapshot"), LedgerDiff.toJson(ShopData()))
    val want = canon(server)
    for (d in devs) {
      assertEquals("${d.label} = سرور (ردیف)", emptyList<String>(), LedgerDiff.diff(LedgerDiff.shadowOf(LedgerDiff.fromJson(server)), LedgerDiff.toJson(d.ledger), 0).map { "${it.table}/${it.rowId}" }.take(10))
      assertSame("${d.label} = سرور (بایت)", server, LedgerDiff.toJson(d.ledger))
      assertEquals(want, canon(LedgerDiff.toJson(d.ledger)))
    }
    for (p in A.ledger.products) for (d in listOf(B, C)) assertEquals("موجودیِ ${p.name}", ShopStore.stock(A.ledger, p.id), ShopStore.stock(d.ledger, p.id), 1e-9)
    for (id in debtors) for (d in listOf(B, C)) assertEquals("قرضِ $id", ShopStore.debt(A.ledger, id), ShopStore.debt(d.ledger, id), 1e-9)

    /* ── دسته، سقف، ادامه، فاصلهٔ تلاش ── */
    val report = ArrayList<String>()
    val json = StringBuilder("{\"name\":\"$name\",\"ops\":${offlineOps()},\"speed\":$speed,\"saveEvery\":$saveEvery,\"devices\":[")
    var gzOk = 0
    var gzBig = 0
    var dropDown = 0
    var dups = 0
    val fails = ArrayList<String>()
    fun soft(msg: String, ok: Boolean) { if (!ok) fails += msg }
    for ((i, r) in runs.withIndex()) {
      val d = r.dev
      val l = r.link
      val kinds = l.obj("kinds")
      val push = kinds["push"] as? JsonObject ?: JsonObject(emptyMap())
      val pull = kinds["pull"] as? JsonObject ?: JsonObject(emptyMap())
      soft("${d.label}: دسته ≤ ${OpQueue.MAX_OPS} op (${d.maxBatch})", d.maxBatch in 1..OpQueue.MAX_OPS)
      soft("${d.label}: دسته ≤ ${OpQueue.MAX_BYTES} بایت (${d.maxBatchBytes})", d.maxBatchBytes <= OpQueue.MAX_BYTES)
      soft("${d.label}: fields ≤ ۶۴KB (${d.maxOpFieldsBytes})", d.maxOpFieldsBytes <= 64 * 1024)
      soft("${d.label}: بدنهٔ push روی سیم ≤ ۲۵۶KB (${push.str("maxReqBody")})", push.str("maxReqBody").toInt() <= OpQueue.MAX_BYTES + 1024)
      for ((k, v) in kinds) if (k != "push") soft("${d.label}: درخواستِ $k ≤ ۶۴KB", (v as JsonObject).str("maxReqBody").toInt() <= 64 * 1024)
      soft("${d.label}: ادامه از همان‌جا — هیچ op ِ پذیرفته‌ای دوباره نرفت (${d.resentAfterAck})", d.resentAfterAck == 0)
      soft("${d.label}: بریدنِ push پیش از سرور دیده شد ${push}", push.str("dropUp").toInt() >= 1)
      dropDown += push.str("dropDown").toInt()
      dups += d.duplicates
      //  روی سیم: پس از هر push ِ ناکام، push ِ بعدی یا پس از فاصلهٔ تلاش است یا تنها یک تلاشِ بی‌صدای `HttpURLConnection`
      val wire = (l["log"] as JsonArray).map { it as JsonArray }.filter { (it[0] as JsonPrimitive).content == "push" }
      fun failed(j: Int) = (wire[j][3] as JsonPrimitive).content != "ok"
      fun quick(j: Int) = (wire[j + 1][1] as JsonPrimitive).content.toLong() - (wire[j][2] as JsonPrimitive).content.toLong() < 500
      val immediate = (0 until wire.size - 1).count { failed(it) && quick(it) }
      val chained = (0 until wire.size - 2).count { failed(it) && quick(it) && failed(it + 1) && quick(it + 1) }
      //  زمان فقط با `speed` = ۱ گواه است (با خطِ فشرده انتظارِ خودِ آزمون هم «بی‌فاصله» دیده می‌شود)؛ شمار همیشه:
      //  هر push ِ اضافه روی سیم یا تلاشِ دوم با پیشوندِ دیگر پس از ۴۰۱ است یا یک تلاشِ دوباره پس از push ِ ناکام
      val n401 = wire.count { (it[6] as JsonPrimitive).content == "401" }
      val failedOnWire = wire.count { (it[3] as JsonPrimitive).content != "ok" }
      soft("${d.label}: push ِ سیم ${wire.size} ≤ ${d.pushes} + ۴۰۱ ${n401} + ناکام ${failedOnWire}", wire.size <= d.pushes + n401 + failedOnWire)
      if (speed == 1.0) soft("${d.label}: هیچ زنجیرِ تکرارِ پشتِ سرِ هم (طوفانِ تلاش) ($chained)", chained == 0)
      soft("${d.label}: هر انتظار ≥ ۱۰ ثانیه و دوبرابر در یک رشتهٔ شکست ${r.waits}", r.waits.all { it >= 10_000 } && r.waits.zipWithNext().all { (a, b) -> b == 10_000L || b == minOf(300_000L, a * 2) })
      val v2g = l.str("vClockMs").toDouble() + (r.backoffVirtualMs - r.backoffRealMs)
      val bigPush = wire.count { (it[4] as JsonPrimitive).content.toInt() > 1024 }
      val gzPush = wire.count { (it[7] as JsonPrimitive).content == "1" }
      gzOk += gzPush; gzBig += bigPush
      report += "${d.label}: op ${d.made.size} (قطعی ${queued.getValue(d)})، push ${d.pushes} (سیم ${push.str("n")}، بریده ${push.str("dropUp")}↑/${push.str("dropDown")}↓، مهلت ${push.str("timeouts")}، پشتِ سرِ هم $immediate)، " +
        "pull ${pull.str("n")} (${d.pulledOps} op)؛ تکراریِ سرور ${d.duplicates}؛ شکست ${r.failures.size} ${r.failures.take(3)} انتظار ${r.waits}؛ " +
        "سیم ↑${l.str("wireUp")} ↓${l.str("wireDown")} بایت؛ بدنهٔ push ${push.str("reqBody")} (gzip ${gzPush}/${bigPush}، برآوردِ gzip ${push.str("reqGzEst")})، " +
        "بدنهٔ pull ${pull.str("resBody")} (gzip ${pull.str("resGzipped")}، برآوردِ gzip ${pull.str("resGzEst")})؛ بیشترین دسته ${d.maxBatch} op/${d.maxBatchBytes}B، fields ${d.maxOpFieldsBytes}B؛ " +
        "گم‌شده ${l.str("lostUp")}+${l.str("lostDown")}؛ زمانِ 2G ${"%.1f".format(v2g / 60_000)} دقیقه (دیواری ${r.wallMs / 1000}s، سرور ${"%.0f".format(l.str("serverMs").toDouble() / 1000)}s)"
      json.append(if (i > 0) "," else "").append("{\"label\":\"${d.label}\",\"ops\":${d.made.size},\"queued\":${queued.getValue(d)},\"pushes\":${d.pushes},\"duplicates\":${d.duplicates},")
        .append("\"failures\":${r.failures.size},\"waits\":${r.waits},\"immediatePushRetries\":$immediate,\"v2gMs\":${v2g.toLong()},\"wallMs\":${r.wallMs},\"maxBatch\":${d.maxBatch},\"maxBatchBytes\":${d.maxBatchBytes},")
        .append("\"maxOpFieldsBytes\":${d.maxOpFieldsBytes},\"pulledOps\":${d.pulledOps},\"failureKinds\":${JsonArray(r.failures.map { JsonPrimitive(it) })},")
        .append("\"wire\":${JsonArray((l["log"] as JsonArray).map { it as JsonArray }.filter { (it[0] as JsonPrimitive).content in setOf("push", "pull", "refresh") })},\"link\":${JsonObject(l.filterKeys { it != "log" })}}")
    }
    soft("پاسخِ push پس از اعمال بریده شد ($dropDown) ⇒ «تکراری» از سرور ($dups)", dropDown >= 1 && dups > 0)
    if (gzip) soft("SYN-04: پرچمِ `sync.gzip_push` ⇒ هر push ِ بالای ۱KB فشرده ($gzOk از $gzBig)", gzBig == gzOk)
    val total = System.currentTimeMillis() - t0
    json.append("],\"finalRounds\":$rounds,\"offlineMs\":$offlineMs,\"parallelMs\":$parallelMs,\"totalMs\":$total,\"finalLinks\":${JsonObject(links.mapValues { (_, v) -> JsonObject((v as JsonObject).filterKeys { it != "log" }) })}}")
    env("S26_REPORT")?.let { File(it.replace(".json", "-$name.json")).writeText(json.toString()) }
    println(
      """
      |── S26/$name (${be.db}، ${offlineOps()} op ِ قطعی + ${seedOps.values.sum()} روزِ صفر، سرعتِ خط ×$speed، ثبت هر $saveEvery کار، gzip=${gzip}) ──
      |${report.joinToString("\n|")}
      |سرور: oplog ${log.str("total")} یکتا ${log.str("distinct")}، تکراری ۰، ردشده ۰؛ همگرایی: سه دفتر = Snapshot (sha ${want.first.take(12)}، ${want.second} بایت)؛ دورِ پایانی $rounds
      |زمان: قطعی ${offlineMs / 1000}s، همگام‌سازیِ هم‌زمان ${parallelMs / 1000}s، کل ${total / 1000}s
      """.trimMargin()
    )
    assertTrue(fails.joinToString("\n"), fails.isEmpty())
  }
}
