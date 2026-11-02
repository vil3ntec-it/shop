package ir.vil3ntec.tohid.core.i18n

/**
 *  «دستگاه‌ها» و اسکن در پس‌زمینه (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱) — فارسیِ معیار؛ رویهٔ دری جداست (`text/DariText.kt`).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrDevices {
  //  ── کارتِ «همهٔ دستگاه‌ها» ───────────────────────────────────────
  val hubTitle: String get() = t("همهٔ دستگاه‌ها", "All devices", "ټولې وسیلې")
  val hubCaption: String get() = t(
    "سبز یعنی همین حالا وصل است؛ سرخ یعنی پاسخ نداد. چند دستگاه با هم وصل می‌مانند و پس از قطع خودشان برمی‌گردند.",
    "Green means connected now; red means it did not answer. Several devices stay connected together and reconnect by themselves.",
    "شین یعنې همدا اوس وصل دی؛ سور یعنې ځواب یې ورنه کړ. څو وسیلې یوځای وصل پاتې کېږي او له پرې کېدو وروسته پخپله بېرته راځي.",
  )
  val search: String get() = t("جست‌وجو", "Search", "لټون")
  val searching: String get() = t("در حالِ جست‌وجو…", "Searching…", "لټون روان دی…")
  val nothingFound: String get() = t(
    "دستگاهی پیدا نشد. دستگاه را روشن و نزدیک کنید و دوباره «جست‌وجو» را بزنید.",
    "No device found. Turn the device on, bring it close and tap \"Search\" again.",
    "هېڅ وسیله ونه موندل شوه. وسیله روښانه او نږدې کړئ او بیا «لټون» ووهئ.",
  )
  val savedHead: String get() = t("ثبت‌شده", "Added", "ثبت شوې")
  val foundHead: String get() = t("پیداشده", "Found", "موندل شوې")
  val add: String get() = t("افزودن", "Add", "زیاتول")
  val pair: String get() = t("جفت کردن", "Pair", "جوړه کول")
  val remove: String get() = t("برداشتن", "Remove", "لرې کول")
  val test: String get() = t("آزمایش", "Test", "ازموینه")
  val role: String get() = t("نقش", "Role", "رول")
  val autoConnect: String get() = t("خودکار وصل شود", "Connect automatically", "په خپله وصل شي")
  val testOk: String get() = t("درست کار کرد", "It worked", "سم کار یې وکړ")
  val pairFirst: String get() = t(
    "پنجرهٔ جفت‌کردن باز شد؛ پس از جفت شدن «افزودن» را بزنید.",
    "The pairing window opened; tap \"Add\" once it is paired.",
    "د جوړه کولو کړکۍ پرانیستل شوه؛ له جوړه کېدو وروسته «زیاتول» ووهئ.",
  )
  val permission: String get() = t(
    "برای پیدا کردن و وصل کردنِ دستگاه‌ها اجازهٔ «دستگاه‌های نزدیک» لازم است.",
    "\"Nearby devices\" permission is needed to find and connect devices.",
    "د وسیلو موندلو او وصلولو لپاره د «نږدې وسیلې» اجازه پکار ده.",
  )
  val allow: String get() = t("اجازه دادن", "Allow", "اجازه ورکول")

  val kindPrinter: String get() = t("چاپگر", "Printer", "چاپګر")
  val kindScanner: String get() = t("بارکدخوان", "Scanner", "بارکوډ لوستونکی")
  val kindScale: String get() = t("ترازو", "Scale", "تله")
  val kindDisplay: String get() = t("نمایشگرِ مشتری", "Customer display", "د پیرودونکي ښودونکی")
  val kindOther: String get() = t("دیگر", "Other", "نور")
  val kindUnknown: String get() = t("نامشخص", "Unknown", "ناڅرګند")

  val stateConnected: String get() = t("وصل", "Connected", "وصل")
  val stateConnecting: String get() = t("در حالِ وصل…", "Connecting…", "د وصلېدو په حال کې…")
  val stateOffline: String get() = t("قطع", "Not connected", "پرې")
  val stateKeyboard: String get() = t("حالتِ صفحه‌کلید", "Keyboard mode", "د کیبورډ حالت")
  val stateNoPermission: String get() = t("بی اجازه", "No permission", "بې اجازې")
  val stateBluetoothOff: String get() = t("بلوتوث خاموش", "Bluetooth off", "بلوتوث بند")
  val stateUntested: String get() = t("آزموده نشده", "Not tested", "نه دی ازمویل شوی")

  val keyboardTitle: String get() = t("بارکدخوانِ حالتِ صفحه‌کلید", "Scanner in keyboard mode", "د کیبورډ په حالت کې بارکوډ لوستونکی")
  val keyboardHelp: String get() = t(
    "این بارکدخوان مثلِ صفحه‌کلید وصل است؛ وقتی برنامه بسته است اسکنش به برنامه نمی‌رسد. برای گرفتنِ اسکن در پس‌زمینه، " +
      "از دفترچهٔ بارکدخوان بارکدِ «SPP» یا «Serial» را اسکن کنید، دستگاه را از تنظیماتِ بلوتوث فراموش و دوباره جفت کنید.",
    "This scanner is connected like a keyboard; while the app is closed its scans do not reach the app. To capture scans in the background, " +
      "scan the \"SPP\" or \"Serial\" code from the scanner's manual, then forget the device in Bluetooth settings and pair it again.",
    "دا بارکوډ لوستونکی د کیبورډ په څېر وصل دی؛ کله چې اپ بند وي سکن یې اپ ته نه رسېږي. په شالید کې د سکن نیولو لپاره، " +
      "د بارکوډ لوستونکي له لارښود څخه «SPP» یا «Serial» بارکوډ سکن کړئ، بیا وسیله د بلوتوث له تنظیماتو هېره او بیا جوړه کړئ.",
  )

  //  ── اسکن در پس‌زمینه ─────────────────────────────────────────────
  val backgroundTitle: String get() = t("اسکن وقتی برنامه بسته است", "Scans while the app is closed", "سکن کله چې اپ بند وي")
  val backgroundCaption: String get() = t(
    "با ثبتِ یک بارکدخوان خودش روشن است و کلیدِ جدا ندارد. اسکن‌ها نگه داشته می‌شوند و با باز شدنِ برنامه پرسیده می‌شود کجا بروند.",
    "It turns on by itself once a scanner is added; there is no separate switch. Scans are kept and the app asks where they go when it opens.",
    "د یو بارکوډ لوستونکي په ثبت سره پخپله روښانه کېږي او جلا تڼۍ نه لري. سکنونه ساتل کېږي او د اپ په پرانیستو پوښتل کېږي چې چېرته لاړ شي.",
  )
  fun backgroundOn(n: Any): String = t("${asIs(n)} بارکدخوان وصل است", "$n ${pl(n, "scanner", "scanners")} connected", "$n بارکوډ لوستونکي وصل دي")
  val backgroundNone: String get() = t(
    "هنوز بارکدخوانی ثبت نشده؛ از «همهٔ دستگاه‌ها» یکی را بیفزایید.",
    "No scanner added yet; add one from \"All devices\".",
    "تر اوسه کوم بارکوډ لوستونکی نه دی ثبت شوی؛ له «ټولې وسیلې» یو زیات کړئ.",
  )
  val memoryNote: String get() = t(
    "اگر بارکدخوان «حافظه» دارد، اسکن‌های وقتِ خاموشی با وصلِ دوباره خودشان می‌آیند و تکراری‌ها یک بار شمرده می‌شوند.",
    "If the scanner has memory, scans made while this device was off arrive on reconnect, and duplicates are counted once.",
    "که بارکوډ لوستونکی «حافظه» ولري، د بندېدو د وخت سکنونه له بیا وصلېدو سره پخپله راځي او تکراري یو ځل شمېرل کېږي.",
  )
  /** خطِ ثابتِ اعلانِ همیشگیِ اندروید — یک جملهٔ ساده */
  val noteTitle: String get() = t("بارکدخوان وصل است", "Scanner connected", "بارکوډ لوستونکی وصل دی")
  val noteLine: String get() = t(
    "اسکن‌ها حتی وقتی برنامه بسته است نگه داشته می‌شوند.",
    "Scans are kept even when the app is closed.",
    "سکنونه هغه وخت هم ساتل کېږي چې اپ بند وي.",
  )
  val noteChannel: String get() = t("اسکن در پس‌زمینه", "Background scanning", "په شالید کې سکن")

  //  ── «N اسکن در انتظار» ───────────────────────────────────────────
  fun pendingTitle(n: Any): String = t("${asIs(n)} اسکن در انتظار", "$n ${pl(n, "scan", "scans")} waiting", "$n سکنونه په تمه")
  fun pendingLine(codes: Any, units: Any): String = t("${asIs(codes)} کالا، روی‌هم ${asIs(units)} عدد", "$codes ${pl(codes, "item", "items")}, $units in total", "$codes توکي، ټول $units")
  fun fromMemory(n: Any): String = t("${asIs(n)} اسکن از حافظهٔ بارکدخوان", "$n from the scanner's memory", "$n د بارکوډ لوستونکي له حافظې")
  val pendingAsk: String get() = t("این اسکن‌ها کجا بروند؟", "Where should these scans go?", "دا سکنونه چېرته لاړ شي؟")
  val toCount: String get() = t("انبارگردانی", "Stock count", "د ګودام شمېرنه")
  val toCountLine: String get() = t("شمارِ هر کالا به برگهٔ انبارگردانی افزوده می‌شود", "Each item's count is added to the stock count", "د هر توکي شمېر د ګودام شمېرنې ته ورزیاتېږي")
  val toReceive: String get() = t("ورودِ کالا", "Receive stock", "د توکو ننوتل")
  val toReceiveLine: String get() = t("هر کالا با شمارش در برگهٔ «ورودِ چند قلم»", "Each item with its count on the bulk entry sheet", "هر توکی له شمېر سره د ډېرو قلمونو په پاڼه کې")
  val toSale: String get() = t("فاکتورِ فروش", "Sales invoice", "د پلور بل")
  val toSaleLine: String get() = t(
    "فاکتورِ نگه‌داشته می‌شود؛ فروش فقط با زدنِ «پرداخت» ثبت می‌شود",
    "Becomes a held invoice; the sale is recorded only when you tap \"Pay\"",
    "ساتل شوی بل کېږي؛ پلور یوازې د «تادیه» په وهلو ثبتېږي",
  )
  val later: String get() = t("بعداً", "Later", "وروسته")
  val discard: String get() = t("کنار گذاشتن", "Set aside", "یو طرف ته کول")
  fun discardAsk(n: Any): String = t(
    "همهٔ ${asIs(n)} اسکن کنار گذاشته شود؟ این کار برگشت ندارد.",
    "Set aside all $n ${pl(n, "scan", "scans")}? This cannot be undone.",
    "ټول $n سکنونه یو طرف ته شي؟ دا کار بېرته نه راګرځي.",
  )
  fun heldReady(n: Any, total: String): String = t(
    "${asIs(n)} فاکتورِ نگه‌داشته آماده است — جمع: ${asIs(total)}",
    "$n held ${pl(n, "invoice", "invoices")} ready — total: $total",
    "$n ساتل شوي بلونه چمتو دي — ټول: $total",
  )
  val pay: String get() = t("پرداخت", "Pay", "تادیه")
  fun unknownCodes(n: Any): String = t(
    "${asIs(n)} بارکد کالا ندارد — با «ثبتِ کالا» ثبتش کنید",
    "$n ${pl(n, "barcode has", "barcodes have")} no item — add with \"New item\"",
    "$n بارکوډونه توکی نه لري — د «نوی توکی» په مرسته یې ثبت کړئ",
  )
  val newItem: String get() = t("ثبتِ کالا", "New item", "نوی توکی")
  fun countAdded(n: Any): String = t("شمارِ ${asIs(n)} کالا از بارکدخوان افزوده شد", "Counts for $n ${pl(n, "item", "items")} added from the scanner", "د $n توکو شمېر له بارکوډ لوستونکي ورزیات شو")
  //  ── «کارتِ فرمان» — فروشِ نقدیِ خودکار از بارکدخوان (راهِ «ب»، B، پیش‌فرض خاموش) ─────────────
  val cashCardTitle: String get() = t("فروشِ نقدی با کارتِ فرمان", "Cash sales with the command card", "د امر کارت سره نغدي پلور")
  val cashCardNote: String get() = t(
    "روشن ⇒ کالاها را اسکن کنید و بعد «نقد پرداخت شد» را از کارت؛ برنامه حتی بسته، فروشِ نقدی را با همان قیمت، تخفیفِ خودکار و گردکردنِ صندوق ثبت می‌کند و رسید چاپ می‌شود. قرض و مشتری همیشه از صندوق.",
    "On ⇒ scan the items, then \"Cash paid\" from the card; even with the app closed the cash sale is recorded with the till's prices, automatic discounts and rounding, and the receipt prints. Credit and customers always go through the till.",
    "روښانه ⇒ توکي سکن کړئ او بیا له کارت «نغدې ورکړل شوې»؛ اپ آن بند هم نغدي پلور د صندوق په بیه، خپلکاره تخفیف او ګردولو ثبتوي او رسید چاپېږي. پور او پیرودونکی تل له صندوق.",
  )
  val cashCardSafety: String get() = t(
    "صندوقِ بسته، کاربرِ بی اجازهٔ فروش یا بارکدِ بی کالا ⇒ فروش ثبت نمی‌شود و فاکتورِ نگه‌داشته ساخته می‌شود تا با «پرداخت» ثبت کنید.",
    "Till closed, a user without sale permission or an unknown barcode ⇒ no sale is recorded; a held invoice is made for you to pay.",
    "بند صندوق، د پلور بې اجازې کاروونکی یا بې توکي بارکوډ ⇒ پلور نه ثبتېږي او ساتل شوی بل جوړېږي چې په «تادیه» یې ثبت کړئ.",
  )
  val cashCardOwnerOnly: String get() = t(
    "فقط صاحب یا مدیرِ دکان این را عوض می‌کند.",
    "Only the shop owner or a manager can change this.",
    "یوازې د دوکان څښتن یا مدیر دا بدلولی شي.",
  )
  val cashCardAsk: String get() = t("فروشِ نقدیِ خودکار روشن شود؟", "Turn on automatic cash sales?", "خپلکاره نغدي پلور روښانه شي؟")
  val cashCardAskBody: String get() = t(
    "با هر «نقد پرداخت شد» از کارت، بی زدنِ دکمه‌ای فروشِ نقدی در دفتر ثبت می‌شود و در گزارشِ صندوق می‌آید. پس از روشن کردن، کارت را از «دستگاه‌ها» چاپ کنید.",
    "Each \"Cash paid\" from the card records a cash sale in the books without any tap, and it appears in the till report. After turning it on, print the card from \"Devices\".",
    "له کارت هره «نغدې ورکړل شوې» پرته له کومې تڼۍ نغدي پلور په دفتر کې ثبتوي او د صندوق په راپور کې راځي. له روښانه کولو وروسته کارت له «وسیلې» چاپ کړئ.",
  )
  val turnOn: String get() = t("روشن کن", "Turn on", "روښانه یې کړه")

  val cardTitle: String get() = t("کارتِ فرمان", "Command card", "د امر کارت")
  val cardLine: String get() = t(
    "این سه بارکد را چاپ کنید و کنارِ بارکدخوان بگذارید. کارت مالِ همین دستگاه است.",
    "Print these three barcodes and keep them by the scanner. The card belongs to this device.",
    "دا درې بارکوډونه چاپ کړئ او د بارکوډ لوستونکي څنګ ته یې کېږدئ. کارت د همدې وسیلې دی.",
  )
  val cmdNew: String get() = t("فاکتورِ تازه", "New invoice", "نوی بل")
  val cmdCash: String get() = t("نقد پرداخت شد", "Cash paid", "نغدې ورکړل شوې")
  val cmdUndo: String get() = t("حذفِ آخرین کالا", "Remove last item", "وروستی توکی لرې کول")
  val printCard: String get() = t("چاپِ کارت", "Print card", "کارت چاپول")
  val newCard: String get() = t("کارتِ تازه", "New card", "نوی کارت")
  val newCardAsk: String get() = t(
    "کارتِ تازه ساخته شود؟ کارت‌های چاپ‌شدهٔ پیشین دیگر کار نمی‌کنند.",
    "Make a new card? Cards printed before will stop working.",
    "نوی کارت جوړ شي؟ پخواني چاپ شوي کارتونه نور کار نه کوي.",
  )
  //  نسخهٔ کارتِ فرمان، نه کارتِ بانکی (نگهبانِ S117 در card-data.test.js)
  fun cardEdition(n: Any): String = t("کارتِ فرمان، نسخهٔ ${asIs(n)}", "Command card, edition $n", "د امر کارت، $n نسخه")
  val cardNoPrinter: String get() = t(
    "چاپگرِ رسید ثبت نشده — کارت را از روی صفحه اسکن کنید یا چاپگر بیفزایید",
    "No receipt printer is set — scan the card from the screen or add a printer",
    "د رسید چاپګر نه دی ثبت — کارت له پردې سکن کړئ یا چاپګر زیات کړئ",
  )
  fun cardDrawFailed(message: String): String = t("کارت کشیده نشد: ${asIs(message)}", "The card could not be drawn: $message", "کارت جوړ نه شو: $message")
  val printerLinkBad: String get() = t("نشانیِ چاپگر خوانده نشد", "The printer address could not be read", "د چاپګر پته ونه لوستل شوه")
  val cardPrinted: String get() = t("کارت چاپ شد", "Card printed", "کارت چاپ شو")
  val cardOff: String get() = t(
    "«فروشِ نقدی با کارتِ فرمان» خاموش است — از «تنظیمات ⇐ پیشرفته» روشن کنید.",
    "\"Cash sales with the command card\" is off — turn it on in \"Settings ⇐ Advanced\".",
    "«د امر کارت سره نغدي پلور» بند دی — له «امستنې ⇐ پرمختللي» یې روښانه کړئ.",
  )
  fun lastRun(recorded: Any, held: Any): String = t(
    "آخرین کار: ${asIs(recorded)} فروشِ نقدی ثبت شد، ${asIs(held)} فاکتور نگه‌داشته شد",
    "Last run: $recorded cash ${pl(recorded, "sale", "sales")} recorded, $held held",
    "وروستی کار: $recorded نغدي پلور ثبت شول، $held بلونه وساتل شول",
  )
  fun ignoredCodes(n: Any): String = t(
    "${asIs(n)} کدِ فرمانِ نادرست یا کهنه نادیده ماند",
    "$n invalid or old command ${pl(n, "code was", "codes were")} ignored",
    "$n ناسم یا زاړه د امر کوډونه پرېښودل شول",
  )

  //  چرا فروش خودکار ثبت نشد — یادداشتِ فاکتورِ نگه‌داشته
  val whyTillClosed: String get() = t("صندوق بسته بود", "the till was closed", "صندوق بند و")
  val whyNoPermission: String get() = t("کاربرِ صندوق اجازهٔ فروش نداشت", "the till user had no sale permission", "د صندوق کاروونکي د پلور اجازه نه درلوده")
  fun whyUnknown(codes: String): String = t("بارکدِ بی کالا: ${asIs(codes)}", "barcode with no item: $codes", "بې توکي بارکوډ: $codes")
  val whyLongGap: String get() = t("میانِ اسکنِ کالاها فاصلهٔ زیاد بود", "there was a long gap between scans", "د سکنونو ترمنځ اوږد واټن و")
  val whyNotSaved: String get() = t("دفتر روی دستگاه نوشته نشد", "the books were not saved on this device", "دفتر په وسیله کې ونه لیکل شو")
  fun whyRefused(message: String): String = t("ثبت نشد: ${asIs(message)}", "not recorded: $message", "ثبت نه شو: $message")
  fun heldNote(why: String): String = t(
    "نقد گرفته شد ولی فروش ثبت نشد — ${asIs(why)}. با «پرداخت» ثبت کنید.",
    "Cash was taken but the sale was not recorded — $why. Record it with \"Pay\".",
    "نغدې واخیستل شوې خو پلور ثبت نه شو — $why. په «تادیه» یې ثبت کړئ.",
  )

  //  گزارشِ صندوق (X/Z و صفحهٔ صندوق)
  fun scannerSales(n: Any, amount: String): String = t(
    "ثبت از اسکنر: ${asIs(n)} فاکتور، ${asIs(amount)}",
    "Recorded from scanner: $n ${pl(n, "invoice", "invoices")}, $amount",
    "له سکنر ثبت: $n بلونه، $amount",
  )
  fun scannerSalesCount(n: Any): String = t("ثبت از اسکنر: ${asIs(n)} فاکتور", "Recorded from scanner: $n ${pl(n, "invoice", "invoices")}", "له سکنر ثبت: $n بلونه")
  fun scannerFromMemory(n: Any): String = t("${asIs(n)} از حافظهٔ بارکدخوان", "$n from scanner memory", "$n د بارکوډ لوستونکي له حافظې")
}
