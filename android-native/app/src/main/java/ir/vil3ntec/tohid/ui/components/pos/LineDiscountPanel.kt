package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.ui.screens.NumberField
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  POS-20 — «تخفیفِ ردیف» (درصد یا مبلغ) در کادرِ ردیفِ سبد، و پیامِ ردِ کادر (`LinePolicy.edit`).
 *  [show] خاموش ⇒ فقط پیامِ رد (اگر هست)؛ پرچمِ خاموش ⇒ هیچ‌کدام.
 */
@Composable
fun LineDiscountPanel(
  show: Boolean,
  percent: Boolean,
  text: String,
  problem: String?,
  onPercent: (Boolean) -> Unit,
  onText: (String) -> Unit,
) {
  if (show) {
    Spacer(Modifier.height(12.dp))
    SegmentedChoice(
      options = listOf("pct" to StrLine.percent, "amt" to StrLine.amount),
      selected = if (percent) "pct" else "amt",
      onSelect = { onPercent(it == "pct") },
    )
    Spacer(Modifier.height(6.dp))
    NumberField(
      value = text,
      onValueChange = onText,
      label = StrLine.lineDiscount + if (percent) " (٪)" else " (؋)",
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(StrLine.lineDiscountNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
  }
  problem?.let {
    Spacer(Modifier.height(8.dp))
    Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
  }
}
