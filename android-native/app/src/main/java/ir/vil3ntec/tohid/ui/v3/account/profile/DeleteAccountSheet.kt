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
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ir.vil3ntec.tohid.core.account.AccountDeletion
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.AccountDeletionDto
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SheetCloser
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.account.AccountSession
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/** آن‌چه برگهٔ «حذفِ حساب» نشان می‌دهد */
internal data class DeleteAccountUi(
  val stage: AccountDeletion.Stage,
  val info: AccountDeletionDto?,
  val typed: String = "",
  val error: String? = null,
  val busy: Boolean = false,
) {
  val canDelete: Boolean
    get() = !busy && info != null && AccountDeletion.matches(typed, info.confirmWith, info.confirmValue)
}

/** کارهای برگه */
internal class DeleteAccountActions(
  val type: (String) -> Unit,
  val delete: () -> Unit,
  val askSupport: () -> Unit,
  val retry: () -> Unit,
)

/**
 *  برگهٔ «حذفِ حساب» (قاعدهٔ Google Play): پیش‌نمایشِ سرور (چه می‌رود، چه می‌ماند، کدام دکان‌ها)، نوشتنِ نشانیِ
 *  همان حساب و دکمهٔ خطرِ «حذفِ همیشگی» که تا نوشتنِ درست خاموش است. پرچمِ خاموش یا حسابِ پمپ‌دار ⇒ فقط
 *  «درخواست از پشتیبانی» (همان رشتهٔ پشتیبانیِ برنامه). تأییدِ دوبارهٔ رمز را `ReauthGate` خودش می‌پرسد.
 */
@Composable
internal fun DeleteAccountContent(ui: DeleteAccountUi, actions: DeleteAccountActions, onDismiss: () -> Unit) {
  V3Sheet(onDismiss = onDismiss, dismissible = !ui.busy) { closer ->
    when (ui.stage) {
      AccountDeletion.Stage.LOADING -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator(color = PhoneV3Roles.focus(PhoneV3.colors), strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(24.dp))
      }
      AccountDeletion.Stage.OFFLINE -> Head(Icons.Outlined.WifiOff, V3Tone.WARNING, StrAccount.deleteAccount, StrAccount.deleteLoadFailed) {
        Buttons(closer, StrAccount.deleteRetry, V3ButtonKind.PRIMARY, enabled = true, busy = false, onAction = actions.retry)
      }
      AccountDeletion.Stage.SUPPORT, AccountDeletion.Stage.PUMP -> Head(
        Icons.Outlined.SupportAgent, V3Tone.INFO, StrAccount.deleteSupportTitle,
        if (ui.stage == AccountDeletion.Stage.PUMP) StrAccount.deletePumpBody else StrAccount.deleteSupportBody,
        ui.error,
      ) { Buttons(closer, StrAccount.deleteAskSupport, V3ButtonKind.PRIMARY, enabled = !ui.busy, busy = ui.busy, onAction = actions.askSupport) }
      AccountDeletion.Stage.CONFIRM -> {
        val info = ui.info ?: return@V3Sheet
        Head(Icons.Outlined.DeleteForever, V3Tone.ERROR, StrAccount.deleteTitle, StrAccount.deleteBody) {
          val c = PhoneV3.colors
          info.shops.forEach { s ->
            Text(
              if (s.otherMembers > 0) StrAccount.deleteShopStaff(s.name, s.otherMembers) else StrAccount.deleteShop(s.name),
              style = V3Type.callout, color = PhoneV3Roles.errorText(c), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
          }
          Text(StrAccount.deleteKept, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.fillMaxWidth())
          Spacer(Modifier.height(14.dp))
          V3TextField(
            ui.typed, actions.type,
            when (info.confirmWith) {
              "email" -> StrAccount.deleteTypeEmail
              "phone" -> StrAccount.deleteTypePhone
              else -> StrAccount.deleteTypeId
            },
            kind = when (info.confirmWith) {
              "email" -> V3FieldKind.EMAIL
              "phone" -> V3FieldKind.PHONE
              else -> V3FieldKind.TEXT
            },
            helper = StrAccount.deleteTypeHelper(info.confirmValue),
            error = ui.error,
            enabled = !ui.busy,
          )
          Spacer(Modifier.height(18.dp))
          Buttons(closer, StrAccount.deleteAction, V3ButtonKind.DANGER, enabled = ui.canDelete, busy = ui.busy, onAction = actions.delete)
        }
      }
    }
  }
}

/** سرِ برگه: دایرهٔ آیکن به رنگِ گونه، عنوان، توضیح و خطای اختیاری؛ بقیه زیرش */
@Composable
private fun Head(icon: ImageVector, tone: V3Tone, title: String, body: String, error: String? = null, rest: @Composable () -> Unit) {
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
    if (!error.isNullOrBlank()) {
      Spacer(Modifier.height(8.dp))
      Text(error, style = V3Type.footnote, color = PhoneV3Roles.errorText(c), textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(16.dp))
    rest()
  }
}

/** «ماندن» (خنثی، برگه را با حرکت می‌بندد) کنارِ کارِ اصلی */
@Composable
private fun Buttons(closer: V3SheetCloser, action: String, kind: V3ButtonKind, enabled: Boolean, busy: Boolean, onAction: () -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    V3Button(StrAccount.stay, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, enabled = !busy)
    V3Button(action, onAction, Modifier.weight(1f), kind = kind, enabled = enabled, loading = busy, haptic = true)
  }
}

/**
 *  مسیرِ `profile/delete`: پیش‌نمایش از `GET /api/me/account/deletion`؛ حذف با `POST` (رمزِ دوباره را
 *  `ReauthGate` می‌پرسد)، بعد همان خروجِ محلیِ برگهٔ خروج (`SyncStore.signOut`) — دفترِ روی گوشی می‌ماند.
 *  پرچمِ خاموش یا پمپ ⇒ یک پیامِ «درخواستِ حذف» در رشتهٔ پشتیبانی و رفتن به همان صفحه.
 */
@Composable
internal fun RouteScope.DeleteAccountRoute() {
  val context = LocalContext.current
  val state = remember { SyncStore(context) }
  var info by remember { mutableStateOf<AccountDeletionDto?>(null) }
  var failed by remember { mutableStateOf(false) }
  var typed by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var attempt by remember { mutableIntStateOf(0) }

  LaunchedEffect(attempt) {
    failed = false
    Backend.account(context).deletionInfo()
      .onSuccess { info = it }
      .onFailure { failed = true }
  }

  val loaded = info
  val stage = AccountDeletion.stage(loaded != null, failed && loaded == null, loaded?.enabled == true, loaded?.blocked)

  DeleteAccountContent(
    ui = DeleteAccountUi(stage, loaded, typed, error, busy),
    actions = DeleteAccountActions(
      type = { typed = it; error = null },
      delete = {
        busy = true
        //  حذف نیمه‌کاره نمی‌ماند حتی اگر برگه در این میان بسته شود
        AccountSession.scope.launch {
          Backend.account(context).deleteAccount(typed)
            .onSuccess {
              SyncStore(context).signOut()
              AccountSession.changed()
              busy = false
              Haptics.success(context)
              toasts.show(StrAccount.deleteDone, V3Tone.SUCCESS)
              back()
              nav.backFrom(AccountPaths.EDIT)
            }
            .onFailure { error = it.userMessage; busy = false }
        }
      },
      askSupport = {
        busy = true
        AccountSession.scope.launch {
          Backend.support(context).sendMessage(state.deviceUid, state.accountName, StrAccount.deleteSupportMessage)
            .onSuccess {
              busy = false
              toasts.show(StrAccount.deleteRequested, V3Tone.SUCCESS)
              back()
              nav.open(AccountPaths.SUPPORT)
            }
            .onFailure { error = it.userMessage; busy = false }
        }
      },
      retry = { attempt++ },
    ),
    onDismiss = { if (!busy) back() },
  )
}
