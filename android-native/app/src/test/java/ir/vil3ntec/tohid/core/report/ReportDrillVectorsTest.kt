package ir.vil3ntec.tohid.core.report

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  RPT-13 — «ریز» ِ ردیفِ گزارش (`ReportCore.invoicesOf`/`dayInvoices`) روی همان بردارهایی که دوقلوی وب
 *  (`license/report-core.js`، `server/test/report-drill-vectors.test.js`) می‌گذراند: `core/test-vectors/report-drill-v1.json`
 *  با دفترهای `report-v1.json`. هر مورد باید عیناً همان `expect` باشد.
 */
class ReportDrillVectorsTest {

  private fun file(rel: String): File {
    var d: File? = File(".").absoluteFile
    while (d != null) {
      val f = File(d, rel)
      if (f.isFile) return f
      d = d.parentFile
    }
    error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")
  }

  private val ledgers: JsonObject by lazy {
    Json.parseToJsonElement(file("core/test-vectors/report-v1.json").readText()).jsonObject.getValue("ledgers").jsonObject
  }
  private val vectors: JsonObject by lazy { Json.parseToJsonElement(file("core/test-vectors/report-drill-v1.json").readText()).jsonObject }

  private fun s(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.contentOrNull

  private fun clock(o: JsonObject): ReportClock? = (o["clock"] as? JsonObject)?.let {
    ReportClock.fixed(it.getValue("offsetMinutes").jsonPrimitive.int, it.getValue("dayStartMinute").jsonPrimitive.int)
  }

  private fun line(x: ReportCore.InvoiceShare): List<Any> = listOf(x.saleId, x.date, x.createdAt, x.qty, x.sales)

  @Test
  fun `ریزِ هر ردیف عیناً برابرِ JS`() {
    assertEquals("report-drill-v1", vectors.getValue("format").jsonPrimitive.content)
    val cases = vectors.getValue("cases").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 100)
    val failures = cases.mapNotNull { c ->
      val l = ReportLedger.parse(ledgers.getValue(c.getValue("ledger").jsonPrimitive.content).jsonObject)
      val o = c.getValue("opts").jsonObject
      val got = when (val fn = c.getValue("fn").jsonPrimitive.content) {
        "invoicesOf" -> ReportCore.invoicesOf(l, s(o, "from"), s(o, "to"), s(o, "dim").orEmpty(), s(o, "key").orEmpty(), clock(o), s(o, "lineSeller") == "true")
        "dayInvoices" -> ReportCore.dayInvoices(l, s(o, "date").orEmpty(), clock(o))
        else -> error("تابعِ ناشناخته: $fn")
      }.map(::line)
      val want = c.getValue("expect").jsonArray.map { it.jsonObject }.map {
        listOf<Any>(
          it.getValue("saleId").jsonPrimitive.content, it.getValue("date").jsonPrimitive.content,
          it.getValue("createdAt").jsonPrimitive.long, it.getValue("qty").jsonPrimitive.long, it.getValue("sales").jsonPrimitive.long,
        )
      }
      if (got == want) null else "${c.getValue("name").jsonPrimitive.content}\n  want: $want\n  got:  $got"
    }
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }
}
