package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrCustomerMask
import ir.vil3ntec.tohid.data.CustomerPrintPref
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/**
 *  `settings/sales/print-phone`: «چاپِ شمارهٔ کامل» ِ مشتری روی رسید و صورت‌حساب (CRM-15)، تنظیمِ دکان، پیش‌فرض خاموش؛
 *  ردیف فقط برای صاحب (`SettingsLayout.OWNER_ONLY`). اثر فقط با پرچمِ نقش‌محورِ `shop.customer_role_mask` (`CustomerMask.printPhone`).
 */
@Composable
internal fun RouteScope.PrintPhoneScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  //  نشانیِ مستقیمِ مدیر/کارمند: صفحه دیده می‌شود ولی چیزی عوض نمی‌شود («فقط صاحب»)
  val owner = ShopRole.current(context) == ShopRole.OWNER
  ToggleScreen(SettingsLayout.PRINT_PHONE, onBack, CustomerPrintPref.on(context), StrCustomerMask.printFull, StrCustomerMask.printFullHint) {
    if (owner) CustomerPrintPref.set(context, it)
  }
}
