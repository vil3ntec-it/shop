package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.returns.ReturnReasons
import ir.vil3ntec.tohid.data.ReturnSettings
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/sales/returns`: برگشت و تعویضِ تازه (RET) — همان `ReturnSettings` و همان رفتارِ
 *  `ReturnSettingsSection` ِ رابطِ امروز: کلیدِ روشن/خاموش (پیش‌فرض خاموش = مرجوعیِ امروز)، و با روشن،
 *  سقفِ بی تأیید (خالی = بی سقف) و فهرستِ دلیل‌ها (RET-08؛ متنِ کادر همان نوشتهٔ کاربر می‌ماند، ذخیره پاک‌شده است).
 *  صفحهٔ تنظیمات برای شاگرد باز نمی‌شود (`Need.MANAGE`).
 */
@Composable
internal fun RouteScope.ReturnsSettingsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var on by remember { mutableStateOf(ReturnSettings.enabled(context)) }
  var limit by remember { mutableStateOf(ReturnSettings.limit(context).let { if (it > 0) plainAfghani(it) else "" }) }
  var reasons by remember { mutableStateOf(ReturnReasons.format(ReturnSettings.reasons(context))) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.RETURNS),
    subtitle = if (on) StrSettings.returnsOn else StrSettings.returnsOff,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock {
        V3Segmented(
          options = listOf(StrShell.switchOff, StrShell.switchOn),
          selected = if (on) 1 else 0,
          onSelect = {
            on = it == 1
            ReturnSettings.setEnabled(context, on)
          },
        )
      }
      SettingsNote(StrSettings.returnsNote)
    }
    if (on) {
      item(key = "limit") {
        SettingsBlock {
          V3TextField(
            value = limit,
            onValueChange = { v ->
              limit = v
              ReturnSettings.setLimit(context, v.toDoubleOrNull() ?: 0.0)
            },
            label = StrSettings.returnLimitLabel,
            modifier = Modifier.fillMaxWidth(),
            kind = V3FieldKind.MONEY,
            imeAction = ImeAction.Done,
          )
        }
        SettingsNote(StrSettings.returnLimitNote)
      }
      item(key = "reasons") {
        SettingsBlock {
          V3TextField(
            value = reasons,
            onValueChange = { v ->
              reasons = v
              ReturnSettings.setReasons(context, v)
            },
            label = StrSettings.returnReasonsLabel,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
          )
        }
        SettingsNote(StrSettings.returnReasonsNote(ReturnReasons.MAX_COUNT, ReturnReasons.MAX_LENGTH))
      }
    }
  }
}

/** مبلغِ افغانی برای کادرِ پول، بی جداکننده و بی صفرِ بی‌جا: 1500.0 ⇒ «1500»، 12.5 ⇒ «12.5» */
internal fun plainAfghani(afghani: Double): String =
  if (afghani == Math.floor(afghani) && !afghani.isInfinite()) afghani.toLong().toString() else afghani.toString()
