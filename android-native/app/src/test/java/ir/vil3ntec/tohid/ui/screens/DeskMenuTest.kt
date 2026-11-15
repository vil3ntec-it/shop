package ir.vil3ntec.tohid.ui.screens

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeskOff
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.shell.ShellTargets
import java.io.File

/**
 *  منوی گروه‌بندی‌شدهٔ کامپیوتر (v3 §۲۰.۳، VISUAL-DIRECTION §۳):
 *  میان‌برها عوض نمی‌شوند، هیچ پیوندِ مرده‌ای نیست، بخشِ ساخته‌شده هرگز «در دست ساخت» نیست — خاموش ⇒ «خاموش» و
 *  برگهٔ «چطور روشن می‌شود» (`DeskOff`)، و «آماده‌سازی و تحویل» به برگهٔ آماده‌سازیِ صفِ سفارش‌ها می‌رود.
 */
class DeskMenuTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private val ui: File? = listOf(
    "src/main/java/ir/vil3ntec/tohid/ui",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/ui",
    "android-native/app/src/main/java/ir/vil3ntec/tohid/ui",
  ).map { File(it) }.firstOrNull { it.isDirectory }

  /** از متنِ AppNav.kt، نه خودِ شیء — AppNav وضعیتِ Compose دارد (همان روشِ KeyMapTest) */
  private fun shortcuts(): List<Pair<String, String>> {
    //  نامِ هر صفحه حالا از جدولِ سه‌زبانه است (`Str.dashboard`…)؛ این‌جا فقط شناسه و ترتیب مهم است
    val nav = File(ui!!, "AppNav.kt").readText().substringAfter("val SHORTCUTS").substringAfter("listOf(").substringBefore(")\n")
    return Regex(""""([a-z]+)" to ([^,\n]+)""").findAll(nav).map { it.groupValues[1] to it.groupValues[2] }.toList()
  }

  @Test
  fun `هر نُه میان‌برِ صفحه دقیقاً یک بار با همان کلید در منو هست`() {
    assertTrue("پوشهٔ ui پیدا نشد", ui != null)
    val sc = shortcuts()
    assertEquals(9, sc.size)
    for (admin in listOf(true, false)) {
      val items = DeskMenu.groups(admin, sc).flatMap { it.items }
      sc.forEachIndexed { i, (id, _) ->
        val hits = items.filter { it.id == id }
        assertEquals("$id یک بار", 1, hits.size)
        assertEquals("$id کلید", "Ctrl+${i + 1}", hits[0].key)
      }
      //  شناسه‌ها تکراری نیستند
      assertEquals(items.size, items.map { it.id }.toSet().size)
    }
  }

  /** مسیرها حالا در فهرستِ ماژول‌ها (`V3Modules`) ثبت‌اند، نه در `when` ِ AppRoot */
  private fun routed(id: String) = V3Modules.table.resolve(id) != null

  /** هر چهار ترکیبِ پرچم‌های سرور × صاحب/شاگرد */
  private fun every(): List<List<DeskMenu.Item>> = listOf(true, false).flatMap { admin ->
    listOf(false, true).map { on -> DeskMenu.groups(admin, shortcuts(), ordersLive = on, storefrontLive = on, deliveryLive = on).flatMap { it.items } }
  }

  @Test
  fun `هر بخشِ زنده به مسیری در AppRoot می‌رود — پیوندِ مرده نیست`() {
    for (items in every()) {
      val live = items.filter { it.state == DeskMenu.State.LIVE }
      val missing = live.filterNot { it.target.isNotEmpty() && routed(it.target) }.map { it.id }
      assertEquals("مسیرِ AppRoot ندارند", emptyList<String>(), missing)
    }
  }

  @Test
  fun `ویترین فقط با روشن بودنِ بخش زنده است و به مسیرِ AppRoot می‌رود`() {
    val off = DeskMenu.groups(true, shortcuts()).flatMap { it.items }.map { it.id }
    assertTrue("off:storefront" in off && "storefront" !in off)
    val on = DeskMenu.groups(true, shortcuts(), storefrontLive = true).flatMap { it.items }
    assertEquals(DeskMenu.State.LIVE, on.single { it.id == "storefront" }.state)
    assertTrue(routed("storefront"))
  }

  @Test
  fun `میزِ تحویل فقط با روشن بودنِ shop_delivery زنده است و به مسیرِ AppRoot می‌رود`() {
    val off = DeskMenu.groups(true, shortcuts()).flatMap { it.items }.map { it.id }
    assertTrue("off:delivery" in off && "delivery" !in off)
    val on = DeskMenu.groups(true, shortcuts(), deliveryLive = true).flatMap { it.items }
    assertEquals(DeskMenu.State.LIVE, on.single { it.id == "delivery" }.state)
    assertTrue("off:delivery" !in on.map { it.id })
    assertTrue(routed("delivery"))
  }

  @Test
  fun `سفارش‌ها فقط با روشن بودنِ بخش زنده است و به مسیرِ AppRoot می‌رود`() {
    val off = DeskMenu.groups(true, shortcuts()).flatMap { it.items }.map { it.id }
    assertTrue("off:orders" in off && "orders" !in off)
    val on = DeskMenu.groups(true, shortcuts(), ordersLive = true).flatMap { it.items }
    val orders = on.single { it.id == "orders" }
    assertEquals(DeskMenu.State.LIVE, orders.state)
    assertTrue(routed("orders"))
  }

  @Test
  fun `هیچ بخشِ ساخته‌شده‌ای در دست ساخت نیست — نه با پرچمِ روشن و نه خاموش`() {
    for (items in every()) {
      assertEquals("«${StrNav.soon}»", emptyList<String>(), items.filter { it.state == DeskMenu.State.SOON }.map { it.id })
      //  نوشتهٔ «در دست ساخت» نامِ هیچ ردیفی نیست
      assertTrue(items.none { it.label == StrNav.soon || it.label == DeskMenu.SOON_LABEL })
    }
    //  چهار بخشی که پیش‌تر «در دست ساخت» بودند همه در برنامه مسیر دارند (ساخته شده‌اند)
    for (path in listOf("orders", DeskMenu.FULFIL_TAB.let { "orders?tab=$it" }, "storefront", "delivery")) assertTrue(path, routed(path))
  }

  @Test
  fun `بخشِ خاموش شناسهٔ off دارد، بی مسیر و کلید، و برگهٔ توضیحِ خودش را`() {
    val off = DeskMenu.groups(true, shortcuts()).flatMap { it.items }.filter { it.state == DeskMenu.State.OFF }
    assertEquals(listOf("off:orders", "off:fulfil", "off:storefront", "off:delivery"), off.map { it.id })
    assertTrue(off.all { it.target.isEmpty() && it.key.isEmpty() })
    assertEquals(off.map { it.id }.toSet(), DeskOff.ids)
    for (o in off) assertNotNull(o.id, DeskOff.info(o.id, owner = false, canAdmin = false))
    //  همه روشن ⇒ هیچ «خاموش»ی
    val on = DeskMenu.groups(true, shortcuts(), ordersLive = true, storefrontLive = true, deliveryLive = true).flatMap { it.items }
    assertTrue(on.none { it.state == DeskMenu.State.OFF })
    //  میزِ تحویلِ خاموش همان نامِ روشنش را دارد
    assertEquals(StrNav.deliveryDeskCourier, off.single { it.id == "off:delivery" }.label)
  }

  @Test
  fun `آماده‌سازی و تحویل با سفارش‌های روشن به برگهٔ آماده‌سازیِ صف می‌رود`() {
    val off = DeskMenu.groups(true, shortcuts()).flatMap { it.items }
    assertEquals(DeskMenu.State.OFF, off.single { it.id == "off:fulfil" }.state)
    assertTrue("fulfil" !in off.map { it.id })
    val on = DeskMenu.groups(true, shortcuts(), ordersLive = true).flatMap { it.items }
    val fulfil = on.single { it.id == "fulfil" }
    assertEquals(DeskMenu.State.LIVE, fulfil.state)
    assertEquals(StrNav.fulfilment, fulfil.label)
    assertEquals("orders?tab=preparing", fulfil.target)
    //  همان مسیرِ صفِ سفارش‌ها (صف | جزئیات، میزِ جمع‌آوری)، با برگهٔ «در آماده‌سازی»: قبول‌شده، در حالِ جمع‌آوری و بسته‌شده
    assertEquals("orders", V3Modules.table.resolve(fulfil.target)!!.route.pattern)
    assertEquals(DeskMenu.FULFIL_TAB, RoutePaths.parse(fulfil.target).second["tab"])
    assertTrue(DeskMenu.FULFIL_TAB in OrderStates.TABS)
    for (status in listOf("accepted", "picking", "packed")) assertEquals(status, DeskMenu.FULFIL_TAB, OrderStates.tabOf(status, null, 0))
    //  ردیفِ روشنِ منو: زدنِ «آماده‌سازی و تحویل» همان را روشن نگه می‌دارد، نه «سفارش‌های آنلاین»
    assertEquals("orders", V3Modules.railId(fulfil.target))
    assertEquals("fulfil", DeskMenu.selectedId(on, "orders", "fulfil"))
    assertEquals("orders", DeskMenu.selectedId(on, "orders", null))
    assertEquals("orders", DeskMenu.selectedId(on, "orders", "orders"))
    assertEquals("dashboard", DeskMenu.selectedId(on, "dashboard", "fulfil"))
    //  ردیفِ خاموش هرگز «زده‌شده» حساب نمی‌شود
    assertEquals("orders", DeskMenu.selectedId(off, "orders", "off:fulfil"))
  }

  @Test
  fun `سفارش‌های خاموش ⇒ برگهٔ چه می‌کند — صاحب با پیام به پشتیبانی، بی گامِ پنل و نامِ پرچم`() {
    val i = DeskOff.info("off:orders", owner = true, canAdmin = true)!!
    assertEquals(StrNav.onlineOrders, i.title)
    assertEquals(StrNav.ordersSub, i.what)
    assertEquals(StrDeskOff.built, i.status)
    assertNull(i.contact)
    assertEquals(StrDeskOff.askSupport, i.actions[0].label)
    assertTrue(i.actions[0].primary)
    assertEquals(AccountPaths.support(StrDeskOff.supportDraft(StrNav.onlineOrders)), i.actions[0].target)
    assertTrue(routed(i.actions[0].target))
    assertEquals("سلام، لطفاً «سفارش‌های آنلاین» را برای همین دکان روشن کنید.", RoutePaths.parse(i.actions[0].target).second["text"])
    //  همان «چرا سفارشِ آنلاین نمی‌رسد» ِ کاشیِ خاموشِ خانه
    assertEquals(ShellTargets.ORDERS_SETUP, i.actions[1].target)
    assertTrue(routed(i.actions[1].target))
  }

  @Test
  fun `شاگرد نامِ پرچم را نمی‌بیند و به تماس با پشتیبانی راه داده می‌شود`() {
    for (id in DeskOff.ids) {
      val i = DeskOff.info(id, owner = false, canAdmin = false)!!
      assertEquals(StrDeskOff.callSupport, i.contact)
      assertEquals(StrDeskOff.builtStaff, i.status)
      assertEquals(listOf(AccountPaths.SUPPORT), i.actions.map { it.target })
      assertEquals(Str.support, i.actions.single().label)
    }
    //  مدیرِ نه‌صاحب: «چرا نمی‌رسد» دارد، نامِ پرچم نه
    val m = DeskOff.info("off:fulfil", owner = false, canAdmin = true)!!
    assertEquals(listOf(AccountPaths.SUPPORT, ShellTargets.ORDERS_SETUP), m.actions.map { it.target })
    assertEquals(StrDeskOff.partOf(StrNav.onlineOrders), m.note)
    assertEquals(listOf(true), DeskOff.info("off:delivery", true, true)!!.actions.map { it.primary })
    assertNull(DeskOff.info("off:unknown", true, true))
  }

  /**
   *  تصمیمِ هماهنگ‌کننده (۱۴۰۵/۰۸/۲۴): برگه را صاحبِ **هر** دکانی می‌بیند و او به پنلِ سکو راه ندارد؛ پس «هیچ جزئیاتِ فنی/زیرساخت
   *  در صفحه» برای همهٔ نقش‌ها — نه نامِ پرچم، نه گامِ پنل، نه «سرور» — در هر چهار زبان. (جای آزمونِ «نامِ برگه‌های پنل در دری و پشتو» ِ 63n.)
   */
  @Test
  fun `برگهٔ خاموش برای هیچ نقش و هیچ زبانی نامِ پرچم یا گامِ پنل ندارد`() {
    for (lang in listOf(Lang.FA, Lang.PRS, Lang.PS, Lang.EN)) {
      I18n.source = { lang }
      for (id in DeskOff.ids) for (owner in listOf(true, false)) for (admin in listOf(true, false)) {
        val i = DeskOff.info(id, owner = owner, canAdmin = admin)!!
        val shown = listOfNotNull(i.title, i.what, i.status, i.note, i.contact) + i.actions.map { it.label }
        for (t in shown) {
          //  نامِ پرچم = `shop.<چیزی>` (پایانِ جملهٔ انگلیسیِ «… for this shop.» نامِ پرچم نیست)
          assertTrue("$lang $id: $t", !Regex("""shop\.[a-z]""").containsMatchIn(t) && "پنل" !in t && "پینل" !in t && "panel" !in t.lowercase() && "سرور" !in t && "server" !in t.lowercase())
        }
      }
    }
    I18n.source = { Lang.EN }
    assertEquals("Off", StrDeskOff.badge)
  }

  @Test
  fun `گروه‌ها — کانال‌های فروش و سدِ شاگرد`() {
    val g = DeskMenu.groups(true, shortcuts())
    assertEquals(listOf("اصلی", "کانال‌های فروش", "مدیریت و سامانه"), g.map { it.title })
    assertTrue(g[1].items.any { it.id == "sale" })
    val staff = DeskMenu.groups(false, shortcuts()).flatMap { it.items }.map { it.id }
    assertFalse("settings" in staff)
    assertFalse("audit" in staff)
    //  ۶۳w: «سابقهٔ عملیات» برای صاحب هم در منو نیست (صاحب‌کار)
    assertFalse(DeskMenu.groups(true, shortcuts()).flatMap { it.items }.any { it.id == "audit" })
    assertEquals("صاحب دکان", DeskMenu.roleLabel("owner"))
    assertEquals("شاگرد", DeskMenu.roleLabel("staff"))
  }
}
