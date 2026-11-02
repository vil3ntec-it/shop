package ir.vil3ntec.tohid.core.i18n

/**
 *  کارتِ هدیه و اعتبارِ فروشگاه به‌عنوانِ روشِ پرداخت (PAY-24) در صندوقِ گوشی و کامپیوتر (`GiftCardBar`، پنلِ پرداخت) و
 *  تنظیماتِ همین دستگاه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/pay/{GiftCard,StoredPay}` (commonMain) و `data/GiftCards`. ترجمه‌های نامطمئن در
 *  `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrGiftCard {
  val settingsTitle: String get() = t("کارتِ هدیه و اعتبارِ فروشگاه", "Gift cards and store credit", "د ډالۍ کارت او د دوکان اعتبار")
  val settingsNote: String get() = t(
    "روشن: در صندوق کارتِ هدیه فروخته و ماندهٔ آن دیده می‌شود، و در پنلِ پرداختِ ترکیبی «کارت هدیه» و «اعتبار فروشگاه» دو روشِ تازه‌اند. خرج فقط تا ماندهٔ کارت یا طلبِ مشتری، هم‌زمان با ثبتِ فاکتور؛ برگشتِ همان فاکتور به همان کارت یا اعتبار.",
    "On: the register sells gift cards and shows their balance, and the split payment panel gets two methods, \"Gift card\" and \"Store credit\". Spending is only up to the card balance or the customer's credit, together with the invoice; a return of that invoice goes back to the same card or credit.",
    "روښانه: په صندوق کې د ډالۍ کارت پلورل کېږي او پاتې یې ښکاري، او د ګډې تادیې په تخته کې «د ډالۍ کارت» او «د دوکان اعتبار» دوه نوې لارې دي. لګښت یوازې د کارت تر پاتې یا د پېرودونکي تر طلب پورې، له بل سره یوځای؛ د همغه بل بېرته ورکول همغه کارت یا اعتبار ته.",
  )
  val needsPayPanel: String get() = t(
    "پرداخت با کارت و اعتبار فقط در پنلِ پرداختِ ترکیبی است (تنظیمِ «پرداخت»).",
    "Paying with a card or credit is only in the split payment panel (the \"Payment\" setting).",
    "په کارت یا اعتبار تادیه یوازې د ګډې تادیې په تخته کې ده (د «تادیه» امستنه).",
  )

  val sell: String get() = t("فروشِ کارتِ هدیه", "Sell a gift card", "د ډالۍ کارت پلورل")
  val check: String get() = t("ماندهٔ کارتِ هدیه", "Gift card balance", "د ډالۍ کارت پاتې")
  val code: String get() = t("کدِ کارت", "Card code", "د کارت کوډ")
  val codeHint: String get() = t(
    "کدِ کارتِ چاپی را بزنید، یا همین کدِ تازه را روی کارت بنویسید و به مشتری بدهید.",
    "Type the code of a printed card, or write this new code on the card and give it to the customer.",
    "د چاپي کارت کوډ ولیکئ، یا همدا نوی کوډ پر کارت ولیکئ او پېرودونکي ته یې ورکړئ.",
  )
  val newCode: String get() = t("کدِ تازه", "New code", "نوی کوډ")
  val amount: String get() = t("مبلغ (افغانی)", "Amount (AFN)", "اندازه (افغانۍ)")
  val cashNote: String get() = t(
    "مبلغ نقد به دخل می‌آید و تا خرج، بدهیِ دکان به دارندهٔ کارت است.",
    "The amount comes into the drawer as cash and, until spent, is the shop's debt to the card holder.",
    "اندازه نغده دخل ته راځي او تر لګښت پورې د کارت لرونکي ته د دوکان پور دی.",
  )
  val save: String get() = t("ثبتِ فروشِ کارت", "Save card sale", "د کارت پلور ثبت کړئ")
  fun sold(code: Any, amount: Any): String = t("کارتِ $code با $amount افغانی فروخته شد", "Card $code sold with $amount AFN", "کارت $code په $amount افغانۍ وپلورل شو")
  val lookup: String get() = t("ببین", "Look up", "وګورئ")
  fun balance(amount: Any): String = t("مانده: $amount افغانی", "Balance: $amount AFN", "پاتې: $amount افغانۍ")
  fun issuedWith(amount: Any, date: Any): String = t("فروخته با $amount افغانی — $date", "Sold with $amount AFN — $date", "په $amount افغانۍ پلورل شوی — $date")
  fun spentBack(spent: Any, back: Any): String = t("خرج: $spent — برگشت: $back", "Spent: $spent — Returned: $back", "لګښت: $spent — بېرته: $back")
  val isVoid: String get() = t("این کارت باطل شده است", "This card has been voided", "دا کارت باطل شوی")
  val review: String get() = t(
    "نیازمندِ بررسی: از دو دستگاهِ بی‌اینترنت بیش از مانده خرج شده (ماندهٔ منفی).",
    "Needs review: spent beyond its balance from two offline devices (negative balance).",
    "کتنې ته اړتیا: له دوو بې‌انټرنېټه وسیلو له پاتې زیات لګول شوی (منفي پاتې).",
  )
  val close: String get() = t("بستن", "Close", "بندول")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")

  fun cardLeft(amount: Any): String = t("ماندهٔ کارت: $amount", "Card balance: $amount", "د کارت پاتې: $amount")
  fun creditLeft(amount: Any): String = t("اعتبارِ مشتری: $amount", "Customer credit: $amount", "د پېرودونکي اعتبار: $amount")

  val errCode: String get() = t("کدِ کارتِ هدیه درست نیست", "The gift card code is not valid", "د ډالۍ کارت کوډ سم نه دی")
  val errUnknown: String get() = t("این کارتِ هدیه پیدا نشد", "This gift card was not found", "دا د ډالۍ کارت ونه موندل شو")
  val errVoid: String get() = t("این کارتِ هدیه باطل شده است", "This gift card has been voided", "دا د ډالۍ کارت باطل شوی")
  val errBalance: String get() = t("ماندهٔ کارتِ هدیه کافی نیست", "The gift card balance is not enough", "د ډالۍ کارت پاتې بس نه دی")
  val errTaken: String get() = t("این کد پیش‌تر برای کارتِ دیگری ثبت شده است", "This code is already used by another card", "دا کوډ مخکې د بل کارت لپاره ثبت شوی")
  val errAmount: String get() = t("مبلغ درست نیست", "The amount is not valid", "اندازه سمه نه ده")
  val errConflict: String get() = t("این ثبت با ثبتِ پیشینِ همین فاکتور نمی‌خواند؛ دوباره باز کنید", "This entry does not match the earlier entry of the same invoice; reopen it", "دا ثبت د همدې بل له مخکېني ثبت سره سمون نه خوري؛ بیا یې پرانیزئ")
  val errNoCustomer: String get() = t("برای اعتبارِ فروشگاه، مشتری را انتخاب کنید", "Choose the customer for store credit", "د دوکان د اعتبار لپاره پېرودونکی وټاکئ")
  val errCredit: String get() = t("اعتبارِ مشتری کافی نیست", "The customer's credit is not enough", "د پېرودونکي اعتبار بس نه دی")
  val errRefundOver: String get() = t("بیش از آنچه از کارت خرج شد به آن برنمی‌گردد", "No more than what was spent from the card goes back to it", "له هغه زیات چې له کارت لګول شوی بېرته ورته نه ځي")
  val errNotSaved: String get() = t("ثبت نشد", "Not saved", "ثبت نشو")
}
