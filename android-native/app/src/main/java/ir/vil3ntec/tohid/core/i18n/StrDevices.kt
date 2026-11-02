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
  fun backgroundOn(n: Any): String = t("$n بارکدخوان وصل است", "$n ${pl(n, "scanner", "scanners")} connected", "$n بارکوډ لوستونکي وصل دي")
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
  fun pendingTitle(n: Any): String = t("$n اسکن در انتظار", "$n ${pl(n, "scan", "scans")} waiting", "$n سکنونه په تمه")
  fun pendingLine(codes: Any, units: Any): String = t("$codes کالا، روی‌هم $units عدد", "$codes ${pl(codes, "item", "items")}, $units in total", "$codes توکي، ټول $units")
  fun fromMemory(n: Any): String = t("$n اسکن از حافظهٔ بارکدخوان", "$n from the scanner's memory", "$n د بارکوډ لوستونکي له حافظې")
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
    "همهٔ $n اسکن کنار گذاشته شود؟ این کار برگشت ندارد.",
    "Set aside all $n ${pl(n, "scan", "scans")}? This cannot be undone.",
    "ټول $n سکنونه یو طرف ته شي؟ دا کار بېرته نه راګرځي.",
  )
  fun heldReady(n: Any, total: String): String = t(
    "$n فاکتورِ نگه‌داشته آماده است — جمع: $total",
    "$n held ${pl(n, "invoice", "invoices")} ready — total: $total",
    "$n ساتل شوي بلونه چمتو دي — ټول: $total",
  )
  val pay: String get() = t("پرداخت", "Pay", "تادیه")
  fun unknownCodes(n: Any): String = t(
    "$n بارکد کالا ندارد — با «ثبتِ کالا» ثبتش کنید",
    "$n ${pl(n, "barcode has", "barcodes have")} no item — add with \"New item\"",
    "$n بارکوډونه توکی نه لري — د «نوی توکی» په مرسته یې ثبت کړئ",
  )
  val newItem: String get() = t("ثبتِ کالا", "New item", "نوی توکی")
  fun countAdded(n: Any): String = t("شمارِ $n کالا از بارکدخوان افزوده شد", "Counts for $n ${pl(n, "item", "items")} added from the scanner", "د $n توکو شمېر له بارکوډ لوستونکي ورزیات شو")
}
