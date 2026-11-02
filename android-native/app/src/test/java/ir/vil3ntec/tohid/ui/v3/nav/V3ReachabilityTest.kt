package ir.vil3ntec.tohid.ui.v3.nav

import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.SettingsSearchIndex
import ir.vil3ntec.tohid.ui.screens.SettingsTiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  «هیچ چیز حذف نشود» (صاحب‌کار): پس از پاک شدنِ رابطِ قدیم (دستهٔ ۱۳، #848/#851) کدِ این کارها ماند ولی هیچ
 *  صفحهٔ نسخهٔ ۳ بازشان نمی‌کرد. این آزمون برای هر کار دو چیز می‌سنجد:
 *
 *  1. **از ریشه می‌رسد:** جزءِ همان کار (`ui/screens/…`) در نقشهٔ ایستای صدا زدن از `AppRoot` (تنها ریشهٔ گوشی،
 *     کامپیوتر و آیفون؛ پوسته ⇐ `V3Modules` ⇐ مسیرها ⇐ صفحه‌ها) رسیده است (`SourceGraph`).
 *  2. **جایش در نسخهٔ ۳ هست:** مسیرِ میزبان در جدولِ مسیرهای نسخهٔ ۳ (حالتِ دکان) پیدا می‌شود و زنجیرهٔ پدرهایش به
 *     یک زبانهٔ ریشه می‌رسد — یعنی از نوار/«بیشتر»/تنظیمات با دست باز می‌شود.
 *
 *  فهرست فقط بلندتر می‌شود؛ کاری که جانشینِ نسخهٔ ۳ ِ کامل گرفت با همان مسیر و نامِ جانشین می‌ماند.
 */
class V3ReachabilityTest {

  /**
   *  یک کار: شناسه (ردیفِ STATUS یا نامِ کار)، مسیرِ میزبان در نسخهٔ ۳ (الگو با `{…}` یا نشانیِ کامل) و جزءهایی که باید
   *  برسند (`بسته.نام`). `root` ریشهٔ رسیدن است: پیش‌فرض `AppRoot`؛ برای کاری که فقط یک سکو آن را کم داشت (صندوقِ گوشی،
   *  گزارشِ کامپیوتر) همان صفحهٔ آن سکو، تا رسیدن از سکوی دیگر جای آن را نگیرد. `pattern` الگوی مسیرِ نشانیِ کامل.
   */
  private class Feature(
    val id: String,
    val route: String,
    vararg val symbols: String,
    val root: String = APP_ROOT,
    val pattern: String = route,
  )

  private fun s(name: String) = "ir.vil3ntec.tohid.ui.screens.$name"
  private fun pos(name: String) = "ir.vil3ntec.tohid.ui.components.pos.$name"

  private val features = listOf(
    //  ── انبار و کالا ──
    Feature("INV-20/21 محل‌ها و بسته‌بندیِ دوباره", "stock/places", s("StockPlacesEntry")),
    Feature("INV-06/07/08 انبارگردانیِ تازه (کلاسِ A، پیش‌فرض روشن)", "stock/count/tools", s("StockCountToolsWired")),
    Feature("دفترِ موجودی (موتورِ تازهٔ موجودی و بها)", "stock/book", s("StockBookScreen"), s("StockStrip")),
    Feature("PRD-23/HW-04 برگهٔ برچسب و بارکدِ داخلی", "stock/labels", s("LabelSheetWired")),
    Feature("ثبتِ چند کالا با هم", "stock/bulk-products", s("BulkProductSheet")),
    Feature("ورودِ گروهیِ عکس‌ها", "stock/images", s("ImageBulkImportSheet")),
    Feature("PRD-20 ورود از xlsx/CSV با پیش‌نمایشِ کامل", "stock/import", s("ProductImportDialog")),
    Feature("PRD-20 سابقهٔ ورود از فایل", "stock/import/history", s("ProductImportHistoryDialog")),
    Feature("IMG-BANK بانکِ تصاویر", "products/{id}/photo/bank", s("ImageBankPicker"), s("BankPhotoSource")),
    Feature("دوربینِ راهنما", "products/{id}/photo/guided", "ir.vil3ntec.tohid.scan.GuidedCaptureDialog"),
    //  ── کارمندان و مدیریت ──
    Feature("STF تأییدکننده‌ها", "team", s("ApproverSetupSection")),
    Feature("STF مجوزِ کارمندان", "team", s("MemberPermsSection")),
    Feature("STF دامنهٔ کارمند", "team", s("StaffScopeSection")),
    Feature("STF دستگاه‌های کارمندان", "team", s("DevicesSection")),
    Feature("STF-07/08 ساعتِ کار و پورسانت", "admin/staff-time", s("StaffTimeHub")),
    //  ── گزارش‌ها ──
    Feature("RPT-16 پرسشِ ساده (کامپیوتر)", "reports", s("DeskSimpleAsk"), root = DESK_REPORTS),
    Feature("RPT-13 مقایسهٔ دوره (کامپیوتر)", "reports", s("ReportCompareLine"), root = DESK_REPORTS),
    Feature(
      "گزارش‌های ضایعات، سنِ بدهی، RFM، شماره‌های جاافتاده و پرسشِ ساده (گوشی)", "reports",
      s("shrinkSection"), s("agingSection"), s("rfmSection"), s("gapsSection"), s("simpleAskSection"), root = PHONE_REPORTS,
    ),
    Feature("گزارشِ تازه و پایانِ روز (گوشی)", "reports", s("PhoneFreshReports"), root = PHONE_REPORTS),
    //  ── خرید و صندوق ──
    Feature("S25/PUR-08 برگشت به تأمین‌کننده و چاپِ سندِ کسر (گوشی)", "purchasing/suppliers/{id}/return", s("SupplierReturnDialog")),
    Feature("POS-39 دریافتِ بدهی در صندوقِ گوشی", "sale", s("TillReceivePhone"), root = PHONE_SALE),
    Feature(
      "POS-25/26/27/18، PAY-24، POS-38 ابزارهای سبدِ صندوقِ گوشی", "sale",
      pos("QuoteBar"), pos("SpecialOrderBar"), pos("GiftCardBar"), pos("LayawayBar"), pos("PriceListAtPos"), pos("DeliverLaterSheetWired"),
      root = PHONE_SALE,
    ),
    //  ── سفارش و تحویل ──
    Feature("PCK جمع‌آوری و بسته‌بندیِ سفارش", "orders/{id}/pick", s("OrderPickHost"), s("OrderPickButton")),
    Feature("PCK-10/12 قفسه و دستهٔ جمع‌آوری", "orders", s("PickDeskEntry")),
    Feature("NTF-07/09 گزارش‌ها و نظرهای سفارش", "orders", s("OrderFeedbackEntry")),
    Feature("ORD-14 ویرایشِ سفارش و تسویهٔ تفاوت", "orders/{id}", s("OrderAmendEntry"), s("OrderAmendCard")),
    Feature("ORD-16 پیام‌های سفارش", "orders/{id}", s("OrderNoticesSection")),
    Feature("RET-11 برگشتِ کالای سفارشِ بازپرداخت‌شده", "orders/{id}", s("RestockDialog"), s("RefundDetailNotices"), s("RefundListNotices")),
    Feature("CHK-15/16 جایگزینی و یادداشتِ قلم", "orders/{id}", s("OrderSubstitutionNote"), s("OrderLineExtras"), s("OrderSubstitutionPicker")),
    Feature("CHK-14 ترجیحِ تحویل و پولِ نقدِ مشتری", "orders/{id}", s("OrderDeliveryPrefsSection"), s("CashTenderField")),
    Feature("RPT-15 گزارشِ نقدِ پیک‌ها", "delivery/cash-report", s("CashReportSheet")),
    Feature("DLV-18 امتیازِ تحویل‌ها", "delivery/ratings", s("RatingsSheet")),
    Feature("PCK-11 QR ِ کدِ تحویل", "delivery/code", s("HandoverQr")),
    //  ── ویترین ──
    Feature("پیوند و QR ِ کارزار", "storefront/campaigns", s("CampaignsPane")),
    Feature("M3 عکس و ویدیوی ویترین", "storefront/media", s("StorefrontMediaPanel")),
    //  ── دستگاه‌ها و تنظیمات ──
    Feature("HW صفِ چاپ، چاپگرِ برچسب، ترازو، نمایشگرِ مشتری و ابزارهای تازه", "devices", s("PrintQueuePanel"), s("LabelsPanel"), s("ScalePanel"), s("CustomerDisplayPanel"), s("HwToolsSwitch")),
    Feature("HW تنظیمِ چاپگر و کشو", "devices", s("PrinterSettingsDialog"), s("OpenDrawerDialog")),
    Feature("HW-12 راه‌اندازیِ آسانِ چاپگر و بارکدخوان", "devices", s("PrinterFinderDialog"), s("ScannerSetupTools")),
    Feature("سرورهای دیگر", "settings/data/servers", s("ServersSettings"), pattern = "settings/{group}/{section}"),
  )

  @Test
  fun `هر کارِ رابطِ قدیم از ریشهٔ نسخهٔ ۳ می‌رسد`() {
    //  هر ریشهٔ سکو خودش از `AppRoot` می‌رسد (نه صفحهٔ مرده‌ای که فقط اسمش ریشه است)
    val roots = features.map { it.root }.toSet()
    assertEquals("ریشه‌ای که از AppRoot نمی‌رسد", emptySet<String>(), roots.filter { it !in reached(APP_ROOT) }.toSet())
    val missing = features.flatMap { f ->
      f.symbols.filter { it !in reached(f.root) }.map { "${f.id}: ${it.substringAfterLast('.')} (از ${f.root.substringAfterLast('.')})" }
    }
    assertEquals("بی‌راه در نسخهٔ ۳ (نامِ کامل در `features`):", emptyList<String>(), missing)
  }

  @Test
  fun `هر کار مسیری در نسخهٔ ۳ دارد که از نوار، «بیشتر» یا تنظیمات باز می‌شود`() {
    val table = V3Modules.table
    //  درهای دستی: زبانه‌ها و هر ورودیِ ماژول (نوار، «بیشتر»، خانه) و ردیفِ تنظیماتِ ماژول‌ها
    val doors = V3Modules.ALL.flatMap { m -> m.entries.map { it.route } + m.settings.map { it.route } }
      .mapNotNull { table.resolve(it)?.route?.pattern }.toSet()
    val bad = features.map { it.id to it.route }.distinct().mapNotNull { (id, route) ->
      val path = route.replace(Regex("""\{[^}]*}"""), "x")
      val pattern = features.first { it.route == route }.pattern
      val r = table.resolve(path) ?: return@mapNotNull "$id: «$route» در جدول نیست"
      if (AppMode.SHOP !in r.module.modes) return@mapNotNull "$id: «$route» در حالتِ دکان نیست"
      if (r.route.pattern != pattern) return@mapNotNull "$id: «$route» به «${r.route.pattern}» رسید"
      val root = table.chain(path).firstOrNull()?.let(table::resolve)?.route
      if (root == null || (root.kind != RouteKind.TAB && root.pattern !in doors)) "$id: زنجیرهٔ «$route» به هیچ دری نمی‌رسد (${table.chain(path)})"
      else null
    }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `نامِ هر جزء در کد هست (فهرست کهنه نمی‌شود)`() {
    val unknown = features.flatMap { it.symbols.toList() }.filter { it !in graph.declared }
    assertEquals(emptyList<String>(), unknown)
  }

  @Test
  fun `نقشهٔ ایستا رسیده و نرسیده را از هم جدا می‌کند`() {
    val g = SourceGraph.parse(
      listOf(
        """
        package a
        import b.Used
        import b.Other as Renamed
        /* fun Ghost() نام در توضیح یال نیست */
        @Composable
        fun Root() { Used(); val t = "Dead() ${'$'}{Renamed()}"; c.deep.Far.go() }
        fun Dead() = "Root"
        """.trimIndent(),
        "package b\nfun Used() = Helper()\nprivate fun Helper() {}\nfun Other() {}\nfun Unused() {}",
        "package c.deep\nobject Far { fun go() {} }",
      ),
    )
    val r = g.reach(listOf("a.Root"))
    assertTrue(r.containsAll(listOf("a.Root", "b.Used", "b.Helper", "b.Other", "c.deep.Far")))
    assertFalse("a.Dead" in r)
    assertFalse("b.Unused" in r)
  }

  @Test
  fun `توضیح و متنِ رشته کنار می‌روند و سطرها سرِ جایشان می‌مانند`() {
    val src = "val a = \"x // y\" // z\nfun b() = '\"' /* c\n d */ + \"\"\"e\n\"\"\""
    val out = SourceGraph.strip(src)
    assertEquals(src.count { it == '\n' }, out.count { it == '\n' })
    assertFalse(out.contains("y") || out.contains("z") || out.contains("c") || out.contains("e"))
    assertTrue(out.startsWith("val a = ") && out.contains("fun b()"))
  }

  @Test
  fun `بخشِ «سرورها» در تنظیماتِ نسخهٔ ۳ جای ثابت و جست‌وجو دارد`() {
    //  کلیدِ بخش همان `SettingsLayout.SERVERS` (= عنوانِ پیشینِ `SERVERS_SECTION_TITLE`)
    val key = "سرورها"
    assertEquals("data", SettingsLayout.partOf(key))
    assertEquals("servers", SettingsLayout.SECTION_SLUGS[key])
    assertTrue(SettingsSearchIndex.keywords(key).isNotEmpty())
    //  کلاسِ B: تصمیمِ صاحب (وصل به سرورِ دیگر)، پیش‌فرض خاموش و دیدنی در دستهٔ خودش
    assertEquals(SettingsTiers.Tier.ADVANCED, SettingsTiers.tier(key))
    assertTrue(SettingsLayout.visible(key, desktop = false, serverOn = emptySet(), featuresOn = emptySet()))
    assertTrue(SettingsLayout.visible(key, desktop = true, serverOn = emptySet(), featuresOn = emptySet()))
  }

  private companion object {
    const val APP_ROOT = "ir.vil3ntec.tohid.ui.AppRoot"
    const val PHONE_SALE = "ir.vil3ntec.tohid.ui.v3.sales.cart.SaleRoute"
    const val PHONE_REPORTS = "ir.vil3ntec.tohid.ui.v3.analytics.ReportsScreen"
    const val DESK_REPORTS = "ir.vil3ntec.tohid.ui.v3.analytics.ReportsDesk"
    val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
    val graph: SourceGraph by lazy { SourceGraph.of(File(repo, "android-native/app/src/main/java")) }
    private val cache = HashMap<String, Set<String>>()
    fun reached(root: String): Set<String> = cache.getOrPut(root) { graph.reach(listOf(root)) }
  }
}
