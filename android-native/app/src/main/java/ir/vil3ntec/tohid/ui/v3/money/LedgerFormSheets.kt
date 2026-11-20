package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.pay.FxTill
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.purchase.PurchaseFx
import ir.vil3ntec.tohid.data.SafeCurrencies
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3DateField
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3FormSheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  تأمین‌کنندهٔ تازه / ویرایش (`purchasing/suppliers/new|{id}/edit`): نام (لازم)، تلفن، آدرس و
 *  یادداشت ⇒ «ذخیره». ثبت = `LedgerEngine.addSupplier` / `editSupplier`.
 */
@Composable
internal fun SupplierFormSheet(supplier: Supplier?, onClose: () -> Unit, onSave: (LedgerEngine.SupplierDraft) -> Boolean, onSaved: () -> Unit) {
  val initial = remember(supplier?.id) { LedgerFormsModel.of(supplier) }
  var f by remember(supplier?.id) { mutableStateOf(initial) }
  var tried by rememberSaveable { mutableStateOf(false) }
  var shake by remember { mutableStateOf(0) }
  val nameError = LedgerFormsModel.nameError(f)
  val leadError = LedgerFormsModel.leadError(f)
  V3FormSheet(title = if (supplier == null) StrMoney.newSupplier else StrMoney.editSupplier, dirty = f != initial, onClose = onClose) { sheet ->
    Column(Modifier.v3Shake(shake)) {
      V3TextField(f.name, { f = f.copy(name = it) }, StrMoney.name, required = true, error = nameError.takeIf { tried })
      Spacer(Modifier.height(12.dp))
      V3TextField(f.phone, { f = f.copy(phone = it) }, StrMoney.phoneOptional, kind = V3FieldKind.PHONE)
      Spacer(Modifier.height(12.dp))
      V3TextField(f.address, { f = f.copy(address = it) }, StrMoney.addressOptional)
      Spacer(Modifier.height(12.dp))
      V3TextField(f.notes, { f = f.copy(notes = it) }, StrMoney.notesOptional, singleLine = false)
      Spacer(Modifier.height(12.dp))
      //  PUR-10: زمانِ تأمینِ دستی ⇐ «پیشنهادِ سفارش» و نقطهٔ سفارش؛ خالی ⇒ سنجیده از سفارش‌ها
      V3TextField(f.leadDays, { f = f.copy(leadDays = it) }, StrMoney.leadDaysOptional, kind = V3FieldKind.NUMBER, helper = StrMoney.leadDaysHelp, error = leadError.takeIf { tried })
      Spacer(Modifier.height(18.dp))
      V3Button(
        StrKit.save,
        { tried = true; if (nameError != null || leadError != null) shake++ else if (onSave(LedgerFormsModel.draft(f))) sheet.finish(onSaved) },
        Modifier.fillMaxWidth(),
        haptic = true,
      )
    }
  }
}

/**
 *  پرداخت به تأمین‌کننده (`purchasing/suppliers/{id}/pay`): «{نام} — ۱٬۲۰۰ ؋ بدهکاریم»، مبلغ با
 *  «تسویهٔ کامل»، تاریخ و یادداشت ⇒ «ثبت پرداخت». ثبت = `LedgerEngine.paySupplier`.
 */
@Composable
internal fun SupplierPaySheet(
  supplier: Supplier,
  debt: Double,
  onClose: () -> Unit,
  onSave: (PayForm) -> Boolean,
  onSaved: () -> Unit,
  /** e-rows-63b: «پرداخت» ِ یک خرید — مبلغ و یادداشتِ پیش‌پُر (ماندهٔ همان خرید)؛ null ⇒ برگهٔ خالیِ پیشین */
  prefill: PayForm? = null,
  /**
   *  PUR-11: حساب‌های ارزیِ همین تأمین‌کننده (`SupplierFx.accounts`)؛ خالی ⇒ همان برگهٔ افغانیِ پیشین. با حساب: تراشهٔ «حساب»
   *  (افغانی | ارز)، «پرداخت با» (همان ارز | افغانی)، نرخِ همین پرداخت، و پیش‌نمایشِ «صاف می‌شود / ارزش / اختلافِ نرخ».
   */
  fxAccounts: List<PurchaseFx.Account> = emptyList(),
  lastRate: (String) -> PurchaseFx.Rate? = { null },
) {
  val initial = remember(supplier.id, prefill) { prefill ?: PayForm(date = todayIso()) }
  var f by remember(supplier.id) { mutableStateOf(initial) }
  var tried by rememberSaveable { mutableStateOf(false) }
  var shake by remember { mutableStateOf(0) }
  val error = LedgerFormsModel.payError(f)
  val code = f.account
  val acc = fxAccounts.firstOrNull { it.currency == code }
  V3FormSheet(title = StrMoney.pay, dirty = f != initial, onClose = onClose) { sheet ->
    Column(Modifier.v3Shake(shake)) {
      if (fxAccounts.isNotEmpty()) {
        val codes = listOf<String?>(null) + fxAccounts.map { it.currency }
        Text(StrPurchaseFx.account, style = v3Text(13, FontWeight.SemiBold, 18), color = PhoneV3.colors.inkMuted)
        Spacer(Modifier.height(6.dp))
        V3Segmented(codes.map { it ?: StrPurchaseFx.afnAccount }, codes.indexOf(code).coerceAtLeast(0), { i ->
          f = LedgerFormsModel.setAccount(f, codes[i], codes[i]?.let(lastRate))
        })
        Spacer(Modifier.height(12.dp))
      }
      if (acc == null) {
        Text(StrMoney.owedLine(supplier.name, money(debt.coerceAtLeast(0.0))), style = v3Text(14, FontWeight.Medium, 20), color = PhoneV3.colors.inkMuted)
      } else {
        val owed = if (acc.fx >= 0) StrPurchaseFx.owed(SafeCurrencies.format(acc.fx, acc.currency), MoneyText.afn(acc.afn / 100.0))
        else StrPurchaseFx.credit(SafeCurrencies.format(-acc.fx, acc.currency))
        Text("${supplier.name} — $owed", style = v3Text(14, FontWeight.Medium, 20), color = PhoneV3.colors.inkMuted)
        Spacer(Modifier.height(12.dp))
        Text(StrPurchaseFx.payWith, style = v3Text(13, FontWeight.SemiBold, 18), color = PhoneV3.colors.inkMuted)
        Spacer(Modifier.height(6.dp))
        V3Segmented(listOf(acc.currency, StrPurchaseFx.afnAccount), if (f.inAfn) 1 else 0, { i -> f = f.copy(inAfn = i == 1, amount = "") })
        Spacer(Modifier.height(12.dp))
        V3TextField(
          f.rate, { f = f.copy(rate = it) }, StrPurchaseFx.rateLabel(FxTill.afnText(f.per * 100), acc.currency), kind = V3FieldKind.MONEY, required = true,
          helper = LedgerFormsModel.fxRate(f)?.let { StrPurchaseFx.rateLine(PurchaseFx.rateLine(it)) },
          error = (if (LedgerFormsModel.fxRate(f) == null) StrPurchaseFx.rateBad else null).takeIf { tried },
        )
        Spacer(Modifier.height(6.dp))
        V3ChipRow(contentPadding = PaddingValues(0.dp)) {
          Pay.FX_PER.forEach { p -> V3Chip(ir.vil3ntec.tohid.data.SupplierFx.ltr("${FxTill.afnText(p * 100)} ${acc.currency}"), selected = f.per == p, onClick = { f = f.copy(per = p) }) }
        }
      }
      Spacer(Modifier.height(12.dp))
      V3TextField(
        f.amount, { f = f.copy(amount = it) },
        if (acc == null) StrMoney.payAmount else StrPurchaseFx.amountIn(if (f.inAfn) PurchaseFx.AFN else acc.currency),
        kind = V3FieldKind.MONEY, required = true, error = error.takeIf { tried },
      )
      val all = if (acc == null) LedgerFormsModel.settleAll(debt) else LedgerFormsModel.settleAllFx(f, acc)
      all?.let {
        Spacer(Modifier.height(8.dp))
        V3Chip(StrMoney.settleAll, selected = f.amount == it, onClick = { f = f.copy(amount = it) })
      }
      LedgerFormsModel.fxSettle(f, acc)?.let { st ->
        //  PUR-11: پیش‌نمایشِ همان حسابِ ثبت — صاف‌شده به ارز، ارزش به نرخِ این پرداخت و اختلافِ نرخ
        Spacer(Modifier.height(10.dp))
        val muted = PhoneV3.colors.inkMuted
        Text(StrPurchaseFx.settles(SafeCurrencies.format(st.fx, acc?.currency ?: PurchaseFx.AFN)), style = V3Type.footnote, color = muted)
        Text(StrPurchaseFx.value(MoneyText.afn(st.value / 100.0)), style = V3Type.footnote, color = muted)
        if (st.diff != 0L) {
          val amount = MoneyText.afn(kotlin.math.abs(st.diff) / 100.0)
          Text(if (st.diff > 0) StrPurchaseFx.fxLoss(amount) else StrPurchaseFx.fxGain(amount), style = V3Type.footnote, color = muted)
        }
        if (!f.inAfn) Text(StrPurchaseFx.drawerNote, style = V3Type.footnote, color = muted)
      }
      Spacer(Modifier.height(12.dp))
      V3DateField(f.date, { f = f.copy(date = it) }, StrMoney.date)
      Spacer(Modifier.height(12.dp))
      V3TextField(f.notes, { f = f.copy(notes = it) }, StrMoney.notesOptional)
      Spacer(Modifier.height(18.dp))
      V3Button(
        StrMoney.recordPayment,
        { tried = true; if (error != null) shake++ else if (onSave(f)) sheet.finish(onSaved) },
        Modifier.fillMaxWidth(),
        haptic = true,
      )
    }
  }
}

/**
 *  مصرفِ تازه / ویرایش (`expenses/new`، `expenses/{id}`): مبلغ، دسته (تراشه‌های دسته‌های دکان و
 *  «+ دستهٔ تازه»)، «بابت چی؟» و تاریخ (امروز) ⇒ «ذخیره»؛ ویرایش «حذف» هم دارد (با پرسش).
 *  ثبت = `LedgerEngine.addExpense` / `editExpense` / `deleteExpense`.
 */
@Composable
internal fun ExpenseFormSheet(
  expense: Expense?,
  categories: List<String>,
  onClose: () -> Unit,
  onSave: (LedgerEngine.ExpenseDraft) -> Boolean,
  onSaved: () -> Unit,
  /** ویرایش: حذف با پرسش (`LedgerEngine.deleteExpense`)، پس از بسته شدنِ برگه */
  onDelete: (() -> Unit)?,
) {
  val today = todayIso()
  val initial = remember(expense?.id) { LedgerFormsModel.of(expense, today).let { if (it.category.isBlank()) it.copy(category = categories.firstOrNull().orEmpty()) else it } }
  var f by remember(expense?.id) { mutableStateOf(initial) }
  var tried by rememberSaveable { mutableStateOf(false) }
  var shake by remember { mutableStateOf(0) }
  var adding by rememberSaveable { mutableStateOf(false) }
  var newName by rememberSaveable { mutableStateOf("") }
  var askDelete by remember { mutableStateOf(false) }
  val error = LedgerFormsModel.expenseAmountError(f)
  V3FormSheet(title = if (expense == null) StrMoney.newExpense else StrMoney.editExpense, dirty = f != initial, onClose = onClose) { sheet ->
    Column(Modifier.v3Shake(shake)) {
      V3TextField(f.amount, { f = f.copy(amount = it) }, StrMoney.expenseAmount, kind = V3FieldKind.MONEY, required = true, error = error.takeIf { tried })
      Spacer(Modifier.height(14.dp))
      Text(StrMoney.category, style = v3Text(13, FontWeight.SemiBold, 18), color = PhoneV3.colors.inkMuted)
      Spacer(Modifier.height(6.dp))
      V3ChipRow(contentPadding = PaddingValues(0.dp)) {
        LedgerFormsModel.categories(categories, f.category).forEach { c ->
          V3Chip(c, selected = f.category == c, onClick = { f = f.copy(category = c) })
        }
        V3AddChip(StrMoney.newCategory) { adding = true }
      }
      if (adding) {
        Spacer(Modifier.height(8.dp))
        V3TextField(
          newName, { newName = it }, StrMoney.newCategory,
          onImeAction = { newName.trim().takeIf { it.isNotEmpty() }?.let { f = f.copy(category = it) }; adding = false; newName = "" },
        )
      }
      Spacer(Modifier.height(12.dp))
      V3TextField(f.title, { f = f.copy(title = it) }, StrMoney.forWhatOptional)
      Spacer(Modifier.height(12.dp))
      V3DateField(f.date, { f = f.copy(date = it) }, StrMoney.date)
      Spacer(Modifier.height(18.dp))
      V3Button(
        StrKit.save,
        {
          tried = true
          val withNew = if (adding && newName.isNotBlank()) f.copy(category = newName.trim()) else f
          if (error != null) shake++ else if (onSave(LedgerFormsModel.draft(withNew))) sheet.finish(onSaved)
        },
        Modifier.fillMaxWidth(),
        haptic = true,
      )
      if (onDelete != null) {
        Spacer(Modifier.height(6.dp))
        V3TextButton(StrMoney.delete, { askDelete = true }, Modifier.fillMaxWidth(), danger = true)
      }
    }
    if (askDelete && expense != null && onDelete != null) {
      V3ConfirmSheet(
        title = StrMoney.deleteExpenseTitle,
        message = StrMoney.deleteExpenseBody(expense.title, money(expense.amount)),
        confirm = StrMoney.delete,
        icon = Icons.Outlined.DeleteOutline,
        onConfirm = { sheet.finish(onDelete) },
        onDismiss = { askDelete = false },
      )
    }
  }
}
