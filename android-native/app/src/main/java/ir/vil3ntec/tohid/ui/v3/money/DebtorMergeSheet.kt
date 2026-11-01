package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.CustomerMerge
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet

/**
 *  «ادغامِ تکراری‌ها» (`debtors/merge`، CRM-10) — گروه‌های `CustomerMerge.duplicates`: اول شمارهٔ
 *  یکسان، بعد نامِ یکسان. سرِ هر گروه پیشنهادِ «ماندگار» (پرکارترین، بعد قدیمی‌ترین)؛ زدنِ هر
 *  عضوِ دیگر ⇐ برگهٔ تأیید (`debtors/merge/{keep}/{drop}`). [blocked] (همگام‌سازیِ v3) ⇒ همان
 *  یک خطِ دلیل و هیچ ردیفی زدنی نیست.
 */
@Composable
internal fun DebtorMergeSheet(
  groups: List<CustomerMerge.Group>,
  blocked: String?,
  onMerge: (keepId: String, dropId: String) -> Unit,
  onClose: () -> Unit,
) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onClose, title = StrMoney.mergeTitle) {
    if (groups.isEmpty()) {
      V3EmptyState(Icons.Outlined.PeopleOutline, StrMoney.noDuplicates)
      return@V3Sheet
    }
    if (blocked != null) {
      Text(blocked, style = v3Text(14, FontWeight.Medium, 20), color = c.inkMuted, modifier = Modifier.padding(bottom = 10.dp))
    }
    for (g in groups) {
      Text(
        if (g.strong) StrMoney.mergeSamePhone else StrMoney.mergeSameName,
        style = v3Text(13, FontWeight.SemiBold, 18), color = c.inkMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
      )
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PhoneV3.tileRadius)).background(c.tile)) {
        g.members.forEachIndexed { i, m ->
          if (i > 0) V3Divider(start = 16.dp)
          val keep = m.id == g.keep.id
          V3ListRow(
            title = m.name,
            subtitle = m.phone.takeIf { it.isNotBlank() }?.let { ir.vil3ntec.tohid.LocaleText.phoneDisplay(it) },
            tile = false,
            value = if (keep) StrMoney.mergeKeep else if (blocked == null) StrMoney.mergeAction else null,
            valueColor = if (keep) c.inkMuted else c.accent,
            chevron = !keep && blocked == null,
            onClick = if (keep || blocked != null) null else ({ onMerge(g.keep.id, m.id) }),
          )
        }
      }
      Spacer(Modifier.height(14.dp))
    }
  }
}
