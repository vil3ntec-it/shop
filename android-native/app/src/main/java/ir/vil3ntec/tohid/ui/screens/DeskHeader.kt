package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.DashboardEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  سربرگِ کامپیوتر — به‌جای نوارِ آبیِ پهنِ گوشی.
 *
 *  باریک و روشن، مثلِ برنامه‌های کامپیوتریِ امروزی: ☰ و برگشت، عنوانِ صفحه،
 *  **جست‌وجوی همه‌جا** (Ctrl+K)، **زنگِ هشدارها** با شمار (ناموجود، کم‌موجود،
 *  انقضا، قرضِ دیرکرده) و دکمهٔ اصلیِ **«فروشِ تازه»**. در انتها نام و نقشِ
 *  کاربرِ همین دستگاه (VISUAL-DIRECTION §۳) که صفحهٔ «حساب من» را باز می‌کند.
 */
@Composable
fun DeskHeader(
  title: String,
  d: ShopData,
  onBack: (() -> Unit)?,
  onMenu: () -> Unit,
  onSearch: () -> Unit,
  onOpen: (String) -> Unit,
) {
  val today = ir.vil3ntec.tohid.todayIso()
  val board = remember(d, today) { DashboardEngine.board(d, today, 7) }
  var bell by remember { mutableStateOf(false) }

  Row(
    Modifier
      .fillMaxWidth()
      .height(60.dp)
      .background(Shop.colors.surfaceSolid)
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "منو", tint = Shop.colors.text) }
    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowForward, "برگشت", tint = Shop.colors.text) }
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1)
    Spacer(Modifier.width(24.dp))

    //  جست‌وجو — کادرِ نمایشی که پنجرهٔ جست‌وجوی سریع را باز می‌کند
    Row(
      Modifier
        .weight(1f)
        .widthIn(max = 520.dp)
        .height(40.dp)
        .clip(RoundedCornerShape(20.dp))
        //  زمینهٔ خنثی + خطِ نازک: روی سربرگِ سفید، سطحِ سفید دیده نمی‌شد
        .background(Shop.colors.bg)
        .border(1.dp, Shop.colors.border, RoundedCornerShape(20.dp))
        .clickable { onSearch() }
        .padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Filled.Search, null, tint = Shop.colors.muted, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
      Text("جست‌وجوی کالا، بارکد، مشتری، فاکتور یا صفحه…", Modifier.weight(1f), color = Shop.colors.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
      Text("Ctrl+K", Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Shop.colors.border, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Spacer(Modifier.weight(0.2f))

    //  زنگِ هشدار
    Box {
      IconButton(onClick = { bell = true }) {
        BadgedBox(badge = { if (board.alertCount > 0) Badge { Text(board.alertCount.toString()) } }) {
          Icon(Icons.Filled.Notifications, "هشدارها", tint = Shop.colors.text)
        }
      }
      DropdownMenu(expanded = bell, onDismissRequest = { bell = false }) {
        @Composable fun item(label: String, n: Int, color: Color, to: String) {
          DropdownMenuItem(
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (n > 0) color else Shop.colors.success))
                Spacer(Modifier.width(8.dp))
                Text(label, Modifier.width(180.dp))
                Text(n.toString(), fontWeight = FontWeight.Bold, color = if (n > 0) color else Shop.colors.muted)
              }
            },
            onClick = { bell = false; onOpen(to) },
          )
        }
        item("ناموجود", board.outOfStock.size, Shop.colors.danger, "dashboard")
        item("کم‌موجود", board.lowStock.size, Shop.colors.warning, "dashboard")
        item("نزدیکِ انقضا", board.expiring.size, Shop.colors.warning, "dashboard")
        item("قرضِ بیش از ۶۰ روز", board.debtOverdue.size, Shop.colors.danger, "debtors")
      }
    }
    //  میان‌برها — همیشه پیدا، نه فقط با F1
    TextButton(onClick = { ir.vil3ntec.tohid.ui.AppNav.help.value = true }) {
      Icon(Icons.Filled.Keyboard, null, tint = Shop.colors.text, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(4.dp))
      Text("میان‌برها", color = Shop.colors.text)
      Spacer(Modifier.width(6.dp))
      Text("F1", Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Shop.colors.border, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    IconButton(onClick = { onOpen("settings") }) { Icon(Icons.Filled.Settings, "تنظیمات", tint = Shop.colors.text) }
    Spacer(Modifier.width(6.dp))
    Button(onClick = { onOpen("sale") }, shape = RoundedCornerShape(20.dp)) {
      Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text("فروشِ تازه  Ctrl+2")
    }
    Spacer(Modifier.width(10.dp))
    UserChip(onOpen)
  }
  HorizontalDivider(color = Shop.colors.border)
}

/**
 *  نام و نقشِ کاربر — نام از حسابِ واردشده (`SyncStore.accountName`)، نقش از
 *  `ShopRole` (همان سدِ مجوزهای برنامه). بی‌حساب: «بی‌حساب» و نقشِ صاحب دکان،
 *  همان فرضِ `ShopRole.current`.
 */
@Composable
private fun UserChip(onOpen: (String) -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val name = remember(context) { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim() }
  val role = remember(context) { DeskMenu.roleLabel(ir.vil3ntec.tohid.data.ShopRole.current(context)) }
  val shown = name.ifEmpty { "بی‌حساب" }
  Row(
    Modifier
      .clip(RoundedCornerShape(20.dp))
      .clickable(onClickLabel = "حساب من") { onOpen("profile") }
      .padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(Shop.colors.primaryTint), contentAlignment = Alignment.Center) {
      Text(shown.take(1), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
    }
    Spacer(Modifier.width(8.dp))
    Column {
      Text(shown, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Shop.colors.text, maxLines = 1, modifier = Modifier.widthIn(max = 140.dp))
      Text(role, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, maxLines = 1)
    }
  }
}
