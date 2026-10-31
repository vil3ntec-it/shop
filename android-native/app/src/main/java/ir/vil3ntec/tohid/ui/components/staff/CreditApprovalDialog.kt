package ir.vil3ntec.tohid.ui.components.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.credit.CreditGate
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.core.perm.PinGuard
import ir.vil3ntec.tohid.data.StaffPins
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  «فروشِ نسیه بیش از سقف» — CRM-03، سناریوی S07 (پشتِ `ShopPolicy.creditGate`).
 *
 *  - کاربرِ مدیر یا صاحب (`CreditGate.canApprove`) خودش با **دلیل** تأیید می‌کند (همان «تأییدِ صریح»ِ
 *    دفترِ قرض، #261).
 *  - شاگرد: مدیر یا صاحبِ دکان **روی همین دستگاه** نامش را برمی‌گزیند، دلیل را می‌نویسد و PINِ خودش را
 *    می‌زند (`ApproverVault.verify`، شمارشِ خطا و قفلِ `PinGuard`). با PINِ غلط یا «انصراف» هیچ چیز ثبت
 *    نمی‌شود. سرور سقفِ اعتبار را نمی‌سنجد، پس این‌جا برگهٔ امضاشده ساخته نمی‌شود؛ تأیید با نام و دلیل در
 *    ردِ `credit_over_limit` ِ همان فروش می‌ماند (`SalesEngine.record`).
 */
@Composable
fun CreditApprovalDialog(
  summary: String,
  debtorId: String,
  addMinor: Long,
  /** نقش و نامِ کاربرِ همین صندوق */
  selfRole: String,
  selfName: String,
  requesterUserId: String,
  onApproved: (CreditGate.Approval) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val store = remember { StaffPins(context) }
  val scope = rememberCoroutineScope()
  val self = CreditGate.canApprove(selfRole)
  val approvers = remember { if (self) emptyList() else store.approvers(requesterUserId) }
  var who by remember { mutableStateOf(approvers.firstOrNull()?.userId.orEmpty()) }
  var reason by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val record = approvers.find { it.userId == who }
  var locked by remember(who) { mutableStateOf(record?.locked == true) }
  fun roleName(role: String) = if (role == "manager") StrNav.roleManager else StrNav.roleOwner

  Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(StrPos.creditOverTitle, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(8.dp))
        StatusPill(StrPos.needsApproval, PillTone.WARNING)
        Spacer(Modifier.height(8.dp))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
        Spacer(Modifier.height(14.dp))

        if (!self && approvers.isEmpty()) {
          Text(StrPos.noApprovers, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          Spacer(Modifier.height(12.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(Str.close) }
          }
          return@Column
        }

        if (approvers.size > 1) {
          Text(StrPos.approver, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          Spacer(Modifier.height(6.dp))
          SegmentedChoice(
            options = approvers.map { it.userId to it.name.ifBlank { roleName(it.role) } },
            selected = who,
            onSelect = { if (!busy) { who = it; error = null } },
          )
          Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it.replace('\n', ' ').take(ApprovalSlip.MAX_REASON) },
          label = { Text(StrPos.reasonKept) },
          singleLine = true,
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))

        if (self) {
          //  مدیر/صاحب: تأییدِ صریح با دلیل، بی PIN (همان کسی که پشتِ صندوق است)
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onDismiss) { Text(Str.cancel) }
            Button(
              enabled = reason.isNotBlank(),
              onClick = {
                onApproved(CreditGate.Approval(selfName.trim().ifBlank { roleName(selfRole) }, selfRole, debtorId, addMinor, reason.trim()))
              },
            ) { Text(StrPos.creditApproveSelf) }
          }
          return@Column
        }

        val name = record?.name?.ifBlank { null } ?: StrPos.approver
        PinPad(
          title = StrPos.pinOf(name),
          hint = if (reason.isBlank()) StrPos.writeReasonFirst else StrPos.sixDigitPinOf(name),
          error = when {
            locked -> StrPos.pinLocked(name, plain(PinGuard.MAX_FAILURES))
            else -> error
          },
          locked = locked || reason.isBlank(),
          busy = busy,
          onComplete = { pin ->
            val r = store.get(who) ?: return@PinPad
            busy = true
            scope.launch {
              //  PBKDF2 سنگین است؛ بیرون از رشتهٔ رابط
              val out = withContext(Dispatchers.Default) { ApproverVault.verify(r, pin) }
              store.put(out.record)
              busy = false
              if (out.ok) {
                onApproved(CreditGate.Approval(r.name.ifBlank { roleName(r.role) }, r.role, debtorId, addMinor, reason.trim()))
              } else {
                locked = out.record.locked
                error = StrPos.pinWrong(plain(PinGuard.remaining(out.record.guard)))
              }
            }
          },
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss, enabled = !busy) { Text(Str.cancel) }
        }
      }
    }
  }
}
