package ir.vil3ntec.tohid.core.i18n

/**
 *  میزِ جمع‌آوری — قفسهٔ انتظار (PCK-10) و جمع‌آوریِ گروهی (PCK-12)، `ui/screens/PickDesk.kt`.
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrPick {

  val desk: String get() = t("قفسه و دسته", "Shelf & batch", "المارۍ او ډله")
  val title: String get() = t("میزِ جمع‌آوری", "Picking desk", "د راټولولو مېز")
  val tabShelf: String get() = t("قفسهٔ انتظار", "Waiting shelf", "د انتظار المارۍ")
  val tabBatch: String get() = t("جمع‌آوریِ گروهی", "Batch picking", "ډله‌ییز راټولول")
  val refresh: String get() = t("تازه کن", "Refresh", "تازه یې کړئ")

  // قفسهٔ انتظار
  val slot: String get() = t("جای قفسه (مثلاً A2)", "Shelf slot (e.g. A2)", "د المارۍ ځای (لکه A2)")
  val scanBag: String get() = t("اسکنِ برچسبِ کیسه", "Scan bag label", "د کڅوړې لیبل سکن کړئ")
  val send: String get() = t("ثبت", "Save", "ثبتول")
  val slotFirst: String get() = t("اول جای قفسه را بنویسید", "Enter the shelf slot first", "لومړی د المارۍ ځای ولیکئ")
  val notLabel: String get() = t("این برچسبِ کیسه نیست (VLNB-…)", "This is not a bag label (VLNB-…)", "دا د کڅوړې لیبل نه دی (VLNB-…)")
  fun bagOn(orderNo: Long, bag: Int, of: Int, slot: String): String = t(
    "سفارش #$orderNo: کیسهٔ $bag از $of روی $slot",
    "Order #$orderNo: bag $bag of $of on $slot",
    "فرمایش #$orderNo: کڅوړه $bag له $of څخه په $slot کې",
  )
  val readyNow: String get() = t("همهٔ کیسه‌ها روی قفسه است؛ سفارش آماده شد", "All bags are on the shelf; the order is ready", "ټولې کڅوړې په المارۍ کې دي؛ فرمایش چمتو شو")
  fun shelfRow(orderNo: Long, slot: String): String = t("#$orderNo · جای $slot", "#$orderNo · slot $slot", "#$orderNo · ځای $slot")
  fun bags(scanned: Int, bags: Int): String = t("کیسه: $scanned از $bags", "Bags: $scanned of $bags", "کڅوړې: $scanned له $bags څخه")
  val ready: String get() = t("آماده", "Ready", "چمتو")
  val waitingBags: String get() = t("کیسه مانده", "Bags missing", "کڅوړې پاتې دي")
  val shelfEmpty: String get() = t("قفسهٔ انتظار خالی است", "The waiting shelf is empty", "د انتظار المارۍ تشه ده")
  val shelfHint: String get() = t(
    "پس از بسته‌بندی، هر کیسه را روی جای قفسه بگذارید و برچسبش را اسکن کنید.",
    "After packing, put each bag on a shelf slot and scan its label.",
    "له بسته‌بندۍ وروسته، هره کڅوړه د المارۍ په یوه ځای کې کېږدئ او لیبل یې سکن کړئ.",
  )

  // جمع‌آوریِ گروهی
  val choose: String get() = t("۲ تا ۱۰ سفارشِ قبول‌شده را برگزینید", "Choose 2 to 10 accepted orders", "له ۲ تر ۱۰ منل شوي فرمایشونه وټاکئ")
  val noOrders: String get() = t("سفارشِ قبول‌شده‌ای برای جمع‌آوری نیست", "No accepted orders to pick", "د راټولولو لپاره منل شوی فرمایش نشته")
  fun make(n: Int): String = t("ساختنِ دسته ($n سفارش)", "Create batch ($n orders)", "ډله جوړه کړئ ($n فرمایشونه)")
  fun progress(done: Int, total: Int): String = t("$done از $total قلم", "$done of $total items", "$done له $total توکو څخه")
  fun bin(bin: Int, orderNo: Long): String = t("سبدِ $bin · #$orderNo", "Bin $bin · #$orderNo", "ټوکرۍ $bin · #$orderNo")
  fun orderSheet(orderNo: Long): String = t("برگهٔ #$orderNo", "Sheet #$orderNo", "پاڼه #$orderNo")
  val picked: String get() = t("برداشتم", "Picked", "ما واخیست")
  val notFound: String get() = t("پیدا نکردم", "Not found", "ونه موندل شو")
  val scanItem: String get() = t("اسکنِ کالای همین قلم", "Scan this item", "د همدې توکي سکن")
  val newBatch: String get() = t("دستهٔ تازه", "New batch", "نوې ډله")
  val packHint: String get() = t(
    "وزن و بسته‌بندیِ هر سفارش در برگهٔ همان سفارش است.",
    "Weighing and packing for each order happen on that order's sheet.",
    "د هر فرمایش تلل او بسته‌بندي د همغه فرمایش په پاڼه کې دي.",
  )
  val retry: String get() = t("دوباره بزنید؛ همان کار یک بار حساب می‌شود", "Try again; it is counted once", "بیا یې وکړئ؛ یو ځل حسابېږي")

  // شاخص‌ها (PCK-13)
  val tabMetrics: String get() = t("شاخص‌ها", "Metrics", "شاخصونه")
  val metricsHint: String get() = t("۷ روزِ گذشته؛ همهٔ عددها از سرور.", "Last 7 days; all figures come from the server.", "تېرې ۷ ورځې؛ ټولې شمېرې له سرور څخه دي.")
  fun mOrders(n: Int): String = t("سفارشِ بسته‌شده: $n", "Packed orders: $n", "بسته شوي فرمایشونه: $n")
  fun mAvg(min: String): String = t("میانگینِ زمانِ جمع‌آوری: $min دقیقه", "Average picking time: $min min", "د راټولولو منځنی وخت: $min دقیقې")
  fun mMedian(min: String): String = t("میانهٔ زمان: $min دقیقه", "Median time: $min min", "منځنی وخت: $min دقیقې")
  fun mLines(found: Int, lines: Int): String = t("قلمِ پیداشده: $found از $lines", "Items found: $found of $lines", "موندل شوي توکي: $found له $lines څخه")
  fun mFound(pct: String): String = t("درصدِ پیدا شدن: $pct٪", "Found rate: $pct%", "د موندلو کچه: $pct٪")
  fun mSubst(pct: String): String = t("نرخِ جایگزینی: $pct٪", "Substitution rate: $pct%", "د بدیل کچه: $pct٪")
  fun mWrong(n: Int): String = t("اسکنِ اشتباه: $n", "Wrong scans: $n", "ناسم سکنونه: $n")

  // رسیدگی به «پیدا نکردم» (PCK-14)
  val tabIssues: String get() = t("پیدا نشده‌ها", "Not-found items", "نه موندل شوي توکي")
  val issuesHint: String get() = t(
    "کالایی که «پیدا نکردم» خورد تا رسیدگی در ویترین «تمام شده» است.",
    "Items marked not found stay sold out in the storefront until reviewed.",
    "هغه توکي چې «ونه موندل شو» شوي، تر کتنې پورې په پلورنځي کې پای ته رسېدلي ښودل کېږي.",
  )
  val issuesEmpty: String get() = t("وظیفهٔ بازی نیست", "No open tasks", "پرانیستې دنده نشته")
  fun issueRow(name: String, hits: Int): String = t("$name · $hits بار", "$name · $hits times", "$name · $hits ځله")
  val issueNote: String get() = t("توضیحِ رسیدگی (لازم)", "Review note (required)", "د کتنې یادښت (اړین)")
  val resolve: String get() = t("رسیدگی شد", "Resolved", "وکتل شو")
  val dismiss: String get() = t("کنار گذاشته شد", "Dismiss", "یو طرف شو")
}
