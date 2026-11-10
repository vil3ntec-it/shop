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
import ir.vil3ntec.tohid.core.shopper.PrefsRules
import ir.vil3ntec.tohid.core.shopper.RefillCard
import ir.vil3ntec.tohid.core.shopper.RepeatPlan
import ir.vil3ntec.tohid.core.shopper.RepeatPlans
import ir.vil3ntec.tohid.core.shopper.ShopperText

/*
 *  کارت‌های مرحلهٔ ۲ ِ پیگیری: گفت‌وگو دربارهٔ همین سفارش، نظر (NTF-09)، امتیاز به رساننده (DLV-18) و «تکرارِ این
 *  سفارش» (MKT-08)؛ و کارتِ «وقتِ سفارشِ دوباره» ِ خانه. هر کدام فقط وقتی سرور آن بخش را دارد (پرچم) دیده می‌شود.
 */

@Composable
fun OrderExtrasBlock(model: CustomerModel, slug: String, o: PublicOrder) {
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
 *  CHK-24: «دستورِ تحویل» تا پیش از حرکت (همان کارتِ `#o-prefs` ِ وب) و «با کدام اسکناس…» با «نقد هنگامِ تحویل» —
 *  فقط با کلیدِ `deliveryPrefs` ِ پیگیری؛ پس از حرکت دیده ولی بسته ([PrefsModel]).
 */
@Composable
fun DeliveryPrefsCard(model: CustomerModel, slug: String, o: PublicOrder) {
  val m = model.deliveryPrefs
  val p = m.prefs
  if (p == null || !m.shown(o)) return
  val f = m.form
  val on = p.editable && !m.busy
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(ShopperText.prefsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Check(ShopperText.callBefore, f.callBeforeArrival, enabled = on) { m.form = m.form.copy(callBeforeArrival = it) }
      OutlinedTextField(
        value = f.leaveWith, onValueChange = { m.form = m.form.copy(leaveWith = it.take(PrefsRules.LEAVE_MAX)) }, enabled = on,
        label = { Text(ShopperText.leaveWith) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = f.landmark, onValueChange = { m.form = m.form.copy(landmark = it.take(PrefsRules.LANDMARK_MAX)) }, enabled = on,
        label = { Text(ShopperText.prefsLandmark) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
      )
      if (PrefsRules.tenderShown(o, p)) {
        Text(ShopperText.tenderAsk, style = MaterialTheme.typography.bodyMedium)
        Choice(ShopperText.tenderNone, f.cashTenderMinor == null, enabled = on) { m.form = m.form.copy(cashTenderMinor = null) }
        m.tenders(o).forEach { x ->
          Choice(ShopperText.tenderOption(x, x - o.totalMinor), f.cashTenderMinor == x, enabled = on) { m.form = m.form.copy(cashTenderMinor = x) }
        }
      }
      m.note?.let { Note(it, if (m.ok) Tone.OK else if (p.editable) Tone.ERROR else Tone.INFO) }
      MainButton(ShopperText.prefsSave, enabled = on) { m.save(slug) }
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
