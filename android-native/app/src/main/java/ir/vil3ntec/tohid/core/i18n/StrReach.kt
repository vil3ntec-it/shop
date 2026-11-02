package ir.vil3ntec.tohid.core.i18n

/**
 *  درهای نسخهٔ ۳ به کارهایی که پس از پاک شدنِ رابطِ قدیم بی‌راه مانده بودند (`V3ReachabilityTest`): برچسبِ ردیفِ «…»،
 *  تراشهٔ کار، زبانه و عنوانِ صفحهٔ میزبان. متنِ درونِ خودِ آن کارها همان جدول‌های خودشان است.
 *
 *  قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrReach {
  //  ── انبار و کالا ──
  val places: String get() = t("محل‌ها و بسته‌بندی", "Places and repacking", "ځایونه او بیا بسته‌بندي")
  val stockBook: String get() = t("دفترِ موجودی", "Stock book", "د موجودي کتاب")
  val bulkProducts: String get() = t("ثبتِ چند کالا با هم", "Add several products", "څو توکي یوځای ثبتول")
  val imageImport: String get() = t("ورودِ گروهیِ عکس‌ها", "Import photos in bulk", "د عکسونو ډله‌ییز راوړل")
  val importHistory: String get() = t("سابقهٔ ورود از فایل", "File import history", "له فایل څخه د راوړلو تاریخچه")
  val importPick: String get() = t("انتخابِ فایل", "Choose a file", "فایل وټاکئ")
  val importPickBody: String get() = t(
    "فایلِ xlsx یا CSV ِ کالاها را انتخاب کنید؛ پیش از ثبت همه‌چیز را می‌بینید.",
    "Choose an xlsx or CSV file of products; you will see everything before saving.",
    "د توکو xlsx یا CSV فایل وټاکئ؛ له ثبت مخکې هر څه وینئ.",
  )
  val fileUnreadable: String get() = t("فایل خوانده نشد", "The file could not be read", "فایل ونه لوستل شو")
  val rejectSaved: String get() = t(
    "فایلِ ردیف‌های رد ذخیره شد — درستش کنید و دوباره وارد کنید",
    "Rejected rows saved — fix them and import again",
    "رد شوې کرښې خوندي شوې — سمې یې کړئ او بیا یې راوړئ",
  )
  val fileNotSaved: String get() = t("فایل ذخیره نشد", "The file was not saved", "فایل خوندي نه شو")
  val imported: String get() = t("ورود از فایل ثبت شد", "Import saved", "له فایل څخه راوړل ثبت شول")
  val photoBank: String get() = t("انتخاب از بانکِ تصاویر", "Choose from the image bank", "د انځورونو له بانک څخه ټاکل")
  val photoGuided: String get() = t("عکس با راهنما", "Guided photo", "لارښود عکس")

  //  ── مدیریت ──
  val staffTime: String get() = t("ساعتِ کار و پورسانت", "Hours and commission", "د کار ساعتونه او کمیشن")
  val staffTimeLoading: String get() = t("در حالِ گرفتن از سرور…", "Loading from the server…", "له سرور څخه راوړل کېږي…")
  val staffTimeOff: String get() = t(
    "برای این دکان روشن نیست یا اینترنت نیست.",
    "Not enabled for this shop, or there is no internet.",
    "د دې دوکان لپاره روښانه نه دی یا انټرنېټ نشته.",
  )

  //  ── گزارش‌ها (زبانه‌های اختیاری، همان کلیدهای `ReportCsv`) ──
  val tabInsight: String get() = t("تحلیلِ موجودی", "Stock analysis", "د موجودي شننه")
  val tabReorder: String get() = t("نیاز به سفارش", "Needs reordering", "بیا امر ته اړتیا")
  val tabAging: String get() = t("سنِ بدهی", "Debt aging", "د پور عمر")
  val tabRfm: String get() = t("تحلیلِ خرید (RFM)", "Purchase analysis (RFM)", "د پېرود شننه (RFM)")
  val tabDuplicates: String get() = t("مشتریانِ تکراری", "Duplicate customers", "تکراري پېرودونکي")
  val tabReminders: String get() = t("یادآوریِ امروز", "Today's reminders", "د نن یادونې")
  val freshOn: String get() = t("گزارشِ تازه", "New report", "نوی راپور")
  val compare: String get() = t("مقایسه با دورهٔ پیش", "Compare with previous period", "له تېرې دورې سره پرتله")
  val prevPeriod: String get() = t("دورهٔ پیش", "Previous period", "تېره دوره")
  val nextPeriod: String get() = t("دورهٔ بعد", "Next period", "راتلونکې دوره")

  //  ── خرید ──
  val supplierReturn: String get() = t("برگشت به تأمین‌کننده", "Return to supplier", "عرضه‌کوونکي ته بېرته ورکول")
  val returnSaved: String get() = t("برگشت و سندِ کسر ثبت شد", "Return and credit note saved", "بېرته ورکول او د کسر سند ثبت شول")
  val printCreditNote: String get() = t("چاپِ سندِ کسر", "Print credit note", "د کسر سند چاپول")

  //  ── سفارش و تحویل ──
  val pick: String get() = t("جمع‌آوری", "Pick", "راټولول")
  val ratings: String get() = t("امتیازِ پیک‌ها", "Courier ratings", "د پیکونو نمرې")
  val cashReport: String get() = t("گزارشِ نقدِ پیک‌ها", "Courier cash report", "د پیکونو د نغدو راپور")
  val campaigns: String get() = t("کارزار و QR", "Campaigns and QR", "کمپاینونه او QR")
  val media: String get() = t("عکس و ویدیو", "Photos and video", "عکسونه او ویډیو")

  //  ── دستگاه‌ها و تنظیمات ──
  val printerSettings: String get() = t("تنظیمِ چاپگر", "Printer settings", "د چاپګر تنظیم")
  val findPrinter: String get() = t("پیدا کردنِ خودکارِ چاپگر", "Find printers automatically", "چاپګر په خپله موندل")
  val printerSaved: String get() = t("تنظیمِ چاپگر ذخیره شد", "Printer settings saved", "د چاپګر تنظیم خوندي شو")
  fun printerAdded(name: Any): String = t("«${asIs(name)}» به چاپگرها افزوده شد", "“$name” was added to printers", "«$name» چاپګرونو ته ورزیات شو")
  val hwTools: String get() = t("صف چاپ، برچسب، ترازو و نمایشگر", "Print queue, labels, scale and display", "د چاپ کتار، لیبل، تله او ښودونکی")
  val servers: String get() = t("سرورها", "Servers", "سرورونه")
  val serversLine: String get() = t("وصل به سرورِ دیگر: شبکهٔ دکان، خانه یا اینترنت", "Connect to another server: shop network, home or internet", "بل سرور ته نښلول: د دوکان شبکه، کور یا انټرنېټ")
  val serversExample: String get() = t(
    "مثلاً سرورِ کوچکِ خودِ دکان: دادهٔ هر سرور روی همین دستگاه جداست و هرگز به سرورِ دیگر نمی‌رود.",
    "For example the shop's own small server: each server's data stays separate on this device and never goes to another.",
    "د بېلګې په توګه د دوکان خپل کوچنی سرور: د هر سرور معلومات په همدې وسیله جلا دي او هېڅکله بل سرور ته نه ځي.",
  )
  val serversOwnerOnly: String get() = t("فقط صاحبِ دکان سرورها را تنظیم می‌کند.", "Only the shop owner sets up servers.", "یوازې د دوکان خاوند سرورونه تنظیموي.")
}
