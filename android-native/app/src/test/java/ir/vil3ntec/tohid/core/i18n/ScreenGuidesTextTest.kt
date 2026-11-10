package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.guide.ScreenGuides
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.core.text.DariText
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  OPS-06 — متنِ راهنمای ۱۶ صفحه (`core/guide/ScreenGuides`) با خودِ برنامه می‌خواند:
 *
 *  - عنوانِ هر راهنما همان سرخطِ صفحه (`V3Route`) است، در فارسی، انگلیسی و پشتو (پشتوی عنوان رونوشتِ جدول است، نه ساختگی)؛
 *  - هر نامِ دکمه یا کادر که گامی میانِ «» (انگلیسی میانِ “”) می‌آورد، همان نوشتهٔ امروزِ جدولِ `Str*` است — فهرستِ
 *    همان کلیدها پایین است، پس کلیدِ پاک‌شده کامپایل نمی‌شود و نوشتهٔ عوض‌شده سرخ می‌کند؛ کلیدِ فهرست هم باید در راهنما بیاید؛
 *  - در دری، همان نام همان‌طور در راهنما دیده می‌شود که روی دکمه (لایهٔ `DariText` روی کلِ گام و روی برچسب یکی است)؛
 *  - شناسهٔ هر راهنما از مسیرِ واقعیِ نسخهٔ ۳ می‌آید (`ScreenGuides.screenOf` ِ `AppRoot`).
 *
 *  آینهٔ محلی ندارد؛ CI ِ اندروید و کامپیوتر می‌دواند.
 */
class ScreenGuidesTextTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  /** سرخطِ هر صفحه — همان عنوانِ `V3Route` ِ مسیرش */
  private val titles: Map<String, () -> String> = mapOf(
    "dashboard" to { Str.home },
    "sale" to { Str.sale },
    "debtors" to { Str.debtors },
    "receipts" to { Str.receipts },
    "products" to { StrProducts.listTitle },
    "warehouse" to { Str.warehouse },
    "purchasing" to { Str.purchasing },
    "sales" to { Str.salesHistory },
    "expenses" to { Str.expenses },
    "reports" to { Str.reports },
    "orders" to { StrOnline.ordersTitle },
    "storefront" to { StrOnline.sfTitle },
    "promos" to { StrKit.promos },
    "team" to { Str.staff },
    "devices" to { Str.devices },
    "settings" to { Str.settings },
  )

  /** هر نامی که راهنمای همان صفحه میانِ «» می‌آورد */
  private val labels: Map<String, List<() -> String>> = mapOf(
    "dashboard" to listOf({ StrHome.todaySales }, { Str.reports }, { StrHome.drawer }, { StrOrdersHub.tile }, { StrHome.receive },
      { StrHome.stockEntry }, { StrHome.history }, { StrHome.urgentNow }, { Str.all }, { StrShell.newSale }),
    "sale" to listOf({ Str.walkIn }, { Str.cashSale }, { Str.credit }, { Str.discount }, { StrPos.recordSaleBtn }, { StrPos.printLastInvoice },
      { StrSale.freeItemAlt }, { StrPos.emptyCart }),
    "debtors" to listOf({ StrCustomerPages.newCustomerName }, { StrCustomerPages.phone }, { StrCustomerPages.tabStatement }, { Str.invoices },
      { StrCustomerPages.tabItems }, { StrCustomerPages.receivePayment }, { StrCustomerPages.amountAfn }, { StrCustomerPages.manualCredit },
      { StrMoney.dueField }),
    "receipts" to listOf({ StrMoney.tabDebtorReceipts }, { StrMoney.tabExpenses }, { StrMoney.allYears }, { StrMoney.allMonths },
      { StrMoney.allDebtors }, { StrMoney.total }, { Str.debtors }, { StrHome.receive }),
    "products" to listOf({ StrProductPages.addItem }, { StrHome.newItem }, { StrProductPages.editItem }, { StrSettings.exportTitle },
      { StrProducts.activate }, { StrProducts.deactivate }, { StrMoney.delete }),
    "warehouse" to listOf({ StrStock.entryAction }, { StrStock.productField }, { StrStock.unitCount }, { StrStock.cartonCount },
      { StrStock.priceField }, { StrStock.expiry }, { StrStock.submitEntry }, { StrStock.filterLow }, { StrStock.filterOut },
      { StrStock.filterExpiring }, { StrStock.movementsRow }, { StrStock.adjustAction }, { StrStock.menuCount }, { StrStock.menuBulk }),
    "purchasing" to listOf({ StrPurchasePages.purchaseInvoices }, { StrPurchasePages.orNewSupplier }, { StrPurchasePages.itemNameBarcode },
      { StrPlaces.amount }, { StrReturnPages.unitPriceAfn }, { StrPurchasePages.paidAfn }, { StrPurchasePages.suppliers }),
    "sales" to listOf({ StrHistory.day }, { StrHistory.month }, { StrHistory.year }, { StrSale.print }, { StrSale.returnWord },
      { StrSale.fixInvoice }, { StrSale.cancelSale }, { StrHistory.table }),
    "expenses" to listOf({ StrMoney.newExpense }, { StrMoney.expenseAmount }, { StrMoney.category }, { StrMoney.newCategory },
      { StrMoney.forWhatOptional }, { StrMoney.date }, { StrKit.save }, { StrMoney.editExpense }, { StrMoney.delete }),
    "reports" to listOf({ StrReports.reportType }, { StrReports.deskSales }, { StrReports.deskProfit }, { StrReports.deskExpenses },
      { StrReports.tabDebtors }, { StrReports.rangeToday }, { StrReports.rangeWeek }, { StrReports.rangeMonth }, { StrReports.rangeJMonth },
      { StrReports.rangeJYear }, { StrReports.rangeCustom }, { StrReports.printShort }, { StrReports.excel }, { StrReports.deskProducts },
      { StrReports.tabExpiry }, { StrReports.rightNow }),
    "orders" to listOf({ StrOrdersHub.tab("new") }, { StrOnline.orderAction("accepted") }, { StrOnline.orderAction("rejected") },
      { StrOnline.orderReason("out_of_stock") }, { StrOnline.orderAction("picking") }, { StrOnline.orderAction("packed") },
      { StrOnline.orderAction("ready_for_pickup") }, { StrOnline.orderAction("out_for_delivery") }, { StrOnline.orderAction("picked_up") },
      { StrOnline.orderAction("delivered") }, { StrOrdersHub.tab("done") }, { StrOnline.manualOrder }),
    "storefront" to listOf({ StrOnline.sfTabSettings }, { StrOnline.sfSlug }, { StrOnline.sfHours }, { StrOnline.sfEnabled },
      { StrOnline.sfPickupToggle }, { StrOnline.sfDeliveryToggle }, { StrOnline.deliveryFee }, { StrOnline.sfSave }, { StrOnline.sfTabProducts },
      { StrOnline.sfOnlinePrice }, { StrOnline.sfTabStatus }, { StrOnline.ordersTitle }),
    "promos" to listOf({ StrPromoPages.autoAtTill }, { StrPromoPages.newRuleBtn }, { StrPromoPages.nameOnCart }, { StrInvoices.colKind },
      { StrSale.percentWord }, { StrMarketPages.kindAmount }, { StrMarketPages.kindBxgy }, { StrPromoPages.onWhichItems },
      { StrPromoPages.specificItems }, { StrPromoPages.whenSection }, { StrPromoPages.codeOptional }, { StrKit.save }, { StrProducts.deactivate }),
    "team" to listOf({ StrAccount.shopCode }, { StrAccount.copyCode }, { StrAuth.areYouStaff }, { StrAccount.newJoinCode }, { StrAccount.role },
      { StrAccount.create }, { StrAccount.permsTitle }, { StrAccount.remove }, { StrAccount.logoutEverywhere }, { StrAccount.newShopCode }),
    "devices" to listOf({ StrSettings.scanners }, { StrSettings.printers }, { StrSettings.addPrinter }, { StrSettings.paperWidth },
      { StrSettings.savePrinter }, { StrSettings.test }, { StrSettings.phoneLink }, { StrSettings.makePairCode }, { StrDevices.hubTitle }),
    "settings" to listOf({ StrSettingsHome.searchHint }, { StrSettings.groupSales }, { StrSettingsHome.goods }, { StrSettingsHome.look },
      { StrSettingsHelp.whatTitle }, { StrSettingsHome.features }),
  )

  /** ریشهٔ مسیرِ نسخهٔ ۳ ِ هر صفحهٔ راهنمادار */
  private val routes = listOf(
    "home", "sale", "debtors", "receipts", "products", "stock", "purchasing", "sales",
    "expenses", "reports", "orders", "storefront", "promos", "team", "devices", "settings",
  )

  private fun inLang(lang: Lang, f: () -> String): String {
    I18n.source = { lang }
    return f()
  }

  private fun quoted(text: String, re: Regex): List<String> = re.findAll(text).map { it.groupValues[1] }.toList()

  private val fa = Regex("«([^»]*)»")
  private val en = Regex("“([^”]*)”")

  @Test fun everyGuideTitleIsTheScreenTitleInEveryLanguage() {
    assertEquals(ScreenGuides.ALL.map { it.screen }.toSet(), titles.keys)
    assertEquals(titles.keys, labels.keys)
    for (g in ScreenGuides.ALL) {
      val header = titles.getValue(g.screen)
      assertEquals(g.screen, inLang(Lang.FA, header), g.title.fa)
      assertEquals(g.screen, inLang(Lang.EN, header), g.title.en)
      assertEquals(g.screen, inLang(Lang.PS, header), g.title.ps)
    }
  }

  @Test fun everyQuotedNameIsTheTableTextAndEveryListedKeyIsUsed() {
    for (g in ScreenGuides.ALL) {
      val names = labels.getValue(g.screen)
      val faNames = names.map { inLang(Lang.FA, it) }.toSet()
      val enNames = names.map { inLang(Lang.EN, it) }.toSet()
      val usedFa = HashSet<String>()
      val usedEn = HashSet<String>()
      g.steps.forEachIndexed { i, s ->
        val at = "${g.screen} #${i + 1}"
        assertTrue("$at: انگلیسی خالی است", s.en.isNotBlank())
        //  ؋ نمادِ پول است، نه متنِ فارسی
        assertFalse("$at: متنِ فارسی در انگلیسی: ${s.en}", s.en.any { it in '\u0600'..'\u06FF' && it != '؋' })
        for (q in quoted(s.fa, fa)) { usedFa += q; assertTrue("$at: «$q» نوشتهٔ هیچ کلیدِ فهرستِ این صفحه نیست", q in faNames) }
        for (q in quoted(s.en, en)) { usedEn += q; assertTrue("$at: “$q” نوشتهٔ هیچ کلیدِ فهرستِ این صفحه نیست", q in enNames) }
      }
      assertEquals("${g.screen}: کلیدِ فهرست که در راهنما نیامده", faNames, usedFa)
      assertEquals("${g.screen}: کلیدِ فهرست که در راهنمای انگلیسی نیامده", enNames, usedEn)
    }
  }

  @Test fun dariGuideShowsTheSameNameAsTheDariButton() {
    for (g in ScreenGuides.ALL) {
      val byFa = labels.getValue(g.screen).associateBy { inLang(Lang.FA, it) }
      g.steps.forEachIndexed { i, s ->
        val dari = s.of("prs")
        assertEquals(DariText.of(s.fa), dari)
        for (q in quoted(s.fa, fa)) {
          val button = inLang(Lang.PRS, byFa.getValue(q))
          assertTrue("${g.screen} #${i + 1}: «$button» در «$dari» نیست", dari.contains("«$button»"))
        }
      }
    }
  }

  @Test fun everyGuideBelongsToARealV3Route() {
    for (r in routes) assertNotNull("مسیرِ ثبت‌نشده: $r", V3Modules.table.resolve(r))
    assertEquals(ScreenGuides.ALL.map { it.screen }.toSet(), routes.map { ScreenGuides.screenOf(RoutePaths.base(it)) }.toSet())
    assertEquals(ScreenGuides.ALL.size, routes.size)
  }
}
