package ir.vil3ntec.tohid.core.i18n

/**
 *  کادرِ بالای خانه (HOME-V3، انتخابِ صاحب‌کار ۱۴۰۵/۰۸/۱۳): «صندوق» و «گاوصندوق» با نمودار، بازه، آمارِ امروز و
 *  دکمهٔ «+». فارسی، انگلیسی و پشتو؛ دری = فارسی + لایهٔ `DariText`. هر مقدار درونِ متنِ فارسی از `asIs` می‌گذرد.
 */
@Suppress("unused")
object StrHero {

  val tillBalance: String get() = t("موجودیِ صندوق", "Till balance", "د صندوق موجودي")
  val safeBalance: String get() = t("موجودیِ گاوصندوق", "Safe balance", "د سېف موجودي")

  //  نشانِ همگام‌سازی کنارِ موجودی — همیشه متن + رنگ + نقطه
  val live: String get() = t("زنده", "Live", "ژوندی")
  val queued: String get() = t("در صف", "Queued", "په کتار کې")
  val offline: String get() = t("بی‌اتصال", "Offline", "بې اتصاله")
  val syncError: String get() = t("خطای همگام‌سازی", "Sync error", "د همغږۍ تېروتنه")
  val waitingServer: String get() = t("منتظرِ سرور", "Waiting for server", "د سرور په تمه")
  val localOnly: String get() = t("فقط این دستگاه", "This device only", "یوازې دا وسیله")

  //  بازه
  val rangeToday: String get() = t("امروز", "Today", "نن")
  val rangeWeek: String get() = t("هفته", "Week", "اونۍ")
  val rangeMonth: String get() = t("ماه", "Month", "میاشت")
  val salesWeek: String get() = t("فروشِ این هفته", "This week's sales", "د دې اونۍ پلور")
  val salesMonth: String get() = t("فروشِ این ماه", "This month's sales", "د دې میاشتې پلور")
  val safeToday: String get() = t("ماندهٔ امروز", "Balance today", "د نن پاتې")
  val safeWeek: String get() = t("ماندهٔ این هفته", "Balance this week", "د دې اونۍ پاتې")
  val safeMonth: String get() = t("ماندهٔ این ماه", "Balance this month", "د دې میاشتې پاتې")

  /** حرفِ روزهای هفته از شنبه تا جمعه، جدا با «,» */
  val weekLetters: String get() = t("ش,ی,د,س,چ,پ,ج", "Sa,Su,Mo,Tu,We,Th,Fr", "ش,ی,د,س,چ,پ,ج")

  val emptySales: String get() = t("هنوز فروشی در این بازه نیست", "No sales in this range yet", "په دې موده کې لا پلور نشته")
  val emptySafe: String get() = t("هنوز ردیفی در گاوصندوق نیست", "Nothing recorded in the safe yet", "په سېف کې لا هېڅ نه دي ثبت شوي")

  val safeIn: String get() = t("واریزِ امروز", "Deposited today", "د نن جمع")
  val safeOut: String get() = t("برداشتِ امروز", "Withdrawn today", "د نن ایستل")

  /** صندوقِ کورِ شاگرد (PAY-13): نقدِ موردِ انتظار دیده نمی‌شود */
  val blindHidden: String get() = t("در شمارشِ کور پنهان است", "Hidden in blind count", "په ړوند شمېرنه کې پټ دی")

  //  دکمهٔ «+» و برگه‌اش
  val addOrTake: String get() = t("اضافه یا برداشت", "Add or withdraw", "زیاتول یا ایستل")
  val add: String get() = t("اضافه", "Add", "زیاتول")
  val take: String get() = t("برداشت", "Withdraw", "ایستل")
  val tillSheet: String get() = t("صندوق: اضافه یا برداشت", "Till: add or withdraw", "صندوق: زیاتول یا ایستل")
  val safeSheet: String get() = t("گاوصندوق: اضافه یا برداشت", "Safe: add or withdraw", "سېف: زیاتول یا ایستل")
  val noteOptional: String get() = t("یادداشت (برای «دیگر» لازم)", "Note (required for “Other”)", "یادښت (د «بل» لپاره اړین)")
  fun nowIn(amount: Any): String = t("اکنون ${asIs(amount)} ؋", "Now $amount ؋", "اوس $amount ؋")
  fun takeMax(amount: Any): String = t("بیشترین برداشت: ${asIs(amount)} ؋", "At most $amount ؋", "تر ټولو ډېر: $amount ؋")

  //  نمایان/پنهان و TalkBack
  val showHero: String get() = t("نشان دادنِ صندوق", "Show the till card", "د صندوق کارت ښکاره کړئ")
  val hideHero: String get() = t("پنهان کردنِ صندوق", "Hide the till card", "د صندوق کارت پټ کړئ")
  val pullHint: String get() = t("برای صندوق پایین بکشید", "Pull down for the till", "د صندوق لپاره ښکته یې کش کړئ")
  val nextPage: String get() = t("صفحهٔ بعد", "Next page", "بله پاڼه")
  val prevPage: String get() = t("صفحهٔ پیش", "Previous page", "مخکېنۍ پاڼه")
  fun pageOf(i: Int, n: Int): String = t("صفحهٔ ${asIs(i)} از ${asIs(n)}", "Page $i of $n", "$i پاڼه له $n")
  fun chart(title: String, amount: Any): String = t("${asIs(title)}، اکنون ${asIs(amount)} ؋", "$title, now $amount ؋", "$title، اوس $amount ؋")
}
