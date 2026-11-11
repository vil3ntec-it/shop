package ir.vil3ntec.tohid.core.sale

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  POS-37: `SaleTax` در برابرِ `core/test-vectors/sale-tax-v1.json` — همان که `server/test/sale-tax.test.js` با
 *  `license/sale-tax.js` می‌خواند. خروجی به همان شکلِ JSON ِ JS برگردانده و برابر سنجیده می‌شود.
 */
class SaleTaxVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/sale-tax-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/sale-tax-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/sale-tax-v1.json پیدا نشد")
  }

  private fun cases(k: String): List<JsonObject> = v[k]!!.jsonArray.map { it.jsonObject }
  private fun isNull(e: JsonElement?): Boolean = e == null || e is JsonNull

  /** تنظیمِ JSON ⇒ `Config`؛ حالتِ ناشناخته یا نرخِ بیرون از Int ⇒ `null` (همان «خاموش» ِ JS) */
  private fun config(e: JsonElement?): SaleTax.Config? {
    if (isNull(e)) return null
    val o = e!!.jsonObject
    val mode = SaleTax.Mode.of((o["mode"] as? JsonPrimitive)?.contentOrNull) ?: return null
    val rate = (o["rateBp"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return null
    val rates = (o["rates"] as? JsonObject)?.entries?.mapNotNull { (k, x) -> x.jsonPrimitive.contentOrNull?.toIntOrNull()?.let { k to it } }?.toMap().orEmpty()
    return SaleTax.Config(mode, rate, rates)
  }

  private fun configJson(c: SaleTax.Config?): JsonElement = if (c == null) JsonNull else JsonObject(
    mapOf(
      "mode" to JsonPrimitive(c.mode.wire),
      "rateBp" to JsonPrimitive(c.rateBp),
      "rates" to JsonObject(c.rates.mapValues { JsonPrimitive(it.value) }),
    ),
  )

  @Test fun constants() {
    val c = v["constants"]!!.jsonObject
    assertEquals(SaleTax.FULL_BP, c["fullBp"]!!.jsonPrimitive.int)
    assertEquals(SaleTax.STEP, c["step"]!!.jsonPrimitive.long)
    assertEquals(SaleTax.MAX_RATES, c["maxRates"]!!.jsonPrimitive.int)
    assertEquals(SaleTax.Mode.entries.map { it.wire }, c["modes"]!!.jsonArray.map { it.jsonPrimitive.content })
  }

  @Test fun roundsHalfUpToWholeAfghani() {
    val rows = cases("ofMinor")
    assertTrue(rows.size >= 8)
    for (r in rows) {
      assertEquals(r["out"]!!.jsonPrimitive.long, SaleTax.ofMinor(r["base"]!!.jsonPrimitive.long, r["rate"]!!.jsonPrimitive.int), r.toString())
    }
  }

  @Test fun cleanConfig() {
    for (r in cases("clean")) assertEquals(r["out"], configJson(SaleTax.clean(config(r["input"]))), r["_"].toString())
  }

  @Test fun computeTax() {
    val rows = cases("compute")
    assertTrue(rows.size >= 15)
    for (r in rows) {
      val lines = r["lines"]!!.jsonArray.map { it.jsonObject }.map { l ->
        SaleTax.Line((l["productId"] as? JsonPrimitive)?.contentOrNull, l["grossMinor"]!!.jsonPrimitive.long)
      }
      val got = SaleTax.compute(lines, r["discount"]!!.jsonPrimitive.long, config(r["config"]))
      val name = r["_"].toString()
      if (isNull(r["out"])) { assertNull(got, name); continue }
      val out = r["out"]!!.jsonObject
      assertEquals(out["taxMinor"]!!.jsonPrimitive.long, got!!.taxMinor, name)
      assertEquals(out["lineTaxMinor"]!!.jsonArray.map { it.jsonPrimitive.long }, got.lineTaxMinor, name)
    }
  }

  @Test fun ratesBox() {
    val ids = mapOf("نان" to "p1", "شیر" to "p2", "626000" to "p3")
    val p = SaleTax.parseRates("نان، شیر = ۰\n626000 = ۵٫۵٪\nبرنج = 5\nبی‌مساوی\nنان = ۱۰۱", ids::get)
    assertEquals(mapOf("p1" to 0, "p2" to 0, "p3" to 550), p.rates)
    assertEquals(
      listOf(SaleTax.Problem.UNKNOWN_ITEM, SaleTax.Problem.BAD_LINE, SaleTax.Problem.BAD_RATE),
      p.problems.map { it.second },
    )
    val names = ids.entries.associate { it.value to it.key }
    assertEquals("نان، شیر = 0\n626000 = 550", SaleTax.formatRates(p.rates, { names[it] ?: it }, { it.toString() }))
  }
}
