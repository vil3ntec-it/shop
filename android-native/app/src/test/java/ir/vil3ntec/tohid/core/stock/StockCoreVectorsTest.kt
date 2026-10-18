package ir.vil3ntec.tohid.core.stock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  بردارهای مرجعِ موجودی و بها (`license/test-vectors/stock-v1.json`) — همان فایلی
 *  که `server/test/stock-core.test.js` روی `license/stock-core.js` می‌دواند.
 *
 *  قاعدهٔ اجرا (در JS هم همین):
 *    doc = { id: "d" + (i + 1), at: 1700000000000 + i * 1000 } ∪ defaults ∪ step.doc
 *    فقط کلیدهایی که در expect آمده سنجیده می‌شوند؛ لات‌ها با (lotId, qtyMilli).
 *
 *  این آزمون در CIِ اندروید (cwd = `android-native/app`) و کامپیوتر (cwd = `desktop`)
 *  اجرا می‌شود؛ فایل با بالا رفتن از cwd پیدا می‌شود.
 */
class StockCoreVectorsTest {

  private val vectors: JsonObject by lazy {
    var dir: File? = File(".").absoluteFile
    var file: File? = null
    while (dir != null && file == null) {
      file = File(dir, "license/test-vectors/stock-v1.json").takeIf { it.isFile }
      dir = dir.parentFile
    }
    assertNotNull("license/test-vectors/stock-v1.json پیدا نشد (cwd=${File(".").absoluteFile})", file)
    Json.parseToJsonElement(file!!.readText()).jsonObject
  }

  /** JSON → مقدارِ Kotlin: عدد Long، شیء Map، آرایه List */
  private fun plain(e: JsonElement?): Any? = when (e) {
    null, JsonNull -> null
    is JsonObject -> e.mapValues { plain(it.value) }
    is JsonArray -> e.map { plain(it) }
    is JsonPrimitive -> if (e.isString) e.content else e.longOrNull ?: e.booleanOrNull ?: e.content
  }

  private fun JsonObject.str(k: String): String? = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
  private fun JsonObject.long(k: String): Long? = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull

  private fun doc(o: JsonObject): StockDoc = StockDoc(
    id = o.str("id") ?: "",
    type = o.str("type") ?: "",
    productId = o.str("productId") ?: "",
    warehouseId = o.str("warehouseId") ?: "",
    userId = o.str("userId") ?: "",
    at = o.long("at") ?: 0,
    qtyMilli = o.long("qtyMilli"),
    reason = o.str("reason"),
    costMinor = o.long("costMinor"),
    landedMinor = o.long("landedMinor"),
    valueMinor = o.long("valueMinor"),
    lot = (o["lot"] as? JsonObject)?.let { LotRef(it.str("lotId") ?: "", it.str("productionDate"), it.str("expiryDate")) },
    lotId = o.str("lotId"),
    refId = o.str("refId"),
  )

  private fun picks(list: List<Pick>) = list.map { mapOf("lotId" to it.lotId, "qtyMilli" to it.qtyMilli) }

  private fun resultMap(r: StockResult): Map<String, Any?> = mapOf(
    "ok" to r.ok,
    "error" to r.error,
    "costMinor" to r.costMinor,
    "costAdjustMinor" to r.costAdjustMinor,
    "picks" to picks(r.picks),
    "shortMilli" to r.shortMilli,
    "availableMilli" to r.availableMilli,
    "expiredMilli" to r.expiredMilli,
    "reviewTask" to r.reviewTask?.let {
      mapOf(
        "kind" to it.kind, "productId" to it.productId, "warehouseId" to it.warehouseId, "docId" to it.docId,
        "reasons" to it.reasons, "shortMilli" to it.shortMilli, "qtyAfterMilli" to it.qtyAfterMilli,
      )
    },
  )

  private fun positionMap(p: Position): Map<String, Any?> = mapOf(
    "productId" to p.productId,
    "warehouseId" to p.warehouseId,
    "qtyMilli" to p.qtyMilli,
    "valueMinor" to p.valueMinor,
    "reservedMilli" to p.reservedMilli,
    "sellableMilli" to p.sellableMilli,
    "lots" to p.fefoLots.map { mapOf("lotId" to it.lotId, "qtyMilli" to it.qtyMilli) },
  )

  private fun totalsMap(t: StockTotals): Map<String, Any?> = mapOf(
    "purchasesMinor" to t.purchasesMinor, "landedMinor" to t.landedMinor, "cogsMinor" to t.cogsMinor,
    "wasteMinor" to t.wasteMinor, "supplierReturnsMinor" to t.supplierReturnsMinor,
    "adjustmentsMinor" to t.adjustmentsMinor, "transfersMinor" to t.transfersMinor,
    "productionMinor" to t.productionMinor, "revaluationMinor" to t.revaluationMinor,
    "costAdjustMinor" to t.costAdjustMinor,
  )

  private fun subset(actual: Map<String, Any?>, expected: JsonObject, where: String) {
    for ((k, v) in expected) {
      assertTrue("$where: کلیدِ ناشناختهٔ $k", actual.containsKey(k))
      assertEquals("$where: $k", plain(v), actual[k])
    }
  }

  @Test
  fun `نسخهٔ بردارها`() {
    assertEquals(StockCore.VECTORS_FORMAT, vectors.str("format"))
  }

  @Test
  fun `mulDivRound — نیمه به سمتِ +∞، دقیق`() {
    for (row in vectors["mulDivRound"]!!.jsonArray) {
      val (a, b, c, want) = row.jsonArray.map { it.jsonPrimitive.longOrNull!! }
      assertEquals("mulDivRound($a, $b, $c)", want, StockCore.mulDivRound(a, b, c))
    }
  }

  @Test
  fun `FEFO`() {
    for (fc in vectors["fefo"]!!.jsonArray.map { it.jsonObject }) {
      val today = fc.str("today")
      val lots = fc["lots"]!!.jsonArray.map { it.jsonObject }.map {
        Lot(it.str("lotId")!!, it.str("productionDate"), it.str("expiryDate"), it.long("qtyMilli")!!, it.long("seq")!!.toInt())
      }
      for (p in fc["picks"]!!.jsonArray.map { it.jsonObject }) {
        val got = StockCore.pickFefo(
          lots, p.long("qtyMilli")!!, today,
          includeExpired = p["includeExpired"]!!.jsonPrimitive.booleanOrNull!!,
          preferLotId = p.str("preferLotId"),
        )
        subset(
          mapOf("picks" to picks(got.picks), "shortMilli" to got.shortMilli),
          p["expect"]!!.jsonObject, "${fc.str("name")} q=${p.long("qtyMilli")}",
        )
      }
    }
  }

  @Test
  fun `دفتر — همهٔ موردها`() {
    for (lc in vectors["ledger"]!!.jsonArray.map { it.jsonObject }) {
      val name = lc.str("name")!!
      val today = lc.str("today")
      val defaults = lc["defaults"]?.jsonObject ?: JsonObject(emptyMap())
      var book = StockBook()
      lc["steps"]!!.jsonArray.forEachIndexed { i, s ->
        val step = s.jsonObject
        val merged = LinkedHashMap<String, JsonElement>()
        merged["id"] = JsonPrimitive("d${i + 1}")
        merged["at"] = JsonPrimitive(1_700_000_000_000L + i * 1000L)
        merged.putAll(defaults)
        merged.putAll(step["doc"]!!.jsonObject)
        val mode = if (step.str("mode") == "recorded") StockMode.RECORDED else StockMode.LOCAL
        val before = book
        val a = StockCore.apply(book, doc(JsonObject(merged)), mode, today)
        subset(resultMap(a.result), step["expect"]!!.jsonObject, "$name گام ${i + 1}")
        if (!a.result.ok) assertSame("سندِ ردشده دفتر را عوض نمی‌کند", before, a.book)
        book = a.book
      }
      val fin = lc["final"]!!.jsonObject
      val positions = StockCore.positions(book)
      val want = fin["positions"]!!.jsonArray
      assertEquals("$name: شمارِ جایگاه‌ها", want.size, positions.size)
      want.forEachIndexed { i, w -> subset(positionMap(positions[i]), w.jsonObject, "$name جایگاه $i") }
      subset(totalsMap(StockCore.bookTotals(book)), fin["totals"]!!.jsonObject, "$name جمع‌ها")
      for (p in positions) {
        assertTrue("$name: آزمونِ تطبیق ${p.productId}/${p.warehouseId}", p.reconciles)
      }
    }
  }

  @Test
  fun `دفترِ حرکت — علامت، موجودی، فیلتر، خروجی`() {
    val v = vectors["ledgerView"]!!.jsonObject
    val docs = v["docs"]!!.jsonArray.map { doc(it.jsonObject) }
    assertEquals(plain(v["signedQtyMilli"]), docs.map { StockCore.signedQtyMilli(it) })
    for (o in v["onHand"]!!.jsonArray.map { it.jsonObject }) {
      assertEquals(o.long("qtyMilli"), StockCore.onHandMilli(docs, o.str("productId")!!, o.str("warehouseId")!!))
    }
    for (f in v["filters"]!!.jsonArray.map { it.jsonObject }) {
      val fo = f["filter"]!!.jsonObject
      val filter = DocFilter(
        productId = fo.str("productId"),
        warehouseId = fo.str("warehouseId"),
        types = fo["types"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet(),
        userId = fo.str("userId"),
        from = fo.long("from"),
        to = fo.long("to"),
      )
      assertEquals(fo.toString(), plain(f["ids"]), StockCore.filterDocs(docs, filter).map { it.id })
    }
    assertEquals(plain(v["exportHeader"]), StockCore.EXPORT_HEADER)
    assertEquals(plain(v["exportRows"]), StockCore.exportRows(docs))
  }
}
