package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import kotlinx.coroutines.launch

private const val TEAM = "team"
private const val AUTH = "auth"

/**
 *  `settings/security/staff-code`: کدِ ثابتِ دکان روی **سرور** (`standingCode`) —
 *  همان که «کارمندان دکان» نشان می‌دهد. ساختِ کدِ تازه روی سرور (`rotateStandingCode`)
 *  با تأیید. بیرون از حساب یا بی‌نت همان پیامِ امروز با راهِ بعدی.
 */
@Composable
internal fun RouteScope.StaffCodeScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  val signedIn = remember { SyncStore(context).accessToken != null }
  val shops = remember { Backend.shop(context) }
  var code by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableIntStateOf(0) }
  var busy by remember { mutableStateOf(false) }
  var confirm by remember { mutableStateOf(false) }
  LaunchedEffect(signedIn, reload) {
    code = if (!signedIn || !Backend.isReady(context)) "" else { code = null; shops.standingCode() }
  }
  if (confirm) {
    V3ConfirmSheet(
      title = StrSettings.rotateQ,
      message = StrSettings.rotateWarn,
      confirm = StrSettings.make,
      icon = Icons.Outlined.Key,
      onConfirm = {
        busy = true
        scope.launch {
          shops.rotateStandingCode()
            .onSuccess { code = it.code.ifBlank { shops.standingCode() }; toasts.show(StrSettings.newCodeMade) }
            .onFailure { toasts.show(it.userMessage) }
          busy = false
        }
      },
      onDismiss = { confirm = false },
    )
  }

  SettingsPageFrame(title = SettingsLayout.labelOf("کد شاگرد"), onBack = onBack) {
    val now = code
    when {
      !signedIn -> item(key = "signin") {
        V3EmptyState(Icons.Outlined.Key, StrSettings.staffCodeSignInTitle, body = StrSettings.staffCodeNeedsAccount, action = Str.signIn, onAction = { nav.open(AUTH) })
      }
      now == null -> item(key = "loading") { V3Skeleton(rows = 1, modifier = Modifier.padding(top = 16.dp)) }
      now.isBlank() -> item(key = "offline") {
        V3EmptyState(Icons.Outlined.CloudOff, StrSettings.staffCodeOfflineTitle, body = StrSettings.staffCodeOffline, action = Str.again, onAction = { reload++ })
      }
      else -> {
        item(key = "code") {
          SettingsBlock {
            Text(StrSettings.staffCodeRow, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              //  کد لاتین است: در صفحهٔ راست‌به‌چپ دسته‌ها وارونه خوانده می‌شدند
              CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(now, style = V3Type.figure.copy(fontFamily = FontFamily.Monospace, fontSize = 28.sp), color = PhoneV3.colors.ink, modifier = Modifier.weight(1f))
              }
              Spacer(Modifier.width(8.dp))
              V3IconButton(Icons.Outlined.ContentCopy, StrSettings.copyCode) {
                clipboard.setText(AnnotatedString(now))
                Haptics.confirm(context)
                toasts.show(StrSettings.staffCodeCopied)
              }
            }
          }
          SettingsNote(StrSettings.staffCodeNote)
        }
        item(key = "rotate") {
          V3Button(
            if (busy) StrSettings.creating else StrSettings.newCode,
            { if (!busy) confirm = true },
            Modifier.fillMaxWidth().padding(top = 16.dp),
            kind = V3ButtonKind.SECONDARY,
            loading = busy,
          )
        }
      }
    }
    item(key = "team") {
      V3Section(null) { V3ListRow(Str.staff, icon = Icons.Outlined.Badge, tile = false, onClick = { nav.open(TEAM) }) }
    }
  }
}
