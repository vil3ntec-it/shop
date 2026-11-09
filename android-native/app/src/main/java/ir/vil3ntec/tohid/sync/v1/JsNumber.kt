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
 *  ⛔ رقم‌ها همان رقم‌های خودِ `Double.toString` اند (کوتاه‌ترین رقم‌های رفت‌وبرگشتی، همان که JS می‌نویسد)؛ فقط جای
 *  ممیز و نمای `e` عوض می‌شود، پس مقدار همان مقدار است. شکلِ ساده (بی نما) دست نمی‌خورد: بین ۰٫۰۰۱ و ده میلیون
 *  هر دو زبان یکی می‌نویسند. بی `BigDecimal` و بی API ِ جاوا، تا آیفون همین کد را داشته باشد.
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
    if (c.indexOf('E') < 0 && c.indexOf('e') < 0) return e
    val t = text(c)
    return if (t == c) e else JsonUnquotedLiteral(t)
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
