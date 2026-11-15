package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.DashboardEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskBadge
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskRadius
import ir.vil3ntec.tohid.ui.desk.DeskShortcut
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskTooltip
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskClickable

/**
 *  سربرگِ کامپیوتر — به‌جای نوارِ آبیِ پهنِ گوشی.
 *
 *  e-desk-d1 (۱۴۰۵/۰۸/۲۴): سربرگ بالای **ستونِ محتوا** است، کنارِ منوی کناریِ تمام‌قد، روی سطحِ پوسته با خطِ مویی:
 *  • آغاز: برگشت، مسیر (پدرِ صفحه، زدنی) و عنوانِ صفحه (۲۲)؛ ☰ فقط وقتی منو پنهان است (منوی پیدا ☰ نمی‌خواهد).
 *  • وسط: **یک** جست‌وجوی همه‌جا (Ctrl+K) با پهنای بیشترِ `DeskSize.searchMax`. در صندوق (`page == "sale"`) و فهرستی که
 *    جست‌وجوی خودش را دارد هیچ کادر و آیکنی نمی‌آید (قاعدهٔ «یک جست‌وجو»؛ Ctrl+K همچنان کار می‌کند).
 *  • پایان: تراشهٔ «نسخهٔ تازه» (`UpdateChip`)، زنگِ هشدارها، «راهنما» ِ همین صفحه، میان‌برها (F1)، «فروشِ تازه» با Ctrl+2
 *    (نه در خودِ صندوق) و نام و نقشِ کاربر.
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
  /** منوی کناری پیداست ⇒ ☰ نمی‌آید (فقط در حالتِ «پنهان») */
  menuVisible: Boolean = false,
  /** پدرهای صفحه (نام، مسیر)، کهنه‌ترین اول — زدن ⇒ برگشت تا همان */
  crumbs: List<Pair<String, String>> = emptyList(),
  onCrumb: (String) -> Unit = {},
) {
  //  PERF-DESK: فقط چهار هشدار، یک بار برای هر دفتر و بیرون از رشتهٔ صفحه (`DeskAlerts`؛ پیش‌تر کلِ تخته روی رشتهٔ صفحه)
  val board = rememberDeskAlerts(d)
  val c = Desk.colors
  val onSale = page == "sale"
  val ownSearch = onSale || page in PAGES_WITH_OWN_SEARCH
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

  Row(
    Modifier
      .fillMaxWidth()
      .height(DeskSize.header)
      .background(c.chrome)
      .bottomLine(c.hairline)
      .padding(horizontal = DeskSpace.x4),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    //  ── آغاز: ☰ (فقط منوی پنهان)، برگشت، مسیر و عنوان ──
    if (!menuVisible) HeaderIcon(Icons.Filled.Menu, ir.vil3ntec.tohid.core.i18n.StrDesk.showMenu, onClick = onMenu)
    if (onBack != null) HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, Str.back, tint = c.ink, onClick = onBack)
    Spacer(Modifier.width(DeskSpace.x1))
    Row(Modifier.widthIn(max = 460.dp), verticalAlignment = Alignment.CenterVertically) {
      crumbs.forEach { (name, path) ->
        Text(
          name,
          Modifier.widthIn(max = 160.dp).deskClickable(label = name, role = Role.Button) { onCrumb(path) }.padding(horizontal = 4.dp, vertical = 2.dp),
          style = DeskType.secondary,
          color = c.inkSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, contentDescription = null, tint = c.icon, modifier = Modifier.size(16.dp))
      }
      Text(
        title,
        Modifier.padding(start = 4.dp).semantics { heading() },
        style = DeskType.pageTitle,
        color = c.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    //  ── وسط: یک جست‌وجو ──
    Box(Modifier.weight(1f).padding(horizontal = DeskSpace.x6), contentAlignment = Alignment.Center) {
      if (!ownSearch) SearchBox(onSearch, Modifier.widthIn(max = DeskSize.searchMax).fillMaxWidth())
    }

    //  ── پایان: کارها ──
    ir.vil3ntec.tohid.ui.UpdateChip()
    Spacer(Modifier.width(DeskSpace.x1))
    Bell(board, onOpen)
    //  OPS-06: «راهنما» ِ همین صفحه — همان متنِ تأییدشدهٔ F1 در برگه، بی اینترنت؛ صفحهٔ بی راهنمای آماده ⇒ هیچ دکمه
    val guide = ir.vil3ntec.tohid.core.guide.ScreenGuides.forScreen(ir.vil3ntec.tohid.ui.AppNav.screen.value)
    var guideOpen by remember { mutableStateOf(false) }
    if (guide != null) {
      HeaderIcon(Icons.AutoMirrored.Outlined.HelpOutline, ir.vil3ntec.tohid.core.i18n.StrKit.guide) { guideOpen = true }
      if (guideOpen) ir.vil3ntec.tohid.ui.v3.V3GuideSheet(guide) { guideOpen = false }
    }
    //  میان‌برها — همیشه پیدا (F1)
    HeaderIcon(Icons.Outlined.Keyboard, StrShell.keyboardShortcuts, shortcut = "F1") { ir.vil3ntec.tohid.ui.AppNav.help.value = true }
    if (!onSale) {
      Spacer(Modifier.width(DeskSpace.x2))
      DeskButton(StrShell.newSale, onClick = { onOpen("sale") }, icon = Icons.Filled.Add, kind = DeskButtonKind.PRIMARY, shortcut = "Ctrl+2")
    }
    Spacer(Modifier.width(DeskSpace.x3))
    UserChip(onOpen)
  }
}

/** کادرِ نمایشیِ جست‌وجو که پنجرهٔ جست‌وجوی سریع (Ctrl+K) را باز می‌کند */
@Composable
private fun SearchBox(onSearch: () -> Unit, modifier: Modifier) {
  val c = Desk.colors
  Row(
    modifier
      .height(DeskSize.control)
      .border(DeskSize.hairline, c.source.borderInput, DeskRadius.controlShape)
      .deskClickable(label = StrNav.searchPlaceholder, rest = c.app, onClick = onSearch)
      .padding(horizontal = DeskSpace.x3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Search, null, tint = c.icon, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(DeskSpace.x2))
    Text(StrNav.searchPlaceholder, Modifier.weight(1f), color = c.inkSecondary, style = DeskType.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.width(DeskSpace.x2))
    DeskShortcut("Ctrl+K")
  }
}

/** دکمهٔ تک‌آیکنِ ۳۶dp با راهنمای هاور (نام + میان‌بر) و نامِ صفحه‌خوان */
@Composable
private fun HeaderIcon(icon: ImageVector, label: String, tint: Color? = null, shortcut: String? = null, onClick: () -> Unit) {
  val c = Desk.colors
  DeskTooltip(label, shortcut) {
    Box(
      Modifier.size(DeskSize.control).deskClickable(label = label, role = Role.Button, onClick = onClick),
      contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = tint ?: c.icon, modifier = Modifier.size(20.dp)) }
  }
}

/** زنگِ هشدارها — شمار روی زنگ و فهرستِ کوتاه با مقصدِ هر کدام */
@Composable
private fun Bell(board: DashboardEngine.Alerts, onOpen: (String) -> Unit) {
  val c = Desk.colors
  val s = ir.vil3ntec.tohid.ui.theme.Shop.colors
  var bell by remember { mutableStateOf(false) }
  Box {
    DeskTooltip(Str.alerts) {
      Box(Modifier.size(DeskSize.control).deskClickable(label = Str.alerts, role = Role.Button) { bell = true }, contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.NotificationsNone, Str.alerts, tint = c.icon, modifier = Modifier.size(20.dp))
        if (board.alertCount > 0) DeskBadge(board.alertCount, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = 3.dp))
      }
    }
    DropdownMenu(expanded = bell, onDismissRequest = { bell = false }) {
      @Composable fun item(label: String, n: Int, color: Color, to: String) {
        DropdownMenuItem(
          text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.size(8.dp).clip(CircleShape).background(if (n > 0) color else s.success))
              Spacer(Modifier.width(8.dp))
              Text(label, Modifier.width(180.dp), style = DeskType.body)
              Text(n.toString(), style = DeskType.number, color = if (n > 0) color else c.inkSecondary)
            }
          },
          onClick = { bell = false; onOpen(to) },
        )
      }
      item(Str.outOfStock, board.outOfStock.size, s.danger, "dashboard")
      item(Str.lowStock, board.lowStock.size, s.warning, "dashboard")
      item(StrNav.nearExpiry, board.expiring.size, s.warning, "dashboard")
      item(Str.overdue60, board.debtOverdue.size, s.danger, "debtors")
    }
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
  val c = Desk.colors
  val name = remember(context) { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim() }
  val role = DeskMenu.roleLabel(remember(context) { ir.vil3ntec.tohid.data.ShopRole.current(context) })
  val shown = name.ifEmpty { StrNav.noAccount }
  Row(
    Modifier
      .height(DeskSize.control + 4.dp)
      .deskClickable(label = Str.myAccount, shape = DeskRadius.pillShape) { onOpen("profile") }
      .padding(start = 4.dp, end = DeskSpace.x3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(c.selected), contentAlignment = Alignment.Center) {
      Text(shown.take(1), style = DeskType.bodyStrong, color = c.brandInk)
    }
    Spacer(Modifier.width(DeskSpace.x2))
    Column {
      Text(shown, style = DeskType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
      Text(role, style = DeskType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = c.inkSecondary, maxLines = 1)
    }
  }
}

/** خطِ مویی زیرِ سربرگ */
private fun Modifier.bottomLine(color: Color): Modifier = drawBehind {
  val y = size.height - 0.5f
  drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}
