package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.report.BranchesReport

/**
 *  RPT-18 — درِ «گزارشِ همهٔ شعبه‌ها» (`GET /api/shop/branches-report`، فقط‌خواندنی، فقط صاحب عدد می‌گیرد).
 *  جدا از صفحه تا حالِ صفحه بی سرور آزموده شود؛ خواندنِ پاسخ در `core/report/BranchesReport`.
 */
interface BranchesReportSource {
  /** گزارشِ [days] روزِ تا [todayIso]؛ پرچمِ خاموش ⇒ `Answer.Off`، نقشِ دیگر ⇒ `Answer.NotOwner` (هر دو ۲۰۰) */
  suspend fun load(days: Int, todayIso: String): ApiResult<BranchesReport.Answer>
}

class BranchesReportRepository(private val api: ApiClient) : BranchesReportSource {
  override suspend fun load(days: Int, todayIso: String): ApiResult<BranchesReport.Answer> = result {
    BranchesReport.of(api.get(BranchesReport.path(days, todayIso)))
  }
}
