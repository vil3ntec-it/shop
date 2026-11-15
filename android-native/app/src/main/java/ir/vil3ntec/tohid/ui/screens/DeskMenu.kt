package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrShell

/**
 *  منوی کناریِ کامپیوتر، گروه‌بندی‌شده — v3 §۲۰.۳ («منوی پیشنهادیِ
 *  فروشگاه») و VISUAL-DIRECTION §۳ («کانال‌های فروش»، «در دست ساخت»).
 *
 *  • هر بخشی که در برنامه ساخته شده (`LIVE`) به همان مسیرِ `AppRoot` می‌رود (`target`؛ «آماده‌سازی و
 *    تحویل» = برگهٔ «در حالِ آماده‌سازی» ِ همان صفِ سفارش‌ها).
 *  • بخشِ **ساخته‌شده** که سرور برای این دکان خاموش گفته (`OFF`) با نشانِ «خاموش» دیده می‌شود و زدنش
 *    برگهٔ «این بخش چه می‌کند و چطور روشن می‌شود» را باز می‌کند (`DeskOff`) — نه «در دست ساخت» (صاحب‌کار
 *    ۱۴۰۵/۰۸/۲۳: «مگه اینا رو نساختی؟»).
 *  • «در دست ساخت» (`SOON`، بی‌کلیک — دکمهٔ بی‌اثر ممنوع، §۱۲.۳، §۱۹.۵ بند ۱) فقط برای بخشی که هنوز در
 *    برنامه نیست؛ امروز هیچ ردیفی چنین نیست (`DeskMenuTest`).
 *  • میان‌برهای Ctrl+۱…۹ همان `AppNav.SHORTCUTS` (و `keymap.json`) می‌مانند؛
 *    فقط جای نمایششان در منو گروه‌بندی شده. `DeskMenuTest` می‌پاید که هر
 *    نُه صفحه دقیقاً یک بار با همان کلید بیایند.
 */
object DeskMenu {
  enum class State { LIVE, OFF, SOON }

  data class Item(
    val id: String,
    val label: String,
    val key: String = "",
    val state: State = State.LIVE,
    /** مسیری که زدنِ ردیفِ زنده باز می‌کند؛ ردیفِ خاموش/در دست ساخت هیچ */
    val target: String = if (state == State.LIVE) id else "",
  )

  data class Group(val title: String, val items: List<Item>)

  val SOON_LABEL: String get() = StrNav.soon

  /** برگهٔ صفِ سفارش‌ها برای «آماده‌سازی و تحویل»: قبول‌شده، در حالِ جمع‌آوری و بسته‌شده (`OrderStates.TABS`) */
  const val FULFIL_TAB = "preparing"

  private fun shortcutItem(shortcuts: List<Pair<String, String>>, id: String, label: String?): Item {
    val i = shortcuts.indexOfFirst { it.first == id }
    require(i >= 0) { "$id در AppNav.SHORTCUTS نیست" }  // داده: خطای برنامه‌نویس (شناسهٔ نادرستِ کد، نه کاربر)
    return Item(id, label ?: shortcuts[i].second, "Ctrl+${i + 1}")
  }

  /** بخشِ ساخته‌شده‌ای که برای این دکان خاموش است — شناسه‌اش کلیدِ برگهٔ `DeskOff` است */
  private fun off(id: String, label: String) = Item("off:$id", label, state = State.OFF)

  /**
   *  ردیفِ روشنِ منو. `current` شناسهٔ منوییِ صفحهٔ باز است (`V3Modules.railId`)؛ «آماده‌سازی و تحویل» و
   *  «سفارش‌های آنلاین» هر دو «orders» اند، پس آخرین ردیفِ زده‌شده (`picked`) تا وقتی صفحهٔ باز مالِ همان است
   *  روشن می‌ماند.
   */
  fun selectedId(items: List<Item>, current: String, picked: String?): String {
    val p = items.firstOrNull { it.id == picked && it.state == State.LIVE } ?: return current
    return if (V3Modules.railId(p.target) == current) p.id else current
  }

  /**
   *  @param canAdmin صاحب/مدیر — سابقهٔ عملیات و تنظیمات فقط برای آن‌ها (همان سدِ `AppRoot`)
   *  @param shortcuts همان `AppNav.SHORTCUTS`؛ آزمون از متنِ AppNav.kt می‌دهد (AppNav وضعیتِ Compose دارد)
   */
  fun groups(
    canAdmin: Boolean,
    shortcuts: List<Pair<String, String>> = AppNav.SHORTCUTS,
    ordersLive: Boolean = false,
    storefrontLive: Boolean = false,
    deliveryLive: Boolean = false,
  ): List<Group> {
    return build(canAdmin, ordersLive, storefrontLive, deliveryLive) { id, label -> shortcutItem(shortcuts, id, label) }
  }

  /**
   *  @param ordersLive سرور بخشِ سفارش را برای این دکان روشن کرده (`OrdersLive.enabled`)؛ «آماده‌سازی و تحویل»
   *  هم با همین (برگهٔ «در حالِ آماده‌سازی» ِ صف و میزِ جمع‌آوریِ همان‌جا)
   *  @param storefrontLive سرور ویترین را برای این دکان روشن کرده و کاربر اجازهٔ مدیریتش را دارد
   *  (`StorefrontGate.visible`). خاموش ⇒ ردیفِ «خاموش»
   *  @param deliveryLive سرور پرچمِ `shop.delivery` را روشن گفته و کاربر اجازهٔ دیدن دارد (`DeliveryGate.visible`)
   */
  private fun build(
    canAdmin: Boolean,
    ordersLive: Boolean,
    storefrontLive: Boolean,
    deliveryLive: Boolean,
    page: (String, String?) -> Item,
  ): List<Group> = listOf(
    Group(
      StrNav.groupMain,
      listOf(
        page("dashboard", null),
        if (ordersLive) Item("orders", StrNav.onlineOrders) else off("orders", StrNav.onlineOrders),
        if (ordersLive) Item("fulfil", StrNav.fulfilment, target = OnlinePaths.orders(FULFIL_TAB)) else off("fulfil", StrNav.fulfilment),
        page("products", null),
        page("warehouse", null),
        page("debtors", null),
        page("expenses", null),
        page("sales", null),
        page("purchasing", null),
        page("reports", null),
        Item("team", StrNav.staffShort),
      ),
    ),
    Group(
      StrNav.groupChannels,
      buildList {
        add(page("sale", Str.saleRegister))
        //  تخفیف‌ها (PRM) — صاحب و مدیر؛ کلیدِ دکان درونِ همان صفحه است
        if (canAdmin) add(Item("promos", StrShell.promos))
        add(if (storefrontLive) Item("storefront", StrNav.storefrontMarket) else off("storefront", StrNav.storefrontMarket))
        add(if (deliveryLive) Item("delivery", StrNav.deliveryDeskCourier) else off("delivery", StrNav.deliveryDeskCourier))
      },
    ),
    Group(
      StrNav.groupAdmin,
      buildList {
        add(Item("admin", Str.adminCenter, "Ctrl+M"))
        add(Item("receipts", Str.receipts))
        add(Item("devices", Str.devices))
        //  «سابقهٔ عملیات» از ۶۳w در برنامه نیست (صاحب‌کار ۱۴۰۵/۰۸/۲۴)
        //  پشتیبان‌گیری بخشی از تنظیمات است (فقط صاحب/مدیر)
        if (canAdmin) add(Item("settings", StrNav.settingsBackup))
        add(Item("more", Str.more))
      },
    ),
  )

  /**
   *  منوی کناریِ **کامپیوتر** (`claude/e-desk-d1`، فقط `Platform.DESKTOP`؛ تبلتِ اندروید همان [groups] را دارد): همان
   *  بخش‌های [groups] به‌اضافهٔ هر بخشی که «بیشتر» ِ گوشی دارد (`MoreLayout`/`V3Modules.moreGroups`) — هیچ بخشی کم نمی‌شود —
   *  در گروه‌های «بیشتر» (`MoreGroup`) با یک گروهِ «فروشِ آنلاین»: خانه · فروش و مشتری · فروشِ آنلاین · کالا و خرید ·
   *  پول و گزارش · دکان · اشتراک و پشتیبانی. «تنظیمات» و «همهٔ بخش‌ها» پایینِ منو ([sidebarFooter]). همان شناسه، همان مسیر
   *  (`target`)، همان میان‌برها (`AppNav.SHORTCUTS`، Ctrl+M) و همان حالتِ «خاموش» ِ [groups].
   *  بخشِ ماژولِ تازه‌ای که این‌جا نامش نیامده (پرچمِ ماژول) خودبه‌خود تهِ گروهِ «بیشتر» ِ خودش می‌نشیند ([extras]).
   *
   *  @param chatLive سرور گفت‌وگو را برای این دکان روشن گفته (`ShopChatSync.config.enabled`)
   *  @param extras ورودی‌های «بیشتر» که این کاربر می‌بیند (`V3Modules.entries(Place.MORE, access)`) — شناسه، نام، مسیر و گروه
   */
  fun sidebar(
    canAdmin: Boolean,
    shortcuts: List<Pair<String, String>> = AppNav.SHORTCUTS,
    ordersLive: Boolean = false,
    storefrontLive: Boolean = false,
    deliveryLive: Boolean = false,
    chatLive: Boolean = false,
    extras: List<Extra> = emptyList(),
  ): List<Group> {
    fun page(id: String, label: String? = null) = shortcutItem(shortcuts, id, label)
    val fixed = listOf(
      Group("", listOf(page("dashboard"))),
      Group(
        ir.vil3ntec.tohid.core.nav.MoreGroup.SALES.title.text,
        buildList {
          add(page("sale", Str.saleRegister))
          add(page("sales"))
          add(page("debtors"))
          add(Item("receipts", Str.receipts))
          if (canAdmin) add(Item("promos", StrShell.promos))
        },
      ),
      Group(
        ir.vil3ntec.tohid.core.i18n.StrDesk.groupOnline,
        buildList {
          add(if (ordersLive) Item("orders", StrNav.onlineOrders) else off("orders", StrNav.onlineOrders))
          add(if (ordersLive) Item("fulfil", StrNav.fulfilment, target = OnlinePaths.orders(FULFIL_TAB)) else off("fulfil", StrNav.fulfilment))
          add(if (storefrontLive) Item("storefront", StrNav.storefrontMarket) else off("storefront", StrNav.storefrontMarket))
          add(if (deliveryLive) Item("delivery", StrNav.deliveryDeskCourier) else off("delivery", StrNav.deliveryDeskCourier))
          if (chatLive) add(Item("chat", Str.customerChat, target = OnlinePaths.CHAT))
        },
      ),
      Group(ir.vil3ntec.tohid.core.nav.MoreGroup.STOCK.title.text, listOf(page("products"), page("warehouse"), page("purchasing"))),
      Group(ir.vil3ntec.tohid.core.nav.MoreGroup.MONEY.title.text, listOf(page("expenses"), page("reports"))),
      Group(
        ir.vil3ntec.tohid.core.nav.MoreGroup.SHOP.title.text,
        listOf(Item("admin", Str.adminCenter, "Ctrl+M"), Item("team", StrNav.staffShort), Item("devices", Str.devices)),
      ),
      Group(
        ir.vil3ntec.tohid.core.nav.MoreGroup.ACCOUNT.title.text,
        listOf(
          Item("myplan", Str.mySubscription, target = ir.vil3ntec.tohid.ui.v3.account.AccountPaths.SUBSCRIPTION),
          Item("support", Str.support, target = ir.vil3ntec.tohid.ui.v3.account.AccountPaths.SUPPORT),
          Item("whats-new", ir.vil3ntec.tohid.core.i18n.StrSettings.whatsNew, target = WHATS_NEW_ROUTE),
          Item("app", ir.vil3ntec.tohid.core.i18n.StrAppData.versionUpdates, target = MoreLayout.APP),
        ),
      ),
    )
    //  ماژولِ تازه (یا پرچمِ ماژول) ⇒ تهِ گروهِ خودش؛ آنچه نامش این‌جا هست تکرار نمی‌شود
    val known = fixed.flatMap { it.items }.flatMap { listOf(it.id, it.id.removePrefix("off:")) }.toSet() + COVERED
    val more = extras.filter { it.id !in known }
    if (more.isEmpty()) return fixed
    return fixed.map { g ->
      val add = more.filter { it.group.title.text == g.title }.map { Item(it.id, it.label, target = it.route) }
      if (add.isEmpty()) g else g.copy(items = g.items + add)
    }
  }

  /** ردیف‌های پایینِ منوی کامپیوتر (همیشه پیدا، بیرون از پیمایش): تنظیمات (صاحب/مدیر) و «همهٔ بخش‌ها» */
  fun sidebarFooter(canAdmin: Boolean): List<Item> = buildList {
    if (canAdmin) add(Item("settings", StrNav.settingsBackup))
    add(Item("more", ir.vil3ntec.tohid.core.i18n.StrDesk.allSections))
  }

  /** یک ورودیِ «بیشتر» برای [sidebar] — بی Compose تا آزمونِ خالص بسازدش */
  data class Extra(val id: String, val label: String, val route: String, val group: ir.vil3ntec.tohid.core.nav.MoreGroup)

  /** شناسهٔ ماژول ⇐ ردیفِ هم‌معنای منو («تاریخچه» همان `sales` ِ Ctrl+6) */
  private val COVERED = setOf("history")

  /** نامِ نقش برای سربرگ — همان واژه‌های صفحهٔ کارمندان */
  fun roleLabel(role: String): String = when (role) {
    "owner" -> StrNav.roleOwner
    "manager" -> StrNav.roleManager
    else -> StrNav.roleStaff
  }
}
