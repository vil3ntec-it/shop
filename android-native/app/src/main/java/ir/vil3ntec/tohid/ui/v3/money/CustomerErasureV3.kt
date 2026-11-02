package ir.vil3ntec.tohid.ui.v3.money

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.crm.CustomerErasure
import ir.vil3ntec.tohid.core.i18n.StrErasure
import ir.vil3ntec.tohid.data.repo.CustomerErasureRepository
import ir.vil3ntec.tohid.ui.screens.CustomerErasureController
import ir.vil3ntec.tohid.ui.screens.CustomerErasureLive
import ir.vil3ntec.tohid.ui.screens.erasureCountLines
import ir.vil3ntec.tohid.ui.screens.erasureKeptLines
import ir.vil3ntec.tohid.ui.screens.rememberErasureApi
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  SEC-10 — «حذفِ دادهٔ این مشتری» در حسابِ قرض‌دارِ نسخهٔ ۳ (`DebtorLedgerScreen`). همان شرط و همان حالتِ رابطِ امروز
 *  (`CustomerErasureButton`، `CustomerErasureController`): فقط صاحب، شمارهٔ قابلِ حذف و پاسخِ روشنِ سرور.
 */

@Composable
internal fun CustomerErasureV3(phone: String?) {
  val api = rememberErasureApi() ?: return
  if (phone == null || !CustomerErasure.usable(phone)) return
  val src = remember(api) { CustomerErasureRepository(api) }
  LaunchedEffect(src) { CustomerErasureLive.ensure(src) }
  if (!CustomerErasureLive.gate.visible) return
  val scope = rememberCoroutineScope()
  val c = remember(src, phone) { CustomerErasureController(src, phone, { api.reauth.confirm() }, scope) }
  var open by remember(phone) { mutableStateOf(false) }
  V3Button(
    StrErasure.action, { c.reset(); open = true }, Modifier.fillMaxWidth().padding(top = 12.dp),
    kind = V3ButtonKind.DANGER, icon = Icons.Outlined.DeleteForever,
  )
  if (open) ErasureSheet(c, onClose = { if (!c.busy) { c.reset(); open = false } })
}

@Composable
private fun ErasureSheet(c: CustomerErasureController, onClose: () -> Unit) {
  LaunchedEffect(c) { if (c.step is CustomerErasure.Step.Idle) c.preview() }
  val ink = PhoneV3.colors.ink
  val muted = PhoneV3.colors.inkMuted
  V3Sheet(onDismiss = onClose, title = StrErasure.action, dismissible = !c.busy) { _ ->
    when (val s = c.step) {
      is CustomerErasure.Step.Idle, is CustomerErasure.Step.Previewing -> {
        Text(StrErasure.previewing, style = V3Type.subhead, color = muted)
        V3Skeleton(rows = 2)
      }
      is CustomerErasure.Step.Preview -> {
        Text(StrErasure.previewTitle, style = V3Type.headline, color = ink)
        Text(StrErasure.intro, style = V3Type.callout, color = ink)
        Spacer(Modifier.height(8.dp))
        val r = s.result
        when {
          r.openOrders > 0 -> V3Message(V3Tone.WARNING, StrErasure.openOrders(r.openOrders.toString()))
          r.total == 0 -> V3Message(V3Tone.INFO, StrErasure.nothing)
          else -> {
            for (l in erasureCountLines(r)) Text(l, style = V3Type.callout, color = ink)
            Text(StrErasure.total(r.total.toString()), style = V3Type.headline, color = ink)
          }
        }
        Kept()
        s.note?.let { V3Message(V3Tone.ERROR, it) }
        Spacer(Modifier.height(12.dp))
        V3Button(StrErasure.proceed, { c.proceed() }, Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER, enabled = CustomerErasure.canProceed(r))
        Spacer(Modifier.height(8.dp))
        V3Button(StrErasure.cancel, onClose, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
      }
      is CustomerErasure.Step.Confirm -> {
        Text(StrErasure.confirmTitle, style = V3Type.headline, color = ink)
        Text(StrErasure.total(s.preview.total.toString()), style = V3Type.callout, color = ink)
        Text(StrErasure.confirmBody, style = V3Type.callout, color = ink)
        Spacer(Modifier.height(8.dp))
        V3TextField(s.typed, c::type, StrErasure.confirmLabel, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER, helper = StrErasure.reauthHint)
        s.note?.let { V3Message(V3Tone.ERROR, it) }
        Spacer(Modifier.height(12.dp))
        V3Button(StrErasure.eraseNow, { c.erase() }, Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER, enabled = c.confirmOk)
        Spacer(Modifier.height(8.dp))
        V3Button(StrErasure.cancel, onClose, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
      }
      is CustomerErasure.Step.Reauth, is CustomerErasure.Step.Erasing -> {
        Text(StrErasure.erasing, style = V3Type.subhead, color = muted)
        V3Skeleton(rows = 2)
      }
      is CustomerErasure.Step.Done -> {
        V3Message(V3Tone.SUCCESS, StrErasure.doneTitle, body = StrErasure.done(s.result.total.toString()))
        Spacer(Modifier.height(8.dp))
        for (l in erasureCountLines(s.result)) Text(l, style = V3Type.callout, color = ink)
        Kept()
        Spacer(Modifier.height(12.dp))
        V3Button(StrErasure.close, onClose, Modifier.fillMaxWidth())
      }
      is CustomerErasure.Step.Failed -> {
        V3Message(V3Tone.ERROR, s.message, action = StrErasure.proceed, onAction = { c.preview() })
        Spacer(Modifier.height(8.dp))
        V3Button(StrErasure.close, onClose, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
      }
    }
  }
}

@Composable
private fun Kept() {
  Spacer(Modifier.height(10.dp))
  Text(StrErasure.keptTitle, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
  for (l in erasureKeptLines) Text(l, style = V3Type.footnote, color = PhoneV3.colors.ink, modifier = Modifier.padding(vertical = 2.dp))
}
