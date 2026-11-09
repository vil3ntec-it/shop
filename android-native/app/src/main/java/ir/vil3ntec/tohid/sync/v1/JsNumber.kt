package ir.vil3ntec.tohid.sync.v1

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs

/**
 *  عددِ روی خط به همان شکلی که جاوااسکریپت (`String(x)` / `JSON.stringify`) می‌نویسد — DATA-SAFETY §۸.
 *
 *  ── چرا ──────────────────────────────────────────────────────────────
 *  سرور اثرِ انگشتِ op (`hash`) را روی عددی که **خوانده** دوباره می‌سازد (`canonical` ِ `lib/sync-v1.js`). جاوا و
 *  کاتلین `Double` ِ کوچک‌تر از ۰٫۰۰۱ یا بزرگ‌تر از ۱۰ میلیون را نمایی می‌نویسند (`4.0E-4`، `1.23456785E7`،
 *  `5.684341886080802E-14`) و جاوااسکریپت ساده (`0.0004`، `12345678.5`) یا با `e` ِ کوچک (`5.684341886080802e-14`).
 *  پس opی که چنین عددی داشت — `remaining` ِ فاکتوری که با تهِ گردکردنِ `Double` صفرِ دقیق نشد، قیمتِ هر گرم، فاکتورِ
 *  بالای ده میلیون با اعشار — `hash_mismatch` می‌گرفت، به دفترِ ردشده می‌رفت و **هرگز به سرور نمی‌رسید**؛ با نصبِ
 *  دوباره همان ردیف گم بود. سرور (مشترک با پمپ) دست نمی‌خورد: برنامه همان بایت‌های جاوااسکریپت را می‌فرستد.
 *
 *  ⛔ رقم‌ها: `Double.toString` ِ جاوا ۱۹ به بعد کوتاه‌ترین رقم‌های رفت‌وبرگشتی را می‌نویسد (همان که JS می‌نویسد)، ولی
 *  جاوا ۱۷ و اندروید (ART) گاهی رقمِ اضافه می‌نویسند: `5.6843418860808015E-14` به جای `5.684341886080802E-14`،
 *  `0.0020` به جای `0.002`. پس عددی که ۱۵ رقمِ معنادار یا بیشتر دارد رقم‌هایش را از بسطِ دقیقِ دهدهیِ خودِ مقدار
 *  می‌گیرد ([shortest]: کمترین شمارِ رقم، نزدیک‌ترین، در تساوی زوج — همان `Number::toString` ِ ECMAScript)، و صفرِ
 *  تهِ کسر هم می‌افتد؛ مقدار همان مقدار است. بقیه فقط جای ممیز و نمای `e` را عوض می‌کنند. بی `BigDecimal` و بی API ِ
 *  جاوا، تا آیفون همین کد را داشته باشد (`JsNumberJdkTest`).
 */
internal object JsNumber {

  /** بزرگ‌ترین عددِ صحیحِ دقیقِ جاوااسکریپت (۲^۵۳) */
  private const val SAFE = 9.007199254740992E15

  /**
   *  عددِ JSON (نه رشته، نه null، نه درست/نادرست) ⇒ همان عدد با متنِ جاوااسکریپت؛ هر چیزِ دیگر همان شیء.
   *  صحیحِ `Double` (`120.0`) ⇒ `120`، همان قاعدهٔ پیشینِ `LedgerDiff.toJson`.
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun of(e: JsonPrimitive): JsonPrimitive {
    if (e.isString) return e
    val d = e.doubleOrNull ?: return e
    val c = e.content
    if (c.contains('.') && d == kotlin.math.round(d) && !d.isInfinite() && abs(d) < SAFE) return JsonPrimitive(d.toLong())
    val sci = c.indexOf('E') >= 0 || c.indexOf('e') >= 0
    val mant = c.substringBefore('E').substringBefore('e')
    val t = when {
      //  شکلِ `Double` (ممیز یا نما) با ۱۵ رقمِ معنادار یا بیشتر ⇒ رقم‌ها از خودِ مقدار، نه از `toString` ِ این جاوا
      (sci || mant.contains('.')) && d.isFinite() && d != 0.0 &&
        mant.filter { it in '0'..'9' }.trimStart('0').trimEnd('0').length >= 15 -> text(shortest(d))
      sci || (mant.contains('.') && mant.endsWith('0')) -> text(c)
      else -> return e
    }
    return if (t == c) e else JsonUnquotedLiteral(t)
  }

  /**
   *  کوتاه‌ترین رقم‌هایی که همان `Double` را برمی‌گردانند، از بسطِ دقیقِ دهدهیِ مقدار (|d| = m×2^e = big×10^-q):
   *  کمترین k که یکی از دو همسایهٔ k رقمی برگردد؛ اگر هر دو، نزدیک‌تر و در تساوی زوج (همان `Number::toString`)
 *  ⇒ `0.<رقم‌ها>E<n>` برای [text].
   */
  internal fun shortest(d: Double): String {
    val bits = d.toRawBits()
    val be = ((bits ushr 52) and 0x7ff).toInt()
    val m = (bits and 0xFFFFFFFFFFFFFL) or (if (be == 0) 0L else 1L shl 52)
    val e = (if (be == 0) 1 else be) - 1075
    val big = Big(m)
    if (e >= 0) big.pow(2, e) else big.pow(5, -e)
    val all = big.toString()
    val n = all.length + (if (e < 0) e else 0)
    val sign = if (d < 0) "-" else ""
    val x = abs(d)
    for (k in 1..17) {
      val lo = "0.${all.take(k)}E$n"
      if (all.length <= k) return sign + lo
      //  دو همسایهٔ k رقمی: پایین (بریده) و بالا؛ کنارِ توانِ ۲ فاصلهٔ پایین نصف است و شاید فقط بالایی برگردد
      val upDigits = inc(all.take(k))
      val hi = if (upDigits.length > k) "0.${upDigits.dropLast(1)}E${n + 1}" else "0.${upDigits}E$n"
      val loOk = lo.toDouble() == x
      val hiOk = hi.toDouble() == x
      if (loOk && hiOk) {
        val rd = all[k]
        val rest = (k + 1 until all.length).any { all[it] != '0' }
        val up = rd > '5' || (rd == '5' && (rest || (all[k - 1] - '0') % 2 == 1))
        return sign + if (up) hi else lo
      }
      if (loOk) return sign + lo
      if (hiOk) return sign + hi
    }
    return d.toString()
  }

  /** رشتهٔ رقم + ۱ (`"199"` ⇒ `"200"`، `"99"` ⇒ `"100"`) */
  private fun inc(s: String): String {
    val c = s.toCharArray()
    var i = c.size - 1
    while (i >= 0 && c[i] == '9') c[i--] = '0'
    return if (i < 0) "1" + c.concatToString() else { c[i] = c[i] + 1; c.concatToString() }
  }

  /** عددِ صحیحِ بزرگِ بی‌علامت، پایهٔ ۱۰^۹، کم‌ارزش اول — فقط ضربِ توان برای [shortest] */
  private class Big(v: Long) {
    private var w = IntArray(8)
    private var size = 0
    init { var x = v; while (x > 0) { w[size++] = (x % B).toInt(); x /= B } }

    fun pow(base: Int, times: Int) {
      val (chunk, per) = if (base == 2) 1L shl 30 to 30 else 1_220_703_125L to 13
      var left = times
      while (left > 0) {
        val step = minOf(left, per)
        mul(if (step == per) chunk else (1..step).fold(1L) { a, _ -> a * base })
        left -= step
      }
    }

    private fun mul(f: Long) {
      var carry = 0L
      for (i in 0 until size) { val x = w[i] * f + carry; w[i] = (x % B).toInt(); carry = x / B }
      while (carry > 0) { if (size == w.size) w = w.copyOf(size * 2); w[size++] = (carry % B).toInt(); carry /= B }
    }

    override fun toString(): String {
      if (size == 0) return "0"
      val sb = StringBuilder(w[size - 1].toString())
      for (i in size - 2 downTo 0) sb.append(w[i].toString().padStart(9, '0'))
      return sb.toString()
    }

    private companion object { const val B = 1_000_000_000L }
  }

  /** `Double` ِ محاسبه‌شده (دلتای `$inc`) ⇒ همان [of] */
  fun of(d: Double): JsonPrimitive = of(JsonPrimitive(d))

  /**
   *  متنِ عدد (هر شکلِ اعشاری یا نماییِ درست) ⇒ متنِ `Number.prototype.toString` ِ ECMAScript با همان رقم‌ها
   *  (بندِ 6.1.6.1.20: k رقم، نمای n). متنِ نامفهوم ⇒ همان متن.
   */
  fun text(raw: String): String {
    var t = raw.trim()
    val neg = t.startsWith('-')
    if (neg || t.startsWith('+')) t = t.substring(1)
    val at = t.indexOfFirst { it == 'e' || it == 'E' }
    val mant = if (at >= 0) t.substring(0, at) else t
    val exp = if (at >= 0) t.substring(at + 1).removePrefix("+").toIntOrNull() ?: return raw else 0
    val dot = mant.indexOf('.')
    val whole = if (dot >= 0) mant.substring(0, dot) else mant
    val frac = if (dot >= 0) mant.substring(dot + 1) else ""
    var digits = whole + frac
    if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return raw
    //  مقدار = 0.<رقم‌ها> × 10^n
    var n = whole.length + exp
    val lead = digits.indexOfFirst { it != '0' }
    if (lead < 0) return "0"
    digits = digits.substring(lead).trimEnd('0')
    n -= lead
    val k = digits.length
    val body = when {
      n in k..21 -> digits + "0".repeat(n - k)
      n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
      n in -5..0 -> "0." + "0".repeat(-n) + digits
      else -> {
        val e = n - 1
        (if (k == 1) digits else digits.substring(0, 1) + "." + digits.substring(1)) + "e" + (if (e < 0) "-" else "+") + abs(e)
      }
    }
    return if (neg) "-$body" else body
  }
}
