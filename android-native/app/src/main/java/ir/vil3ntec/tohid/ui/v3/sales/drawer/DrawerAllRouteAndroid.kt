package ir.vil3ntec.tohid.ui.v3.sales.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrDrawers
import ir.vil3ntec.tohid.data.MultiDrawerPref
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.isoMillis
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/**
 *  `sales/drawer/all` — PAY-17: X/Z ِ همهٔ کشوهای امروز با هم (هر کشوی باز و هر کشوی بسته از نیمه‌شب). فقط با «چند کشو» ِ
 *  روشن و نه برای نمای کور (شاگرد با شمارشِ کور «موردِ انتظار» و اختلاف را هیچ‌جا نمی‌بیند، PAY-13).
 */
@Composable
fun RouteScope.DrawerAllRoute() {
  val context = LocalContext.current
  val ok = remember { MultiDrawerPref.on(context) && !ShopPolicy.blindHere(context) }
  if (!ok) { LaunchedEffect(Unit) { back() }; return }
  val payV2 = remember { ShopPolicy.payV2(context) }
  val all = remember(data) {
    //  آغازِ روزِ کاریِ امروز (`todayIso` با پایانِ روزِ دکان): نیمروزِ همان روز − ۱۲ ساعت
    val dayStart = isoMillis(todayIso()) - 12 * 3_600_000L
    DrawerModel.allDrawers(data, System.currentTimeMillis(), dayStart, payV2)
  }
  DrawerLinesSheet(StrDrawers.allTitle, StrDrawers.allNote, DrawerModel.allLines(all), onClose = { back() })
}
