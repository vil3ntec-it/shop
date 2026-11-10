package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.core.sync.platform.FileReplayStore
import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.core.sync.RestoredDevice
import ir.vil3ntec.tohid.core.sync.SyncOutage
import ir.vil3ntec.tohid.core.sync.v3.Hlc
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
    /** SYN-12: سرور پاسخ می‌دهد ولی گرفتار است ⇒ ۵۰۳ ِ `db_busy` برای هر درخواست */
    var busy = false
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
    var seq = 0L
      private set

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
        busy -> V3Response(503, buildJsonObject {
          put("error", buildJsonObject { put("code", JsonPrimitive("db_busy")); put("message", JsonPrimitive("سرور الان شلوغ است")) })
        })
        path == "/push" && device in pushForbidden -> V3Response(403, buildJsonObject {
          put("error", buildJsonObject { put("code", JsonPrimitive("permission_denied")); put("message", JsonPrimitive("اجازه‌ی ثبت اطلاعات ندارید")) })
        })
        path == "/push" -> push(device, body!!)
        path.startsWith("/pull") -> pull(device, path)
        path == "/snapshot" -> snapshot()
        path == "/deleted" -> deletedRows(body!!)
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

    /** `POST /api/sync/v3/deleted` (§۹.۵): کدام ردیف این‌جا حذف‌شده است */
    val asked = ArrayList<String>()
    private fun deletedRows(body: JsonObject): V3Response {
      val rows = body.getValue("rows").jsonArray.map { it.jsonObject }
      rows.forEach { asked += "${str(it, "table")}/${str(it, "row_id")}" }
      val out = rows.filter { (str(it, "table") to str(it, "row_id")) in deleted }
      return V3Response(200, meta(mapOf("deleted" to JsonArray(out))))
    }

    /** Snapshot ِ ناقص (سرور هنوز دفترِ v1 را به v3 نرسانده) */
    var incomplete = false

    private fun snapshot(): V3Response {
      val tables = LinkedHashMap<String, MutableList<JsonElement>>()
      for ((key, data) in rows) {
        if (key in deleted) continue
        tables.getOrPut(key.first) { ArrayList() } += buildJsonObject { put("id", JsonPrimitive(key.second)); put("data", JsonObject(data)) }
      }
      return V3Response(200, meta(mapOf(
        "tables" to JsonObject(tables.mapValues { JsonArray(it.value) }), "cursor" to JsonPrimitive(seq),
        "complete" to JsonPrimitive(!incomplete), "rows" to JsonPrimitive(tables.values.sumOf { it.size }),
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
    /** DATA-06: فایلِ حال خوانده نشد (`FileV3Store`) */
    override var unreadable = false
    //  همان دفترچهٔ `FileV3Store` (§۹.۵) — همهٔ آزمون‌ها با آن می‌دوند، مثلِ برنامه
    override var replay: ReplayBook? = FileReplayStore.inDir(dir, "replay")
  }

  private val tmp: File = Files.createTempDirectory("v3-engine").toFile()

  private inner class Device(val name: String, val server: FakeServer, data: ShopData, protocol: Int = 3, random: Double = 0.5) {
    val ledger = MemLedger(data)
    val store = MemStore(File(tmp, name).apply { mkdirs() })
    /** بایگانیِ دفتر (`LedgerArchive.archivedIds`) */
    val arch = HashMap<String, Set<String>>()
    var engine = make(protocol, random)
    fun make(protocol: Int = 3, random: Double = 0.5) =
      SyncV3Engine(server.transport(name), store, name, ledger, rawNow = { server.time }, random = { random }, protocol = protocol, archived = { arch }).load()
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

  /**
   *  SYN-12: قطعیِ کوتاه «دوباره می‌کوشد» (بی هشدار)، ماندگار «به سرور نمی‌رسیم» و با ۵۰۳ «سرور» (اعلان، نه سرخ)،
   *  بی‌نت «آفلاین»، ۴۰۳ «نشست» (سرخ) — و صف، فاصلهٔ تلاش و op ِ فرستاده همان پیشین.
   */
  @Test fun outageVerdictIsQuietThenNoticeAndLeavesQueueAndBackoffAlone() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    assertEquals(SyncOutage.Kind.OK, a.engine.outage(true).kind)
    val pushesBefore = server.pushSizes.size
    a.edit { d -> d.copy(products = d.products.map { it.copy(name = "نو") }) }
    a.engine.record(a.ledger.data)
    server.offline = true
    assertEquals(2000L, runBlocking { a.engine.pushOnce() }.retryInMs)
    assertEquals(SyncOutage.Kind.RETRYING, a.engine.outage(true).kind)
    assertFalse("نخستین شکست هشدار نیست", a.engine.outage(true).alarm)
    assertTrue("خطا پنهان نمی‌شود (SYN-08)", a.engine.status().lastError.isNotBlank())
    server.time += 30_000
    assertEquals(4000L, runBlocking { a.engine.pushOnce() }.retryInMs)
    assertEquals(SyncOutage.Kind.RETRYING, a.engine.outage(true).kind)
    server.time += 31_000
    val down = a.engine.outage(true)
    assertEquals(SyncOutage.Kind.UNREACHABLE, down.kind)
    assertEquals(SyncOutage.Level.NOTICE, down.level)
    assertEquals(SyncOutage.Kind.OFFLINE, a.engine.outage(false).kind)
    server.offline = false
    server.busy = true
    assertEquals(8000L, runBlocking { a.engine.pushOnce() }.retryInMs)
    assertEquals(SyncOutage.Kind.SERVER, a.engine.outage(true).kind)
    assertEquals(1, a.engine.queued())
    assertEquals("هیچ push ای نرسید", pushesBefore, server.pushSizes.size)
    server.busy = false
    assertTrue(a.sync(first = false).ok)
    assertEquals(SyncOutage.Kind.OK, a.engine.outage(true).kind)
    assertEquals(0, a.engine.queued())
    assertEquals("همان یک op، یک بار", listOf(1), server.pushSizes.drop(pushesBefore))

    server.pushForbidden += "dev-a"
    a.edit { d -> d.copy(products = d.products.map { it.copy(name = "نوتر") }) }
    a.engine.record(a.ledger.data)
    runBlocking { a.engine.pushOnce() }
    assertEquals(SyncOutage.Kind.AUTH, a.engine.outage(true).kind)
    assertTrue("نشست بالاتر از بی‌نت", a.engine.outage(false).alarm)
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
    val book = a.store.replay!!
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
    val sent = b.store.replay!!.replayAbove(saved.seq).single()
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

  @Test fun ownSaleMissingFromTheBookIsKeptAndInsertedWithANotice() {
    //  بازبینِ دوم، B1(b): دفترچه نبود (برنامه میانِ پشتیبان و بازیابی به‌روز شد) ⇒ پیش‌تر ساختنِ
    //  دوباره فروشِ خودِ دستگاه را پاک می‌کرد
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales + Sale(id = "s9", total = 50.0, finalTotal = 50.0, paidAmount = 50.0)) }
    assertTrue(b.sync().ok)
    b.store.replay = FileReplayStore.inDir(File(tmp, "dev-b-lost").apply { mkdirs() }, "replay")
    server.restore(saved, "ep_2")
    assertNull(server.row("sales", "s9"))
    assertTrue(b.sync().ok)
    assertFalse(b.engine.meta.restorePending)
    assertTrue(b.ledger.data.sales.any { it.id == "s9" })
    assertNotNull("درج شد", server.row("sales", "s9"))
    assertTrue("پوششِ دفترچه کم بود ⇒ هشدار", b.engine.status().notice.isNotEmpty())
    assertEquals("هشدار حال را «شکست» نمی‌کند", "synced", b.engine.status().state)
    assertTrue(a.sync().ok)
    assertTrue(a.ledger.data.sales.any { it.id == "s9" })
    converged(server, a, b)
  }

  @Test fun saleSeenFromACreatorThatNeverComesBackIsKeptAndSent() {
    //  بازبینِ دوم، B1(a): a پس از پشتیبان فروخت و b گرفت؛ پس از بازیابی فقط b همگام می‌شود
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    val saved = server.backup()
    a.edit { d -> d.copy(sales = d.sales + Sale(id = "s8", total = 70.0, finalTotal = 70.0, paidAmount = 70.0)) }
    assertTrue(a.sync().ok)
    assertTrue(b.sync().ok)
    assertTrue(b.ledger.data.sales.any { it.id == "s8" })
    server.restore(saved, "ep_2")
    assertTrue(b.sync().ok)
    assertFalse(b.engine.meta.restorePending)
    assertTrue("فروشِ a روی b ماند", b.ledger.data.sales.any { it.id == "s8" })
    assertNotNull("و به سرور رسید", server.row("sales", "s8"))
    converged(server, b)
  }

  @Test fun rowDeletedOnServerAfterRestoreIsNotRevivedByALateDevice() {
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData.copy(products = baseData.products + Product(id = "p2", name = "ماست", salePrice = 50.0)))
      .also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 81.0) }) }
    assertTrue(b.sync().ok)
    assertTrue(a.sync().ok)
    server.restore(saved, "ep_2")
    assertTrue(a.sync().ok)
    a.edit { d -> d.copy(products = d.products.filter { it.id != "p2" }) }
    assertTrue(a.sync().ok)
    assertNull(server.row("products", "p2"))
    assertTrue(b.sync().ok)
    assertTrue("از سرور پرسید", "products/p2" in server.asked)
    assertFalse("p2 روی b نماند", b.ledger.data.products.any { it.id == "p2" })
    assertNull("p2 زنده نشد", server.row("products", "p2"))
    assertTrue(a.sync().ok)                     //  a دلتای دوباره‌رسیدهٔ b را می‌گیرد
    converged(server, a, b)
  }

  @Test fun archivedRowsDoNotComeBackFromTheSnapshot() {
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val b = Device("dev-b", server, ShopData()).also { assertTrue(it.sync().ok) }
    //  `LedgerArchive.compact`: m1 از دفتر به بایگانی، از سایه بیرون، بی op
    assertEquals(1, a.engine.forget(mapOf("stockMovements" to listOf("m1"))))
    a.ledger.data = a.ledger.data.copy(stockMovements = emptyList())
    a.arch["stockMovements"] = setOf("m1")
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 81.0) }) }
    assertTrue(b.sync().ok)
    assertTrue(a.sync().ok)
    server.restore(saved, "ep_2")
    assertTrue(a.sync().ok)
    assertFalse(a.engine.meta.restorePending)
    assertTrue("بایگانی‌شده به دفتر برنگشت", a.ledger.data.stockMovements.none { it.id == "m1" })
    assertEquals(0, a.engine.queued())
    assertNotNull("سرور هنوز دارد", server.row("stockMovements", "m1"))
    assertEquals(0, a.engine.record(a.ledger.data))
  }

  @Test fun incompleteSnapshotKeepsPendingSaysWhyAndRetries() {
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val saved = server.backup()
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 120.0) }) }
    assertTrue(a.sync().ok)
    server.restore(saved, "ep_2")
    server.incomplete = true
    val step = a.sync()
    assertFalse(step.ok)
    assertTrue(step.retryInMs > 0)
    assertTrue(a.engine.meta.restorePending)
    assertEquals(SyncV3Engine.INCOMPLETE_MESSAGE, a.engine.meta.lastError)
    server.incomplete = false
    assertTrue(a.sync().ok)
    assertFalse(a.engine.meta.restorePending)
    assertEquals("", a.engine.meta.lastError)
    assertEquals(120.0, a.ledger.data.products.single().salePrice, 0.0)
    converged(server, a)
  }

  @Test fun replayedEntriesGetFreshQueueKeys() {
    //  بازبینِ دوم: کلیدِ کهنهٔ ورودیِ دوباره‌آمده با ورودیِ همین حالا در صف یکی می‌شد
    val server = FakeServer().apply { epochId = "ep_1" }
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    val saved = server.backup()
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 120.0) }) }
    assertTrue(a.sync().ok)
    server.restore(saved, "ep_2")
    //  صفی که شمارش از نو شد (مثلاً فایلِ صف از نو ساخته شد): ورودیِ تازه همان کلیدِ کهنه را می‌گیرد
    val old = Json.decodeFromJsonElement(V3Entry.serializer(), a.store.replay!!.replayAbove(saved.seq).single().body).key
    a.store.queue = a.store.queue!!.copy(seq = old.drop(1).toLong() - 1)
    a.engine = a.make()
    a.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 120.0, remaining = 111.0) }) }
    runBlocking { a.engine.record(a.ledger.data) }
    val step = runBlocking { a.engine.pullOnce() }
    assertTrue(step.replayed > 0)
    val keys = a.engine.queue.outbox.map { it.key }
    assertEquals("کلیدها یکتا", keys.size, keys.toSet().size)
    assertTrue(a.sync().ok)
    assertEquals(12000L, long(server.row("products", "p1"), "salePriceMinor"))
    assertEquals(12000L, long(server.row("sales", "s1"), "paidAmountMinor"))
    converged(server, a)
  }

  @Test fun serverFlagOffWritesNoReplayBookAndFirstEpochCoversFromServerHead() {
    //  `SYNC_EPOCH_ENABLED` خاموش ⇒ pull بی دوران ⇒ دفترچه نوشته نمی‌شود (همان رفتارِ پیشین)
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 120.0) }) }
    assertTrue(a.sync().ok)
    assertEquals("خاموش: دفترچه خالی", 0, a.store.replay!!.count())
    assertEquals("", a.engine.meta.epoch)

    //  روشن ⇒ پوشش از سرِ دفترِ سرور در همان لحظه؛ push ِ پس از آن نوشته می‌شود
    server.epochId = "ep_1"
    assertTrue(a.sync().ok)
    assertEquals("ep_1", a.engine.meta.epoch)
    assertEquals(server.seq, a.store.replay!!.floor())
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 130.0) }) }
    assertTrue(a.sync().ok)
    assertTrue("روشن: opِ خودی نوشته شد", a.store.replay!!.count() > 0)
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

  /* ------------------------------------------------ DATA-06: پشتیبانِ دستگاهِ دیگر (`V3State.kt`) */

  /** پشتیبانِ خودکارِ اندروید از یک دستگاه: دفتر، صف (`sync-v3-<کلید>/queue.json`)، و فقط در ساخت‌های پیشین حال (`state.json`) */
  private class Backup(val ledger: ShopData, val queue: V3Queue?, val meta: V3Meta?)

  private fun Device.backup(withState: Boolean) = Backup(ledger.data, store.queue, if (withState) store.meta else null)

  /** دستگاهِ **دیگری** ([name]، شناسهٔ خودش) که از [b] بالا آمد */
  private fun restored(name: String, server: FakeServer, b: Backup): Device =
    Device(name, server, b.ledger).also { it.store.meta = b.meta; it.store.queue = b.queue; it.engine = it.make() }

  private val p2 = Product(id = "p2", name = "روغن", salePrice = 300.0)
  private fun debtSettled(d: Device) = d.ledger.data.sales.single { it.id == "s1" }.debtSettled
  private fun returned(d: Device) = d.ledger.data.saleItems.single { it.id == "si1" }.returnedQty
  private fun stock(d: Device) = d.ledger.data.stockMovements.filter { it.productId == "p1" }.sumOf { it.qty }
  private fun nodeOf(e: V3Entry) = e.ops.map { (it["hlc"] as JsonPrimitive).content.substringAfterLast(':') }.toSet()

  /** برگشتِ ۱ از قلمِ si1 ِ فاکتورِ نسیهٔ s1: `$inc` ِ `returnedQty` و `debtSettled` (۵۰)، و حرکتِ +۱ */
  private fun ShopData.returnOne() = copy(
    sales = sales.map { if (it.id == "s1") it.copy(debtSettled = it.debtSettled + 50.0) else it },
    saleItems = saleItems.map { if (it.id == "si1") it.copy(returnedQty = it.returnedQty + 1.0) else it },
    saleReturns = saleReturns + SaleReturn(id = "r1", saleId = "s1", saleItemId = "si1", productId = "p1", quantity = 1.0, amount = 50.0),
    stockMovements = stockMovements + StockMovement(id = "m-r1", productId = "p1", type = "return", qty = 1.0),
  )

  private fun ShopData.sell(id: String, qty: Double) = copy(
    sales = sales + Sale(id = id, total = 115.5 * qty, finalTotal = 115.5 * qty, paidAmount = 115.5 * qty),
    saleItems = saleItems + SaleItem(id = "$id-i", saleId = id, productId = "p1", quantity = qty, unitPrice = 115.5, totalPrice = 115.5 * qty),
    stockMovements = stockMovements + StockMovement(id = "$id-m", productId = "p1", type = "sale", qty = -qty),
  )

  /**
   *  گوشیِ اول (dev-a): همگام؛ بی اینترنت ۱ برگشت از فاکتورِ نسیه (در صف) ⇐ **پشتیبان** ⇐ [after] (گوشیِ اول همان صف را
   *  می‌فرستد، ۱ فروش و کالای p2 را پاک می‌کند؛ گوشیِ از کار افتاده: هیچ).
   */
  private fun phoneWithBackup(server: FakeServer, withState: Boolean, after: Boolean = true): Pair<Device, Backup> {
    val a = Device("dev-a", server, baseData.copy(products = baseData.products + p2)).also { assertTrue(it.sync().ok) }
    server.offline = true
    a.edit { it.returnOne() }
    assertFalse(a.sync(first = false).ok)
    assertTrue("برگشت در صفِ پشتیبان است", a.engine.queued() > 0)
    val files = a.backup(withState)
    server.offline = false
    if (after) {
      assertTrue(a.sync(first = false).ok)
      a.edit { it.sell("s-a2", 1.0).let { d -> d.copy(products = d.products.filter { p -> p.id != "p2" }) } }
      assertTrue(a.sync(first = false).ok)
    }
    return a to files
  }

  /** همه همان سرور: دستگاهِ تازه از Snapshot؛ برگشت یک بار (۵۰، نه ۱۰۰)، کالای حذف‌شده زنده نشد */
  private fun sameAsServer(server: FakeServer, vararg devices: Device) {
    val fresh = Device("dev-truth-${devices.size}", server, ShopData()).also { assertTrue(it.sync().ok) }
    assertEquals(5000L, long(server.row("sales", "s1"), "debtSettledMinor"))
    assertEquals("روی سرور برگشت یک بار", 1, server.log.count { it.rowId == "s1" && it.kind == "delta" })
    for (d in devices) {
      assertEquals("${d.name}: قرضِ برگشته", 50.0, debtSettled(d), 0.0)
      assertEquals("${d.name}: قرض", debtSettled(fresh), debtSettled(d), 0.0)
      assertEquals("${d.name}: برگشته", returned(fresh), returned(d), 0.0)
      assertEquals("${d.name}: موجودی", stock(fresh), stock(d), 0.0)
      assertEquals("${d.name}: فروش‌ها", fresh.ledger.data.sales.map { it.id }.toSet(), d.ledger.data.sales.map { it.id }.toSet())
      assertEquals("${d.name}: کالاها", fresh.ledger.data.products.map { it.id }.toSet(), d.ledger.data.products.map { it.id }.toSet())
      assertFalse("${d.name}: در انتظار نماند", d.engine.meta.restorePending || d.engine.meta.restoredRebuild)
      assertEquals("${d.name}: صف خالی", 0, d.engine.queued())
      assertEquals("${d.name}: opِ تازه‌ای ساخته نشد", 0, d.engine.record(d.ledger.data))
    }
  }

  /** «اول صف، بعد Snapshot، Pull فقط پس از آن» — از درخواست‌های همین دور */
  private fun rebuiltBeforeAnyPull(server: FakeServer) {
    val snap = server.requests.indexOf("GET /snapshot")
    assertTrue("بازسازی از سرور: ${server.requests}", snap >= 0)
    assertTrue("هیچ Pull ِ پیش از بازسازی: ${server.requests}", server.requests.take(snap).none { it == "GET /pull" })
  }

  @Test fun restoredStateFromAnotherDeviceIsFencedRebuiltFirstAndNothingCountsTwice() {
    val server = FakeServer()
    val (a, files) = phoneWithBackup(server, withState = true)
    //  شاهدِ خطر: همان حالِ پشتیبان (cursor ِ کهنه) با شناسهٔ تازه و بی حصار ⇒ برگشتِ گوشیِ اول «دیگری» است و دو بار می‌نشیند
    val naive = restored("dev-naive", server, files)
    assertTrue(naive.sync(first = false).ok)
    assertEquals("شاهد: بی حصار قرضِ برگشته دو بار", 100.0, debtSettled(naive), 0.0)

    //  ساختِ پیشین (یا رونوشتِ خام): حالِ گوشیِ اول آمد؛ شناسهٔ دستگاه پیش‌تر ساخته شده بود (`restoredAt` ِ صفر) — گرهٔ
    //  ساعتِ همان حال (`dev-a`) کافی است
    val b = restored("dev-b", server, files)
    val stale = b.store.meta!!.cursor
    assertTrue(stale > 0)
    assertEquals(RestoredDevice.Verdict.FENCE, b.engine.fence("dev-b", 0L, b.ledger.data))
    val m = b.store.meta!!
    assertEquals("dev-b", m.device)
    assertEquals("cursor ِ کهنه دور", 0L, m.cursor)
    assertTrue(m.restorePending && m.restoredRebuild)
    assertEquals("گرهٔ HLC ِ تازه", "dev-b", Hlc.parse(m.clock)!!.node)
    assertEquals("پایهٔ سایه = دفترِ بازیابی‌شده", LedgerDiff.shadowOf(LedgerDiff.toJson(b.ledger.data)).toString(), m.shadow)
    assertEquals("صفِ پشتیبان دست نخورد", files.queue, b.store.queue)
    //  دوباره ⇒ همان دستگاه، هیچ نوشتنی
    assertEquals(RestoredDevice.Verdict.SAME, b.engine.fence("dev-b", 0L, b.ledger.data))
    assertTrue(m === b.store.meta)

    //  پیش از ورود، بی اینترنت: فروشِ گوشیِ دوم op می‌شود («پایه» نمی‌شود) با گرهٔ خودش
    b.edit { it.sell("s-b1", 2.0) }
    assertTrue(b.engine.record(b.ledger.data) > 0)
    assertEquals(setOf("dev-b"), b.engine.queue.outbox.filter { it.rowId.startsWith("s-b1") }.flatMap { nodeOf(it) }.toSet())
    server.requests.clear()
    assertTrue(b.sync(first = false).ok)
    rebuiltBeforeAnyPull(server)
    assertTrue("cursor سرِ همان Snapshot", b.engine.meta.cursor > 0)
    assertEquals(setOf("dev-b"), server.log.filter { it.rowId.startsWith("s-b1") }.map { it.device }.toSet())
    assertTrue(a.sync(first = false).ok)
    sameAsServer(server, a, b)
  }

  @Test fun normalRestoreWithoutStateIsFencedByTheMintedIdQueueGoesFirstAndTheFirstSaleIsNotLost() {
    val server = FakeServer()
    //  این ساخت: `sync-v3/` با پشتیبان نمی‌رود ⇒ فقط دفتر و صف
    val (a, files) = phoneWithBackup(server, withState = false)
    val b = restored("dev-b", server, files)
    assertNull(b.store.meta)
    //  شناسهٔ دستگاه روی ردِ کارِ پیشین ساخته شد (`SyncStore.restoredAt`)
    assertEquals(RestoredDevice.Verdict.FENCE, b.engine.fence("dev-b", 1_234L, b.ledger.data))
    assertTrue(b.engine.meta.initialized && b.engine.meta.restorePending && b.engine.meta.restoredRebuild)
    b.edit { it.sell("s-b1", 2.0) }
    assertTrue("فروشِ پیش از ورود op شد", b.engine.record(b.ledger.data) > 0)
    server.requests.clear()
    assertTrue(b.sync(first = false).ok)
    rebuiltBeforeAnyPull(server)
    assertTrue("p2 را گوشیِ اول پاک کرد ⇒ زنده نشد", b.ledger.data.products.none { it.id == "p2" })
    assertNull(server.row("products", "p2"))
    assertTrue(a.sync(first = false).ok)
    sameAsServer(server, a, b)
  }

  @Test fun theQueueOfAPhoneThatDiedAfterTheBackupStillReachesTheServerExactlyOnce() {
    val server = FakeServer()
    //  گوشیِ اول پس از پشتیبان هرگز برنگشت: برگشتِ در صف فقط در پشتیبان است
    val (_, files) = phoneWithBackup(server, withState = false, after = false)
    assertEquals(0, server.log.count { it.rowId == "s1" && it.kind == "delta" })
    val b = restored("dev-b", server, files)
    assertEquals(RestoredDevice.Verdict.FENCE, b.engine.fence("dev-b", 1_234L, b.ledger.data))
    assertTrue(b.sync(first = false).ok)
    //  همان op_idها، یک بار؛ دفترِ گوشیِ دوم همان سرور
    val sent = files.queue!!.outbox.flatMap { e -> e.ops.map { (it["op_id"] as JsonPrimitive).content } }
    assertTrue(sent.isNotEmpty())
    for (id in sent) assertEquals(id, listOf("dev-b"), server.log.filter { it.opId == id }.map { it.device })
    sameAsServer(server, b)
    //  دوباره همان صف (مثلاً بازیابیِ دوم) ⇒ سرور «تکراری»، هیچ چیزی دو بار
    val again = restored("dev-c", server, files)
    assertEquals(RestoredDevice.Verdict.FENCE, again.engine.fence("dev-c", 1_234L, again.ledger.data))
    assertTrue(again.sync(first = false).ok)
    sameAsServer(server, b, again)
  }

  @Test fun anUpgradeOfThisDeviceBindsAndNeverRotates_unreadableDecidesNothing() {
    val server = FakeServer()
    val a = Device("dev-a", server, baseData).also { assertTrue(it.sync().ok) }
    a.edit { it.sell("s-a2", 1.0) }
    assertTrue(a.sync(first = false).ok)
    //  حالِ ساختِ پیشین: بی بستگی، ساعت از همین دستگاه
    a.store.meta = a.store.meta!!.copy(device = "")
    val before = a.store.meta!!
    a.engine = a.make()
    //  شناسهٔ دستگاه ناخوانا ⇒ هیچ تصمیمی، هیچ نوشتنی
    assertEquals(RestoredDevice.Verdict.UNDECIDED, a.engine.fence("", 0L, a.ledger.data))
    assertTrue(before === a.store.meta)
    //  حالِ ناخوانا (فایلِ خراب کنار رفت) ⇒ همان
    a.store.unreadable = true
    assertEquals(RestoredDevice.Verdict.UNDECIDED, a.engine.fence("dev-a", 0L, a.ledger.data))
    assertTrue(before === a.store.meta)
    a.store.unreadable = false
    //  به‌روزرسانی ⇒ فقط بستن: همان cursor، بی بازسازی
    assertEquals(RestoredDevice.Verdict.BIND, a.engine.fence("dev-a", 0L, a.ledger.data))
    assertEquals(before.copy(device = "dev-a", clock = a.store.meta!!.clock), a.store.meta)
    assertEquals(before.cursor, a.engine.meta.cursor)
    assertFalse(a.engine.meta.restorePending || a.engine.meta.restoredRebuild)
    assertEquals(RestoredDevice.Verdict.SAME, a.engine.fence("dev-a", 0L, a.ledger.data))
    server.requests.clear()
    assertTrue(a.sync(first = false).ok)
    assertFalse("بی بازسازی", "GET /snapshot" in server.requests)
    //  حسابِ تازه روی همین دستگاه (نصبِ تازه، یا گذر از v1): بی ردِ کار ⇒ بستن، بی بازسازی
    val fresh = Device("dev-f", server, baseData)
    assertEquals(RestoredDevice.Verdict.BIND, fresh.engine.fence("dev-f", 0L, fresh.ledger.data))
    assertFalse(fresh.engine.meta.initialized || fresh.engine.meta.restorePending)
  }

  @Test fun aCrashMidFenceRepeatsTheSameDecisionAndNeverPullsTheStaleCursor() {
    val server = FakeServer()
    val (a, files) = phoneWithBackup(server, withState = true)
    val b = restored("dev-b", server, files)
    val before = b.store.meta
    //  برق در لحظهٔ نوشتنِ حال ⇒ پرتاب، هیچ چیزی ننشست (`SyncV3Runner.work` این دور را نمی‌رود)
    b.store.failMeta = 1
    assertTrue(runCatching { b.engine.fence("dev-b", 0L, b.ledger.data) }.isFailure)
    assertTrue(before === b.store.meta)
    assertEquals(before!!.cursor, b.engine.meta.cursor)
    //  بالا آمدنِ دوباره ⇒ همان تصمیم و همان نتیجه
    b.engine = b.make()
    assertEquals(RestoredDevice.Verdict.FENCE, b.engine.fence("dev-b", 0L, b.ledger.data))
    //  بسته شدن پس از حصار و پیش از بازسازی ⇒ «در انتظار» روی دیسک می‌ماند
    b.engine = b.make()
    assertEquals(RestoredDevice.Verdict.SAME, b.engine.fence("dev-b", 0L, b.ledger.data))
    assertTrue(b.engine.meta.restorePending && b.engine.meta.restoredRebuild)
    //  برق میانِ بازسازی (نوشتنِ دفتر) ⇒ همان‌جا برگردانده می‌شود؛ هنوز «در انتظار»، هیچ Pull
    server.requests.clear()
    b.ledger.failWrite = 1
    assertTrue(runCatching { b.sync(first = false) }.isFailure)
    assertTrue(b.engine.meta.restorePending)
    assertFalse("GET /pull" in server.requests)
    b.engine = b.make()
    b.engine.recover(b.ledger.data)
    server.requests.clear()
    assertTrue(b.sync(first = false).ok)
    rebuiltBeforeAnyPull(server)
    assertTrue(a.sync(first = false).ok)
    sameAsServer(server, a, b)
  }

  @Test fun aQueueWithoutStateIsRebuiltNotInitialized() {
    //  صف با پشتیبان آمد و حال نه، ولی شناسهٔ دستگاه پیش از آن ساخته شده بود (`restoredAt` ِ صفر) ⇒ بستن، با همان بازسازی
    val server = FakeServer()
    val (_, files) = phoneWithBackup(server, withState = false, after = false)
    val b = restored("dev-b", server, files)
    assertEquals(RestoredDevice.Verdict.BIND, b.engine.fence("dev-b", 0L, b.ledger.data))
    assertTrue(b.engine.meta.restorePending)
    assertFalse(b.engine.meta.restoredRebuild)
    assertTrue(b.sync(first = false).ok)
    sameAsServer(server, b)
  }
}
