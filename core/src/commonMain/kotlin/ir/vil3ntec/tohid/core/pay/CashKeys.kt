package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.MoneyInput

/**
 *  پنلِ پرداخت: دکمه و کلیدِ اسکناس (PAY-03) و نگهبانِ Enter (PAY-04) — جدولِ «پنلِ پرداخت» در بخشِ ۵.۲ِ مشخصات.
 *
 *  کاتلینِ خالص در commonMain تا گوشی، کامپیوتر و آیفون یک حساب داشته باشند. هر مبلغ پول است (۱/۱۰۰ افغانی).
 *  ⛔ [NOTES] همان `Pay.NOTES` ِ هستهٔ پرداخت است (`CashKeysParityTest` در jvmTest). هستهٔ پرداخت هنوز در
 *  jvmMain است و commonMain به آن دسترسی ندارد؛ برای همین فهرست این‌جا نوشته شده و آزمون برابری را می‌سنجد.
 */
object CashKeys {

  /** اسکناس‌های ۱۰، ۲۰، ۵۰، ۱۰۰، ۵۰۰ و ۱۰۰۰ افغانی به پول — به ترتیبِ Shift+1 تا Shift+6 (کدِ فیزیکی) */
  val NOTES: List<Long> = listOf(1_000L, 2_000L, 5_000L, 10_000L, 50_000L, 100_000L)

  /** Enter تا این مدت پس از باز شدنِ پنل کاری نمی‌کند: Enterِ بارکدخوان یا دستِ جامانده فاکتور را نمی‌بندد */
  const val ENTER_GUARD_MS: Long = 400

  /** همان `MAX_EXACT` ِ Money.kt: بیرون از ۲^۵۳−۱ پول عددِ دقیقی نیست */
  private const val MAX_EXACT_MINOR = 9_007_199_254_740_991L

  /** رقم، و اختیاری یک نقطه با حداکثر دو رقمِ اعشار (پول) — همان چیزی که `numericInput` ِ کادر می‌گذارد */
  private val AMOUNT = Regex("^([0-9]*)(?:\\.([0-9]{0,2}))?$")

  /** بیش از ۱۴ رقمِ افغانی همیشه بیرون از سقفِ دقیق است (و در `toLong` سرریز می‌کند)؛ پیش از خواندن رد می‌شود */
  private const val MAX_WHOLE_DIGITS = 14

  /** Shift+رقم (۱ تا ۶) ⇒ اسکناس؛ بیرون از بازه ⇒ null */
  fun noteForDigit(digit: Int): Long? = if (digit in 1..NOTES.size) NOTES[digit - 1] else null

  /**
   *  متنِ کادرِ «نقدِ دریافتی» (افغانی) ⇒ پول، **دقیق** و بی Double: «899.6» ⇒ 89960، «12.» ⇒ 1200، «.5» ⇒ 50؛ خالی = ۰.
   *  بیش از دو رقمِ اعشار (کمتر از یک پول)، نوشتهٔ نامعتبر یا بیرون از بازهٔ دقیق ⇒ null. تا دو رقمِ اعشار همان پولی است
   *  که پیش‌نمایشِ پنل (`PayFlow.minor` = `Money.fromLegacy`) می‌سازد (`CashKeysParityTest`)؛ خواندنِ Double در آیفون
   *  (Kotlin/Native) نزدیکِ سقف با JVM یکی نیست، پس این‌جا عدد از خودِ رقم‌ها ساخته می‌شود.
   */
  fun cashMinor(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return 0L
    val m = AMOUNT.matchEntire(t) ?: return null
    val whole = m.groupValues[1].trimStart('0')
    val frac = m.groupValues[2]
    if (m.groupValues[1].isEmpty() && frac.isEmpty()) return null // «.» به‌تنهایی
    if (whole.length > MAX_WHOLE_DIGITS) return null
    val minor = (if (whole.isEmpty()) 0L else whole.toLong()) * 100 + frac.padEnd(2, '0').toLong()
    return if (minor > MAX_EXACT_MINOR) null else minor
  }

  /**
   *  PAY-03 — افزودنِ یک اسکناس به «نقدِ دریافتی». مشتری چند اسکناس می‌دهد و هر دکمه یکی را می‌افزاید
   *  (۱۰۰۰ + ۱۰۰۰ + ۵۰۰ ⇒ ۲۵۰۰). متنِ تازه دقیقاً تا پول است، بی گرد (`MoneyInput.ofMinor`).
   *  اسکناسِ ناشناخته، متنِ نامعتبر یا جمعِ بیرون از بازهٔ دقیق ⇒ null: کادر دست نمی‌خورد و خطایش پیدا می‌ماند.
   */
  fun addNote(text: String, note: Long): String? {
    if (note !in NOTES) return null
    val m = cashMinor(text) ?: return null
    //  m ≤ ۲^۵۳−۱ پس جمع در Long سرریز نمی‌کند؛ بیرون از بازهٔ دقیق را `ofMinor` رد می‌کند
    return MoneyInput.ofMinor(m + note)
  }

  /** PAY-04 — Enter فقط وقتی کار می‌کند که دستِ کم [ENTER_GUARD_MS] از باز شدنِ پنل گذشته باشد */
  fun enterArmed(elapsedMs: Long): Boolean = elapsedMs >= ENTER_GUARD_MS
}
