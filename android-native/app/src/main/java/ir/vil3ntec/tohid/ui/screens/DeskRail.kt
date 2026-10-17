package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.Shop

/** پهنای منوی کناری — محتوا به همین اندازه از کنار فاصله می‌گیرد */
val DESK_RAIL_WIDTH = 212.dp
/** حالتِ «فقط آیکن» */
val DESK_RAIL_COMPACT = 72.dp

/** سه حالتِ منو — انتخابِ خودِ کاربر، روی همین دستگاه می‌ماند */
object RailMode {
  const val FULL = "full"        // ثابت، با نام
  const val COMPACT = "compact"  // فقط آیکن
  const val HIDDEN = "hidden"    // پنهان؛ با ☰ باز می‌شود
}

/**
 *  منوی کناریِ کامپیوتر.
 *
 *  روی صفحهٔ پهن نوارِ پایینِ گوشی (پنج دکمه + «بیشتر») کنار می‌رود و همهٔ
 *  بخش‌ها یک‌جا، با میان‌برِ صفحه‌کلیدشان، در ستونِ راست دیده می‌شوند —
 *  همان چیدمانِ برنامه‌های میزکار. ترتیبِ نُه‌تای اول همان `AppNav.SHORTCUTS`
 *  است، پس «Ctrl+۴» همیشه همان چیزی است که این‌جا چهارم نوشته شده.
 */
@Composable
fun DeskRail(
  current: String,
  onPick: (String) -> Unit,
  compact: Boolean = false,
  mode: String = RailMode.FULL,
  onMode: (String) -> Unit = {},
  /** شمارِ کنارِ هر بخش (کم‌موجودی کنارِ کالاها، قرضِ دیرکرده کنارِ قرض‌داران) */
  badges: Map<String, Int> = emptyMap(),
) {
  val context = LocalContext.current
  val canAdmin = ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context)
  val main = AppNav.SHORTCUTS.mapIndexed { i, (id, label) -> Triple(id, label, "Ctrl+${i + 1}") }
  val extra = buildList {
    //  فقط وقتی سرور گفته بخشِ سفارش برای این دکان روشن است
    if (ir.vil3ntec.tohid.ui.OrdersLive.enabled) add(Triple("orders", "سفارش‌ها", ""))
    add(Triple("admin", "مرکزِ مدیریت", "Ctrl+M"))
    add(Triple("receipts", "رسیدها", ""))
    add(Triple("team", "کارمندان", ""))
    add(Triple("devices", "دستگاه‌های جانبی", ""))
    if (canAdmin) add(Triple("audit", "سابقهٔ عملیات", ""))
    if (canAdmin) add(Triple("settings", "تنظیمات", ""))
    add(Triple("more", "بیشتر", ""))
  }
  Column(
    Modifier
      .width(if (compact) DESK_RAIL_COMPACT else DESK_RAIL_WIDTH)
      .fillMaxHeight()
      .background(Shop.colors.surface)
      .verticalScroll(rememberScrollState())
      .padding(vertical = 10.dp, horizontal = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    //  نشانِ برنامه
    if (!compact) {
      Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Shop.colors.primary), contentAlignment = Alignment.Center) {
          Icon(Icons.Filled.Storefront, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
          Text("فروشگاه من", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
          Text("مدیریتِ دکان", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
      }
      Spacer(Modifier.height(8.dp))
      Text("اصلی", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
    main.forEach { (id, label, key) -> RailItem(id, label, key, iconOf(id), current == id, onPick, compact, badges[id] ?: 0) }
    Spacer(Modifier.height(10.dp))
    if (!compact) Text("مدیریت", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    extra.forEach { (id, label, key) -> RailItem(id, label, key, iconOf(id), current == id, onPick, compact, badges[id] ?: 0) }
    //  حالتِ منو — سه آیکنِ کوچک پایینِ منو
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      listOf(RailMode.FULL to Icons.Filled.ViewSidebar, RailMode.COMPACT to Icons.Filled.ViewCompact, RailMode.HIDDEN to Icons.Filled.VisibilityOff).forEach { (m, ic) ->
        Box(
          Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
            .background(if (mode == m) Shop.colors.primary.copy(alpha = 0.12f) else Shop.colors.surface.copy(alpha = 0f))
            .clickable { onMode(m) },
          contentAlignment = Alignment.Center,
        ) { Icon(ic, null, tint = if (mode == m) Shop.colors.primary else Shop.colors.muted, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(4.dp))
      }
    }
  }
}

@Composable
private fun RailItem(id: String, label: String, key: String, icon: ImageVector, selected: Boolean, onPick: (String) -> Unit, compact: Boolean = false, badge: Int = 0) {
  if (compact) {
    //  فقط آیکن — نام در راهنمای دکمه (برای صفحه‌خوان هم)
    Box(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) Shop.colors.primary.copy(alpha = 0.12f) else Shop.colors.surface.copy(alpha = 0f))
        .clickable { onPick(id) }
        .padding(vertical = 11.dp),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = label, tint = if (selected) Shop.colors.primary else Shop.colors.muted, modifier = Modifier.size(21.dp))
    }
    return
  }
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(if (selected) Shop.colors.primary else Shop.colors.surface.copy(alpha = 0f))
      .clickable { onPick(id) }
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val white = androidx.compose.ui.graphics.Color.White
    Icon(icon, contentDescription = null, tint = if (selected) white else Shop.colors.muted, modifier = Modifier.size(19.dp))
    Spacer(Modifier.width(12.dp))
    Text(
      label,
      style = MaterialTheme.typography.bodyMedium,
      color = if (selected) white else Shop.colors.text,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      modifier = Modifier.weight(1f),
    )
    //  میان‌بر فقط در راهنمای F1؛ این‌جا فقط شمارِ هشدار
    if (badge > 0) Text(
      badge.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = white,
      modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) white.copy(alpha = 0.25f) else Shop.colors.danger).padding(horizontal = 7.dp, vertical = 1.dp),
    )
  }
}

private fun iconOf(id: String): ImageVector = when (id) {
  "dashboard" -> Icons.Filled.GridView
  "sale" -> Icons.Filled.PointOfSale
  "debtors" -> Icons.Filled.Groups
  "warehouse" -> Icons.Filled.Inventory2
  "products" -> Icons.Filled.ShoppingBag
  "sales" -> Icons.Filled.ReceiptLong
  "purchasing" -> Icons.Filled.LocalShipping
  "reports" -> Icons.Filled.BarChart
  "expenses" -> Icons.Filled.Payments
  "receipts" -> Icons.Filled.Receipt
  "team" -> Icons.Filled.Badge
  "audit" -> Icons.Filled.History
  "settings" -> Icons.Filled.Settings
  "admin" -> Icons.Filled.AdminPanelSettings
  "orders" -> Icons.Filled.ShoppingBasket
    "devices" -> Icons.Filled.Devices
  else -> Icons.Filled.MoreHoriz
}
