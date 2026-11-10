package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.MAX_EXACT
import ir.vil3ntec.tohid.core.money.addExact
import ir.vil3ntec.tohid.core.money.mulExact
import ir.vil3ntec.tohid.core.text.JsText

/*
 *  PAY-10 — ارزِ نقدِ دیگر در صندوق (دالر، کلدار، ریال): نرخِ خودِ دکان ⇒ رکوردِ تبادلِ ثابت ⇒ ردیفِ `fx` ِ تسویه.
 *
 *  ⛔ هم‌زادِ `license/fx-till.js`؛ هر دو با `core/test-vectors/fx-till-v1.json` سنجیده می‌شوند
 *  (`FxTillVectorsTest`، `server/test/fx-till.test.js`). یکی را عوض کنی، دیگری هم.
 *
 *  • فروش همیشه به افغانی است؛ ارز فقط پرداخت است (`Pay.settle` با `SettleInput.fx`) و پولِ پس افغانیِ نقد.
 *  • نرخ را دکان می‌گذارد (صاحب/مدیر): ارز، نرخ (پولِ افغانی برای `per` واحد)، منبع، زمان و گذارنده — همان چهار چیزِ
 *    PAY-10. رکوردِ هر پرداخت همان لحظه ثابت می‌شود؛ نرخِ تازه فاکتورِ پیشین را عوض نمی‌کند.
 *  • نرخِ کهنه‌تر از [FxTill.MAX_AGE_MS] (یا جلوتر از ساعتِ صندوق بیش از [FxTill.SKEW_MS]) پذیرفته نمی‌شود (`FX_STALE`).
 *  • خالص: نه ساعت، نه ذخیره؛ «اکنون» را صدازننده می‌دهد.
 */

/** نرخِ یک ارز در جدولِ دکان — فقط از [FxTill.rates] */
data class FxRate(
  val currency: String,
  /** پولِ افغانی (واحدِ کوچک) برای [per] واحدِ اصلیِ ارز: ۱ دالر = ۷۰٫۲۵ ⇒ 7025 و per = 1 */
  val rate: Long,
  val per: Long,
  /** از کجا (صرافی، بانک) — یک خط */
  val source: String,
  /** کی گذاشته شد (ms) */
  val at: Long,
  /** چه کسی گذاشت/تأیید کرد */
  val by: String,
)

/** ردیفِ خامِ جدولِ نرخ (تنظیمِ دکان)؛ هر فیلد شاید نباشد */
data class FxRateInput(
  val currency: String? = null,
  val rate: Long? = null,
  val per: Long? = null,
  val source: String? = null,
  val at: Long? = null,
  val by: String? = null,
)

object FxTill {

  /** ارزِ فروش و پولِ پس */
  const val BASE = "AFN"
  const val MAX_RATES = 8
  /** بیشترین درازای منبع و نامِ گذارنده */
  const val MAX_TEXT = 60
  /** نرخِ کهنه‌تر از یک روز در صندوق پذیرفته نمی‌شود */
  const val MAX_AGE_MS: Long = 86_400_000L
  /** نرخی که تا ده دقیقه «آینده» است (ساعتِ دستگاهِ مدیر کمی جلو) پذیرفته است */
  const val SKEW_MS: Long = 600_000L

  /** واحدِ نرخِ پیش‌فرضِ هر ارز در فرمِ نرخ: یک دالر، هزار کلدار، یک میلیون ریال */
  val DEFAULT_PER: Map<String, Long> = mapOf("USD" to 1L, "PKR" to 1_000L, "IRR" to 1_000_000L)

  private fun line(s: String?): String? =
    s?.let(JsText::trim)?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT && '\n' !in it && '\r' !in it }

  /** جدولِ نرخِ دکان: هر ارز یک بار، نه افغانی؛ نرخ و زمان عددِ صحیحِ مثبت؛ منبع و گذارنده یک خطِ ناخالی */
  fun rates(list: List<FxRateInput>): PayResult<List<FxRate>> {
    if (list.size > MAX_RATES) return PayResult.Err("TOO_MANY_RATES")
    val out = ArrayList<FxRate>()
    list.forEachIndexed { i, x ->
      val cur = try { Currency.of(x.currency?.let(JsText::trim).orEmpty()) } catch (e: IllegalArgumentException) {
        return PayResult.Err("BAD_RATE", index = i, field = "currency")
      }
      if (cur.code == BASE) return PayResult.Err("BAD_RATE", index = i, field = "currency")
      if (out.any { it.currency == cur.code }) return PayResult.Err("DUPLICATE_RATE", index = i)
      val rate = x.rate
      if (rate == null || !posExact(rate)) return PayResult.Err("BAD_RATE", index = i, field = "rate")
      val per = x.per ?: 1L
      if (per !in Pay.FX_PER) return PayResult.Err("BAD_RATE", index = i, field = "per")
      val source = line(x.source) ?: return PayResult.Err("BAD_RATE", index = i, field = "source")
      val at = x.at
      if (at == null || !posExact(at)) return PayResult.Err("BAD_RATE", index = i, field = "at")
      val by = line(x.by) ?: return PayResult.Err("BAD_RATE", index = i, field = "by")
      out += FxRate(cur.code, rate, per, source, at, by)
    }
    return PayResult.Ok(out)
  }

  /**
   *  پرداختِ [amount] (واحدِ کوچکِ [currency]) با نرخِ همان ارزِ جدول در لحظهٔ [now] ⇒ رکوردِ تبادلِ ثابت برای
   *  `SettleInput.fx`. خطا: `FX_NO_RATE`، `FX_STALE`، `INVALID_AMOUNT` (صفر، بیرون از بازه، یا کمتر از یک پلهٔ افغانی)،
   *  `INVALID_TIME`.
   */
  fun tender(rates: List<FxRate>, currency: String, amount: Long, now: Long, maxAgeMs: Long = MAX_AGE_MS): PayResult<ExchangeRecord> {
    val r = rates.firstOrNull { it.currency == currency } ?: return PayResult.Err("FX_NO_RATE")
    if (!posExact(amount)) return PayResult.Err("INVALID_AMOUNT")
    if (!posExact(now)) return PayResult.Err("INVALID_TIME")
    val age = now - r.at
    if (age > maxAgeMs || age < -SKEW_MS) return PayResult.Err("FX_STALE")
    val rec = try {
      Pay.exchange(ExchangeInput(r.currency, amount, BASE, r.rate, r.source, r.at, r.by, per = r.per))
    } catch (e: ArithmeticException) {
      return PayResult.Err("INVALID_AMOUNT")
    }
    if (rec is PayResult.Ok && rec.value.toAmount <= 0) return PayResult.Err("INVALID_AMOUNT")
    return rec
  }

  private val NUM = Regex("^([0-9]+)(?:\\.([0-9]+))?$")

  /** رقمِ فارسی و عربی ⇒ لاتین، «٫» ⇒ نقطه (همان صفحه‌کلیدِ فارسی) */
  private fun latin(s: String): String = buildString {
    for (c in s) {
      append(
        when (c) {
          in '۰'..'۹' -> '0' + (c - '۰')
          in '٠'..'٩' -> '0' + (c - '٠')
          '٫' -> '.'
          else -> c
        }
      )
    }
  }

  /**
   *  متنِ کادرِ مبلغِ ارز ⇒ واحدِ کوچکِ همان ارز، دقیق و بی Double: «۱۰٫۵» دالر ⇒ 1050، «1,000» کلدار ⇒ 100000.
   *  بیش از اعشارِ ارز، منفی، ناعدد یا بیرون از ۲^۵۳−۱ ⇒ null.
   */
  fun parseAmount(text: String, currency: String): Long? {
    val cur = try { Currency.of(currency) } catch (e: IllegalArgumentException) { return null }
    val t = JsText.trim(latin(text)).filter { it != ',' && it != '٬' }
    val m = NUM.matchEntire(t) ?: return null
    val whole = m.groupValues[1].trimStart('0').ifEmpty { "0" }
    val frac = m.groupValues[2]
    if (frac.length > cur.decimals || whole.length > 16) return null
    val w = whole.toLong()
    if (w > MAX_EXACT) return null
    val f = if (frac.isEmpty()) 0L else frac.padEnd(cur.decimals, '0').toLong()
    return try { addExact(mulExact(w, cur.minorPerMajor), f) } catch (e: ArithmeticException) { null }
  }

  private fun group(d: String): String = buildString {
    d.forEachIndexed { i, c ->
      if (i > 0 && (d.length - i) % 3 == 0) append(',')
      append(c)
    }
  }

  /** مبلغِ ارز با همهٔ اعشارِ همان ارز: 1000 دالر‌سنت ⇒ «10.00»، ریال ⇒ «7,500,000» (رقمِ لاتین، همان `money`) */
  fun format(minor: Long, currency: String): String {
    val cur = try { Currency.of(currency) } catch (e: IllegalArgumentException) { return minor.toString() }
    val a = if (minor < 0) -minor else minor
    val unit = cur.minorPerMajor
    val frac = if (cur.decimals > 0) "." + (a % unit).toString().padStart(cur.decimals, '0') else ""
    return (if (minor < 0) "-" else "") + group((a / unit).toString()) + frac
  }

  /** پولِ افغانی بی صفرِ زائدِ اعشار: 7025 ⇒ «70.25»، 24600 ⇒ «246» */
  fun afnText(minor: Long): String {
    val a = if (minor < 0) -minor else minor
    val frac = (a % 100).toString().padStart(2, '0').trimEnd('0')
    return (if (minor < 0) "-" else "") + group((a / 100).toString()) + if (frac.isEmpty()) "" else ".$frac"
  }

  /** «1 USD = 70.25 AFN»، «1,000 PKR = 246 AFN» — همان روی صفحه، رسید و گزارش */
  fun rateLine(rate: Long, per: Long, currency: String): String = "${group(per.toString())} $currency = ${afnText(rate)} $BASE"
}
