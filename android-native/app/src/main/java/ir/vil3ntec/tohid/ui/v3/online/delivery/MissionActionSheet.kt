package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/**
 *  یک کارِ کارمند روی مأموریت (`delivery/missions/{id}/action?act=`) — همان دلیل‌ها و شرط‌های سرور
 *  (`DeskActions`، `DeliveryForms`): «تحویل شد» نقدِ گرفته را می‌خواهد و اگر با سفارش فرق کند دلیل؛
 *  کدِ تحویلِ مشتری (یا تأییدِ دستیِ مدیر با توضیح)؛ «کدِ تازه» کد را یک بار نشان می‌دهد.
 *  @param onDone پیامِ کوتاه پس از کار
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MissionActionSheet(
  m: Mission,
  act: DeskActions.Act,
  cfg: DeskConfig,
  repo: DeliveryRepository,
  onDismiss: () -> Unit,
  onCode: (String) -> Unit,
  onDone: (String) -> Unit,
) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  var f by remember { mutableStateOf(DeliveryForms.startAct(m)) }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  val keys = remember { DeliveryRepository.OnceKey() }
  val reasons = DeliveryForms.reasonKey(act)?.let { reasonsOf(cfg, it) }.orEmpty()
  val deliver = DeliveryForms.deliver(act)
  val needCode = DeliveryForms.needCode(m, act)
  val cashMinor = DeliveryForms.cashMinor(act, f)
  val differs = DeliveryForms.cashDiffers(m, act, f)
  val noteNeeded = DeliveryForms.noteNeeded(m, act, f)

  fun send() {
    busy = true
    err = null
    scope.launch {
      when (act) {
        DeskActions.Act.NEW_CODE -> repo.newCode(m.id).onSuccess { onCode(it) }.onFailure { err = it.userMessage }
        DeskActions.Act.RECALL -> repo.recall(m.id, f.reason, f.note, keys.of(DeliveryWire.recallJson(f.reason, f.note)))
          .onSuccess { onDone(StrOnline.dlRecalled(m.orderNo)) }
          .onFailure { err = it.userMessage }
        else -> {
          val to = DeskActions.target(act)
          if (to != null) {
            val body = DeliveryWire.statusJson(
              to = to, from = m.status, reason = f.reason, note = f.note, code = f.code.trim(),
              cashCollectedMinor = if (deliver) cashMinor else null, cashReason = if (differs) f.cashReason else "",
            )
            repo.status(m.id, body, keys.of(body))
              .onSuccess { onDone(StrOnline.movedTo(m.orderNo, StrOnline.missionStatus(it.status))) }
              .onFailure { e -> if (e.code == "status_changed") onDone(StrOnline.dlMissionStale) else err = e.userMessage }
          }
        }
      }
      busy = false
    }
  }

  V3Sheet(onDismiss = onDismiss, title = StrOnline.actionTitle(OnlineFmt.deskAction(act), m.orderNo)) { _ ->
    Text("${StrOnline.deliveryMode(m.mode)} · ${StrOnline.missionStatus(m.status)}", style = V3Type.footnote, color = c.inkMuted)
    when (act) {
      DeskActions.Act.NEW_CODE -> Note(StrOnline.dlNewCodeNote)
      DeskActions.Act.RECALL -> Note(StrOnline.dlRecallNote)
      DeskActions.Act.RETURN -> Note(StrOnline.dlReturnNote)
      else -> Unit
    }
    if (reasons.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      Text(StrOnline.reason, style = V3Type.subhead, color = c.ink)
      Spacer(Modifier.height(8.dp))
      ReasonChips(reasons, f.reason) { f = f.copy(reason = it) }
    }
    if (deliver) {
      Spacer(Modifier.height(14.dp))
      V3TextField(
        f.cash, { f = f.copy(cash = it.take(DeliveryForms.NUMBER_MAX)) }, StrOnline.dlCashCollected, kind = V3FieldKind.MONEY,
        helper = if (m.expectedCollectMinor > 0) StrOnline.dlExpected(OnlineFmt.afn(m.expectedCollectMinor)) else StrOnline.dlPaidUsuallyZero,
        error = if (cashMinor == null) StrOnline.dlBadNumber else null, modifier = Modifier.fillMaxWidth(),
      )
      if (differs) {
        Spacer(Modifier.height(10.dp))
        Text(StrOnline.dlCashDiffers, style = V3Type.subhead, color = c.ink)
        Spacer(Modifier.height(8.dp))
        ReasonChips(reasonsOf(cfg, "cash"), f.cashReason) { f = f.copy(cashReason = it) }
      }
      if (needCode) {
        Spacer(Modifier.height(12.dp))
        V3TextField(
          f.code, { f = f.copy(code = it.filter(Char::isDigit).take(DeliveryForms.CODE_LENGTH)) }, StrOnline.dlCustomerCode,
          kind = V3FieldKind.NUMBER, helper = if (cfg.perms.assign) StrOnline.dlNoCodeHelp else null, modifier = Modifier.fillMaxWidth(),
        )
      }
    }
    if (act != DeskActions.Act.NEW_CODE) {
      Spacer(Modifier.height(12.dp))
      V3TextField(
        f.note, { f = f.copy(note = it.take(DeliveryForms.NOTE_MAX)) }, if (noteNeeded) StrOnline.dlNoteRequired else StrOnline.dlNoteOptional,
        required = noteNeeded, singleLine = false, modifier = Modifier.fillMaxWidth(),
      )
    }
    err?.let { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.ERROR, it, Modifier.v3Shake(it)) }
    Spacer(Modifier.height(20.dp))
    V3Button(
      OnlineFmt.deskAction(act), { send() }, Modifier.fillMaxWidth(),
      kind = if (DeliveryModel.secondary(act)) V3ButtonKind.DANGER else V3ButtonKind.PRIMARY,
      enabled = DeliveryForms.actValid(m, act, cfg, f) && !busy, loading = busy, haptic = true,
    )
  }
}

/** تراشه‌های دلیلِ تحویل (یکی انتخاب می‌شود) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReasonChips(reasons: List<String>, selected: String, onPick: (String) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    reasons.forEach { r -> V3Chip(StrOnline.deliveryReason(r), selected == r, { onPick(r) }) }
  }
}
