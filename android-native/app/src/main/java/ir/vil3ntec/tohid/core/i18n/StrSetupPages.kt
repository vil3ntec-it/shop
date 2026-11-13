package ir.vil3ntec.tohid.core.i18n

/**
 *  سرورهای دیگر، مجوز و PIN ِ کارمندان، دستگاه‌های دکان، پیدا کردنِ چاپگر و راه‌اندازیِ بارکدخوان در صفحه‌های قدیمیِ `ui/screens`
 *  که نسخهٔ ۳ صدا می‌زند (`Servers.kt`، `TeamStaff.kt`، `EasySetupAndroid.kt`). این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ
 *  زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۴). فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به
 *  کار رفته. کلیدِ بخشِ «سرورها» (`SettingsLayout.SERVERS`) و نامِ کلیدِ تأیید که روی سرور ثبت می‌شود داده‌اند و در صفحه «// داده:»
 *  دارند. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrSetupPages {
  // ── `Servers.kt` ──

  // بخشِ «سرورها»
  fun activeServer(name: Any?): String = t("فعال: ${asIs(name)}", "Active: $name", "فعال: $name")
  val mainServerOnly: String get() = t("فقط سرورِ اصلیِ برنامه", "Only the app's main server", "یوازې د اپ اصلي سرور")
  val serversIntro1: String get() = t(
    "برنامه می‌تواند به سرورِ دیگری هم وصل شود: سرورِ خودِ دکان در شبکهٔ دکان، سرورِ خانه یا سروری در اینترنت. ",
    "The app can also connect to another server: the shop's own server on the shop network, a home server or a server on the internet. ",
    "اپ کولی شي له بل سرور سره هم ونښلي: د دوکان خپل سرور د دوکان په شبکه کې، د کور سرور یا په انټرنېټ کې یو سرور. ",
  )
  val serversIntro2: String get() = t(
    "دادهٔ هر سرور روی همین دستگاه جداست و هیچ‌وقت به سرورِ دیگر فرستاده نمی‌شود.",
    "Each server's data is kept separately on this device and is never sent to another server.",
    "د هر سرور معلومات په همدې وسیله بېل ساتل کېږي او هېڅکله بل سرور ته نه لېږل کېږي.",
  )
  val serverAdded: String get() = t("سرور افزوده شد", "Server added", "سرور زیات شو")
  fun switchTo(name: Any?): String = t("رفتن به «${asIs(name)}»", "Switch to “$name”", "«$name» ته تګ")
  val switchNote1: String get() = t(
    "برنامه بسته می‌شود؛ دوباره که بازش کنید به این سرور وصل است. دفتر، عکس‌ها و نشستِ همین سرور سرِ جایشان ",
    "The app will close; when you open it again it's connected to this server. This server's ledger, photos and session stay ",
    "اپ بندېږي؛ چې بیا یې پرانیزئ له دې سرور سره نښتی وي. د دې سرور دفتر، انځورونه او ناسته په خپل ځای ",
  )
  val switchNote2: String get() = t(
    "بایگانی می‌مانند و با برگشتن باز می‌گردند — چیزی از این‌جا به آن سرور نمی‌رود.",
    "archived and come back when you return — nothing goes from here to that server.",
    "آرشیف پاتې کېږي او د راګرځېدو سره بېرته راځي — له دې ځایه هغه سرور ته څه نه ځي.",
  )
  val goAndClose: String get() = t("برو و ببند", "Go and close", "ولاړ شه او بند یې کړه")
  fun removeServer(name: Any?): String = t("برداشتنِ «${asIs(name)}»", "Remove “$name”", "د «$name» لرې کول")
  val removeNote: String get() = t(
    "فقط از فهرست برداشته می‌شود؛ دادهٔ بایگانی‌شدهٔ این سرور روی دستگاه می‌ماند و با افزودنِ دوبارهٔ همان سرور برمی‌گردد.",
    "It's only removed from the list; this server's archived data stays on the device and comes back if you add the same server again.",
    "یوازې له لړلیک څخه لرې کېږي؛ د دې سرور آرشیف شوي معلومات په وسیله پاتې کېږي او د همغه سرور د بیا زیاتولو سره بېرته راځي.",
  )
  val backToMain: String get() = t("برگشت به سرورِ اصلی", "Back to the main server", "اصلي سرور ته بېرته")
  val backToMainNote: String get() = t(
    "با خاموش کردن، برنامه به سرورِ اصلی برمی‌گردد و بسته می‌شود. دادهٔ سرورِ فعلی بایگانی می‌ماند.",
    "Turning it off returns the app to the main server and closes it. The current server's data stays archived.",
    "په بندولو سره اپ اصلي سرور ته ګرځي او بندېږي. د اوسني سرور معلومات آرشیف پاتې کېږي.",
  )
  val offAndClose: String get() = t("خاموش کن و ببند", "Turn off and close", "بند یې کړه او وتړه")
  val appPin: String get() = t("رمزِ برنامه", "App PIN", "د اپ پټنوم")
  val addServer: String get() = t("افزودنِ سرور", "Add server", "سرور زیاتول")
  val localNetwork: String get() = t("  شبکهٔ محلی", "  Local network", "  ځایي شبکه")
  val legacyServer: String get() = t(" — سرورِ قدیمی (بی شناسه)", " — old server (no ID)", " — زوړ سرور (بې پېژند)")
  fun fingerprint(fp: Any?): String = t("اثرِ انگشت: ${asIs(fp)}", "Fingerprint: $fp", "ګوتنښه: $fp")
  val useThisServer: String get() = t("رفتن به این سرور", "Switch to this server", "دې سرور ته تګ")
  val addressOrQr: String get() = t("نشانی (https://…) یا متنِ کیو‌آر", "Address (https://…) or QR text", "پته (https://…) یا د QR متن")
  val lanServer: String get() = t("شبکهٔ محلی (سرورِ داخلِ دکان)", "Local network (server inside the shop)", "ځایي شبکه (د دوکان دننه سرور)")
  val scanServerQr: String get() = t("اسکنِ کیو‌آرِ سرور", "Scan the server's QR", "د سرور QR سکن کړئ")
  val httpsOnly: String get() = t(
    "فقط https. سرورِ شبکهٔ دکان با گواهیِ خودامضا: اثرِ انگشتِ گواهی را با آن‌چه سرور هنگامِ شروع چاپ می‌کند مقایسه کنید.",
    "https only. A shop-network server with a self-signed certificate: compare the certificate fingerprint with what the server prints when it starts.",
    "یوازې https. د دوکان د شبکې سرور له ځان لاسلیک شوي سند سره: د سند ګوتنښه له هغه سره پرتله کړئ چې سرور د پیل پر مهال یې چاپوي.",
  )
  val legacyWarn: String get() = t(
    "سرورِ قدیمی: شناسه ندارد، پس جابه‌جا شدنِ سرور پشتِ این نشانی را نمی‌شود فهمید. اگر می‌شود، سرور را به‌روز کنید.",
    "Old server: it has no ID, so a different server behind this address can't be detected. Update the server if you can.",
    "زوړ سرور: پېژند نه لري، نو د دې پتې شاته د سرور بدلېدل نه شي معلومېدای. که کېږي، سرور تازه کړئ.",
  )
  fun serverFingerprint(fp: Any?): String = t("اثرِ انگشتِ سرور: ${asIs(fp)}", "Server fingerprint: $fp", "د سرور ګوتنښه: $fp")
  val fingerprintCompare: String get() = t(
    "همین را با اثرِ انگشتی که پنلِ سرور نشان می‌دهد مقایسه کنید؛ پس از تأیید، برنامه فقط به همین سرور وصل می‌شود.",
    "Compare this with the fingerprint the server panel shows; after you confirm, the app connects only to this server.",
    "دا د سرور په پینل کې ښودل شوې ګوتنښې سره پرتله کړئ؛ له تایید وروسته اپ یوازې له همدې سرور سره نښلي.",
  )
  fun sameServer(name: Any?): String = t(
    "این همان «${asIs(name)}» است؛ نشانی به آن افزوده می‌شود.",
    "This is the same “$name”; the address is added to it.",
    "دا همغه «$name» دی؛ پته ورته زیاتېږي.",
  )
  val confirmAndAdd: String get() = t("تأیید و افزودن", "Confirm and add", "تایید او زیاتول")
  val unknownCert: String get() = t("گواهیِ ناشناس", "Unknown certificate", "ناپېژندل شوی سند")
  val selfSignedNote: String get() = t(
    "این سرور گواهیِ خودامضا دارد. فقط اگر این اثرِ انگشت با آن‌چه سرور هنگامِ شروع چاپ کرده یکی است، تأیید کنید:",
    "This server has a self-signed certificate. Confirm only if this fingerprint matches what the server printed when it started:",
    "دا سرور له ځان لاسلیک شوی سند لري. یوازې هغه وخت تایید کړئ چې دا ګوتنښه د سرور د پیل پر مهال چاپ شوې ګوتنښې سره یو شان وي:",
  )
  val sameConfirm: String get() = t("یکی است، تأیید", "It matches, confirm", "یو شان ده، تایید")
  val serverQr: String get() = t("کیو‌آرِ سرور", "Server QR", "د سرور QR")
  val histAdded: String get() = t("افزوده شد", "Added", "زیات شو")
  val histAddUrl: String get() = t("نشانیِ دوم", "Second address", "دویمه پته")
  val histSchedule: String get() = t("رفتن (پس از بستن)", "Switch (after closing)", "تګ (له بندولو وروسته)")
  val histSwitch: String get() = t("عوض شد", "Switched", "بدل شو")
  val histFlagOn: String get() = t("روشن شد", "Turned on", "روښانه شو")
  val histFlagOff: String get() = t("خاموش شد", "Turned off", "بند شو")

  // ── `TeamStaff.kt` ──

  // مجوزِ کارمندان
  val permVoid: String get() = t("لغوِ فاکتور", "Invoice void", "د بل لغوه کول")
  val permReturns: String get() = t("برگشت و بازپرداخت", "Returns and refunds", "بېرته ورکړه او بېرته تادیه")
  val permSafe: String get() = t("گاوصندوق: شمارش و جابه‌جاییِ پول", "Safe: counting and moving money", "ګاوصندوق: د پیسو شمېرل او لېږدول")
  val withManagerOk: String get() = t("با تأییدِ مدیر", "With manager's approval", "د مدیر په تایید")
  val denied: String get() = t("ممنوع", "Not allowed", "منع")
  val noCap: String get() = t("بی سقف", "No limit", "بې حده")
  fun capPct(n: Any?): String = t("حداکثر ${asIs(n)}٪", "Max $n%", "تر ټولو ډېر $n٪")
  fun capAmount(n: Any?): String = t("حداکثر ${asIs(n)} ؋", "Max $n ؋", "تر ټولو ډېر $n ؋")
  val aboveNeedsOk: String get() = t(
    " — بیشتر از آن با تأییدِ مدیر",
    " — more than that needs a manager's approval",
    " — له هغه ډېر د مدیر تایید غواړي",
  )
  fun capOf(perm: Any?): String = t("سقفِ ${asIs(perm)}", "Limit for $perm", "د $perm حد")
  val maxPctField: String get() = t("حداکثر درصد (خالی = بی سقف)", "Max percent (empty = no limit)", "تر ټولو ډېر سلنه (تش = بې حده)")
  val maxAmountField: String get() = t(
    "حداکثر مبلغ به افغانی (خالی = بی سقف)",
    "Max amount in afghani (empty = no limit)",
    "تر ټولو ډېر مبلغ په افغانیو (تش = بې حده)",
  )

  // PIN و کلیدِ تأیید
  val keyRegistered: String get() = t("کلیدِ تأیید ثبت شد", "Approval key registered", "د تایید کیلي ثبت شوه")
  val keyMismatch: String get() = t(
    "پاسخِ سرور با این کلید نخواند",
    "The server's reply didn't match this key",
    "د سرور ځواب له دې کیلي سره سمون ونه خوړ",
  )
  val pinAndKey: String get() = t("PIN و کلیدِ تأیید", "PIN and approval key", "پټنوم او د تایید کیلي")
  val pinIntro1: String get() = t(
    "با این PIN روی همین دستگاه تخفیف، لغو یا برگشتِ بیش از سقفِ کارمند را تأیید می‌کنید. ",
    "With this PIN you approve, on this device, a discount, void or return above a staff member's limit. ",
    "په دې پټنوم سره په همدې وسیله د کارکوونکي له حده ډېر تخفیف، لغوه یا بېرته ورکړه تاییدوئ. ",
  )
  val pinIntro2: String get() = t(
    "PIN و کلیدِ خصوصی از این دستگاه بیرون نمی‌روند.",
    "The PIN and the private key never leave this device.",
    "پټنوم او شخصي کیلي له دې وسیلې نه وځي.",
  )
  val noPin: String get() = t("PIN ندارد", "No PIN", "پټنوم نشته")
  fun lockedAfter(n: Any?): String = t("قفل پس از ${asIs(n)} خطا", "Locked after $n errors", "له $n تېروتنو وروسته قفل")
  val readyToApprove: String get() = t("آمادهٔ تأیید", "Ready to approve", "تایید ته چمتو")
  val keyNotYet: String get() = t("کلید هنوز ثبت نشده", "Key not registered yet", "کیلي لا ثبت شوې نه ده")
  val registerKey: String get() = t("ثبتِ کلید", "Register key", "کیلي ثبتول")
  val setPin: String get() = t("گذاشتنِ PIN", "Set a PIN", "پټنوم ایښودل")
  val newPin: String get() = t("PINِ تازه", "New PIN", "نوی پټنوم")
  val samePinAgain: String get() = t("دوباره همان PIN", "The same PIN again", "همغه پټنوم بیا")
  val pinHint: String get() = t(
    "شش رقم؛ نه همه یکی و نه پشتِ سرِ هم",
    "Six digits; not all the same and not in sequence",
    "شپږ عددونه؛ نه ټول یو شان او نه یو په بل پسې",
  )
  val pinTooSimple: String get() = t(
    "این PIN ساده است؛ شش رقمِ دیگری بزنید",
    "This PIN is too simple; enter six other digits",
    "دا پټنوم ډېر ساده دی؛ نور شپږ عددونه ولیکئ",
  )
  val pinsDiffer: String get() = t("دو PIN یکی نبودند؛ از نو", "The two PINs didn't match; start again", "دواړه پټنومونه یو شان نه وو؛ له سره")

  // دستگاه‌ها
  fun rejectedTries(n: Any?): String = t(
    " · ${asIs(n)} تلاشِ ردشده",
    " · $n ${pl(n ?: 0, "rejected attempt", "rejected attempts")}",
    " · $n رد شوې هڅې",
  )
  val blockNote1: String get() = t(
    "همهٔ نشست‌های این دستگاه بسته می‌شود و از این پس هیچ چیزی از آن پذیرفته نمی‌شود. ",
    "All of this device's sessions are closed and from now on nothing from it is accepted. ",
    "د دې وسیلې ټولې ناستې بندېږي او له دې وروسته ترې هېڅ نه منل کېږي. ",
  )
  val blockNote2: String get() = t(
    "فروش‌هایی که هنوز بالا نرفته‌اند روی خودِ دستگاه و در پشتیبانِ آن می‌مانند.",
    "Sales that haven't been uploaded yet stay on the device itself and in its backup.",
    "هغه پلور چې لا پورته شوي نه دي په خپله وسیله او د هغې په بیک اپ کې پاتې کېږي.",
  )

  // ── `EasySetupAndroid.kt` ──

  // پیدا کردنِ خودکارِ چاپگر
  val viaNetwork: String get() = t("شبکه · درگاهِ ۹۱۰۰ باز است", "Network · port 9100 is open", "شبکه · ۹۱۰۰ دروازه پرانیستې ده")
  val viaBtPaired: String get() = t("بلوتوث · جفت‌شده", "Bluetooth · paired", "بلوتوث · جوړه شوې")
  val viaBtNearby: String get() = t("بلوتوث · نزدیک، هنوز جفت نشده", "Bluetooth · nearby, not paired yet", "بلوتوث · نږدې، لا جوړه شوې نه ده")
  val maybeNotPrinter: String get() = t(" · شاید چاپگر نباشد", " · may not be a printer", " · کېدای شي چاپګر نه وي")
  val btSearching: String get() = t(
    "جست‌وجوی بلوتوث… چاپگر را روشن و نزدیک نگه دارید",
    "Searching Bluetooth… keep the printer on and close by",
    "د بلوتوث لټون… چاپګر روښانه او نږدې وساتئ",
  )
  val btDone: String get() = t("جست‌وجوی بلوتوث تمام شد", "Bluetooth search finished", "د بلوتوث لټون پای ته ورسېد")
  val btNoneNew: String get() = t(
    "دستگاهِ بلوتوثیِ تازه‌ای پیدا نشد — چاپگر روشن است و دکمهٔ جفت‌سازی‌اش را زده‌اید؟",
    "No new Bluetooth device found — is the printer on and did you press its pairing button?",
    "نوې بلوتوث وسیله ونه موندل شوه — چاپګر روښانه دی او د جوړولو تڼۍ یې مو کېکاږلې ده؟",
  )
  val btNeedsNearby: String get() = t(
    "بی اجازهٔ «دستگاه‌های نزدیک» جست‌وجوی بلوتوث ممکن نیست",
    "Bluetooth search isn't possible without the “Nearby devices” permission",
    "د «نږدې وسایلو» له اجازې پرته د بلوتوث لټون نه کېږي",
  )
  val paperWidthNote: String get() = t(
    "عرضِ کاغذِ چاپگری که افزوده می‌شود (بعداً در «تنظیم» هم عوض می‌شود):",
    "Paper width of the printer being added (can be changed later in “Setup” too):",
    "د زیاتېدونکي چاپګر د کاغذ پلنوالی (وروسته په «تنظیم» کې هم بدلېږي):",
  )
  val netWifiLan: String get() = t("شبکه (وای‌فای / LAN)", "Network (Wi-Fi / LAN)", "شبکه (وای‌فای / LAN)")
  val noPrivateNet: String get() = t(
    "این دستگاه به وای‌فای یا شبکهٔ سیمیِ خصوصی وصل نیست؛ جست‌وجوی شبکه فقط زیرشبکهٔ خودِ دکان را می‌گردد.",
    "This device isn't on Wi-Fi or a private wired network; network search only scans the shop's own subnet.",
    "دا وسیله له وای‌فای یا شخصي سیمي شبکې سره نه ده نښتې؛ د شبکې لټون یوازې د دوکان خپله فرعي شبکه ګوري.",
  )
  val subnetOnly: String get() = t(
    " — فقط همین زیرشبکه، درگاهِ ۹۱۰۰؛ چیزی به چاپگر فرستاده نمی‌شود.",
    " — this subnet only, port 9100; nothing is sent to the printer.",
    " — یوازې همدا فرعي شبکه، ۹۱۰۰ دروازه؛ چاپګر ته هېڅ نه لېږل کېږي.",
  )
  val netSearch: String get() = t("جست‌وجوی شبکه", "Search the network", "د شبکې لټون")
  val btSearch12: String get() = t("جست‌وجوی بلوتوثِ نزدیک (۱۲ ثانیه)", "Search nearby Bluetooth (12 seconds)", "د نږدې بلوتوث لټون (۱۲ ثانیې)")
  val btPairInSystem: String get() = t(
    "چاپگرِ بلوتوثی را در تنظیماتِ بلوتوثِ سیستم جفت کنید؛ درگاهِ COM ِ آن در فهرستِ پایین می‌آید.",
    "Pair the Bluetooth printer in the system's Bluetooth settings; its COM port then shows in the list below.",
    "بلوتوث چاپګر د سیسټم د بلوتوث په امستنو کې جوړه کړئ؛ د هغه COM دروازه په لاندې لړلیک کې راځي.",
  )
  val findings: String get() = t("یافته‌ها", "Found", "موندل شوي")
  val nothingYet: String get() = t("هنوز چیزی پیدا نشده.", "Nothing found yet.", "لا څه نه دي موندل شوي.")
  fun idPrintSent(name: Any?): String = t(
    "چاپِ شناسایی به «${asIs(name)}» رفت…",
    "Identification print sent to “$name”…",
    "د پېژندنې چاپ «$name» ته ولاړ…",
  )
  val idPrintDone: String get() = t(
    "چاپِ شناسایی انجام شد — نشانی روی کاغذ است",
    "Identification print done — the address is on the paper",
    "د پېژندنې چاپ وشو — پته پر کاغذ ده",
  )
  val idPrint: String get() = t("چاپِ شناسایی", "Identification print", "د پېژندنې چاپ")
  val registered: String get() = t("ثبت شده", "Registered", "ثبت شوی")
  val pairAndAdd: String get() = t("جفت کردن و افزودن", "Pair and add", "جوړول او زیاتول")

  // راه‌اندازیِ آسانِ بارکدخوان
  val scannerEasySetup: String get() = t("راه‌اندازیِ آسانِ بارکدخوان", "Easy barcode scanner setup", "د بارکوډ لوستونکي اسانه تنظیم")
  val scannerSetupNote: String get() = t(
    "برگهٔ بارکدِ آزمون را چاپ کنید (یا از روی صفحه اسکن کنید) و «آزمونِ بارکدخوان» را بزنید؛ پیشوند، پسوند و Enter خودکار پیدا می‌شود.",
    "Print the test barcode sheet (or scan from the screen) and press “Scanner test”; prefix, suffix and Enter are detected automatically.",
    "د ازموینې د بارکوډ پاڼه چاپ کړئ (یا له پردې سکن کړئ) او «د بارکوډ لوستونکي ازموینه» کېکاږئ؛ مختاړی، وروستاړی او Enter پخپله موندل کېږي.",
  )
  val scannerTest: String get() = t("آزمونِ بارکدخوان", "Scanner test", "د بارکوډ لوستونکي ازموینه")
  fun sheetNotDrawn(err: Any?): String = t("برگه کشیده نشد: ${asIs(err)}", "The sheet couldn't be drawn: $err", "پاڼه جوړه نه شوه: $err")
  val sheetPreview: String get() = t("پیش‌نمایشِ برگه", "Sheet preview", "د پاڼې مخکتنه")
  val sheetSent: String get() = t(
    "برگهٔ بارکدخوان به چاپگرِ پیش‌فرض رفت…",
    "Scanner sheet sent to the default printer…",
    "د بارکوډ لوستونکي پاڼه اصلي چاپګر ته ولاړه…",
  )
  val sheetPrinted: String get() = t("برگهٔ بارکدخوان چاپ شد", "Scanner sheet printed", "د بارکوډ لوستونکي پاڼه چاپ شوه")
  val printSheet: String get() = t("چاپِ برگهٔ بارکدخوان", "Print the scanner sheet", "د بارکوډ لوستونکي پاڼه چاپول")
  val setupSheet: String get() = t("برگهٔ راه‌اندازیِ بارکدخوان", "Barcode scanner setup sheet", "د بارکوډ لوستونکي د تنظیم پاڼه")
  fun scanThis(code: Any?): String = t(
    "این بارکد را (از روی برگه یا همین صفحه) اسکن کنید؛ برنامه می‌بیند بارکدخوان پیش و پس از «${asIs(code)}» چه می‌فرستد.",
    "Scan this barcode (from the sheet or this screen); the app sees what the scanner sends before and after “$code”.",
    "دا بارکوډ (له پاڼې یا همدې پردې) سکن کړئ؛ اپ ګوري چې بارکوډ لوستونکی د «$code» مخکې او وروسته څه لېږي.",
  )
  val testBarcode: String get() = t("بارکدِ آزمون", "Test barcode", "د ازموینې بارکوډ")
  val tapAndScan: String get() = t("این‌جا بزنید و اسکن کنید", "Tap here and scan", "دلته کېکاږئ او سکن کړئ")
  val currentSetup: String get() = t(
    "تنظیمِ کنونیِ بارکدخوانِ همین دستگاه همین است.",
    "This is the current scanner setting of this device.",
    "د دې وسیلې د بارکوډ لوستونکي اوسنی تنظیم همدا دی.",
  )
  val affixSaved: String get() = t(
    "پیشوند و پسوندِ بارکدخوان ذخیره شد",
    "Scanner prefix and suffix saved",
    "د بارکوډ لوستونکي مختاړی او وروستاړی خوندي شول",
  )
  val saveAffix: String get() = t("ذخیرهٔ همین پیشوند و پسوند", "Save this prefix and suffix", "همدا مختاړی او وروستاړی خوندي کړئ")
  val ownerOnlySave: String get() = t(
    "ذخیرهٔ تنظیم فقط برای صاحب و مدیرِ دکان است.",
    "Only the shop owner and manager can save the setting.",
    "تنظیم یوازې د دوکان څښتن او مدیر خوندي کولی شي.",
  )
  val affixWhen: String get() = t(
    "پیشوند و پسوندِ ذخیره‌شده وقتی به کار می‌رود که «ابزارهای تازهٔ دستگاه» (بالای همین صفحه) روشن باشد.",
    "The saved prefix and suffix are used when “New device tools” (top of this page) is on.",
    "خوندي شوی مختاړی او وروستاړی هغه وخت کارېږي چې «د وسیلو نوې اوزار» (د همدې پاڼې سر کې) روښانه وي.",
  )
  val noMakerCodes: String get() = t(
    "کدِ تنظیمِ سازنده‌ها در برنامه نیست (برای هر سازنده جداست و کدِ حدسی ساخته نمی‌شود)؛ Enter و پیشوند را از دفترچهٔ خودِ بارکدخوان تنظیم کنید.",
    "Manufacturer setup codes aren't in the app (they differ for each maker and no guessed code is made); set Enter and the prefix from the scanner's own manual.",
    "د جوړونکو د تنظیم کوډونه په اپ کې نشته (د هر جوړونکي جلا دي او اټکلي کوډ نه جوړېږي)؛ Enter او مختاړی د بارکوډ لوستونکي له خپل لارښود څخه تنظیم کړئ.",
  )
}
