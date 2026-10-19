package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
 *  A00 — کلیدی که مدلِ **این** نسخه نمی‌شناسد هرگز `null` نمی‌رود.
 *
 *  ردیفِ سرور فیلدهایی دارد که برنامهٔ تازه‌تر یا خودِ سرور نوشته
 *  (`payments`، `approval`، `orderId`، … — این‌جا با نام‌های ساختگیِ `future*`
 *  تا روزی که مدل یکی از آن‌ها را بشناسد آزمون معنایش را از دست ندهد).
 *  برنامهٔ قدیمی‌تر دفتر را از درِ مدل می‌خواند (`ignoreUnknownKeys`) و آن
 *  کلیدها در دفترش نیستند. پیش از این، `changedFields` هر کلیدی را که در
 *  سایه بود و در دفتر نه «پاک‌شده» می‌دید و `null` ِ صریح می‌فرستاد ⇐ سرور
 *  دادهٔ برنامهٔ تازه‌تر را از دست می‌داد. سنجه‌ها:
 *   • ویرایشِ بی‌ربط پس از Snapshot یا Pull هیچ opی روی آن ردیف نمی‌سازد؛
 *   • ویرایشِ همان ردیف فقط فیلدهای عوض‌شدهٔ مدل را می‌فرستد، بی هیچ `null`؛
 *   • سایه کلیدهای ناشناخته را نگه می‌دارد (رفت‌وبرگشتِ JSON ِ خام)؛
 *   • پاک کردنِ واقعیِ فیلدی که مدل می‌شناسد هنوز `null` می‌فرستد.
 */
class UnknownFieldsTest {

  private class Disk {
    var ledger = ShopData()
    var shadow = ""
    var cursor = 0L
    val queue = ArrayList<SyncOp>()
  }

  private class Crash : RuntimeException("برنامه بسته شد")

  private class Parts(private val disk: Disk, private val crashAfterPush: Boolean = false) : SyncCommit.Parts {
    override fun shadow(): String = disk.shadow
    override fun commit(shadow: String, cursor: Long?) {
      disk.shadow = shadow
      cursor?.let { disk.cursor = it }
    }
    override fun queued(): Set<String> = disk.queue.mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) {
      disk.queue += ops
      if (crashAfterPush) throw Crash()
    }
  }

  private val journal: File = Files.createTempDirectory("unknown").toFile().resolve("sync-journal-u.json")
  private fun session(disk: Disk, crash: Boolean = false) = SyncCommit(SyncCommit.Journal(journal), Parts(disk, crash))

  /** `ShopStore.writeInternal` ⇒ `record` */
  private fun save(disk: Disk, next: ShopData): List<SyncOp> {
    val from = disk.queue.size
    disk.ledger = next
    session(disk).record(next)
    return disk.queue.drop(from)
  }

  /** فیلدهایی که مدلِ امروز ندارد — یکی رشته، یکی فهرستِ تودرتو، یکی `null` ِ صریحِ سرور */
  private val future: Map<String, JsonElement> = mapOf(
    "futureOrderId" to JsonPrimitive("ord_01"),
    "futurePayments" to JsonArray(
      listOf(
        buildJsonObject {
          put("method", "cod"); put("kind", "cash"); put("amount", 20000)
          put("ref", JsonNull); put("status", "pending"); put("custody", "courier")
        },
      ),
    ),
    "futureRef" to JsonNull,
  )

  /** ردیفِ فاکتور آن‌طور که سرور دارد: فیلدهای مدل + فیلدهای تازه‌تر */
  private fun serverSale(): JsonObject = buildJsonObject {
    put("invoiceNumber", 17); put("total", 200); put("discountType", "amount"); put("discountValue", 0)
    put("discount", 0); put("finalTotal", 200); put("paymentMethod", "cod"); put("debtorId", JsonNull)
    put("paidAmount", 200); put("remaining", 0); put("status", "completed"); put("debtGiven", 0)
    put("debtSettled", 0); put("createdAt", 1_792_440_404_167L); put("date", "2026-10-20")
    put("syncStatus", "synced"); put("cancelReason", ""); put("seller", ""); put("series", "W"); put("channel", "online")
    for ((k, v) in future) put(k, v)
  }

  private fun snapshotBody(vararg tables: Pair<String, List<Pair<String, JsonObject>>>): JsonObject = buildJsonObject {
    put("tables", JsonObject(tables.associate { (t, rows) ->
      t to JsonArray(rows.map { (id, data) -> buildJsonObject { put("id", id); put("data", data) } })
    }))
    put("cursor", 9)
  }

  /** `SyncV1Engine.restoreFromServer`: دفتر از درِ مدل، سایه همان درختِ Snapshot */
  private fun restored(body: JsonObject): Disk {
    val disk = Disk()
    val next = LedgerDiff.fromSnapshot(body, LedgerDiff.toJson(ShopData()))
    disk.ledger = LedgerDiff.fromJson(next)
    disk.shadow = LedgerDiff.shadowOf(next).toString()
    return disk
  }

  private fun shadowRow(disk: Disk, table: String, id: String): JsonObject =
    ((Json.parseToJsonElement(disk.shadow) as JsonObject)[table] as JsonArray)
      .map { it as JsonObject }.first { (it["id"] as JsonPrimitive).content == id }

  private fun describe(ops: List<SyncOp>) = ops.map { "${it.type} ${it.table}/${it.rowId} ${it.fields}" }
  private fun msg(ops: List<SyncOp>) = describe(ops).toString()

  @Test
  fun snapshot_thenUnrelatedEdit_sendsNothingForTheNewerRow() {
    val disk = restored(snapshotBody("sales" to listOf("w-1" to serverSale())))
    val ops = save(disk, disk.ledger.copy(debtors = listOf(Debtor(id = "d-1", name = "احمد", createdAt = 1L))))
    assertEquals(msg(ops), listOf("debtors"), ops.map { it.table })
    //  سایهٔ تازه کلیدهای ناشناخته را نگه داشته است
    val row = shadowRow(disk, "sales", "w-1")
    for ((k, v) in future) assertEquals(k, v, row[k])
    //  و ذخیرهٔ بعدی هم چیزی برای آن ردیف نمی‌سازد
    val again = save(disk, disk.ledger.copy(debtors = disk.ledger.debtors + Debtor(id = "d-2", name = "کریم", createdAt = 2L)))
    assertEquals(msg(again), listOf("insert debtors/d-2"), again.map { "${it.type} ${it.table}/${it.rowId}" })
  }

  @Test
  fun snapshot_thenEditSameRow_sendsOnlyTheChangedModelFields() {
    val disk = restored(snapshotBody("sales" to listOf("w-1" to serverSale())))
    val sale = disk.ledger.sales.single()
    val ops = save(disk, disk.ledger.copy(sales = listOf(sale.copy(status = "cancelled", cancelReason = "مشتری نخواست"))))
    assertEquals(msg(ops), 1, ops.size)
    assertEquals(SyncOp.UPDATE, ops[0].type)
    assertEquals(setOf("status", "cancelReason"), ops[0].fields!!.keys)
    assertFalse(describe(ops).toString(), ops[0].fields!!.values.any { it is JsonNull })
  }

  @Test
  fun pull_thenUnrelatedEdit_keepsUnknownKeysAndSendsNoNull() {
    val disk = Disk()
    save(disk, ShopData())                                                   // سایهٔ پایه
    val op = SyncOp("op-w-1", 1L, "sales", "w-1", SyncOp.INSERT, serverSale())
    session(disk).absorb(listOf(op), disk.ledger, 10L) { disk.ledger = it }
    for ((k, v) in future) assertEquals(k, v, shadowRow(disk, "sales", "w-1")[k])
    val ops = save(disk, disk.ledger.copy(debtors = listOf(Debtor(id = "d-1", name = "احمد", createdAt = 1L))))
    assertEquals(msg(ops), listOf("insert debtors/d-1"), ops.map { "${it.type} ${it.table}/${it.rowId}" })
    //  بعد ویرایشِ همان فاکتور: فقط همان فیلد
    val sale = disk.ledger.sales.single()
    val edit = save(disk, disk.ledger.copy(sales = listOf(sale.copy(cancelReason = "x"))))
    assertEquals(msg(edit), listOf("update sales/w-1 {\"cancelReason\":\"x\"}"), describe(edit))
  }

  @Test
  fun snapshotRowMissingModelDefaults_isNotEchoedBack() {
    //  ردیفی که وب یا نسخهٔ قدیمی بی پیش‌فرض‌ها نوشته: ویرایشِ بی‌ربط آن را «پر» نمی‌فرستد
    val disk = restored(snapshotBody("debtors" to listOf("d-old" to buildJsonObject { put("name", "قدیمی"); put("legacyNote", "x") })))
    val ops = save(disk, disk.ledger.copy(debtors = disk.ledger.debtors + Debtor(id = "d-new", name = "تازه", createdAt = 1L)))
    assertEquals(msg(ops), listOf("insert debtors/d-new"), ops.map { "${it.type} ${it.table}/${it.rowId}" })
  }

  @Test
  fun clearingAFieldTheModelKnows_stillSendsNull() {
    val disk = Disk()
    save(disk, ShopData())
    save(disk, ShopData(debtors = listOf(Debtor(id = "d-1", name = "احمد", createdAt = 1L, creditLimit = 500.0))))
    val ops = save(disk, disk.ledger.copy(debtors = listOf(disk.ledger.debtors.single().copy(creditLimit = null))))
    assertEquals(msg(ops), listOf("update debtors/d-1 {\"creditLimit\":null}"), describe(ops))
  }

  @Test
  fun crashAfterPush_recoversForwardWithTheSameShadow() {
    val disk = restored(snapshotBody("sales" to listOf("w-1" to serverSale())))
    val next = disk.ledger.copy(debtors = listOf(Debtor(id = "d-1", name = "احمد", createdAt = 1L)))
    disk.ledger = next
    runCatching { session(disk, crash = true).record(next) }
    //  شروعِ دوباره: ژورنال سایهٔ «پس از» را از همان دفتر دوباره می‌سازد
    assertEquals(SyncCommit.Outcome.FORWARD, session(disk).recover(disk.ledger))
    for ((k, v) in future) assertEquals(k, v, shadowRow(disk, "sales", "w-1")[k])
    assertEquals(listOf("debtors"), disk.queue.map { it.table })
  }

  @Test
  fun changedFields_neverNullsAKeyTheModelDoesNotKnow() {
    val before = buildJsonObject { put("status", "completed"); put("futureOrderId", "o1") }
    val after = buildJsonObject { put("status", "completed") }
    assertEquals(null, LedgerDiff.changedFields("sales", before, after))
    //  کلیدِ مدل که برداشته شده هنوز null می‌گیرد
    val known = buildJsonObject { put("series", "B1"); put("futureOrderId", "o1") }
    assertEquals("{\"series\":null}", LedgerDiff.changedFields("sales", known, JsonObject(emptyMap())).toString())
  }

  @Test
  fun modelFields_comeFromTheModelOfThisBuild() {
    assertTrue(LedgerDiff.isModelField("sales", "series"))
    assertTrue(LedgerDiff.isModelField("returns", "saleItemId"))         // نامِ سریالِ `saleReturns`
    assertTrue(LedgerDiff.isModelField("debtors", "creditLimit"))
    assertFalse(LedgerDiff.isModelField("sales", "futureOrderId"))
    assertFalse(LedgerDiff.isModelField("returns", "futureDocId"))
  }
}
