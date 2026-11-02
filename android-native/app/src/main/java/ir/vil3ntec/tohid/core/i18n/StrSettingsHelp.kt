package ir.vil3ntec.tohid.core.i18n

/**
 *  تنظیماتِ کم (۱۴۰۵/۰۸/۱۱): یک خطِ ساده زیرِ هر ردیفِ دیده‌شده (`…Line`) و یک مثالِ «این چه می‌کند؟»
 *  بالای صفحهٔ هر بخش (`…Example`) — به زبانِ دکان‌دار: چه می‌کند، نه چطور. نقشه از کلید: `SettingsHelp`.
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrSettingsHelp {
  val advanced: String get() = t("پیشرفته", "Advanced", "پرمختللي")
  val advancedLine: String get() = t(
    "گزینه‌هایی برای وقتی که لازم شد؛ برنامه بی آن‌ها هم کامل کار می‌کند",
    "Options for when you need them; the app works fully without them",
    "د اړتیا پر وخت انتخابونه؛ اپ بې له دوی هم بشپړ کار کوي",
  )
  val whatTitle: String get() = t("این چه می‌کند؟", "What does this do?", "دا څه کوي؟")

  //  ── فهرستِ اصلی (C) ──────────────────────────────────────────────
  val appearanceLine: String get() = t("رنگِ برنامه: روشن، تیره یا خودکار", "App colours: light, dark or automatic", "د اپ رنګ: روښانه، تیاره یا خپلکاره")
  val appearanceExample: String get() = t(
    "مثلاً شب‌ها «تیره» چشم را کمتر خسته می‌کند.",
    "For example, \"Dark\" is easier on the eyes at night.",
    "د بېلګې په توګه، د شپې «تیاره» سترګې لږ ستړې کوي.",
  )
  val languageLine: String get() = t("زبانِ نوشته‌های برنامه", "The language the app is shown in", "هغه ژبه چې اپ پرې ښکاري")
  val languageExample: String get() = t(
    "مثلاً پشتو را بزنید؛ همهٔ دکمه‌ها و نوشته‌ها پشتو می‌شوند.",
    "For example, pick Pashto and every button and label switches to Pashto.",
    "د بېلګې په توګه، پښتو وټاکئ؛ ټولې تڼۍ او لیکنې پښتو کېږي.",
  )
  val digitsLine: String get() = t("تقویم، شکلِ رقم‌ها و نشانِ پول", "Calendar, digit style and currency sign", "کلیز، د شمېرو بڼه او د پیسو نښه")
  val digitsExample: String get() = t(
    "مثلاً قیمت «۱۲۵ افغانی» نوشته شود یا «125 ؋».",
    "For example, show a price as \"125 afghani\" or \"125 AFN\".",
    "د بېلګې په توګه، بیه «۱۲۵ افغانۍ» ولیکل شي یا «125 ؋».",
  )
  val appLockLine: String get() = t("با یک رمزِ کوتاه کسی بی‌اجازه برنامه را باز نمی‌کند", "A short PIN so no one opens the app without permission", "په لنډ پین څوک بې اجازې اپ نه شي پرانیستلی")
  val appLockExample: String get() = t(
    "مثلاً اگر دستگاه را روی میز جا گذاشتید، بی رمز کسی حساب‌ها را نمی‌بیند.",
    "For example, if you leave the device on the counter, no one sees your accounts without the PIN.",
    "د بېلګې په توګه، که وسیله مو په مېز پرېښوده، بې پین څوک ستاسو حسابونه نه ویني.",
  )
  val staffCodeLine: String get() = t("کدی که شاگرد با آن واردِ برنامهٔ شما می‌شود", "The code your staff use to sign in", "هغه کوډ چې شاګرد پرې ستاسو اپ ته ننوځي")
  val staffCodeExample: String get() = t(
    "مثلاً کد را به شاگردِ تازه بدهید؛ بی آن‌که رمزِ شما را بداند وارد می‌شود.",
    "For example, give the code to a new staff member; they sign in without knowing your password.",
    "د بېلګې په توګه، کوډ نوي شاګرد ته ورکړئ؛ ستاسو له پټنوم پرته ننوځي.",
  )
  val passwordLine: String get() = t("رمزِ ورود به حسابِ خودتان را عوض کنید", "Change your account password", "د خپل حساب پټنوم بدل کړئ")
  val passwordExample: String get() = t(
    "مثلاً اگر کسی رمزتان را دیده، همین‌جا رمزِ تازه بگذارید.",
    "For example, if someone saw your password, set a new one here.",
    "د بېلګې په توګه، که چا ستاسو پټنوم لیدلی، همدلته نوی کېږدئ.",
  )
  val payLine: String get() = t("روش‌های پرداخت و گرد کردنِ نقد", "Payment methods and cash rounding", "د تادیې لارې او د نغدو ګردول")
  val payExample: String get() = t(
    "مثلاً مشتری نیمی نقد و نیمی با حواله بدهد؛ یا ۱۲۳ افغانی به ۱۲۵ گرد شود.",
    "For example, a customer pays half cash and half by transfer; or 123 afghani is rounded to 125.",
    "د بېلګې په توګه، پېرودونکی نیم نغد او نیم په حواله ورکوي؛ یا ۱۲۳ افغانۍ ۱۲۵ ته ګردېږي.",
  )
  //  «حساب‌پیِ دکان» (B — تصمیمِ پولی؛ فقط صاحب)
  val hesabShopTitle: String get() = t("حساب‌پیِ دکان", "Shop HesabPay", "د دوکان حساب‌پې")
  val hesabShopLine: String get() = t(
    "حساب‌پیِ خودِ دکان؛ پول مستقیم به حسابِ خودتان",
    "Your shop's own HesabPay; money goes straight to you",
    "د دوکان خپل حساب‌پې؛ پیسې مستقیم ستاسو حساب ته",
  )
  val hesabShopExample: String get() = t(
    "مثلاً شمارهٔ حساب‌پیِ دکان را یک بار با رمزِ جدا بزنید؛ صندوق QR و مبلغ را نشان می‌دهد و پول مستقیم به همان حساب می‌رود.",
    "For example, enter your shop's HesabPay number once with a separate PIN; the till shows the QR and amount and the money goes straight to that account.",
    "د بېلګې په توګه، د دوکان د حساب‌پې شمېره یو ځل په جلا پاسورډ ولیکئ؛ صندوق QR او مبلغ ښيي او پیسې مستقیم همغه حساب ته ځي.",
  )
  val printerLine: String get() = t("چاپگرِ رسید را وصل و آزمایش کنید", "Connect and test the receipt printer", "د رسید چاپګر وصل او و یې ازمویئ")
  val cameraLine: String get() = t("خواندنِ بارکدِ کالا با دوربینِ دستگاه", "Scan item barcodes with the device camera", "د وسیلې په کمره د توکو بارکوډ لوستل")
  val cameraExample: String get() = t(
    "مثلاً بارکدِ یک بسته را نشانه بگیرید؛ کالا خودش به سبد می‌آید.",
    "For example, point at a pack's barcode and the item lands in the cart.",
    "د بېلګې په توګه، د یوې بستې بارکوډ ته کمره ونیسئ؛ توکی پخپله ټوکرۍ ته راځي.",
  )
  val syncLine: String get() = t("حساب‌ها روی همهٔ دستگاه‌ها و سرور یکی می‌ماند", "Keeps your books the same on every device and the server", "ستاسو حسابونه په ټولو وسیلو او سرور کې یو شان ساتي")
  val syncExample: String get() = t(
    "مثلاً فروشی که روی یک دستگاه ثبت شد، چند لحظه بعد روی دستگاهِ دیگر هم دیده می‌شود.",
    "For example, a sale made on one device shows up on your other device a moment later.",
    "د بېلګې په توګه، په یوه وسیله ثبت شوی پلور څو شېبې وروسته په بله وسیله هم ښکاري.",
  )
  val backupLine: String get() = t("نسخهٔ پشتیبان بگیرید یا حساب‌ها را برگردانید", "Back up your books or restore them", "د حسابونو بیک‌اپ واخلئ یا یې بېرته راوګرځوئ")
  val backupExample: String get() = t(
    "مثلاً پیش از عوض کردنِ دستگاه پشتیبان بگیرید و روی دستگاهِ تازه برگردانید.",
    "For example, before changing devices, take a backup and restore it on the new one.",
    "د بېلګې په توګه، د وسیلې له بدلولو مخکې بیک‌اپ واخلئ او په نوې وسیله یې راوګرځوئ.",
  )
  val aboutLine: String get() = t("نسخهٔ برنامه و به‌روزرسانی", "App version and updates", "د اپ نسخه او تازه کول")
  val aboutExample: String get() = t(
    "مثلاً اگر نسخهٔ تازه آمده باشد، از همین‌جا نصبش کنید.",
    "For example, if a new version is out, install it from here.",
    "د بېلګې په توګه، که نوې نسخه راغلې وي، له همدې ځایه یې نصب کړئ.",
  )

  //  ── پیشرفته (B) ─────────────────────────────────────────────────
  val alertsLine: String get() = t("هشدار وقتی قرضِ یک مشتری از اندازه‌ای بیشتر شود", "Warn when a customer's debt passes an amount", "خبرداری کله چې د یوه پېرودونکي پور له یوې اندازې واوړي")
  val alertsExample: String get() = t(
    "مثلاً اندازه را ۵۰۰۰ افغانی بگذارید؛ مشتری‌ای که بیشتر بدهد نشان داده می‌شود.",
    "For example, set 5,000 afghani; any customer owing more is flagged.",
    "د بېلګې په توګه، ۵۰۰۰ افغانۍ کېږدئ؛ هر پېرودونکی چې ډېر پور لري، ښودل کېږي.",
  )
  val navBarLine: String get() = t("کدام صفحه‌ها در نوارِ پایین باشند", "Choose which pages sit in the bottom bar", "وټاکئ چې کومې پاڼې په لاندې پټه کې وي")
  val navBarExample: String get() = t(
    "مثلاً صفحه‌ای را که هر روز باز می‌کنید به نوار بیاورید تا با یک لمس باز شود.",
    "For example, put the page you open every day in the bar, one tap away.",
    "د بېلګې په توګه، هغه پاڼه چې هره ورځ یې پرانیزئ پټې ته راوړئ، چې په یوه ټک پرانیستل شي.",
  )
  val trainingLine: String get() = t("دفترِ جدا برای یاد گرفتن؛ با حساب‌های واقعی قاطی نمی‌شود", "A separate practice book; never mixes with your real books", "د زده کړې جلا دفتر؛ له اصلي حسابونو سره نه ګډېږي")
  val trainingExample: String get() = t(
    "مثلاً شاگردِ تازه چند فروشِ آزمایشی بزند، بی آن‌که حساب‌ها عوض شوند.",
    "For example, a new staff member practises a few sales without touching the real accounts.",
    "د بېلګې په توګه، نوی شاګرد څو ازمایښتي پلورونه کوي، پرته له دې چې اصلي حسابونه بدل شي.",
  )
  val encryptionLine: String get() = t("حساب‌های ذخیره‌شده روی این دستگاه رمزگذاری شوند", "Encrypt the books stored on this device", "په دې وسیله ساتل شوي حسابونه کوډ شي")
  val encryptionExample: String get() = t(
    "مثلاً اگر دستگاه دزدیده شد، فایلِ حساب‌ها بی کلید خوانده نمی‌شود.",
    "For example, if the device is stolen, the book file can't be read without the key.",
    "د بېلګې په توګه، که وسیله غلا شي، د حسابونو فایل بې کیلي نه لوستل کېږي.",
  )
  val sessionsLine: String get() = t("دستگاه‌هایی که با حسابِ شما واردند؛ بیرون کردنِ هر کدام", "Devices signed in to your account; sign any of them out", "هغه وسیلې چې ستاسو په حساب ننوتې دي؛ هره یوه وباسئ")
  val sessionsExample: String get() = t(
    "مثلاً دستگاهِ گم‌شده را از همین‌جا بیرون کنید.",
    "For example, sign out a lost device from here.",
    "د بېلګې په توګه، ورکه شوې وسیله له همدې ځایه وباسئ.",
  )
  val anomalyLine: String get() = t("کارهای غیرعادی را نشان می‌دهد (آزمایشی)", "Points out unusual activity (experimental)", "غیرعادي کارونه ښيي (ازمایښتي)")
  val anomalyExample: String get() = t(
    "مثلاً اگر یک روز لغوِ فاکتور چند برابرِ همیشه شد، نشانش می‌دهد.",
    "For example, if one day has many times the usual cancellations, it points it out.",
    "د بېلګې په توګه، که یوه ورځ د فاکتور لغوه څو چنده زیاته شوه، هغه ښيي.",
  )
  val expiryLine: String get() = t("فروشِ کالای تاریخ‌گذشته: فقط هشدار، یا جلوگیری", "Selling expired goods: just warn, or block", "د تاریخ تېرو توکو پلور: یوازې خبرداری، یا مخنیوی")
  val expiryExample: String get() = t(
    "مثلاً شیرِ تاریخ‌گذشته را اسکن کنید؛ برنامه هشدار می‌دهد یا اصلاً نمی‌فروشد.",
    "For example, scan expired milk: the app warns, or refuses the sale.",
    "د بېلګې په توګه، تاریخ تېرې شیدې سکن کړئ؛ اپ خبرداری ورکوي یا یې هېڅ نه پلوري.",
  )
  val checkoutRulesLine: String get() = t("شرط‌ها پیش از بستنِ فاکتور، مثلاً نامِ مشتری", "Checks before an invoice closes, such as a customer name", "د فاکتور له تړلو مخکې شرطونه، لکه د پېرودونکي نوم")
  val checkoutRulesExample: String get() = t(
    "مثلاً فاکتورِ بالای ۱۰۰۰۰ افغانی بی نامِ مشتری بسته نشود.",
    "For example, an invoice over 10,000 afghani can't close without a customer name.",
    "د بېلګې په توګه، له ۱۰۰۰۰ افغانیو پورته فاکتور بې د پېرودونکي نوم نه تړل کېږي.",
  )
  val heldLine: String get() = t("چند فاکتورِ باز با هم؛ یکی را نگه دارید و دیگری را بفروشید", "Several open invoices: hold one, serve the next", "څو پرانیستي فاکتورونه یوځای؛ یو وساتئ او بل وپلورئ")
  val heldExample: String get() = t(
    "مثلاً مشتری رفت پول بیاورد؛ فاکتورش را نگه دارید و به نفرِ بعدی بفروشید.",
    "For example, a customer goes to fetch money: hold their invoice and serve the next person.",
    "د بېلګې په توګه، پېرودونکی پیسې راوړو ته ولاړ؛ فاکتور یې وساتئ او بل ته وپلورئ.",
  )
  val returnsLine: String get() = t("قاعدهٔ برگشت و تعویض: تا چند روز و با چه دلیلی", "Return and exchange rules: how many days and which reasons", "د بېرته راوړلو او بدلولو قاعده: څو ورځې او کوم دلیلونه")
  val returnsExample: String get() = t(
    "مثلاً فقط تا ۷ روز پس از خرید، با دلیلِ «خراب» یا «اندازه».",
    "For example, only within 7 days of purchase, for \"faulty\" or \"wrong size\".",
    "د بېلګې په توګه، یوازې د پېر تر ۷ ورځو پورې، د «خراب» یا «اندازه» دلیل سره.",
  )
  val payFixLine: String get() = t("روشِ پرداختِ فاکتورِ ثبت‌شده را با دلیل اصلاح کنید", "Correct a saved invoice's payment method, with a reason", "د ثبت شوي فاکتور د تادیې لار په دلیل سمه کړئ")
  val payFixExample: String get() = t(
    "مثلاً فاکتوری که اشتباهی «نقد» ثبت شد «کارت» شود؛ خودِ فاکتور دست نمی‌خورد.",
    "For example, an invoice wrongly saved as cash becomes card; the invoice itself stays the same.",
    "د بېلګې په توګه، هغه فاکتور چې په غلطۍ «نغد» ثبت شو، «کارت» شي؛ پخپله فاکتور نه بدلېږي.",
  )
  val tillReceiveLine: String get() = t("گرفتنِ قسط یا قرضِ مشتری در خودِ صندوق", "Take a customer's instalment or debt payment right at the till", "د پېرودونکي قسط یا پور په صندوق کې اخیستل")
  val tillReceiveExample: String get() = t(
    "مثلاً مشتری ۲۰۰۰ از قرضش را می‌دهد؛ در صندوق دریافت بزنید و رسید بدهید.",
    "For example, a customer pays 2,000 off their debt: take it at the till and give a receipt.",
    "د بېلګې په توګه، پېرودونکی له خپل پور ۲۰۰۰ ورکوي؛ په صندوق کې یې واخلئ او رسید ورکړئ.",
  )
  val touchLine: String get() = t("دکمه‌های درشت برای صفحهٔ لمسیِ صندوق", "Bigger buttons for a touch-screen till", "د لمسي سکرین صندوق لپاره لویې تڼۍ")
  val touchExample: String get() = t(
    "مثلاً اگر نمایشگرِ صندوق لمسی است، تعداد و قیمت را با انگشت بزنید.",
    "For example, on a touch monitor, enter quantity and price with your finger.",
    "د بېلګې په توګه، په لمسي سکرین کې شمېر او بیه په ګوته ولیکئ.",
  )
  val moreKeysLine: String get() = t("کلیدهای میان‌بُرِ بیشتر در صندوق", "Extra keyboard shortcuts at the till", "په صندوق کې نورې لنډې کیلي")
  val moreKeysExample: String get() = t(
    "مثلاً Shift+F5 یک‌راست به تخفیفِ کلِ فاکتور می‌رود.",
    "For example, Shift+F5 jumps straight to the whole-invoice discount.",
    "د بېلګې په توګه، Shift+F5 نېغ د ټول فاکتور تخفیف ته ځي.",
  )
  val tillSwitchLine: String get() = t("هر نفر با PINِ خودش صندوق را باز کند", "Each person opens the till with their own PIN", "هر څوک صندوق په خپل پین پرانیزي")
  val tillSwitchExample: String get() = t(
    "مثلاً Ctrl+L بزنید و نفرِ بعدی با PINِ خودش بفروشد؛ فروش به نامِ خودش ثبت می‌شود.",
    "For example, press Ctrl+L and the next person sells with their own PIN, under their own name.",
    "د بېلګې په توګه، Ctrl+L ووهئ او بل کس په خپل پین پلوري؛ پلور د هغه په نوم ثبتېږي.",
  )
  val giftCardLine: String get() = t("فروشِ کارتِ هدیه و خرج کردنِ اعتبارِ مشتری", "Sell gift cards and spend store credit", "د ډالۍ کارت پلور او د پېرودونکي اعتبار لګول")
  val giftCardExample: String get() = t(
    "مثلاً کارتِ ۱۰۰۰ افغانی بفروشید؛ صاحبش بعداً با آن خرید می‌کند.",
    "For example, sell a 1,000 afghani card; its holder shops with it later.",
    "د بېلګې په توګه، د ۱۰۰۰ افغانیو کارت وپلورئ؛ خاوند یې وروسته پرې پېر کوي.",
  )
  val layawayLine: String get() = t("کالا کنار گذاشته می‌شود و مشتری قسطی می‌پردازد", "Put goods aside while the customer pays in instalments", "توکي یوې خوا ته ایښودل کېږي او پېرودونکی قسط ورکوي")
  val layawayExample: String get() = t(
    "مثلاً یخچال را با ۲۰۰۰ پیش‌پرداخت نگه دارید و پس از آخرین قسط تحویل بدهید.",
    "For example, hold a fridge on a 2,000 deposit and hand it over after the last instalment.",
    "د بېلګې په توګه، یخچال په ۲۰۰۰ مخکې تادیه وساتئ او له وروستي قسط وروسته یې وسپارئ.",
  )
  val safeLine: String get() = t("حسابِ گاوصندوق: شمارش، ورود و خروج، تحویل به بانک", "Safe book: counts, money in and out, bank deposits", "د سیف حساب: شمېرنه، راتګ او وتنه، بانک ته سپارل")
  val safeExample: String get() = t(
    "مثلاً هر شب پولِ صندوق را به گاوصندوق ببرید و فردا به بانک بدهید؛ هر گام ثبت می‌ماند.",
    "For example, move the till cash to the safe each night and to the bank next day; every step is recorded.",
    "د بېلګې په توګه، هره شپه د صندوق پیسې سیف ته یوسئ او سبا بانک ته؛ هر ګام ثبتېږي.",
  )
  val creditGateLine: String get() = t("نسیهٔ بیش از سقفِ مشتری تأییدِ مدیر بخواهد", "Credit sales over the customer's limit need a manager's approval", "د پېرودونکي له حد پورته نسیه د مدیر تایید غواړي")
  val creditGateExample: String get() = t(
    "مثلاً سقفِ مشتری ۳۰۰۰ است؛ نسیهٔ ۵۰۰۰ بی تأیید ثبت نمی‌شود.",
    "For example, a customer's limit is 3,000, so a 5,000 credit sale needs approval.",
    "د بېلګې په توګه، د پېرودونکي حد ۳۰۰۰ دی؛ د ۵۰۰۰ نسیه بې تایید نه ثبتېږي.",
  )
  val loyaltyLine: String get() = t("امتیاز برای هر خرید، و خرج کردنش به‌جای پول", "Points for each purchase, spendable instead of cash", "د هر پېر لپاره نمرې، چې د پیسو پر ځای لګېږي")
  val loyaltyExample: String get() = t(
    "مثلاً هر ۱۰۰ افغانی خرید یک امتیاز؛ ۵۰ امتیاز یعنی ۵۰ افغانی تخفیف.",
    "For example, one point per 100 afghani spent; 50 points take 50 afghani off.",
    "د بېلګې په توګه، په هرو ۱۰۰ افغانیو پېر یوه نمره؛ ۵۰ نمرې یعنې ۵۰ افغانۍ تخفیف.",
  )
  val crmLine: String get() = t("گزارش‌های مشتری: قرض‌های کهنه و مشتریانِ پرخرید", "Customer reports: old debts and best customers", "د پېرودونکو راپورونه: زاړه پورونه او ښه پېرودونکي")
  val crmExample: String get() = t(
    "مثلاً ببینید کدام قرض‌ها بیش از ۹۰ روز مانده‌اند.",
    "For example, see which debts are over 90 days old.",
    "د بېلګې په توګه، وګورئ کوم پورونه له ۹۰ ورځو زیات پاتې دي.",
  )
  val taxLine: String get() = t("جمعِ مالیاتِ هر سه‌ماه با نرخی که خودتان می‌دهید", "Tax totals for each quarter at the rate you set", "د هرو درېیو میاشتو د مالیې ټولګه، په هغه نرخ چې تاسو یې ټاکئ")
  val taxExample: String get() = t(
    "مثلاً با نرخِ ۴٪ برنامه مالیاتِ فصل را حساب می‌کند و پیش از سررسید یادآوری می‌کند.",
    "For example, at 4% the app works out the quarter's tax and reminds you before it's due.",
    "د بېلګې په توګه، په ۴٪ نرخ اپ د فصل مالیه حسابوي او له نېټې مخکې یادونه کوي.",
  )
  val ownerReportLine: String get() = t("خلاصهٔ خودکارِ کارِ روز برای صاحب", "An automatic daily summary for the owner", "د خاوند لپاره د ورځې خپلکاره لنډیز")
  val ownerReportExample: String get() = t(
    "مثلاً هر شب فروش و پولِ صندوقِ همان روز برایتان فرستاده شود.",
    "For example, get the day's sales and till cash sent to you every night.",
    "د بېلګې په توګه، هره شپه د همغې ورځې پلور او د صندوق پیسې درته واستول شي.",
  )
  val linePricingLine: String get() = t("چه کسی قیمت را دستی عوض کند و تا چه اندازه تخفیف بدهد", "Who may change prices by hand, and how much discount", "څوک بیه په لاس بدلولی شي او څومره تخفیف")
  val linePricingExample: String get() = t(
    "مثلاً شاگرد بی تأییدِ شما بیش از ۵٪ تخفیف ندهد.",
    "For example, staff can't give more than 5% off without your approval.",
    "د بېلګې په توګه، شاګرد ستاسو له تایید پرته له ۵٪ ډېر تخفیف نه شي ورکولی.",
  )
  val tillGoodsLine: String get() = t("ثبتِ سریعِ کالای تازه و فروش به کارتن و بسته", "Quick-add new items and sell by carton or pack", "د نوي توکي چټک ثبت او په کارتن او بسته پلور")
  val tillGoodsExample: String get() = t(
    "مثلاً بارکدِ ناشناس را اسکن کنید و همان‌جا نام و قیمت بدهید؛ یا یک کارتن = ۲۴ دانه بفروشید.",
    "For example, scan an unknown barcode and add its name and price on the spot; or sell a carton of 24.",
    "د بېلګې په توګه، ناپېژندل شوی بارکوډ سکن کړئ او همدلته نوم او بیه ورکړئ؛ یا یو کارتن = ۲۴ دانې وپلورئ.",
  )
  val priceListLine: String get() = t("قیمتِ عمده برای مشتریِ عمده", "Wholesale prices for wholesale customers", "د عمده پېرودونکي لپاره عمده بیه")
  val priceListExample: String get() = t(
    "مثلاً مغازه‌دارِ دیگری که عمده می‌خرد، خودکار قیمتِ عمده بگیرد.",
    "For example, another shopkeeper buying in bulk gets the wholesale price automatically.",
    "د بېلګې په توګه، بل دوکاندار چې عمده اخلي، پخپله عمده بیه ترلاسه کوي.",
  )
  val feesLine: String get() = t("مبلغِ ثابتی که خودکار به فاکتور اضافه شود", "A fixed charge added to invoices automatically", "ټاکلې پیسې چې پخپله فاکتور ته ورزیاتېږي")
  val feesExample: String get() = t(
    "مثلاً برای هر فاکتور ۵ افغانی پولِ کیسه.",
    "For example, 5 afghani for a bag on every invoice.",
    "د بېلګې په توګه، په هر فاکتور د کڅوړې ۵ افغانۍ.",
  )
  val quoteLine: String get() = t("پیش‌فاکتور با قیمتِ امروز، برای فروشِ بعدی", "Quotes at today's price, to sell later", "د نن ورځې په بیه مخکینی فاکتور، د وروسته پلور لپاره")
  val quoteExample: String get() = t(
    "مثلاً مشتری قیمتِ ۱۰ کیسه سیمان را می‌خواهد؛ پیش‌فاکتوری بدهید که تا یک هفته معتبر است.",
    "For example, a customer wants a price for 10 bags of cement; give a quote valid for a week.",
    "د بېلګې په توګه، پېرودونکی د ۱۰ بوجۍ سمټو بیه غواړي؛ یو مخکینی فاکتور ورکړئ چې یوه اوونۍ باوري وي.",
  )
  val specialOrderLine: String get() = t("سفارشِ کالای ناموجود با پیش‌پرداخت", "Order out-of-stock items with a deposit", "د نشتو توکو فرمایش له مخکې تادیې سره")
  val specialOrderExample: String get() = t(
    "مثلاً مشتری الان ۵۰۰ می‌دهد؛ وقتی کالا رسید، از قیمت کم می‌شود.",
    "For example, a customer pays 500 now; it comes off the price when the item arrives.",
    "د بېلګې په توګه، پېرودونکی اوس ۵۰۰ ورکوي؛ کله چې توکی ورسېږي، له بیې کمېږي.",
  )
  val deliverLaterLine: String get() = t("فروشی که کالایش بعداً به مشتری می‌رسد", "Sales delivered to the customer later", "هغه پلور چې توکي یې وروسته پېرودونکي ته رسېږي")
  val deliverLaterExample: String get() = t(
    "مثلاً سفارشِ امروز فردا فرستاده می‌شود و پول هنگامِ تحویل گرفته می‌شود.",
    "For example, today's order goes out tomorrow and is paid on delivery.",
    "د بېلګې په توګه، د نن فرمایش سبا لېږل کېږي او پیسې د سپارلو پر وخت اخیستل کېږي.",
  )
  val stockModeLine: String get() = t("روشِ تازهٔ حسابِ موجودی و بهای کالا (آزمایشی)", "A new way of counting stock and item cost (experimental)", "د زېرمې او د توکي د لګښت د حساب نوې لار (ازمایښتي)")
  val stockModeExample: String get() = t(
    "مثلاً بهای کالا میانگینِ همهٔ خریدها با کرایهٔ بار باشد و کالای زودتر منقضی اول فروخته شود.",
    "For example, item cost becomes the average of all purchases with freight, and the soonest-expiring stock sells first.",
    "د بېلګې په توګه، د توکي لګښت د ټولو پېرونو منځنی د بار له کرایې سره وي، او هغه توکي چې ژر تېرېږي لومړی وپلورل شي.",
  )
  val forecastLine: String get() = t("پیش‌بینیِ فروشِ هفته‌های آینده (آزمایشی)", "Forecast of the coming weeks' sales (experimental)", "د راتلونکو اوونیو د پلور وړاندوینه (ازمایښتي)")
  val forecastExample: String get() = t(
    "مثلاً برنامه حدس می‌زند هفتهٔ بعد چند کیسه آرد لازم دارید.",
    "For example, the app estimates how many bags of flour you'll need next week.",
    "د بېلګې په توګه، اپ اټکل کوي چې راتلونکې اوونۍ څو بوجۍ اوړه درته پکار دي.",
  )
  val placesLine: String get() = t("موجودیِ هر محل، و شکستنِ کارتن به دانه", "Stock per location, and breaking cartons into units", "د هر ځای زېرمه، او د کارتن ماتول په دانو")
  val placesExample: String get() = t(
    "مثلاً ۲ کارتن در اتاقِ پشتی و ۱۰ دانه روی قفسه؛ یک کارتن را به ۲۴ دانه بشکنید.",
    "For example, 2 cartons in the back room and 10 units on the shelf; break a carton into 24 units.",
    "د بېلګې په توګه، ۲ کارتنه په شاتنۍ کوټه کې او ۱۰ دانې په المارۍ کې؛ یو کارتن په ۲۴ دانو مات کړئ.",
  )
  val productToolsLine: String get() = t("چند بارکد، برند و تغییرِ گروهیِ قیمت", "Several barcodes, brands and bulk price changes", "څو بارکوډونه، برانډ او ډله‌ییز د بیې بدلون")
  val productToolsExample: String get() = t(
    "مثلاً قیمتِ همهٔ نوشابه‌ها را یک‌جا ۱۰٪ بالا ببرید و پیش از ثبت ببینید.",
    "For example, raise all soft drinks by 10% at once and preview before saving.",
    "د بېلګې په توګه، د ټولو یخو څښاکونو بیه یوځای ۱۰٪ لوړه کړئ او له ثبت مخکې یې وګورئ.",
  )
  val productInfoLine: String get() = t("کدِ کالا، نامِ پشتو و انگلیسی و جای قفسه در فرمِ کالا", "Item code, Pashto and English names and shelf spot in the item form", "د توکي کوډ، پښتو او انګلیسي نوم او د المارۍ ځای د توکي په فورمه کې")
  val productInfoExample: String get() = t(
    "مثلاً بنویسید «راهرو ۲، قفسه ۳» تا شاگرد زود پیدایش کند.",
    "For example, write \"aisle 2, shelf 3\" so staff find it fast.",
    "د بېلګې په توګه، ولیکئ «لار ۲، المارۍ ۳» چې شاګرد یې ژر ومومي.",
  )
  val labelsLine: String get() = t("چاپِ برچسبِ قیمت و ساختِ بارکد برای کالای بی‌بارکد", "Print price labels and make barcodes for items without one", "د بیې لیبل چاپول او بې بارکوډه توکو ته بارکوډ جوړول")
  val labelsExample: String get() = t(
    "مثلاً برای برنجِ فله بارکد بسازید و ۲۰ برچسب چاپ کنید.",
    "For example, make a barcode for loose rice and print 20 labels.",
    "د بېلګې په توګه، د خلاصو وریجو لپاره بارکوډ جوړ کړئ او ۲۰ لیبلونه چاپ کړئ.",
  )
  val etaLine: String get() = t("زمانِ تقریبیِ تحویل که مشتری در ویترین می‌بیند", "The delivery time customers see in your online storefront", "د سپارلو اټکلي وخت چې پېرودونکي یې په آنلاین ویترین کې ویني")
  val etaExample: String get() = t(
    "مثلاً «۲ تا ۴ ساعت» برای سفارش‌های داخلِ شهر.",
    "For example, \"2 to 4 hours\" for orders in town.",
    "د بېلګې په توګه، د ښار دننه فرمایشونو لپاره «۲ تر ۴ ساعته».",
  )
  val promoLine: String get() = t("تخفیفِ اولین خرید، بی سوءاستفاده", "First-purchase discount, guarded against abuse", "د لومړي پېر تخفیف، له ناوړه ګټې خوندي")
  val promoExample: String get() = t(
    "مثلاً ۱۰٪ برای اولین سفارش، و هر شمارهٔ تلفن فقط یک بار.",
    "For example, 10% off the first order, once per phone number.",
    "د بېلګې په توګه، په لومړي فرمایش ۱۰٪، او هره د تلیفون شمېره یوازې یو ځل.",
  )
  val easySetupLine: String get() = t("چاپگر و بارکدخوان را قدم‌به‌قدم وصل کنید (آزمایشی)", "Connect a printer and scanner step by step (experimental)", "چاپګر او بارکوډ لوستونکی ګام په ګام وصل کړئ (ازمایښتي)")
  val easySetupExample: String get() = t(
    "مثلاً برنامه چاپگرِ شبکه را خودش پیدا می‌کند و یک رسیدِ آزمایشی چاپ می‌کند.",
    "For example, the app finds the network printer itself and prints a test receipt.",
    "د بېلګې په توګه، اپ د شبکې چاپګر پخپله مومي او یو ازمایښتي رسید چاپوي.",
  )
  val webcamLine: String get() = t("وب‌کمِ کامپیوتر بارکدِ کالا را می‌خواند", "The computer's webcam reads item barcodes", "د کمپیوټر ویب‌کم د توکو بارکوډ لولي")
  val webcamExample: String get() = t(
    "مثلاً دکانی که بارکدخوان ندارد وب‌کمِ لپ‌تاپ را روشن می‌کند و بستهٔ چای را جلوی آن می‌گیرد؛ بوق که زد، چای در سبد است.",
    "For example, a shop without a scanner turns on the laptop webcam and holds a pack of tea in front of it; after the beep, the tea is in the cart.",
    "د بېلګې په توګه، یو دوکان چې بارکوډ لوستونکی نه لري د لپ‌ټاپ ویب‌کم روښانه کوي او د چای بسته یې مخې ته نیسي؛ چې ټک یې وکړ، چای په ټوکرۍ کې دی.",
  )
  val scanCashLine: String get() = t(
    "برنامهٔ بسته هم با کارتِ «نقد پرداخت شد» فروشِ نقدی ثبت کند",
    "Record cash sales from the scanner card even with the app closed",
    "بند اپ هم د «نغدې ورکړل شوې» کارت په مرسته نغدي پلور ثبت کړي",
  )
  val scanCashExample: String get() = t(
    "مثلاً برنامه بسته است؛ دو شیر اسکن می‌شود و بعد «نقد پرداخت شد» از کارت. فروشِ نقدی با یادداشتِ «ثبت از اسکنر» ثبت و رسید چاپ می‌شود. صندوقِ بسته یا کاربرِ بی اجازه ⇒ فقط فاکتورِ نگه‌داشته.",
    "For example, the app is closed; two milks are scanned, then \"Cash paid\" from the card. A cash sale noted \"Recorded from scanner\" is saved and the receipt prints. Till closed or user without permission ⇒ only a held invoice.",
    "د بېلګې په توګه، اپ بند دی؛ دوه شیدې سکن کېږي او بیا له کارت «نغدې ورکړل شوې». نغدي پلور د «له سکنر ثبت» یادښت سره ثبتېږي او رسید چاپېږي. بند صندوق یا بې اجازې کاروونکی ⇒ یوازې ساتل شوی بل.",
  )
  val peripheralsLine: String get() = t(
    "چاپگر، بارکدخوان، ترازو و نمایشگر را پیدا، وصل و آزمایش کنید",
    "Find, connect and test printers, scanners, scales and displays",
    "چاپګر، بارکوډ لوستونکی، تله او ښودونکی ومومئ، وصل او و یې ازمویئ",
  )
  val peripheralsExample: String get() = t(
    "مثلاً «جست‌وجو» را بزنید؛ چاپگر و بارکدخوانِ نزدیک با نوعشان می‌آیند. «افزودن» را بزنید تا وصل بمانند؛ اسکنِ وقتِ بسته بودنِ برنامه هم نگه داشته می‌شود.",
    "For example, tap \"Search\": nearby printers and scanners appear with their type. Tap \"Add\" to keep them connected; scans made while the app is closed are kept too.",
    "د بېلګې په توګه، «لټون» ووهئ؛ نږدې چاپګرونه او بارکوډ لوستونکي له ډول سره راځي. «زیاتول» ووهئ چې وصل پاتې شي؛ هغه سکنونه هم ساتل کېږي چې اپ بند وي.",
  )
  val lateApologyLine: String get() = t("پوزش و کوپنِ خودکار وقتی سفارش دیر برسد", "Automatic apology and coupon when an order is late", "کله چې فرمایش ناوخته شي، خپلکاره بښنه او کوپون")
  val lateApologyExample: String get() = t(
    "مثلاً سفارشی که یک ساعت دیر شد، خودکار پیامِ پوزش و کوپنِ تخفیف می‌گیرد.",
    "For example, an order that is an hour late automatically gets an apology and a discount coupon.",
    "د بېلګې په توګه، هغه فرمایش چې یو ساعت ناوخته شي، پخپله د بښنې پیغام او د تخفیف کوپون ترلاسه کوي.",
  )
}
