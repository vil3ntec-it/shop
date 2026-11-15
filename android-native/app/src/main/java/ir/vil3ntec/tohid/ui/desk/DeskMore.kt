package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDesk
import ir.vil3ntec.tohid.core.i18n.StrDeskOff
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.screens.DeliveryGate
import ir.vil3ntec.tohid.ui.screens.DeskMenu
import ir.vil3ntec.tohid.ui.screens.DeskOff
import ir.vil3ntec.tohid.ui.screens.DeskOffSheet
import ir.vil3ntec.tohid.ui.screens.MoreLayout
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import ir.vil3ntec.tohid.ui.v3.nav.ModuleFlags
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules

/**
 *  «همهٔ بخش‌ها» ِ کامپیوتر (`more` با `LocalDeskLayout`، e-desk-d1) — جای فهرستِ کشیدهٔ گوشی‌گونه: شبکهٔ کارت‌های بخش (۳ تا
 *  ۴ ستون) در همان گروه‌های منوی کناری ([DeskMenu.sidebar])، هر کارت کاشیِ رنگِ بخش، نام و یک خطِ «چه می‌کند»؛ و برای صاحب/مدیر
 *  گروهِ «تنظیمات» با همان شش دستهٔ «بیشتر» ِ گوشی (`MoreLayout`). بخشِ «خاموش» همان برگهٔ «چطور روشن می‌شود». تهِ صفحه
 *  «وضعیت» (نسخه و شمارِ دفتر) — همان پنلِ پیشینِ این صفحه؛ به‌روزرسانی در کارتِ «نسخه و به‌روزرسانی».
 *  «بیشتر» ِ گوشی (`PhoneMoreScreen`) دست نخورد.
 */
@Composable
fun DeskMoreScreen(d: ShopData, onOpen: (String) -> Unit) {
  val c = Desk.colors
  val context = LocalContext.current
  val canManage = remember { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }
  LaunchedEffect(Unit) {
    ir.vil3ntec.tohid.data.ShopChatSync.refreshConfig(context)
    ModuleFlags.load(context)
  }
  val ordersLive = ir.vil3ntec.tohid.ui.OrdersLive.enabled
  val newOrders = ir.vil3ntec.tohid.ui.OrdersLive.newCount
  val chatLive = ir.vil3ntec.tohid.data.ShopChatSync.config.value?.enabled == true
  val flags = ModuleFlags.on.value
  val storefront = StorefrontGate.visible
  val delivery = DeliveryGate.visible
  val sections = remember(canManage, ordersLive, newOrders, chatLive, flags, storefront, delivery, I18n.lang) {
    DeskMoreModel.sections(canManage, ordersLive, newOrders, chatLive, storefront, delivery, flags)
  }
  var offSheet by remember { mutableStateOf<String?>(null) }
  val owner = remember { ir.vil3ntec.tohid.data.ShopRole.current(context) == ir.vil3ntec.tohid.data.ShopRole.OWNER }

  BoxWithConstraints(Modifier.fillMaxSize().background(c.app), contentAlignment = Alignment.TopCenter) {
    val usable = (maxWidth.coerceAtMost(DeskSize.contentMax) - DeskSpace.page * 2)
    val cols = (usable.value / 300f).toInt().coerceIn(2, 4)
    LazyVerticalGrid(
      columns = GridCells.Fixed(cols),
      modifier = Modifier.widthIn(max = DeskSize.contentMax).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = DeskSpace.page, vertical = DeskSpace.page),
      horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap),
      verticalArrangement = Arrangement.spacedBy(DeskSpace.gap),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }, key = "lead") {
        Text(StrDesk.allSectionsLead, style = DeskType.body, color = c.inkSecondary, maxLines = 2)
      }
      sections.forEach { g ->
        item(span = { GridItemSpan(maxLineSpan) }, key = "g:" + g.title) {
          Text(
            g.title,
            Modifier.padding(top = DeskSpace.x2).semantics { heading() },
            style = DeskType.sectionTitle,
            color = c.ink,
            maxLines = 1,
          )
        }
        items(g.cards, key = { "c:" + it.id }) { card ->
          SectionCard(card) { if (card.off) offSheet = card.id else onOpen(card.route) }
        }
      }
      item(span = { GridItemSpan(maxLineSpan) }, key = "status") { StatusCard(d) }
    }
  }
  offSheet?.let { id -> DeskOff.info(id, owner, canManage)?.let { DeskOffSheet(it, onOpen, onDismiss = { offSheet = null }) } }
}

/** کارت‌ها و گروه‌های «همهٔ بخش‌ها» — خالص (آزمونِ «هیچ بخشی از «بیشتر» ِ گوشی گم نشد») */
internal object DeskMoreModel {
  data class Card(
    val id: String,
    val title: String,
    val line: String?,
    val route: String,
    val hueKey: String,
    val off: Boolean = false,
    val badge: String? = null,
    val icon: ImageVector? = null,
  )

  data class Section(val title: String, val cards: List<Card>)

  /** همان گروه‌های منوی کناری (بی «خانه») و گروهِ «تنظیمات» ِ «بیشتر» ِ گوشی برای صاحب/مدیر */
  fun sections(
    canManage: Boolean,
    ordersLive: Boolean,
    newOrders: Int,
    chatLive: Boolean,
    storefront: Boolean,
    delivery: Boolean,
    flags: Set<String>,
  ): List<Section> {
    val access = MoreLayout.access(canManage, ordersLive, chatLive, storefront, delivery, flags)
    val extras = V3Modules.entries(Place.MORE, access).mapNotNull { e -> e.group?.let { DeskMenu.Extra(e.id, e.label(), e.route, it) } }
    val groups = DeskMenu.sidebar(canManage, ordersLive = ordersLive, storefrontLive = storefront, deliveryLive = delivery, chatLive = chatLive, extras = extras)
    val moreLabels = V3Modules.entries(Place.MORE, access).associate { it.id to it.label() }
    val badges = if (newOrders > 0) mapOf("orders" to ir.vil3ntec.tohid.core.i18n.StrCatalogPages.freshN(plain(newOrders))) else emptyMap()
    val fromMenu = groups.filter { it.title.isNotEmpty() }.map { g ->
      Section(g.title, g.items.filter { it.state != DeskMenu.State.SOON }.map { item ->
        val off = item.state == DeskMenu.State.OFF
        Card(
          id = item.id,
          //  نامِ کاملِ همان ردیفِ «بیشتر» ِ گوشی وقتی هست («خرید و تأمین‌کننده»)؛ منو نامِ کوتاهِ میان‌بر را نگه می‌دارد
          title = moreLabels[if (item.id == "sales") "history" else item.id] ?: item.label,
          line = if (off) StrDesk.offNote else lineOf(item.id),
          route = item.target,
          hueKey = item.id,
          off = off,
          badge = badges[item.id],
        )
      })
    }
    if (!canManage) return fromMenu
    val settings = MoreLayout.groups(
      canManage = true, ordersOn = ordersLive, newOrders = newOrders, chatOn = chatLive, version = BuildConfig.VERSION_NAME,
      storefrontOn = storefront, deliveryOn = delivery, flags = flags,
    ).firstOrNull { it.title == MoreLayout.SETTINGS }
    val cards = settings?.items.orEmpty().map { i ->
      Card(i.id, i.label, i.settingsGroup?.let { SettingsLayout.groupLine(it) }, i.route, i.id, icon = i.icon)
    }
    return if (cards.isEmpty()) fromMenu else fromMenu + Section(Str.settings, cards)
  }

  /** «این بخش چه می‌کند» — همان نوشته‌های «بیشتر» ِ پیشینِ کامپیوتر، و برای چند بخشِ تازه از `StrDesk` */
  fun lineOf(id: String): String? = when (id) {
    "sale" -> StrDesk.saleSub
    "sales" -> StrNav.salesHistorySub
    "debtors" -> StrDesk.debtorsSub
    "receipts" -> StrNav.receiptsSub
    "promos" -> StrShell.promosSub
    "orders" -> StrNav.ordersSub
    "fulfil" -> StrDesk.fulfilSub
    "storefront" -> StrNav.storefrontSub
    "delivery" -> StrNav.deliverySub
    "chat" -> StrNav.customerChatSub
    "products" -> StrDesk.productsSub
    "warehouse" -> StrNav.warehouseSub
    "purchasing" -> StrNav.purchasingSub
    "expenses" -> StrNav.expensesSub
    "reports" -> StrNav.reportsSub
    "admin" -> StrNav.adminSub
    "team" -> StrNav.staffSub
    "devices" -> StrNav.devicesSub
    "myplan" -> StrNav.mySubscriptionSub
    "support" -> StrNav.supportSub
    "whats-new" -> StrDesk.whatsNewSub
    "app" -> StrDesk.updatesSub
    "settings" -> StrDesk.settingsSub
    "dashboard" -> StrDesk.homeSub
    else -> null
  }
}

@Composable
private fun SectionCard(card: DeskMoreModel.Card, onClick: () -> Unit) {
  val c = Desk.colors
  val tone = DeskHues.tone(DeskHues.of(card.hueKey))
  val icon = card.icon ?: deskIcon(card.id)
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 76.dp)
      .deskCard(c)
      .deskClickable(label = if (card.off) StrDeskOff.whyOff else card.title, shape = DeskRadius.cardShape, onClick = onClick)
      .padding(horizontal = DeskSpace.x4, vertical = DeskSpace.x3 + 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (card.off) {
      Box(Modifier.width(DeskSize.tileLarge).height(DeskSize.tileLarge).background(c.surfaceAlt, DeskRadius.tileShape), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.width(DeskSize.tileIcon).height(DeskSize.tileIcon))
      }
    } else DeskIconTile(icon, tone, size = DeskSize.tileLarge, iconSize = 22.dp)
    Spacer(Modifier.width(DeskSpace.x3 + 2.dp))
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          card.title,
          Modifier.weight(1f, fill = false),
          style = DeskType.bodyStrong.copy(fontSize = DeskType.sectionTitle.fontSize, lineHeight = DeskType.sectionTitle.lineHeight),
          color = if (card.off) c.inkSecondary else c.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (card.off) {
          Spacer(Modifier.width(DeskSpace.x2))
          Text(StrDeskOff.badge, Modifier.background(c.surfaceAlt, DeskRadius.pillShape).padding(horizontal = 6.dp, vertical = 1.dp), style = DeskType.badge, color = c.inkSecondary)
        }
        if (card.badge != null) {
          Spacer(Modifier.width(DeskSpace.x2))
          Text(card.badge, Modifier.background(tone.tile, DeskRadius.pillShape).padding(horizontal = 6.dp, vertical = 1.dp), style = DeskType.badge, color = tone.ink, maxLines = 1)
        }
      }
      if (card.line != null) {
        Spacer(Modifier.height(2.dp))
        Text(card.line, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** «وضعیت» — نسخه و شمارِ دفتر (همان پنلِ پیشینِ «بیشتر» ِ کامپیوتر)، یک ردیفِ عدد */
@Composable
private fun StatusCard(d: ShopData) {
  val c = Desk.colors
  val rows = listOf(
    StrNav.versionLabel to BuildConfig.VERSION_NAME,
    StrNav.goods to plain(d.products.size),
    Str.invoices to plain(d.sales.size),
    Str.debtors to plain(d.debtors.size),
    Str.expenses to plain(d.expenses.size),
    StrNav.suppliers to plain(d.suppliers.size),
  )
  Column(Modifier.padding(top = DeskSpace.x2)) {
    Text(StrNav.status, Modifier.semantics { heading() }, style = DeskType.sectionTitle, color = c.ink)
    Spacer(Modifier.height(DeskSpace.gap))
    Row(Modifier.fillMaxWidth().deskCard(c).padding(vertical = DeskSpace.x4)) {
      rows.forEachIndexed { i, (label, value) ->
        if (i > 0) Box(Modifier.width(DeskSize.hairline).height(40.dp).background(c.divider).align(Alignment.CenterVertically))
        Column(Modifier.weight(1f).padding(horizontal = DeskSpace.x4)) {
          Text(label, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(value, style = DeskType.kpiSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
