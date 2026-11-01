package ir.vil3ntec.tohid.core.i18n

/**
 *  صفحه‌های پیک — اسکنِ کیسه‌ها (DLV-07)، «تحویل شد» نزدیکِ پین (DLV-17)، مسیرِ پیشنهادی (DLV-04) و امتیاز
 *  (DLV-18)؛ و «امتیازِ تحویل‌ها» برای صاحب و مدیر. `ui/courier/CourierChecksUi.kt` و `ui/screens/DeliveryRatings.kt`.
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrCourier {

  // اسکنِ کیسه‌ها (DLV-07)
  val bagsTitle: String get() = t("اسکنِ کیسه‌ها", "Scan the bags", "د کڅوړو سکن")
  fun bagsHint(bags: Int): String = t(
    "برچسبِ هر $bags کیسه را اسکن کنید؛ بعد «بسته را گرفتم».",
    "Scan the label of all $bags bags, then confirm the pickup.",
    "د ټولو $bags کڅوړو لیبل سکن کړئ؛ بیا «بسته مې واخیسته».",
  )
  val scanBag: String get() = t("اسکنِ برچسبِ کیسه", "Scan bag label", "د کڅوړې لیبل سکن کړئ")
  val add: String get() = t("افزودن", "Add", "زیاتول")
  fun bagOk(bag: Int, of: Int): String = t("کیسهٔ $bag از $of", "Bag $bag of $of", "کڅوړه $bag له $of څخه")
  fun missing(list: String): String = t("مانده: $list", "Missing: $list", "پاتې: $list")
  val wrongOrder: String get() = t("این کیسه مالِ سفارشِ دیگری است", "This bag belongs to another order", "دا کڅوړه د بل فرمایش ده")
  val mismatch: String get() = t(
    "برچسب با تعدادِ کیسه‌های بسته نمی‌خواند؛ از دکان برچسبِ تازه بخواهید",
    "The label does not match the packed bag count; ask the shop for a new label",
    "لیبل د بسته شویو کڅوړو له شمېر سره سمون نه خوري؛ له دوکانه نوی لیبل وغواړئ",
  )
  val pickedUp: String get() = t("بسته را گرفتم", "Picked up", "بسته مې واخیسته")

  // «تحویل شد» نزدیکِ پین (DLV-17)
  val pinTitle: String get() = t("نزدیکِ پینِ مشتری", "Near the customer's pin", "د پېرودونکي پن ته نږدې")
  val locating: String get() = t("در حالِ گرفتنِ موقعیت…", "Getting your location…", "ستاسو موقعیت اخیستل کېږي…")
  val noLocation: String get() = t(
    "موقعیت پیدا نشد؛ مکان‌یاب را روشن کنید و دوباره بزنید",
    "Location not found; turn on location and try again",
    "موقعیت ونه موندل شو؛ موقعیت‌یاب روښانه کړئ او بیا هڅه وکړئ",
  )
  fun tooFar(distanceM: Long, maxM: Int): String = t(
    "$distanceM متر از پینِ مشتری دورید؛ «تحویل شد» تا $maxM متر ثبت می‌شود. نزدیک‌تر بروید یا به دکان زنگ بزنید.",
    "You are $distanceM m from the customer's pin; delivery is recorded within $maxM m. Move closer or call the shop.",
    "تاسو د پېرودونکي له پن څخه $distanceM متره لرې یاست؛ «وسپارل شو» تر $maxM مترو پورې ثبتېږي. نږدې ورشئ یا دوکان ته زنګ ووهئ.",
  )
  val checkAgain: String get() = t("دوباره بسنج", "Check again", "بیا یې وګورئ")

  // مسیرِ پیشنهادی (DLV-04)
  val routeTitle: String get() = t("مسیرِ پیشنهادی", "Suggested route", "وړاندیز شوې لار")
  val routeHint: String get() = t(
    "ترتیبِ پیشنهادی از جای شما — فقط پیشنهاد است و کاری عوض نمی‌شود.",
    "Suggested order from where you are — only a suggestion; nothing changes.",
    "ستاسو له ځایه وړاندیز شوی ترتیب — یوازې وړاندیز دی؛ هېڅ نه بدلېږي.",
  )
  val routeNoStart: String get() = t(
    "موقعیتِ شما نیامد؛ ترتیب از یکی از نشانی‌هاست.",
    "Your location is unavailable; the order starts from one of the stops.",
    "ستاسو موقعیت ونه رسېد؛ ترتیب له یوې پتې پیلېږي.",
  )
  fun stop(seq: Int, landmark: String): String = t("ایستگاهِ $seq: $landmark", "Stop $seq: $landmark", "تمځای $seq: $landmark")
  fun meters(m: Long): String = t("$m متر", "$m m", "$m متره")
  fun km(km: String): String = t("$km کیلومتر", "$km km", "$km کیلومتره")
  fun total(km: String): String = t("همهٔ راه: $km کیلومتر", "Whole route: $km km", "ټوله لار: $km کیلومتره")
  val noPin: String get() = t("بی پین — آخرِ راه", "No pin — last", "بې پن — د لارې په پای کې")
  val noLandmark: String get() = t("بی نشانه", "No landmark", "بې نښه")
  val routeEmpty: String get() = t("مأموریتِ پذیرفته‌ای برای مسیر نیست", "No accepted missions for a route", "د لارې لپاره منل شوې دنده نشته")

  // امتیاز (DLV-18)
  val rate: String get() = t("امتیاز به این تحویل", "Rate this delivery", "دې سپارنې ته نمره ورکړئ")
  val rateTitle: String get() = t("تحویل چطور بود؟", "How was the delivery?", "سپارنه څنګه وه؟")
  fun stars(n: Int): String = t("$n ستاره", "$n stars", "$n ستوري")
  val send: String get() = t("ثبت", "Save", "ثبتول")
  val rated: String get() = t("امتیاز ثبت شد؛ سپاس", "Rating saved; thank you", "نمره ثبت شوه؛ مننه")
  val rateOnline: String get() = t("امتیاز فقط با اینترنت فرستاده می‌شود", "Ratings need the internet", "نمره یوازې له انټرنټ سره لېږل کېږي")
  val rateClosed: String get() = t("مهلتِ امتیاز (۷ روز پس از تحویل) گذشته است", "The rating window (7 days after delivery) has passed", "د نمرې وخت (له سپارلو ۷ ورځې وروسته) تېر شوی")

  // برچسب‌های پیک به تحویل
  val tagEasyToFind: String get() = t("نشانی آسان پیدا شد", "Easy to find", "پته په اسانۍ وموندل شوه")
  val tagReady: String get() = t("مشتری آماده بود", "Customer was ready", "پېرودونکی چمتو و")
  val tagHardToFind: String get() = t("نشانی سخت پیدا شد", "Hard to find", "پته په سختۍ وموندل شوه")
  val tagNotReady: String get() = t("مشتری آماده نبود", "Customer was not ready", "پېرودونکی چمتو نه و")
  val tagWrongPin: String get() = t("پین جای درست نبود", "The pin was in the wrong place", "پن په سم ځای کې نه و")

  // برچسب‌های مشتری به پیک
  val tagOnTime: String get() = t("به‌موقع", "On time", "پر وخت")
  val tagPolite: String get() = t("مؤدب", "Polite", "با ادبه")
  val tagCareful: String get() = t("با دقت", "Careful", "په پاملرنه")
  val tagLate: String get() = t("دیر", "Late", "ناوخته")
  val tagRude: String get() = t("بی‌ادب", "Rude", "بې ادبه")
  val tagDamaged: String get() = t("کالا آسیب دید", "Items damaged", "توکي زیانمن شول")

  // صاحب و مدیر: «امتیازِ تحویل‌ها»
  val ratingsButton: String get() = t("امتیازِ تحویل‌ها", "Delivery ratings", "د سپارنو نمرې")
  val ratingsNote: String get() = t(
    "۹۰ روزِ گذشته. فقط صاحب و مدیر می‌بینند؛ پیک و مشتری امتیازِ یکدیگر را نمی‌بینند.",
    "Last 90 days. Only the owner and manager see this; couriers and customers do not see each other's ratings.",
    "تېرې ۹۰ ورځې. یوازې خاوند او مدیر یې ویني؛ سپارونکي او پېرودونکي د یو بل نمرې نه ویني.",
  )
  fun fromCustomers(avg: String, n: Int): String = t(
    "مشتری‌ها به پیک: $avg از 5 ($n امتیاز)", "Customers to courier: $avg of 5 ($n ratings)", "پېرودونکي سپارونکي ته: $avg له 5 ($n نمرې)",
  )
  fun byCourier(avg: String, n: Int): String = t(
    "پیک به تحویل‌ها: $avg از 5 ($n امتیاز)", "Courier on deliveries: $avg of 5 ($n ratings)", "سپارونکی سپارنو ته: $avg له 5 ($n نمرې)",
  )
  fun tagCount(tag: String, n: Int): String = t("$tag: $n", "$tag: $n", "$tag: $n")
  val ratingsEmpty: String get() = t("در ۹۰ روزِ گذشته امتیازی نیست", "No ratings in the last 90 days", "په تېرو ۹۰ ورځو کې نمره نشته")
  val ratingsFailed: String get() = t("امتیازها نیامد", "Ratings did not load", "نمرې رانغلې")
  fun recent(orderNo: Long, stars: Int): String = t("سفارش #$orderNo · $stars ستاره", "Order #$orderNo · $stars stars", "فرمایش #$orderNo · $stars ستوري")
  val raterCustomer: String get() = t("مشتری", "Customer", "پېرودونکی")
  val raterCourier: String get() = t("پیک", "Courier", "سپارونکی")
}
