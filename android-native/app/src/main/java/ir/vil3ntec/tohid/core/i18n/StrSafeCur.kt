package ir.vil3ntec.tohid.core.i18n

/**
 *  ارزهای گاوصندوق (PAY-18، خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴): کادرِ «ارز» کنارِ مبلغ، «+ ارزِ دیگر»، ماندهٔ هر ارز و تراشهٔ
 *  ارزِ نمودار. قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/safe/SafeCurrency` و `data/SafeCurrencies`. کدِ ارز (`USD`) و عدد از
 *  بیرون می‌آیند و همان می‌مانند. نامی که دکان برای ارزِ تازه می‌نویسد داده است و ترجمه نمی‌شود.
 */
@Suppress("unused")
object StrSafeCur {
  /** نامِ ارزهای همیشه‌آماده (دالر، کلدار و ریال همان `StrFx`) */
  val afn: String get() = t("افغانی (AFN)", "Afghani (AFN)", "افغانۍ (AFN)")
  val eur: String get() = t("یورو (EUR)", "Euro (EUR)", "یورو (EUR)")

  val currency: String get() = t("ارز", "Currency", "اسعار")
  val pick: String get() = t("انتخابِ ارز", "Choose currency", "اسعار وټاکئ")
  fun chartCurrency(name: String): String = t("ارزِ نمودار: ${asIs(name)}", "Chart currency: $name", "د ګراف اسعار: $name")
  fun amountIn(code: String): String = t("مبلغ (${asIs(code)})", "Amount ($code)", "اندازه ($code)")
  fun countedIn(code: String): String = t("مبلغِ شمرده (${asIs(code)})", "Counted amount ($code)", "شمېرل شوې اندازه ($code)")
  val balances: String get() = t("مانده به تفکیکِ ارز", "Balance by currency", "د اسعارو له مخې پاتې")
  val varianceAnyReason: String get() = t(
    "دلیلِ اختلاف (در این ارز برای هر اختلاف لازم)",
    "Reason for the difference (needed for any difference in this currency)",
    "د توپیر دلیل (په دې اسعارو کې د هر توپیر لپاره اړین)",
  )
  fun varianceNeedsReason(over: Boolean, amount: String): String = if (over) t(
    "اختلافِ شمارش اضافه ${asIs(amount)} است — در این ارز هر اختلافی دلیل می‌خواهد",
    "The count is over by $amount — any difference in this currency needs a reason",
    "شمېرنه $amount زیاته ده — په دې اسعارو کې هر توپیر دلیل غواړي",
  ) else t(
    "اختلافِ شمارش کسری ${asIs(amount)} است — در این ارز هر اختلافی دلیل می‌خواهد",
    "The count is short by $amount — any difference in this currency needs a reason",
    "شمېرنه $amount کمه ده — په دې اسعارو کې هر توپیر دلیل غواړي",
  )
  val badCurrency: String get() = t(
    "ارزِ این ردیف درست نیست؛ انتقال با دخل فقط به افغانی است",
    "This currency is not valid; drawer transfers are in AFN only",
    "د دې کرښې اسعار سم نه دي؛ له دخل سره لېږد یوازې په افغانیو دی",
  )

  // ── «+ ارزِ دیگر» ──
  val addCurrency: String get() = t("+ ارزِ دیگر", "+ Another currency", "+ بل اسعار")
  val addTitle: String get() = t("ارزِ تازه برای گاوصندوق", "New currency for the safe", "د تجوري لپاره نوی اسعار")
  val code: String get() = t("کدِ ارز (سه حرفِ لاتین، مثلِ GBP)", "Currency code (three Latin letters, e.g. GBP)", "د اسعارو کوډ (درې لاتیني توري، لکه GBP)")
  val name: String get() = t("نامِ ارز (مثلِ پوند)", "Currency name (e.g. Pound)", "د اسعارو نوم (لکه پونډ)")
  val addNote: String get() = t(
    "هر ارز ماندهٔ جدای خودش را دارد و هرگز به افغانی تبدیل یا با آن جمع نمی‌شود. این فهرست برای همهٔ دستگاه‌های دکان است؛ دستگاه‌ها را به‌روز کنید تا ارز را بشناسند.",
    "Each currency keeps its own balance and is never converted to or added to AFN. This list is shared by every device of the shop; update the devices so they know the currency.",
    "هر اسعار خپل جلا پاتې لري او هېڅکله په افغانیو نه بدلېږي او نه ورسره جمع کېږي. دا لړ د دوکان د ټولو وسیلو لپاره دی؛ وسیلې تازه کړئ چې اسعار وپېژني.",
  )
  val add: String get() = t("افزودن", "Add", "زیاتول")
  val badCode: String get() = t("کد باید سه حرفِ لاتین باشد", "The code must be three Latin letters", "کوډ باید درې لاتیني توري وي")
  val duplicate: String get() = t("این ارز پیش‌تر در فهرست هست", "This currency is already listed", "دا اسعار مخکې په لړ کې شته")
  val nameRequired: String get() = t("نامِ ارز را بنویسید", "Enter the currency name", "د اسعارو نوم ولیکئ")
  val nameTooLong: String get() = t("نامِ ارز بیش از اندازه بلند است", "The currency name is too long", "د اسعارو نوم ډېر اوږد دی")
  fun tooMany(max: Int): String = t("بیش از ${asIs(max)} ارزِ تازه نمی‌شود", "No more than $max added currencies", "له $max زیات نوي اسعار نه شي")
}
