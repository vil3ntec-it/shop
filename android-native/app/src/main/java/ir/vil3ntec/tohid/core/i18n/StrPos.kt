package ir.vil3ntec.tohid.core.i18n

/**
 *  صندوقِ نسخهٔ ۲: پنلِ پرداختِ ترکیبی، تخفیف و تأییدِ PIN، سبد و کاشیِ کالا، و رسیدِ برگشت در فاکتورِ چاپی.
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrPos {

  // صندوقِ کامپیوتر
  val discountForbidden: String get() = t("این تخفیف برای شما ممنوع است", "You're not allowed to give this discount", "تاسو ته د دې تخفیف اجازه نشته")
  fun saleRecorded(a: Any): String = t("فروش $a ؋ ثبت شد", "Sale of $a ؋ recorded", "د $a ؋ پلور ثبت شو")
  fun changeDue(a: Any): String = t("پولِ پس: $a ؋", "Change: $a ؋", "بېرته پیسې: $a ؋")
  val f8Print: String get() = t("F8 چاپ", "F8 to print", "F8 چاپ")
  val keysLineF1: String get() = t("Enter افزودن · ↑↓ ردیف · + − تعداد · Delete حذف · F3 قیمت/تعداد · F4 قلمِ آزاد · F2 مشتری · F9 ثبت · F8 چاپِ آخر · Esc پاک · F1 همهٔ کلیدها", "Enter add · ↑↓ row · + − qty · Delete remove · F3 price/qty · F4 custom item · F2 customer · F9 record · F8 print last · Esc clear · F1 all keys", "Enter اضافه · ↑↓ کرښه · + − شمېر · Delete لرې کول · F3 بیه/شمېر · F4 آزاد توکی · F2 پیرودونکی · F9 ثبت · F8 وروستی چاپ · Esc پاکول · F1 ټولې کیلي")
  val emptyCart: String get() = t("خالی", "Clear", "تش یې کړئ")
  val needsApproval: String get() = t("نیازمند تأیید", "Needs approval", "تایید ته اړتیا لري")
  val requestApprovalF9: String get() = t("درخواستِ تأیید (F9)", "Request approval (F9)", "د تایید غوښتنه (F9)")
  val approved: String get() = t("تأیید شد", "Approved", "تایید شو")
  val mixedPayHint: String get() = t("پرداختِ ترکیبی: F9 پنلِ پرداخت (نقد، کیف پول، کارت، حواله، به حسابِ مشتری) — F12 نقدِ دقیق", "Mixed payment: F9 payment panel (cash, wallet, card, transfer, customer account) — F12 exact cash", "ګډه ورکړه: F9 د ورکړې پاڼه (نغد، بټوه، کارت، حواله، د پیرودونکي حساب) — F12 دقیق نغد")
  val paidNowShort: String get() = t("دریافتیِ همین حالا", "Received now", "همدا اوس ترلاسه شوې")
  val approveDiscount: String get() = t("تأییدِ تخفیف", "Approve discount", "د تخفیف تایید")
  fun discountSummary(d: Any, s: Any): String = t("تخفیفِ $d ؋ روی فاکتورِ $s ؋", "Discount of $d ؋ on an invoice of $s ؋", "د $s ؋ بل باندې $d ؋ تخفیف")
  fun cashierCap(p: Any): String = t("سقفِ صندوقدار $p٪", "cashier limit $p%", "د صندوقدار حد $p٪")
  fun weighedAdded(n: Any, q: Any): String = t("$n — $q به سبد اضافه شد", "$n — $q added to the cart", "$n — $q ټوکرۍ ته اضافه شو")

  // پنلِ پرداخت
  val payPanel: String get() = t("پنلِ پرداخت", "Payment panel", "د ورکړې پاڼه")
  val cashReceived: String get() = t("نقدِ دریافتی", "Cash received", "ترلاسه شوې نغدې")
  val enterExactHint: String get() = t("Enter روی کادرِ خالی = نقدِ دقیق", "Enter on an empty box = exact cash", "په تش بکس کې Enter = دقیق نغد")
  val exactCash: String get() = t("نقدِ دقیق", "Exact cash", "دقیق نغد")
  val reference: String get() = t("شمارهٔ مرجع", "Reference number", "د مرجع شمېره")
  val amount: String get() = t("مبلغ", "Amount", "مبلغ")
  val removeMethod: String get() = t("حذفِ این روش", "Remove this method", "دا لاره لرې کړئ")
  val addMethod: String get() = t("+ روشِ دیگر (PgDn)", "+ Another method (PgDn)", "+ بله لاره (PgDn)")
  val changeCashOnly: String get() = t("پولِ پس (فقط از نقد)", "Change (from cash only)", "بېرته پیسې (یوازې له نغدو)")
  fun toAccountOf(n: Any): String = t("به حسابِ $n", "To $n's account", "د $n حساب ته")
  val customer: String get() = t("مشتری", "Customer", "پیرودونکی")
  val nonCashPending: String get() = t("غیرنقد: ثبتِ دستی، در انتظارِ تأیید", "Non-cash: manual entry, pending confirmation", "غیرنغد: لاسي ثبت، د تایید په تمه")
  fun customerRestToAccount(n: Any): String = t("مشتری: $n — باقی‌مانده به حسابش می‌رود", "Customer: $n — the rest goes on their account", "پیرودونکی: $n — پاتې یې حساب ته ځي")
  val noCustomerHint: String get() = t("بی مشتری — برای پرداختِ جزئی پیش از F9 با F2 مشتری بگذارید", "No customer — for a partial payment, add a customer with F2 before F9", "بې پیرودونکي — د برخه‌یي ورکړې لپاره له F9 مخکې په F2 پیرودونکی وټاکئ")
  val cancelEsc: String get() = t("انصراف (Esc)", "Cancel (Esc)", "لغوه (Esc)")
  val recordSaleEnter: String get() = t("ثبتِ فروش (Enter)", "Record sale (Enter)", "پلور ثبت کړئ (Enter)")

  // سبد و کاشیِ کالا
  val inCartColon: String get() = t("در سبد:", "in cart:", "په ټوکرۍ کې:")
  val inCart: String get() = t("در سبد", "In cart", "په ټوکرۍ کې")
  val pickCustomer: String get() = t("انتخابِ مشتری", "Choose customer", "پیرودونکی وټاکئ")
  val removeCustomer: String get() = t("برداشتنِ مشتری", "Remove customer", "پیرودونکی لرې کړئ")
  val scanHintCard: String get() = t("بارکد را بخوانید، نام را بنویسید و Enter بزنید، یا روی کارتِ کالا بزنید.", "Scan a barcode, type a name and press Enter, or tap an item card.", "بارکوډ ولولئ، نوم ولیکئ او Enter ووهئ، یا د توکي پر کارت ټک وکړئ.")
  val recordSaleBtn: String get() = t("ثبتِ فروش", "Record sale", "پلور ثبت کړئ")
  val printLastInvoice: String get() = t("چاپِ فاکتورِ آخر", "Print last invoice", "وروستی بل چاپ کړئ")

  // تخفیف
  val autoDiscount: String get() = t("تخفیفِ خودکار", "Automatic discount", "اتومات تخفیف")
  fun promoA11y(n: Any, a: Any): String = t("تخفیفِ $n: $a افغانی", "Discount $n: $a AFN", "تخفیف $n: $a افغانۍ")
  val discountCode: String get() = t("کدِ تخفیف", "Discount code", "د تخفیف کوډ")
  val apply: String get() = t("اعمال", "Apply", "پلي کړئ")
  fun removeCode(c: Any): String = t("برداشتنِ کدِ $c", "Remove code $c", "$c کوډ لرې کړئ")

  // تأییدِ PIN
  val deviceNotLinked: String get() = t("این دستگاه هنوز به دکان وصل نشده است؛ یک بار آنلاین وارد شوید.", "This device isn't linked to a shop yet; sign in online once.", "دا وسیله لا له دوکان سره نه ده تړل شوې؛ یو ځل آنلاین ننوځئ.")
  val noApprovers: String get() = t("روی این دستگاه هیچ مدیر یا صاحبِ دکانی PIN و کلیدِ تأییدِ ثبت‌شده ندارد. از «کارمندان ← PIN و کلیدِ تأیید» یک بار آنلاین بسازید.", "No manager or shop owner has a PIN and approval key registered on this device. Create one once online under “Staff → PIN and approval key”.", "په دې وسیله کې هېڅ مدیر یا د دوکان مالک ثبت شوی PIN او د تایید کیلي نه لري. یو ځل آنلاین یې له «کارکوونکي ← PIN او د تایید کیلي» جوړ کړئ.")
  val approver: String get() = t("تأییدکننده", "Approver", "تاییدوونکی")
  val reasonKept: String get() = t("دلیل (در سابقه می‌ماند)", "Reason (kept in the log)", "لامل (په تاریخچه کې پاتې کېږي)")
  fun pinOf(n: Any): String = t("PINِ $n", "$n's PIN", "د $n PIN")
  val writeReasonFirst: String get() = t("اول دلیل را بنویسید", "Write the reason first", "لومړی لامل ولیکئ")
  fun sixDigitPinOf(n: Any): String = t("PINِ شش‌رقمیِ $n", "$n's six-digit PIN", "د $n شپږ عدده PIN")
  fun pinLocked(n: Any, max: Any): String = t("PINِ $n پس از $max خطا قفل شد؛ با ورودِ آنلاین PINِ تازه بگذارد.", "$n's PIN is locked after $max wrong tries; they need to set a new PIN by signing in online.", "د $n PIN له $max غلطو هڅو وروسته قفل شو؛ په آنلاین ننوتلو دې نوی PIN کېږدي.")
  fun pinWrong(left: Any): String = t("PIN درست نیست — $left تلاش مانده", "Wrong PIN — $left ${pl(left, "try", "tries")} left", "PIN سم نه دی — $left هڅې پاتې دي")
  fun pinA11y(n: Any, len: Any): String = t("$n رقم از $len", "$n of $len digits", "$n له $len عددونو")

  // رسیدِ برگشت (فاکتورِ چاپی)
  val showQrToReturn: String get() = t("برای برگشت، همین QR را نشان دهید", "To return items, show this QR", "د بېرته ورکولو لپاره همدا QR وښایاست")
  val returnDocNotFound: String get() = t("سندِ برگشت پیدا نشد", "Return document not found", "د بېرته ورکړې سند ونه موندل شو")
  fun returnReceipt(l: Any): String = t("رسید برگشت $l", "Return receipt $l", "د بېرته ورکړې رسید $l")
  fun fromInvoice(n: Any): String = t("از فاکتور #$n", "From invoice #$n", "له #$n بل څخه")
  val returnNoReceipt: String get() = t("برگشتِ بی رسید", "Return without receipt", "بې رسیده بېرته ورکړه")
  val returnAmount: String get() = t("مبلغ برگشت", "Return amount", "د بېرته ورکړې مبلغ")
  val offDebt: String get() = t("کم از بدهی", "Taken off the debt", "له پور څخه کم شو")
  val storeCredit: String get() = t("اعتبار فروشگاه", "Store credit", "د دوکان اعتبار")
  fun refundTo(m: Any): String = t("پس به $m", "Refunded to $m", "$m ته بېرته")
  val cashRefunded: String get() = t("پس‌داده نقد", "Refunded in cash", "نغد بېرته ورکړل شوي")
  fun newPurchaseInvoice(n: Any): String = t("خرید تازه — فاکتور #$n", "New purchase — invoice #$n", "نوی پېرود — بل #$n")
  val newPurchaseTotal: String get() = t("جمع خرید تازه", "New purchase total", "د نوي پېرود ټول")
  val collectDifference: String get() = t("دریافت تفاوت", "Collect the difference", "توپیر ترلاسه کړئ")
  val payDifference: String get() = t("پرداخت تفاوت", "Pay the difference", "توپیر ورکړئ")
  fun reasonLine(r: Any): String = t("دلیل: $r", "Reason: $r", "لامل: $r")
  fun approvedBy(n: Any): String = t("تأیید: $n", "Approved by: $n", "تایید: $n")
  fun recordedBy(n: Any): String = t("ثبت: $n", "Recorded by: $n", "ثبت: $n")
  val returnSeparate: String get() = t("سند برگشت جدا ثبت شد؛ فاکتور اصلی دست نخورده است", "The return was recorded separately; the original invoice is unchanged", "د بېرته ورکړې سند جلا ثبت شو؛ اصلي بل نه دی بدل شوی")
}
