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
import ir.vil3ntec.tohid.core.perm.CreditLimitPerm
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
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.delay

/**
 *  قرض‌دارِ تازه / ویرایش (`debtors/new`، `debtors/{id}/edit`) — صاحب‌کار ۱۴۰۵/۰۸/۲۳: «اسم و شماره بسه»: فقط نام (لازم)
 *  و تلفن (اختیاری). ذخیره = `DebtorFormsModel.saveBasic` (`DebtorEngine.add` / `editWithLimit`)؛ ردِ موتور پیامِ خودش را
 *  دارد. یادداشت، سقف (CRM-03: تازه ⇒ سقفِ پیش‌فرضِ دکان) و پروندهٔ CRM ِ مشتریِ کهنه (شمارهٔ دوم، برچسب، نشانی، رضایت)
 *  پرسیده نمی‌شوند و دست نمی‌خورند.
 */
@Composable
internal fun DebtorFormSheet(
  debtor: Debtor?,
  onClose: () -> Unit,
  /** `true` ⇐ موتور پذیرفت */
  onSave: (DebtorForm) -> Boolean,
  /** پس از بسته شدنِ برگه (برگشت یا نتیجه به صدازننده) */
  onSaved: () -> Unit,
  /** CRM-03 — همین کاربر و پرچمِ دکان (`rememberCreditLimitRules`)؛ پیش‌فرض خاموش = همان امروز */
  limitRules: CreditLimitPerm.Rules = CreditLimitPerm.Rules.OFF,
) {
  val initial = remember(debtor?.id, limitRules) { DebtorFormsModel.of(debtor, limitRules) } // CRM-03 #۲۵: تازه ⇒ سقفِ پیش‌فرض
  var f by remember(debtor?.id) { mutableStateOf(initial) }
  var tried by rememberSaveable { mutableStateOf(false) }
  var shake by remember { mutableStateOf(0) }
  val nameError = DebtorFormsModel.nameError(f)
  V3FormSheet(title = if (debtor == null) StrMoney.newDebtor else StrMoney.editDebtor, dirty = f != initial, onClose = onClose) { sheet ->
    Column(Modifier.v3Shake(shake)) {
      V3TextField(f.name, { f = f.copy(name = it) }, StrMoney.name, required = true, error = nameError.takeIf { tried })
      Spacer(Modifier.height(12.dp))
      //  CRM-15: بی «دیدنِ شمارهٔ مشتریان» شمارهٔ مشتریِ موجود نه دیده و نه ویرایش می‌شود (سرور هم نمی‌پذیرد)؛ مشتریِ تازه شماره می‌گیرد
      val contacts = ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts()
      if (debtor == null || contacts || debtor.phone.isBlank()) V3TextField(f.phone, { f = f.copy(phone = it) }, StrMoney.phoneOptional, kind = V3FieldKind.PHONE)
      else Text(ir.vil3ntec.tohid.ui.components.customerPhone(debtor.phone, false), style = ir.vil3ntec.tohid.ui.v3.V3Type.footnote)
      Spacer(Modifier.height(18.dp))
      V3Button(
        StrKit.save,
        {
          tried = true
          if (nameError != null) shake++
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
 *  ثبتِ تراکنش (`debtors/{id}/tx?kind=give|receive`، و گامِ ۲ ِ «دریافت») — برگهٔ کوچک‌شده (صاحب‌کار ۱۴۰۵/۰۸/۲۳):
 *  سربرگِ «ثبتِ تراکنش» با تاریخِ نوشته‌ایِ کوچک روبه‌رویش (پیش‌فرض امروز؛ زدن ⇒ کادرِ تاریخ)، مبلغ با قوطیِ «برد/رسید»
 *  کنارش (پیش‌فرض همان `kind` ِ دکمه‌ای که برگه را باز کرد؛ زدن ⇒ نوعِ دیگر) و «تسویهٔ کامل» (فقط رسید)، یادداشتِ یک‌خطی،
 *  تراشه‌های سررسیدِ برد و «مانده پس از ثبت» ِ زنده. آن‌چه ذخیره می‌شود همان `TxForm` و همان `DebtorFormsModel.submit` است.
 *  بردی که از سقف می‌گذرد ⇒ گامِ دومِ همین برگه با هشدار و «باز هم ثبت کن» (CRM-03). ثبت ⇒ تیکِ موفقیت و بسته شدن.
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
  var dateOpen by rememberSaveable { mutableStateOf(false) }
  var shake by remember { mutableStateOf(0) }
  val receiving = f.kind == DebtorEngine.Kind.RECEIVE
  val amountError = DebtorFormsModel.amountError(f)

  V3FormSheet(
    title = StrMoney.newTx,
    dirty = f != initial && step != TxFormStep.DONE,
    onClose = onClose,
    headerEnd = { if (step == TxFormStep.FORM) TxDateLink(f.date, dateOpen) { dateOpen = !dateOpen } },
  ) { sheet ->
    LaunchedEffect(step) { if (step == TxFormStep.DONE) { delay(450); sheet.finish(onDone) } }
    V3PageSwitch(step, depth = { it.ordinal }) { s ->
      when (s) {
        TxFormStep.FORM -> Column(Modifier.v3Shake(shake)) {
          if (dateOpen) {
            //  همان کادرِ تاریخِ همیشگی (نوشتن + «امروز/دیروز»)؛ تراشه ⇒ تاریخ و بسته شدن
            V3DateField(f.date, { iso -> f = f.copy(date = iso); if (DebtBook.isIsoDate(iso) && iso.length == 10) dateOpen = false }, StrMoney.date)
            Spacer(Modifier.height(12.dp))
          }
          Text("${debtor.name} — ${MoneyText.state(balance)}", style = v3Text(13, FontWeight.Medium, 18), color = PhoneV3.colors.inkMuted)
          Spacer(Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            V3TextField(
              f.amount, { f = f.copy(amount = it) }, StrMoney.amount, Modifier.weight(1f),
              kind = V3FieldKind.MONEY, required = true, error = amountError.takeIf { tried },
            )
            TxKindToggle(f.kind, onToggle = { f = f.copy(kind = f.kind.flipped()) })
          }
          val settle = DebtorFormsModel.settleAll(balance)
          if (receiving && settle != null) {
            Spacer(Modifier.height(8.dp))
            V3Chip(StrMoney.settleAll, selected = f.amount == settle, onClick = { f = f.copy(amount = settle) })
          }
          Spacer(Modifier.height(12.dp))
          V3TextField(f.notes, { f = f.copy(notes = it) }, StrMoney.notesOptional)
          if (!receiving) {
            Spacer(Modifier.height(10.dp))
            TxDueChips(f.due) { f = f.copy(due = it) }
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
                amountError != null || DebtorFormsModel.dueBroken(f) -> shake++
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
