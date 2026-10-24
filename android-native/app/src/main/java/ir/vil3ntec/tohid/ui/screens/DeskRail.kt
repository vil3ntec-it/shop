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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 *  بخش‌ها یک‌جا در ستونِ راست دیده می‌شوند — همان چیدمانِ برنامه‌های میزکار.
 *
 *  از ۱۴۰۵/۰۷/۲۵ گروه‌بندی‌شده است (`DeskMenu`، v3 §۲۰.۳ و VISUAL-DIRECTION
 *  §۳): «اصلی»، «کانال‌های فروش» و «مدیریت و سامانه»؛ بخشِ ساخته‌نشده با
 *  برچسبِ «در دست ساخت» و بی‌کلیک. میان‌برهای Ctrl+۱…۹ عوض نشده‌اند (همان
 *  `AppNav.SHORTCUTS`)؛ فهرستشان در راهنمای F1 است.
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
  //  سفارش‌ها و ویترین فقط وقتی سرور روشنشان کرده (و برای ویترین، کاربر اجازهٔ مدیریت دارد)
  val groups = DeskMenu.groups(
    canAdmin,
    ordersLive = ir.vil3ntec.tohid.ui.OrdersLive.enabled,
    storefrontLive = StorefrontGate.visible,
    deliveryLive = DeliveryGate.visible,
  )
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
          Icon(Icons.Filled.Storefront, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
          Text("فروشگاه من", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
          Text("مدیریتِ دکان", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
      }
    }
    groups.forEachIndexed { gi, group ->
      Spacer(Modifier.height(if (gi == 0) 4.dp else 12.dp))
      //  عنوانِ گروه — `muted` نه `muted2`: muted2 در شب زیرِ ۴٫۵:۱ است
      if (!compact) Text(group.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
      else if (gi > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Shop.colors.border)
      group.items.forEach { item ->
        if (item.state == DeskMenu.State.SOON) {
          //  «در دست ساخت»: دیده می‌شود ولی کلیک ندارد؛ در حالتِ فقط‌آیکن نمی‌آید
          if (!compact) RailSoonItem(item.label, iconOf(item.id))
        } else RailItem(item.id, item.label, item.key, iconOf(item.id), current == item.id, onPick, compact, badges[item.id] ?: 0)
      }
    }
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

/** بخشِ ساخته‌نشده — بی‌کلیک، با نشانِ «در دست ساخت» (متن + رنگ، نه رنگِ تنها) */
@Composable
private fun RailSoonItem(label: String, icon: ImageVector) {
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .semantics(mergeDescendants = true) { disabled() },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = Shop.colors.muted, modifier = Modifier.size(19.dp))
    Spacer(Modifier.width(12.dp))
    //  نشان زیرِ نام می‌آید، نه کنارش — کنارِ هم نامِ بلند («آماده‌سازی و تحویل») بریده می‌شد
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted, maxLines = 2)
      Text(
        DeskMenu.SOON_LABEL,
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted,
        maxLines = 1,
        modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(10.dp)).background(Shop.colors.muted.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp),
      )
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
        .background(if (selected) Shop.colors.primaryTint else Shop.colors.surface.copy(alpha = 0f))
        .clickable { onPick(id) }
        .padding(vertical = 11.dp),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = label, tint = if (selected) Shop.colors.primary else Shop.colors.muted, modifier = Modifier.size(21.dp))
    }
    return
  }
  /*
   *  انتخاب‌شده: زمینهٔ کم‌رنگِ اصلی، متنِ تیرهٔ پررنگ و آیکنِ رنگِ اصلی —
   *  نه متنِ سفید روی رنگِ اصلی (در شب ۲٫۷:۱ بود، زیرِ ۴٫۵:۱).
   */
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(if (selected) Shop.colors.primaryTint else Shop.colors.surface.copy(alpha = 0f))
      .clickable { onPick(id) }
      .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = if (selected) Shop.colors.primary else Shop.colors.muted, modifier = Modifier.size(19.dp))
    Spacer(Modifier.width(12.dp))
    Text(
      label,
      style = MaterialTheme.typography.bodyMedium,
      color = Shop.colors.text,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      modifier = Modifier.weight(1f),
    )
    //  میان‌بر فقط در راهنمای F1؛ این‌جا فقط شمارِ هشدار
    if (badge > 0) Text(
      badge.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onError,
      modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Shop.colors.danger).padding(horizontal = 7.dp, vertical = 1.dp),
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
  "promos" -> Icons.Filled.LocalOffer
  "devices" -> Icons.Filled.Devices
  "storefront" -> Icons.Filled.Storefront
  "delivery" -> Icons.Filled.DeliveryDining
  "soon:orders" -> Icons.Filled.ShoppingCart
  "soon:fulfil" -> Icons.Filled.Inventory
  "soon:storefront" -> Icons.Filled.Storefront
  "soon:delivery" -> Icons.Filled.DeliveryDining
  else -> Icons.Filled.MoreHoriz
}
