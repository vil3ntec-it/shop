package ir.vil3ntec.tohid.ui.v3.sales.checkout

import ir.vil3ntec.tohid.core.credit.CreditGate
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.numberInvalid
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.v3.sales.history.TotalRow

/** نوشته‌ها و انتخاب‌های برگهٔ تسویه */
data class CheckoutForm(
  val payment: SalesEngine.Payment,
  val debtorId: String?,
  /** تخفیف پیش‌فرض درصدی و «۰» — همان پنجرهٔ پیشین */
  val discountType: SalesEngine.DiscountType = SalesEngine.DiscountType.PERCENT,
  val discountText: String = "0",
  val paidText: String = "",
  /** ردیفِ «تخفیف» باز است */
  val discountOpen: Boolean = false,
)

/** عددهای برگه — همه از `SalesEngine.totals` و همان قاعدهٔ «پرداختی» ِ ثبت */
data class CheckoutNumbers(
  val subtotal: Double,
  val discount: Double,
  val finalTotal: Double,
  val paid: Double,
  val remaining: Double,
)

/**
 *  برگهٔ تسویه (`sale/checkout`) — خالص، همان قاعده‌های `CheckoutDialog`:
 *  • سبد قرض‌دار دارد ⇒ «نسیه» و پرداختیِ «۰»؛ وگرنه «نقدی»؛
 *  • تخفیف فقط با `canDiscount` (`ShopRole.mayDiscount`)؛ خالی ⇒ صفر، نادرست ⇒ خطای درجا؛
 *  • نقدی ⇒ پرداختی همیشه کلِ مبلغِ نهایی؛ نسیه ⇒ کمینهٔ نوشته و مبلغِ نهایی؛ باقی‌مانده به حسابِ قرض‌دار.
 *  هیچ عددی این‌جا ساخته نمی‌شود جز همان «کمینه» که ثبت هم می‌کند (`SalesEngine.record`).
 */
object CheckoutModel {

  /** @param presetDiscount POS-12 — تخفیفی که با فاکتورِ نگه‌داشته آمد (درصد، همان کادرِ کامپیوتر) */
  fun start(presetDebtorId: String?, debtors: List<Debtor>, presetDiscount: String = ""): CheckoutForm {
    val preset = presetDebtorId?.takeIf { id -> debtors.any { it.id == id } }
    val discount = presetDiscount.ifBlank { "0" }
    return if (preset != null) CheckoutForm(SalesEngine.Payment.CREDIT, preset, discountText = discount, paidText = "0")
    else CheckoutForm(SalesEngine.Payment.CASH, null, discountText = discount)
  }

  fun checkout(form: CheckoutForm, canDiscount: Boolean, blockExpired: Boolean, seller: String): SalesEngine.Checkout =
    SalesEngine.Checkout(
      seller = seller,
      discountType = form.discountType,
      discountValue = if (canDiscount) parseNumber(form.discountText) ?: 0.0 else 0.0,
      payment = form.payment,
      paidAmount = parseNumber(form.paidText) ?: 0.0,
      debtorId = form.debtorId,
      blockExpired = blockExpired,
    )

  fun numbers(d: ShopData, cart: List<SalesEngine.CartLine>, form: CheckoutForm, checkout: SalesEngine.Checkout): CheckoutNumbers {
    val t = SalesEngine.totals(d, cart, checkout)
    val paid = if (form.payment == SalesEngine.Payment.CASH) t.finalTotal
    else minOf(parseNumber(form.paidText)?.coerceAtLeast(0.0) ?: 0.0, t.finalTotal)
    return CheckoutNumbers(t.subtotal, t.discount, t.finalTotal, paid, (t.finalTotal - paid).coerceAtLeast(0.0))
  }

  /** همان `Checkout` با پرداختیِ نهایی و مشتری — آنچه به `record` می‌رود (پرداختِ ساده) */
  fun legacy(checkout: SalesEngine.Checkout, n: CheckoutNumbers, form: CheckoutForm): SalesEngine.Checkout =
    checkout.copy(paidAmount = n.paid, debtorId = form.debtorId)

  fun discountError(form: CheckoutForm, canDiscount: Boolean): String? =
    if (canDiscount && numberInvalid(form.discountText)) StrSale.badNumberShort else null

  fun paidError(form: CheckoutForm, payV2: Boolean): String? =
    if (!payV2 && form.payment == SalesEngine.Payment.CREDIT && numberInvalid(form.paidText)) StrSale.badNumberShort else null

  /** «۱۰٪» یا «۲۰۰ ؋» یا «بی تخفیف» برای ردیفِ بسته */
  fun discountLabel(form: CheckoutForm, discount: Double, amount: (Double) -> String): String {
    val v = parseNumber(form.discountText) ?: 0.0
    return when {
      discount <= 0 || v <= 0 -> StrSale.noDiscount
      form.discountType == SalesEngine.DiscountType.PERCENT -> "${qty(v)}٪ · −${amount(discount)}"
      else -> "−${amount(discount)}"
    }
  }

  /** عوض کردنِ نوعِ پرداخت؛ نسیه بی پرداختیِ نوشته ⇒ «۰» */
  fun withPayment(form: CheckoutForm, p: SalesEngine.Payment): CheckoutForm =
    form.copy(payment = p, paidText = if (p == SalesEngine.Payment.CREDIT && form.paidText.isBlank()) "0" else form.paidText)

  /**
   *  ردیف‌های جمعِ برگه — همان `PromoSummary.rows`: با تخفیفِ خودکار «جمع» پیش از آن و یک ردیفِ
   *  منفیِ «تخفیفِ خودکار»، بعد تخفیفِ دستی؛ «مبلغ نهایی» پررنگ. پرداختِ ساده ⇒ «مبلغ پرداختی»
   *  و «باقی‌مانده» هم (ترکیبی خلاصهٔ خودش را دارد).
   */
  fun rows(n: CheckoutNumbers, promo: PromoEngine.Outcome?, payV2: Boolean): List<TotalRow> = buildList {
    if (promo != null && promo.discount > 0) {
      add(TotalRow(StrSale.totalLabel, promo.gross))
      add(TotalRow(StrSale.autoDiscount, -promo.discount))
    } else add(TotalRow(StrSale.itemsTotal, n.subtotal))
    if (n.discount > 0) add(TotalRow(StrSale.discountWord, -n.discount))
    add(TotalRow(StrSale.finalAmount, n.finalTotal, strong = true))
    if (!payV2) {
      add(TotalRow(StrSale.paid, n.paid))
      add(TotalRow(StrSale.remaining, n.remaining, warn = n.remaining > 0))
    }
  }

  /** زیرِ مبلغِ درشت: «جمع اقلام ۱٬۲۰۰ · تخفیف −۱۰۰» (تخفیفِ خودکار و دستی با هم) */
  fun heroLine(n: CheckoutNumbers, promo: PromoEngine.Outcome?, amount: (Double) -> String): String {
    val auto = promo?.discount?.takeIf { it > 0 } ?: 0.0
    val gross = if (auto > 0) promo!!.gross else n.subtotal
    val off = auto + n.discount
    return if (off <= 0) "${StrSale.itemsTotal} ${amount(gross)}"
    else "${StrSale.itemsTotal} ${amount(gross)} · ${StrSale.discountWord} ${amount(-off)}"
  }

  /** حالِ تخفیف برای کاربر (STF-02): متنِ کنارِ قرصِ «نیازمند تأیید» */
  fun gateNote(cap: String?, limit: Double?): String =
    if (cap == "maxPct") StrSale.overYourCap(qty(limit ?: 0.0)) else StrSale.overYourRights

  /**
   *  CRM-03 / S07: فاکتوری که از سقفِ مشتری می‌گذرد (`SalesEngine.creditOver`) فقط با تأییدی ثبت می‌شود که همین
   *  مشتری و دست‌کم همین برد را بپوشاند (`CreditGate.state`) — آن تأیید همراهِ فاکتور می‌رود و `SalesEngine.record`
   *  دوباره می‌سنجد. `null` ⇒ اول «درخواستِ تأیید». زیرِ سقف، بی مشتری یا تنظیمِ خاموش ⇒ همان گزینه‌ها.
   */
  fun credited(options: SalesEngine.Checkout, over: CreditGate.Over?, approval: CreditGate.Approval?): SalesEngine.Checkout? {
    val debtorId = options.debtorId ?: return options
    if (over == null) return options
    return if (CreditGate.state(over, debtorId, approval) == CreditGate.State.APPROVED) options.copy(creditApproval = approval) else null
  }

  /** ثبت ممکن است؟ — همان شرطِ دکمهٔ «ثبت فروش» ِ پیشین */
  fun canSubmit(busy: Boolean, discountError: String?, paidError: String?, gate: DiscountGate.State): Boolean =
    !busy && discountError == null && paidError == null &&
      gate != DiscountGate.State.NEEDS_APPROVAL && gate != DiscountGate.State.DENIED
}
