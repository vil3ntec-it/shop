package ir.vil3ntec.tohid.core.i18n

/**
 *  کادرهای مشترکِ فرم (`Fields`، `EntryFields`، `CategoryPick`): تاریخ، دسته‌بندی و انتخاب از فهرست —
 *  در مصارف، خرید، انبار، قرض‌داران و کالا یکی‌اند.
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrFields {

  // تاریخ
  val date: String get() = t("تاریخ", "Date", "نېټه")
  val yesterday: String get() = t("دیروز", "Yesterday", "پرون")
  val solarDate: String get() = t("تاریخ خورشیدی", "Solar (Hijri Shamsi) date", "لمریزه نېټه")
  val dateUnreadable: String get() = t(
    "تاریخ خوانده نشد — مثل ۱۴۰۵/۰۶/۰۶ بنویسید",
    "Couldn't read the date — write it like 1405/06/06",
    "نېټه ونه لوستل شوه — داسې یې ولیکئ: 1405/06/06",
  )

  // پروندهٔ مشتری ← گروه، لیستِ قیمتِ پیش‌فرض و رضایتِ تبلیغ (CRM-08، CRM-12)
  val crmPricingTitle: String get() = t("گروه، لیستِ قیمت و رضایتِ تبلیغ", "Group, price list and marketing consent", "ډله، د بیو لېست او د اعلان رضایت")
  val crmGroup: String get() = t("گروهِ مشتری", "Customer group", "د پیرودونکي ډله")
  val crmGroupHint: String get() = t("مثلاً همکار یا عمده‌فروش؛ خالی یعنی بی گروه", "For example colleague or wholesaler; empty means no group", "د بېلګې په توګه همکار یا عمده پلورونکی؛ خالي یعني بې ډلې")
  val crmNoGroup: String get() = t("بی گروه", "No group", "بې ډلې")
  val crmPriceList: String get() = t("لیستِ قیمتِ پیش‌فرض", "Default price list", "د بیو اصلي لېست")
  val crmRetail: String get() = t("خرده", "Retail", "پرچون")
  val crmWholesale: String get() = t("عمده", "Wholesale", "عمده")
  val crmPriceListNote: String get() = t(
    "در پروندهٔ مشتری ثبت می‌شود و هر تغییرش در دفترچهٔ ثبت می‌ماند. صندوق هنوز قیمتِ عمده را خودکار نمی‌گذارد.",
    "Saved on the customer's file; every change stays in the activity log. The register doesn't apply the wholesale price automatically yet.",
    "د پیرودونکي په دوسیه کې ثبتېږي او هر بدلون یې د فعالیت په لاګ کې پاتې کېږي. صندوق لا عمده بیه پخپله نه ږدي.",
  )
  val crmConsent: String get() = t("رضایت برای پیامِ تبلیغاتی", "Consent to marketing messages", "د اعلاني پیغامونو لپاره رضایت")
  val crmConsentUnasked: String get() = t("نپرسیده", "Not asked", "نه دی پوښتل شوی")
  val crmConsentYes: String get() = t("بله", "Yes", "هو")
  val crmConsentNo: String get() = t("نه", "No", "نه")
  val crmConsentNote: String get() = t(
    "«نپرسیده» یعنی نه: فقط «بله»ی صریح گیرندهٔ پیامِ تبلیغاتی است. یادآوریِ بدهی تبلیغ نیست. هر تغییر با تاریخ و منبع در دفترچهٔ ثبت می‌ماند.",
    "“Not asked” means no: only an explicit “yes” receives marketing messages. A debt reminder is not marketing. Every change stays in the activity log with its date and source.",
    "«نه دی پوښتل شوی» یعني نه: یوازې ښکاره «هو» اعلاني پیغام ترلاسه کوي. د پور یادونه اعلان نه دی. هر بدلون د نېټې او سرچینې سره د فعالیت په لاګ کې پاتې کېږي.",
  )
  val crmConsentSource: String get() = t("منبعِ رضایت (مثلاً حضوری یا تلفنی)", "Source of consent (for example in person or by phone)", "د رضایت سرچینه (د بېلګې په توګه حضوري یا تلیفوني)")
  val crmConsentSourceNeeded: String get() = t("برای «بله» منبعِ رضایت را بنویسید", "Write where the “yes” came from", "د «هو» سرچینه ولیکئ")
  fun crmLastConsent(date: String): String = t("آخرین سندِ رضایت: $date", "Last consent record: $date", "د رضایت وروستی سند: $date")
  fun crmSummary(group: String, list: String, consent: String): String =
    t("گروه: $group · قیمت: $list · تبلیغ: $consent", "Group: $group · Price: $list · Marketing: $consent", "ډله: $group · بیه: $list · اعلان: $consent")
  val crmSaved: String get() = t("پروندهٔ مشتری ذخیره شد", "Customer file saved", "د پیرودونکي دوسیه خوندي شوه")
  val crmSave: String get() = t("ذخیره", "Save", "خوندي کول")
  val crmNotSaved: String get() = t("ذخیره نشد — دوباره امتحان کنید", "Not saved — try again", "خوندي نه شو — بیا هڅه وکړئ")

  // دسته‌بندی و انتخاب از فهرست
  val category: String get() = t("دسته‌بندی", "Category", "ډله")
  val newCategory: String get() = t("دسته‌بندی تازه", "New category", "نوې ډله")
  val addNew: String get() = t("+ تازه", "+ New", "+ نوی")
  val add: String get() = t("افزودن", "Add", "اضافه کول")
  val choose: String get() = t("انتخاب کنید", "Choose", "وټاکئ")
  val search: String get() = t("جستجو", "Search", "لټون")
  val noMatch: String get() = t("چیزی با این جستجو پیدا نشد", "Nothing matches this search", "په دې لټون هېڅ ونه موندل شول")
}
