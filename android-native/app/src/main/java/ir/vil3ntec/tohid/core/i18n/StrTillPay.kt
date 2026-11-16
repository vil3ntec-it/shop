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

  // گامِ پرداخت (`PayStep`، e-paystep): «پرداخت» ⇒ هر پرداخت جدا تأیید ⇒ فروش فقط پس از پرداختِ کامل
  fun payAmount(a: Any): String = t("پرداخت ${asIs(a)}", "Pay $a", "ورکړه $a")
  val payTitle: String get() = t("پرداخت", "Payment", "ورکړه")
  val amountDue: String get() = t("مبلغِ قابلِ پرداخت", "Amount due", "د ورکړې مبلغ")
  val remainingDue: String get() = t("باقی‌مانده", "Remaining", "پاتې")
  fun paidSummary(total: Any, paid: Any): String = t(
    "جمعِ فاکتور ${asIs(total)} · دریافت‌شده ${asIs(paid)}",
    "Invoice total $total · received $paid",
    "د بل ټول $total · ترلاسه شوي $paid",
  )
  val paidTitle: String get() = t("پرداخت‌شده‌ها", "Payments taken", "ترلاسه شوې ورکړې")
  val otherForRest: String get() = t("روشِ دیگر برای باقی‌مانده", "Another method for the rest", "د پاتې لپاره بله طریقه")
  val pickMethod: String get() = t("روشِ پرداخت را انتخاب کنید", "Choose how the customer pays", "د ورکړې طریقه وټاکئ")
  fun cashBlankExact(a: Any): String = t("خالی = دقیق (${asIs(a)})", "Empty = exact ($a)", "تش = سم ($a)")
  fun changeDue(a: Any): String = t("پولِ پس: ${asIs(a)}", "Change: $a", "بېرته پیسې: $a")
  val received: String get() = t("دریافت شد", "Received", "ترلاسه شو")
  fun cardCharge(a: Any): String = t(
    "${asIs(a)} را روی کارت‌خوان بزنید؛ وقتی کارت‌خوان تأیید کرد، دکمهٔ زیر را بزنید",
    "Charge $a on the card terminal; when it approves, tap the button below",
    "$a په کارت لوستونکي ووهئ؛ کله چې کارت لوستونکي تایید کړ، لاندې تڼۍ ووهئ",
  )
  val cardApproved: String get() = t("کارت‌خوان تأیید کرد", "Terminal approved", "کارت لوستونکي تایید کړ")
  val approvalOptional: String get() = t("شمارهٔ تأیید (اختیاری)", "Approval no. (optional)", "د تایید شمېره (اختیاري)")
  val confirmPaid: String get() = t("تأییدِ پرداخت", "Confirm payment", "ورکړه تایید کړئ")
  val toAccountButton: String get() = t("ثبت به حسابِ مشتری", "Put on customer's account", "د پیرودونکي حساب ته ثبت")
  fun toAccountLine(a: Any, name: Any): String = t(
    "${asIs(a)} به حسابِ «${asIs(name)}» می‌رود",
    "$a goes on “$name”'s account",
    "$a د «$name» حساب ته ځي",
  )
  val creditPickFirst: String get() = t("نسیه فقط با مشتری — اول مشتری را انتخاب کنید", "Credit needs a customer — choose one first", "پور یوازې له پیرودونکي سره — لومړی پیرودونکی وټاکئ")
  val backToCart: String get() = t("بازگشت به سبد", "Back to cart", "سبد ته بېرته")
  fun removePaid(name: Any): String = t("برداشتنِ ${asIs(name)}", "Remove $name", "$name لرې کړئ")
  val recordPaid: String get() = t("ثبتِ فروش (پرداخت دریافت شد)", "Record sale (payment received)", "پلور ثبت کړئ (ورکړه ترلاسه شوه)")
  val paidWaiting: String get() = t(
    "پول گرفته شد ولی فروش هنوز ثبت نشده — «ثبتِ فروش» را بزنید؛ پرداختِ دوباره لازم نیست",
    "The money was taken but the sale isn't recorded yet — tap “Record sale”; no second payment",
    "پیسې واخیستل شوې خو پلور لا نه دی ثبت — «پلور ثبت کړئ» ووهئ؛ بیا ورکړه نه غواړي",
  )

  //  عکسِ QR ِ حساب‌پیِ دکان (صاحب‌کار ۱۴۰۵/۰۸/۲۶ — «عکسِ QR ِ حساب‌پیم را بار می‌کنم»)
  val pickQrImage: String get() = t("انتخابِ عکسِ QR", "Choose QR picture", "د QR انځور وټاکئ")
  val shopQrImage: String get() = t("عکسِ QR ِ حساب‌پیِ دکان", "The shop's HesabPay QR picture", "د دوکان د حساب‌پې QR انځور")
  val qrImageReady: String get() = t(
    "QR ِ عکس خوانده شد — پس از ذخیره همین عکس در صندوق و نمایشگرِ مشتری بزرگ نشان داده می‌شود",
    "The picture's QR was read — after saving, this picture is shown large at the till and on the customer display",
    "د انځور QR ولوستل شو — له ساتلو وروسته همدا انځور په صندوق او د پیرودونکي پرده کې لوی ښودل کېږي",
  )
  val qrImageNoQr: String get() = t(
    "در این عکس QR ای خوانده نشد؛ اسکرین‌شاتِ QR یا عکسِ روشن‌ترش را بدهید",
    "No QR could be read in this picture; use a screenshot of the QR or a clearer photo",
    "په دې انځور کې QR ونه لوستل شو؛ د QR سکرین‌شاټ یا روښانه انځور ورکړئ",
  )
  val qrImageHas: String get() = t(
    "عکسِ QR ِ دکان در صندوق نشان داده می‌شود؛ عکسِ تازه همان ۲۴ ساعتِ حسابِ تازه را دارد",
    "The shop's QR picture is shown at the till; a new picture waits the same 24 hours as a new account",
    "د دوکان QR انځور په صندوق کې ښودل کېږي؛ نوی انځور د نوي حساب په څېر ۲۴ ساعته انتظار کوي",
  )
  fun qrImagePending(at: String): String = t(
    "عکسِ QR ِ تازه از ${asIs(at)} در صندوق نشان داده می‌شود؛ تا آن دم همان QR ِ پیشین",
    "The new QR picture shows at the till from $at; until then the previous QR",
    "نوی QR انځور له $at څخه په صندوق کې ښودل کېږي؛ تر هغه پخوانی QR",
  )

  // روش‌های پرداختِ صندوق (`core/pay/TillMethods`، e-paymethods)
  val methodsTitle: String get() = t("روشِ پرداخت", "Payment method", "د ورکړې طریقه")
  val tileCash: String get() = t("نقد", "Cash", "نغد")
  val tileCard: String get() = t("کارت", "Card", "کارت")
  val tileCardSub: String get() = t("کارت‌خوان", "Card terminal", "کارت لوستونکی")
  val tileHesab: String get() = t("حساب‌پی", "HesabPay", "حساب‌پې")
  val tileHesabSub: String get() = t("QR / NFC", "QR / NFC", "QR / NFC")
  val tileWallet: String get() = t("کیف پول", "Wallet", "بټوه")
  val tileTransfer: String get() = t("حواله", "Transfer", "حواله")
  val tileCredit: String get() = t("نسیه", "On credit", "پور")
  val tileCreditSub: String get() = t("به حسابِ مشتری", "To customer's account", "د پیرودونکي حساب ته")
  val tileSoon: String get() = t("به‌زودی", "Soon", "ژر")
  /** کاشیِ خاموشِ حساب‌پی: حسابِ تازه در ۲۴ ساعتِ امنیتی (`TillMethods.Hesab.Pending`) */
  fun hesabPending(at: Any): String = t(
    "حساب‌پی هنوز فعال نیست: حسابِ تازهٔ دکان برای امنیت ۲۴ ساعت در انتظار است و از ${asIs(at)} فعال می‌شود.",
    "HesabPay isn't active yet: the shop's new account is on a 24-hour security hold and becomes active at $at.",
    "حساب‌پې لا فعاله نه ده: د دوکان نوی حساب د امنیت لپاره ۲۴ ساعته په تمه دی او له $at څخه فعالېږي.",
  )
  /** فقط صاحبِ دکان: حساب‌پی هنوز راه‌اندازی نشده (`TillMethods.Hesab.Setup`) */
  val hesabSetup: String get() = t("راه‌اندازیِ حساب‌پی", "Set up HesabPay", "حساب‌پې جوړ کړئ")
  val hesabSetupNote: String get() = t(
    "پول مستقیم به حسابِ خودِ دکان — QR و NFC در همین صندوق",
    "Money goes straight to the shop's own account — QR and NFC right at this till",
    "پیسې مستقیم د دوکان خپل حساب ته — QR او NFC په همدې صندوق کې",
  )
  /** کاشیِ کارت: مبلغ روی کارت‌خوان؛ شمارهٔ تأییدِ رسید اختیاری (صاحب‌کار ۱۴۰۵/۰۸/۲۶) */
  val cardRefHint: String get() = t(
    "مبلغ را روی کارت‌خوان بزنید؛ شمارهٔ تأییدِ رسید (اختیاری) را در «شمارهٔ مرجع» بنویسید",
    "Charge the amount on the card terminal; the slip's approval number (optional) goes in “Reference no.”",
    "مبلغ په کارت لوستونکي ووهئ؛ د رسید د تایید شمېره (اختیاري) په «د مرجع شمېره» کې ولیکئ",
  )
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
