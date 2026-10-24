package ir.vil3ntec.tohid.core.i18n

/**
 *  واژه‌های کیت و پوستهٔ نسخهٔ ۳ (VILL3N): دکمه‌ها و پیام‌های مشترکِ `ui/v3`،
 *  سربرگ، برگه‌ها، مسیرِ بی‌دسترسی، حالت‌های برنامه و صفحهٔ هشدارها.
 *  صاحب: پایه (foundation)؛ گروه‌ها فقط می‌خوانند. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrKit {

  // دکمه‌ها و واژه‌های کیت
  val back: String get() = t("بازگشت", "Back", "شاته")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val close: String get() = t("بستن", "Close", "بندول")
  val add: String get() = t("افزودن", "Add", "زیاتول")
  val addNew: String get() = t("تازه", "New", "نوی")
  val save: String get() = t("ذخیره", "Save", "خوندي کول")
  val more: String get() = t("بیشتر", "More", "نور")
  val search: String get() = t("جست‌وجو", "Search", "لټون")
  val searchHint: String get() = t("جست‌وجو…", "Search…", "لټون…")
  val scanBarcode: String get() = td("اسکنِ بارکد", "Scan barcode", "بارکوډ سکن کړئ", "Scan barcode", "بارکوډ سکن کړئ")
  val voice: String get() = t("جست‌وجوی صوتی", "Voice search", "غږیز لټون")
  val clear: String get() = t("پاک کردن", "Clear", "پاکول")
  val selected: String get() = t("انتخاب‌شده", "Selected", "ټاکل شوی")
  val nothingFound: String get() = t("چیزی پیدا نشد", "Nothing found", "هېڅ ونه موندل شو")
  val yesterday: String get() = t("دیروز", "Yesterday", "پرون")
  val working: String get() = t("در حالِ انجام…", "Working…", "روان دی…")
  val done: String get() = t("انجام شد", "Done", "وشو")
  val undo: String get() = t("برگرداندن", "Undo", "بېرته")
  val retry: String get() = t("دوباره", "Retry", "بیا هڅه")
  val required: String get() = t("لازم است", "Required", "اړین دی")
  val notANumber: String get() = t("عدد درست بنویسید", "Enter a valid number", "سم شمېر ولیکئ")
  fun selectedCount(n: Any): String = t("$n انتخاب‌شده", "$n selected", "$n ټاکل شوي")

  // فرم: دور انداختنِ تغییرها
  val discardTitle: String get() = t("تغییرها دور ریخته شوند؟", "Discard changes?", "بدلونونه لرې شي؟")
  val discardBody: String get() = t("آنچه نوشته‌اید ذخیره نشده است.", "What you entered hasn't been saved.", "څه چې مو لیکلي خوندي شوي ندي.")
  val discard: String get() = t("دور انداختن", "Discard", "لرې کول")
  val keepEditing: String get() = t("ادامهٔ ویرایش", "Keep editing", "سمون ته دوام")

  // مسیر
  val noAccessTitle: String get() = t("به این بخش دسترسی ندارید", "You don't have access to this section", "دې برخې ته لاسرسی نلرئ")
  val noAccessBody: String get() = t("صاحبِ دکان می‌تواند این دسترسی را بدهد.", "The shop owner can grant this access.", "د دوکان خاوند کولای شي دا لاسرسی درکړي.")
  val notOnTitle: String get() = t("این بخش برای دکانِ شما روشن نیست", "This section isn't turned on for your shop", "دا برخه ستاسو د دوکان لپاره فعاله نه ده")
  val unknownRouteTitle: String get() = t("این صفحه پیدا نشد", "This page wasn't found", "دا پاڼه ونه موندل شوه")
  val comingSoonBody: String get() = t("این بخش در حالِ ساخته شدن است.", "This section is being built.", "دا برخه جوړېږي.")

  // حالت‌های برنامه
  val modeTitle: String get() = t("از برنامه چه می‌خواهید؟", "How will you use the app?", "له اپ څخه څه غواړئ؟")
  val modeSubtitle: String get() = t("بعداً از «حساب من» عوضش می‌کنید.", "You can change this later in My account.", "وروسته یې له «زما حساب» بدلولای شئ.")
  val modeShop: String get() = t("دکان دارم", "I run a shop", "دوکان لرم")
  val modeShopBody: String get() = t("فروش، انبار، قرض و گزارشِ دکان", "Sales, stock, debts and reports", "پلور، ګدام، پور او راپورونه")
  val modeCourier: String get() = t("پیکم", "I'm a courier", "رسوونکی یم")
  val modeCourierBody: String get() = t("سفارش‌ها را از دکان به مشتری می‌رسانم", "I deliver orders from shops to customers", "فرمایشونه له دوکان څخه پېرودونکي ته رسوم")
  val modeShopper: String get() = t("خرید می‌کنم", "I'm shopping", "پېرود کوم")
  val modeShopperBody: String get() = t("از دکان‌های نزدیک سفارش می‌دهم", "I order from nearby shops", "له نږدې دوکانونو فرمایش ورکوم")
  val continueLabel: String get() = t("ادامه", "Continue", "دوام")
  val switchMode: String get() = t("حالتِ برنامه", "App mode", "د اپ حالت")
  val currentMode: String get() = t("حالتِ فعلی", "Current mode", "اوسنی حالت")

  // هشدارها (صفحهٔ موقت تا ساختِ مرکزِ اعلان‌ها در گروهِ پوسته)
  val notifications: String get() = t("هشدارها", "Notifications", "خبرتیاوې")
  val notificationsEmpty: String get() = t("هشداری نیست", "No notifications", "هېڅ خبرتیا نشته")
  val notificationsEmptyBody: String get() = t("هر چه نیاز به کارِ شما داشته باشد این‌جا می‌آید.", "Anything that needs your attention shows up here.", "هر څه چې ستاسو پاملرنې ته اړتیا ولري دلته راځي.")

  // خرید (جای‌نگهدارِ حالتِ خرید تا گروهِ خرید بسازد)
  val shopperHome: String get() = t("بازارچه", "Market", "بازار")
  val shopperCart: String get() = t("سبد", "Cart", "ټوکرۍ")
  val shopperCheckout: String get() = t("پرداخت", "Checkout", "تادیه")
  val shopperOrders: String get() = t("سفارش‌های من", "My orders", "زما فرمایشونه")
  val shopperOrder: String get() = t("سفارش", "Order", "فرمایش")
  val shopperShop: String get() = t("دکان", "Shop", "دوکان")
  val shopperAccount: String get() = t("حساب", "Account", "حساب")
  val openShopMode: String get() = t("دکان دارم — ورود یا ساختِ دکان", "I run a shop — sign in or create one", "دوکان لرم — ننوتل یا جوړول")

  // ماژولِ نمونه (پشتِ پرچمِ خاموش؛ `docs/MODULES-fa.md`)
  val exampleTitle: String get() = t("ماژولِ نمونه", "Example module", "بېلګه ماډیول")
  val exampleBody: String get() = t("این صفحه نشان می‌دهد ماژولِ تازه چطور با یک فایل و یک خط به برنامه وصل می‌شود.", "This page shows how a new module plugs in with one file and one line.", "دا پاڼه ښيي چې نوی ماډیول څنګه په یوه فایل او یوه کرښه وصلېږي.")

  // اسکنِ QR (`scan?kind=qr`، همهٔ حالت‌ها)
  val scanQr: String get() = t("اسکنِ QR", "Scan QR code", "QR سکن کړئ")
  val scanQrHint: String get() = t("کدِ QR را درونِ کادر بگیرید", "Hold the QR code inside the frame", "QR کوډ چوکاټ ته دننه ونیسئ")
  val cameraNeeded: String get() = t("برای اسکن، به دوربین اجازه بدهید", "Allow the camera to scan", "د سکن لپاره کامرې ته اجازه ورکړئ")
  val cameraAllow: String get() = t("اجازهٔ دوربین", "Allow camera", "کامرې ته اجازه")
  val cameraDenied: String get() = t("اجازهٔ دوربین داده نشد — از تنظیماتِ گوشی اجازه دهید", "Camera permission was denied — allow it in the phone's settings", "د کامرې اجازه ورنکړل شوه — د تلیفون له تنظیماتو یې ورکړئ")
  val noCameraTitle: String get() = t("این‌جا دوربین نیست", "No camera here", "دلته کامره نشته")
  val noCameraBody: String get() = t("اسکنِ QR فقط در گوشی کار می‌کند.", "QR scanning works on the phone only.", "د QR سکن یوازې په تلیفون کې کار کوي.")
}
