package ir.vil3ntec.tohid.ui.v3.nav

import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.screens.MoreLayout
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.example.EXAMPLE_FLAG
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  فهرستِ ماژول‌ها (`V3Modules`): هر مسیر و هر ورودی پیدا می‌شود، نام‌های قدیمی به
 *  مسیرِ امروز می‌روند، پدرها زنجیرهٔ برگشتِ درست می‌سازند، و ورودی‌ها نقش و پرچم
 *  را رعایت می‌کنند — همان قراردادِ `docs/MODULES-fa.md`.
 */
class ModuleRegistryTest {
  private val table = V3Modules.table
  private val all = Access.all(flags = setOf(EXAMPLE_FLAG))

  /** نمونهٔ واقعی برای هر `{param}` */
  private fun sample(pattern: String) = pattern.replace(Regex("\\{[^}]+}"), "x1")

  @Test
  fun `هر مسیرِ ثبت‌شده به خودش می‌رسد و الگوی تکراری نیست`() {
    val patterns = V3Modules.ALL.flatMap { m -> m.routes.map { it.pattern } }
    assertEquals("الگوی تکراری", patterns.size, patterns.toSet().size)
    V3Modules.ALL.forEach { m ->
      m.routes.forEach { r ->
        val got = table.resolve(sample(r.pattern))
        assertNotNull("پیدا نشد: ${r.pattern}", got)
        assertEquals(r.pattern, got!!.route.pattern)
      }
    }
    val ids = V3Modules.ALL.map { it.id }
    assertEquals("شناسهٔ تکراریِ ماژول", ids.size, ids.toSet().size)
  }

  @Test
  fun `هر پدر خودش مسیرِ ثبت‌شده است و زنجیره حلقه ندارد`() {
    V3Modules.ALL.flatMap { it.routes }.forEach { r ->
      val path = sample(r.pattern)
      val chain = table.chain(path)
      assertEquals(path, chain.last())
      chain.forEach { assertNotNull("پدرِ ناموجود در زنجیرهٔ ${r.pattern}: $it", table.resolve(it)) }
    }
    assertEquals(listOf("products", "products/42"), table.chain("products/42"))
    assertEquals(listOf("stock", "stock/7", "stock/7/adjust"), table.chain("stock/7/adjust"))
    assertEquals(listOf("settings", "settings/security", "settings/security/app-lock"), table.chain("settings/security/app-lock"))
  }

  @Test
  fun `نام‌های قدیمی (NAV-MAP) به مسیرِ امروز می‌روند و پرسش می‌ماند`() {
    mapOf(
      "dashboard" to "home", "warehouse" to "stock", "quick" to "sale/quick", "vip" to "subscription/plans",
      "myplan" to "subscription", "nav-editor" to "settings/general/navbar", "app" to "app/updates",
    ).forEach { (old, now) -> assertEquals(now, table.canonical(old)) }
    assertEquals("stock?product=5", table.canonical("warehouse?product=5"))
    assertEquals("stock", V3Modules.legacyId("warehouse").let { table.canonical(it) })
    assertEquals("warehouse", V3Modules.legacyId("stock?filter=low"))
  }

  @Test
  fun `الگوی دقیق‌تر پیشی می‌گیرد`() {
    assertEquals("settings/about", table.resolve("settings/about")!!.route.pattern)
    assertEquals("settings/general/navbar", table.resolve("settings/general/navbar")!!.route.pattern)
    assertEquals("settings/{group}", table.resolve("settings/security")!!.route.pattern)
    assertEquals("debtors/new", table.resolve("debtors/new")!!.route.pattern)
    assertEquals("debtors/merge", table.resolve("debtors/merge")!!.route.pattern)
    assertEquals("debtors/merge/{keep}/{drop}", table.resolve("debtors/merge/a/b")!!.route.pattern)
    assertEquals("debtors/{id}/tx/{txId}", table.resolve("debtors/a/tx/b")!!.route.pattern)
    assertEquals("products/new", table.resolve("products/new?barcode=1")!!.route.pattern)
    assertEquals("expenses/month", table.resolve("expenses/month")!!.route.pattern)
    assertNull(table.resolve("nowhere/at/all"))
  }

  @Test
  fun `NAV-MAP — هر مسیرِ اصلی ثبت است`() {
    listOf(
      "home", "notifications", "search", "palette", "more",
      "auth", "auth/code", "setup/shop", "lock", "lock/setup", "reauth",
      "products", "products/1", "products/new", "products/1/edit", "products/1/photo", "products/categories",
      "stock", "stock/1", "stock/entry", "stock/entry/bulk", "stock/1/adjust", "stock/1/movements", "stock/count", "stock/import",
      "debtors", "debtors/1", "debtors/new", "debtors/receive", "debtors/1/tx", "receipts", "purchasing", "purchasing/new",
      "expenses", "expenses/new", "sale", "sale/quick", "sale/checkout", "scan", "sales", "sales/1", "sales/drawer",
      "reports", "stats", "audit", "admin", "profile", "team", "subscription", "subscription/plans", "support",
      "orders", "orders/1", "orders/new", "storefront", "chat", "delivery", "courier",
      "settings", "settings/general", "settings/security", "settings/security/app-lock", "settings/general/navbar",
      "settings/data/sync", "settings/data/backup", "settings/about", "devices",
    ).forEach { assertNotNull("ثبت نیست: $it", table.resolve(it)) }
  }

  @Test
  fun `هر ردیفِ تنظیمات مسیرِ خودش را دارد — «امنیت» مستقیم صفحهٔ امنیت`() {
    assertEquals("settings/security", SettingsPaths.group(SettingsLayout.SECURITY))
    SettingsLayout.GROUPS.forEach { g ->
      val r = table.resolve(SettingsPaths.group(g.title))
      assertNotNull(g.title, r)
      g.sections.forEach { s ->
        val path = SettingsPaths.section(s)
        assertNotNull("$s ⇐ $path", table.resolve(path))
      }
    }
    assertEquals("settings/security/app-lock", SettingsPaths.section("قفل برنامه"))
    assertEquals("devices?tab=printers", SettingsPaths.section(SettingsLayout.PRINTER))
    assertEquals(SettingsLayout.SECURITY, SettingsPaths.groupOfSlug("security"))
    assertEquals("قفل برنامه", SettingsPaths.sectionOfSlug(SettingsLayout.SECURITY, "app-lock"))
  }

  @Test
  fun `هر ورودی (نوار، بیشتر، خانه) به مسیرِ ثبت‌شده می‌رود`() {
    V3Modules.ALL.forEach { m ->
      m.entries.forEach { e -> assertNotNull("${m.id}/${e.id} ⇐ ${e.route}", table.resolve(e.route)) }
      m.settings.forEach { s -> assertNotNull("${m.id} settings ⇐ ${s.route}", table.resolve(s.route)) }
    }
    NavCatalog.BUILT_IN.forEach { assertNotNull("نوار: ${it.id} ⇐ ${it.route}", table.resolve(it.route)) }
    MoreLayout.groups(true, true, 0, true, "1", storefrontOn = true, deliveryOn = true, flags = setOf(EXAMPLE_FLAG))
      .flatMap { it.items }.forEach { assertNotNull("بیشتر: ${it.id} ⇐ ${it.route}", table.resolve(it.route)) }
  }

  @Test
  fun `ورودی‌ها نقش و پرچمِ سرور را رعایت می‌کنند`() {
    val clerk = Access(granted = emptySet())
    val more = V3Modules.entries(Place.MORE, clerk).map { it.id }
    assertFalse("team" in more); assertFalse("audit" in more)
    assertFalse("orders" in more); assertFalse("storefront" in more); assertFalse("chat" in more); assertFalse("delivery" in more)
    assertTrue("warehouse" in more && "support" in more && "devices" in more)
    val owner = V3Modules.entries(Place.MORE, Access(granted = setOf(Need.MANAGE, Need.ORDERS_LIVE)))
    assertTrue(owner.any { it.id == "team" } && owner.any { it.id == "orders" })
    //  مسیرِ تنظیمات برای شاگرد بسته است؛ ویرایشگرِ نوار و دستگاه‌ها نه
    assertFalse(table.resolve("settings/security")!!.allowed(clerk))
    assertTrue(table.resolve("settings/general/navbar")!!.allowed(clerk))
    assertTrue(table.resolve("devices")!!.allowed(clerk))
    assertFalse(table.resolve("audit")!!.allowed(Access(granted = setOf(Need.MANAGE))))
  }

  @Test
  fun `ماژولِ نمونه پشتِ پرچمِ خاموش است و با روشن شدن همه‌جا می‌آید`() {
    val off = Access(granted = setOf(Need.MANAGE))
    val on = off.copy(flags = setOf(EXAMPLE_FLAG))
    assertFalse(V3Modules.entries(Place.MORE, off).any { it.id == "example" })
    assertTrue(V3Modules.entries(Place.MORE, on).any { it.id == "example" })
    assertFalse(table.resolve("example")!!.allowed(off))
    assertTrue(table.resolve("example")!!.allowed(on))
    assertTrue(V3Modules.settingsEntries(on).any { it.route == "settings/example" })
  }

  @Test
  fun `حالت‌ها — مسیرهای پیک فقط در پیک و مسیرهای دکان فقط در دکان؛ «خرید» در برنامهٔ مدیریت نیست`() {
    val courier = Access.all(AppMode.COURIER)
    assertTrue(table.resolve("courier")!!.allowed(courier))
    assertFalse(table.resolve("stock")!!.allowed(courier))
    assertFalse(table.resolve("courier")!!.allowed(all))
    //  تصمیمِ ۱۴۰۵/۰۸/۰۸: هیچ مسیر، ماژول یا ورودیِ «خرید»
    listOf("shopper/home", "shopper/shop/abc", "shopper/cart", "shopper/checkout", "shopper/orders", "shopper/account")
      .forEach { assertNull("مسیرِ خرید مانده: $it", table.resolve(it)) }
    assertTrue(V3Modules.ALL.none { it.id == "shopper" })
    assertTrue(V3Modules.ALL.flatMap { m -> m.routes.map { it.pattern } + m.entries.map { it.route } }.none { it.startsWith("shopper") })
  }

  @Test
  fun `اسکنِ QR در هر دو حالت؛ اسکنِ بارکدِ فروش همان که بود`() {
    val qr = table.resolve(ScanPaths.QR)!!
    assertEquals("scan", qr.module.id)
    assertEquals(RouteKind.SHEET, qr.route.kind)
    AppMode.entries.forEach { assertTrue("QR در $it", qr.allowed(Access.all(it))) }
    val barcode = table.resolve("scan?for=stock")!!
    assertEquals("scan", barcode.route.pattern)
    assertTrue(barcode.allowed(all))
    assertFalse(barcode.allowed(Access.all(AppMode.COURIER)))
  }
}
