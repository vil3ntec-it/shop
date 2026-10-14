package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.SyncCommit
import ir.vil3ntec.tohid.sync.v1.SyncOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 *  فشرده کردنِ دفتر و همگام‌سازیِ Sync v1 — بازبینیِ F0-01.
 *
 *  `LedgerArchive.compact` فقط سایهٔ همگام‌سازیِ **قدیمی** (`SyncStore`) را
 *  هرس می‌کرد. سایهٔ Sync v1 (`SyncStateV1`) دست نمی‌خورد، پس ذخیرهٔ دفترِ
 *  کوتاه‌شده (`ShopStore.save` ⇐ `SyncV1Engine.record`) برای هر ردیفِ
 *  بایگانی‌شده یک **DELETE** به سرور می‌فرستاد — و سرور آن را به همهٔ
 *  دستگاه‌های دیگر می‌داد. یعنی بایگانی روی یک دستگاه = پاک شدنِ سابقه
 *  روی همه.
 *
 *  این‌جا «دیسک» حافظه‌ای است و همان `SyncCommit` و `LedgerArchive`ِ واقعی
 *  کار می‌کنند؛ فقط جای `Context` خالی است.
 */
class LedgerArchiveSyncTest {

  private val now = 1_800_000_000_000L
  private val day = 24L * 60L * 60L * 1000L

  private fun move(id: String, at: Long) =
    StockMovement(id = id, productId = "p1", type = "sale", qty = -1.0, createdAt = at)

  private fun ledger() = ShopData(
    products = listOf(Product(id = "p1", name = "برنج")),
    sales = listOf(Sale(id = "s1", invoiceNumber = 1, finalTotal = 100.0, createdAt = now)),
    stockMovements = listOf(move("old1", now - 500 * day), move("old2", now - 401 * day), move("new1", now - day)),
    auditLog = listOf(AuditEntry(id = "a-old", type = "sale", createdAt = now - 500 * day), AuditEntry(id = "a-new", type = "sale", createdAt = now)),
    priceHistory = listOf(
      PriceChange(id = "pr-old", productId = "p1", oldPrice = 1.0, newPrice = 2.0, createdAt = now - 500 * day),
      PriceChange(id = "pr-new", productId = "p1", oldPrice = 2.0, newPrice = 3.0, createdAt = now),
    ),
  )

  /** سایه، صف و cursorِ Sync v1 — حافظه‌ای */
  private class Parts : SyncCommit.Parts {
    var shadow = ""
    val queue = ArrayList<SyncOp>()
    var failCommit = false
    override fun shadow(): String = shadow
    override fun commit(shadow: String, cursor: Long?) {
      if (failCommit) throw java.io.IOException("سایه ننشست")
      this.shadow = shadow
    }
    override fun queued(): Set<String> = queue.mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) { queue += ops }
  }

  private val journal = SyncCommit.Journal(Files.createTempDirectory("journal").toFile().resolve("sync-journal-t.json"))

  /** دستگاهی که دفترش همین حالا با سرور یکی است (سایه = دفتر، صف خالی) */
  private fun synced(d: ShopData): Pair<SyncCommit, Parts> {
    val parts = Parts()
    val commit = SyncCommit(journal, parts)
    assertEquals(0, commit.record(d)) // نخستین بار: فقط حالتِ پایه
    assertTrue(parts.shadow.isNotEmpty())
    return commit to parts
  }

  /** کارِ `compact`، بی `Context`: بایگانی ⇐ هرسِ سایه */
  private fun compact(d: ShopData, commit: SyncCommit, kept: MutableList<LedgerArchive.Archive> = ArrayList()) =
    LedgerArchive.compactWith(d, now, LedgerArchive.KEEP_DAYS, keep = { kept += it }, forget = { commit.forget(it) })

  @Test
  fun `بی هرسِ سایهٔ v1، ذخیرهٔ دفترِ کوتاه همان ردیف‌ها را حذف می‌فرستاد`() {
    //  همان رفتارِ پیشین: فقط دفتر کوتاه شد، سایهٔ v1 نه
    val d = ledger()
    val (commit, parts) = synced(d)
    val (kept, _) = LedgerArchive.split(d, now)

    commit.record(kept)

    val deletes = parts.queue.filter { it.type == SyncOp.DELETE }.map { it.rowId }.toSet()
    assertEquals(setOf("old1", "old2", "a-old", "pr-old"), deletes)
  }

  @Test
  fun `بایگانی هیچ opی نمی‌سازد — نه حذف، نه چیزِ دیگر`() {
    val d = ledger()
    val (commit, parts) = synced(d)
    val archived = ArrayList<LedgerArchive.Archive>()

    val done = compact(d, commit, archived)
    commit.record(done.data) // `store.save(it.data)` در تنظیمات

    assertEquals(4, done.moved.total)
    assertEquals(setOf("old1", "old2"), archived.single().stockMovements.map { it.id }.toSet())
    assertTrue("opی ساخته شد: ${parts.queue.map { it.type + ":" + it.rowId }}", parts.queue.isEmpty())
  }

  @Test
  fun `پس از بایگانی، تغییرِ تازه مثلِ همیشه می‌رود و ردیفِ ماندهٔ پاک‌شده هم حذف می‌شود`() {
    val d = ledger()
    val (commit, parts) = synced(d)
    val done = compact(d, commit)
    commit.record(done.data)

    //  حرکتِ تازه ⇒ یک درج؛ پاک کردنِ ردیفی که در دفتر مانده بود ⇒ یک حذف
    val next = done.data.copy(
      stockMovements = done.data.stockMovements.filterNot { it.id == "new1" } + move("new2", now),
    )
    commit.record(next)

    assertEquals(setOf("insert:new2", "delete:new1"), parts.queue.map { it.type + ":" + it.rowId }.toSet())
    assertEquals(2, parts.queue.size)
  }

  @Test
  fun `بسته شدن پس از هرسِ سایه و پیش از ذخیرهٔ دفتر، فقط درج می‌فرستد نه حذف`() {
    //  دفترِ روی دیسک هنوز بلند است؛ سرور همین ردیف‌ها را دارد و درجِ همان
    //  مقدار چیزی را عوض نمی‌کند
    val d = ledger()
    val (commit, parts) = synced(d)
    compact(d, commit)

    commit.record(d)

    assertTrue(parts.queue.none { it.type == SyncOp.DELETE })
    assertEquals(setOf("old1", "old2", "a-old", "pr-old"), parts.queue.map { it.rowId }.toSet())
  }

  @Test
  fun `اگر بایگانی به دیسک نرسد، سایه دست نمی‌خورد و دفتر کوتاه نمی‌شود`() {
    val d = ledger()
    val (commit, parts) = synced(d)
    val before = parts.shadow

    val failed = runCatching {
      LedgerArchive.compactWith(d, now, LedgerArchive.KEEP_DAYS, keep = { throw java.io.IOException("دیسک پر") }, forget = { commit.forget(it) })
    }

    assertTrue(failed.isFailure)
    assertEquals(before, parts.shadow)
  }

  @Test
  fun `اگر سایهٔ v1 هرس نشود، فشرده کردن شکست می‌خورد`() {
    //  تا تنظیمات دفترِ کوتاه را ذخیره نکند — همان که حذف می‌ساخت
    val d = ledger()
    val (commit, parts) = synced(d)
    parts.failCommit = true

    assertTrue(runCatching { compact(d, commit) }.isFailure)
  }

  @Test
  fun `دستگاهی که هنوز همگام نشده سایه‌ای ندارد و چیزی عوض نمی‌شود`() {
    val parts = Parts()
    val commit = SyncCommit(journal, parts)

    assertEquals(0, commit.forget(mapOf("stockMovements" to listOf("old1"))))
    assertEquals("", parts.shadow)
  }

  @Test
  fun `دفترِ بی ردیفِ کهنه به سایه دست نمی‌زند`() {
    val fresh = ShopData(stockMovements = listOf(move("m", now)))
    val (commit, parts) = synced(fresh)
    val before = parts.shadow
    var forgot = false

    val done = LedgerArchive.compactWith(fresh, now, LedgerArchive.KEEP_DAYS, keep = {}, forget = { forgot = true; commit.forget(it) })

    assertEquals(0, done.moved.total)
    assertFalse(forgot)
    assertEquals(before, parts.shadow)
  }
}
