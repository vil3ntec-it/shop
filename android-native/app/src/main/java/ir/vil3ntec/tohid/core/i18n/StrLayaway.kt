package ir.vil3ntec.tohid.core.i18n

/**
 *  فروشِ امانی با پیش‌پرداخت و جدولِ قسط (POS-27) در صندوقِ هر دو رابط (`LayawayBar`)، پروندهٔ مشتری
 *  (`LayawayCustomerPanel`) و تنظیماتِ همین دستگاه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/pos/Layaway` (commonMain) و `data/Layaways`. ترجمه‌های نامطمئن در
 *  `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrLayaway {
  val settingsTitle: String get() = t("فروشِ امانی و قسطی", "Layaway with installments", "امانتي پلور له قسطونو سره")
  val settingsNote: String get() = t(
    "در صندوق، سبدِ همین فاکتور برای مشتری کنار گذاشته می‌شود: پیش‌پرداخت و هر قسط نقد به دخل و حسابِ او می‌رود. کالا فقط پس از تسویهٔ کامل تحویل می‌شود (فروشِ عادیِ صندوق)؛ لغو همهٔ پرداختی را نقد و با دلیل پس می‌دهد.",
    "At the register, this invoice's cart is set aside for a customer: the deposit and each installment go as cash to the drawer and their account. Goods are handed over only after full payment (a normal register sale); cancelling returns everything paid in cash, with a reason.",
    "په صندوق کې، د همدې بل ټوکرۍ د پېرودونکي لپاره ساتل کېږي: مخکېنۍ ورکړه او هر قسط نغد دخل او د هغه حساب ته ځي. توکي یوازې له بشپړې تصفیې وروسته سپارل کېږي (د صندوق عادي پلور)؛ لغوه ټوله ورکړه نغده او له دلیل سره بېرته ورکوي.",
  )
  val newLayaway: String get() = t("امانی از همین سبد", "Layaway from this cart", "له همدې ټوکرۍ امانت")
  fun list(open: Any, late: Any): String = t("امانی‌ها ($open باز، $late دیر)", "Layaways ($open open, $late late)", "امانتونه ($open پرانیستي، $late ځنډېدلي)")
  val pickCustomerFirst: String get() = t("اول مشتری را برای همین فاکتور انتخاب کنید", "First choose the customer for this invoice", "لومړی د دې بل لپاره پېرودونکی وټاکئ")
  val emptyCart: String get() = t("سبد خالی است", "The cart is empty", "ټوکری تشه ده")
  fun customer(name: Any): String = t("مشتری: $name", "Customer: $name", "پېرودونکی: $name")
  fun cartTotal(lines: Any, total: Any): String = t("$lines قلم — جمع $total ؋", "$lines items — total $total AFN", "$lines توکي — ټوله $total ؋")
  val deposit: String get() = t("پیش‌پرداخت (افغانی)", "Deposit (AFN)", "مخکېنۍ ورکړه (افغانۍ)")
  val count: String get() = t("شمارِ قسط (۱ تا ۳۶)", "Number of installments (1 to 36)", "د قسطونو شمېر (۱ تر ۳۶)")
  val every: String get() = t("فاصلهٔ قسط‌ها (روز)", "Days between installments", "د قسطونو ترمنځ ورځې")
  val firstIn: String get() = t("قسطِ نخست تا چند روز دیگر", "First installment in how many days", "لومړی قسط په څو ورځو کې")
  val scheduleTitle: String get() = t("جدولِ قسط", "Installment schedule", "د قسطونو جدول")
  fun dueLine(no: Any, date: Any, amount: Any): String = t("قسطِ $no — $date — $amount ؋", "Installment $no — $date — $amount AFN", "قسط $no — $date — $amount ؋")
  val cashNote: String get() = t(
    "پیش‌پرداخت و قسط‌ها نقد به دخل می‌آیند و تا تحویل در حسابِ مشتری می‌مانند. موجودی هنگامِ تحویل کم می‌شود.",
    "The deposit and installments are cash into the drawer and stay on the customer's account until handover. Stock goes down at handover.",
    "مخکېنۍ ورکړه او قسطونه نغد دخل ته راځي او تر سپارلو د پېرودونکي په حساب کې پاتې کېږي. موجودي د سپارلو پر مهال کمېږي.",
  )
  val save: String get() = t("ثبتِ امانی و پیش‌پرداخت", "Save layaway and deposit", "امانت او مخکېنۍ ورکړه ثبت کړئ")
  fun saved(deposit: Any): String = t("امانی ثبت شد — پیش‌پرداختِ $deposit ؋ گرفته شد", "Layaway saved — deposit of $deposit AFN taken", "امانت ثبت شو — $deposit ؋ مخکېنۍ ورکړه واخیستل شوه")
  val empty: String get() = t("هنوز امانی‌ای نیست", "No layaways yet", "تر اوسه امانت نشته")
  fun row(customer: Any, lines: Any): String = t("$customer — $lines قلم", "$customer — $lines items", "$customer — $lines توکي")
  fun amounts(total: Any, paid: Any, left: Any): String = t("جمع $total ؋ · پرداختی $paid ؋ · مانده $left ؋", "Total $total AFN · paid $paid AFN · left $left AFN", "ټوله $total ؋ · ورکړې $paid ؋ · پاتې $left ؋")
  fun nextDue(date: Any, amount: Any): String = t("قسطِ بعدی $date: $amount ؋", "Next installment $date: $amount AFN", "بل قسط $date: $amount ؋")
  val stateOpen: String get() = t("باز", "Open", "پرانیستی")
  val statePaidUp: String get() = t("تسویه — آمادهٔ تحویل", "Paid up — ready to hand over", "تصفیه — د سپارلو لپاره چمتو")
  val stateDelivered: String get() = t("تحویل شد", "Handed over", "وسپارل شو")
  val stateCancelled: String get() = t("لغو شد", "Cancelled", "لغوه شو")
  fun late(amount: Any): String = t("دیر: $amount ؋", "Late: $amount AFN", "ځنډېدلی: $amount ؋")
  val review: String get() = t("نیازِ بازبینی", "Needs review", "بیاکتنې ته اړتیا")
  val pay: String get() = t("گرفتنِ قسط", "Take installment", "قسط اخیستل")
  fun payTitle(no: Any): String = t("قسطِ شمارهٔ $no", "Installment payment $no", "د قسط ورکړه $no")
  fun remaining(amount: Any): String = t("مانده: $amount ؋", "Left to pay: $amount AFN", "پاتې: $amount ؋")
  val payAmount: String get() = t("مبلغِ نقد (افغانی)", "Cash amount (AFN)", "نغده اندازه (افغانۍ)")
  val payConfirm: String get() = t("ثبتِ رسید", "Record receipt", "رسید ثبت کړئ")
  fun paid(amount: Any, left: Any): String = t("رسیدِ $amount ؋ ثبت شد — مانده $left ؋", "Receipt of $amount AFN recorded — $left AFN left", "د $amount ؋ رسید ثبت شو — $left ؋ پاتې")
  val payAgain: String get() = t("همین رسید پیش‌تر ثبت شده بود؛ دوباره ثبت نشد", "This receipt was already recorded; not recorded twice", "دا رسید مخکې ثبت شوی و؛ بیا ثبت نه شو")
  val deliver: String get() = t("تحویل", "Hand over", "سپارل")
  val deliverTitle: String get() = t("تحویلِ کالای امانی", "Hand over layaway goods", "د امانت توکو سپارل")
  val deliverBody: String get() = t(
    "فاکتورِ عادیِ صندوق با همان قیمت‌ها ثبت می‌شود و همه از پرداختی‌های مشتری بسته می‌شود؛ موجودی همین حالا کم می‌شود.",
    "A normal register invoice is recorded at the same prices and fully settled from the customer's payments; stock goes down now.",
    "د صندوق عادي بل په همغو بیو ثبتېږي او ټول د پېرودونکي له ورکړو تړل کېږي؛ موجودي همدا اوس کمېږي.",
  )
  val deliverConfirm: String get() = t("ثبتِ فاکتور و تحویل", "Record invoice and hand over", "بل ثبت او وسپارئ")
  fun delivered(label: Any): String = t("تحویل شد — فاکتور #$label", "Handed over — invoice #$label", "وسپارل شو — بل #$label")
  val cancelLayaway: String get() = t("لغو", "Cancel", "لغوه")
  val cancelTitle: String get() = t("لغوِ امانی و بازپرداخت", "Cancel layaway and refund", "د امانت لغوه او بېرته ورکړه")
  fun refund(amount: Any): String = t("بازپرداختِ نقد از دخل: $amount ؋", "Cash refund from the drawer: $amount AFN", "له دخل نغده بېرته ورکړه: $amount ؋")
  val reason: String get() = t("دلیلِ لغو", "Reason for cancelling", "د لغوې دلیل")
  val noCancelRight: String get() = t("لغو و بازپرداخت فقط با مجوزِ «لغوِ فاکتور»", "Cancelling and refunding needs the cancel-invoice right", "لغوه او بېرته ورکړه یوازې د بل لغوې له اجازې سره")
  val cancelConfirm: String get() = t("لغو و بازپرداخت", "Cancel and refund", "لغوه او بېرته ورکړه")
  fun cancelled(amount: Any): String = t("امانی لغو شد — $amount ؋ نقد پس داده شد", "Layaway cancelled — $amount AFN returned in cash", "امانت لغوه شو — $amount ؋ نغد بېرته ورکړل شول")
  val print: String get() = t("چاپِ جدول", "Print schedule", "جدول چاپ کړئ")
  val printed: String get() = t("جدولِ امانی چاپ شد", "Layaway schedule printed", "د امانت جدول چاپ شو")
  val noPrinter: String get() = t("چاپگرِ فاکتور تعریف نشده", "No receipt printer is set up", "د بل چاپګر نه دی ټاکل شوی")
  fun notPrinted(why: Any): String = t("چاپ نشد: $why", "Not printed: $why", "چاپ نه شو: $why")
  fun slipTitle(day: Any): String = t("فروشِ امانی — $day", "Layaway — $day", "امانتي پلور — $day")
  fun slipLine(name: Any, qty: Any, price: Any, total: Any): String = t("$name × $qty @ $price = $total", "$name × $qty @ $price = $total", "$name × $qty @ $price = $total")
  fun slipTotal(amount: Any): String = t("جمع: $amount ؋", "Total: $amount AFN", "ټوله: $amount ؋")
  fun slipPaid(amount: Any): String = t("پرداختی تا امروز: $amount ؋", "Paid so far: $amount AFN", "تر نن ورکړې: $amount ؋")
  fun slipDue(no: Any, date: Any, amount: Any, state: Any): String = t("$no) $date — $amount ؋ — $state", "$no) $date — $amount AFN — $state", "$no) $date — $amount ؋ — $state")
  val duePaid: String get() = t("پرداخت شد", "paid", "ورکړل شو")
  val duePartial: String get() = t("بخشی", "part paid", "برخه")
  val dueDue: String get() = t("مانده", "due", "پاتې")
  val dueLate: String get() = t("دیر", "late", "ځنډېدلی")
  val slipFooter: String get() = t("کالا پس از پرداختِ کامل تحویل می‌شود.", "Goods are handed over after full payment.", "توکي له بشپړې ورکړې وروسته سپارل کېږي.")
  fun customerTitle(open: Any): String = t("فروش‌های امانی ($open باز)", "Layaways ($open open)", "امانتي پلورونه ($open پرانیستي)")
  val close: String get() = t("بستن", "Close", "بندول")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val errNoCustomer: String get() = t("مشتری لازم است", "A customer is required", "پېرودونکی اړین دی")
  val errEmpty: String get() = t("سبد خالی است", "The cart is empty", "ټوکری تشه ده")
  val errTooMany: String get() = t("امانی بیش از ۶۰ قلم نمی‌شود", "A layaway can have at most 60 items", "امانت له ۶۰ توکو ډېر نه شي لرلی")
  val errQty: String get() = t("مقدار یا قیمتِ یک قلم درست نیست", "An item's quantity or price is not valid", "د یوه توکي شمېر یا بیه سمه نه ده")
  val errTotal: String get() = t("جمعِ سبد درست نیست", "The cart total is not valid", "د ټوکرۍ ټوله سمه نه ده")
  val errDeposit: String get() = t("پیش‌پرداخت را درست بنویسید (صفر هم می‌شود)", "Enter a valid deposit (zero is allowed)", "سمه مخکېنۍ ورکړه ولیکئ (صفر هم کېږي)")
  val errWhole: String get() = t("مبلغ‌ها افغانیِ درست باشند، بی اعشار", "Amounts must be whole afghanis, no decimals", "اندازې دې بشپړې افغانۍ وي، بې اعشاریه")
  val errDepositOver: String get() = t("پیش‌پرداخت به اندازهٔ جمع است؛ همین فروشِ نقد است", "The deposit covers the total; make it a cash sale", "مخکېنۍ ورکړه ټوله پوښي؛ نغد پلور یې کړئ")
  val errCount: String get() = t("شمارِ قسط ۱ تا ۳۶ باشد", "Installments must be 1 to 36", "قسطونه دې له ۱ تر ۳۶ وي")
  val errEvery: String get() = t("فاصلهٔ قسط‌ها ۱ تا ۹۲ روز باشد", "Days between installments must be 1 to 92", "د قسطونو ترمنځ ورځې دې له ۱ تر ۹۲ وي")
  val errDue: String get() = t("روزِ قسطِ نخست درست نیست (تا یک سال دیگر)", "The first installment day is not valid (within a year)", "د لومړي قسط ورځ سمه نه ده (تر یو کال)")
  val errPartSmall: String get() = t("هر قسط دست‌کم یک افغانی باشد", "Each installment must be at least one afghani", "هر قسط دې لږ تر لږه یوه افغانۍ وي")
  val errNoProduct: String get() = t("یک کالای سبد دیگر در فهرست نیست", "An item in the cart is no longer in the catalog", "د ټوکرۍ یو توکی نور په لړلیک کې نشته")
  val errState: String get() = t("وضعیتِ امانی همین حالا عوض شده؛ فهرست را دوباره ببینید", "The layaway just changed; check the list again", "امانت همدا اوس بدل شو؛ لړلیک بیا وګورئ")
  val errReview: String get() = t("این امانی نیازِ بازبینیِ مالک دارد؛ چیزی ثبت نشد", "This layaway needs the owner's review; nothing was recorded", "دا امانت د مالک بیاکتنې ته اړتیا لري؛ هېڅ ثبت نه شو")
  val errStale: String get() = t("همین قسط همین حالا در دستگاهِ دیگری ثبت شد؛ دوباره باز کنید", "This installment was just recorded on another device; open it again", "همدا قسط همدا اوس په بل وسیله کې ثبت شو؛ بیا یې پرانیزئ")
  val errAmount: String get() = t("مبلغ را درست بنویسید", "Enter a valid amount", "سمه اندازه ولیکئ")
  val errOver: String get() = t("مبلغ بیش از مانده است؛ بیش از مانده گرفته نمی‌شود", "The amount is more than what is left; no more than that is taken", "اندازه له پاتې ډېره ده؛ له پاتې ډېر نه اخیستل کېږي")
  val errSettled: String get() = t("همهٔ قسط‌ها پرداخت شده‌اند", "All installments are paid", "ټول قسطونه ورکړل شوي")
  val errNotSettled: String get() = t("هنوز تسویه نشده؛ کالا فقط پس از پرداختِ کامل تحویل می‌شود", "Not paid up yet; goods are handed over only after full payment", "لا تصفیه شوی نه دی؛ توکي یوازې له بشپړې ورکړې وروسته سپارل کېږي")
  val errReason: String get() = t("دلیل ۳ تا ۱۲۰ نویسه باشد", "The reason must be 3 to 120 characters", "دلیل دې له ۳ تر ۱۲۰ توريو وي")
  val errMismatch: String get() = t("جمعِ فاکتور با امانی نخواند؛ چیزی ثبت نشد", "The invoice total did not match the layaway; nothing was recorded", "د بل ټوله له امانت سره سمه نه وه؛ هېڅ ثبت نه شو")
  val errNotSaved: String get() = t("ثبت نشد، دوباره امتحان کنید", "Not saved, try again", "ثبت نه شو، بیا هڅه وکړئ")
}
