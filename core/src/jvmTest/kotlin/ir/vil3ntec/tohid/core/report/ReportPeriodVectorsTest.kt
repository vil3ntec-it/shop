package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  RPT-13 — بردارهای مرجعِ «دورهٔ قبل / بعد» و «تغییر» (`core/test-vectors/report-period-v1.json`) روی `ReportPeriod`؛
 *  همان پرونده را `server/test/report-period-vectors.test.js` روی دوقلوی وب (`license/report-period-core.js`) می‌دواند —
 *  هر دو یک عدد. فقط JVM (پرونده‌خوان)؛ ریشهٔ مخزن با بالا رفتن از cwd.
 */
class ReportPeriodVectorsTest {

  private val vectors: JsonObject by lazy {
    val rel = "core/test-vectors/report-period-v1.json"
    val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, rel).isFile }
      ?: error("$rel پیدا نشد (cwd=${System.getProperty("user.dir")})")
    Json.parseToJsonElement(File(root, rel).readText()).jsonObject
  }

  private fun JsonObject.text(k: String): String = getValue(k).jsonPrimitive.content

  private fun unitOf(name: String): ReportPeriod.Unit = ReportPeriod.Unit.valueOf(name.uppercase())

  @Test fun formatAndBound() {
    assertEquals("report-period-v1", vectors.text("format"))
    assertEquals(ReportPeriod.MAX_DIFF, vectors.getValue("max_diff").jsonPrimitive.long)
  }

  @Test fun everyShiftMatchesExpect() {
    val cases = vectors.getValue("shift").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 25, "shift: ${cases.size}")
    for (c in cases) {
      val got = ReportPeriod.shift(unitOf(c.text("unit")), c.text("from"), c.text("to"), c.getValue("steps").jsonPrimitive.int, c.text("today"))
      val e = c.getValue("expect")
      val want = if (e is JsonNull) null else e.jsonObject.let { ReportPeriod.Range(it.text("from"), it.text("to")) }
      assertEquals(want, got, c.text("name"))
    }
  }

  @Test fun everyChangeMatchesExpect() {
    val cases = vectors.getValue("change").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 15, "change: ${cases.size}")
    for (c in cases) {
      val got = ReportPeriod.change(c.getValue("current").jsonPrimitive.long, c.getValue("previous").jsonPrimitive.long)
      val e = c.getValue("expect").jsonObject
      assertEquals(ReportPeriod.Change(e.getValue("diff").jsonPrimitive.long, e.getValue("bp").jsonPrimitive.longOrNull), got, c.text("name"))
    }
  }
}
