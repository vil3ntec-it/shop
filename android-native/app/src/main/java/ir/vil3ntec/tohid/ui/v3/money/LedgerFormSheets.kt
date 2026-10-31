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
  V3FormSheet(title = if (supplier == null) StrMoney.newSupplier else StrMoney.editSupplier, dirty = f != initial, onClose = onClose) { sheet ->
    Column(Modifier.v3Shake(shake)) {
      V3TextField(f.name, { f = f.copy(name = it) }, StrMoney.name, required = true, error = nameError.takeIf { tried })
      Spacer(Modifier.height(12.dp))
      V3TextField(f.phone, { f = f.copy(phone = it) }, StrMoney.phoneOptional, kind = V3FieldKind.PHONE)
      Spacer(Modifier.height(12.dp))
      V3TextField(f.address, { f = f.copy(address = it) }, StrMoney.addressOptional)
      Spacer(Modifier.height(12.dp))
      V3TextField(f.notes, { f = f.copy(notes = it) }, StrMoney.notesOptional, singleLine = false)
      Spacer(Modifier.height(18.dp))
      V3Button(
        StrKit.save,
        { tried = true; if (nameError != null) shake++ else if (onSave(LedgerFormsModel.draft(f))) sheet.finish(onSaved) },
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
internal fun SupplierPaySheet(supplier: Supplier, debt: Double, onClose: () -> Unit, onSave: (PayForm) -> Boolean, onSaved: () -> Unit) {
  val initial = remember(supplier.id) { PayForm(date = todayIso()) }
  var f by remember(supplier.id) { mutableStateOf(initial) }
  var tried by rememberSaveable { mutableStateOf(false) }
  var shake by remember { mutableStateOf(0) }
  val error = LedgerFormsModel.payError(f)
  V3FormSheet(title = StrMoney.pay, dirty = f != initial, onClose = onClose) { sheet ->
    Column(Modifier.v3Shake(shake)) {
      Text(StrMoney.owedLine(supplier.name, money(debt.coerceAtLeast(0.0))), style = v3Text(14, FontWeight.Medium, 20), color = PhoneV3.colors.inkMuted)
      Spacer(Modifier.height(12.dp))
      V3TextField(f.amount, { f = f.copy(amount = it) }, StrMoney.payAmount, kind = V3FieldKind.MONEY, required = true, error = error.takeIf { tried })
      LedgerFormsModel.settleAll(debt)?.let { all ->
        Spacer(Modifier.height(8.dp))
        V3Chip(StrMoney.settleAll, selected = f.amount == all, onClick = { f = f.copy(amount = all) })
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
