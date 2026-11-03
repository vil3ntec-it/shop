package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.report.OnlineReport
import ir.vil3ntec.tohid.data.repo.OnlineReportSource
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
 *  RPT-14 — «گزارشِ فروشِ اینترنتی» در برنامهٔ مدیریت (پیش‌تر فقط API): راهش در «گزارش‌ها» فقط با پرچمِ روشنِ سرور،
 *  صفحه همان عددهای سرور را با بازهٔ انتخابی می‌خواند و ردِ سرور صفحه را «روشن نیست» می‌کند.
 */
class OnlineReportPageTest {

  private val view = OnlineReport.of(
    Json.parseToJsonElement(
      """{"enabled":true,"funnel":{"placed":2,"accepted":1,"packed":1,"handedOver":1,"completed":1},"completedTotalMinor":5000}""",
    ).jsonObject,
  )!!

  private class Fake(var next: ApiResult<OnlineReport.View>) : OnlineReportSource {
    val calls = mutableListOf<Pair<Int, Long>>()
    override suspend fun load(days: Int, nowMs: Long): ApiResult<OnlineReport.View> = next.also { calls += days to nowMs }
  }

  private val off = ApiResult.Failure(ApiFailure.Forbidden("off", OnlineReport.OFF))

  @Test
  fun `راهِ صفحه فقط با پاسخِ روشنِ سرور و بی‌اینترنت همان پیشین`() = runBlocking {
    val f = Fake(off)
    OnlineReportLive.probe(f, 9L)
    assertFalse("پرچمِ خاموش ⇒ پنهان", OnlineReportLive.visible)
    assertEquals(listOf(1 to 9L), f.calls)
    f.next = ApiResult.Success(view)
    OnlineReportLive.probe(f, 10L)
    assertTrue(OnlineReportLive.visible)
    f.next = ApiResult.Failure(ApiFailure.Offline())
    OnlineReportLive.probe(f, 11L)
    assertTrue("بی‌اینترنت پنهان نمی‌کند", OnlineReportLive.visible)
    f.next = off
    OnlineReportLive.probe(f, 12L)
    assertFalse(OnlineReportLive.visible)
  }

  @Test
  fun `صفحه با بازهٔ انتخابی می‌خواند و رد و بی‌اینترنت را جدا نشان می‌دهد`() = runBlocking {
    val f = Fake(ApiResult.Success(view))
    val s = OnlineReportState(f)
    assertEquals("پیش‌فرض ۷ روز", 7, s.days)
    s.load(100L)
    assertEquals(view, s.view)
    assertFalse(s.off || s.failed || s.busy)
    s.days = 92
    f.next = ApiResult.Failure(ApiFailure.Offline())
    s.load(200L)
    assertTrue(s.failed)
    assertEquals("آخرین نما می‌ماند", view, s.view)
    f.next = off
    s.load(300L)
    assertTrue(s.off)
    assertNull(s.view)
    assertEquals(listOf(7 to 100L, 92 to 200L, 92 to 300L), f.calls)
  }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `صفحه زیرِ گزارش‌ها ثبت است و راهش در گوشی و کامپیوتر پشتِ همان پرچم`() {
    val r = V3Modules.table.resolve(AnalyticsPaths.ONLINE_REPORT)
    assertNotNull(r)
    assertEquals(AnalyticsPaths.REPORTS, r!!.route.parent)
    val phone = src("ui/v3/analytics/ReportsScreen.kt")
    assertTrue(phone.contains("val onlineItem = if (online) listOf("))
    assertTrue("گزارشِ تازه هم راه دارد", phone.contains("overflow = onlineItem"))
    assertTrue(phone.contains(") + onlineItem,"))
    val desk = src("ui/v3/analytics/ReportsDesk.kt")
    assertTrue(desk.contains("val online = rememberOnlineReportVisible()"))
    assertTrue(desk.contains("if (online) {"))
    assertTrue(src("ui/v3/analytics/AnalyticsRoutes.kt").contains("ReportsDesk(data, snackbar) { nav.open(it) }"))
  }
}
