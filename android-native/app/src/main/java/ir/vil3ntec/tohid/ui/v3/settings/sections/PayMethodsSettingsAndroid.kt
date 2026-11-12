package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.PayMethodsPref
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote

// i18n: سه‌زبانه

/**
 *  PAY-06 — بخشِ «روش‌های پرداختِ این دکان» در `settings/sales/payments` (فقط با «ترکیبی و شیفت»، چون پنلِ روش‌ها همان‌جاست):
 *  برای هر روشِ [PayFlow.toggleableMethods] خاموش/روشن (`PayMethodsPref.set`، تنظیمِ دکان؛ پیش‌فرض همه روشن = همان پنلِ امروز).
 *  بخشِ تنظیمات فقط صاحب و مدیر (`Need.MANAGE`).
 */
@Composable
internal fun PayMethodsSettings() {
  val context = LocalContext.current
  var off by remember { mutableStateOf(PayMethodsPref.off(context)) }
  SettingsBlock { PartTitle(StrShell.payMethodsTitle) }
  SettingsNote(StrShell.payMethodsNote)
  PayFlow.toggleableMethods().forEach { m ->
    SettingsBlock {
      PartTitle(PayFlow.methodName(m.id))
      V3Segmented(
        options = listOf(StrShell.switchOff, StrShell.switchOn),
        selected = if (m.id in off) 0 else 1,
        onSelect = {
          PayMethodsPref.set(context, m.id, on = it == 1)
          off = PayMethodsPref.off(context)
        },
      )
    }
  }
}
