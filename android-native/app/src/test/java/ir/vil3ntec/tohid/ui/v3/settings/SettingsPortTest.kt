package ir.vil3ntec.tohid.ui.v3.settings

import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.settings.sections.CheckoutRulesText
import ir.vil3ntec.tohid.ui.v3.settings.sections.plainAfghani
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  هیچ تنظیمی از دست نمی‌رود — رابطِ قدیم (`ui/legacy`) ۱۴۰۵/۰۸/۱۱ پاک شد و نسخهٔ ۳ تنها رابط است.
 *
 *  «تنظیم» این‌جا یعنی **نوشتنِ** یک مقدارِ ماندگار: `Owner.set…(` روی شیءِ نام‌دار (`ShopPolicy.setPayV2`،
 *  `ReturnSettings.setLimit`، `LocaleChoice.setDigits`…) یا `….idleMinutes = …` ِ قفل. دو سنجش:
 *   • هر نوشتنی که تنظیماتِ رابطِ قدیم داشت ([LEGACY_WRITERS]، فهرستِ ثابتِ همان روزِ پاک شدن) در تنظیماتِ نسخهٔ ۳
 *     (همهٔ پرونده‌های زیرِ `ui/v3/settings` و هر `…Section(` / `…Settings(` / `…SettingsWired(` / `…Choice(` ای که
 *     صدا می‌زنند) هم هست؛
 *   • هر جزءِ تنظیمِ بیرون از `ui/v3` (هر پرونده‌ای که چنین تابعی تعریف می‌کند و جزءهای صدازده‌اش) نوشتنش را در
 *     نسخهٔ ۳ هم دارد، یا در [OUTSIDE_SETTINGS]/[EXCEPTIONS] با دلیل — بخشِ تازه‌ای که صفحهٔ نسخهٔ ۳ نگیرد همین‌جا قرمز است.
 *
 *  ⚠️ متن با متن، مثلِ `SettingsLayoutTest`: کامپیوتر پیش از کامپایل چند عبارت را در کدِ اندروید عوض
 *  می‌کند؛ این آزمون همان پرونده‌های خامِ `android-native` را می‌خواند.
 */
class SettingsPortTest {

  private val root: File by lazy {
    listOf(
      "src/main/java/ir/vil3ntec/tohid",
      "../android-native/app/src/main/java/ir/vil3ntec/tohid",
      "android-native/app/src/main/java/ir/vil3ntec/tohid",
    ).map(::File).firstOrNull { it.isDirectory } ?: error("ریشهٔ کد پیدا نشد (cwd=${File(".").absoluteFile})")
  }

  private val sources: Map<File, String> by lazy {
    root.walkTopDown().filter { it.isFile && it.extension == "kt" }.associateWith { it.readText() }
  }

  private fun rel(f: File): String = f.relativeTo(root).invariantSeparatorsPath

  /** نوشتنِ تنظیم روی شیءِ نام‌دار: `ShopPolicy.setPayV2(`، `HeldInvoicePrefs.set(` */
  private val writer = Regex("""\b([A-Z][A-Za-z0-9]*)\.(set(?:[A-Z][A-Za-z0-9]*)?)\(""")

  /** «قفلِ خودکار پس از بی‌کاری» (STF-05): ویژگیِ `LockStore`، نه تابع */
  private val idle = Regex("""\b[a-z][A-Za-z0-9]*\.idleMinutes\s*=[^=]""")

  /** جزءِ صدازدهٔ صفحه که خودش تنظیم می‌نویسد — `…SettingsWired(` (یک خطِ تنظیمِ رابطِ امروز) هم */
  private val part = Regex("""\b([A-Z][A-Za-z0-9]*(?:Section|Settings|SettingsWired|Choice))\(""")

  /** `set…` هایی که تنظیم نیستند */
  private val notSettings = setOf("SalesEngine", "Modifier")

  private fun definitions(name: String, allow: (File) -> Boolean): List<File> {
    val def = Regex("""fun (?:[A-Za-z0-9_.]+\.)?""" + Regex.escape(name) + """\(""")
    return sources.filter { (f, text) -> allow(f) && def.containsMatchIn(text) }.keys.toList()
  }

  /** خودِ پرونده‌ها و پروندهٔ هر جزءِ صدازده، تا ته */
  private fun closure(start: List<File>, allow: (File) -> Boolean): Set<File> {
    val seen = start.toMutableSet()
    val todo = ArrayDeque(start)
    while (todo.isNotEmpty()) {
      val f = todo.removeFirst()
      part.findAll(sources.getValue(f)).map { it.groupValues[1] }.toSet().forEach { name ->
        definitions(name, allow).forEach { d -> if (seen.add(d)) todo.addLast(d) }
      }
    }
    return seen
  }

  private fun writers(files: Collection<File>): Set<String> = files.flatMap { f ->
    val text = sources.getValue(f)
    writer.findAll(text)
      .filter { it.groupValues[1] !in notSettings }
      .map { it.groupValues[1] + "." + it.groupValues[2] }
      .toList() + (if (idle.containsMatchIn(text)) listOf("LockStore.idleMinutes") else emptyList())
  }.toSet()

  /** جزءهای تنظیمِ بیرون از نسخهٔ ۳: هر پرونده‌ای که `fun …Section(` / `…Settings(` / `…SettingsWired(` / `…Choice(` دارد */
  private val outsideFiles: Set<File> by lazy {
    val def = Regex("""fun (?:[A-Za-z0-9_.]+\.)?[A-Z][A-Za-z0-9]*(?:Section|Settings|SettingsWired|Choice)\(""")
    closure(sources.filter { (f, text) -> !rel(f).startsWith("ui/v3/") && def.containsMatchIn(text) }.keys.toList()) { !rel(it).startsWith("ui/v3/") }
  }

  private val v3Files: Set<File> by lazy {
    closure(sources.keys.filter { rel(it).startsWith("ui/v3/settings/") }) { true }
  }

  @Test
  fun `هر تنظیمِ رابطِ قدیمِ پاک‌شده در نسخهٔ ۳ صفحه دارد`() {
    val v3 = writers(v3Files)
    assertEquals("فهرستِ ثابتِ رابطِ قدیم تکراری دارد", LEGACY_WRITERS.size, LEGACY_WRITERS.toSet().size)
    assertTrue("نوشتنِ تنظیمی در نسخهٔ ۳ پیدا نشد — الگوی متن عوض شده؟", v3.size >= LEGACY_WRITERS.size)
    val missing = LEGACY_WRITERS.toSet() - v3
    assertTrue("این تنظیم‌های رابطِ قدیم در نسخهٔ ۳ صفحه ندارند:\n" + missing.sorted().joinToString("\n"), missing.isEmpty())
    assertFalse("رابطِ قدیم برگشته است", root.resolve("ui/legacy").exists())
  }

  @Test
  fun `هر جزءِ تنظیمِ بیرون از نسخهٔ ۳ در نسخهٔ ۳ جایی دارد یا با دلیل استثناست`() {
    val outside = writers(outsideFiles)
    val v3 = writers(v3Files)
    println("settings port: outside ${outside.size} — ${outside.sorted()}")
    assertTrue("نوشتنِ تنظیمی بیرون از نسخهٔ ۳ پیدا نشد — الگوی متن عوض شده؟", outside.size >= 20)
    val missing = outside - v3 - EXCEPTIONS.keys - OUTSIDE_SETTINGS.keys
    assertTrue(
      "این تنظیم‌ها در نسخهٔ ۳ صفحه ندارند — به `ui/v3/settings` ببرید، یا با دلیل در EXCEPTIONS بگذارید:\n" +
        missing.sorted().joinToString("\n"),
      missing.isEmpty(),
    )
    //  استثنای کهنه نمی‌ماند: یا هنوز بیرون هست و در نسخهٔ ۳ نیست، یا برداشته شود
    val stale = (EXCEPTIONS.keys + OUTSIDE_SETTINGS.keys).filter { it !in outside || it in v3 }
    assertTrue("استثنای کهنه: $stale", stale.isEmpty())
    assertTrue(OUTSIDE_SETTINGS.values.all { it.isNotBlank() })
    //  ترازو: همان کلید از صفحهٔ «دستگاه‌ها»ی نسخهٔ ۳
    assertTrue(sources.entries.first { rel(it.key) == "ui/screens/DevicesHw.kt" }.value.contains("ScaleAtPosSettings(rules)"))
    assertTrue(sources.entries.first { rel(it.key) == "ui/v3/settings/SettingsRoutes.kt" }.value.contains("DevicesScreen(data"))
  }

  @Test
  fun `بخش‌های آمده از رابطِ امروز هر کدام گروه، صفحه و نشانیِ خودشان را دارند`() {
    val ported = listOf(
      SettingsLayout.ENCRYPTION, SettingsLayout.CHECKOUT_RULES, SettingsLayout.HELD, SettingsLayout.RETURNS,
      SettingsLayout.PAY, SettingsLayout.CREDIT_GATE, SettingsLayout.CRM_REPORTS,
      SettingsLayout.PAY_FIX, SettingsLayout.PRICE_LIST, SettingsLayout.QUOTE,
    )
    ported.forEach { key ->
      assertTrue("$key در هیچ گروهی نیست", SettingsLayout.groupOf(key) != null)
      assertTrue("$key نشانی ندارد", SettingsLayout.SECTION_SLUGS.containsKey(key))
      //  صفحهٔ هر بخش را `SettingsLayoutTest` از متنِ `SettingsPages.kt` می‌سنجد
      assertTrue("$key نامِ ردیف ندارد", SettingsLayout.labelOf(key).isNotBlank())
    }
    //  تنظیماتِ نسخهٔ دوم (۱۴۰۵/۰۸/۱۱): هر بخش در دستهٔ کارِ خودش
    assertEquals(SettingsLayout.SHOP, SettingsLayout.groupOf(SettingsLayout.ENCRYPTION))
    assertEquals(SettingsLayout.DATA, SettingsLayout.groupOf(SettingsLayout.CRM_REPORTS))
    ported.filterNot { it == SettingsLayout.ENCRYPTION || it == SettingsLayout.CRM_REPORTS }
      .forEach { assertEquals(it, SettingsLayout.SALES, SettingsLayout.groupOf(it)) }
    //  کلیدهای صندوق و گزارشِ کامپیوتر مثلِ رابطِ امروز فقط کامپیوتر. «گزارش‌های مشتری» از دورِ ۱۴ روی گوشی هم:
    //  ادغامِ تکراری‌ها، تصویرِ صورت‌حساب، گروه و لیستِ قیمت، و سنِ بدهی و RFM ِ گوشی پشتِ همین کلیدِ همین دستگاه‌اند
    //  (`V3ReachabilityTest`)؛ پنهان بودنش روی گوشی یعنی آن‌ها روی گوشی هرگز روشن نمی‌شدند.
    assertEquals(DESK_ONLY.keys, SettingsLayout.PHONE_HIDDEN)
    assertFalse(SettingsLayout.CRM_REPORTS in SettingsLayout.PHONE_HIDDEN)
    assertTrue(SettingsLayout.CRM_REPORTS in SettingsLayout.deskSections)
    //  کلیدِ بخش داده است («// داده:» ِ `SettingsLayout`) و همان عنوانِ پیشین می‌ماند؛ ثابت‌های `…_TITLE` ِ رابطِ امروز بی‌راه بودند و
    //  برداشته شدند (`claude/e-rows-39m`) ⇒ مقدار این‌جا میخ است تا کلید بی‌صدا عوض نشود
    assertEquals("برگشت و تعویض", SettingsLayout.RETURNS)
    assertEquals("لیستِ قیمت در صندوق (خرده/عمده)", SettingsLayout.PRICE_LIST)
  }

  @Test
  fun `پیش‌فاکتور، لیستِ قیمت و اصلاحِ پرداخت همان کلیدِ رابطِ امروز را می‌نویسند`() {
    val v3 = writers(v3Files)
    listOf("QuotePref.set", "PriceListPrefs.set", "PayFixPref.set").forEach { assertTrue("$it در نسخهٔ ۳ نیست", it in v3) }
    assertEquals(
      listOf("settings/sales/payment-fix", "settings/sales/price-lists", "settings/sales/quotes"),
      listOf(SettingsLayout.PAY_FIX, SettingsLayout.PRICE_LIST, SettingsLayout.QUOTE).map(SettingsPaths::section),
    )
  }

  @Test
  fun `پول و صندوق، کالا و انبار، گزارش و ویترین هر کدام صفحه دارند و همان نوشتنِ رابطِ امروز را صدا می‌زنند`() {
    val v3 = writers(v3Files)
    PORTED_WRITERS.forEach { assertTrue("$it در نسخهٔ ۳ نیست", it in v3) }
    val keys = mapOf(
      SettingsLayout.GIFT_CARD to "settings/sales/gift-cards",
      SettingsLayout.LAYAWAY to "settings/sales/layaway",
      SettingsLayout.LOYALTY to "settings/sales/loyalty",
      SettingsLayout.SAFE_BOOK to "settings/sales/safe",
      SettingsLayout.TAX_QUARTER to "settings/data/tax-quarter",
      SettingsLayout.CUSTOMER_CARD to "settings/sales/customer-cards",
      SettingsLayout.TILL_GOODS to "settings/goods/till-goods",
      SettingsLayout.STOCK_PLACES to "settings/goods/stock-places",
      SettingsLayout.SMART_FORECAST to "settings/goods/forecast",
      SettingsLayout.ANOMALY to "settings/shop/anomalies",
      SettingsLayout.OWNER_REPORT to "settings/data/owner-report",
      SettingsLayout.STOREFRONT_ETA to "settings/sales/storefront-eta",
      SettingsLayout.PROMO_GUARD to "settings/sales/promo-guard",
      SettingsLayout.GIFT_RECEIPT to "settings/sales/gift-receipt",
      SettingsLayout.DELIVER_LATER to "settings/sales/deliver-later",
      SettingsLayout.SPECIAL_ORDER to "settings/sales/special-orders",
      SettingsLayout.PRICE_WHY to "settings/sales/price-why",
      SettingsLayout.SHRINK_REPORT to "settings/goods/shrink-report",
      SettingsLayout.COUNT_TOOLS to "settings/goods/stock-count",
      SettingsLayout.LABEL_TOOLS to "settings/goods/labels",
      SettingsLayout.PRODUCT_INFO to "settings/goods/product-info",
      SettingsLayout.PRODUCT_SUGGEST to "settings/goods/product-suggest",
      SettingsLayout.POS_SEARCH to "settings/sales/smart-search",
      SettingsLayout.POS_PRICE_CHECK to "settings/sales/price-check",
      SettingsLayout.POS_TOUCH to "settings/sales/touch-mode",
      SettingsLayout.POS_MORE_KEYS to "settings/sales/more-keys",
      SettingsLayout.KEY_HELP to "settings/sales/key-help",
      SettingsLayout.TILL_SWITCH to "settings/shop/till-switch",
      SettingsLayout.REPORT_KEYS to "settings/data/report-keys",
      SettingsLayout.DOC_GAPS to "settings/data/doc-gaps",
      SettingsLayout.LINE_PRICING to "settings/sales/line-pricing",
      SettingsLayout.POS_NOTES to "settings/sales/notes",
      SettingsLayout.AUTO_FEES to "settings/sales/auto-fees",
      SettingsLayout.SIMPLE_ASK to "settings/data/simple-ask",
      SettingsLayout.EASY_SETUP to "settings/devices/easy-setup",
      SettingsLayout.SCAN_CASH to "settings/devices/scan-cash",
      SettingsLayout.WEBCAM to "settings/devices/webcam",
      SettingsLayout.TRAINING to "settings/look/training",
    )
    val table = ir.vil3ntec.tohid.ui.v3.nav.V3Modules.table
    keys.forEach { (key, path) ->
      assertEquals(key, path, SettingsPaths.section(key))
      assertTrue("$key ⇐ $path", table.resolve(path) != null)
      assertTrue("$key نامِ ردیف ندارد", SettingsLayout.labelOf(key).isNotBlank())
      //  روی کامپیوتر همه (هیچ‌کدام فقط‌گوشی نیست، مثلِ رابطِ امروز)
      assertTrue("$key در ستونِ کامپیوتر نیست", key in SettingsLayout.deskSections)
      assertEquals("$key فقط‌کامپیوتر؟", key in DESK_ONLY, key in SettingsLayout.PHONE_HIDDEN)
    }
    //  کلید همان عنوانِ پیشینِ بخش می‌ماند (ثابت‌های `…_TITLE` ِ رابطِ امروز بی‌راه بودند و برداشته شدند؛ مقدار میخ است)
    mapOf(
      SettingsLayout.STOCK_PLACES to "محل‌ها و بسته‌بندیِ دوباره",
      SettingsLayout.SMART_FORECAST to "پیش‌بینیِ تقاضا (آزمایشی)",
      SettingsLayout.ANOMALY to "تشخیصِ ناهنجاری (آزمایشی)",
      SettingsLayout.OWNER_REPORT to "گزارشِ خودکار برای صاحب",
      SettingsLayout.STOREFRONT_ETA to "زمانِ تقریبیِ تحویلِ ویترین",
      SettingsLayout.PROMO_GUARD to "تخفیفِ اولین خرید و سدِ سوءاستفاده",
      SettingsLayout.EASY_SETUP to "راه‌اندازیِ آسانِ چاپگر و بارکدخوان (آزمایشی)",
      SettingsLayout.SCAN_CASH to "فروشِ نقدی با کارتِ فرمان",
      SettingsLayout.TRAINING to "حالتِ آموزشی",
      SettingsLayout.TILL_GOODS to "ثبتِ سریع و واحدِ بزرگ در صندوق",
    ).forEach { (key, title) -> assertEquals(title, key) }
    //  حالتِ آموزشی: ورود و پاک کردن همان `TrainingLedger` و همان سدِ نقشِ `TrainingSettingsWired`
    val ops = sources.entries.first { rel(it.key) == "ui/v3/settings/sections/OpsToggleScreensAndroid.kt" }.value
    listOf("TrainingLedger.enter(", "TrainingLedger.wipe(", "TrainingMode.canEnter(role, staff)", "TrainingMode.canManage(role)", "if (!active)")
      .forEach { assertTrue("$it در صفحهٔ آموزش نیست", ops.contains(it)) }
  }

  @Test
  fun `نشانیِ پیش از نسخهٔ دوم هم همان بخش را باز می‌کند`() {
    val table = ir.vil3ntec.tohid.ui.v3.nav.V3Modules.table
    OLD_PATHS.forEach { (path, key) ->
      assertTrue(path, table.resolve(path)?.route?.pattern == "settings/{group}/{section}")
      val group = SettingsPaths.groupOfSlug(path.split('/')[1])!!
      assertEquals(path, key, SettingsPaths.sectionOfSlug(group, path.substringAfterLast('/')))
      assertTrue("$path دیگر نشانیِ امروزِ $key نیست", SettingsPaths.section(key) != path)
    }
  }

  @Test
  fun `بخش‌های پشتِ پرچمِ سرور تا پاسخِ روشن ردیف ندارند`() {
    assertEquals(
      //  «سفارشِ آنلاین» (۱۴۰۵/۰۸/۱۳): پشتِ پاسخِ `/shop/orders/config` (`onlineOrdersRow`)، نه پرچم
      //  «نشانی و نقشهٔ دکان» (ممیزیِ ویترین ۱۴۰۵/۰۸/۱۷): پیوند به «ویترین ⇐ تنظیمات»، پشتِ همان درِ «سفارشِ آنلاین»
      //  «کالای ناموجود در ویترین» (WEB-09): همان پیوند و همان در
      setOf(SettingsLayout.OWNER_REPORT, SettingsLayout.STOREFRONT_ETA, SettingsLayout.PROMO_GUARD, SettingsLayout.ONLINE_ORDERS, SettingsLayout.STORE_PLACE, SettingsLayout.STORE_STOCK),
      SettingsLayout.SERVER_GATED,
    )
    SettingsLayout.SERVER_GATED.forEach { key ->
      assertFalse("$key بی پاسخِ سرور دیده شد", SettingsLayout.shown(key, emptySet()))
      assertTrue(SettingsLayout.shown(key, setOf(key)))
      assertFalse("$key با پاسخِ بخشِ دیگری دیده شد", SettingsLayout.shown(key, SettingsLayout.SERVER_GATED - key))
    }
    //  بخشِ بی پرچم همیشه دیده می‌شود
    (SettingsLayout.sections - SettingsLayout.SERVER_GATED).forEach { assertTrue(it, SettingsLayout.shown(it, emptySet())) }
    //  صفحهٔ دسته (گوشی و کامپیوتر) و جست‌وجو همان فیلتر را می‌گذرانند، و هر سه صفحه خودش هم با پاسخِ خاموش هیچ نمی‌کشد
    SettingsLayout.SERVER_GATED.forEach { key ->
      for (desktop in listOf(true, false)) {
        assertFalse(key, SettingsLayout.visible(key, desktop, emptySet(), SettingsLayout.FEATURES.toSet()))
        assertTrue(key, SettingsLayout.visible(key, desktop, setOf(key), emptySet()))
        assertTrue(key, ir.vil3ntec.tohid.ui.screens.SettingsSearchIndex.docs(desktop, emptySet()).none { it.key == key })
      }
    }
    val rows = sources.entries.first { rel(it.key) == "ui/v3/settings/SettingsRows.kt" }.value
    val index = sources.entries.first { rel(it.key) == "ui/v3/settings/SettingsIndexScreen.kt" }.value
    assertTrue(rows.contains("SettingsLayout.visibleParts(group, Platform.DESKTOP, facts.serverOn, featuresOn)") && rows.contains("serverOn = rememberServerSections()"))
    assertTrue(index.contains("SettingsSearchIndex.docs(Platform.DESKTOP, facts.serverOn, extras)"))
    val server = sources.entries.first { rel(it.key) == "ui/v3/settings/sections/ServerSettingsScreensAndroid.kt" }.value
    listOf("ownerReportRow(", "storefrontEtaRow(", "promoGuardRow(", "onlineOrdersRow(", "it.enabled && it.owner", "StorefrontEtaApi.visible(it)", "ShopRole.OWNER")
      .forEach { assertTrue("$it در صفحه‌های سروری نیست", server.contains(it)) }
  }

  @Test
  fun `آستانهٔ قاعدهٔ فاکتور همان قاعدهٔ رابطِ امروز`() {
    assertEquals(1234.5, CheckoutRulesText.valid("1234.5")!!, 0.0)
    assertEquals(0.0, CheckoutRulesText.valid("0")!!, 0.0)
    assertNull(CheckoutRulesText.valid("-5"))
    assertNull(CheckoutRulesText.valid("abc"))
    assertEquals("1234.5", CheckoutRulesText.afn(123450))
    assertEquals("1000", CheckoutRulesText.afn(100000))
    assertEquals("0.05", CheckoutRulesText.afn(5))
    assertEquals("1500", plainAfghani(1500.0))
    assertEquals("12.5", plainAfghani(12.5))
  }

  companion object {
    /** نوشتن‌هایی که از EXCEPTIONS به صفحه‌های نسخهٔ ۳ آمدند — نباید دوباره استثنا شوند */
    val PORTED_WRITERS: List<String> = listOf(
      "GiftCardPref.set", "LayawayPref.set", "LoyaltyPref.set", "LoyaltyPref.setRule", "SafeBookPref.set",
      "TaxQuarterPref.setOn", "TaxQuarterPref.setConfig", "CustomerCardPref.setOn",
      "StockPlacesPref.setPlaces", "StockPlacesPref.setRepack",
      "SmartForecastPref.set", "AnomalyPref.set", "OwnerReportLive.set",
      "StorefrontEtaLive.set", "PromoGuardLive.set",
      "GiftReceiptPrefs.set", "DeliverLaterPrefs.set", "SpecialOrderPref.set", "PriceWhyPrefs.set",
      "ShrinkReportPref.set", "CountToolsPref.setOn", "CountToolsPref.setBlind", "LabelToolsPref.set", "ProductInfoPrefs.set",
      "PosSearchPrefs.set", "PosPriceCheckPrefs.set", "PosTouchPrefs.setOn", "PosMoreKeysPrefs.set", "KeyHelpPref.set", "TillUser.set", "ReportKeysPref.setOn", "DocGapsPref.setOn",
      "LinePolicyPrefs.setOn", "LinePolicyPrefs.setDirection", "LinePolicyPrefs.setFreeLineCapMinor",
      "ReturnPolicyPrefs.setDays", "ReturnPolicyPrefs.setBlocked", "PosNotesPrefs.set", "PosNotesPrefs.setTemplates",
      "AutoFeesPref.set", "SimpleAskPref.set", "ProductSuggestPref.set", "EasySetupPref.set", "ScanCashPref.set", "TrainingPref.setOn", "TrainingPref.setStaff",
      //  S03/S05/S22 — ثبتِ سریع، واحدِ بزرگ و «بستهٔ کامل»؛ «منقضی فقط با مجوز» و روزِ هشدار
      "TillGoodsPref.setQuickRegister", "TillGoodsPref.setUnits", "TillGoodsPref.setWholePacks", "ExpiryPref.setApproval", "ExpiryPref.setNearDays",
    )

    /** نشانیِ پیش از نسخهٔ دوم (۱۴۰۵/۰۸/۱۱) ⇐ بخش — پیوندِ کهنه باید همان صفحه را باز کند */
    val OLD_PATHS: Map<String, String> = mapOf(
      "settings/sales/tax-quarter" to SettingsLayout.TAX_QUARTER,
      "settings/sales/till-goods" to SettingsLayout.TILL_GOODS,
      "settings/sales/stock-places" to SettingsLayout.STOCK_PLACES,
      "settings/sales/forecast" to SettingsLayout.SMART_FORECAST,
      "settings/security/anomalies" to SettingsLayout.ANOMALY,
      "settings/sales/owner-report" to SettingsLayout.OWNER_REPORT,
      "settings/sales/shrink-report" to SettingsLayout.SHRINK_REPORT,
      "settings/sales/stock-count" to SettingsLayout.COUNT_TOOLS,
      "settings/sales/labels" to SettingsLayout.LABEL_TOOLS,
      "settings/sales/product-info" to SettingsLayout.PRODUCT_INFO,
      "settings/sales/till-switch" to SettingsLayout.TILL_SWITCH,
      "settings/sales/report-keys" to SettingsLayout.REPORT_KEYS,
      "settings/sales/doc-gaps" to SettingsLayout.DOC_GAPS,
      "settings/sales/simple-ask" to SettingsLayout.SIMPLE_ASK,
      "settings/general/training" to SettingsLayout.TRAINING,
      "settings/sales/customer-reports" to SettingsLayout.CRM_REPORTS,
      "settings/security/app-lock" to "قفل برنامه",
      "settings/security/encryption" to SettingsLayout.ENCRYPTION,
      "settings/general/appearance" to "ظاهر",
    )

    /**
     *  بخش‌هایی که عمداً فقط کامپیوتر ردیف دارند ⇐ دلیل (رابطِ قدیم همین‌ها را پشتِ `Platform.DESKTOP` داشت). هیچ صفحهٔ
     *  گوشی پشتِ کلیدشان نیست (`V3ReachabilityTest`، `GATES`). POS-15: «قیمت‌پرسی در صندوق» از دورِ ۱۱ و «گزارش‌های مشتری»
     *  از دورِ ۱۴ گوشی هم دارند ⇒ این‌جا نیستند.
     */
    private val DESK_ONLY: Map<String, String> = mapOf(
      SettingsLayout.POS_SEARCH to "جست‌وجوی کادرِ صندوقِ کامپیوتر (`DeskSale`)؛ گوشی `CatalogSearch` ِ خودش را دارد — ردهٔ A، روشن",
      SettingsLayout.POS_TOUCH to "دکمه‌های لمسیِ صندوقِ کامپیوتر (`TouchKeypad`)؛ گوشی همیشه لمسی است",
      SettingsLayout.POS_MORE_KEYS to "کلیدهای بیشترِ صفحه‌کلیدِ صندوقِ کامپیوتر (`DeskSale`)",
      SettingsLayout.KEY_HELP to "برچسبِ کلیدهای میان‌برِ صفحه‌کلید — ردهٔ A، روشن",
      SettingsLayout.TILL_SWITCH to "تعویضِ کاربرِ صندوق با Ctrl+L ِ کامپیوتر (`AppRoot`)؛ خاموش ⇒ `TillUser.me` همان نامِ حساب",
      SettingsLayout.REPORT_KEYS to "کلیدهای دورهٔ «گزارش‌ها» ِ کامپیوتر (`ReportsDesk`) — ردهٔ A، روشن",
      //  HW-08
      SettingsLayout.WEBCAM to "وب‌کم به‌عنوانِ بارکدخوان — گوشی دوربینِ خودش را دارد",
    )

    /** دلیلِ پیش‌فرضِ استثنای تازه: `"X.set" to "$PENDING — …"` */
    @Suppress("unused")
    private const val PENDING = "هنوز فقط در جزءِ بیرون از `ui/v3` (`…SettingsWired`)؛ صفحهٔ نسخهٔ ۳ ندارد"

    /**
     *  نوشتنِ تنظیمی که در نسخهٔ ۳ نیست ⇐ دلیل. اکنون خالی: همهٔ تنظیم‌ها صفحهٔ نسخهٔ ۳ دارند. بخشِ تازه‌ای که
     *  صفحهٔ نسخهٔ ۳ نگیرد همین‌جا قرمز می‌شود — یا صفحه‌اش را بسازید، یا با `PENDING` و دلیل این‌جا بگذارید
     *  (و هر کدام که آمد برداشته شود: «استثنای کهنه» قرمز است).
     */
    val EXCEPTIONS: Map<String, String> = emptyMap()

    /** نوشتنی که جای نسخهٔ ۳ ِ آن صفحه‌ای غیر از تنظیمات است ⇐ همان صفحه */
    val OUTSIDE_SETTINGS: Map<String, String> = mapOf(
      "ScaleAtPos.set" to "«دستگاه‌ها ⇐ ترازو» (`DevicesScreen` ⇐ `ScalePanel` ⇐ `ScaleAtPosSettings`)",
    )

    /** همهٔ نوشتن‌های تنظیماتِ رابطِ قدیم (`SettingsLegacy` و جزءهایش) در روزِ پاک شدن — ۱۴۰۵/۰۸/۱۱ */
    val LEGACY_WRITERS: List<String> = listOf(
      "AnomalyPref.set", "AtRest.setWanted", "AutoFeesPref.set", "BackupMirror.setEnabled", "BackupMirror.setFolder",
      "CashRoundPref.set", "CheckoutRulesPref.set", "CountToolsPref.setBlind", "CountToolsPref.setOn",
      "CustomerCardPref.setOn", "DebtReminderPref.set", "DeliverLaterPrefs.set", "DocGapsPref.setOn",
      "EasySetupPref.set", "ExpiryPref.setApproval", "ExpiryPref.setNearDays", "FraudTrailPref.setEnabled",
      "GiftCardPref.set", "GiftReceiptPrefs.set", "HeldInvoicePrefs.set", "HeldInvoicePrefs.setAutoClean",
      "KeyHelpPref.set", "LabelToolsPref.set", "LateApologyLive.set", "LayawayPref.set",
      "LinePolicyPrefs.setDirection", "LinePolicyPrefs.setFreeLineCapMinor", "LinePolicyPrefs.setOn",
      "LocaleChoice.setCalendar", "LocaleChoice.setDigits", "LocaleChoice.setLang", "LocaleChoice.setLunar",
      "LocaleChoice.setLunarAdjust", "LocaleChoice.setMonths", "LocaleChoice.setSymbol", "LockStore.idleMinutes",
      "LoyaltyPref.set", "LoyaltyPref.setExpiry", "LoyaltyPref.setRule", "LoyaltyShop.setRule", "Motion.set",
      "OwnerReportLive.set", "PayFixPref.set", "PosMoreKeysPrefs.set", "PosNotesPrefs.set",
      "PosNotesPrefs.setTemplates", "PosPriceCheckPrefs.set", "PosSearchPrefs.set", "PosTouchPrefs.setOn",
      "PriceListPrefs.set", "PriceWhyPrefs.set", "ProductInfoPrefs.set", "PromoGuardLive.set", "QuotePref.set",
      "ReportKeysPref.setOn", "ReturnPolicyPrefs.setBlocked", "ReturnPolicyPrefs.setDays",
      "ReturnSettings.setEnabled", "ReturnSettings.setLimit", "ReturnSettings.setReasons", "SafeBookPref.set",
      "ScanCashPref.set", "ShopDayChoice.set", "ShopPolicy.setBlockExpired", "ShopPolicy.setChatMediaGuard",
      "ShopPolicy.setCreditGate", "ShopPolicy.setCrmReports", "ShopPolicy.setPayV2", "ShopPolicy.setProductTools",
      "ShopPolicy.setStockInsight", "ShopPolicy.setStockLevels", "ShrinkReportPref.set", "SimpleAskPref.set",
      "SmartForecastPref.set", "SpecialOrderPref.set", "StockActions.setMode", "StockPlacesPref.setPlaces",
      "StockPlacesPref.setRepack", "StorefrontEtaLive.set", "TaxQuarterPref.setConfig", "TaxQuarterPref.setOn",
      "TillGoodsPref.setQuickRegister", "TillGoodsPref.setUnits", "TillGoodsPref.setWholePacks",
      "TillReceivePref.set", "TillUser.set", "TrainingPref.setOn", "TrainingPref.setStaff", "Watchman.setDebtLimit",
    )
  }
}
