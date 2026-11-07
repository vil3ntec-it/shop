package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Place
import ir.vil3ntec.tohid.ui.v3.settings.sections.ServersScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CallToAction
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.PublishedWithChanges
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.sections.AboutScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.AlertsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.AppLockScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.AppearanceScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.BackupScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.CameraScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ScanCashScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.WebcamScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.CheckoutRulesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.CreditGateScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.CrmReportsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.DigitsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.EncryptionScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.HesabPayAccountScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ExpiryScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.HeldInvoicesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.LanguageScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.NavBarScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PasswordScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PayFixSettingsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PaySettingsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PriceListSettingsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.QuoteSettingsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ReturnsSettingsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.SessionsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.StaffCodeScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncScreen
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Storage
import ir.vil3ntec.tohid.ui.v3.settings.sections.TillReceiveScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.MultiDrawerScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.StockModeScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.StockInsightScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.StockLevelsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ProductToolsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.FraudTrailScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ChatGuardScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.WhatsNewSettingScreen
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Loyalty
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Warehouse
import ir.vil3ntec.tohid.ui.v3.settings.sections.AnomalyScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.CustomerCardScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.TillGoodsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.GiftCardScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.LayawayScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.LoyaltyScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.OwnerReportScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PromoGuardScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.OnlineOrdersScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.SafeBookScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.SmartForecastScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.StockPlacesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.StorefrontEtaScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.TaxQuarterScreen
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShoppingBag
import ir.vil3ntec.tohid.ui.v3.settings.sections.CountToolsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.DeliverLaterScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.GiftReceiptScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.LabelToolsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PriceWhyScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ProductInfoScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ProductSuggestScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ShrinkReportScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.SpecialOrderScreen
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PriceCheck
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import ir.vil3ntec.tohid.ui.v3.settings.sections.DocGapsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PosMoreKeysScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.KeyHelpScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.TillSwitchScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PosPriceCheckScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PosSearchScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PosTouchScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ReportKeysScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.LinePricingScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PosNotesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.AutoFeesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.EasySetupScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.SimpleAskScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.TrainingScreen
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Discount

/** صفحهٔ یک بخش: کلیدش (`SettingsLayout`)، آیکنِ خطیِ ردیفش و خودِ صفحه */
internal class SettingsPage(
  val key: String,
  val icon: ImageVector,
  /** `onBack` = null ⇒ صفحه درونِ ستونِ کامپیوتر است، بی دکمهٔ برگشت */
  val content: @Composable RouteScope.(onBack: (() -> Unit)?) -> Unit,
)

private fun Page(key: String, icon: ImageVector, content: @Composable RouteScope.(onBack: (() -> Unit)?) -> Unit) =
  SettingsPage(key, icon, content)

/**
 *  هر بخشِ `SettingsLayout` دقیقاً یک صفحه این‌جا دارد (`SettingsLayoutTest` از
 *  رویِ متن می‌سنجد). فایلِ هر صفحه در `sections/`.
 */
internal object SettingsPages {
  val ALL: List<SettingsPage> = listOf(
    Page("هشدارها", Icons.Outlined.Notifications) { AlertsScreen(it) },
    Page("ظاهر", Icons.Outlined.LightMode) { AppearanceScreen(it) },
    Page(SettingsLayout.NAV_BAR, Icons.Outlined.CallToAction) { NavBarScreen(shell.navAllowed, it) },
    Page("زبان", Icons.Outlined.Language) { LanguageScreen(it) },
    Page("تاریخ و رقم", Icons.Outlined.CalendarToday) { DigitsScreen(it) },
    Page(SettingsLayout.TRAINING, Icons.Outlined.School) { TrainingScreen(it) },
    Page("قفل برنامه", Icons.Outlined.Lock) { AppLockScreen(it) },
    Page(SettingsLayout.ENCRYPTION, Icons.Outlined.Shield) { EncryptionScreen(it) },
    Page("کد شاگرد", Icons.Outlined.Key) { StaffCodeScreen(it) },
    Page("دستگاه‌های وارد شده", Icons.Outlined.PhoneAndroid) { SessionsScreen(it) },
    Page("تغییر رمز حساب", Icons.Outlined.Password) { PasswordScreen(it) },
    Page("کالای منقضی", Icons.Outlined.EventBusy) { ExpiryScreen(it) },
    Page(SettingsLayout.CHECKOUT_RULES, Icons.Outlined.TaskAlt) { CheckoutRulesScreen(it) },
    Page(SettingsLayout.HELD, Icons.Outlined.ViewAgenda) { HeldInvoicesScreen(it) },
    Page(SettingsLayout.RETURNS, Icons.Outlined.SwapVert) { ReturnsSettingsScreen(it) },
    Page(SettingsLayout.PAY, Icons.Outlined.Payments) { PaySettingsScreen(it) },
    Page(SettingsLayout.HESAB_SHOP, Icons.Outlined.AccountBalanceWallet) { HesabPayAccountScreen(it) },
    Page(SettingsLayout.PAY_FIX, Icons.Outlined.PublishedWithChanges) { PayFixSettingsScreen(it) },
    Page(SettingsLayout.CREDIT_GATE, Icons.Outlined.CreditCardOff) { CreditGateScreen(it) },
    Page(SettingsLayout.CRM_REPORTS, Icons.Outlined.TableChart) { CrmReportsScreen(it) },
    Page(SettingsLayout.LINE_PRICING, Icons.Outlined.Discount) { LinePricingScreen(it) },
    Page(SettingsLayout.PRICE_LIST, Icons.Outlined.LocalOffer) { PriceListSettingsScreen(it) },
    Page(SettingsLayout.QUOTE, Icons.Outlined.RequestQuote) { QuoteSettingsScreen(it) },
    Page(SettingsLayout.PRICE_WHY, Icons.Outlined.QuestionMark) { PriceWhyScreen(it) },
    Page(SettingsLayout.POS_NOTES, Icons.Outlined.EditNote) { PosNotesScreen(it) },
    Page(SettingsLayout.AUTO_FEES, Icons.Outlined.AddCard) { AutoFeesScreen(it) },
    Page(SettingsLayout.SPECIAL_ORDER, Icons.Outlined.ShoppingBag) { SpecialOrderScreen(it) },
    Page(SettingsLayout.DELIVER_LATER, Icons.Outlined.PendingActions) { DeliverLaterScreen(it) },
    Page(SettingsLayout.GIFT_RECEIPT, Icons.Outlined.Celebration) { GiftReceiptScreen(it) },
    Page(SettingsLayout.TILL_RECEIVE, Icons.Outlined.PointOfSale) { TillReceiveScreen(it) },
    Page(SettingsLayout.MULTI_DRAWER, Icons.Outlined.PointOfSale) { MultiDrawerScreen(it) },
    Page(SettingsLayout.POS_SEARCH, Icons.Outlined.Search) { PosSearchScreen(it) },
    Page(SettingsLayout.POS_PRICE_CHECK, Icons.Outlined.PriceCheck) { PosPriceCheckScreen(it) },
    Page(SettingsLayout.POS_TOUCH, Icons.Outlined.TouchApp) { PosTouchScreen(it) },
    Page(SettingsLayout.POS_MORE_KEYS, Icons.Outlined.KeyboardCommandKey) { PosMoreKeysScreen(it) },
    Page(SettingsLayout.KEY_HELP, Icons.Outlined.KeyboardCommandKey) { KeyHelpScreen(it) },
    Page(SettingsLayout.TILL_SWITCH, Icons.Outlined.Lock) { TillSwitchScreen(it) },
    Page(SettingsLayout.GIFT_CARD, Icons.Outlined.CardGiftcard) { GiftCardScreen(it) },
    Page(SettingsLayout.LAYAWAY, Icons.Outlined.Schedule) { LayawayScreen(it) },
    Page(SettingsLayout.SAFE_BOOK, Icons.Outlined.AccountBalance) { SafeBookScreen(it) },
    Page(SettingsLayout.LOYALTY, Icons.Outlined.Loyalty) { LoyaltyScreen(it) },
    Page(SettingsLayout.CUSTOMER_CARD, Icons.Outlined.CreditCard) { CustomerCardScreen(it) },
    Page(SettingsLayout.TILL_GOODS, Icons.Outlined.QrCodeScanner) { TillGoodsScreen(it) },
    Page(SettingsLayout.SIMPLE_ASK, Icons.Outlined.QuestionAnswer) { SimpleAskScreen(it) },
    Page(SettingsLayout.REPORT_KEYS, Icons.Outlined.Keyboard) { ReportKeysScreen(it) },
    Page(SettingsLayout.DOC_GAPS, Icons.Outlined.FormatListNumbered) { DocGapsScreen(it) },
    Page(SettingsLayout.TAX_QUARTER, Icons.Outlined.Receipt) { TaxQuarterScreen(it) },
    Page(SettingsLayout.OWNER_REPORT, Icons.Outlined.Summarize) { OwnerReportScreen(it) },
    Page(SettingsLayout.STOCK_MODE, Icons.Outlined.SwapHoriz) { StockModeScreen(it) },
    Page(SettingsLayout.STOCK_INSIGHT, Icons.Outlined.Inventory2) { StockInsightScreen(it) },
    Page(SettingsLayout.STOCK_LEVELS, Icons.Outlined.Inventory) { StockLevelsScreen(it) },
    Page(SettingsLayout.SMART_FORECAST, Icons.Outlined.Insights) { SmartForecastScreen(it) },
    Page(SettingsLayout.STOCK_PLACES, Icons.Outlined.Warehouse) { StockPlacesScreen(it) },
    Page(SettingsLayout.COUNT_TOOLS, Icons.Outlined.FactCheck) { CountToolsScreen(it) },
    Page(SettingsLayout.SHRINK_REPORT, Icons.Outlined.RemoveShoppingCart) { ShrinkReportScreen(it) },
    Page(SettingsLayout.PRODUCT_TOOLS, Icons.Outlined.Description) { ProductToolsScreen(it) },
    Page(SettingsLayout.PRODUCT_INFO, Icons.Outlined.Badge) { ProductInfoScreen(it) },
    Page(SettingsLayout.PRODUCT_SUGGEST, Icons.Outlined.Category) { ProductSuggestScreen(it) },
    Page(SettingsLayout.LABEL_TOOLS, Icons.Outlined.Sell) { LabelToolsScreen(it) },
    Page(SettingsLayout.STOREFRONT_ETA, Icons.Outlined.LocalShipping) { StorefrontEtaScreen(it) },
    Page(SettingsLayout.PROMO_GUARD, Icons.Outlined.Redeem) { PromoGuardScreen(it) },
    Page(SettingsLayout.ONLINE_ORDERS, Icons.Outlined.ShoppingBag) { OnlineOrdersScreen(it) },
    Page(SettingsLayout.FRAUD_TRAIL, Icons.Outlined.History) { FraudTrailScreen(it) },
    Page(SettingsLayout.ANOMALY, Icons.Outlined.ReportProblem) { AnomalyScreen(it) },
    Page(SettingsLayout.CHAT_GUARD, Icons.Outlined.Storage) { ChatGuardScreen(it) },
    Page(SettingsLayout.WHATS_NEW, Icons.Outlined.NewReleases) { WhatsNewSettingScreen(it) },
    Page(SettingsLayout.CAMERA, Icons.Outlined.QrCodeScanner) { CameraScreen(it) },
    Page(SettingsLayout.EASY_SETUP, Icons.Outlined.SettingsSuggest) { EasySetupScreen(it) },
    Page(SettingsLayout.SCAN_CASH, Icons.Outlined.PointOfSale) { ScanCashScreen(it) },
    Page(SettingsLayout.WEBCAM, Icons.Outlined.Videocam) { WebcamScreen(it) },
    Page("همگام‌سازی", Icons.Outlined.Sync) { SyncScreen(it) },
    Page("داده‌ها", Icons.Outlined.Backup) { BackupScreen(it) },
    Page(SettingsLayout.ABOUT_APP, Icons.Outlined.Info) { AboutScreen(data, it) },
    Page(SettingsLayout.SERVERS, Icons.Outlined.Dns) { ServersScreen(it) },
  )

  fun of(key: String): SettingsPage? = ALL.firstOrNull { it.key == key }

  /** آیکنِ ردیفِ هر بخش، پیوند و دسته — همه خطی و تک‌رنگ (r5) */
  fun icon(key: String): ImageVector = of(key)?.icon ?: when (key) {
    SettingsLayout.PRINTER -> Icons.Outlined.Print
    SettingsLayout.PERIPHERALS -> Icons.Outlined.Devices
    SettingsLayout.PROFILE -> Icons.Outlined.AccountCircle
    SettingsLayout.STORE_PLACE -> Icons.Outlined.Place
    SettingsLayout.STORE_STOCK -> Icons.Outlined.Inventory2
    SettingsLayout.SHOP -> Icons.Outlined.Shield
    SettingsLayout.SALES -> Icons.Outlined.PointOfSale
    SettingsLayout.GOODS -> Icons.Outlined.Inventory2
    SettingsLayout.DEVICES -> Icons.Outlined.Devices
    SettingsLayout.DATA -> Icons.Outlined.Backup
    SettingsLayout.LOOK -> Icons.Outlined.Palette
    else -> Icons.Outlined.Tune
  }
}
