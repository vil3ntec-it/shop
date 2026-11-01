package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.core.model.OrderLineDto
import ir.vil3ntec.tohid.core.orders.OrderSubstitution
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space

/*
 *  ترجیحِ جایگزینیِ سفارش (CHK-15) در کارت و فرمِ «سفارش‌ها» — متن‌ها از هستهٔ مشترک
 *  (`core/orders/OrderSubstitution.kt`) تا برنامهٔ آیفون همین را بخواند. جدا از `Orders.kt`
 *  تا آن پرونده بزرگ‌تر نشود.
 */

/** «اگر کالایی نبود: …» بالای اقلام — بی ترجیحِ کلی هیچ */
@Composable
internal fun OrderSubstitutionNote(code: String) {
  OrderSubstitution.orderLabel(code)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted) }
}

/** زیرِ نامِ قلم: یادداشتِ قلم (CHK-16) و ترجیحِ مؤثرش (CHK-15) */
@Composable
internal fun OrderLineExtras(l: OrderLineDto) {
  val muted = Shop.colors.muted
  if (l.note.isNotEmpty()) Text(l.note, style = MaterialTheme.typography.labelSmall, color = muted)
  OrderSubstitution.lineLabel(l.substitution, l.substituteName)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = muted) }
}

/** فرمِ سفارشِ دستی: ترجیحِ کلی — فقط وقتی سرور `substitution` ِ `/config` را روشن گفته */
@Composable
internal fun OrderSubstitutionPicker(show: Boolean, selected: String, onPick: (String) -> Unit) {
  if (!show) return
  Text("اگر کالایی نبود", style = MaterialTheme.typography.labelLarge, color = Shop.colors.muted)
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
    OrderSubstitution.ORDER_CODES.forEach { code ->
      TohidFilterChip(label = OrderSubstitution.label(code), selected = selected == code, onClick = { onPick(code) })
    }
  }
}
