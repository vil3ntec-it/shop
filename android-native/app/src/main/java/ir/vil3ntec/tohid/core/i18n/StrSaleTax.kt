package ir.vil3ntec.tohid.core.i18n

/**
 *  مالیاتِ فاکتور (POS-37): ردیفِ برگهٔ پرداخت، رسید و فاکتور، و صفحهٔ تنظیمِ دکان.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/sale/SaleTax` (commonMain) و `data/SaleTaxWiring`. ترجمه‌های نامطمئن در
 *  `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrSaleTax {
  /** ردیفِ جدای فاکتور، رسید و برگهٔ پرداخت */
  val line: String get() = t("مالیات", "Tax", "مالیه")
  val title: String get() = t("مالیاتِ فاکتور", "Invoice tax", "د بل مالیه")
  val note: String get() = t(
    "مالیات روی «جمع − تخفیف» افزوده می‌شود و یک ردیفِ جدای «مالیات» روی فاکتور و رسید است؛ قیمت و تخفیف دست نمی‌خورند. نرخ را حسابدارِ شما می‌دهد؛ برنامه هیچ نرخی از پیش نمی‌گذارد و ادعای انطباقِ قانونی ندارد. فقط وقتی سرور هم برای این دکان روشن کرده باشد کار می‌کند؛ برای همهٔ دستگاه‌های دکان.",
    "Tax is added on top of “total − discount” and shows as a separate “Tax” line on the invoice and receipt; prices and discounts are not changed. Your accountant gives the rate; the app sets no rate and makes no claim of legal compliance. Works only when the server has also enabled it for this shop; applies to every device of the shop.",
    "مالیه په «ټول − تخفیف» ورزیاتېږي او په بل او رسید کې د «مالیه» جلا کرښه ده؛ بیې او تخفیف نه بدلېږي. نرخ ستاسو محاسب ورکوي؛ برنامه هېڅ نرخ له مخکې نه ږدي او د قانوني سموالي ادعا نه کوي. یوازې هغه وخت کار کوي چې سرور هم دې دوکان ته چالان کړی وي؛ د دوکان ټولو وسیلو ته.",
  )
  val modeOff: String get() = StrShell.switchOff
  val modeInvoice: String get() = t("روی کلِ فاکتور", "On the whole invoice", "په ټول بل")
  val modeLine: String get() = t("هر ردیف جدا", "Per line", "هره کرښه جلا")
  val rate: String get() = t("نرخِ پایه (درصد)", "Base rate (percent)", "اساسي نرخ (سلنه)")
  val rateBad: String get() = t("نرخ ۰ تا ۱۰۰ با حداکثر دو رقمِ اعشار", "Rate 0 to 100 with at most two decimals", "نرخ له ۰ تر ۱۰۰ تر دوو اعشاري عددونو پورې")
  val rates: String get() = t("نرخِ کالاها (هر خط: کالا، کالا = درصد؛ ۰ = معاف)", "Item rates (one per line: item, item = percent; 0 = exempt)", "د توکو نرخونه (هره کرښه: توکی، توکی = سلنه؛ ۰ = معاف)")
  /** ایرادِ نخستین خطِ کادرِ نرخ‌ها (`SaleTax.Problem`) */
  val badRateLine: String get() = t("نرخِ یک خط درست نیست (۰ تا ۱۰۰)", "A line has a bad rate (0 to 100)", "د یوې کرښې نرخ سم نه دی (۰ تر ۱۰۰)")
  val badItem: String get() = t("کالایی با این نام یا بارکد نیست", "No item with that name or barcode", "په دې نوم یا بارکوډ توکی نشته")
  val tooMany: String get() = t("نرخِ کالا بیش از حد است", "Too many item rates", "د توکو نرخونه ډېر دي")
  val badLine: String get() = t("هر خط: کالا = درصد", "Each line: item = percent", "هره کرښه: توکی = سلنه")
  val save: String get() = t("ذخیرهٔ تنظیم", "Save settings", "تنظیم خوندي کړئ")
  val saved: String get() = t("ذخیره شد", "Saved", "خوندي شو")
  val serverOff: String get() = t(
    "سرور هنوز مالیات را برای این دکان روشن نکرده؛ تنظیم ذخیره می‌شود ولی صندوق تا آن روز بی مالیات می‌فروشد.",
    "The server has not enabled tax for this shop yet; the setting is saved but the till sells without tax until then.",
    "سرور تر اوسه دې دوکان ته مالیه نه ده چالان کړې؛ تنظیم خوندي کېږي خو صندوق تر هغې بې مالیې پلوري.",
  )
  val outOfRange: String get() = t("مبلغِ فاکتور با مالیات بیش از حد بزرگ است", "The invoice amount with tax is too large", "د بل مبلغ له مالیې سره ډېر لوی دی")
}
