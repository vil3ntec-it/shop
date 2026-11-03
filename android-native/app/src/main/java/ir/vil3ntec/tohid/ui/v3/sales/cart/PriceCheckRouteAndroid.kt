package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.pos.PosPriceCheckPrefs
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/** POS-15 — مسیرِ `sale/price-check`: تنظیمِ همین دستگاه و حقِ دیدنِ پول از `Context`؛ خاموش ⇒ برگشت */
@Composable
fun RouteScope.PriceCheckRoute() {
  val context = LocalContext.current
  val on = remember { PosPriceCheckPrefs.load(context); PosPriceCheckPrefs.on.value }
  if (!on) { LaunchedEffect(Unit) { back() }; return }
  val showCost = remember { ShopRole.canSeeMoney(context) }
  //  S17: موجودیِ هر محل فقط با «محل‌های نگه‌داری» ِ همین دستگاه (همان دروازهٔ برگهٔ ردیف، POS-16)
  val places = remember { ir.vil3ntec.tohid.data.StockPlacesPref.places(context) }
  PriceCheckSheet(data, todayIso(), showCost, onClose = { back() }, places = places)
}
