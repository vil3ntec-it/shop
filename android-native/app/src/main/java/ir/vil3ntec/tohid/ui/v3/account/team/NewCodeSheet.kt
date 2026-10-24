package ir.vil3ntec.tohid.ui.v3.account.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.account.DraftCodeReveal
import ir.vil3ntec.tohid.ui.v3.account.DraftSelectCard
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/** فرمِ «کد پیوستن تازه» در برگه: کارت‌های نقش، چند بار، چند روز، «بساز» */
@Composable
internal fun NewCodeContent(
  role: String,
  uses: String,
  days: String,
  courierAllowed: Boolean,
  busy: Boolean,
  onRole: (String) -> Unit,
  onUses: (String) -> Unit,
  onDays: (String) -> Unit,
  onCreate: () -> Unit,
  onDismiss: () -> Unit,
) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss, title = StrAccount.newCodeTitle) { _ ->
    Text(NewCodeModel.body(role), style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    Text(StrAccount.role, style = V3Type.subhead, color = c.inkMuted)
    Spacer(Modifier.height(6.dp))
    NewCodeModel.roles(courierAllowed).chunked(2).forEach { pair ->
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pair.forEach { opt ->
          DraftSelectCard(selected = role == opt.role, label = opt.title, onClick = { onRole(opt.role) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(opt.title, style = V3Type.headline, color = c.ink)
            Text(opt.detail, style = V3Type.footnote, color = c.inkMuted)
          }
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
      }
      Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(6.dp))
    V3TextField(uses, onUses, StrAccount.usesField, kind = V3FieldKind.NUMBER, enabled = role != NewCodeModel.COURIER)
    Spacer(Modifier.height(12.dp))
    V3TextField(days, onDays, StrAccount.daysField, kind = V3FieldKind.NUMBER)
    Spacer(Modifier.height(18.dp))
    V3Button(StrAccount.create, onCreate, Modifier.fillMaxWidth(), loading = busy, haptic = true)
  }
}

/** مسیرِ `team/codes/new`: `createStaffCode` و بعد برگهٔ نمایشِ یک‌باره (`team/codes/reveal`) */
@Composable
internal fun RouteScope.NewCodeRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val shops = remember { Backend.shop(context) }
  val team = TeamSession.data
  var role by rememberSaveable { mutableStateOf(NewCodeModel.STAFF) }
  var uses by rememberSaveable { mutableStateOf("1") }
  var days by rememberSaveable { mutableStateOf("7") }
  var busy by remember { mutableStateOf(false) }
  NewCodeContent(
    role, uses, days, team.courierAllowed, busy,
    onRole = { role = it; uses = NewCodeModel.usesAfterPick(it, uses) },
    onUses = { uses = it },
    onDays = { days = it },
    onCreate = {
      val r = NewCodeModel.request(role, uses, days)
      busy = true
      scope.launch {
        shops.createStaffCode(r.role, r.maxUses, r.days)
          .onSuccess { issued ->
            Haptics.success(context)
            team.fresh = issued.code
            nav.replace(AccountPaths.REVEAL)
            TeamSession.scope.launch { team.reload(shops, ShopRole.current(context) == ShopRole.OWNER) }
          }
          .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
        busy = false
      }
    },
    onDismiss = { back() },
  )
}

/** نمایشِ یک‌بارهٔ کد: نویسه‌ها یکی‌یکی می‌چرخند؛ «رونوشت» (اصلی) و «بستن» */
@Composable
internal fun RevealContent(code: String, onCopy: () -> Unit, onClose: () -> Unit, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss, title = StrAccount.revealTitle) { closer ->
    Spacer(Modifier.height(4.dp))
    DraftCodeReveal(code)
    Spacer(Modifier.height(12.dp))
    Text(StrAccount.revealBody, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(18.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrAccount.copyAction, onCopy, Modifier.fillMaxWidth(), icon = Icons.Outlined.ContentCopy, haptic = true)
      V3Button(Str.close, { closer.close(onClose) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
    }
  }
}

/** مسیرِ `team/codes/reveal`: کد فقط از حافظه (`TeamData.fresh`)؛ بی کد ⇒ برگشت */
@Composable
internal fun RouteScope.RevealRoute() {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val team = TeamSession.data
  val code = team.fresh
  if (code == null) { LaunchedEffect(Unit) { back() }; return }
  RevealContent(
    code,
    onCopy = {
      clipboard.setText(AnnotatedString(code))
      Haptics.confirm(context)
      toasts.show(StrAccount.codeCopied, V3Tone.SUCCESS)
    },
    onClose = { team.fresh = null },
    //  پیش از «بستن» رفت ⇒ کد روی صفحهٔ کارمندان می‌ماند تا گم نشود
    onDismiss = { back() },
  )
}
