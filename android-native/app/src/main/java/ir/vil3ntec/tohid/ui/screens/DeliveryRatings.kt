package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import ir.vil3ntec.tohid.core.delivery.DeliveryRating
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.ui.courier.ratingTagLabel
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  DLV-18 — «امتیازِ تحویل‌ها» برای صاحب و مدیر (`delivery.couriers`)، پشتِ پرچمِ خاموشِ `shop.delivery_ratings`.
 *  همهٔ عددها از سرور (`GET /shop/delivery/ratings`، ۹۰ روزِ گذشته)؛ بی نام و شمارهٔ مشتری. میانگین با
 *  `DeliveryRating.avgText` و برچسب‌ها با همان نوشته‌های سه‌زبانهٔ صفحهٔ پیک.
 */

private fun tagsLine(s: DeliveryRating.Summary): String =
  s.tags.entries.sortedByDescending { it.value }.joinToString(" · ") { StrCourier.tagCount(ratingTagLabel(it.key), it.value) }

@Composable
internal fun RatingsSheet(repo: DeliveryRepository, onDismiss: () -> Unit) {
  val c = Shop.colors
  var board by remember { mutableStateOf<DeliveryRating.Board?>(null) }
  var err by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableIntStateOf(0) }
  LaunchedEffect(reload) {
    board = null
    err = null
    repo.ratings().onSuccess { board = it }.onFailure { err = it.userMessage }
  }
  TohidBottomSheet(title = StrCourier.ratingsButton, onDismiss = onDismiss) {
    SheetBody {
      Text(StrCourier.ratingsNote, style = MaterialTheme.typography.bodySmall, color = c.muted)
      val b = board
      if (b == null) {
        if (err != null) TohidErrorState(title = StrCourier.ratingsFailed, description = err ?: "", onRetry = { reload++ })
        else TohidLoadingState(rows = 3)
        return@SheetBody
      }
      if (b.couriers.isEmpty()) {
        Text(StrCourier.ratingsEmpty, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        return@SheetBody
      }
      b.couriers.forEach { x ->
        TohidCard {
          Text(x.name.ifBlank { StrCourier.raterCourier }, fontWeight = FontWeight.Bold, color = c.text)
          Text(StrCourier.fromCustomers(DeliveryRating.avgText(x.fromCustomers.avgTenths), x.fromCustomers.count), style = MaterialTheme.typography.bodyMedium, color = c.text)
          if (x.fromCustomers.tags.isNotEmpty()) Text(tagsLine(x.fromCustomers), style = MaterialTheme.typography.bodySmall, color = c.muted)
          Text(StrCourier.byCourier(DeliveryRating.avgText(x.byCourier.avgTenths), x.byCourier.count), style = MaterialTheme.typography.bodyMedium, color = c.text)
          if (x.byCourier.tags.isNotEmpty()) Text(tagsLine(x.byCourier), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
      }
      DeskSection(StrCourier.ratingsButton)
      b.recent.take(20).forEach { r ->
        val who = if (r.rater == "customer") StrCourier.raterCustomer else StrCourier.raterCourier
        Text("$who · ${StrCourier.recent(r.orderNo, r.stars)}", style = MaterialTheme.typography.bodySmall, color = c.text)
      }
    }
  }
}
