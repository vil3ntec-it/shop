package ir.vil3ntec.tohid.ui.screens

/**
 *  گروه‌های صفحهٔ «بیشتر»ِ گوشی (نسخهٔ ۳، بندِ F: «≡ ⇐ بیشتر») — خالص، بی Compose.
 *
 *  هر بخشی که در نوارِ پایین نیست این‌جا یک ردیف است، در کارت‌های گروهیِ r5؛ و
 *  «تنظیمات» **آخرین** گروه است (ردیف‌هایش گروه‌های خودِ تنظیمات‌اند و صفحهٔ
 *  تنظیمات را روی همان گروه باز می‌کنند). هر مسیری که «بیشترِ» پیشین داشت این‌جا
 *  هم هست (`MoreLayoutTest`)؛ دسترسیِ نقش‌ها همان است: سابقهٔ عملیات، کارمندان و
 *  تنظیمات فقط برای کسی که تنظیمات را باز می‌کند، سفارش‌ها، ویترین و گفت‌وگو فقط
 *  وقتی سرور برای این دکان روشن گفته.
 */
object MoreLayout {
  /** `route` مسیرِ صفحهٔ برنامه است؛ `settingsGroup` یعنی «تنظیمات را روی این گروه باز کن» */
  data class Item(
    val id: String,
    val label: String,
    val route: String,
    val value: String? = null,
    val settingsGroup: String? = null,
  )

  data class Group(val title: String, val items: List<Item>)

  const val SETTINGS = "تنظیمات"
  /** زیرصفحهٔ خودِ «بیشتر»: نسخه، به‌روزرسانی و شمارِ دفتر */
  const val APP = "app"

  fun groups(
    canManage: Boolean,
    ordersOn: Boolean,
    newOrders: Int,
    chatOn: Boolean,
    version: String,
    /** ویترین: سرور روشنش کرده و کاربر حقِ مدیریتش را دارد (`StorefrontGate.visible`) */
    storefrontOn: Boolean = false,
  ): List<Group> = buildList {
    add(Group("فروش و مشتری", buildList {
      if (ordersOn) add(Item("orders", "سفارش‌ها", "orders", if (newOrders > 0) "$newOrders تازه" else null))
      if (storefrontOn) add(Item("storefront", "ویترینِ اینترنتی", "storefront"))
      add(Item("debtors", "قرض‌داران", "debtors"))
      add(Item("sales", "تاریخچهٔ فروش", "sales"))
      add(Item("receipts", "رسیدها", "receipts"))
      if (chatOn) add(Item("chat", "گفت‌وگو با مشتری", "chat"))
    }))
    add(Group("کالا و خرید", listOf(
      Item("warehouse", "انبار", "warehouse"),
      Item("purchasing", "خرید و تأمین‌کننده", "purchasing"),
    )))
    add(Group("پول و گزارش", listOf(
      Item("expenses", "مصارف", "expenses"),
      Item("reports", "گزارش‌ها", "reports"),
    )))
    add(Group("دکان", buildList {
      add(Item("admin", "مرکزِ مدیریت", "admin"))
      if (canManage) add(Item("team", "کارمندان دکان", "team"))
      if (canManage) add(Item("audit", "سابقهٔ عملیات", "audit"))
      add(Item("devices", "دستگاه‌های جانبی", "devices"))
    }))
    add(Group("اشتراک و پشتیبانی", listOf(
      Item("myplan", "اشتراکِ من", "myplan"),
      Item("support", "پشتیبانی", "support"),
      Item(APP, "نسخه و به‌روزرسانی", APP, version),
    )))
    if (canManage) add(Group(SETTINGS, SettingsLayout.GROUPS.map {
      Item("settings:" + it.title, it.title, "settings", settingsGroup = it.title)
    }))
  }
}
