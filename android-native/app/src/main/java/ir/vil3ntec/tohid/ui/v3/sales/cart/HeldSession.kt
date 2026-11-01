package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.HeldExtras

/**
 *  POS-12: آنچه با فاکتورِ نگه‌داشته برگشت و در `CartStore` نیست (تخفیفِ نوشته‌شده، روشِ پرداخت،
 *  دریافتی) — فقط برای همان فاکتور (کلیدِ پیش‌نویس) می‌ماند؛ با نگه‌داشتنِ دوباره گم نمی‌شود و
 *  برگهٔ تسویه تخفیفش را از آن پر می‌کند. بیرون از صفحه، تا رفتن به تسویه پاکش نکند.
 */
object HeldSession {
  var kept by mutableStateOf<Pair<String, HeldExtras>?>(null)

  fun extras(cart: CartStore): HeldExtras = kept?.takeIf { it.first == cart.snapshot().key }?.second ?: HeldExtras()
}
