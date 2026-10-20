package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  A00 — مهرِ سایه و درمانِ یک‌بارهٔ سایهٔ ساختِ پیش از A00 (`SyncCommit.heal`).
 *
 *  گوشیِ ساختِ پیش از A00 که از Snapshot بازیابی شده سایهٔ خامِ سرور دارد: کلیدهایی
 *  که دفترِ آن ساخت نمی‌شناخت (`active = false` ِ کالا، `seller = "x"` ِ فاکتور). اگر
 *  **پیش از هیچ ذخیره‌ای** به ساختی به‌روز شود که آن‌ها را می‌شناسد، دفترش پیش‌فرض‌ها
 *  (`active = true`، `seller = ""`) را دارد و نخستین ذخیرهٔ بی‌ربط همان پیش‌فرض‌ها را
 *  روی مقدارِ سرور می‌نوشت. درمان پیش از نخستین تفاضل مقدارِ دفتر را در سایه می‌نشاند.
 *
 *  و ژورنالی که ساختِ پیشین نوشته (اثرِ انگشتِ سایهٔ بی مهر) هنوز «جلو» تمام می‌شود، نه
 *  «ناجور» — وگرنه opهای رسیده دوباره گرفته و `$inc`ها دو بار جمع می‌شدند.
 */
class ShadowStampTest {

  private class Disk {
    var ledger = ShopData()
    var shadow = ""
    var cursor = 0L
    val queue = ArrayList<SyncOp>()
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

  private val journalFile: File = Files.createTempDirectory("stamp").toFile().resolve("sync-journal-s.json")
  private fun session(disk: Disk) = SyncCommit(SyncCommit.Journal(journalFile), Parts(disk))

  private fun save(disk: Disk, next: ShopData): List<SyncOp> {
    val from = disk.queue.size
    disk.ledger = next
    session(disk).record(next)
    return disk.queue.drop(from)
  }

  private fun heads(ops: List<SyncOp>) = ops.map { "${it.type} ${it.table}/${it.rowId} ${if (it.type == SyncOp.UPDATE) it.fields else ""}".trim() }

  private fun snapshotBody(): JsonObject = buildJsonObject {
    put("tables", buildJsonObject {
      put("products", JsonArray(listOf(buildJsonObject {
        put("id", "p-1")
        put("data", buildJsonObject { put("name", "برنج"); put("salePrice", 100); put("active", false); put("createdAt", 1) })
      })))
      put("sales", JsonArray(listOf(buildJsonObject {
        put("id", "s-1")
        put("data", buildJsonObject { put("invoiceNumber", 1); put("total", 100); put("finalTotal", 100); put("paidAmount", 100); put("seller", "x"); put("createdAt", 1) })
      })))
    })
    put("cursor", 5)
  }

  /**
   *  گوشیِ ساختِ پیش از A00 (پیش از 3.2.300: نه `active` می‌شناخت نه `seller`) پس از
   *  `restoreFromServer`: سایه = درختِ خامِ Snapshot، بی مهر؛ دفتر = آنچه مدلِ آن ساخت
   *  نگه می‌داشت ⇐ پس از به‌روزرسانی همان دو فیلد با پیش‌فرض خوانده می‌شوند.
   */
  private fun oldBuildRestored(): Disk {
    val disk = Disk()
    val next = LedgerDiff.fromSnapshot(snapshotBody(), LedgerDiff.toJson(ShopData()))
    val read = LedgerDiff.fromJson(next)
    disk.ledger = read.copy(
      products = read.products.map { it.copy(active = true) },
      sales = read.sales.map { it.copy(seller = "") },
    )
    disk.shadow = LedgerDiff.unstamped(LedgerDiff.shadowOf(next).toString())
    return disk
  }

  @Test
  fun oldRawShadow_upgradeBeforeFirstSave_sendsNothingForUntouchedRows() {
    val disk = oldBuildRestored()
    assertFalse(LedgerDiff.isStamped(Json.parseToJsonElement(disk.shadow) as JsonObject))
    assertTrue("درمان", session(disk).heal(disk.ledger))
    assertTrue(LedgerDiff.isStamped(Json.parseToJsonElement(disk.shadow) as JsonObject))
    val ops = save(disk, disk.ledger.copy(debtors = listOf(Debtor(id = "d-1", name = "احمد", createdAt = 1L))))
    assertEquals(heads(ops).toString(), listOf("insert debtors/d-1"), heads(ops))
    assertFalse("یک بار", session(disk).heal(disk.ledger))
  }

  @Test
  fun withoutHeal_theSameUpgradeWouldOverwriteTheServer() {
    //  همان حال، بی درمان — سنجهٔ اینکه آزمونِ بالا واقعاً چیزی را می‌پوشاند
    val disk = oldBuildRestored()
    val ops = save(disk, disk.ledger.copy(debtors = listOf(Debtor(id = "d-1", name = "احمد", createdAt = 1L))))
    assertEquals(
      listOf("update products/p-1 {\"active\":true}", "update sales/s-1 {\"seller\":\"\"}", "insert debtors/d-1"),
      heads(ops),
    )
  }

  @Test
  fun afterHeal_realEditsOfTheSameFieldsStillGo() {
    val disk = oldBuildRestored()
    session(disk).heal(disk.ledger)
    val off = save(disk, disk.ledger.copy(products = disk.ledger.products.map { it.copy(active = false) }))
    assertEquals(listOf("update products/p-1 {\"active\":false}"), heads(off))
    val on = save(disk, disk.ledger.copy(products = disk.ledger.products.map { it.copy(active = true) }))
    assertEquals(listOf("update products/p-1 {\"active\":true}"), heads(on))
  }

  @Test
  fun everyShadowThisBuildWrites_isStamped_andNotHealedAgain() {
    val disk = Disk()
    save(disk, ShopData())
    assertTrue(LedgerDiff.isStamped(Json.parseToJsonElement(disk.shadow) as JsonObject))
    assertFalse(session(disk).heal(disk.ledger))
    session(disk).absorb(listOf(SyncOp("op-1", 1L, "debtors", "d-9", SyncOp.INSERT, buildJsonObject { put("name", "x") })), disk.ledger, 3L) { disk.ledger = it }
    assertTrue(LedgerDiff.isStamped(Json.parseToJsonElement(disk.shadow) as JsonObject))
  }

  /** ژورنالِ «نشاندن» که ساختِ پیش از A00 نوشته و پیش از سایه بسته شد؛ ساختِ تازه آن را جلو تمام می‌کند */
  @Test
  fun absorbJournalFromOldBuild_finishesForward_notMismatch() {
    val disk = Disk()
    val l0 = ShopData(debtors = listOf(Debtor(id = "d-1", name = "احمد", createdAt = 1L)))
    disk.ledger = l0
    disk.shadow = LedgerDiff.unstamped(LedgerDiff.shadowOf(l0).toString())
    val ops = listOf(SyncOp("op-a", 1L, "sales", "s-9", SyncOp.INSERT, buildJsonObject { put("paidAmount", 100); put("total", 100) }))
    val l1 = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(l0), ops))
    val oldAfter = LedgerDiff.unstamped(LedgerDiff.shadowOf(LedgerDiff.applyRemote(Json.parseToJsonElement(disk.shadow) as JsonObject, ops)).toString())
    SyncCommit.Journal(journalFile).write(
      SyncCommit.Journal.Entry(
        SyncCommit.Journal.ABSORB, before = SyncCommit.sha(disk.shadow), shadow = SyncCommit.sha(oldAfter),
        ledger = SyncCommit.sha(LedgerDiff.toJson(l1).toString()), cursor = 7L, ops = ops,
      ),
    )
    disk.ledger = l1                                                         //  دفتر نشسته بود
    assertEquals(SyncCommit.Outcome.FORWARD, session(disk).recover(disk.ledger))
    assertEquals(7L, disk.cursor)
  }

  /**
   *  بازبینیِ #341 (repro2/HealOrderReproTest): ژورنالِ «ثبت» ِ ساختِ پیشین که اثرِ انگشتش با مدلِ
   *  این ساخت نمی‌خواند ⇒ بازیابی از شاخهٔ «دفترِ آن لحظه در دست نیست» سایه‌ای **مهردار ولی
   *  درمان‌نشده** می‌نوشت و درمانِ بعدی آن را رد می‌کرد ⇒ نخستین ذخیره `{creditLimit: null}`.
   *  `start` مهر را پیش از بازیابی می‌خواند (همان کارِ `recover` ِ موتورها).
   */
  @Test
  fun oldRecordJournal_fallbackBranch_isStillHealed() {
    val disk = Disk()
    val d1 = Debtor(id = "d-1", name = "a", createdAt = 1L)
    val ledger0 = ShopData(debtors = listOf(d1))
    val tree = LedgerDiff.toJson(ledger0)
    //  سایهٔ خامِ Snapshot ِ ساختِ پیشین با کلیدی که آن ساخت نمی‌شناخت
    val raw = (tree["debtors"] as JsonArray).map { JsonObject((it as JsonObject) + ("creditLimit" to JsonPrimitive(500))) }
    disk.shadow = LedgerDiff.unstamped(LedgerDiff.shadowOf(JsonObject(tree + ("debtors" to JsonArray(raw)))).toString())
    //  ساختِ پیشین: ذخیره‌ای که d-2 را افزود، ژورنال نوشته شد و پیش از سایه بسته شد
    val d2 = Debtor(id = "d-2", name = "b", createdAt = 2L)
    val ledger1 = ledger0.copy(debtors = ledger0.debtors + d2)
    val d2row = (LedgerDiff.toJson(ShopData(debtors = listOf(d2)))["debtors"] as JsonArray)[0] as JsonObject
    val op = SyncOp("01OLDOP0000000000000000000", 1L, "debtors", "d-2", SyncOp.INSERT, JsonObject(d2row - "id"))
    SyncCommit.Journal(journalFile).write(SyncCommit.Journal.Entry(SyncCommit.Journal.RECORD,
      before = SyncCommit.sha(disk.shadow), shadow = "old-model-shape-sha", ops = listOf(op)))
    disk.ledger = ledger1
    var marked = ""
    val start = session(disk).start(ledger1) { marked = it }
    assertEquals(SyncCommit.Outcome.FORWARD, start.outcome)
    assertTrue(start.healed)
    assertEquals("01OLDOP0000000000000000000", marked)
    val ops = save(disk, ledger1.copy(debtors = ledger1.debtors + Debtor(id = "d-3", name = "c", createdAt = 3L)))
    assertEquals(listOf("insert debtors/d-3"), heads(ops))
  }

  /** opهایی که ساختِ پیشین در صف گذاشته نشان می‌شوند (بزرگ‌ترین `op_id`)، پیش از مهر */
  @Test
  fun start_marksTheOldBuildsQueuedOps_beforeStamping() {
    val disk = oldBuildRestored()
    val old = listOf(SyncOp(Ulid.next(1_000L), 1_000L, "debtors", "d-x", SyncOp.INSERT, buildJsonObject { put("name", "x") }),
      SyncOp(Ulid.next(2_000L), 2_000L, "debtors", "d-y", SyncOp.INSERT, buildJsonObject { put("name", "y") }))
    disk.queue += old
    var stampedWhenMarked: Boolean? = null
    val start = session(disk).start(disk.ledger) {
      stampedWhenMarked = LedgerDiff.isStamped(Json.parseToJsonElement(disk.shadow) as JsonObject)
    }
    assertEquals(old.maxOf { it.opId }, start.legacyUpTo)
    assertEquals("نشان پیش از مهر روی دیسک", false, stampedWhenMarked)
    //  دسته‌ای که op ِ پیشین دارد بی caps؛ op ِ تازهٔ همین ساخت (و پسوندِ v3 اش) با caps
    val fresh = save(disk, disk.ledger.copy(debtors = listOf(Debtor(id = "d-1", name = "احمد", createdAt = 1L))))
    assertFalse(LedgerDiff.capsFor(old.map { it.opId } + fresh.map { it.opId }, start.legacyUpTo))
    assertTrue(LedgerDiff.capsFor(fresh.map { it.opId }, start.legacyUpTo))
    assertFalse(LedgerDiff.capsFor(listOf(old[0].opId + ".u"), start.legacyUpTo))
    assertTrue(LedgerDiff.capsFor(fresh.map { it.opId + ".u" }, start.legacyUpTo))
    assertTrue(LedgerDiff.capsFor(old.map { it.opId }, ""))
    //  شروعِ بعدی: سایه مهر دارد ⇒ نه درمان، نه نشانِ تازه
    var again = ""
    assertEquals("", session(disk).start(disk.ledger) { again = it }.legacyUpTo)
    assertEquals("", again)
  }

  /** ژورنالِ «ثبت» ِ ساختِ پیشین که پس از صف و پیش از سایه بسته شد */
  @Test
  fun recordJournalFromOldBuild_finishesForward() {
    val disk = Disk()
    val l0 = ShopData()
    disk.shadow = LedgerDiff.unstamped(LedgerDiff.shadowOf(l0).toString())
    val l1 = l0.copy(debtors = listOf(Debtor(id = "d-1", name = "احمد", createdAt = 1L)))
    val ops = LedgerDiff.diff(Json.parseToJsonElement(disk.shadow) as JsonObject, LedgerDiff.toJson(l1), 1L)
    SyncCommit.Journal(journalFile).write(
      SyncCommit.Journal.Entry(
        SyncCommit.Journal.RECORD, before = SyncCommit.sha(disk.shadow),
        shadow = SyncCommit.sha(LedgerDiff.unstamped(LedgerDiff.shadowOf(l1).toString())), ops = ops,
      ),
    )
    disk.queue += ops
    disk.ledger = l1
    assertEquals(SyncCommit.Outcome.FORWARD, session(disk).recover(disk.ledger))
    assertEquals(1, disk.queue.size)
    assertEquals(emptyList<SyncOp>(), save(disk, l1))
  }
}
