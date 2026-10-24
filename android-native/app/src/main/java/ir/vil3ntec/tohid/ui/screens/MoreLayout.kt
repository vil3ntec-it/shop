package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material.icons.outlined.SystemUpdate
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths

/**
 *  گروه‌های صفحهٔ «بیشتر»ِ گوشی (نسخهٔ ۳) — خالص، از روی **ماژول‌ها** (`V3Modules`).
 *
 *  هر ورودیِ `Place.MORE`ِ هر ماژول این‌جا یک ردیف است، زیرِ گروهِ `MoreGroup`ِ خودش
 *  و با مسیرِ **دقیقِ** همان صفحه؛ ماژولِ تازه بی دست زدن به این فایل این‌جا
 *  می‌آید. «نسخه و به‌روزرسانی» ته گروهِ اشتراک، و «تنظیمات» **آخرین** گروه —
 *  ردیف‌هایش صفحهٔ همان گروهِ تنظیمات را باز می‌کنند (`settings/security`، …).
 *  دسترسیِ نقش‌ها همان است (`MoreLayoutTest`): سابقه، کارمندان و تنظیمات فقط برای
 *  مدیر؛ سفارش، ویترین، گفت‌وگو و میزِ تحویل فقط وقتی سرور روشن گفته.
 */
object MoreLayout {
  /** `route` مسیرِ دقیقِ صفحه است؛ `settingsGroup` یعنی «صفحهٔ این گروهِ تنظیمات» */
  data class Item(
    val id: String,
    val label: String,
    val route: String,
    val value: String? = null,
    val settingsGroup: String? = null,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val hue: ir.vil3ntec.tohid.ui.v3.V3Hue? = null,
  )

  data class Group(val title: String, val items: List<Item>)

  const val SETTINGS = "تنظیمات"
  /** «نسخه و به‌روزرسانی» — برای همه (کارمند هم)، مستقیم روی کنترل‌های به‌روزرسانی */
  const val APP = SettingsPaths.UPDATES

  fun access(
    canManage: Boolean,
    ordersOn: Boolean,
    chatOn: Boolean,
    storefrontOn: Boolean,
    deliveryOn: Boolean,
    flags: Set<String> = emptySet(),
  ): Access = Access(
    granted = buildSet {
      if (canManage) { add(Need.MANAGE); add(Need.AUDIT) }
      if (ordersOn) add(Need.ORDERS_LIVE)
      if (chatOn) add(Need.CHAT)
      if (storefrontOn) add(Need.STOREFRONT)
      if (deliveryOn) add(Need.DELIVERY)
    },
    flags = flags,
  )

  fun groups(
    canManage: Boolean,
    ordersOn: Boolean,
    newOrders: Int,
    chatOn: Boolean,
    version: String,
    /** ویترین: سرور روشنش کرده و کاربر حقِ مدیریتش را دارد (`StorefrontGate.visible`) */
    storefrontOn: Boolean = false,
    /** میزِ تحویل: سرور روشنش کرده (`DeliveryGate.visible`) */
    deliveryOn: Boolean = false,
    /** پرچم‌های ماژولِ روشن (`ModuleFlags`) */
    flags: Set<String> = emptySet(),
  ): List<Group> {
    val access = access(canManage, ordersOn, chatOn, storefrontOn, deliveryOn, flags)
    val badges = buildMap { if (newOrders > 0) put("orders", "$newOrders تازه") }
    val fromModules = V3Modules.moreGroups(access).map { (g, entries) ->
      val items = entries.map { e -> Item(e.id, e.label(), e.route, badges[e.id] ?: e.badge?.invoke(), icon = e.icon, hue = e.hue) }
      Group(g.title.text, if (g == MoreGroup.ACCOUNT) items + appItem(version) else items)
    }
    val withApp = if (fromModules.any { it.items.any { i -> i.route == APP } }) fromModules
    else fromModules + Group(MoreGroup.ACCOUNT.title.text, listOf(appItem(version)))
    return if (!canManage) withApp else withApp + Group(SETTINGS, SettingsLayout.GROUPS.map {
      Item("settings:" + it.title, it.title, SettingsPaths.group(it.title), settingsGroup = it.title, icon = settingsIcon(it.title), hue = settingsHue(it.title))
    })
  }

  private fun appItem(version: String) =
    Item("app", "نسخه و به‌روزرسانی", APP, version, icon = androidx.compose.material.icons.Icons.Outlined.SystemUpdate, hue = ir.vil3ntec.tohid.ui.v3.V3Hue.BLUE)
}
