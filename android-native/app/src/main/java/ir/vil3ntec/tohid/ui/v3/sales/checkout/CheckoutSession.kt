package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.credit.CreditGate
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.pos.PayPanelState
import ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalAsk

/** فروشِ تازه ثبت شد: دفترِ همان لحظه (فاکتور همان چیزی را نشان دهد که ثبت شد) و شناسه */
data class CheckoutDone(val data: ShopData, val saleId: String)

/** فروشِ نسیهٔ بیش از سقف که منتظرِ تأیید است (CRM-03، `sale/checkout/credit`): مشتری، بردِ تازه، خلاصه و فروشنده */
data class CreditAsk(val debtorId: String, val addMinor: Long, val summary: String, val seller: String)

/**
 *  حالتِ تسویهٔ سبدِ جاری، بیرون از برگه: رفتن به «انتخابِ مشتری» (`sale/checkout/customer`،
 *  مسیرِ خودش) و برگشت، نوشته‌ها، ردیف‌های پرداختِ ترکیبی و برگهٔ تأیید را پاک نمی‌کند.
 *  هر بار «پرداخت» ِ سبد از نو (`reset`)؛ پس از ثبت `done` تا برگه رسید را نشان دهد.
 */
object CheckoutSession {
  var form by mutableStateOf<CheckoutForm?>(null)
  /** پنلِ پرداختِ ترکیبی (`ShopPolicy.payV2`) */
  val pay = PayPanelState()
  /** برگهٔ امضاشدهٔ تأییدِ تخفیف (STF-02)؛ با پرچمِ قیمتِ سرور همان برگهٔ «تخفیفِ پنهان» هم (`SaleCapGate`) */
  var approval by mutableStateOf<ApprovalSlip.Slip?>(null)
  /** STF-03 — برگه‌های تأییدِ قرض و تغییرِ قیمتِ همین فاکتور به کلیدِ کار (`sales.credit`، `sales.price`) */
  var capSlips by mutableStateOf<Map<String, ApprovalSlip.Slip>>(emptyMap())
  /** STF-03 — برگهٔ تأییدِ سقف را خودِ «ثبت» باز کرد ⇒ با رسیدنِ تأیید همان ثبت دوباره (مثلِ سقفِ اعتبار) */
  var capPending by mutableStateOf(false)
  var done by mutableStateOf<CheckoutDone?>(null)
  /** برگهٔ «درخواستِ تأیید» ِ باز (`sale/checkout/approve`) */
  var ask by mutableStateOf<ApprovalAsk?>(null)
  /** برگهٔ «فروشِ نسیه بیش از سقف» ِ باز (`sale/checkout/credit`) */
  var credit by mutableStateOf<CreditAsk?>(null)
  /** تأییدِ سقفِ اعتبار؛ فقط همان مشتری و تا همان برد را می‌پوشاند (`CreditGate.state` هر بار می‌سنجد) */
  var creditApproval by mutableStateOf<CreditGate.Approval?>(null)
  /** S22 — برگهٔ «فروشِ کالای منقضی» ِ باز (`sale/checkout/expiry`) */
  var expiry by mutableStateOf<ExpiryAsk?>(null)
  /** تأییدِ فروشِ منقضی؛ فقط همان کالاها و تا همان مقدار را می‌پوشاند (`ExpiryGate.covers` هر بار می‌سنجد) */
  var expiryApproval by mutableStateOf<ir.vil3ntec.tohid.core.stock.ExpiryGate.Approval?>(null)
  /** برگهٔ تأییدِ منقضی را خودِ «ثبت» باز کرد ⇒ با رسیدنِ تأیید همان ثبت (از «حساب‌پی» ⇒ نه؛ پس از تأیید دوباره «حساب‌پی») */
  var expiryPending by mutableStateOf(false)
  /** برگهٔ «پرداخت با حساب‌پی (NFC/QR)» ِ باز (`sale/checkout/hesabpay`، PAY-21) */
  var hesab by mutableStateOf<HesabTillModel.Ask?>(null)
  /** محیطِ حساب‌پیِ همان برگه (`HesabPayEnv.of`، از `CheckoutRoute`) */
  var hesabEnv: ir.vil3ntec.tohid.pay.platform.HesabPayEnv? = null
  /** تسویهٔ تأییدشدهٔ حساب‌پی که هنوز ثبت نشده (ثبتِ ردشده ⇒ همان دوباره، بی پرداختِ دوم) */
  var hesabPaid by mutableStateOf<ir.vil3ntec.tohid.core.pay.Settlement?>(null)

  fun reset() {
    form = null
    pay.cash = ""
    pay.rows.clear()
    pay.fxAmount = ""
    approval = null
    capSlips = emptyMap()
    capPending = false
    done = null
    ask = null
    credit = null
    creditApproval = null
    expiry = null
    expiryApproval = null
    expiryPending = false
    hesab = null
    hesabPaid = null
  }
}
