package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.core.pay.FxTill
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.purchase.PurchaseFx
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.SafeCurrencies
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockLedger
import ir.vil3ntec.tohid.data.SupplierFx
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.desk.DeskChip
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.v3.money.LedgerFormsModel
import ir.vil3ntec.tohid.ui.v3.money.PayForm

/*
 *  PUR-11 (D-15 ب) — ارز و نرخ در خریدِ کامپیوتر (`DeskPurchasing`): کادرهای فاکتورِ خرید و ردیفِ پرداختِ حسابِ ارزیِ
 *  تأمین‌کننده. حساب همان `core/purchase/PurchaseFx` و `data/SupplierFx` ِ گوشی است؛ این‌جا فقط کادر و حالت.
 */

/** ارز و نرخِ خوانده‌شدهٔ فاکتور: قیمت‌ها و «پرداخت‌شده» به [code]؛ [rate] null = نرخ هنوز نادرست */
internal class DeskFx(val code: String, val rate: PurchaseFx.Rate?) {
  fun minor(text: String): Long? = if (text.isBlank()) null else SafeCurrencies.parse(text, code)

  /** جمعِ ارزیِ یک ردیف (واحدِ کوچک) از متنِ مقدار و فی؛ نادرست ⇒ null */
  fun line(qty: String, price: String): Long? {
    val q = qty.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    val p = minor(price) ?: return null
    return runCatching { PurchaseFx.lineFx(StockLedger.milli(q), p) }.getOrNull()
  }

  /** معادلِ افغانیِ تنهای یک ردیف (برای بهای واحدِ خرید)؛ نرخِ نادرست ⇒ ۰ */
  fun afnOf(qty: String, price: String): Double =
    rate?.let { r -> line(qty, price)?.let { runCatching { PurchaseFx.toAfn(it, r) }.getOrNull() } }?.let { it / 100.0 } ?: 0.0

  fun text(minor: Long) = SupplierFx.text(minor, code)

  /** سندِ زنده (همان `PurchaseFx.doc` ِ ثبت) از ردیف‌های (مقدار، فی) و «پرداخت‌شده»؛ نرخِ نادرست ⇒ null */
  fun doc(rows: List<Pair<String, String>>, paid: String): PurchaseFx.Doc? {
    val r = rate ?: return null
    return runCatching { PurchaseFx.doc(r, rows.map { (q, p) -> line(q, p) ?: 0L }, minor(paid) ?: 0L) }.getOrNull()
  }
}

/** حالتِ ارزِ فاکتورِ خریدِ کامپیوتر: افغانی (`code` null) تا کاربر ارزِ دیگر برگزیند */
internal class DeskFxInvoice {
  var code by mutableStateOf<String?>(null)
  var rateText by mutableStateOf("")
  var per by mutableStateOf(1L)

  val fx: DeskFx? get() = code?.let { c -> DeskFx(c, FxTill.parseAmount(rateText, PurchaseFx.AFN)?.let { PurchaseFx.rate(c, it, per) }) }

  /** ارزِ تازه ⇒ نرخ از [last] (آخرین نرخِ ثبت‌شده) و واحدِ نرخِ پیش‌فرض؛ `true` ⇐ عوض شد (فرم قیمت‌های تایپی را پاک کند) */
  fun pick(code: String, last: (String) -> PurchaseFx.Rate?): Boolean {
    val c = SupplierFx.cur(code)
    if (c == this.code) return false
    this.code = c
    val r = c?.let(last)
    rateText = r?.let { FxTill.afnText(it.rate) }.orEmpty()
    per = r?.per ?: c?.let(PurchaseFx::defaultPer) ?: 1L
    return true
  }
}

/** کادرهای ارز، نرخ و واحدِ نرخ کنارِ تاریخِ فاکتور — فقط وقتی دکان جز افغانی ارزی دارد */
@Composable
internal fun DeskFxInvoiceControls(state: DeskFxInvoice, currencies: List<String>, name: (String) -> String, onPick: (String) -> Unit) {
  var currencyMenu by remember { mutableStateOf(false) }
  var perMenu by remember { mutableStateOf(false) }
  if (currencies.size <= 1) return
  val shown = state.code ?: PurchaseFx.AFN
  Spacer(Modifier.width(8.dp))
  Box {
    OutlinedButton(onClick = { currencyMenu = true }, modifier = Modifier.semantics { contentDescription = "${StrSafeCur.currency}: $shown" }) {
      Text("${StrSafeCur.currency}: $shown", maxLines = 1)
    }
    DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
      currencies.forEach { c -> DropdownMenuItem(text = { Text(name(c)) }, onClick = { currencyMenu = false; onPick(c) }) }
    }
  }
  val code = state.code ?: return
  val rate = state.fx?.rate
  Spacer(Modifier.width(8.dp))
  OutlinedTextField(
    state.rateText, { state.rateText = ir.vil3ntec.tohid.numericInput(it) }, singleLine = true,
    label = { Text(StrPurchaseFx.rateLabel(FxTill.afnText(state.per * 100), code)) },
    isError = rate == null,
    supportingText = { Text(rate?.let { StrPurchaseFx.rateLine(PurchaseFx.rateLine(it)) } ?: StrPurchaseFx.rateBad) },
    modifier = Modifier.width(240.dp),
  )
  Spacer(Modifier.width(4.dp))
  Box {
    OutlinedButton(onClick = { perMenu = true }) { Text("${StrPurchaseFx.per} ${SupplierFx.ltr("${FxTill.afnText(state.per * 100)} $code")}", maxLines = 1) }
    DropdownMenu(expanded = perMenu, onDismissRequest = { perMenu = false }) {
      Pay.FX_PER.forEach { p -> DropdownMenuItem(text = { Text(SupplierFx.ltr("${FxTill.afnText(p * 100)} $code")) }, onClick = { state.per = p; perMenu = false }) }
    }
  }
}

/** «بدهی به تفکیکِ ارز» ِ پروندهٔ تأمین‌کننده (همان `SupplierFx.accounts`) */
@Composable
internal fun DeskFxBalances(accounts: List<PurchaseFx.Account>) {
  if (accounts.isEmpty()) return
  DeskNote(
    StrPurchaseFx.byCurrency + ": " + accounts.joinToString(" • ") { a ->
      val fx = SafeCurrencies.format(kotlin.math.abs(a.fx), a.currency)
      if (a.fx >= 0) StrPurchaseFx.owed(fx, money(a.afn / 100.0) + " ؋") else StrPurchaseFx.credit(fx)
    },
  )
}

/**
 *  ردیفِ «حساب / پرداخت با / نرخ» ِ پرداختِ کامپیوتر و پیش‌نمایشِ «صاف می‌شود / ارزش / اختلافِ نرخ» (همان `LedgerFormsModel` ِ برگهٔ
 *  گوشی). [form] بی مبلغ است (مبلغ کادرِ خودِ ردیفِ پرداخت است، [amount]).
 */
@Composable
internal fun DeskFxPayControls(
  accounts: List<PurchaseFx.Account>,
  form: PayForm,
  amount: String,
  onForm: (PayForm) -> Unit,
  last: (String) -> PurchaseFx.Rate?,
) {
  if (accounts.isEmpty()) return
  val c = Desk.colors
  val code = form.account
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
    Text(StrPurchaseFx.account, style = DeskType.label, color = c.inkSecondary)
    (listOf<String?>(null) + accounts.map { it.currency }).forEach { a ->
      DeskChip(a ?: StrPurchaseFx.afnAccount, code == a, { onForm(LedgerFormsModel.setAccount(form, a, a?.let(last))) })
    }
    if (code != null) {
      Spacer(Modifier.width(DeskSpace.x3))
      Text(StrPurchaseFx.payWith, style = DeskType.label, color = c.inkSecondary)
      DeskChip(code, !form.inAfn, { onForm(form.copy(inAfn = false)) })
      DeskChip(StrPurchaseFx.afnAccount, form.inAfn, { onForm(form.copy(inAfn = true)) })
      Spacer(Modifier.width(DeskSpace.x3))
      Text(StrPurchaseFx.rateCol, style = DeskType.label, color = c.inkSecondary)
      DeskTextField(form.rate, { onForm(form.copy(rate = ir.vil3ntec.tohid.numericInput(it))) }, StrPurchaseFx.rateLabel(FxTill.afnText(form.per * 100), code), Modifier.width(240.dp))
    }
  }
  val acc = accounts.firstOrNull { it.currency == code } ?: return
  val st = LedgerFormsModel.fxSettle(form.copy(amount = amount), acc) ?: return
  val diff = if (st.diff == 0L) null else money(kotlin.math.abs(st.diff) / 100.0) + " ؋"
  DeskNote(
    listOfNotNull(
      StrPurchaseFx.settles(SafeCurrencies.format(st.fx, acc.currency)),
      StrPurchaseFx.value(money(st.value / 100.0) + " ؋"),
      diff?.let { if (st.diff > 0) StrPurchaseFx.fxLoss(it) else StrPurchaseFx.fxGain(it) },
    ).joinToString(" • "),
  )
}

/** پرداختِ حسابِ ارزی از ردیفِ کامپیوتر: نرخ و مبلغِ نادرست ⇒ پیام؛ وگرنه همان `SupplierFx.pay` */
internal fun deskFxPay(
  d: ShopData, supplierId: String, form: PayForm, amount: String, notes: String, today: String, now: Long, newId: () -> String, key: String,
): LedgerEngine.Result {
  val code = form.account ?: return LedgerEngine.Result.Failed(StrPurchaseFx.amountBad)
  val f = form.copy(amount = amount)
  val rate = LedgerFormsModel.fxRate(f) ?: return LedgerEngine.Result.Failed(StrPurchaseFx.rateBad)
  val minor = LedgerFormsModel.fxMinor(f) ?: return LedgerEngine.Result.Failed(StrPurchaseFx.amountBad)
  return SupplierFx.pay(d, supplierId, code, minor, form.inAfn, rate, today, notes, today, now, newId, key = key)
}

/** برچسبِ کادرِ مبلغِ ردیفِ پرداخت برای حسابِ ارزی («مبلغ (USD)» یا «مبلغ (AFN)»)؛ حسابِ افغانی ⇒ null (همان برچسبِ پیشین) */
internal fun deskFxAmountLabel(form: PayForm): String? = form.account?.let { StrPurchaseFx.amountIn(if (form.inAfn) PurchaseFx.AFN else it) }

/** سطرهای نرخ و جمعِ ارزیِ فاکتورِ ارزی در پنجرهٔ فاکتورِ کامپیوتر؛ افغانی ⇒ هیچ */
@Composable
internal fun DeskFxInvoiceRows(inv: ir.vil3ntec.tohid.data.PurchaseBook.Invoice) {
  val code = inv.currency ?: return
  inv.fxRate?.let { ir.vil3ntec.tohid.ui.desk.DeskInfoRow(StrPurchaseFx.rateCol, SupplierFx.rateText(it)) }
  ir.vil3ntec.tohid.ui.desk.DeskInfoRow(StrPurchaseFx.totalIn(code), SupplierFx.text(kotlin.math.abs(inv.fxTotal), code), strong = true)
}
