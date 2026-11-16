package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.data.PurchaseFxPref
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/**
 *  `settings/goods/purchase-fx`: کلیدِ دکانِ «خرید به ارزِ دیگر» (PUR-11، `PurchaseFxPref`، پیش‌فرض خاموش) با هشدارِ
 *  «پیش از روشن کردن، همهٔ دستگاه‌ها را به‌روز کنید». فهرستِ دستگاه‌های دکان نسخهٔ برنامه را ندارد، پس فقط هشدار است.
 *  بخشِ تنظیمات صاحب و مدیر (`Need.MANAGE`)؛ نشانیِ مستقیمِ نقشِ دیگر صفحه را می‌بیند ولی چیزی نمی‌نویسد.
 */
@Composable
internal fun RouteScope.PurchaseFxScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val manager = !ShopRole.isStaff(context)
  ToggleScreen(
    SettingsLayout.PURCHASE_FX, onBack, PurchaseFxPref.on(context), StrPurchaseFx.settingOn, StrPurchaseFx.updateFirst,
    more = listOf(StrPurchaseFx.settingNote, StrPurchaseFx.managersOnly),
  ) {
    if (manager) PurchaseFxPref.set(context, it)
  }
}
