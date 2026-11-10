package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.shopper.RefillCard
import ir.vil3ntec.tohid.core.shopper.RefundRules
import ir.vil3ntec.tohid.core.shopper.RefundView
import ir.vil3ntec.tohid.core.shopper.RepeatPlan
import ir.vil3ntec.tohid.core.shopper.RepeatPlans
import ir.vil3ntec.tohid.core.shopper.ShopperText

/*
 *  کارت‌های مرحلهٔ ۲ ِ پیگیری: گفت‌وگو دربارهٔ همین سفارش، نظر (NTF-09)، امتیاز به رساننده (DLV-18) و «تکرارِ این
 *  سفارش» (MKT-08)؛ و کارتِ «وقتِ سفارشِ دوباره» ِ خانه. هر کدام فقط وقتی سرور آن بخش را دارد (پرچم) دیده می‌شود.
 */

@Composable
fun OrderExtrasBlock(model: CustomerModel, slug: String, o: PublicOrder) {
  model.orderExtras.refund?.let { RefundCard(it) }
  if (model.orderExtras.chat) {
    OutlinedButton(onClick = { model.go(Route.Chat(slug, o.orderNo)) }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.chatOrder) }
  }
  val f = model.feedback
  f.review?.let { rs ->
    when {
      rs.review != null -> Note(ShopperText.reviewMine(rs.review!!.stars), Tone.OK)
      rs.canReview -> Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(ShopperText.reviewTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Stars(f.stars) { f.stars = it }
          OutlinedTextField(
            value = f.comment, onValueChange = { f.comment = it.take(rs.commentMax) }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(ShopperText.reviewHint) }, minLines = 2,
          )
          OutlinedButton(onClick = { f.sendReview(slug) }, enabled = !f.busy, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.reviewSend) }
        }
      }
    }
  }
  f.rating?.let { st ->
    when {
      st.rating != null -> Note(ShopperText.rateMine(st.rating!!.stars), Tone.OK)
      st.canRate -> Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(ShopperText.rateCourier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Stars(f.rateStars) { f.rateStars = it }
          Tags(st.tags, f.tags) { f.toggleTag(it) }
          OutlinedButton(onClick = { f.sendRating(slug) }, enabled = !f.busy, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.rateSend) }
        }
      }
    }
  }
  f.note?.let { Note(it) }
  RepeatChooser(model, o)
}

/**
 *  NTF-08: بازپرداختِ همین سفارش — همان `renderRefund` ِ `store/order-changes.js`: وضعیت، «برگشته به شما» و هر ردیفِ دفتر
 *  (زمان — روش، مبلغ) و زمانِ درخواست؛ بی دلیلِ کارمند و کننده (سرور هم نمی‌دهد). کلیدِ نیامده (پرچمِ خاموش یا سرورِ کهنه) ⇒ هیچ.
 */
@Composable
private fun RefundCard(r: RefundView) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(ShopperText.rfTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Note(RefundRules.note(r), if (RefundRules.closed(r)) Tone.OK else Tone.INFO)
      Text(ShopperText.rfSum(r.refundedMinor), style = MaterialTheme.typography.bodyLarge)
      for (e in r.entries) {
        val (left, right) = RefundRules.line(e)
        MoneyRow(left, right)
      }
      r.requestedAt?.let { Text(ShopperText.rfAsked(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
  }
}

/** پنج ستاره با هدفِ لمسِ ۴۸dp و برچسبِ دسترس‌پذیرِ «n ستاره از ۵» */
@Composable
fun Stars(value: Int, onPick: (Int) -> Unit) {
  Row {
    for (n in 1..5) {
      val on = n <= value
      IconButton(
        onClick = { onPick(n) },
        modifier = Modifier.size(48.dp).semantics { contentDescription = ShopperText.stars(n); selected = n == value; role = Role.RadioButton },
      ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(all: List<String>, picked: List<String>, onToggle: (String) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    all.forEach { t -> Pill(ShopperText.rateTag(t), t in picked) { onToggle(t) } }
  }
}

/** «تکرارِ این سفارش»: فقط سفارشی که از همین دستگاه ثبت شده و پایان‌یافتهٔ ناموفق نیست */
@Composable
private fun RepeatChooser(model: CustomerModel, o: PublicOrder) {
  if (model.saved.orders.none { it.token == o.token } || o.status in ir.vil3ntec.tohid.core.shopper.TrackRules.STOP) return
  val current = model.repeatOf(o.token)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    SectionTitle(ShopperText.repeatTitle)
    FlowRowOf {
      Pill(ShopperText.repeatOff, current == null) { model.setRepeat(o.token, null) }
      RepeatPlans.CHOICES.forEach { d -> Pill(ShopperText.repeatEvery(d), current?.everyDays == d) { model.setRepeat(o.token, d) } }
    }
    Text(ShopperText.repeatHow, style = MaterialTheme.typography.bodySmall)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOf(content: @Composable () -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** کارتِ خانه: «وقتِ سفارشِ دوباره» — پُر کردنِ سبد با قیمتِ امروز، فردا، یا این بار نه */
@Composable
fun DuePlanCard(model: CustomerModel, p: RepeatPlan) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(ShopperText.repeatDue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(ShopperText.repeatOf(p.storeTitle.ifBlank { p.slug }, p.orderNo) + " — " + ShopperText.repeatEvery(p.everyDays), style = MaterialTheme.typography.bodyMedium)
      MainButton(ShopperText.repeatFill) { model.fillFromPlan(p) }
      Row {
        TextButton(onClick = { model.snoozePlan(p) }) { Text(ShopperText.repeatLater) }
        TextButton(onClick = { model.skipPlan(p) }) { Text(ShopperText.repeatSkip) }
      }
    }
  }
}

/** MKT-06: یک دکان، کالاهای مصرفیِ رسیده به موعد؛ «افزودن» از پاسخِ تازهٔ سرور، «بعداً» فردا، «دیگر نه» تا خریدِ بعد */
@Composable
fun RefillCardView(model: CustomerModel, c: RefillCard) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(ShopperText.refillTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(ShopperText.refillBody(c.storeTitle, c.items.map { it.name.ifBlank { it.productId } }), style = MaterialTheme.typography.bodyMedium)
      MainButton(ShopperText.refillAdd) { model.fillRefill(c) }
      Row {
        TextButton(onClick = { model.snoozeRefill(c) }) { Text(ShopperText.refillLater) }
        TextButton(onClick = { model.muteRefill(c) }) { Text(ShopperText.refillMute) }
      }
    }
  }
}
