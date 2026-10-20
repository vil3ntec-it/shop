package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.Str

/**
 *  منوی کناریِ کامپیوتر، گروه‌بندی‌شده — v3 §۲۰.۳ («منوی پیشنهادیِ
 *  فروشگاه») و VISUAL-DIRECTION §۳ («کانال‌های فروش»، «در دست ساخت»).
 *
 *  • هر بخشی که در برنامه ساخته شده (`LIVE`) به همان مسیرِ `AppRoot` می‌رود.
 *  • بخشی که هنوز نیست (`SOON`) با برچسبِ «در دست ساخت» دیده می‌شود و
 *    **کلیک‌پذیر نیست** — دکمهٔ بی‌اثر ممنوع (§۱۲.۳، §۱۹.۵ بند ۱).
 *  • میان‌برهای Ctrl+۱…۹ همان `AppNav.SHORTCUTS` (و `keymap.json`) می‌مانند؛
 *    فقط جای نمایششان در منو گروه‌بندی شده. `DeskMenuTest` می‌پاید که هر
 *    نُه صفحه دقیقاً یک بار با همان کلید بیایند.
 */
object DeskMenu {
  enum class State { LIVE, SOON }

  data class Item(
    val id: String,
    val label: String,
    val key: String = "",
    val state: State = State.LIVE,
  )

  data class Group(val title: String, val items: List<Item>)

  val SOON_LABEL: String get() = StrNav.soon

  private fun shortcutItem(shortcuts: List<Pair<String, String>>, id: String, label: String?): Item {
    val i = shortcuts.indexOfFirst { it.first == id }
    require(i >= 0) { "$id در AppNav.SHORTCUTS نیست" }
    return Item(id, label ?: shortcuts[i].second, "Ctrl+${i + 1}")
  }

  private fun soon(id: String, label: String) = Item("soon:$id", label, state = State.SOON)

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
   *  @param ordersLive سرور بخشِ سفارش را برای این دکان روشن کرده (`OrdersLive.enabled`)
   *  @param storefrontLive سرور ویترین را برای این دکان روشن کرده و کاربر اجازهٔ مدیریتش را دارد
   *  (`StorefrontGate.visible`). خاموش ⇒ همان ردیفِ «در دست ساخت»
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
        if (ordersLive) Item("orders", StrNav.onlineOrders) else soon("orders", StrNav.onlineOrders),
        soon("fulfil", StrNav.fulfilment),
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
      listOf(
        page("sale", Str.saleRegister),
        if (storefrontLive) Item("storefront", StrNav.storefrontMarket) else soon("storefront", StrNav.storefrontMarket),
        if (deliveryLive) Item("delivery", StrNav.deliveryDeskCourier) else soon("delivery", StrNav.delivery),
      ),
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
