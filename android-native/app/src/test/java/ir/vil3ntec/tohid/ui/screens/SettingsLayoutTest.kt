package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  هیچ بخشِ تنظیمات بی‌راه نمی‌ماند (VILL3N، G9).
 *
 *  هر بخشِ `SettingsLayout.GROUPS` (از **متنِ** `SettingsLayout.kt`؛ شش دسته، هر کدام چند `Part`) دقیقاً یک صفحه در
 *  `ui/v3/settings/SettingsPages.kt` (از **متنِ** همان فایل) و یک نامِ نشانی دارد، و
 *  مسیرش در فهرستِ مسیرها به صفحهٔ خودش می‌رسد — «دکان و امنیت ⇐ قفلِ برنامه» یک‌راست؛ نشانیِ کهنهٔ گروه‌های
 *  پیشین (`settings/security/app-lock`) هم همان صفحه را باز می‌کند.
 *
 *  ⚠️ متن با متن: کامپیوتر پیش از کامپایل چند عبارت را در کدِ اندروید عوض می‌کند
 *  (`phrases` در `desktop/build.gradle.kts`، مثلاً «دوربین بارکدخوان» ⇐ «بارکدخوان»)،
 *  ولی این آزمون متنِ خام را می‌خواند؛ از شیءِ کامپایل‌شده فقط شمار و مسیرها.
 */
class SettingsLayoutTest {

  private fun src(rel: String): String = listOf(
    "src/main/java/ir/vil3ntec/tohid/$rel",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
    "android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
  ).map { File(it) }.firstOrNull { it.isFile }?.readText()
    ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val layoutText by lazy { src("ui/screens/SettingsLayout.kt") }

  /** `const val NAME = "…"` ِ همان فایل */
  private val consts: Map<String, String> by lazy {
    Regex("""const val ([A-Z_]+) = "([^"]+)"""").findAll(layoutText).associate { it.groupValues[1] to it.groupValues[2] }
  }

  private fun names(block: String): List<String> =
    Regex(""""([^"]+)"|\b([A-Z_]+)\b""").findAll(block).mapNotNull { m -> m.groupValues[1].ifEmpty { null } ?: consts[m.groupValues[2]] }.toList()

  private val links: Set<String> by lazy {
    val block = Regex("""val LINKS[^=]*= mapOf\(([^)]*)\)""").find(layoutText)?.groupValues?.get(1).orEmpty()
    Regex("""([A-Z_]+) to """).findAll(block).mapNotNull { consts[it.groupValues[1]] }.toSet()
  }

  /** بخش‌های دسته‌ها از متنِ `SettingsLayout.kt` (هر `Part("…", listOf(…))`)، بی پیوندها */
  private val layoutSections: List<String> by lazy {
    Regex("""Part\(\s*"[a-z]+"\s*,\s*listOf\(([^)]*)\)""").findAll(layoutText).flatMap { names(it.groupValues[1]) }.filterNot { it in links }.toList()
  }

  /** صفحه‌ها از متنِ `SettingsPages.kt`: `Page("…"` یا `Page(SettingsLayout.X` */
  private val pageKeys: List<String> by lazy {
    Regex("""Page\(\s*(?:"([^"]+)"|SettingsLayout\.([A-Z_]+))""").findAll(src("ui/v3/settings/SettingsPages.kt"))
      .mapNotNull { m -> m.groupValues[1].ifEmpty { null } ?: consts[m.groupValues[2]] }.toList()
  }

  @Test
  fun `هر بخشِ تنظیمات دقیقاً یک صفحه دارد و دقیقاً در یک دسته و یک بخشِ کوتاه است`() {
    assertTrue("بخشی پیدا نشد — الگوی متن عوض شده؟", layoutSections.size >= 12)
    assertEquals("بخشِ تکراری در گروه‌ها", layoutSections.size, layoutSections.toSet().size)
    assertEquals("صفحهٔ تکراری", pageKeys.size, pageKeys.toSet().size)
    assertEquals(layoutSections.toSet(), pageKeys.toSet())
    assertEquals("متنِ گروه‌ها با شیءِ کامپایل‌شده نمی‌خواند", layoutSections.size, SettingsLayout.sections.size)
    SettingsLayout.sections.forEach { assertTrue("$it گروه ندارد", SettingsLayout.groupOf(it) != null) }
    val parts = SettingsLayout.GROUPS.flatMap { it.parts }
    assertEquals("شناسهٔ بخشِ کوتاهِ تکراری", parts.size, parts.map { it.id }.toSet().size)
    val rows = parts.flatMap { it.rows }
    assertEquals("ردیفِ تکراری", rows.size, rows.toSet().size)
    rows.forEach { assertEquals(it, SettingsLayout.partOf(it), parts.first { p -> it in p.rows }.id) }
  }

  @Test
  fun `هر بخش نامِ نشانیِ خودش را دارد و مسیرش به صفحهٔ تنظیمات می‌رسد`() {
    val slugBlock = Regex("""val SECTION_SLUGS[^=]*= mapOf\(([\s\S]*?)\n\s*\)""").find(layoutText)?.groupValues?.get(1).orEmpty()
    val slugged = Regex("""(?:"([^"]+)"|\b([A-Z_]+)\b) to """").findAll(slugBlock)
      .mapNotNull { m -> m.groupValues[1].ifEmpty { null } ?: consts[m.groupValues[2]] }.toSet()
    assertEquals(layoutSections.toSet(), slugged)
    val table = V3Modules.table
    //  نامِ نشانیِ بخش در کلِ تنظیمات یکتاست — پیوندِ کهنهٔ گروهِ پیشین هم به همان بخش می‌رسد
    assertEquals("نامِ نشانیِ تکراری", SettingsLayout.SECTION_SLUGS.size, SettingsLayout.SECTION_SLUGS.values.toSet().size)
    SettingsLayout.GROUPS.forEach { g ->
      assertNotNull(g.title, table.resolve(SettingsPaths.group(g.title)))
      g.sections.forEach { s ->
        val path = SettingsPaths.section(s)
        val r = table.resolve(path)
        assertNotNull("$s ⇐ $path", r)
        if (!SettingsLayout.isLink(s) && r!!.route.pattern == "settings/{group}/{section}") {
          assertEquals(s, SettingsPaths.sectionOfSlug(g.title, path.substringAfterLast('/')))
        }
      }
    }
    assertEquals("settings/shop/app-lock", SettingsPaths.section("قفل برنامه"))
    assertEquals("settings/data/sync", SettingsPaths.section("همگام‌سازی"))
    assertEquals("settings/look/navbar", SettingsPaths.section(SettingsLayout.NAV_BAR))
    assertEquals("settings/about", SettingsPaths.section(SettingsLayout.ABOUT_APP))
    assertEquals("settings/about", SettingsPaths.UPDATES)
    assertEquals(SettingsPaths.APP_LOCK, SettingsPaths.section("قفل برنامه"))
    assertEquals(SettingsPaths.SYNC, SettingsPaths.section("همگام‌سازی"))
  }

  @Test
  fun `نشانیِ کهنهٔ گروه‌های پیشین همان صفحه را باز می‌کند`() {
    val table = V3Modules.table
    val old = mapOf(
      "settings/security/app-lock" to "قفل برنامه",
      "settings/security/encryption" to SettingsLayout.ENCRYPTION,
      "settings/general/alerts" to "هشدارها",
      "settings/general/language" to "زبان",
      "settings/general/training" to SettingsLayout.TRAINING,
      "settings/sales/labels" to SettingsLayout.LABEL_TOOLS,
      "settings/sales/stock-places" to SettingsLayout.STOCK_PLACES,
      "settings/sales/tax-quarter" to SettingsLayout.TAX_QUARTER,
      "settings/data/backup" to "داده‌ها",
    )
    old.forEach { (path, key) ->
      val r = table.resolve(path)
      assertNotNull(path, r)
      assertEquals(path, "settings/{group}/{section}", r!!.route.pattern)
      val group = SettingsPaths.groupOfSlug(path.split('/')[1])
      assertNotNull(path, group)
      assertEquals(path, key, SettingsPaths.sectionOfSlug(group!!, path.substringAfterLast('/')))
    }
    assertEquals(SettingsLayout.SHOP, SettingsPaths.groupOfSlug("security"))
    assertEquals(SettingsLayout.LOOK, SettingsPaths.groupOfSlug("general"))
    assertEquals(SettingsLayout.DATA, SettingsPaths.groupOfSlug("about"))
    assertEquals(null, SettingsPaths.groupOfSlug("ناموجود"))
    //  ویرایشگرِ نوار و برگهٔ رمز ثابت‌اند: نشانیِ کهنه همان مسیر
    assertEquals(SettingsPaths.NAVBAR, table.resolve(SettingsPaths.OLD_NAVBAR)!!.route.pattern)
    assertEquals(SettingsPaths.PIN, table.resolve(SettingsPaths.OLD_PIN)!!.route.pattern)
    assertEquals(SettingsPaths.NAVBAR, table.resolve("nav-editor")!!.route.pattern)
  }

  @Test
  fun `شش دسته به ترتیبِ صفحهٔ اول و هیچ‌کدام خالی نیست`() {
    assertEquals(
      listOf("دکان و امنیت", "فروش و پرداخت", "کالا و انبار", "دستگاه‌ها", "داده و پشتیبان", "ظاهر و زبان"),
      SettingsLayout.GROUPS.map { it.title },
    )
    SettingsLayout.GROUPS.forEach { assertTrue("${it.title} خالی است", it.sections.isNotEmpty()) }
    assertEquals(SettingsLayout.GROUPS.map { it.title }.toSet(), SettingsLayout.GROUP_SLUGS.keys)
    //  نامِ نشانیِ گروه‌های پیشین با دسته‌های امروز برخورد ندارد
    assertTrue(SettingsLayout.GROUP_SLUG_ALIASES.keys.none { it in SettingsLayout.GROUP_SLUGS.values })
    assertTrue(SettingsLayout.GROUP_SLUG_ALIASES.values.all { a -> SettingsLayout.GROUPS.any { it.title == a } })
    assertEquals(SettingsLayout.SHOP, SettingsLayout.groupOf("قفل برنامه"))
    assertEquals(SettingsLayout.SHOP, SettingsLayout.groupOf("دستگاه‌های وارد شده"))
    assertEquals(SettingsLayout.SHOP, SettingsLayout.groupOf(SettingsLayout.PROFILE))
    assertEquals(SettingsLayout.DATA, SettingsLayout.groupOf("همگام‌سازی"))
    assertEquals(SettingsLayout.DATA, SettingsLayout.groupOf(SettingsLayout.ABOUT_APP))
    assertEquals(SettingsLayout.LOOK, SettingsLayout.groupOf("زبان"))
    assertEquals(SettingsLayout.LOOK, SettingsLayout.groupOf(SettingsLayout.NAV_BAR))
    assertEquals(SettingsLayout.SALES, SettingsLayout.groupOf(SettingsLayout.PRINTER))
    assertEquals(SettingsLayout.GOODS, SettingsLayout.groupOf(SettingsLayout.LABEL_TOOLS))
    assertEquals(SettingsLayout.DEVICES, SettingsLayout.groupOf(SettingsLayout.PERIPHERALS))
    assertEquals(null, SettingsLayout.groupOf("ناموجود"))
  }

  @Test
  fun `پیوندها بخش نیستند و به صفحهٔ دستگاه‌ها، «حسابِ من» یا تنظیماتِ ویترین می‌روند`() {
    assertEquals(setOf(SettingsLayout.PRINTER, SettingsLayout.PERIPHERALS, SettingsLayout.PROFILE, SettingsLayout.STORE_PLACE), links)
    //  ممیزیِ ویترین (۱۴۰۵/۰۸/۱۷): «نشانی و نقشهٔ دکان» ⇐ «ویترین ⇐ تنظیمات»
    assertEquals("storefront?tab=settings", SettingsLayout.LINKS[SettingsLayout.STORE_PLACE])
    assertEquals("devices?tab=printers", SettingsLayout.LINKS[SettingsLayout.PRINTER])
    assertEquals("devices", SettingsLayout.LINKS[SettingsLayout.PERIPHERALS])
    assertEquals("profile/edit", SettingsLayout.LINKS[SettingsLayout.PROFILE])
    SettingsLayout.LINKS.values.forEach { assertNotNull(it, V3Modules.table.resolve(it)) }
    SettingsLayout.LINKS.keys.forEach { assertTrue(it !in SettingsLayout.sections) }
  }

  @Test
  fun `نامِ ردیف و گروه به زبانِ صفحه — کلید عوض نمی‌شود`() {
    assertEquals("پشتیبان و بازگردانی", SettingsLayout.labelOf("داده‌ها"))
    assertEquals("بارکدخوان", SettingsLayout.labelOf(SettingsLayout.CAMERA))
    assertEquals("دستگاه‌های وصل", SettingsLayout.labelOf("دستگاه‌های وارد شده"))
    assertEquals("نسخه و به‌روزرسانی", SettingsLayout.labelOf(SettingsLayout.ABOUT_APP))
    assertEquals("امنیت", SettingsLayout.groupLabel(SettingsLayout.SECURITY))
    (SettingsLayout.sections + SettingsLayout.LINKS.keys).forEach { assertTrue(it, SettingsLayout.labelOf(it).isNotBlank()) }
  }

  @Test
  fun `کامپیوتر — بخشِ تازه کنارِ هم‌گروهش، ترتیبِ داده دست‌نخورده، بی بخش‌های فقط‌گوشی`() {
    val given = listOf(
      "هشدارها", "ظاهر", "تاریخ و رقم", "کالای منقضی", "قفل برنامه", "تغییر رمز حساب",
      "دستگاه‌های وارد شده", "کد شاگرد", "داده‌ها", SettingsLayout.ABOUT_APP,
    )
    val out = SettingsLayout.deskOrder(given)
    assertEquals(SettingsLayout.sections.toSet() - SettingsLayout.DESK_HIDDEN, out.toSet())
    assertEquals(out.size, out.toSet().size)
    assertEquals(given, out.filter { it in given })
    assertEquals(out.indexOf("ظاهر") + 1, out.indexOf("زبان"))
    assertTrue(out.indexOf("همگام‌سازی") < out.indexOf("داده‌ها"))
    SettingsLayout.LINKS.keys.forEach { assertTrue(it !in out) }
    assertEquals(SettingsLayout.sections.filterNot { it in SettingsLayout.DESK_HIDDEN }, SettingsLayout.deskSections)
  }
}
