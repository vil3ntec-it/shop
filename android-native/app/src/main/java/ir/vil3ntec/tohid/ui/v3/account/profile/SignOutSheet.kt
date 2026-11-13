package ir.vil3ntec.tohid.ui.v3.account.profile

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.core.sync.SignOutSafety
import ir.vil3ntec.tohid.data.SafeSignOut
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SheetCloser
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountSession
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/** آن‌چه برگهٔ خروج در هر مرحله نشان می‌دهد */
internal data class SignOutUi(
  val stage: SignOutModel.Stage,
  val pending: Int,
  val online: Boolean,
  val lastError: String = "",
  val backupPath: String = "",
  val busy: Boolean = false,
  /** چرا خروج نگه داشته شد (`SafeSignOut.check`) — `NONE` یعنی فقط صفِ زنده */
  val reason: SignOutSafety.Reason = SignOutSafety.Reason.NONE,
  /** AUTH-06: «خروج از همهٔ دستگاه‌ها» — همان مراحل، با تأییدِ سرور پیش از خروجِ همین دستگاه */
  val everywhere: Boolean = false,
  /** ردِ `POST /auth/logout-all` (پیامِ سرور) — زیرِ همان تأیید */
  val serverError: String = "",
)

/** کارهای برگهٔ خروج */
internal class SignOutActions(
  val signOut: () -> Unit,
  /** «دوباره بکوش» — همه را دوباره می‌فرستد و می‌سنجد */
  val syncNow: () -> Unit,
  val force: () -> Unit,
  val forceConfirm: () -> Unit,
  val toConfirm: () -> Unit,
)

/**
 *  برگهٔ خروج — یک `V3Sheet` که بلندی‌اش با هر مرحله نرم عوض می‌شود (نه پنجره‌های
 *  روی هم): A تأیید، `CHECKING` فرستادنِ همه و سنجیدنِ پاسخِ سرور، B «X تغییرِ ثبت‌نشده هنوز
 *  به سرور نرسیده» (دوباره بکوش / خروجِ اجباری… / بیرون نشو)، C پشتیبان + گاوصندوقِ رمزدارِ
 *  همین حساب و پرسشِ دوم. تا سرور همه را نگرفته، هیچ چیزی پاک نمی‌شود (`data/SafeSignOut`).
 */
@Composable
internal fun SignOutContent(ui: SignOutUi, actions: SignOutActions, onDismiss: () -> Unit) {
  V3Sheet(onDismiss = onDismiss) { closer ->
    //  مرحله‌ها با همان حرکتِ صفحهٔ کیت می‌روند و بلندیِ برگه با فنر عوض می‌شود
    V3PageSwitch(ui.stage, depth = { it.ordinal }, modifier = Modifier.fillMaxWidth()) { stage ->
      Column(Modifier.fillMaxWidth()) {
        when (stage) {
          SignOutModel.Stage.CONFIRM -> if (ui.everywhere) Step(
            Icons.AutoMirrored.Outlined.Logout, V3Tone.ERROR, StrAccount.signOutAllTitle, StrAccount.signOutAllBody, ui.serverError,
          ) { Buttons(closer, StrAccount.signOutAllAction, V3ButtonKind.DANGER, ui.busy, actions.signOut) }
          else Step(
            Icons.AutoMirrored.Outlined.Logout, V3Tone.ERROR, StrAccount.signOutTitle, StrAccount.signOutBody,
          ) { Buttons(closer, StrAccount.signOutAction, V3ButtonKind.DANGER, ui.busy, actions.signOut) }
          SignOutModel.Stage.CHECKING -> Busy(StrAccount.checkingBeforeSignOut)
          SignOutModel.Stage.BLOCKED -> if (ui.pending > 0) {
            val body = if (ui.reason == SignOutSafety.Reason.NONE) StrAccount.pendingBody(ui.pending, ui.online)
              else SignOutModel.reasonText(ui.reason)
            Step(Icons.Outlined.CloudUpload, V3Tone.WARNING, StrAccount.unsyncedTitle(ui.pending), body, ui.lastError) {
              V3Button(StrAccount.tryAgain, actions.syncNow, Modifier.fillMaxWidth(), enabled = !ui.busy, haptic = true)
              Spacer(Modifier.height(10.dp))
              Buttons(closer, StrAccount.forceSignOut, V3ButtonKind.DANGER, ui.busy, actions.force, neutral = StrAccount.dontLeave)
            }
          } else {
            Step(Icons.Outlined.CloudUpload, V3Tone.SUCCESS, StrAccount.allSentTitle, StrAccount.allSentBody) {
              Buttons(closer, StrAccount.signOutAction, V3ButtonKind.DANGER, ui.busy, actions.toConfirm)
            }
          }
          SignOutModel.Stage.WRITING -> Busy(StrAccount.writingBackup)
          SignOutModel.Stage.FORCE -> Step(
            Icons.Outlined.SaveAlt, V3Tone.ERROR, StrAccount.forceTitle, StrAccount.forceKeptBody(ui.pending, ui.backupPath),
          ) { Buttons(closer, StrAccount.signOutAnyway, V3ButtonKind.DANGER, ui.busy, actions.forceConfirm, neutral = StrAccount.dontLeave) }
        }
      }
    }
  }
}

/** چرخندهٔ کارِ در جریان (فرستادن، نوشتنِ پشتیبان) */
@Composable
private fun Busy(text: String) {
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(12.dp))
    CircularProgressIndicator(color = PhoneV3Roles.focus(PhoneV3.colors), strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
    Spacer(Modifier.height(16.dp))
    Text(text, style = V3Type.headline, color = PhoneV3.colors.ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
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
private fun Buttons(
  closer: V3SheetCloser,
  action: String,
  kind: V3ButtonKind,
  busy: Boolean,
  onAction: () -> Unit,
  neutral: String = StrAccount.stay,
) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    V3Button(neutral, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, enabled = !busy)
    V3Button(action, onAction, Modifier.weight(1f), kind = kind, loading = busy, haptic = true)
  }
}

/**
 *  مسیرِ `profile/signout`: «خروج» ⇒ اول همه‌چیز به سرور (`SafeSignOut.check`: تغییرِ op‌نشده، دفترِ
 *  هرگز‌هم‌ترازنشده، صف تا ته، پاسخِ سرور)؛ فقط اگر سرور همه را گرفت، باطل کردنِ نشست و پاک کردنِ توکن
 *  (`SafeSignOut.finish`). وگرنه «X تغییرِ ثبت‌نشده هنوز به سرور نرسیده» با «دوباره بکوش»، «بیرون نشو» و
 *  «خروجِ اجباری…» (پشتیبانِ دیدنی + گاوصندوقِ رمزدارِ همین حساب، و پرسشِ دوم).
 */
@Composable
internal fun RouteScope.SignOutRoute(everywhere: Boolean = false) {
  val context = LocalContext.current
  val v1 by SyncV1Engine.of(context).status.collectAsState()
  val live = SignOutModel.pending(v1.queued, AutoSync.pendingCount)
  //  برگه همیشه با تأیید باز می‌شود؛ «خروج» خودش می‌فرستد و می‌سنجد
  var stage by remember { mutableStateOf(SignOutModel.Stage.CONFIRM) }
  var reason by remember { mutableStateOf(SignOutSafety.Reason.NONE) }
  var held by remember { mutableIntStateOf(0) }
  var backupPath by remember { mutableStateOf("") }
  var kept by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  //  برگه وسطِ فرستادن بسته شد ⇒ خروج انجام نمی‌شود (فقط فرستادن تمام می‌شود)
  var dismissed by remember { mutableStateOf(false) }
  var serverError by remember { mutableStateOf("") }

  fun leave() {
    busy = true
    //  خروج نیمه‌کاره نمی‌ماند حتی اگر برگه در این میان بسته شود
    AccountSession.scope.launch {
      //  AUTH-06: اول سرور همهٔ نشست‌ها را می‌بندد؛ رد ⇒ همین دستگاه هم وارد می‌ماند و برگه خطا را می‌گوید
      if (everywhere) {
        val out = Backend.auth(context).signOutEverywhere()
        val next = SignOutModel.afterEverywhere(serverOk = out is ApiResult.Success)
        if (next != null) {
          serverError = (out as? ApiResult.Failure)?.message.orEmpty()
          busy = false
          stage = next
          return@launch
        }
      }
      SafeSignOut.finish(context)
      AccountSession.changed()
      busy = false
      Haptics.success(context)
      toasts.show(if (everywhere) StrAccount.signedOutAll else StrAccount.signedOut, V3Tone.SUCCESS)
      back()
    }
  }

  fun checkThenLeave() {
    if (busy) return
    busy = true
    stage = SignOutModel.Stage.CHECKING
    AccountSession.scope.launch {
      val verdict = runCatching { SafeSignOut.check(context, store).first }
        .getOrElse { SignOutSafety.Hold(live.coerceAtLeast(1), SignOutSafety.Reason.FAILED) }
      busy = false
      when (SignOutModel.afterCheck(verdict)) {
        null -> if (!dismissed) leave()
        else -> {
          val hold = verdict as SignOutSafety.Hold
          held = hold.pending
          reason = hold.reason
          stage = SignOutModel.Stage.BLOCKED
        }
      }
    }
  }

  val shown = if (reason == SignOutSafety.Reason.NONE) live else maxOf(held, live)
  SignOutContent(
    ui = SignOutUi(stage, shown, remember(v1) { Backend.isOnline(context) }, v1.lastError, backupPath, busy, reason, everywhere, serverError),
    actions = SignOutActions(
      signOut = { checkThenLeave() },
      syncNow = { checkThenLeave() },
      force = {
        stage = SignOutModel.Stage.WRITING
        AccountSession.scope.launch {
          SafeSignOut.keepForAccount(context)
            .onSuccess { backupPath = it; kept = true; stage = SignOutModel.Stage.FORCE }
            .onFailure { toasts.show(it.userText(StrAccount.backupFailed), V3Tone.ERROR); stage = SignOutModel.Stage.BLOCKED }
        }
      },
      forceConfirm = { if (SignOutModel.canForce(backupWritten = backupPath.isNotBlank(), kept = kept)) leave() },
      toConfirm = { stage = SignOutModel.Stage.CONFIRM },
    ),
    onDismiss = { dismissed = true; back() },
  )
}
