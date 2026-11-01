package ir.vil3ntec.tohid.ui.v3.settings

import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.settings.sections.CheckoutRulesText
import ir.vil3ntec.tohid.ui.v3.settings.sections.plainAfghani
import org.junit.Assert.assertEquals
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
    assertEquals(SettingsLayout.SECURITY, SettingsLayout.groupOf(SettingsLayout.ENCRYPTION))
    ported.filterNot { it == SettingsLayout.ENCRYPTION }.forEach { assertEquals(it, SettingsLayout.SALES, SettingsLayout.groupOf(it)) }
    //  «گزارش‌های مشتری» مثلِ رابطِ امروز فقط کامپیوتر
    assertEquals(setOf(SettingsLayout.CRM_REPORTS), SettingsLayout.PHONE_HIDDEN)
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
    private const val PENDING = "هنوز فقط در رابطِ امروز (`…SettingsWired` ِ `SettingsLegacy`)؛ صفحهٔ نسخهٔ ۳ ندارد"

    /**
     *  نوشتنِ تنظیمی که در نسخهٔ ۳ نیست ⇐ دلیل. فهرستِ باز: این‌ها هنوز صفحهٔ نسخهٔ ۳ ندارند و هر کدام که آمد
     *  از این‌جا برداشته شود («استثنای کهنه» قرمز است). بخشِ تازه‌ای که فقط به رابطِ امروز برود همین‌جا قرمز می‌شود.
     */
    val EXCEPTIONS: Map<String, String> = mapOf(
      "AutoFeesPref.set" to "$PENDING — هزینهٔ خودکارِ فاکتور، POS-36",
      "CountToolsPref.setBlind" to "$PENDING — انبارگردانیِ تازه، INV-06/07/08",
      "CountToolsPref.setOn" to "$PENDING — انبارگردانیِ تازه، INV-06/07/08",
      "DeliverLaterPrefs.set" to "$PENDING — فروش با تحویلِ بعدی، POS-38",
      "DocGapsPref.setOn" to "$PENDING — شماره‌های جاافتاده، RPT-17، فقط کامپیوتر",
      "GiftReceiptPrefs.set" to "$PENDING — رسیدِ هدیه، POS-34",
      "LabelToolsPref.set" to "$PENDING — برچسب و بارکدِ داخلی، PRD-23/PRD-05",
      "LinePolicyPrefs.setDirection" to "$PENDING — قیمت و تخفیفِ ردیف، POS-08/10/20/21",
      "LinePolicyPrefs.setFreeLineCapMinor" to "$PENDING — قیمت و تخفیفِ ردیف، POS-08/10/20/21",
      "LinePolicyPrefs.setOn" to "$PENDING — قیمت و تخفیفِ ردیف، POS-08/10/20/21",
      "PosMoreKeysPrefs.set" to "$PENDING — کلیدهای بیشترِ صندوق، فقط کامپیوتر",
      "PosNotesPrefs.set" to "$PENDING — یادداشتِ ردیف و فاکتور، POS-11",
      "PosNotesPrefs.setTemplates" to "$PENDING — یادداشتِ ردیف و فاکتور، POS-11",
      "PosPriceCheckPrefs.set" to "$PENDING — بررسیِ قیمت در صندوق، فقط کامپیوتر",
      "PosSearchPrefs.set" to "$PENDING — جست‌وجوی مقاوم، POS-04، فقط کامپیوتر",
      "PosTouchPrefs.setOn" to "$PENDING — حالتِ لمسیِ صندوق، POS-31، فقط کامپیوتر",
      "PriceWhyPrefs.set" to "$PENDING — «چرا این قیمت؟»، POS-22",
      "ProductInfoPrefs.set" to "$PENDING — نام‌های دیگر و محلِ قفسه، PRD-02/PRD-26",
      "ReportKeysPref.setOn" to "$PENDING — کلیدهای دوره در گزارش‌ها، RPT-13، فقط کامپیوتر",
      "ReturnPolicyPrefs.setBlocked" to "$PENDING — سیاستِ برگشت (روز و کالای بی‌برگشت)",
      "ReturnPolicyPrefs.setDays" to "$PENDING — سیاستِ برگشت (روز و کالای بی‌برگشت)",
      "SafeBookPref.set" to "$PENDING — گاوصندوق، PAY-18 (صفحه‌اش هم فقط در «تاریخچهٔ فروش» ِ رابطِ امروز است)",
      "ShrinkReportPref.set" to "$PENDING — گزارشِ ضایعات و کسری، INV-17",
      "SpecialOrderPref.set" to "$PENDING — سفارشِ ویژه، POS-26",
    )
  }
}
