package ir.vil3ntec.tohid.ui.v3.sales.checkout

import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.core.pay.GiftCard
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.StoredPay
import ir.vil3ntec.tohid.data.GiftCards
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money

/**
 *  PAY-24 در صندوقِ v3 (`CheckoutPaySplit`) — کارتِ هدیه و اعتبارِ فروشگاه همان دو روشِ صندوقِ قدیم (`PaySplitSection`،
 *  `SaleDialogs`) با همان API (`PayFlow` با `Draft.stored`، `GiftCards.problem`/`message`). خالص؛ تنظیم (`GiftCardPref`)
 *  را `CheckoutRoute` می‌خواند و در `PayPanelState.stored` می‌گذارد. خاموش ⇒ همان روش‌ها و همان پیام‌های امروز. خرج
 *  فقط تا ماندهٔ کارت یا طلبِ مشتری؛ `SalesEngine.record` هنگامِ ثبت دوباره می‌سنجد و ردیفِ خرج را هم‌اتم می‌نویسد.
 */
object CheckoutStoredPay {

  sealed interface Out {
    data class Ok(val settlement: Settlement) : Out
    data class Bad(val message: String) : Out
  }

  /** «ثبتِ فروش»: تسویهٔ پنل، و با تنظیم پیش‌سنجشِ مانده روی دفترِ صفحه — همان ترتیبِ صندوقِ قدیم */
  fun settle(ledger: ShopData, draft: PayFlow.Draft): Out = when (val r = PayFlow.checkout(draft)) {
    is PayFlow.Outcome.Bad -> Out.Bad(PayFlow.message(r.problem))
    is PayFlow.Outcome.Ok -> problem(ledger.takeIf { draft.stored }, r.value, draft.customerId)?.let { Out.Bad(it) } ?: Out.Ok(r.value)
  }

  /** خرج بیش از ماندهٔ کارت یا طلبِ مشتری (یا کارتِ ناشناخته/باطل) ⇒ پیام؛ بی دفتر (تنظیمِ خاموش) ⇒ null */
  fun problem(ledger: ShopData?, s: Settlement?, customerId: String?): String? =
    if (ledger == null || s == null) null else GiftCards.problem(ledger, s, customerId)?.let(GiftCards::message)

  /** راهنمای زیرِ ردیف: ماندهٔ خرج‌شدنیِ کارت (با کدِ درست) یا اعتبارِ مشتری؛ بی دفتر ⇒ null */
  fun hint(ledger: ShopData?, view: GiftCard.View?, row: PayFlow.DraftRow, customerId: String?): String? {
    if (ledger == null || view == null) return null
    return when (PayFlow.method(row.method)?.kind) {
      StoredPay.GIFT -> view.card(GiftCard.normalize(row.ref))?.let { StrGiftCard.cardLeft(money(PayFlow.major(it.spendableMinor))) }
      StoredPay.CREDIT -> StrGiftCard.creditLeft(money(PayFlow.major(GiftCards.creditOf(ledger, customerId))))
      else -> null
    }
  }
}
