package ir.vil3ntec.tohid.core.i18n

/**
 *  هشدارِ رفتارِ غیرعادیِ صندوقدار (RSK-10) در زبانهٔ «ضدتقلب» ِ گزارشِ تازه؛ سابقهٔ مشتری (RSK-03، جزئیات و صف) و
 *  فهرستِ بسته و مطمئن (RSK-04، `OrderRiskV3.kt` و `OrderRiskListsV3.kt`).
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطقِ هشدار در `core/risk/CashierAlerts` (commonMain).
 */
@Suppress("unused")
object StrRisk {
  fun title(n: Any): String = t(
    "${asIs(n)} هشدارِ رفتارِ غیرعادیِ صندوقدار",
    "$n unusual cashier ${pl(n, "alert", "alerts")}",
    "د صندوقدار $n غیرعادي ${pl(n, "خبرتیا", "خبرتیاوې")}",
  )
  val rule: String get() = t(
    "سنجش با بقیهٔ کاربرانِ همین بازه: دستِ کم ۳ بار و دو برابرِ نرخِ آن‌ها",
    "Compared with the other users in this period: at least 3 times and twice their rate",
    "په همدې موده کې د نورو کاروونکو سره پرتله: لږ تر لږه ۳ ځلې او د هغوی دوه چنده",
  )
  val voids: String get() = t("لغوِ فاکتور", "voided invoices", "لغوه شوي بلونه")
  val discounts: String get() = t("تخفیف", "discounts", "تخفیفونه")
  val noSales: String get() = t("کشو بی فروش", "no-sale drawer opens", "بې پلور د دراز خلاصول")
  fun line(name: Any, kind: Any, count: Any, invoices: Any, peerCount: Any, peerInvoices: Any): String = t(
    "${asIs(name)}: ${asIs(count)} ${asIs(kind)} در ${asIs(invoices)} فاکتور — بقیه ${asIs(peerCount)} در ${asIs(peerInvoices)}",
    "$name: $count $kind in $invoices invoices — others $peerCount in $peerInvoices",
    "$name: $count $kind په $invoices بلونو کې — نور $peerCount په $peerInvoices کې",
  )

  /* ---------------- RSK-03 — سابقهٔ مشتری در جزئیاتِ سفارشِ اینترنتی ---------------- */

  val orderRiskTitle: String get() = t("سابقهٔ این مشتری در دکانِ شما", "This customer's history with your shop", "په ستاسو دوکان کې د دې پیرودونکي مخینه")
  val orderRiskFooter: String get() = t(
    "فقط سفارش‌های همین دکان؛ شماره دیده نمی‌شود.",
    "Only orders from this shop; the number is not shown.",
    "یوازې د همدې دوکان فرمایشونه؛ شمېره نه ښودل کېږي.",
  )
  val orderRiskFailed: String get() = t("سابقهٔ مشتری خوانده نشد", "Couldn't load the customer's history", "د پیرودونکي مخینه ونه لوستل شوه")
  fun orderRiskHistory(orders: Any, completed: Any, cancelled: Any, open: Any): String = t(
    "${asIs(orders)} سفارشِ پیشین · ${asIs(completed)} تحویل · ${asIs(cancelled)} لغو یا رد · ${asIs(open)} باز",
    "$orders earlier ${pl(orders, "order", "orders")} · $completed delivered · $cancelled cancelled or rejected · $open open",
    "$orders پخواني فرمایشونه · $completed سپارل شوي · $cancelled لغوه یا رد · $open پرانیستي",
  )
  /* نشانه‌های سرور (`order-risk.js` ‹riskOf›)؛ گزینشِ کد در `OrderRiskV3.kt` */
  val flagBlocklisted: String get() = t("این شماره یا جا در فهرستِ بستهٔ دکان است", "This number or place is on your block list", "دا شمېره یا ځای ستاسو په بند لېست کې دی")
  val flagAllowlisted: String get() = t("این شماره در فهرستِ مطمئنِ دکان است", "This number is on your trusted list", "دا شمېره ستاسو په باوري لېست کې ده")
  val flagNewCustomer: String get() = t("مشتریِ تازه — هنوز هیچ سفارشی از شما نگرفته", "New customer — hasn't received an order from you yet", "نوی پیرودونکی — تر اوسه یې له تاسو فرمایش نه دی اخیستی")
  fun flagDeliveryFailed(count: Any): String = t("${asIs(count)} بار تحویل ناموفق بوده", "Delivery failed $count ${pl(count, "time", "times")} before", "$count ځله سپارل ناکام شوي")
  fun flagNoShow(count: Any): String = t("${asIs(count)} بار سرِ تحویل نبوده", "Wasn't there for delivery $count ${pl(count, "time", "times")}", "$count ځله د سپارلو پر وخت نه و")
  fun flagReturnedToShop(count: Any): String = t("${asIs(count)} سفارش به دکان برگشته", "$count ${pl(count, "order", "orders")} came back to the shop", "$count فرمایشونه دوکان ته راستانه شوي")
  fun flagReturns(count: Any): String = t("${asIs(count)} برگشت یا بازپرداخت داشته", "$count ${pl(count, "return or refund", "returns or refunds")} before", "$count ځله ستنېدنه یا بېرته ورکړه")
  fun flagCancelsOften(count: Any): String = t("${asIs(count)} بار خودش لغو کرده", "Cancelled $count ${pl(count, "time", "times")} themselves", "$count ځله یې پخپله لغوه کړی")
  fun flagManyOpen(count: Any): String = t("${asIs(count)} سفارشِ بازِ دیگر دارد", "Has $count other open ${pl(count, "order", "orders")}", "$count نور پرانیستي فرمایشونه لري")
  fun flagDuplicateOf(orderNo: Any): String = t("شاید تکراریِ سفارشِ ${asIs(orderNo)}", "Possibly a duplicate of order $orderNo", "کېدای شي د $orderNo فرمایش تکرار وي")
  val flagDuplicate: String get() = t("شاید سفارشِ تکراری", "Possibly a duplicate order", "کېدای شي تکراري فرمایش وي")
  fun flagOther(code: Any): String = t("نشانهٔ دیگر: ${asIs(code)}", "Other signal: $code", "بله نښه: $code")

  /* ---------------- RSK-04 — فهرستِ بسته و مطمئن (صاحب و مدیر) ---------------- */

  val blockNumber: String get() = t("بستنِ این شماره", "Block this number", "دا شمېره بندول")
  val trustNumber: String get() = t("شمارهٔ مطمئن", "Trust this number", "باوري شمېره")
  val listsTitle: String get() = t("فهرستِ بسته و مطمئن", "Blocked and trusted list", "بند او باوري لېست")
  val blockHelp: String get() = t(
    "سفارشِ اینترنتیِ تازه از این شماره پذیرفته نمی‌شود؛ مشتری دلیل را نمی‌بیند.",
    "New online orders from this number are refused; the customer never sees the reason.",
    "له دې شمېرې نوي انلاین فرمایشونه نه منل کېږي؛ پیرودونکی دلیل نه ویني.",
  )
  val trustHelp: String get() = t(
    "سقفِ مشتریِ تازه برای این شماره برداشته می‌شود؛ بررسیِ سفارشِ تکراری می‌ماند.",
    "The new-customer limit is lifted for this number; duplicate checks stay.",
    "د دې شمېرې لپاره د نوي پیرودونکي حد لرې کېږي؛ د تکراري فرمایش څارنه پاتې کېږي.",
  )
  val entryReason: String get() = t("دلیل (برای خودتان)", "Reason (for you only)", "دلیل (یوازې ستاسو لپاره)")
  val entryReasonError: String get() = t("دلیل ۳ تا ۲۰۰ حرف", "Reason must be 3–200 characters", "دلیل باید ۳ تر ۲۰۰ توري وي")
  val entryDays: String get() = t("تا چند روز", "For how many days", "تر څو ورځو")
  val entryDaysError: String get() = t("۱ تا ۳۶۵ روز", "1 to 365 days", "۱ تر ۳۶۵ ورځې")
  val listsEmpty: String get() = t("هیچ شماره یا جایی در فهرست نیست", "Nothing is on the list", "په لېست کې هېڅ نشته")
  val remove: String get() = t("برداشتن", "Remove", "لرې کول")
  fun entryBlocked(phone: Any): String = t("بسته: ${asIs(phone)}", "Blocked: $phone", "بند: $phone")
  fun entryTrusted(phone: Any): String = t("مطمئن: ${asIs(phone)}", "Trusted: $phone", "باوري: $phone")
  fun entryPin(radiusM: Any): String = t("جای بسته (شعاعِ ${asIs(radiusM)} متر)", "Blocked place ($radiusM m radius)", "بند ځای ($radiusM متره)")
  fun entrySubtitle(reason: String, until: String): String =
    t("${asIs(reason)} · تا ${asIs(until)}", "$reason · until $until", "$reason · تر $until پورې")

  /* ---------------- RSK-03 «کارت در صف» — سفارش‌های پیش از ارسال با هشدار (`OrderRisk.queue`) ---------------- */

  fun queueTitle(n: Any): String = t(
    "${asIs(n)} سفارش پیش از ارسال نگاهِ دوباره می‌خواهد",
    "$n ${pl(n, "order needs", "orders need")} a second look before sending",
    "$n فرمایشونه د لېږلو مخکې بیا کتنې ته اړتیا لري",
  )
  val queueFooter: String get() = t(
    "از سابقهٔ همین مشتری در دکانِ شما؛ تصمیم با خودتان است.",
    "From this customer's history with your shop; the decision is yours.",
    "ستاسو په دوکان کې د همدې پیرودونکي له مخینې؛ پرېکړه ستاسو ده.",
  )
  val queueTruncated: String get() = t(
    "فقط سفارش‌های تازه‌تر سنجیده شدند؛ بقیه را در جزئیاتِ هر سفارش ببینید.",
    "Only the newest orders were checked; see the rest in each order's details.",
    "یوازې نوي فرمایشونه وکتل شول؛ نور د هر فرمایش په جزئیاتو کې وګورئ.",
  )
  val queueFailed: String get() = t("سفارش‌های پیش از ارسال سنجیده نشدند", "Couldn't check orders before sending", "د لېږلو مخکې فرمایشونه ونه کتل شول")

  /* ---------------- RSK-04 — پین (جای بسته) و صفحهٔ جدای فهرست ---------------- */

  val blockPlace: String get() = t("بستنِ این جا", "Block this place", "دا ځای بندول")
  val blockPlaceHelp: String get() = t(
    "سفارشِ اینترنتیِ تازه برای ارسال به این جا (درونِ شعاع) پذیرفته نمی‌شود؛ مشتری دلیل را نمی‌بیند.",
    "New online delivery orders to this place (within the radius) are refused; the customer never sees the reason.",
    "دې ځای ته (د شعاع دننه) نوي انلاین فرمایشونه نه منل کېږي؛ پیرودونکی دلیل نه ویني.",
  )
  val entryRadius: String get() = t("شعاع (متر)", "Radius (metres)", "شعاع (متره)")
  val entryRadiusError: String get() = t("۱۰ تا ۲۰۰۰ متر", "10 to 2000 metres", "۱۰ تر ۲۰۰۰ متره")
  val blockANumber: String get() = t("بستنِ یک شماره", "Block a number", "یوه شمېره بندول")
  val trustANumber: String get() = t("شمارهٔ مطمئنِ تازه", "Trust a number", "یوه شمېره باوري کول")
  val blockAPlace: String get() = t("بستنِ یک جا", "Block a place", "یو ځای بندول")
  val entryPhone: String get() = t("شمارهٔ مشتری", "Customer's number", "د پیرودونکي شمېره")
  val entryPhoneError: String get() = t("شماره لازم است", "Enter a number", "شمېره ولیکئ")
  val entryPlace: String get() = t("مختصات یا پیوندِ نقشه", "Coordinates or map link", "مختصات یا د نقشې لینک")
  val entryPlaceHelp: String get() = t(
    "پیوند را از برنامهٔ نقشه بچسبانید، یا مثلاً ۳۴٫۵۲۸, ۶۹٫۱۷۲",
    "Paste a link from your maps app, or e.g. 34.528, 69.172",
    "د نقشې له اپ څخه لینک ولګوئ، یا لکه 34.528, 69.172",
  )
  val entryPlaceError: String get() = t("مختصات یا پیوند خوانده نشد", "Couldn't read the coordinates or link", "مختصات یا لینک ونه لوستل شو")
  /* ---------------- RSK-04 — «دستگاه» (شناسهٔ تصادفیِ مرورگر یا برنامهٔ خریدِ همان سفارش) ---------------- */

  val blockDevice: String get() = t("بستنِ این دستگاه", "Block this device", "دا وسیله بندول")
  val trustDevice: String get() = t("دستگاهِ مطمئن", "Trust this device", "باوري وسیله")
  val blockDeviceHelp: String get() = t(
    "سفارشِ اینترنتیِ تازه از همان مرورگر یا برنامهٔ خریدی که این سفارش را فرستاد پذیرفته نمی‌شود، حتی با شمارهٔ دیگر؛ مشتری دلیل را نمی‌بیند.",
    "New online orders from the browser or shopping app that sent this order are refused, even with another number; the customer never sees the reason.",
    "له هماغه براوزر یا د پېرلو اپ څخه چې دا فرمایش یې لېږلی، نوي انلاین فرمایشونه نه منل کېږي، که څه هم بله شمېره وي؛ پیرودونکی دلیل نه ویني.",
  )
  val trustDeviceHelp: String get() = t(
    "سقفِ مشتریِ تازه برای همان مرورگر یا برنامهٔ خرید برداشته می‌شود؛ بررسیِ سفارشِ تکراری می‌ماند.",
    "The new-customer limit is lifted for that browser or shopping app; duplicate checks stay.",
    "د هماغه براوزر یا د پېرلو اپ لپاره د نوي پیرودونکي حد لرې کېږي؛ د تکراري فرمایش څارنه پاتې کېږي.",
  )
  fun entryDeviceBlocked(orderNo: Any?): String = t(
    if (orderNo == null) "دستگاهِ بسته" else "دستگاهِ بسته (از سفارشِ #${asIs(orderNo)})",
    "Blocked device" + (orderNo?.let { " (from order #$it)" } ?: ""),
    "بنده وسیله" + (orderNo?.let { " (له فرمایش #$it)" } ?: ""),
  )
  fun entryDeviceTrusted(orderNo: Any?): String = t(
    if (orderNo == null) "دستگاهِ مطمئن" else "دستگاهِ مطمئن (از سفارشِ #${asIs(orderNo)})",
    "Trusted device" + (orderNo?.let { " (from order #$it)" } ?: ""),
    "باوري وسیله" + (orderNo?.let { " (له فرمایش #$it)" } ?: ""),
  )

  val listsOnlyManagers: String get() = t(
    "این فهرست فقط برای صاحب و مدیر است و فقط وقتی «ریسکِ سفارش» روشن باشد.",
    "Only owners and managers can see this list, and only when order risk checks are on.",
    "دا لېست یوازې د خاوند او مدیر لپاره دی، او یوازې کله چې د فرمایش خطر څارنه روښانه وي.",
  )
}
