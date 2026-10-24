package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 *  نوارِ پیامِ پوسته — جای `notice` ِ `V3Scaffold` در گوشی و زیرِ سربرگِ کامپیوتر (AppRoot
 *  همین را صدا می‌زند): اشتراک (`SubscriptionNotice`) و نسخهٔ تازه (`UpdateNotice`)، هر دو در
 *  `ShellNotices.kt`. خانه این نوار را ندارد؛ همان را کارتِ هشدار نشان می‌دهد.
 */
@Composable
fun ShellNotice() {
  //  دو نوار زیرِ هم، نه روی هم (ممیزیِ ناوبری S7)
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    SubscriptionNotice()
    UpdateNotice()
  }
}
