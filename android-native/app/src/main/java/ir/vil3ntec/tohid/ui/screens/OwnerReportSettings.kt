package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.report.OwnerReportPlan
import ir.vil3ntec.tohid.data.repo.result
import kotlinx.serialization.json.JsonObject

/** عنوانِ بخش — همان کلیدِ `SettingsLayout` */
const val OWNER_REPORT_TITLE: String = "گزارشِ خودکار برای صاحب" // داده: کلیدِ بخشِ تنظیمات (همان SettingsLayout)

/**
 *  درِ سرورِ گزارشِ خودکار (`/api/shop/owner-report`، PAY-20/RPT-12) و آخرین «دیده شود؟» برای فهرستِ کامپیوتر.
 *  تا پاسخِ نخست و با پرچمِ خاموش یا نقشِ دیگر `visible = false` ⇒ نه ردیفی، نه بخشی (همان رابطِ امروز).
 */
object OwnerReportLive {
  const val ROOT = "/shop/owner-report"

  var visible by mutableStateOf(false)
    private set

  suspend fun load(api: ApiClient): ApiResult<OwnerReportPlan.View> = result {
    OwnerReportPlan.parse(api.get(ROOT)).also { visible = it.enabled && it.owner }
  }

  /** فقط صاحبِ دکان (سرور می‌سنجد: ۴۰۳ `owner_only`)؛ نسخهٔ کهنه ⇒ ۴۰۹ `version_conflict` */
  suspend fun set(api: ApiClient, next: OwnerReportPlan.Settings, expectedVersion: Long): ApiResult<OwnerReportPlan.Settings> = result {
    OwnerReportPlan.parseSettings(api.put(ROOT, OwnerReportPlan.body(next, expectedVersion))["settings"] as? JsonObject)
  }
}

/** ردیفِ فهرستِ تنظیماتِ کامپیوتر: فقط برای صاحبِ واردشده و پرچمِ روشنِ سرور (`api == null` ⇒ نه). */
@Composable
fun ownerReportRow(api: ApiClient?): Boolean {
  LaunchedEffect(api) { if (api != null) OwnerReportLive.load(api) }
  return api != null && OwnerReportLive.visible
}
