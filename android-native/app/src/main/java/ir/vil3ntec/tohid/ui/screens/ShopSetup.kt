package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.v3.onboarding.ShopSetupGate

/**
 *  نامِ پیشینِ دروازهٔ «دکانت را بسازیم» — `AppRoot` هنوز این را صدا می‌زند. صفحهٔ تازه
 *  `ui/v3/onboarding/ShopSetupGate` است (G1)؛ فاز ۳ این فایل را برمی‌دارد.
 */
@Composable
fun ShopSetupScreen(store: ShopStore, onDone: () -> Unit) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { ShopSetupScreenLegacy(store, onDone); return }
  ShopSetupGate(store, onDone)
}
