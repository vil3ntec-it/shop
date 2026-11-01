package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.sync.platform.FileReplayStore
import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.core.sync.RestoreReplay
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  §۹.۵ برای Sync v1 — `V1Restore` با همان ترتیبی که `SyncV1Engine.runOnce` صدا می‌زند:
 *
 *      دوران ⇒ check ⇒ Push (accepted پیش از ack، ردِ دوباره‌فرستاده بی‌صدا) ⇒
 *      «در انتظار» و صفِ خالی ⇒ Snapshot ⇒ localOnly ⇒ (سرور: حذف‌شده‌ها) ⇒ rebuild ⇒ Push ⇒
 *      Pull (در انتظار ⇒ نه؛ گرفته‌ها پیش از نشاندن در دفترچه)
 *
 *  سرورِ حافظه‌ای قراردادِ `lib/sync-v1.js` را دارد: op_id ِ تکراری ⇒ «duplicate» با همان
 *  شماره، اعمال به ترتیبِ رسیدن (`$inc` جمع، فیلدِ ساده و درج آخرین)، حذفِ نرم (Snapshot ندارد،
 *  `/sync-epoch/deleted` دارد)، pull بی opهای خودِ دستگاه با شماره و دستگاهِ سازنده، و دورانِ
 *  `lib/sync-epoch.js`. پشتیبان/بازیابی شمارنده را هم برمی‌گرداند. صف، سایه و cursor ِ هر دستگاه
 *  «دیسک»ِ حافظه‌ای است و ژورنال و دفترچه فایلِ واقعی.
 */
class V1RestoreTest {

  /* ------------------------------------------------ سرورِ حافظه‌ای */

  private class Server {
    var seq = 0L
    var tree: JsonObject = JsonObject(emptyMap())
    val log = ArrayList<Triple<Long, String, SyncOp>>()
    val seen = HashMap<String, Long>()
    val gone = HashSet<RestoreMerge.Row>()
    var epochId = "ep_1"
    var epochSeq = 0L
    val refuse = HashSet<String>()
    var offline = false
    /** پرچمِ `SYNC_EPOCH_ENABLED` ِ سرور خاموش ⇒ درِ دوران ۴۰۴ (برنامه null می‌بیند)، بقیه همان */
    var epochOff = false
    var pushes = 0
    /** شمارِ `GET /api/shop/sync-epoch` — خاموش ⇒ هر بار ۴۰۴ (آزمونِ «خاموش ⇒ هیچ درخواستی») */
    var epochCalls = 0
    var epoch404 = 0

    /** درِ جدای دوران: خاموش ⇒ ۴۰۴ (برنامه null می‌بیند) */
    fun epochDoor(): RestoreReplay.Epoch? {
      check(!offline) { "offline" }
      epochCalls++
      if (epochOff) { epoch404++; return null }
      return epoch()
    }

    /** دورانِ کنارِ pull ِ v1 (`routes/sync-v1.js`): فقط با پرچمِ روشن */
    fun pullEpoch(): RestoreReplay.Epoch? = if (epochOff) null else epoch()

    class Saved(val seq: Long, val tree: JsonObject, val log: List<Triple<Long, String, SyncOp>>, val seen: Map<String, Long>, val gone: Set<RestoreMerge.Row>)
    fun backup() = Saved(seq, tree, log.toList(), HashMap(seen), HashSet(gone))
    fun restore(s: Saved) {
      seq = s.seq; tree = s.tree; log.clear(); log += s.log; seen.clear(); seen.putAll(s.seen); gone.clear(); gone += s.gone
      epochId = "ep_${s.seq}_${System.nanoTime()}"; epochSeq = s.seq
    }

    fun epoch() = RestoreReplay.Epoch(epochId, log.filter { it.first <= epochSeq }.maxOfOrNull { it.first } ?: 0L, seq)

    fun push(device: String, ops: List<SyncOp>): List<OpResult> {
      check(!offline) { "offline" }
      pushes++
      return ops.map { op ->
        val dup = seen[op.opId]
        when {
          dup != null -> OpResult(op.opId, "duplicate", seq = dup)
          op.opId in refuse -> OpResult(op.opId, "rejected", "forbidden")
          else -> {
            tree = LedgerDiff.applyRemote(tree, listOf(op))
            val row = RestoreMerge.Row(op.table, op.rowId)
            if (op.type == SyncOp.DELETE) gone += row else gone -= row
            seq += 1
            seen[op.opId] = seq
            log += Triple(seq, device, op)
            OpResult(op.opId, "applied", seq = seq)
          }
        }
      }
    }

    fun pull(device: String, since: Long): Pair<List<Triple<Long, String, SyncOp>>, Long> =
      log.filter { it.first > since && it.second != device } to maxOf(seq, since)

    fun deletedOf(rows: List<RestoreMerge.Row>): Set<RestoreMerge.Row> = rows.filter { it in gone }.toSet()

    fun snapshot(): JsonObject = buildJsonObject {
      put("tables", buildJsonObject {
        for (key in LedgerDiff.COLLECTIONS) {
          val rows = (tree[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
          put(key, JsonArray(rows.map { r -> buildJsonObject { put("id", r.getValue("id")); put("data", JsonObject(r - "id")) } }))
        }
        //  فهرست‌های تنظیماتی: هر نام یک ردیف (`row_id` = sha ِ نام)
        for (key in LedgerDiff.LIST_COLLECTIONS) {
          val names = (tree[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
          if (names.isNotEmpty()) put(key, JsonArray(names.map { n -> buildJsonObject { put("id", JsonPrimitive(LedgerDiff.listRowId(n))); put("data", buildJsonObject { put("name", JsonPrimitive(n)) }) } }))
        }
      })
      put("cursor", JsonPrimitive(seq))
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
  }

  /* ------------------------------------------------ دستگاه */

  private val tmp: File = Files.createTempDirectory("v1-restore").toFile()

  private inner class Device(val name: String, val server: Server) : V1Restore.State, V1Restore.Queue {
    var ledger = ShopData()
    var shadow = ""
    override var cursor = 0L
    override var epoch = ""
    override var restorePending = false
    override var epochLive = false
    var gapNoted = false
    val queue = ArrayList<SyncOp>()
    val dropped = ArrayList<SyncOp>()
    var rebuilt = 0
    var crashAt: String? = null
    val archived = HashMap<String, Set<String>>()

    override fun commitRestore(epoch: String, pending: Boolean): Boolean { this.epoch = epoch; restorePending = pending; return true }
    override fun adoptEpoch(epoch: String) { this.epoch = epoch }
    override fun noteGap(at: Long) { gapNoted = true }
    override fun all(): List<SyncOp> = queue.toList()
    override fun pushFront(ops: List<SyncOp>): Int {
      val have = queue.mapTo(HashSet()) { it.opId }
      val add = ops.filter { have.add(it.opId) }
      queue.addAll(0, add)
      return add.size
    }

    private val parts = object : SyncCommit.Parts {
      override fun shadow(): String = shadow
      override fun commit(shadow: String, cursor: Long?) {
        if (crashAt == "commit") throw IllegalStateException("برنامه بسته شد")
        this@Device.shadow = shadow
        cursor?.let { this@Device.cursor = it }
      }
      override fun queued(): Set<String> = queue.mapTo(HashSet()) { it.opId }
      override fun push(ops: List<SyncOp>) { queue += ops }
    }
    val commit = SyncCommit(SyncCommit.Journal(File(tmp, "$name-journal.json")), parts)
    var book = FileReplayStore.inDir(tmp, name)
    var restore = V1Restore(this, this, book, commit, { 0L }) { archived }

    /** دفترچه از دست رفت (برنامه میانِ پشتیبان و بازیابی به‌روز شد / پاک شد) */
    fun loseBook() {
      File(tmp, "$name-sent.jsonl").delete()
      book = FileReplayStore.inDir(tmp, name)
      restore = V1Restore(this, this, book, commit, { 0L }) { archived }
    }

    /** `ShopStore.save` ⇒ `record` */
    fun edit(f: (ShopData) -> ShopData) { ledger = f(ledger); commit.record(ledger) }

    /** ذخیره‌ای که `record`ش نرسید (مثلاً حافظهٔ پر) */
    fun editUnrecorded(f: (ShopData) -> ShopData) { ledger = f(ledger) }

    /** دستگاهِ نخست: دفترِ موجود یک‌جا (`baseline`) */
    fun baseline(data: ShopData) { ledger = data; commit.baseline(data) }

    /** گوشیِ نو: Snapshot (`restoreFromServer`) */
    fun join() {
      val snap = server.snapshot()
      val next = LedgerDiff.fromSnapshot(snap, LedgerDiff.toJson(ledger))
      ledger = LedgerDiff.fromJson(next)
      parts.commit(LedgerDiff.shadowOf(next).toString(), server.seq)
      //  `restoreFromServer`: دوران کنارِ همان Snapshot (فقط با پرچمِ روشن)، بی درِ جدا
      val ep = server.pullEpoch()
      restore.pulled(ep)
      if (ep != null) epoch = ep.id
    }

    /** `SyncV1Engine.pushAll` */
    private fun pushAll(): Boolean {
      while (queue.isNotEmpty()) {
        val batch = queue.toList()
        val results = runCatching { server.push(name, batch) }.getOrElse { return false }
        val accepted = results.filter { it.accepted }.map { it.opId }.toHashSet()
        restore.accepted(batch, results)
        queue.removeAll { it.opId in accepted }
        val (replayed, refused) = results.filter { !it.accepted }.partition { restore.isReplay(it.opId) }
        queue.removeAll { op -> replayed.any { it.opId == op.opId } }
        val bad = refused.map { it.opId }.toHashSet()
        dropped += batch.filter { it.opId in bad }
        queue.removeAll { it.opId in bad }
      }
      return true
    }

    /** همان ترتیبِ `SyncV1Engine.runOnce` */
    fun sync(): Boolean {
      if (commit.pending()) commit.recover(ledger)
      //  `SyncV1Engine.checkEpoch`: درِ دوران فقط وقتی آخرین pull دوران آورد؛ ۴۰۴ ⇒ `closed`
      if (restore.asks()) {
        val ep = runCatching { server.epochDoor() }
        ep.onSuccess { if (it == null) restore.closed() else restore.check(it) }
      }
      if (!pushAll()) return false
      if (restorePending && queue.isEmpty()) {
        if (server.offline) return false
        val snap = server.snapshot()
        val ask = restore.localOnly(snap, ledger)
        val gone = server.deletedOf(ask)
        if (rebuild(snap, gone) && !pushAll()) return false
      }
      if (!restorePending) {
        val (got, next) = server.pull(name, cursor)
        restore.seen(got.map { (seq, device, op) -> ReplayBook.Sent(op.opId, seq, op.toJson(), device) })
        restore.pulled(server.pullEpoch())
        val ops = got.map { it.third }
        if (ops.isNotEmpty()) commit.absorb(ops, ledger, next) { ledger = it } else cursor = next
      }
      return true
    }

    /** زیرِ قفل، با همان «شکست ⇒ همین‌جا بازیابی» ِ `SyncV1Engine` */
    fun rebuild(snapshot: JsonObject, gone: Set<RestoreMerge.Row> = emptySet()): Boolean {
      if (commit.pending()) commit.recover(ledger)
      val built = try {
        restore.rebuild(snapshot, ledger, gone) { if (crashAt == "ledger") throw IllegalStateException("برنامه بسته شد"); ledger = it }
      } catch (e: Throwable) {
        runCatching { commit.recover(ledger) }
        throw e
      }
      if (built) rebuilt++
      return built
    }

    fun paid(id: String = "s1") = ledger.sales.single { it.id == id }.paidAmount
    fun price(id: String = "p1") = ledger.products.single { it.id == id }.salePrice
    fun returned(id: String) = ledger.saleItems.single { it.id == id }.returnedQty
  }

  private val p1 = Product(id = "p1", name = "شیر", salePrice = 100.0)
  private val p2 = Product(id = "p2", name = "ماست", salePrice = 50.0)
  private val base = ShopData(
    products = listOf(p1, p2),
    sales = listOf(Sale(id = "s1", total = 300.0, finalTotal = 300.0, paidAmount = 100.0, remaining = 200.0)),
    stockMovements = listOf(StockMovement(id = "m-old", productId = "p1", type = "sale", qty = -1.0)),
  )

  private fun shop(tag: String = ""): Triple<Server, Device, Device> {
    val server = Server()
    val a = Device("a$tag", server).apply { baseline(base); assertTrue(sync()) }
    val b = Device("b$tag", server).apply { join(); assertTrue(sync()) }
    return Triple(server, a, b)
  }

  private fun probe(server: Server) = Device("probe${System.nanoTime()}", server).apply { join() }

  /** همه با سرور (و یک دستگاهِ تازه) یکی‌اند؛ هیچ‌کس در انتظار نمانده و opِ تازه‌ای نمی‌سازد */
  private fun converged(server: Server, vararg devices: Device) {
    val fresh = probe(server)
    for (d in devices) {
      assertEquals("${d.name}: فروش", fresh.ledger.sales.map { it.id to it.paidAmount }, d.ledger.sales.map { it.id to it.paidAmount })
      assertEquals("${d.name}: قلم", fresh.ledger.saleItems.map { it.id to it.returnedQty }, d.ledger.saleItems.map { it.id to it.returnedQty })
      assertEquals("${d.name}: کالا", fresh.ledger.products.map { it.id to it.salePrice }, d.ledger.products.map { it.id to it.salePrice })
      assertFalse("${d.name}: در انتظار", d.restorePending)
      assertTrue("${d.name}: صف", d.queue.isEmpty())
      assertEquals("${d.name}: opِ تازه", 0, d.commit.record(d.ledger))
    }
  }

  private fun sale(id: String, item: String) = { d: ShopData ->
    d.copy(
      sales = d.sales + Sale(id = id, total = 200.0, finalTotal = 200.0, paidAmount = 200.0),
      saleItems = d.saleItems + SaleItem(id = item, saleId = id, productId = "p1", quantity = 2.0, unitPrice = 100.0, totalPrice = 200.0),
    )
  }

  /* ------------------------------------------------ آزمون‌ها */

  @Test fun `فروشِ پس از پشتیبان دوباره می‌رسد و دلتای دوباره‌رسیده دو بار نمی‌نشیند`() {
    val (server, a, b) = shop()
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 150.0) }) }
    assertTrue(b.sync())
    assertTrue(a.sync())
    assertEquals(150.0, a.paid(), 0.0)

    server.restore(saved)
    assertEquals(100.0, probe(server).paid(), 0.0)
    //  دستگاهِ سوم پس از بازیابی قیمت را عوض می‌کند — شماره‌اش زیرِ cursor ِ کهنهٔ a و b است
    val c = Device("c", server).apply { join() }
    c.edit { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 130.0) else it }) }
    assertTrue(c.sync())

    assertTrue(b.sync())
    assertEquals("b همان دلتای خودش را با همان op_id دوباره فرستاد", 150.0, probe(server).paid(), 0.0)
    assertEquals(1, b.rebuilt)
    assertEquals(130.0, b.price(), 0.0)
    assertTrue(a.sync())
    assertEquals("a دلتای گرفته را هم دوباره فرستاد («duplicate») و از نو ساخت؛ ۱۵۰ نه ۲۰۰", 150.0, a.paid(), 0.0)
    assertEquals(130.0, a.price(), 0.0)
    assertTrue(c.sync())
    assertEquals("c پس از بازیابی آمد ⇒ کاری ندارد", 0, c.rebuilt)
    assertEquals(150.0, c.paid(), 0.0)
    converged(server, a, b, c)
  }

  @Test fun `ویرایشِ یک فیلد در دو دستگاه پس از پشتیبان ⇒ همه به ترتیبِ سرور یکی می‌شوند`() {
    //  بازبین #۱: b ۱۱۱، a پس از دیدنِ آن ۲۲۲؛ پس از بازیابی اول a بعد b دوباره می‌فرستند
    val (server, a, b) = shop()
    val saved = server.backup()
    b.edit { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 111.0) else it }) }
    assertTrue(b.sync())
    assertTrue(a.sync())
    a.edit { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 222.0) else it }) }
    assertTrue(a.sync())
    assertTrue(b.sync())
    assertEquals(222.0, b.price(), 0.0)

    server.restore(saved)
    assertTrue(a.sync())
    assertEquals("a هر دو را به ترتیبِ پیش از بازیابی فرستاد", 222.0, probe(server).price(), 0.0)
    assertTrue(b.sync())
    assertTrue(a.sync())
    assertEquals(222.0, a.price(), 0.0)
    assertEquals(222.0, b.price(), 0.0)
    converged(server, a, b)
  }

  @Test fun `درجِ یک دستگاه و دلتای دستگاهِ دیگر روی همان قلم — هر کدام اول برسد، مرجوعی یک بار می‌ماند`() {
    //  بازبینِ دوم، P3: a فروش می‌سازد، b قلمش را یک عدد پس می‌گیرد ($inc)؛ پس از بازیابی b اول
    for (first in listOf("b", "a")) {
      val (server, a, b) = shop(first)
      val saved = server.backup()
      a.edit(sale("s2", "si2"))
      assertTrue(a.sync())
      assertTrue(b.sync())
      b.edit { d -> d.copy(saleItems = d.saleItems.map { if (it.id == "si2") it.copy(returnedQty = 1.0) else it }) }
      assertTrue("مرجوعی \$inc است", b.queue.any { it.table == "saleItems" && it.type == SyncOp.UPDATE })
      assertTrue(b.sync())
      assertTrue(a.sync())
      assertEquals(1.0, a.returned("si2"), 0.0)

      server.restore(saved)
      val (x, y) = if (first == "b") b to a else a to b
      assertTrue(x.sync())
      assertEquals("$first اول: سرور", 1.0, probe(server).returned("si2"), 0.0)
      assertTrue(y.sync())
      assertTrue(x.sync())
      assertEquals("$first اول: سرور پس از هر دو (درجِ دیرتر دلتا را نپوشاند)", 1.0, probe(server).returned("si2"), 0.0)
      assertEquals(1.0, a.returned("si2"), 0.0)
      assertEquals(1.0, b.returned("si2"), 0.0)
      converged(server, a, b)
    }
  }

  @Test fun `دستگاهِ سازنده دیگر برنمی‌گردد — فروشش از دفترچهٔ دستگاهی که گرفته بود می‌رسد`() {
    //  بازبینِ دوم، B1(a): پیش‌تر ساختنِ دوبارهٔ b آخرین نسخهٔ این فروش را پاک می‌کرد
    val (server, a, b) = shop()
    val saved = server.backup()
    a.edit(sale("s3", "si3"))
    assertTrue(a.sync())
    assertTrue(b.sync())
    server.restore(saved)
    //  a گم شد/خاموش ماند؛ فقط b همگام می‌شود
    assertTrue(b.sync())
    assertEquals(1, b.rebuilt)
    assertTrue("فروشِ a روی b ماند", b.ledger.sales.any { it.id == "s3" })
    assertTrue("و به سرور رسید", probe(server).ledger.sales.any { it.id == "s3" })
    assertEquals("قلمش هم", 1, probe(server).ledger.saleItems.count { it.id == "si3" })
    converged(server, b)
  }

  @Test fun `فروشِ خودِ دستگاه که در دفترچه نیست (برنامه میانِ پشتیبان و بازیابی به‌روز شد) می‌ماند و درج می‌رود`() {
    //  بازبینِ دوم، B1(b)
    val (server, a, b) = shop()
    val saved = server.backup()
    b.edit(sale("s4", "si4"))
    assertTrue(b.sync())
    b.loseBook()
    server.restore(saved)
    assertTrue(b.sync())
    assertEquals(1, b.rebuilt)
    assertTrue("پوششِ دفترچه کم بود ⇒ هشدار", b.gapNoted)
    assertTrue(b.ledger.sales.any { it.id == "s4" })
    assertTrue("درجِ تازه به سرور رسید", probe(server).ledger.sales.any { it.id == "s4" })
    assertTrue(a.sync())
    assertTrue(a.ledger.sales.any { it.id == "s4" })
    converged(server, a, b)
  }

  @Test fun `ردیفی که پس از بازیابی روی سرور حذف شد با ساختنِ دوبارهٔ دستگاهِ دیرآمده زنده نمی‌شود`() {
    val (server, a, b) = shop()
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 150.0) }) }
    assertTrue(b.sync())
    assertTrue(a.sync())
    server.restore(saved)
    assertTrue(a.sync())
    a.edit { d -> d.copy(products = d.products.filter { it.id != "p2" }) }
    assertTrue(a.sync())
    assertFalse(probe(server).ledger.products.any { it.id == "p2" })
    //  b دیر می‌رسد: p2 هنوز در دفترش است و Snapshot ندارد ⇒ از سرور می‌پرسد ⇒ حذف‌شده
    assertTrue(b.sync())
    assertFalse("p2 روی b ماند", b.ledger.products.any { it.id == "p2" })
    assertFalse("p2 دوباره درج شد", probe(server).ledger.products.any { it.id == "p2" })
    converged(server, a, b)
  }

  @Test fun `ردیفِ بایگانی‌شدهٔ دفتر از Snapshot برنمی‌گردد و opی نمی‌سازد`() {
    val (server, a, b) = shop()
    //  `LedgerArchive.compact`: از دفتر به بایگانی، از سایه بیرون، بی op
    assertEquals(1, a.commit.forget(mapOf("stockMovements" to listOf("m-old"))))
    a.ledger = a.ledger.copy(stockMovements = emptyList())
    a.archived["stockMovements"] = setOf("m-old")
    assertEquals(0, a.commit.record(a.ledger))
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 150.0) }) }
    assertTrue(b.sync())
    assertTrue(a.sync())
    server.restore(saved)
    assertTrue(a.sync())
    assertEquals(1, a.rebuilt)
    assertTrue("بایگانی‌شده به دفتر برگشت", a.ledger.stockMovements.none { it.id == "m-old" })
    assertTrue(a.queue.isEmpty())
    assertTrue("سرور هنوز دارد", probe(server).ledger.stockMovements.any { it.id == "m-old" })
    assertEquals(0, a.commit.record(a.ledger))
  }

  @Test fun `دستگاهی که پس از پشتیبان با Snapshot آمد دلتای دوباره‌رسیده را دو بار نمی‌نشاند`() {
    //  بازبین #۲
    val (server, _, b) = shop()
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 150.0) }) }
    assertTrue(b.sync())
    val n = Device("n", server).apply { join(); assertTrue(sync()) }
    assertEquals(150.0, n.paid(), 0.0)

    server.restore(saved)
    assertTrue(b.sync())
    assertTrue(n.sync())
    assertEquals(150.0, n.paid(), 0.0)
    assertTrue(n.sync())
    assertEquals(150.0, n.paid(), 0.0)
    converged(server, b, n)
  }

  @Test fun `بالا آمدنِ عادیِ سرور میانِ رفت‌وآمد ⇒ نه دوباره‌فرستادن، نه ساختنِ دوباره`() {
    //  بازبین #۴: دوران با بالا آمدنِ عادی عوض نمی‌شود
    val (server, a, b) = shop()
    a.edit { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 120.0) else it }) }
    assertTrue(a.sync())
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 150.0) }) }
    val pushes = server.pushes
    assertTrue(b.sync())
    assertTrue(a.sync())
    assertTrue(b.sync())
    assertEquals(pushes + 1, server.pushes)
    assertEquals(0, a.rebuilt + b.rebuilt)
    converged(server, a, b)
  }

  @Test fun `نشانِ پشتیبانِ کشته‌شده (دورانِ تازه با پایه = سرِ دفتر) ⇒ فقط دوران عوض می‌شود`() {
    val (server, a, b) = shop()
    a.edit { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 120.0) else it }) }
    assertTrue(a.sync())
    assertTrue(b.sync())
    //  `rotate('boot')` با نشانِ مانده: id ِ تازه، پایه = سرِ دفترِ زنده
    server.epochId = "ep_marker"
    server.epochSeq = server.seq
    val pushes = server.pushes
    assertTrue(a.sync())
    assertTrue(b.sync())
    assertEquals(pushes, server.pushes)
    assertEquals(0, a.rebuilt + b.rebuilt)
    assertEquals("ep_marker", a.epoch)
    assertEquals("ep_marker", b.epoch)
    converged(server, a, b)
  }

  @Test fun `opِ دوباره‌فرستاده‌ای که حالا رد می‌شود ردشدهٔ پیداشده نیست و دفتر = سرور`() {
    //  بازبین #۱۱
    val (server, _, b) = shop()
    val saved = server.backup()
    b.edit { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 125.0) else it }) }
    assertTrue(b.sync())
    server.restore(saved)
    server.refuse += b.book.replayAbove(0).map { it.id }
    assertTrue(b.sync())
    assertTrue(b.dropped.isEmpty())
    assertEquals(100.0, b.price(), 0.0)
    converged(server, b)
  }

  @Test fun `تغییرِ محلی وسطِ بازیابی گم نمی‌شود — آفلاین، میانِ Snapshot و ساختن، و ثبت‌نشده`() {
    val (server, _, b) = shop()
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 150.0) }) }
    assertTrue(b.sync())
    server.restore(saved)

    //  دوران دیده شد ولی Push نرسید ⇒ «در انتظار» می‌ماند و Pull نمی‌شود
    b.restore.check(server.epoch())
    assertTrue(b.restorePending)
    server.offline = true
    assertFalse(b.sync())
    b.edit { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 140.0) else it }) }
    server.offline = false

    //  Snapshot گرفته شد، بعد پیش از ساختن یک تغییرِ دیگر رسید ⇒ این بار نه
    while (b.queue.isNotEmpty()) server.push("b", b.queue.toList()).also { r -> b.restore.accepted(b.queue.toList(), r); b.queue.clear() }
    val snap = server.snapshot()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 160.0, remaining = 140.0) }) }
    assertFalse("صفِ پر ⇒ ساخته نشد", b.rebuild(snap))
    assertTrue(b.restorePending)
    assertEquals(160.0, b.paid(), 0.0)

    //  ذخیره‌ای که `record`ش نرسید: صف خالی ولی تفاضل نه ⇒ اول op می‌شود، ساختن دورِ بعد
    assertTrue(b.sync())
    b.restorePending = true
    b.editUnrecorded { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 145.0) else it }) }
    assertFalse("تغییرِ ثبت‌نشده زیرِ Snapshot نرفت", b.rebuild(server.snapshot()))
    assertEquals(145.0, b.price(), 0.0)
    assertEquals(1, b.queue.size)
    assertTrue(b.sync())
    assertEquals(160.0, probe(server).paid(), 0.0)
    assertEquals(145.0, probe(server).price(), 0.0)
    converged(server, b)
  }

  @Test fun `بسته شدن وسطِ ساختنِ دوباره ⇒ یا تمام یا هیچ؛ دفترِ نیمه نه`() {
    for (at in listOf("ledger", "commit")) {
      val (server, a, b) = shop(at)
      val saved = server.backup()
      b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 150.0) }) }
      assertTrue(b.sync())
      assertTrue(a.sync())
      server.restore(saved)
      assertTrue(b.sync())

      a.restore.check(server.epoch())
      a.crashAt = at
      runCatching { a.rebuild(server.snapshot()) }
      a.crashAt = null
      assertTrue("$at: هنوز در انتظار", a.restorePending)
      //  «شروعِ دوباره»: ژورنال بازیابی می‌شود و دورِ بعد کار تمام می‌شود
      a.commit.recover(a.ledger)
      assertTrue(a.sync())
      assertFalse(a.restorePending)
      assertEquals("$at: ۱۵۰ نه ۲۰۰", 150.0, a.paid(), 0.0)
      converged(server, a, b)
    }
  }

  @Test fun `ساختنِ دوبارهٔ شکست‌خورده که بازیابی‌اش هم نشد ⇒ ثبتِ بعدی سایهٔ تازه را می‌گیرد، نه DELETE و دلتای ساختگی`() {
    //  بازبینِ دوم، SHOULD-FIX 2: `recover(null)` ژورنالِ ساختن را «برگشت» می‌گرفت و پاک می‌کرد
    val (server, a, b) = shop()
    val saved = server.backup()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 150.0, remaining = 150.0) }) }
    assertTrue(b.sync())
    assertTrue(a.sync())
    server.restore(saved)
    //  دلتای گم‌شده دیگر پذیرفته نمی‌شود ⇒ سرور ۱۰۰ می‌ماند و دفترِ تازهٔ a هم ۱۰۰
    server.refuse += a.book.replayAbove(0).map { it.id } + b.book.replayAbove(0).map { it.id }
    a.crashAt = "commit"                         //  نوشتنِ سایه و بازیابیِ همین‌جا هر دو شکست
    assertTrue(runCatching { a.sync() }.isFailure)
    a.crashAt = null
    assertTrue(a.commit.pending())
    assertEquals(100.0, a.paid(), 0.0)
    //  opِ هدفمندِ بی دفتر (CRM-10 `recordOps`) ژورنالِ ساختن را رونویسی نمی‌کند: پرتاب، ژورنال سرِ جایش
    val sweep = SyncOp("01SWEEP000000000000000001", 1L, "sales", "s1", SyncOp.UPDATE, buildJsonObject { put("note", JsonPrimitive("x")) })
    assertTrue(runCatching { a.commit.recordOps(listOf(sweep)) }.isFailure)
    assertTrue(a.commit.pending())
    assertTrue(a.queue.isEmpty())
    a.edit { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 140.0) else it }) }
    assertEquals("فقط همان ویرایش", listOf(SyncOp.UPDATE), a.queue.map { it.type })
    assertFalse(a.commit.pending())
    assertTrue(a.sync())
    assertEquals("سرور: دلتای ساختگیِ −۵۰ نرفت", 100.0, probe(server).paid(), 0.0)
    assertEquals(140.0, probe(server).price(), 0.0)
    assertTrue(b.sync())
    converged(server, a, b)
  }

  @Test fun `سرورِ کهنه (بی دوران) ⇒ هیچ تغییری`() {
    val (server, a, _) = shop()
    val saved = server.backup()
    a.edit { d -> d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 120.0) else it }) }
    assertTrue(a.sync())
    server.restore(saved)
    assertFalse(a.restore.check(null))
    assertFalse(a.restorePending)
  }

  @Test fun `پرچمِ سرور خاموش ⇒ دفترچه نوشته نمی‌شود؛ روشن شدن ⇒ پوشش از سرِ دفترِ سرور در همان لحظه`() {
    val server = Server().apply { epochOff = true }
    val a = Device("a-off", server).apply { baseline(base); assertTrue(sync()) }
    val b = Device("b-off", server).apply { assertTrue(sync()) }
    a.edit(sale("s2", "i2"))
    assertTrue(a.sync())
    assertTrue(b.sync())
    assertEquals("خاموش: نه opِ خودی", 0, a.book.count())
    assertEquals("خاموش: نه opِ گرفته‌شده", 0, b.book.count())
    assertEquals("", a.epoch)
    assertFalse(a.restore.check(null))

    assertEquals("خاموش: درِ دوران هرگز زده نشد (نه ۴۰۴ در هر دور)", 0, server.epochCalls)

    //  پرچم روشن شد: دورِ نخست فقط pull ِ دوران‌دار را می‌بیند؛ از دورِ بعد درِ دوران پرسیده می‌شود
    server.epochOff = false
    assertTrue(a.sync())
    assertEquals("هنوز پرسیده نشد", 0, server.epochCalls)
    assertTrue(a.epochLive)
    assertTrue(a.sync())
    assertEquals("ep_1", a.epoch)
    assertEquals(server.seq, a.book.floor())
    a.edit(sale("s3", "i3"))
    assertTrue(a.sync())
    val head = server.seq
    assertTrue("b هنوز عقب است", b.cursor < head)
    assertTrue(b.sync())
    assertTrue(b.sync())
    assertEquals("پوشش از سرِ دفترِ سرور در نخستین دوران", head, b.book.floor())
    assertTrue("روشن: opِ خودی نوشته شد", a.book.count() > 0)
    a.edit(sale("s4", "i4"))
    assertTrue(a.sync())
    assertTrue(b.sync())
    assertTrue("روشن: opِ گرفته‌شده نوشته شد", b.book.count() > 0)
    converged(server, a, b)
  }

  @Test fun `پرچمِ خاموش ⇒ هیچ درخواستی به درِ دوران در هیچ دوری — نه ۴۰۴ ِ تکراری`() {
    val server = Server().apply { epochOff = true }
    val a = Device("a-quiet", server).apply { baseline(base) }
    val b = Device("b-quiet", server).apply { join() }
    repeat(20) { i ->
      a.edit(sale("q$i", "qi$i"))
      assertTrue(a.sync())
      assertTrue(b.sync())
    }
    assertEquals("خاموش ⇒ هرگز", 0, server.epochCalls)
    assertFalse(a.epochLive)
    assertFalse(b.epochLive)
    assertEquals("", b.epoch)
    converged(server, a, b)
  }

  @Test fun `پرچم پس از روشن بودن خاموش شد ⇒ یک ۴۰۴ و دیگر هیچ؛ روشن شدنِ دوباره ⇒ دوباره پرسیده می‌شود`() {
    val (server, a, _) = shop("-flip")
    assertTrue(a.sync())
    assertTrue("روشن ⇒ پرسیده می‌شود", server.epochCalls > 0)
    server.epochOff = true
    val before = server.epochCalls
    repeat(10) { assertTrue(a.sync()) }
    assertEquals("فقط یک بار، همان ۴۰۴", 1, server.epochCalls - before)
    assertEquals(1, server.epoch404)
    assertFalse(a.epochLive)
    server.epochOff = false
    assertTrue(a.sync())
    val on = server.epochCalls
    assertTrue(a.sync())
    assertEquals("روشن ⇒ دوباره هر دور", on + 1, server.epochCalls)
  }
}
