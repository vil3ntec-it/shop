package ir.vil3ntec.tohid.core.i18n

/**
 *  SEC-10 — «حذفِ دادهٔ این مشتری» در پروندهٔ مشتری (`ui/screens/CustomerErasurePanel.kt`، نسخهٔ ۳:
 *  `CustomerErasureV3`؛ منطق در `core/crm/CustomerErasure`).
 */
@Suppress("unused")
object StrErasure {

  val action: String get() = t("حذفِ دادهٔ این مشتری", "Erase this customer's data", "د دې پېرودونکي معلومات پاکول")
  val intro: String get() = t(
    "دادهٔ شخصیِ این مشتری در سفارش‌های آنلاین، گفت‌وگو و ویترینِ همین دکان پاک یا ناشناس می‌شود. این کار برگشت ندارد.",
    "This customer's personal data in this shop's online orders, chat and storefront is erased or anonymised. This cannot be undone.",
    "د دې پېرودونکي شخصي معلومات د همدې دوکان په آنلاین فرمایشونو، خبرو او ویټرین کې پاکېږي یا بې نومه کېږي. دا کار بېرته نه راګرځي.",
  )
  val previewing: String get() = t("در حالِ شمردن…", "Counting…", "د شمېرلو په حال کې…")
  val previewTitle: String get() = t("پیش‌نمایش — هنوز چیزی پاک نشده", "Preview — nothing erased yet", "مخکتنه — تر اوسه هېڅ نه دي پاک شوي")
  fun total(n: String): String = t("روی هم ${asIs(n)} ردیف", "$n rows in total", "ټول $n کرښې")
  val nothing: String get() = t(
    "از این مشتری در سرورِ این دکان چیزی برای پاک کردن نیست.",
    "There is nothing to erase for this customer on this shop's server.",
    "د دې پېرودونکي لپاره د دې دوکان په سرور کې د پاکولو لپاره څه نشته.",
  )
  fun openOrders(n: String): String = t(
    "${asIs(n)} سفارشِ بازِ این مشتری هنوز تمام نشده؛ پس از پایانشان دوباره بخواهید.",
    "$n open orders of this customer are not finished yet; ask again after they end.",
    "د دې پېرودونکي $n پرانیستي فرمایشونه لا نه دي بشپړ شوي؛ له پای وروسته بیا وغواړئ.",
  )
  val keptTitle: String get() = t("چه می‌ماند", "What stays", "څه پاتې کېږي")
  val keptFinance: String get() = t(
    "اسنادِ مالی (فروش، بازپرداخت، تسویهٔ پیک) می‌مانند ولی بی نام، شماره و نشانی — جمع‌ها عوض نمی‌شوند.",
    "Financial records (sales, refunds, courier settlements) stay, but without name, number or address — totals do not change.",
    "مالي اسناد (پلور، بېرته ورکړه، د پیک تصفیه) پاتې کېږي خو بې نوم، شمېرې او پتې — ټولګې نه بدلېږي.",
  )
  val keptDevice: String get() = t(
    "قرض‌دار و فروش‌های این مشتری در دفترِ دستگاه‌های دکان پاک نمی‌شوند (فرمانِ حذف به دستگاه‌ها نمی‌رود)؛ اگر لازم است، خودتان در برنامه حذفشان کنید.",
    "This customer's debtor record and sales in the shop devices' ledgers are not erased (no erase command reaches devices); delete them yourself in the app if needed.",
    "د دې پېرودونکي پوروړی او پلورونه د دوکان د وسیلو په دفتر کې نه پاکېږي (د پاکولو امر وسیلو ته نه ځي)؛ که اړتیا وي، پخپله یې په برنامه کې پاک کړئ.",
  )
  val keptOther: String get() = t(
    "فهرستِ سیاه، سابقهٔ عملیات و پشتیبان‌ها هم می‌مانند.",
    "Block lists, the activity log and backups also stay.",
    "تور لېست، د عملیاتو تاریخچه او بېک اپونه هم پاتې کېږي.",
  )
  val proceed: String get() = t("ادامه", "Continue", "دوام")
  val confirmTitle: String get() = t("تأییدِ حذف", "Confirm erasure", "د پاکولو تایید")
  val confirmBody: String get() = t(
    "برای تأیید، چهار رقمِ آخرِ شمارهٔ این مشتری را بنویسید.",
    "To confirm, type the last four digits of this customer's number.",
    "د تایید لپاره د دې پېرودونکي د شمېرې وروستي څلور رقمونه ولیکئ.",
  )
  val confirmLabel: String get() = t("چهار رقمِ آخر", "Last four digits", "وروستي څلور رقمونه")
  val reauthHint: String get() = t(
    "پس از زدنِ «حذف کن» رمز یا کدِ حسابتان دوباره خواسته می‌شود.",
    "After you tap Erase, your account password or code is asked again.",
    "د پاکولو له کېکاږلو وروسته ستاسو د حساب پټنوم یا کوډ بیا غوښتل کېږي.",
  )
  val eraseNow: String get() = t("حذف کن", "Erase", "پاک یې کړه")
  val reauthCancelled: String get() = t("تأییدِ هویت انجام نشد؛ چیزی پاک نشد.", "Identity was not confirmed; nothing was erased.", "هویت تایید نه شو؛ هېڅ پاک نه شول.")
  val erasing: String get() = t("در حالِ حذف…", "Erasing…", "د پاکولو په حال کې…")
  val doneTitle: String get() = t("حذف شد", "Erased", "پاک شول")
  fun done(n: String): String = t("${asIs(n)} ردیف پاک یا ناشناس شد.", "$n rows were erased or anonymised.", "$n کرښې پاکې یا بې نومه شوې.")
  val close: String get() = t("بستن", "Close", "بندول")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  fun count(label: String, n: String): String = t("${asIs(label)}: ${asIs(n)}", "$label: $n", "$label: $n")

  // شمارش‌ها — همان `COUNT_KEYS` ِ سرور
  fun label(key: String): String = when (key) {
    "orders" -> t("سفارشِ آنلاین", "Online orders", "آنلاین فرمایشونه")
    "orderLines" -> t("قلمِ سفارش", "Order lines", "د فرمایش توکي")
    "orderEvents" -> t("رویدادِ سفارش", "Order events", "د فرمایش پېښې")
    "deliveryEvents" -> t("رویدادِ پیک", "Courier events", "د پیک پېښې")
    "deliveryPhotos" -> t("عکسِ تحویل", "Delivery photos", "د سپارلو انځورونه")
    "pickPhotos" -> t("عکسِ بسته‌بندی", "Packing photos", "د بسته بندۍ انځورونه")
    "deliveryLocations" -> t("موقعیتِ زندهٔ پیک", "Live courier locations", "د پیک ژوندي ځایونه")
    "notifyOutbox" -> t("پیامِ مشتری", "Customer messages", "د پېرودونکي پیغامونه")
    "conversations" -> t("گفت‌وگو", "Chats", "خبرې")
    "messages" -> t("پیامِ گفت‌وگو", "Chat messages", "د خبرو پیغامونه")
    "chatMedia" -> t("پروندهٔ گفت‌وگو", "Chat files", "د خبرو فایلونه")
    "chatPins" -> t("سنجاقِ گفت‌وگو", "Chat pins", "د خبرو سنجاقونه")
    "chatReports" -> t("گزارشِ گفت‌وگو", "Chat reports", "د خبرو راپورونه")
    "carts" -> t("سبدِ خرید", "Carts", "د پېر ټوکرۍ")
    "favorites" -> t("پسندیده", "Favourites", "خوښې")
    "lists" -> t("فهرستِ خرید", "Shopping lists", "د پېرود لړونه")
    "areaRequests" -> t("درخواستِ ناحیه", "Area requests", "د سیمې غوښتنې")
    "alerts" -> t("«خبرم کن»", "Notify-me requests", "ما خبر کړه غوښتنې")
    "issues" -> t("گزارشِ مشکل", "Problem reports", "د ستونزې راپورونه")
    "issuePhotos" -> t("عکسِ گزارش", "Report photos", "د راپور انځورونه")
    "reviews" -> t("نظر", "Reviews", "نظرونه")
    "pushSubs" -> t("اعلانِ سفارش روی دستگاه", "Order notifications on devices", "پر وسیلو د فرمایش خبرتیاوې")
    "alertPushSubs" -> t("اعلانِ «خبرم کن» روی دستگاه", "Notify-me notifications on devices", "پر وسیلو د «خبر مې کړه» خبرتیاوې")
    "orderDevices" -> t("شناسهٔ دستگاهِ سفارش", "Order device identifiers", "د فرمایش د وسیلې پېژندنې")
    "addressMedia" -> t("عکسِ دروازه و یادداشتِ صوتیِ نشانی", "Gate photos and voice notes of addresses", "د پتې د دروازې انځورونه او غږیز یادښتونه")
    else -> t(asIs(key), key, key)
  }
}
