package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrDeliver
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.ui.components.pos.DeliverLaterSheetWired
import ir.vil3ntec.tohid.ui.components.pos.GiftCardBar
import ir.vil3ntec.tohid.ui.components.pos.LayawayBar
import ir.vil3ntec.tohid.ui.components.pos.PriceListAtPos
import ir.vil3ntec.tohid.ui.components.pos.QuoteBar
import ir.vil3ntec.tohid.ui.components.pos.SpecialOrderBar
import ir.vil3ntec.tohid.ui.components.pos.deliverLaterReady
import ir.vil3ntec.tohid.ui.screens.TillReceivePhone
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/**
 *  ابزارهای سبدِ صندوقِ گوشی که فقط صفحهٔ فروشِ گوشیِ پیشین (`ui/screens/Sale.kt`) داشت و پس از پاک شدنِ رابطِ قدیم
 *  بی‌راه ماندند (کامپیوتر همین‌ها را در `DeskSale` دارد) — هر کدام پشتِ تنظیمِ خودش و بی آن هیچ: پیش‌فاکتور (POS-25)،
 *  سفارشِ ویژه (POS-26)، کارتِ هدیه (PAY-24)، فروشِ امانی (POS-27)، لیستِ قیمت (POS-18)، دریافتِ بدهی (POS-39) و
 *  «سبد ⇐ سفارشِ ارسال» (POS-38، با قیمتِ پس از تخفیفِ خودکار).
 */
@Composable
internal fun RouteScope.PhoneTillTools(lines: List<SalesEngine.CartLine>, priced: List<SalesEngine.CartLine>, debtorId: String?) {
  val toast: (String) -> Unit = { toasts.show(it) }
  var deliverOpen by remember { mutableStateOf(false) }
  val deliverLater = deliverLaterReady()
  Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    QuoteBar(data, cart, toast)
    SpecialOrderBar(data, store, debtorId, toast)
    GiftCardBar(data, store, toast)
    LayawayBar(data, store, cart, toast)
    PriceListAtPos(cart, data, lines, debtorId)
    TillReceivePhone(data, store, debtorId, toast)
    if (deliverLater && lines.isNotEmpty()) {
      V3Button(StrDeliver.button, { deliverOpen = true }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.LocalShipping)
    }
  }
  if (deliverOpen) {
    DeliverLaterSheetWired(data, priced, onClose = { deliverOpen = false }) { o ->
      deliverOpen = false
      cart.clear()
      toast(StrDeliver.created(o.orderNo))
    }
  }
}
