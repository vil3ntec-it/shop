package ir.vil3ntec.tohid.core.calendar

/**
 *  پایانِ روزِ کاریِ دکان — LOC-03، «روزِ کاری که از نیمه‌شب می‌گذرد».
 *
 *  دکانی که تا ۲ بامداد باز است، فروشِ ۱:۳۰ را به نامِ دیروز می‌خواهد تا یک
 *  شبِ کاری در «فروشِ امروز»، شیفت و گزارش دو روز نشود. پایانِ روز یکی از
 *  [CHOICES] است؛ ۰ یعنی نیمه‌شب، یعنی رفتارِ امروز (پیش‌فرض، خاموش).
 *
 *  قاعده همان `BusinessDay.businessDate` است (دوقلوی `license/calendar-core.js`):
 *  ساعتِ دیواریِ پیش از پایان ⇒ دیروز. این‌جا فقط «چند روز عقب» حساب می‌شود تا
 *  هر سکو تاریخ را با تقویم و منطقهٔ زمانیِ خودش بسازد؛ هم‌سنجی با
 *  `BusinessDay` در `ShopDayParityTest` (jvmTest) است.
 */
object ShopDay {

  /** نیمه‌شب: هیچ تاریخی جابه‌جا نمی‌شود */
  const val OFF: Int = 0

  /** ساعت‌های پایانِ روزِ کاری پس از نیمه‌شب؛ ۰ = نیمه‌شب (پیش‌فرض) */
  val CHOICES: List<Int> = listOf(0, 1, 2, 3, 4)

  /** عددِ ذخیره‌شدهٔ ناشناخته ⇒ نیمه‌شب، نه جابه‌جاییِ ناخواستهٔ تاریخِ فروش */
  fun clamp(hours: Int): Int = if (hours in CHOICES) hours else OFF

  /** دقیقه از نیمه‌شب — همان `dayStartMinutes` ِ `BusinessDay` و `ReportClock.fixed` */
  fun dayStartMinutes(hours: Int): Int = clamp(hours) * 60

  /**
   *  چند روز از تاریخِ تقویمی کم شود: −۱ وقتی ساعتِ دیواری (دقیقه از نیمه‌شب)
   *  پیش از پایانِ روزِ کاری است، وگرنه ۰. دقیقهٔ بیرون از شبانه‌روز ⇒ ۰.
   */
  fun dayShift(minuteOfDay: Int, hours: Int): Int =
    if (minuteOfDay >= 0 && minuteOfDay < dayStartMinutes(hours)) -1 else 0

  /** برچسبِ بی‌زبانِ گزینه: `00:00` … `04:00` (رقمِ فارسی را فونت می‌کشد) */
  fun label(hours: Int): String = clamp(hours).toString().padStart(2, '0') + ":00"
}
