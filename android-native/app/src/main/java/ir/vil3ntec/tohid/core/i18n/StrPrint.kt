package ir.vil3ntec.tohid.core.i18n

/**
 *  پیام‌های لایهٔ چاپ (`print/`) — صفِ چاپ، چاپگرِ حرارتی (بلوتوث، شبکه، سیم)، کشو، برگهٔ آزمون و برچسب، PDF ِ گزارش و فرستادنِ
 *  فاکتور (LOC-08 دری، دورِ ۷). پیش‌تر متنِ خامِ فارسی بودند؛ فارسی بایت‌به‌بایت همان پیشین، هر مقدار با `asIs`. عنوانِ کارِ صف و دلیلِ
 *  چاپِ دوباره در صف و سابقه ذخیره می‌شوند و داده‌اند. نمونهٔ خطِ دری و پشتوی برگهٔ آزمون هم داده است (خودِ نمونه).
 */
@Suppress("unused")
object StrPrint {

  // ── صفِ چاپ و کشو (`PrintSpooler`) ──
  /** روی کاغذِ چاپِ دوباره */
  val copyMark: String get() = t("— چاپِ دوباره —", "— Reprint —", "— بیا چاپ —")
  val drawerNoQueue: String get() = t("کشو در صف نمی‌رود", "The drawer doesn't go in the queue", "دخل په لیکه کې نه ځي")
  val copyNotMade: String get() = t("نسخهٔ «چاپِ دوباره» ساخته نشد", "The «reprint» copy couldn't be made", "د «بیا چاپ» نسخه جوړه نه شوه")
  fun jobNotSaved(why: Any?): String = t("کارِ چاپ ذخیره نشد: ${asIs(why)}", "The print job wasn't saved: $why", "د چاپ کار خوندي نه شو: $why")
  val alreadyQueued: String get() = t(
    "این فاکتور همین حالا در صفِ چاپ است",
    "This invoice is already in the print queue",
    "دا بل همدا اوس د چاپ په لیکه کې دی",
  )
  val printed: String get() = t("چاپ شد", "Printed", "چاپ شو")
  val notPrinted: String get() = t("چاپ نشد", "Not printed", "چاپ نه شو")
  val inQueue: String get() = t(
    "در صفِ چاپ است — پشتِ کارِ قبلی",
    "It's in the print queue — behind the previous job",
    "د چاپ په لیکه کې دی — د مخکیني کار شاته",
  )
  fun printerAway(why: Any?): String = t(
    "چاپگر در دسترس نیست: ${asIs(why)} — در صف ماند و دوباره تلاش می‌شود",
    "The printer isn't available: $why — it stays in the queue and will be tried again",
    "چاپګر شتون نه لري: $why — په لیکه کې پاتې شو او بیا هڅه کېږي",
  )
  fun invoiceNotDrawn(why: Any?): String = t("فاکتور کشیده نشد: ${asIs(why)}", "The invoice couldn't be drawn: $why", "بل رسم نه شو: $why")
  val unknownReason: String get() = t("دلیل نامعلوم", "unknown reason", "نامعلوم لامل")
  val reprinted: String get() = t("چاپِ دوباره انجام شد", "Reprinted", "بیا چاپ شو")
  val invoicePrinted: String get() = t("فاکتور چاپ شد", "Invoice printed", "بل چاپ شو")
  fun invoiceNo(no: Any?): String = t("فاکتورِ #${asIs(no)}", "Invoice #$no", "بل #$no")
  fun drawerFailedKey(why: Any?): String = t(
    "کشو باز نشد: ${asIs(why)} — با کلید باز کنید",
    "The drawer didn't open: $why — open it with the key",
    "دخل خلاص نه شو: $why — په کیلي یې خلاص کړئ",
  )
  fun labelNotDrawn(label: Any?, why: Any?): String = t(
    "${asIs(label)} کشیده نشد: ${asIs(why)}",
    "$label couldn't be drawn: $why",
    "$label رسم نه شو: $why",
  )
  fun labelNotPrinted(label: Any?, why: Any?): String = t(
    "${asIs(label)} چاپ نشد — ${asIs(why)}",
    "$label not printed — $why",
    "$label چاپ نه شو — $why",
  )
  val badAddress: String get() = t("نشانیِ چاپگر خوانده نشد", "The printer's address couldn't be read", "د چاپګر پته ونه لوستل شوه")
  val printerBusy: String get() = t("چاپگر مشغول بود", "The printer was busy", "چاپګر بوخت و")
  fun drawerFailed(why: Any?): String = t("کشو باز نشد: ${asIs(why)}", "The drawer didn't open: $why", "دخل خلاص نه شو: $why")
  val didntReach: String get() = t("به چاپگر نرسید", "didn't reach the printer", "چاپګر ته ونه رسېد")
  val drawerOpened: String get() = t("کشو باز شد", "The drawer opened", "دخل خلاص شو")
  fun drawerMaybe(why: Any?): String = t(
    "شاید کشو باز شد (خطای چاپگر: ${asIs(why)})",
    "The drawer may have opened (printer error: $why)",
    "ښايي دخل خلاص شوی وي (د چاپګر تېروتنه: $why)",
  )
  val logged: String get() = t(" — در سابقه ثبت شد", " — recorded in the history", " — په تاریخچه کې ثبت شو")
  val notLogged: String get() = t(
    " — ولی در سابقه ثبت نشد؛ دوباره ثبت می‌شود",
    " — but not recorded in the history; it will be recorded again",
    " — خو په تاریخچه کې ثبت نه شو؛ بیا ثبتېږي",
  )
  val setAside: String get() = t("کنار گذاشته شد", "Set aside", "یوې خوا ته شو")
  fun noCopySetAside(title: Any?): String = t(
    "${asIs(title)} نسخهٔ «چاپِ دوباره» ندارد (صفِ نسخهٔ پیشین) و کنار گذاشته شد — از فهرستِ فاکتورها با دلیل دوباره چاپ کنید",
    "$title has no «reprint» copy (queue from the previous version) and was set aside — print it again from the invoice list with a reason",
    "$title د «بیا چاپ» نسخه نه لري (د پخوانۍ نسخې لیکه) او یوې خوا ته شو — د بلونو له لېست څخه له لامل سره بیا چاپ یې کړئ",
  )
  val jobFileMissing: String get() = t("فایلِ این کار پیدا نشد", "This job's file wasn't found", "د دې کار فایل پیدا نه شو")

  // ── چاپگرِ حرارتی (`ThermalPrinter`) ──
  val tooBigForMemory: String get() = t(
    "فاکتور برای حافظهٔ این گوشی بزرگ است",
    "The invoice is too big for this device's memory",
    "بل د دې وسیلې د حافظې لپاره لوی دی",
  )
  fun invoiceNotReady(why: Any?): String = t("فاکتور آماده نشد: ${asIs(why)}", "The invoice wasn't ready: $why", "بل چمتو نه شو: $why")
  val unknownError: String get() = t("خطای ناشناخته", "unknown error", "نامعلومه تېروتنه")
  val noBluetooth: String get() = t("بلوتوث روی این دستگاه نیست", "There's no Bluetooth on this device", "په دې وسیله بلوتوث نشته")
  val bluetoothOff: String get() = t("بلوتوث خاموش است", "Bluetooth is off", "بلوتوث بند دی")
  val usbNotFound: String get() = t(
    "چاپگرِ سیمی پیدا نشد — کابل وصل است؟",
    "No wired printer found — is the cable connected?",
    "سیمي چاپګر پیدا نه شو — کېبل وصل دی؟",
  )
  val usbNoPermission: String get() = t(
    "اجازهٔ دسترسی به چاپگرِ سیمی داده نشده",
    "Permission to use the wired printer wasn't given",
    "سیمي چاپګر ته د لاسرسي اجازه نه ده ورکړل شوې",
  )
  val usbConnected: String get() = t(
    "وصل است — وضعیتِ کاغذ از راهِ سیم خوانده نمی‌شود",
    "Connected — the paper status can't be read over the cable",
    "وصل دی — د کاغذ حالت له سیم نه لوستل کېږي",
  )
  fun connectedNoPaper(label: Any?): String = t(
    "وصل شد (${asIs(label)}) — چاپگر وضعیتِ کاغذ را نگفت",
    "Connected ($label) — the printer didn't report the paper status",
    "وصل شو ($label) — چاپګر د کاغذ حالت ونه ویل",
  )
  val paperOut: String get() = t("کاغذ تمام شده است", "The paper has run out", "کاغذ خلاص شوی")
  val paperLow: String get() = t("وصل است — کاغذ رو به پایان است", "Connected — the paper is running low", "وصل دی — کاغذ د خلاصېدو په حال کې دی")
  val paperOk: String get() = t("وصل است و کاغذ دارد", "Connected and has paper", "وصل دی او کاغذ لري")
  val connected: String get() = t("وصل شد", "Connected", "وصل شو")
  val btNoPermission: String get() = t("اجازهٔ بلوتوث داده نشده", "Bluetooth permission wasn't given", "د بلوتوث اجازه نه ده ورکړل شوې")
  fun notConnected(label: Any?, why: Any?): String = t(
    "وصل نشد (${asIs(label)}): ${asIs(why)}",
    "Didn't connect ($label): $why",
    "وصل نه شو ($label): $why",
  )
  val noAnswer: String get() = t("پاسخی نیامد", "no answer came", "ځواب رانغی")
  val noPrinterChosen: String get() = t("چاپگری انتخاب نشده است", "No printer is chosen", "هېڅ چاپګر نه دی ټاکل شوی")
  val noBluetoothPhone: String get() = t("بلوتوث روی این گوشی نیست", "There's no Bluetooth on this phone", "په دې موبایل کې بلوتوث نشته")
  val printerNotFound: String get() = t("چاپگر پیدا نشد", "Printer not found", "چاپګر پیدا نه شو")
  fun printFailed(why: Any?): String = t("چاپ نشد: ${asIs(why)}", "Not printed: $why", "چاپ نه شو: $why")
  val noConnection: String get() = t("اتصال برقرار نشد", "the connection wasn't made", "اړیکه ونه نیول شوه")
  fun netNoAnswer(host: Any?): String = t(
    "چاپگر در شبکه جواب نداد (${asIs(host)}) — روشن است؟",
    "The printer didn't answer on the network ($host) — is it on?",
    "چاپګر په شبکه کې ځواب ورنه کړ ($host) — روښانه دی؟",
  )
  fun netNoConnect(host: Any?): String = t(
    "به چاپگر وصل نشد (${asIs(host)}) — نشانی و شبکه را ببینید",
    "Couldn't connect to the printer ($host) — check the address and the network",
    "چاپګر ته وصل نه شو ($host) — پته او شبکه وګورئ",
  )
  fun netBadHost(host: Any?): String = t(
    "نشانیِ چاپگر شناخته نشد (${asIs(host)})",
    "The printer's address wasn't recognised ($host)",
    "د چاپګر پته ونه پېژندل شوه ($host)",
  )
  val usbUnsupported: String get() = t(
    "این گوشی از چاپگرِ سیمی پشتیبانی نمی‌کند",
    "This device doesn't support wired printers",
    "دا وسیله له سیمي چاپګر ملاتړ نه کوي",
  )
  val notPrinter: String get() = t("این دستگاه چاپگر نیست", "This device isn't a printer", "دا وسیله چاپګر نه ده")
  val noEndpoint: String get() = t(
    "راهِ فرستادن روی این چاپگر پیدا نشد",
    "No way to send to this printer was found",
    "دې چاپګر ته د لېږلو لاره پیدا نه شوه",
  )
  val usbNotOpened: String get() = t("چاپگرِ سیمی باز نشد", "The wired printer didn't open", "سیمي چاپګر خلاص نه شو")
  val usbBusy: String get() = t(
    "چاپگرِ سیمی در دستِ برنامهٔ دیگری است",
    "The wired printer is being used by another app",
    "سیمي چاپګر د بل پروګرام په لاس کې دی",
  )
  val printHalf: String get() = t(
    "چاپ نیمه‌کاره ماند — کابل را ببینید",
    "The print stopped halfway — check the cable",
    "چاپ نیمګړی پاتې شو — کېبل وګورئ",
  )
  val noUsbConnection: String get() = t("اتصالِ سیمی برقرار نشد", "the wired connection wasn't made", "سیمي اړیکه ونه نیول شوه")

  // ── برگهٔ آزمون، برچسب و نشان (`HwPages`) ──
  fun pieceTooWide(piece: Any?, paper: Any?): String = t(
    "پهنای تکه (${asIs(piece)} نقطه) از پهنای کاغذ (${asIs(paper)} نقطه) بیشتر است",
    "The piece width ($piece dots) is more than the paper width ($paper dots)",
    "د ټوټې پلنوالی ($piece ټکي) د کاغذ له پلنوالي ($paper ټکي) ډېر دی",
  )
  val testTitle: String get() = t("برگهٔ آزمونِ چاپگر", "Printer test page", "د چاپګر د ازموینې پاڼه")
  val previewNoPrinter: String get() = t("پیش‌نمایش — بی چاپگر", "Preview — no printer", "مخکتنه — بې چاپګره")
  fun testPaper(mm: Any?, dots: Any?, mode: Any?, cut: Any?): String = t(
    "کاغذ ${asIs(mm)} میلی‌متر (${asIs(dots)} نقطه) · ${asIs(mode)} · برش: ${asIs(cut)}",
    "Paper $mm mm ($dots dots) · $mode · cut: $cut",
    "کاغذ $mm ملي‌متره ($dots ټکي) · $mode · پرې کول: $cut",
  )
  val modeColumn: String get() = t("ستونی ESC *", "column ESC *", "ستوني ESC *")
  val modeRaster: String get() = t("رستری GS v 0", "raster GS v 0", "راسټري GS v 0")
  val drawerAfterCash: String get() = t("کشو: پس از فروشِ نقدی", "Drawer: after a cash sale", "دخل: له نغدي پلور وروسته")
  val drawerOff: String get() = t("کشو: خاموش", "Drawer: off", "دخل: بند")
  val testOk: String get() = t(
    "اگر دو سرِ خطِ سیاه بریده نشده و نوشته‌ها کامل‌اند، چاپگر درست تنظیم است.",
    "If both ends of the black line aren't cut off and the text is complete, the printer is set up right.",
    "که د تور کرښې دواړه سرونه پرې شوي نه وي او لیکنې بشپړې وي، چاپګر سم تنظیم دی.",
  )
  val cutFull: String get() = t("کامل", "full", "بشپړ")
  val cutNone: String get() = t("بی‌برش", "no cut", "بې پرې کولو")
  val cutHalf: String get() = t("نیمه", "half", "نیم")
  fun testLabel(name: Any?): String = t("برچسبِ آزمون — ${asIs(name)}", "Test label — $name", "د ازموینې نښه — $name")
  val testLabelFailed: String get() = t("برچسبِ آزمون کشیده نشد", "The test label couldn't be drawn", "د ازموینې نښه رسم نه شوه")
  val noBarcode: String get() = t("این کالا بارکد ندارد", "This item has no barcode", "دا توکی بارکوډ نه لري")
  fun notCode128(code: Any?): String = t(
    "بارکدِ «${asIs(code)}» در Code128 نمی‌گنجد (فقط حرف و رقمِ لاتین)",
    "Barcode «$code» doesn't fit Code128 (Latin letters and digits only)",
    "بارکوډ «$code» په Code128 کې نه ځایږي (یوازې لاتین توري او عددونه)",
  )
  fun barcodeTooLong(code: Any?, mm: Any?): String = t(
    "بارکدِ «${asIs(code)}» برای برچسبِ ${asIs(mm)} میلی‌متری بلند است",
    "Barcode «$code» is too long for a $mm mm label",
    "بارکوډ «$code» د $mm ملي‌متره نښې لپاره اوږد دی",
  )
  val sampleLabel: String get() = t("برچسبِ نمونه", "Sample label", "نمونه نښه")
  val imageUnread: String get() = t("تصویر خوانده نشد", "The picture couldn't be read", "انځور ونه لوستل شو")
  fun logoNotSaved(why: Any?): String = t("نشان ذخیره نشد: ${asIs(why)}", "The logo wasn't saved: $why", "نښان خوندي نه شو: $why")

  // ── چاپگرها، آماده‌سازی و فرستادن (`PrinterRegistry`، `EasySetupAndroid`، `PrintJob`، `StatementImage`، `ReportPrint`، `LabelSheetPrint`) ──
  val kindNetwork: String get() = t("شبکه / وای‌فای", "Network / Wi-Fi", "شبکه / وای‌فای")
  val kindSystem: String get() = t("چاپگرِ نصب‌شده در سیستم", "Printer installed in the system", "په سیستم کې نصب شوی چاپګر")
  val kindUsb: String get() = t("سیم (USB)", "Cable (USB)", "سیم (USB)")
  val kindBluetooth: String get() = t("بلوتوث", "Bluetooth", "بلوتوث")
  fun testNotDrawn(why: Any?): String = t(
    "برگهٔ آزمون کشیده نشد: ${asIs(why)}",
    "The test page couldn't be drawn: $why",
    "د ازموینې پاڼه رسم نه شوه: $why",
  )
  fun modeTestNotDrawn(why: Any?): String = t(
    "برگهٔ دوروشی کشیده نشد: ${asIs(why)}",
    "The two-mode page couldn't be drawn: $why",
    "دوه لارې پاڼه رسم نه شوه: $why",
  )
  val noReceiptPrinter: String get() = t(
    "چاپگرِ رسید ثبت نشده — برگه را از روی صفحه اسکن کنید",
    "No receipt printer is registered — scan the sheet from the screen",
    "د رسید چاپګر نه دی ثبت — پاڼه له پردې سکن کړئ",
  )
  fun sheetNotDrawn(why: Any?): String = t("برگه کشیده نشد: ${asIs(why)}", "The sheet couldn't be drawn: $why", "پاڼه رسم نه شوه: $why")
  fun invoiceNotMade(why: Any?): String = t("فاکتور ساخته نشد: ${asIs(why)}", "The invoice couldn't be made: $why", "بل جوړ نه شو: $why")
  val sendInvoice: String get() = t("فرستادنِ فاکتور", "Send invoice", "بل لېږل")
  val noShareApp: String get() = t("برنامه‌ای برای فرستادن پیدا نشد", "No app was found to send with", "د لېږلو لپاره پروګرام پیدا نه شو")
  fun returnNotDrawn(why: Any?): String = t(
    "رسیدِ برگشت کشیده نشد: ${asIs(why)}",
    "The return receipt couldn't be drawn: $why",
    "د بېرته ورکړې رسید رسم نه شو: $why",
  )
  fun returnNotMade(why: Any?): String = t(
    "رسیدِ برگشت ساخته نشد: ${asIs(why)}",
    "The return receipt couldn't be made: $why",
    "د بېرته ورکړې رسید جوړ نه شو: $why",
  )
  fun returnShareText(label: Any?, shop: Any?): String = t(
    "رسیدِ برگشت ${asIs(label)}${asIs(shop)}",
    "Return receipt $label$shop",
    "د بېرته ورکړې رسید $label$shop",
  )
  val sendReturn: String get() = t("فرستادنِ رسیدِ برگشت", "Send return receipt", "د بېرته ورکړې رسید لېږل")
  val statementNotMade: String get() = t("تصویرِ صورت‌حساب ساخته نشد", "The statement picture couldn't be made", "د حساب انځور جوړ نه شو")
  fun savedAt(path: Any?): String = t("ذخیره شد: ${asIs(path)}", "Saved: $path", "خوندي شو: $path")
  val notSaved: String get() = t("ذخیره نشد", "Not saved", "خوندي نه شو")
  val sendStatement: String get() = t("فرستادنِ صورت‌حساب", "Send statement", "د حساب لېږل")
  val statementReady: String get() = t("صورت‌حساب آمادهٔ فرستادن است", "The statement is ready to send", "حساب د لېږلو لپاره چمتو دی")
  val pdfReady: String get() = t("PDF آماده شد", "The PDF is ready", "PDF چمتو شو")
  fun pdfFailed(why: Any?): String = t("PDF ساخته نشد: ${asIs(why)}", "The PDF couldn't be made: $why", "PDF جوړ نه شو: $why")
  val paperUnknown: String get() = t("اندازهٔ کاغذ معلوم نیست", "The paper size isn't known", "د کاغذ اندازه معلومه نه ده")
  val nothingToPrint: String get() = t("داده‌ای برای چاپ نیست.", "There is no data to print.", "د چاپ لپاره معلومات نشته.")

  // ── کامپیوتر (`ReportPrintDesk`، `LabelSheetPrintDesk`) ──
  val labelPrintTitle: String get() = t("چاپِ برچسب", "Label printing", "د نښې چاپ")
  fun printNotDone(why: Any?): String = t("چاپ انجام نشد: ${asIs(why)}", "Printing didn't work: $why", "چاپ ونه شو: $why")
  val printTitle: String get() = t("چاپ", "Print", "چاپ")
  fun previewNotMade(why: Any?): String = t("پیش‌نمایش ساخته نشد: ${asIs(why)}", "The preview couldn't be made: $why", "مخکتنه جوړه نه شوه: $why")
  fun pageOf(n: Any?, of: Any?): String = t("صفحهٔ ${asIs(n)} از ${asIs(of)}", "Page $n of $of", "پاڼه $n له $of")
  fun previewTitle(title: Any?): String = t("پیش‌نمایش چاپ — ${asIs(title)}", "Print preview — $title", "د چاپ مخکتنه — $title")
  val printDots: String get() = t("چاپ…", "Print…", "چاپ…")
  val close: String get() = t("بستن", "Close", "بندول")
  fun pagesN(n: Any): String = t("${asIs(n)} صفحه", "$n ${pl(n, "page", "pages")}", "$n پاڼې")
}
