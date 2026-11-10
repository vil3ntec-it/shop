package ir.vil3ntec.tohid.core.i18n

/**
 *  RPT-14 — صفحهٔ «گزارشِ فروشِ اینترنتی» (`ui/v3/analytics/OnlineReportPage.kt`؛ عددها از `core/report/OnlineReport`).
 */
@Suppress("unused")
object StrOnlineReport {

  val title: String get() = t("گزارشِ فروشِ اینترنتی", "Online sales report", "د انټرنېټي پلور راپور")
  val loading: String get() = t("گزارش از سرور می‌آید…", "Loading the report from the server…", "راپور له سرور څخه راځي…")
  val failed: String get() = t("گزارش نیامد. اینترنت را ببینید و دوباره بزنید.", "The report did not load. Check the internet and try again.", "راپور رانغی. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val off: String get() = t("این گزارش برای این دکان روشن نیست.", "This report is not turned on for this shop.", "دا راپور د دې دوکان لپاره فعال نه دی.")
  val retry: String get() = t("دوباره", "Retry", "بیا")
  fun span(days: Int): String =
    if (days == 1) t("۲۴ ساعت", "24 hours", "۲۴ ساعته") else t("${asIs(days)} روز", "$days days", "$days ورځې")
  val none: String get() = t("در این بازه سفارشِ ویترین نیست.", "No storefront orders in this period.", "په دې موده کې د ویټرین فرمایش نشته.")

  val funnel: String get() = t("قیفِ سفارش", "Order funnel", "د فرمایش کیف")
  val funnelNote: String get() = t(
    "هر گام یعنی سفارش دست‌کم یک بار به آن رسید؛ نسبت از سفارش‌های ثبت‌شده است.",
    "Each step means the order reached it at least once; rates are of placed orders.",
    "هر ګام دا معنا لري چې فرمایش لږ تر لږه یو ځل هلته ورسېد؛ نسبت د ثبت شویو فرمایشونو دی.",
  )
  val placed: String get() = t("ثبت شد", "Placed", "ثبت شو")
  val accepted: String get() = t("قبول شد", "Accepted", "ومنل شو")
  val packed: String get() = t("بسته شد", "Packed", "بسته شو")
  val handedOver: String get() = t("سپرده شد (پیک یا بردن)", "Handed over (courier or pickup)", "وسپارل شو (پیک یا وړل)")
  val completed: String get() = t("انجام شد", "Completed", "بشپړ شو")

  val lost: String get() = t("از دست رفته", "Lost orders", "له لاسه وتلي فرمایشونه")
  fun lostRate(pct: String): String = t("${asIs(pct)} از سفارش‌ها", "$pct of orders", "$pct فرمایشونه")
  val rejected: String get() = t("رد شد", "Rejected", "رد شو")
  val autoRejected: String get() = t("ردِ خودکار (مهلتِ قبول)", "Auto-rejected (acceptance timeout)", "اتومات رد (د منلو موده)")
  val cancelledByCustomer: String get() = t("لغوِ مشتری", "Cancelled by customer", "د پېرودونکي لغوه")
  val cancelledByShop: String get() = t("لغوِ دکان", "Cancelled by shop", "د دوکان لغوه")
  val returnedToStore: String get() = t("برگشت به دکان", "Returned to shop", "دوکان ته بېرته")
  val deliveryFailed: String get() = t("ناموفقِ تحویل", "Failed delivery", "ناکامه سپارنه")
  fun failRate(pct: String): String = t("${asIs(pct)} از سفارش‌های بیرون‌رفته", "$pct of orders sent out", "$pct له وتلو فرمایشونو")

  val times: String get() = t("زمان‌ها", "Times", "وختونه")
  val timesNote: String get() = t("میانه و ۹۰٪ ِ سفارش‌ها؛ زمانِ هر گام از گامِ پیش.", "Median and 90th percentile; each step from the one before.", "منځنی او ۹۰٪؛ د هر ګام وخت له مخکیني ګام څخه.")
  val untilAccepted: String get() = t("تا قبول", "Until accepted", "تر منلو")
  val preparation: String get() = t("آماده‌سازی", "Preparation", "چمتو کول")
  val delivery: String get() = t("تحویل", "Delivery", "سپارنه")
  val total: String get() = t("کل", "Total", "ټول")
  fun median(v: String, p90: String): String = t("میانه ${asIs(v)} · ۹۰٪: ${asIs(p90)}", "Median $v · 90%: $p90", "منځنی $v · ۹۰٪: $p90")
  fun duration(hours: Long, minutes: Long): String = when {
    hours == 0L -> t("${asIs(minutes)} دقیقه", "$minutes min", "$minutes دقیقې")
    minutes == 0L -> t("${asIs(hours)} ساعت", "$hours h", "$hours ساعته")
    else -> t("${asIs(hours)} ساعت و ${asIs(minutes)} دقیقه", "$hours h $minutes min", "$hours ساعته او $minutes دقیقې")
  }

  val completedTotal: String get() = t("جمعِ سفارش‌های انجام‌شده", "Total of completed orders", "د بشپړو فرمایشونو ټولګه")
  val visitsNone: String get() = t(
    "شمارِ بازدیدِ ویترین (بالای قیف) هنوز نیست؛ عددِ ساختگی نشان داده نمی‌شود.",
    "Storefront visit counts (top of the funnel) are not available yet; no made-up number is shown.",
    "د ویټرین د لیدنو شمېر (د کیف سر) لا نشته؛ جوړ شوی شمېر نه ښودل کېږي.",
  )

  val webFunnel: String get() = t("بالای قیف (ویترینِ وب)", "Top of the funnel (web storefront)", "د کیف سر (ویب ویټرین)")
  val webFunnelNote: String get() = t(
    "بازدیدکنندهٔ یکتای هر روز؛ نسبت از بازدید است. برنامهٔ خرید شمرده نمی‌شود.",
    "Unique visitors per day; rates are of visits. The shopping app is not counted.",
    "د هرې ورځې ځانګړي لیدونکي؛ نسبت د لیدنو دی. د پېرودلو اپ نه شمېرل کېږي.",
  )
  val webFunnelCapped: String get() = t(
    "در روزی از این بازه سقفِ روزانهٔ شمارش پر شد؛ عددِ واقعی بیشتر است.",
    "The daily counting cap was reached on a day in this period; the real number is higher.",
    "د دې مودې په یوه ورځ د شمېرنې ورځنی حد پوره شو؛ اصلي شمېر ډېر دی.",
  )
  val visitors: String get() = t("بازدید", "Visits", "لیدنې")
  val productViewers: String get() = t("دیدنِ کالا", "Viewed a product", "توکی یې ولید")
  val cartAdders: String get() = t("افزودن به سبد", "Added to cart", "ټوکرۍ ته ورزیاتول")

  val misses: String get() = t("جست‌وجوهای بی‌نتیجهٔ ویترین", "Storefront searches with no results", "د ویټرین بې پایلې لټونونه")
  val missesNote: String get() = t("کالایی که مشتری خواست و نیافت؛ پرتکرارترین بالا.", "What customers looked for and did not find; most frequent first.", "هغه څه چې پېرودونکو وغوښتل او ونه موندل؛ تر ټولو ډېر پورته.")
  val missesNone: String get() = t("در این بازه جست‌وجوی بی‌نتیجه‌ای نبود.", "No searches without results in this period.", "په دې موده کې بې پایلې لټون نه و.")
  fun times(n: Int): String = t("${asIs(n)} بار", "$n times", "$n ځله")
}
