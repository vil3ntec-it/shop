package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
 *  RPT-08 — بردارهای مرجعِ نقشهٔ حرارتی (`core/test-vectors/sales-heatmap-v1.json`) روی `SalesHeatmap`؛ همان پرونده را
 *  `server/test/report-heatmap-vectors.test.js` روی دوقلوی وب (`license/sales-heatmap-core.js`) می‌دواند — هر دو یک عدد.
 *  `cells` و `levels` فقط خانه‌های ناصفرند. فقط JVM (پرونده‌خوان)؛ ریشهٔ مخزن با بالا رفتن از cwd.
 */
class SalesHeatmapVectorsTest {

  private val vectors: JsonObject by lazy {
    val rel = "core/test-vectors/sales-heatmap-v1.json"
    val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, rel).isFile }
      ?: error("$rel پیدا نشد (cwd=${System.getProperty("user.dir")})")
    Json.parseToJsonElement(File(root, rel).readText()).jsonObject
  }

  private fun JsonObject.int(k: String): Int = getValue(k).jsonPrimitive.int
  private fun longs(e: kotlinx.serialization.json.JsonElement): List<Long> = e.jsonArray.map { it.jsonPrimitive.long }
  private fun table(e: kotlinx.serialization.json.JsonElement): List<List<Long>> = e.jsonArray.map { longs(it) }
  private fun pair(c: HeatCell): List<Long> = listOf(c.count.toLong(), c.totalMinor)

  @Test fun formatAndWeekdays() {
    assertEquals("sales-heatmap-v1", vectors.getValue("format").jsonPrimitive.content)
    for (w in vectors.getValue("weekdayOf").jsonArray.map { it.jsonObject }) {
      assertEquals(w.int("expect"), SalesHeatmap.weekdayOf(w.getValue("epochDay").jsonPrimitive.long), "epochDay ${w.getValue("epochDay")}")
    }
  }

  @Test fun everyCaseMatchesExpect() {
    val cases = vectors.getValue("cases").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 20, "cases: ${cases.size}")
    for (c in cases) {
      val name = c.getValue("name").jsonPrimitive.content
      val sales = c.getValue("sales").jsonArray.map { it.jsonObject }.map {
        HeatSale(it.getValue("atMs").jsonPrimitive.long, it.getValue("amountMinor").jsonPrimitive.long, it.getValue("isSale").jsonPrimitive.boolean)
      }
      val m = SalesHeatmap.build(sales, c.int("offsetMinutes"), c.int("dayStartMinutes"), c.int("weekStart"))
      val levels = c.int("levels")
      val e = c.getValue("expect").jsonObject
      assertEquals(e.getValue("weekdays").jsonArray.map { it.jsonPrimitive.int }, m.weekdays, "$name: weekdays")
      val cells = ArrayList<List<Long>>()
      val lv = ArrayList<List<Long>>()
      for (r in 0 until SalesHeatmap.DAYS) for (h in 0 until SalesHeatmap.HOURS) {
        val x = m.cell(r, h)
        if (x.count != 0 || x.totalMinor != 0L) cells += listOf(r.toLong(), h.toLong(), x.count.toLong(), x.totalMinor)
        val l = m.level(r, h, levels)
        if (l != 0) lv += listOf(r.toLong(), h.toLong(), l.toLong())
      }
      assertEquals(table(e.getValue("cells")), cells, "$name: cells")
      assertEquals(table(e.getValue("levels")), lv, "$name: levels")
      assertEquals(e.int("maxCount"), m.maxCount, "$name: maxCount")
      val peak = e.getValue("peak")
      assertEquals(if (peak is JsonNull) null else (peak as JsonArray).let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int }, m.peak, "$name: peak")
      assertEquals(table(e.getValue("rowTotals")), m.rowTotals.map(::pair), "$name: rowTotals")
      assertEquals(table(e.getValue("hourTotals")), m.hourTotals.map(::pair), "$name: hourTotals")
      assertEquals(longs(e.getValue("total")), pair(m.total), "$name: total")
    }
  }
}
