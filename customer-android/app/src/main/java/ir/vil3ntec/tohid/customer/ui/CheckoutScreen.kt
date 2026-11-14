package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.CartRules
import ir.vil3ntec.tohid.core.shopper.CheckoutForm
import ir.vil3ntec.tohid.core.shopper.CheckoutRules
import ir.vil3ntec.tohid.core.shopper.DeliverySlot
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.core.shopper.StorePage
import ir.vil3ntec.tohid.core.shopper.PromoRules
import ir.vil3ntec.tohid.core.shopper.RepeatPlans

/**
 *  ثبتِ سفارش — همان قاعده‌های وب: روش‌های خودِ دکان، تأییدِ شماره با کد، نشانی یا نشانهٔ نزدیک، ناحیه و
 *  بازه (اگر دکان دارد)، فقط نقد بی گامِ دوبارهٔ انتخاب (یافتهٔ ۳ ِ ممیزی) و جمعی که سرور دوباره می‌سنجد.
 */
@Composable
fun CheckoutScreen(model: CustomerModel, slug: String) {
  val page = model.store.valueOrNull()
  Column(Modifier.fillMaxSize()) {
    TopBar(ShopperText.checkout, onBack = { model.back() })
    if (page == null) {
      (model.store as? Load.Failed)?.let { ErrorBox(it.error, onRetry = { model.loadStore(slug) }) } ?: BusyBox()
      return@Column
    }
    val f = model.form
    val set = { x: CheckoutForm -> model.updateForm(x) }
    val ful = CheckoutRules.fulfillmentOf(page.shop, f)
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (CheckoutRules.closed(page.shop)) Note(page.shop.ordering?.message?.takeIf { it.isNotBlank() } ?: ShopperText.notAccepting, Tone.WARN)

      // ── روشِ دریافت ──
      val options = CheckoutRules.fulfillments(page.shop)
      if (options.size > 1) {
        options.forEach { o ->
          Choice(if (o == CheckoutRules.DELIVERY) ShopperText.shipToMe else ShopperText.pickup, ful == o) { set(f.copy(fulfillment = o)) }
        }
      } else if (options.isNotEmpty()) Text(if (ful == CheckoutRules.PICKUP) ShopperText.pickup else ShopperText.shipToMe, style = MaterialTheme.typography.titleMedium)

      // ── شما و تأییدِ شماره ──
      SectionTitle(ShopperText.you)
      Field(ShopperText.name, f.name) { set(f.copy(name = it.take(80))) }
      Verify(model, page.verificationAvailable)

      // ── نشانی ──
      if (ful == CheckoutRules.DELIVERY) {
        SectionTitle(ShopperText.address)
        SavedAddresses(model)
        Field(ShopperText.address, f.addressText, ShopperText.addressHint) { set(f.copy(addressText = it.take(300))) }
        Field(ShopperText.landmark, f.landmark, ShopperText.landmarkHint) { set(f.copy(landmark = it.take(200))) }
        Field(ShopperText.area, f.area) { set(f.copy(area = it.take(80))) }
        Field(ShopperText.gate, f.gateColor) { set(f.copy(gateColor = it.take(40))) }
        PinBlock(
          pin = f.pin, busy = model.pinBusy, note = model.pinNote,
          onMine = { model.takePin() }, onPaste = { model.pastePin(it) }, onClear = { model.clearPin() },
          map = model.mapFeature(), onMap = { model.setPin(it) },
        )
        if (f.addressId == null && (f.pin != null || f.addressText.isNotBlank() || f.landmark.isNotBlank())) {
          TextButton(onClick = { model.go(Route.AddressEdit(null, fromCheckout = true)) }) { Text(ShopperText.saveThisAddress) }
        }
        CheckoutRules.zones(page)?.let { zones ->
          SectionTitle(ShopperText.zone)
          when {
            model.pinOutside -> Note(ShopperText.pinOutside, Tone.WARN)
            f.pin != null && model.pinBusy -> Text(ShopperText.pinZoneWait, style = MaterialTheme.typography.bodySmall)
            f.pin != null -> CheckoutRules.zoneOf(page, f)?.let { Note(ShopperText.pinZone(it.name), Tone.OK) }
          }
          val sub = CartRules.subtotal(model.cartOf(slug))
          zones.forEach { z -> Choice(ShopperText.zoneOption(z.name, CheckoutRules.zoneFee(z, sub)), f.zoneId == z.id) { model.pickZone(z.id) } }
        }
        Check(ShopperText.callBefore, f.callBeforeArrival) { set(f.copy(callBeforeArrival = it)) }
        Field(ShopperText.leaveWith, f.leaveWith) { set(f.copy(leaveWith = it.take(80))) }
      }

      // ── زمانِ تحویل (CHK-12) ──
      if (CheckoutRules.scheduled(page, ful)) Slots(model, f)

      Field(ShopperText.note, f.note, single = false) { set(f.copy(note = it.take(500))) }

      Promo(model)

      // ── پرداخت: فقط نقد، بی پرسش (سرور هم فقط `cod` می‌پذیرد) ──
      SectionTitle(ShopperText.pay)
      Text(if (ful == CheckoutRules.PICKUP) ShopperText.payCashPickup else ShopperText.payCash, style = MaterialTheme.typography.bodyLarge)
      Tender(model, page, slug)

      // ── پول (برآورد؛ جمعِ نهایی را سرور هنگامِ ثبت می‌سنجد) ──
      val s = CheckoutRules.summary(page, model.cartOf(slug), f, model.slotsPage())
      Column {
        MoneyRow(ShopperText.itemsTotal, ShopperText.money(s.subtotalMinor))
        if (ful == CheckoutRules.DELIVERY) {
          MoneyRow(ShopperText.deliveryFee, s.deliveryFeeMinor?.let { if (it > 0) ShopperText.money(it) else ShopperText.free } ?: "—")
          if (s.deliveryFeeMinor == null) Text(ShopperText.zoneAfter, style = MaterialTheme.typography.bodySmall)
          if (s.smallBasketFeeMinor > 0) Text(ShopperText.zoneSmall(s.smallBasketFeeMinor, CheckoutRules.zoneOf(page, f)?.minOrderMinor ?: 0), style = MaterialTheme.typography.bodySmall)
          s.freeLeftMinor?.let { Text(ShopperText.freeLeft(it), style = MaterialTheme.typography.bodySmall) }
        }
        val off = model.promo.applied(model.saved.ticket, model.saved.deviceId)?.discountMinor ?: 0
        if (off > 0) MoneyRow(ShopperText.discount, "− " + ShopperText.money(off))
        MoneyRow(ShopperText.total, ShopperText.money(s.totalMinor - off), strong = true)
        if (s.shortOfMinMinor > 0) Note(ShopperText.minLeft(s.shortOfMinMinor, s.minOrderMinor), Tone.WARN)
      }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      model.placeNote?.let { Note(it, if (model.placing) Tone.INFO else Tone.WARN) }
      val total = CheckoutRules.summary(page, model.cartOf(slug), f, model.slotsPage()).totalMinor -
        (model.promo.applied(model.saved.ticket, model.saved.deviceId)?.discountMinor ?: 0)
      val dup = model.duplicate
      if (dup != null) {
        //  S41: گزینهٔ بی‌خطر دکمهٔ اصلی است؛ «سفارشِ دوم» فقط با زدنِ صریح
        MainButton(ShopperText.dupTrack, enabled = !model.placing) { model.trackDuplicate() }
        OutlinedButton(onClick = { model.placeOrder(second = true) }, enabled = !model.placing, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.dupSecond) }
      } else {
        MainButton(if (model.repriced) ShopperText.accept(total) else ShopperText.submit(total), enabled = !model.placing) { model.placeOrder() }
      }
    }
  }
}

/** تأییدِ ایمیل با کد — برگهٔ ثبت و گفت‌وگو (هویتِ مشتری همان ایمیلِ تأییدشده است؛ شماره فقط تماسِ پیک، «فقط ایمیل») */
@Composable
fun VerifyBlock(model: CustomerModel) = Verify(model, model.store.valueOrNull()?.verificationAvailable ?: true)

@Composable
private fun Verify(model: CustomerModel, available: Boolean) {
  val f = model.form
  Field(ShopperText.phone, f.phone, keyboard = KeyboardType.Phone) { model.updateForm(f.copy(phone = it.take(20))) }
  if (!available) {
    Note(ShopperText.verifyOff, Tone.WARN)
    return
  }
  val ok = CheckoutRules.verified(f, model.saved.ticket, model.now())
  if (ok) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(f.email.trim() + " — " + ShopperText.verified, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
      TextButton(onClick = { model.otherNumber() }) { Text(ShopperText.otherNumber) }
    }
    return
  }
  Field(ShopperText.email, f.email, keyboard = KeyboardType.Email) { model.updateForm(f.copy(email = it.take(190))) }
  Text(ShopperText.verifyText, style = MaterialTheme.typography.bodySmall)
  OutlinedButton(onClick = { model.sendCode() }, enabled = !model.verifyBusy, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.sendCode) }
  if (model.codeSent != null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = model.code, onValueChange = { model.code = it.take(8) }, singleLine = true, label = { Text(ShopperText.code) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.weight(1f),
      )
      OutlinedButton(onClick = { model.checkCode() }, enabled = !model.verifyBusy) { Text(ShopperText.check) }
    }
  }
  model.verifyNote?.let { Note(it) }
}

/** «از نشانی‌های من»: یک لمس ⇒ نشانی، نشانه و پین؛ ناحیه از پین */
@Composable
private fun SavedAddresses(model: CustomerModel) {
  val list = model.saved.addresses
  if (list.isEmpty()) return
  Text(ShopperText.useSaved, style = MaterialTheme.typography.bodySmall)
  androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(list.size) { i -> val a = list[i]; Pill(a.label, model.form.addressId == a.id) { model.useAddress(a) } }
  }
}

/** تخفیف (MKT-01/09): خودکارهای زنده، کدِ تخفیف و پیامِ سرور؛ خاموش ⇒ هیچ */
@Composable
private fun Promo(model: CustomerModel) {
  val p = model.promo
  val list = p.list.valueOrNull() ?: return
  SectionTitle(ShopperText.promoTitle)
  list.promos.forEach { a ->
    val left = PromoRules.endsAtMs(a.endsOn)?.let { ((it - model.now()) / RepeatPlans.DAY_MS).coerceAtLeast(0) }
    Text(if (left != null) ShopperText.promoEnds(a.name, left) else a.name, style = MaterialTheme.typography.bodyMedium)
  }
  if (list.codes) {
    if (p.code.isBlank()) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = p.input, onValueChange = { p.input = it.take(PromoRules.CODE_MAX) }, singleLine = true,
          label = { Text(ShopperText.promoCode) }, modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { model.applyPromo() }, enabled = p.input.isNotBlank()) { Text(ShopperText.promoApply) }
      }
    } else TextButton(onClick = { model.clearPromo() }) { Text(ShopperText.promoClear) }
  }
  p.note?.let { Note(it, if (p.code.isNotBlank()) Tone.OK else Tone.WARN) }
}

/**
 *  CHK-14: «با کدام اسکناس پرداخت می‌کنید؟» — فقط `features.deliveryPrefs` و ارسال؛ گزینه‌ها همان قاعدهٔ سرور برای جمعِ
 *  دیده‌شده ([CheckoutRules.tenders])؛ جمع عوض شد و اسکناس دیگر درست نیست ⇒ برداشته می‌شود (همان `renderTender` ِ وب).
 */
@Composable
private fun Tender(model: CustomerModel, page: StorePage, slug: String) {
  val f = model.form
  val cart = model.cartOf(slug)
  val slots = model.slotsPage()
  val off = model.promo.applied(model.saved.ticket, model.saved.deviceId)?.discountMinor ?: 0
  val chosen = CheckoutRules.tenderOf(page, cart, f, slots, off)
  LaunchedEffect(chosen, f.cashTenderMinor) {
    if (chosen == null && model.form.cashTenderMinor != null) model.updateForm(model.form.copy(cashTenderMinor = null))
  }
  if (!CheckoutRules.tenderShown(page, cart, f, slots)) return
  val total = CheckoutRules.summary(page, cart, f, slots).totalMinor - off
  Text(ShopperText.tenderAsk, style = MaterialTheme.typography.bodyMedium)
  Choice(ShopperText.tenderNone, chosen == null) { model.updateForm(model.form.copy(cashTenderMinor = null)) }
  CheckoutRules.tenders(page, cart, f, slots, off).forEach { x ->
    Choice(ShopperText.tenderOption(x, x - total), chosen == x) { model.updateForm(model.form.copy(cashTenderMinor = x)) }
  }
}

@Composable
private fun Slots(model: CustomerModel, f: CheckoutForm) {
  SectionTitle(ShopperText.slot)
  when (val l = model.slots[f.fulfillment]) {
    is Load.Ready -> {
      val page = l.value
      if (page.allowAsap) Choice(ShopperText.asap, f.slotStartsAt == null) { model.updateForm(f.copy(slotStartsAt = null)) }
      if (page.slots.none { it.available }) Note(ShopperText.slotNone, Tone.WARN)
      page.slots.forEach { s -> Choice(slotLabel(s), f.slotStartsAt == s.startsAt, enabled = s.available) { model.updateForm(f.copy(slotStartsAt = s.startsAt)) } }
    }
    is Load.Failed -> ErrorBox(l.error, onRetry = null)
    else -> BusyBox(Modifier.fillMaxWidth())
  }
}

private fun slotLabel(s: DeliverySlot): String {
  val extra = when {
    !s.available -> " — " + ShopperText.slotFull
    s.few -> " — " + ShopperText.slotFew
    else -> ""
  }
  val fee = if (s.feeMinor > 0) " (+" + ShopperText.money(s.feeMinor) + ")" else ""
  return ShopperText.slot(s.date, s.startMin, s.endMin) + fee + extra
}

@Composable
private fun Field(
  label: String,
  value: String,
  hint: String? = null,
  keyboard: KeyboardType = KeyboardType.Text,
  single: Boolean = true,
  onChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
    placeholder = if (hint != null) { { Text(hint) } } else null, singleLine = single, minLines = if (single) 1 else 2,
    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
  )
}

/** گزینهٔ رادیویی (ثبت و کارتِ «دستورِ تحویل» ِ پیگیری) */
@Composable
internal fun Choice(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null, enabled = enabled)
    Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
internal fun Check(text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
  }
}
