package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.sync.v1.SyncCommit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
    var pageSize = 1000
    val requests = ArrayList<String>()
    val pushSizes = ArrayList<Int>()
    val formats = ArrayList<String>()
    val conflicts = ArrayList<JsonObject>()
    private var seq = 0L

    class Op(val seq: Long, val device: String, val opId: String, val table: String, val rowId: String, val kind: String, val effect: JsonObject?, val tx: String)
    val log = ArrayList<Op>()
    val seen = HashMap<String, Long>()
    val rows = LinkedHashMap<Pair<String, String>, MutableMap<String, JsonElement>>()
    val deleted = HashSet<Pair<String, String>>()

    fun row(table: String, id: String): Map<String, JsonElement>? = rows[table to id]?.takeIf { (table to id) !in deleted }

    /** §۹.۵: «دوران»ی که سرورِ واقعی کنارِ هر pull می‌دهد (`lib/sync-epoch.js`)؛ null ⇒ سرورِ کهنه */
    var epochId: String? = null
    var epochBase = 0L

    class Saved(
      val seq: Long, val log: List<Op>, val seen: Map<String, Long>,
      val rows: Map<Pair<String, String>, Map<String, JsonElement>>, val deleted: Set<Pair<String, String>>,
    )

    /** پشتیبانِ سرور: همه‌چیز همین لحظه */
    fun backup() = Saved(seq, log.toList(), HashMap(seen), rows.mapValues { LinkedHashMap(it.value) }, HashSet(deleted))

    /** بازیابی از پشتیبان و بالا آمدنِ دوباره: دفتر و شمارنده به لحظهٔ پشتیبان، دورانِ تازه */
    fun restore(s: Saved, epoch: String) {
      seq = s.seq
      log.clear(); log += s.log
      seen.clear(); seen.putAll(s.seen)
      rows.clear(); for ((k, v) in s.rows) rows[k] = LinkedHashMap(v)
      deleted.clear(); deleted += s.deleted
      epochId = epoch
      epochBase = s.seq
    }

    /** بالا آمدنِ عادی: دورانِ بازیابی همان می‌ماند (فقط شمارِ بالا آمدن‌ها) */
    var boots = 0
    fun reboot() { boots++ }

    /** op_idهایی که سرور از این پس رد می‌کند (مثلاً مجوزِ عوض‌شده) */
    val refuse = HashSet<String>()
    /** v2 بی هیچ opِ v3 (فقط مانده یا نشانِ محلی) ⇒ `converted: []` بی شماره */
    var v2Empty = false

    private fun meta(body: Map<String, JsonElement>): JsonObject = JsonObject(
      body + mapOf("server_time" to JsonPrimitive(time), "hlc" to JsonPrimitive("%015d:000000:server".format(time))),
    )

    fun transport(device: String) = V3Transport { method, path, body ->
      requests += "$method ${path.substringBefore('?')}"
      if (offline) throw IOException("offline")
      val r = when {
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
      formats += str(body, "format").ifEmpty { "v3" }
      if (str(body, "format") == "v2") {
        //  سرورِ واقعی ویرایشِ سندِ مالی را به صندوقِ تعارض می‌برد؛ هر op ِ v2 یک پاسخ، با
        //  opهای v3ی که از آن ساخت در `converted[]` (هر کدام server_seq ِ خودش) — sync-v3.js
        return V3Response(200, meta(mapOf("results" to JsonArray(ops.map { o ->
          val id = str(o, "op_id")
          buildJsonObject {
            put("op_id", JsonPrimitive(id))
            if (v2Empty) {
              put("status", JsonPrimitive("applied")); put("converted", JsonArray(emptyList()))
            } else {
              val known = seen[id]
              val at = known ?: run { seq += 1; seen[id] = seq; seq }
              put("status", JsonPrimitive(if (known != null) "duplicate" else "conflict"))
              put("converted", JsonArray(listOf(buildJsonObject {
                put("op_id", JsonPrimitive("$id.c")); put("status", JsonPrimitive("conflict")); put("server_seq", JsonPrimitive(at))
              })))
            }
          }
        }))))
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
          id in seen -> buildJsonObject { put("op_id", JsonPrimitive(id)); put("status", JsonPrimitive("duplicate")); put("server_seq", JsonPrimitive(seen.getValue(id))) }
          id in refuse -> buildJsonObject { put("op_id", JsonPrimitive(id)); put("status", JsonPrimitive("rejected")); put("reason", JsonPrimitive("forbidden")) }
          i in bad -> buildJsonObject { put("op_id", JsonPrimitive(id)); put("status", JsonPrimitive("rejected")); put("reason", JsonPrimitive(bad.getValue(i))) }
          tx.isNotEmpty() && tx in badTx -> buildJsonObject { put("op_id", JsonPrimitive(id)); put("status", JsonPrimitive("rejected")); put("reason", JsonPrimitive("tx_rejected")) }
          else -> { apply(device, o); buildJsonObject { put("op_id", JsonPrimitive(id)); put("status", JsonPrimitive("applied")); put("server_seq", JsonPrimitive(seq)) } }
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
      val epoch = epochId?.let { id ->
        mapOf("epoch" to buildJsonObject { put("id", JsonPrimitive(id)); put("base", JsonPrimitive(minOf(epochBase, seq))); put("head", JsonPrimitive(seq)) })
      }.orEmpty()
      return V3Response(200, meta(mapOf("ops" to ops, "cursor" to JsonPrimitive(cursor), "has_more" to JsonPrimitive(more)) + epoch))
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
    //  همان دفترچهٔ `FileV3Store` (§۹.۵) — همهٔ آزمون‌ها با آن می‌دوند، مثلِ برنامه
    override val replay = ReplayBook.inDir(dir, "replay")
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

  /* --------------------------------------- §۹.۵: سرور از پشتیبان برگشت */

  private fun converged(server: FakeServer, vararg devices: Device) {
    val fresh = Device("dev-fresh-${server.boots}-${devices.size}", server, ShopData()).also { assertTrue(it.sync().ok) }
    for (d in devices) {
      assertEquals("${d.name}: فروش", fresh.ledger.data.sales.map { it.id to it.paidAmount }, d.ledger.data.sales.map { it.id to it.paidAmount })
      assertEquals("${d.name}: کالا", fresh.ledger.data.products.map { it.id to it.salePrice }, d.ledger.data.products.map { it.id to it.salePrice })
      assertFalse("${d.name}: در انتظار نماند", d.engine.meta.restorePending)
      assertEquals("${d.name}: صف خالی", 0, d.engine.queued())
      assertEquals("${d.name}: opِ تازه‌ای ساخته نشد", 0, d.engine.record(d.ledger.data))
    }
  }

  @Test fun restoredServerGetsLostOpsAgainAndNoDeviceAppliesTwice() {
    //  همان سناریوی E2E-OPS-14.replay، این بار برای v3
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    assertEquals("ep_1", b.engine.meta.epoch)
    val saved = server.backup()

    //  پس از پشتیبان: شاگرد ۵۰ از فروشِ s1 می‌گیرد (دلتا) و صاحب آن را می‌نشاند
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 81.0) }) }
    assertTrue(b.sync().ok)
    assertTrue(a.sync().ok)
    assertEquals(150.0, a.ledger.data.sales.single().paidAmount, 0.0)

    server.restore(saved, "ep_2")
    assertEquals("سرور به لحظهٔ پشتیبان برگشت", 10000L, long(server.row("sales", "s1"), "paidAmountMinor"))

    //  دستگاهِ سوم پس از بازیابی قیمت را عوض می‌کند — شماره‌اش زیرِ cursor ِ کهنهٔ a و b است
    val c = Device("dev-c", server, ShopData()).also { assertTrue(it.sync().ok) }
    c.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 130.0) }) }
    assertTrue(c.sync().ok)

    //  شاگرد: دورانِ تازه ⇒ دلتای خودش با همان op_id دوباره، بعد دفتر از Snapshot
    val pushes = server.pushSizes.size
    assertTrue(b.sync().ok)
    assertEquals("دلتای گم‌شده همین دور رفت", pushes + 1, server.pushSizes.size)
    assertEquals("ep_2", b.engine.meta.epoch)
    assertEquals(15000L, long(server.row("sales", "s1"), "paidAmountMinor"))
    assertEquals(150.0, b.ledger.data.sales.single().paidAmount, 0.0)
    assertEquals(130.0, b.ledger.data.products.single().salePrice, 0.0)

    //  صاحب: چیزی نفرستاده بود ولی پس از پایه دیده بود ⇒ دفتر از Snapshot؛ ۱۵۰ می‌ماند، نه ۲۰۰
    assertTrue(a.sync().ok)
    assertEquals(150.0, a.ledger.data.sales.single().paidAmount, 0.0)
    assertEquals(81.0, a.ledger.data.sales.single().remaining, 0.0)
    assertEquals(130.0, a.ledger.data.products.single().salePrice, 0.0)
    //  c پس از بازیابی آمد ⇒ کاری ندارد؛ دلتای دوباره‌رسیدهٔ b را یک بار می‌گیرد
    assertTrue(c.sync().ok)
    assertFalse(c.engine.meta.restorePending)
    assertEquals(150.0, c.ledger.data.sales.single().paidAmount, 0.0)
    converged(server, a, b, c)
  }

  @Test fun sameFieldEditsAcrossRestoreConvergeOnServerOrder() {
    //  بازبین #۱: پس از پشتیبان b قیمت را ۱۱۱ و a پس از دیدنِ آن ۲۲۲ می‌کند؛ پس از بازیابی
    //  اول a و بعد b دوباره می‌فرستند ⇒ همه همان چیزی را دارند که سرور دارد
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    val saved = server.backup()
    b.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 111.0) }) }
    assertTrue(b.sync().ok)
    assertTrue(a.sync().ok)
    assertEquals(111.0, a.ledger.data.products.single().salePrice, 0.0)
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 222.0) }) }
    assertTrue(a.sync().ok)
    assertTrue(b.sync().ok)
    assertEquals(222.0, b.ledger.data.products.single().salePrice, 0.0)

    server.restore(saved, "ep_2")
    assertTrue(a.sync().ok)
    assertTrue(b.sync().ok)
    assertTrue(a.sync().ok)
    val truth = (long(server.row("products", "p1"), "salePriceMinor") ?: 0L) / 100.0
    assertEquals(truth, a.ledger.data.products.single().salePrice, 0.0)
    assertEquals(truth, b.ledger.data.products.single().salePrice, 0.0)
    converged(server, a, b)
  }

  @Test fun deviceThatJoinedBySnapshotAfterBackupDoesNotApplyReplayTwice() {
    //  بازبین #۲: دستگاهِ n پس از پشتیبان با Snapshot آمد (دلتای b را در Snapshot دید)
    val server = FakeServer().apply { epochId = "ep_1" }
    Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 81.0) }) }
    assertTrue(b.sync().ok)
    val n = Device("dev-n", server, ShopData()).also { assertTrue(it.sync().ok) }
    assertEquals(150.0, n.ledger.data.sales.single().paidAmount, 0.0)

    server.restore(saved, "ep_2")
    assertTrue(b.sync().ok)                     //  دلتا دوباره ⇒ سرور ۱۵۰
    assertTrue(n.sync().ok)                     //  n از نو ⇒ ۱۵۰، نه ۲۰۰
    assertEquals(150.0, n.ledger.data.sales.single().paidAmount, 0.0)
    assertTrue(n.sync().ok)
    assertEquals(150.0, n.ledger.data.sales.single().paidAmount, 0.0)
    converged(server, b, n)
  }

  @Test fun plainRestartWithTrafficChangesNothing() {
    //  بازبین #۴: بالا آمدنِ عادی میانِ رفت‌وآمدِ چند دستگاه ⇒ نه دوباره‌فرستادن، نه ساختنِ دوباره
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 120.0) }) }
    assertTrue(a.sync().ok)
    server.reboot()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 81.0) }) }
    val pushes = server.pushSizes.size
    assertTrue(b.sync().ok)
    server.reboot()
    assertTrue(a.sync().ok)
    assertTrue(b.sync().ok)
    assertEquals("فقط همان یک تغییر رفت", pushes + 1, server.pushSizes.size)
    assertEquals(150.0, a.ledger.data.sales.single().paidAmount, 0.0)
    assertEquals(120.0, b.ledger.data.products.single().salePrice, 0.0)
    assertEquals("ep_1", a.engine.meta.epoch)
    converged(server, a, b)
  }

  @Test fun v2EntriesAreReplayedByConvertedSeqAndEmptyOnesNever() {
    //  بازبین #۶: پاسخِ v2 شماره را در `converted[]` دارد؛ بی شماره ⇒ در دفترچه نه
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(sales = d.sales.map { it.copy(debtorId = "d9") }) }
    assertTrue(a.sync().ok)
    val book = a.store.replay
    val v2 = book.replayAbove(0).filter { it.body["fmt"]?.jsonPrimitive?.content == V3Entry.V2 }
    assertEquals(1, v2.size)
    assertTrue("شماره از converted[]", v2.single().seq > 0)

    server.v2Empty = true
    a.edit { d -> d.copy(sales = d.sales.map { it.copy(debtorId = "d10") }) }
    assertTrue(a.sync().ok)
    assertEquals("v2 ِ بی opِ v3 در دفترچه نمی‌نشیند", 1, book.replayAbove(0).count { it.body["fmt"]?.jsonPrimitive?.content == V3Entry.V2 })
    server.v2Empty = false

    //  بالا آمدنِ عادی ⇒ هیچ چیزی دوباره نمی‌رود
    val pushes = server.pushSizes.size
    server.reboot()
    assertTrue(a.sync().ok)
    assertEquals(pushes, server.pushSizes.size)
  }

  @Test fun replayRejectedNowIsDoneNotAVisibleRejection() {
    //  بازبین #۱۱: opِ دوباره‌فرستاده‌ای که سرورِ برگشته رد می‌کند ⇒ از صف بیرون، بی ردیف در «ردشده‌ها»
    val server = FakeServer().apply { epochId = "ep_1" }
    Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    val saved = server.backup()
    b.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 125.0) }) }
    assertTrue(b.sync().ok)
    val sent = b.store.replay.replayAbove(saved.seq).single()
    server.restore(saved, "ep_2")
    val entry = Json.decodeFromJsonElement(V3Entry.serializer(), sent.body)
    entry.ops.forEach { server.refuse += (it["op_id"] as JsonPrimitive).content }
    assertTrue(b.sync().ok)
    assertEquals(0, b.engine.queued())
    assertEquals(emptyList<V3Entry>(), b.engine.rejected())
    assertFalse(b.engine.meta.restorePending)
    //  دفتر = سرور (قیمتِ ردشده نمانده)
    assertEquals(115.5, b.ledger.data.products.single().salePrice, 0.0)
  }

  @Test fun oldServerWithoutEpochKeepsCursorAndQueue() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    assertEquals("", a.engine.meta.epoch)
    val saved = server.backup()
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 120.0) }) }
    assertTrue(a.sync().ok)
    server.restore(saved, "ignored")
    server.epochId = null
    val pushes = server.pushSizes.size
    assertTrue(a.sync().ok)
    assertEquals("بی دوران همان رفتارِ پیشین: چیزی دوباره نمی‌رود", pushes, server.pushSizes.size)
    assertFalse(a.engine.meta.restorePending)
  }
}
