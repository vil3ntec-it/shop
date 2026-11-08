package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.LayawayCustomerPanel
import ir.vil3ntec.tohid.ui.components.pos.LoyaltyHistory
import ir.vil3ntec.tohid.ui.screens.CrmPricingButton
import ir.vil3ntec.tohid.ui.screens.CustomerCardButton
import ir.vil3ntec.tohid.ui.screens.CustomerOnlineOrdersPanel
import ir.vil3ntec.tohid.ui.screens.CustomerPricesButton
import ir.vil3ntec.tohid.ui.screens.StoreCreditGrantButton

/**
 *  بخش‌های پروندهٔ مشتری که پیش‌تر فقط در پروندهٔ رابطِ قدیمِ گوشی بودند، حالا زیرِ فاکتورهای حسابِ نسخهٔ ۳
 *  (`debtors/{id}`) — همان جزءهای مشترکِ پروندهٔ کامپیوتر (`DeskCustomers`)، بی رونوشتِ منطق:
 *  - فروشِ امانی و قسطیِ همین مشتری (POS-27، `LayawayCustomerPanel`، پشتِ `LayawayPref`)؛
 *  - مانده و سابقهٔ امتیازِ وفاداری (PRM-13، `LoyaltyHistory`؛ `LoyaltyPref` خاموش و بی ردیف ⇒ هیچ)؛
 *  - «گروه، لیستِ قیمت و رضایت» (CRM-08/12، `CrmPricingButton`، پشتِ «گزارش‌های مشتری»)؛
 *  - «قیمتِ خاصِ این مشتری» (POS-18، `CustomerPricesButton`، پشتِ `CustomerPricePrefs`)؛
 *  - «کارتِ مشتری» (CRM-16، `CustomerCardButton` — همان دکمهٔ پروندهٔ کامپیوتر، پشتِ `CustomerCardPref`؛ مشتریِ ادغام‌شده نه)؛
 *  - «دادنِ اعتبارِ هدیه» (PRM-15، `StoreCreditGrantButton`، پشتِ `GiftCardPref` و تخفیفِ «مجاز» ِ همان مبلغ)؛
 *  - سفارش‌های اینترنتیِ باز یا لغوشده (CRM-07، `CustomerOnlineOrdersPanel` — همان بخشِ کامپیوتر، فقط با بخشِ سفارشِ
 *    روشن و اجازهٔ «تماس»؛ هر ردیف همان سفارش را باز می‌کند).
 *  - «چه کسی این پرونده را دید» (CRM-15، `CustomerAccessPanel` — همان بخشِ کامپیوتر، فقط صاحب و `audit.view`).
 *  هر کدام خاموش یا بی ردیف ⇒ هیچ چیز کشیده نمی‌شود.
 */
@Composable
internal fun DebtorFileExtras(
  d: ShopData,
  store: ShopStore,
  debtorId: String,
  /** CRM-07: ردیفِ سفارش ⇐ همان سفارش (`orders/{id}`)؛ null ⇒ ردیف فقط خواندنی */
  onOpenOrder: ((String) -> Unit)? = null,
  toast: (String) -> Unit,
) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
    LayawayCustomerPanel(d, store, debtorId, toast)
    LoyaltyHistory(d, debtorId, Modifier.padding(top = 12.dp), store, toast)
    CrmPricingButton(store, d, debtorId, toast, Modifier.fillMaxWidth().padding(top = 8.dp))
    CustomerPricesButton(store, d, debtorId, toast, Modifier.fillMaxWidth().padding(top = 8.dp)) // POS-18، پیش‌فرض خاموش
    CustomerCardButton(store, d, debtorId, toast, Modifier.fillMaxWidth().padding(top = 8.dp))
    StoreCreditGrantButton(store, d, debtorId, toast, Modifier.fillMaxWidth().padding(top = 8.dp))
    CustomerOnlineOrdersPanel(d, debtorId, Modifier.padding(top = 12.dp), onOpen = onOpenOrder)
    //  CRM-15: «چه کسی این پرونده را دید» — فقط صاحب و `audit.view`
    ir.vil3ntec.tohid.ui.screens.CustomerAccessPanel(d, debtorId, Modifier.padding(top = 16.dp))
  }
}
