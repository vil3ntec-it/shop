package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPick
import ir.vil3ntec.tohid.core.model.PickWeightHoldDto
import ir.vil3ntec.tohid.core.pick.PickText
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  S56 — وزنِ بیش از حد تا جوابِ مشتری روی قلمِ برگهٔ جمع‌آور (پرچمِ خاموشِ `shop.orders.weight_hold`؛ `lib/pick-weight-hold.js`).
 *  وزن، مبلغ، وقت و حالت همه از سرور: این‌جا فقط «در انتظارِ مشتری» و «مشتری پذیرفت/رد کرد» ِ کارمند پس از تماس
 *  (`POST …/pick/weight-holds/decide`). وزنِ تازه، «پیدا نکردم» یا «از نو» نگه‌داشت را پس می‌گیرد (همان سرور).
 *  فقط Compose ِ مشترک (بی API ِ اندروید) ⇒ همین پرونده روی آیفون هم کامپایل می‌شود.
 */

/** [onDecide] = (شناسهٔ نگه‌داشت، `accept`/`reject`)؛ null ⇒ فقط نمایش (برگهٔ بسته) */
@Composable
internal fun PickWeightHoldNote(h: PickWeightHoldDto, unit: String, enabled: Boolean, onDecide: ((String, String) -> Unit)?) {
  val c = Shop.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      StrPick.holdLine(PickText.qty(h.actualQtyMilli, unit), afn(h.totalMinor), afn(h.extraMinor)),
      Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.text,
    )
    StatusPill(if (h.expired) StrPick.holdExpired else StrPick.holdPending, if (h.expired) PillTone.DANGER else PillTone.WARNING)
  }
  if (h.expired || onDecide == null) return
  Text(StrPick.holdHint, style = MaterialTheme.typography.bodySmall, color = c.muted)
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    TohidButton(StrPick.subAccept, onClick = { onDecide(h.id, "accept") }, enabled = enabled)
    TohidSecondaryButton(StrPick.subReject, onClick = { onDecide(h.id, "reject") }, enabled = enabled)
  }
}
