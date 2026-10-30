package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.MoneyInput
import kotlin.math.roundToLong

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

  private const val MINOR_PER_MAJOR = 100.0

  /** همان `MAX_EXACT` ِ Money.kt: بیرون از ۲^۵۳−۱ پول عددِ دقیقی نیست */
  private const val MAX_EXACT_MINOR = 9_007_199_254_740_991L

  /** فقط رقم و حداکثر یک نقطه، با دستِ کم یک رقم — همان چیزی که `numericInput` ِ کادر می‌گذارد */
  private val AMOUNT = Regex("^(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)$")

  /** Shift+رقم (۱ تا ۶) ⇒ اسکناس؛ بیرون از بازه ⇒ null */
  fun noteForDigit(digit: Int): Long? = if (digit in 1..NOTES.size) NOTES[digit - 1] else null

  /**
   *  متنِ کادرِ «نقدِ دریافتی» (افغانی) ⇒ پول. خالی = ۰.
   *  تبدیل همان `PayFlow.minor(amountOf(...))` ِ پیش‌نمایشِ پنل است (`round(x × 100)`، نیمه به سمتِ +∞)، تا
   *  عددی که دکمه می‌افزاید با عددی که تسویه می‌بیند یکی باشد. نوشتهٔ نامعتبر یا بیرون از بازهٔ دقیق ⇒ null.
   */
  fun cashMinor(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return 0L
    if (!AMOUNT.matches(t)) return null
    val scaled = (t.toDoubleOrNull() ?: return null) * MINOR_PER_MAJOR
    //  هزار رقم ⇒ بی‌نهایت؛ `roundToLong` عددِ بزرگ را به Long.MAX می‌چسباند که از بازه بیرون است
    if (!scaled.isFinite()) return null
    val m = scaled.roundToLong()
    return if (m > MAX_EXACT_MINOR) null else m
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
