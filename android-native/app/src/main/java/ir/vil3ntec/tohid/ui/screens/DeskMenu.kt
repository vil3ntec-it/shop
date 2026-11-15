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
        if (canAdmin) add(Item("audit", StrNav.auditLogAlt))
        //  پشتیبان‌گیری بخشی از تنظیمات است (فقط صاحب/مدیر)
        if (canAdmin) add(Item("settings", StrNav.settingsBackup))
        add(Item("more", Str.more))
      },
    ),
  )

  /** نامِ نقش برای سربرگ — همان واژه‌های صفحهٔ کارمندان */
  fun roleLabel(role: String): String = when (role) {
    "owner" -> StrNav.roleOwner
    "manager" -> StrNav.roleManager
    else -> StrNav.roleStaff
  }
}
