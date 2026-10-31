package ir.vil3ntec.tohid.ui.v3.sales.approval

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.core.perm.PinGuard
import ir.vil3ntec.tohid.data.StaffPins
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleKeyGrid
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  برگهٔ «درخواستِ تأیید» با PINِ مدیر/صاحبِ دکان (STF-02) — جای `ApprovalDialog`ِ پیشین،
 *  روی کیت: قرصِ «نیازمند تأیید»، خلاصهٔ کار، انتخابِ تأییدکننده (بیش از یکی)، دلیل، شش نقطهٔ
 *  PIN (با هر رقم پر می‌شود؛ PINِ غلط ⇒ لرزش) و کلیدهای صندوق. PBKDF2 و امضا بیرون از رشتهٔ
 *  رابط؛ PINِ غلط یا «انصراف» هیچ چیزی ثبت نمی‌کند. سدِ واقعی سرور است.
 */
@Composable
fun ApprovalSheet(
  ask: ApprovalAsk,
  shopId: String,
  requesterUserId: String,
  onApproved: (ApprovalSlip.Slip) -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val store = remember { StaffPins(context) }
  val approvers = remember { runCatching { store.approvers(requesterUserId) }.getOrDefault(emptyList()) }
  ApprovalBody(
    ask = ask,
    approvers = approvers,
    block = ApprovalModel.block(shopId, approvers),
    onPin = { who, pin, reason ->
      val r = store.get(who)
      if (r == null) null to null
      else {
        val (next, slip) = withContext(Dispatchers.Default) {
          ApproverVault.approve(r, pin, ApproverVault.draft(r, shopId, ask.need, reason, System.currentTimeMillis()))
        }
        store.put(next)
        next to slip
      }
    },
    onApproved = onApproved,
    onClose = onClose,
  )
}

/** بدنهٔ برگه — بی انبار (عکس‌ها همین را با تأییدکنندهٔ نمایشی صدا می‌زنند) */
@Composable
fun ApprovalBody(
  ask: ApprovalAsk,
  approvers: List<ApproverVault.Record>,
  block: ApprovalBlock?,
  /** PIN را می‌سنجد: رکوردِ تازهٔ تأییدکننده (شمارِ خطا) و برگهٔ امضاشده، یا `null` */
  onPin: suspend (who: String, pin: String, reason: String) -> Pair<ApproverVault.Record?, ApprovalSlip.Slip?>,
  onApproved: (ApprovalSlip.Slip) -> Unit,
  onClose: () -> Unit,
  start: ApprovalStart = ApprovalStart(),
) = ApprovalPinBody(ask.title, ask.summary, approvers, block, onPin, onApproved, onClose, start)

/**
 *  همان بدنه برای هر نتیجهٔ تأیید: برگهٔ امضاشدهٔ تخفیف (`ApprovalSlip.Slip`) یا تأییدِ سقفِ اعتبار
 *  (`CreditGate.Approval`، `CreditApprovalSheet`). `onPin` نتیجه را می‌سازد؛ `null` ⇒ PINِ غلط.
 */
@Composable
internal fun <T : Any> ApprovalPinBody(
  title: String,
  summary: String,
  approvers: List<ApproverVault.Record>,
  block: ApprovalBlock?,
  onPin: suspend (who: String, pin: String, reason: String) -> Pair<ApproverVault.Record?, T?>,
  onApproved: (T) -> Unit,
  onClose: () -> Unit,
  start: ApprovalStart = ApprovalStart(),
) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  var who by remember { mutableStateOf(approvers.firstOrNull()?.userId.orEmpty()) }
  var reason by remember { mutableStateOf(start.reason) }
  var pin by remember { mutableStateOf(start.pin) }
  var error by remember { mutableStateOf(start.error) }
  var busy by remember { mutableStateOf(false) }
  var shake by remember { mutableIntStateOf(0) }
  val record = approvers.find { it.userId == who }
  var locked by remember(who) { mutableStateOf(record?.locked == true) }
  val name = ApprovalModel.name(record)
  //  `onDismiss` ِ برگه پیش از کارِ `closer` می‌آید: پس از تأیید، «بستن» دوباره برنمی‌گردد
  val chosen = remember { booleanArrayOf(false) }

  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = title, dismissible = !busy, detents = false) { closer ->
    V3StatusPill(V3Status.AWAITING, StrSale.needsApproval)
    Spacer(Modifier.height(10.dp))
    Text(summary, style = V3Type.body, color = c.ink)
    Spacer(Modifier.height(14.dp))

    if (block != null) {
      V3Message(V3Tone.INFO, ApprovalModel.blockText(block))
      Spacer(Modifier.height(12.dp))
      V3TextButton(StrKit.close, { closer.close() }, Modifier.align(Alignment.End))
      return@V3Sheet
    }

    if (approvers.size > 1) {
      Text(StrSale.approverWord, style = V3Type.subhead, color = c.inkMuted)
      Spacer(Modifier.height(6.dp))
      V3Segmented(
        options = approvers.map { ApprovalModel.name(it) },
        selected = approvers.indexOfFirst { it.userId == who }.coerceAtLeast(0),
        onSelect = { i -> if (!busy) { who = approvers[i].userId; pin = ""; error = null } },
      )
      Spacer(Modifier.height(10.dp))
    }
    V3TextField(
      value = reason,
      onValueChange = { reason = it.replace('\n', ' ').take(ApprovalSlip.MAX_REASON) },
      label = StrSale.approvalReason,
      enabled = !busy,
    )
    Spacer(Modifier.height(14.dp))

    val blocked = locked || reason.isBlank() || busy
    PinDots(pin.length, PinGuard.NEW_LEN, Modifier.align(Alignment.CenterHorizontally).v3Shake(shake.takeIf { it > 0 }))
    Spacer(Modifier.height(8.dp))
    val note = when {
      locked -> ApprovalModel.lockedText(name)
      error != null -> error
      else -> ApprovalModel.hint(reason, name)
    }
    Text(
      note.orEmpty(),
      style = V3Type.subhead,
      color = if (locked || error != null) PhoneV3Roles.errorText(c) else c.inkMuted,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(12.dp))
    SaleKeyGrid(ApprovalModel.KEYS, enabled = !blocked, onPress = { k ->
      pin = ApprovalModel.press(pin, k)
      error = null
      if (ApprovalModel.complete(pin)) {
        val typed = pin
        busy = true
        scope.launch {
          val (next, slip) = onPin(who, typed, reason)
          busy = false
          pin = ""
          if (slip != null) { chosen[0] = true; closer.close { onApproved(slip) } }
          else {
            locked = next?.locked == true
            error = next?.let { ApprovalModel.wrongText(PinGuard.remaining(it.guard)) }
            shake++
          }
        }
      }
    })
    Spacer(Modifier.height(12.dp))
    V3TextButton(StrKit.cancel, { if (!busy) closer.close() }, Modifier.align(Alignment.End))
  }
}

/** حالتِ آغازِ دلخواه (عکس‌های پیش‌نمایش) */
data class ApprovalStart(val reason: String = "", val pin: String = "", val error: String? = null)

/** شش نقطهٔ PIN؛ نقطهٔ پر رنگِ برند می‌گیرد (رنگ با رقم، نه به‌تنهایی: شمارش هم خوانده می‌شود) */
@Composable
private fun PinDots(filled: Int, length: Int, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  Row(
    modifier.semantics { contentDescription = StrSale.pinDigits(plain(filled), plain(length)) },
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    repeat(length) { i ->
      val color by animateColorAsState(if (i < filled) c.accent else c.hairline, MotionSpec.tweenOf(MotionTokens.SHORT), label = "pinDot")
      Box(Modifier.size(14.dp).clip(CircleShape).background(color))
    }
  }
}
