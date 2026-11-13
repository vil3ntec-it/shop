package ir.vil3ntec.tohid.core.branch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  شعبه، فازِ ۲: صفحهٔ گزارشِ هر شعبه و «جمعِ همه» از `GET /api/shop/branches/report` (`server/src/lib/branch-report.js`).
 *  ⛔ هیچ عددی در برنامه حساب نمی‌شود؛ «جمع» فقط وقتی هست که سرور داده (فقط صاحب).
 */
class BranchTallyTest {

  private val b1 = "brn_00112233445566778899aabb"

  private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

  @Test
  fun ownerGetsEachBranchAndTotals() {
    val v = BranchTally.of(
      json(
        """{"enabled":true,"from":"2026-11-01","to":"2026-11-07","timezone":"Asia/Kabul","branches":[
          {"id":"","name":"مرکز","main":true,"status":"active","summary":{"invoices":2,"netSales":7000,"cashNet":7000,"otherNet":0,
            "payments":[{"method":"cash","netSales":7000}],"topProducts":[{"name":"برنج","netSales":7000}],"partial":false}},
          {"id":"$b1","name":"بازار","main":false,"status":"archived","summary":{"invoices":1,"netSales":3000}}],
          "totals":{"branches":2,"partial":false,"invoices":3,"netSales":10000}}""",
      ),
    )
    assertNotNull(v)
    assertEquals("2026-11-01", v.from)
    assertEquals(listOf("", b1), v.rows.map { it.id })
    assertTrue(v.rows[1].archived)
    assertEquals(7000L, v.rows[0].summary["netSales"])
    assertEquals("برنج", v.rows[0].summary.top.single().name)
    assertEquals(10000L, v.totals!!["netSales"])
  }

  @Test
  fun staffGetsNoTotalsAndMissingNumbersStayMissing() {
    val v = BranchTally.of(json("""{"enabled":true,"branches":[{"id":"$b1","name":"بازار","summary":{"invoices":1}}]}"""))
    assertNotNull(v)
    assertNull(v.totals)
    assertNull(v.rows.single().summary["netSales"], "عددِ نیامده «—»، نه صفر")
  }

  @Test
  fun offIsNull() {
    assertNull(BranchTally.of(json("""{"enabled":false}""")))
    assertNull(BranchTally.of(json("""{}""")))
  }

  @Test
  fun pathFollowsTheServerRange() {
    assertEquals("/shop/branches/report", BranchTally.path(1, "2026-11-07"))
    assertEquals("/shop/branches/report?from=2026-11-01&to=2026-11-07", BranchTally.path(7, "2026-11-07"))
    assertEquals("/shop/branches/report?from=2026-10-08&to=2026-11-07", BranchTally.path(99, "2026-11-07"))
  }
}
