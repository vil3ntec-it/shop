package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** برگشتِ سیستم در «سفارشِ دستی» (جزئیات ⇐ کالا، کالا ⇐ «دور ریخته شود؟») — مرزِ پلتفرم جدا از صفحه (راهِ آیفون) */
@Composable
internal fun ManualOrderBack(enabled: Boolean = true, onBack: () -> Unit) {
  BackHandler(enabled = enabled, onBack = onBack)
}
