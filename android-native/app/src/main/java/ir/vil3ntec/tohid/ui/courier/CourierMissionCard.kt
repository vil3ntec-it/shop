package ir.vil3ntec.tohid.ui.courier

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.courier.CourierFlow
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Message

/*
 *  کارتِ مأموریت در برگهٔ «مسیر» (گوشی و نمای کارتِ تبلت) و ردیفِ نمای جدولیِ صفحهٔ پهن — از `CourierHome.kt` جدا شد
 *  تا آن پرونده زیرِ سقفِ اندازه بماند؛ رفتار همان، به‌اضافهٔ DLV-02 (خطِ «ناحیه · دروازه» و «تماس با مشتری»).
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MissionCardView(
  c: MissionCard,
  waiting: Boolean,
  modifier: Modifier,
  onOpen: () -> Unit,
  onAction: (String) -> Unit,
  onRate: (() -> Unit)? = null,
  onCall: (() -> Unit)? = null,
) {
  val p = PhoneV3.colors
  val live = CourierFlow.isLive(c.status)
  Column(
    modifier.fillMaxWidth().courierCard(p).clip(CourierCardShape).clickable(onClick = onOpen).padding(18.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrCourierApp.orderNo(c.orderNo.fa()), style = courierText(14.sp, FontWeight.Bold), color = p.ink, modifier = Modifier.weight(1f))
      if (waiting) { QueuedPill(); Spacer(Modifier.width(6.dp)) }
      MissionStatus(c.status)
    }
    if (c.recallRequested && live) {
      Spacer(Modifier.height(10.dp))
      V3Message(V3Tone.ERROR, RECALL_NOTE)
    }
    //  DLV-02: «اول نشانه» — نشانه، «ناحیه · دروازهٔ رنگ»، بعد نشانیِ متنی (پایان‌یافته ⇒ هیچ؛ `MissionView.forDisplay`)
    landmarkLines(c).forEachIndexed { i, line ->
      Spacer(Modifier.height(if (i == 0) 10.dp else 2.dp))
      Text(
        line, style = if (i == 0) courierText(18.sp, FontWeight.Bold) else courierText(14.sp),
        color = if (i == 0) p.ink else p.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
      )
    }
    //  S33: Plus Code از پینِ مقصد زیرِ نشانی، فقط در حالِ زنده (همان `missionPlusCode` ِ جزئیات؛ بی پین ⇒ هیچ)
    if (live) missionPlusCode(c)?.let {
      Spacer(Modifier.height(2.dp))
      Text(StrCourier.plusCode(it), style = courierText(13.sp, FontWeight.Medium), color = p.inkMuted)
    }
    val chips = live && (c.expectedCollectMinor > 0 || c.requiresCode) || !live && (c.cashCollectedMinor != null || c.fareMinor > 0)
    if (chips) {
      Spacer(Modifier.height(12.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (live) {
          if (c.expectedCollectMinor > 0) CourierInfoChip(Icons.Outlined.Payments, StrCourierApp.cashChip(afn(c.expectedCollectMinor)))
          if (c.requiresCode) CourierInfoChip(Icons.Outlined.Lock, StrCourierApp.deliveryCode)
        } else {
          c.cashCollectedMinor?.let { CourierInfoChip(Icons.Outlined.CheckCircle, StrCourierApp.collectedChip(afn(it)), positive = true) }
          if (c.fareMinor > 0) CourierInfoChip(Icons.Outlined.Payments, StrCourierApp.fareChip(afn(c.fareMinor)))
        }
      }
    }
    if (reached(c.status) >= 0 && live) {
      Spacer(Modifier.height(14.dp))
      StepTrack(c.status)
    }
    val actions = if (live) CourierFlow.actions(c.status) else emptyList()
    if (actions.isNotEmpty()) {
      Spacer(Modifier.height(16.dp))
      val primary = actions.firstOrNull { !isNegative(it) }
      if (c.status == "requested") {
        //  کارِ تازه: قبول و رد کنارِ هم، هم‌اندازه
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          primary?.let { StepButton(actionLabel(c.status, it), { onAction(it) }, Modifier.weight(1f)) }
          actions.filter { isNegative(it) }.forEach {
            CourierSecondary(actionLabel(c.status, it), { onAction(it) }, Modifier.weight(1f), negative = true, height = 56.dp)
          }
        }
      } else if (primary != null) {
        //  یک دکمهٔ بزرگ برای پلهٔ بعد؛ «ناموفق» و بقیه در صفحهٔ جزئیات
        StepButton(actionLabel(c.status, primary), { onAction(primary) }, Modifier.fillMaxWidth())
      }
    }
    //  DLV-02: «تماس با مشتری» — فقط با پرچمِ دکان و پس از «قبول» (`CourierExtras.canCall`)؛ شماره همان لحظه از سرور
    if (onCall != null) {
      Spacer(Modifier.height(10.dp))
      CourierSecondary(StrCourierApp.callCustomer, onCall, Modifier.fillMaxWidth(), icon = Icons.Outlined.Call)
    }
    //  DLV-18: امتیازِ پیک به تحویلِ انجام‌شده — فقط با پرچمِ دکان و یک بار
    if (onRate != null) {
      Spacer(Modifier.height(14.dp))
      CourierSecondary(StrCourier.rate, onRate, Modifier.fillMaxWidth(), icon = Icons.Outlined.Star)
    }
  }
}

/** نمای جدولیِ فشرده (صفحهٔ بزرگ) — همان داده؛ زدنِ ردیف جزئیات را باز می‌کند */
@Composable
internal fun MissionRow(c: MissionCard, waiting: Boolean, modifier: Modifier, onOpen: () -> Unit) {
  val p = PhoneV3.colors
  Row(
    modifier.fillMaxWidth().courierCard(p, CourierGroupShape).clip(CourierGroupShape).clickable(onClick = onOpen)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(c.orderNo.fa(), style = courierText(13.sp, FontWeight.Medium), color = p.inkMuted, modifier = Modifier.width(56.dp))
    //  DLV-02: همان «اول نشانه» ِ کارت — نشانه (بی نشانه، نشانیِ متنی) و زیرش «ناحیه · دروازه» (`rowLines`)
    val lines = rowLines(c)
    Column(Modifier.weight(1f)) {
      Text(lines.firstOrNull() ?: "—", style = courierText(15.sp), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      lines.getOrNull(1)?.let { Text(it, style = courierText(12.sp), color = p.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    Text(if (c.expectedCollectMinor > 0) afn(c.expectedCollectMinor) else "—", style = courierText(13.sp), color = p.inkMuted)
    if (waiting) QueuedPill()
    MissionStatus(c.status)
  }
}
