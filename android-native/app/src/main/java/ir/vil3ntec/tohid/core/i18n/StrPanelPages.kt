package ir.vil3ntec.tohid.core.i18n

/**
 *  لوگو و تصویرِ سرِ ویترین (`StorefrontMediaPanel.kt`)، کارت‌های نقشهٔ «ساعت و روز» (`HeatmapPanel.kt`) و کارت‌های سنِ بدهی و
 *  RFM ِ گزارشِ مشتریِ گوشی (`CustomerReportsPhone.kt`) — صفحه‌های زندهٔ کوچکِ `ui/screens` که پیش‌تر متنِ خامِ فارسی بودند و از هیچ
 *  زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۵). فارسی کلمه‌به‌کلمه همان پیشین؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار
 *  رفته. نامِ روز و ساعتِ نقشه (`HeatmapModel`) و برچسبِ بازهٔ بدهی و گروهِ RFM (`CustomerInsight`) ستون‌های خروجیِ اکسل هم هستند و
 *  تا تصمیمِ صاحب‌کار دست نخوردند. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrPanelPages {
  // ── `StorefrontMediaPanel.kt` ──
  val mediaTitle: String get() = t("لوگو و تصویرِ سرِ ویترین", "Storefront logo and header image", "د ویترین لوګو او سرانځور")
  val mediaNote: String get() = t(
    "لوگو مربع و تصویرِ سر سه به یک بریده می‌شود (از وسطِ عکس). همین‌ها در ویترین و بازار دیده می‌شوند.",
    "The logo is cropped square and the header image three to one (from the middle of the photo). These are what the storefront and the market show.",
    "لوګو مربع او سرانځور درې پر یو پرې کېږي (د انځور له منځه). همدا په ویترین او بازار کې ښکاري.",
  )
  val mediaLoadFailed: String get() = t("لوگو و تصویر باز نشد", "The logo and image didn't open", "لوګو او انځور خلاص نه شول")
  /** نامِ هر نوع (`StorefrontMediaSpec.Kind.label` ِ همان فارسی) */
  val logo: String get() = t("لوگو", "Logo", "لوګو")
  val headerImage: String get() = t("تصویرِ سر", "Header image", "سرانځور")
  val fileTooBig: String get() = t("فایل بیش از اندازه بزرگ است", "The file is too large", "فایل له اندازې ډېر لوی دی")
  val change: String get() = t("عوض کردن", "Change", "بدلول")
  fun mediaSaved(kind: String): String = t("${asIs(kind)} ثبت شد", "$kind saved", "$kind ثبت شو")
  fun mediaRemoved(kind: String): String = t("${asIs(kind)} برداشته شد", "$kind removed", "$kind لرې شو")

  // ── `HeatmapPanel.kt` ──
  val peakHour: String get() = t("پرفروش‌ترین ساعت", "Busiest hour", "تر ټولو ګڼ ساعت")
  fun salesN(n: Int): String = t("${asIs(n)} فروش", "$n ${pl(n, "sale", "sales")}", "$n پلور")
  val noSales: String get() = t("بی فروش", "No sales", "پلور نشته")
  val salesCount: String get() = t("شمارِ فروش", "Number of sales", "د پلور شمېر")
  val inThisRange: String get() = t("در این بازه", "In this period", "په دې موده کې")
  val netAmount: String get() = t("خالصِ مبلغ", "Net amount", "خالص مبلغ")
  val salesLessReturns: String get() = t("فروش − برگشت", "Sales − returns", "پلور − ستنېدنه")

  // ── `CustomerReportsPhone.kt` ──
  val totalBalance: String get() = t("ماندهٔ کل", "Total balance", "ټول پاتې")
  val noOpenDebt: String get() = t("بدهیِ بازی نیست", "No open debts", "هېڅ خلاص پور نشته")
  val nothingOpen: String get() = t("بی بردِ باز", "Nothing open", "هېڅ خلاص مبلغ نشته")
  fun oldestDays(n: Any): String = t(" — کهنه‌ترین ${asIs(n)} روز", " — oldest $n ${pl(n, "day", "days")}", " — تر ټولو زوړ $n ${pl(n, "ورځ", "ورځې")}")
  val rfmNote: String get() = t(
    "مشتریانِ خریدارِ یک سالِ گذشته: امتیازِ ۱ تا ۵ برای تازگی (R)، دفعات (F) و مبلغ (M).",
    "Customers who bought in the past year: a score of 1 to 5 for recency (R), frequency (F) and amount (M).",
    "د تېر یو کال پېرودونکي: د تازګۍ (R)، ځلونو (F) او مبلغ (M) لپاره له ۱ تر ۵ نمره.",
  )
  val noBuyersYear: String get() = t(
    "در یک سالِ گذشته خریدی به نامِ مشتری ثبت نشده",
    "No purchase was recorded under a customer's name in the past year",
    "په تېر یو کال کې د پېرودونکي په نوم هېڅ پېرود نه دی ثبت شوی",
  )
  fun rfmLine(times: Any, amount: String, days: Any): String = t(
    "${asIs(times)} خرید · ${asIs(amount)} ؋ · آخرین خرید ${asIs(days)} روز پیش",
    "$times ${pl(times, "purchase", "purchases")} · $amount ؋ · last purchase $days ${pl(days, "day", "days")} ago",
    "$times پېرود · $amount ؋ · وروستی پېرود $days ${pl(days, "ورځ", "ورځې")} مخکې",
  )

  // ── دورِ ۶: نمایشگرِ مشتری (HW-10، پنجرهٔ دومِ کامپیوتر رو به مشتری) ──
  val thanksForPurchase: String get() = t("سپاس از خرید شما", "Thank you for your purchase", "له پېرود مو مننه")
}
