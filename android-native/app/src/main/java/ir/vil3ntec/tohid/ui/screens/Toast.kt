package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost

/**
 *  پیام‌های برنامه — حالا همان `V3SnackbarHost` ِ کیت (قرصِ تیره با نقطهٔ گونه، کارِ
 *  برندرنگ، حرکتِ فنری). گونه همچنان از خودِ متن (`V3Toasts.toneOf`)، پس هیچ صدازننده‌ای
 *  عوض نمی‌شود. این نام برای صفحه‌های امروز (پیک) مانده و در فاز ۳ برداشته می‌شود.
 */
@Composable
fun TohidSnackbar(host: SnackbarHostState, modifier: Modifier = Modifier) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { TohidSnackbarLegacy(host, modifier); return }
  V3SnackbarHost(host, modifier)
}
