package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.data.CustomerMerge
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncCommit
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 *  موتورِ Sync v3ِ برنامه با یک «سرورِ حافظه‌ای» که قراردادِ `routes/sync-v3.js` را
 *  دارد (تکرار ⇒ تکراری، cursor، has_more، ۴۲۶، tx همه یا هیچ). همان سناریوها روی
 *  سرورِ واقعی در `server/test/sync-v3-client.test.js` (وب) دویده می‌شوند.
 */
class SyncV3EngineTest {

  /* ------------------------------------------------ سرورِ حافظه‌ای */

  private class FakeServer {
    var time = 1_800_000_000_000L
    var offline = false
    var loseNext = 0
    /** Pushِ این دستگاه‌ها ۴۰۳ می‌گیرد (نقش یا اشتراک) — Pull باز است */
    val pushForbidden = HashSet<String>()
    var pageSize = 1000
    val requests = ArrayList<String>()
    val pushSizes = ArrayList<Int>()
    val formats = ArrayList<String>()
    /** A00: آیا هر push نشانِ `caps` داشت */
    val caps = ArrayList<Boolean>()
    val conflicts = ArrayList<JsonObject>()
    private var seq = 0L

    class Op(val seq: Long, val device: String, val opId: String, val table: String, val rowId: String, val kind: String, val effect: JsonObject?, val tx: String)
    val log = ArrayList<Op>()
    val seen = HashMap<String, Long>()
    val rows = LinkedHashMap<Pair<String, String>, MutableMap<String, JsonElement>>()
    val deleted = HashSet<Pair<String, String>>()

    fun row(table: String, id: String): Map<String, JsonElement>? = rows[table to id]?.takeIf { (table to id) !in deleted }

    private fun meta(body: Map<String, JsonElement>): JsonObject = JsonObject(
      body + mapOf("server_time" to JsonPrimitive(time), "hlc" to JsonPrimitive("%015d:000000:server".format(time))),
    )

    fun transport(device: String) = V3Transport { method, path, body ->
      requests += "$method ${path.substringBefore('?')}"
      if (offline) throw IOException("offline")
      val r = when {
        path == "/push" && device in pushForbidden -> V3Response(403, buildJsonObject {
          put("error", buildJsonObject { put("code", JsonPrimitive("permission_denied")); put("message", JsonPrimitive("اجازه‌ی ثبت اطلاعات ندارید")) })
        })
        path == "/push" -> push(device, body!!)
        path.startsWith("/pull") -> pull(device, path)
        path == "/snapshot" -> snapshot()
        path.startsWith("/conflicts") -> V3Response(200, meta(mapOf("conflicts" to JsonArray(conflicts))))
        else -> V3Response(404, null)
      }
      if (path == "/push" && loseNext > 0) { loseNext--; throw IOException("پاسخ گم شد") }
      r
    }

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()

    private fun push(device: String, body: JsonObject): V3Response {
      if (body.getValue("protocol").jsonPrimitive.long > 3) {
        return V3Response(426, buildJsonObject { put("error", JsonPrimitive("UPGRADE_REQUIRED")); put("message", JsonPrimitive("سرور باید به‌روز شود")) })
      }
      val ops = body.getValue("ops").jsonArray.map { it.jsonObject }
      pushSizes += ops.size
      caps += body.containsKey("caps")
      formats += str(body, "format").ifEmpty { "v3" }
      if (str(body, "format") == "v2") {
        //  سرورِ واقعی ویرایشِ سندِ مالی را به صندوقِ تعارض می‌برد
        return V3Response(200, meta(mapOf("results" to JsonArray(ops.map { buildJsonObject { put("op_id", it.getValue("op_id")); put("status", JsonPrimitive("conflict")) } }))))
      }
      //  tx: یک op ِ ردشده همهٔ همان tx را رد می‌کند
      val bad = HashMap<Int, String>()
      ops.forEachIndexed { i, o ->
        val f = o["fields"] as? JsonObject
        if (str(o, "table") == "sales" && str(o, "kind") == "insert" && ((f?.get("discountMinor") as? JsonPrimitive)?.content?.toLongOrNull() ?: 0) > 0) {
          bad[i] = "discount_not_allowed"
        }
      }
      val badTx = bad.keys.mapNotNull { str(ops[it], "tx").ifEmpty { null } }.toSet()
      val results = ops.mapIndexed { i, o ->
        val id = str(o, "op_id")
        val tx = str(o, "tx")
        when {
          id in seen -> buildJsonObject { put("op_id", JsonPrimitive(id)); put("status", JsonPrimitive("duplicate")) }
          i in bad -> buildJsonObject { put("op_id", JsonPrimitive(id)); put("status", JsonPrimitive("rejected")); put("reason", JsonPrimitive(bad.getValue(i))) }
          tx.isNotEmpty() && tx in badTx -> buildJsonObject { put("op_id", JsonPrimitive(id)); put("status", JsonPrimitive("rejected")); put("reason", JsonPrimitive("tx_rejected")) }
          else -> { apply(device, o); buildJsonObject { put("op_id", JsonPrimitive(id)); put("status", JsonPrimitive("applied")) } }
        }
      }
      return V3Response(200, meta(mapOf("results" to JsonArray(results), "head" to JsonPrimitive(seq))))
    }

    private fun apply(device: String, o: JsonObject) {
      val id = str(o, "op_id")
      val table = str(o, "table")
      val rowId = str(o, "row_id")
      val kind = str(o, "kind")
      val key = table to rowId
      val f = (o["fields"] as? JsonObject) ?: JsonObject(emptyMap())
      val effect: JsonObject = when (kind) {
        "insert", "update" -> { rows.getOrPut(key) { LinkedHashMap() }.putAll(f); deleted -= key; buildJsonObject { put("set", f) } }
        "delta" -> {
          val r = rows.getOrPut(key) { LinkedHashMap() }
          for ((k, v) in f) r[k] = JsonPrimitive(((r[k] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0) + v.jsonPrimitive.long)
          buildJsonObject { put("inc", f) }
        }
        "cancel" -> { rows.getOrPut(key) { LinkedHashMap() }["status"] = JsonPrimitive("cancelled"); buildJsonObject { put("set", buildJsonObject { put("status", JsonPrimitive("cancelled")) }) } }
        else -> { deleted += key; buildJsonObject { put("deleted", JsonPrimitive(true)) } }
      }
      seq += 1
      seen[id] = seq
      log += Op(seq, device, id, table, rowId, kind, effect, str(o, "tx"))
    }

    private fun pull(device: String, path: String): V3Response {
      val since = Regex("since=(\\d+)").find(path)!!.groupValues[1].toLong()
      val mine = log.filter { it.seq > since && it.device != device }
      val page = mine.take(pageSize)
      val more = mine.size > page.size
      val cursor = if (more) page.last().seq else maxOf(seq, since)
      val ops = JsonArray(page.map { op ->
        buildJsonObject {
          put("op_id", JsonPrimitive(op.opId)); put("seq", JsonPrimitive(op.seq)); put("table", JsonPrimitive(op.table))
          put("row_id", JsonPrimitive(op.rowId)); put("kind", JsonPrimitive(op.kind)); put("effect", op.effect ?: JsonNull)
        }
      })
      return V3Response(200, meta(mapOf("ops" to ops, "cursor" to JsonPrimitive(cursor), "has_more" to JsonPrimitive(more))))
    }

    private fun snapshot(): V3Response {
      val tables = LinkedHashMap<String, MutableList<JsonElement>>()
      for ((key, data) in rows) {
        if (key in deleted) continue
        tables.getOrPut(key.first) { ArrayList() } += buildJsonObject { put("id", JsonPrimitive(key.second)); put("data", JsonObject(data)) }
      }
      return V3Response(200, meta(mapOf(
        "tables" to JsonObject(tables.mapValues { JsonArray(it.value) }), "cursor" to JsonPrimitive(seq),
        "complete" to JsonPrimitive(true), "rows" to JsonPrimitive(tables.values.sumOf { it.size }),
      )))
    }
  }

  /* ------------------------------------------------ دستگاه */

  private class MemLedger(var data: ShopData) : V3Ledger {
    var failWrite = 0
    override fun read() = data
    override fun write(next: ShopData) {
      if (failWrite > 0) { failWrite--; throw IOException("برق رفت پیش از نوشتنِ دفتر") }
      data = next
    }
  }

  private class MemStore(dir: File) : V3Store {
    var meta: V3Meta? = null
    var queue: V3Queue? = null
    var failMeta = 0
    override val journal = SyncCommit.Journal(File(dir, "journal.json"))
    override fun loadMeta() = meta
    override fun saveMeta(meta: V3Meta) {
      if (failMeta > 0) { failMeta--; throw IOException("برق رفت پس از نوشتنِ دفتر") }
      this.meta = meta
    }
    override fun loadQueue() = queue
    override fun saveQueue(queue: V3Queue) { this.queue = queue }
  }

  private val tmp: File = Files.createTempDirectory("v3-engine").toFile()

  private inner class Device(val name: String, val server: FakeServer, data: ShopData, protocol: Int = 3, random: Double = 0.5) {
    val ledger = MemLedger(data)
    val store = MemStore(File(tmp, name).apply { mkdirs() })
    var engine = make(protocol, random)
    fun make(protocol: Int = 3, random: Double = 0.5) =
      SyncV3Engine(server.transport(name), store, name, ledger, rawNow = { server.time }, random = { random }, protocol = protocol).load()
    fun edit(f: (ShopData) -> ShopData) { ledger.data = f(ledger.data) }
    fun sync(first: Boolean = true) = runBlocking { engine.sync(keepLocalOnly = first) }
  }

  private fun long(row: Map<String, JsonElement>?, k: String) = (row?.get(k) as? JsonPrimitive)?.content?.toLongOrNull()

  private val p1 = Product(id = "p1", name = "شیر", salePrice = 115.5, purchasePrice = 1.005, minStock = 2.5)
  private val baseData = ShopData(
    products = listOf(p1),
    sales = listOf(Sale(id = "s1", total = 231.0, finalTotal = 231.0, paidAmount = 100.0, remaining = 131.0)),
    saleItems = listOf(SaleItem(id = "si1", saleId = "s1", productId = "p1", quantity = 2.0, unitPrice = 115.5, totalPrice = 231.0)),
    stockMovements = listOf(StockMovement(id = "m1", productId = "p1", type = "sale", qty = -2.0)),
  )

  /**
   *  A00: opی که ساختِ پیش از A00 ساخته و پس از به‌روزرسانی هنوز در صف است بی `caps` می‌رود
   *  تا تورِ سرور `null` ِ کهنه‌اش را بپالاید؛ op ِ تازهٔ همین ساخت با `caps`.
   */
  @Test fun legacyOpsOfAPreA00BuildGoWithoutCaps_newOnesWithCaps() {
    val server = FakeServer()
    val a = Device("dev-legacy", server, baseData)
    assertTrue(a.sync().ok)
    assertTrue("push ِ همین ساخت نشان دارد", server.caps.all { it })
    server.caps.clear()
    a.edit { d -> d.copy(products = d.products.map { it.copy(name = "شیرِ تازه") }) }
    assertEquals(1, a.engine.record(a.ledger.data))
    //  همین op را «ساختِ پیش از A00» ساخته بود: سایهٔ بی مهر؛ برنامه به‌روز و دوباره باز شد
    a.store.meta = a.store.meta!!.copy(shadow = LedgerDiff.unstamped(a.store.meta!!.shadow))
    a.engine = a.make()
    a.engine.recover(a.ledger.data)
    assertTrue(a.store.meta!!.legacyOpsUpTo.isNotEmpty())
    assertTrue(a.sync(first = false).ok)
    assertEquals(listOf(false), server.caps)
    a.edit { d -> d.copy(products = d.products.map { it.copy(name = "شیرِ تازه‌تر") }) }
    assertEquals(1, a.engine.record(a.ledger.data))
    assertTrue(a.sync(first = false).ok)
    assertEquals(listOf(false, true), server.caps)
    assertEquals("", a.store.meta!!.legacyOpsUpTo)
  }

  @Test fun firstUploadUsesFromLegacyAndOneTxThenSecondDeviceFromSnapshot() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData)
    assertTrue(a.sync().ok)
    assertEquals(0, a.engine.queued())
    assertEquals("synced", a.engine.status().state)
    val p = server.row("products", "p1")
    assertEquals(11550L, long(p, "salePriceMinor"))
    assertEquals(100L, long(p, "purchasePriceMinor"))       //  ۱٫۰۰۵ ⇒ ۱۰۰، همان money-v1.json
    assertEquals(2500L, long(p, "minStockMilli"))
    assertNull(p?.get("salePrice"))
    assertNull(server.row("sales", "s1")?.get("syncStatus"))
    assertEquals(-2000L, long(server.row("stockMovements", "m1"), "qtyMilli"))
    //  سند + قلم + حرکت با یک tx
    val docs = server.log.filter { it.table in setOf("sales", "saleItems", "stockMovements") }
    assertEquals(3, docs.size)
    assertEquals(1, docs.map { it.tx }.toSet().size)
    assertTrue(docs.first().tx.startsWith("tx."))

    val b = Device("dev-b", server, ShopData())
    assertTrue(b.sync().ok)
    assertEquals(115.5, b.ledger.data.products.single().salePrice, 0.0)
    assertEquals(2.0, b.ledger.data.saleItems.single().quantity, 0.0)
    assertEquals(0, b.engine.queued())
    assertEquals("Snapshot و پیش‌فرض‌های مدل هیچ opی نمی‌سازند", 0, b.engine.record(b.ledger.data))
  }

  @Test fun editTravelsByPullWithoutEcho() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    b.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 120.25) }) }
    assertTrue(b.sync().ok)
    assertEquals(12025L, long(server.row("products", "p1"), "salePriceMinor"))
    assertTrue(a.sync().ok)
    assertEquals(120.25, a.ledger.data.products.single().salePrice, 0.0)
    assertEquals("opِ رسیده opِ تازه نمی‌سازد", 0, a.engine.record(a.ledger.data))
  }

  @Test fun lostResponseIsRetriedAsDuplicateNotSecondDelta() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.25, remaining = 80.75) }) }
    assertEquals(1, a.engine.record(a.ledger.data))
    server.loseNext = 1
    val r1 = runBlocking { a.engine.pushOnce() }
    assertFalse(r1.ok)
    assertTrue(r1.retryInMs > 0)
    assertEquals("بی پاسخ از صف بیرون نمی‌رود", 1, a.engine.queued())
    assertTrue(runBlocking { a.engine.pushOnce() }.ok)
    assertEquals(0, a.engine.queued())
    assertEquals(15025L, long(server.row("sales", "s1"), "paidAmountMinor"))
    assertEquals(8075L, long(server.row("sales", "s1"), "remainingMinor"))
  }

  @Test fun offlineKeepsQueueAndBacksOffExponentiallyWithJitter() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(products = d.products.map { it.copy(name = "نو") }) }
    a.engine.record(a.ledger.data)
    server.offline = true
    val waits = (0 until 4).map { runBlocking { a.engine.pushOnce() }.retryInMs }
    assertEquals(listOf(2000L, 4000L, 8000L, 16000L), waits)
    assertEquals(1, a.engine.queued())
    assertEquals("failed", a.engine.status().state)
    assertEquals(1500L, a.engine.backoffMs(0, 0.0))
    assertEquals(2500L, a.engine.backoffMs(0, 1.0))
    assertEquals(SyncV3Engine.MAX_BACKOFF_MS, a.engine.backoffMs(30, 0.5))
    server.offline = false
    assertTrue(a.sync().ok)
    assertEquals(0, a.engine.queued())
    assertEquals("synced", a.engine.status().state)
  }

  @Test fun upgradeRequiredKeepsQueueAndStops() {
    val server = FakeServer()
    val n = Device("dev-new", server, ShopData(), protocol = 4)
    //  دستگاهی که پیش‌تر همگام بوده (چیزی برای نخستین بارگذاری ندارد): Snapshot و Pull پروتکل ندارند
    assertTrue(n.sync(first = false).ok)
    n.edit { d -> d.copy(products = listOf(Product(id = "p426", name = "نو"))) }
    val r = n.sync(first = false)
    assertFalse(r.ok)
    assertTrue(r.upgrade)
    assertEquals(1, n.engine.queued())
    assertEquals("upgrade", n.engine.status().state)
    assertEquals(SyncV3Engine.UPGRADE_MESSAGE, n.engine.status().lastError)
    assertNull(server.row("products", "p426"))
    val calls = server.requests.size
    assertTrue(n.sync().stopped)
    assertEquals("پس از ۴۲۶ دیگر درخواستی نمی‌رود", calls, server.requests.size)
    val again = n.make(protocol = 4)
    assertTrue(again.status().upgradeRequired)
    assertEquals(1, again.queued())
  }

  @Test fun bulkGoesInSmallBatches() {
    val server = FakeServer()
    val a = Device("dev-a", server, ShopData()).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(products = (0 until 450).map { Product(id = "bulk-$it", name = "کالای $it", salePrice = it + 0.5) }) }
    assertTrue(a.sync().ok)
    assertTrue("دسته‌ها: ${server.pushSizes}", server.pushSizes.size >= 3)
    assertTrue(server.pushSizes.all { it <= SyncV3Engine.MAX_OPS })
    assertEquals(450, server.rows.keys.count { it.first == "products" })
    assertEquals(750L, long(server.row("products", "bulk-7"), "salePriceMinor"))
  }

  /**
   *  A16: Pushِ ۴۰۳ (نقش یا اشتراک) Pull را نمی‌خواباند — دستگاه دادهٔ دکان را
   *  همچنان می‌گیرد و opهای خودش در صف می‌مانند تا وقتی سرور بپذیرد یا op به op رد کند.
   */
  @Test fun forbiddenPushStillPulls() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val acc = Device("dev-acc", server, ShopData()).also { assertTrue(it.sync().ok) }
    acc.edit { d -> d.copy(products = d.products + Product(id = "p-local", name = "محلی")) }
    server.pushForbidden += "dev-acc"
    a.edit { d -> d.copy(products = d.products.map { it.copy(name = "شیرِ تازه") }) }
    assertTrue(a.sync().ok)

    val r = acc.sync(first = false)
    assertFalse(r.ok)
    assertEquals(403, r.status)
    assertEquals("Pull با وجودِ ۴۰۳ِ Push رفت", "شیرِ تازه", acc.ledger.data.products.single { it.id == "p1" }.name)
    assertEquals("opِ خودِ دستگاه گم نمی‌شود", 1, acc.engine.queued())
    assertNull(server.row("products", "p-local"))
    assertEquals("failed", acc.engine.status().state)
  }

  @Test fun rejectedLeavesQueueButStaysVisibleAndLocalSaleIsKept() {
    val server = FakeServer()
    val s = Device("dev-s", server, ShopData()).also { assertTrue(it.sync().ok) }
    s.edit { d -> d.copy(sales = listOf(Sale(id = "disc", total = 100.0, discount = 10.0, finalTotal = 90.0, paidAmount = 90.0))) }
    assertTrue(s.sync().ok)
    assertEquals(0, s.engine.queued())
    val rej = s.engine.rejected().single()
    assertEquals(listOf("discount_not_allowed"), rej.reasons)
    assertEquals("disc", rej.rowId)
    assertNotNull("فروشِ ثبت‌شده روی دستگاه می‌ماند", s.ledger.data.sales.singleOrNull { it.id == "disc" })
    assertEquals("conflict", s.engine.status().state)
    assertNull(server.row("sales", "disc"))
  }

  @Test fun docEditIsNotLostGoesAsV2() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(sales = d.sales.map { it.copy(debtorId = "d9") }) }
    assertEquals(1, a.engine.record(a.ledger.data))
    assertEquals(V3Entry.V2, a.engine.queue.outbox.single().fmt)
    assertTrue(runBlocking { a.engine.pushOnce() }.ok)
    assertEquals("v2", server.formats.last())
    assertEquals(0, a.engine.queued())
    assertTrue(a.engine.meta.conflictsDirty)
    assertEquals("d9", a.ledger.data.sales.single().debtorId)
  }

  @Test fun pullCrashAfterLedgerWriteRecoversForwardWithoutDoubleDelta() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 160.25) }) }
    assertTrue(a.sync().ok)
    val before = b.engine.meta.cursor
    b.store.failMeta = 1
    val crashed = runCatching { runBlocking { b.engine.pullOnce() } }
    assertTrue(crashed.isFailure)
    assertEquals("دفتر نشسته بود", 160.25, b.ledger.data.sales.single().paidAmount, 0.0)

    val again = b.make()
    assertEquals(SyncCommit.Outcome.FORWARD, again.recover(b.ledger.data))
    assertTrue(again.meta.cursor > before)
    assertEquals("تغییرِ رسیده «تغییرِ من» شمرده نشد", 0, again.record(b.ledger.data))
    assertEquals(0, runBlocking { again.pullOnce() }.applied)
    assertEquals(160.25, b.ledger.data.sales.single().paidAmount, 0.0)
  }

  @Test fun pullCrashBeforeLedgerWriteGoesBackAndRepulls() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(products = d.products.map { it.copy(name = "شیرِ تازه") }) }
    assertTrue(a.sync().ok)
    val before = b.engine.meta.cursor
    b.ledger.failWrite = 1
    assertTrue(runCatching { runBlocking { b.engine.pullOnce() } }.isFailure)
    val again = b.make()
    assertEquals(SyncCommit.Outcome.BACK, again.recover(b.ledger.data))
    assertEquals(before, again.meta.cursor)
    assertEquals("شیر", b.ledger.data.products.single().name)
    assertEquals(1, runBlocking { again.pullOnce() }.applied)
    assertEquals("شیرِ تازه", b.ledger.data.products.single().name)
  }

  @Test fun pullResumesFromCursorAcrossPages() {
    val server = FakeServer()
    val a = Device("dev-a", server, ShopData()).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(products = (0 until 25).map { Product(id = "r-$it", name = "ک $it") }) }
    assertTrue(a.sync().ok)
    server.pageSize = 10
    val first = runBlocking { b.engine.pullOnce() }
    assertTrue(first.hasMore)
    assertEquals(10, b.ledger.data.products.size)
    server.offline = true
    assertFalse(runBlocking { b.engine.pullOnce() }.ok)
    server.offline = false
    val again = b.make()                      //  برنامه بسته و باز شد: از همان cursor
    assertEquals(10, server.log.count { it.seq <= again.meta.cursor && it.device == "dev-a" && it.table == "products" })
    assertTrue(runBlocking { again.sync(keepLocalOnly = true) }.ok)
    assertEquals(25, b.ledger.data.products.size)
  }

  @Test fun conflictListFeedsStatus() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    server.conflicts += buildJsonObject {
      put("id", JsonPrimitive("cf1")); put("kind", JsonPrimitive("lww")); put("status", JsonPrimitive("open"))
      put("table", JsonPrimitive("products")); put("row_id", JsonPrimitive("p1")); put("field", JsonPrimitive("name"))
      put("loser_value", JsonPrimitive("A")); put("winner_value", JsonPrimitive("B"))
    }
    val list = runBlocking { a.engine.fetchConflicts() }!!
    assertEquals("cf1", list.single()["id"]!!.jsonPrimitive.content)
    assertEquals(1, a.engine.status().conflictsOpen)
    assertEquals("conflict", a.engine.status().state)
  }

  @Test fun oneSaleOfAnySizeIsOneTxButMultiSaleLedgerIsNot() {
    val server = FakeServer()
    val a = Device("dev-a", server, ShopData(products = listOf(p1))).also { assertTrue(it.sync().ok) }
    //  یک فاکتورِ ۶۰قلمی (بیش از سقفِ قدیمِ ۵۰) ⇒ هنوز یک tx: ردِ فاکتور قلم‌ها را هم برمی‌گرداند
    a.edit { d ->
      d.copy(
        sales = d.sales + Sale(id = "big", total = 60.0, finalTotal = 60.0, paidAmount = 60.0),
        saleItems = d.saleItems + (0 until 60).map {
          SaleItem(id = "big-$it", saleId = "big", productId = "p1", quantity = 1.0, unitPrice = 1.0, totalPrice = 1.0)
        },
      )
    }
    assertTrue(a.sync().ok)
    val docs = server.log.filter { it.rowId == "big" || it.rowId.startsWith("big-") }
    assertEquals(61, docs.size)
    assertEquals(1, docs.map { it.tx }.toSet().size)
    assertTrue(docs.first().tx.startsWith("tx."))

    //  دفترِ چندفاکتوری (نخستین بارگذاری، بازگردانیِ پشتیبان) بیش از ۵۰ op ⇒ سند به سند نیست، بی tx ِ مشترک
    a.edit { d -> d.copy(sales = d.sales + (0 until 60).map { Sale(id = "bulk-$it", total = 1.0, finalTotal = 1.0, paidAmount = 1.0) }) }
    assertTrue(a.sync().ok)
    val bulk = server.log.filter { it.rowId.startsWith("bulk-") }
    assertEquals(60, bulk.size)
    //  (هر فاکتورِ قدیمی tx ِ خودش را از `V3Convert` دارد — درج + شمارنده — ولی نه یک tx برای همه)
    assertTrue(bulk.none { it.tx.startsWith("tx.") })
    assertEquals(60, bulk.map { it.tx }.toSet().size)

    //  ثبتِ بی سند (۶۰ حرکتِ انبار، مثلِ شمارش) ⇒ هم سند به سند نیست، همان سقفِ ۵۰
    a.edit { d -> d.copy(stockMovements = d.stockMovements + (0 until 60).map { StockMovement(id = "cnt-$it", productId = "p1", type = "adjustment", qty = 1.0) }) }
    assertTrue(a.sync().ok)
    val moves = server.log.filter { it.rowId.startsWith("cnt-") }
    assertEquals(60, moves.size)
    assertTrue(moves.none { it.tx.startsWith("tx.") })
  }

  @Test fun returnRowsGoBeforeTheirCountersInsideTheTx() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    a.edit { d ->
      d.copy(
        sales = d.sales.map { if (it.id == "s1") it.copy(debtSettled = 50.0) else it },
        saleItems = d.saleItems.map { if (it.id == "si1") it.copy(returnedQty = 1.0) else it },
        saleReturns = d.saleReturns + SaleReturn(id = "r1", saleId = "s1", saleItemId = "si1", productId = "p1", quantity = 1.0, amount = 115.5),
      )
    }
    assertTrue(a.sync().ok)
    val head = server.log.single { it.rowId == "r1" }
    val counters = server.log.filter { (it.rowId == "s1" || it.rowId == "si1") && it.kind == "delta" }
    assertEquals(2, counters.size)
    //  سرِ برگشت پیش از شمارنده‌هایش، همه یک tx
    assertTrue(counters.all { it.seq > head.seq })
    assertTrue(head.tx.startsWith("tx."))
    assertTrue(counters.all { it.tx == head.tx })
  }

  /**
   *  CRM-10 روی v3: `recordOps` فقط همان opهای داده‌شده را به صف می‌برد و سایه را با همان‌ها
   *  جلو می‌برد — تفاضلِ کلِ دفتر نمی‌گیرد، پس تغییرِ محلیِ دیگر نه همراهش می‌رود نه گم می‌شود.
   *  و چرا `ShopStore.sweepMerged` روی v3 نمی‌نشیند: جابه‌جاییِ `debtorId` ِ قرض «ویرایشِ سند»
   *  است و با قالبِ v2 به صندوقِ تعارض می‌رود.
   */
  @Test fun crmSweepRecordOpsIsTargetedAndDocMovesGoAsV2() {
    val server = FakeServer()
    val crm = ShopData(
      debtors = listOf(Debtor(id = "c1", name = "احمد", phone = "+93700123456"), Debtor(id = "c2", name = "احمد کابلی", phone = "+93799555444")),
      transactions = listOf(DebtTransaction(id = "t1", debtorId = "c2", type = "give", amount = 300.0, date = "2026-09-01")),
    )
    val b = Device("dev-b", server, crm).also { assertTrue(it.sync().ok) }
    //  c1 در c2 ادغام شده و یک قرضِ دیررسیده هنوز روی c1 است (هر دو ثبت‌شده)
    b.edit { d ->
      d.copy(
        debtors = d.debtors.map { if (it.id == "c1") it.copy(mergedInto = "c2") else it },
        transactions = d.transactions + DebtTransaction(id = "late", debtorId = "c1", type = "give", amount = 70.0, date = "2026-10-18"),
      )
    }
    assertTrue(b.engine.record(b.ledger.data) > 0)
    //  تغییرِ محلیِ دیگری که هنوز op نشده
    b.edit { it.copy(products = it.products + p1) }

    val disk = b.ledger.data
    val next = CustomerMerge.sweep(disk, "2026-10-20", 2) { "m2" }!!
    val ops = CustomerMerge.sweepOps(disk, next)!!
    assertEquals(setOf("transactions" to SyncOp.UPDATE, "auditLog" to SyncOp.INSERT), ops.map { it.table to it.type }.toSet())
    b.ledger.data = next
    val before = b.engine.queue.outbox.size
    assertEquals(2, b.engine.recordOps(ops))
    val added = b.engine.queue.outbox.drop(before)
    assertTrue("جابه‌جاییِ قرض در v3 ویرایشِ سند است ⇒ v2", added.any { it.fmt == V3Entry.V2 })
    assertEquals("فقط درجِ کالا مانده؛ sweep دوباره نمی‌رود و کالا گم نشده", 1, b.engine.record(b.ledger.data))
    assertEquals(0, b.engine.record(b.ledger.data))
  }
}
