package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.guide.WhatsNew
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  OPS-05 — پنجرهٔ «چه چیزی تازه است»: یادداشت‌ها دسته‌شده به نسخه، برداشته‌شده اول و با «به‌جایش».
 *  فقط رابط، بی Context و بی نوشتن (`WhatsNewAndroid.kt` باز و بسته‌اش می‌کند).
 */
@Composable
internal fun WhatsNewDialog(groups: List<Pair<String, List<WhatsNew.Note>>>, onClose: () -> Unit) {
  val lang = I18n.source().code
  AlertDialog(
    onDismissRequest = onClose,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrSettings.whatsNew, color = Shop.colors.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (groups.isEmpty()) Text(StrSettings.whatsNewEmpty, color = Shop.colors.muted)
        groups.forEach { (version, notes) ->
          Text(StrSettings.whatsNewVersion(version), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
          Spacer(Modifier.height(4.dp))
          notes.forEach { n ->
            val (badge, tint) = when (n.kind) {
              WhatsNew.Kind.REMOVED -> StrSettings.whatsNewKindRemoved to Shop.colors.danger
              WhatsNew.Kind.CHANGED -> StrSettings.whatsNewKindChanged to Shop.colors.warning
              WhatsNew.Kind.NEW -> StrSettings.whatsNewKindNew to Shop.colors.success
            }
            Text(badge, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
            Text(n.text.of(lang), style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
            n.instead?.let { Text(StrSettings.whatsNewInstead(it.of(lang)), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted) }
            Spacer(Modifier.height(8.dp))
          }
          Spacer(Modifier.height(4.dp))
        }
      }
    },
    confirmButton = { TextButton(onClick = onClose) { Text(StrSettings.whatsNewGotIt) } },
  )
}
