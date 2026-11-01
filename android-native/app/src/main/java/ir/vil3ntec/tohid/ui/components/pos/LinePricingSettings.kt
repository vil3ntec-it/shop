package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.AmountField
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlin.math.roundToLong

/**
 *  بخشِ تنظیماتِ «قیمت و تخفیفِ ردیف» (POS-08، POS-10، POS-20، POS-21) — پیش‌فرض **خاموش**.
 *  خودش چیزی نمی‌خواند و نمی‌نویسد (بی `Context`): مقدارها و نوشتن از صدازننده (`LinePolicyPrefs`).
 *  «قاعدهٔ جهت» و سقفِ قلمِ آزاد فقط وقتی روشن است دیده می‌شوند.
 *
 *  @param capMinor سقفِ ذخیره‌شدهٔ قلمِ آزاد (`null` بی سقف)
 *  @param onCap نوشتهٔ درست ⇒ سقف (خالی ⇒ `null`)؛ نوشتهٔ نادرست ذخیره نمی‌شود
 */
@Composable
fun LinePricingSettings(
  on: Boolean,
  direction: LinePricing.Direction,
  capMinor: Long?,
  onOn: (Boolean) -> Unit,
  onDirection: (LinePricing.Direction) -> Unit,
  onCap: (Long?) -> Unit,
) {
  var capText by remember { mutableStateOf(capMinor?.let { qty(it / 100.0) }.orEmpty()) }
  SettingsSection(
    icon = Icons.Filled.LocalOffer,
    title = StrLine.section,
    subtitle = if (on) StrLine.sectionOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrLine.note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { onOn(it == "on") },
    )
    if (on) {
      Spacer(Modifier.height(12.dp))
      Text(StrLine.direction, style = MaterialTheme.typography.labelMedium, color = Shop.colors.text)
      Spacer(Modifier.height(4.dp))
      SegmentedChoice(
        options = LinePricing.Direction.entries.map { it.code to label(it) },
        selected = direction.code,
        onSelect = { onDirection(LinePricing.Direction.of(it)) },
      )
      Spacer(Modifier.height(12.dp))
      AmountField(
        value = capText,
        onValueChange = { text ->
          capText = text
          if (text.isBlank()) onCap(null)
          else parseNumber(text)?.takeIf { it >= 0 && it.isFinite() }?.let { onCap((it * 100).roundToLong()) }
        },
        label = StrLine.freeCap,
      )
      Text(StrLine.freeCapNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
  }
}

private fun label(d: LinePricing.Direction): String = when (d) {
  LinePricing.Direction.BOTH -> StrLine.dirBoth
  LinePricing.Direction.UP -> StrLine.dirUp
  LinePricing.Direction.DOWN -> StrLine.dirDown
  LinePricing.Direction.NONE -> StrLine.dirNone
}
