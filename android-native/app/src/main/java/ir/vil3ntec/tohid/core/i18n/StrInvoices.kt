package ir.vil3ntec.tohid.core.i18n

/**
 *  فاکتورها: تاریخچهٔ فروش (`SalesHistory`، با صندوقِ ساده، مرجوعی، اصلاح و لغو) و جدولِ فاکتورهای کامپیوتر (`DeskInvoices`).
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 *  ⛔ برگهٔ اکسل/چاپِ A4، دلیلِ لغو و نامِ قلمِ آزاد داده‌اند و ترجمه نمی‌شوند.
 */
@Suppress("unused")
object StrInvoices {

  // تاریخچهٔ فروش
  val salesTotal: String get() = t("جمع فروش", "Total sales", "ټول پلور")
  fun invoicesHint(n: Any, cancelled: Any): String = t(
    "$n فاکتور — $cancelled لغوشده",
    "$n ${pl(n, "invoice", "invoices")} — $cancelled cancelled",
    "$n ${pl(n, "بل", "بلونه")} — $cancelled لغوه شوي",
  )
  val searchHint: String get() = t("شمارهٔ فاکتور یا نام مشتری", "Invoice number or customer name", "د بل شمېره یا د پیرودونکي نوم")
  val clearSearch: String get() = t("پاک کردن جستجو", "Clear search", "لټون پاک کړئ")
  val returnHint: String get() = t(
    "برای برگشت، QRِ رسید را اسکن کنید یا شماره و نامِ مشتری را بنویسید.",
    "To return items, scan the receipt QR or type the invoice number or customer name.",
    "د بېرته ورکولو لپاره د رسید QR سکن کړئ یا شمېره او د پیرودونکي نوم ولیکئ.",
  )
  val scanReceiptQr: String get() = t("اسکنِ QRِ رسید", "Scan receipt QR", "د رسید QR سکن کړئ")
  val nonCash: String get() = t("غیرنقد", "Non-cash", "غیرنغد")
  val allMonths: String get() = t("همهٔ ماه‌ها", "All months", "ټولې میاشتې")
  val noMatch: String get() = t("فاکتوری با این جستجو یا فیلتر پیدا نشد.", "No invoices match this search or filter.", "د دې لټون یا فلټر سره هېڅ بل ونه موندل شو.")
  val online: String get() = t("اینترنتی", "Online", "آنلاین")
  val actions: String get() = t("کارها", "Actions", "کړنې")
  val viewInvoice: String get() = t("دیدن فاکتور", "View invoice", "بل وګورئ")
  val returnOrExchange: String get() = t("برگشت یا تعویض", "Return or exchange", "بېرته ورکړه یا بدلول")
  val returnItems: String get() = t("مرجوعی", "Return", "بېرته ورکړه")
  fun returnReceiptOf(l: Any): String = t("رسیدِ برگشت $l", "Return receipt $l", "د بېرته ورکړې رسید $l")
  val correctInvoice: String get() = t("اصلاح فاکتور", "Correct invoice", "بل سمول")
  val cancelSale: String get() = t("لغو فروش", "Cancel sale", "پلور لغوه کول")
  val cancelDenied: String get() = t("لغوِ فاکتور با صاحب یا مدیرِ دکان است", "Only the shop owner or a manager can cancel an invoice", "بل یوازې د دوکان مالک یا مدیر لغوه کولی شي")

  // سرستونِ جدول
  val colNumber: String get() = t("شماره", "No.", "شمېره")
  val colDate: String get() = t("تاریخ", "Date", "نېټه")
  val colName: String get() = t("نام", "Name", "نوم")
  val colKind: String get() = t("نوع", "Type", "ډول")
  val colInvoice: String get() = t("فاکتور", "Invoice", "بل")
  val colItems: String get() = t("اقلام", "Items", "توکي")
  val colDue: String get() = t("قابل پرداخت", "Due", "د ورکړې وړ")
  val colPaid: String get() = t("پرداخت‌شده", "Paid", "ورکړل شوي")
  val colBalance: String get() = t("باقی", "Balance", "پاتې")
  val colSeller: String get() = t("فروشنده", "Seller", "پلورونکی")
  val total: String get() = t("جمع", "Total", "ټول")

  // صندوقِ ساده (پرچمِ pay v2 خاموش)
  val registerClosed: String get() = t("صندوق بسته است", "The register is closed", "صندوق بند دی")
  fun registerOpenSince(at: Any): String = t("صندوق باز — از $at", "Register open — since $at", "صندوق خلاص — له $at راهیسې")
  fun expectedInDrawer(a: Any): String = t("نقدِ مورد انتظار در دخل: $a ؋", "Expected cash in the drawer: $a ؋", "په دراز کې د تمې وړ نغدې: $a ؋")
  val history: String get() = t("سابقه", "History", "تاریخچه")
  val closeRegister: String get() = t("بستن صندوق", "Close register", "صندوق بندول")
  val openRegisterTitle: String get() = t("باز کردنِ صندوق", "Open the register", "د صندوق پرانیستل")
  val closeRegisterTitle: String get() = t("بستنِ صندوق", "Close the register", "د صندوق بندول")
  val openingFloat: String get() = t("اول صندوق", "Opening float", "د صندوق پیل")
  val cashSales: String get() = t("فروش نقدی", "Cash sales", "نغد پلور")
  val debtorReceipts: String get() = t("رسید قرض‌داران", "Debtor payments", "د پوروړو ورکړې")
  val returns: String get() = t("مرجوعی", "Returns", "بېرته ورکړې")
  val supplierPayments: String get() = t("پرداخت به تأمین‌کننده و خرید", "Supplier payments and purchases", "عرضه‌کوونکو ته ورکړې او پېرود")
  val cashIn: String get() = t("ورودِ نقد", "Cash in", "د نغدو داخلول")
  val cashOut: String get() = t("خروجِ نقد", "Cash out", "د نغدو ایستل")
  val expected: String get() = t("مورد انتظار", "Expected", "د تمې وړ")
  val openingCash: String get() = t("پولِ اولِ صندوق", "Opening cash", "د صندوق لومړنۍ پیسې")
  val countedCash: String get() = t("پولِ شمرده‌شده در دخل", "Cash counted in the drawer", "په دراز کې شمېرل شوې پیسې")
  val noDifference: String get() = t("بی اختلاف", "No difference", "بې توپیره")
  fun overBy(a: Any): String = t("اضافه: $a ؋", "Over: $a ؋", "زیات: $a ؋")
  fun shortBy(a: Any): String = t("کسری: $a ؋", "Short: $a ؋", "کم: $a ؋")
  val noteOptional: String get() = t("یادداشت (اختیاری)", "Note (optional)", "یادښت (اختیاري)")
  val registerOpened: String get() = t("صندوق باز شد", "Register opened", "صندوق پرانیستل شو")
  val registerClosedNow: String get() = t("صندوق بسته شد", "Register closed", "صندوق بند شو")
  val openIt: String get() = t("باز کن", "Open", "یې پرانیزئ")
  val closeIt: String get() = t("ببند", "Close", "یې بند کړئ")
  val registerHistory: String get() = t("سابقهٔ صندوق", "Register history", "د صندوق تاریخچه")
  val noShiftsYet: String get() = t("هنوز صندوقی باز نشده.", "No register has been opened yet.", "لا تر اوسه هېڅ صندوق نه دی پرانیستل شوی.")
  val stillOpen: String get() = t("هنوز باز است", "Still open", "لا خلاص دی")

  // مرجوعی (پرچمِ برگشتِ تازه خاموش)
  fun returnTitle(n: Any): String = t("مرجوعی — فاکتور #$n", "Return — invoice #$n", "بېرته ورکړه — #$n بل")
  val nothingToReturn: String get() = t("موردی برای مرجوعی وجود ندارد.", "Nothing left to return.", "د بېرته ورکولو لپاره هېڅ نه دي پاتې.")
  fun returnable(name: Any, q: Any): String = t("$name — قابل مرجوعی: $q", "$name — returnable: $q", "$name — بېرته ورکېدونکی: $q")
  val returnQty: String get() = t("مقدار مرجوعی", "Quantity to return", "د بېرته ورکولو مقدار")
  val returnAmount: String get() = t("مبلغ مرجوعی", "Return amount", "د بېرته ورکړې مبلغ")
  val reasonOptional: String get() = t("دلیل (اختیاری)", "Reason (optional)", "لامل (اختیاري)")
  val recordReturn: String get() = t("ثبت مرجوعی", "Record return", "بېرته ورکړه ثبت کړئ")
  val returnRecorded: String get() = t("مرجوعی با موفقیت ثبت شد", "Return recorded", "بېرته ورکړه ثبت شوه")

  // اصلاح و لغو
  val reopenTitle: String get() = t("اصلاح فاکتور؟", "Correct this invoice?", "دا بل سم کړئ؟")
  fun reopenBody(n: Any): String = t(
    "فاکتور #$n لغو می‌شود و اقلامش با همان قیمت به سبد خرید برمی‌گردند. بعد از اصلاح، فروش را دوباره ثبت کنید — شمارهٔ فاکتور تازه‌ای می‌گیرد.",
    "Invoice #$n will be cancelled and its items go back to the cart at the same prices. After correcting, record the sale again — it gets a new invoice number.",
    "#$n بل لغوه کېږي او توکي یې په هماغه بیه ټوکرۍ ته ورګرځي. له سمون وروسته پلور بیا ثبت کړئ — د بل نوې شمېره اخلي.",
  )
  val reopenedToCart: String get() = t("فاکتور لغو شد و اقلامش در سبد است", "Invoice cancelled; its items are in the cart", "بل لغوه شو او توکي یې په ټوکرۍ کې دي")
  val cancelAndToCart: String get() = t("لغو و بردن به سبد", "Cancel and move to cart", "لغوه او ټوکرۍ ته")
  val cancelSaleTitle: String get() = t("لغو فروش؟", "Cancel this sale?", "دا پلور لغوه کړئ؟")
  val cancelReason: String get() = t("دلیلِ لغو", "Reason for cancelling", "د لغوه کولو لامل")
  val saleCancelled: String get() = t("فروش لغو شد", "Sale cancelled", "پلور لغوه شو")
  val alreadyCancelled: String get() = t("این فاکتور قبلاً لغو شده است", "This invoice is already cancelled", "دا بل مخکې لغوه شوی دی")
  val reasonRequired: String get() = t("دلیلِ لغو لازم است", "A reason for cancelling is required", "د لغوه کولو لامل اړین دی")
  fun reasonTooLong(max: Any): String = t("دلیلِ لغو حداکثر $max نویسه است", "The reason can be at most $max characters", "لامل تر $max تورو زیات نه شي کېدای")
  val invoiceNotFound: String get() = t("فاکتور پیدا نشد", "Invoice not found", "بل ونه موندل شو")

  // جدولِ فاکتورهای کامپیوتر
  val backToTable: String get() = t("← برگشت به جدولِ فاکتورها", "← Back to the invoice table", "← د بلونو جدول ته شاته")
  fun savedTo(p: Any): String = t("ذخیره شد: $p", "Saved: $p", "خوندي شو: $p")
  val netSales: String get() = t("فروشِ خالص", "Net sales", "خالص پلور")
  val creditUnpaid: String get() = t("نسیه (باقی)", "Credit (unpaid)", "پور (پاتې)")
  val deskSearchHint: String get() = t("شمارهٔ فاکتور، مشتری یا فروشنده — Ctrl+F", "Invoice number, customer or seller — Ctrl+F", "د بل شمېره، پیرودونکی یا پلورونکی — Ctrl+F")
  val last7Days: String get() = t("۷ روز", "7 days", "7 ورځې")
  val allMethods: String get() = t("همهٔ روش‌ها", "All methods", "ټولې لارې")
  val recorded: String get() = t("ثبت‌شده", "Recorded", "ثبت شوی")
  val cancelAndReturns: String get() = t("لغو و برگشتی", "Cancel & returns", "لغوه او بېرته ورکړه")
  val excelKey: String get() = t("اکسل  Ctrl+E", "Excel  Ctrl+E", "اکسل  Ctrl+E")
  val emptyRange: String get() = t("در این بازه فاکتوری نیست.", "No invoices in this period.", "په دې موده کې هېڅ بل نشته.")
  val printA4: String get() = t("چاپِ A4 (برگهٔ کامل)", "Print A4 (full page)", "A4 چاپ (بشپړه پاڼه)")
}
