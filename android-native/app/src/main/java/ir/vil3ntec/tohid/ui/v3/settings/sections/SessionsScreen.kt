package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.model.DeviceDto
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.displayMillis
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import kotlinx.coroutines.launch

/**
 *  `settings/security/sessions`: دستگاه‌هایی که با این حساب وارد شده‌اند (روی سرور).
 *  «بستنِ نشست» با کشیدنِ ردیف یا دکمهٔ پایانِ ردیف، پس از تأیید؛ نشستِ همین
 *  دستگاه بسته نمی‌شود (کاربر خودش را بیرون می‌کرد).
 */
@Composable
internal fun RouteScope.SessionsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = remember { SyncStore(context) }
  val signedIn = remember { state.accessToken != null }
  val account = remember { Backend.account(context) }
  var devices by remember { mutableStateOf<List<DeviceDto>?>(null) }
  var failed by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableIntStateOf(0) }
  var closing by remember { mutableStateOf<DeviceDto?>(null) }
  //  نشستِ بسته‌شده با جمع شدنِ نرمِ ردیف می‌رود (`V3Collapse`)
  var closed by remember { mutableStateOf(emptySet<String>()) }
  LaunchedEffect(signedIn, reload) {
    if (!signedIn) return@LaunchedEffect
    failed = null
    account.devices().onSuccess { devices = it }.onFailure { failed = it.userMessage; devices = emptyList() }
  }
  closing?.let { d ->
    V3ConfirmSheet(
      title = StrSettings.closeSessionQ(d.label),
      message = StrSettings.closeSessionWarn,
      confirm = StrSettings.closeSessionAction,
      icon = Icons.AutoMirrored.Outlined.Logout,
      onConfirm = {
        scope.launch {
          account.revokeDevice(d.id)
            .onSuccess { closed = closed + d.id; toasts.show(StrSettings.sessionClosed) }
            .onFailure { toasts.show(it.userMessage) }
        }
      },
      onDismiss = { closing = null },
    )
  }

  SettingsPageFrame(title = SettingsLayout.labelOf("دستگاه‌های وارد شده"), onBack = onBack) {
    val list = devices
    when {
      !signedIn -> item(key = "out") { V3EmptyState(Icons.Outlined.Devices, StrSettings.sessionsSignedOut, body = StrSettings.sessionsSignedOutNote) }
      list == null -> item(key = "loading") { V3Skeleton(rows = 3, modifier = Modifier.padding(top = 16.dp)) }
      list.isEmpty() -> item(key = "empty") {
        V3EmptyState(Icons.Outlined.Devices, failed ?: StrSettings.noDevices, action = if (failed != null) Str.again else null, onAction = { reload++ })
      }
      else -> item(key = "list") {
        V3Section(null, footer = StrSettings.lostPhone) {
          list.forEachIndexed { i, d ->
            //  «همین دستگاه» از روی شناسهٔ دستگاه شناخته می‌شود، نه از روی نام
            val isThis = d.deviceUid.isNotBlank() && d.deviceUid == state.deviceUid
            V3Collapse(visible = d.id !in closed) { SessionRow(d, isThis, divider = i > 0) { closing = d } }
          }
        }
      }
    }
    item(key = "note") { SettingsNote(StrSettings.sessionsNote) }
  }
}

@Composable
private fun SessionRow(d: DeviceDto, isThis: Boolean, divider: Boolean, onClose: () -> Unit) {
  Column {
    if (divider) V3Divider(start = 64.dp)
    V3ListRow(
      d.label,
      subtitle = if (isThis) StrSettings.thisDevice else d.lastSeenAt?.let { StrSettings.lastSeen(displayMillis(it)) },
      icon = Icons.Outlined.PhoneAndroid,
      chevron = false,
      endActions = if (isThis) emptyList() else listOf(V3SwipeAction(Icons.AutoMirrored.Outlined.Logout, StrSettings.closeSessionAction, V3ButtonKind.DANGER, onClose)),
      trailing = if (isThis) null else ({ V3TextButton(StrSettings.closeSessionAction, onClose, danger = true) }),
    )
  }
}
