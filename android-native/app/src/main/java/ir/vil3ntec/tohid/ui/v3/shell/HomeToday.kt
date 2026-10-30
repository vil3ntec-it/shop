package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** سرخطِ یک بلوکِ خانه (۱۸، پررنگ) */
@Composable
internal fun HomeTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, style = v3Text(18, FontWeight.Bold, 26), color = PhoneV3.colors.ink, modifier = modifier)
}

/**
 *  «امروز» (HOME-V2 §۲٫۴): فهرستِ فشردهٔ آخرین ۵ تراکنشِ همین روز، هر کدام یک خط
 *  (نام · مبلغ · زمان)، در یک کارتِ خانواده (شعاعِ ۲۰، مرزِ ۱dp) و «همه» ⇐ فهرستِ کامل.
 *  بی سرخطِ تاریخ؛ روزِ بی تراکنش فقط یک خطِ آرام. زدن ⇐ برگهٔ اقلام (`TxSheet`).
 */
@Composable
internal fun HomeToday(d: ShopData, purchases: Boolean, onOpen: (String) -> Unit, modifier: Modifier = Modifier, inset: Dp = 16.dp) {
  val c = PhoneV3.colors
  val today = ir.vil3ntec.tohid.todayIso()
  //  نوشته‌ها (مشتریِ گذری) در زبانِ همین لحظه
  val rows = remember(d, purchases, today, I18n.lang) { Transactions.today(d, today, purchases = purchases) }
  var open by remember { mutableStateOf<Transactions.Tx?>(null) }
  Column(modifier.padding(horizontal = inset)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      HomeTitle(Str.today, Modifier.weight(1f))
      Text(
        Str.all,
        color = PhoneV3Roles.brandText(c),
        style = v3Text(15, FontWeight.Bold, 22),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).v3Pressable(label = StrHome.transactions) { onOpen(TransactionPaths.LIST) }
          .padding(horizontal = 6.dp, vertical = 4.dp),
      )
    }
    Spacer(Modifier.height(10.dp))
    V3Card(radius = HomeCardSpec.card, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
      if (rows.isEmpty()) {
        Text(
          StrHome.txEmptyToday,
          style = v3Text(13, FontWeight.Medium, 18),
          color = c.inkMuted,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
        )
      } else rows.forEachIndexed { i, tx ->
        if (i > 0) Box(Modifier.padding(start = 16.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(c.hairline))
        TodayRow(tx, today) { open = tx }
      }
    }
  }
  open?.let { tx -> TxSheet(d, tx, onDismiss = { open = null }, onOpen = onOpen) }
}

/** یک خط: نام · مبلغ · زمان؛ خرید با «−» (پول رفت)، لغوشده خط‌خورده و کم‌رنگ */
@Composable
private fun TodayRow(tx: Transactions.Tx, today: String, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val amount = (if (tx.incoming) "" else "−") + "${money(tx.amount)} ؋"
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .v3Pressable(label = tx.who, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(tx.who, style = v3Text(14, FontWeight.Medium, 20), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(10.dp))
    Text(
      amount,
      style = v3Text(14, FontWeight.Bold, 20).copy(textDecoration = if (tx.cancelled) TextDecoration.LineThrough else null),
      color = if (tx.cancelled) c.inkMuted else c.ink,
      maxLines = 1,
    )
    Spacer(Modifier.width(10.dp))
    Text(txWhen(tx, today), style = v3Text(12, FontWeight.Medium, 17), color = c.inkMuted, maxLines = 1)
  }
}

/** نشانهٔ هر نوع هشدار */
internal fun alertIcon(k: HomeAlerts.Kind): ImageVector = when (k) {
  HomeAlerts.Kind.ORDERS -> Icons.Outlined.ShoppingBag
  HomeAlerts.Kind.SUBSCRIPTION -> Icons.Outlined.WorkspacePremium
  HomeAlerts.Kind.SYNC -> Icons.Outlined.SyncProblem
  HomeAlerts.Kind.STOCK -> Icons.Outlined.Inventory2
  HomeAlerts.Kind.DEBTS -> Icons.Outlined.Group
  HomeAlerts.Kind.EXPIRY -> Icons.Outlined.EventBusy
}

/**
 *  هشدارِ خانه (HOME-V2 §۲٫۵): فقط وقتی کاری فوری هست (`HomeAlerts.pressing`)، یک کارتِ کوچکِ
 *  یک‌خطی؛ وقتی چیزی نیست، هیچ. یکی ⇐ مقصدِ همان هشدار؛ چند تا ⇐ «+n» و مرکزِ هشدارها.
 *  فوری‌ها سرخ، صفِ مانده کهربایی — رنگ با آیکن، نه تنها.
 */
@Composable
internal fun HomeUrgent(alerts: List<HomeAlerts.Alert>, onOpen: (String) -> Unit, modifier: Modifier = Modifier, inset: Dp = 16.dp) {
  val first = alerts.firstOrNull() ?: return
  val c = PhoneV3.colors
  val s = Shop.colors
  val tone = if (first.urgent) s.message.error else s.message.warning
  val shape = RoundedCornerShape(HomeCardSpec.tile)
  val route = if (alerts.size == 1) first.route else ShellTargets.notifications()
  Row(
    modifier
      .padding(horizontal = inset)
      .fillMaxWidth()
      .v3Pressable(label = first.title) { onOpen(route) }
      .clip(shape)
      .background(tone.bg)
      .border(1.dp, tone.border, shape)
      .heightIn(min = 52.dp)
      .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(if (first.kind == HomeAlerts.Kind.SYNC && !first.urgent) Icons.Outlined.CloudOff else alertIcon(first.kind), contentDescription = null, tint = tone.icon, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(10.dp))
    Text(first.title, style = v3Text(14, FontWeight.SemiBold, 20), color = tone.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    if (alerts.size > 1) {
      Spacer(Modifier.width(8.dp))
      Box(Modifier.clip(CircleShape).background(c.card).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text("+${alerts.size - 1}", style = v3Text(12, FontWeight.Bold, 16), color = tone.text, maxLines = 1)
      }
    }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Icon(if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, contentDescription = null, tint = tone.icon, modifier = Modifier.size(20.dp))
  }
}
