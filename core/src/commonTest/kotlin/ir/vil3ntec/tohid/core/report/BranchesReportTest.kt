package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  RPT-18 — صفحهٔ «گزارشِ همهٔ شعبه‌ها» همان عددهای سرور را می‌خواند. نمونه همان پاسخِ قفل‌شدهٔ
 *  `server/test/branches-report.test.js` است (مرکز: ۲ فاکتور، ۱۴۰۰؛ کارته: ۱ فاکتور، ۳۰۰؛ یک شعبهٔ پرچم‌خاموش).
 */
class BranchesReportTest {

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val sample = json(
    """{"enabled":true,"owner":true,"from":"2026-10-31","to":"2026-10-31",
      "shops":[
        {"shop_id":"S1","name":"شعبهٔ مرکز","current":true,"timezone":"Asia/Kabul","from":"2026-10-31","to":"2026-10-31",
         "summary":{"invoices":2,"sales":140000,"discount":0,"returnCount":0,"returns":0,"netSales":140000,"cashNet":90000,"otherNet":50000,
           "payments":[{"method":"cash","invoices":1,"netSales":90000},{"method":"credit","invoices":1,"netSales":50000}],
           "topProducts":[{"name":"چای","netSales":90000},{"name":"  ","netSales":1}],"lowStock":3,"partial":false}},
        {"shop_id":"S2","name":"شعبهٔ کارته","current":false,"timezone":"Asia/Kabul","from":"2026-10-31","to":"2026-10-31",
         "summary":{"invoices":1,"sales":30000,"discount":0,"returnCount":0,"returns":0,"netSales":30000,"cashNet":0,"otherNet":30000,
           "payments":[{"method":"card","netSales":30000}],"topProducts":[],"lowStock":null,"partial":false}}],
      "totals":{"shops":2,"partial":false,"invoices":3,"sales":170000,"discount":0,"returnCount":0,"returns":0,"netSales":170000,"cashNet":90000,"otherNet":80000},
      "skipped":1,"truncated":false}""",
  )

  @Test
  fun readsEachBranchAndTheTotalsAsTheServerSentThem() {
    val v = assertIs<BranchesReport.Answer.Report>(BranchesReport.of(sample)).view
    assertEquals(listOf("S1" to true, "S2" to false), v.branches.map { it.shopId to it.current })
    assertEquals("شعبهٔ مرکز", v.branches[0].name)
    val m = v.branches[0].summary
    assertEquals(listOf(2L, 140_000L, 0L, 0L, 0L, 140_000L, 90_000L, 50_000L), BranchesReport.SUM_KEYS.map { m[it] })
    assertEquals(listOf(BranchesReport.Pay("cash", 90_000), BranchesReport.Pay("credit", 50_000)), m.payments)
    assertEquals(listOf(BranchesReport.Top("چای", 90_000)), m.top, "نامِ خالی کنار می‌رود")
    assertEquals(3L, m.lowStock)
    assertNull(v.branches[1].summary.lowStock, "کم‌موجودِ نیامده null است، نه صفر")
    assertEquals(170_000L, v.totals["netSales"])
    assertEquals(80_000L, v.totals["otherNet"])
    assertEquals(1, v.skipped)
    assertEquals(false, v.truncated)
    assertEquals("2026-10-31" to "2026-10-31", v.from to v.to)
  }

  @Test
  fun flagOffAndNonOwnerShowNoNumbers() {
    assertEquals(BranchesReport.Answer.Off, BranchesReport.of(json("""{"enabled":false}""")))
    assertEquals(BranchesReport.Answer.NotOwner, BranchesReport.of(json("""{"enabled":true,"owner":false}""")))
    //  شکلِ بد هرگز عددِ ساختگی نمی‌سازد
    assertEquals(BranchesReport.Answer.Off, BranchesReport.of(json("""{"enabled":"true","owner":true,"shops":[]}""")))
    assertEquals(BranchesReport.Answer.Off, BranchesReport.of(json("""{"enabled":true,"owner":true}""")))
    assertEquals(BranchesReport.Answer.NotOwner, BranchesReport.of(json("""{"enabled":true,"owner":"yes","shops":[]}""")))
  }

  @Test
  fun missingNumbersStayNullAndBadRowsAreDropped() {
    val v = assertIs<BranchesReport.Answer.Report>(
      BranchesReport.of(
        json(
          """{"enabled":true,"owner":true,"shops":[{"name":"بی شناسه"},{"shop_id":"S9","summary":{"invoices":"2","netSales":1.5}}],
            "totals":{}}""",
        ),
      ),
    ).view
    assertEquals(listOf("S9"), v.branches.map { it.shopId })
    assertNull(v.branches[0].summary["invoices"], "رشته عدد نیست")
    assertNull(v.branches[0].summary["netSales"], "اعشار پول نیست")
    assertTrue(v.branches[0].summary.empty)
    assertNull(v.from)
    assertEquals(0, v.skipped)
  }

  @Test
  fun pathUsesTheServerRangeRules() {
    assertEquals("/shop/branches-report", BranchesReport.path(1, "2026-10-31"))
    assertEquals("/shop/branches-report?from=2026-10-25&to=2026-10-31", BranchesReport.path(7, "2026-10-31"))
    assertEquals("/shop/branches-report?from=2026-10-01&to=2026-10-31", BranchesReport.path(31, "2026-10-31"))
    assertEquals(BranchesReport.path(31, "2026-10-31"), BranchesReport.path(400, "2026-10-31"), "بیش از سقف به ۳۱ روز می‌چسبد")
    assertEquals("/shop/branches-report", BranchesReport.path(0, "2026-10-31"))
    assertEquals("/shop/branches-report", BranchesReport.path(7, "نه-تاریخ"), "تاریخِ خراب ⇒ امروزِ هر شعبه")
  }

  @Test
  fun shareIsOfTheServerTotal() {
    val v = (BranchesReport.of(sample) as BranchesReport.Answer.Report).view
    assertEquals(8235L, BranchesReport.shareBp(v.branches[0], v.totals))
    assertEquals(1765L, BranchesReport.shareBp(v.branches[1], v.totals))
    assertNull(BranchesReport.shareBp(v.branches[0], BranchesReport.Summary(mapOf("netSales" to 0L))))
  }
}
