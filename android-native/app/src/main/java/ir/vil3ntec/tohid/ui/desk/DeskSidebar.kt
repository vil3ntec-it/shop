package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDesk
import ir.vil3ntec.tohid.core.i18n.StrDeskOff
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.screens.DeliveryGate
import ir.vil3ntec.tohid.ui.screens.DeskMenu
import ir.vil3ntec.tohid.ui.screens.DeskOff
import ir.vil3ntec.tohid.ui.screens.DeskOffSheet
import ir.vil3ntec.tohid.ui.screens.MoreLayout
import ir.vil3ntec.tohid.ui.screens.RailMode
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import ir.vil3ntec.tohid.ui.v3.nav.ModuleFlags
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules

/**
 *  منوی کناریِ کامپیوتر (`claude/e-desk-d1`) — جای ستونِ ۲۰۰dp ِ پیشین روی ویندوز/مک/لینوکس (تبلتِ اندروید همان `DeskRail`):
 *  • همیشه با نوشته (۲۴۰dp)، در گروه‌های «بیشتر» ([DeskMenu.sidebar])؛ هر ردیف کاشیِ رنگِ بخشِ خودش ([DeskHues])، نام و
 *    شمارِ هشدار. ردیفِ باز با زمینهٔ نرمِ همان رنگ، نوشتهٔ پررنگ و نوارِ رنگیِ لبه.
 *  • جمع‌شونده به ستونِ آیکنِ ۷۲dp (انتخابِ کاربر در `DesktopPrefs` ⇐ `desk_rail` ِ `AppRoot`)؛ پنجرهٔ باریک‌تر از
 *    `DeskSize.sidebarAutoCompactBelow` خودبه‌خود جمع (انتخاب دست نمی‌خورد). «پنهان» همان حالتِ پیشین است (☰ ِ سربرگ).
 *  • زیرِ ماوس، Tab/Enter ِ صفحه‌کلید و راهنمای هاور با میان‌بر (Ctrl+۱…۹، Ctrl+M). «تنظیمات» و «همهٔ بخش‌ها» همیشه پایین.
 *  • بخشِ «خاموش» همان برگهٔ «چطور روشن می‌شود» (`DeskOff`)؛ در حالتِ جمع، مثلِ پیش، نمی‌آید.
 *
 *  @param path مسیرِ کاملِ صفحهٔ باز (برای روشن کردنِ ردیفِ دقیق وقتی چند ردیف یک ریشه دارند)
 */
@Composable
fun DeskSidebar(
  current: String,
  onPick: (String) -> Unit,
  compact: Boolean = false,
  mode: String = RailMode.FULL,
  onMode: (String) -> Unit = {},
  badges: Map<String, Int> = emptyMap(),
  path: String = "",
) {
  val c = Desk.colors
  val context = LocalContext.current
  val canAdmin = ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context)
  val owner = ir.vil3ntec.tohid.data.ShopRole.current(context) == ir.vil3ntec.tohid.data.ShopRole.OWNER
  val ordersLive = ir.vil3ntec.tohid.ui.OrdersLive.enabled
  val chatLive = ir.vil3ntec.tohid.data.ShopChatSync.config.value?.enabled == true
  val flags = ModuleFlags.on.value
  val storefront = StorefrontGate.visible
  val delivery = DeliveryGate.visible
  val groups = remember(canAdmin, ordersLive, storefront, delivery, chatLive, flags, ir.vil3ntec.tohid.core.i18n.I18n.lang) {
    val access = MoreLayout.access(canAdmin, ordersLive, chatLive, storefront, delivery, flags)
    val extras = V3Modules.entries(Place.MORE, access).mapNotNull { e -> e.group?.let { DeskMenu.Extra(e.id, e.label(), e.route, it) } }
    DeskMenu.sidebar(canAdmin, ordersLive = ordersLive, storefrontLive = storefront, deliveryLive = delivery, chatLive = chatLive, extras = extras)
  }
  val footer = remember(canAdmin, ir.vil3ntec.tohid.core.i18n.I18n.lang) { DeskMenu.sidebarFooter(canAdmin) }
  val all = remember(groups, footer) { groups.flatMap { it.items } + footer }
  var picked by rememberSaveable { mutableStateOf<String?>(null) }
  val selected = remember(all, current, path, picked) { DeskSidebarModel.selected(all, current, path, picked) }
  LaunchedEffect(current, path) { if (picked != null && selected != picked) picked = null }
  var offSheet by remember { mutableStateOf<String?>(null) }
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val pick: (DeskMenu.Item) -> Unit = { item -> picked = item.id; onPick(item.target) }

  Column(
    Modifier
      .width(if (compact) DeskSize.sidebarCompact else DeskSize.sidebar)
      .fillMaxHeight()
      .background(c.chrome)
      .sideEdge(c.hairline)
      .semantics { contentDescription = StrDesk.mainNav },
  ) {
    Brand(compact)
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (compact) DeskSpace.x3 else DeskSpace.x3, vertical = DeskSpace.x2),
    ) {
      groups.forEachIndexed { gi, group ->
        val shown = if (compact) group.items.filter { it.state == DeskMenu.State.LIVE } else group.items
        if (shown.isEmpty()) return@forEachIndexed
        if (group.title.isNotEmpty()) {
          item(key = "g:" + group.title) {
            if (compact) DeskDivider(Modifier.padding(vertical = DeskSpace.x2, horizontal = DeskSpace.x2))
            else Text(
              group.title,
              Modifier.fillMaxWidth().padding(start = DeskSpace.x3, end = DeskSpace.x3, top = if (gi == 0) DeskSpace.x1 else DeskSpace.x4, bottom = DeskSpace.x1)
                .semantics { heading() },
              style = DeskType.caption,
              color = c.inkSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        items(shown, key = { "i:" + it.id }) { item ->
          when (item.state) {
            DeskMenu.State.LIVE -> SideItem(item, selected == item.id, badges[item.id] ?: 0, compact) { pick(item) }
            DeskMenu.State.OFF -> OffItem(item) { offSheet = item.id }
            //  «در دست ساخت»: دیده می‌شود ولی کلیک ندارد (امروز هیچ ردیفی چنین نیست — `DeskMenuTest`)
            DeskMenu.State.SOON -> SoonItem(item)
          }
        }
      }
    }
    //  پایینِ منو: تنظیمات و «همهٔ بخش‌ها»، سپس جمع/باز و پنهان
    DeskDivider(color = c.hairline)
    Column(Modifier.padding(horizontal = DeskSpace.x3, vertical = DeskSpace.x2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      footer.forEach { item -> SideItem(item, selected == item.id, 0, compact) { pick(item) } }
      val canToggle = mode == RailMode.FULL || mode == RailMode.COMPACT
      if (canToggle) {
        Spacer(Modifier.height(DeskSpace.x1))
        if (compact) {
          ModeButton(if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, StrShell.openMenu, Modifier.fillMaxWidth()) { onMode(RailMode.FULL) }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
          ModeButton(if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft, StrShell.collapseMenu, Modifier.weight(1f), label = StrShell.collapseMenu) {
            onMode(RailMode.COMPACT)
          }
          Spacer(Modifier.width(DeskSpace.x1))
          ModeButton(Icons.Outlined.VisibilityOff, StrDesk.hideMenu) { onMode(RailMode.HIDDEN) }
        }
      }
    }
  }
  offSheet?.let { id -> DeskOff.info(id, owner, canAdmin)?.let { DeskOffSheet(it, onPick, onDismiss = { offSheet = null }) } }
}

/** انتخابِ ردیفِ روشن — خالص، برای آزمون */
internal object DeskSidebarModel {
  /**
   *  1) ردیفی که همین حالا زده شد و صفحهٔ باز مالِ آن است؛ 2) ردیفی که مسیرش دقیقاً صفحهٔ باز است؛ 3) شناسهٔ منوییِ صفحه
   *  (`V3Modules.railId`)؛ 4) ردیفی که ریشهٔ مسیرش همان ریشه است.
   */
  fun selected(items: List<DeskMenu.Item>, current: String, path: String, picked: String?): String? {
    val live = items.filter { it.state == DeskMenu.State.LIVE }
    fun root(target: String) = runCatching { V3Modules.railId(target) }.getOrDefault(target)
    fun base(p: String) = runCatching { ir.vil3ntec.tohid.core.nav.RoutePaths.normalize(V3Modules.table.canonical(p)) }.getOrDefault(p)
    live.firstOrNull { it.id == picked && root(it.target) == current }?.let { return it.id }
    if (path.isNotEmpty()) {
      val here = base(path)
      live.firstOrNull { base(it.target) == here }?.let { return it.id }
    }
    live.firstOrNull { it.id == current }?.let { return it.id }
    return live.firstOrNull { root(it.target) == current }?.id
  }
}

/** آیکنِ هر ردیف — همان آیکنِ ورودیِ ماژول («بیشتر» ِ گوشی)، و برای ردیف‌های بی‌ماژول آیکنِ هم‌معنا */
internal fun deskIcon(id: String): ImageVector {
  val key = id.removePrefix("off:")
  return when (key) {
    "dashboard", "home" -> Icons.Outlined.Home
    "fulfil" -> Icons.Outlined.Inventory
    "app" -> Icons.Outlined.SystemUpdate
    "settings" -> Icons.Outlined.Settings
    "more" -> Icons.Outlined.Apps
    else -> ENTRY_ICONS[if (key == "sales") "history" else key] ?: Icons.Outlined.MoreHoriz
  }
}

private val ENTRY_ICONS: Map<String, ImageVector> by lazy {
  V3Modules.ALL.flatMap { it.entries }.associate { it.id to it.icon }
}

@Composable
private fun Brand(compact: Boolean) {
  val c = Desk.colors
  Row(
    Modifier.fillMaxWidth().height(DeskSize.header).padding(horizontal = if (compact) 0.dp else DeskSpace.x4),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
  ) {
    Box(Modifier.size(32.dp).clip(DeskRadius.controlShape).background(c.primary), contentAlignment = Alignment.Center) {
      Icon(Icons.Filled.Storefront, contentDescription = if (compact) Str.appName else null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
    }
    if (!compact) {
      Spacer(Modifier.width(DeskSpace.x3))
      Column(Modifier.weight(1f)) {
        Text(Str.appName, style = DeskType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(StrNav.shopManagement, style = DeskType.caption.copy(fontWeight = FontWeight.Medium), color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
  DeskDivider(color = c.hairline)
}

@Composable
private fun SideItem(item: DeskMenu.Item, selected: Boolean, badge: Int, compact: Boolean, onClick: () -> Unit) {
  val c = Desk.colors
  val tone = DeskHues.tone(DeskHues.of(item.id))
  val icon = remember(item.id) { deskIcon(item.id) }
  val tip = item.key.takeIf { it.isNotEmpty() }
  if (compact) {
    DeskTooltip(item.label, tip) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(DeskSize.navItem + 4.dp)
          .deskClickable(label = item.label, selected = selected, selectedFill = tone.tile) { onClick() }
          .semantics { this.selected = selected; contentDescription = item.label },
        contentAlignment = Alignment.Center,
      ) {
        Box(
          Modifier.size(DeskSize.navTile + 4.dp).clip(DeskRadius.tileShape).background(if (selected) tone.solid else tone.tile),
          contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (selected) tone.onSolid else tone.glyph, modifier = Modifier.size(DeskSize.navIcon + 2.dp)) }
        if (badge > 0) DeskBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = 2.dp))
      }
    }
    return
  }
  val row: @Composable () -> Unit = {
    Row(
      Modifier
        .fillMaxWidth()
        .height(DeskSize.navItem)
        .deskClickable(label = item.label, selected = selected, selectedFill = tone.tile) { onClick() }
        .then(if (selected) Modifier.startBar(tone.solid) else Modifier)
        .semantics(mergeDescendants = true) { this.selected = selected }
        .padding(start = DeskSpace.x2, end = DeskSpace.x2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        Modifier.size(DeskSize.navTile).clip(DeskRadius.tileShape).background(if (selected) tone.solid else tone.tile),
        contentAlignment = Alignment.Center,
      ) { Icon(icon, contentDescription = null, tint = if (selected) tone.onSolid else tone.glyph, modifier = Modifier.size(DeskSize.navIcon)) }
      Spacer(Modifier.width(DeskSpace.x3))
      Text(
        item.label,
        Modifier.weight(1f),
        style = if (selected) DeskType.navItem.copy(fontWeight = FontWeight.SemiBold) else DeskType.navItem,
        color = if (selected) tone.ink else c.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (badge > 0) {
        Spacer(Modifier.width(DeskSpace.x1))
        DeskBadge(badge)
      }
    }
  }
  if (tip != null) DeskTooltip(item.label, tip) { row() } else row()
}

/** بخشِ ساخته‌شده‌ای که برای این دکان خاموش است — کم‌رنگ با نشانِ «خاموش»؛ زدن ⇒ برگهٔ «چطور روشن می‌شود» */
@Composable
private fun OffItem(item: DeskMenu.Item, onOpen: () -> Unit) {
  val c = Desk.colors
  Row(
    Modifier
      .fillMaxWidth()
      .height(DeskSize.navItem)
      .deskClickable(label = StrDeskOff.whyOff, onClick = onOpen)
      .semantics { stateDescription = StrDeskOff.badge }
      .padding(start = DeskSpace.x2, end = DeskSpace.x2),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(DeskSize.navTile).clip(DeskRadius.tileShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
      Icon(deskIcon(item.id), contentDescription = null, tint = c.icon, modifier = Modifier.size(DeskSize.navIcon))
    }
    Spacer(Modifier.width(DeskSpace.x3))
    Text(item.label, Modifier.weight(1f, fill = false), style = DeskType.navItem, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.width(DeskSpace.x2))
    Text(
      StrDeskOff.badge,
      Modifier.clip(DeskRadius.pillShape).background(c.surfaceAlt).padding(horizontal = 6.dp, vertical = 1.dp),
      style = DeskType.badge,
      color = c.inkSecondary,
      maxLines = 1,
    )
  }
}

/** بخشِ ساخته‌نشده — بی‌کلیک، با نشانِ «در دست ساخت» (متن + رنگ) */
@Composable
private fun SoonItem(item: DeskMenu.Item) {
  val c = Desk.colors
  Row(Modifier.fillMaxWidth().height(DeskSize.navItem).padding(horizontal = DeskSpace.x2), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(DeskSize.navTile).clip(DeskRadius.tileShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
      Icon(deskIcon(item.id), contentDescription = null, tint = c.icon, modifier = Modifier.size(DeskSize.navIcon))
    }
    Spacer(Modifier.width(DeskSpace.x3))
    Text(item.label, Modifier.weight(1f, fill = false), style = DeskType.navItem, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.width(DeskSpace.x2))
    Text(DeskMenu.SOON_LABEL, style = DeskType.badge, color = c.inkSecondary, maxLines = 1)
  }
}

@Composable
private fun ModeButton(icon: ImageVector, tip: String, modifier: Modifier = Modifier, label: String? = null, onClick: () -> Unit) {
  val c = Desk.colors
  DeskTooltip(tip) {
    Row(
      modifier.height(DeskSize.controlSmall).deskClickable(label = tip, role = Role.Button, onClick = onClick).padding(horizontal = DeskSpace.x2),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = if (label == null) Arrangement.Center else Arrangement.Start,
    ) {
      Icon(icon, contentDescription = if (label == null) tip else null, tint = c.icon, modifier = Modifier.size(18.dp))
      if (label != null) {
        Spacer(Modifier.width(DeskSpace.x2))
        Text(label, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** نوارِ رنگیِ ۳dp لبهٔ آغازِ ردیفِ فعال (در راست‌به‌چپ لبهٔ راست) */
private fun Modifier.startBar(color: Color): Modifier = drawBehind {
  val w = DeskSize.accentBar.toPx()
  val inset = 7.dp.toPx()
  val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
  drawRoundRect(color, topLeft = Offset(x, inset), size = Size(w, size.height - inset * 2), cornerRadius = CornerRadius(w / 2, w / 2))
}

/** خطِ مویی میانِ منو و محتوا (لبهٔ پایانِ منو؛ راست‌به‌چپ ⇒ چپ) */
private fun Modifier.sideEdge(color: Color): Modifier = drawBehind {
  val x = if (layoutDirection == LayoutDirection.Rtl) 0.5f else size.width - 0.5f
  drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
}
