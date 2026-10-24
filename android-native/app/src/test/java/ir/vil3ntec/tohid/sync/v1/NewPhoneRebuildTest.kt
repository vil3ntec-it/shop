package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  SEC-01، بازبینیِ #331 — گوشیِ تازه (بازیابیِ گوگل / انتقال) با دفترِ رمزداری که کلیدش نیامده.
 *
 *  روی گوشیِ تازه دفتر باز نمی‌شود (دفترِ حافظه خالی است) ولی سایهٔ پُر، صف و cursor رسیده‌اند.
 *  سنجه‌ها:
 *   • راهِ پیشین (بازیابیِ پشتیبانِ کهنه ⇐ تفاضل با سایهٔ تازه‌تر) DELETE و برگرداندن می‌ساخت —
 *     همین‌جا نشان داده می‌شود تا سناریو همان سناریوی واقعی باشد؛
 *   • «بازسازی از سرور» اول صف را می‌فرستد (INSERTِ در صف به سرور می‌رسد)، بعد دفتر و سایه را
 *     از Snapshot می‌سازد — سایه پیش از دفتر — و پس از آن ذخیره **هیچ** opی نمی‌سازد؛
 *   • پشتیبانی که در همین حال بازیابی شود فقط INSERT می‌سازد، هرگز DELETE/UPDATE؛
 *   • صف خالی نشد یا شبکه نبود ⇒ هیچ چیزی عوض نمی‌شود.
 */
class NewPhoneRebuildTest {

  private class Disk {
    var ledger = ShopData()
    var shadow = ""
    var cursor = 0L
    val queue = ArrayList<SyncOp>()
    /** کناری و نشانِ «بازسازیِ نیمه‌کاره» */
    var sidecar: ShopData? = null
    var marker: String? = null
  }

  private class Parts(private val disk: Disk) : SyncCommit.Parts {
    override fun shadow(): String = disk.shadow
    override fun commit(shadow: String, cursor: Long?) {
      disk.shadow = shadow
      cursor?.let { disk.cursor = it }
    }
    override fun queued(): Set<String> = disk.queue.mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) { disk.queue += ops }
  }

  /** سرورِ حافظه‌ای: opها را با همان `applyRemote` می‌نشاند و Snapshot ِ همان قالبِ سرور را می‌دهد */
  private class Server(var tree: JsonObject, var cursor: Long) {
    var offline = false
    fun apply(ops: List<SyncOp>) {
      tree = LedgerDiff.applyRemote(tree, ops)
      cursor += ops.size
    }
    fun snapshot(): JsonObject {
      var rows = 0
      val tables = buildJsonObject {
        for (key in LedgerDiff.COLLECTIONS) {
          val list = tree[key] as? JsonArray ?: continue
          rows += list.size
          put(key, JsonArray(list.map { r ->
            val o = r as JsonObject
            buildJsonObject { put("id", o["id"]!!); put("data", JsonObject(o - "id")) }
          }))
        }
        for (key in LedgerDiff.LIST_COLLECTIONS) {
          val names = (tree[key] as? JsonArray)?.map { (it as JsonPrimitive).content } ?: continue
          put(key, JsonArray(names.map { n -> buildJsonObject { put("id", LedgerDiff.listRowId(n)); put("data", buildJsonObject { put("name", n) }) } }))
        }
      }
      return buildJsonObject {
        put("tables", tables)
        put("cursor", cursor)
        put("rows", rows)
      }
    }
  }

  private val journal: File = Files.createTempDirectory("rebuild").toFile().resolve("sync-journal-t.json")
  private fun session(disk: Disk) = SyncCommit(SyncCommit.Journal(journal), Parts(disk))

  private fun p(id: String, price: Double) = Product(id = id, name = "کالا $id", salePrice = price)

  /** «گوشیِ کهنه»: p1، p2 همگام؛ p3 تازه ثبت شده و هنوز در صف است. سرور p2 را ۹۰ دارد. */
  private fun oldPhone(): Triple<Disk, Server, ShopData> {
    val synced = ShopData(products = listOf(p("p1", 10.0), p("p2", 90.0)))
    val disk = Disk()
    assertEquals("نخستین ثبت حالتِ پایه است", 0, session(disk).record(synced).also { disk.ledger = synced })
    val withP3 = synced.copy(products = synced.products + p("p3", 30.0))
    disk.ledger = withP3
    assertEquals(1, session(disk).record(withP3))
    val server = Server(LedgerDiff.toJson(synced), cursor = 100)
    //  پشتیبانِ کهنه: p2 هنوز ۸۰ بود، p3 نبود، p0 فقط در پشتیبان است؛ تنظیمی بیرون از همگام‌سازی هم دارد
    val backup = ShopData(products = listOf(p("p0", 5.0), p("p1", 10.0), p("p2", 80.0)), nextInvoiceNo = 42)
    return Triple(disk, server, backup)
  }

  /** گوشیِ تازه: دفتر باز نمی‌شود (حافظه خالی)، سایه و صف و cursor رسیده‌اند */
  private fun newPhone(old: Disk): Disk = Disk().also {
    it.shadow = old.shadow
    it.cursor = 7
    it.queue += old.queue
  }

  private class Kill : RuntimeException("برنامه بسته شد")

  private fun io(disk: Disk, server: Server, events: MutableList<String>, killAt: String = "") = object : SyncRebuild.Io {
    override fun queued(): Int = disk.queue.size
    override suspend fun push() {
      if (server.offline) throw java.io.IOException("offline")
      server.apply(disk.queue.toList())
      disk.queue.clear()
      events += "push"
    }
    override fun shadow(): String = disk.shadow
    override suspend fun snapshot(): JsonObject = server.snapshot().also { events += "snapshot" }
    override fun stage(next: ShopData, shadowSha: String) {
      disk.sidecar = next
      disk.marker = shadowSha
      events += "stage"
    }
    override fun commitShadow(shadow: String, cursor: Long) {
      if (killAt == "shadow") throw Kill()
      disk.shadow = shadow
      disk.cursor = cursor
      events += "shadow"
    }
    override fun swap() {
      if (killAt == "swap") throw Kill()
      finish(disk)
      events += "swap"
    }
  }

  /** کارِ `ShopStore.finishRebuild` */
  private fun finish(disk: Disk) {
    disk.ledger = disk.sidecar!!
    disk.sidecar = null
    disk.marker = null
  }

  @Test fun `راهِ پیشین — پشتیبانِ کهنه روی سایهٔ تازه‌تر DELETE و برگرداندن می‌ساخت`() {
    val (old, _, backup) = oldPhone()
    val phone = newPhone(old)
    val shadow = Json.parseToJsonElement(phone.shadow) as JsonObject
    val ops = LedgerDiff.diff(shadow, LedgerDiff.toJson(backup))
    assertTrue("p3 (در صف، هنوز نرفته) حذف می‌شد", ops.any { it.type == SyncOp.DELETE && it.rowId == "p3" })
    assertTrue("p2ِ همگام‌شده به ۸۰ برمی‌گشت", ops.any { it.type == SyncOp.UPDATE && it.rowId == "p2" })
  }

  @Test fun `بازسازی از سرور — صف اول می‌رود، سایه پیش از دفتر، و پس از آن هیچ opی ساخته نمی‌شود`() {
    val (old, server, _) = oldPhone()
    val phone = newPhone(old)
    val events = ArrayList<String>()

    val out = runBlocking { SyncRebuild(io(phone, server, events)).run(ShopData()) }

    assertEquals(listOf("push", "snapshot", "stage", "shadow", "swap"), events)
    assertTrue(out is SyncRebuild.Outcome.Done)
    assertTrue("INSERTِ در صف به سرور رسید", (server.tree["products"] as JsonArray).any { (it as JsonObject)["id"] == JsonPrimitive("p3") })
    assertEquals(listOf("p1", "p2", "p3"), phone.ledger.products.map { it.id })
    assertEquals(90.0, phone.ledger.products.first { it.id == "p2" }.salePrice, 0.0)
    assertEquals("cursor سرِ Snapshot", server.cursor, phone.cursor)
    assertTrue(phone.queue.isEmpty())
    //  دفتر = سایه: ذخیرهٔ همان دفتر هیچ opی نمی‌سازد
    assertEquals(0, session(phone).record(phone.ledger))
    assertTrue(phone.queue.isEmpty())
  }

  @Test fun `بازیابیِ پشتیبان با دفترِ خوانده‌نشده — فقط INSERTِ ردیف‌هایی که سرور ندارد، هرگز DELETE یا برگرداندن`() {
    val (old, server, backup) = oldPhone()
    val phone = newPhone(old)
    val out = runBlocking { SyncRebuild(io(phone, server, ArrayList())).run(backup) } as SyncRebuild.Outcome.Done
    assertEquals("تنظیمِ بیرون از همگام‌سازی از پشتیبان", 42, out.ledger.nextInvoiceNo)

    val merged = SyncRebuild.withBackupRows(out.ledger, backup, out.before)
    session(phone).record(merged)
    assertTrue(phone.queue.isNotEmpty())
    assertTrue("فقط INSERT", phone.queue.all { it.type == SyncOp.INSERT })
    assertEquals(listOf("p0"), phone.queue.map { it.rowId })
    assertEquals("ردیفِ مشترک همان نسخهٔ سرور", 90.0, merged.products.first { it.id == "p2" }.salePrice, 0.0)
    assertTrue(merged.products.any { it.id == "p3" })
  }

  @Test fun `صف خالی نشد — هیچ چیزی عوض نمی‌شود؛ شبکه نبود — همین`() {
    val (old, server, _) = oldPhone()
    val phone = newPhone(old)
    val shadowBefore = phone.shadow
    val events = ArrayList<String>()
    val stuck = object : SyncRebuild.Io by io(phone, server, events) {
      override suspend fun push() = Unit // سرور نپذیرفت (۴۲۶) — صف می‌ماند
    }
    val out = runBlocking { SyncRebuild(stuck).run(ShopData()) }
    assertEquals(SyncRebuild.Outcome.QueueLeft(1), out)
    assertEquals(shadowBefore, phone.shadow)
    assertEquals(ShopData(), phone.ledger)
    assertTrue(events.isEmpty())

    server.offline = true
    try {
      runBlocking { SyncRebuild(io(phone, server, events)).run(ShopData()) }
      fail("بی شبکه باید شکست بخورد")
    } catch (e: java.io.IOException) {
      assertEquals(shadowBefore, phone.shadow)
      assertEquals(1, phone.queue.size)
      assertTrue(events.isEmpty())
    }
  }

  @Test fun `بی سرور (هنوز وارد نشده) — پشتیبان خودش سایه می‌شود و هیچ opی نمی‌سازد؛ صف دست نمی‌خورد`() {
    val (old, _, backup) = oldPhone()
    val phone = newPhone(old)
    phone.shadow = SyncRebuild.shadowFor(backup)
    phone.ledger = backup
    assertEquals(0, session(phone).record(backup))
    assertEquals("INSERTِ p3 هنوز در صف است تا پس از ورود برود", listOf("p3"), phone.queue.map { it.rowId })
  }

  @Test fun `ردیفی که پس از پشتیبان حذف شد با بازیابی زنده نمی‌شود — سایهٔ پیشین داشتش، سرور ندارد`() {
    val (old, server, backup) = oldPhone()
    //  دستگاهِ دیگر p2 را پس از پشتیبان حذف کرد
    server.apply(listOf(SyncOp("del-p2", 1L, "products", "p2", SyncOp.DELETE, null)))
    val phone = newPhone(old)
    val out = runBlocking { SyncRebuild(io(phone, server, ArrayList())).run(backup) } as SyncRebuild.Outcome.Done
    assertTrue(out.ledger.products.none { it.id == "p2" })

    val merged = SyncRebuild.withBackupRows(out.ledger, backup, out.before)
    assertTrue("p2 زنده نشد", merged.products.none { it.id == "p2" })
    session(phone).record(merged)
    assertTrue("هیچ INSERTی برای p2", phone.queue.none { it.rowId == "p2" })
    assertEquals("فقط p0 (هرگز همگام‌نشده)", listOf("p0"), phone.queue.map { it.rowId })
  }

  @Test fun `سایهٔ پیشین نیست — نمی‌دانیم کدام حذف شده، پس هیچ ردیفی از پشتیبان افزوده نمی‌شود`() {
    val (_, server, backup) = oldPhone()
    val phone = Disk() // گوشیِ تازه بی سایه (پشتیبانِ اندروید حالِ همگام‌سازی را نمی‌برد)
    val out = runBlocking { SyncRebuild(io(phone, server, ArrayList())).run(backup) } as SyncRebuild.Outcome.Done
    assertEquals(null, out.before)
    assertEquals(out.ledger, SyncRebuild.withBackupRows(out.ledger, backup, out.before))
  }

  @Test fun `شمارندهٔ فاکتور پس از بازسازی از بلندترین شمارهٔ سرور جلوتر است`() {
    val (old, server, _) = oldPhone()
    server.tree = LedgerDiff.toJson(LedgerDiff.fromJson(server.tree).copy(sales = listOf(Sale(id = "s1", invoiceNumber = 7))))
    val phone = newPhone(old)
    val out = runBlocking { SyncRebuild(io(phone, server, ArrayList())).run(ShopData()) } as SyncRebuild.Outcome.Done
    assertEquals(8, out.ledger.nextInvoiceNo)
    assertEquals(8, phone.ledger.nextInvoiceNo)
  }

  @Test fun `بسته شدن میانِ سایه و جابه‌جایی — هنگامِ باز شدن جابه‌جایی تمام می‌شود، نه تفاضلِ دفترِ کهنه`() {
    val (old, server, _) = oldPhone()
    val phone = newPhone(old)
    phone.ledger = old.ledger // دفترِ کهنه، که شاید با برگشتنِ کلید خواندنی شود
    runCatching { runBlocking { SyncRebuild(io(phone, server, ArrayList(), killAt = "swap")).run(ShopData()) } }
      .exceptionOrNull().let { assertTrue(it is Kill) }

    assertEquals(SyncRebuild.AtLoad.FINISH, SyncRebuild.atLoad(phone.marker, phone.sidecar != null, SyncRebuild.sha(phone.shadow)))
    finish(phone)
    assertEquals(0, session(phone).record(phone.ledger))
    assertTrue(phone.ledger.products.any { it.id == "p3" })
  }

  @Test fun `بسته شدن پیش از سایه — کناری دور ریخته می‌شود و هیچ چیزی عوض نشده`() {
    val (old, server, _) = oldPhone()
    //  دستگاهِ دیگر p4 را افزوده: سایهٔ تازه با سایهٔ کهنه فرق دارد
    server.apply(listOf(SyncOp("ins-p4", 1L, "products", "p4", SyncOp.INSERT, buildJsonObject { put("name", "کالا p4"); put("salePrice", 4) })))
    val phone = newPhone(old)
    val shadowBefore = phone.shadow
    runCatching { runBlocking { SyncRebuild(io(phone, server, ArrayList(), killAt = "shadow")).run(ShopData()) } }
      .exceptionOrNull().let { assertTrue(it is Kill) }
    assertEquals(shadowBefore, phone.shadow)
    assertEquals(SyncRebuild.AtLoad.DISCARD, SyncRebuild.atLoad(phone.marker, phone.sidecar != null, SyncRebuild.sha(phone.shadow)))
    assertEquals(SyncRebuild.AtLoad.DISCARD, SyncRebuild.atLoad(null, true, SyncRebuild.sha(phone.shadow)))
    assertEquals(SyncRebuild.AtLoad.NONE, SyncRebuild.atLoad(null, false, SyncRebuild.sha(phone.shadow)))
  }
}
