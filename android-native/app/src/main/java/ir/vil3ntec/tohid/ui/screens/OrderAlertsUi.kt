package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.orders.OrderAlerts
import ir.vil3ntec.tohid.ui.OrderAlertsLive
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  نشان‌های بالای صفحهٔ سفارش‌ها — ORD-03 «اتصال قطع است» (پرسشِ صف به سرور نرسید؛ فقط با `presence` ِ `/config`
 *  یا میزِ سفارشِ روشن) و ORD-18 «سفارشِ زمان‌بندی‌شده — وقتِ شروع» (فهرستِ سرور؛ پرچمِ خاموش ⇒ تهی). هر نشان متن
 *  و رنگ دارد، نه رنگِ تنها؛ «دیدم» فقط روی همین دستگاه پنهانش می‌کند و سفارشِ شروع‌شده خودش می‌رود.
 */

@Composable
internal fun OrderAlertBanners(cfg: OrdersConfigDto, onOpen: (String) -> Unit) {
  val net = OrderAlertsLive.net.value
  if (net.down && (cfg.presence || OrderAlertsLive.deskOn.value)) {
    val ago = OrderAlerts.minutesSinceOk(net)?.let { StrOrderOps.netLastOk(it) + " " }.orEmpty()
    AlertBox(danger = true, title = StrOrderOps.netDown, body = ago + StrOrderOps.netBack)
  }
  val now = OrderAlertsLive.serverNow.value
  for (a in OrderAlerts.visible(OrderAlertsLive.start.value, OrderAlertsLive.dismissed.value)) {
    val left = OrderAlerts.minutesLeft(a.scheduledFor, now)
    AlertBox(
      danger = false,
      title = StrOrderOps.startTitle(a.orderNo.toString()),
      body = if (left > 0) StrOrderOps.startIn(left) else StrOrderOps.startDue,
      open = StrOrderOps.startOpen to { onOpen(a.id) },
      seen = StrOrderOps.startSeen to { OrderAlertsLive.dismiss(a.id) },
    )
  }
}

@Composable
private fun AlertBox(
  danger: Boolean,
  title: String,
  body: String,
  open: Pair<String, () -> Unit>? = null,
  seen: Pair<String, () -> Unit>? = null,
) {
  val c = Shop.colors
  Spacer(Modifier.height(8.dp))
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(if (danger) c.dangerTint else c.warningTint)
      .semantics { liveRegion = if (danger) LiveRegionMode.Assertive else LiveRegionMode.Polite }.padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(if (danger) c.danger else c.warning))
      Spacer(Modifier.width(8.dp))
      Text(title, Modifier.weight(1f), color = c.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
    if (body.isNotBlank()) {
      Spacer(Modifier.height(4.dp))
      Text(body, color = c.text, style = MaterialTheme.typography.bodySmall)
    }
    if (open != null || seen != null) {
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        open?.let { (label, act) -> TohidSecondaryButton(label, onClick = act) }
        seen?.let { (label, act) -> TohidSecondaryButton(label, onClick = act) }
      }
    }
  }
}
