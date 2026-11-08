package ir.vil3ntec.tohid.core.i18n

/**
 *  چاپگر، کشو، برچسب، ترازو و نمایشگر در صفحه‌های قدیمیِ `ui/screens/DevicesHw.kt` که «دستگاه‌ها» ِ نسخهٔ ۳ صدا می‌زند: صفِ چاپ و
 *  وضعیتش، تنظیمِ هر چاپگر، پیش‌نمایشِ بی کاغذ، باز کردنِ کشو، برچسبِ کالا، ترازو و نمایشگرِ مشتری. این نوشته‌ها پیش‌تر متنِ خامِ
 *  فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۳). فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری
 *  بود، همان دوباره به کار رفته. نامِ برچسبِ نمونه روی کاغذ و دلیلِ پیش‌فرضی که در سابقهٔ دکان می‌نشیند داده‌اند. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrDevicePages {
  // ── `DevicesHw.kt` ──

  // صفِ چاپ
  val printReady: String get() = t("چاپ: آماده", "Print: ready", "چاپ: چمتو")
  fun printing(waiting: Any?): String = t("در حالِ چاپ${asIs(waiting)}", "Printing$waiting", "چاپېږي$waiting")
  fun printRetrying(attempts: Any?, waiting: Any?): String = t(
    "تلاشِ دوبارهٔ چاپ (${asIs(attempts)})${asIs(waiting)}",
    "Retrying print ($attempts)$waiting",
    "د چاپ بیا هڅه ($attempts)$waiting",
  )
  fun printerUnavailable(waiting: Any?): String = t("چاپگر در دسترس نیست${asIs(waiting)}", "Printer unavailable$waiting", "چاپګر نشته$waiting")
  fun inQueue(n: Any?): String = t(" · ${asIs(n)} در صف", " · $n in queue", " · $n په کتار کې")
  val noPrinter: String get() = t("بی چاپگر", "No printer", "بې چاپګره")
  val previewTestSheet: String get() = t("پیش‌نمایشِ برگهٔ آزمون — بی کاغذ", "Test sheet preview — no paper", "د ازموینې پاڼې مخکتنه — بې کاغذه")
  val previewLabel: String get() = t("پیش‌نمایشِ برچسبِ ۴۰×۳۰", "40×30 label preview", "د ۴۰×۳۰ لیبل مخکتنه")
  val previewA4: String get() = t("پیش‌نمایشِ برگهٔ A4 (۳×۸)", "A4 sheet preview (3×8)", "د A4 پاڼې مخکتنه (۳×۸)")
  val printQueue: String get() = t("صفِ چاپ", "Print queue", "د چاپ کتار")
  val queueNote: String get() = t(
    "فروش همیشه ثبت می‌شود؛ رسیدی که چاپ نشد این‌جا به ترتیب می‌ماند و دوباره تلاش می‌شود.",
    "The sale is always recorded; a receipt that didn't print stays here in order and is retried.",
    "پلور تل ثبتېږي؛ هغه رسید چې چاپ نه شو دلته په ترتیب پاتې کېږي او بیا هڅه کېږي.",
  )
  fun lastError(error: Any?): String = t("آخرین خطا: ${asIs(error)}", "Last error: $error", "وروستۍ تېروتنه: $error")
  val reprintSuffix: String get() = t(" — چاپِ دوباره", " — reprint", " — بیا چاپ")
  fun attempts(n: Any?): String = t("${asIs(n)} تلاش", "$n ${pl(n ?: 0, "attempt", "attempts")}", "$n هڅې")
  val removedFromQueue: String get() = t(
    "از صف برداشته شد — فروش دست نخورد",
    "Removed from the queue — the sale is untouched",
    "له کتار څخه لرې شو — پلور نه دی بدل شوی",
  )
  val autoPrint: String get() = t("چاپِ خودکارِ فاکتور پس از هر فروش", "Auto-print the invoice after each sale", "د هر پلور وروسته د بل اتومات چاپ")
  val autoPrintNote: String get() = t(
    "روی چاپگرِ پیش‌فرض. خاموش = مثلِ همیشه فقط با دکمهٔ «چاپ».",
    "On the default printer. Off = as always, only with the “Print” button.",
    "پر اصلي چاپګر. بند = د تل په څېر یوازې د «چاپ» تڼۍ سره.",
  )
  val autoCopies: String get() = t("تعدادِ نسخهٔ چاپِ خودکار", "Number of auto-print copies", "د اتومات چاپ د نسخو شمېر")
  val copiesNote: String get() = t(
    "نسخهٔ دوم به بعد روی کاغذ «نسخهٔ ۲ از …» دارد تا با اصل اشتباه نشود.",
    "From the second copy on, the paper says “Copy 2 of …” so it isn't mistaken for the original.",
    "له دویمې نسخې وروسته پر کاغذ «۲ نسخه له …» لیکل کېږي چې له اصلي سره ګډه نه شي.",
  )
  val deferredPrint: String get() = t("چاپِ معوق", "Deferred printing", "ځنډنی چاپ")
  val deferredNote: String get() = t(
    "روشن: تا چاپگر برگردد خودش هر دقیقه تلاش می‌کند. خاموش: پس از چند تلاش می‌ایستد و می‌پرسد.",
    "On: retries every minute until the printer is back. Off: stops after a few tries and asks.",
    "روښانه: تر هغه چې چاپګر بېرته راشي هره دقیقه هڅه کوي. بند: له څو هڅو وروسته درېږي او پوښتنه کوي.",
  )
  fun autoSummary(auto: Any?, deferred: Any?): String = t(
    "چاپِ خودکار: ${asIs(auto)} · چاپِ معوق: ${asIs(deferred)} — تنظیمش با صاحب یا مدیرِ دکان است.",
    "Auto-print: $auto · deferred printing: $deferred — set by the shop's owner or manager.",
    "اتومات چاپ: $auto · ځنډنی چاپ: $deferred — تنظیم یې د دوکان د خاوند یا مدیر سره دی.",
  )
  val openDrawer: String get() = t("باز کردنِ کشو", "Open drawer", "دخل پرانیستل")
  val openDrawerOwner: String get() = t(
    "باز کردنِ کشو بی فروش فقط برای صاحب و مدیر است.",
    "Opening the drawer without a sale is only for the owner and manager.",
    "له پلور پرته د دخل پرانیستل یوازې د خاوند او مدیر لپاره دي.",
  )
  fun previewFailed(error: Any?): String = t("پیش‌نمایش ساخته نشد: ${asIs(error)}", "Preview couldn't be made: $error", "مخکتنه جوړه نه شوه: $error")
  val previewTestNoPrinter: String get() = t(
    "پیش‌نمایشِ برگهٔ آزمون (بی چاپگر)",
    "Test sheet preview (no printer)",
    "د ازموینې پاڼې مخکتنه (بې چاپګره)",
  )
  val recentReprints: String get() = t("چاپ‌های دوبارهٔ اخیر", "Recent reprints", "وروستي بیا چاپونه")
  val newHwTools: String get() = t("ابزارهای تازهٔ دستگاه (آزمایشی)", "New device tools (experimental)", "د وسیلو نوې اوزار (ازمایښتي)")
  val newHwToolsNote: String get() = t(
    "بررسیِ کاملِ چاپگر (درِ باز، آفلاین، تیغه، کاغذ) و «کدام روش؟» برای چاپگری که رسید را بی‌معنا می‌زند. انتخابِ همین دستگاه است.",
    "Full printer check (cover open, offline, cutter, paper) and “Which method?” for a printer that prints garbage receipts. Chosen per device.",
    "د چاپګر بشپړه کتنه (پرانیستې دروازه، آفلاین، چاقو، کاغذ) او «کومه طریقه؟» د هغه چاپګر لپاره چې رسید بې‌معنا چاپوي. د همدې وسیلې ټاکنه ده.",
  )
  fun errorIs(error: Any?): String = t("خطا: ${asIs(error)}", "Error: $error", "تېروتنه: $error")

  // تنظیمِ چاپگر
  fun setupOf(name: Any?): String = t("تنظیمِ «${asIs(name)}»", "Set up “$name”", "د «$name» تنظیم")
  val printerJob: String get() = t("کارِ این چاپگر", "This printer's job", "د دې چاپګر کار")
  val labelLanguage: String get() = t("زبانِ چاپگرِ برچسب", "Label printer language", "د لیبل چاپګر ژبه")
  val labelNote: String get() = t(
    "برچسبِ ۴۰×۳۰ میلی‌متر، ۲۰۳ نقطه در اینچ. نام و قیمت به‌صورتِ تصویر چاپ می‌شوند.",
    "40×30 mm label, 203 dpi. Name and price are printed as an image.",
    "۴۰×۳۰ ملي‌متره لیبل، ۲۰۳ ټکي په انچ کې. نوم او بیه د انځور په بڼه چاپېږي.",
  )
  val paperWidth: String get() = t("عرضِ کاغذ", "Paper width", "د کاغذ پلنوالی")
  val imageMode: String get() = t("راهِ فرستادنِ تصویر", "How the image is sent", "د انځور د لېږلو لار")
  val modeRaster: String get() = t("رستری GS v 0 (پیش‌فرض)", "Raster GS v 0 (default)", "راستري GS v 0 (اصلي)")
  val modeColumn: String get() = t("ستونی ESC *", "Column ESC *", "ستوني ESC *")
  val garbageHintTools: String get() = t(
    "اگر چاپگر به‌جای رسید نویسه‌های بی‌معنا زد، «ستونی» را بزنید — یا «کدام روش؟» تا با یک برگه معلوم شود.",
    "If the printer prints garbage instead of the receipt, choose “Column” — or “Which method?” to find out with one sheet.",
    "که چاپګر د رسید پر ځای بې‌معنا توري چاپ کړل، «ستوني» وټاکئ — یا «کومه طریقه؟» چې په یوه پاڼه معلومه شي.",
  )
  val garbageHint: String get() = t(
    "اگر چاپگر به‌جای رسید نویسه‌های بی‌معنا زد، «ستونی» را بزنید.",
    "If the printer prints garbage instead of the receipt, choose “Column”.",
    "که چاپګر د رسید پر ځای بې‌معنا توري چاپ کړل، «ستوني» وټاکئ.",
  )
  val whichMethod: String get() = t("کدام روش؟ (برگهٔ دوروشی)", "Which method? (two-method sheet)", "کومه طریقه؟ (دوه طریقې پاڼه)")
  val paperCut: String get() = t("برشِ کاغذ", "Paper cut", "د کاغذ پرې کول")
  val cutPartial: String get() = t("نیمه", "Partial", "نیمګړی")
  val cutFull: String get() = t("کامل", "Full", "بشپړ")
  val cutNone: String get() = t("بی‌تیغه", "No cutter", "بې چاقو")
  val drawerAfterCash: String get() = t(
    "باز کردنِ کشوی پول پس از فروشِ نقدی",
    "Open the cash drawer after a cash sale",
    "له نغدي پلور وروسته د پیسو دخل پرانیستل",
  )
  val drawerAfterCashNote: String get() = t(
    "از راهِ چاپگر (ESC p). فروشِ نسیه کشو را باز نمی‌کند.",
    "Through the printer (ESC p). A credit sale doesn't open the drawer.",
    "د چاپګر له لارې (ESC p). نسیه پلور دخل نه پرانیزي.",
  )
  val pin2: String get() = t("پینِ ۲ (رایج)", "Pin 2 (common)", "۲ پن (عام)")
  val pin5: String get() = t("پینِ ۵", "Pin 5", "۵ پن")
  val logoOnReceipt: String get() = t("نشانِ دکان بالای رسید", "Shop logo above the receipt", "د رسید پر سر د دوکان نښه")
  val logoChosen: String get() = t("نشان انتخاب شده است", "A logo is chosen", "نښه ټاکل شوې ده")
  val chooseImageFirst: String get() = t("اول یک تصویر انتخاب کنید", "Choose an image first", "لومړی یو انځور وټاکئ")
  val changeLogo: String get() = t("عوض کردنِ نشان", "Change logo", "نښه بدلول")
  val chooseLogo: String get() = t("انتخابِ نشان", "Choose logo", "نښه ټاکل")
  val removeLogo: String get() = t("برداشتنِ نشان", "Remove logo", "نښه لرې کول")
  val preview: String get() = t("پیش‌نمایش", "Preview", "مخکتنه")
  val checkConnection: String get() = t("بررسیِ اتصال", "Check connection", "د نښلون کتنه")
  val drawerTest: String get() = t("آزمونِ کشو", "Drawer test", "د دخل ازموینه")
  val testPrinted: String get() = t("برگهٔ آزمون چاپ شد", "Test sheet printed", "د ازموینې پاڼه چاپ شوه")
  val testSheet: String get() = t("برگهٔ آزمون", "Test sheet", "د ازموینې پاڼه")
  val logoNote: String get() = t("نشانِ سیاه‌وسفید بهتر چاپ می‌شود", "A black-and-white logo prints better", "تور او سپین لوګو ښه چاپېږي")
  val previewPrint: String get() = t("پیش‌نمایشِ چاپ — بی کاغذ", "Print preview — no paper", "د چاپ مخکتنه — بې کاغذه")
  val noneWorked: String get() = t(
    "هیچ‌کدام درست نبود — عرضِ کاغذ و اتصال را بررسی کنید؛ روش عوض نشد",
    "Neither was right — check the paper width and connection; the method wasn't changed",
    "هېڅ یو سم نه و — د کاغذ پلنوالی او نښلون وګورئ؛ طریقه بدله نه شوه",
  )
  val columnPicked: String get() = t(
    "«ستونی ESC *» انتخاب شد — «ذخیره» را بزنید",
    "“Column ESC *” selected — press “Save”",
    "«ستوني ESC *» وټاکل شو — «خوندي کول» ووهئ",
  )
  val rasterKept: String get() = t(
    "«رستری GS v 0» می‌ماند — «ذخیره» را بزنید",
    "“Raster GS v 0” stays — press “Save”",
    "«راستري GS v 0» پاتې کېږي — «خوندي کول» ووهئ",
  )
  val whichSample: String get() = t("کدام نمونه درست چاپ شد؟", "Which sample printed correctly?", "کومه بېلګه سمه چاپ شوه؟")
  val whichSampleNote: String get() = t(
    "برگه دو نمونه دارد: «۱» ستونی و «۲» رستری. نمونه‌ای درست است که نوشتهٔ دری و پشتو و خطِ سیاهِ تمام‌عرضش کامل آمده.",
    "The sheet has two samples: “1” column and “2” raster. The right one is where the Dari and Pashto text and the full-width black line came out complete.",
    "پاڼه دوه بېلګې لري: «۱» ستوني او «۲» راستري. هغه بېلګه سمه ده چې دري او پښتو لیکنه او پوره پلن تور کرښه یې بشپړه راغلې وي.",
  )
  val bothSamples: String get() = t("هر دو", "Both", "دواړه")
  val only2: String get() = t("فقط ۲", "Only 2", "یوازې ۲")
  val only1: String get() = t("فقط ۱", "Only 1", "یوازې ۱")

  // کشو و پیش‌نمایش
  val drawerLogNote: String get() = t(
    "در سابقهٔ دکان ثبت می‌شود: چه کسی، کی، از کدام دستگاه و چرا.",
    "Recorded in the shop history: who, when, from which device and why.",
    "د دوکان په تاریخچه کې ثبتېږي: څوک، کله، له کومې وسیلې او ولې.",
  )
  val paperPreview: String get() = t("پیش‌نمایشِ کاغذ", "Paper preview", "د کاغذ مخکتنه")
  val nothingToPrint: String get() = t("چیزی برای چاپ نیست", "Nothing to print", "د چاپ لپاره څه نشته")
  val commands: String get() = t("دستورها: ", "Commands: ", "امرونه: ")
  fun unknownBytes(bytes: Any?): String = t("بایتِ ناشناخته: ${asIs(bytes)}", "Unknown bytes: $bytes", "نامعلوم بایټونه: $bytes")

  // برچسبِ کالا
  val labelSpec: String get() = t(
    "۴۰×۳۰ میلی‌متر: نامِ دری، قیمت و بارکدِ همان کالا (EAN-13 یا Code128).",
    "40×30 mm: the item's Dari name, price and barcode (EAN-13 or Code128).",
    "۴۰×۳۰ ملي‌متره: د توکي دري نوم، بیه او بارکوډ (EAN-13 یا Code128).",
  )
  val nameOrBarcode: String get() = t("نام یا بارکدِ کالا", "Item name or barcode", "د توکي نوم یا بارکوډ")
  val noBarcode: String get() = t(" · بی‌بارکد", " · no barcode", " · بې بارکوډه")
  val countColon: String get() = t("تعداد:", "Count:", "شمېر:")
  val labelFailed: String get() = t("برچسب ساخته نشد", "Label couldn't be made", "لیبل جوړ نه شو")
  fun labelOf(name: Any?): String = t("برچسبِ ${asIs(name)}", "Label $name", "د $name لیبل")
  val labelPrinted: String get() = t("برچسب چاپ شد", "Label printed", "لیبل چاپ شو")
  val a4Sheet: String get() = t("برگهٔ A4", "A4 sheet", "A4 پاڼه")
  val a4Note: String get() = t(
    "برگهٔ A4: ۳ ستون × ۸ ردیف، تا ۲۴ برچسب در یک برگ؛ در پنجرهٔ چاپ «اندازهٔ واقعی / بی حاشیه» را بزنید.",
    "A4 sheet: 3 columns × 8 rows, up to 24 labels per sheet; in the print window choose “Actual size / no margins”.",
    "A4 پاڼه: ۳ ستونه × ۸ قطارونه، په یوه پاڼه کې تر ۲۴ لیبلونو؛ د چاپ په کړکۍ کې «اصلي اندازه / بې حاشیې» وټاکئ.",
  )
  val noLabelPrinter: String get() = t(
    "هنوز چاپگری «برچسبِ کالا» تعریف نشده — در «چاپگرها» یکی را تنظیم کنید.",
    "No “Product labels” printer is set yet — set one up in “Printers”.",
    "لا د «توکي لیبل» چاپګر نه دی ټاکل شوی — په «چاپګرونه» کې یو تنظیم کړئ.",
  )
  val previewLabelTitle: String get() = t("پیش‌نمایشِ برچسب", "Label preview", "د لیبل مخکتنه")

  // ترازو و نمایشگر
  val notScaleLabel: String get() = t(
    "برچسبِ ترازو نیست (یا قاعده خاموش است) — بارکدِ معمولی خوانده می‌شود",
    "Not a scale label (or the rule is off) — read as a normal barcode",
    "د تلې لیبل نه دی (یا قاعده بنده ده) — د عادي بارکوډ په توګه لوستل کېږي",
  )
  val scaleLabelAtTill: String get() = t("برچسبِ ترازو در صندوق", "Scale labels at the till", "په دخل کې د تلې لیبل")
  val scaleLabelNote: String get() = t(
    "بارکدِ ۱۳ رقمیِ ۲۰ تا ۲۹ (پیش‌فرض: کدِ کالای ترازو ۵ رقم و وزن ۵ رقم به گرم؛ نوع و طول‌ها زیرِ همین کلید). کدِ ترازو را به بارکدهای همان کالا بیفزایید.",
    "13-digit barcodes from 20 to 29 (default: 5-digit scale item code and 5-digit weight in grams; type and lengths under this switch). Add the scale code to that item's barcodes.",
    "له ۲۰ تر ۲۹ پورې ۱۳ رقمه بارکوډ (اصلي: د تلې د توکي ۵ رقمه کوډ او په ګرام ۵ رقمه وزن؛ ډول او اوږدوالی د همدې تڼۍ لاندې). د تلې کوډ د همغه توکي بارکوډونو ته ورزیات کړئ.",
  )
  val scaleTest: String get() = t("آزمون: یک برچسبِ ترازو این‌جا اسکن کنید", "Test: scan a scale label here", "ازموینه: دلته د تلې یو لیبل سکن کړئ")
  val serialDesktopOnly: String get() = t(
    "ترازوی سریال (NCI) فقط در برنامهٔ کامپیوتر است.",
    "The serial scale (NCI) is only in the computer app.",
    "سریال تله (NCI) یوازې د کمپیوټر په اپ کې ده.",
  )
  fun serialScale(proto: Any?, baud: Any?, bits: Any?, parity: Any?, stop: Any?): String = t(
    "ترازوی سریال — پروتکلِ ${asIs(proto)}، ${asIs(baud)} و ${asIs(bits)}${asIs(parity)}${asIs(stop)}. روی ترازوی واقعی آزموده نشده است.",
    "Serial scale — $proto protocol, $baud and $bits$parity$stop. Not tested on a real scale.",
    "سریال تله — د $proto پروتوکول، $baud او $bits$parity$stop. پر رښتینې تله نه ده ازمویل شوې.",
  )
  val noSerialPort: String get() = t("درگاهِ سریالی پیدا نشد.", "No serial port found.", "سریال پورټ ونه موندل شو.")
  fun kg(w: Any?): String = t("${asIs(w)} کیلو", "$w kg", "$w کیلو")
  val noWeight: String get() = t("بی‌وزن", "No weight", "بې وزنه")
  val inMotion: String get() = t("در حرکت", "Moving", "په حرکت کې")
  val onZero: String get() = t("روی صفر", "On zero", "پر صفر")
  val overCapacity: String get() = t("بیش از ظرفیت", "Over capacity", "له ظرفیت زیات")
  val underCapacity: String get() = t("زیرِ ظرفیت", "Under capacity", "له ظرفیت کم")
  val scaleError: String get() = t("خطای ترازو", "Scale error", "د تلې تېروتنه")
  //  بی « · » ِ آغاز (آن را صفحه می‌گذارد) تا متنِ فارسی کلیدِ جملهٔ کاملِ دری شود (`DariText.PHRASES`)
  val stable: String get() = t("ایستا", "stable", "ثابت")
  val readWeight: String get() = t("خواندنِ وزن", "Read weight", "وزن لوستل")
  val displayNote: String get() = t(
    "پنجرهٔ دوم رو به مشتری: اقلامِ سبد و جمع. پنجره را به صفحهٔ دوم ببرید.",
    "A second window facing the customer: cart items and total. Move the window to the second screen.",
    "پېرودونکي ته مخامخ دویمه کړکۍ: د ټوکرۍ توکي او ټولټال. کړکۍ دویمې پردې ته یوسئ.",
  )
  val poleNote: String get() = t(
    "نمایشگرِ قطبی (دوخطی، ۲۰ نویسه) — ۹۶۰۰ و 8N1. روی نمایشگرِ واقعی آزموده نشده است.",
    "Pole display (two lines, 20 characters) — 9600 and 8N1. Not tested on a real display.",
    "قطبي نندارې (دوه کرښې، ۲۰ توري) — ۹۶۰۰ او 8N1. پر رښتینې نندارې نه ده ازمویل شوې.",
  )
  val poleSent: String get() = t(
    "فرستاده شد — «VILL3N TEST» روی نمایشگر است؟",
    "Sent — is “VILL3N TEST” on the display?",
    "ولېږل شو — آیا «VILL3N TEST» پر نندارې دی؟",
  )
  val poleTest: String get() = t("آزمونِ نمایشگرِ قطبی", "Test pole display", "د قطبي نندارې ازموینه")
  fun copiesSuffix(n: Any?): String = t(" (${asIs(n)} نسخه)", " ($n ${pl(n ?: 0, "copy", "copies")})", " ($n نسخې)")
}
