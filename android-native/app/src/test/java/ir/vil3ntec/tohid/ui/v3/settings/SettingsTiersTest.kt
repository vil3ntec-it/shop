package ir.vil3ntec.tohid.ui.v3.settings

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.ui.screens.deskLegacyTier
import ir.vil3ntec.tohid.ui.screens.SettingsHelp
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.SettingsTiers
import ir.vil3ntec.tohid.ui.screens.SettingsTiers.Tier
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  تنظیماتِ کم (تصمیمِ صاحب‌کار، ۱۴۰۵/۰۸/۱۱): A روشن و پنهان، B در «پیشرفته» و خاموش، C فهرستِ کوتاهِ اصلی؛
 *  هر ردیفِ دیده‌شده یک خطِ ساده و هر صفحهٔ دیده‌شده یک مثالِ «این چه می‌کند؟» در هر سه زبان دارد.
 *
 *  ⚠️ پیش‌فرضِ کلیدها در پرونده‌های `…Android.kt` (`SharedPreferences`) است؛ مثلِ `SimpleAskEngineTest` از متنِ
 *  همان پرونده‌های خامِ `android-native` سنجیده می‌شود.
 */
class SettingsTiersTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private fun src(rel: String): String = listOf(
    "src/main/java/ir/vil3ntec/tohid/$rel",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
    "android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
  ).map { File(it) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  /** همهٔ بخش‌ها و پیوندهای `SettingsLayout` (بی ماژول‌ها) */
  private val all: List<String> get() = SettingsLayout.GROUPS.flatMap { it.sections }

  @Test
  fun `هر بخش دقیقاً در یک دسته است و فهرستِ اصلی کوتاه است`() {
    assertTrue("بخشِ A یا C ِ ناشناس", (SettingsTiers.ALWAYS_ON + SettingsTiers.ESSENTIAL).all { it in all })
    assertTrue("A و C هم‌پوشانی دارند", SettingsTiers.ALWAYS_ON.intersect(SettingsTiers.ESSENTIAL).isEmpty())
    val main = all.filter { SettingsTiers.tier(it) == Tier.MAIN }
    val phone = main.filterNot { it in SettingsLayout.PHONE_HIDDEN }
    val desk = main.filterNot { it in SettingsLayout.DESK_HIDDEN }
    println("settings tiers: A=${SettingsTiers.ALWAYS_ON.size} B=${all.count { SettingsTiers.tier(it) == Tier.ADVANCED }} C=${main.size}; " +
      "phone main ${phone.size} (was ${all.filterNot { it in SettingsLayout.PHONE_HIDDEN || it in SettingsLayout.SERVER_GATED }.size}), " +
      "desk main ${desk.size} (was ${all.filterNot { it in SettingsLayout.DESK_HIDDEN || it in SettingsLayout.SERVER_GATED }.size})")
    assertTrue("فهرستِ اصلیِ گوشی بلند است: $phone", phone.size <= 12)
    assertTrue("فهرستِ اصلیِ کامپیوتر بلند است: $desk", desk.size <= 12)
    //  هر گروهِ گوشی دست‌کم یک ردیفِ اصلی دارد تا ردیفِ گروه در «بیشتر» به صفحهٔ خالی نرسد
    SettingsLayout.GROUPS.forEach { g -> assertTrue(g.title, SettingsTiers.rows(g, Tier.MAIN).any { it !in SettingsLayout.PHONE_HIDDEN }) }
    //  چیزهایی که هر دکان تنظیم می‌کند در فهرستِ اصلی‌اند
    listOf("زبان", "ظاهر", "قفل برنامه", "کد شاگرد", "داده‌ها", SettingsLayout.PERIPHERALS, SettingsLayout.PAY)
      .forEach { assertEquals(it, Tier.MAIN, SettingsTiers.tier(it)) }
    //  «همهٔ دستگاه‌ها» (۱۴۰۵/۰۸/۱۱) جای پیوندِ چاپگر را گرفت — همان صفحه، چاپگرها کارتِ سومش؛ فهرست کوتاه ماند
    assertEquals(Tier.ADVANCED, SettingsTiers.tier(SettingsLayout.PRINTER))
    //  تصمیم‌های پولی و آزمایشی هرگز روشن و پنهان نیستند
    listOf(
      SettingsLayout.CREDIT_GATE, "کالای منقضی", SettingsLayout.CHECKOUT_RULES, SettingsLayout.PAY_FIX, SettingsLayout.GIFT_CARD,
      SettingsLayout.LAYAWAY, SettingsLayout.SAFE_BOOK, SettingsLayout.LOYALTY, SettingsLayout.SPECIAL_ORDER, SettingsLayout.AUTO_FEES,
      SettingsLayout.PRICE_LIST, SettingsLayout.LINE_PRICING, SettingsLayout.SMART_FORECAST, SettingsLayout.ANOMALY, SettingsLayout.EASY_SETUP,
      SettingsLayout.TILL_RECEIVE, SettingsLayout.TAX_QUARTER, SettingsLayout.STOCK_MODE, SettingsLayout.STOCK_PLACES,
    ).forEach { assertEquals(it, Tier.ADVANCED, SettingsTiers.tier(it)) }
    all.filter { "(آزمایشی)" in it }.forEach { assertEquals(it, Tier.ADVANCED, SettingsTiers.tier(it)) }
  }

  @Test
  fun `هر کلیدِ A پیش‌فرض روشن است`() {
    assertEquals(SettingsTiers.ALWAYS_ON, A_PREFS.keys)
    A_PREFS.forEach { (key, prefs) ->
      prefs.forEach { (file, const) ->
        val text = src(file)
        assertTrue("$key: $file پیش‌فرضِ $const روشن نیست", text.contains("getBoolean($const, true)"))
        assertFalse("$key: $file هنوز پیش‌فرضِ خاموش دارد", text.contains("getBoolean($const, false)"))
      }
    }
    //  «انبارگردانیِ کور» سیاست است و خاموش می‌ماند؛ فقط خودِ ابزارها روشن‌اند
    assertTrue(src("data/CountToolsAndroid.kt").contains("getBoolean(BLIND, false)"))
    //  پاک کردنِ خودکارِ فاکتورِ نگه‌داشته (B) هم خاموش
    assertTrue(src("ui/components/pos/HeldInvoiceBar.kt").contains("getBoolean(AUTO_KEY, false)"))
  }

  @Test
  fun `هیچ کلیدِ B پیش‌فرض روشن نیست`() {
    B_PREFS.forEach { (key, prefs) ->
      assertEquals(key, Tier.ADVANCED, SettingsTiers.tier(key))
      prefs.forEach { (file, const) ->
        val text = src(file)
        assertTrue("$key: $file پیش‌فرضِ خاموشِ $const پیدا نشد", text.contains("getBoolean($const, false)"))
        assertFalse("$key: $file پیش‌فرضِ $const روشن شده", text.contains("getBoolean($const, true)"))
      }
    }
  }

  @Test
  fun `فهرستِ اصلی هیچ بخشِ A ندارد و ردیف‌ها فقط با دسته کشیده می‌شوند`() {
    SettingsLayout.GROUPS.forEach { g ->
      assertTrue(g.title, SettingsTiers.rows(g, Tier.MAIN).none { it in SettingsTiers.ALWAYS_ON })
      assertTrue(g.title, SettingsTiers.rows(g, Tier.ADVANCED).none { it in SettingsTiers.ALWAYS_ON })
    }
    //  گوشی، صفحهٔ گروه و ستونِ کامپیوتر هر سه از همان دسته می‌گذرند؛ HIDDEN هرگز کشیده نمی‌شود
    val rows = src("ui/v3/settings/SettingsRows.kt")
    assertTrue(rows.contains(".filter { SettingsTiers.tier(it) == tier }"))
    assertTrue(rows.contains("val more = if (tier == SettingsTiers.Tier.ADVANCED) extra else emptyList()"))
    val desk = src("ui/v3/settings/DeskSettingsScreen.kt")
    assertTrue(desk.contains("SettingsTiers.tier(it) == tier"))
    val index = src("ui/v3/settings/SettingsIndexScreen.kt")
    assertEquals(2, Regex("""tier = SettingsTiers\.Tier\.ADVANCED""").findAll(index).count())
    listOf(rows, desk, index).forEach { assertFalse(it.contains("Tier.HIDDEN")) }
    //  رابطِ امروز: کارتِ گوشی و ستونِ کامپیوتر
    val legacy = src("ui/legacy/SettingsPartsLegacy.kt")
    assertTrue(legacy.contains("if (SettingsTiers.hiddenTitle(title)) return"))
  }

  @Test
  fun `هر ردیفِ دیده‌شده در هر سه زبان یک خطِ ساده و هر صفحه یک مثال دارد`() {
    val visible = all.filter { SettingsTiers.tier(it) != Tier.HIDDEN }
    val extras = V3Modules.ALL.filter { it.id != "example" }.flatMap { it.settings }
    assertTrue("ردیفِ ماژولِ تنظیمات پیدا نشد", extras.isNotEmpty())
    for (lang in Lang.entries) {
      I18n.source = { lang }
      visible.forEach { key ->
        val line = SettingsHelp.line(key)
        assertTrue("$key ($lang) خطِ ساده ندارد", !line.isNullOrBlank())
        assertTrue("$key ($lang) خطِ بلند: $line", line!!.length <= 90)
        if (!SettingsLayout.isLink(key)) {
          val ex = SettingsHelp.example(key)
          assertTrue("$key ($lang) مثال ندارد", !ex.isNullOrBlank())
        }
      }
      extras.forEach { e -> assertTrue("${e.route} ($lang) خطِ ساده ندارد", !e.subtitle().isNullOrBlank()) }
      assertTrue(!SettingsHelp.example(SettingsHelp.LATE_APOLOGY).isNullOrBlank())
    }
    //  همان کلیدِ مسیرِ «پوزش و کوپنِ تأخیر»
    assertEquals(SettingsPaths.LATE_APOLOGY, SettingsHelp.LATE_APOLOGY)
    //  کارت از کلیدِ صفحه کشیده می‌شود: مسیرِ بخش و ستونِ کامپیوتر کلید را می‌دهند
    assertTrue(src("ui/v3/settings/SettingsRoutes.kt").contains("CompositionLocalProvider(LocalSettingsKey provides key)"))
    assertTrue(src("ui/v3/settings/DeskSettingsScreen.kt").contains("CompositionLocalProvider(LocalSettingsKey provides key)"))
    assertTrue(src("ui/v3/settings/SettingsPageFrame.kt").contains("SettingsWhatCard(whatKey)"))
  }

  @Test
  fun `هر بخشِ A هنوز صفحه، نوشتن و نشانیِ خودش را دارد`() {
    val table = V3Modules.table
    SettingsTiers.ALWAYS_ON.forEach { key ->
      assertNotNull("$key صفحه ندارد", SettingsPages.of(key))
      assertTrue("$key نشانی ندارد", SettingsLayout.SECTION_SLUGS.containsKey(key))
      val path = SettingsPaths.section(key)
      assertNotNull("$key ⇐ $path", table.resolve(path))
      assertEquals(key, SettingsPaths.sectionOfSlug(SettingsLayout.groupOf(key)!!, path.substringAfterLast('/')))
      //  ستونِ کامپیوتر: نشانیِ مستقیم همان صفحه را انتخاب می‌کند (`selected in all`)
      if (key !in SettingsLayout.PHONE_HIDDEN) assertTrue(key in SettingsLayout.sections)
    }
  }

  @Test
  fun `رابطِ امروز — عنوانِ A پنهان، B پیشرفته، عنوانِ ناشناس در فهرستِ اصلی`() {
    for (lang in Lang.entries) {
      I18n.source = { lang }
      SettingsTiers.ALWAYS_ON.forEach { key ->
        assertTrue("$key ($lang)", SettingsTiers.hiddenTitle(SettingsLayout.labelOf(key)))
        assertEquals(key, Tier.HIDDEN, deskLegacyTier(SettingsLayout.labelOf(key)))
      }
      assertEquals(Tier.MAIN, deskLegacyTier(Str.appLock))
      assertEquals(Tier.MAIN, deskLegacyTier(Str.appearance))
      assertEquals(Tier.ADVANCED, deskLegacyTier(Str.alerts))
      assertEquals(Tier.ADVANCED, deskLegacyTier(Str.signedInDevices))
      assertEquals(Tier.ADVANCED, deskLegacyTier(ir.vil3ntec.tohid.ui.UiV3.TOGGLE_TITLE))
      assertEquals(Tier.ADVANCED, deskLegacyTier(ir.vil3ntec.tohid.core.i18n.StrSale.checkoutRulesTitle))
      assertEquals(Tier.MAIN, deskLegacyTier("بخشی که نیست"))
      assertFalse(SettingsTiers.hiddenTitle("بخشی که نیست"))
    }
  }

  companion object {
    /** A ⇐ پرونده و نامِ کلیدِ `getBoolean` (پیش‌فرض باید `true` باشد) */
    val A_PREFS: Map<String, List<Pair<String, String>>> = mapOf(
      SettingsLayout.FRAUD_TRAIL to listOf("data/FraudTrailAndroid.kt" to "KEY"),
      SettingsLayout.POS_SEARCH to listOf("ui/components/pos/PosSearchPrefsAndroid.kt" to "KEY"),
      SettingsLayout.POS_PRICE_CHECK to listOf("ui/components/pos/PosPriceCheckPrefsAndroid.kt" to "KEY"),
      SettingsLayout.KEY_HELP to listOf("ui/components/keys/KeyHelpAndroid.kt" to "KEY"),
      SettingsLayout.CUSTOMER_CARD to listOf("data/CustomerCardPrefAndroid.kt" to "KEY"),
      SettingsLayout.SIMPLE_ASK to listOf("data/SimpleAskPrefAndroid.kt" to "KEY"),
      SettingsLayout.REPORT_KEYS to listOf("data/ReportKeysPrefAndroid.kt" to "KEY"),
      SettingsLayout.DOC_GAPS to listOf("data/DocGapsPrefAndroid.kt" to "KEY"),
      SettingsLayout.PRICE_WHY to listOf("ui/components/pos/PriceWhyAndroid.kt" to "KEY"),
      SettingsLayout.POS_NOTES to listOf("ui/components/pos/PosNotesAndroid.kt" to "KEY"),
      SettingsLayout.GIFT_RECEIPT to listOf("ui/components/pos/GiftReceiptAndroid.kt" to "KEY"),
      SettingsLayout.STOCK_INSIGHT to listOf("data/ShopPolicy.kt" to "STOCK_INSIGHT"),
      SettingsLayout.STOCK_LEVELS to listOf("data/ShopPolicy.kt" to "STOCK_LEVELS"),
      SettingsLayout.COUNT_TOOLS to listOf("data/CountToolsAndroid.kt" to "TOOLS"),
      SettingsLayout.SHRINK_REPORT to listOf("data/ShrinkReportAndroid.kt" to "KEY"),
      SettingsLayout.CHAT_GUARD to listOf("data/ShopPolicy.kt" to "CHAT_MEDIA_GUARD"),
    )

    /** B ⇐ کلیدهای روشن/خاموشِ هر بخش (پیش‌فرض باید `false` بماند) */
    val B_PREFS: Map<String, List<Pair<String, String>>> = mapOf(
      SettingsLayout.TRAINING to listOf("data/TrainingLedgerAndroid.kt" to "ON", "data/TrainingLedgerAndroid.kt" to "STAFF"),
      SettingsLayout.ENCRYPTION to listOf("data/AtRest.kt" to "PREF_KEY"),
      SettingsLayout.ANOMALY to listOf("data/SmartInsightsPrefAndroid.kt" to "KEY"),
      SettingsLayout.SMART_FORECAST to listOf("data/SmartInsightsPrefAndroid.kt" to "KEY"),
      "کالای منقضی" to listOf("data/ShopPolicy.kt" to "BLOCK_EXPIRED", "data/ExpiryPrefAndroid.kt" to "APPROVAL"),
      SettingsLayout.CHECKOUT_RULES to listOf("data/CheckoutRulesAndroid.kt" to "BLOCK_ZERO"),
      SettingsLayout.HELD to listOf("ui/components/pos/HeldInvoiceBar.kt" to "KEY", "ui/components/pos/HeldInvoiceBar.kt" to "AUTO_KEY"),
      SettingsLayout.RETURNS to listOf("data/ReturnSettings.kt" to "ENABLED"),
      SettingsLayout.PAY_FIX to listOf("data/PayFixAndroid.kt" to "KEY"),
      SettingsLayout.TILL_RECEIVE to listOf("data/TillReceiveAndroid.kt" to "KEY"),
      SettingsLayout.POS_TOUCH to listOf("ui/components/pos/PosTouchPrefsAndroid.kt" to "ON"),
      SettingsLayout.POS_MORE_KEYS to listOf("ui/components/pos/PosMoreKeysPrefsAndroid.kt" to "KEY"),
      SettingsLayout.TILL_SWITCH to listOf("data/TillUserAndroid.kt" to "KEY"),
      SettingsLayout.GIFT_CARD to listOf("data/GiftCardAndroid.kt" to "KEY"),
      SettingsLayout.LAYAWAY to listOf("data/LayawayAndroid.kt" to "KEY"),
      SettingsLayout.SAFE_BOOK to listOf("data/SafeBookAndroid.kt" to "KEY"),
      SettingsLayout.CREDIT_GATE to listOf("data/ShopPolicy.kt" to "CREDIT_GATE"),
      SettingsLayout.LOYALTY to listOf("data/LoyaltyAndroid.kt" to "KEY"),
      SettingsLayout.CRM_REPORTS to listOf("data/ShopPolicy.kt" to "CRM_REPORTS"),
      SettingsLayout.TAX_QUARTER to listOf("data/TaxQuarterPrefAndroid.kt" to "KEY"),
      SettingsLayout.LINE_PRICING to listOf("data/LinePolicyAndroid.kt" to "ON"),
      SettingsLayout.TILL_GOODS to listOf("data/TillGoodsPrefAndroid.kt" to "key"),
      SettingsLayout.PRICE_LIST to listOf("ui/components/pos/PriceListAtPosAndroid.kt" to "KEY"),
      SettingsLayout.QUOTE to listOf("data/QuoteStoreAndroid.kt" to "KEY"),
      SettingsLayout.SPECIAL_ORDER to listOf("data/SpecialOrderAndroid.kt" to "KEY"),
      SettingsLayout.DELIVER_LATER to listOf("ui/components/pos/DeliverLaterAndroid.kt" to "KEY"),
      SettingsLayout.STOCK_PLACES to listOf("data/StockPlacesAndroid.kt" to "PLACES", "data/StockPlacesAndroid.kt" to "REPACK"),
      SettingsLayout.PRODUCT_TOOLS to listOf("data/ShopPolicy.kt" to "PRODUCT_TOOLS"),
      SettingsLayout.PRODUCT_INFO to listOf("ui/components/product/ProductInfoAndroid.kt" to "KEY"),
      SettingsLayout.LABEL_TOOLS to listOf("data/LabelToolsAndroid.kt" to "KEY"),
      SettingsLayout.EASY_SETUP to listOf("print/EasySetupAndroid.kt" to "KEY"),
    )
  }
}
