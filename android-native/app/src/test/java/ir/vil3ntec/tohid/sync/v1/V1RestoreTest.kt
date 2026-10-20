package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.core.sync.RestoreReplay
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
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
 *      «در انتظار» و صفِ خالی ⇒ Snapshot ⇒ rebuild ⇒ Pull (در انتظار ⇒ نه)
 *
 *  سرورِ حافظه‌ای قراردادِ `lib/sync-v1.js` را دارد: op_id ِ تکراری ⇒ «duplicate» با همان
 *  شماره، اعمال به ترتیبِ رسیدن (`$inc` جمع، فیلدِ ساده آخرین)، pull بی opهای خودِ دستگاه،
 *  و دورانِ `lib/sync-epoch.js` (id فقط با بازیابی عوض می‌شود). پشتیبان/بازیابی شمارنده را هم
 *  برمی‌گرداند. صف، سایه و cursor ِ هر دستگاه «دیسک»ِ حافظه‌ای است و ژورنال و دفترچه فایلِ واقعی.
 */
class V1RestoreTest {

  /* ------------------------------------------------ سرورِ حافظه‌ای */

  private class Server {
    var seq = 0L
    var tree: JsonObject = JsonObject(emptyMap())
    val log = ArrayList<Triple<Long, String, SyncOp>>()
    val seen = HashMap<String, Long>()
    var epochId = "ep_1"
    var epochSeq = 0L
    val refuse = HashSet<String>()
    var offline = false
    var pushes = 0

    class Saved(val seq: Long, val tree: JsonObject, val log: List<Triple<Long, String, SyncOp>>, val seen: Map<String, Long>)
    fun backup() = Saved(seq, tree, log.toList(), HashMap(seen))
    fun restore(s: Saved) {
      seq = s.seq; tree = s.tree; log.clear(); log += s.log; seen.clear(); seen.putAll(s.seen)
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
            seq += 1
            seen[op.opId] = seq
            log += Triple(seq, device, op)
            OpResult(op.opId, "applied", seq = seq)
          }
        }
      }
    }

    fun pull(device: String, since: Long): Pair<List<SyncOp>, Long> =
      log.filter { it.first > since && it.second != device }.map { it.third } to maxOf(seq, since)

    fun snapshot(): JsonObject = buildJsonObject {
      put("tables", buildJsonObject {
        for (key in LedgerDiff.COLLECTIONS) {
          val rows = (tree[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
          put(key, JsonArray(rows.map { r -> buildJsonObject { put("id", r.getValue("id")); put("data", JsonObject(r - "id")) } }))
        }
      })
      put("cursor", JsonPrimitive(seq))
    }
  }

  /* ------------------------------------------------ دستگاه */

  private val tmp: File = Files.createTempDirectory("v1-restore").toFile()

  private inner class Device(val name: String, val server: Server) : V1Restore.State, V1Restore.Queue {
    var ledger = ShopData()
    var shadow = ""
    override var cursor = 0L
    override var epoch = ""
    override var restorePending = false
    val queue = ArrayList<SyncOp>()
    val dropped = ArrayList<SyncOp>()
    var rebuilt = 0
    var crashAt: String? = null

    override fun commitRestore(epoch: String, pending: Boolean): Boolean { this.epoch = epoch; restorePending = pending; return true }
    override fun adoptEpoch(epoch: String) { this.epoch = epoch }
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
    val book = ReplayBook.inDir(tmp, name)
    val restore = V1Restore(this, this, book, commit)

    /** `ShopStore.save` ⇒ `record` */
    fun edit(f: (ShopData) -> ShopData) { ledger = f(ledger); commit.record(ledger) }

    /** دستگاهِ نخست: دفترِ موجود یک‌جا (`baseline`) */
    fun baseline(data: ShopData) { ledger = data; commit.baseline(data) }

    /** گوشیِ نو: Snapshot (`restoreFromServer`) */
    fun join() {
      val snap = server.snapshot()
      val next = LedgerDiff.fromSnapshot(snap, LedgerDiff.toJson(ledger))
      ledger = LedgerDiff.fromJson(next)
      parts.commit(LedgerDiff.shadowOf(next).toString(), server.seq)
    }

    /** همان ترتیبِ `SyncV1Engine.runOnce` */
    fun sync(): Boolean {
      if (commit.pending()) commit.recover(ledger)
      restore.check(if (server.offline) null else server.epoch())
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
      if (restorePending && queue.isEmpty()) rebuild(server.snapshot())
      if (!restorePending) {
        val (ops, next) = server.pull(name, cursor)
        if (ops.isNotEmpty()) commit.absorb(ops, ledger, next) { ledger = it } else cursor = next
      }
      return true
    }

    fun rebuild(snapshot: JsonObject) {
      if (commit.pending()) commit.recover(ledger)
      if (restore.rebuild(snapshot, ledger) { if (crashAt == "ledger") throw IllegalStateException("برنامه بسته شد"); ledger = it }) rebuilt++
    }

    fun paid(id: String = "s1") = ledger.sales.single { it.id == id }.paidAmount
    fun price(id: String = "p1") = ledger.products.single { it.id == id }.salePrice
  }

  private val p1 = Product(id = "p1", name = "شیر", salePrice = 100.0)
  private val base = ShopData(products = listOf(p1), sales = listOf(Sale(id = "s1", total = 300.0, finalTotal = 300.0, paidAmount = 100.0, remaining = 200.0)))

  private fun shop(tag: String = ""): Triple<Server, Device, Device> {
    val server = Server()
    val a = Device("a$tag", server).apply { baseline(base); assertTrue(sync()) }
    val b = Device("b$tag", server).apply { join(); assertTrue(sync()) }
    return Triple(server, a, b)
  }

  /** همه با سرور (و یک دستگاهِ تازه) یکی‌اند؛ هیچ‌کس در انتظار نمانده و opِ تازه‌ای نمی‌سازد */
  private fun converged(server: Server, vararg devices: Device) {
    val fresh = Device("fresh${System.nanoTime()}", server).apply { join() }
    for (d in devices) {
      assertEquals("${d.name}: فروش", fresh.ledger.sales.map { it.id to it.paidAmount }, d.ledger.sales.map { it.id to it.paidAmount })
      assertEquals("${d.name}: کالا", fresh.ledger.products.map { it.id to it.salePrice }, d.ledger.products.map { it.id to it.salePrice })
      assertFalse("${d.name}: در انتظار", d.restorePending)
      assertTrue("${d.name}: صف", d.queue.isEmpty())
      assertEquals("${d.name}: opِ تازه", 0, d.commit.record(d.ledger))
    }
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
    assertEquals(100.0, Device("probe", server).apply { join() }.paid(), 0.0)
    //  دستگاهِ سوم پس از بازیابی قیمت را عوض می‌کند — شماره‌اش زیرِ cursor ِ کهنهٔ a و b است
    val c = Device("c", server).apply { join() }
    c.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 130.0) }) }
    assertTrue(c.sync())

    assertTrue(b.sync())
    assertEquals("b همان دلتای خودش را با همان op_id دوباره فرستاد", 150.0, Device("probe2", server).apply { join() }.paid(), 0.0)
    assertEquals(1, b.rebuilt)
    assertEquals(130.0, b.price(), 0.0)
    assertTrue(a.sync())
    assertEquals("a چیزی نفرستاده بود ولی پس از پایه دیده بود ⇒ از نو؛ ۱۵۰ نه ۲۰۰", 150.0, a.paid(), 0.0)
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
    b.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 111.0) }) }
    assertTrue(b.sync())
    assertTrue(a.sync())
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 222.0) }) }
    assertTrue(a.sync())
    assertTrue(b.sync())
    assertEquals(222.0, b.price(), 0.0)

    server.restore(saved)
    assertTrue(a.sync())
    assertTrue(b.sync())
    assertTrue(a.sync())
    val truth = Device("probe", server).apply { join() }.price()
    assertEquals(truth, a.price(), 0.0)
    assertEquals(truth, b.price(), 0.0)
    converged(server, a, b)
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
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 120.0) }) }
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

  @Test fun `opِ دوباره‌فرستاده‌ای که حالا رد می‌شود ردشدهٔ پیداشده نیست و دفتر = سرور`() {
    //  بازبین #۱۱
    val (server, _, b) = shop()
    val saved = server.backup()
    b.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 125.0) }) }
    assertTrue(b.sync())
    server.restore(saved)
    server.refuse += b.book.replayAbove(0).map { it.id }
    assertTrue(b.sync())
    assertTrue(b.dropped.isEmpty())
    assertEquals(100.0, b.price(), 0.0)
    converged(server, b)
  }

  @Test fun `تغییرِ محلی وسطِ بازیابی گم نمی‌شود — آفلاین، و میانِ گرفتنِ Snapshot و ساختن`() {
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
    b.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 140.0) }) }
    server.offline = false

    //  Snapshot گرفته شد، بعد پیش از ساختن یک تغییرِ دیگر رسید
    while (b.queue.isNotEmpty()) server.push("b", b.queue.toList()).also { r -> b.restore.accepted(b.queue.toList(), r); b.queue.clear() }
    val snap = server.snapshot()
    b.edit { d -> d.copy(sales = d.sales.map { it.copy(paidAmount = 160.0, remaining = 140.0) }) }
    b.rebuild(snap)
    assertFalse(b.restorePending)
    assertEquals("تغییرِ نرفته روی Snapshot نشست", 160.0, b.paid(), 0.0)
    assertEquals(140.0, b.price(), 0.0)
    assertEquals("و هنوز در صف است، بی opِ تازه", 1, b.queue.size)
    assertEquals(0, b.commit.record(b.ledger))
    assertTrue(b.sync())
    assertEquals(160.0, Device("probe", server).apply { join() }.paid(), 0.0)
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

  @Test fun `سرورِ کهنه (بی دوران) ⇒ هیچ تغییری`() {
    val (server, a, _) = shop()
    val saved = server.backup()
    a.edit { d -> d.copy(products = d.products.map { it.copy(salePrice = 120.0) }) }
    assertTrue(a.sync())
    server.restore(saved)
    assertFalse(a.restore.check(null))
    assertFalse(a.restorePending)
  }

  private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
