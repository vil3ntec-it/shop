package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.StaffTimeHub
import ir.vil3ntec.tohid.ui.screens.rememberStaffTimeConfig
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  STF-07/08 «ساعتِ کار و پورسانت» (`admin/staff-time`) — همان کارتِ «مدیریت» ِ پیشین (`StaffTimeHub`: ورود و
 *  خروج، نوبت‌ها، کارکردِ هفته و ماه، نرخِ پورسانتِ هر فروشنده) با پرچمِ `shop.staff_time` ِ سرور؛ بازهٔ «این ماه».
 */
@Composable
internal fun StaffTimePage(d: ShopData) {
  val cfg = rememberStaffTimeConfig()
  val today = todayIso()
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    when {
      cfg == null -> Text(StrReach.staffTimeLoading, style = V3Type.callout, color = PhoneV3.colors.inkMuted)
      !cfg.enabled -> Text(StrReach.staffTimeOff, style = V3Type.callout, color = PhoneV3.colors.inkMuted)
      else -> StaffTimeHub(d, today.take(7) + "-01", today, cfg)
    }
  }
}
