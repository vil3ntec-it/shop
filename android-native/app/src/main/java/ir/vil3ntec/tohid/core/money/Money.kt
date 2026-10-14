package ir.vil3ntec.tohid.core.money

/*
 *  نوع‌های پایهٔ پول و مقدار — بخشِ ۳.۴ِ مشخصات (docs/spec/03-architecture.md).
 *
 *  پول عددِ صحیح است به «واحدِ کوچک» (پول = یک‌صدمِ افغانی) و مقدار عددِ صحیح
 *  به «هزارم». هیچ Double‌ای در حساب نیست؛ Double فقط در مرزِ دادهٔ امروز
 *  (`fromLegacy`) خوانده می‌شود.
 *
 *  ⛔ هم‌زادِ این پرونده `license/money.js` است و هر دو با یک فایلِ آزمونِ مرجع
 *  (`core/test-vectors/money-v1.json`) سنجیده می‌شوند. یکی را عوض کنی، دیگری هم.
 *
 *  هنوز هیچ موتوری به این‌ها وصل نیست؛ `SalesEngine` همان حسابِ امروز را می‌کند.
 */

/** بزرگ‌ترین عددِ صحیحی که JS بی‌خطا نگه می‌دارد (۲^۵۳−۱). همهٔ مبلغ‌ها و مقدارها و میانه‌ها زیرِ آن‌اند. */
const val MAX_EXACT: Long = 9_007_199_254_740_991L

internal fun checkExact(v: Long, what: String): Long {
  if (v > MAX_EXACT || v < -MAX_EXACT) throw ArithmeticException("$what بیرون از بازهٔ دقیق: $v")
  return v
}

/**
 *  ارز. هویتِ ارز فقط `code` است؛ `decimals` و `stepMinor` (پلهٔ گردکردن به
 *  واحدِ کوچک) از جدول می‌آیند و پله برای هر دکان قابلِ تنظیم است (`withStep`).
 */
class Currency(val code: String, val decimals: Int, val stepMinor: Long) {
  init {
    require(code.isNotBlank()) { "کدِ ارز خالی است" }
    require(decimals in 0..4) { "رقمِ اعشار باید ۰ تا ۴ باشد: $decimals" }
    require(stepMinor in 1..MAX_EXACT) { "پلهٔ گردکردن باید مثبت باشد: $stepMinor" }
  }

  /** چند واحدِ کوچک = یک واحدِ اصلی (AFN: ۱۰۰) */
  val minorPerMajor: Long get() = (1..decimals).fold(1L) { acc, _ -> acc * 10 }

  fun withStep(stepMinor: Long): Currency = Currency(code, decimals, stepMinor)

  override fun equals(other: Any?): Boolean = other is Currency && other.code == code
  override fun hashCode(): Int = code.hashCode()
  override fun toString(): String = code

  companion object {
    /** افغانی: دو رقمِ اعشار، پلهٔ گردکردن یک افغانی (۱۰۰ پول) — همان رفتارِ امروز */
    val AFN = Currency("AFN", 2, 100)
    /** دالر: فقط برای عملیاتِ تبادل (PAY-10)؛ پله یک سنت */
    val USD = Currency("USD", 2, 1)

    private val table = listOf(AFN, USD).associateBy { it.code }

    fun of(code: String): Currency = table[code] ?: throw IllegalArgumentException("ارزِ ناشناخته: $code")
  }
}

/** ارزِ دو طرف یکی نیست — جمع یا مقایسهٔ دو ارز خطای زمانِ اجراست، نه تبدیلِ بی‌صدا. */
class CurrencyMismatchException(a: Currency, b: Currency) :
  IllegalArgumentException("دو ارزِ متفاوت: $a و $b")

/** مبلغ به واحدِ کوچک. */
data class Money(val minor: Long, val currency: Currency) : Comparable<Money> {
  init { checkExact(minor, "مبلغ") }

  operator fun plus(other: Money): Money = Money(Math.addExact(minor, same(other).minor), currency)
  operator fun minus(other: Money): Money = Money(Math.subtractExact(minor, same(other).minor), currency)
  operator fun unaryMinus(): Money = Money(-minor, currency)
  override fun compareTo(other: Money): Int = minor.compareTo(same(other).minor)

  val isZero: Boolean get() = minor == 0L

  private fun same(other: Money): Money {
    if (other.currency != currency) throw CurrencyMismatchException(currency, other.currency)
    return other
  }

  companion object {
    fun zero(currency: Currency): Money = Money(0, currency)

    /**
     *  از عددِ اعشاریِ امروز (مثلاً `salePrice` به افغانی) به واحدِ کوچک، با
     *  `Math.round` (نیمه به سمتِ +∞). برای هر قیمتی که با حداکثر دو رقمِ اعشار
     *  وارد شده، دقیقاً همان عدد را برمی‌گرداند.
     */
    fun fromLegacy(major: Double, currency: Currency): Money {
      require(!major.isNaN() && !major.isInfinite()) { "مبلغِ نامعتبر: $major" }
      return Money(Math.round(major * currency.minorPerMajor), currency)
    }
  }
}

/** مقدار به هزارم (کالای وزنی ۳ رقمِ اعشار، مثلِ رفتارِ امروز). */
@JvmInline
value class Qty(val milli: Long) : Comparable<Qty> {
  init { checkExact(milli, "مقدار") }

  override fun compareTo(other: Qty): Int = milli.compareTo(other.milli)

  companion object {
    const val SCALE: Long = 1000

    fun units(n: Long): Qty = Qty(Math.multiplyExact(n, SCALE))

    /** همان پاک‌سازیِ `SalesEngine.setCartQty`: `Math.round(x × 1000)` */
    fun fromLegacy(quantity: Double): Qty {
      require(!quantity.isNaN() && !quantity.isInfinite()) { "مقدارِ نامعتبر: $quantity" }
      return Qty(Math.round(quantity * SCALE))
    }
  }
}
