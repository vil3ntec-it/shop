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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.core.perm.PinGuard
import ir.vil3ntec.tohid.core.perm.ReturnApprovals
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
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrNav

/**
 *  «درخواستِ تأیید» — STF-02، سناریوهای S07 و S08 (بخشِ ۱۰.۱).
 *
 *  کاری بیش از سقفِ کاربر است (مثلاً تخفیفِ ۲۰٪ با سقفِ ۱۰٪). مدیر یا صاحبِ
 *  دکان **روی همین دستگاه** نامش را برمی‌گزیند، دلیل را می‌نویسد و PINِ خودش را
 *  می‌زند؛ PIN کلیدِ خصوصیِ او را باز می‌کند و «برگهٔ امضاشده» ساخته می‌شود که
 *  همراهِ سند به سرور می‌رود. با PINِ غلط یا «انصراف» هیچ چیزی ثبت نمی‌شود.
 *
 *  سدِ واقعی سرور است: برگه را با کلیدِ ثبت‌شده، اختیارِ خودِ تأییدکننده و
 *  nonceِ یک‌بارمصرف می‌سنجد.
 */
@Composable
fun ApprovalDialog(
  title: String,
  summary: String,
  shopId: String,
  requesterUserId: String,
  need: ApprovalSlip.Need,
  onApproved: (ApprovalSlip.Slip) -> Unit,
  onDismiss: () -> Unit,
  /**
   *  کمترین نقشِ تأییدکننده (`manager` | `owner`)؛ `null` ⇒ هر مدیر یا صاحب (همان تخفیف).
   *  برگشتِ بالای سقفِ نقش فقط صاحب را نشان می‌دهد (S14؛ `ReturnApprovals.approverCovers`).
   */
  minRole: String? = null,
) {
  val context = LocalContext.current
  val store = remember { StaffPins(context) }
  val scope = rememberCoroutineScope()
  val approvers = remember(minRole) {
    store.approvers(requesterUserId).filter { minRole == null || ReturnApprovals.approverCovers(it.role, minRole) }
  }
  var who by remember { mutableStateOf(approvers.firstOrNull()?.userId.orEmpty()) }
  var reason by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val record = approvers.find { it.userId == who }
  var locked by remember(who) { mutableStateOf(record?.locked == true) }

  Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(8.dp))
        StatusPill(StrPos.needsApproval, PillTone.WARNING)
        Spacer(Modifier.height(8.dp))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
        Spacer(Modifier.height(14.dp))

        if (shopId.isBlank() || approvers.isEmpty()) {
          Text(
            if (shopId.isBlank()) StrPos.deviceNotLinked
            else StrPos.noApprovers,
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
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
            options = approvers.map { it.userId to it.name.ifBlank { if (it.role == "owner") StrNav.roleOwner else StrNav.roleManager } },
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
              //  PBKDF2 و امضا سنگین است؛ بیرون از رشتهٔ رابط
              val (next, slip) = withContext(Dispatchers.Default) {
                ApproverVault.approve(r, pin, ApproverVault.draft(r, shopId, need, reason, System.currentTimeMillis()))
              }
              store.put(next)
              busy = false
              if (slip != null) {
                onApproved(slip)
              } else {
                locked = next.locked
                error = StrPos.pinWrong(plain(PinGuard.remaining(next.guard)))
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
