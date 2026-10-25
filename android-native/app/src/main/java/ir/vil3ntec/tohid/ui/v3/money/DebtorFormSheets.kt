package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3DateField
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3FormSheet
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.delay

/**
 *  قرض‌دارِ تازه / ویرایش (`debtors/new`، `debtors/{id}/edit`): نام (لازم)، تلفن، یادداشت و سقفِ
 *  اعتبار (خالی = بی سقف). ذخیره = `DebtorEngine.add` / `editWithLimit`؛ ردِ موتور پیامِ خودش را دارد.
 */
@Composable
internal fun DebtorFormSheet(
  debtor: Debtor?,
  onClose: () -> Unit,
  /** `true` ⇐ موتور پذیرفت */
  onSave: (DebtorForm) -> Boolean,
  /** پس از بسته شدنِ برگه (برگشت یا نتیجه به صدازننده) */
  onSaved: () -> Unit,
) {
  val initial = remember(debtor?.id) { DebtorFormsModel.of(debtor) }
  var f by remember(debtor?.id) { mutableStateOf(initial) }
  var tried by rememberSaveable { mutableStateOf(false) }
  var shake by remember { mutableStateOf(0) }
  val nameError = DebtorFormsModel.nameError(f)
  val limitError = DebtorFormsModel.limitError(f)
  V3FormSheet(title = if (debtor == null) StrMoney.newDebtor else StrMoney.editDebtor, dirty = f != initial, onClose = onClose) { sheet ->
    Column(Modifier.v3Shake(shake)) {
      V3TextField(f.name, { f = f.copy(name = it) }, StrMoney.name, required = true, error = nameError.takeIf { tried })
      Spacer(Modifier.height(12.dp))
      V3TextField(f.phone, { f = f.copy(phone = it) }, StrMoney.phoneOptional, kind = V3FieldKind.PHONE)
      Spacer(Modifier.height(12.dp))
      V3TextField(f.notes, { f = f.copy(notes = it) }, StrMoney.notesOptional, singleLine = false)
      Spacer(Modifier.height(12.dp))
      V3TextField(
        f.creditLimit, { f = f.copy(creditLimit = it) }, StrMoney.creditLimitField,
        kind = V3FieldKind.MONEY, helper = StrMoney.creditLimitHelper, error = limitError,
      )
      Spacer(Modifier.height(18.dp))
      V3Button(
        StrKit.save,
        {
          tried = true
          if (nameError != null || limitError != null) shake++
          else if (onSave(f)) sheet.finish(onSaved)
        },
        Modifier.fillMaxWidth(),
        haptic = true,
      )
    }
  }
}

private enum class TxFormStep { FORM, OVER_LIMIT, DONE }

/**
 *  ثبتِ تراکنش (`debtors/{id}/tx?kind=give|receive`، و گامِ ۲ ِ «دریافت»): «برد | رسید» (از
 *  `kind`)، مبلغ با «تسویهٔ کامل» (فقط رسید)، تاریخ، یادداشت، سررسیدِ برد، و «مانده پس از ثبت» ِ
 *  زنده. بردی که از سقف می‌گذرد ⇒ گامِ دومِ همین برگه با هشدار و «باز هم ثبت کن» (همان پرسشِ
 *  امروز، CRM-03). ثبت ⇒ تیکِ موفقیت و بسته شدن؛ حساب دوباره می‌شمارد.
 */
@Composable
internal fun TxFormSheet(
  debtor: Debtor,
  balance: Double,
  initialKind: DebtorEngine.Kind,
  overLimitOf: (TxForm) -> DebtBook.Credit?,
  onClose: () -> Unit,
  /** `true` ⇐ موتور پذیرفت */
  onSubmit: (TxForm, overLimit: Boolean) -> Boolean,
  onDone: () -> Unit,
) {
  val initial = remember(debtor.id, initialKind) { TxForm(kind = initialKind, date = todayIso()) }
  var f by remember(debtor.id, initialKind) { mutableStateOf(initial) }
  var step by rememberSaveable { mutableStateOf(TxFormStep.FORM) }
  var tried by rememberSaveable { mutableStateOf(false) }
  var shake by remember { mutableStateOf(0) }
  val receiving = f.kind == DebtorEngine.Kind.RECEIVE
  val amountError = DebtorFormsModel.amountError(f)

  V3FormSheet(title = StrMoney.newTx, dirty = f != initial && step != TxFormStep.DONE, onClose = onClose) { sheet ->
    LaunchedEffect(step) { if (step == TxFormStep.DONE) { delay(450); sheet.finish(onDone) } }
    V3PageSwitch(step, depth = { it.ordinal }) { s ->
      when (s) {
        TxFormStep.FORM -> Column(Modifier.v3Shake(shake)) {
          Text("${debtor.name} — ${MoneyText.state(balance)}", style = v3Text(13, FontWeight.Medium, 18), color = PhoneV3.colors.inkMuted)
          Spacer(Modifier.height(12.dp))
          V3Segmented(
            listOf(StrMoney.giveKind, StrMoney.receiveKind),
            selected = if (receiving) 1 else 0,
            onSelect = { i -> f = f.copy(kind = if (i == 1) DebtorEngine.Kind.RECEIVE else DebtorEngine.Kind.GIVE) },
          )
          Spacer(Modifier.height(14.dp))
          V3TextField(f.amount, { f = f.copy(amount = it) }, StrMoney.amount, kind = V3FieldKind.MONEY, required = true, error = amountError.takeIf { tried })
          val settle = DebtorFormsModel.settleAll(balance)
          if (receiving && settle != null) {
            Spacer(Modifier.height(8.dp))
            V3Chip(StrMoney.settleAll, selected = f.amount == settle, onClick = { f = f.copy(amount = settle) })
          }
          Spacer(Modifier.height(12.dp))
          V3DateField(f.date, { f = f.copy(date = it) }, StrMoney.date)
          Spacer(Modifier.height(12.dp))
          V3TextField(f.notes, { f = f.copy(notes = it) }, StrMoney.notesOptional)
          if (!receiving) {
            Spacer(Modifier.height(12.dp))
            DueDateEditor(f.due, { f = f.copy(due = it) })
          }
          Spacer(Modifier.height(14.dp))
          Text(
            StrMoney.balanceAfterSave(MoneyText.state(DebtorFormsModel.balanceAfter(balance, f))),
            style = v3Text(14, FontWeight.SemiBold, 20),
            color = PhoneV3.colors.ink,
          )
          Spacer(Modifier.height(14.dp))
          V3Button(
            if (receiving) StrMoney.recordReceive else StrMoney.recordGive,
            {
              tried = true
              when {
                amountError != null -> shake++
                overLimitOf(f) != null -> step = TxFormStep.OVER_LIMIT
                onSubmit(f, false) -> step = TxFormStep.DONE
              }
            },
            Modifier.fillMaxWidth(),
            haptic = true,
          )
        }
        TxFormStep.OVER_LIMIT -> Column {
          val credit = overLimitOf(f)
          V3Message(
            V3Tone.WARNING,
            StrMoney.overLimitTitle,
            body = (credit?.let { MoneyText.overLimit(it) + ".\n" } ?: "") + StrMoney.overLimitNote,
          )
          Spacer(Modifier.height(16.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            V3Button(StrKit.cancel, { step = TxFormStep.FORM }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
            V3Button(StrMoney.recordAnyway, { if (onSubmit(f, true)) step = TxFormStep.DONE }, Modifier.weight(1.3f), kind = V3ButtonKind.DANGER, haptic = true)
          }
        }
        TxFormStep.DONE -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
          V3SuccessCheck(visible = true, haptic = true)
        }
      }
    }
  }
}
