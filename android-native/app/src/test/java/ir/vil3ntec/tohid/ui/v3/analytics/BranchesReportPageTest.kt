package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.report.BranchesReport
import ir.vil3ntec.tohid.data.repo.BranchesReportSource
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RPT-18 — «گزارشِ همهٔ شعبه‌ها» در برنامهٔ مدیریت (پیش‌تر فقط API، #882): راهش در «گزارش‌ها» فقط با پرچمِ روشنِ سرور و
 *  فقط برای صاحب؛ صفحه همان عددهای سرور را با بازهٔ انتخابی می‌خواند؛ پرچمِ خاموش یا نقشِ دیگر هیچ عددی نشان نمی‌دهد.
 */
class BranchesReportPageTest {

  private val report = BranchesReport.of(
    Json.parseToJsonElement(
      """{"enabled":true,"owner":true,"shops":[{"shop_id":"S1","name":"مرکز","current":true,
        "summary":{"invoices":1,"netSales":5000}}],"totals":{"invoices":1,"netSales":5000},"skipped":0}""",
    ).jsonObject,
  )

  private class Fake(var next: ApiResult<BranchesReport.Answer>) : BranchesReportSource {
    val calls = mutableListOf<Pair<Int, String>>()
    override suspend fun load(days: Int, todayIso: String): ApiResult<BranchesReport.Answer> = next.also { calls += days to todayIso }
  }

  @Test
  fun `راهِ صفحه فقط با پاسخِ روشن و صاحب؛ بی‌اینترنت همان پیشین`() = runBlocking {
    val f = Fake(ApiResult.Success(BranchesReport.Answer.Off))
    BranchesReportLive.probe(f, "2026-10-31")
    assertFalse("پرچمِ خاموش (پیش‌فرض) ⇒ پنهان", BranchesReportLive.visible)
    assertEquals(listOf(1 to "2026-10-31"), f.calls)
    f.next = ApiResult.Success(report)
    BranchesReportLive.probe(f, "2026-11-01")
    assertTrue(BranchesReportLive.visible)
    f.next = ApiResult.Failure(ApiFailure.Offline())
    BranchesReportLive.probe(f, "2026-11-02")
    assertTrue("بی‌اینترنت پنهان نمی‌کند", BranchesReportLive.visible)
    f.next = ApiResult.Success(BranchesReport.Answer.NotOwner)
    BranchesReportLive.probe(f, "2026-11-03")
    assertFalse("مدیر/کارمند ⇒ پنهان", BranchesReportLive.visible)
    f.next = ApiResult.Success(report)
    BranchesReportLive.probe(f, "2026-11-04")
    f.next = ApiResult.Failure(ApiFailure.Forbidden("no", "forbidden"))
    BranchesReportLive.probe(f, "2026-11-05")
    assertFalse("ردِ سرور ⇒ پنهان", BranchesReportLive.visible)
  }

  @Test
  fun `صفحه با بازهٔ انتخابی می‌خواند و خطا آخرین گزارش را پاک نمی‌کند`() = runBlocking {
    val f = Fake(ApiResult.Success(report))
    val s = BranchesReportState(f)
    assertEquals("پیش‌فرض امروز", 1, s.days)
    s.load("2026-10-31")
    assertEquals(report, s.answer)
    assertFalse(s.failed || s.busy)
    s.days = 31
    f.next = ApiResult.Failure(ApiFailure.Offline())
    s.load("2026-10-31")
    assertTrue(s.failed)
    assertEquals("آخرین گزارش می‌ماند", report, s.answer)
    f.next = ApiResult.Failure(ApiFailure.NotFound("no", "not_found"))
    s.load("2026-10-31")
    assertEquals(BranchesReport.Answer.Off, s.answer)
    assertEquals(listOf(1 to "2026-10-31", 31 to "2026-10-31", 31 to "2026-10-31"), f.calls)
  }

  @Test
  fun `بازه همان قاعدهٔ سرور است`() {
    assertNull("امروز بی بازه", Regex("from=").find(BranchesReport.path(1, "2026-10-31")))
    assertEquals("/shop/branches-report?from=2026-10-25&to=2026-10-31", BranchesReport.path(7, "2026-10-31"))
  }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `صفحه زیرِ گزارش‌ها ثبت است و راهش در گوشی و کامپیوتر پشتِ همان پرچم`() {
    val r = V3Modules.table.resolve(AnalyticsPaths.BRANCHES_REPORT)
    assertNotNull(r)
    assertEquals(AnalyticsPaths.REPORTS, r!!.route.parent)
    val phone = src("ui/v3/analytics/ReportsScreen.kt")
    assertTrue(phone.contains("val branches = rememberBranchesReportVisible()"))
    assertTrue(phone.contains("val branchesItem = if (branches) listOf("))
    assertTrue("گزارشِ تازه هم راه دارد", phone.contains("overflow = onlineItem"))
    val desk = src("ui/v3/analytics/ReportsDesk.kt")
    assertTrue(desk.contains("val branches = rememberBranchesReportVisible()"))
    assertTrue(desk.contains("if (branches) {"))
  }
}
