package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CallToAction
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
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
import ir.vil3ntec.tohid.ui.v3.settings.sections.CheckoutRulesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.CreditGateScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.CrmReportsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.DigitsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.EncryptionScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ExpiryScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.HeldInvoicesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.LanguageScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.NavBarScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PasswordScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PaySettingsScreen
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
import ir.vil3ntec.tohid.ui.v3.settings.sections.StockModeScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.StockInsightScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.StockLevelsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ProductToolsScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.FraudTrailScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.ChatGuardScreen

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
    Page(SettingsLayout.CREDIT_GATE, Icons.Outlined.CreditCardOff) { CreditGateScreen(it) },
    Page(SettingsLayout.CRM_REPORTS, Icons.Outlined.TableChart) { CrmReportsScreen(it) },
    Page(SettingsLayout.TILL_RECEIVE, Icons.Outlined.PointOfSale) { TillReceiveScreen(it) },
    Page(SettingsLayout.STOCK_MODE, Icons.Outlined.SwapHoriz) { StockModeScreen(it) },
    Page(SettingsLayout.STOCK_INSIGHT, Icons.Outlined.Inventory2) { StockInsightScreen(it) },
    Page(SettingsLayout.STOCK_LEVELS, Icons.Outlined.Inventory) { StockLevelsScreen(it) },
    Page(SettingsLayout.PRODUCT_TOOLS, Icons.Outlined.Description) { ProductToolsScreen(it) },
    Page(SettingsLayout.FRAUD_TRAIL, Icons.Outlined.History) { FraudTrailScreen(it) },
    Page(SettingsLayout.CHAT_GUARD, Icons.Outlined.Storage) { ChatGuardScreen(it) },
    Page(SettingsLayout.CAMERA, Icons.Outlined.QrCodeScanner) { CameraScreen(it) },
    Page("همگام‌سازی", Icons.Outlined.Sync) { SyncScreen(it) },
    Page("داده‌ها", Icons.Outlined.Backup) { BackupScreen(it) },
    Page(SettingsLayout.ABOUT_APP, Icons.Outlined.Info) { AboutScreen(data, it) },
  )

  fun of(key: String): SettingsPage? = ALL.firstOrNull { it.key == key }

  /** آیکنِ ردیفِ هر بخش، پیوند و گروه — همه خطی و تک‌رنگ (r5) */
  fun icon(key: String): ImageVector = of(key)?.icon ?: when (key) {
    SettingsLayout.PRINTER -> Icons.Outlined.Print
    SettingsLayout.PERIPHERALS -> Icons.Outlined.Devices
    SettingsLayout.SECURITY -> Icons.Outlined.Shield
    SettingsLayout.SALES -> Icons.Outlined.PointOfSale
    SettingsLayout.DEVICES -> Icons.Outlined.QrCodeScanner
    SettingsLayout.DATA -> Icons.Outlined.Backup
    SettingsLayout.ABOUT -> Icons.Outlined.Info
    else -> Icons.Outlined.Tune
  }
}
