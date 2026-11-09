package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.sync.v3.Hlc
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.v3.V3Convert
import ir.vil3ntec.tohid.sync.v3.V3Schema
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  «هر چه دکان‌دار وارد کند کامل به سرور می‌رسد و با نصبِ دوباره برمی‌گردد» — فیلدبه‌فیلد (DATA-SAFETY §۸).
 *
 *  بردارِ `core/test-vectors/ledger-sync-v1.json` دفترِ کاملاً پری است: هر فیلدِ هر مدلِ `ShopData`، تودرتو هم. این آزمون
 *  می‌سنجد که
 *   • هر کلیدِ `ShopData` بیرون از مجموعه‌های همگام در `deviceOnly` ِ بردار با دلیل آمده (همان دسته‌بندیِ `DataSafetyGuardTest`)؛
 *   • هر فیلدِ هر مدل (`Product.packs[].priceMinor`، `AuditEntry.layaway.schedule[].due`، …) در بردار مقدارِ ناتهی دارد
 *     (فیلدِ تازهٔ مدل ⇒ سرخ تا مقداری در بردار بگیرد — و سرور همان را رفت‌وبرگشت کند)؛
 *   • مدل هیچ کلیدِ بردار را نمی‌اندازد و از خودش چیزی نمی‌افزاید؛
 *   • شکلِ روی خط (insert ِ تفاضل از سایهٔ خالی، با `hash`) همان `ops` ِ بردار است — `server/test/sync-ledger-coverage.test.js`
 *     همان بایت‌ها را به push ِ واقعی می‌دهد و Pull و Snapshot را بایت‌به‌بایت می‌سنجد؛
 *   • عددِ روی خط شکلِ جاوااسکریپت دارد (وگرنه سرور `hash_mismatch` می‌دهد و ردیف هرگز نمی‌رسد)؛
 *   • Pull روی دفترِ خالی و Snapshot روی دستگاهِ تازه همان دفتر را می‌سازند — جز `deviceOnly`؛
 *   • v3: رفت (`fromLegacyOp`) و برگشت (`dataToLegacy`، `snapshotToLegacy`) هر فیلد را همان نگه می‌دارد — جز `localOnly`.
 *
 *  `TOHID_WRITE_VECTORS=ledger-sync` ⇒ `ops` و `v3` ِ بردار از روی `ledger` ِ همان پرونده نوشته می‌شوند.
 */
class LedgerSyncCoverageTest {

  private val file: File = locate("core/test-vectors/ledger-sync-v1.json")
  private val vector: JsonObject = Json.parseToJsonElement(file.readText()).jsonObject
  private val tree: JsonObject = vector.getValue("ledger").jsonObject
  private val ledger: ShopData = LedgerDiff.fromJson(tree)
  private val deviceOnly: Set<String> = vector.getValue("deviceOnly").jsonObject.keys

  //  حالتِ نوشتن: پیش از هر آزمون، یک بار (ترتیبِ آزمون‌های JUnit ثابت نیست)
  init {
    if (System.getenv("TOHID_WRITE_VECTORS").orEmpty().contains("ledger-sync") && !written) {
      written = true
      rewrite(wireOps().map(::opJson), v3Json())
    }
  }

  private fun locate(name: String): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      File(dir, name).takeIf { it.isFile }?.let { return it }
      dir = dir.parentFile
    }
    error("$name پیدا نشد")
  }

  private fun canon(e: JsonElement): String = OpHash.canonical(e)

  /* ---------------- ۲) هر فیلدِ هر مدل در بردار ---------------- */

  @OptIn(ExperimentalSerializationApi::class)
  @Test fun `بردار هر فیلدِ هر مدل را تودرتو با مقدارِ ناتهی دارد`() {
    val root = ShopData.serializer().descriptor
    //  کلیدِ بیرون از همگام‌سازی فقط با دلیل در `deviceOnly` ِ بردار (دسته‌بندیِ کلیدها: `DataSafetyGuardTest`)
    val synced = (LedgerDiff.COLLECTIONS + LedgerDiff.LIST_COLLECTIONS).toSet()
    assertEquals((0 until root.elementsCount).map { root.getElementName(it) }.filter { it !in synced }.toSet(), deviceOnly)
    assertTrue(deviceOnly.all { vector.getValue("deviceOnly").jsonObject.getValue(it).jsonPrimitive.content.length > 20 })
    val missing = ArrayList<String>()
    val all = LinkedHashSet<String>()
    for (i in 0 until root.elementsCount) {
      val key = root.getElementName(i)
      val v = tree[key]
      all += key
      if (v == null || v is JsonNull) { missing += key; continue }
      visit(root.getElementDescriptor(i), listOf(v), key, missing, all)
    }
    println("LedgerSyncCoverage: ${all.size} مسیر، ${all.count { p -> deviceOnly.any { p == it || p.startsWith("$it[") || p.startsWith("$it.") } }} فقط-همین-دستگاه")
    assertEquals("فیلدِ مدل بی مقدار در core/test-vectors/ledger-sync-v1.json — یک مقدارِ ناتهی بدهید", emptyList<String>(), missing)
  }

  @OptIn(ExperimentalSerializationApi::class)
  private fun visit(d: SerialDescriptor, got: List<JsonElement>, path: String, missing: MutableList<String>, all: MutableSet<String>) {
    val name = d.serialName.removeSuffix("?")
    when {
      name.startsWith("kotlinx.serialization.json.") ->
        if (got.none { (it as? JsonObject)?.isNotEmpty() == true }) missing += "$path{}"
      d.kind == StructureKind.CLASS -> for (i in 0 until d.elementsCount) {
        val key = d.getElementName(i)
        val p = "$path.$key"
        all += p
        val values = got.mapNotNull { (it as? JsonObject)?.get(key) }.filter { it !is JsonNull }
        if (values.isEmpty()) missing += p else visit(d.getElementDescriptor(i), values, p, missing, all)
      }
      d.kind == StructureKind.LIST -> {
        val items = got.flatMap { (it as? JsonArray).orEmpty() }
        if (items.isEmpty()) missing += "$path[]" else visit(d.getElementDescriptor(0), items, "$path[]", missing, all)
      }
      d.kind == StructureKind.MAP -> {
        val values = got.flatMap { (it as? JsonObject)?.values.orEmpty() }
        if (values.isEmpty()) missing += "$path{}" else visit(d.getElementDescriptor(1), values, "$path{}", missing, all)
      }
    }
  }

  /* ---------------- ۳) مدل همهٔ بردار را نگه می‌دارد ---------------- */

  @Test fun `مدل هیچ کلیدی از بردار نمی‌اندازد و چیزی نمی‌افزاید`() {
    val back = LedgerDiff.toJson(ledger)
    for (key in tree.keys + back.keys) {
      assertEquals("ShopData.$key", tree[key]?.let { canon(it) }, back[key]?.let { canon(it) })
    }
  }

  /* ---------------- ۴) عددِ روی خط = جاوااسکریپت ---------------- */

  @Test fun `عددِ روی خط همان شکلِ جاوااسکریپت است`() {
    //  همان `String(x)` ِ Node برای این عددها (DATA-SAFETY §۸)
    val js = mapOf(
      "4.0E-4" to "0.0004", "2.0E-4" to "0.0002", "5.684341886080802E-14" to "5.684341886080802e-14",
      "1.23456785E7" to "12345678.5", "1.0000000000000002E7" to "10000000.000000002", "1.0E-7" to "1e-7",
      "1.5E-6" to "0.0000015", "-5.0E-4" to "-0.0005", "1.0E21" to "1e+21", "1.2345E22" to "1.2345e+22",
      "1.0E16" to "10000000000000000", "2.5" to "2.5", "0.30000000000000004" to "0.30000000000000004",
      "9.689999999999998" to "9.689999999999998", "1.0E-3" to "0.001", "9.999999E6" to "9999999",
    )
    for ((java, want) in js) assertEquals(java, want, JsNumber.text(java))
    val wire = LedgerDiff.toJson(ledger)
    fun num(table: String, key: String) = (wire.getValue(table).jsonArray[0].jsonObject.getValue(key) as JsonPrimitive).content
    assertEquals("0.0004", num("products", "unitFactor"))
    assertEquals("5.684341886080802e-14", num("sales", "discountValue"))
    assertEquals("12345678.5", num("purchases", "landedCost"))
    val kit = wire.getValue("products").jsonArray[0].jsonObject.getValue("kit").jsonArray[0].jsonObject
    assertEquals("0.0002", (kit.getValue("qty") as JsonPrimitive).content)
    //  هیچ عددی در کلِ دفتر نماییِ جاوا (`E`) یا صحیحِ `.0` ندارد
    val bad = ArrayList<String>()
    fun walk(e: JsonElement, p: String) {
      when (e) {
        is JsonObject -> e.forEach { (k, v) -> walk(v, "$p.$k") }
        is JsonArray -> e.forEachIndexed { i, v -> walk(v, "$p[$i]") }
        is JsonPrimitive -> if (!e.isString && e !is JsonNull && e.booleanOrNull == null) {
          val c = e.content
          if (c.contains('E') || (c.contains('.') && c.toDouble() == Math.rint(c.toDouble()))) bad += "$p=$c"
        }
      }
    }
    walk(wire, "")
    assertEquals(emptyList<String>(), bad)
    //  دلتای `$inc` ِ ریزِ ته‌ماندهٔ `Double` هم
    val inc = LedgerDiff.changedFields("sales",
      buildJsonObject { put("paidAmount", 309.69) }, buildJsonObject { put("paidAmount", 309.69000000000005) })!!
    assertEquals("5.684341886080802e-14", (inc.getValue("paidAmount").jsonObject.getValue("\$inc") as JsonPrimitive).content)
    //  همان عدد در سایهٔ ساختِ پیشین (`4.0E-4`) «تغییر» نیست ⇒ هیچ ویرایشِ تنهایی که ردیفِ نیمه بسازد
    val old = Json.parseToJsonElement("""{"unitFactor":4.0E-4,"kit":[{"productId":"p","qty":2.0E-4}]}""").jsonObject
    val now = buildJsonObject {
      put("unitFactor", JsNumber.of(4.0E-4))
      put("kit", JsonArray(listOf(buildJsonObject { put("productId", "p"); put("qty", JsNumber.of(2.0E-4)) })))
    }
    assertEquals(null, LedgerDiff.changedFields("products", old, now))
  }

  /* ---------------- ۵) شکلِ روی خط = ops ِ بردار ---------------- */

  private fun wireOps(): List<SyncOp> = LedgerDiff.diff(null, LedgerDiff.toJson(ledger), now = 1762500000000)

  private fun opJson(op: SyncOp): JsonObject = buildJsonObject {
    put("table", op.table)
    put("row_id", op.rowId)
    put("type", op.type)
    put("fields", op.fields ?: JsonObject(emptyMap()))
    put("hash", op.hash)
  }

  private fun v3Json(): List<JsonObject> = wireOps().map { op ->
    val r = V3Convert.fromLegacyOp(op, Hlc.format(1762500000000, 0, "ledger-sync"))
    assertTrue("${op.table}/${op.rowId}: ${r.reason}", r.ok && r.native && r.ops.size == 1)
    val o = r.ops.single()
    buildJsonObject {
      put("table", op.table)
      put("row_id", op.rowId)
      put("kind", o.getValue("kind"))
      put("fields", o.getValue("fields"))
    }
  }

  @Test fun `شکلِ روی خط همان ops ِ بردار است و هر ردیف کامل می‌رود`() {
    val ops = wireOps()
    val got = ops.map { canon(opJson(it)) }
    val want = Json.parseToJsonElement(file.readText()).jsonObject.getValue("ops").jsonArray.map { canon(it) }
    assertEquals("ops ِ بردار کهنه است — TOHID_WRITE_VECTORS=ledger-sync", want, got)
    //  هر ردیفِ هر مجموعه یک insert با **همهٔ** کلیدهایش؛ هر نامِ فهرست یک insert
    for (key in LedgerDiff.COLLECTIONS) for (row in tree.getValue(key).jsonArray.map { it.jsonObject }) {
      val id = row.getValue("id").jsonPrimitive.content
      val op = ops.single { it.table == key && it.rowId == id }
      assertEquals(SyncOp.INSERT, op.type)
      assertEquals("$key/$id", canon(JsonObject(row - "id")), canon(op.fields!!))
    }
    for (key in LedgerDiff.LIST_COLLECTIONS) {
      val names = tree.getValue(key).jsonArray.map { it.jsonPrimitive.content }.toSet()
      assertEquals(key, names, ops.filter { it.table == key }.map { it.fields!!.getValue("name").jsonPrimitive.content }.toSet())
    }
    assertTrue(ops.none { it.table in deviceOnly })
  }

  /** فقط `ops` و `v3` ِ پایانِ پرونده؛ بقیهٔ متن (دفترِ دست‌نوشته) همان */
  private fun rewrite(ops: List<JsonObject>, v3: List<JsonObject>) {
    val text = file.readText()
    val at = text.indexOf("\n  \"ops\": ")
    check(at > 0)
    fun lines(list: List<JsonObject>) = list.joinToString(",\n", "[\n", "\n  ]") { "    $it" }
    file.writeText(text.substring(0, at) + "\n  \"ops\": " + lines(ops) + ",\n  \"v3\": " + lines(v3) + "\n}\n")
  }

  /* ---------------- ۶) Pull و Snapshot همان دفتر را می‌سازند ---------------- */

  /** دفتر به شکلِ قابلِ مقایسه: ردیف‌ها به شناسه، فهرست‌های نام مجموعه (سرور ترتیبِ نام را ندارد — §۷) */
  private fun shape(d: ShopData): Map<String, String> {
    val t = LedgerDiff.toJson(d)
    return t.keys.associateWith { key ->
      val v = t.getValue(key)
      when (key) {
        in LedgerDiff.COLLECTIONS -> canon(JsonObject(v.jsonArray.associate { it.jsonObject.getValue("id").jsonPrimitive.content to it }))
        in LedgerDiff.LIST_COLLECTIONS -> v.jsonArray.map { it.jsonPrimitive.content }.sorted().toString()
        else -> canon(v)
      }
    }
  }

  /** دستگاهِ تازه: همه جز deviceOnly، که پیش‌فرضِ دفترِ تازه را دارند */
  private fun expectedRestore(): Map<String, String> {
    val fresh = shape(ShopData())
    return shape(ledger).mapNotNull { (k, v) -> if (k in deviceOnly) fresh[k]?.let { k to it } else k to v }.toMap()
  }

  @Test fun `Pull روی دفترِ خالی همان دفتر را می‌سازد`() {
    val empty = ShopData(expenseCategories = emptyList(), productCategories = emptyList(), productUnits = emptyList())
    val pulled = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(empty), wireOps()))
    assertEquals(expectedRestore(), shape(pulled))
  }

  /** Snapshot ِ سرور از همین ops: `data` = همان fields، به ترتیبِ `row_id` (همان `ORDER BY` ِ سرور) */
  private fun snapshotOf(rows: List<Triple<String, String, JsonObject>>): JsonObject = buildJsonObject {
    put("tables", buildJsonObject {
      for ((table, list) in rows.groupBy { it.first }) {
        put(table, JsonArray(list.sortedBy { it.second }.map { (_, id, data) ->
          buildJsonObject { put("id", id); put("data", data) }
        }))
      }
    })
  }

  @Test fun `Snapshot روی دستگاهِ تازه همان دفتر را برمی‌گرداند جز deviceOnly`() {
    val snap = snapshotOf(wireOps().map { Triple(it.table, it.rowId, it.fields!!) })
    val restored = LedgerDiff.fromJson(LedgerDiff.fromSnapshot(snap, LedgerDiff.toJson(ShopData())))
    assertEquals(expectedRestore(), shape(restored))
  }

  /* ---------------- ۷) Sync v3 ---------------- */

  @Test fun `v3 هر فیلد را رفت و برگشت همان نگه می‌دارد جز localOnly`() {
    val v3 = v3Json()
    val want = Json.parseToJsonElement(file.readText()).jsonObject.getValue("v3").jsonArray.map { canon(it) }
    assertEquals("v3 ِ بردار کهنه است — TOHID_WRITE_VECTORS=ledger-sync", want, v3.map { canon(it) })
    for ((op, o) in wireOps().zip(v3)) {
      val spec = V3Schema.specOf(op.table)!!
      val back = V3Convert.dataToLegacy(spec, o.getValue("fields").jsonObject)
      assertEquals("${op.table}/${op.rowId}", canon(JsonObject(op.fields!! - spec.localOnly.toSet())), canon(back))
    }
    //  Snapshot ِ v3 ⇒ دفتر: همان v1، جز `localOnly` (`Sale.syncStatus` ⇒ پیش‌فرض)
    val snap = snapshotOf(v3.map { Triple(it.getValue("table").jsonPrimitive.content, it.getValue("row_id").jsonPrimitive.content, it.getValue("fields").jsonObject) })
    val restored = LedgerDiff.fromJson(LedgerDiff.fromSnapshot(V3Convert.snapshotToLegacy(snap), LedgerDiff.toJson(ShopData())))
    val want3 = expectedRestore().toMutableMap()
    want3["sales"] = shape(ledger.copy(sales = ledger.sales.map { it.copy(syncStatus = "pending") })).getValue("sales")
    assertEquals(want3, shape(restored))
  }

  private companion object {
    var written = false
  }
}
