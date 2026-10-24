package ir.vil3ntec.tohid.ui.v3.account.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.data.BackupMirror
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SheetCloser
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountSession
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** آن‌چه برگهٔ خروج در هر مرحله نشان می‌دهد */
internal data class SignOutUi(
  val stage: SignOutModel.Stage,
  val pending: Int,
  val online: Boolean,
  val lastError: String = "",
  val backupPath: String = "",
  val busy: Boolean = false,
)

/** کارهای برگهٔ خروج */
internal class SignOutActions(
  val signOut: () -> Unit,
  val syncNow: () -> Unit,
  val force: () -> Unit,
  val forceConfirm: () -> Unit,
  val toConfirm: () -> Unit,
)

/**
 *  برگهٔ خروج — یک `V3Sheet` که بلندی‌اش با هر مرحله نرم عوض می‌شود (نه پنجره‌های
 *  روی هم): A تأیید، B صفِ پُر (زنده: صف که خالی شد «همه‌چیز به سرور رسید»)،
 *  C نوشتنِ پشتیبان و پرسشِ دوم.
 */
@Composable
internal fun SignOutContent(ui: SignOutUi, actions: SignOutActions, onDismiss: () -> Unit) {
  V3Sheet(onDismiss = { if (!ui.busy) onDismiss() }) { closer ->
    AnimatedContent(
      targetState = ui.stage,
      modifier = Modifier.fillMaxWidth().animateContentSize(MotionSpec.sheet()),
      transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) },
      label = "signOutStage",
    ) { stage ->
      Column(Modifier.fillMaxWidth()) {
        when (stage) {
          SignOutModel.Stage.CONFIRM -> Step(
            Icons.AutoMirrored.Outlined.Logout, V3Tone.ERROR, StrAccount.signOutTitle, StrAccount.signOutBody,
          ) { Buttons(closer, StrAccount.signOutAction, V3ButtonKind.DANGER, ui.busy, actions.signOut) }
          SignOutModel.Stage.BLOCKED -> if (ui.pending > 0) {
            Step(Icons.Outlined.CloudUpload, V3Tone.WARNING, StrAccount.pendingTitle, StrAccount.pendingBody(ui.pending, ui.online), ui.lastError) {
              V3Button(StrAccount.syncNow, actions.syncNow, Modifier.fillMaxWidth(), haptic = true)
              Spacer(Modifier.height(10.dp))
              Buttons(closer, StrAccount.forceSignOut, V3ButtonKind.DANGER, ui.busy, actions.force)
            }
          } else {
            Step(Icons.Outlined.CloudUpload, V3Tone.SUCCESS, StrAccount.allSentTitle, StrAccount.allSentBody) {
              Buttons(closer, StrAccount.signOutAction, V3ButtonKind.DANGER, ui.busy, actions.toConfirm)
            }
          }
          SignOutModel.Stage.WRITING -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(color = PhoneV3Roles.focus(PhoneV3.colors), strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text(StrAccount.writingBackup, style = V3Type.headline, color = PhoneV3.colors.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
          }
          SignOutModel.Stage.FORCE -> Step(
            Icons.Outlined.SaveAlt, V3Tone.ERROR, StrAccount.forceTitle, StrAccount.forceBody(ui.pending, ui.backupPath),
          ) { Buttons(closer, StrAccount.signOutAnyway, V3ButtonKind.DANGER, ui.busy, actions.forceConfirm) }
        }
      }
    }
  }
}

/** سرِ هر مرحله: دایرهٔ آیکن به رنگِ گونه، عنوان، توضیح و خطای اختیاری؛ دکمه‌ها زیرش */
@Composable
private fun Step(icon: ImageVector, tone: V3Tone, title: String, body: String, error: String = "", buttons: @Composable () -> Unit) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.tone(c, tone)
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(56.dp).background(pair.bg, CircleShape), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = PhoneV3Roles.toneIcon(c, tone), modifier = Modifier.size(26.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(title, style = V3Type.title, color = c.ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(body, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center)
    if (error.isNotBlank()) {
      Spacer(Modifier.height(8.dp))
      Text(error, style = V3Type.footnote, color = PhoneV3Roles.errorText(c), textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    buttons()
  }
}

/** «ماندن» (خنثی، برگه را با حرکت می‌بندد) کنارِ کارِ اصلیِ مرحله */
@Composable
private fun Buttons(closer: V3SheetCloser, action: String, kind: V3ButtonKind, busy: Boolean, onAction: () -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    V3Button(StrAccount.stay, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, enabled = !busy)
    V3Button(action, onAction, Modifier.weight(1f), kind = kind, loading = busy, haptic = true)
  }
}

/**
 *  مسیرِ `profile/signout`: صفِ واقعیِ Sync v1 (`SyncV1Engine.status`) و راهِ قدیم
 *  (`AutoSync.pendingCount`) زنده خوانده می‌شوند؛ خروجِ واقعی = باطل کردنِ نشست روی
 *  سرور (اگر شد) و پاک کردنِ توکن (`SyncStore.signOut`).
 */
@Composable
internal fun RouteScope.SignOutRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val v1 by SyncV1Engine.of(context).status.collectAsState()
  val pending = SignOutModel.pending(v1.queued, AutoSync.pendingCount)
  var stage by remember { mutableStateOf(SignOutModel.initial(v1.queued, AutoSync.pendingCount)) }
  var backupPath by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  fun signOutNow() {
    busy = true
    scope.launch {
      runCatching { Backend.auth(context).signOut() }
      SyncStore(context).signOut()
      AccountSession.changed()
      busy = false
      toasts.show(StrAccount.signedOut, V3Tone.SUCCESS)
      back()
    }
  }

  SignOutContent(
    ui = SignOutUi(stage, pending, remember(v1) { Backend.isOnline(context) }, v1.lastError, backupPath, busy),
    actions = SignOutActions(
      signOut = { SignOutModel.onConfirm(v1.queued, AutoSync.pendingCount)?.let { stage = it } ?: signOutNow() },
      syncNow = {
        runCatching { SyncV1Worker.now(context) }
          .onSuccess { toasts.show(StrAccount.syncStarted, V3Tone.INFO) }
          .onFailure { toasts.show(it.userText(StrAccount.syncFailed), V3Tone.ERROR) }
      },
      force = {
        stage = SignOutModel.Stage.WRITING
        scope.launch {
          withContext(Dispatchers.IO) { BackupMirror.write(context) }
            .onSuccess { backupPath = it.absolutePath; stage = SignOutModel.Stage.FORCE }
            .onFailure { toasts.show(it.userText(StrAccount.backupFailed), V3Tone.ERROR); stage = SignOutModel.Stage.BLOCKED }
        }
      },
      forceConfirm = { if (SignOutModel.canForce(backupWritten = backupPath.isNotBlank())) signOutNow() },
      toConfirm = { stage = SignOutModel.Stage.CONFIRM },
    ),
    onDismiss = { back() },
  )
}
