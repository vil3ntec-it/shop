package ir.vil3ntec.tohid.core.i18n

/**
 *  کارتِ هدیه و اعتبارِ فروشگاه به‌عنوانِ روشِ پرداخت (PAY-24) در صندوقِ گوشی و کامپیوتر (`GiftCardBar`، پنلِ پرداخت) و
 *  تنظیماتِ همین دستگاه؛ شارژ، باطل، برگهٔ چاپی و بدهیِ کارت‌ها (PRM-14).
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
  fun sold(code: Any, amount: Any): String = t("کارتِ ${asIs(code)} با ${asIs(amount)} افغانی فروخته شد", "Card $code sold with $amount AFN", "کارت $code په $amount افغانۍ وپلورل شو")
  val lookup: String get() = t("ببین", "Look up", "وګورئ")
  fun balance(amount: Any): String = t("مانده: ${asIs(amount)} افغانی", "Balance: $amount AFN", "پاتې: $amount افغانۍ")
  fun issuedWith(amount: Any, date: Any): String = t("فروخته با ${asIs(amount)} افغانی — ${asIs(date)}", "Sold with $amount AFN — $date", "په $amount افغانۍ پلورل شوی — $date")
  fun spentBack(spent: Any, back: Any): String = t("خرج: ${asIs(spent)} — برگشت: ${asIs(back)}", "Spent: $spent — Returned: $back", "لګښت: $spent — بېرته: $back")
  val isVoid: String get() = t("این کارت باطل شده است", "This card has been voided", "دا کارت باطل شوی")
  val review: String get() = t(
    "نیازمندِ بررسی: از دو دستگاهِ بی‌اینترنت بیش از مانده خرج شده (ماندهٔ منفی).",
    "Needs review: spent beyond its balance from two offline devices (negative balance).",
    "کتنې ته اړتیا: له دوو بې‌انټرنېټه وسیلو له پاتې زیات لګول شوی (منفي پاتې).",
  )
  val close: String get() = t("بستن", "Close", "بندول")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")

  // ── شارژِ دوباره، باطل، برگهٔ چاپی و بدهی (PRM-14) ─────────────────
  val recharge: String get() = t("شارژِ کارت", "Recharge card", "کارت چارج کړئ")
  val rechargeNote: String get() = t(
    "مبلغ نقد به دخل می‌آید و به ماندهٔ همین کارت افزوده می‌شود.",
    "The amount comes into the drawer as cash and is added to this card's balance.",
    "اندازه نغده دخل ته راځي او د همدې کارت پر پاتې ورزیاتېږي.",
  )
  val rechargeSave: String get() = t("ثبتِ شارژ", "Save recharge", "چارج ثبت کړئ")
  fun recharged(code: Any, amount: Any): String = t("کارتِ ${asIs(code)} با ${asIs(amount)} افغانی شارژ شد", "Card $code recharged with $amount AFN", "کارت $code په $amount افغانۍ چارج شو")
  fun rechargedTotal(amount: Any): String = t("شارژ: ${asIs(amount)}", "Recharged: $amount", "چارج: $amount")
  val voidCard: String get() = t("باطل کردنِ کارت", "Void card", "کارت باطلول")
  val voidReason: String get() = t("دلیلِ باطل کردن", "Reason for voiding", "د باطلولو لامل")
  fun voidNote(amount: Any): String = t(
    "ماندهٔ ${asIs(amount)} افغانی از بدهیِ دکان کنار می‌رود و نقدی پس داده نمی‌شود. این کار برنمی‌گردد.",
    "The balance of $amount AFN leaves the shop's debt and no cash is paid back. This cannot be undone.",
    "د $amount افغانیو پاتې د دوکان له پور څخه وځي او نغدې بېرته نه ورکول کېږي. دا کار بېرته نه ګرځي.",
  )
  val voidSave: String get() = t("باطل شود", "Void it", "باطل یې کړئ")
  fun voided(code: Any): String = t("کارتِ ${asIs(code)} باطل شد", "Card $code voided", "کارت $code باطل شو")
  val printCard: String get() = t("چاپِ کارت", "Print card", "کارت چاپ کړئ")
  val sheetTitle: String get() = t("کارتِ هدیه", "Gift card", "د ډالۍ کارت")
  fun sheetAmount(amount: Any): String = t("مبلغ: ${asIs(amount)} افغانی", "Amount: $amount AFN", "اندازه: $amount افغانۍ")
  val sheetNote: String get() = t(
    "این کد مثلِ پول است؛ به کسی نشان ندهید. ماندهٔ کارت را از دکان بپرسید.",
    "This code is like cash; do not show it to anyone. Ask the shop for the card balance.",
    "دا کوډ د پیسو په څېر دی؛ چا ته یې مه ښیئ. د کارت پاتې له دوکان وپوښتئ.",
  )
  val sheetOpened: String get() = t("برگهٔ چاپیِ کارت باز شد", "The card sheet is open for printing", "د کارت چاپي پاڼه پرانیستل شوه")
  val printed: String get() = t("کارت چاپ شد", "Card printed", "کارت چاپ شو")
  fun printFailed(why: Any): String = t("کارت چاپ نشد: ${asIs(why)}", "The card was not printed: $why", "کارت چاپ نشو: $why")
  val liability: String get() = t("بدهیِ کارت‌ها", "Gift card liability", "د کارتونو پور")
  val liabilityTotal: String get() = t("بدهیِ دکان به دارندگانِ کارت", "Shop's debt to card holders", "د کارت لرونکو ته د دوکان پور")
  val issuedWord: String get() = t("صدور", "Issued", "صادر شوي")
  val rechargedWord: String get() = t("شارژ", "Recharged", "چارج شوي")
  val redeemedWord: String get() = t("خرج", "Spent", "لګول شوي")
  val refundedWord: String get() = t("برگشت به کارت", "Returned to cards", "کارت ته بېرته")
  val voidedWord: String get() = t("باطل (کنار رفته)", "Voided (removed)", "باطل شوي (لرې شوي)")
  val netWord: String get() = t("ماندهٔ کارت‌های فعال", "Balance of active cards", "د فعالو کارتونو پاتې")
  val overdrawnWord: String get() = t("ماندهٔ منفیِ نیازمندِ بررسی", "Negative balance needing review", "منفي پاتې چې کتنې ته اړتیا لري")
  fun cardCounts(active: Any, void: Any): String = t("${asIs(active)} کارتِ فعال — ${asIs(void)} باطل", "$active active cards — $void voided", "$active فعال کارتونه — $void باطل")
  val reconciled: String get() = t(
    "صدور + شارژ − خرج + برگشت − باطل با ماندهٔ کارت‌ها می‌خواند.",
    "Issued + recharged − spent + returned − voided matches the card balances.",
    "صادر + چارج − لګښت + بېرته − باطل د کارتونو له پاتې سره سمون خوري.",
  )
  val notReconciled: String get() = t("جمعِ دفتر با مانده‌ها نمی‌خواند — بررسی کنید", "Ledger totals do not match the balances — check", "د دفتر ټولګه له پاتې سره سمون نه خوري — وګورئ")
  val noCards: String get() = t("هنوز کارتی فروخته نشده است", "No card has been sold yet", "تر اوسه کوم کارت نه دی پلورل شوی")

  fun cardLeft(amount: Any): String = t("ماندهٔ کارت: ${asIs(amount)}", "Card balance: $amount", "د کارت پاتې: $amount")
  fun creditLeft(amount: Any): String = t("اعتبارِ مشتری: ${asIs(amount)}", "Customer credit: $amount", "د پېرودونکي اعتبار: $amount")

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
  val errNotAllowed: String get() = t("باطل کردنِ کارت اجازهٔ صاحب یا مدیر می‌خواهد", "Voiding a card needs the owner's or a manager's permission", "د کارت باطلول د خاوند یا مدیر اجازه غواړي")
  val errReason: String get() = t("دلیل را بنویسید (دست‌کم سه حرف)", "Write the reason (at least three letters)", "لامل ولیکئ (لږ تر لږه درې توري)")
  val errTooLong: String get() = t("متن بیش از اندازه بلند است", "The text is too long", "متن ډېر اوږد دی")
  val errGrantNotAllowed: String get() = t(
    "دادنِ اعتبارِ هدیه اجازهٔ تخفیف به همین اندازه می‌خواهد (صاحب یا مدیر)",
    "Giving store credit needs permission to discount this much (owner or a manager)",
    "د ډالۍ اعتبار ورکول د همدومره تخفیف اجازه غواړي (خاوند یا مدیر)",
  )

  //  PRM-15 — اعطای دستیِ اعتبار در پروندهٔ مشتری (`StoreCreditGrantButton`)
  val grantTitle: String get() = t("دادنِ اعتبارِ هدیه", "Give store credit", "د ډالۍ اعتبار ورکول")
  val grantReason: String get() = t("دلیل (مثلاً هدیه یا جبرانِ تأخیر)", "Reason (for example a gift or making up for a delay)", "لامل (د بېلګې په توګه ډالۍ یا د ځنډ جبران)")
  val grantNote: String get() = t(
    "اعتبار بی نقد داده می‌شود و دخل دست نمی‌خورد؛ اگر مشتری بدهکار است، اول از بدهی‌اش کم می‌شود. با نامِ شما و دلیل در سابقه می‌ماند.",
    "The credit is given without cash and the drawer is not touched; if the customer owes money, it first reduces the debt. It stays in the log with your name and the reason.",
    "اعتبار بې نغدو ورکول کېږي او دخل ته لاس نه وړل کېږي؛ که پېرودونکی پوروړی وي، لومړی یې له پور کمېږي. ستاسو له نوم او لامل سره په سابقه کې پاتې کېږي.",
  )
  val grantSave: String get() = t("ثبتِ اعتبار", "Give credit", "اعتبار ثبت کړه")
  fun debtNow(amount: Any): String = t("بدهیِ مشتری: ${asIs(amount)}", "Customer owes: $amount", "د پېرودونکي پور: $amount")
  fun afterCredit(amount: Any): String = t("پس از آن اعتبارِ مشتری: ${asIs(amount)}", "Afterwards the customer's credit: $amount", "وروسته د پېرودونکي اعتبار: $amount")
  fun afterDebt(amount: Any): String = t("پس از آن بدهیِ مشتری: ${asIs(amount)}", "Afterwards the customer owes: $amount", "وروسته د پېرودونکي پور: $amount")
  fun granted(name: Any, amount: Any): String = t(
    "به «${asIs(name)}» ${asIs(amount)} افغانی اعتبار داده شد", "$amount afghani of credit given to “$name”", "«$name» ته $amount افغانۍ اعتبار ورکړل شو",
  )
}
