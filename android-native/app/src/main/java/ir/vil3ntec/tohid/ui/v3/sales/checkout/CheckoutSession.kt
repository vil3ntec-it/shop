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
  /** برگهٔ امضاشدهٔ تأییدِ تخفیف (STF-02) */
  var approval by mutableStateOf<ApprovalSlip.Slip?>(null)
  var done by mutableStateOf<CheckoutDone?>(null)
  /** برگهٔ «درخواستِ تأیید» ِ باز (`sale/checkout/approve`) */
  var ask by mutableStateOf<ApprovalAsk?>(null)
  /** برگهٔ «فروشِ نسیه بیش از سقف» ِ باز (`sale/checkout/credit`) */
  var credit by mutableStateOf<CreditAsk?>(null)
  /** تأییدِ سقفِ اعتبار؛ فقط همان مشتری و تا همان برد را می‌پوشاند (`CreditGate.state` هر بار می‌سنجد) */
  var creditApproval by mutableStateOf<CreditGate.Approval?>(null)

  fun reset() {
    form = null
    pay.cash = ""
    pay.rows.clear()
    approval = null
    done = null
    ask = null
    credit = null
    creditApproval = null
  }
}
