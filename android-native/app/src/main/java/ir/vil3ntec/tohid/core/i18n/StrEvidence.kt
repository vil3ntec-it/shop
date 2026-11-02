package ir.vil3ntec.tohid.core.i18n

/**
 *  RSK-08 — «پروندهٔ مدرک» در جزئیاتِ سفارشِ آنلاین (`ui/screens/OrderEvidencePanel.kt`، نسخهٔ ۳: `OrderEvidenceV3`؛
 *  منطق در `core/orders/OrderEvidence`). قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrEvidence {

  val entry: String get() = t("پروندهٔ مدرک", "Evidence file", "د شواهدو دوسیه")
  fun title(orderNo: String): String = t("پروندهٔ مدرکِ سفارش #$orderNo", "Evidence file of order #$orderNo", "د فرمایش #$orderNo د شواهدو دوسیه")
  fun generated(at: String): String = t("ساخته‌شده: $at", "Generated: $at", "جوړ شوی: $at")
  val print: String get() = t("چاپ", "Print", "چاپ")
  val reload: String get() = t("تازه کردن", "Refresh", "تازه کول")
  val close: String get() = t("بستن", "Close", "بندول")
  val loadFailed: String get() = t("پرونده نیامد", "The file did not load", "دوسیه رانغله")
  val refused: String get() = t(
    "پروندهٔ اختلاف برای این دکان روشن نیست یا اجازه‌اش را ندارید.",
    "The dispute file is not enabled for this shop, or you are not allowed to see it.",
    "د شخړې دوسیه د دې دوکان لپاره فعاله نه ده، یا تاسو اجازه نه لرئ.",
  )
  val readOnlyNote: String get() = t(
    "فقط آن‌چه امروز ثبت است؛ موقعیتِ خامِ پیک نگه داشته نمی‌شود و شمارهٔ مشتری پوشیده است.",
    "Only what is already recorded; raw courier location is not kept and the customer number is masked.",
    "یوازې هغه چې ثبت شوي؛ د پیک خام ځای نه ساتل کېږي او د پېرودونکي شمېره پټه ده.",
  )

  // اختلاف
  val sDisputes: String get() = t("اختلاف", "Dispute", "شخړه")
  val noDispute: String get() = t("اختلافِ بازی برای این سفارش نیست.", "There is no open dispute for this order.", "د دې فرمایش لپاره پرانیستې شخړه نشته.")
  val appendOnly: String get() = t(
    "اختلاف فقط افزوده می‌شود: هیچ ردیفی ویرایش یا پاک نمی‌شود.",
    "Disputes are append-only: no entry is edited or deleted.",
    "شخړه یوازې زیاتېږي: هېڅ کرښه نه سمېږي او نه پاکېږي.",
  )
  val openDispute: String get() = t("باز کردنِ اختلاف", "Open a dispute", "شخړه پرانیستل")
  val addNote: String get() = t("یادداشت", "Add a note", "یادښت")
  val resolve: String get() = t("بستن با نتیجه", "Resolve", "د پایلې سره بندول")
  val kindLabel: String get() = t("نوعِ اختلاف", "Dispute type", "د شخړې ډول")
  val outcomeLabel: String get() = t("نتیجه", "Outcome", "پایله")
  val reasonLabel: String get() = t("دلیل (۳ تا ۱۰۰۰ نویسه)", "Reason (3 to 1000 characters)", "دلیل (۳ تر ۱۰۰۰ توري)")
  val noteLabel: String get() = t("متنِ یادداشت (۳ تا ۱۰۰۰ نویسه)", "Note (3 to 1000 characters)", "د یادښت متن (۳ تر ۱۰۰۰ توري)")
  val resolveLabel: String get() = t("شرحِ نتیجه (۳ تا ۱۰۰۰ نویسه)", "Outcome details (3 to 1000 characters)", "د پایلې شرح (۳ تر ۱۰۰۰ توري)")
  val send: String get() = t("ثبت", "Save", "ثبت")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val openedOk: String get() = t("اختلاف باز شد", "Dispute opened", "شخړه پرانیستل شوه")
  val notedOk: String get() = t("یادداشت ثبت شد", "Note saved", "یادښت ثبت شو")
  val resolvedOk: String get() = t("اختلاف بسته شد", "Dispute resolved", "شخړه بنده شوه")
  val stOpen: String get() = t("باز", "Open", "پرانیستې")
  val stResolved: String get() = t("بسته‌شده", "Resolved", "بنده شوې")
  fun head(kind: String, status: String): String = t("$kind — $status", "$kind — $status", "$kind — $status")
  fun openedBy(who: String, at: String): String = t("باز شد به دستِ $who · $at", "Opened by $who · $at", "د $who لخوا پرانیستل شوه · $at")
  fun noteLine(who: String, at: String, text: String): String = t("$who · $at: $text", "$who · $at: $text", "$who · $at: $text")
  fun resolution(outcome: String, who: String, text: String): String =
    t("نتیجه: $outcome ($who) — $text", "Outcome: $outcome ($who) — $text", "پایله: $outcome ($who) — $text")
  fun past(n: Int): String = t("اختلاف‌های بسته ($n)", "Resolved disputes ($n)", "بندې شوې شخړې ($n)")

  // نوع — همان `KINDS` ِ سرور
  val kNotDelivered: String get() = t("نرسید", "Not delivered", "ونه رسېد")
  val kMissing: String get() = t("کالایی کم بود", "An item was missing", "یو توکی کم و")
  val kWrong: String get() = t("کالای اشتباه", "Wrong item", "ناسم توکی")
  val kDamaged: String get() = t("خراب یا آسیب‌دیده", "Broken or damaged", "مات یا زیانمن")
  val kCash: String get() = t("اختلافِ پولِ نقد", "Cash disagreement", "د نغدو پیسو شخړه")
  val kLate: String get() = t("دیر رسید", "Arrived late", "ناوخته ورسید")
  val kOther: String get() = t("دیگر", "Other", "بل")

  // نتیجه — همان `OUTCOMES` ِ سرور
  val oCustomer: String get() = t("به سودِ مشتری", "In the customer's favour", "د پېرودونکي په ګټه")
  val oShop: String get() = t("به سودِ دکان", "In the shop's favour", "د دوکان په ګټه")
  val oSettled: String get() = t("توافق شد", "Settled", "جوړجاړی وشو")
  val oWithdrawn: String get() = t("پس گرفته شد", "Withdrawn", "بېرته واخیستل شوه")

  // سفارش
  val sOrder: String get() = t("سفارش", "Order", "فرمایش")
  fun orderLine(status: String, total: String, items: String): String =
    t("$status · $total · $items قلم", "$status · $total · $items items", "$status · $total · $items توکي")
  fun customer(name: String, phone: String): String = t("مشتری: $name · $phone", "Customer: $name · $phone", "پېرودونکی: $name · $phone")
  fun address(area: String, landmark: String): String = t("نشانی: $area · $landmark", "Address: $area · $landmark", "پته: $area · $landmark")
  val pinYes: String get() = t("پینِ نقشه دارد", "Has a map pin", "د نقشې پن لري")
  val pinNo: String get() = t("پینِ نقشه ندارد", "No map pin", "د نقشې پن نه لري")
  val testOrder: String get() = t("سفارشِ آزمایشی", "Test order", "ازمایښتي فرمایش")

  // خطِ زمانی
  fun sTimeline(n: Int): String = t("خطِ زمانی ($n)", "Timeline ($n)", "مهالوېش ($n)")
  val created: String get() = t("ثبت شد", "Placed", "ثبت شو")
  val noteEvent: String get() = t("یادداشت", "Note", "یادښت")
  fun step(at: String, what: String): String = t("$at · $what", "$at · $what", "$at · $what")
  fun by(who: String): String = t("به دستِ $who", "by $who", "د $who لخوا")

  // تحویل
  fun sDelivery(n: Int): String = t("تحویل و پیک ($n مأموریت)", "Delivery and courier ($n runs)", "سپارل او پیک ($n ماموریتونه)")
  val noDelivery: String get() = t("مأموریتِ پیکی ثبت نشده.", "No courier run is recorded.", "د پیک ماموریت نه دی ثبت.")
  fun attempt(n: String, courier: String, status: String): String =
    t("تلاشِ $n — $courier · $status", "Attempt $n — $courier · $status", "هڅه $n — $courier · $status")
  fun cash(expected: String, collected: String): String =
    t("نقدِ موردِ انتظار $expected · گرفته‌شده $collected", "Cash expected $expected · collected $collected", "تمه شوې نغدې $expected · اخیستل شوې $collected")
  val confCode: String get() = t("تأیید با کدِ تحویل", "Confirmed with the delivery code", "د سپارلو په کوډ تایید")
  val confManual: String get() = t("تأییدِ دستیِ پیک", "Confirmed manually by the courier", "د پیک لاسي تایید")
  val confNone: String get() = t("تأییدِ تحویل ثبت نشده", "No delivery confirmation recorded", "د سپارلو تایید نه دی ثبت")
  fun photos(n: Int): String = t("عکسِ تحویل: $n", "Delivery photos: $n", "د سپارلو انځورونه: $n")
  val photosHidden: String get() = t("عکسِ تحویل: اجازهٔ دیدن ندارید", "Delivery photos: you are not allowed to see them", "د سپارلو انځورونه: د لیدو اجازه نه لرئ")
  fun courierEvents(n: Int): String = t("رویدادِ پیک: $n", "Courier events: $n", "د پیک پېښې: $n")
  val noSignature: String get() = t("امضای مشتری در هیچ مرحله گرفته نمی‌شود.", "No customer signature is collected at any step.", "د پېرودونکي لاسلیک په هېڅ پړاو کې نه اخیستل کېږي.")

  // موقعیت و ساعت
  val sLocation: String get() = t("فاصله تا پین", "Distance to the pin", "تر پن واټن")
  fun distance(meters: String, at: String): String = t("$meters متر · $at", "$meters m · $at", "$meters متره · $at")
  val noDistance: String get() = t("فاصله‌ای سنجیده نشده.", "No distance was measured.", "هېڅ واټن نه دی اندازه شوی.")
  val sClock: String get() = t("ساعتِ دستگاهِ پیک", "Courier device clock", "د پیک د وسیلې ساعت")
  fun clock(samples: String, minutes: String, warnings: String): String = t(
    "$samples نمونه · بیشترین اختلاف $minutes دقیقه · $warnings هشدار",
    "$samples samples · largest gap $minutes min · $warnings warnings",
    "$samples بېلګې · تر ټولو لوی توپیر $minutes دقیقې · $warnings خبرتیاوې",
  )

  // اسکن
  val sScans: String get() = t("جمع‌آوری و اسکن", "Picking and scans", "راټولول او سکن")
  val noPick: String get() = t("جمع‌آوری ثبت نشده.", "No picking is recorded.", "راټولول نه دي ثبت.")
  fun pick(status: String, bags: String, slot: String): String =
    t("وضعیت: $status · کیسه: $bags · قفسه: $slot", "Status: $status · bags: $bags · shelf: $slot", "حالت: $status · کڅوړې: $bags · المارۍ: $slot")
  fun pickEvents(n: String, notFound: String): String =
    t("رویدادِ جمع‌آوری: $n (پیدا نشد: $notFound)", "Picking events: $n (not found: $notFound)", "د راټولولو پېښې: $n (ونه موندل شو: $notFound)")
  fun shelfScans(n: Int): String = t("اسکنِ قفسه: $n", "Shelf scans: $n", "د المارۍ سکنونه: $n")
  fun bagsAtPickup(list: String): String =
    t("کیسه‌های اسکن‌شده هنگامِ تحویل به پیک: $list", "Bags scanned at courier pickup: $list", "پیک ته د سپارلو پر وخت سکن شوې کڅوړې: $list")
  fun pickPhotos(n: Int): String = t("عکسِ بسته‌بندی: $n", "Packing photos: $n", "د بسته بندۍ انځورونه: $n")
  val consentYes: String get() = t("مشتری با عکسِ تحویل موافق است", "The customer agreed to delivery photos", "پېرودونکی د سپارلو له انځور سره موافق دی")
  val consentNo: String get() = t("مشتری با عکسِ تحویل موافق نیست", "The customer did not agree to delivery photos", "پېرودونکی د سپارلو له انځور سره موافق نه دی")

  // گزارش و تماس
  fun sIssues(n: Int): String = t("گزارشِ مشتری ($n)", "Customer reports ($n)", "د پېرودونکي راپورونه ($n)")
  fun issue(kind: String, status: String, photos: String): String =
    t("$kind · $status · عکس: $photos", "$kind · $status · photos: $photos", "$kind · $status · انځورونه: $photos")
  val sContacts: String get() = t("تماس و گفت‌وگو", "Calls and chat", "اړیکې او خبرې")
  fun contacts(calls: String, chats: String, fromCustomer: String, fromStaff: String): String = t(
    "تماسِ پیک: $calls · گفت‌وگو: $chats (پیامِ مشتری $fromCustomer، پیامِ دکان $fromStaff)",
    "Courier calls: $calls · chats: $chats (customer messages $fromCustomer, shop messages $fromStaff)",
    "د پیک زنګونه: $calls · خبرې: $chats (د پېرودونکي پیغامونه $fromCustomer، د دوکان پیغامونه $fromStaff)",
  )
  fun truncated(list: String): String = t("فهرستِ بلند بریده شد: $list", "Long list was cut: $list", "اوږد لېست لنډ شو: $list")

  // چاپ
  val colSection: String get() = t("بخش", "Section", "برخه")
  val colDetail: String get() = t("شرح", "Detail", "شرح")
}
