package ir.vil3ntec.tohid.core.i18n

/**
 *  صندوق: نسیه فقط با مشتری (`CreditDebtor`) و حساب‌پی با NFC/QR در صندوقِ گوشیِ نسخهٔ ۳ (`HesabTillRoute`، PAY-21).
 *
 *  قاعده‌ها سرِ `I18n.kt`. ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 *  ⚠️ هیچ عبارتی از جدولِ `phrases` ِ کامپیوتر (`DesktopPhrases`) در متنِ فارسیِ این جدول نیست.
 */
@Suppress("unused")
object StrTillPay {

  // نسیه فقط با مشتریِ موجود و فعال
  val creditNeedsCustomer: String get() = t(
    "فروشِ نسیه بی مشتری ثبت نمی‌شود — باقی‌مانده به حسابِ هیچ‌کس نمی‌رفت. مشتری را انتخاب کنید.",
    "A credit sale can't be saved without a customer — the balance would go to no one. Choose the customer.",
    "پور پلور بې پیرودونکي نه ثبتېږي — پاتې پیسې د هېچا حساب ته نه ځي. پیرودونکی وټاکئ.",
  )
  fun unpaidNeedsCustomer(a: Any): String = t(
    "${asIs(a)} ؋ پرداخت نشده — مبلغ را کامل کنید، یا مشتری را انتخاب کنید تا به حسابش برود.",
    "$a ؋ is unpaid — complete the amount, or choose the customer to put it on their account.",
    "$a ؋ نه دي ورکړل شوي — مبلغ بشپړ کړئ، یا پیرودونکی وټاکئ چې د هغه حساب ته لاړ شي.",
  )
  val customerGone: String get() = t(
    "این مشتری در دفتر نیست (پاک شده یا هنوز نرسیده) — مشتریِ دیگری انتخاب کنید.",
    "This customer isn't in the ledger (deleted or not synced yet) — choose another customer.",
    "دا پیرودونکی په دفتر کې نشته (پاک شوی یا لا نه دی رسېدلی) — بل پیرودونکی وټاکئ.",
  )
  fun customerMerged(name: Any, keeper: Any): String = t(
    "«${asIs(name)}» با «${asIs(keeper)}» یکی شده است — برای نسیه «${asIs(keeper)}» را انتخاب کنید.",
    "“$name” was merged into “$keeper” — choose “$keeper” for the credit sale.",
    "«$name» له «$keeper» سره یو شوی — د پور لپاره «$keeper» وټاکئ.",
  )
  /** صندوقِ کامپیوتر: همان «انتخابِ مشتری» با کلیدش */
  val pickCustomerKey: String get() = t("انتخابِ مشتری (F2)", "Choose customer (F2)", "پیرودونکی وټاکئ (F2)")

  // حساب‌پی با NFC/QR در صندوقِ گوشی
  val entry: String get() = t("پرداخت با حساب‌پی (NFC/QR)", "Pay with HesabPay (NFC/QR)", "د حساب‌پې ورکړه (NFC/QR)")
  val entrySimple: String get() = t(
    "پول مستقیم به حسابِ دکان؛ تأیید با «پول رسید» ِ شما",
    "Money goes straight to the shop's account; you confirm with “Money received”",
    "پیسې مستقیم د دوکان حساب ته؛ تاسو یې د «پیسې ورسېدې» په وهلو تاییدوئ",
  )
  val entryAuto: String get() = t(
    "فاکتور با تأییدِ خودِ حساب‌پی بسته می‌شود",
    "The invoice closes when HesabPay itself confirms",
    "فاکتور د حساب‌پې په تایید تړل کېږي",
  )
  val title: String get() = t("پرداخت با حساب‌پی", "Pay with HesabPay", "د حساب‌پې ورکړه")
  val tapNow: String get() = t(
    "گوشیِ مشتری را به پشتِ این دستگاه نزدیک کنید؛ یا مشتری QR را اسکن کند",
    "Hold the customer's phone to the back of this device, or let them scan the QR",
    "د پیرودونکي موبایل د دې وسیلې شا ته نږدې کړئ، یا QR سکن کړي",
  )
  val nfcNone: String get() = t(
    "این دستگاه NFC ندارد — مشتری QR را با برنامهٔ حساب‌پی اسکن کند",
    "This device has no NFC — the customer scans the QR with the HesabPay app",
    "دا وسیله NFC نه لري — پیرودونکی دې QR د حساب‌پې په اپ سکن کړي",
  )
  val customerNeeds: String get() = t(
    "گوشیِ مشتری NFC و برنامهٔ حساب‌پی (یا مرورگر) می‌خواهد.",
    "The customer's phone needs NFC and the HesabPay app (or a browser).",
    "د پیرودونکي موبایل NFC او د حساب‌پې اپ (یا براوزر) ته اړتیا لري.",
  )
  val simpleHonest: String get() = t(
    "حالتِ «ساده»: حساب‌پی به برنامه خبر نمی‌دهد. فقط وقتی پول را در حساب‌پیِ دکان دیدید «پول رسید» را بزنید. تأییدِ خودکار با حسابِ پذیرندهٔ حساب‌پی می‌آید (هنوز آماده نیست).",
    "“Simple” mode: HesabPay doesn't tell the app. Tap “Money received” only after you see the money in the shop's HesabPay. Automatic confirmation comes with a HesabPay merchant account (not ready yet).",
    "«ساده» حالت: حساب‌پې اپ ته خبر نه ورکوي. یوازې هغه وخت «پیسې ورسېدې» ووهئ چې پیسې مو د دوکان په حساب‌پې کې ولیدې. خپلکاره تایید د حساب‌پې د سوداګر حساب سره راځي (لا چمتو نه دی).",
  )
  val autoHonest: String get() = t(
    "حالتِ «خودکار»: فاکتور فقط وقتی خودش بسته می‌شود که حساب‌پی پرداخت با همین کد، همین مبلغ و همین حسابِ گیرنده را تأیید کند.",
    "“Automatic” mode: the invoice closes by itself only when HesabPay confirms this code, this amount and this payout account.",
    "«خپلکاره» حالت: فاکتور یوازې هغه وخت پخپله تړل کېږي چې حساب‌پې همدا کوډ، همدا مبلغ او همدا اخیستونکی حساب تایید کړي.",
  )
  val confirmed: String get() = t("پرداخت تأیید شد — ثبتِ فروش…", "Payment confirmed — saving the sale…", "ورکړه تایید شوه — پلور ثبتېږي…")
  val timedOut: String get() = t(
    "زمانِ این پرداخت گذشت — فروش ثبت نشد؛ دوباره بزنید یا روشِ دیگری انتخاب کنید",
    "This payment timed out — the sale wasn't saved; try again or choose another method",
    "د دې ورکړې وخت تېر شو — پلور ثبت نه شو؛ بیا هڅه وکړئ یا بله لار وټاکئ",
  )
  fun review(why: Any): String = t(
    "پولی رسید ولی با این فاکتور نمی‌خواند (${asIs(why)}) — فاکتور خودکار بسته نشد؛ صاحب یا مدیر در «پرداخت‌های حساب‌پی» بررسی کند.",
    "A payment arrived but doesn't match this invoice ($why) — the invoice wasn't closed automatically; the owner or a manager should review it.",
    "یوه ورکړه ورسېده خو له دې فاکتور سره سمون نه لري ($why) — فاکتور پخپله ونه تړل شو؛ مالک یا مدیر دې یې وګوري.",
  )
  val whyOtherSale: String get() = t("فاکتورِ دیگر", "another invoice", "بل فاکتور")
  val whyAmount: String get() = t("مبلغِ دیگر", "a different amount", "بل مبلغ")
  val whyPayee: String get() = t("حسابِ گیرندهٔ دیگر", "a different payout account", "بل اخیستونکی حساب")
  val whyPayeeUnknown: String get() = t("حسابِ گیرنده معلوم نیست", "payout account unknown", "اخیستونکی حساب نامعلوم دی")
  val whyFlagged: String get() = t("نیازمندِ بررسی", "needs review", "کتنې ته اړتیا")
  val whyNotPaid: String get() = t("پرداخت‌نشده", "not paid", "نه ده ورکړل شوې")
}
