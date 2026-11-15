package ir.vil3ntec.tohid.ui.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 *  نگهبانِ ظاهرِ ۶۳w (صاحب‌کار ۱۴۰۵/۰۸/۲۴) — قاعدهٔ `docs/RULES-fa.md` ← «بی سربرگِ رنگی جز داشبورد؛ هیچ ردیفی روی نوارِ پایین؛
 *  تاریخ و بازه با کادرِ کشویی»:
 *   • کادرِ بزرگِ گرادیانی (`V3HeroCard`) فقط در خانه (و نمونهٔ تمِ همان کادر)؛ هر بخشِ دیگر `V3Summary`؛
 *   • `V3Dock` و پارامترِ `dock` / `thumbDock` دیگر نیستند؛
 *   • خانه (داشبورد) و آیکن‌ها دست نخوردند: پرونده‌های داشبورد بایت‌به‌بایت همان (پایانِ خطِ ویندوز نادیده)، آیکنِ هر بخش و
 *     جدولِ آیکنِ نوار همان. تغییرِ آگاهانهٔ داشبورد فقط با خواستِ صاحب‌کار و به‌روز کردنِ همین فهرست.
 */
class LookGuardTest {
  private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
  private val main = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid")

  private fun sources(): Sequence<File> = main.walkTopDown().filter { it.isFile && it.extension == "kt" }

  private fun rel(f: File) = f.relativeTo(main).invariantSeparatorsPath

  private fun text(path: String) = File(main, path).readText().replace("\r\n", "\n")

  private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

  /** خانه و نمونهٔ تمِ کادرِ خانه — تنها جاهای مجازِ کادرِ گرادیانی */
  private val heroAllowed = setOf(
    "ui/v3/V3HeroCard.kt",
    "ui/v3/shell/HomeSalesCard.kt",
    "ui/v3/shell/HomeDrawerCard.kt",
    "ui/v3/gallery/ThemeGallery.kt",
  )

  @Test
  fun `هیچ بخشی جز خانه کادرِ گرادیانیِ بزرگ ندارد`() {
    val users = sources().filter { Regex("""\bV3HeroCard\(""").containsMatchIn(it.readText()) }.map(::rel).toSet()
    assertTrue("کادرِ گرادیانی بیرون از خانه: ${users - heroAllowed}", (users - heroAllowed).isEmpty())
    //  همان سطحِ آرام جایش: مصارف، تاریخچهٔ فروش، تأمین‌کننده‌ها و وضعیتِ ویترین
    listOf(
      "ui/v3/money/ExpensesScreen.kt", "ui/v3/sales/history/HistoryParts.kt", "ui/v3/money/SuppliersScreen.kt", "ui/v3/online/storefront/StatusPane.kt",
    ).forEach { assertTrue(it, text(it).contains("V3Summary(")) }
    //  گرادیانِ تم فقط کادرِ خانه و نمونه‌های انتخابِ تم
    val gradient = sources().filter { it.readText().contains("heroGradient") }.map(::rel).toSet()
    assertTrue("$gradient", gradient.all { it.startsWith("ui/theme/") || it in setOf("ui/v3/V3HeroCard.kt", "ui/v3/gallery/ThemeGallery.kt", "ui/v3/settings/sections/AppearanceScreen.kt") })
  }

  @Test
  fun `هیچ ردیفی روی نوارِ پایین نیست — نه V3Dock، نه dock، نه thumbDock`() {
    assertFalse(File(main, "ui/v3/V3Dock.kt").exists())
    val offenders = sources().filter { f -> f.readText().let { "V3Dock(" in it || "thumbDock" in it } }.map(::rel).toList()
    assertTrue("$offenders", offenders.isEmpty())
    val scaffold = text("ui/v3/V3Scaffold.kt")
    assertFalse("پارامترِ dock ِ V3Scaffold", Regex("""\n\s+dock: List<V3Action>""").containsMatchIn(scaffold))
    assertTrue("جست‌وجو دادهٔ کادر است، نه خودِ کادر", scaffold.contains("search: V3Search? = null"))
    assertFalse(text("ui/v3/V3Form.kt").contains("dock"))
  }

  @Test
  fun `دوره و ماه با کادرِ کشویی — نه ‹ ماه › و نه «روز، ماه، سال»`() {
    val expenses = text("ui/v3/money/ExpensesScreen.kt")
    assertFalse(expenses.contains("MonthSwitcher"))
    assertTrue(expenses.contains("V3Dropdown("))
    val history = text("ui/v3/sales/history/HistoryParts.kt") + text("ui/v3/sales/history/HistoryScreen.kt")
    assertFalse("کلیدِ «روز | ماه | سال»", history.contains("UnitBar") || history.contains("V3Segmented(UNITS"))
    assertTrue(text("ui/v3/sales/history/HistoryPeriodBar.kt").contains("V3Dropdown("))
    assertTrue(text("ui/v3/analytics/ReportsScreen.kt").contains("V3Dropdown("))
  }

  /**
   *  پرونده‌های داشبوردِ خانه (و خودِ کادرِ گرادیانی) — صاحب‌کار: «خانه را دست نزن». اثرِ تازه فقط با خواستهٔ خودِ صاحب‌کار:
   *  ۶۳g «ارز را یک کادرِ کوچک توی چارت بگذار» (HomeHero*، HeroMoney*: تراشهٔ ارزِ گاوصندوق؛ دکانِ فقط‌افغانی بیت‌به‌بیت همان کادر).
   */
  private val dashboard = mapOf(
    "ui/v3/shell/HomeAccents.kt" to "661f1bb3f069b39a",
    "ui/v3/shell/HomeDrawerCard.kt" to "b2abe9468bb6cfd6",
    "ui/v3/shell/HomeHero.kt" to "7d387b698015fed9",
    "ui/v3/shell/HomeHeroAndroid.kt" to "7848c0af51b14200",
    "ui/v3/shell/HomeHeroCard.kt" to "dfe6ef9b70b42ed0",
    "ui/v3/shell/HomeSalesCard.kt" to "4ba6b745c8b4805b",
    "ui/v3/shell/HomeScreen.kt" to "2053f8948f58e2d0",
    "ui/v3/shell/HomeShortcuts.kt" to "1c6a39676f781dc4",
    "ui/v3/shell/HomeTiles.kt" to "8b1a8dd019fea541",
    "ui/v3/shell/HomeToday.kt" to "56d3f8fc29e5085a",
    "ui/v3/shell/HomeTrend.kt" to "57d139695c46830d",
    //  ۶۳k (خواستهٔ صاحب‌کار «هشدارِ انقضا»): فقط ورودیِ کاشیِ هشدار — «منقضی‌شده» جدا از «نزدیکِ انقضا» با نامِ کالا؛ ظاهرِ خانه همان
    "ui/v3/shell/HomeView.kt" to "070fca810e6224ce",
    "ui/v3/shell/DeskHome.kt" to "4b21911118a57c2c",
    "ui/v3/shell/DashboardNumbers.kt" to "132f992d7d26c645",
    "ui/v3/shell/HeroMoney.kt" to "b543739c866c10d4",
    "ui/v3/shell/HeroMoneySheet.kt" to "74e98770e7221dce",
    "ui/v3/V3HeroCard.kt" to "95171b224dfd0333",
  )

  @Test
  fun `داشبوردِ خانه دست نخورد`() {
    dashboard.forEach { (path, hash) -> assertEquals("داشبورد: $path", hash, sha(text(path))) }
  }

  /**
   *  صاحب‌کار ۱۴۰۵/۰۸/۲۴: صفحهٔ ورودِ قدیمی (سه خط و دایرهٔ چرخان) — بی اجازهٔ صاحب‌کار دست نخورد.
   *  همان `WelcomeDecor.kt` ِ پیش از #1041 (موج‌ها، سه خطِ سفید، حباب‌ها، شبِ کم‌رنگ، قرص و حلقهٔ چرخان، زمان‌های ۳۰/۹/۱۴/۳ ثانیه)؛
   *  ساعتِ «فقط روی همین صفحه» جداست (`WelcomeClock.kt`) و این‌جا قفل نیست.
   */
  private val welcomeLook = mapOf(
    //  دستهٔ ۶۸: فقط `Color(0xFFFFFFFF)` ⇐ `Color.White` (همان رنگ، برای HardcodedColorsTest)؛ ظاهر همان
    "ui/v3/onboarding/WelcomeDecor.kt" to "19f2b85fce3e9a75",
  )

  @Test
  fun `صفحهٔ ورودِ قدیمی دست نخورد — سه خط، موج‌ها، حباب‌ها و دایرهٔ چرخان`() {
    welcomeLook.forEach { (path, hash) -> assertEquals("صفحهٔ ورود: $path", hash, sha(text(path))) }
    val decor = text("ui/v3/onboarding/WelcomeDecor.kt")
    assertTrue("سه خط", decor.contains("listOf(0f, 0.06f, 0.12f).forEachIndexed"))
    assertTrue("حلقهٔ چرخان", decor.contains("rotate(spin)"))
    //  یک نسخه، نه دو؛ و به کار رفته: پس‌زمینه پشتِ گام‌های ورود، نشان بالای قابِ دروازه، قفلِ رمز همان نشانِ پیشین
    assertFalse(File(main, "ui/screens/WelcomeDecor.kt").exists())
    assertEquals(listOf("ui/v3/onboarding/WelcomeDecor.kt"), sources().filter { Regex("""\bfun WelcomeBackground\(""").containsMatchIn(it.readText()) }.map(::rel).toList())
    assertTrue(text("ui/v3/onboarding/AuthFlow.kt").contains("WelcomeBackdrop {"))
    assertTrue(text("ui/v3/onboarding/GateFrame.kt").contains("WelcomeMark()"))
    assertTrue(text("ui/v3/onboarding/LockGate.kt").let { it.contains("GateBackdrop(welcome = false)") && it.contains("BrandMark()") })
  }

  /** آیکنِ هر بخش (همهٔ `ModuleEntry`ها) — «سابقهٔ عملیات» از ۶۳w نیست، بقیه همان */
  private val sectionIcons = mapOf(
    "account" to "Icons.Outlined.AccountCircle", "admin" to "Icons.Outlined.AdminPanelSettings", "chat" to "Icons.Outlined.Forum",
    "debtors" to "Icons.Outlined.PeopleOutline", "delivery" to "Icons.Outlined.DeliveryDining", "devices" to "Icons.Outlined.Devices",
    "example" to "Icons.Outlined.Extension", "expenses" to "Icons.Outlined.Payments", "history" to "Icons.Outlined.History",
    "home" to "Icons.Outlined.Home", "more" to "Icons.Outlined.Menu", "myplan" to "Icons.Outlined.WorkspacePremium",
    "orders" to "Icons.Outlined.ShoppingBasket", "products" to "Icons.Outlined.Sell", "promos" to "Icons.Outlined.LocalOffer",
    "purchasing" to "Icons.Outlined.LocalShipping", "receipts" to "Icons.AutoMirrored.Outlined.ReceiptLong", "reports" to "Icons.Outlined.InsertChart",
    "sale" to "Icons.Outlined.PointOfSale", "scan" to "Icons.Outlined.QrCodeScanner", "search" to "Icons.Outlined.Search",
    "settings" to "Icons.Outlined.Settings", "storefront" to "Icons.Outlined.Storefront", "support" to "Icons.Outlined.SupportAgent",
    "team" to "Icons.Outlined.Badge", "warehouse" to "Icons.Outlined.Inventory", "whats-new" to "Icons.Outlined.NewReleases",
  )

  @Test
  fun `آیکن‌ها دست نخوردند — آیکنِ هر بخش و جدولِ آیکنِ نوار`() {
    val entry = Regex("""ModuleEntry\("([a-z0-9/_-]+)",[^\n]*?(Icons\.[A-Za-z.]+)""")
    val found = sources().flatMap { f -> entry.findAll(f.readText()).map { it.groupValues[1] to it.groupValues[2] } }.toMap()
    assertEquals(sectionIcons, found)
    val nav = text("ui/components/common/PhoneNavBar.kt")
    val start = nav.indexOf("  fun icon(id: String, filled: Boolean)")
    assertTrue(start > 0)
    assertEquals("جدولِ آیکنِ نوارِ پایین", "d67f9c40a1236165", sha(nav.substring(start, nav.indexOf("\n  }\n", start))))
  }

  @Test
  fun `سابقهٔ عملیات در برنامه راهی ندارد ولی ثبتش همان است`() {
    val routes = text("ui/v3/analytics/AnalyticsRoutes.kt")
    assertFalse("بخشِ «همهٔ بخش‌ها»", routes.contains("ModuleEntry(\"audit\""))
    assertFalse("مسیر", routes.contains("V3Route(\"audit\"") || routes.contains("V3Route(AnalyticsPaths.DOC_CHAIN"))
    assertFalse("جست‌وجو", text("ui/v3/shell/SearchIndex.kt").contains("AnalyticsPaths.audit("))
    assertFalse("مرکزِ مدیریت", text("ui/v3/analytics/AdminScreen.kt").contains("AnalyticsPaths.audit("))
    assertFalse("منوی کامپیوتر", text("ui/screens/DeskMenu.kt").contains("Item(\"audit\""))
    assertFalse("«بیشتر» ِ قدیم", text("ui/screens/More.kt").contains("onOpen(\"audit\")"))
    //  ثبتِ رویدادها دست نخورد: دفتر هنوز `auditLog` می‌نویسد
    assertTrue(File(main, "data/Models.kt").readText().contains("auditLog"))
  }
}
