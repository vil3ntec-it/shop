package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.calendar.CivilDays

/**
 *  POS-25 — پیش‌فاکتور (قیمت‌دهی): سبدِ صندوق با قیمتِ همان لحظه، شماره و «اعتبار تا»، چاپ، و تبدیل به فروش.
 *
 *  ⛔ پیش‌فاکتور سندِ مالی نیست: نه فروش است، نه موجودی کم می‌کند، نه شماره‌ای از سریِ فاکتور می‌گیرد و نه به
 *  دفتر و همگام‌سازی می‌رود. «تبدیل» یعنی ردیف‌ها با همان قیمتِ پیش‌فاکتور به سبدِ خالیِ صندوق می‌روند و فروش
 *  از همان راهِ همیشگیِ صندوق ثبت می‌شود؛ وقتی فروشی با کلیدِ همان سبد نشست، پیش‌فاکتور «فروخته‌شده» است.
 *
 *  قاعده‌ها:
 *   • اعتبار [MIN_DAYS] تا [MAX_DAYS] روز؛ روزِ آخر هنوز معتبر است (`validUntil` = امروز + روزها).
 *   • پس از روزِ آخر «گذشته» — دیگر با قیمتِ پیش‌فاکتور به سبد نمی‌رود (قیمت فقط تا همان روز قول داده شده بود).
 *   • فروخته‌شده دوباره به سبد نمی‌رود؛ سبدِ پُر هم نه (چیزی از سبدِ جاری پاک نمی‌شود).
 *   • حداکثر [MAX_OPEN] پیش‌فاکتورِ نگه‌داشته روی دستگاه؛ شماره یکی بیش از بزرگ‌ترین شمارهٔ موجود.
 *
 *  کاتلینِ خالص (commonMain): بی ساعتِ دستگاه؛ «امروز» روزِ کاریِ صندوق است و از بیرون می‌آید.
 */
object Quote {
  const val DEFAULT_DAYS = 7
  const val MIN_DAYS = 1
  const val MAX_DAYS = 90
  const val MAX_OPEN = 100
  const val NOTE_MAX = 120

  enum class State { VALID, IN_CART, EXPIRED, SOLD }

  /** «اعتبار تا» برای پیش‌فاکتورِ امروز؛ روزهای بیرون از بازه یا تاریخِ نادرست ⇒ null */
  fun validUntil(today: String, days: Int): String? =
    if (days !in MIN_DAYS..MAX_DAYS) null else CivilDays.addDays(today, days)

  /** روزهای ماندهٔ اعتبار (۰ = همین امروز روزِ آخر است؛ منفی = گذشته)؛ تاریخِ نادرست ⇒ null */
  fun daysLeft(validUntil: String, today: String): Int? {
    val end = CivilDays.jdnOfIso(validUntil) ?: return null
    val now = CivilDays.jdnOfIso(today) ?: return null
    return end - now
  }

  /** وضعیتِ یک پیش‌فاکتور: فروخته‌شده بر همه جلو است؛ بعد «گذشته»؛ بعد «در سبد» */
  fun state(validUntil: String, today: String, inCart: Boolean, sold: Boolean): State {
    if (sold) return State.SOLD
    val left = daysLeft(validUntil, today)
    if (left == null || left < 0) return State.EXPIRED
    return if (inCart) State.IN_CART else State.VALID
  }

  fun nextNumber(used: Collection<Int>): Int = (used.maxOrNull() ?: 0) + 1

  /** چرا ساخته نمی‌شود: EMPTY، TOO_MANY، BAD_DAYS؛ null ⇒ ساخته می‌شود */
  fun createProblem(lines: Int, open: Int, today: String, days: Int): String? = when {
    lines <= 0 -> "EMPTY"
    open >= MAX_OPEN -> "TOO_MANY"
    validUntil(today, days) == null -> "BAD_DAYS"
    else -> null
  }

  /** چرا به سبد نمی‌رود: SOLD، EXPIRED، EMPTY، CART_BUSY؛ null ⇒ می‌رود (پیش‌فاکتورِ «در سبد» ِ رهاشده هم) */
  fun convertProblem(state: State, lines: Int, cartEmpty: Boolean): String? = when {
    state == State.SOLD -> "SOLD"
    state == State.EXPIRED -> "EXPIRED"
    lines <= 0 -> "EMPTY"
    !cartEmpty -> "CART_BUSY"
    else -> null
  }

  /** یادداشتِ پیش‌فاکتور: یک خط، حداکثر [NOTE_MAX]؛ خالی ⇒ null */
  fun note(text: String?): String? =
    text?.replace(Regex("\\s+"), " ")?.trim()?.take(NOTE_MAX)?.ifEmpty { null }
}
