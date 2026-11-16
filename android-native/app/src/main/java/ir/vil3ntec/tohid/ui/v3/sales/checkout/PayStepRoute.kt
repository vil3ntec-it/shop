package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.gateway.TillHesab
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.TillMethods
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.ui.components.pos.PayEntry
import ir.vil3ntec.tohid.ui.components.pos.PayPanelState
import ir.vil3ntec.tohid.ui.components.pos.PayStep
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths

/**
 *  `sale/checkout/pay` — «گامِ پرداخت» ِ صندوقِ گوشی (e-paystep): مبلغِ قابلِ پرداختِ درشت، پرداخت‌های تأییدشده، کاشی‌های روش‌ها و
 *  کادرِ همان روش. هر پرداخت جدا تأیید می‌شود (`PayStep.confirm`)؛ وقتی همهٔ فاکتور پرداخت یا به حسابِ مشتری رفت تسویه در
 *  `CheckoutSession.settled` می‌نشیند و برگه بسته می‌شود تا `CheckoutRoute` همان دم از راهِ همیشگی ثبت کند. «بازگشت» ⇒ تسویه با سبدِ
 *  دست‌نخورده (پرداخت‌های تأییدشده می‌مانند تا صندوقدار برگردد یا بردارد). حساب‌پی ⇒ همان برگهٔ `sale/checkout/hesabpay` برای باقی.
 */
@Composable
fun RouteScope.PayStepRoute() {
  val ctx = CheckoutSession.payCtx
  if (ctx == null || CheckoutSession.done != null) { LaunchedEffect(Unit) { back() }; return }
  val busy by cart.posting.collectAsState()
  val pay = CheckoutSession.pay
  val entry = CheckoutSession.entry
  val customerId = CheckoutSession.form?.debtorId ?: ctx.customerId
  val customerName = customerId?.let { id -> data.debtors.find { it.id == id }?.name } ?: ctx.customerName
  var error by remember { mutableStateOf<String?>(null) }
  //  حالِ حساب‌پیِ دکان زنده (یک دقیقه در حافظهٔ `HesabPayEnv`): پاسخی که پس از زدنِ «پرداخت» رسید کاشی را همین‌جا می‌آورد
  val env = CheckoutSession.hesabEnv
  val till by produceState<TillHesab?>(null, ctx.saleKey, env) { value = env?.till() }
  val (tiles, hesabSetup) = remember(ctx, till) { till?.let(ctx.retile) ?: (ctx.tiles to ctx.hesabSetup) }
  //  حساب‌پی (برگهٔ رویِ همین) پول را گرفت ⇒ برگشت به تسویه تا همان دم ثبت شود
  val hesabPaid = CheckoutSession.hesabPaid
  LaunchedEffect(hesabPaid) { if (hesabPaid != null) back() }

  fun paid(st: Settlement) {
    CheckoutSession.settledFor = customerId
    CheckoutSession.settled = st
    entry.clear()
    back()
  }

  /** نسیه بی مشتری ⇒ گزینش؛ پس از گزینش برگشت به تسویه (جمع با همان مشتری دوباره) و همین گام با «نسیه» */
  fun pickCustomer() {
    nav.openForResult(SalesPaths.customer(customerId)) { picked ->
      if (!picked.isNullOrEmpty()) {
        CheckoutSession.form = CheckoutSession.form?.copy(debtorId = picked)
        CheckoutSession.reopenPay = TillMethods.Kind.CREDIT
        back()
      }
    }
  }

  fun confirm() {
    if (busy || CheckoutSession.settled != null) return
    when (val r = PayStep.confirm(entry, pay, ctx.total, customerId, ctx.ledger)) {
      is PayStep.Result.Done -> { error = null; paid(r.settlement) }
      PayStep.Result.More -> {
        error = null
        //  باقی‌مانده: پیش‌فرضِ بعدی نقد
        tiles.firstOrNull { it.kind == TillMethods.Kind.CASH }?.let { PayStep.open(entry, it, pay, ctx.total) }
      }
      is PayStep.Result.Bad -> {
        error = r.message
        if (r.needCustomer && entry.tile?.kind == TillMethods.Kind.CREDIT && customerId == null) pickCustomer()
      }
    }
  }

  fun pick(t: TillMethods.Tile) {
    if (!t.enabled || busy) return
    error = null
    when (t.kind) {
      //  حساب‌پی باقیِ همین گام را می‌گیرد (همان برگهٔ QR/NFC و «پول رسید» یا وب‌هوکِ رسمی)
      TillMethods.Kind.HESABPAY -> if (PayStep.dueMinor(pay, ctx.total) > 0) ctx.startHesab()
      TillMethods.Kind.CREDIT -> { PayStep.open(entry, t, pay, ctx.total); if (customerId == null) pickCustomer() }
      else -> PayStep.open(entry, t, pay, ctx.total)
    }
  }

  PayStepSheet(
    total = ctx.total,
    tiles = tiles,
    pay = pay,
    entry = entry,
    customerName = customerName,
    busy = busy,
    error = error,
    onPick = ::pick,
    onConfirm = ::confirm,
    onRemove = { row -> PayStep.remove(pay, row); error = null },
    onPickCustomer = ::pickCustomer,
    onSetup = if (hesabSetup) ({ nav.open(ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths.section(ir.vil3ntec.tohid.ui.screens.SettingsLayout.HESAB_SHOP)) }) else null,
    onBack = { back() },
    ledger = ctx.ledger,
    customerId = customerId,
  )
}

/** برگهٔ بلندِ گامِ پرداخت — فقط کشیدن؛ همهٔ حساب در `PayStep` */
@Composable
internal fun PayStepSheet(
  total: Double,
  tiles: List<TillMethods.Tile>,
  pay: PayPanelState,
  entry: PayEntry,
  customerName: String?,
  busy: Boolean,
  error: String?,
  onPick: (TillMethods.Tile) -> Unit,
  onConfirm: () -> Unit,
  onRemove: (Int) -> Unit,
  onPickCustomer: () -> Unit,
  onSetup: (() -> Unit)?,
  onBack: () -> Unit,
  ledger: ir.vil3ntec.tohid.data.ShopData? = null,
  customerId: String? = null,
) {
  val c = PhoneV3.colors
  val due = PayStep.dueMinor(pay, total)
  val paid = PayStep.paid(pay)
  V3Sheet(onDismiss = onBack, title = StrTillPay.payTitle, dismissible = !busy, scroll = false, detents = false) {
    Column(Modifier.fillMaxWidth()) {
      Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        //  مبلغِ قابلِ پرداخت (یا باقی‌مانده) درشت
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(if (paid.isEmpty()) StrTillPay.amountDue else StrTillPay.remainingDue, style = V3Type.subhead, color = c.inkMuted)
          Text(
            PhoneSaleText.amount(PayFlow.major(due)),
            style = V3Type.figure.copy(fontSize = 34.sp, lineHeight = 42.sp),
            color = c.ink, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          )
          if (paid.isNotEmpty()) {
            Text(
              StrTillPay.paidSummary(PhoneSaleText.amount(total), PhoneSaleText.amount(PayFlow.major(paid.sumOf { it.amountMinor }))),
              style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center,
            )
          }
        }
        if (paid.isNotEmpty()) {
          Spacer(Modifier.height(12.dp))
          Text(StrTillPay.paidTitle, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(bottom = 6.dp))
          V3Card(radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            paid.forEach { p ->
              Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text(p.name, style = V3Type.callout, color = c.ink)
                  if (p.ref.isNotEmpty()) Text(p.ref, style = V3Type.footnote, color = c.inkMuted)
                }
                Text(PhoneSaleText.amount(PayFlow.major(p.amountMinor)), style = V3Type.callout, color = c.ink)
                V3IconButton(Icons.Outlined.Close, StrTillPay.removePaid(p.name), onClick = { onRemove(p.row) })
              }
            }
          }
        }
        error?.let {
          Spacer(Modifier.height(12.dp))
          V3Message(V3Tone.ERROR, it, Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
        if (due > 0) {
          Spacer(Modifier.height(16.dp))
          CheckoutMethods(
            tiles, { it.kind == entry.tile?.kind }, busy, onPick, onSetup,
            title = if (paid.isEmpty()) StrTillPay.methodsTitle else StrTillPay.otherForRest,
          )
          Spacer(Modifier.height(4.dp))
          EntryPanel(total, pay, entry, customerName, onConfirm, onPickCustomer, ledger, customerId)
        }
        Spacer(Modifier.height(12.dp))
      }
      if (due > 0) {
        entry.tile?.let { t ->
          val label = when (t.kind) {
            TillMethods.Kind.CASH -> StrTillPay.received
            TillMethods.Kind.CARD -> StrTillPay.cardApproved
            TillMethods.Kind.CREDIT -> if (customerName == null) StrSale.pickCustomer else StrTillPay.toAccountButton
            else -> StrTillPay.confirmPaid
          }
          V3Button(
            if (busy) StrSale.posting else label,
            { if (t.kind == TillMethods.Kind.CREDIT && customerName == null) onPickCustomer() else onConfirm() },
            Modifier.fillMaxWidth(), enabled = !busy && t.kind != TillMethods.Kind.HESABPAY, loading = busy, haptic = true,
          )
        }
      } else {
        //  همه پرداخت شد ولی ثبت نشد (مثلاً برگه دوباره باز شد) ⇒ همان تأییدِ نهایی
        V3Button(StrTillPay.recordPaid, onConfirm, Modifier.fillMaxWidth(), enabled = !busy, loading = busy)
      }
      Spacer(Modifier.height(6.dp))
      V3TextButton(StrTillPay.backToCart, onBack, Modifier.align(Alignment.CenterHorizontally))
    }
  }
}

/** کادرِ روشِ باز: نقد (دریافتی، دکمه‌های تند، پولِ پس)، کارت (کارت‌خوان)، کیف پول/حواله (روش، مرجع، مبلغ)، نسیه (مشتری) */
@Composable
private fun EntryPanel(
  total: Double,
  pay: PayPanelState,
  entry: PayEntry,
  customerName: String?,
  onConfirm: () -> Unit,
  onPickCustomer: () -> Unit,
  ledger: ir.vil3ntec.tohid.data.ShopData?,
  customerId: String?,
) {
  val c = PhoneV3.colors
  val t = entry.tile ?: return
  Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    when (t.kind) {
      TillMethods.Kind.CASH -> {
        val exact = PayStep.exactCashMinor(pay, total)
        V3TextField(
          value = entry.amount, onValueChange = { entry.amount = it }, label = StrSale.cashReceived, kind = V3FieldKind.MONEY,
          helper = StrTillPay.cashBlankExact(PhoneSaleText.amount(PayFlow.major(exact))),
          imeAction = androidx.compose.ui.text.input.ImeAction.Done, onImeAction = onConfirm,
        )
        val chips = PayStep.cashChips(exact)
        if (chips.isNotEmpty()) {
          V3ChipRow(contentPadding = PaddingValues(0.dp)) {
            chips.forEach { m -> V3Chip(PhoneSaleText.amount(PayFlow.major(m)), entry.amount == PayStep.plain(m), { entry.amount = PayStep.plain(m) }) }
          }
        }
        val change = PayStep.changeMinor(entry, pay, total)
        if (change > 0) Text(StrTillPay.changeDue(PhoneSaleText.amount(PayFlow.major(change))), style = V3Type.headline, color = c.positive)
        //  PAY-10: ارزِ نقد فقط با جدولِ نرخِ روشنِ دکان
        if (pay.fxRates.isNotEmpty()) CheckoutFxBox(pay, bad = false, onSubmit = onConfirm)
      }
      TillMethods.Kind.CARD -> {
        val amount = PayFlow.amountOf(entry.amount)?.takeIf { !it.isNaN() } ?: PayFlow.major(PayStep.dueMinor(pay, total))
        Text(StrTillPay.cardCharge(PhoneSaleText.amount(amount)), style = V3Type.callout, color = c.ink)
        V3TextField(value = entry.amount, onValueChange = { entry.amount = it }, label = StrSale.amountWord, kind = V3FieldKind.MONEY)
        V3TextField(
          value = entry.ref, onValueChange = { entry.ref = it }, label = StrTillPay.approvalOptional,
          imeAction = androidx.compose.ui.text.input.ImeAction.Done, onImeAction = onConfirm,
        )
      }
      TillMethods.Kind.CREDIT -> {
        val rest = PhoneSaleText.amount(PayFlow.major(PayStep.dueMinor(pay, total)))
        if (customerName != null) Text(StrTillPay.toAccountLine(rest, customerName), style = V3Type.callout, color = c.ink)
        else {
          Text(StrTillPay.creditPickFirst, style = V3Type.callout, color = c.inkMuted)
          V3Button(StrSale.pickCustomer, onPickCustomer, kind = V3ButtonKind.SECONDARY)
        }
      }
      TillMethods.Kind.HESABPAY -> Unit
      else -> {
        //  کیف پول، حواله و (با تنظیم) کارتِ هدیه و اعتبار — همان روش‌های پنل، نه نقد و نه کارت
        V3ChipRow(contentPadding = PaddingValues(0.dp)) {
          pay.methods().filter { TillMethods.kindOf(it) != TillMethods.Kind.CARD }.forEach { m ->
            V3Chip(m.name, entry.method == m.id, { entry.method = m.id })
          }
        }
        V3TextField(value = entry.ref, onValueChange = { entry.ref = it }, label = StrSale.refNumber)
        V3TextField(
          value = entry.amount, onValueChange = { entry.amount = it }, label = StrSale.amountWord, kind = V3FieldKind.MONEY,
          imeAction = androidx.compose.ui.text.input.ImeAction.Done, onImeAction = onConfirm,
        )
        //  PAY-24: ماندهٔ کارتِ هدیه یا اعتبارِ مشتری (فقط با همان تنظیم) — همان راهنمای پنلِ پیشین
        val book = ledger?.takeIf { pay.stored }
        val giftView = remember(book) { book?.let(ir.vil3ntec.tohid.data.GiftCards::view) }
        CheckoutStoredPay.hint(book, giftView, PayFlow.DraftRow(entry.method, ref = entry.ref, amount = entry.amount), customerId)?.let {
          Text(it, style = V3Type.footnote, color = c.inkMuted)
        }
      }
    }
  }
}
