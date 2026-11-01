package ir.vil3ntec.tohid.core.i18n

/**
 *  سفارشِ ویژهٔ مشتری با پیش‌پرداخت (POS-26) در صندوقِ گوشی و کامپیوتر (`SpecialOrderBar`) و تنظیماتِ همین دستگاه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/pos/SpecialOrder` (commonMain) و `data/SpecialOrders`. ترجمه‌های نامطمئن در
 *  `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrSpecialOrder {
  val settingsTitle: String get() = t("سفارشِ ویژه با پیش‌پرداخت", "Special orders with deposit", "ځانګړی فرمایش له مخکېنۍ ورکړې سره")
  val settingsNote: String get() = t(
    "در صندوق، کالایی که موجود نیست برای مشتری سفارش می‌شود و پیش‌پرداختش به حسابِ او و دخل می‌رود. تحویل فروشِ عادیِ صندوق است که پیش‌پرداخت از آن کم می‌شود؛ لغو پیش‌پرداخت را با دلیل پس می‌دهد.",
    "At the register, an out-of-stock item is ordered for a customer and the deposit goes to their account and the drawer. Delivery is a normal register sale with the deposit deducted; cancelling returns the deposit with a reason.",
    "په صندوق کې، هغه توکی چې نشته د پېرودونکي لپاره فرمایش کېږي او مخکېنۍ ورکړه یې د هغه حساب او دخل ته ځي. سپارل د صندوق عادي پلور دی چې مخکېنۍ ورکړه ترې کمېږي؛ لغوه مخکېنۍ ورکړه له دلیل سره بېرته ورکوي.",
  )
  val newOrder: String get() = t("سفارشِ ویژهٔ تازه", "New special order", "نوی ځانګړی فرمایش")
  fun list(open: Any): String = t("سفارش‌های ویژه ($open باز)", "Special orders ($open open)", "ځانګړي فرمایشونه ($open پرانیستي)")
  val pickCustomerFirst: String get() = t("اول مشتری را برای همین فاکتور انتخاب کنید", "First choose the customer for this invoice", "لومړی د دې بل لپاره پېرودونکی وټاکئ")
  fun customer(name: Any): String = t("مشتری: $name", "Customer: $name", "پېرودونکی: $name")
  val itemField: String get() = t("کالا (نام یا بارکد) یا شرحِ دلخواه", "Item (name or barcode) or a free description", "توکی (نوم یا بارکوډ) یا خپله تشرېح")
  fun linked(name: Any): String = t("کالای فهرست: $name", "Catalog item: $name", "د لړلیک توکی: $name")
  val unlink: String get() = t("شرحِ دلخواه", "Free description", "خپله تشرېح")
  val qty: String get() = t("مقدار", "Quantity", "شمېر")
  val total: String get() = t("مبلغِ کل (افغانی)", "Total (AFN)", "ټوله اندازه (افغانۍ)")
  val deposit: String get() = t("پیش‌پرداخت (افغانی)", "Deposit (AFN)", "مخکېنۍ ورکړه (افغانۍ)")
  val days: String get() = t("تحویل تا چند روز دیگر", "Delivery in how many days", "په څو ورځو کې سپارل")
  fun dueOn(date: Any): String = t("روزِ تحویل: $date", "Delivery day: $date", "د سپارلو ورځ: $date")
  val depositIsCash: String get() = t("پیش‌پرداخت نقد به دخل می‌آید و تا تحویل در حسابِ مشتری می‌ماند.", "The deposit is cash into the drawer and stays on the customer's account until delivery.", "مخکېنۍ ورکړه نغده دخل ته راځي او تر سپارلو د پېرودونکي په حساب کې پاتې کېږي.")
  val save: String get() = t("ثبتِ سفارش و پیش‌پرداخت", "Save order and deposit", "فرمایش او مخکېنۍ ورکړه ثبت کړئ")
  val close: String get() = t("بستن", "Close", "بندول")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  fun saved(deposit: Any): String = t("سفارشِ ویژه ثبت شد — پیش‌پرداخت $deposit ؋", "Special order saved — deposit $deposit ؋", "ځانګړی فرمایش ثبت شو — مخکېنۍ ورکړه $deposit ؋")
  val empty: String get() = t("سفارشِ ویژه‌ای نیست", "No special orders", "ځانګړی فرمایش نشته")

  // فهرست
  fun row(customer: Any, item: Any): String = t("$customer — $item", "$customer — $item", "$customer — $item")
  fun amounts(total: Any, held: Any): String = t("جمع $total ؋ · پیش‌پرداخت $held ؋", "Total $total ؋ · deposit $held ؋", "ټوله $total ؋ · مخکېنۍ ورکړه $held ؋")
  val stateOpen: String get() = t("باز", "Open", "پرانیستی")
  val stateReady: String get() = t("آماده", "Ready", "چمتو")
  val stateDelivered: String get() = t("تحویل شد", "Delivered", "وسپارل شو")
  val stateCancelled: String get() = t("لغو شد", "Cancelled", "لغوه شو")
  val overdue: String get() = t("دیر شده", "Overdue", "ځنډېدلی")
  val review: String get() = t("نیازمندِ بررسیِ مدیر", "Needs a manager's review", "د مدیر کتنې ته اړتیا لري")
  val markReady: String get() = t("آماده شد", "Mark ready", "چمتو شو")
  val deliver: String get() = t("تحویل", "Deliver", "سپارل")
  val cancelOrder: String get() = t("لغو", "Cancel order", "لغوه")
  val readyDone: String get() = t("سفارش آماده شد", "Order marked ready", "فرمایش چمتو شو")

  // تحویل
  val deliverTitle: String get() = t("تحویلِ سفارشِ ویژه", "Deliver special order", "د ځانګړي فرمایش سپارل")
  val deliverBody: String get() = t(
    "فروشِ عادیِ صندوق با مشتریِ همین سفارش ثبت می‌شود و پیش‌پرداخت از آن کم می‌شود.",
    "A normal register sale is recorded for this order's customer and the deposit is deducted from it.",
    "د دې فرمایش د پېرودونکي لپاره د صندوق عادي پلور ثبتېږي او مخکېنۍ ورکړه ترې کمېږي.",
  )
  fun fromDeposit(amount: Any): String = t("از پیش‌پرداخت: $amount ؋", "From deposit: $amount ؋", "له مخکېنۍ ورکړې: $amount ؋")
  fun payNow(amount: Any): String = t("دریافتِ نقدِ همین لحظه: $amount ؋", "Cash to collect now: $amount ؋", "اوس نغدې اخیستل: $amount ؋")
  fun leftOnAccount(amount: Any): String = t("پیش‌پرداختِ اضافه در حسابِ مشتری می‌ماند: $amount ؋", "Extra deposit stays on the customer's account: $amount ؋", "اضافي مخکېنۍ ورکړه د پېرودونکي په حساب کې پاتې کېږي: $amount ؋")
  val deliverConfirm: String get() = t("دریافت شد و تحویل شد", "Collected and delivered", "واخیستل شو او وسپارل شو")
  fun delivered(invoice: Any): String = t("تحویل شد — فاکتور #$invoice", "Delivered — invoice #$invoice", "وسپارل شو — بل #$invoice")

  // لغو
  val cancelTitle: String get() = t("لغوِ سفارشِ ویژه", "Cancel special order", "د ځانګړي فرمایش لغوه")
  fun refund(amount: Any): String = t("پیش‌پرداختِ $amount ؋ نقد به مشتری پس داده می‌شود.", "A deposit of $amount ؋ is returned to the customer in cash.", "د $amount ؋ مخکېنۍ ورکړه نغده پېرودونکي ته بېرته ورکول کېږي.")
  val reason: String get() = t("دلیل (اجباری)", "Reason (required)", "دلیل (اړین)")
  val cancelConfirm: String get() = t("لغو و بازپرداخت", "Cancel and refund", "لغوه او بېرته ورکړه")
  fun cancelled(amount: Any): String = t("سفارش لغو شد — بازپرداخت $amount ؋", "Order cancelled — refunded $amount ؋", "فرمایش لغوه شو — بېرته ورکړه $amount ؋")
  val noCancelRight: String get() = t("لغو و بازپرداخت فقط با مجوزِ «لغوِ فاکتور» است", "Cancelling and refunding needs the “cancel invoice” permission", "لغوه او بېرته ورکړه یوازې د «بل لغوه» اجازې سره ده")

  // ردِ قاعده
  val errNoCustomer: String get() = t("مشتری پیدا نشد", "Customer not found", "پېرودونکی ونه موندل شو")
  val errNoItem: String get() = t("کالا یا شرحِ سفارش را بنویسید", "Write the item or a description", "توکی یا تشرېح ولیکئ")
  val errQty: String get() = t("مقدار را درست بنویسید", "Enter a valid quantity", "سم شمېر ولیکئ")
  val errTotal: String get() = t("مبلغِ کل را درست بنویسید", "Enter a valid total", "سمه ټوله اندازه ولیکئ")
  val errDeposit: String get() = t("پیش‌پرداخت را درست بنویسید (صفر هم می‌شود)", "Enter a valid deposit (zero is allowed)", "سمه مخکېنۍ ورکړه ولیکئ (صفر هم کېږي)")
  val errWhole: String get() = t("مبلغ‌ها افغانیِ درست باشند، بی اعشار", "Amounts must be whole afghanis, no decimals", "اندازې دې بشپړې افغانۍ وي، بې اعشاریه")
  val errDepositOver: String get() = t("پیش‌پرداخت بیش از مبلغِ کل است", "The deposit is more than the total", "مخکېنۍ ورکړه له ټولې اندازې ډېره ده")
  val errDue: String get() = t("روزِ تحویل درست نیست", "The delivery day is not valid", "د سپارلو ورځ سمه نه ده")
  val errNoProduct: String get() = t("این کالا دیگر در فهرست نیست", "This item is no longer in the catalog", "دا توکی نور په لړلیک کې نشته")
  val errState: String get() = t("وضعیتِ سفارش همین حالا عوض شده؛ فهرست را دوباره ببینید", "The order's status just changed; check the list again", "د فرمایش حالت همدا اوس بدل شو؛ لړلیک بیا وګورئ")
  val errReason: String get() = t("دلیل ۳ تا ۱۲۰ نویسه باشد", "The reason must be 3 to 120 characters", "دلیل دې له ۳ تر ۱۲۰ توريو وي")
  val errMismatch: String get() = t("جمعِ فاکتور با سفارش نخواند؛ چیزی ثبت نشد", "The invoice total did not match the order; nothing was recorded", "د بل ټوله له فرمایش سره سمه نه وه؛ هېڅ ثبت نه شو")
  val errNotSaved: String get() = t("ثبت نشد، دوباره امتحان کنید", "Not saved, try again", "ثبت نه شو، بیا هڅه وکړئ")
}
