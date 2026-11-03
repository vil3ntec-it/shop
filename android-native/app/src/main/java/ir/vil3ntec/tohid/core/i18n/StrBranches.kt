package ir.vil3ntec.tohid.core.i18n

/**
 *  RPT-18 — صفحهٔ «گزارشِ همهٔ شعبه‌ها» (`ui/v3/analytics/BranchesReportPage.kt`؛ عددها از `core/report/BranchesReport`).
 */
@Suppress("unused")
object StrBranches {

  val title: String get() = t("گزارشِ همهٔ شعبه‌ها", "All branches report", "د ټولو څانګو راپور")
  val loading: String get() = t("گزارش از سرور می‌آید…", "Loading the report from the server…", "راپور له سرور څخه راځي…")
  val failed: String get() = t("گزارش نیامد. اینترنت را ببینید و دوباره بزنید.", "The report did not load. Check the internet and try again.", "راپور رانغی. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val off: String get() = t("این گزارش برای این دکان روشن نیست.", "This report is not turned on for this shop.", "دا راپور د دې دوکان لپاره فعال نه دی.")
  val ownerOnly: String get() = t("این گزارش فقط برای صاحبِ دکان است.", "This report is for the shop owner only.", "دا راپور یوازې د دوکان د خاوند لپاره دی.")
  val retry: String get() = t("دوباره", "Retry", "بیا")
  fun span(days: Int): String =
    if (days == 1) t("امروز", "Today", "نن") else t("${asIs(days)} روز", "$days days", "$days ورځې")
  val spanNote: String get() = t(
    "روزِ هر شعبه به وقتِ همان شعبه است؛ عددها فقط از فروش‌های همگام‌شده.",
    "Each branch's day is in its own local time; numbers come only from synced sales.",
    "د هرې څانګې ورځ د هماغې څانګې په وخت ده؛ شمېرې یوازې له همغږي شویو پلورونو څخه.",
  )

  val allBranches: String get() = t("جمعِ همهٔ شعبه‌ها", "All branches together", "د ټولو څانګو ټولګه")
  fun branchCount(n: Int): String = t("${asIs(n)} شعبه", "$n branches", "$n څانګې")
  val thisBranch: String get() = t("همین شعبه", "This branch", "همدا څانګه")
  val netSales: String get() = t("فروشِ خالص", "Net sales", "خالص پلور")
  val invoices: String get() = t("فاکتور", "Invoices", "بلونه")
  val sales: String get() = t("فروش", "Sales", "پلور")
  val discount: String get() = t("تخفیف", "Discount", "تخفیف")
  val returnCount: String get() = t("شمارِ برگشتی", "Returns (count)", "د ستنېدنو شمېر")
  val returns: String get() = t("برگشتی", "Returns", "ستنېدنې")
  val cashNet: String get() = t("نقد (خالص)", "Cash (net)", "نغدې (خالص)")
  val otherNet: String get() = t("روش‌های دیگر (خالص)", "Other methods (net)", "نورې لارې (خالص)")
  fun share(pct: String): String = t("${asIs(pct)} از کل", "$pct of total", "$pct د ټولو")
  val payments: String get() = t("روشِ پرداخت", "Payment methods", "د تادیې لارې")
  val top: String get() = t("پرفروش‌ها", "Best sellers", "تر ټولو پلورل شوي")
  fun lowStock(n: Long): String = t("کالای کم‌موجود: ${asIs(n)}", "Low-stock items: $n", "کم موجود توکي: $n")
  val noneInRange: String get() = t("در این بازه فاکتور یا برگشتیِ همگام‌شده‌ای نیست.", "No synced invoices or returns in this period.", "په دې موده کې همغږي شوي بلونه یا ستنېدنې نشته.")
  val partial: String get() = t("ردیف‌ها از سقفِ یک گزارش بیشتر بود؛ این عددها ناقص‌اند.", "There were more rows than one report holds; these numbers are incomplete.", "قطارونه د یوه راپور له پولې ډېر وو؛ دا شمېرې نیمګړې دي.")
  fun skipped(n: Int): String = t(
    "${asIs(n)} شعبه گزارشِ شعبه‌ها را روشن ندارد و این‌جا نیامد.",
    "$n branches do not have the branches report turned on and are not shown.",
    "$n څانګې د څانګو راپور نه لري او دلته نه دي راغلې.",
  )
  val truncated: String get() = t("فقط ۲۰ شعبهٔ نخست آمد.", "Only the first 20 branches are shown.", "یوازې لومړۍ ۲۰ څانګې راغلې.")
  val totalsNote: String get() = t(
    "«جمعِ همه» فقط جمعِ عددهای شعبه‌هاست؛ کالا و مشتری میانِ شعبه‌ها یکی نمی‌شوند.",
    "“All together” only adds up the branch numbers; products and customers are not merged across branches.",
    "«ټولګه» یوازې د څانګو شمېرې جمع کوي؛ توکي او پېرودونکي د څانګو ترمنځ نه یوځای کېږي.",
  )
}
