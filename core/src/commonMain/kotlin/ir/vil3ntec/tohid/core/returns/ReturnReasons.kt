package ir.vil3ntec.tohid.core.returns

/**
 *  RET-08 — «دلیلِ برگشت اجباری است و از فهرستِ قابلِ تنظیم انتخاب می‌شود».
 *
 *  فهرست را صاحبِ دکان در تنظیمات می‌نویسد (هر خط یک دلیل)؛ این‌جا فقط پاک‌سازی و سنجش است،
 *  کاتلینِ خالص و بی سکو. متنِ ذخیره‌شده عوض نمی‌شود: یکی‌سازیِ «ي/ی»، «ك/ک»، نیم‌فاصله و فاصله‌ها
 *  فقط روی «کلیدِ مقایسه» است (همان قاعدهٔ `TextNormalizer`)، و دلیلِ ثبت‌شده همیشه خودِ ردیفِ فهرست است.
 */
object ReturnReasons {

  /** فهرستِ پیش‌فرض — همان `ReturnFlow.REASONS` ِ برنامه */
  val DEFAULT: List<String> = listOf(
    "خراب یا معیوب",
    "اشتباه در فروش",
    "پشیمانیِ مشتری",
    "تاریخ گذشته",
    "اندازه یا مدلِ نامناسب",
    "دیگر",
  )

  /** بیشترین شمارِ دلیل‌ها — بیشتر از این روی صفحهٔ صندوق جا نمی‌شود */
  const val MAX_COUNT = 12

  /** بیشترین طولِ هر دلیل (نویسه) — بلندتر بریده نمی‌شود، کنار می‌رود */
  const val MAX_LENGTH = 40

  /**
   *  فهرستِ پاک: هر ردیف بی فاصلهٔ دو سر و بی نویسهٔ کنترلی، خالی و بلندتر از [MAX_LENGTH] کنار، تکراری
   *  (با کلیدِ مقایسه) فقط نخستین، و حداکثر [MAX_COUNT] ردیف. فهرستِ خالی یا `null` ⇒ [DEFAULT]
   *  (دلیل همیشه اجباری می‌ماند؛ فهرستِ خالی یعنی «هیچ برگشتی ممکن نیست»، که خواستهٔ کسی نیست).
   */
  fun normalize(raw: List<String>?): List<String> {
    val out = ArrayList<String>()
    val seen = HashSet<String>()
    for (r in raw.orEmpty()) {
      val t = clean(r)
      if (t.isEmpty() || t.length > MAX_LENGTH) continue
      if (!seen.add(key(t))) continue
      out += t
      if (out.size == MAX_COUNT) break
    }
    return out.ifEmpty { DEFAULT }
  }

  /** متنِ کادرِ تنظیمات (هر خط یک دلیل) ⇒ فهرستِ پاک */
  fun parse(text: String): List<String> = normalize(text.split('\n', '\r'))

  /** فهرست ⇒ متنِ کادرِ تنظیمات */
  fun format(list: List<String>): String = normalize(list).joinToString("\n")

  /**
   *  دلیلِ انتخاب‌شده ⇒ همان ردیفِ فهرست (برای ثبت)، یا `null` اگر خالی است یا در فهرست نیست.
   *  مقایسه با کلید است، پس «خراب يا معيوب» ِ عربی همان «خراب یا معیوب» است.
   */
  fun pick(reason: String?, list: List<String>): String? {
    val k = key(clean(reason.orEmpty()))
    if (k.isEmpty()) return null
    return normalize(list).firstOrNull { key(it) == k }
  }

  /* ------------------------------ کمکی‌ها ------------------------------ */

  /** فاصله‌های پیاپی یکی، نویسهٔ کنترلی (خطِ تازه، تب…) فاصله، و دو سر بی فاصله */
  private fun clean(s: String): String {
    val out = StringBuilder(s.length)
    var space = false
    for (c in s) {
      if (c.isWhitespace() || c.isISOControl()) { space = out.isNotEmpty(); continue }
      if (space) { out.append(' '); space = false }
      out.append(c)
    }
    return out.toString()
  }

  /** کلیدِ مقایسه — زیرمجموعهٔ `TextNormalizer.compareKey`: حروفِ هم‌ارز، بی اعراب و کشیده، بی نیم‌فاصله و بی فاصله */
  internal fun key(s: String): String {
    val out = StringBuilder(s.length)
    for (c in s) {
      val ch = when (c) {
        'ي', 'ى' -> 'ی'
        'ك' -> 'ک'
        'ۀ' -> 'ه'
        'أ', 'إ', 'آ' -> 'ا'
        '\u200C', '\u200D', '\u200E', '\u200F', '\u0640', '\u0670' -> continue
        in '\u064B'..'\u065F' -> continue   //  اعراب: «پشیمانیِ» = «پشیمانی»
        in 'A'..'Z' -> c + 32
        else -> c
      }
      if (ch == ' ' || ch.isWhitespace()) continue
      out.append(ch)
    }
    return out.toString()
  }
}
