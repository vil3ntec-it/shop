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

/**
 *  بخش‌های پروندهٔ مشتری که پیش‌تر فقط در پروندهٔ رابطِ قدیمِ گوشی بودند، حالا زیرِ فاکتورهای حسابِ نسخهٔ ۳
 *  (`debtors/{id}`) — همان جزءهای مشترکِ پروندهٔ کامپیوتر (`DeskCustomers`)، بی رونوشتِ منطق:
 *  - فروشِ امانی و قسطیِ همین مشتری (POS-27، `LayawayCustomerPanel`، پشتِ `LayawayPref`)؛
 *  - مانده و سابقهٔ امتیازِ وفاداری (PRM-13، `LoyaltyHistory`؛ `LoyaltyPref` خاموش و بی ردیف ⇒ هیچ)؛
 *  - «گروه، لیستِ قیمت و رضایت» (CRM-08/12، `CrmPricingButton`، پشتِ «گزارش‌های مشتری»).
 *  هر کدام خاموش یا بی ردیف ⇒ هیچ چیز کشیده نمی‌شود.
 */
@Composable
internal fun DebtorFileExtras(d: ShopData, store: ShopStore, debtorId: String, toast: (String) -> Unit) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
    LayawayCustomerPanel(d, store, debtorId, toast)
    LoyaltyHistory(d, debtorId, Modifier.padding(top = 12.dp), store, toast)
    CrmPricingButton(store, d, debtorId, toast, Modifier.fillMaxWidth().padding(top = 8.dp))
  }
}
