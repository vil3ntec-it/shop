package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrRounding
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.data.GiftCards
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.pos.PayPanelState
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  پرداختِ ترکیبی در تسویه (`ShopPolicy.payV2`، PAY-01..07) — همان `PaySplitSection` روی کیت:
 *  نقدِ دریافتی با «نقدِ دقیق»، ردیف‌های کیف پول/کارت/حواله (روش با تراشه، شمارهٔ مرجع، مبلغ)،
 *  «+ روشِ دیگر»، و خلاصهٔ هسته (قابلِ پرداخت، دریافتی، پولِ پس، باقی‌مانده یا «به حسابِ …»).
 *  همهٔ حساب `PayFlow` است؛ متن‌ها در `PayPanelState` (`CheckoutSession.pay`) می‌مانند.
 *  PAY-24: با `state.stored` (`GiftCardPref`) «کارت هدیه» و «اعتبار فروشگاه» هم در تراشه‌هایند، با راهنمای مانده زیرِ
 *  ردیف و پیامِ خرجِ بیش از مانده (`CheckoutStoredPay`، همان صندوقِ قدیم)؛ خاموش ⇒ همان روش‌های امروز.
 *  PAY-10: با جدولِ نرخِ دکان (`state.fxRates`) کادرِ «ارزِ نقد» (`CheckoutFxBox`)؛ پولِ پس همچنان افغانیِ نقد.
 */
@Composable
fun CheckoutPaySplit(
  state: PayPanelState,
  total: Double,
  customerId: String?,
  customerName: String?,
  onSubmit: () -> Unit,
  error: String?,
  /** PAY-24 — دفترِ صفحه برای ماندهٔ کارت و اعتبار (فقط با `state.stored`)؛ `null` ⇒ بی راهنما */
  ledger: ShopData? = null,
) {
  val c = PhoneV3.colors
  val preview = PayFlow.preview(state.draft(total, customerId))
  val book = ledger?.takeIf { state.stored }
  val giftView = remember(book) { book?.let(GiftCards::view) }
  Column(Modifier.fillMaxWidth().animateContentSize(MotionSpec.tweenOf(MotionTokens.MEDIUM))) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      V3TextField(
        value = state.cash,
        onValueChange = { state.cash = it },
        label = StrSale.cashReceived,
        kind = V3FieldKind.MONEY,
        helper = StrSale.exactCashHint,
        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        onImeAction = { if (state.cash.isBlank()) state.fillExact(total, customerId) else onSubmit() },
        modifier = Modifier.weight(1f),
      )
      V3Button(StrSale.exactCash, { state.fillExact(total, customerId) }, kind = V3ButtonKind.SECONDARY, height = 44.dp)
    }
    state.rows.forEachIndexed { i, row ->
      val bad = preview is PayFlow.Outcome.Bad && preview.problem.index == i
      Spacer(Modifier.height(10.dp))
      V3Card(radius = 16.dp, padding = PaddingValues(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          V3ChipRow(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
            state.methods().forEach { m ->
              V3Chip(m.name, row.method == m.id, { state.rows[i] = row.copy(method = m.id) })
            }
          }
          V3IconButton(Icons.Outlined.Close, StrSale.removePayRow, onClick = { state.rows.removeAt(i) })
          Spacer(Modifier.padding(end = 6.dp))
        }
        Row(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          V3TextField(
            value = row.ref,
            onValueChange = { state.rows[i] = row.copy(ref = it) },
            label = StrSale.refNumber,
            error = if (bad) StrSale.checkRow else null,
            modifier = Modifier.weight(1f),
          )
          V3TextField(
            value = row.amount,
            onValueChange = { state.rows[i] = row.copy(amount = it) },
            label = StrSale.amountWord,
            kind = V3FieldKind.MONEY,
            error = if (bad) StrSale.checkRow else null,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            onImeAction = onSubmit,
            modifier = Modifier.weight(1f),
          )
        }
        CheckoutStoredPay.hint(book, giftView, row, customerId)?.let {
          Text(it, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp))
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    V3AddChip(StrSale.otherMethod) { state.addRow() }
    //  PAY-10: ارزِ نقد فقط با جدولِ نرخِ روشنِ دکان (`state.fxRates`)؛ خاموش ⇒ هیچ کادرِ تازه‌ای
    if (state.fxRates.isNotEmpty()) {
      Spacer(Modifier.height(10.dp))
      CheckoutFxBox(state, bad = preview is PayFlow.Outcome.Bad && preview.problem.method == ir.vil3ntec.tohid.core.pay.Pay.FX, onSubmit = onSubmit)
    }

    //  خلاصه — همان عددِ هسته
    val s: Settlement? = preview.valueOrNull()
    Spacer(Modifier.height(12.dp))
    V3Card(radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
      //  PAY-09: ردیفِ جدای گرد (فقط سهمِ نقدی که فاکتور را می‌بندد) و «قابلِ پرداخت» با آن
      val rounding = s?.rounding ?: 0L
      if (rounding != 0L) SplitRow(StrRounding.line, PhoneSaleText.amount(PayFlow.major(rounding)))
      SplitRow(StrSale.payable, PhoneSaleText.amount(if (rounding != 0L) PayFlow.major(s!!.due) else total), strong = true)
      SplitRow(StrSale.tendered, s?.let { PhoneSaleText.amount(PayFlow.major(it.tendered)) } ?: "—")
      SplitRow(StrSale.changeCashOnly, s?.let { PhoneSaleText.amount(PayFlow.major(it.change)) } ?: "—")
      SplitRow(
        if ((s?.toAccount ?: 0) > 0) StrSale.toAccountOf(customerName ?: StrSale.customerWord) else StrSale.remaining,
        s?.let { PhoneSaleText.amount(PayFlow.major(it.remaining)) } ?: "—",
        warn = (s?.remaining ?: 0) > 0,
      )
      if (s != null && s.payments.any { it.status == "pending" }) {
        Spacer(Modifier.height(6.dp))
        V3StatusPill(V3Status.AWAITING, StrSale.nonCashPending)
        Spacer(Modifier.height(4.dp))
      }
    }
    val stored = CheckoutStoredPay.problem(book, s, customerId)
    val shown = error ?: when {
      preview is PayFlow.Outcome.Bad -> PayFlow.message(preview.problem)
      stored != null -> stored
      s != null && s.status == "short" && (s.tendered > 0 || state.rows.isNotEmpty()) -> PayFlow.message(PayFlow.Problem("SHORT", remaining = s.remaining))
      else -> null
    }
    if (shown != null) {
      Spacer(Modifier.height(6.dp))
      Text(shown, style = V3Type.footnote, color = PhoneV3Roles.errorText(c))
    }
  }
}

@Composable
private fun SplitRow(label: String, value: String, strong: Boolean = false, warn: Boolean = false) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
    Text(
      value,
      style = if (strong) V3Type.headline else V3Type.callout,
      color = if (warn) PhoneV3Roles.toneIcon(c, V3Tone.WARNING) else c.ink,
    )
  }
}
