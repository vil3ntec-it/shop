package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderCustomerPageDto
import ir.vil3ntec.tohid.data.CustomerOnlineOrders
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.components.common.OrderStatusTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  CRM-07 — «سفارش‌های اینترنتیِ باز یا لغوشده» در پروندهٔ مشتری (گوشیِ نسخهٔ ۳: `DebtorFileExtras`؛ کامپیوتر:
 *  `DeskCustomers`). قاعده‌ها در `data/CustomerOnlineOrders`؛ این‌جا فقط پرسیدن از سرور و کشیدن.
 *  ⛔ بخشِ سفارشِ خاموش، بی اجازهٔ «تماس»، مشتریِ بی شماره، خطای شبکه یا فهرستِ خالی ⇒ هیچ چیز کشیده نمی‌شود
 *  (همان پروندهٔ امروز). با هر تغییرِ صفِ سفارش (`OrdersLive.rev`) دوباره پرسیده می‌شود.
 */
@Composable
fun CustomerOnlineOrdersPanel(d: ShopData, debtorId: String, modifier: Modifier = Modifier) {
  if (!CustomerOnlineOrders.canAsk(OrdersLive.config.value)) return
  val phones = CustomerOnlineOrders.phones(d.debtors.find { it.id == debtorId })
  if (phones.isEmpty()) return
  val context = LocalContext.current
  val rev = OrdersLive.rev.value
  var page by remember(debtorId, phones) { mutableStateOf<OrderCustomerPageDto?>(null) }
  LaunchedEffect(debtorId, phones, rev) {
    OrdersLive.repository(context).forCustomer(phones).valueOrNull()?.let { page = it }
  }
  val shown = page ?: return
  val rows = CustomerOnlineOrders.rows(shown.items)
  if (rows.isEmpty()) return
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(StrOnline.customerOrders(rows.size), style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
    for (o in rows) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          StrOnline.customerOrderLine(o.orderNo, ir.vil3ntec.tohid.money(CustomerOnlineOrders.amount(o)), ir.vil3ntec.tohid.spanText(o.createdAt)),
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.text, modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        StatusPill(StrOnline.orderStatus(o.status), OrderStatusTone.order(o.status))
      }
    }
    if (shown.more) Text(StrOnline.customerOrdersMore, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
  }
}
