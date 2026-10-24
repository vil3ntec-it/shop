package ir.vil3ntec.tohid.ui.v3.settings.sections

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.Watchman
import ir.vil3ntec.tohid.normalizeDigits
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/general/alerts`: هشدارهای نگهبان (`Watchman`) — کالای تمام‌شده، قرضِ
 *  بالاتر از مرز، پایانِ اشتراک، حتی وقتی برنامه بسته است. مرزِ قرض همان
 *  `Watchman.setDebtLimit` است؛ صفر یعنی هشدارِ قرض خاموش.
 */
@Composable
internal fun RouteScope.AlertsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var text by remember {
    val v = Watchman.debtLimit(context)
    mutableStateOf(if (v > 0) v.toLong().toString() else "")
  }
  SettingsPageFrame(title = SettingsLayout.labelOf("هشدارها"), onBack = onBack) {
    item(key = "about") { V3Message(V3Tone.INFO, StrSettings.alertsTitle, Modifier.padding(top = 8.dp), body = StrSettings.alertsNote) }
    item(key = "limit") {
      SettingsBlock {
        V3TextField(
          value = text,
          onValueChange = { entered ->
            val digits = normalizeDigits(entered).filter { it in '0'..'9' }
            text = digits
            Watchman.setDebtLimit(context, digits.toDoubleOrNull() ?: 0.0)
          },
          label = StrSettings.debtAlertLabel,
          kind = V3FieldKind.MONEY,
          helper = StrSettings.zeroOff,
          imeAction = ImeAction.Done,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
    if (!Platform.DESKTOP) {
      item(key = "system") {
        V3Section(null) {
          V3ListRow(
            StrSettings.phoneNotifications,
            subtitle = StrSettings.phoneNotificationsNote,
            icon = Icons.Outlined.NotificationsActive,
            tile = false,
            onClick = {
              runCatching {
                context.startActivity(
                  Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
              }
            },
          )
        }
      }
    }
  }
}
