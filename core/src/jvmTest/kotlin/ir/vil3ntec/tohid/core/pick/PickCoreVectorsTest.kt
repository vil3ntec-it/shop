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
 *  PCK: `PickCore` در برابرِ بردارِ مرجعِ `core/test-vectors/pick-v1.json` — همان که
 *  `server/test/pick-core.test.js` با `server/src/lib/pick-core.js` می‌خواند. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class PickCoreVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/pick-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/pick-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/pick-v1.json پیدا نشد")
  }

  private fun JsonElement.str() = jsonPrimitive.content
  private fun JsonElement.longOrNull(): Long? = if (this is JsonNull) null else jsonPrimitive.long
  private fun cases(k: String) = vec.getValue(k).jsonArray.map { it.jsonObject }

  @Test
  fun versionIsOne() = assertEquals(1, vec.getValue("version").jsonPrimitive.int)

  @Test
  fun lineTotal() {
    val cs = cases("lineTotal")
    assertTrue(cs.size >= 5)
    for (c in cs) {
      assertEquals(c.getValue("want").jsonPrimitive.long,
        PickCore.lineTotalMinor(c.getValue("qtyMilli").jsonPrimitive.long, c.getValue("unitPriceMinor").jsonPrimitive.long), c.toString())
    }
  }

  @Test
  fun pickOrderByShelf() {
    for (c in cases("pickOrder")) {
      val lines = c.getValue("lines").jsonArray.map { it.jsonObject }.map {
        PickCore.SheetLine(it.getValue("lineNo").jsonPrimitive.int, it.getValue("name").str(),
          it.getValue("aisle").str(), it.getValue("shelf").str(), it.getValue("level").str())
      }
      val want = c.getValue("want").jsonArray.map { it.jsonPrimitive.int }
      assertEquals(want, PickCore.pickOrder(lines), c.getValue("name").str())
      assertEquals(want, PickCore.pickOrder(lines.reversed()), "پایدار: ${c.getValue("name").str()}")
    }
  }

  @Test
  fun barcodeMatches() {
    for (c in cases("barcode")) {
      val codes = c.getValue("barcodes").jsonArray.map { it.str() }
      assertEquals(c.getValue("want").jsonPrimitive.boolean, PickCore.barcodeMatches(codes, c.getValue("scanned").str()), c.toString())
    }
  }

  @Test
  fun weighedUnitAndTolerance() {
    for (c in cases("weighedUnit")) {
      assertEquals(c.getValue("want").jsonPrimitive.boolean, PickCore.isWeighedUnit(c.getValue("unit").str()), c.toString())
    }
    for (c in cases("weigh")) {
      val v = PickCore.weighVerdict(c.getValue("orderedMilli").jsonPrimitive.long, c.getValue("actualMilli").jsonPrimitive.long,
        c.getValue("toleranceBp").jsonPrimitive.int)
      assertEquals(c.getValue("want").str(), v.key, c.toString())
    }
  }

  @Test
  fun settleAfterPicking() {
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
      assertEquals(w.getValue("pending").jsonPrimitive.int, got.pending, name)
    }
  }

  @Test
  fun metricsWithoutMadeUpNumbers() {
    for (c in cases("metrics")) {
      val runs = c.getValue("picks").jsonArray.map { it.jsonObject }.map {
        PickCore.Run(it.getValue("startedAt").longOrNull(), it.getValue("packedAt").longOrNull(),
          it.getValue("lines").jsonArray.map { l -> l.jsonObject.getValue("status").str() })
      }
      val m = PickCore.metrics(runs)
      val w = c.getValue("want").jsonObject
      val name = c.getValue("name").str()
      assertEquals(w.getValue("orders").jsonPrimitive.int, m.orders, name)
      assertEquals(w.getValue("lines").jsonPrimitive.int, m.lines, name)
      assertEquals(w.getValue("found").jsonPrimitive.int, m.found, name)
      assertEquals(w.getValue("notFound").jsonPrimitive.int, m.notFound, name)
      assertEquals(w.getValue("short").jsonPrimitive.int, m.short, name)
      assertEquals(w.getValue("substituted").jsonPrimitive.int, m.substituted, name)
      assertEquals(w.getValue("avgPickMs").longOrNull(), m.avgPickMs, name)
      assertEquals(w.getValue("medianPickMs").longOrNull(), m.medianPickMs, name)
      assertEquals(w.getValue("foundRateBp").longOrNull(), m.foundRateBp, name)
      assertEquals(w.getValue("substitutionRateBp").longOrNull(), m.substitutionRateBp, name)
    }
  }
}
