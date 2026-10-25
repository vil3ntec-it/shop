package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** بلندیِ ردیفِ مرکزِ هشدارها (shell.md: ۶۴dp) */
private val ROW = 64.dp
private val TILE = 40.dp

internal fun kindIcon(k: ShopAlerts.Kind): ImageVector = when (k) {
  ShopAlerts.Kind.ORDERS -> Icons.Outlined.ShoppingBag
  ShopAlerts.Kind.SUBSCRIPTION -> Icons.Outlined.WorkspacePremium
  ShopAlerts.Kind.SYNC -> Icons.Outlined.CloudSync
  ShopAlerts.Kind.STOCK -> Icons.Outlined.Inventory2
  ShopAlerts.Kind.DEBTS -> Icons.Outlined.Group
  ShopAlerts.Kind.EXPIRY -> Icons.Outlined.EventBusy
  ShopAlerts.Kind.PRODUCT_OUT -> Icons.Outlined.RemoveShoppingCart
  ShopAlerts.Kind.PRODUCT_LOW -> Icons.AutoMirrored.Outlined.TrendingDown
  ShopAlerts.Kind.SUPPLIER -> Icons.Outlined.LocalShipping
  ShopAlerts.Kind.BACKUP -> Icons.Outlined.Backup
  ShopAlerts.Kind.DEBTOR -> Icons.Outlined.Person
}

/** کاشیِ ۴۰dp با رنگِ پیامِ §۹ و آیکنِ خطی — رنگ هرگز تنها نیست (عنوان کنارش است) */
@Composable
private fun ToneTile(item: ShopAlerts.Item) {
  val c = PhoneV3.colors
  Box(
    Modifier.size(TILE).clip(RoundedCornerShape(11.dp)).background(PhoneV3Roles.tone(c, item.tone).bg),
    contentAlignment = Alignment.Center,
  ) { Icon(kindIcon(item.kind), contentDescription = null, tint = PhoneV3Roles.toneIcon(c, item.tone), modifier = Modifier.size(21.dp)) }
}

/** یک گروه («فوری» / «بقیه»): سرخط بیرونِ کارت و ردیف‌ها درونش؛ ردیف و کارش هر دو مستقیم سرِ کادرِ خودشان */
@Composable
internal fun AlertGroup(title: String, items: List<ShopAlerts.Item>, stagger: V3StaggerState, offset: Int, onOpen: (String) -> Unit) {
  if (items.isEmpty()) return
  val c = PhoneV3.colors
  V3Section(title, padding = PaddingValues(vertical = 4.dp)) {
    items.forEachIndexed { i, item ->
      if (i > 0) V3Divider(start = 64.dp)
      V3ListRow(
        title = item.title,
        subtitle = item.subtitle,
        leading = { ToneTile(item) },
        minHeight = ROW,
        subtitleLines = 1,
        modifier = Modifier.v3Stagger(stagger, item.key, offset + i),
        trailing = {
          Text(
            item.action,
            style = v3Text(14, FontWeight.SemiBold, 20),
            color = PhoneV3Roles.brandText(c),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).v3Pressable(label = item.action) { onOpen(item.actionRoute) }
              .padding(horizontal = 8.dp, vertical = 6.dp),
          )
        },
        onClick = { onOpen(item.route) },
      )
    }
  }
}

/** یک روزِ خبر: سرخطِ روز و ردیف‌های آواتارِ حرفی؛ خبرِ دیده‌نشده نقطهٔ برند دارد */
@Composable
internal fun NewsDay(day: NewsModel.Day, stagger: V3StaggerState, offset: Int) {
  val c = PhoneV3.colors
  V3Section(day.label, padding = PaddingValues(vertical = 4.dp)) {
    day.rows.forEachIndexed { i, row ->
      if (i > 0) V3Divider(start = 64.dp)
      V3ListRow(
        title = row.title,
        subtitle = row.subtitle,
        leading = {
          Box(Modifier.size(TILE).clip(CircleShape).background(PhoneV3Palette.selected(c)), contentAlignment = Alignment.Center) {
            Text(row.letter, style = v3Text(17, FontWeight.Bold, 22), color = PhoneV3Roles.brandText(c))
          }
        },
        minHeight = ROW,
        subtitleLines = 1,
        modifier = Modifier.v3Stagger(stagger, row.key, offset + i),
        trailing = if (row.unseen) ({ Box(Modifier.size(8.dp).clip(CircleShape).background(PhoneV3Palette.iconActive(c))) }) else null,
      )
    }
  }
}
