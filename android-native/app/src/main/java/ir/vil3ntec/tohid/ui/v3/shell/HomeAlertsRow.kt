package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger

internal fun alertIcon(k: HomeAlerts.Kind): ImageVector = when (k) {
  HomeAlerts.Kind.ORDERS -> Icons.Outlined.ShoppingBag
  HomeAlerts.Kind.SUBSCRIPTION -> Icons.Outlined.WorkspacePremium
  HomeAlerts.Kind.SYNC -> Icons.Outlined.CloudSync
  HomeAlerts.Kind.STOCK -> Icons.Outlined.Inventory2
  HomeAlerts.Kind.DEBTS -> Icons.Outlined.Group
  HomeAlerts.Kind.EXPIRY -> Icons.Outlined.EventBusy
}

/**
 *  «هشدارها و پیام‌ها» + «همه» (⇐ مرکزِ هشدارها) و چرخِ کارت‌های بزرگِ r7. کارت ⇐ مقصدِ
 *  خودش؛ قرصِ کارِ کارت ⇐ `actionRoute` (اشتراک: «تمدید» ⇐ پلن‌ها). بی هشدار ⇐ کارتِ آرام.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeAlertsRow(alerts: List<HomeAlerts.Alert>, onOpen: (String) -> Unit) {
  val p = PhoneV3.colors
  Column {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
      HomeTitle(StrHome.alertsAndMessages, Modifier.weight(1f))
      if (alerts.isNotEmpty()) Text(
        Str.all,
        color = PhoneV3Roles.brandText(p),
        style = v3Text(15, FontWeight.Bold, 22),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).v3Pressable(label = Str.all) { onOpen(ShellTargets.notifications()) }
          .padding(horizontal = 6.dp, vertical = 4.dp),
      )
    }
    Spacer(Modifier.height(14.dp))
    if (alerts.isEmpty()) {
      CalmCard(Modifier.padding(horizontal = 22.dp))
    } else {
      val state = rememberLazyListState()
      val stagger = rememberV3Stagger()
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = (maxWidth - 44.dp - 28.dp).coerceAtLeast(240.dp)
        LazyRow(
          state = state,
          flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
          contentPadding = PaddingValues(horizontal = 22.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          itemsIndexed(alerts, key = { _, a -> a.kind.name + a.route }) { i, a ->
            AlertCard(
              a,
              if (i % 2 == 0) p.accentDeep else p.accent,
              Modifier.v3AnimateItem(this).v3Stagger(stagger, a.kind.name + a.route, i).width(w).height(164.dp),
              onCard = { onOpen(a.route) },
              onAction = { onOpen(a.actionRoute) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AlertCard(a: HomeAlerts.Alert, bg: Color, modifier: Modifier, onCard: () -> Unit, onAction: () -> Unit) {
  val p = PhoneV3.colors
  Box(modifier.v3Pressable(label = a.title, onClick = onCard).clip(RoundedCornerShape(22.dp)).background(bg)) {
    //  نقشِ پس‌زمینه مثلِ r7: دایرهٔ روشنِ بزرگ و نشانهٔ کم‌رنگ در گوشهٔ پایان
    Box(Modifier.align(Alignment.BottomEnd).offset(x = (-30).dp, y = 46.dp).size(150.dp).clip(CircleShape).background(p.onAccent.copy(alpha = 0.08f)))
    Icon(
      alertIcon(a.kind), contentDescription = null, tint = p.onAccent.copy(alpha = 0.22f),
      modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(72.dp),
    )
    Column(Modifier.fillMaxSize().padding(start = 22.dp, top = 20.dp, end = 96.dp, bottom = 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (a.urgent) {
          Box(Modifier.size(8.dp).clip(CircleShape).background(p.negative).border(1.dp, p.onAccent.copy(alpha = 0.6f), CircleShape))
          Spacer(Modifier.width(8.dp))
        }
        Text(a.title, color = p.onAccent, maxLines = 2, overflow = TextOverflow.Ellipsis, style = v3Text(19, FontWeight.Bold, 26))
      }
      Spacer(Modifier.height(6.dp))
      Text(a.body, color = p.onAccent.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = v3Text(13, FontWeight.Medium, 18))
      Spacer(Modifier.weight(1f))
      Text(
        a.action,
        color = bg,
        style = v3Text(13, FontWeight.Bold, 18),
        modifier = Modifier.clip(RoundedCornerShape(percent = 50)).v3Pressable(label = a.action, scale = 0.94f, onClick = onAction)
          .background(p.onAccent).padding(horizontal = 16.dp, vertical = 6.dp),
      )
    }
  }
}

/** هیچ هشداری نیست — یک کارتِ آرام، نه فهرستِ خالی */
@Composable
private fun CalmCard(modifier: Modifier) {
  val p = PhoneV3.colors
  val shape = RoundedCornerShape(22.dp)
  Row(
    modifier.fillMaxWidth().height(104.dp).clip(shape).background(p.tile).border(1.dp, p.hairline, shape).padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = p.positive, modifier = Modifier.size(34.dp))
    Spacer(Modifier.width(14.dp))
    Column {
      Text(StrHome.calmTitle, color = p.ink, style = v3Text(17, FontWeight.Bold, 24))
      Spacer(Modifier.height(4.dp))
      Text(StrHome.calmBody, color = p.inkMuted, style = v3Text(13, FontWeight.Medium, 18))
    }
  }
}
