package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.shopper.CheckoutRules
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.core.shopper.TrackRules

/** «سفارش ثبت شد»: شماره و مبلغِ سرور، و دو راه: پیگیری یا برگشت به دکان. */
@Composable
fun PlacedScreen(model: CustomerModel, slug: String, token: String) {
  val o = model.order.valueOrNull()?.takeIf { it.token == token }
  Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(ShopperText.placedTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    if (o != null) {
      Text(ShopperText.orderNo(o.orderNo), style = MaterialTheme.typography.titleMedium)
      MoneyRow(ShopperText.finalTotal, ShopperText.money(o.totalMinor), strong = true)
    }
    Note(ShopperText.placedText, Tone.OK)
    MainButton(ShopperText.track) { model.go(Route.Track(slug, token)) }
    OutlinedButton(onClick = { model.back() }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.backToShop) }
  }
}

/** پیگیری با توکن: پله‌ها (مثلِ وب)، قلم‌ها، پول، «پرداخت پس از پذیرش» و لغو تا پیش از قبول. */
@Composable
fun TrackScreen(model: CustomerModel, slug: String, token: String) {
  var askCancel by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize()) {
    TopBar(ShopperText.track, onBack = { model.back() }) {
      IconButton(onClick = { model.refreshOrder() }) { Icon(Icons.Filled.Refresh, contentDescription = ShopperText.refresh) }
    }
    when (val l = model.order) {
      is Load.Ready -> if (l.value.token == token) OrderBody(model, l.value, onCancel = { askCancel = true }) else BusyBox()
      is Load.Failed -> ErrorBox(l.error, onRetry = { model.refreshOrder() })
      else -> BusyBox()
    }
  }
  if (askCancel) {
    AlertDialog(
      onDismissRequest = { askCancel = false },
      text = { Text(ShopperText.cancelQuestion) },
      confirmButton = { TextButton(onClick = { askCancel = false; model.cancelOrder() }) { Text(ShopperText.cancelYes) } },
      dismissButton = { TextButton(onClick = { askCancel = false }) { Text(ShopperText.no) } },
    )
  }
}

@Composable
private fun OrderBody(model: CustomerModel, o: PublicOrder, onCancel: () -> Unit) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(ShopperText.orderNo(o.orderNo), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(ShopperText.status(o.status), style = MaterialTheme.typography.titleMedium, color = kindColor(TrackRules.kind(o.status)))
    model.orderNote?.let { Note(it, Tone.INFO) }
    Steps(o)

    SectionTitle(ShopperText.itemsTotal)
    o.lines.forEach { l ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
          Text(l.name, style = MaterialTheme.typography.bodyLarge)
          Text(ShopperText.lineQty(l.qtyMilli, l.unit, l.unitPriceMinor), style = MaterialTheme.typography.bodySmall)
        }
        Text(ShopperText.money(l.lineTotalMinor), style = MaterialTheme.typography.bodyLarge)
      }
    }
    Column {
      MoneyRow(ShopperText.itemsTotal, ShopperText.money(o.subtotalMinor))
      if (o.discountMinor > 0) MoneyRow(ShopperText.discount, "− " + ShopperText.money(o.discountMinor))
      if (o.fulfillment == CheckoutRules.DELIVERY) {
        MoneyRow(ShopperText.deliveryFee, if (o.deliveryFeeMinor > 0) ShopperText.money(o.deliveryFeeMinor) else ShopperText.free)
      }
      MoneyRow(ShopperText.finalTotal, ShopperText.money(o.totalMinor), strong = true)
      MoneyRow(ShopperText.paid, ShopperText.money(TrackRules.paidMinor(o)))
      //  «مانده» فقط وقتی سفارش هنوز زنده است (ردشده/لغوشده پولی نمی‌خواهد — یافتهٔ ۴ ِ ممیزی)
      if (o.status !in TrackRules.STOP) MoneyRow(ShopperText.due, ShopperText.money(TrackRules.dueMinor(o)))
    }

    o.paymentStep?.let { s ->
      SectionTitle(ShopperText.pay)
      Text(ShopperText.payState(s.state, s.cash, s.amountMinor), style = MaterialTheme.typography.bodyLarge)
      if (s.canChoose && s.choice != "cash") {
        OutlinedButton(onClick = { model.payCash() }, enabled = !model.orderBusy, modifier = Modifier.fillMaxWidth()) {
          Text(ShopperText.payCashButton(s.cash))
        }
      }
    }

    if (o.canCancel) {
      OutlinedButton(onClick = onCancel, enabled = !model.orderBusy, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.cancelOrder) }
    }

    //  CHK-24: «دستورِ تحویل» تا پیش از حرکت؛ مرحلهٔ ۲: گفت‌وگو دربارهٔ همین سفارش، نظر، امتیاز به رساننده و «تکرارِ این سفارش»
    (model.route as? Route.Track)?.let {
      DeliveryPrefsCard(model, it.slug, o)
      OrderExtrasBlock(model, it.slug, o)
    }
  }
}

@Composable
private fun kindColor(kind: String) = when (kind) {
  "err" -> MaterialTheme.colorScheme.error
  "ok" -> MaterialTheme.colorScheme.primary
  else -> MaterialTheme.colorScheme.onSurface
}

/** پله‌ها: ✓ انجام‌شده، ● اکنون، ○ مانده، ✕ پایانِ ردشده — رنگ تنها نشانه نیست (برچسبِ دسترس‌پذیر هم دارد) */
@Composable
private fun Steps(o: PublicOrder) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    TrackRules.steps(o).forEach { s ->
      val c = MaterialTheme.colorScheme
      val (mark, color) = when (s.state) {
        TrackRules.StepState.DONE -> "✓" to c.primary
        TrackRules.StepState.NOW -> "●" to c.tertiary
        TrackRules.StepState.TODO -> "○" to c.outline
        TrackRules.StepState.STOP -> "✕" to c.error
      }
      val label = ShopperText.step(s.id)
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "$mark $label" }) {
        Box(Modifier.size(28.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
          Text(mark, color = color, style = MaterialTheme.typography.labelLarge)
        }
        Text(
          label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge,
          fontWeight = if (s.state == TrackRules.StepState.NOW) FontWeight.Bold else null,
        )
      }
    }
  }
}
