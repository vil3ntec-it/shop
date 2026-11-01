package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  «تغییرِ کاربر روی دستگاه گم نشود» — دو یافتهٔ ممیزیِ SYN روی همان راهِ `SyncV1Engine.absorbRemote`:
 *
 *   ۲) ویرایشی که وسطِ یک Pull ثبت شد (در صف است) با opِ رسیده بازنویسی می‌شد ⇒ پرچمِ `sync.keep_pending`:
 *      opها پیش از نشاندن از `LedgerDiff.keepPending` می‌گذرند (قاعده: `core/…/core/sync/KeepPending.kt`).
 *   ۳) ردیفی که دستگاهِ دیگر پس از حذف زنده کرد روی دستگاهِ حذف‌کننده نیمه‌ای می‌نشست ⇒ پرچمِ سرورِ
 *      `sync.revive_full_row`: op ِ زنده‌کننده `row` (ردیفِ کاملِ سرور پس از همان op) دارد.
 *
 *  همان `SyncCommit` با «دیسکِ» حافظه‌ای (مثلِ `SyncCommitTest`): ژورنال، صف، سایه و cursor. سنجه همیشه دو
 *  چیز است: دفتر همان است که سرور پس از رسیدنِ صف خواهد داشت، و تفاضلِ بعدی **هیچ** op ِ پژواکی نمی‌سازد.
 *  بی پرچم (رفتارِ امروز) هم سنجیده می‌شود تا روشن باشد چه چیزی عوض شد.
 */
class KeepLocalTest {

  private class Disk {
    var ledger = ShopData()
    var shadow = ""
    var cursor = 0L
    val queue = ArrayList<SyncOp>()
  }

  private class Crash : RuntimeException("برنامه بسته شد")

  private class Parts(private val disk: Disk) : SyncCommit.Parts {
    override fun shadow(): String = disk.shadow
    override fun commit(shadow: String, cursor: Long?) {
      disk.shadow = shadow
      cursor?.let { disk.cursor = it }
    }
    override fun queued(): Set<String> = disk.queue.mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) { disk.queue += ops }
  }

  private val journalFile: File = Files.createTempDirectory("keep-local").toFile().resolve("sync-journal-k.json")
  private fun session(disk: Disk) = SyncCommit(SyncCommit.Journal(journalFile), Parts(disk))

  /** کارِ `ShopStore.writeLocked`: دفتر روی دیسک، بعد `record` */
  private fun save(disk: Disk, next: ShopData): Int {
    disk.ledger = next
    return session(disk).record(next)
  }

  /** کارِ `SyncV1Engine.absorbRemote` با/بی پرچم (همان دو خط) */
  private fun pull(disk: Disk, ops: List<SyncOp>, cursor: Long, keep: Boolean) {
    val use = if (keep) LedgerDiff.keepPending(ops, disk.queue) else ops
    if (use.isEmpty()) { disk.cursor = cursor; return }
    session(disk).absorb(use, disk.ledger, cursor) { disk.ledger = it }
  }

  /** دستگاهِ همگام با یک کالا و یک فاکتور؛ صف خالی */
  private fun synced(): Disk {
    val d = Disk()
    val base = ShopData(
      products = listOf(Product("p1", name = "چای", unit = "بسته", salePrice = 100.0, barcodes = listOf("123"))),
      sales = listOf(Sale("s1", total = 1000.0, finalTotal = 1000.0, paidAmount = 100.0, remaining = 900.0)),
    )
    d.ledger = base
    session(d).record(base)           // نخستین بار: فقط سایه
    assertTrue(d.queue.isEmpty())
    return d
  }

  private fun theirs(id: String, table: String, row: String, type: String, fields: JsonObject?, full: JsonObject? = null) =
    SyncOp(id, 1L, table, row, type, fields, full)

  private fun fields(vararg kv: Pair<String, Any>) = JsonObject(kv.associate { (k, v) ->
    k to when (v) {
      is Number -> JsonPrimitive(v)
      is JsonObject -> v
      else -> JsonPrimitive(v.toString())
    }
  })

  private fun inc(n: Number) = buildJsonObject { put("\$inc", JsonPrimitive(n)) }

  private fun product(d: Disk, id: String = "p1") = d.ledger.products.firstOrNull { it.id == id }

  /** تفاضلِ بعدی هیچ op ِ پژواکی نمی‌سازد */
  private fun noEcho(d: Disk) {
    val before = d.queue.size
    assertEquals("پس از نشاندن تفاضلی نماند", 0, save(d, d.ledger))
    assertEquals(before, d.queue.size)
  }

  @Test fun `ویرایشِ وسطِ pull با پرچم می‌ماند و در صف است و فیلدِ دیگرِ رسیده می‌نشیند`() {
    val d = synced()
    save(d, d.ledger.copy(products = d.ledger.products.map { it.copy(salePrice = 200.0) }))
    assertEquals(1, d.queue.size)
    pull(d, listOf(theirs("o1", "products", "p1", SyncOp.UPDATE, fields("salePrice" to 300, "name" to "چای سبز"))), 10, keep = true)
    assertEquals(200.0, product(d)!!.salePrice, 0.0)
    assertEquals("چای سبز", product(d)!!.name)
    assertEquals(10L, d.cursor)
    assertEquals("opِ ۲۰۰ هنوز در صف است و می‌رود", "200", d.queue.single().fields!!["salePrice"].toString())
    noEcho(d)
  }

  @Test fun `بی پرچم همان رفتارِ امروز — ویرایشِ در صف با opِ رسیده بازنویسی می‌شود`() {
    val d = synced()
    save(d, d.ledger.copy(products = d.ledger.products.map { it.copy(salePrice = 200.0) }))
    pull(d, listOf(theirs("o1", "products", "p1", SyncOp.UPDATE, fields("salePrice" to 300))), 10, keep = false)
    assertEquals("مرجع: دستگاه ۳۰۰ نشان می‌دهد ولی سرور پس از رسیدنِ صف ۲۰۰ دارد", 300.0, product(d)!!.salePrice, 0.0)
  }

  @Test fun `حذفِ در صف و ویرایشِ رسیده — ردیفِ شبح نمی‌آید`() {
    val d = synced()
    save(d, d.ledger.copy(products = emptyList()))
    pull(d, listOf(theirs("o1", "products", "p1", SyncOp.UPDATE, fields("salePrice" to 300))), 10, keep = true)
    assertNull(product(d))
    assertEquals(10L, d.cursor)
    noEcho(d)
    //  مرجع: بی پرچم ردیفِ نیمه‌ای می‌آمد
    val off = synced()
    save(off, off.ledger.copy(products = emptyList()))
    pull(off, listOf(theirs("o1", "products", "p1", SyncOp.UPDATE, fields("salePrice" to 300))), 10, keep = false)
    assertEquals("", product(off)!!.name)
  }

  @Test fun `ویرایشِ در صف و حذفِ رسیده — ردیف می‌ماند چون سرور با رسیدنِ ویرایش زنده‌اش می‌کند`() {
    val d = synced()
    save(d, d.ledger.copy(products = d.ledger.products.map { it.copy(salePrice = 200.0) }))
    pull(d, listOf(theirs("o1", "products", "p1", SyncOp.DELETE, null)), 10, keep = true)
    assertEquals(200.0, product(d)!!.salePrice, 0.0)
    assertEquals("چای", product(d)!!.name)
    noEcho(d)
  }

  @Test fun `پول دقیق می‌ماند — inc روی inc جمع و مقدارِ سادهٔ رسیده با دلتای صف`() {
    val d = synced()
    save(d, d.ledger.copy(sales = d.ledger.sales.map { it.copy(paidAmount = 150.0, remaining = 850.0) }))
    assertEquals(inc(50).toString(), d.queue.single().fields!!["paidAmount"].toString())
    pull(d, listOf(
      theirs("o1", "sales", "s1", SyncOp.UPDATE, fields("paidAmount" to inc(30), "remaining" to inc(-30))),
      theirs("o2", "sales", "s1", SyncOp.UPDATE, fields("total" to 1200, "paidAmount" to 400)),
    ), 10, keep = true)
    val s = d.ledger.sales.single()
    assertEquals("۴۰۰ ِ رسیده + ۵۰ ِ صف (سرور: ۴۰۰ و بعد ‎+۵۰)", 450.0, s.paidAmount, 0.0)
    assertEquals("۹۰۰ − ۵۰ − ۳۰", 820.0, s.remaining, 0.0)
    assertEquals(1200.0, s.total, 0.0)
    noEcho(d)
  }

  @Test fun `ردیفِ زنده‌شده با row کامل می‌نشیند و inc دو بار جمع نمی‌شود`() {
    val d = synced()
    //  این دستگاه حذف را دیده (از pull ِ پیشین)
    pull(d, listOf(theirs("o0", "products", "p1", SyncOp.DELETE, null)), 5, keep = true)
    assertNull(product(d))
    val full = JsonObject(fields("name" to "چای", "unit" to "بسته", "salePrice" to 250) + ("barcodes" to JsonArray(listOf(JsonPrimitive("123")))))
    pull(d, listOf(
      theirs("o1", "products", "p1", SyncOp.UPDATE, fields("salePrice" to 250), full),
      theirs("o2", "products", "p1", SyncOp.UPDATE, fields("notes" to "تازه")),
    ), 10, keep = true)
    val p = product(d)!!
    assertEquals("چای", p.name)
    assertEquals("بسته", p.unit)
    assertEquals(listOf("123"), p.barcodes)
    assertEquals(250.0, p.salePrice, 0.0)
    assertEquals("op ِ پس از زنده شدن هم نشست", "تازه", p.notes)
    noEcho(d)

    //  فاکتور: row ِ پس از op ِ `$inc` همان دلتا را دارد ⇒ دوباره جمع نمی‌شود
    pull(d, listOf(theirs("o3", "sales", "s1", SyncOp.DELETE, null)), 11, keep = true)
    pull(d, listOf(theirs("o4", "sales", "s1", SyncOp.UPDATE, fields("paidAmount" to inc(50)),
      fields("total" to 1000, "finalTotal" to 1000, "paidAmount" to 150, "remaining" to 850))), 12, keep = true)
    assertEquals(150.0, d.ledger.sales.single().paidAmount, 0.0)
    assertEquals(1000.0, d.ledger.sales.single().total, 0.0)

    //  ردیفی که این‌جا هست `row` را نادیده می‌گیرد
    pull(d, listOf(theirs("o5", "products", "p1", SyncOp.UPDATE, fields("name" to "چای ۲"), fields("name" to "نادیده"))), 13, keep = true)
    assertEquals("چای ۲", product(d)!!.name)
    assertEquals("بسته", product(d)!!.unit)
  }

  @Test fun `مرجع — بی row ردیفِ زنده‌شده نیمه‌ای است`() {
    val d = synced()
    pull(d, listOf(theirs("o0", "products", "p1", SyncOp.DELETE, null)), 5, keep = false)
    pull(d, listOf(theirs("o1", "products", "p1", SyncOp.UPDATE, fields("salePrice" to 250))), 10, keep = false)
    assertEquals("", product(d)!!.name)
    assertTrue(product(d)!!.barcodes.isEmpty())
  }

  @Test fun `row فقط در ژورنال می‌رود نه به سرور — بسته شدن پس از نوشتنِ دفتر همان سایه را می‌سازد`() {
    val full = fields("name" to "چای", "salePrice" to 250)
    val op = theirs("o1", "products", "p1", SyncOp.UPDATE, fields("salePrice" to 250), full)
    assertFalse("بدنهٔ push بی row", op.toJson().containsKey("row"))
    assertEquals(full, SyncOp.fromJson(op.toJournalJson())!!.row)
    assertNull("حذف row نمی‌گیرد", SyncOp.fromJson(JsonObject(theirs("x", "products", "p1", SyncOp.DELETE, null).toJson() + ("row" to full)))!!.row)

    val d = synced()
    pull(d, listOf(theirs("o0", "products", "p1", SyncOp.DELETE, null)), 5, keep = true)
    //  ژورنال ⇐ دفتر ⇐ (بسته شد) — سایه و cursor ننشستند
    runCatching { session(d).absorb(listOf(op), d.ledger, 10) { d.ledger = it; throw Crash() } }
    assertEquals(5L, d.cursor)
    assertEquals(SyncCommit.Outcome.FORWARD, session(d).recover(d.ledger))
    assertEquals(10L, d.cursor)
    assertEquals("چای", product(d)!!.name)
    noEcho(d)
  }
}
