package ir.vil3ntec.tohid.core.report

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  تابلوی خانه (کارت‌ها و نشان‌ها) — همان `core/test-vectors/report-board-v1.json` که
 *  `license/report-board.js` می‌گذراند؛ دفترها از `report-v1.json`.
 */
class ReportBoardVectorsTest {
  private val json = Json { encodeDefaults = true }
  private val ledgers: JsonObject by lazy {
    Json.parseToJsonElement(ReportCoreVectorsTest.vectorFile().readText()).jsonObject.getValue("ledgers").jsonObject
  }
  private val board: JsonObject by lazy {
    Json.parseToJsonElement(File(ReportCoreVectorsTest.vectorFile().parentFile, "report-board-v1.json").readText()).jsonObject
  }

  private fun s(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.contentOrNull

  private fun run(c: JsonObject): JsonElement = when (val fn = c.getValue("fn").jsonPrimitive.content) {
    "build" -> {
      val o = c.getValue("opts").jsonObject
      val l = ReportLedger.parse(ledgers.getValue(c.getValue("ledger").jsonPrimitive.content).jsonObject)
      val clock = (o["clock"] as? JsonObject)?.let {
        ReportClock.fixed(it.getValue("offsetMinutes").jsonPrimitive.int, it.getValue("dayStartMinute").jsonPrimitive.int)
      }
      val home = ReportBoard.build(
        l, s(o, "date")!!, clock,
        if (s(o, "costing") == "mavg") ReportCore.Costing.MAVG else ReportCore.Costing.LINE,
        s(o, "costApproved") == "true", s(o, "money") != "false",
      )
      json.encodeToJsonElement(ReportBoard.Home.serializer(), home)
    }
    "pct" -> JsonArray(c.getValue("input").jsonArray.map { v ->
      ReportBoard.pct((v as? JsonPrimitive)?.longOrNull)?.let(::JsonPrimitive) ?: JsonNull
    })
    "range" -> JsonArray(c.getValue("today").jsonArray.map { t ->
      JsonArray(c.getValue("input").jsonArray.map { sp ->
        json.encodeToJsonElement(ReportBoard.Range.serializer(), ReportBoard.range(sp.jsonPrimitive.content, t.jsonPrimitive.content))
      })
    })
    "sortRows" -> {
      val rows = c.getValue("rows").jsonArray.map { r -> r.jsonArray.map { it.jsonPrimitive } }
      val out = ReportBoard.sortRows(rows, c.getValue("col").jsonPrimitive.int, c.getValue("desc").jsonPrimitive.boolean)
      JsonArray(out.map { JsonArray(it) })
    }
    else -> error("تابعِ ناشناخته: $fn")
  }

  @Test
  fun `همهٔ آزمون‌های مرجعِ تابلو عیناً برابرِ JS`() {
    assertEquals(1, board.getValue("version").jsonPrimitive.int)
    val cases = board.getValue("cases").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 12)
    val failures = cases.mapNotNull { c ->
      val got = run(c)
      val want = c.getValue("expect")
      if (got == want) null else "${c.getValue("name").jsonPrimitive.content}\n  want: $want\n  got:  $got"
    }
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }

  /** کارت = جمعِ جدولِ مقصد: کلیک روی کارت همان عدد را در فهرست نشان می‌دهد (§۱۹.۵ بند ۲) */
  @Test
  fun `عددِ هر کارت همان جمعِ فهرستی است که باز می‌کند`() {
    val l = ReportLedger.parse(ledgers.getValue("kabul").jsonObject)
    val date = "2026-10-14"
    val home = ReportBoard.build(l, date)
    fun kpi(id: String) = home.kpis.first { it.id == id }
    val r = ReportBoard.range(kpi("netSales").target.span, date)
    val sales = ReportCore.sales(l, r.from, r.to)
    assertEquals(kpi("netSales").value, sales.product.sumOf { it.netSales })
    assertEquals(kpi("invoices").value, sales.totals.invoices.toLong())
    assertEquals(kpi("grossProfit").value, sales.product.sumOf { it.grossProfit })
    assertEquals(kpi("receivable").value, ReportCore.debts(l, date).customers.rows.sumOf { it.balance })
    //  سود تا D-23 همیشه تقریبی است و این در عنوانِ کارت هم نوشته می‌شود
    assertTrue(kpi("grossProfit").approximate)
    assertTrue(kpi("grossProfit").title.contains("تقریبی"))
    assertEquals("warning", home.pills.first { it.id == "profit" }.tone)
    //  شاگرد: کارت و نشانِ سود نیست (پنهان، نه صفر)
    val staff = ReportBoard.build(l, date, money = false)
    assertTrue(staff.kpis.none { it.id == "grossProfit" })
    assertTrue(staff.pills.none { it.id == "profit" || it.id == "cost" })
  }

  @Test
  fun `دفترِ خالی هیچ عددِ ساختگی ندارد`() {
    val home = ReportBoard.build(ReportLedger.parse(ledgers.getValue("empty").jsonObject), "2026-10-14")
    assertTrue(home.kpis.all { it.value == 0L })
    assertTrue(home.pills.all { it.tone == "success" || it.tone == "warning" && it.id == "profit" })
    assertEquals(listOf("today", "sales", "profit", "debts", "fraud"), ReportBoard.TABS.map { it.id })
  }
}
