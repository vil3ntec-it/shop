package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.risk.CashierAlerts
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  RSK-10 — هشدارِ رفتارِ غیرعادیِ صندوقدار: `core/risk/CashierAlerts` (commonMain) همان
 *  `core/test-vectors/cashier-alerts-v1.json` را می‌گذراند که `cashierAlerts` ِ `license/report-board.js`،
 *  و نشانِ خانهٔ `ReportBoard` فقط با هشدار و فقط برای صاحب/مدیر است.
 */
class CashierAlertsVectorsTest {
  private val vectors: JsonObject by lazy {
    Json.parseToJsonElement(File(ReportCoreVectorsTest.vectorFile().parentFile, "cashier-alerts-v1.json").readText()).jsonObject
  }
  private val ledgers: JsonObject by lazy {
    Json.parseToJsonElement(ReportCoreVectorsTest.vectorFile().readText()).jsonObject.getValue("ledgers").jsonObject
  }

  private fun encode(a: CashierAlerts.Alert): JsonElement = JsonObject(linkedMapOf(
    "key" to JsonPrimitive(a.key), "kind" to JsonPrimitive(a.kind.id), "count" to JsonPrimitive(a.count),
    "invoices" to JsonPrimitive(a.invoices), "peerCount" to JsonPrimitive(a.peerCount),
    "peerInvoices" to JsonPrimitive(a.peerInvoices), "ratioPct" to JsonPrimitive(a.ratioPct),
  ))

  private fun row(key: String, invoices: Int, voids: Int, discounts: Int, noSales: Int) = ReportCore.FraudRow(
    key = key, invoices = invoices, gross = 0, discountCount = discounts, discount = 0, discountBp = null,
    returnCount = 0, returns = 0, voidCount = voids, voids = 0, lineDeleteCount = 0, lineDeletes = 0,
    noSaleCount = noSales, overrideCount = 0, approvalCount = 0,
  )

  @Test
  fun `همهٔ بردارهای هشدار عیناً برابرِ JS`() {
    assertEquals(1, vectors.getValue("version").jsonPrimitive.int)
    val cases = vectors.getValue("cases").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 15)
    val failures = cases.mapNotNull { c ->
      val rows = c.getValue("rows").jsonArray.map { r ->
        val x = r.jsonArray
        CashierAlerts.Counts(x[0].jsonPrimitive.content, x[1].jsonPrimitive.long, x[2].jsonPrimitive.long, x[3].jsonPrimitive.long, x[4].jsonPrimitive.long)
      }
      val p = c["policy"] as? JsonObject
      val policy = CashierAlerts.Policy(
        p?.get("minCount")?.jsonPrimitive?.long ?: CashierAlerts.Policy().minCount,
        p?.get("factorPct")?.jsonPrimitive?.long ?: CashierAlerts.Policy().factorPct,
      )
      val got = JsonArray(CashierAlerts.alerts(rows, policy).map(::encode))
      val want = c.getValue("expect")
      if (got == want) null else "${c.getValue("name").jsonPrimitive.content}\n  want: $want\n  got:  $got"
    }
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }

  @Test
  fun `نشانِ خانه فقط با هشدار، برای صاحب و مدیر، و کلیک به ضدتقلبِ امروز`() {
    val l = ReportLedger.parse(ledgers.getValue("kabul").jsonObject)
    val date = "2026-10-14"
    val t = ReportCore.today(l, date)
    val debts = ReportCore.debts(l, date)
    val quiet = ReportBoard.home(t, debts, ReportCore.fraud(l, date, date))
    assertTrue("دفترِ مرجع هشداری ندارد", quiet.pills.none { it.id == "cashier" })
    val busy = ReportCore.FraudReport(date, date, listOf(row("علی", 40, 5, 0, 4), row("سارا", 160, 0, 0, 0)), row("", 200, 5, 0, 4))
    assertEquals(2, ReportBoard.alertsOf(busy).size)
    val home = ReportBoard.home(t, debts, busy)
    assertEquals(
      ReportBoard.Pill("cashier", "2 هشدارِ رفتارِ غیرعادیِ صندوقدار امروز", "danger", ReportBoard.Preset("fraud", "today", "")),
      home.pills.last(),
    )
    assertTrue("شاگرد زبانهٔ ضدتقلب ندارد", ReportBoard.home(t, debts, busy, money = false).pills.none { it.id == "cashier" })
  }
}
