package ir.vil3ntec.tohid.core.i18n

/**
 *  ورود و خروجِ کارمند و پورسانتِ فروش (`StaffTimeCard`، STF-07، STF-08) — کارتِ «مدیریت».
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ قاعده‌ها سرِ `I18n.kt`. ترجمه‌های نامطمئن در
 *  `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 *  ⛔ نامِ کارمند و فروشنده و مبلغ از دفتر و سرور می‌آیند و همان‌طور نشان داده می‌شوند.
 */
@Suppress("unused")
object StrStaffTime {
  val title: String get() = t("ورود و خروج", "Clock in and out", "ننوتل او وتل")
  fun inSince(time: String): String = t("سرِ کار هستید از $time", "On duty since $time", "له $time راهیسې په دنده یاست")
  val out: String get() = t("بیرون هستید.", "You are off duty.", "تاسو له دندې بهر یاست.")
  val clockIn: String get() = t("ثبتِ ورود", "Clock in", "ننوتل ثبت کړئ")
  val clockOut: String get() = t("ثبتِ خروج", "Clock out", "وتل ثبت کړئ")
  fun queued(n: Any): String = t(
    "$n زدن در صف است و با وصل شدنِ اینترنت فرستاده می‌شود.",
    "$n ${pl(n, "punch is", "punches are")} queued and will be sent once the internet is connected.",
    "$n ثبت په لیکه کې دي او د انټرنېټ په وصلېدو لېږل کېږي.",
  )
  val sameKind: String get() = t("این زدن پیش‌تر ثبت شده است.", "This punch is already recorded.", "دا ثبت مخکې شوی دی.")
  val beforeLast: String get() = t(
    "ساعتِ این دستگاه از آخرین زدن عقب‌تر است؛ ساعتِ دستگاه را درست کنید.",
    "This device's clock is behind the last punch; please fix the device clock.",
    "د دې وسیلې ساعت له وروستي ثبت څخه شاته دی؛ د وسیلې ساعت سم کړئ.",
  )
  val team: String get() = t("امروزِ کارمندان", "Staff today", "د کارکوونکو نن ورځ")
  val noPunches: String get() = t("امروز کسی نزده است.", "Nobody has clocked in today.", "نن هېچا ثبت نه دی کړی.")
  fun hoursMinutes(h: Any, m: Any): String = t("$h ساعت و $m دقیقه", "${h}h ${m}m", "$h ساعته او $m دقیقې")
  val stillIn: String get() = t("هنوز سرِ کار", "still on duty", "لا هم په دنده")
  fun issues(n: Any): String = t(
    "$n نوبتِ ناجور (بی خروج، بی ورود یا بیش از سقف) شمرده نشد؛ مدیر با زدنِ تازه درستش کند.",
    "$n ${pl(n, "irregular shift was", "irregular shifts were")} not counted (no clock-out, no clock-in, or over the cap); a manager should fix it with a new punch.",
    "$n ناسم ${pl(n, "نوبت", "نوبتونه")} (بې وتلو، بې ننوتلو یا له حد څخه زیات) ونه شمېرل شول؛ مدیر دې په نوي ثبت سره سم کړي.",
  )
  fun clockOutFor(name: String): String = t("ثبتِ خروجِ $name", "Clock out $name", "د $name وتل ثبت کړئ")
  val commission: String get() = t("پورسانتِ این ماه", "Commission this month", "د دې میاشتې کمېشن")
  val noSales: String get() = t("فروشی ثبت نشده.", "No sales recorded.", "هېڅ پلور نه دی ثبت شوی.")
  fun unassigned(amount: String): String = t("بی فروشنده: $amount", "No seller: $amount", "بې پلورونکي: $amount")
  val rates: String get() = t("نرخ‌های پورسانت", "Commission rates", "د کمېشن نرخونه")
  val defaultRate: String get() = t("نرخِ همه (٪)", "Rate for everyone (%)", "د ټولو نرخ (٪)")
  fun sellerRate(name: String): String = t("نرخِ $name (٪)", "Rate for $name (%)", "د $name نرخ (٪)")
  val save: String get() = t("ذخیرهٔ نرخ‌ها", "Save rates", "نرخونه خوندي کړئ")
  val saved: String get() = t("نرخ‌ها ذخیره شد.", "Rates saved.", "نرخونه خوندي شول.")
  val rateInvalid: String get() = t(
    "نرخ باید عددی میانِ ۰ و ۱۰۰ با حداکثر دو رقمِ اعشار باشد.",
    "A rate must be a number from 0 to 100 with at most two decimals.",
    "نرخ باید له ۰ تر ۱۰۰ پورې شمېره وي، تر دوو اعشاري رقمونو پورې.",
  )
  val stale: String get() = t(
    "نرخ‌ها در این میان روی دستگاهِ دیگری عوض شدند؛ دوباره باز کنید.",
    "The rates were changed on another device meanwhile; please reopen.",
    "نرخونه په دې منځ کې په بله وسیله بدل شول؛ بیا یې پرانیزئ.",
  )
}
