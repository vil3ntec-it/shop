package ir.vil3ntec.tohid.core.i18n

/**
 *  تنظیمات: زبان، هشدارها، ظاهر، تاریخ و رقم، قفل، کدِ شاگرد، داده و پشتیبان، درباره.
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrSettings {

  // زبان
  val language: String get() = t("زبان", "Language", "ژبه")
  val languageNote: String get() = t("زبانِ نوشته‌های برنامه روی همین دستگاه. داده، فاکتورهای ذخیره‌شده و همگام‌سازی عوض نمی‌شوند.", "The app's language on this device. Data, saved invoices and sync are not affected.", "په دې وسیله کې د اپ ژبه. معلومات، خوندي شوي بلونه او همغږي نه بدلېږي.")

  // فایل و پشتیبان (پیام‌ها)
  val pathNotWritable: String get() = t("این مسیر برای نوشتن باز نشد", "This location couldn't be opened for writing", "دا ځای د لیکلو لپاره خلاص نه شو")
  fun backupMadeWithPhotos(n: Any): String = t("پشتیبان ساخته شد — با $n عکس", "Backup created — with $n ${pl(n, "photo", "photos")}", "بیک‌اپ جوړ شو — له $n ${pl(n, "انځور", "انځورونو")} سره")
  val backupMade: String get() = t("پشتیبان ساخته شد", "Backup created", "بیک‌اپ جوړ شو")
  fun backupFailed(r: Any): String = t("فایل پشتیبان ساخته نشد: $r", "The backup file wasn't created: $r", "د بیک‌اپ دوتنه جوړه نه شوه: $r")
  val ledgerFileMade: String get() = t("فایل دفتر ساخته شد — بدون عکس‌ها", "Ledger file created — without photos", "د دفتر دوتنه جوړه شوه — بې انځورونو")
  fun fileFailed(r: Any): String = t("فایل ساخته نشد: $r", "The file wasn't created: $r", "دوتنه جوړه نه شوه: $r")
  val exportOwnerOnly: String get() = t("خروجی کامل فقط برای صاحب دکان یا مدیر است", "Full export is only for the shop owner or a manager", "بشپړ ایستل یوازې د دوکان مالک یا مدیر لپاره دي")
  val excelExportMade: String get() = t("فایل اکسلِ همهٔ داده‌ها ساخته شد", "Excel file of all data created", "د ټولو معلوماتو اکسل دوتنه جوړه شوه")
  val jsonExportMade: String get() = t("فایل JSONِ همهٔ داده‌ها ساخته شد", "JSON file of all data created", "د ټولو معلوماتو JSON دوتنه جوړه شوه")
  fun exportFailed(r: Any): String = t("خروجی ساخته نشد: $r", "Export failed: $r", "ایستل ونه شول: $r")
  val fileNotOpened: String get() = t("این فایل باز نشد", "This file couldn't be opened", "دا دوتنه خلاصه نه شوه")
  val invalidBackup: String get() = t("فایل پشتیبان معتبر نیست", "The backup file isn't valid", "د بیک‌اپ دوتنه سمه نه ده")
  val cameraReady: String get() = td("دوربین آماده است — بارکدخوان کار می‌کند", "Camera ready — the scanner works", "کمره چمتو ده — بارکوډ لوستونکی کار کوي", "Barcode scanner ready", "بارکوډ لوستونکی چمتو دی")
  val cameraDenied: String get() = t("بدون اجازهٔ دوربین، بارکدخوان باز نمی‌شود", "Without camera permission, the scanner can't open", "د کمرې له اجازې پرته بارکوډ لوستونکی نه خلاصېږي")
  val settingsIntro: String get() = t("هشدارها، ظاهر، داده‌ها و پشتیبان — حساب و نام دکان در «حساب من» است", "Alerts, appearance, data and backup — your account and shop name are under “My account”", "خبرتیاوې، بڼه، معلومات او بیک‌اپ — حساب او د دوکان نوم په «زما حساب» کې دي")

  // هشدارها
  fun debtFrom(a: Any): String = t("قرض از $a ؋ به بالا", "Debts of $a ؋ or more", "له $a ؋ او زیات پور")
  val alertsNote: String get() = t("وقتی کالایی تمام شود، قرضِ کسی از این مبلغ بگذرد، یا اشتراک رو به پایان باشد، برنامه خبر می‌دهد — حتی وقتی بسته است.", "The app notifies you when an item runs out, someone's debt passes this amount, or the subscription is about to end — even when the app is closed.", "کله چې یو توکی خلاص شي، د چا پور له دې مبلغ واوړي، یا ګډون پای ته نږدې وي، اپ خبر درکوي — حتی کله چې بند وي.")
  val debtAlertLabel: String get() = t("هشدار قرض از این مبلغ به بالا (افغانی)", "Debt alert from this amount (AFN)", "له دې مبلغ څخه د پور خبرتیا (افغانۍ)")
  val zeroOff: String get() = t("صفر یعنی هشدارِ قرض خاموش. کالای تمام‌شده و اشتراک سرِ جایشان می‌مانند.", "Zero turns debt alerts off. Sold-out and subscription alerts stay on.", "صفر یعني د پور خبرتیا بنده ده. د خلاصو توکو او ګډون خبرتیاوې پاتې کېږي.")

  // ظاهر
  val light: String get() = t("روشن", "Light", "روښانه")
  val dark: String get() = t("تاریک", "Dark", "تیاره")
  val likePhone: String get() = td("مثل گوشی", "Like the phone", "د موبایل په څېر", "Like the system", "د سیستم په څېر")
  val reduceMotionNote: String get() = td("اگر گوشی «کاهش حرکت» یا حالت ذخیرهٔ باتری روشن باشد، انیمیشن‌ها خاموش می‌شوند. با این کلید همیشه روشن می‌مانند.", "If the phone has “Reduce motion” or battery saver on, animations are turned off. With this switch they always stay on.", "که په موبایل کې «حرکت کمول» یا د بیټرۍ د سپما حالت روښانه وي، انیمیشنونه بندېږي. په دې کلي سره تل روښانه پاتې کېږي.", "If the system has “Reduce motion” on, animations are turned off. With this switch they always stay on.", "که سیستم «حرکت کمول» روښانه کړی وي، انیمیشنونه بندېږي. په دې کلي سره تل روښانه پاتې کېږي.")
  val animationsOn: String get() = t("انیمیشن روشن", "Animations on", "انیمیشن روښانه")
  val likePhoneSetting: String get() = td("مثل تنظیم گوشی", "Follow the phone setting", "د موبایل د امستنې په څېر", "Follow the system setting", "د سیستم د امستنې په څېر")

  // کالای منقضی
  val notSold: String get() = t("فروخته نمی‌شود", "Not sold", "نه پلورل کېږي")
  val warnOnly: String get() = t("فقط هشدار", "Warn only", "یوازې خبرتیا")
  val expiryNote: String get() = t("هشدارِ انقضا همیشه هست. اگر «فروخته نشود» را بزنید، مقدارِ محموله‌های منقضی از موجودیِ فروختنی کم می‌شود و فروشِ بیش از آن ثبت نمی‌شود.", "Expiry alerts are always on. If you choose “Don't sell”, expired batches are taken out of sellable stock and sales beyond that aren't recorded.", "د نېټې د تېرېدو خبرتیا تل شته. که «ونه پلورل شي» وټاکئ، د تېرې نېټې د کڅوړو مقدار له پلورلو وړ موجودۍ کمېږي او له هغې زیات پلور نه ثبتېږي.")
  val dontSell: String get() = t("فروخته نشود", "Don't sell", "ونه پلورل شي")

  // دوربین
  val cameraScanner: String get() = td("دوربین بارکدخوان", "Barcode camera", "د بارکوډ کمره", "Barcode scanner", "بارکوډ لوستونکی")
  val permitted: String get() = t("اجازه داده شده", "Permission granted", "اجازه ورکړل شوې")
  val notPermitted: String get() = t("هنوز اجازه داده نشده", "Permission not granted yet", "لا اجازه نه ده ورکړل شوې")
  val cameraNeeded: String get() = td("برنامه برای اسکن بارکد محصولات به دوربین نیاز دارد.", "The app needs the camera to scan product barcodes.", "اپ د توکو د بارکوډ سکن کولو لپاره کمرې ته اړتیا لري.", "Connect a USB or Bluetooth barcode scanner to the computer; it works like a keyboard and needs no installation. Without a scanner, type the code in the sale box.", "د USB یا بلوتوث بارکوډ لوستونکی کمپیوټر ته وصل کړئ؛ د کیبورډ په څېر کار کوي او نصب ته اړتیا نه لري. بې بارکوډ لوستونکي، کوډ د پلور په بکس کې ولیکئ.")
  val testCamera: String get() = td("آزمایش دوربین", "Test camera", "کمره وازمویئ", "Test scanner", "بارکوډ لوستونکی وازمویئ")
  val requestCamera: String get() = t("درخواست دسترسی دوربین", "Request camera access", "د کمرې لاسرسی وغواړئ")

  // قفل برنامه
  val lockOnPin: String get() = t("روشن — با رمز باز می‌شود", "On — opens with a PIN", "روښانه — په پټنوم خلاصېږي")
  val off: String get() = t("خاموش", "Off", "بند")
  val lockNote: String get() = td("تا امروز هر کسی گوشی را برمی‌داشت، حساب‌های دکان جلویش باز بود: طلب مشتری‌ها، سود، قیمت خرید. با رمز، تا زده نشود هیچ‌چیز دیده نمی‌شود.", "Until now, anyone who picked up the phone could see the shop's accounts: customer debts, profit, purchase prices. With a PIN, nothing is visible until it's entered.", "تر نن پورې هر چا چې موبایل پورته کاوه، د دوکان حسابونه ورته خلاص وو: د پیرودونکو پورونه، ګټه، د پېرود بیه. په پټنوم سره، تر څو یې ونه لیکئ هېڅ شی نه ښکاري.", "Until now, anyone who sat at the computer could see the shop's accounts: customer debts, profit, purchase prices. With a PIN, nothing is visible until it's entered.", "تر نن پورې هر چا چې د کمپیوټر شاته کېناست، د دوکان حسابونه ورته خلاص وو: د پیرودونکو پورونه، ګټه، د پېرود بیه. په پټنوم سره، تر څو یې ونه لیکئ هېڅ شی نه ښکاري.")
  val oldPin4: String get() = t("رمز شما چهار رقمی است. رمزهای تازه شش رقمی‌اند — اگر خواستید عوضش کنید.", "Your PIN has four digits. New PINs have six — change it if you like.", "ستاسو پټنوم څلور عدده دی. نوي پټنومونه شپږ عدده دي — که مو خوښه وي بدل یې کړئ.")
  val changePin: String get() = t("عوض کردن رمز", "Change PIN", "پټنوم بدل کړئ")
  val removePin: String get() = t("برداشتن رمز", "Remove PIN", "پټنوم لرې کړئ")
  val lockRemoved: String get() = t("قفل برداشته شد", "Lock removed", "قفل لرې شو")
  val setPin: String get() = t("گذاشتن رمز", "Set a PIN", "پټنوم کېږدئ")
  val appPin: String get() = t("رمز برنامه", "App PIN", "د اپ پټنوم")
  val pinNote: String get() = t("یک رمزِ شش رقمی. هر بار که برنامه باز شود همین پرسیده می‌شود.", "A six-digit PIN. It's asked every time the app opens.", "یو شپږ عدده پټنوم. هر ځل چې اپ خلاصېږي، همدا پوښتل کېږي.")
  val pin: String get() = t("رمز", "PIN", "پټنوم")
  val pinAgain: String get() = t("دوباره", "Repeat", "بیا")
  val put: String get() = t("بگذار", "Set", "کېږدئ")
  val passwordChanged: String get() = t("رمز عوض شد", "Password changed", "پټنوم بدل شو")
  val lockTurnedOn: String get() = t("قفل برنامه روشن شد", "App lock turned on", "د اپ قفل روښانه شو")

  // حساب و دستگاه‌ها
  val serverPassword: String get() = t("رمزِ ورود به سرور", "Your server sign-in password", "سرور ته د ننوتلو پټنوم")
  val lostPhone: String get() = td("گوشی گم شد؟ نشستش را ببندید", "Lost a phone? Close its session", "موبایل مو ورک شو؟ ناسته یې بنده کړئ", "Lost a device? Close its session", "وسیله مو ورکه شوه؟ ناسته یې بنده کړئ")
  val pwdMin8: String get() = t("رمزِ تازه دستِ‌کم هشت نویسه باشد.", "The new password must be at least eight characters.", "نوی پټنوم باید لږ تر لږه اته توري وي.")
  val currentPassword: String get() = t("رمز فعلی", "Current password", "اوسنی پټنوم")
  val changePassword: String get() = t("تغییر رمز", "Change password", "پټنوم بدل کړئ")
  val reading: String get() = t("در حال خواندن…", "Loading…", "لوستل کېږي…")
  val noDevices: String get() = t("دستگاهی ثبت نشده است.", "No devices registered.", "هېڅ وسیله نه ده ثبت شوې.")
  val thisDevice: String get() = td("همین گوشی", "This phone", "همدا موبایل", "This computer", "همدا کمپیوټر")
  val closeSession: String get() = t("ببند", "Close", "بند یې کړه")
  val sessionClosed: String get() = t("نشست بسته شد", "Session closed", "ناسته بنده شوه")

  // کدِ شاگرد
  val staffCodeSub: String get() = t("کدی که کارمندتان با آن روی همین دکان می‌آید", "The code your staff use to join this shop", "هغه کوډ چې کارکوونکی مو پرې همدې دوکان ته راځي")
  val staffCodeCopied: String get() = t("کد شاگرد کپی شد", "Staff code copied", "د شاګرد کوډ کاپي شو")
  val staffCodeNote: String get() = t("این کد را به شاگردتان بدهید تا در صفحهٔ ورود بزند و روی همین دکان بیاید — ایمیل و شماره لازم نیست. با کد، حسابِ خودش ساخته می‌شود و هر کارش در «سابقه عملیات» به نامِ خودش می‌نشیند. تنظیمات و عددهای سود برای او بسته است.", "Give this code to your staff member to enter on the sign-in screen and join this shop — no email or phone number needed. The code creates their own account, and everything they do is recorded under their name in the “Activity log”. Settings and profit figures are closed to them.", "دا کوډ خپل شاګرد ته ورکړئ چې د ننوتلو په پاڼه کې یې ولیکي او همدې دوکان ته راشي — ایمیل او شمېرې ته اړتیا نشته. په کوډ سره د هغه خپل حساب جوړېږي او هر کار یې په «د کړنو تاریخچه» کې د هغه په نوم ثبتېږي. امستنې او د ګټې شمېرې د هغه لپاره بندې دي.")
  val creating: String get() = t("در حال ساخت…", "Creating…", "جوړېږي…")
  val newCode: String get() = t("ساخت کد تازه", "Create a new code", "نوی کوډ جوړ کړئ")
  val staffCodeNeedsAccount: String get() = t("کد شاگرد روی سرور ساخته می‌شود، پس اول وارد حساب شوید و دکانتان را بسازید.", "Staff codes are created on the server, so sign in and create your shop first.", "د شاګرد کوډ په سرور کې جوړېږي، نو لومړی حساب ته ننوځئ او خپل دوکان جوړ کړئ.")
  val staffCodeOffline: String get() = t("کد خوانده نشد — برای گرفتنش به اینترنت نیاز است. همین کد در «بیشتر → کارمندان دکان» هم هست.", "Couldn't load the code — you need internet to get it. The same code is also under “More → Shop staff”.", "کوډ ونه لوستل شو — د اخیستو لپاره یې انټرنېټ ته اړتیا ده. همدا کوډ په «نور → د دوکان کارکوونکي» کې هم شته.")
  val rotateQ: String get() = t("کد شاگرد تازه ساخته شود؟", "Create a new staff code?", "د شاګرد نوی کوډ جوړ شي؟")
  val rotateWarn: String get() = t("کد فعلی از کار می‌افتد و هر شاگردی که با آن وارد شده، دفعهٔ بعد باید کد تازه را بزند.", "The current code stops working, and any staff member who signed in with it will need the new code next time.", "اوسنی کوډ له کاره لوېږي، او هر شاګرد چې پرې ننوتلی، بله ځل به نوی کوډ لیکي.")
  val newCodeMade: String get() = t("کد شاگرد تازه ساخته شد", "New staff code created", "د شاګرد نوی کوډ جوړ شو")
  val make: String get() = t("بساز", "Create", "جوړ یې کړه")

  // داده‌ها و پشتیبان
  fun dataSummary(p: Any, s: Any, d: Any): String = t("$p کالا — $s فاکتور — $d قرض‌دار", "$p ${pl(p, "item", "items")} — $s ${pl(s, "invoice", "invoices")} — $d ${pl(d, "debtor", "debtors")}", "$p ${pl(p, "توکی", "توکي")} — $s ${pl(s, "بل", "بلونه")} — $d پوروړي")
  val fullBackup: String get() = t("گرفتن پشتیبان کامل", "Full backup", "بشپړ بیک‌اپ واخلئ")
  val fullBackupDesc: String get() = t("دفتر و عکس‌های کالاها، در یک فایل", "Ledger and product photos in one file", "دفتر او د توکو انځورونه، په یوه دوتنه کې")
  val ledgerOnly: String get() = t("پشتیبان فقط دفتر", "Ledger-only backup", "یوازې د دفتر بیک‌اپ")
  val ledgerOnlyDesc: String get() = t("بدون عکس — همان فایلی که نسخهٔ وب می‌خواند", "Without photos — the same file the web version reads", "بې انځورونو — همغه دوتنه چې وېب نسخه یې لولي")
  val exportExcel: String get() = t("خروجی کامل داده — اکسل", "Full data export — Excel", "د ټولو معلوماتو ایستل — اکسل")
  val exportExcelDesc: String get() = t("همهٔ داده‌ها، هر بخش در یک برگه با سرستونِ فارسی", "All data, each section on its own sheet with Persian column headers", "ټول معلومات، هره برخه په جلا پاڼه کې د فارسي سرلیکونو سره")
  val exportJson: String get() = t("خروجی کامل داده — JSON", "Full data export — JSON", "د ټولو معلوماتو ایستل — JSON")
  val exportJsonDesc: String get() = t("همهٔ داده‌ها با سرآیندِ نسخه‌دار، برای بردن به هر جای دیگر", "All data with a versioned header, to take anywhere else", "ټول معلومات د نسخې لرونکي سرلیک سره، بل هر ځای ته د وړلو لپاره")
  val restoreFromFile: String get() = t("بازیابی از فایل", "Restore from file", "له دوتنې بېرته راګرځول")
  val restoreDesc: String get() = t("جایگزینی اطلاعات فعلی با یک پشتیبان", "Replace current data with a backup", "اوسني معلومات د یوه بیک‌اپ سره بدلول")
  val autoSnapshots: String get() = t("نسخه‌های خودکار — هر نیم ساعت اگر دفتر عوض شده؛ ۷ روز و ۴ هفتهٔ اخیر", "Automatic copies — every half hour if the ledger changed; last 7 days and 4 weeks", "اتومات کاپيانې — هر نیم ساعت که دفتر بدل شوی وي؛ وروستۍ ۷ ورځې او ۴ اونۍ")
  fun snapshotDesc(kb: Any): String = t("$kb کیلوبایت — برای بازگرداندن بزنید", "$kb KB — tap to restore", "$kb KB — د بېرته راګرځولو لپاره ټک کړئ")
  val snapshotUnreadable: String get() = t("این نسخه خوانده نشد", "This copy couldn't be read", "دا کاپي ونه لوستل شوه")
  val cloudBackup: String get() = t("پشتیبان روی سرور", "Server backup", "په سرور کې بیک‌اپ")
  val sendToServerNow: String get() = t("همین حالا روی سرور بگذار", "Put it on the server now", "همدا اوس یې سرور ته واستوئ")
  val needSignIn: String get() = t("برای این کار باید وارد حساب شده باشید", "You need to be signed in for this", "د دې کار لپاره باید حساب ته ننوتلي یاست")
  val nothingSentOrOffline: String get() = t("هنوز چیزی نرفته — یا نت نیست", "Nothing sent yet — or no internet", "لا هېڅ نه دي لېږل شوي — یا انټرنېټ نشته")
  val nothingSent: String get() = t("هنوز چیزی نرفته", "Nothing sent yet", "لا هېڅ نه دي لېږل شوي")
  fun cloudStats(n: Any, used: Any, quota: Any): String = t("$n نسخه روی سرور · $used از $quota مگابایت", "$n ${pl(n, "copy", "copies")} on the server · $used of $quota MB", "$n ${pl(n, "کاپي", "کاپيانې")} په سرور کې · له $quota MB څخه $used")
  val signInFirst: String get() = t("اول وارد حساب شوید", "Sign in first", "لومړی حساب ته ننوځئ")
  val cloudSent: String get() = t("پشتیبان روی سرور نشست", "The backup is on the server", "بیک‌اپ سرور ته ورسېد")
  val cloudNotSent: String get() = td("نرفت — نت یا حساب را ببینید. نسخهٔ روی گوشی سرِ جایش است.", "Not sent — check the internet or your account. The copy on the phone is safe.", "ونه لېږل شو — انټرنېټ یا حساب وګورئ. په موبایل کې کاپي خوندي ده.", "Not sent — check the internet or your account. The copy on this computer is safe.", "ونه لېږل شو — انټرنېټ یا حساب وګورئ. په کمپیوټر کې کاپي خوندي ده.")
  fun kb(n: Any): String = t("$n کیلوبایت", "$n KB", "$n کیلوبایټ")
  val manual: String get() = t("دستی", "Manual", "لاسي")
  val auto: String get() = t("خودکار", "Automatic", "اتومات")
  val restoreFromPanel: String get() = t("برای برگرداندن، فایل را از پنل بگیرید و «بازیابی از فایل» را بزنید", "To restore, download the file from the panel and tap “Restore from file”", "د بېرته راګرځولو لپاره، دوتنه له پینل څخه واخلئ او «له دوتنې بېرته راګرځول» ټک کړئ")
  val folderBackup: String get() = t("پشتیبانِ کامل در پوشه", "Full backup to a folder", "په فولډر کې بشپړ بیک‌اپ")
  val buildingDots: String get() = t("در حالِ ساختن…", "Creating…", "جوړېږي…")
  val mirrorNow: String get() = t("پشتیبانِ کامل همین حالا (پوشه + سرور)", "Full backup now (folder + server)", "همدا اوس بشپړ بیک‌اپ (فولډر + سرور)")
  fun mirrorDesc(n: Any): String = t("دفتر و همهٔ عکس‌ها · $n نسخه در پوشه", "Ledger and all photos · $n ${pl(n, "copy", "copies")} in the folder", "دفتر او ټول انځورونه · $n ${pl(n, "کاپي", "کاپيانې")} په فولډر کې")
  val lastLabel: String get() = t("آخرین:", "Latest:", "وروستۍ:")
  val savedToFolder: String get() = t("در پوشه نشست", "Saved to the folder", "په فولډر کې خوندي شو")
  fun folder(p: Any): String = t("پوشه: $p", "Folder: $p", "فولډر: $p")
  val alsoOnServer: String get() = t("روی سرور هم رفت", "also sent to the server", "سرور ته هم ولېږل شو")
  val serverNotSent: String get() = t("سرور: نرفت (نت؟)", "server: not sent (internet?)", "سرور: ونه لېږل شو (انټرنېټ؟)")
  val chooseFolder: String get() = t("انتخابِ پوشه…", "Choose folder…", "فولډر وټاکئ…")
  val folderNotOpened: String get() = t("پوشه باز نشد", "The folder couldn't be opened", "فولډر خلاص نه شو")
  val openFolder: String get() = t("باز کردنِ پوشه", "Open folder", "فولډر پرانیزئ")
  val every12h: String get() = t("خودکار هر ۱۲ ساعت", "Automatic every 12 hours", "هر ۱۲ ساعته اتومات")
  val compactLedger: String get() = t("فشرده کردن دفتر", "Compact the ledger", "دفتر سپک کړئ")
  fun compactDesc(n: Any): String = t("$n ردیف سابقهٔ کهنه به بایگانی می‌رود — دفتر سبک‌تر و ثبت فروش تندتر می‌شود", "$n old history ${pl(n, "row moves", "rows move")} to the archive — the ledger gets lighter and sales save faster", "د زړې تاریخچې $n ${pl(n, "کرښه", "کرښې")} آرشیف ته ځي — دفتر سپک او د پلور ثبت چټک کېږي")
  val undoRestore: String get() = t("برگرداندن به پیش از بازیابی", "Undo the last restore", "له بېرته راګرځولو مخکې حالت ته ستنول")
  val undoRestoreDesc: String get() = t("اگر فایل اشتباهی بازیابی شد", "If the wrong file was restored", "که غلطه دوتنه بېرته راګرځول شوې وي")
  val undone: String get() = t("به وضعیت پیش از بازیابی برگشت", "Back to the state before the restore", "له بېرته راګرځولو مخکې حالت ته ستون شو")
  val undoFailed: String get() = t("برگرداندن ممکن نشد", "Couldn't undo", "ستنول ونه شول")
  val wipeAll: String get() = t("پاک‌سازی کامل اطلاعات", "Erase all data", "ټول معلومات پاکول")
  val aboutText: String get() = td("فروشگاه من — برنامهٔ اندروید.\nتمام اطلاعات روی همین گوشی ذخیره می‌شود؛ هر چند وقت یک‌بار پشتیبان بگیرید.", "My Shop — Android app.\nAll data is stored on this phone; take a backup every now and then.", "زما دوکان — د اندروید اپ.\nټول معلومات په همدې موبایل کې خوندي کېږي؛ وخت ناوخت بیک‌اپ واخلئ.", "My Shop — computer app.\nAll data is stored on this computer; take a backup every now and then.", "زما دوکان — د کمپیوټر اپ.\nټول معلومات په همدې کمپیوټر کې خوندي کېږي؛ وخت ناوخت بیک‌اپ واخلئ.")
  val getLatest: String get() = t("دریافت آخرین نسخه", "Get the latest version", "وروستۍ نسخه واخلئ")
  fun installedVersion(v: Any): String = t("نسخهٔ نصب‌شده: $v", "Installed version: $v", "نصب شوې نسخه: $v")
  //  «چه چیزی تازه است» (OPS-05)
  val whatsNew: String get() = t("چه چیزی تازه است", "What's new", "څه نوي دي")
  val whatsNewShow: String get() = t("نشان دادن پس از هر به‌روزرسانی", "Show after each update", "له هر تازه کېدو وروسته ښودل")
  val whatsNewNote: String get() = t(
    "روشن: پس از هر به‌روزرسانی، تغییرهای همان نسخه یک بار نشان داده می‌شوند و هر قابلیتِ برداشته‌شده با جای تازه‌اش گفته می‌شود. فقط روی همین دستگاه.",
    "On: after each update, that version's changes are shown once, and any removed feature is announced with where to find it now. Only on this device.",
    "روښانه: له هر تازه کېدو وروسته، د هماغې نسخې بدلونونه یو ځل ښودل کېږي او هره لرې شوې ځانګړنه د خپل نوي ځای سره ویل کېږي. یوازې په همدې وسیله.",
  )
  val whatsNewAll: String get() = t("دیدنِ همهٔ تغییرها", "See all changes", "ټول بدلونونه وګورئ")
  val whatsNewEmpty: String get() = t("هنوز یادداشتی نیست.", "No notes yet.", "لا یادښت نشته.")
  fun whatsNewVersion(v: String): String = t("نسخهٔ $v", "Version $v", "نسخه $v")
  val whatsNewKindNew: String get() = t("تازه", "New", "نوی")
  val whatsNewKindChanged: String get() = t("تغییر", "Changed", "بدل شوی")
  val whatsNewKindRemoved: String get() = t("برداشته شد", "Removed", "لرې شو")
  fun whatsNewInstead(text: String): String = t("به‌جایش: $text", "Instead: $text", "پر ځای یې: $text")
  val whatsNewGotIt: String get() = t("فهمیدم", "Got it", "پوه شوم")
  val restoreQ: String get() = t("بازیابی اطلاعات؟", "Restore data?", "معلومات بېرته راوګرځول شي؟")
  val restoreWarn: String get() = t("بازیابی فقط روی همین دستگاه است: هیچ چیزی از سرور یا دستگاه‌های دیگر پاک یا عوض نمی‌شود. اگر این دستگاه با سرور همگام است، اطلاعاتی که روی سرور هست — از جمله فروش و دریافت‌هایی که پس از این پشتیبان ثبت شده — دوباره از سرور می‌آید و برای این کار اینترنت لازم است. یک نسخه از وضعیت فعلی نگه داشته می‌شود تا اگر اشتباه شد، برگردانده شود.", "Restoring only affects this device: nothing is deleted or changed on the server or on other devices. If this device syncs with the server, the data on the server — including sales and payments recorded after this backup — comes back from the server, so an internet connection is needed. A copy of the current state is kept so it can be undone if this was a mistake.", "بېرته راګرځول یوازې په همدې وسیله اغېز کوي: له سرور یا نورو وسیلو هېڅ شی نه پاکېږي او نه بدلېږي. که دا وسیله له سرور سره همغږې وي، هغه معلومات چې په سرور کې دي — د هغو پلورونو او ترلاسه شویو پیسو په ګډون چې له دې بیک‌اپ وروسته ثبت شوي — بیا له سرور څخه راځي، نو انټرنېټ پکار دی. د اوسني حالت یوه کاپي ساتل کېږي څو که غلطي وه، بېرته وګرځول شي.")
  //  بازیابی روی دستگاهِ همگام (S143، `ShopStore.restoreBackup`): هرگز چیزی از سرور پاک نمی‌کند
  val restoreNeedNet: String get() = t("این دستگاه با سرور همگام است و بازیابی اطلاعات را از سرور می‌گیرد تا فروش و اطلاعاتِ تازه‌تر از پشتیبان پاک نشود. به اینترنت وصل شوید و دوباره بازیابی کنید. هیچ چیزی پاک یا عوض نشده.", "This device syncs with the server, so a restore takes the data from the server and sales or data newer than the backup aren't deleted. Connect to the internet and restore again. Nothing was deleted or changed.", "دا وسیله له سرور سره همغږې ده، نو بېرته راګرځول معلومات له سرور څخه اخلي څو هغه پلورونه او معلومات چې له بیک‌اپ نوي دي پاک نه شي. انټرنېټ ته وصل شئ او بیا یې بېرته راوګرځوئ. هېڅ شی نه دی پاک یا بدل شوی.")
  val restoreSyncFirst: String get() = t("این دستگاه هنوز با حسابِ شما همگام نشده و حساب روی سرور اطلاعات دارد؛ بازیابی آن‌ها را از سرور می‌گیرد تا پشتیبانِ کهنه با اطلاعاتِ تازه‌تر قاطی نشود. اول به اینترنت وصل شوید و همگام کنید، بعد دوباره بازیابی کنید. هیچ چیزی پاک یا عوض نشده.", "This device hasn't synced with your account yet and the account has data on the server; a restore takes that data from the server so an old backup isn't mixed with newer data. Connect to the internet and sync first, then restore again. Nothing was deleted or changed.", "دا وسیله لا ستاسو له حساب سره همغږې شوې نه ده او حساب په سرور کې معلومات لري؛ بېرته راګرځول هغه له سرور څخه اخلي څو زوړ بیک‌اپ له نویو معلوماتو سره ګډ نه شي. لومړی انټرنېټ ته وصل شئ او همغږي یې کړئ، بیا یې بېرته راوګرځوئ. هېڅ شی نه دی پاک یا بدل شوی.")
  val restoreNotOnV3: String get() = t("بازیابیِ پشتیبان با همگام‌سازیِ نسخهٔ ۳ هنوز ممکن نیست، چون اطلاعاتِ تازه‌ترِ سرور را پاک می‌کرد. هیچ چیزی پاک یا عوض نشده.", "Restoring a backup isn't possible yet with sync version 3, because it would delete newer data on the server. Nothing was deleted or changed.", "د همغږۍ په ۳ نسخه کې د بیک‌اپ بېرته راګرځول لا ممکن نه دي، ځکه چې په سرور کې به یې نوي معلومات پاک کړي وای. هېڅ شی نه دی پاک یا بدل شوی.")
  fun restoreQueueLeft(n: Int): String = t("$n تغییرِ این دستگاه هنوز به سرور نرسیده؛ اول باید برسند تا گم نشوند. به اینترنت وصل شوید و دوباره بازیابی کنید. هیچ چیزی پاک یا عوض نشده.", "$n ${pl(n, "change", "changes")} on this device ${pl(n, "hasn't", "haven't")} reached the server yet; they must get there first so they aren't lost. Connect to the internet and restore again. Nothing was deleted or changed.", "د دې وسیلې $n ${pl(n, "بدلون", "بدلونونه")} لا سرور ته نه دي رسېدلي؛ لومړی باید ورسېږي څو ورک نه شي. انټرنېټ ته وصل شئ او بیا یې بېرته راوګرځوئ. هېڅ شی نه دی پاک یا بدل شوی.")
  fun restoreFailed(r: Any): String = t("بازیابی انجام نشد ($r). هیچ چیزی پاک یا عوض نشده؛ دوباره امتحان کنید.", "The restore didn't go through ($r). Nothing was deleted or changed; try again.", "بېرته راګرځول ونه شول ($r). هېڅ شی نه دی پاک یا بدل شوی؛ بیا هڅه وکړئ.")
  val restoreBusy: String get() = t("بازیابیِ پشتیبان در جریان است؛ این تغییر ثبت نشد. چند لحظهٔ دیگر دوباره امتحان کنید.", "A backup restore is in progress, so this change wasn't saved. Try again in a moment.", "د بیک‌اپ بېرته راګرځول روان دي، نو دا بدلون ثبت نه شو. یو څو شېبې وروسته بیا هڅه وکړئ.")
  val restoreRaced: String get() = t("در همین لحظه تغییرِ تازه‌ای روی این دستگاه ثبت شد", "a new change was saved on this device at the same moment", "په همدې شېبه کې په دې وسیله یو نوی بدلون ثبت شو")
  fun restoredFromServer(rows: Int, notOnServer: Int): String = t(
    "این دستگاه با سرور همگام است: تنظیماتِ پشتیبان نشست و $rows ردیف اطلاعات از سرور آمد؛ از سرور و دستگاه‌های دیگر چیزی پاک نشد." +
      if (notOnServer > 0) " $notOnServer ردیفِ پشتیبان روی سرور نیست (پس از پشتیبان حذف شده یا هرگز همگام نشده) و افزوده نشد؛ فایلِ پشتیبان دست‌نخورده است." else "",
    "This device syncs with the server: the backup's settings were restored and $rows ${pl(rows, "row", "rows")} of data came from the server; nothing was deleted on the server or other devices." +
      if (notOnServer > 0) " $notOnServer backup ${pl(notOnServer, "row isn't", "rows aren't")} on the server (deleted after the backup or never synced) and ${pl(notOnServer, "wasn't", "weren't")} added; the backup file is untouched." else "",
    "دا وسیله له سرور سره همغږې ده: د بیک‌اپ تنظیمات بېرته راغلل او د معلوماتو $rows ${pl(rows, "کرښه", "کرښې")} له سرور څخه راغلې؛ په سرور او نورو وسیلو کې هېڅ شی پاک نه شو." +
      if (notOnServer > 0) " د بیک‌اپ $notOnServer ${pl(notOnServer, "کرښه", "کرښې")} په سرور کې نشته (له بیک‌اپ وروسته پاکې شوې یا هېڅکله نه دي همغږې شوې) او ور زیاتې نه شوې؛ د بیک‌اپ دوتنه نه ده بدله شوې." else "",
  )
  val warehouseEntries: String get() = t("ورودهای انبار", "Warehouse entries", "د ګدام داخلې")
  fun restorePhotos(n: Any): String = t("$n عکس هم در این فایل هست", "This file also has $n ${pl(n, "photo", "photos")}", "په دې دوتنه کې $n ${pl(n, "انځور", "انځورونه")} هم شته")
  val restoreNoPhotos: String get() = t("این فایل عکسی ندارد — عکس‌های فعلی دست‌نخورده می‌مانند", "This file has no photos — current photos stay untouched", "دا دوتنه انځور نه لري — اوسني انځورونه نه بدلېږي")
  val restoreDiskFail: String get() = t("بازیابی روی حافظهٔ دستگاه نوشته نشد — اطلاعاتِ قبلی سرِ جایش است", "The restore couldn't be written to device storage — your previous data is still in place", "بېرته راګرځول د وسیلې په حافظه کې ونه لیکل شول — پخواني معلومات خپل ځای دي")
  fun restoredWithPhotos(n: Any): String = t("اطلاعات بازیابی شد — با $n عکس", "Data restored — with $n ${pl(n, "photo", "photos")}", "معلومات بېرته راوګرځول شول — له $n ${pl(n, "انځور", "انځورونو")} سره")
  val restored: String get() = t("اطلاعات با موفقیت بازیابی شد", "Data restored successfully", "معلومات په بریالیتوب بېرته راوګرځول شول")
  val restore: String get() = t("بازیابی", "Restore", "بېرته راګرځول")
  val compactQ: String get() = t("دفتر فشرده شود؟", "Compact the ledger?", "دفتر سپک شي؟")
  fun compactWarn(n: Any): String = t("$n ردیف از حرکات انبار، سابقهٔ عملیات و تغییرات قیمت که بیش از یک سال از آن‌ها گذشته، به یک فایل بایگانی کنار دفتر منتقل می‌شوند. پاک نمی‌شوند و با پشتیبان کامل هم بیرون می‌روند — فقط از دفتری که هر فروش آن را بازنویسی می‌کند بیرون می‌آیند. فاکتورها، کالاها، قرض‌داران و موجودی دست‌نخورده می‌مانند.", "$n ${pl(n, "row", "rows")} of warehouse movements, activity log and price changes older than a year move to an archive file next to the ledger. They aren't deleted and are included in full backups — they just leave the ledger that every sale rewrites. Invoices, items, debtors and stock stay untouched.", "د ګدام د حرکتونو، د کړنو د تاریخچې او د بیو د بدلونونو $n ${pl(n, "کرښه", "کرښې")} چې له یو کال زیاتې زړې دي، د دفتر څنګ ته یوې آرشیف دوتنې ته ځي. نه پاکېږي او له بشپړ بیک‌اپ سره هم وځي — یوازې له هغه دفتر څخه وځي چې هر پلور یې بیا لیکي. بلونه، توکي، پوروړي او موجودي نه بدلېږي.")
  fun archived(n: Any): String = t("$n ردیف بایگانی شد", "$n ${pl(n, "row", "rows")} archived", "$n ${pl(n, "کرښه", "کرښې")} آرشیف شوې")
  fun compactFailed(r: Any): String = t("فشرده کردن انجام نشد: $r", "Compacting failed: $r", "سپکول ونه شول: $r")
  val compactDo: String get() = t("فشرده کن", "Compact", "سپک یې کړه")
  val wipeQ: String get() = t("پاک‌سازی کامل اطلاعات؟", "Erase all data?", "ټول معلومات پاک شي؟")
  //  ⛔ «پاک کردنِ همه» برای هر ردیف DELETE به سرور می‌فرستد (`ShopStore.save` ⇐ `record`): همهٔ دستگاه‌های دکان، نه فقط این یکی
  val wipeWarn: String get() = t("همهٔ اطلاعاتِ دکان از همهٔ دستگاه‌ها و سرور پاک می‌شود و برنمی‌گردد. اگر پشتیبان نگرفته‌اید، اول پشتیبان بگیرید.", "All shop data will be erased from every device and the server, and can't be recovered. If you haven't made a backup, make one first.", "د دوکان ټول معلومات له ټولو وسیلو او سرور څخه پاکېږي او بېرته نه راځي. که مو بیک‌اپ نه دی اخیستی، لومړی بیک‌اپ واخلئ.")
  val wiped: String get() = t("تمام اطلاعات پاک شد", "All data erased", "ټول معلومات پاک شول")
  val wipe: String get() = t("پاک کن", "Erase", "پاک یې کړه")
  val wipeNext: String get() = t("ادامه", "Continue", "دوام ورکړئ")
  val wipeNameTitle: String get() = t("نامِ دکان را بنویسید", "Type the shop name", "د دوکان نوم ولیکئ")
  val wipeNameLabel: String get() = t("نامِ دکان", "Shop name", "د دوکان نوم")
  fun wipeNameHint(name: Any): String = t("برای پاک کردنِ همهٔ اطلاعات از همهٔ دستگاه‌ها و سرور، بنویسید: «$name»", "To erase all data from every device and the server, type: “$name”", "له ټولو وسیلو او سرور څخه د ټولو معلوماتو د پاکولو لپاره ولیکئ: «$name»")
  val wipeOwnerOnly: String get() = t("فقط صاحبِ دکان می‌تواند همهٔ اطلاعات را پاک کند", "Only the shop owner can erase all data", "یوازې د دوکان خاوند کولای شي ټول معلومات پاک کړي")

  // اشتراک (برچسبِ وضعیت)
  val active: String get() = t("فعال", "Active", "فعال")
  val graceEnded: String get() = t("تمام شده — در مهلت", "Ended — in grace period", "پای ته رسېدلی — په مهلت کې")
  val ended: String get() = t("تمام شده", "Ended", "پای ته رسېدلی")
  val notStarted: String get() = t("هنوز شروع نشده", "Not started yet", "لا نه دی پیل شوی")
  val licenseInvalid: String get() = t("مجوز معتبر نیست", "The license isn't valid", "جواز د اعتبار وړ نه دی")
  val noSubscription: String get() = t("بدون اشتراک", "No subscription", "ګډون نشته")

  // بخش‌های جمع‌شو
  val open: String get() = t("باز کردن", "Open", "پرانیستل")

  // پایانِ روزِ کاری (LocaleSection › ShopDayRow، LOC-03)
  val dayEndNote: String get() = t(
    "پایانِ روزِ کاری. فروش و هر ثبتی که پس از نیمه‌شب و پیش از این ساعت باشد به نامِ دیروز نوشته می‌شود، تا یک شبِ کاری دو روز نشود. 00:00 یعنی نیمه‌شب، مثلِ همیشه. فقط روی همین دستگاه.",
    "End of the business day. Sales and other entries made after midnight but before this time are dated to the previous day, so one working night isn't split into two days. 00:00 means midnight, as always. This device only.",
    "د کاري ورځې پای. هغه پلور او نور ثبتونه چې له نیمې شپې وروسته او له دې وخت مخکې وي د پرون په نوم لیکل کېږي، څو یوه کاري شپه په دوو ورځو ونه وېشل شي. 00:00 یعني نیمه شپه، لکه تل. یوازې په همدې وسیله.",
  )
  fun dayEndNow(d: Any): String = t("فروشِ همین حالا به تاریخِ $d ثبت می‌شود", "A sale made right now is dated $d", "همدا اوس پلور د $d په نېټه ثبتېږي")

  // تاریخ و رقم (LocaleSection)
  val persianDigits: String get() = t("رقمِ فارسی", "Persian digits", "فارسي عددونه")
  val latinDigits: String get() = t("رقمِ لاتین", "Latin digits", "لاتین عددونه")
  val pashtoMonths: String get() = t("ماهِ پشتو", "Pashto months", "پښتو میاشتې")
  val dariMonths: String get() = t("ماهِ دری", "Dari months", "دري میاشتې")
  val digitsNote: String get() = t("رقم روی صفحه و فاکتورِ چاپی. چیزی که ذخیره و همگام می‌شود همیشه رقمِ لاتین است؛ با عوض کردنِ این کلید هیچ داده‌ای عوض نمی‌شود.", "Digits on screen and on printed receipts. What's saved and synced always uses Latin digits; switching this changes no data.", "په پرده او چاپي بل کې عددونه. هغه څه چې خوندي او همغږي کېږي تل لاتین عددونه دي؛ د دې کلي بدلول هېڅ معلومات نه بدلوي.")
  val digitsEnNote: String get() = t("در انگلیسی رقم همیشه لاتین است؛ این انتخاب برای فارسی و پشتو است.", "English always uses Latin digits; this choice applies to Persian and Pashto.", "په انګلیسي کې عددونه تل لاتین وي؛ دا ټاکنه د فارسي او پښتو لپاره ده.")
  fun monthsToday(d: Any): String = t("نامِ ماه‌های خورشیدی: امروز $d", "Solar month names: today is $d", "د لمریزو میاشتو نومونه: نن $d دی")
  val monthsDari: String get() = t("دری (حمل، ثور…)", "Dari (Hamal, Sawr…)", "دري (حمل، ثور…)")
  val monthsPashto: String get() = t("پشتو (وری، غویی…)", "Pashto (Wray, Ghwayay…)", "پښتو (وری، غویی…)")
  val symbolNote: String get() = t("نمادِ پول در فاکتور. مبلغ همیشه بی اعشار است.", "Currency sign on receipts. Amounts never have decimals.", "په بل کې د پیسو نښه. مبلغ تل بې اعشاریې وي.")
  val calendar: String get() = t("تقویم", "Calendar", "جنتري")
  val calAuto: String get() = t("خودکار", "Automatic", "اتومات")
  val calSolar: String get() = t("خورشیدی", "Solar", "لمریز")
  val calGregorian: String get() = t("میلادی", "Gregorian", "میلادي")
  val calNote: String get() = t("خودکار یعنی فارسی و پشتو خورشیدی، انگلیسی میلادی. فقط نمایش است؛ تاریخِ ذخیره‌شده عوض نمی‌شود.", "Automatic means solar for Persian and Pashto, Gregorian for English. Display only; saved dates don't change.", "اتومات یعني فارسي او پښتو لمریز، انګلیسي میلادي. یوازې ښودنه ده؛ خوندي شوې نېټې نه بدلېږي.")
}
