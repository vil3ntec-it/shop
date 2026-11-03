package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.report.OnlineReport

/**
 *  RPT-14 — درِ «گزارشِ فروشِ اینترنتی» (`GET /api/shop/orders/online-report`، فقط‌خواندنی، `reports.view`).
 *  جدا از صفحه تا حالِ صفحه بی سرور آزموده شود؛ خواندنِ پاسخ در `core/report/OnlineReport`.
 */
interface OnlineReportSource {
  /** گزارشِ [days] روزِ تا [nowMs]؛ پرچمِ خاموش ⇒ ۴۰۳ `online_report_disabled` (`ApiFailure.Forbidden`) */
  suspend fun load(days: Int, nowMs: Long): ApiResult<OnlineReport.View>
}

class OnlineReportRepository(private val api: ApiClient) : OnlineReportSource {
  override suspend fun load(days: Int, nowMs: Long): ApiResult<OnlineReport.View> = result {
    OnlineReport.of(api.get(OnlineReport.path(days, nowMs))) ?: throw ApiFailure.Forbidden("off", OnlineReport.OFF)
  }
}
