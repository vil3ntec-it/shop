package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.atrest.AtRestKeyring
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSecurity
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.AtRestKeys
import ir.vil3ntec.tohid.ui.screens.PLAIN_IN_PHASE_1
import ir.vil3ntec.tohid.ui.screens.REBUILD
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.enableWarning
import ir.vil3ntec.tohid.ui.screens.unreadableText
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  `settings/security/encryption`: رمزگذاریِ دفتر روی همین دستگاه (SEC-01، فاز ۱) — همان کارِ `SecuritySection` ِ
 *  رابطِ امروز رویِ کیت: روشن کردن پس از پرسش (`V3ConfirmSheet`، همان `enableWarning`)، خاموش کردن همان دم؛ هر دو
 *  همان لحظه فایل‌ها را کوچ می‌دهند (`AtRest.setWanted` + `ShopStore.convergeAtRest`) و نتیجه را راست می‌گویند.
 *  دفترِ رمزدارِ خوانده‌نشده: «بازسازی از سرور» و «دوباره امتحان» همین‌جاست (پنجرهٔ «دفتر باز نشد» به همین‌جا می‌فرستد).
 */
@Composable
internal fun RouteScope.EncryptionScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val ledger = store
  val unreadable by ledger.unreadable.collectAsState()

  var status by remember { mutableStateOf<AtRest.Status?>(null) }
  var busy by remember { mutableStateOf(false) }
  //  هر بار که `tick` جلو برود، وضعیت از روی دیسک دوباره خوانده می‌شود (فقط سرآیندها)
  var tick by remember { mutableIntStateOf(0) }
  LaunchedEffect(tick) {
    status = withContext(Dispatchers.IO) { runCatching { AtRest.status(context) }.getOrNull() }
  }
  var askOn by remember { mutableStateOf(false) }
  var askRebuild by remember { mutableStateOf(false) }

  /** نوشتنِ تنظیم و کوچِ همان لحظه */
  fun setEncryption(want: Boolean) {
    busy = true
    scope.launch {
      val saved = withContext(Dispatchers.IO) { AtRest.setWanted(context, want) }
      val r = if (saved) ledger.convergeAtRest() else null
      busy = false
      tick++
      toasts.show(
        when {
          r == null -> StrSecurity.settingNotSaved
          !r.ok -> StrSecurity.filesNotConverted(r.failed.size)
          want -> StrSecurity.encTurnedOn(r.converted)
          else -> StrSecurity.encTurnedOff(r.converted)
        },
      )
    }
  }

  if (askOn) {
    V3ConfirmSheet(
      title = StrSecurity.askOnTitle,
      message = enableWarning(),
      confirm = StrSecurity.turnOn,
      onConfirm = { askOn = false; setEncryption(true) },
      onDismiss = { askOn = false },
      icon = Icons.Outlined.Shield,
      danger = false,
    )
  }
  if (askRebuild) {
    V3ConfirmSheet(
      title = StrSecurity.rebuildAsk(REBUILD),
      message = StrSecurity.rebuildBody,
      confirm = REBUILD,
      onConfirm = {
        askRebuild = false
        busy = true
        scope.launch {
          val r = ledger.rebuildFromServer()
          busy = false
          tick++
          toasts.show(r.message)
        }
      },
      onDismiss = { askRebuild = false },
      icon = Icons.Outlined.CloudDownload,
    )
  }

  val s = status
  val on = s?.wanted ?: false
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.ENCRYPTION),
    subtitle = if (on) StrSecurity.encSubtitleOn else StrSecurity.encSubtitleOff,
    onBack = onBack,
  ) {
    item(key = "what") {
      SettingsNote(StrSecurity.whatIsEncrypted)
      SettingsNote(PLAIN_IN_PHASE_1)
    }
    item(key = "toggle") {
      V3Section(null, Modifier.padding(top = 16.dp)) {
        V3ToggleRow(
          title = StrSecurity.encOnThisDevice,
          checked = on,
          //  روشن کردن پس از پرسش (بازبینیِ #331)؛ خاموش کردن همان لحظه — راهِ برگشت است
          onCheckedChange = { want -> if (want) askOn = true else setEncryption(false) },
          subtitle = when {
            busy -> StrSecurity.converting
            s == null -> "…"
            else -> StrSecurity.filesState(s.sealed, s.plain)
          },
          icon = Icons.Outlined.Shield,
          enabled = !busy && s != null,
        )
      }
    }
    //  روشن است ولی کلید ساخته/باز نشد ⇒ دفتر ساده نوشته می‌شود؛ راستش گفته می‌شود
    val keyLine = if (s != null && s.wanted) {
      when (val k = s.key) {
        is AtRestKeyring.State.Ready -> null
        is AtRestKeyring.State.Unavailable -> StrSecurity.keyUnavailable(k.reason)
        is AtRestKeyring.State.Lost -> StrSecurity.keyLost(k.reason)
        AtRestKeyring.State.None -> StrSecurity.keyNone
      } ?: s.sealError?.let { StrSecurity.lastSaveNotSealed(it) }
    } else null
    if (keyLine != null) {
      item(key = "key") { V3Message(V3Tone.WARNING, keyLine, Modifier.padding(top = 16.dp)) }
    }
    item(key = "note") {
      SettingsNote(AtRestKeys.note() + " " + StrSecurity.backupsNotSealed)
      //  فایل‌های کنارگذاشته — هیچ‌وقت خودکار پاک نمی‌شوند؛ فقط گفته می‌شود که هستند
      val left = s?.leftovers.orEmpty()
      if (left.isNotEmpty()) {
        SettingsNote(
          StrSecurity.leftovers(
            left.size,
            left.take(4).joinToString(Str.listSep + " ") + if (left.size > 4) Str.listSep + " …" else "",
          ),
        )
      }
    }
    //  دفترِ رمزدارِ خوانده‌نشده: نوشتن بسته است؛ راهِ برگشت همین‌جا
    val broken = unreadable
    if (broken != null) {
      item(key = "unreadable") {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
          V3Message(V3Tone.ERROR, StrSecurity.unreadableTitle, body = unreadableText(broken))
          Spacer(Modifier.height(12.dp))
          V3Button(REBUILD, { askRebuild = true }, Modifier.fillMaxWidth(), enabled = !busy, loading = busy, haptic = true)
          Spacer(Modifier.height(10.dp))
          V3Button(
            StrSecurity.retry,
            {
              busy = true
              scope.launch {
                ledger.loadAndSummarize()
                busy = false
                tick++
                toasts.show(if (ledger.unreadable.value == null) StrSecurity.ledgerOpened else StrSecurity.stillNotOpened)
              }
            },
            Modifier.fillMaxWidth(),
            kind = V3ButtonKind.SECONDARY,
            enabled = !busy,
          )
        }
      }
    }
  }
}
