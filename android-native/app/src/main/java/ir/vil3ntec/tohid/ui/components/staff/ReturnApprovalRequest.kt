package ir.vil3ntec.tohid.ui.components.staff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.StaffPins
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.ReturnApproval

/**
 *  «درخواستِ تأیید» ِ برگشت — RET-04، RET-10، STF-02 (S14، S45).
 *
 *  همان [ApprovalDialog] ِ تخفیف: مدیر یا صاحب روی همین دستگاه نامش را برمی‌گزیند، دلیل می‌نویسد
 *  و PIN می‌زند؛ «برگهٔ امضاشده» برای سندِ `returns/<docId>` و تا مبلغِ همین سند ساخته می‌شود و
 *  `ReturnFlow.post` آن را روی نخستین ردیفِ سند می‌گذارد. بالای سقفِ نقش فقط صاحب در فهرست است.
 *  انصراف، PINِ غلط یا نبودنِ تأییدکننده ⇒ `null` و هیچ چیزی ثبت نمی‌شود.
 *
 *  فقط وقتی دیده می‌شود که «برگشت و تعویضِ تازه» روشن است (`ReturnSettings`، پیش‌فرض خاموش).
 */
@Composable
fun ReturnApprovalRequest(need: ReturnApproval.Need, onResult: (ReturnFlow.Approval?) -> Unit) {
  val context = LocalContext.current
  val requester = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") }
  ApprovalDialog(
    title = "تأییدِ برگشت",
    summary = "برگشتِ ${money(need.amountMinor / 100.0)} ؋ (${ReturnApproval.whyText(need.reasons)}) — " +
      "تأییدِ ${ReturnApproval.approverText(need.approver)} لازم است.",
    shopId = ShopRole.shopId(context),
    requesterUserId = requester,
    need = ApprovalSlip.Need(need.perm, need.doc, null, need.amountMinor),
    onApproved = { slip ->
      val who = StaffPins(context).get(slip.by)
      //  نقشِ نامعلوم ⇒ شاگرد (بسته‌به‌خطا): `ReturnFlow.canApprove` آن را نمی‌پذیرد
      onResult(ReturnFlow.Approval(by = who?.name?.ifBlank { null } ?: slip.by, role = who?.role ?: "staff", slip = slip))
    },
    onDismiss = { onResult(null) },
    minRole = need.approver,
  )
}
