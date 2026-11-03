package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  صندوقِ کامپیوتر: چرا «ثبت» (F9) خاموش است — نسیه بی مشتریِ موجود و فعال (`CreditDebtor.tillBlock`) — و میان‌برِ
 *  «انتخابِ مشتری (F2)». `message == null` ⇒ هیچ چیز.
 */
@Composable
fun CreditDebtorHint(message: String?, onPick: () -> Unit) {
  if (message == null) return
  Column(Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
    Text(message, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
    TextButton(onClick = onPick) { Text(StrTillPay.pickCustomerKey) }
  }
}
