package ir.vil3ntec.tohid.ui.v3

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** برگشتِ سیستم وقتی جست‌وجوی بخش باز است ⇒ بستنِ همان (`rememberV3SearchToggle`) — مرزِ پلتفرم جدا از منطقِ کیت */
@Composable
internal fun V3SearchBack(enabled: Boolean, onBack: () -> Unit) {
  BackHandler(enabled = enabled, onBack = onBack)
}
