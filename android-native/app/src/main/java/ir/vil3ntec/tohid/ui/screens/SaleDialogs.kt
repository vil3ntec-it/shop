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
  /** POS-12 — تخفیفی که با فاکتورِ نگه‌داشته آمد (درصد، همان کادرِ کامپیوتر) */
  presetDiscount: String = "",
  /** شناسهٔ فاکتورِ همین سبد (`CartStore.saleKey`) — برگهٔ تأیید به آن بسته است */
  saleKey: String? = null,
) {
  val context = LocalContext.current
  //  «نیازمند تأیید» هم کادرِ تخفیف را نشان می‌دهد؛ فقط «ممنوع» پنهانش می‌کند (STF-02)
  val canDiscount = remember { ir.vil3ntec.tohid.data.ShopRole.mayDiscount(context) }
  // مثل نسخهٔ وب: اگر از سبد قرض‌داری انتخاب شده باشد، پنجره روی «نسیه»
  // باز می‌شود؛ وگرنه «نقدی». تخفیف هم پیش‌فرض درصدی است.
  val preset = presetDebtorId?.takeIf { id -> d.debtors.any { it.id == id } }

  var discountType by remember { mutableStateOf(SalesEngine.DiscountType.PERCENT) }
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
    seller = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") },
  )
  val totals = SalesEngine.totals(d, cart, checkout)

  //  S08: تخفیفِ بیش از سقف ⇒ «درخواستِ تأیید» با PINِ مدیر — همان تصمیمِ سرور
  var approval by remember { mutableStateOf<ApprovalSlip.Slip?>(null) }
  var askApproval by remember { mutableStateOf(false) }
  //  POS-21 (پشتِ `LinePolicy.on`، پیش‌فرض خاموش): قیمتِ دستیِ زیرِ فهرست و تخفیفِ ردیف هم تخفیف‌اند
  val lineGate = remember { ir.vil3ntec.tohid.data.LinePolicyPrefs.on(context) }
  val basis = DiscountGate.basis(lineGate, d, cart, promo, totals)
  val decision = ir.vil3ntec.tohid.data.ShopRole.decide(
    context, "sales.discount", DiscountGate.req(basis.discount, basis.subtotal),
  )
  val gate = DiscountGate.state(decision.decision, saleKey, basis.discount, basis.subtotal, approval)

  // در فروشِ نقدی مبلغِ پرداختی همیشه کلِ مبلغِ نهایی است
  val paid = if (payment == SalesEngine.Payment.CASH) totals.finalTotal
  else minOf(parseNumber(paidText)?.coerceAtLeast(0.0) ?: 0.0, totals.finalTotal)
  val remaining = (totals.finalTotal - paid).coerceAtLeast(0.0)
  val discountBad = canDiscount && numberInvalid(discountText)
  //  PAY (v3 A4): پرداختِ ترکیبی — فقط با پرچم؛ خاموش ⇒ همان نقد/نسیهٔ امروز
  val payV2 = remember { ir.vil3ntec.tohid.data.ShopPolicy.payV2(context) }
  val payState = remember { ir.vil3ntec.tohid.ui.components.pos.PayPanelState() }
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
      is ir.vil3ntec.tohid.data.PayFlow.Outcome.Ok -> onConfirm(checkout.copy(settlement = r.value, debtorId = debtorId, approval = signed))
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
        Text("تسویه فروش", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(14.dp))
        //  POS-21: کادرِ تخفیف پنهان است ولی قیمتِ ردیفی زیرِ فهرست مانده ⇒ چرا «ثبت» خاموش است
        if (!canDiscount && gate == DiscountGate.State.DENIED) {
          StatusPill(ir.vil3ntec.tohid.core.i18n.StrLine.reductionDenied, PillTone.DANGER)
          Spacer(Modifier.height(14.dp))
        }

        //  تخفیف فقط با صاحب و مدیر — `ShopRole.canDiscount`
        if (canDiscount) {
        Text("نوع تخفیف", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf("درصدی" to SalesEngine.DiscountType.PERCENT, "مبلغی" to SalesEngine.DiscountType.AMOUNT),
          selected = discountType,
          onSelect = { discountType = it },
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = discountText,
          onValueChange = { discountText = numericInput(it) },
          label = { Text(if (discountType == SalesEngine.DiscountType.PERCENT) "تخفیف (٪)" else "تخفیف (افغانی)") },
          singleLine = true,
          isError = discountBad,
          supportingText = if (discountBad) { { Text("عدد درست نیست") } } else null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )

        when (gate) {
          DiscountGate.State.NEEDS_APPROVAL -> {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatusPill("نیازمند تأیید", PillTone.AWAITING)
              Text(
                if (decision.cap == "maxPct") "بیش از سقفِ ${qty(decision.limit ?: 0.0)}٪ِ شما"
                else "بیش از اختیارِ شما",
                style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, modifier = Modifier.weight(1f),
              )
              OutlinedButton(onClick = { askApproval = true }, enabled = saleKey != null && !busy) { Text("درخواستِ تأیید") }
            }
          }
          DiscountGate.State.APPROVED -> {
            Spacer(Modifier.height(6.dp))
            StatusPill("تأیید شد", PillTone.SUCCESS)
          }
          DiscountGate.State.DENIED -> {
            Spacer(Modifier.height(6.dp))
            StatusPill("این تخفیف برای شما ممنوع است", PillTone.DANGER)
          }
          DiscountGate.State.FREE -> Unit
        }

        Spacer(Modifier.height(14.dp))
        }
        if (!payV2) {
        Text("نوع پرداخت", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf("نقدی" to SalesEngine.Payment.CASH, "نسیه" to SalesEngine.Payment.CREDIT),
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
          if (payV2) "مشتری — باقی‌مانده به حسابش می‌رود" else if (payment == SalesEngine.Payment.CREDIT) "قرض‌دار" else "مشتری (اختیاری)",
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
                ?: if (d.debtors.isEmpty()) "— هنوز کسی ثبت نشده —" else "انتخاب",
            )
          }
          if (debtorId != null) {
            TextButton(onClick = { debtorId = null }) { Text("برداشتن") }
          }
        }
        if (debtorMenu) {
          SearchablePicker(
            title = if (payment == SalesEngine.Payment.CREDIT) "انتخاب قرض‌دار" else "انتخاب مشتری",
            options = d.debtors,
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
          )
          payError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
        }

        if (!payV2 && payment == SalesEngine.Payment.CREDIT) {
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = paidText,
            onValueChange = { paidText = numericInput(it) },
            label = { Text("مبلغ پرداختی") },
            singleLine = true,
            isError = paidBad,
            supportingText = if (paidBad) { { Text("عدد درست نیست") } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Spacer(Modifier.height(16.dp))
        Panel {
          //  S3: مثلِ خلاصهٔ صندوق — جمع پیش از تخفیف‌های خودکار، ردیفشان با نام، بعد تخفیفِ دستی
          if (promo != null && promo.stamp && promo.discount > 0) {
            MoneyRow("جمع اقلام", promo.gross)
            MoneyRow(SaleReceipt.promoLabel(promo.applied.map { it.name }), promo.discount, negative = true)
          } else MoneyRow("جمع اقلام", totals.subtotal)
          MoneyRow("تخفیف", totals.discount)
          HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Shop.colors.border)
          MoneyRow("مبلغ نهایی", totals.finalTotal, strong = true)
          if (!payV2) {
          MoneyRow("مبلغ پرداختی", paid)
          MoneyRow("باقی‌مانده", remaining, tint = if (remaining > 0) Shop.colors.warning else null)
          }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = { confirm() },
            enabled = !busy && !discountBad && !paidBad &&
              gate != DiscountGate.State.NEEDS_APPROVAL && gate != DiscountGate.State.DENIED,
            modifier = Modifier.weight(1f),
          ) { Text(if (busy) "در حالِ ثبت…" else "ثبت فروش") }
        }
      }
    }
    }
  }

  if (askApproval && saleKey != null) {
    ApprovalDialog(
      title = "تأییدِ تخفیف",
      summary = "تخفیفِ ${money(basis.discount)} ؋ روی فاکتورِ ${money(basis.subtotal)} ؋",
      shopId = ir.vil3ntec.tohid.data.ShopRole.shopId(context),
      requesterUserId = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") },
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
          "فاکتور فروش #${sale.invoiceLabel() ?: "—"}",
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "${formatDate(sale.date)} — ${ir.vil3ntec.tohid.data.PayFlow.payLabel(sale, cash = "نقدی")}",
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
            }
            Text(money(item.totalPrice), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Shop.colors.border)
        Spacer(Modifier.height(10.dp))

        //  «جمع اقلام» = جمعِ همان ستون؛ تخفیفِ خودکار فقط آگاهی است و دوباره کم نمی‌شود
        MoneyRow("جمع اقلام", sale.total)
        SaleReceipt.included(sale, items, stamped, ir.vil3ntec.tohid.ui.PromoLive::ruleName)?.let { MoneyRow(it.label, it.amount) }
        MoneyRow("تخفیف", sale.discount)
        MoneyRow("مبلغ نهایی", sale.finalTotal, strong = true)
        MoneyRow("پرداختی", sale.paidAmount)
        MoneyRow("باقی‌مانده", sale.remaining, tint = if (sale.remaining > 0) Shop.colors.warning else null)
        //  PAY (v3 A4): ردیف‌های پرداختِ ترکیبی — فقط فاکتوری که `payments` دارد
        ir.vil3ntec.tohid.data.PayFlow.receiptLines(sale).forEach { (label, amount) -> MoneyRow(label, amount) }
        if (debtor != null) {
          Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("قرض‌دار", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            Text(debtor.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("بستن") }
          //  گوشی: فاکتور به واتساپ/تلگرامِ مشتری — بی چاپگر هم (با پیوندِ آنلاین اگر باشد، POS-32)
          if (!ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
            OutlinedButton(
              onClick = { scope.launch { ir.vil3ntec.tohid.print.PrintJob.shareSale(context, d, sale)?.let(onMessage) } },
              modifier = Modifier.weight(1f),
            ) { Text("فرستادن") }
          } else {
            //  POS-32، کامپیوتر: برگهٔ اشتراکِ سیستم نیست؛ پیوندِ فاکتورِ آنلاین کپی می‌شود تا در واتس‌اپ/تلگرامِ وب بنشیند.
            //  فقط فروشِ نسخهٔ ۲ با توکن و پرچمِ `shop.receipt_v2` (`Receipts.publicUrl`)؛ وگرنه دکمه‌ای نیست
            val link = remember(sale.id, sale.receiptToken) {
              ir.vil3ntec.tohid.core.receipt.ReceiptShare.safeLink(ir.vil3ntec.tohid.print.Receipts.publicUrl(context, sale))
            }
            if (link != null) {
              val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
              OutlinedButton(
                onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(link)); onMessage("پیوندِ فاکتورِ آنلاین کپی شد") },
                modifier = Modifier.weight(1f),
              ) { Text("کپیِ پیوند") }
            }
          }
          Button(onClick = { printer = true }, modifier = Modifier.weight(1f)) { Text("چاپ") }
        }
      }
    }
    }
  }

  //  HW-02: فاکتوری که پیش‌تر چاپ شده دوباره فقط با دلیل؛ چاپ از صفِ چاپ می‌رود و هرگز فروش نمی‌سازد
  var reprint by remember { mutableStateOf<Pair<String, Int>?>(null) }
  fun send(address: String, width: Int, reason: String?) {
    onMessage("در حال فرستادن به چاپگر…")
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
  val reasons = listOf("کاغذ گیر کرد یا بریده شد", "چاپ ناخوانا بود", "مشتری نسخهٔ دوم خواست")
  var picked by remember { mutableStateOf<String?>(null) }
  var other by remember { mutableStateOf("") }
  val reason = picked ?: other.trim()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("این فاکتور پیش‌تر چاپ شده") },
    text = {
      Column {
        Text("برای چاپِ دوباره دلیل را بگویید. روی کاغذ «چاپِ دوباره» می‌آید و فروشِ تازه‌ای ساخته نمی‌شود.",
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
          label = { Text("دلیلِ دیگر") }, modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = { Button(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) { Text("چاپِ دوباره") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
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
  actionLabel: String = "چاپ",
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
        Text("چاپ فاکتور", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))

        Text("عرض کاغذ", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf("۵۸ میلی‌متر" to ThermalPrinter.WIDTH_58MM, "۸۰ میلی‌متر" to ThermalPrinter.WIDTH_80MM),
          selected = width,
          onSelect = { width = it; prefs.edit().putInt("printer_width", it).apply() },
        )

        Spacer(Modifier.height(16.dp))
        Text("چاپگر با چه چیزی وصل است؟", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf("بلوتوث" to "bt", "وای‌فای" to "net", "سیم" to "usb"),
          selected = kind,
          onSelect = { kind = it },
        )

        Spacer(Modifier.height(16.dp))

        when (kind) {
          /* ---------------- بلوتوث ---------------- */
          "bt" -> {
            if (!granted) {
              Text(
                "برای دیدن چاپگرها، اجازهٔ بلوتوث لازم است.",
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
              Spacer(Modifier.height(8.dp))
              Button(onClick = { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("اجازه دادن") }
            } else if (paired.isEmpty()) {
              Text(
                "چاپگری پیدا نشد. اول چاپگر را در تنظیمات بلوتوث گوشی جفت کنید، بعد اینجا برگردید.",
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
              "نشانیِ چاپگر در شبکهٔ دکان. روی خودِ چاپگر یا در برگهٔ تنظیماتش نوشته شده.",
              style = MaterialTheme.typography.bodySmall,
              color = Shop.colors.muted,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = host,
              onValueChange = { host = it },
              label = { Text("نشانیِ IP") },
              placeholder = { Text("192.168.1.50") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = port,
              onValueChange = { port = ir.vil3ntec.tohid.normalizeDigits(it).filter { c -> c in '0'..'9' }.take(5) },
              label = { Text("درگاه") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
              "تقریباً همهٔ چاپگرها روی ۹۱۰۰ گوش می‌دهند؛ تا وقتی چاپگرتان چیزِ دیگری نگفته، دست نزنید.",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
          }

          /* ---------------- سیم ---------------- */
          else -> {
            if (usbList.isEmpty()) {
              Text(
                "چاپگرِ سیمی پیدا نشد. کابل را وصل کنید و همین برگه را دوباره باز کنید.",
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
                "اولین بار اندروید اجازه می‌خواهد؛ «همیشه» را بزنید تا هر بار نپرسد.",
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.muted2,
              )
            }
          }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
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
) {
  var search by remember { mutableStateOf("") }
  val shown = d.debtors.filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }

  Dialog(onDismissRequest = onClose) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
        Text("انتخاب قرض‌دار", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = search,
          onValueChange = { search = it },
          placeholder = { Text("نام قرض‌دار") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        if (shown.isEmpty()) {
          EmptyNote(if (d.debtors.isEmpty()) "هنوز قرض‌داری ثبت نشده است" else "قرض‌داری پیدا نشد")
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
                  Text(
                    debtStateText(d, debtor.id),
                    style = MaterialTheme.typography.labelSmall,
                    color = Shop.colors.muted,
                  )
                }
              }
            }
          }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("بستن") }
          if (selected != null) {
            Button(onClick = { onPick(null) }, modifier = Modifier.weight(1f)) { Text("بدون قرض‌دار") }
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
  /**
   *  POS-08/10/20 — سیاستِ قیمتِ ردیف (`LinePolicyPrefs.of`)؛ پیش‌فرض خاموش ⇒ همان کادرِ پیشین،
   *  بی تخفیفِ ردیف و بی سنجش
   */
  linePolicy: ir.vil3ntec.tohid.core.pos.LinePricing.Policy = ir.vil3ntec.tohid.core.pos.LinePricing.Policy(),
) {
  val policy = linePolicy
  var qtyText by remember { mutableStateOf(qty(quantity)) }
  var priceText by remember { mutableStateOf(qty(unitPrice)) }
  var manual by remember { mutableStateOf(custom) }
  var discountText by remember { mutableStateOf("") }
  var discountPercent by remember { mutableStateOf(true) }
  //  مقدار به کدام واحد تایپ می‌شود؛ موجودی و قیمت همیشه به `unit` می‌مانند
  var inSaleUnit by remember { mutableStateOf(false) }
  val hasSaleUnit = saleUnit.isNotBlank() && unitFactor > 0

  val typedQty = qtyText.toDoubleOrNull()
  val parsedQty = typedQty?.let { if (inSaleUnit) Math.round(it * unitFactor * 1000.0) / 1000.0 else it }
  val parsedPrice = priceText.toDoubleOrNull()
  //  POS-08/10/20: قاعدهٔ جهت، سقفِ قلمِ آزاد و تخفیفِ ردیف (`LinePolicy.edit`)؛ خاموش ⇒ بی رد و بی تخفیف
  val edit = ir.vil3ntec.tohid.data.LinePolicy.edit(
    policy, free, basePrice, unitPrice, if (manual) parsedPrice else null, parsedQty, discountPercent, discountText,
  )
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

        if (hasSaleUnit) {
          SegmentedChoice(
            options = listOf("stock" to unit.ifBlank { "واحد" }, "sale" to saleUnit),
            selected = if (inSaleUnit) "sale" else "stock",
            onSelect = { choice ->
              val wantSale = choice == "sale"
              if (wantSale != inSaleUnit) {
                //  همان مقدار، به واحدِ تازه — نه پاک شدنِ کادر
                typedQty?.let { v ->
                  qtyText = qty(if (wantSale) v / unitFactor else v * unitFactor)
                }
                inSaleUnit = wantSale
              }
            },
          )
          Spacer(Modifier.height(8.dp))
        }
        NumberField(
          value = qtyText,
          onValueChange = { qtyText = it },
          label = (if (inSaleUnit) saleUnit else unit).let { u -> if (u.isNotBlank()) "تعداد ($u)" else "تعداد" },
          modifier = Modifier.fillMaxWidth(),
        )
        if (inSaleUnit && parsedQty != null) {
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
            "قیمت دستی برای این ردیف",
            style = MaterialTheme.typography.bodyMedium,
            color = Shop.colors.text,
          )
        }

        if (manual) {
          Spacer(Modifier.height(6.dp))
          AmountField(
            value = priceText,
            onValueChange = { priceText = it },
            label = "قیمت هر واحد",
          )
          Spacer(Modifier.height(4.dp))
          Text(
            "قیمت خودِ کالا ${money(basePrice)} ؋ است. این تغییر فقط روی همین فاکتور اثر دارد.",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
        }

        ir.vil3ntec.tohid.ui.components.pos.LineDiscountPanel(
          show = edit.canDiscount, percent = discountPercent, text = discountText, problem = edit.problem,
          onPercent = { discountPercent = it }, onText = { discountText = it },
        )

        Spacer(Modifier.height(14.dp))
        val shownPrice = if (manual) (parsedPrice ?: 0.0) else basePrice
        Text(
          "جمع این ردیف: ${money((parsedQty ?: 0.0) * shownPrice)} ؋",
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.text,
        )
        edit.net?.let { Text(ir.vil3ntec.tohid.core.i18n.StrLine.afterDiscount(money(it)), style = MaterialTheme.typography.titleSmall, color = Shop.colors.success) }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
          TextButton(onClick = onDismiss) { Text("انصراف") }
          Button(
            enabled = ok,
            //  تخفیفِ ردیف ⇒ قیمتِ واحدِ پس از تخفیف؛ وگرنه همان قیمتِ دستیِ پیشین
            onClick = { onConfirm(parsedQty ?: 0.0, edit.netUnit ?: if (manual) parsedPrice else null) },
          ) { Text("ثبت") }
        }
      }
    }
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
        Text("قلم آزاد", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(4.dp))
        Text(
          "چیزی که در فهرست کالاها نیست. روی موجودی اثر نمی‌گذارد و کالای تازه‌ای هم ثبت نمی‌کند.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("نام") },
          placeholder = { Text("مثلاً: کیسه") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        AmountField(value = priceText, onValueChange = { priceText = it }, label = "قیمت هر واحد")
        Spacer(Modifier.height(12.dp))
        NumberField(
          value = qtyText,
          onValueChange = { qtyText = it },
          label = "تعداد",
          modifier = Modifier.fillMaxWidth(),
        )

        problem?.let {
          Spacer(Modifier.height(8.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
        }
        Spacer(Modifier.height(14.dp))
        Text(
          "جمع: ${money((parsedQty ?: 0.0) * (parsedPrice ?: 0.0))} ؋",
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.text,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
          TextButton(onClick = onDismiss) { Text("انصراف") }
          Button(
            enabled = ok,
            onClick = { onConfirm(label.trim(), parsedPrice ?: 0.0, parsedQty ?: 1.0) },
          ) { Text("افزودن") }
        }
      }
    }
  }
}
