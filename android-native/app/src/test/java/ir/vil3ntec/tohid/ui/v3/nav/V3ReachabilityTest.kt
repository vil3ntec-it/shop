package ir.vil3ntec.tohid.ui.v3.nav

import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.ui.screens.SettingsHelp
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
    //  ── Google Play: «حذفِ حساب» در خودِ برنامه (حساب من ⇐ ویرایش ⇐ حذفِ حساب) ──
    Feature("Google Play حذفِ حساب در برنامه", "profile/delete", "ir.vil3ntec.tohid.ui.v3.account.profile.DeleteAccountRoute"),
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
    //  ── کمبودهای MISSING، دورِ ۳ (صفحه برای درهای سرورِ #882 و دو ردیفِ بی مانع) ──
    Feature("IMG-PRO-17 تغییرِ دسته‌ایِ عکس‌ها و برگشتِ یک‌جا", "stock/images/changes", "ir.vil3ntec.tohid.ui.v3.stock.ImageChangesRoute"),
    Feature("IMG-PRO-17 دستهٔ تازه", "stock/images/changes/new", "ir.vil3ntec.tohid.ui.v3.stock.ImageBatchRoute"),
    Feature("IMG-PRO-17 پیش و پسِ یک دسته و برگشت", "stock/images/changes/{id}", "ir.vil3ntec.tohid.ui.v3.stock.ImageChangeRoute"),
    //  ── INV-11: انتقالِ موجودیِ سرور (پشتِ پرچمِ `shop.catalog`) ──
    Feature("INV-11 انتقالِ موجودی با «در مسیر»", "stock/transfers", "ir.vil3ntec.tohid.ui.v3.stock.TransferListRoute"),
    Feature("INV-11 پیش‌نویس و ارسالِ انتقال", "stock/transfers/new", "ir.vil3ntec.tohid.ui.v3.stock.TransferDraftRoute"),
    Feature("INV-11 دریافت با اسکن، ناقص و رد", "stock/transfers/{key}", "ir.vil3ntec.tohid.ui.v3.stock.TransferReceiveRoute"),
    Feature("IMG-PRO-16 نسخه‌های کوچک و بزرگِ عکس و سنجهٔ فشرده‌سازی", "products/{id}/photo/versions", "ir.vil3ntec.tohid.ui.v3.products.PhotoVersionsPage"),
    //  ── PRD-17/27 و PRM-02 (`claude/app-gaps-4`): صفحه برای درهای موجودِ سرور ──
    Feature("PRD-17/27 عکس‌های تکمیلی و عکسِ جدای ویترین", "products/{id}/photo/gallery", "ir.vil3ntec.tohid.ui.v3.products.ProductGalleryPage"),
    Feature("PRM-02 آزادِ دستیِ کدِ تخفیفِ نگه‌داشته", "settings/promo-held", "ir.vil3ntec.tohid.ui.v3.settings.sections.PromoHeldScreen"),
    Feature("RPT-18 گزارشِ همهٔ شعبه‌ها", "reports/branches", "ir.vil3ntec.tohid.ui.v3.analytics.BranchesReportPage"),
    Feature(
      "PAY-17 چند کشو و شیفتِ شناور (ردهٔ B، پیش‌فرض خاموش)", "settings/sales/multi-drawer",
      "ir.vil3ntec.tohid.ui.v3.settings.sections.MultiDrawerScreen", pattern = "settings/{group}/{section}",
    ),
    Feature("PAY-17 کشوهای باز و «ادامه روی این دستگاه»", "sales/drawer", "ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerScreen"),
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
    //  PUR-14 (دورِ ۱۳): فاکتورِ تأمین‌کننده و «باطل» پیش‌تر فقط در پنجرهٔ «سفارش‌های خرید» ِ کامپیوتر — ریشه همان صفحهٔ گوشی
    //  تا رسیدنِ پنجره از کامپیوتر جای آن را نگیرد
    Feature("PUR-14 فهرستِ فاکتورهای تأمین‌کننده (گوشی)", "purchasing/bills", "$MONEY.SupplierBillsRoute", root = "$MONEY.SupplierBillsRoute"),
    Feature(
      "PUR-14 فاکتورهای یک سفارش، «باطل» و تطبیق (گوشی)", "purchasing/bills/{id}",
      s("PoBillsList"), s("PoBillVoidDialog"), s("PoMatchLines"), root = "$MONEY.SupplierBillRoute",
    ),
    Feature("PUR-14 فرمِ فاکتورِ تأمین‌کننده (گوشی)", "purchasing/bills/{id}/new", s("PoBill"), root = "$MONEY.SupplierBillNewRoute"),
    Feature("PUR-14 درِ «فاکتورهای تأمین‌کننده» در زبانهٔ خریدِ گوشی", "purchasing", "$MONEY.rememberSupplierBillsDoor", "$MONEY.SuppliersScreen"),
    //  PUR-02/03/04/14 (دستهٔ ۳۰): سفارشِ خرید پیش‌تر فقط پنجرهٔ «سفارش‌های خرید» ِ کامپیوتر — ریشه همان صفحهٔ گوشی
    Feature("PUR-02 درِ «سفارش‌های خرید» در زبانهٔ خریدِ گوشی", "purchasing", "$MONEY.rememberPurchaseOrdersDoor", "$MONEY.SuppliersScreen"),
    Feature(
      "PUR-02 فهرستِ سفارش‌های خرید با پالایهٔ وضعیت (گوشی)", "purchasing/orders",
      "$MONEY.PurchaseOrdersRoute", "ir.vil3ntec.tohid.data.PoForms", root = "$MONEY.PurchaseOrdersRoute",
    ),
    Feature("PUR-02/03 ساختنِ سفارش با پیشنهادها و «همهٔ کالاهای این تأمین‌کننده» (گوشی)", "purchasing/orders/new", "$MONEY.PoEditorForm", root = "$MONEY.PurchaseOrderNewRoute"),
    Feature("PUR-03 پیشنهادِ سفارش و پیش‌نویس با یک کلید (گوشی)", "purchasing/orders/suggest", s("PoSuggest"), root = "$MONEY.PurchaseOrderSuggestRoute"),
    Feature("PUR-02 جزئیات، فرستادن و لغو با دلیل (گوشی)", "purchasing/orders/{id}", "$MONEY.PoCancelSheet", "$MONEY.postReceipt", root = "$MONEY.PurchaseOrderRoute"),
    Feature("PUR-02 ویرایشِ پیش‌نویس (گوشی)", "purchasing/orders/{id}/edit", "$MONEY.PoEditorForm", root = "$MONEY.PurchaseOrderEditRoute"),
    Feature("PUR-04 دریافتِ کامل/جزئی با اسکن، خراب و باقی‌ماندهٔ باز (گوشی)", "purchasing/orders/{id}/receive", "$MONEY.PoReceiveForm", root = "$MONEY.PurchaseOrderReceiveRoute"),
    //  PUR-04 (دستهٔ ۳۴): دریافتِ کامل با یک لمس — «رسیده» ِ هر ردیفِ باز = باقی‌مانده (`PoForms.fillRemaining`)
    Feature("PUR-04 دریافتِ همهٔ باقی‌مانده با یک لمس (گوشی)", "purchasing/orders/{id}/receive", "$MONEY.PoReceiveAll", root = "$MONEY.PurchaseOrderReceiveRoute"),
    //  PUR-04 (`claude/pur-2`): همان دکمه در پنجرهٔ دریافتِ کامپیوتر (پیش‌تر فقط گوشی؛ ریشه همان پنجرهٔ دریافت)
    Feature("PUR-04 دریافتِ همهٔ باقی‌مانده با یک کلیک (کامپیوتر)", "purchasing", s("DeskReceiveAll"), root = s("PoReceive")),
    Feature("PUR-14 تطبیقِ سفارش، رسید و فاکتور (گوشی)", "purchasing/orders/{id}/match", "$MONEY.PurchaseOrderMatchRoute", root = "$MONEY.PurchaseOrderMatchRoute"),
    Feature("PUR-13 برچسبِ اقلامِ رسیده پس از دریافت (گوشی)", "purchasing/orders/{id}/receive", "$MONEY.PoReceiptLabels", "ir.vil3ntec.tohid.ui.platform.printReceiptLabels", root = "$MONEY.PurchaseOrderReceiveRoute"),
    Feature("PUR-13 برچسبِ هر رسید در جزئیاتِ سفارش (گوشی)", "purchasing/orders/{id}", "$MONEY.PoReceiptLabels", root = "$MONEY.PurchaseOrderRoute"),
    Feature("POS-39 دریافتِ بدهی در صندوقِ گوشی", "sale", s("TillReceivePhone"), root = PHONE_SALE),
    Feature(
      "POS-25/26/27/18، PAY-24، POS-38 ابزارهای سبدِ صندوقِ گوشی", "sale",
      pos("QuoteBar"), pos("SpecialOrderBar"), pos("GiftCardBar"), pos("LayawayBar"), pos("PriceListAtPos"), pos("DeliverLaterSheetWired"),
      root = PHONE_SALE,
    ),
    Feature(
      "PRM-13 مصرف و کسبِ امتیاز سرِ صندوقِ گوشی (ثبت با `TillSale`)", "sale/checkout",
      pos("rememberLoyaltyTill"), pos("LoyaltyBar"), "ir.vil3ntec.tohid.data.TillSale", root = PHONE_CHECKOUT,
    ),
    Feature("POS-11 یادداشتِ فاکتورِ صندوقِ گوشی", "sale", pos("InvoiceNoteButton"), root = PHONE_SALE),
    //  «تاریخچه» ِ روز/ماه/سال و چاپِ هر دوره از صفِ چاپ (خواستِ صاحب‌کار) — گوشی، تبلت و کامپیوتر از یک مسیر
    Feature(
      "تاریخچهٔ روز/ماه/سال، فیلتر و چاپِ خلاصه یا همهٔ فاکتورهای هر دوره", "sales",
      "$HISTORY.HistoryScreen", "$HISTORY.PeriodCard", "$HISTORY.MoneySplit", "$HISTORY.FilterSheet",
      "$HISTORY.HistoryPrintSheet", "$HISTORY.PrintBannerCard", "ir.vil3ntec.tohid.print.PeriodPrint",
    ),
    Feature("جدولِ اکسلیِ فاکتورهای کامپیوتر از سربرگِ تاریخچه", "sales/table", s("DeskInvoicesScreen")),
    //  PAY-21: حساب‌پی (QR، NFC با HCE) فقط در `CheckoutDialog` ِ رابطِ قدیم بود؛ پس از دستهٔ ۱۳ صندوقِ گوشی راهی نداشت
    Feature(
      "PAY-21 «پرداخت با حساب‌پی (NFC/QR)» در صندوقِ گوشی", "sale/checkout/hesabpay",
      "ir.vil3ntec.tohid.ui.v3.sales.checkout.HesabTillRoute", "ir.vil3ntec.tohid.pay.HesabPayIntentFlow", "ir.vil3ntec.tohid.pay.HceSlot",
    ),
    Feature("PAY-21 درِ حساب‌پی در برگهٔ تسویهٔ گوشی", "sale/checkout", "ir.vil3ntec.tohid.ui.v3.sales.checkout.HesabTillModel", root = PHONE_CHECKOUT),
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
    //  ── «سفارش‌های آنلاین» (خواستِ صاحب‌کار): کاشیِ خانه جای «فروشِ تازه»؛ صندوق با نوار یا دکمهٔ شناورِ خانه ──
    Feature("کاشیِ «سفارش‌های آنلاین» با نشانِ زنده روی خانه", "orders", "$SHELL.HomeOrdersTile", "$SHELL.rememberHomeOrders"),
    Feature("«فروشِ تازه» ِ شناور روی خانه وقتی نوار راهِ صندوق ندارد", "sale", "$SHELL.HomeSaleFab", "$SHELL.HomeTiles"),
    Feature("آماده‌سازی و تحویل: عکس برای مشتری، «بسته شد»، پیک یا «خودش می‌آید»", "orders/{id}", "$ONLINE.orders.OrderPrepCardV3"),
    //  ── RSK-03/04: «کارت در صف» بالای صف و فهرستِ بسته و مطمئن (شماره و جا) بیرون از هر سفارش ──
    Feature("RSK-03 سفارش‌های پیش از ارسال با هشدار، بالای صف", "orders", "$ONLINE.orders.OrderRiskQueueCardV3", "$ONLINE.orders.rememberRiskQueue"),
    Feature("RSK-04 صفحهٔ جدای فهرستِ بسته و مطمئن", "orders/risk-lists", "$ONLINE.orders.RiskListsRoute", "$ONLINE.orders.RiskEntriesList"),
    Feature("RSK-04 بستنِ جای همین سفارش (پین) با شعاع", "orders/{id}", "$ONLINE.orders.OrderRiskCardV3", "$ONLINE.orders.RiskEntrySheet"),
    Feature(
      "گفت‌وگوی هر سفارش: «بسته شد»، «مشکل دارم» و رشته‌های پیشینِ مشتری", "chat/{conversationId}",
      "$ONLINE.chat.ChatLifeBanner", "$ONLINE.chat.ChatEarlierThreads",
    ),
    //  ── ویترین ──
    Feature("پیوند و QR ِ کارزار", "storefront/campaigns", s("CampaignsPane")),
    Feature("M3 عکس و ویدیوی ویترین", "storefront/media", s("StorefrontMediaPanel")),
    Feature("ABUSE-01 شکایت و گزارشِ خریداران (پاسخ و اعتراض)", "storefront/trust", "ir.vil3ntec.tohid.ui.v3.online.storefront.TrustCasesContent"),
    //  ── دستگاه‌ها و تنظیمات ──
    Feature("HW صفِ چاپ، چاپگرِ برچسب، ترازو، نمایشگرِ مشتری و ابزارهای تازه", "devices", s("PrintQueuePanel"), s("LabelsPanel"), s("ScalePanel"), s("CustomerDisplayPanel"), s("HwToolsSwitch")),
    Feature("HW تنظیمِ چاپگر و کشو", "devices", s("PrinterSettingsDialog"), s("OpenDrawerDialog")),
    Feature("HW-12 راه‌اندازیِ آسانِ چاپگر و بارکدخوان", "devices", s("PrinterFinderDialog"), s("ScannerSetupTools")),
    Feature("سرورهای دیگر", "settings/data/servers", s("ServersSettings"), pattern = "settings/{group}/{section}"),
    //  ── «تازه‌ها» (OPS-05، خواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۲) ──
    Feature("OPS-05 تازه‌ها — همهٔ تغییرها از «بیشتر»", "whats-new", s("WhatsNewScreen")),
    Feature("OPS-05 تازه‌ها — برگهٔ یک‌باره پس از به‌روزرسانی", "whats-new", s("WhatsNewOnUpdate"), s("WhatsNewSheet")),
    Feature("OPS-05 کلیدِ ردهٔ A ِ «تازه‌ها»", "settings/data/whats-new", s("whatsNewFromNow"), pattern = "settings/{group}/{section}"),
    //  ── «گزارش‌های مشتری» روی گوشی (دورِ ۱۴): کلید تا این دور روی گوشی ردیف نداشت و این کارها هرگز روشن نمی‌شدند ──
    Feature("CRM-10 ادغامِ مشتریانِ تکراری (گوشی)", "debtors/merge", "ir.vil3ntec.tohid.ui.v3.money.DebtorMergeSheet"),
    Feature("CRM-05 تصویرِ صورت‌حساب (گوشی)", "debtors/{id}", "ir.vil3ntec.tohid.print.StatementImage"),
    Feature("CRM-08/12 گروه، لیستِ قیمت و رضایتِ تبلیغ (گوشی)", "debtors/{id}", s("CrmPricingButton")),
    Feature(
      "کلیدِ «گزارش‌های مشتری» (ردهٔ B)", "settings/data/customer-reports",
      "ir.vil3ntec.tohid.ui.v3.settings.sections.CrmReportsScreen", pattern = "settings/{group}/{section}",
    ),
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

  /**
   *  «فروشِ تازه» از خانه رفت (کاشیِ «سفارش‌های آنلاین» جایش آمد) ⇒ صندوق باید از ریشه با دستِ کاربر برسد: زبانهٔ
   *  صندوق در جدولِ نسخهٔ ۳، دکمهٔ شناورِ خانه و کاشیِ کششیِ خانه به همان مقصد، و `HomeTilesTest` برای هر چیدمانِ نوار.
   */
  @Test
  fun `صندوق پس از رفتنِ کاشیِ «فروشِ تازه» هنوز از خانه می‌رسد`() {
    val sale = V3Modules.table.resolve("sale")
    assertTrue("زبانهٔ صندوق", sale != null && sale.route.kind == RouteKind.TAB && AppMode.SHOP in sale.module.modes)
    val shell = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/shell")
    val fab = SourceGraph.strip(File(shell, "HomeShortcuts.kt").readText())
    assertTrue("دکمهٔ شناور به «فروشِ تازه» می‌رود", "onOpen(ShellTargets.NEW_SALE)" in fab.substringAfter("fun HomeSaleFab("))
    assertTrue("کاشیِ کششیِ خانه هنوز «فروشِ تازه» دارد", "ShellTargets.NEW_SALE" in File(shell, "HomeDrawerCard.kt").readText())
    assertTrue("$SHELL.HomeSaleFab" in reached("$SHELL.HomeScreen"))
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

  /**
   *  «هیچ چیز بی‌راه نماند» (صاحب‌کار): کلیدِ این بخش‌ها روی **همین دستگاه** است، پس کاری که در صفحه‌های گوشی پشتِ
   *  یکی از آن‌هاست با پنهان بودنِ ردیفش روی گوشی (`SettingsLayout.PHONE_HIDDEN`) هرگز روشن نمی‌شود — روشن کردن
   *  روی کامپیوتر گوشی را روشن نمی‌کند. «گزارش‌های مشتری» تا دورِ ۱۴ همین بود: ادغامِ تکراری‌ها، تصویرِ صورت‌حساب،
   *  گروه و لیستِ قیمت، و سنِ بدهی و RFM ِ گوشی پشتِ آن بودند و ردیفش فقط روی کامپیوتر.
   *  صفحه‌های گوشیِ نسخهٔ ۳ = هر پروندهٔ زیرِ `ui/v3` جز صفحه‌های خودِ تنظیمات و پرونده‌های `Desk…`/`…Desk`.
   */
  @Test
  fun `کلیدی که روی گوشی پنهان است هیچ کارِ صفحه‌های گوشی را پشتِ خودش ندارد`() {
    assertEquals("بخشِ پنهان روی گوشی بی خواننده در GATES", emptySet<String>(), SettingsLayout.PHONE_HIDDEN - GATES.keys)
    val v3 = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3")
    val phone = v3.walkTopDown()
      .filter { it.isFile && it.extension == "kt" && !it.relativeTo(v3).invariantSeparatorsPath.startsWith("settings/") && "Desk" !in it.name }
      .associate { it.relativeTo(v3).invariantSeparatorsPath to SourceGraph.strip(it.readText()) }
    assertTrue("صفحه‌های گوشی پیدا نشدند", phone.keys.any { it.startsWith("money/") })
    val bad = SettingsLayout.PHONE_HIDDEN.sorted().flatMap { key ->
      GATES.getValue(key).flatMap { gate -> phone.filterValues { gate in it }.keys.sorted().map { "$key ⇐ «$gate» در ui/v3/$it" } }
    }
    assertEquals("کارِ گوشی پشتِ کلیدی که روی گوشی ردیف ندارد:", emptyList<String>(), bad)
  }

  @Test
  fun `«گزارش‌های مشتری» روی گوشی ردیف و توضیح دارد و «ادغام» و «صورت‌حساب» پیدایش می‌کنند`() {
    val key = SettingsLayout.CRM_REPORTS
    //  ردهٔ B (تصمیمِ سیاستیِ دکان): پیش‌فرض خاموش، دیدنی در دستهٔ خودش با یک خطِ ساده
    assertEquals(SettingsTiers.Tier.ADVANCED, SettingsTiers.tier(key))
    for (desktop in listOf(false, true)) {
      assertTrue("$key ($desktop) ردیف ندارد", SettingsLayout.visible(key, desktop, serverOn = emptySet(), featuresOn = emptySet()))
    }
    assertTrue(SettingsHelp.line(key).orEmpty().isNotBlank())
    val docs = SettingsSearchIndex.docs(desktop = false, serverOn = emptySet())
    listOf("گزارش مشتری", "ادغام", "تکراری", "صورت‌حساب", "merge duplicates", "customer group").forEach { q ->
      assertTrue("جست‌وجوی «$q» روی گوشی", SettingsSearchIndex.search(docs, q).any { it.key == key })
    }
  }

  private companion object {
    /**
     *  بخش ⇐ خواننده‌(های) «روشن است؟» ِ کلیدش در کد. هر بخشِ `PHONE_HIDDEN` باید این‌جا باشد؛ دلیلِ «فقط کامپیوتر»
     *  ِ هر کدام در `SettingsPortTest.DESK_ONLY`. `TillUser.me`/`meId` دروازه نیستند (خاموش ⇒ همان نامِ حساب).
     */
    val GATES: Map<String, List<String>> = mapOf(
      SettingsLayout.CRM_REPORTS to listOf("ShopPolicy.crmReports("),
      SettingsLayout.POS_SEARCH to listOf("PosSearchPrefs."),
      SettingsLayout.POS_TOUCH to listOf("PosTouchPrefs."),
      SettingsLayout.POS_MORE_KEYS to listOf("PosMoreKeysPrefs."),
      SettingsLayout.KEY_HELP to listOf("KeyHelpPref."),
      SettingsLayout.TILL_SWITCH to listOf("TillUser.on", "TillUser.lock(", "TillUser.current(", "TillUser.unlock("),
      SettingsLayout.REPORT_KEYS to listOf("ReportKeysPref."),
      SettingsLayout.WEBCAM to listOf("WebcamPref."),
    )
    const val APP_ROOT = "ir.vil3ntec.tohid.ui.AppRoot"
    const val PHONE_SALE = "ir.vil3ntec.tohid.ui.v3.sales.cart.SaleRoute"
    const val PHONE_CHECKOUT = "ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutRoute"
    const val PHONE_REPORTS = "ir.vil3ntec.tohid.ui.v3.analytics.ReportsScreen"
    const val DESK_REPORTS = "ir.vil3ntec.tohid.ui.v3.analytics.ReportsDesk"
    const val MONEY = "ir.vil3ntec.tohid.ui.v3.money"
    const val HISTORY = "ir.vil3ntec.tohid.ui.v3.sales.history"
    const val SHELL = "ir.vil3ntec.tohid.ui.v3.shell"
    const val ONLINE = "ir.vil3ntec.tohid.ui.v3.online"
    val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
    val graph: SourceGraph by lazy { SourceGraph.of(File(repo, "android-native/app/src/main/java")) }
    private val cache = HashMap<String, Set<String>>()
    fun reached(root: String): Set<String> = cache.getOrPut(root) { graph.reach(listOf(root)) }
  }
}
