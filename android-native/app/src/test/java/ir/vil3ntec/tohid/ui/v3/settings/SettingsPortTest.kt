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
 *  پیش از روشن شدنِ `UiV3` برای همه هیچ تنظیمی نباید از دست برود.
 *
 *  «تنظیم» این‌جا یعنی **نوشتنِ** یک مقدارِ ماندگار: `Owner.set…(` روی شیءِ نام‌دار (`ShopPolicy.setPayV2`،
 *  `ReturnSettings.setLimit`، `LocaleChoice.setDigits`…) یا `….idleMinutes = …` ِ قفل. هر چنین نوشتنی در صفحهٔ
 *  تنظیماتِ رابطِ امروز (`ui/legacy/SettingsLegacy.kt` و هر `…Section(` / `…Settings(` / `…Choice(` ای که
 *  صدا می‌زند، بیرون از `ui/v3`) باید در تنظیماتِ نسخهٔ ۳ (همهٔ پرونده‌های زیرِ `ui/v3/settings` و همان جزءهای صدازده) هم باشد،
 *  یا در [EXCEPTIONS] با دلیل. بخشِ تازه‌ای که به رابطِ امروز اضافه شود و به نسخهٔ ۳ نیاید همین‌جا قرمز می‌شود.
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

  private val legacyFiles: Set<File> by lazy {
    val start = root.resolve("ui/legacy/SettingsLegacy.kt")
    assertTrue("$start پیدا نشد", start.isFile)
    closure(listOf(start)) { !rel(it).startsWith("ui/v3/") }
  }

  private val v3Files: Set<File> by lazy {
    closure(sources.keys.filter { rel(it).startsWith("ui/v3/settings/") }) { true }
  }

  @Test
  fun `هر تنظیمِ رابطِ امروز در نسخهٔ ۳ جایی دارد یا با دلیل استثناست`() {
    val legacy = writers(legacyFiles)
    val v3 = writers(v3Files)
    println("settings port: legacy ${legacy.size} — ${legacy.sorted()}")
    assertTrue("نوشتنِ تنظیمی در رابطِ امروز پیدا نشد — الگوی متن عوض شده؟", legacy.size >= 20)
    val missing = legacy - v3 - EXCEPTIONS.keys
    assertTrue(
      "این تنظیم‌ها در نسخهٔ ۳ صفحه ندارند — به `ui/v3/settings` ببرید، یا با دلیل در EXCEPTIONS بگذارید:\n" +
        missing.sorted().joinToString("\n"),
      missing.isEmpty(),
    )
    //  استثنای کهنه نمی‌ماند: یا هنوز در رابطِ امروز هست و در نسخهٔ ۳ نیست، یا برداشته شود
    val stale = EXCEPTIONS.keys.filter { it !in legacy || it in v3 }
    assertTrue("استثنای کهنه: $stale", stale.isEmpty())
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
    //  «گزارش‌های مشتری» و کلیدهای صندوق و گزارشِ کامپیوتر مثلِ رابطِ امروز فقط کامپیوتر
    assertEquals(setOf(SettingsLayout.CRM_REPORTS) + DESK_ONLY, SettingsLayout.PHONE_HIDDEN)
    assertTrue(SettingsLayout.CRM_REPORTS in SettingsLayout.deskSections)
    //  کلیدِ برگشت همان عنوانِ رابطِ امروز است
    assertEquals(ir.vil3ntec.tohid.ui.screens.RETURN_SETTINGS_TITLE, SettingsLayout.RETURNS)
    assertEquals(ir.vil3ntec.tohid.ui.components.pos.PRICE_LIST_SETTINGS_TITLE, SettingsLayout.PRICE_LIST)
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
    //  کلید همان عنوانِ بخشِ رابطِ امروز است
    assertEquals(ir.vil3ntec.tohid.ui.screens.STOCK_PLACES_TITLE, SettingsLayout.STOCK_PLACES)
    assertEquals(ir.vil3ntec.tohid.ui.screens.SMART_FORECAST_TITLE, SettingsLayout.SMART_FORECAST)
    assertEquals(ir.vil3ntec.tohid.ui.screens.ANOMALY_TITLE, SettingsLayout.ANOMALY)
    assertEquals(ir.vil3ntec.tohid.ui.screens.OWNER_REPORT_TITLE, SettingsLayout.OWNER_REPORT)
    assertEquals(ir.vil3ntec.tohid.ui.screens.STOREFRONT_ETA_TITLE, SettingsLayout.STOREFRONT_ETA)
    assertEquals(ir.vil3ntec.tohid.ui.screens.PROMO_GUARD_TITLE, SettingsLayout.PROMO_GUARD)
    assertEquals(ir.vil3ntec.tohid.ui.screens.EASY_SETUP_TITLE, SettingsLayout.EASY_SETUP)
    assertEquals(ir.vil3ntec.tohid.ui.screens.TRAINING_SETTINGS_TITLE, SettingsLayout.TRAINING)
    assertEquals(ir.vil3ntec.tohid.ui.screens.TILL_GOODS_TITLE, SettingsLayout.TILL_GOODS)
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
      setOf(SettingsLayout.OWNER_REPORT, SettingsLayout.STOREFRONT_ETA, SettingsLayout.PROMO_GUARD),
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
    listOf("ownerReportRow(", "storefrontEtaRow(", "promoGuardRow(", "it.enabled && it.owner", "StorefrontEtaApi.visible(it)", "ShopRole.OWNER")
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
      "AutoFeesPref.set", "SimpleAskPref.set", "EasySetupPref.set", "TrainingPref.setOn", "TrainingPref.setStaff",
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

    /** کلیدهایی که `SettingsLegacy` فقط پشتِ `Platform.DESKTOP` دارد */
    private val DESK_ONLY: Set<String> = setOf(
      SettingsLayout.POS_SEARCH, SettingsLayout.POS_PRICE_CHECK, SettingsLayout.POS_TOUCH, SettingsLayout.POS_MORE_KEYS,
      SettingsLayout.KEY_HELP, SettingsLayout.TILL_SWITCH, SettingsLayout.REPORT_KEYS,
    )

    /** دلیلِ پیش‌فرضِ استثنای تازه: `"X.set" to "$PENDING — …"` */
    @Suppress("unused")
    private const val PENDING = "هنوز فقط در رابطِ امروز (`…SettingsWired` ِ `SettingsLegacy`)؛ صفحهٔ نسخهٔ ۳ ندارد"

    /**
     *  نوشتنِ تنظیمی که در نسخهٔ ۳ نیست ⇐ دلیل. اکنون خالی: همهٔ تنظیم‌های رابطِ امروز صفحهٔ نسخهٔ ۳ دارند. بخشِ
     *  تازه‌ای که فقط به رابطِ امروز برود همین‌جا قرمز می‌شود — یا صفحه‌اش را بسازید، یا با `PENDING` و دلیل این‌جا
     *  بگذارید (و هر کدام که آمد برداشته شود: «استثنای کهنه» قرمز است).
     */
    val EXCEPTIONS: Map<String, String> = emptyMap()
  }
}
