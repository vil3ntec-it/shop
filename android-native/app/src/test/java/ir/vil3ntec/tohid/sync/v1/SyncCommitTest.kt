package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
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
 *  F0-05 — بسته شدنِ برنامه وسطِ ذخیره، شمارنده‌ها را دو بار نمی‌شمارد.
 *
 *  سایه، صف و cursor جدا روی دیسک‌اند. این‌جا «دیسک» حافظه‌ای است و هر گام
 *  یا کامل نوشته شده یا اصلاً نه؛ برنامه در هر نقطهٔ میانِ گام‌ها «بسته»
 *  می‌شود (`Crash`)، بعد از نو باز می‌شود (`recover` با همان دفترِ دیسک) و
 *  کارِ عادی ادامه پیدا می‌کند. سنجه:
 *   • سرور (opها با همان قاعدهٔ تکراریِ `op_id`) پرداخت را **یک بار** دارد؛
 *   • دفترِ محلی opِ رسیده را **یک بار** دارد و آن را به سرور پس نمی‌فرستد.
 *
 *  ⚠️ با ترتیبِ پیشینِ موتور (صف ⇐ سایه با `apply()`؛ دفتر ⇐ سایه ⇐
 *  cursor) همین سناریوها پرداختِ ۲۰۰ به‌جای ۱۰۰ می‌دادند.
 */
class SyncCommitTest {

  /** «دیسک»: آنچه پس از بسته شدن می‌ماند */
  private class Disk {
    var ledger = ShopData()
    var shadow = ""
    var cursor = 0L
    val queue = ArrayList<SyncOp>()
  }

  private class Crash : RuntimeException("برنامه بسته شد")

  /**
   *  نقطه‌های بسته شدن — «پیش از» یعنی آن گام انجام نشد، «پس از» یعنی انجام
   *  شد و بلافاصله بسته شد.
   */
  private enum class At { NONE, BEFORE_PUSH, AFTER_PUSH, BEFORE_LEDGER, AFTER_LEDGER, AFTER_COMMIT }

  private class Parts(private val disk: Disk, private val at: At) : SyncCommit.Parts {
    override fun shadow(): String = disk.shadow
    override fun commit(shadow: String, cursor: Long?) {
      disk.shadow = shadow
      cursor?.let { disk.cursor = it }
      if (at == At.AFTER_COMMIT) throw Crash()
    }
    override fun queued(): Set<String> = disk.queue.mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) {
      if (at == At.BEFORE_PUSH) throw Crash()
      disk.queue += ops
      if (at == At.AFTER_PUSH) throw Crash()
    }
  }

  private val journalFile: File = Files.createTempDirectory("journal").toFile().resolve("sync-journal-t.json")

  /** یک «اجرای برنامه» روی همان دیسک و همان فایلِ ژورنال */
  private fun session(disk: Disk, at: At = At.NONE) = SyncCommit(SyncCommit.Journal(journalFile), Parts(disk, at))

  /** کارِ `ShopStore.load`: هنگامِ شروع */
  private fun start(disk: Disk): SyncCommit.Outcome = session(disk).recover(disk.ledger)

  /** کارِ `ShopStore.writeInternal`: دفتر روی دیسک، بعد `record` */
  private fun save(disk: Disk, next: ShopData, at: At = At.NONE) {
    disk.ledger = next
    runCatching { session(disk, at).record(next) }.exceptionOrNull()?.let { if (it !is Crash) throw it }
  }

  /** کارِ `absorbRemote`: نوشتنِ دفتر از راهِ همان `write` */
  private fun absorb(disk: Disk, ops: List<SyncOp>, cursor: Long, at: At = At.NONE) {
    runCatching {
      session(disk, at).absorb(ops, disk.ledger, cursor) { next ->
        if (at == At.BEFORE_LEDGER) throw Crash()
        disk.ledger = next
        if (at == At.AFTER_LEDGER) throw Crash()
      }
    }.exceptionOrNull()?.let { if (it !is Crash) throw it }
  }

  /** سرور: هر `op_id` یک بار (همان «تکراری»ِ `sync-v1.js`) */
  private class Server(start: ShopData) {
    var tree: JsonObject = LedgerDiff.toJson(start)
    private val seen = HashSet<String>()
    fun receive(ops: List<SyncOp>) {
      val fresh = ops.filter { seen.add(it.opId) }
      if (fresh.isNotEmpty()) tree = LedgerDiff.applyRemote(tree, fresh)
    }
    fun sale(id: String): Sale = LedgerDiff.fromJson(tree).sales.single { it.id == id }
  }

  private val unpaid = ShopData(
    sales = listOf(Sale(id = "s1", finalTotal = 100.0, paidAmount = 0.0, remaining = 100.0, debtorId = "d1")),
  )

  private fun fresh(): Disk = Disk().apply {
    ledger = unpaid
    shadow = LedgerDiff.shadowOf(LedgerDiff.toJson(unpaid)).toString()
    cursor = 5
  }

  private fun paid(d: ShopData) = d.copy(sales = d.sales.map { it.copy(paidAmount = it.paidAmount + 100, remaining = it.remaining - 100) })
  private fun withProduct(d: ShopData) = d.copy(products = d.products + Product(id = "p-after", name = "شکر"))

  /* ---------------------------- ثبتِ محلی ---------------------------- */

  private fun localPaymentSurvives(at: At, sentBeforeCrash: Boolean = false) {
    journalFile.delete()
    val disk = fresh()
    val server = Server(unpaid)

    //  اجرای ۱: پرداختِ ۱۰۰ ثبت می‌شود و برنامه وسطِ کار بسته می‌شود
    save(disk, paid(disk.ledger), at)
    if (sentBeforeCrash) {
      //  کارِ پس‌زمینه پیش از بسته شدن صف را فرستاده و ack گرفته بود
      server.receive(disk.queue.toList())
      disk.queue.clear()
    }

    //  اجرای ۲: شروع، بعد یک تغییرِ بی‌ربط
    start(disk)
    save(disk, withProduct(disk.ledger))
    server.receive(disk.queue.toList())

    val s = server.sale("s1")
    assertEquals("پرداخت روی سرور یک بار ($at)", 100.0, s.paidAmount, 0.0)
    assertEquals("باقی روی سرور یک بار ($at)", 0.0, s.remaining, 0.0)
    assertTrue(LedgerDiff.fromJson(server.tree).products.any { it.id == "p-after" })
    assertEquals(LedgerDiff.shadowOf(LedgerDiff.toJson(disk.ledger)).toString(), disk.shadow)
    assertFalse("ژورنال پس از کار نمی‌ماند", journalFile.exists())
  }

  @Test fun `ثبتِ محلی — بسته شدن پیش از صف`() = localPaymentSurvives(At.BEFORE_PUSH)

  @Test fun `ثبتِ محلی — بسته شدن پس از صف و پیش از سایه`() = localPaymentSurvives(At.AFTER_PUSH)

  @Test fun `ثبتِ محلی — پس از صف، فرستاده‌شده پیش از بسته شدن`() =
    localPaymentSurvives(At.AFTER_PUSH, sentBeforeCrash = true)

  @Test fun `ثبتِ محلی — بسته شدن پس از سایه و پیش از پاک کردنِ ژورنال`() = localPaymentSurvives(At.AFTER_COMMIT)

  @Test fun `ثبتِ محلی — بی بسته شدن`() = localPaymentSurvives(At.NONE)

  /* ------------------------- نشاندنِ opهای رسیده ------------------------- */

  /** پرداختِ ۱۰۰ از دستگاهِ دیگر، cursor ۶ */
  private val remote = listOf(
    SyncOp(
      "01JREMOTE00000000000000001", 1L, "sales", "s1", SyncOp.UPDATE,
      buildJsonObject {
        put("paidAmount", buildJsonObject { put("\$inc", JsonPrimitive(100)) })
        put("remaining", buildJsonObject { put("\$inc", JsonPrimitive(-100)) })
      },
    ),
  )

  private fun remotePaymentOnce(at: At) {
    journalFile.delete()
    val disk = fresh()

    //  اجرای ۱: Pull از cursor ۵ ⇐ op با cursor ۶، و بسته شدن وسطِ نشاندن
    absorb(disk, remote, 6, at)

    //  اجرای ۲: شروع، دوباره Pull از cursorِ روی دیسک، بعد یک تغییرِ محلی
    start(disk)
    if (disk.cursor < 6) absorb(disk, remote, 6)
    disk.queue.clear()
    save(disk, withProduct(disk.ledger))

    val s = disk.ledger.sales.single()
    assertEquals("پرداختِ رسیده روی دفتر یک بار ($at)", 100.0, s.paidAmount, 0.0)
    assertEquals("باقی روی دفتر یک بار ($at)", 0.0, s.remaining, 0.0)
    assertEquals(6L, disk.cursor)
    assertTrue(
      "opِ رسیده نباید به سرور پس برود ($at): ${disk.queue.map { it.table }}",
      disk.queue.none { it.table == "sales" },
    )
    assertEquals(listOf("products"), disk.queue.map { it.table })
    assertFalse(journalFile.exists())
  }

  @Test fun `نشاندن — بسته شدن پیش از نوشتنِ دفتر`() = remotePaymentOnce(At.BEFORE_LEDGER)

  @Test fun `نشاندن — بسته شدن پس از دفتر و پیش از سایه و cursor`() = remotePaymentOnce(At.AFTER_LEDGER)

  @Test fun `نشاندن — بسته شدن پس از سایه و پیش از پاک کردنِ ژورنال`() = remotePaymentOnce(At.AFTER_COMMIT)

  @Test fun `نشاندن — بی بسته شدن`() = remotePaymentOnce(At.NONE)

  /* ------------------------------ ژورنال ------------------------------ */

  @Test fun `ژورنالِ نصفه «نبود» خوانده می‌شود و چیزی را عوض نمی‌کند`() {
    val disk = fresh()
    journalFile.writeText("{\"v\":1,\"stage\":\"rec")
    val before = disk.shadow
    assertEquals(SyncCommit.Outcome.BACK, start(disk))
    assertEquals(before, disk.shadow)
    assertFalse(journalFile.exists())
  }

  @Test fun `ژورنالِ کهنه سایهٔ تازه‌تر را عقب نمی‌برد`() {
    journalFile.delete()
    val disk = fresh()
    //  ثبتی که نیمه ماند، بعد دفتر جلو رفت (بی بازیابی — نباید پیش بیاید)
    save(disk, paid(disk.ledger), At.AFTER_PUSH)
    val stale = journalFile.readText()
    start(disk)
    save(disk, withProduct(disk.ledger))
    val shadow = disk.shadow
    val queued = disk.queue.map { it.opId }
    journalFile.writeText(stale)
    assertEquals(SyncCommit.Outcome.STALE, start(disk))
    assertEquals(shadow, disk.shadow)
    assertEquals("opهای صف دست نمی‌خورند", queued, disk.queue.map { it.opId })
    assertFalse(journalFile.exists())
  }

  @Test fun `ثبتِ نیمه‌کاره در همان اجرا — ذخیرهٔ بعدی بی شروعِ دوباره هم یک بار می‌شمارد`() {
    journalFile.delete()
    val disk = fresh()
    val server = Server(unpaid)
    save(disk, paid(disk.ledger), At.AFTER_PUSH)
    //  بی `start`: همان اجرا ادامه می‌دهد (مثلاً نوشتنِ سایه خطا داده بود)
    save(disk, withProduct(disk.ledger))
    server.receive(disk.queue.toList())
    assertEquals(100.0, server.sale("s1").paidAmount, 0.0)
    assertEquals(0.0, server.sale("s1").remaining, 0.0)
    assertEquals(LedgerDiff.shadowOf(LedgerDiff.toJson(disk.ledger)).toString(), disk.shadow)
    assertFalse(journalFile.exists())
  }

  @Test fun `بی ژورنال، شروع هیچ کاری نمی‌کند`() {
    journalFile.delete()
    assertEquals(SyncCommit.Outcome.NONE, start(fresh()))
  }
}
