package ir.vil3ntec.tohid.ui.v3.sales.approval

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.credit.CreditGate
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.data.StaffPins
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  «فروشِ نسیه بیش از سقف» (CRM-03، S07، پشتِ `ShopPolicy.creditGate`) رویِ کیت — همان قاعده‌های
 *  `CreditApprovalDialog` ِ رابطِ امروز:
 *  - مدیر یا صاحبِ پشتِ صندوق (`CreditGate.canApprove`) خودش با **دلیل** تأیید می‌کند، بی PIN.
 *  - شاگرد: مدیر یا صاحبِ دکان روی همین دستگاه نامش را برمی‌گزیند، دلیل می‌نویسد و PINِ خودش را می‌زند
 *    (`ApproverVault.verify`، شمارشِ خطا و قفلِ `PinGuard`) — همان بدنهٔ برگهٔ تأییدِ تخفیف (`ApprovalPinBody`).
 *  PINِ غلط یا «انصراف» هیچ چیزی ثبت نمی‌کند. سرور سقفِ اعتبار را نمی‌سنجد، پس برگهٔ امضاشده ساخته نمی‌شود؛
 *  تأیید با نام و دلیل همراهِ همان فروش می‌رود و `SalesEngine.record` دوباره می‌سنجد (`CreditGate.state`).
 */
@Composable
fun CreditApprovalSheet(
  summary: String,
  debtorId: String,
  addMinor: Long,
  /** نقش و نامِ کاربرِ همین صندوق */
  selfRole: String,
  selfName: String,
  requesterUserId: String,
  onApproved: (CreditGate.Approval) -> Unit,
  onClose: () -> Unit,
) {
  if (CreditGate.canApprove(selfRole)) {
    CreditSelfBody(summary, onApprove = { reason ->
      onApproved(CreditGate.Approval(selfName.trim().ifBlank { creditRoleName(selfRole) }, selfRole, debtorId, addMinor, reason))
    }, onClose = onClose)
    return
  }
  val context = LocalContext.current
  val store = remember { StaffPins(context) }
  val approvers = remember { runCatching { store.approvers(requesterUserId) }.getOrDefault(emptyList()) }
  ApprovalPinBody(
    title = StrPos.creditOverTitle,
    summary = summary,
    approvers = approvers,
    block = if (approvers.isEmpty()) ApprovalBlock.NO_APPROVER else null,
    onPin = { who, pin, reason ->
      val r = store.get(who)
      if (r == null) null to null
      else {
        //  PBKDF2 سنگین است؛ بیرون از رشتهٔ رابط
        val out = withContext(Dispatchers.Default) { ApproverVault.verify(r, pin) }
        store.put(out.record)
        val approval = if (out.ok) CreditGate.Approval(r.name.ifBlank { creditRoleName(r.role) }, r.role, debtorId, addMinor, reason.trim()) else null
        out.record to approval
      }
    },
    onApproved = onApproved,
    onClose = onClose,
  )
}

/** نامِ نقشِ تأییدکننده وقتی نامش خالی است — همان رابطِ امروز */
internal fun creditRoleName(role: String): String = if (role == "manager") StrNav.roleManager else StrNav.roleOwner

/** مدیر/صاحبِ پشتِ صندوق: دلیل و «تأیید با این دلیل»، بی PIN */
@Composable
private fun CreditSelfBody(summary: String, onApprove: (String) -> Unit, onClose: () -> Unit) {
  val c = PhoneV3.colors
  var reason by remember { mutableStateOf("") }
  //  `onDismiss` ِ برگه پیش از کارِ `closer` می‌آید: پس از تأیید، «بستن» دوباره برنمی‌گردد
  val chosen = remember { booleanArrayOf(false) }
  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = StrPos.creditOverTitle, detents = false) { closer ->
    V3StatusPill(V3Status.AWAITING, StrSale.needsApproval)
    Spacer(Modifier.height(10.dp))
    Text(summary, style = V3Type.body, color = c.ink)
    Spacer(Modifier.height(14.dp))
    V3TextField(
      value = reason,
      onValueChange = { reason = it.replace('\n', ' ').take(ApprovalSlip.MAX_REASON) },
      label = StrPos.reasonKept,
    )
    Spacer(Modifier.height(14.dp))
    V3Button(
      StrPos.creditApproveSelf,
      {
        val why = reason.trim()
        chosen[0] = true
        closer.close { onApprove(why) }
      },
      Modifier.fillMaxWidth(),
      enabled = reason.isNotBlank(),
      haptic = true,
    )
    Spacer(Modifier.height(8.dp))
    V3TextButton(StrKit.cancel, { closer.close() }, Modifier.align(Alignment.End))
  }
}
