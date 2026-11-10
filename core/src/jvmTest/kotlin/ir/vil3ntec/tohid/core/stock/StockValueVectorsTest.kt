package ir.vil3ntec.tohid.core.stock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  INV-16 — بردارهای مرجعِ ارزشِ موجودی و سودِ بالقوه (`core/test-vectors/stock-value-v1.json`) روی
 *  `StockAnalysis.valuation`؛ همان پرونده را `server/test/stock-value-core.test.js` روی دوقلوی وب
 *  (`license/stock-value-core.js` ‹`valuation`›) می‌دواند — هر دو یک عدد. فقط JVM (پرونده‌خوان)؛ ریشهٔ مخزن با بالا رفتن از cwd.
 */
class StockValueVectorsTest {

  private val vectors: JsonObject by lazy {
    val rel = "core/test-vectors/stock-value-v1.json"
    val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, rel).isFile }
      ?: error("$rel پیدا نشد (cwd=${System.getProperty("user.dir")})")
    Json.parseToJsonElement(File(root, rel).readText()).jsonObject
  }

  private fun JsonObject.num(k: String): Long? = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull
  private fun JsonObject.text(k: String): String = getValue(k).jsonPrimitive.content
  private fun JsonObject.ids(k: String): List<String> = getValue(k).jsonArray.map { it.jsonPrimitive.content }

  @Test fun formatAndBound() {
    assertEquals("stock-value-v1", vectors.text("format"))
    assertEquals(StockAnalysis.MAX_EXACT, vectors.num("max_exact"))
  }

  @Test fun everyCaseMatchesExpect() {
    val cases = vectors.getValue("cases").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 15, "cases: ${cases.size}")
    for (c in cases) {
      val name = c.text("name")
      val lines = c.getValue("lines").jsonArray.map { it.jsonObject }.map { l ->
        StockAnalysis.ValueLine(
          id = l.text("id"),
          onHandMilli = l.num("onHandMilli")!!,
          unitCostMinor = l.num("unitCostMinor")!!,
          unitPriceMinor = l.num("unitPriceMinor")!!,
          costValueMinor = l.num("costValueMinor"),
        )
      }
      val v = StockAnalysis.valuation(lines)
      val e = c.getValue("expect").jsonObject
      assertEquals(e.num("costMinor"), v.costMinor, "$name: costMinor")
      assertEquals(e.num("retailMinor"), v.retailMinor, "$name: retailMinor")
      assertEquals(e.num("potentialProfitMinor"), v.potentialProfitMinor, "$name: potentialProfitMinor")
      assertEquals(e.num("marginBp"), v.marginBp, "$name: marginBp")
      assertEquals(e.num("inStock"), v.inStock.toLong(), "$name: inStock")
      assertEquals(e.ids("missingCost"), v.missingCost, "$name: missingCost")
      assertEquals(e.ids("missingPrice"), v.missingPrice, "$name: missingPrice")
      assertEquals(e.ids("belowCost"), v.belowCost, "$name: belowCost")
      assertEquals(e.ids("negative"), v.negative, "$name: negative")
      assertEquals(e.ids("outOfRange"), v.outOfRange, "$name: outOfRange")
      val want = e.getValue("lines").jsonArray.map { it.jsonObject }.map {
        StockAnalysis.LineValue(it.text("id"), it.num("costMinor")!!, it.num("retailMinor")!!)
      }
      assertEquals(want, v.lines, "$name: lines")
      assertEquals(e.getValue("complete").jsonPrimitive.boolean, v.complete, "$name: complete")
    }
  }
}
