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
) {
  val context = LocalContext.current
  val canAdmin = ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context)
  val main = AppNav.SHORTCUTS.mapIndexed { i, (id, label) -> Triple(id, label, "Ctrl+${i + 1}") }
  val extra = buildList {
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
    main.forEach { (id, label, key) -> RailItem(id, label, key, iconOf(id), current == id, onPick, compact) }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Shop.colors.border))
    Spacer(Modifier.height(8.dp))
    extra.forEach { (id, label, key) -> RailItem(id, label, key, iconOf(id), current == id, onPick, compact) }
    //  انتخابِ حالتِ منو — همین‌جا، تا لازم نباشد در تنظیمات دنبالش بگردد
    if (!compact) {
      Spacer(Modifier.height(14.dp))
      Text("نمایشِ منو", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
        modifier = Modifier.padding(horizontal = 12.dp))
      Spacer(Modifier.height(4.dp))
      Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(RailMode.FULL to "ثابت", RailMode.COMPACT to "آیکن", RailMode.HIDDEN to "پنهان").forEach { (m, label) ->
          Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (mode == m) Shop.colors.primary else Shop.colors.muted,
            fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (mode == m) Shop.colors.primary.copy(alpha = 0.12f) else Shop.colors.surface.copy(alpha = 0f))
              .clickable { onMode(m) }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun RailItem(id: String, label: String, key: String, icon: ImageVector, selected: Boolean, onPick: (String) -> Unit, compact: Boolean = false) {
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
      .background(if (selected) Shop.colors.primary.copy(alpha = 0.12f) else Shop.colors.surface.copy(alpha = 0f))
      .clickable { onPick(id) }
      .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = if (selected) Shop.colors.primary else Shop.colors.muted, modifier = Modifier.size(19.dp))
    Spacer(Modifier.width(10.dp))
    Text(
      label,
      style = MaterialTheme.typography.bodyMedium,
      color = if (selected) Shop.colors.primary else Shop.colors.text,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      modifier = Modifier.weight(1f),
    )
    if (key.isNotEmpty()) Text(key, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
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
    "devices" -> Icons.Filled.Devices
  else -> Icons.Filled.MoreHoriz
}
