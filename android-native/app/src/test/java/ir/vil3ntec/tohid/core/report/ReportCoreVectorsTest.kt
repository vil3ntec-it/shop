package ir.vil3ntec.tohid.core.report

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  هستهٔ گزارشِ Kotlin روی همان آزمون‌های مرجعی که `license/report-core.js` می‌گذراند
 *  (`server/test/report-core.test.js`). هر مورد باید عیناً همان `expect` باشد.
 *  این آزمون هم در `android-native/app` و هم در `desktop/` اجرا می‌شود؛ فایل از بالا پیدا می‌شود.
 */
class ReportCoreVectorsTest {
  private val json = Json { encodeDefaults = true }

  private val vectors: JsonObject by lazy { Json.parseToJsonElement(vectorFile().readText()).jsonObject }

  private fun s(o: JsonObject, k: String): String? = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

  private fun clock(o: JsonObject): ReportClock? = (o["clock"] as? JsonObject)?.let {
    ReportClock.fixed(it.getValue("offsetMinutes").jsonPrimitive.int, it.getValue("dayStartMinute").jsonPrimitive.int)
  }

  private fun costing(o: JsonObject) = if (s(o, "costing") == "mavg") ReportCore.Costing.MAVG else ReportCore.Costing.LINE

  private fun today(l: ReportLedger, o: JsonObject) =
    ReportCore.today(l, s(o, "date"), clock(o), costing(o), (o["now"] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: 0L)

  private fun sales(l: ReportLedger, o: JsonObject) = ReportCore.sales(l, s(o, "from"), s(o, "to"), clock(o), costing(o))

  private fun debts(l: ReportLedger, o: JsonObject) =
    ReportCore.debts(l, s(o, "asOf")!!, (o["buckets"] as? JsonArray)?.map { it.jsonPrimitive.long } ?: emptyList(), clock(o))

  private fun fraud(l: ReportLedger, o: JsonObject) = ReportCore.fraud(l, s(o, "from"), s(o, "to"), clock(o))

  private fun run(c: JsonObject): JsonElement {
    val l = ReportLedger.parse(vectors.getValue("ledgers").jsonObject.getValue(c.getValue("ledger").jsonPrimitive.content).jsonObject)
    val o = c["opts"] as? JsonObject ?: JsonObject(emptyMap())
    return when (val fn = c.getValue("fn").jsonPrimitive.content) {
      "today" -> json.encodeToJsonElement(ReportCore.TodayReport.serializer(), today(l, o))
      "sales" -> json.encodeToJsonElement(ReportCore.SalesReport.serializer(), sales(l, o))
      "debts" -> json.encodeToJsonElement(ReportCore.DebtReport.serializer(), debts(l, o))
      "fraud" -> json.encodeToJsonElement(ReportCore.FraudReport.serializer(), fraud(l, o))
      "tables" -> {
        val tables = when (val of = c.getValue("of").jsonPrimitive.content) {
          "sales" -> ReportTables.sales(sales(l, o))
          "today" -> ReportTables.today(today(l, o))
          "debts" -> ReportTables.debts(debts(l, o))
          "fraud" -> ReportTables.fraud(fraud(l, o))
          else -> error("نوعِ جدولِ ناشناخته: $of")
        }
        JsonArray(tables.map { json.encodeToJsonElement(ReportTables.Plain.serializer(), ReportTables.plain(it)) })
      }
      else -> error("تابعِ ناشناخته: $fn")
    }
  }

  @Test
  fun `همهٔ آزمون‌های مرجعِ گزارش عیناً برابرِ JS`() {
    assertEquals(1, vectors.getValue("version").jsonPrimitive.int)
    val cases = vectors.getValue("cases").jsonArray
    assertTrue(cases.size >= 16)
    val failures = cases.map { it.jsonObject }.mapNotNull { c ->
      val got = run(c)
      val want = c.getValue("expect")
      if (got == want) null else "${c.getValue("name").jsonPrimitive.content}\n  want: $want\n  got:  $got"
    }
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }

  @Test
  fun `تخصیص و گرد کردن`() {
    assertEquals(listOf(982L, 524L, 44L), ReportCore.allocate(1550, listOf(22500, 12000, 1000)))
    assertEquals(listOf(-5L, 0L, -2L), ReportCore.allocate(-7, listOf(3, 0, 1)))
    assertEquals(listOf(5L, 0L), ReportCore.allocate(5, listOf(0, 0)))
    assertEquals(3882L, ReportCore.bp(17450, 44950))
    assertEquals(null, ReportCore.bp(1, 0))
    assertEquals(-2L, ReportCore.divRound(-5, 2)) // نیمه رو به +∞، مثلِ Math.round
    assertEquals("2026-10-13", ReportClock.fixed(270, 240).date(java.time.Instant.parse("2026-10-13T22:00:00Z").toEpochMilli()))
    assertEquals(2, ReportClock.fixed(270, 240).hour(java.time.Instant.parse("2026-10-13T22:00:00Z").toEpochMilli()))
    assertEquals("-15.50", ReportTables.cellText(kotlinx.serialization.json.JsonPrimitive(-1550L), "money"))
    assertEquals("5.5", ReportTables.cellText(kotlinx.serialization.json.JsonPrimitive(5500L), "qty"))
  }

  companion object {
    fun vectorFile(): File {
      var d: File? = File(".").absoluteFile
      while (d != null) {
        val f = File(d, "license/test-vectors/report-v1.json")
        if (f.isFile) return f
        d = d.parentFile
      }
      error("license/test-vectors/report-v1.json پیدا نشد (cwd=${File(".").absoluteFile})")
    }
  }
}
