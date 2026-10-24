package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.ui.UpdateBanner
import ir.vil3ntec.tohid.ui.screens.SubscriptionBanner
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav

/**
 *  نوارِ پیامِ پوسته (اشتراک، نسخهٔ تازه) — جای `notice` ِ `V3Scaffold` در گوشی و زیرِ
 *  سربرگِ کامپیوتر. **مالِ G10**: این فقط جای‌نگهدارِ پایه است (همان دو نوارِ امروز)
 *  تا AppRoot از حالا یک جا صدا بزند؛ G10 محتوایش را می‌سازد.
 */
@Composable
fun ShellNotice() {
  val nav = LocalV3Nav.current
  SubscriptionBanner { nav?.open("subscription") }
  UpdateBanner()
}
