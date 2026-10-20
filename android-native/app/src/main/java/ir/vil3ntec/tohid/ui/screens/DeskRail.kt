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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Shape

/** پهنای منوی کناری — محتوا به همین اندازه از کنار فاصله می‌گیرد */
val DESK_RAIL_WIDTH = 200.dp
/** حالتِ «فقط آیکن» */
val DESK_RAIL_COMPACT = 68.dp

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
      //  نسخهٔ ۲: ناحیهٔ منو رنگِ خودش را دارد، نه سفیدِ هم‌رنگِ کارت‌ها. VILL3N: «پنلِ کناری
      //  جدا از محتوا» ⇒ زمینهٔ فرعی + یک خطِ جداکننده رو به محتوا
      .background(Shop.colors.zoneNav)
      .railEdge(Shop.colors.divider)
      .verticalScroll(rememberScrollState())
      .padding(vertical = 10.dp, horizontal = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    //  کلیدِ بازکردن/جمع‌کردنِ منو — بالای منو، همیشه پیدا (نسخهٔ ۲)؛ انتخاب برای همین صفحه
    //  می‌ماند (صندوق `desk_rail_pos`، بقیه `desk_rail` — `AppRoot`)
    val canToggle = mode == RailMode.FULL || mode == RailMode.COMPACT
    if (compact) {
      if (canToggle) RailTip("بازکردنِ منو") {
        Box(
          Modifier.fillMaxWidth().clip(Shape.button).clickable(onClickLabel = "بازکردنِ منو") { onMode(RailMode.FULL) }.padding(vertical = 10.dp),
          contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.ViewSidebar, contentDescription = "بازکردنِ منو", tint = Shop.colors.muted, modifier = Modifier.size(20.dp)) }
      }
    } else {
      //  نشانِ برنامه
      Row(Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(Shape.button).background(Shop.colors.brandPrimary), contentAlignment = Alignment.Center) {
          Icon(Icons.Filled.Storefront, null, tint = Shop.colors.button.primary.text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text("فروشگاه من", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1)
          Text("مدیریتِ دکان", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, maxLines = 1)
        }
        if (canToggle) RailTip("جمع‌کردنِ منو (فقط آیکن)") {
          Box(
            Modifier.size(32.dp).clip(Shape.button).clickable(onClickLabel = "جمع‌کردنِ منو") { onMode(RailMode.COMPACT) },
            contentAlignment = Alignment.Center,
          ) { Icon(Icons.Filled.ViewCompact, contentDescription = "جمع‌کردنِ منو", tint = Shop.colors.muted, modifier = Modifier.size(19.dp)) }
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
            .background(if (mode == m) Shop.colors.surfaceSelected else Shop.colors.surface.copy(alpha = 0f))
            .clickable { onMode(m) },
          contentAlignment = Alignment.Center,
        ) { Icon(ic, null, tint = if (mode == m) Shop.colors.iconActive else Shop.colors.icon, modifier = Modifier.size(17.dp)) }
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
    //  فقط آیکن — همان آیکن و همان ترتیبِ منوی کامل؛ نام در راهنمای هاور و برای صفحه‌خوان
    RailTip(label) { Box(
      Modifier
        .fillMaxWidth()
        .clip(Shape.button)
        .background(if (selected) Shop.colors.primaryTint else Shop.colors.zoneNav)
        .clickable { onPick(id) }
        .padding(vertical = 10.dp),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = label, tint = if (selected) Shop.colors.iconActive else Shop.colors.icon, modifier = Modifier.size(21.dp))
    } }
    return
  }
  /*
   *  انتخاب‌شده: زمینهٔ کم‌رنگِ اصلی، متنِ تیرهٔ پررنگ و آیکنِ رنگِ اصلی —
   *  نه متنِ سفید روی رنگِ اصلی (در شب ۲٫۷:۱ بود، زیرِ ۴٫۵:۱).
   */
  //  نسخهٔ ۲: قرصِ گرد با تهِ رنگِ اصلی و متنِ اصلیِ پررنگ (کنتراست در `BackOfficeContrastTest`)
  Row(
    Modifier
      .fillMaxWidth()
      .clip(Shape.chip)
      .background(if (selected) Shop.colors.primaryTint else Shop.colors.zoneNav)
      .clickable { onPick(id) }
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = if (selected) Shop.colors.iconActive else Shop.colors.icon, modifier = Modifier.size(19.dp))
    Spacer(Modifier.width(12.dp))
    Text(
      label,
      style = MaterialTheme.typography.bodyMedium,
      color = if (selected) Shop.colors.primaryDark else Shop.colors.text,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      modifier = Modifier.weight(1f),
    )
    //  میان‌بر فقط در راهنمای F1؛ این‌جا فقط شمارِ هشدار
    if (badge > 0) Text(
      badge.toString(),
      style = MaterialTheme.typography.labelSmall,
      //  شمارِ هشدار = هشدارِ واقعی ⇒ همان جفتِ دکمهٔ خطر (#DC2626 + سفید، ۴٫۸:۱) در روز و شب
      color = Shop.colors.button.danger.text,
      modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Shop.colors.button.danger.bg).padding(horizontal = 7.dp, vertical = 1.dp),
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
  "storefront" -> Icons.Filled.Storefront
  "delivery" -> Icons.Filled.DeliveryDining
  "soon:orders" -> Icons.Filled.ShoppingCart
  "soon:fulfil" -> Icons.Filled.Inventory
  "soon:storefront" -> Icons.Filled.Storefront
  "soon:delivery" -> Icons.Filled.DeliveryDining
  else -> Icons.Filled.MoreHoriz
}

/** راهنمای کوچکِ هاور (و نگه‌داشتنِ انگشت) با نامِ بخش — برای منوی فقط‌آیکن */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RailTip(label: String, content: @Composable () -> Unit) {
  androidx.compose.material3.TooltipBox(
    positionProvider = androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider(),
    tooltip = { PlainTooltip { Text(label) } },
    state = androidx.compose.material3.rememberTooltipState(),
  ) { content() }
}

/** خطِ یک‌پیکسلیِ لبهٔ منو رو به محتوا — در راست‌به‌چپ سمتِ چپِ منو */
private fun Modifier.railEdge(color: androidx.compose.ui.graphics.Color): Modifier = drawBehind {
  val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) 0.5f else size.width - 0.5f
  drawLine(color, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
}
