package ir.vil3ntec.tohid.core.pick

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  PCK-05/06: `PickSubstitute` (و `PickCore.settle` با قلمِ جایگزین) در برابرِ بردارِ مرجعِ
 *  `core/test-vectors/pick-sub-v1.json` — همان که `server/test/pick-sub-core.test.js` با `pick-core.js` می‌خواند.
 *  فایل‌خوانی فقط این‌جا (jvmTest).
 */
class PickSubstituteVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/pick-sub-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/pick-sub-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/pick-sub-v1.json پیدا نشد")
  }

  private fun JsonElement.str() = jsonPrimitive.content
  private fun cases(k: String) = vec.getValue(k).jsonArray.map { it.jsonObject }
  private fun JsonObject.s(k: String) = this[k]?.takeIf { it !is JsonNull }?.str() ?: ""

  private fun item(o: JsonObject) = PickSubstitute.Item(
    productId = o.s("productId"), category = o.s("category"), subcategory = o.s("subcategory"), halal = o.s("halal"),
    unit = o.s("unit"), priceMinor = o["priceMinor"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.long,
    brand = o.s("brand"), name = o.s("name"),
  )

  @Test
  fun versionIsOne() = assertEquals(1, vec.getValue("version").jsonPrimitive.int)

  @Test
  fun safeSubstitutesOnly() {
    val cs = cases("block")
    assertTrue(cs.size >= 9)
    for (c in cs) {
      val want = c["want"]?.takeIf { it !is JsonNull }?.str()
      assertEquals(want, PickSubstitute.block(item(c.getValue("orig").jsonObject), item(c.getValue("cand").jsonObject)), c.getValue("name").str())
    }
  }

  @Test
  fun transparentPriceIsTheLowerOne() {
    for (c in cases("charge")) {
      val w = c.getValue("want").jsonObject
      assertEquals(
        PickSubstitute.Charge(w.getValue("chargedUnitPriceMinor").jsonPrimitive.long, w.getValue("totalMinor").jsonPrimitive.long),
        PickSubstitute.charge(c.getValue("origUnitPriceMinor").jsonPrimitive.long, c.getValue("candUnitPriceMinor").jsonPrimitive.long,
          c.getValue("qtyMilli").jsonPrimitive.long),
        c.toString(),
      )
    }
  }

  @Test
  fun rankedSuggestions() {
    for (c in cases("rank")) {
      val orig = item(c.getValue("orig").jsonObject)
      val cands = c.getValue("cands").jsonArray.map { item(it.jsonObject) }
      val want = c.getValue("want").jsonArray.map { it.str() }
      val limit = c.getValue("limit").jsonPrimitive.int
      assertEquals(want, PickSubstitute.rank(orig, cands, limit), c.getValue("name").str())
      assertEquals(want, PickSubstitute.rank(orig, cands.reversed(), limit), "پایدار: ${c.getValue("name").str()}")
    }
  }

  @Test
  fun preferenceAfterDeadline() {
    for (c in cases("timeout")) assertEquals(c.getValue("want").str(), PickSubstitute.timeoutDecision(c.getValue("preference").str()))
    for (c in cases("proposable")) {
      assertEquals(c.getValue("want").jsonPrimitive.boolean, PickSubstitute.proposable(c.getValue("preference").str()))
    }
  }

  @Test
  fun settleWithSubstitutes() {
    for (c in cases("settle")) {
      val input = c.getValue("input").jsonObject
      val lines = input.getValue("lines").jsonArray.map { it.jsonObject }.map {
        PickCore.SettleLine(it.getValue("lineNo").jsonPrimitive.int, it.getValue("status").str(),
          it.getValue("orderedQtyMilli").jsonPrimitive.long, it.getValue("pickedQtyMilli").jsonPrimitive.long,
          it.getValue("unitPriceMinor").jsonPrimitive.long, it.getValue("originalTotalMinor").jsonPrimitive.long)
      }
      val got = PickCore.settle(lines, input.getValue("discountMinor").jsonPrimitive.long, input.getValue("deliveryFeeMinor").jsonPrimitive.long)
      val w = c.getValue("want").jsonObject
      val name = c.getValue("name").str()
      assertEquals(w.getValue("lines").jsonArray.map { it.jsonObject }.map {
        PickCore.SettledLine(it.getValue("lineNo").jsonPrimitive.int, it.getValue("qtyMilli").jsonPrimitive.long, it.getValue("totalMinor").jsonPrimitive.long)
      }, got.lines, name)
      for ((k, v) in listOf("subtotalMinor" to got.subtotalMinor, "discountMinor" to got.discountMinor,
        "deliveryFeeMinor" to got.deliveryFeeMinor, "totalMinor" to got.totalMinor, "deductedMinor" to got.deductedMinor)) {
        assertEquals(w.getValue(k).jsonPrimitive.long, v, "$name: $k")
      }
      assertEquals(w.getValue("removedLines").jsonArray.map { it.jsonPrimitive.int }, got.removedLines, name)
      assertEquals(w.getValue("changedLines").jsonArray.map { it.jsonPrimitive.int }, got.changedLines, name)
    }
  }
}
