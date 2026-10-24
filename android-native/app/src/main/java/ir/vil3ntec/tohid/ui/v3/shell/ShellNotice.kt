package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav

/**
 *  نوارِ پیامِ پوسته — جای `notice` ِ `V3Scaffold` در گوشی و زیرِ سربرگِ کامپیوتر (AppRoot
 *  همین را صدا می‌زند): اشتراک (`SubscriptionNotice`) و نسخهٔ تازه (`UpdateNotice`)، هر دو در
 *  `ShellNotices.kt`، زیرِ هم و نه روی هم (ممیزیِ ناوبری S7). خانهٔ گوشی اشتراک را در کارت‌های
 *  «هشدارها و پیام‌ها» دارد؛ کامپیوتر نسخهٔ تازه را در تراشهٔ سربرگ (`UpdateChip`).
 */
@Composable
fun ShellNotice() {
  val nav = LocalV3Nav.current
  val home = nav?.current?.let(RoutePaths::base) == ShellPaths.HOME
  val desk = LocalDeskLayout.current
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (desk || !home) SubscriptionNotice()
    if (!desk) UpdateNotice()
  }
}
