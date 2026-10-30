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
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.Str
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import ir.vil3ntec.tohid.core.i18n.StrShell

/**
 *  سربرگِ کامپیوتر — به‌جای نوارِ آبیِ پهنِ گوشی.
 *
 *  نسخهٔ ۲ (نقدِ ۱۴۰۵/۰۷/۲۷: «همه‌چیز دو جا هست»): سربرگ هم‌رنگِ زمینهٔ صفحه
 *  و بی خط است و **فقط یک جست‌وجو** دارد. در صندوق (`page == "sale"`) همان کادرِ
 *  اسکنِ صندوق جست‌وجوی صفحه است؛ پس کادرِ سراسری و «فروشِ تازه» این‌جا نمی‌آیند
 *  (Ctrl+K و Ctrl+2 همچنان کار می‌کنند). تنظیمات فقط در منوی کناری است، و نوارِ
 *  «نسخهٔ تازه» یک تراشهٔ کوچک کنارِ زنگ شد (`UpdateChip`) که با کلیک جزئیات را باز می‌کند.
 *  باقی: ☰ و برگشت، عنوان، زنگِ هشدارها، راهنمای کلیدها (F1)، و نام و نقشِ کاربر.
 */
/** صفحه‌هایی که جست‌وجوی خودِ فهرست را دارند — سربرگ جست‌وجوی دوم نمی‌گذارد (قاعدهٔ «یک جست‌وجو») */
private val PAGES_WITH_OWN_SEARCH = setOf("products", "debtors", "sales")

@Composable
fun DeskHeader(
  title: String,
  d: ShopData,
  onBack: (() -> Unit)?,
  onMenu: () -> Unit,
  onSearch: () -> Unit,
  onOpen: (String) -> Unit,
  /** صفحهٔ باز — صندوق جست‌وجو و «فروشِ تازه»ِ سربرگ را نمی‌گیرد */
  page: String = "",
) {
  val today = ir.vil3ntec.tohid.todayIso()
  val board = remember(d, today) { DashboardEngine.board(d, today, 7) }
  var bell by remember { mutableStateOf(false) }
  val c = Shop.colors
  val onSale = page == "sale"
  //  صفحه‌ای که جست‌وجوی خودش را دارد (کادرِ اسکنِ صندوق، کادرِ فهرستِ کالاها/قرض‌داران/فروش‌ها):
  //  سربرگ هیچ جست‌وجویی نمی‌گذارد — نه کادر، نه آیکن. Ctrl+K همچنان جست‌وجوی همه‌جاست.
  val ownSearch = onSale || page in PAGES_WITH_OWN_SEARCH

  Row(
    Modifier
      .fillMaxWidth()
      .height(56.dp)
      .background(c.bg)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, Str.menu, tint = c.muted) }
    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Str.back, tint = c.text) }
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1)
    Spacer(Modifier.width(20.dp))

    if (!ownSearch) {
      //  جست‌وجو — کادرِ نمایشی که پنجرهٔ جست‌وجوی سریع را باز می‌کند
      Row(
        Modifier
          .weight(1f)
          .widthIn(max = 480.dp)
          .height(40.dp)
          .clip(Shape.field)
          .background(c.fieldBg)
          .border(1.dp, c.fieldBorder, Shape.field)
          .clickable { onSearch() }
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Filled.Search, null, tint = c.muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(StrNav.searchPlaceholder, Modifier.weight(1f), color = c.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text("Ctrl+K", Modifier.clip(Shape.chip).background(c.surface2).padding(horizontal = 8.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
      Spacer(Modifier.weight(0.15f))
    } else Spacer(Modifier.weight(1f))

    //  نسخهٔ تازه — تراشهٔ کوچک، نه نوارِ تمام‌عرض
    ir.vil3ntec.tohid.ui.UpdateChip()
    Spacer(Modifier.width(4.dp))

    //  زنگِ هشدار
    Box {
      IconButton(onClick = { bell = true }) {
        BadgedBox(badge = { if (board.alertCount > 0) Badge { Text(board.alertCount.toString()) } }) {
          Icon(Icons.Filled.Notifications, Str.alerts, tint = c.muted)
        }
      }
      DropdownMenu(expanded = bell, onDismissRequest = { bell = false }) {
        @Composable fun item(label: String, n: Int, color: Color, to: String) {
          DropdownMenuItem(
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (n > 0) color else c.success))
                Spacer(Modifier.width(8.dp))
                Text(label, Modifier.width(180.dp))
                Text(n.toString(), fontWeight = FontWeight.Bold, color = if (n > 0) color else c.muted)
              }
            },
            onClick = { bell = false; onOpen(to) },
          )
        }
        item(Str.outOfStock, board.outOfStock.size, c.danger, "dashboard")
        item(Str.lowStock, board.lowStock.size, c.warning, "dashboard")
        item(StrNav.nearExpiry, board.expiring.size, c.warning, "dashboard")
        item(Str.overdue60, board.debtOverdue.size, c.danger, "debtors")
      }
    }
    //  میان‌برها — همیشه پیدا (F1)، حالا یک آیکن با راهنما نه دکمهٔ نوشته‌دار
    IconButton(onClick = { ir.vil3ntec.tohid.ui.AppNav.help.value = true }) {
      Icon(Icons.Filled.Keyboard, StrShell.keyboardShortcuts, tint = c.muted)
    }
    if (!onSale) {
      Spacer(Modifier.width(6.dp))
      Button(onClick = { onOpen("sale") }, shape = Shape.button, contentPadding = PaddingValues(horizontal = 14.dp)) {
        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(StrShell.newSale)
        Spacer(Modifier.width(8.dp))
        Text("Ctrl+2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
      }
    }
    Spacer(Modifier.width(8.dp))
    UserChip(onOpen)
  }
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
  val role = DeskMenu.roleLabel(remember(context) { ir.vil3ntec.tohid.data.ShopRole.current(context) })
  val shown = name.ifEmpty { StrNav.noAccount }
  Row(
    Modifier
      .clip(Shape.chip)
      .clickable(onClickLabel = Str.myAccount) { onOpen("profile") }
      .padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(Shop.colors.primaryTint), contentAlignment = Alignment.Center) {
      Text(shown.take(1), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.primaryDark)
    }
    Spacer(Modifier.width(8.dp))
    Column {
      Text(shown, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Shop.colors.text, maxLines = 1, modifier = Modifier.widthIn(max = 140.dp))
      Text(role, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, maxLines = 1)
    }
  }
}
