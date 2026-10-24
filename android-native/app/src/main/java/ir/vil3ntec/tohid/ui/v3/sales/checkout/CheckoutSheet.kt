package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.ui.components.pos.PayPanelState
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SheetCloser
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTotals
import ir.vil3ntec.tohid.ui.v3.sales.history.TotalRow

/** همهٔ آنچه برگهٔ تسویه نشان می‌دهد — `CheckoutRoute` از موتورها می‌سازد */
data class CheckoutView(
  val form: CheckoutForm,
  val numbers: CheckoutNumbers,
  val rows: List<TotalRow>,
  val heroLine: String,
  val promo: PromoEngine.Outcome?,
  /** قاعدهٔ کددار هست ⇒ کادرِ «کدِ تخفیف» */
  val showCodes: Boolean,
  val coupons: List<String>,
  val canDiscount: Boolean,
  val payV2: Boolean,
  val gate: DiscountGate.State,
  val gateNote: String,
  val customerName: String?,
  val customerBalance: String?,
  val busy: Boolean,
  val error: String?,
  val discountError: String?,
  val paidError: String?,
  val payError: String?,
  val canSubmit: Boolean,
  val canAskApproval: Boolean,
)

/** کارهای برگه — هر کدام یک تغییرِ فرم یا یک مسیر */
class CheckoutActions(
  val form: (CheckoutForm) -> Unit,
  val customer: () -> Unit,
  val clearCustomer: () -> Unit,
  val approve: () -> Unit,
  val addCoupon: (String) -> Unit,
  val removeCoupon: (String) -> Unit,
  val submit: () -> Unit,
  val close: () -> Unit,
)

/**
 *  برگهٔ تسویه (`sale/checkout`، VILL3N §G5): بالا «مبلغ نهایی» درشت (با هر تغییرِ تخفیف
 *  می‌شمارد) و «جمع اقلام · تخفیف»؛ تخفیف‌های خودکار با نامِ قاعده و کادرِ کد؛ «نقدی | نسیه»
 *  (ترکیبی ⇐ پنلِ روش‌ها)؛ ردیفِ مشتری ⇐ `sale/checkout/customer`؛ تخفیفِ دستی (فقط با اجازه،
 *  با سدِ «نیازمند تأیید» ⇐ `sale/checkout/approve`)؛ مبلغِ پرداختی و باقی‌مانده؛ «ثبت فروش»
 *  چسبیده به پایین. خطای موتور (کمبود، قفل) بالای برگه. پس از ثبت، همین برگه رسید می‌شود.
 */
@Composable
fun CheckoutSheet(
  view: CheckoutView?,
  pay: PayPanelState,
  actions: CheckoutActions,
  receipt: (@Composable (V3SheetCloser) -> Unit)?,
) {
  V3Sheet(
    onDismiss = actions.close,
    title = if (receipt == null) StrSale.checkoutTitle else null,
    dismissible = view?.busy != true,
    scroll = false,
    detents = false,
  ) { closer ->
    AnimatedContent(
      targetState = receipt != null,
      transitionSpec = {
        (fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) + scaleIn(MotionSpec.tweenOf(MotionTokens.MEDIUM), initialScale = 0.96f)) togetherWith
          fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) using SizeTransform(clip = false)
      },
      label = "checkoutMorph",
    ) { done ->
      if (done) receipt?.invoke(closer) else if (view != null) FormBody(view, pay, actions)
    }
  }
}

@Composable
private fun FormBody(v: CheckoutView, pay: PayPanelState, a: CheckoutActions) {
  val c = PhoneV3.colors
  val f = v.form
  Column(Modifier.fillMaxWidth()) {
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
      v.error?.let {
        V3Message(V3Tone.ERROR, it, Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        Spacer(Modifier.height(12.dp))
      }
      Hero(v)
      PromoApplied(v.promo?.applied.orEmpty())
      if (v.showCodes) {
        Spacer(Modifier.height(10.dp))
        PromoCodeBox(v.promo?.codes.orEmpty(), v.coupons, a.addCoupon, a.removeCoupon)
      }
      Spacer(Modifier.height(16.dp))

      if (!v.payV2) {
        V3Segmented(
          options = listOf(StrSale.cashPay, StrSale.creditPay),
          selected = if (f.payment == SalesEngine.Payment.CREDIT) 1 else 0,
          onSelect = { i -> a.form(CheckoutModel.withPayment(f, if (i == 1) SalesEngine.Payment.CREDIT else SalesEngine.Payment.CASH)) },
        )
        Spacer(Modifier.height(12.dp))
      }
      CustomerRow(v, a)

      if (v.canDiscount) {
        Spacer(Modifier.height(12.dp))
        DiscountBox(v, a)
      }

      Spacer(Modifier.height(12.dp))
      if (v.payV2) {
        CheckoutPaySplit(pay, v.numbers.finalTotal, f.debtorId, v.customerName, onSubmit = a.submit, error = v.payError)
        Spacer(Modifier.height(12.dp))
      } else if (f.payment == SalesEngine.Payment.CREDIT) {
        V3TextField(
          value = f.paidText,
          onValueChange = { a.form(f.copy(paidText = it)) },
          label = StrSale.paidAmount,
          kind = V3FieldKind.MONEY,
          error = v.paidError,
          helper = StrSale.restToDebtor,
        )
        Spacer(Modifier.height(12.dp))
      }
      SaleTotals(v.rows)
      Spacer(Modifier.height(12.dp))
    }
    V3Button(
      if (v.busy) StrSale.posting else StrSale.recordSale,
      a.submit,
      Modifier.fillMaxWidth(),
      enabled = v.canSubmit,
      loading = v.busy,
      haptic = true,
    )
  }
}

/** «مبلغ نهایی» درشت با شمارش، و زیرش «جمع اقلام · تخفیف» */
@Composable
private fun Hero(v: CheckoutView) {
  val c = PhoneV3.colors
  val shown = rememberCountUp(v.numbers.finalTotal)
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(StrSale.finalAmount, style = V3Type.subhead, color = c.inkMuted)
    Text(
      PhoneSaleText.amount(shown),
      style = V3Type.figure.copy(fontSize = 34.sp, lineHeight = 42.sp),
      color = c.ink,
      textAlign = TextAlign.Center,
      maxLines = 1,
    )
    Text(v.heroLine, style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center)
  }
}

/** هر تخفیفِ خودکارِ اعمال‌شده با نامِ قاعده و مبلغش (PRM-18) */
@Composable
private fun PromoApplied(applied: List<PromoEngine.Applied>) {
  if (applied.isEmpty()) return
  val c = PhoneV3.colors
  Spacer(Modifier.height(12.dp))
  V3Card(radius = 16.dp, padding = PaddingValues(vertical = 2.dp)) {
    applied.forEach { p ->
      V3ListRow(
        title = p.name,
        subtitle = StrSale.autoDiscount,
        icon = Icons.Outlined.LocalOffer,
        hue = V3Hue.MINT,
        value = PhoneSaleText.amount(-p.discount),
        valueColor = c.positive,
        chevron = false,
        minHeight = 52.dp,
      )
    }
  }
}

/** کادرِ «کدِ تخفیف» و حالِ هر کدِ زده‌شده — حساب `PromoEngine` است */
@Composable
private fun PromoCodeBox(
  codes: List<ir.vil3ntec.tohid.core.promo.PromoCore.CodeStatus>,
  entered: List<String>,
  onAdd: (String) -> Unit,
  onRemove: (String) -> Unit,
) {
  val c = PhoneV3.colors
  var text by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
  fun submit() { if (text.isNotBlank()) onAdd(text.trim()); text = "" }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    V3TextField(
      value = text,
      onValueChange = { text = it },
      label = StrSale.promoCode,
      imeAction = androidx.compose.ui.text.input.ImeAction.Done,
      onImeAction = ::submit,
      modifier = Modifier.weight(1f),
    )
    V3Button(StrSale.apply, ::submit, kind = V3ButtonKind.SECONDARY, enabled = text.isNotBlank(), height = 44.dp)
  }
  entered.forEach { code ->
    val st = codes.firstOrNull { it.code == code }?.status ?: "unknown"
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      V3StatusPill(if (st == "applied") V3Status.DELIVERED else V3Status.CANCELLED, "$code — ${PromoEngine.codeText(st)}", Modifier.weight(1f, fill = false))
      Spacer(Modifier.weight(1f))
      V3TextButton(StrSale.removeWord, { onRemove(code) })
    }
  }
}

@Composable
private fun CustomerRow(v: CheckoutView, a: CheckoutActions) {
  val f = v.form
  val label = when {
    v.payV2 -> StrSale.customerToAccount
    f.payment == SalesEngine.Payment.CREDIT -> StrSale.debtorWord
    else -> StrSale.customerOptional
  }
  V3Card(radius = 16.dp, padding = PaddingValues(0.dp)) {
    V3ListRow(
      title = v.customerName ?: label,
      subtitle = if (v.customerName != null) listOfNotNull(label, v.customerBalance).joinToString(" · ")
      else if (f.payment == SalesEngine.Payment.CREDIT && !v.payV2) StrSale.debtorNeeded else StrSale.chooseWord,
      icon = Icons.Outlined.Person,
      hue = V3Hue.CORAL,
      chevron = v.customerName == null,
      trailing = if (v.customerName != null) ({ V3TextButton(StrSale.removeWord, a.clearCustomer) }) else null,
      onClick = a.customer,
    )
  }
}

/** ردیفِ بستهٔ «تخفیف»؛ باز ⇒ «درصدی | مبلغی»، کادر و حالِ سدِ تأیید */
@Composable
private fun DiscountBox(v: CheckoutView, a: CheckoutActions) {
  val c = PhoneV3.colors
  val f = v.form
  V3Card(radius = 16.dp, padding = PaddingValues(0.dp)) {
    V3ListRow(
      title = StrSale.discountWord,
      icon = Icons.Outlined.LocalOffer,
      hue = V3Hue.AMBER,
      value = CheckoutModel.discountLabel(f, v.numbers.discount, PhoneSaleText::amount),
      chevron = !f.discountOpen,
      onClick = { a.form(f.copy(discountOpen = !f.discountOpen)) },
    )
    AnimatedVisibility(f.discountOpen || v.discountError != null || v.gate != DiscountGate.State.FREE) {
      Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
        V3Segmented(
          options = listOf(StrSale.percentWord, StrSale.amountWord),
          selected = if (f.discountType == SalesEngine.DiscountType.PERCENT) 0 else 1,
          onSelect = { i -> a.form(f.copy(discountType = if (i == 0) SalesEngine.DiscountType.PERCENT else SalesEngine.DiscountType.AMOUNT)) },
        )
        Spacer(Modifier.height(10.dp))
        V3TextField(
          value = f.discountText,
          onValueChange = { a.form(f.copy(discountText = it)) },
          label = if (f.discountType == SalesEngine.DiscountType.PERCENT) StrSale.discountPercent else StrSale.discountAmount,
          kind = V3FieldKind.NUMBER,
          suffix = if (f.discountType == SalesEngine.DiscountType.PERCENT) "٪" else "؋",
          error = v.discountError,
        )
        when (v.gate) {
          DiscountGate.State.NEEDS_APPROVAL -> {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Column(Modifier.weight(1f)) {
                V3StatusPill(V3Status.AWAITING, StrSale.needsApproval)
                Spacer(Modifier.height(4.dp))
                Text(v.gateNote, style = V3Type.footnote, color = c.inkMuted)
              }
              V3Button(StrSale.askApproval, a.approve, kind = V3ButtonKind.SECONDARY, enabled = v.canAskApproval, height = 40.dp)
            }
          }
          DiscountGate.State.APPROVED -> {
            Spacer(Modifier.height(8.dp))
            V3StatusPill(V3Status.DELIVERED, StrSale.approved)
          }
          DiscountGate.State.DENIED -> {
            Spacer(Modifier.height(8.dp))
            V3StatusPill(V3Status.CANCELLED, StrSale.discountDenied)
          }
          DiscountGate.State.FREE -> Unit
        }
      }
    }
  }
}
