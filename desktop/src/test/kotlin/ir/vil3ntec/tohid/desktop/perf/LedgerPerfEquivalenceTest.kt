package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.data.DebtMinorFlag
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.LedgerTrees
import ir.vil3ntec.tohid.sync.v1.SyncCommit
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.Ulid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  PERF-LEDGER — هر میان‌بُرِ ذخیرهٔ دفترِ بزرگ **همان نتیجه** را می‌دهد (`docs/PERF-LEDGER-fa.md`):
 *
 *   • `LedgerTrees.toJson` ≡ `LedgerDiff.toJson` — افزودن، ویرایشِ وسط، حذف، ردیفِ تازه در آغاز، شناسهٔ تکراری،
 *     دفترِ تازه‌خوانده از JSON (هیچ شیءِ مشترک).
 *   • `LedgerDiff.diff` ≡ الگوی پیشینِ همان تابع ([referenceDiff]، رونوشتِ پیش از PERF-LEDGER) — همان opها به همان
 *     ترتیب، با سایهٔ هم‌شیء و سایهٔ تازه‌تجزیه، ردیفِ بی پیش‌فرض (`modelView`)، شمارنده و فیلدِ برداشته.
 *   • `LedgerDiff.text` ≡ `toString` نویسه‌به‌نویسه؛ تجزیهٔ همان متن ≡ همان درخت (پایهٔ `SyncCommit.seated`).
 *   • `SyncCommit.record` با کشِ گرم ≡ نمونهٔ تازه (بی کش) در هر گام: همان opها، همان متنِ سایه.
 *   • `ShopStore.debt` (کشِ یک‌خانه‌ای) ≡ `filter` ِ پیشین برای هر مشتری، پیش و پس از تراکنشِ تازه.
 */
class LedgerPerfEquivalenceTest {

  private val base = BigLedger.make(BigLedger.SMOKE)
  private val diskJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  /** همان دفتر، همه شیءِ تازه (مثلِ خواندنِ دیسک) */
  private fun reread(d: ShopData): ShopData = diskJson.decodeFromString(ShopData.serializer(), diskJson.encodeToString(ShopData.serializer(), d))

  /** دنباله‌ای از کارهای واقعی روی دفتر — هر گام از گامِ پیش با `copy` */
  private fun steps(): List<Pair<String, ShopData>> {
    val out = ArrayList<Pair<String, ShopData>>()
    var d = base
    fun add(name: String, next: ShopData) { out += name to next; d = next }
    add("sale", BigLedger.sell(d, 1))
    add("credit sale", BigLedger.sell(d, 2, credit = true))
    add("edit middle sale", d.copy(sales = d.sales.mapIndexed { i, s -> if (i == d.sales.size / 2) s.copy(paidAmount = s.paidAmount + 10, note = "ویرایش \"وسط\"\n") else s }))
    add("delete one item", d.copy(saleItems = d.saleItems.filterIndexed { i, _ -> i != 7 }))
    add("prepend product", d.copy(products = listOf(Product(id = "perf-new-p", name = "کالای تازه\u0001\t")) + d.products))
    add("duplicate sale id", d.copy(sales = d.sales + d.sales[3].copy(finalTotal = 1.5, seller = "دوم")))
    add("units + invoice no", d.copy(productUnits = d.productUnits + "جعبه", nextInvoiceNo = d.nextInvoiceNo + 5))
    add("unpaired surrogate + emoji", d.copy(debtors = d.debtors.mapIndexed { i, x -> if (i == 1) x.copy(name = "a\uD800b 😀 \uDC00") else x }))
    add("reread from disk (no shared rows)", reread(d))
    add("sale after reread", BigLedger.sell(d, 3))
    add("drop half of moves", d.copy(stockMovements = d.stockMovements.filterIndexed { i, _ -> i % 2 == 0 }))
    add("same ledger again", d)
    return out
  }

  /* ------------------------------ LedgerTrees ------------------------------ */

  @Test fun `جدول‌های میان‌بُر همان مجموعه‌های همگام‌شدنی‌اند`() {
    assertEquals(LedgerDiff.COLLECTIONS.toSet(), LedgerTrees.KEYS.toSet())
  }

  @Test fun `درختِ کش‌دار در هر گام همان LedgerDiff_toJson است`() {
    val trees = LedgerTrees()
    assertEquals(LedgerDiff.toJson(base), trees.toJson(base))
    for ((name, d) in steps()) {
      val want = LedgerDiff.toJson(d)
      val got = trees.toJson(d)
      assertEquals(name, want, got)
      assertEquals(name, want.toString(), got.toString())
      assertEquals(name, want.keys.toList(), got.keys.toList())
    }
  }

  /* ------------------------------ diff ------------------------------ */

  private fun key(op: SyncOp) = "${op.table}|${op.rowId}|${op.type}|${op.fields}"

  @Test fun `diff همان opهای الگوی پیشین را می‌دهد — سایهٔ هم‌شیء و سایهٔ تازه‌تجزیه`() {
    val trees = LedgerTrees()
    var prev = trees.toJson(base)
    var shadow = LedgerDiff.shadowOf(prev)
    for ((name, d) in steps()) {
      val tree = trees.toJson(d)
      val parsed = Json.parseToJsonElement(shadow.toString()) as JsonObject
      val want = referenceDiff(shadow, tree).map(::key)
      assertEquals("$name (هم‌شیء)", want, LedgerDiff.diff(shadow, tree).map(::key))
      assertEquals("$name (تجزیه)", want, LedgerDiff.diff(parsed, tree).map(::key))
      assertEquals("$name (الگوی پیشین روی تجزیه)", want, referenceDiff(parsed, tree).map(::key))
      prev = tree
      shadow = LedgerDiff.shadowOf(tree)
    }
    assertTrue(prev.isNotEmpty())
  }

  @Test fun `diff با سایهٔ خامِ کهنه (بی پیش‌فرض، کلیدِ ناشناخته، شمارنده، فیلدِ برداشته) همان است`() {
    val tree = LedgerDiff.toJson(base)
    //  سایهٔ ساختِ پیش از A00: ردیف‌ها بی چند پیش‌فرض، با کلیدِ ناشناخته و پولِ رسیدهٔ دیگر
    val raw = JsonObject(
      tree.mapValues { (k, v) ->
        if (k !in LedgerDiff.COLLECTIONS || v !is JsonArray) v
        else JsonArray(
          v.mapIndexed { i, r ->
            val o = r as JsonObject
            when (i % 5) {
              0 -> JsonObject(o - "notes")
              1 -> JsonObject(o + ("custody" to JsonPrimitive("x")))
              2 -> if ("paidAmount" in o) JsonObject(o + ("paidAmount" to JsonPrimitive(1))) else o
              3 -> JsonObject(o + ("approval" to JsonNull))
              else -> o
            }
          },
        )
      },
    )
    val next = LedgerDiff.toJson(BigLedger.sell(base, 9))
    assertEquals(referenceDiff(raw, next).map(::key), LedgerDiff.diff(raw, next).map(::key))
    assertEquals(referenceDiff(raw, tree).map(::key), LedgerDiff.diff(raw, tree).map(::key))
  }

  /* ------------------------------ متنِ سایه ------------------------------ */

  @Test fun `text نویسه‌به‌نویسه همان toString است و تجزیه‌اش همان درخت`() {
    val trees = LedgerTrees()
    var prev: ir.vil3ntec.tohid.sync.v1.ShadowText? = null
    var prevTree: JsonObject? = null
    for ((name, d) in steps() + steps().take(3)) {
      val shadow = LedgerDiff.shadowOf(trees.toJson(d))
      val text = LedgerDiff.text(shadow)
      assertEquals(name, shadow.toString(), text)
      assertEquals(name, shadow, Json.parseToJsonElement(text))
      //  رونوشتِ تکه‌های متنِ پیشین برای ردیفِ همان‌شیء — همان متن
      val spliced = ir.vil3ntec.tohid.sync.v1.ShadowText.of(shadow, prev, prevTree)
      assertEquals(name, text, spliced.text)
      assertEquals(name, text, ir.vil3ntec.tohid.sync.v1.ShadowText.of(shadow).text)
      prev = spliced
      prevTree = shadow
    }
    val odd = buildJsonObject {
      put("ctl", JsonPrimitive((0..0x1f).map { it.toChar() }.joinToString("") + "\"\\/\u007f "))
      put("num", JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2.5), JsonPrimitive(-0.0), JsonPrimitive(1e-7), JsonPrimitive(Long.MAX_VALUE), JsonPrimitive(true), JsonNull)))
      put("raw", Json.parseToJsonElement("""{"a":1e5,"b":[{}],"c":"😀","": -12.50}"""))
      put("ke\"y\n", JsonArray(emptyList()))
    }
    assertEquals(odd.toString(), LedgerDiff.text(odd))
    assertEquals(odd, Json.parseToJsonElement(LedgerDiff.text(odd)))
  }

  /* ------------------------------ SyncCommit ------------------------------ */

  private class Disk { var shadow = ""; val queue = ArrayList<SyncOp>() }

  private class Parts(private val disk: Disk) : SyncCommit.Parts {
    override fun shadow(): String = disk.shadow
    override fun commit(shadow: String, cursor: Long?) { disk.shadow = shadow }
    override fun queued(): Set<String> = disk.queue.mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) { disk.queue += ops }
  }

  @Test fun `record با کشِ گرم در هر گام همان opها و همان سایهٔ نمونهٔ تازه را می‌دهد`() {
    val dir = Files.createTempDirectory("perf-commit").toFile()
    try {
      val warmDisk = Disk()
      val warm = SyncCommit(SyncCommit.Journal(File(dir, "warm.json")), Parts(warmDisk))
      assertEquals(0, warm.record(base))
      for ((name, d) in steps() + listOf("outside write" to base)) {
        if (name == "outside write") {
          //  نوشتنِ دیگری سایه را عوض کرد (نشاندنِ op ِ رسیده، Snapshot…): کش نباید دیده شود
          warmDisk.shadow = LedgerDiff.shadowOf(LedgerDiff.toJson(BigLedger.sell(base, 77, tag = 3))).toString()
        }
        val coldDisk = Disk().apply { shadow = warmDisk.shadow }
        val cold = SyncCommit(SyncCommit.Journal(File(dir, "cold.json")), Parts(coldDisk))
        val w0 = warmDisk.queue.size
        val nWarm = warm.record(d)
        val nCold = cold.record(d)
        assertEquals(name, nCold, nWarm)
        assertEquals(name, coldDisk.queue.map(::key), warmDisk.queue.drop(w0).map(::key))
        assertEquals(name, coldDisk.shadow, warmDisk.shadow)
        assertTrue(name, !File(dir, "warm.json").exists() || File(dir, "warm.json").length() == 0L)
      }
      //  بایگانی (`LedgerArchive.compact` ⇐ `forget`): همان سایهٔ هرس‌شده، با کشِ گرم و بی آن
      val d = BigLedger.sell(base, 88)
      warm.record(d)
      val gone = mapOf("stockMovements" to d.stockMovements.take(500).map { it.id }, "auditLog" to d.auditLog.takeLast(3).map { it.id })
      val coldDisk = Disk().apply { shadow = warmDisk.shadow }
      val cold = SyncCommit(SyncCommit.Journal(File(dir, "cold.json")), Parts(coldDisk))
      assertEquals(cold.forget(gone), warm.forget(gone))
      assertEquals(coldDisk.shadow, warmDisk.shadow)
      //  و ثبتِ پس از بایگانی روی همان سایه
      val after = BigLedger.sell(d, 89)
      val coldDisk2 = Disk().apply { shadow = warmDisk.shadow }
      val w0 = warmDisk.queue.size
      SyncCommit(SyncCommit.Journal(File(dir, "cold.json")), Parts(coldDisk2)).record(after)
      warm.record(after)
      assertEquals(coldDisk2.queue.map(::key), warmDisk.queue.drop(w0).map(::key))
      assertEquals(coldDisk2.shadow, warmDisk.shadow)
    } finally {
      dir.deleteRecursively()
    }
  }

  /* ------------------------------ بدهی ------------------------------ */

  @Test fun `debt ِ کش‌دار برای هر مشتری همان filter ِ پیشین است — پیش و پس از تراکنشِ تازه`() {
    fun old(d: ShopData, id: String) = DebtMinorFlag.balance(d.transactions.filter { it.debtorId == id })
    val after = BigLedger.sell(base, 5, credit = true)
    for (d in listOf(base, after, base, reread(after))) {
      for (c in d.debtors) assertEquals(old(d, c.id), ShopStore.debt(d, c.id), 0.0)
      assertEquals(old(d, "no-such-customer"), ShopStore.debt(d, "no-such-customer"), 0.0)
    }
    //  بدهیِ مشتریِ نسیه با فروشِ تازه بالا رفت — کشِ دفترِ پیشین دیده نشد
    val who = after.debtors.first().id
    assertTrue(ShopStore.debt(after, who) > ShopStore.debt(base, who))
  }

  /* ------------------------------ فایلِ دفتر ------------------------------ */

  /** همان پیکربندیِ `ShopStore.json` */
  private val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  private fun streamed(d: ShopData): ByteArray =
    java.io.ByteArrayOutputStream().also { o -> ir.vil3ntec.tohid.data.LedgerText.write(storeJson, d) { b, at, n -> o.write(b, at, n) } }.toByteArray()

  @Test fun `LedgerText بایت‌به‌بایت همان encodeToString است`() {
    val odd = base.copy(
      debtors = base.debtors.mapIndexed { i, x ->
        when (i) {
          0 -> x.copy(name = "a\uD800b") // نیم‌جفتِ تنها
          1 -> x.copy(name = "\uDC00 پایان \uD83D")
          2 -> x.copy(name = "😀 \"sales\":[] \\ \u0000\u001f\n")
          else -> x
        }
      },
      expenseCategories = base.expenseCategories + "\"products\":[]",
    )
    val cases = listOf(base, ShopData(), odd, base.copy(sales = emptyList()), BigLedger.sell(base, 4, credit = true), reread(base)) + steps().map { it.second }
    for (d in cases) {
      val want = storeJson.encodeToString(ShopData.serializer(), d).toByteArray(Charsets.UTF_8)
      assertTrue(java.util.Arrays.equals(want, streamed(d)))
    }
  }

  /** همان دو راهِ `ShopStore.writeToDisk`: پیشین (`AtRest.writeText`) و تازه (`DiskWrite.stream` + `LedgerText`) */
  @Test fun `نوشتنِ تکه‌تکه همان فایل و همان prev ِ writeText را می‌نشاند`() {
    val dir = Files.createTempDirectory("perf-ledger-file").toFile()
    try {
      val ctx = ir.vil3ntec.tohid.desktop.DesktopContext
      val a = File(dir, "a.json")
      val b = File(dir, "b.json")
      for (d in listOf(base, BigLedger.sell(base, 6), ShopData())) {
        ir.vil3ntec.tohid.data.AtRest.writeText(ctx, a, storeJson.encodeToString(ShopData.serializer(), d), keepPrevious = true)
        ir.vil3ntec.tohid.data.DiskWrite.stream(b, keepPrevious = true) { o -> ir.vil3ntec.tohid.data.LedgerText.write(storeJson, d) { x, at, n -> o.write(x, at, n) } }
        assertTrue(java.util.Arrays.equals(a.readBytes(), b.readBytes()))
        val pa = ir.vil3ntec.tohid.data.DiskWrite.previousOf(a)
        val pb = ir.vil3ntec.tohid.data.DiskWrite.previousOf(b)
        assertEquals(pa.exists(), pb.exists())
        if (pa.exists()) assertTrue(java.util.Arrays.equals(pa.readBytes(), pb.readBytes()))
        assertTrue(!File(dir, "b.json.tmp").exists())
      }
    } finally {
      dir.deleteRecursively()
    }
  }

  /* ------------------------------ kinship ------------------------------ */

  private val lists: List<(ShopData) -> List<Any?>> = listOf(
    { it.debtors }, { it.transactions }, { it.expenses }, { it.expenseCategories }, { it.products },
    { it.productCategories }, { it.productUnits }, { it.warehouseEntries }, { it.sales }, { it.saleItems },
    { it.saleReturns }, { it.suppliers }, { it.purchases }, { it.supplierPayments }, { it.stockMovements },
    { it.priceHistory }, { it.auditLog },
  )

  /** الگوی پیشینِ `LedgerMerge.kinship` (مجموعهٔ همان‌شیء برای هر فهرستِ عوض‌شده) */
  private fun referenceKinship(a: ShopData, b: ShopData): Long {
    var n = 0L
    for (f in lists) {
      val x = f(a)
      val y = f(b)
      if (x === y) { n += 1 + x.size; continue }
      if (x.isEmpty() || y.isEmpty()) continue
      val mine = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any?, Boolean>(y.size)).apply { addAll(y) }
      for (r in x) if (r !is String && r in mine) n++
    }
    return n
  }

  @Test fun `kinship همان شمارِ الگوی پیشین است — افزودن، وسط، آغاز، تکراری، تازه‌خوانده`() {
    val all = listOf("base" to base) + steps()
    val dup = base.copy(sales = base.sales + base.sales[0] + base.sales[0], productUnits = base.productUnits + base.productUnits[0])
    val big = base.copy(saleItems = base.saleItems.reversed())
    val pool = all.map { it.second } + dup + big + ShopData() + reread(base)
    for (a in pool) for (b in pool) assertEquals(referenceKinship(a, b), ir.vil3ntec.tohid.data.LedgerMerge.kinship(a, b))
  }

  /* ---------------- الگوی پیشینِ `LedgerDiff.diff` (رونوشتِ پیش از PERF-LEDGER؛ دست نزنید) ---------------- */

  private val diffOrder = listOf("returns") + LedgerDiff.COLLECTIONS.filter { it != "returns" }

  private fun rowsOf(tree: JsonObject?, key: String): List<JsonObject> =
    (tree?.get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }?.filter { idOf(it).isNotEmpty() } ?: emptyList()

  private fun idOf(row: JsonObject): String = (row["id"] as? JsonPrimitive)?.content.orEmpty()

  private fun bodyOf(row: JsonObject): JsonObject = JsonObject(row.filterKeys { it != "id" })

  private fun modelView(table: String, rows: List<JsonObject>): Map<String, JsonObject> {
    if (rows.isEmpty()) return emptyMap()
    fun shape(list: List<JsonObject>): List<JsonObject>? = try {
      rowsOf(LedgerDiff.toJson(LedgerDiff.fromJson(JsonObject(mapOf(table to JsonArray(list))))), table)
    } catch (_: Exception) {
      null
    }
    shape(rows)?.takeIf { it.size == rows.size }?.let { all -> return all.associateBy { idOf(it) } }
    val out = HashMap<String, JsonObject>()
    for (r in rows) shape(listOf(r))?.singleOrNull()?.let { out[idOf(it)] = it }
    return out
  }

  private fun referenceDiff(shadow: JsonObject?, next: JsonObject, now: Long = System.currentTimeMillis()): List<SyncOp> {
    val ops = ArrayList<SyncOp>()
    for (table in diffOrder) {
      val before = rowsOf(shadow, table).associateBy { idOf(it) }
      val after = rowsOf(next, table)
      val afterIds = HashSet<String>(after.size)
      val view = modelView(table, after.mapNotNull { row ->
        before[idOf(row)]?.takeIf { LedgerDiff.changedFields(table, bodyOf(it), bodyOf(row)) != null }
      })
      for (row in after) {
        val id = idOf(row)
        afterIds += id
        val was = before[id]
        if (was == null) {
          ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.INSERT, bodyOf(row))
        } else {
          val fields = LedgerDiff.changedFields(table, bodyOf(view[id] ?: was), bodyOf(row))
          if (fields != null) ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.UPDATE, fields)
        }
      }
      for (id in before.keys) if (id !in afterIds) ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.DELETE, null)
    }
    for (table in LedgerDiff.LIST_COLLECTIONS) {
      val after = LedgerDiff.listNames(next, table) ?: continue
      val before = (LedgerDiff.listNames(shadow, table) ?: emptyList()).associateBy { LedgerDiff.listRowId(it) }
      val afterIds = HashSet<String>(after.size)
      for (name in after) {
        val id = LedgerDiff.listRowId(name)
        afterIds += id
        if (id in before) continue
        ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.INSERT, buildJsonObject { put("name", JsonPrimitive(name)) })
      }
      for (id in before.keys) if (id !in afterIds) ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.DELETE, null)
    }
    return ops
  }
}
