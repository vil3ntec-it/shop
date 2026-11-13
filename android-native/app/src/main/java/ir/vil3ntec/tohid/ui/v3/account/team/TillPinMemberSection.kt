package ir.vil3ntec.tohid.ui.v3.account.team

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextButton

/**
 *  STF-04 — بخشِ «PIN ِ صندوق» ِ صفحهٔ کارمند (`TillPinMemberModel`): حال از سرور، «باز کردنِ قفل» (بی برگهٔ تأیید؛ کاری
 *  برگشت‌پذیر و شمارِ خطا در سرور می‌ماند) و «برداشتنِ PIN» (با برگهٔ تأییدِ صفحه). [row] ِ `null` ⇒ هیچ (پرچمِ خاموش،
 *  بی اجازه یا سرورِ کهنه).
 */
@Composable
internal fun TillPinMemberSection(row: TillPinMemberModel.Row?, busy: Boolean, onUnlock: () -> Unit, onRemove: () -> Unit) {
  val r = row ?: return
  V3Section(StrAccount.tillPinTitle, footer = StrAccount.tillPinHelp) {
    val label = when (r.state) {
      TillPinMemberModel.State.LOCKED -> StrAccount.tillPinLocked
      TillPinMemberModel.State.SET -> StrAccount.tillPinSet
      TillPinMemberModel.State.INVALIDATED -> StrAccount.tillPinVoid
      TillPinMemberModel.State.NONE -> StrAccount.tillPinNone
    }
    val locked = r.state == TillPinMemberModel.State.LOCKED
    V3ListRow(title = label, icon = if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, chevron = false, danger = locked)
    if (r.canUnlock) V3TextButton(StrAccount.tillPinUnlock, onUnlock, modifier = Modifier.padding(horizontal = 8.dp), enabled = !busy)
    if (r.canRemove) V3TextButton(StrAccount.tillPinRemove, onRemove, modifier = Modifier.padding(horizontal = 8.dp), danger = true, enabled = !busy)
  }
}

