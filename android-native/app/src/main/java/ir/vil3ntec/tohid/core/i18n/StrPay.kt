package ir.vil3ntec.tohid.core.i18n

/**
 *  پرداختِ درگاهی در صندوق: حساب‌پی (QR و NFC)، نمایشگرِ مشتری و کارت‌خوان (PAY-21، PAY-08).
 *
 *  قاعده‌ها سرِ `I18n.kt`. ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 *  ⚠️ هیچ عبارتی از جدولِ `phrases` ِ کامپیوتر (`DesktopPhrases`) در متنِ فارسیِ این جدول نیست.
 */
@Suppress("unused")
object StrPay {

  // دکمه و برگهٔ حساب‌پی
  val payWithHesab: String get() = t("پرداخت با حساب‌پی", "Pay with HesabPay", "د حساب‌پې له لارې ورکړه")
  val resumeHesab: String get() = t("ادامهٔ پرداختِ حساب‌پی", "Continue the HesabPay payment", "د حساب‌پې ورکړه پسې ونیسئ")
  fun coversRest(a: Any): String = t("حساب‌پی باقی‌مانده را می‌پردازد: $a ؋", "HesabPay pays the rest: $a ؋", "حساب‌پې پاتې برخه ورکوي: $a ؋")
  val hesabTitle: String get() = t("حساب‌پی", "HesabPay", "حساب‌پې")
  val scanHint: String get() = t(
    "مشتری این کد را با برنامهٔ حساب‌پی اسکن کند — با کیف پولِ حساب‌پی یا کارتِ بانکیِ AfPay",
    "The customer scans this code with the HesabPay app — HesabPay wallet or AfPay bank card",
    "پیرودونکی دا کوډ د حساب‌پې په اپ سکن کړي — د حساب‌پې بټوه یا د AfPay بانکي کارت",
  )
  val tapHint: String get() = t("یا گوشیِ مشتری را به این دستگاه نزدیک کنید", "Or hold the customer's phone near this device", "یا د پیرودونکي موبایل دې وسیلې ته نږدې کړئ")
  val nfcOff: String get() = t("NFC خاموش است — برای لمسِ گوشیِ مشتری روشنش کنید", "NFC is off — turn it on so the customer can tap", "NFC بند دی — چالان یې کړئ چې پیرودونکی خپل موبایل ولګوي")
  val nfcSettings: String get() = t("تنظیماتِ NFC", "NFC settings", "د NFC امستنې")
  fun expiresIn(left: Any): String = t("مهلت: $left", "Expires in $left", "پای ته وخت: $left")

  // وضعیت
  val creating: String get() = t("در حالِ ساختنِ پرداخت…", "Creating the payment…", "ورکړه جوړېږي…")
  val waiting: String get() = t("در انتظارِ پرداختِ مشتری", "Waiting for the customer to pay", "د پیرودونکي ورکړې ته انتظار")
  val reconnecting: String get() = t("اتصال قطع شد — دوباره امتحان می‌شود…", "Connection lost — retrying…", "اړیکه پرې شوه — بیا هڅه کېږي…")
  val paidRecording: String get() = t("پرداخت شد — فروش ثبت می‌شود…", "Paid — recording the sale…", "ورکړه وشوه — پلور ثبتېږي…")
  val recordSale: String get() = t("ثبتِ فروش", "Record the sale", "پلور ثبت کړئ")
  val cancel: String get() = t("لغو", "Cancel", "لغوه")
  val close: String get() = t("بستن", "Close", "بندول")
  val retry: String get() = t("دوباره", "Try again", "بیا هڅه")
  val failed: String get() = t("پرداخت ناموفق بود — روشِ دیگری انتخاب کنید", "The payment failed — choose another method", "ورکړه ناکامه شوه — بله لاره وټاکئ")
  val expired: String get() = t("مهلتِ پرداخت تمام شد — فروش ثبت نشد", "The payment expired — the sale wasn't recorded", "د ورکړې وخت پای ته ورسېد — پلور ثبت نه شو")
  val cancelled: String get() = t("پرداخت لغو شد", "The payment was cancelled", "ورکړه لغوه شوه")
  val refunded: String get() = t("این پرداخت بازپرداخت شده است", "This payment was refunded", "دا ورکړه بېرته ورکړل شوې ده")
  val unknown: String get() = t(
    "وضعیتِ پرداخت هنوز معلوم نیست — فروش ثبت نشد؛ دوباره باز کنید تا همان پرداخت پیگیری شود",
    "The payment status is still unknown — the sale wasn't recorded; reopen to follow the same payment",
    "د ورکړې حالت لا معلوم نه دی — پلور ثبت نه شو؛ بیا یې پرانیزئ چې همدا ورکړه وڅارل شي",
  )
  val lost: String get() = t("سرور این پرداخت را نمی‌شناسد — دوباره بزنید", "The server doesn't know this payment — try again", "سرور دا ورکړه نه پېژني — بیا هڅه وکړئ")
  val disabled: String get() = t("حساب‌پی برای این دکان روشن نیست", "HesabPay isn't enabled for this shop", "حساب‌پې د دې دوکان لپاره فعال نه دی")
  val unreachable: String get() = t("سرور در دسترس نیست — دوباره بزنید", "The server can't be reached — try again", "سرور ته لاس نه رسېږي — بیا هڅه وکړئ")
  val providerDown: String get() = t("درگاهِ حساب‌پی پاسخ نداد — دوباره بزنید", "HesabPay didn't respond — try again", "حساب‌پې ځواب ورنه کړ — بیا هڅه وکړئ")
  val alreadyPaid: String get() = t(
    "این فاکتور پیش‌تر با حساب‌پی پرداخت شده ولی آن پرداخت روی این دستگاه نیست — به مدیر خبر دهید",
    "This invoice was already paid with HesabPay, but that payment isn't on this device — tell the manager",
    "دا بل مخکې د حساب‌پې له لارې ورکړل شوی، خو هغه ورکړه په دې وسیله کې نشته — مدیر ته خبر ورکړئ",
  )
  val otherOpen: String get() = t(
    "پرداختِ بازِ دیگری برای همین فاکتور هست — پس از پایانِ مهلتش دوباره بزنید",
    "Another open payment exists for this invoice — try again after it expires",
    "د همدې بل لپاره بله پرانیستې ورکړه شته — د هغې له پای وروسته بیا هڅه وکړئ",
  )
  val amountMismatch: String get() = t(
    "مبلغِ پرداختِ حساب‌پی با این فاکتور نمی‌خواند — فروش ثبت نشد؛ به مدیر خبر دهید",
    "The HesabPay amount doesn't match this invoice — the sale wasn't recorded; tell the manager",
    "د حساب‌پې مبلغ له دې بل سره سمون نه لري — پلور ثبت نه شو؛ مدیر ته خبر ورکړئ",
  )
  val nothingDue: String get() = t("چیزی برای پرداخت نمانده", "Nothing is left to pay", "د ورکړې لپاره څه نه دي پاتې")
  val needsReview: String get() = t("این پرداخت دیر رسید و مدیر باید بررسی‌اش کند", "This payment arrived late and needs a manager's review", "دا ورکړه ناوخته ورسېده او مدیر یې باید وګوري")
}
