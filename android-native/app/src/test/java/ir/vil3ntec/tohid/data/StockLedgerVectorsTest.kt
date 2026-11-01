package ir.vil3ntec.tohid.data

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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  بردارهای مرجعِ پلِ دفتر ⇐ هستهٔ موجودی (`license/test-vectors/stock-ledger-v1.json`)،
 *  کنارِ بردارهای خودِ هسته (`stock-v1.json`). فقط کلیدهایی که در expect آمده سنجیده می‌شوند.
 *
 *  cwd در CIِ اندروید `android-native/app` و در کامپیوتر `desktop` است؛ فایل با بالا
 *  رفتن از cwd پیدا می‌شود (همان `StockCoreVectorsTest`).
 */
class StockLedgerVectorsTest {

  private val vectors: JsonObject by lazy {
    var dir: File? = File(".").absoluteFile
    var file: File? = null
    while (dir != null && file == null) {
      file = File(dir, "license/test-vectors/stock-ledger-v1.json").takeIf { it.isFile }
      dir = dir.parentFile
    }
    assertNotNull("license/test-vectors/stock-ledger-v1.json پیدا نشد (cwd=${File(".").absoluteFile})", file)
    Json.parseToJsonElement(file!!.readText()).jsonObject
  }

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private fun ledger(o: JsonElement): ShopData = json.decodeFromJsonElement(ShopData.serializer(), o)

  private val shop: ShopData by lazy { ledger(vectors.getValue("ledger")) }
  private val view: StockLedger.View by lazy { StockLedger.replay(shop) }

  /** JSON → مقدارِ Kotlin: عدد Long، شیء Map، آرایه List */
  private fun plain(e: JsonElement?): Any? = when (e) {
    null, JsonNull -> null
    is JsonObject -> e.mapValues { plain(it.value) }
    is JsonArray -> e.map { plain(it) }
    is JsonPrimitive -> if (e.isString) e.content else e.longOrNull ?: e.booleanOrNull ?: e.content
  }

  private fun subset(actual: Map<String, Any?>, expected: JsonObject, where: String) {
    for ((k, v) in expected) assertEquals("$where: $k", plain(v), actual[k])
  }

  private fun picks(list: List<ir.vil3ntec.tohid.core.stock.Pick>) = list.map { mapOf("lotId" to it.lotId, "qtyMilli" to it.qtyMilli) }

  private fun rowMap(r: StockLedger.Row): Map<String, Any?> = mapOf(
    "id" to r.id, "type" to r.type, "productId" to r.productId, "location" to r.location, "qtyMilli" to r.qtyMilli,
    "costMinor" to r.costMinor, "costAdjustMinor" to r.costAdjustMinor, "userId" to r.userId, "reason" to r.reason,
    "refId" to r.refId, "lotId" to r.lotId,
  )

  private fun taskMap(t: StockLedger.Task): Map<String, Any?> = mapOf(
    "id" to t.id, "productId" to t.productId, "location" to t.location, "reasons" to t.reasons,
    "shortMilli" to t.shortMilli, "qtyAfterMilli" to t.qtyAfterMilli, "acked" to t.acked,
  )

  private fun checkMap(c: StockLedger.Check): Map<String, Any?> = mapOf(
    "ok" to c.ok, "error" to c.error, "availableMilli" to c.availableMilli, "expiredMilli" to c.expiredMilli,
    "costMinor" to c.costMinor, "unitCostMinor" to c.unitCostMinor,
    "picks" to c.picks.map { mapOf("location" to it.location, "lotId" to it.lotId, "qtyMilli" to it.qtyMilli) },
  )

  @Test
  fun format() {
    assertEquals("stock-ledger-v1", vectors["format"]!!.jsonPrimitive.content)
    assertEquals("stock-ledger-v1", StockLedger.FORMAT)
  }

  @Test
  fun modes() {
    val expect = vectors.getValue("expect").jsonObject
    assertEquals(expect["on"]!!.jsonPrimitive.content.toBoolean(), StockLedger.modeOn(shop))
    for (m in vectors.getValue("modes").jsonArray) {
      val o = m.jsonObject
      val d = ledger(JsonObject(mapOf("auditLog" to o.getValue("auditLog"))))
      assertEquals(o["name"]!!.jsonPrimitive.content, o["on"]!!.jsonPrimitive.content.toBoolean(), StockLedger.modeOn(d))
    }
    assertEquals(false, StockLedger.modeOn(ShopData()))
  }

  @Test
  fun positions() {
    val expect = vectors.getValue("expect").jsonObject
    assertEquals(plain(expect["errors"]), view.errors.size.toLong())
    assertEquals(plain(expect["reconciles"]), StockLedger.reconcileAll(view))
    assertEquals(plain(expect["locations"]), StockLedger.locations(shop))
    for ((pid, w) in expect.getValue("products").jsonObject) {
      val want = w.jsonObject
      val pv = StockLedger.productView(view, pid)
      assertEquals("$pid qty", plain(want["qtyMilli"]), pv.qtyMilli)
      assertEquals("$pid value", plain(want["valueMinor"]), pv.valueMinor)
      assertEquals("$pid unit", plain(want["unitCostMinor"]), pv.unitCostMinor)
      assertEquals("$pid locations", plain(want["locations"]),
        pv.locations.map { mapOf("location" to it.location, "qtyMilli" to it.qtyMilli, "valueMinor" to it.valueMinor) })
      assertEquals("$pid lots", plain(want["lots"]),
        pv.lots.map { mapOf("lotId" to it.lotId, "location" to it.location, "qtyMilli" to it.qtyMilli) })
    }
  }

  @Test
  fun rows() {
    val expect = vectors.getValue("expect").jsonObject
    val want = expect.getValue("rows").jsonArray
    assertEquals("ترتیب", want.map { it.jsonObject["id"]!!.jsonPrimitive.content }, view.rows.map { it.id })
    want.forEachIndexed { i, w -> subset(rowMap(view.rows[i]), w.jsonObject, "row ${view.rows[i].id}") }
    for ((id, p) in expect.getValue("picks").jsonObject) {
      assertEquals("picks $id", plain(p), picks(view.rows.first { it.id == id }.picks))
    }
  }

  @Test
  fun tasks() {
    val want = vectors.getValue("expect").jsonObject.getValue("tasks").jsonArray
    assertEquals(want.map { it.jsonObject["id"]!!.jsonPrimitive.content }, view.tasks.map { it.id })
    want.forEachIndexed { i, w -> subset(taskMap(view.tasks[i]), w.jsonObject, "task ${view.tasks[i].id}") }
  }

  @Test
  fun cart() {
    for (c in vectors.getValue("cart").jsonArray) {
      val o = c.jsonObject
      val name = o["name"]!!.jsonPrimitive.content
      val lines = o.getValue("lines").jsonArray.map {
        val l = it.jsonObject
        StockLedger.Line(l["productId"]!!.jsonPrimitive.content, l["qtyMilli"]!!.jsonPrimitive.longOrNull!!, l["lotId"]?.jsonPrimitive?.content)
      }
      val got = StockLedger.checkCart(view, lines, o["today"]!!.jsonPrimitive.content)
      val want = o.getValue("expect").jsonArray
      assertEquals(name, want.size, got.size)
      want.forEachIndexed { i, w -> subset(checkMap(got[i]), w.jsonObject, "$name [$i]") }
    }
  }

  @Test
  fun transfers() {
    for (t in vectors.getValue("transfers").jsonArray) {
      val o = t.jsonObject
      val got = StockLedger.checkTransfer(
        view, o["productId"]!!.jsonPrimitive.content, o["from"]!!.jsonPrimitive.content, o["to"]!!.jsonPrimitive.content,
        o["qtyMilli"]!!.jsonPrimitive.longOrNull!!, null, o["today"]!!.jsonPrimitive.content,
      )
      val m = checkMap(got).toMutableMap()
      m["picks"] = got.picks.map { mapOf("lotId" to it.lotId, "qtyMilli" to it.qtyMilli) }
      subset(m, o.getValue("expect").jsonObject, o["name"]!!.jsonPrimitive.content)
    }
  }

  @Test
  fun count() {
    val c = vectors.getValue("count").jsonObject
    val ids = c.getValue("productIds").jsonArray.map { it.jsonPrimitive.content }
    val counts = c.getValue("counts").jsonObject.mapValues { it.value.jsonPrimitive.longOrNull!! }
    val snap = c.getValue("snapshot").jsonObject.mapValues { it.value.jsonPrimitive.longOrNull!! }
    val r = StockLedger.countReview(view, ids, counts, snap)
    subset(
      mapOf(
        "matched" to r.matched.toLong(), "variance" to r.variance.toLong(), "uncounted" to r.uncounted.toLong(),
        "valueMinor" to r.valueMinor, "gainMinor" to r.gainMinor, "lossMinor" to r.lossMinor,
        "lines" to r.lines.map {
          mapOf("productId" to it.productId, "systemMilli" to it.systemMilli, "countedMilli" to it.countedMilli,
            "diffMilli" to it.diffMilli, "valueMinor" to it.valueMinor)
        },
      ),
      c.getValue("expect").jsonObject, "count",
    )
  }

  @Test
  fun spreadLandedAndUnitCost() {
    for (row in vectors.getValue("spreadLanded").jsonArray) {
      val a = row.jsonArray
      val total = a[0].jsonPrimitive.longOrNull!!
      val weights = a[1].jsonArray.map { it.jsonPrimitive.longOrNull!! }
      val got = StockLedger.spreadLanded(total, weights)
      assertEquals("spreadLanded($total, $weights)", plain(a[2]), got)
      if (weights.isNotEmpty()) assertEquals(maxOf(0L, total), got.sum())
    }
    for (row in vectors.getValue("unitCost").jsonArray) {
      val a = row.jsonArray.map { it.jsonPrimitive.longOrNull!! }
      assertEquals("unitCost(${a[0]}, ${a[1]})", a[2], StockLedger.unitCostMinor(a[0], a[1]))
    }
  }

  /** رفتارِ امروز: موجودیِ دست از پل = `ShopStore.stock` (ورودی منهای فروشِ خالص) */
  @Test
  fun onHandEqualsTodaysEngine() {
    val ids = (shop.warehouseEntries.map { it.productId } + shop.saleItems.map { it.productId }).filter { it.isNotEmpty() }.toSet()
    for (pid in ids) {
      assertEquals(pid, StockLedger.milli(ShopStore.stock(shop, pid)), StockLedger.productView(view, pid).qtyMilli)
    }
  }

  @Test
  fun singleProductReplayMatchesFull() {
    for (pid in listOf("p1", "p2", "p3", "p4")) {
      val one = StockLedger.replay(shop, listOf(pid))
      assertEquals(pid, StockLedger.productView(view, pid), StockLedger.productView(one, pid))
    }
  }

  /** خاموش: دفترِ بی ردیفِ stock_mode خاموش است؛ پل چیزی در دفتر نمی‌نویسد (ShopData تغییرناپذیر است) */
  @Test
  fun offByDefault() {
    val off = shop.copy(auditLog = emptyList())
    assertEquals(false, StockLedger.modeOn(off))
    val before = off.toString()
    StockLedger.checkCart(StockLedger.replay(off), listOf(StockLedger.Line("p1", 1000)), "2026-10-15")
    assertEquals(before, off.toString())
    assertTrue(StockLedger.replay(off).errors.isEmpty())
  }
}
