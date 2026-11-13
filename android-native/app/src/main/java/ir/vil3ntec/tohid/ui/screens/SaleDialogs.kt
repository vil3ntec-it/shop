package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.invoiceLabel
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleReceipt
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.numberInvalid
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.unitMoney
import ir.vil3ntec.tohid.print.PrintJob
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.components.staff.ApprovalDialog
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.ui.components.pos.touchKeysOnFocus

/* ============================ تسویه ============================ */

/**
 *  پنجرهٔ تسویه — تخفیف، نقدی/نسیه، مبلغِ پرداختی.
 *
 *  عددها همان‌جا و همان لحظه با `SalesEngine` حساب می‌شوند، نه با فرمولِ
 *  جداگانه‌ای در رابط کاربری. پس چیزی که اینجا دیده می‌شود دقیقاً همان
 *  چیزی است که ثبت خواهد شد.
 */
@Composable
fun CheckoutDialog(
  d: ShopData,
  cart: List<SalesEngine.CartLine>,
  presetDebtorId: String?,
  onDismiss: () -> Unit,
  onConfirm: (SalesEngine.Checkout) -> Unit,
  /** تخفیف‌های خودکارِ همین سبد (`cart` پس از آن‌هاست)؛ `null` ⇒ همان ردیف‌های امروز */
  promo: ir.vil3ntec.tohid.data.PromoEngine.Outcome? = null,
  /** POS-14 — ثبت در جریان است ⇒ دکمه تا پایانش قفل */
  busy: Boolean = false,
  /** POS-12 — تخفیفی که با فاکتورِ نگه‌داشته آمد (درصد، همان کادرِ کامپیوتر؛ [presetAmount] ⇒ مبلغ به افغانی، POS-20) */
  presetDiscount: String = "",
  presetAmount: Boolean = false,
  /** شناسهٔ فاکتورِ همین سبد (`CartStore.saleKey`) — برگهٔ تأیید به آن بسته است */
  saleKey: String? = null,
  /** PRM-13 — سبدِ پیش از امتیاز برای سنجشِ تخفیفِ ردیف (امتیاز تخفیفِ کارمند نیست)؛ `null` ⇒ همان [cart] */
  gateCart: List<SalesEngine.CartLine>? = null,
  /** PRM-13 — مبلغی که امتیاز از [cart] کم کرد (افغانی)؛ ۰ ⇒ همان ردیف‌های امروز */
  pointsOff: Double = 0.0,
) {
  val context = LocalContext.current
  //  «نیازمند تأیید» هم کادرِ تخفیف را نشان می‌دهد؛ فقط «ممنوع» پنهانش می‌کند (STF-02)
  val canDiscount = remember { ir.vil3ntec.tohid.data.ShopRole.mayDiscount(context) }
  // مثل نسخهٔ وب: اگر از سبد قرض‌داری انتخاب شده باشد، پنجره روی «نسیه»
  // باز می‌شود؛ وگرنه «نقدی». تخفیف هم پیش‌فرض درصدی است.
  val preset = presetDebtorId?.takeIf { id -> d.debtors.any { it.id == id } }

  var discountType by remember { mutableStateOf(if (presetAmount) SalesEngine.DiscountType.AMOUNT else SalesEngine.DiscountType.PERCENT) }
  var payment by remember { mutableStateOf(if (preset != null) SalesEngine.Payment.CREDIT else SalesEngine.Payment.CASH) }
  var discountText by remember { mutableStateOf(presetDiscount.ifBlank { "0" }) }
  var debtorId by remember { mutableStateOf(preset) }
  var paidText by remember { mutableStateOf(if (preset != null) "0" else "") }
  var debtorMenu by remember { mutableStateOf(false) }

  val checkout = SalesEngine.Checkout(
    discountType = discountType,
    //  F0-06: خالی ⇐ صفر؛ نامعتبر ⇐ خطای کنارِ کادر و دکمهٔ ثبت خاموش
    discountValue = if (canDiscount) parseNumber(discountText) ?: 0.0 else 0.0,
    payment = payment,
    paidAmount = parseNumber(paidText) ?: 0.0,
    debtorId = debtorId,
    blockExpired = remember { ir.vil3ntec.tohid.data.ShopPolicy.blockExpired(context) },
    rules = remember { ir.vil3ntec.tohid.data.CheckoutRulesPref.get(context) }, // POS-24، پیش‌فرض خاموش
    //  نامِ حسابِ واردشده — گزارشِ «فروشِ هر کاربر» از همین می‌خواند
    seller = remember { ir.vil3ntec.tohid.data.TillUser.me(context) }, // S46: کاربرِ صندوق؛ خاموش ⇒ همان نامِ حساب
    cashRounding = remember { ir.vil3ntec.tohid.data.CashRoundPref.step(context) }, // PAY-09، پیش‌فرض خاموش
    tax = remember { ir.vil3ntec.tohid.data.SaleTaxPref.active(context) }, // POS-37، پیش‌فرض خاموش
  )
  val totals = SalesEngine.totals(d, cart, checkout)

  //  S08: تخفیفِ بیش از سقف ⇒ «درخواستِ تأیید» با PINِ مدیر — همان تصمیمِ سرور
  var approval by remember { mutableStateOf<ApprovalSlip.Slip?>(null) }
  var askApproval by remember { mutableStateOf(false) }
  //  POS-21 (پشتِ `LinePolicy.on`، پیش‌فرض خاموش): قیمتِ دستیِ زیرِ فهرست و تخفیفِ ردیف هم تخفیف‌اند
  val lineGate = remember { ir.vil3ntec.tohid.data.LinePolicyPrefs.on(context) }
  val basis = DiscountGate.basis(lineGate, d, gateCart ?: cart, promo, totals)
  val decision = ir.vil3ntec.tohid.data.ShopRole.decide(
    context, "sales.discount", DiscountGate.req(basis.discount, basis.subtotal),
  )
  val gate = DiscountGate.state(decision.decision, saleKey, basis.discount, basis.subtotal, approval)

  //  PAY-09: نقدِ کاملِ پرداختِ ساده ⇒ ردیفِ گرد (همان حسابِ `SalesEngine.record`)؛ ترکیبی گردِ خودش را دارد
  val payV2 = remember { ir.vil3ntec.tohid.data.ShopPolicy.payV2(context) }
  val rounding = if (!payV2 && payment == SalesEngine.Payment.CASH) ir.vil3ntec.tohid.data.PayFlow.cashRounding(totals.finalTotal, checkout.cashRounding) else 0.0
  val due = totals.finalTotal + rounding
  // در فروشِ نقدی مبلغِ پرداختی همیشه کلِ مبلغِ نهایی است
  val paid = if (payment == SalesEngine.Payment.CASH) due
  else minOf(parseNumber(paidText)?.coerceAtLeast(0.0) ?: 0.0, totals.finalTotal)
  val remaining = (due - paid).coerceAtLeast(0.0)
  val discountBad = canDiscount && numberInvalid(discountText)
  //  PAY (v3 A4): پرداختِ ترکیبی — فقط با پرچم (`payV2` بالا)؛ خاموش ⇒ همان نقد/نسیهٔ امروز
  //  PAY-24: کارتِ هدیه و اعتبارِ فروشگاه در روش‌ها فقط با تنظیمِ همین دستگاه (پیش‌فرض خاموش ⇒ همان پنل)؛ PAY-09: پلهٔ گرد
  val payState = remember { ir.vil3ntec.tohid.ui.components.pos.PayPanelState().also { it.stored = ir.vil3ntec.tohid.data.GiftCardPref.on(context); it.off = ir.vil3ntec.tohid.data.PayMethodsPref.off(context); it.cashRounding = checkout.cashRounding } }
  val cashFocus = remember { androidx.compose.ui.focus.FocusRequester() }
  var payError by remember { mutableStateOf<String?>(null) }
  val paidBad = !payV2 && payment == SalesEngine.Payment.CREDIT && numberInvalid(paidText)
  fun confirm() {
    if (busy || discountBad || paidBad) return
    //  STF-02: تخفیفِ «نیازمند تأیید» بی برگه، یا «ممنوع»، ثبت نمی‌شود؛ برگهٔ تأییدشده همراهِ فاکتور
    if (gate == DiscountGate.State.NEEDS_APPROVAL || gate == DiscountGate.State.DENIED) return
    val signed = if (gate == DiscountGate.State.APPROVED) approval?.let(DiscountGate::json) else null
    if (!payV2) { onConfirm(checkout.copy(paidAmount = paid, debtorId = debtorId, approval = signed)); return }
    when (val r = ir.vil3ntec.tohid.data.PayFlow.checkout(payState.draft(totals.finalTotal, debtorId))) {
      is ir.vil3ntec.tohid.data.PayFlow.Outcome.Ok -> {
        val bad = if (payState.stored) ir.vil3ntec.tohid.data.GiftCards.problem(d, r.value, debtorId) else null
        if (bad != null) payError = ir.vil3ntec.tohid.data.GiftCards.message(bad)
        else onConfirm(checkout.copy(settlement = r.value, debtorId = debtorId, approval = signed))
      }
      is ir.vil3ntec.tohid.data.PayFlow.Outcome.Bad -> payError = ir.vil3ntec.tohid.data.PayFlow.message(r.problem)
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(Radius.lg),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(ir.vil3ntec.tohid.core.i18n.StrSalePages.settleSale, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(14.dp))
        //  POS-21: کادرِ تخفیف پنهان است ولی قیمتِ ردیفی زیرِ فهرست مانده ⇒ چرا «ثبت» خاموش است
        if (!canDiscount && gate == DiscountGate.State.DENIED) {
          StatusPill(ir.vil3ntec.tohid.core.i18n.StrLine.reductionDenied, PillTone.DANGER)
          Spacer(Modifier.height(14.dp))
        }

        //  تخفیف فقط با صاحب و مدیر — `ShopRole.canDiscount`
        if (canDiscount) {
        Text(ir.vil3ntec.tohid.core.i18n.StrSalePages.discountType, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf(ir.vil3ntec.tohid.core.i18n.StrSale.percentWord to SalesEngine.DiscountType.PERCENT, ir.vil3ntec.tohid.core.i18n.StrSalePages.byAmount to SalesEngine.DiscountType.AMOUNT),
          selected = discountType,
          onSelect = { discountType = it },
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = discountText,
          onValueChange = { discountText = numericInput(it) },
          label = { Text(if (discountType == SalesEngine.DiscountType.PERCENT) ir.vil3ntec.tohid.core.i18n.StrSale.discountPct else ir.vil3ntec.tohid.core.i18n.StrSale.discountAmount) },
          singleLine = true,
          isError = discountBad,
          supportingText = if (discountBad) { { Text(ir.vil3ntec.tohid.core.i18n.StrSale.badNumberShort) } } else null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )

        when (gate) {
          DiscountGate.State.NEEDS_APPROVAL -> {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatusPill(ir.vil3ntec.tohid.core.i18n.StrSale.needsApproval, PillTone.AWAITING)
              Text(
                if (decision.cap == "maxPct") ir.vil3ntec.tohid.core.i18n.StrSalePages.overYourCap(qty(decision.limit ?: 0.0))
                else ir.vil3ntec.tohid.core.i18n.StrSale.overYourRights,
                style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, modifier = Modifier.weight(1f),
              )
              OutlinedButton(onClick = { askApproval = true }, enabled = saleKey != null && !busy) { Text(ir.vil3ntec.tohid.core.i18n.StrSale.askApproval) }
            }
          }
          DiscountGate.State.APPROVED -> {
            Spacer(Modifier.height(6.dp))
            StatusPill(ir.vil3ntec.tohid.core.i18n.StrSale.approved, PillTone.SUCCESS)
          }
          DiscountGate.State.DENIED -> {
            Spacer(Modifier.height(6.dp))
            StatusPill(ir.vil3ntec.tohid.core.i18n.StrSale.discountDenied, PillTone.DANGER)
          }
          DiscountGate.State.FREE -> Unit
        }

        Spacer(Modifier.height(14.dp))
        }
        if (!payV2) {
        Text(ir.vil3ntec.tohid.core.i18n.StrSalePages.paymentType, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf(ir.vil3ntec.tohid.core.i18n.StrSale.cashPay to SalesEngine.Payment.CASH, ir.vil3ntec.tohid.core.i18n.StrSale.creditPay to SalesEngine.Payment.CREDIT),
          selected = payment,
          onSelect = { payment = it },
        )
        }

        /*
         *  مشتری — در نسیه اجباری، در نقدی اختیاری.
         *
         *  تا دیروز فروشِ نقدی به هیچ‌کس وصل نمی‌شد، پس «این مشتری امسال
         *  چقدر خرید کرده» فقط برای کسانی جواب داشت که نسیه برده بودند.
         *  حسابِ بدهی از تراکنش‌ها می‌آید نه از این انتخاب، پس مشتریِ
         *  فاکتورِ نقدی هیچ بدهی‌ای نمی‌سازد — فقط نامش روی فاکتور
         *  می‌نشیند.
         *
         *  فهرست هم دیگر یک `DropdownMenu`ِ بی‌انتها نیست؛ با جستجو.
         */
        Spacer(Modifier.height(12.dp))
        Text(
          if (payV2) ir.vil3ntec.tohid.core.i18n.StrSale.customerToAccount else if (payment == SalesEngine.Payment.CREDIT) ir.vil3ntec.tohid.core.i18n.Str.debtor else ir.vil3ntec.tohid.core.i18n.StrSale.customerOptional,
          style = MaterialTheme.typography.labelMedium,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { if (d.debtors.isNotEmpty()) debtorMenu = true },
            modifier = Modifier.weight(1f),
          ) {
            Text(
              debtorId?.let { id -> d.debtors.find { it.id == id }?.name }
                ?: if (d.debtors.isEmpty()) ir.vil3ntec.tohid.core.i18n.StrSalePages.nobodyYet else ir.vil3ntec.tohid.core.i18n.StrSale.chooseWord,
            )
          }
          if (debtorId != null) {
            TextButton(onClick = { debtorId = null }) { Text(ir.vil3ntec.tohid.core.i18n.StrAccount.remove) }
          }
        }
        if (debtorMenu) {
          SearchablePicker(
            title = if (payment == SalesEngine.Payment.CREDIT) ir.vil3ntec.tohid.core.i18n.StrSalePages.chooseDebtor else ir.vil3ntec.tohid.core.i18n.StrSalePages.chooseCustomer,
            //  ادغام‌شده دوباره انتخاب نمی‌شود (CRM-10)؛ بی ادغام همان فهرستِ امروز
            options = ir.vil3ntec.tohid.data.CustomerCrm.active(d),
            idOf = { it.id },
            nameOf = { it.name },
            onClose = { debtorMenu = false },
            onPick = { debtorId = it; debtorMenu = false },
          )
        }

        if (payV2) {
          Spacer(Modifier.height(12.dp))
          ir.vil3ntec.tohid.ui.components.pos.PaySplitSection(
            state = payState,
            total = totals.finalTotal,
            customerId = debtorId,
            customerName = debtorId?.let { id -> d.debtors.find { it.id == id }?.name },
            target = 48.dp,
            cashFocus = cashFocus,
            onSubmit = { confirm() },
            ledger = d,
          )
          payError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
        }

        if (!payV2 && payment == SalesEngine.Payment.CREDIT) {
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = paidText,
            onValueChange = { paidText = numericInput(it) },
            label = { Text(ir.vil3ntec.tohid.core.i18n.StrSale.paidAmount) },
            singleLine = true,
            isError = paidBad,
            supportingText = if (paidBad) { { Text(ir.vil3ntec.tohid.core.i18n.StrSale.badNumberShort) } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        //  PAY-21: حساب‌پی — فقط با پرچمِ سرور (`pay.hesabpay`) و نت؛ پیش از گرفتنِ پول، ثبت‌شدنیِ همین فاکتور سنجیده می‌شود
        ir.vil3ntec.tohid.ui.components.pos.CheckoutGateways(
          hook = ir.vil3ntec.tohid.ui.components.pos.GatewayHook(remember { ir.vil3ntec.tohid.pay.platform.HesabPayEnv.of(context) }, saleKey) { (SalesEngine.record(d, cart, checkout.copy(payment = SalesEngine.Payment.CASH), ir.vil3ntec.tohid.todayIso(), 0L, ::newId, saleId = saleKey) as? SalesEngine.Result.Failed)?.message },
          total = totals.finalTotal, panel = if (payV2) payState else null, customerId = debtorId, busy = busy, modifier = Modifier.padding(top = 12.dp),
          enabled = !discountBad && gate != DiscountGate.State.NEEDS_APPROVAL && gate != DiscountGate.State.DENIED,
          onPaid = { st -> onConfirm(checkout.copy(settlement = st, debtorId = debtorId, approval = if (gate == DiscountGate.State.APPROVED) approval?.let(DiscountGate::json) else null)) },
        )

        Spacer(Modifier.height(16.dp))
        Panel {
          //  S3: مثلِ خلاصهٔ صندوق — جمع پیش از تخفیف‌های خودکار، ردیفشان با نام، بعد تخفیفِ دستی
          if (promo != null && promo.stamp && promo.discount > 0) {
            MoneyRow(ir.vil3ntec.tohid.core.i18n.StrSale.itemsTotal, promo.gross)
            MoneyRow(SaleReceipt.promoLabel(promo.applied.map { it.name }), promo.discount, negative = true)
          } else MoneyRow(ir.vil3ntec.tohid.core.i18n.StrSale.itemsTotal, totals.subtotal + pointsOff)
          if (pointsOff > 0) MoneyRow(ir.vil3ntec.tohid.core.i18n.StrLoyalty.use, pointsOff, negative = true) // PRM-13
          MoneyRow(ir.vil3ntec.tohid.core.i18n.Str.discount, totals.discount)
          if (rounding != 0.0) MoneyRow(ir.vil3ntec.tohid.core.i18n.StrRounding.line, kotlin.math.abs(rounding), negative = rounding < 0) // PAY-09
          HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Shop.colors.border)
          MoneyRow(ir.vil3ntec.tohid.core.i18n.StrSale.finalAmount, due, strong = true)
          if (!payV2) {
          MoneyRow(ir.vil3ntec.tohid.core.i18n.StrSale.paidAmount, paid)
          MoneyRow(ir.vil3ntec.tohid.core.i18n.StrSale.remaining, remaining, tint = if (remaining > 0) Shop.colors.warning else null)
          }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(ir.vil3ntec.tohid.core.i18n.Str.cancel) }
          Button(
            onClick = { confirm() },
            enabled = !busy && !discountBad && !paidBad &&
              gate != DiscountGate.State.NEEDS_APPROVAL && gate != DiscountGate.State.DENIED,
            modifier = Modifier.weight(1f),
          ) { Text(if (busy) ir.vil3ntec.tohid.core.i18n.StrSale.posting else ir.vil3ntec.tohid.core.i18n.StrSale.recordSale) }
        }
      }
    }
    }
  }

  if (askApproval && saleKey != null) {
    ApprovalDialog(
      title = ir.vil3ntec.tohid.core.i18n.StrSale.discountApproval,
      summary = ir.vil3ntec.tohid.core.i18n.StrSalePages.discountOnInvoice(money(basis.discount), money(basis.subtotal)),
      shopId = ir.vil3ntec.tohid.data.ShopRole.shopId(context),
      requesterUserId = remember { ir.vil3ntec.tohid.data.TillUser.meId(context) },
      need = DiscountGate.need(saleKey, basis.discount, basis.subtotal),
      onApproved = { approval = it; askApproval = false },
      onDismiss = { askApproval = false },
    )
  }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.sm))
      .background(Shop.colors.surface2)
      .padding(3.dp),
  ) {
    options.forEach { (label, value) ->
      val active = value == selected
      Box(
        Modifier
          .weight(1f)
          .clip(RoundedCornerShape(Radius.sm))
          .background(if (active) Shop.colors.primary else Color.Transparent)
          .clickable { onSelect(value) }
          .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelLarge,
          color = if (active) Color.White else Shop.colors.muted,
        )
      }
    }
  }
}

@Composable
private fun MoneyRow(label: String, amount: Double, strong: Boolean = false, tint: Color? = null, negative: Boolean = false) {
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      label,
      style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
      color = if (strong) Shop.colors.text else Shop.colors.muted,
      modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
    )
    Text(
      "${if (negative) "−" else ""}${money(amount)} ؋",
      style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
      color = tint ?: Shop.colors.text,
      fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

/* ============================ فاکتور ============================ */

/**
 *  فاکتور — همان چیدمانِ نسخهٔ وب، به‌علاوهٔ چاپ روی چاپگرِ حرارتی.
 */
@Composable
fun InvoiceDialog(
  d: ShopData,
  sale: Sale,
  onDismiss: () -> Unit,
  onMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var printer by remember { mutableStateOf(false) }

  val items = remember(sale.id) { d.saleItems.filter { it.saleId == sale.id } }
  val stamped = remember { ir.vil3ntec.tohid.ui.PromoLive.stampOn(context) }
  val debtor = sale.debtorId?.let { id -> d.debtors.find { it.id == id } }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(Radius.lg),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(
          ir.vil3ntec.tohid.core.i18n.StrSalePages.salesInvoiceNo(sale.invoiceLabel() ?: "—"),
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "${formatDate(sale.date)} — ${ir.vil3ntec.tohid.data.PayFlow.payLabel(sale, cash = ir.vil3ntec.tohid.core.i18n.StrSale.cashPay)}",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
        )

        Spacer(Modifier.height(14.dp))
        items.forEach { item ->
          val product = d.products.find { it.id == item.productId }
          val name = SalesEngine.itemName(d, item)
          Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.weight(1f)) {
              Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = Shop.colors.text,
              )
              //  S3 (پرچمِ `shop.promo_stamp`): «مقدار × قیمتِ فهرست − تخفیفِ خودکار»؛ خاموش ⇒ همان متنِ پیشین
              Text(
                SaleReceipt.detail(item, product?.unit.orEmpty(), stamped, ::money, ::unitMoney, ::qty),
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.muted,
              )
              item.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted) } // POS-11
            }
            Text(money(item.totalPrice), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Shop.colors.border)
        Spacer(Modifier.height(10.dp))

        //  «جمع اقلام» = جمعِ همان ستون؛ تخفیفِ خودکار فقط آگاهی است و دوباره کم نمی‌شود
        MoneyRow(ir.vil3ntec.tohid.core.i18n.StrSale.itemsTotal, sale.total)
        SaleReceipt.included(sale, items, stamped, ir.vil3ntec.tohid.ui.PromoLive::ruleName)?.let { MoneyRow(it.label, it.amount) }
        MoneyRow(ir.vil3ntec.tohid.core.i18n.Str.discount, sale.discount)
        ir.vil3ntec.tohid.data.PayFlow.rounding(sale)?.let { MoneyRow(ir.vil3ntec.tohid.core.i18n.StrRounding.line, kotlin.math.abs(it), negative = it < 0) } // PAY-09
        MoneyRow(ir.vil3ntec.tohid.core.i18n.StrSale.finalAmount, sale.finalTotal, strong = true)
        MoneyRow(ir.vil3ntec.tohid.core.i18n.StrSale.paid, sale.paidAmount)
        MoneyRow(ir.vil3ntec.tohid.core.i18n.StrSale.remaining, sale.remaining, tint = if (sale.remaining > 0) Shop.colors.warning else null)
        //  PAY (v3 A4): ردیف‌های پرداختِ ترکیبی — فقط فاکتوری که `payments` دارد
        ir.vil3ntec.tohid.data.PayFlow.receiptLines(sale).forEach { (label, amount) -> MoneyRow(label, amount) }
        //  PRM-13: امتیازِ مصرف‌شده و کسب‌شدهٔ همین فاکتور — بی امتیاز ⇒ هیچ
        ir.vil3ntec.tohid.data.LoyaltyPoints.receiptLines(d, sale.id).forEach { (label, amount) ->
          if (amount != null) MoneyRow(label, amount) else Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.success)
        }
        if (debtor != null) {
          Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ir.vil3ntec.tohid.core.i18n.Str.debtor, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            Text(debtor.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          }
        }
        sale.note?.takeIf { it.isNotBlank() }?.let { Text("${ir.vil3ntec.tohid.core.i18n.StrSale.invoiceNote}: $it", style = MaterialTheme.typography.bodySmall, color = Shop.colors.text, modifier = Modifier.padding(vertical = 3.dp)) } // POS-11

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(ir.vil3ntec.tohid.core.i18n.Str.close) }
          //  گوشی: فاکتور به واتساپ/تلگرامِ مشتری — بی چاپگر هم (با پیوندِ آنلاین اگر باشد، POS-32)
          if (!ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
            OutlinedButton(
              onClick = { scope.launch { ir.vil3ntec.tohid.print.PrintJob.shareSale(context, d, sale)?.let(onMessage) } },
              modifier = Modifier.weight(1f),
            ) { Text(ir.vil3ntec.tohid.core.i18n.StrAccount.send) }
          } else {
            //  POS-32، کامپیوتر: برگهٔ اشتراکِ سیستم نیست؛ پیوندِ فاکتورِ آنلاین کپی می‌شود تا در واتس‌اپ/تلگرامِ وب بنشیند.
            //  فقط فروشِ نسخهٔ ۲ با توکن و پرچمِ `shop.receipt_v2` (`Receipts.publicUrl`)؛ وگرنه دکمه‌ای نیست
            val link = remember(sale.id, sale.receiptToken) {
              ir.vil3ntec.tohid.core.receipt.ReceiptShare.safeLink(ir.vil3ntec.tohid.print.Receipts.publicUrl(context, sale))
            }
            if (link != null) {
              val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
              OutlinedButton(
                onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(link)); onMessage(ir.vil3ntec.tohid.core.i18n.StrSalePages.linkCopied) },
                modifier = Modifier.weight(1f),
              ) { Text(ir.vil3ntec.tohid.core.i18n.StrSalePages.copyLink) }
            }
          }
          Button(onClick = { printer = true }, modifier = Modifier.weight(1f)) { Text(ir.vil3ntec.tohid.core.i18n.StrMoney.print) }
        }
        //  POS-32 (کامپیوتر: ذخیرهٔ تصویر/PDF ِ رسید) و POS-34 (پشتِ `GiftReceiptPrefs`، پیش‌فرض خاموش): رسیدِ هدیهٔ بی قیمت
        ir.vil3ntec.tohid.ui.components.pos.InvoiceSaveRow(d, sale, onMessage); ir.vil3ntec.tohid.ui.components.pos.GiftReceiptButton(d, sale, onMessage)
      }
    }
    }
  }

  //  HW-02: فاکتوری که پیش‌تر چاپ شده دوباره فقط با دلیل؛ چاپ از صفِ چاپ می‌رود و هرگز فروش نمی‌سازد
  var reprint by remember { mutableStateOf<Pair<String, Int>?>(null) }
  fun send(address: String, width: Int, reason: String?) {
    onMessage(ir.vil3ntec.tohid.core.i18n.StrSale.sendingToPrinter)
    scope.launch { onMessage(ir.vil3ntec.tohid.print.PrintSpooler.printSale(context, d, sale, address, width, reason)) }
  }
  if (printer) {
    PrinterDialog(
      onDismiss = { printer = false },
      onPrint = { address, width ->
        printer = false
        //  چاپ‌شده روی همین دستگاه، یا چاپِ دوباره‌ای که از دستگاهِ دیگری در سابقهٔ دکان آمده
        if (ir.vil3ntec.tohid.print.PrintLog.wasPrinted(context, sale.id) || d.auditLog.any { it.type == "reprint" && it.refId == sale.id }) reprint = address to width
        else send(address, width, null)
      },
    )
  }
  reprint?.let { (address, width) ->
    ReprintReasonDialog(onDismiss = { reprint = null }) { reason -> reprint = null; send(address, width, reason) }
  }
}

/** دلیلِ چاپِ دوباره — یکی از دلیل‌های رایج یا نوشتهٔ خودِ فروشنده */
@Composable
internal fun ReprintReasonDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  val reasons = listOf(ir.vil3ntec.tohid.core.i18n.StrSale.reprintJam, ir.vil3ntec.tohid.core.i18n.StrSale.reprintFaint, ir.vil3ntec.tohid.core.i18n.StrSale.reprintCopy)
  var picked by remember { mutableStateOf<String?>(null) }
  var other by remember { mutableStateOf("") }
  val reason = picked ?: other.trim()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(ir.vil3ntec.tohid.core.i18n.StrSale.reprintTitle) },
    text = {
      Column {
        Text(ir.vil3ntec.tohid.core.i18n.StrSale.reprintBody,
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        reasons.forEach { r ->
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = picked == r, onClick = { picked = r })
            Text(r, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          }
        }
        OutlinedTextField(
          value = other, onValueChange = { other = it.take(80); picked = null }, singleLine = true,
          label = { Text(ir.vil3ntec.tohid.core.i18n.StrOrderOps.delayReasonOther) }, modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = { Button(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) { Text(ir.vil3ntec.tohid.core.i18n.StrSale.reprint) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(ir.vil3ntec.tohid.core.i18n.Str.cancel) } },
  )
}

/* ============================ چاپگر ============================ */

/**
 *  انتخابِ چاپگر — با هر سه راهی که چاپگرِ دکان ممکن است وصل باشد.
 *
 *  ── چرا سه راه ────────────────────────────────────────────────────
 *  تا دیروز فقط بلوتوث بود. ولی چاپگرِ هر دکان یک‌جور وصل می‌شود:
 *
 *    • **بلوتوث** — چاپگرهای کوچکِ سیار
 *    • **وای‌فای** — چاپگرهای ۸۰ میلی‌متریِ روی پیشخان، با یک IP
 *    • **سیم** — چاپگر با کابلِ OTG
 *
 *  هیچ فهرستِ ساختگی ساخته نمی‌شود: بلوتوث فقط جفت‌شده‌ها را نشان
 *  می‌دهد و سیم فقط دستگاهی که واقعاً چاپگر است. اگر چیزی نبود، همین
 *  گفته می‌شود — نه اینکه دکمهٔ چاپ بی‌صدا کاری نکند.
 */
@Composable
internal fun PrinterDialog(
  onDismiss: () -> Unit,
  /** «چاپ» در فروش؛ «ثبتِ چاپگر» در صفحهٔ دستگاه‌ها */
  actionLabel: String = ir.vil3ntec.tohid.core.i18n.StrMoney.print,
  /** در فروش، چاپگرِ انتخاب‌شده پیش‌فرض می‌شود و به فهرستِ چاپگرها می‌رود */
  makeDefault: Boolean = true,
  onPrint: (String, Int) -> Unit,
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }

  //  چاپگرِ دفعهٔ قبل — تا فروشنده هر بار از نو انتخاب نکند
  val saved = remember { ThermalPrinter.Link.parse(prefs.getString("printer_address", null)) }

  var width by remember { mutableStateOf(prefs.getInt("printer_width", ThermalPrinter.WIDTH_58MM)) }

  //  همان راهی که دفعهٔ قبل کار می‌کرد، باز هم اول باز می‌شود
  var kind by remember {
    mutableStateOf(
      when (saved) {
        is ThermalPrinter.Link.Network -> "net"
        is ThermalPrinter.Link.Usb -> "usb"
        else -> "bt"
      }
    )
  }

  /* ---------------------------- بلوتوث ---------------------------- */

  var granted by remember {
    mutableStateOf(
      android.os.Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

  var paired by remember { mutableStateOf(emptyList<ThermalPrinter.Printer>()) }
  LaunchedEffect(granted, kind) {
    paired = if (granted && kind == "bt") ThermalPrinter.paired(context) else emptyList()
  }
  var btAddress by remember {
    mutableStateOf((saved as? ThermalPrinter.Link.Bluetooth)?.address)
  }

  /* ---------------------------- وای‌فای ---------------------------- */

  var host by remember {
    mutableStateOf((saved as? ThermalPrinter.Link.Network)?.host.orEmpty())
  }
  var port by remember {
    mutableStateOf(
      ((saved as? ThermalPrinter.Link.Network)?.port ?: ThermalPrinter.RAW_PORT).toString()
    )
  }

  /* ---------------------------- سیم ---------------------------- */

  var usbList by remember { mutableStateOf(emptyList<ThermalPrinter.Printer>()) }
  var usbName by remember { mutableStateOf((saved as? ThermalPrinter.Link.Usb)?.deviceName) }
  //  با هر بار باز شدنِ برگه دوباره نگاه می‌کنیم: کابل ممکن است همین
  //  حالا وصل شده باشد
  LaunchedEffect(kind) {
    if (kind == "usb") usbList = ThermalPrinter.usbPrinters(context)
  }

  /**
   *  اجازهٔ دسترسی به چاپگرِ سیمی.
   *
   *  اندروید این را با یک پنجرهٔ خودش می‌گیرد و جوابش به شکلِ یک
   *  Broadcast برمی‌گردد. بدونِ اجازه `openDevice` خالی برمی‌گردد و
   *  چاپ بی‌آنکه معلوم شود چرا، نمی‌شود.
   */
  fun askUsb(deviceName: String) {
    val manager = ThermalPrinter.usbManager(context) ?: return
    val device = ThermalPrinter.usbDevice(context, deviceName) ?: return
    if (manager.hasPermission(device)) return
    val intent = android.app.PendingIntent.getBroadcast(
      context, 0,
      android.content.Intent(USB_PERMISSION_ACTION).setPackage(context.packageName),
      android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
    )
    runCatching { manager.requestPermission(device, intent) }
  }

  /* ---------------------------- کدام چاپگر انتخاب شده ---------------------------- */

  val link: ThermalPrinter.Link? = when (kind) {
    "bt" -> btAddress?.let { ThermalPrinter.Link.Bluetooth(it) }
    "net" -> host.trim().takeIf { it.isNotBlank() }?.let {
      ThermalPrinter.Link.Network(it, port.trim().toIntOrNull() ?: ThermalPrinter.RAW_PORT)
    }
    else -> usbName?.let { ThermalPrinter.Link.Usb(it) }
  }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(ir.vil3ntec.tohid.core.i18n.StrSale.printInvoice, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))

        Text(ir.vil3ntec.tohid.core.i18n.StrSale.paperWidth, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf(ir.vil3ntec.tohid.core.i18n.StrSalePages.paper58 to ThermalPrinter.WIDTH_58MM, ir.vil3ntec.tohid.core.i18n.StrSalePages.paper80 to ThermalPrinter.WIDTH_80MM),
          selected = width,
          onSelect = { width = it; prefs.edit().putInt("printer_width", it).apply() },
        )

        Spacer(Modifier.height(16.dp))
        Text(ir.vil3ntec.tohid.core.i18n.StrSale.connectedBy, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf(ir.vil3ntec.tohid.core.i18n.StrSale.kindBluetooth to "bt", ir.vil3ntec.tohid.core.i18n.StrSale.kindWifi to "net", (if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) ir.vil3ntec.tohid.core.i18n.StrSale.kindSystem else ir.vil3ntec.tohid.core.i18n.StrSale.kindCable) to "usb"),
          selected = kind,
          onSelect = { kind = it },
        )

        Spacer(Modifier.height(16.dp))

        when (kind) {
          /* ---------------- بلوتوث ---------------- */
          "bt" -> {
            if (!granted) {
              Text(
                ir.vil3ntec.tohid.core.i18n.StrSale.btPermission,
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
              Spacer(Modifier.height(8.dp))
              Button(onClick = { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text(ir.vil3ntec.tohid.core.i18n.StrSale.allow) }
            } else if (paired.isEmpty()) {
              Text(
                ir.vil3ntec.tohid.core.i18n.StrSale.btEmpty,
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
            } else {
              paired.forEach { p ->
                PrinterRow(
                  title = p.name,
                  subtitle = p.address,
                  selected = btAddress == p.address,
                  onSelect = { btAddress = p.address },
                )
              }
            }
          }

          /* ---------------- وای‌فای ---------------- */
          "net" -> {
            Text(
              ir.vil3ntec.tohid.core.i18n.StrSale.netNote,
              style = MaterialTheme.typography.bodySmall,
              color = Shop.colors.muted,
            )
            LocalNetworkNotice(context)  // HW-13: اندروید ۱۷ و هدفِ ۳۷ ⇒ «اجازه دادن»؛ وگرنه همان فاصلهٔ ۸
            OutlinedTextField(
              value = host,
              onValueChange = { host = it },
              label = { Text(ir.vil3ntec.tohid.core.i18n.StrSale.ipAddress) },
              placeholder = { Text("192.168.1.50") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = port,
              onValueChange = { port = ir.vil3ntec.tohid.normalizeDigits(it).filter { c -> c in '0'..'9' }.take(5) },
              label = { Text(ir.vil3ntec.tohid.core.i18n.StrSale.port) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
              ir.vil3ntec.tohid.core.i18n.StrSale.portHint,
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
          }

          /* ---------------- سیم ---------------- */
          else -> {
            if (usbList.isEmpty()) {
              Text(
                ir.vil3ntec.tohid.core.i18n.StrSale.usbEmpty,
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
            } else {
              usbList.forEach { p ->
                PrinterRow(
                  title = p.name,
                  subtitle = p.address.substringAfterLast('/'),
                  selected = usbName == p.address,
                  onSelect = { usbName = p.address; askUsb(p.address) },
                )
              }
              Spacer(Modifier.height(6.dp))
              Text(
                ir.vil3ntec.tohid.core.i18n.StrSale.usbHint,
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.muted2,
              )
            }
          }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(ir.vil3ntec.tohid.core.i18n.Str.cancel) }
          Button(
            enabled = link != null,
            onClick = {
              val chosen = link ?: return@Button
              if (makeDefault) {
                prefs.edit().putString("printer_address", chosen.save()).apply()
                ir.vil3ntec.tohid.print.PrinterRegistry.add(context, "", chosen.save(), width)
              }
              onPrint(chosen.save(), width)
            },
            modifier = Modifier.weight(1f),
          ) { Text(actionLabel) }
        }
      }
    }
    }
  }
}

/** نامِ کارِ اجازهٔ USB — فقط داخلِ همین برنامه معنا دارد */
private const val USB_PERMISSION_ACTION = "ir.vil3ntec.tohid.USB_PERMISSION"

/** یک ردیف از فهرستِ چاپگرها — بلوتوث و سیم هر دو همین شکل را دارند */
@Composable
private fun PrinterRow(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.sm))
      .background(if (selected) Shop.colors.primaryTint else Color.Transparent)
      .clickable { onSelect() }
      .padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onSelect)
    Spacer(Modifier.width(6.dp))
    Column {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
      Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
  }
}

/* ============================ انتخاب‌ها ============================ */

/** انتخابِ قرض‌دار برای فروشِ نسیه */
@Composable
fun DebtorPicker(
  d: ShopData,
  selected: String?,
  onClose: () -> Unit,
  onPick: (String?) -> Unit,
  /** POS-31 — کامپیوترِ لمسی (`PosTouchPrefs.extras()`): کلیدِ «حروف» و صفحه‌کلید برای نام؛ [onAdd] «مشتریِ تازه» (`TillNewCustomer`) */
  touch: Boolean = false, onAdd: (suspend (name: String) -> String?)? = null,
) {
  var search by remember { mutableStateOf("") }
  val kb = ir.vil3ntec.tohid.ui.components.pos.rememberTouchKeyboard(); val keys = remember(kb, touch) { ir.vil3ntec.tohid.ui.components.pos.TouchTextKeys(kb, touch) } // §۵.۳: فوکوسِ نام ⇒ صفحه‌کلید
  val shown = ir.vil3ntec.tohid.data.CustomerCrm.active(d).filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }

  Dialog(onDismissRequest = onClose) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
        Text(ir.vil3ntec.tohid.core.i18n.StrSalePages.chooseDebtor, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text(ir.vil3ntec.tohid.core.i18n.StrSale.debtorNameHint) },
            singleLine = true, modifier = Modifier.weight(1f).touchKeysOnFocus(keys))
          ir.vil3ntec.tohid.ui.components.pos.TouchKeysToggle(kb, touch, 48.dp) {}
        }
        ir.vil3ntec.tohid.ui.components.pos.TouchKeysPanel(kb, touch, 48.dp) { search = ir.vil3ntec.tohid.core.pos.TouchKeyboard.apply(search, it) }
        TillNewCustomerButton(search, shown, onAdd)
        Spacer(Modifier.height(10.dp))
        if (shown.isEmpty()) {
          EmptyNote(if (d.debtors.isEmpty()) ir.vil3ntec.tohid.core.i18n.StrSale.noDebtorsYet else ir.vil3ntec.tohid.core.i18n.StrSale.debtorNotFound)
        } else {
          LazyColumn(Modifier.weight(1f, fill = false)) {
            items(shown, key = { it.id }) { debtor ->
              Row(
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(Radius.sm))
                  .background(if (selected == debtor.id) Shop.colors.primaryTint else Color.Transparent)
                  .clickable { onPick(debtor.id) }
                  .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(Modifier.weight(1f)) {
                  Text(debtor.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
                  Text(debtStateText(d, debtor.id), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
                }
              }
            }
          }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(ir.vil3ntec.tohid.core.i18n.Str.close) }
          if (selected != null) {
            Button(onClick = { onPick(null) }, modifier = Modifier.weight(1f)) { Text(ir.vil3ntec.tohid.core.i18n.StrSale.noDebtor) }
          }
        }
      }
    }
  }
}

/**
 *  تعداد و قیمتِ یک ردیفِ سبد — با تایپ، نه با زدنِ پیاپیِ «+».
 *
 *  دو چیز را با هم حل می‌کند: فروشِ عمده که با دکمهٔ «+» بیست‌وپنج بار
 *  زدن می‌خواست، و چانه‌زنی که تا دیروز فقط با دستکاریِ تخفیفِ کلِ فاکتور
 *  ممکن بود — و آن، سودِ همان کالا را در گزارش غلط می‌کرد.
 */
@Composable
fun CartLineDialog(
  name: String,
  unit: String,
  quantity: Double,
  unitPrice: Double,
  basePrice: Double,
  custom: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (quantity: Double, price: Double?) -> Unit,
  /** واحدِ فروشِ دومِ کالا و ضریبش — شرح سرِ `Product.saleUnit` */
  saleUnit: String = "",
  unitFactor: Double = 1.0,
  /** قلمِ آزاد — سقفِ قیمتش (POS-10) به‌جای «قاعدهٔ جهت» */
  free: Boolean = false,
  /** POS-08/10/20 — سیاستِ قیمتِ ردیف (`LinePolicyPrefs.of`)؛ پیش‌فرض خاموش ⇒ همان کادرِ پیشین، بی تخفیفِ ردیف و بی سنجش */
  linePolicy: ir.vil3ntec.tohid.core.pos.LinePricing.Policy = ir.vil3ntec.tohid.core.pos.LinePricing.Policy(),
  /** POS-22 — گام‌های «چرا این قیمت؟» (`PriceWhyLine`)؛ `null` (تنظیمِ خاموش) ⇒ همان کادرِ پیشین */
  why: List<ir.vil3ntec.tohid.core.pos.PriceWhy.Row>? = null,
  /** POS-11 — یادداشتِ ردیف (`posLineNote`)؛ `null` (تنظیمِ خاموش) ⇒ همان کادرِ پیشین */
  note: ir.vil3ntec.tohid.ui.components.pos.PosLineNote? = null,
  serials: ir.vil3ntec.tohid.ui.components.pos.PosLineSerials? = null, // POS-28 — کالای سریال‌دار؛ `null` ⇒ همان کادرِ پیشین
  /** S05 — واحدهای کالا با قیمتِ لیست و «بستهٔ کامل» (`TillGoodsPref.plan`)؛ `null` (خاموش) ⇒ همان کادرِ پیشین */
  units: ir.vil3ntec.tohid.data.UnitLine.Plan? = null,
  /** S45 — «درخواستِ تأیید» برای ردِ قاعدهٔ جهت یا سقفِ قلمِ آزاد (`PriceApproval`)؛ `null` ⇒ همان پیامِ رد */
  priceAsk: ir.vil3ntec.tohid.data.PriceAsk? = null,
  /** POS-41 — F5 («تخفیفِ ردیف»): فوکوس روی کادرِ تخفیفِ ردیف وقتی سیاست آن را نشان می‌دهد */
  focusDiscount: Boolean = false, pinned: Boolean? = null, onPin: () -> Unit = {}, // POS-06 — سنجاقِ کاشیِ همین صندوق (`TillPinsPrefs`)؛ `null` ⇒ بی دکمه
) {
  val policy = linePolicy
  var grant by remember { mutableStateOf<ir.vil3ntec.tohid.core.pos.PriceApproval.Grant?>(null) }
  var asking by remember { mutableStateOf(false) }
  var noteText by remember { mutableStateOf(note?.value.orEmpty()) }; var serialText by remember { mutableStateOf(serials?.text.orEmpty()) }
  var qtyText by remember { mutableStateOf(qty(units?.startCount ?: quantity)) }
  var priceText by remember { mutableStateOf(qty(unitPrice)) }
  var manual by remember { mutableStateOf(custom && units?.packPriced != true) }
  var unitAt by remember { mutableStateOf(units?.startAt ?: 0) }
  val unitNow = units?.units?.getOrNull(unitAt)
  var discountText by remember { mutableStateOf("") }
  var discountPercent by remember { mutableStateOf(true) }
  //  مقدار به کدام واحد تایپ می‌شود؛ موجودی و قیمت همیشه به `unit` می‌مانند
  var inSaleUnit by remember { mutableStateOf(false) }
  val hasSaleUnit = saleUnit.isNotBlank() && unitFactor > 0

  val typedQty = qtyText.toDoubleOrNull()
  val parsedQty = if (unitNow != null) ir.vil3ntec.tohid.core.product.UnitSale.toBase(typedQty, unitNow, units?.wholeOnly == true)
    else typedQty?.let { if (inSaleUnit) ir.vil3ntec.tohid.core.product.UnitSale.typedToBase(it, unitFactor) else it } // PRD-07: همان هزارمِ هسته و سرور
  val partBad = unitNow != null && typedQty != null && ir.vil3ntec.tohid.core.product.UnitSale.partRejected(typedQty, unitNow, units?.wholeOnly == true)
  val listPrice = unitNow?.let(ir.vil3ntec.tohid.core.product.UnitSale::lineUnitPrice) // قیمتِ هر واحدِ پایه از قیمتِ بسته
  val parsedPrice = priceText.toDoubleOrNull()
  //  POS-08/10/20: قاعدهٔ جهت، سقفِ قلمِ آزاد و تخفیفِ ردیف؛ خاموش ⇒ بی رد و بی تخفیف. S45: تأییدِ گرفته‌شده فقط همان قاعده را
  //  برای همان قیمت برمی‌دارد — `LinePolicy.checked`، همان برگهٔ ردیفِ گوشیِ نسخهٔ ۳ (⛔ پیش‌تر کدِ رد جای متنِ رد بود ⇒ دکمه هرگز)
  val gate = ir.vil3ntec.tohid.data.LinePolicy.checked(
    policy, free, basePrice, unitPrice, if (manual) parsedPrice else null, parsedQty, discountPercent, discountText, grant, canAsk = priceAsk != null, pack = unitNow,
  ) // S45 (#۲۱): با بستهٔ قیمت‌دارِ انتخاب‌شده قاعده با قیمتِ همان بسته (`LinePricing.ruleListMinor`)
  val firstEdit = gate.first; val edit = gate.edit; val granted = gate.granted; val askCode = gate.askCode
  val typedMinor = if (manual) parsedPrice?.let(DiscountGate::minor) else null
  val ok = parsedQty != null && parsedQty > 0 && (!manual || (parsedPrice != null && parsedPrice >= 0)) && edit.ok

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(Radius.lg),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(18.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))
        if (why != null) {
          ir.vil3ntec.tohid.ui.components.pos.PriceWhyPanel(why) { money(it) }
          Spacer(Modifier.height(12.dp))
        }

        if (units != null) {
          SegmentedChoice(
            options = units.units.mapIndexed { i, u -> i.toString() to ir.vil3ntec.tohid.data.UnitLine.label(u, unit) },
            selected = unitAt.toString(),
            onSelect = { c ->
              val j = c.toIntOrNull() ?: 0
              //  همان مقدار، به واحدِ تازه
              if (j != unitAt) typedQty?.let { v -> qtyText = qty(ir.vil3ntec.tohid.core.product.UnitSale.fromBase(v * units.units[unitAt].sizeMilli / 1000.0, units.units[j])) }
              unitAt = j
            },
          )
          Spacer(Modifier.height(8.dp))
        } else if (hasSaleUnit) {
          SegmentedChoice(
            options = listOf("stock" to unit.ifBlank { ir.vil3ntec.tohid.core.i18n.StrGrid.unit }, "sale" to saleUnit),
            selected = if (inSaleUnit) "sale" else "stock",
            onSelect = { choice ->
              val wantSale = choice == "sale"
              if (wantSale != inSaleUnit) {
                //  همان مقدار، به واحدِ تازه — نه پاک شدنِ کادر
                typedQty?.let { v ->
                  qtyText = qty(if (wantSale) v / unitFactor else ir.vil3ntec.tohid.core.product.UnitSale.typedToBase(v, unitFactor))
                }
                inSaleUnit = wantSale
              }
            },
          )
          Spacer(Modifier.height(8.dp))
        }
        NumberField(
          value = qtyText,
          onValueChange = { if (!ir.vil3ntec.tohid.data.QtyExactWiring.tooPrecise(it)) qtyText = it }, // PRD-08: حداکثر سه رقمِ اعشار، مثلِ صفحه‌کلیدِ گوشی
          label = (unitNow?.name ?: if (inSaleUnit) saleUnit else unit).let { u -> if (u.isNotBlank()) ir.vil3ntec.tohid.core.i18n.StrSalePages.qtyIn(u) else ir.vil3ntec.tohid.core.i18n.StrSale.qty },
          modifier = Modifier.fillMaxWidth(),
        )
        ir.vil3ntec.tohid.ui.components.pos.ScaleWeighButton(unit) { qtyText = qty(it); inSaleUnit = false; unitAt = 0 }
        if (unitNow != null && (unitAt > 0 || partBad)) {
          Spacer(Modifier.height(4.dp))
          Text(
            if (partBad) ir.vil3ntec.tohid.core.product.UnitSale.partMessage(unitNow.name)
            else "= ${qty(parsedQty ?: 0.0)} $unit" + (unitNow.priceMinor?.let { ir.vil3ntec.tohid.core.i18n.StrSalePages.listPriceTail(unitNow.name, money(it / 100.0)) } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = if (partBad) Shop.colors.danger else Shop.colors.muted,
          )
        } else if (inSaleUnit && parsedQty != null) {
          Spacer(Modifier.height(4.dp))
          Text(
            "= ${qty(parsedQty)} $unit",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = manual,
            onCheckedChange = {
              manual = it
              if (!it) priceText = qty(basePrice)
            },
          )
          Spacer(Modifier.width(4.dp))
          Text(
            ir.vil3ntec.tohid.core.i18n.StrSalePages.manualPrice,
            style = MaterialTheme.typography.bodyMedium,
            color = Shop.colors.text,
          )
        }

        if (manual) {
          Spacer(Modifier.height(6.dp))
          AmountField(
            value = priceText,
            onValueChange = { priceText = it },
            label = ir.vil3ntec.tohid.core.i18n.StrSale.unitPrice,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            ir.vil3ntec.tohid.core.i18n.StrSalePages.ownPriceNote(money(basePrice)),
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
        }

        ir.vil3ntec.tohid.ui.components.pos.LineDiscountPanel(
          show = edit.canDiscount, percent = discountPercent, text = discountText, problem = edit.problem,
          onPercent = { discountPercent = it }, onText = { discountText = it },
          focus = if (focusDiscount) remember { androidx.compose.ui.focus.FocusRequester() } else null,
        )
        if (askCode != null) TextButton(onClick = { asking = true }) { Text(ir.vil3ntec.tohid.core.i18n.StrTill.askApproval) }
        granted?.let { Text(ir.vil3ntec.tohid.core.i18n.StrTill.approvedBy(it.by), style = MaterialTheme.typography.labelSmall, color = Shop.colors.success) }

        Spacer(Modifier.height(14.dp))
        val shownPrice = if (manual) (parsedPrice ?: 0.0) else listPrice ?: basePrice
        Text(
          ir.vil3ntec.tohid.core.i18n.StrSalePages.lineTotal(money(ir.vil3ntec.tohid.data.QtyExactWiring.preview(shownPrice, parsedQty ?: 0.0))), // PRD-08: همان مبلغِ ثبت
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.text,
        )
        edit.net?.let { Text(ir.vil3ntec.tohid.core.i18n.StrLine.afterDiscount(money(it)), style = MaterialTheme.typography.titleSmall, color = Shop.colors.success) }
        if (note != null) ir.vil3ntec.tohid.ui.components.pos.PosNoteField(noteText, { noteText = it }, ir.vil3ntec.tohid.core.i18n.StrSalePages.lineNote); if (serials != null) ir.vil3ntec.tohid.ui.components.pos.PosSerialField(serialText, { serialText = it }, parsedQty); if (pinned != null) TextButton(onClick = onPin) { Text(if (pinned) ir.vil3ntec.tohid.core.i18n.StrSale.unpinFirst else ir.vil3ntec.tohid.core.i18n.StrSale.pinFirst) } // POS-06

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
          TextButton(onClick = onDismiss) { Text(ir.vil3ntec.tohid.core.i18n.Str.cancel) }
          Button(
            enabled = ok,
            //  تخفیفِ ردیف ⇒ قیمتِ واحدِ پس از تخفیف؛ وگرنه همان قیمتِ دستیِ پیشین
            onClick = { onConfirm(parsedQty ?: 0.0, edit.netUnit ?: if (manual) parsedPrice else listPrice); note?.save?.invoke(noteText); serials?.save(serialText); if (firstEdit.problem != null) granted?.let { priceAsk?.onGranted?.invoke(it) } },
          ) { Text(ir.vil3ntec.tohid.core.i18n.StrSalePages.record) }
        }
      }
    }
  }
  val ask = priceAsk
  if (asking && ask != null && askCode != null && typedMinor != null) {
    ir.vil3ntec.tohid.ui.components.staff.PinApprovalDialog(
      title = ir.vil3ntec.tohid.core.i18n.StrTill.priceTitle,
      summary = ir.vil3ntec.tohid.core.i18n.StrTill.priceSummary(name, money(ir.vil3ntec.tohid.data.LinePolicy.listMinor(basePrice, unitNow) / 100.0), money(typedMinor / 100.0)),
      selfRole = ask.selfRole, selfName = ask.selfName, requesterUserId = ask.requesterUserId,
      onApproved = { by, role, reason ->
        grant = ir.vil3ntec.tohid.core.pos.PriceApproval.Grant(askCode, by, role, reason, ask.selfName, ir.vil3ntec.tohid.data.LinePolicy.listMinor(basePrice, unitNow), typedMinor)
        asking = false
      },
      onDismiss = { asking = false },
    )
  }
}

/**
 *  قلمِ آزاد — چیزی که در فهرستِ کالاها نیست.
 *
 *  کیسه، یک جنسِ تک، خدمتی که کالا نیست. تا دیروز راهی نبود جز ساختنِ
 *  یک کالای واقعی در فهرست، و نتیجه‌اش فهرستِ شلوغی از «متفرقه ۱،
 *  متفرقه ۲» بود که خودش گزارش‌ها را کند می‌کرد.
 */
@Composable
fun FreeLineDialog(
  onDismiss: () -> Unit,
  onConfirm: (label: String, price: Double, quantity: Double) -> Unit,
  /** POS-10 — سیاستِ قیمتِ ردیف (`LinePolicyPrefs.of`)؛ پیش‌فرض خاموش ⇒ بی سقف */
  linePolicy: ir.vil3ntec.tohid.core.pos.LinePricing.Policy = ir.vil3ntec.tohid.core.pos.LinePricing.Policy(),
) {
  var label by remember { mutableStateOf("") }
  var priceText by remember { mutableStateOf("") }
  var qtyText by remember { mutableStateOf("1") }

  val parsedPrice = priceText.toDoubleOrNull()
  val parsedQty = qtyText.toDoubleOrNull()
  //  POS-10 — سقفِ قیمتِ قلمِ آزاد برای همه جز صاحبِ دکان (خاموش ⇒ بی سقف)
  val policy = linePolicy
  val problem = ir.vil3ntec.tohid.data.LinePolicy.message(
    parsedPrice?.let { ir.vil3ntec.tohid.core.pos.LinePricing.checkFreeLine(policy, DiscountGate.minor(it)) }, policy,
  )
  val ok = label.isNotBlank() && parsedPrice != null && parsedPrice > 0 &&
    parsedQty != null && parsedQty > 0 && problem == null

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(Radius.lg),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(18.dp)) {
        Text(ir.vil3ntec.tohid.core.i18n.StrSale.freeItem, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(4.dp))
        Text(
          ir.vil3ntec.tohid.core.i18n.StrSale.freeItemHint,
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(14.dp))

        val keys = ir.vil3ntec.tohid.ui.components.pos.rememberTouchTextKeys(); OutlinedTextField( // POS-31: لمسی ⇒ حروف با فوکوس
          value = label,
          onValueChange = { label = it },
          label = { Text(ir.vil3ntec.tohid.core.i18n.StrGrid.name) },
          placeholder = { Text(ir.vil3ntec.tohid.core.i18n.StrSale.freeNameHint) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().touchKeysOnFocus(keys),
        )
        ir.vil3ntec.tohid.ui.components.pos.TouchTextKeysPanel(keys, label, { label = it }); Spacer(Modifier.height(12.dp))
        AmountField(value = priceText, onValueChange = { priceText = it }, label = ir.vil3ntec.tohid.core.i18n.StrSale.unitPrice)
        Spacer(Modifier.height(12.dp))
        NumberField(
          value = qtyText,
          onValueChange = { qtyText = it },
          label = ir.vil3ntec.tohid.core.i18n.StrSale.qty,
          modifier = Modifier.fillMaxWidth(),
        )

        problem?.let {
          Spacer(Modifier.height(8.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
        }
        Spacer(Modifier.height(14.dp))
        Text(
          ir.vil3ntec.tohid.core.i18n.StrSalePages.total(money(ir.vil3ntec.tohid.data.QtyExactWiring.preview(parsedPrice ?: 0.0, parsedQty ?: 0.0))),
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.text,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
          TextButton(onClick = onDismiss) { Text(ir.vil3ntec.tohid.core.i18n.Str.cancel) }
          Button(
            enabled = ok,
            onClick = { onConfirm(label.trim(), parsedPrice ?: 0.0, parsedQty ?: 1.0) },
          ) { Text(ir.vil3ntec.tohid.core.i18n.StrFields.add) }
        }
      }
    }
  }
}
