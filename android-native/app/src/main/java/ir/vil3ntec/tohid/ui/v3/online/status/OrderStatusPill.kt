package ir.vil3ntec.tohid.ui.v3.online.status

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.v3.V3Type

/** آیکنِ معنادارِ هر وضعیت — کنارِ برچسب، تا رنگ تنها نباشد */
internal fun statusIcon(s: V3Status): ImageVector = when (s) {
  V3Status.AWAITING -> Icons.Outlined.HourglassTop
  V3Status.WARNING -> Icons.Outlined.WarningAmber
  V3Status.PREPARING -> Icons.Outlined.Inventory2
  V3Status.IN_DELIVERY -> Icons.Outlined.LocalShipping
  V3Status.DELIVERED -> Icons.Outlined.CheckCircle
  V3Status.CANCELLED -> Icons.Outlined.Block
  V3Status.REVIEW -> Icons.Outlined.ReportProblem
  V3Status.INACTIVE -> Icons.Outlined.Schedule
}

/** قرصِ وضعیتِ سفارش: رنگ از نگاشتِ مشترک، برچسب همان وضعیتِ دقیق (`OrderText`) */
@Composable
fun OrderStatusPill(status: String, modifier: Modifier = Modifier) {
  DraftStatusPill(OnlineStatus.order(status), StrOnline.orderStatus(status), modifier)
}

/** قرصِ وضعیتِ مأموریتِ تحویل — همان رنگ‌ها با نام‌های پیک */
@Composable
fun MissionStatusPill(status: String, modifier: Modifier = Modifier) {
  DraftStatusPill(OnlineStatus.mission(status), StrOnline.missionStatus(status), modifier)
}

/**
 *  قرصِ وضعیت با آیکن + برچسب (رنگِ §۸). با عوض شدنِ وضعیت زمینه و رنگِ نوشته نرم
 *  جابه‌جا می‌شوند (cross-fade). گونهٔ آیکن‌دارِ `V3StatusPill` ِ کیت (نقطه‌دار) — برای بردن به
 *  کیت در PR نام برده شده.
 */
@Composable
internal fun DraftStatusPill(status: V3Status, label: String, modifier: Modifier = Modifier) {
  val pair = PhoneV3Roles.status(PhoneV3.colors, status)
  val bg by animateColorAsState(pair.bg, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "pillBg")
  val fg by animateColorAsState(pair.fg, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "pillFg")
  Row(
    modifier
      .clip(RoundedCornerShape(50))
      .background(bg)
      .semantics(mergeDescendants = true) { contentDescription = label }
      .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(statusIcon(status), contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(5.dp))
    Text(label, style = V3Type.caption, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
