package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestKeyring
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.AtRestKeys
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSecurity

/** عنوانِ بخش — همان که فهرستِ بخش‌های تنظیماتِ کامپیوتر (`AppRoot`) نشان می‌دهد؛ به زبانِ صفحه */
val SECURITY_SECTION_TITLE: String get() = StrSecurity.security

/**
 *  تنظیمات ← امنیت: «رمزگذاریِ دفتر روی همین دستگاه» (SEC-01، فاز ۱).
 *
 *  یک ردیفِ کمینه، خودبسنده و بی‌وابستگی به چیدمانِ تنظیمات تا چیدمانِ تازهٔ
 *  تنظیمات هر جا خواست بگذاردش. پیش‌فرض **خاموش**؛ روشن/خاموش کردن همان لحظه
 *  فایل‌ها را کوچ می‌دهد (`ShopStore.convergeAtRest`) و نتیجه را راست می‌گوید.
 *
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrSecurity` (LOC-06)؛ `AtRestKeys.note()` و پیامِ بازسازی هنوز از لایهٔ داده‌اند.
 */
@Composable
fun SecuritySection(store: ShopStore, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val colors = Shop.colors
  val unreadable by store.unreadable.collectAsState()

  var status by remember { mutableStateOf<AtRest.Status?>(null) }
  var busy by remember { mutableStateOf(false) }
  //  هر بار که `tick` جلو برود، وضعیت از روی دیسک دوباره خوانده می‌شود (فقط سرآیندها)
  var tick by remember { mutableIntStateOf(0) }
  LaunchedEffect(tick) {
    status = withContext(Dispatchers.IO) { runCatching { AtRest.status(context) }.getOrNull() }
  }

  val on = status?.wanted ?: false
  var askOn by remember { mutableStateOf(false) }
  var askRebuild by remember { mutableStateOf(false) }

  /** نوشتنِ تنظیم و کوچِ همان لحظه */
  fun setEncryption(want: Boolean) {
    busy = true
    scope.launch {
      val saved = withContext(Dispatchers.IO) { AtRest.setWanted(context, want) }
      val r = if (saved) store.convergeAtRest() else null
      busy = false
      tick++
      toast(
        when {
          r == null -> StrSecurity.settingNotSaved
          !r.ok -> StrSecurity.filesNotConverted(r.failed.size)
          want -> StrSecurity.encTurnedOn(r.converted)
          else -> StrSecurity.encTurnedOff(r.converted)
        },
      )
    }
  }

  SettingsSection(
    icon = Icons.Filled.Security,
    title = SECURITY_SECTION_TITLE,
    subtitle = if (on) StrSecurity.encSubtitleOn else StrSecurity.encSubtitleOff,
    tint = if (unreadable != null) colors.danger else if (on) colors.success else colors.muted,
  ) {
    Text(
      StrSecurity.whatIsEncrypted,
      style = MaterialTheme.typography.bodySmall,
      color = colors.muted,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      PLAIN_IN_PHASE_1,
      style = MaterialTheme.typography.labelSmall,
      color = colors.muted,
    )
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(StrSecurity.encOnThisDevice, style = MaterialTheme.typography.labelLarge, color = colors.text)
        Text(
          when {
            busy -> StrSecurity.converting
            status == null -> "…"
            else -> StrSecurity.filesState(status!!.sealed, status!!.plain)
          },
          style = MaterialTheme.typography.labelSmall,
          color = colors.muted,
        )
      }
      Switch(
        checked = on,
        enabled = !busy && status != null,
        modifier = Modifier.semantics { contentDescription = StrSecurity.encOnThisDevice },
        //  روشن کردن پس از پرسشِ روشن (بازبینیِ #331)؛ خاموش کردن همان لحظه — راهِ برگشت است
        onCheckedChange = { want -> if (want) askOn = true else setEncryption(false) },
      )
    }

    //  روشن است ولی کلید ساخته/باز نشد ⇒ دفتر ساده نوشته می‌شود؛ راستش گفته می‌شود
    val s = status
    if (s != null && s.wanted) {
      val keyLine = when (val k = s.key) {
        is AtRestKeyring.State.Ready -> null
        is AtRestKeyring.State.Unavailable -> StrSecurity.keyUnavailable(k.reason)
        is AtRestKeyring.State.Lost -> StrSecurity.keyLost(k.reason)
        AtRestKeyring.State.None -> StrSecurity.keyNone
      } ?: s.sealError?.let { StrSecurity.lastSaveNotSealed(it) }
      if (keyLine != null) {
        Spacer(Modifier.height(8.dp))
        Text(keyLine, style = MaterialTheme.typography.labelSmall, color = colors.warning)
      }
    }

    Spacer(Modifier.height(8.dp))
    Text(
      AtRestKeys.note() + " " + StrSecurity.backupsNotSealed,
      style = MaterialTheme.typography.labelSmall,
      color = colors.muted,
    )

    //  فایل‌های کنارگذاشته — هیچ‌وقت خودکار پاک نمی‌شوند؛ فقط گفته می‌شود که هستند
    val left = s?.leftovers.orEmpty()
    if (left.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      Text(
        StrSecurity.leftovers(
          left.size,
          left.take(4).joinToString(Str.listSep + " ") + if (left.size > 4) Str.listSep + " …" else "",
        ),
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted,
      )
    }

    //  دفترِ رمزدارِ خوانده‌نشده: نوشتن بسته است؛ راهِ برگشت همین‌جا
    if (unreadable != null) {
      Spacer(Modifier.height(12.dp))
      Text(unreadableText(unreadable), style = MaterialTheme.typography.bodySmall, color = colors.danger)
      Spacer(Modifier.height(8.dp))
      TohidButton(
        text = REBUILD,
        enabled = !busy,
        busy = busy,
        onClick = { askRebuild = true },
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(8.dp))
      TohidSecondaryButton(
        text = StrSecurity.retry,
        enabled = !busy,
        onClick = {
          busy = true
          scope.launch {
            store.loadAndSummarize()
            busy = false
            tick++
            toast(if (store.unreadable.value == null) StrSecurity.ledgerOpened else StrSecurity.stillNotOpened)
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }

  if (askOn) {
    AlertDialog(
      onDismissRequest = { askOn = false },
      containerColor = colors.bg,
      title = { Text(StrSecurity.askOnTitle, color = colors.text) },
      text = { Text(enableWarning(), color = colors.muted) },
      confirmButton = {
        TextButton(onClick = { askOn = false; setEncryption(true) }) { Text(StrSecurity.turnOn, color = colors.primary) }
      },
      dismissButton = { TextButton(onClick = { askOn = false }) { Text(StrPos.no, color = colors.muted) } },
    )
  }

  if (askRebuild) {
    RebuildDialog(
      onDismiss = { askRebuild = false },
      onConfirm = {
        askRebuild = false
        busy = true
        scope.launch {
          val r = store.rebuildFromServer()
          busy = false
          tick++
          toast(r.message)
        }
      },
    )
  }
}

/** نامِ راهِ برگشت — همان در صفحهٔ امنیت و پنجرهٔ «دفتر باز نشد»؛ به زبانِ صفحه */
val REBUILD: String get() = StrSecurity.rebuild

/** چه چیزی در فاز ۱ ساده می‌ماند — بی تعارف (بازبینیِ #331؛ شرح سرِ `AtRestScope`) */
val PLAIN_IN_PHASE_1: String get() = StrSecurity.plainInPhase1

private fun unreadableText(kind: AtRestException.Kind?): String = when (kind) {
  AtRestException.Kind.KEY_UNAVAILABLE -> StrSecurity.unreadableKeyUnavailable(REBUILD)
  AtRestException.Kind.KEY_MISSING ->
    StrSecurity.unreadableKeyMissing(if (Platform.DESKTOP) StrSecurity.newComputerOf else StrSecurity.newPhoneOf, REBUILD)
  AtRestException.Kind.IO -> StrSecurity.unreadableIo(REBUILD)
  else -> StrSecurity.unreadableDamaged(REBUILD)
}

private fun enableWarning(): String = buildString {
  append(StrSecurity.keyOnlyHere(if (Platform.DESKTOP) StrSecurity.thisComputer else StrSecurity.thisPhone))
  append(AtRestKeys.note())
  append(StrSecurity.keyLostComesBack(REBUILD))
  if (Platform.DESKTOP) append(StrSecurity.desktopDowngradeWarn)
}

/** پرسشِ پیش از «بازسازی از سرور» */
@Composable
private fun RebuildDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
  val colors = Shop.colors
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.bg,
    title = { Text(StrSecurity.rebuildAsk(REBUILD), color = colors.text) },
    text = {
      Text(
        StrSecurity.rebuildBody,
        color = colors.muted,
      )
    },
    confirmButton = { TextButton(onClick = onConfirm) { Text(REBUILD, color = colors.primary) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(StrPos.no, color = colors.muted) } },
  )
}

/**
 *  پنجرهٔ «دفتر باز نشد» — `AppRoot` وقتی `ShopStore.unreadable` پر شد نشانش می‌دهد (به‌جای
 *  پیامِ تنها): راهِ برگشت همین‌جا، بی گشتن در تنظیمات.
 */
@Composable
fun UnreadableLedgerDialog(
  store: ShopStore,
  onOpenSecurity: () -> Unit,
  onDismiss: () -> Unit,
  toast: (String) -> Unit,
) {
  val colors = Shop.colors
  val scope = rememberCoroutineScope()
  val kind by store.unreadable.collectAsState()
  var busy by remember { mutableStateOf(false) }
  var ask by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    containerColor = colors.bg,
    title = { Text(StrSecurity.unreadableTitle, color = colors.text) },
    text = {
      Text(
        StrSecurity.recordingBlocked + unreadableText(kind),
        color = colors.muted,
      )
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = { ask = true }) {
        Text(if (busy) StrSecurity.rebuilding else REBUILD, color = colors.primary)
      }
    },
    dismissButton = {
      Row {
        TextButton(enabled = !busy, onClick = onOpenSecurity) { Text(SECURITY_SECTION_TITLE, color = colors.muted) }
        TextButton(enabled = !busy, onClick = onDismiss) { Text(StrSecurity.later, color = colors.muted) }
      }
    },
  )
  if (ask) {
    RebuildDialog(
      onDismiss = { ask = false },
      onConfirm = {
        ask = false
        busy = true
        scope.launch {
          val r = store.rebuildFromServer()
          busy = false
          toast(r.message)
          if (r.ok) onDismiss()
        }
      },
    )
  }
}
