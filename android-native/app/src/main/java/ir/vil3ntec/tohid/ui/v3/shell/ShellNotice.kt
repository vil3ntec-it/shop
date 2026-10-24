package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable

/**
 *  نوارِ پیامِ پوسته — جای `notice` ِ `V3Scaffold` در گوشی و زیرِ سربرگِ کامپیوتر (AppRoot
 *  همین را صدا می‌زند): اشتراک (`SubscriptionNotice`) و نسخهٔ تازه (`UpdateNotice`)، هر دو در
 *  `ShellNotices.kt`. خانه این نوار را ندارد؛ همان را کارتِ هشدار نشان می‌دهد.
 */
@Composable
fun ShellNotice() {
  SubscriptionNotice()
  UpdateNotice()
}
