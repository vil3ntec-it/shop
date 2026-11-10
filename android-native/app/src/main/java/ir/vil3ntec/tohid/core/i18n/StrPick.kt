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
    "سفارش #${asIs(orderNo)}: کیسهٔ ${asIs(bag)} از ${asIs(of)} روی ${asIs(slot)}",
    "Order #$orderNo: bag $bag of $of on $slot",
    "فرمایش #$orderNo: کڅوړه $bag له $of څخه په $slot کې",
  )
  val readyNow: String get() = t("همهٔ کیسه‌ها روی قفسه است؛ سفارش آماده شد", "All bags are on the shelf; the order is ready", "ټولې کڅوړې په المارۍ کې دي؛ فرمایش چمتو شو")
  fun shelfRow(orderNo: Long, slot: String): String = t("#${asIs(orderNo)} · جای ${asIs(slot)}", "#$orderNo · slot $slot", "#$orderNo · ځای $slot")
  fun bags(scanned: Int, bags: Int): String = t("کیسه: ${asIs(scanned)} از ${asIs(bags)}", "Bags: $scanned of $bags", "کڅوړې: $scanned له $bags څخه")
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
  fun make(n: Int): String = t("ساختنِ دسته (${asIs(n)} سفارش)", "Create batch ($n orders)", "ډله جوړه کړئ ($n فرمایشونه)")
  fun progress(done: Int, total: Int): String = t("${asIs(done)} از ${asIs(total)} قلم", "$done of $total items", "$done له $total توکو څخه")
  fun bin(bin: Int, orderNo: Long): String = t("سبدِ ${asIs(bin)} · #${asIs(orderNo)}", "Bin $bin · #$orderNo", "ټوکرۍ $bin · #$orderNo")
  fun orderSheet(orderNo: Long): String = t("برگهٔ #${asIs(orderNo)}", "Sheet #$orderNo", "پاڼه #$orderNo")
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
  fun mOrders(n: Int): String = t("سفارشِ بسته‌شده: ${asIs(n)}", "Packed orders: $n", "بسته شوي فرمایشونه: $n")
  fun mAvg(min: String): String = t("میانگینِ زمانِ جمع‌آوری: ${asIs(min)} دقیقه", "Average picking time: $min min", "د راټولولو منځنی وخت: $min دقیقې")
  fun mMedian(min: String): String = t("میانهٔ زمان: ${asIs(min)} دقیقه", "Median time: $min min", "منځنی وخت: $min دقیقې")
  fun mLines(found: Int, lines: Int): String = t("قلمِ پیداشده: ${asIs(found)} از ${asIs(lines)}", "Items found: $found of $lines", "موندل شوي توکي: $found له $lines څخه")
  fun mFound(pct: String): String = t("درصدِ پیدا شدن: ${asIs(pct)}٪", "Found rate: $pct%", "د موندلو کچه: $pct٪")
  fun mSubst(pct: String): String = t("نرخِ جایگزینی: ${asIs(pct)}٪", "Substitution rate: $pct%", "د بدیل کچه: $pct٪")
  fun mWrong(n: Int): String = t("اسکنِ اشتباه: ${asIs(n)}", "Wrong scans: $n", "ناسم سکنونه: $n")

  // رسیدگی به «پیدا نکردم» (PCK-14)
  val tabIssues: String get() = t("پیدا نشده‌ها", "Not-found items", "نه موندل شوي توکي")
  val issuesHint: String get() = t(
    "کالایی که «پیدا نکردم» خورد تا رسیدگی در ویترین «تمام شده» است.",
    "Items marked not found stay sold out in the storefront until reviewed.",
    "هغه توکي چې «ونه موندل شو» شوي، تر کتنې پورې په پلورنځي کې پای ته رسېدلي ښودل کېږي.",
  )
  val issuesEmpty: String get() = t("وظیفهٔ بازی نیست", "No open tasks", "پرانیستې دنده نشته")
  fun issueRow(name: String, hits: Int): String = t("${asIs(name)} · ${asIs(hits)} بار", "$name · $hits times", "$name · $hits ځله")
  val issueNote: String get() = t("توضیحِ رسیدگی (لازم)", "Review note (required)", "د کتنې یادښت (اړین)")
  val resolve: String get() = t("رسیدگی شد", "Resolved", "وکتل شو")
  val dismiss: String get() = t("کنار گذاشته شد", "Dismiss", "یو طرف شو")

  // جایگزین (PCK-05) و حلال (PCK-06)
  val substitute: String get() = t("جایگزین", "Substitute", "بدیل")
  fun subTitle(name: String): String = t("جایگزینِ «${asIs(name)}»", "Substitute for “$name”", "د «$name» بدیل")
  val subHint: String get() = t(
    "فقط هم‌دسته و هم‌حلال؛ مشتری قیمتِ کمتر را می‌پردازد و تأیید با خودِ اوست.",
    "Same category and halal status only; the customer pays the lower price and decides.",
    "یوازې ورته ډله او ورته حلال حالت؛ پېرودونکی ټیټه بیه ورکوي او پرېکړه د هغه ده.",
  )
  val subNone: String get() = t("مشتری جایگزین نخواسته است", "The customer does not want substitutes", "پېرودونکي بدیل نه دی غوښتی")
  val subNoCategory: String get() = t("دستهٔ این کالا معلوم نیست؛ جایگزینِ امن ساخته نمی‌شود", "This item has no category; no safe substitute", "د دې توکي ډله نه ده معلومه؛ خوندي بدیل نه جوړېږي")
  val subEmpty: String get() = t("جایگزینِ امنی در دکان نیست", "No safe substitute in the shop", "په دوکان کې خوندي بدیل نشته")
  fun subLine(name: String, price: String, total: String): String = t("${asIs(name)} · ${asIs(price)} · جمع ${asIs(total)}", "$name · $price · total $total", "$name · $price · ټول $total")
  val subScan: String get() = t("اسکنِ بارکدِ جایگزین", "Scan the substitute's barcode", "د بدیل بارکوډ سکن کړئ")
  val subPropose: String get() = t("پیشنهاد به مشتری", "Offer to customer", "پېرودونکي ته وړاندیز")
  val subSent: String get() = t("پیشنهاد ثبت شد؛ پیوند را برای مشتری بفرستید", "Offer saved; send the link to the customer", "وړاندیز ثبت شو؛ پېرودونکي ته لینک ولېږئ")
  val subWhatsapp: String get() = t("فرستادن در واتس‌اپ", "Send on WhatsApp", "په واټس‌اپ کې لېږل")
  val subAccept: String get() = t("مشتری پذیرفت", "Customer accepted", "پېرودونکي ومنله")
  val subReject: String get() = t("مشتری رد کرد", "Customer declined", "پېرودونکي رد کړه")
  val subPending: String get() = t("در انتظارِ مشتری", "Waiting for the customer", "د پېرودونکي په تمه")
  val subAccepted: String get() = t("پذیرفته شد", "Accepted", "ومنل شو")
  val subRejected: String get() = t("رد شد", "Declined", "رد شو")
  val subWithdrawn: String get() = t("پس گرفته شد", "Withdrawn", "بېرته واخیستل شو")
  val subWillAccept: String get() = t("مهلت گذشت؛ پذیرفته می‌شود", "Time is up; it will be accepted", "وخت تېر شو؛ منل کېږي")
  val subWillReject: String get() = t("مهلت گذشت؛ رد می‌شود", "Time is up; it will be declined", "وخت تېر شو؛ ردېږي")
  val halalOf: String get() = t("حلالِ کالای اصل", "Halal status of the original item", "د اصلي توکي حلال حالت")
  val halal: String get() = t("حلال", "Halal", "حلال")
  val nonHalal: String get() = t("غیرحلال", "Not halal", "ناحلال")
  val halalUnknown: String get() = t("نامعلوم", "Unknown", "نامعلوم")
  val halalSaved: String get() = t("حلالِ کالا ثبت شد", "Halal status saved", "حلال حالت ثبت شو")

  // وزنِ بیش از حد تا جوابِ مشتری (S56، `ui/screens/PickWeightHoldUi.kt`)
  val holdPending: String get() = t("وزن بیشتر از حد؛ در انتظارِ مشتری", "Over the weight limit; waiting for the customer", "له حد څخه زیات وزن؛ د پېرودونکي په تمه")
  fun holdLine(actual: String, total: String, extra: String): String = t(
    "وزنِ واقعی ${asIs(actual)} · مبلغ ${asIs(total)} (${asIs(extra)} بیشتر)",
    "Actual weight $actual · amount $total ($extra more)",
    "رښتینی وزن $actual · بیه $total ($extra زیات)",
  )
  val holdHint: String get() = t(
    "مشتری با پیوندِ پیگیری جواب می‌دهد، یا پس از تماس همین‌جا ثبت کنید. اگر تا وقتش جواب نیاید، قلم حذف و از جمع کسر می‌شود.",
    "The customer answers on the tracking link, or record the answer here after a call. With no answer in time, the item is removed from the total.",
    "پېرودونکی د تعقیب په لینک ځواب ورکوي، یا له اړیکې وروسته یې دلته ثبت کړئ. که په وخت ځواب رانشي، توکی لرې او له ټولې بیې کمېږي.",
  )
  val holdExpired: String get() = t("وقتِ جواب گذشت؛ قلم حذف می‌شود", "Time is up; the item will be removed", "د ځواب وخت تېر شو؛ توکی لرې کېږي")
  val holdSent: String get() = t(
    "وزن از حدِ مجاز بیشتر است؛ تا جوابِ مشتری نگه داشته شد",
    "Over the allowed weight; held until the customer answers",
    "وزن له اجازه لرونکي حد څخه زیات دی؛ د پېرودونکي تر ځواب پورې وساتل شو",
  )
  val holdRemoved: String get() = t("قلم حذف و از جمع کسر شد", "Item removed and taken off the total", "توکی لرې او له ټولې بیې کم شو")

  // عکسِ بسته (PCK-08)
  val photoTitle: String get() = t("عکسِ بسته", "Parcel photo", "د بستې انځور")
  val photoConsent: String get() = t(
    "عکس فقط از کیسه‌های بسته است؛ چهرهٔ کسی در عکس نیست",
    "The photo shows only the packed bags; no one's face",
    "انځور یوازې د بسته شویو کڅوړو دی؛ د هېچا مخ په کې نشته",
  )
  val photoTake: String get() = t("گرفتنِ عکس", "Take photo", "انځور اخیستل")
  val photoNote: String get() = t("فقط کیسه‌های بسته؛ حداکثر ۳ عکس", "Packed bags only; up to 3 photos", "یوازې بسته شوې کڅوړې؛ تر ۳ انځورونو پورې")
  fun photos(n: Int, max: Int): String = t("عکسِ بسته: ${asIs(n)} از ${asIs(max)}", "Parcel photos: $n of $max", "د بستې انځورونه: $n له $max څخه")
  val photoSaved: String get() = t("عکسِ بسته ثبت شد", "Parcel photo saved", "د بستې انځور ثبت شو")
  val photoBad: String get() = t("عکس خوانده نشد؛ دوباره بگیرید", "The photo could not be read; take it again", "انځور ونه لوستل شو؛ بیا یې واخلئ")

  // چاپِ برچسبِ کیسه‌ها (PCK-09)
  val printLabels: String get() = t("چاپِ برچسبِ کیسه‌ها", "Print bag labels", "د کڅوړو لیبلونه چاپ کړئ")
  val noPrinter: String get() = t("چاپگرِ رسید تنظیم نشده است", "No receipt printer is set up", "د رسید چاپګر نه دی ټاکل شوی")
  val printed: String get() = t("برچسب‌ها چاپ شد", "Labels printed", "لیبلونه چاپ شول")
  fun printFailed(why: String): String = t("چاپ نشد: ${asIs(why)}", "Not printed: $why", "چاپ نه شو: $why")
  fun labelCaption(orderNo: Long, bag: Int, of: Int): String = t("#${asIs(orderNo)} · کیسهٔ ${asIs(bag)} از ${asIs(of)}", "#$orderNo · bag $bag of $of", "#$orderNo · کڅوړه $bag له $of څخه")
  fun labelsJob(orderNo: Long): String = t("برچسبِ کیسه‌های #${asIs(orderNo)}", "Bag labels #$orderNo", "د #$orderNo کڅوړو لیبلونه")
}
