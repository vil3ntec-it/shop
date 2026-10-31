package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.text.TextNormalizer

/*
 *  کالای جایگزین (PRD-24) و فیلدهای سفارشیِ کالا (PRD-25) — §۶.۳. همان سازوکارِ `ProductInfo`:
 *  مدلِ دفتر و فرم در برنامه‌اند و همه پشتِ تنظیمِ خاموشِ همین دستگاه (`ProductInfoPrefs`).
 *
 *  ── PRD-24 ─────────────────────────────────────────────────────────
 *  صاحبِ دکان برای هر کالا فهرستِ کوتاهی از کالاهای جایگزین می‌گذارد (به ترتیبِ ترجیح). وقتی
 *  صندوق کالایی را ناموجود یا کم می‌بیند، جایگزین‌های **فعال و موجود** را پیشنهاد می‌کند. در دفتر
 *  شناسهٔ کالا نوشته می‌شود، نه نام: نامِ کالا عوض می‌شود و پیوند نمی‌شکند؛ کالای پاک‌شده خودبه‌خود
 *  از پیشنهاد بیرون است.
 *
 *  ── PRD-25 ─────────────────────────────────────────────────────────
 *  جفت‌های «نام: مقدار» (گارانتی: ۱۲ ماه، کشورِ ساخت: ترکیه) به ترتیبِ ورود. نامِ تکراری (با کلیدِ
 *  مقایسه) یک بار و همان نخستین. مقدارها در جست‌وجوی کالا هم گشته می‌شوند (`ProductInfo.matches`).
 *
 *  منطقِ خالص (commonMain، بی java.* و android.*)؛ آزمون: `ProductExtrasTest`.
 */

/** کالای جایگزین — PRD-24 */
object ProductAlternatives {

  /** سقفِ جایگزینِ ثبت‌شده برای یک کالا */
  const val MAX = 10

  /** سقفِ پیشنهاد در یک پیام */
  const val SHOW = 3

  /** یک کالای دفتر برای پیدا کردن از روی نام یا بارکد */
  data class Ref(val id: String, val name: String, val barcodes: List<String> = emptyList())

  /** نتیجهٔ کادرِ فرم: شناسه‌های پیداشده به ترتیب، و بخش‌هایی که هیچ کالایی نبودند */
  data class Resolved(val ids: List<String>, val unknown: List<String>)

  /** وضعِ یک جایگزین هنگامِ پیشنهاد */
  data class Candidate(val id: String, val name: String, val active: Boolean, val inStock: Boolean)

  /**
   *  کادرِ «جایگزین‌ها» (نام‌ها یا بارکدها با «،» و…) ⇐ شناسه‌ها. هر بخش اول با بارکدِ کامل، سپس با
   *  نامِ دقیق (کلیدِ مقایسه: «ي/ی»، نیم‌فاصله، بزرگیِ لاتین) پیدا می‌شود. خودِ کالا ([selfId])،
   *  تکراری و بیش از [MAX] کنار می‌روند؛ ناشناخته‌ها در `unknown` می‌مانند تا فرم بگوید.
   */
  fun resolve(text: String, selfId: String?, catalog: List<Ref>): Resolved {
    val byCode = HashMap<String, String>()
    val byName = HashMap<String, String>()
    for (r in catalog) {
      for (c in r.barcodes) byCode.getOrPut(c.trim()) { r.id }
      byName.getOrPut(TextNormalizer.compareKey(r.name)) { r.id }
    }
    val ids = ArrayList<String>()
    val unknown = ArrayList<String>()
    for (part in ProductTerms.parse(text)) {
      val id = byCode[DigitLatin.latin(part)] ?: byName[TextNormalizer.compareKey(part)]
      when {
        id == null -> unknown += part
        id == selfId || id in ids -> Unit
        ids.size < MAX -> ids += id
      }
    }
    return Resolved(ids, unknown)
  }

  /**
   *  پیشنهادهای صندوق برای کالای [selfId]: به ترتیبِ ثبت، فقط فعال و موجود، نه خودِ کالا، و
   *  حداکثر [SHOW]. [lookup] برای کالای پاک‌شده `null` می‌دهد.
   */
  fun suggest(ids: List<String>, selfId: String, lookup: (String) -> Candidate?): List<Candidate> =
    ids.asSequence().distinct().filter { it != selfId }.mapNotNull(lookup)
      .filter { it.active && it.inStock }.take(SHOW).toList()
}

/** رقمِ فارسی/عربیِ بارکد ⇐ لاتین — همان قاعدهٔ `ProductBarcodes.parse` */
internal object DigitLatin {
  fun latin(s: String): String = buildString {
    for (c in s.trim()) append(
      when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c
      },
    )
  }
}

/** فیلدهای سفارشیِ کالا — PRD-25 */
object CustomFields {

  const val MAX_FIELDS = 20
  const val MAX_KEY = 40
  const val MAX_VALUE = 200

  /** [fields] به ترتیبِ ورود؛ [bad] خط‌هایی که «نام: مقدار» نبودند (بی نام یا بی مقدار) */
  data class Parsed(val fields: List<Pair<String, String>>, val bad: List<String>)

  private fun squash(s: String, max: Int): String =
    s.trim { it.isWhitespace() || it == '‌' }.split(Regex("\\s+")).joinToString(" ").take(max).trim()

  /**
   *  هر خط یک فیلد: «نام: مقدار» (جداکنندهٔ نخستین «:»، «：» یا «=»). خطِ تهی کنار؛ خطِ بی جداکننده،
   *  بی نام یا بی مقدار در `bad`. نامِ تکراری (کلیدِ مقایسه) یک بار و همان نخستین؛ حداکثر [MAX_FIELDS].
   */
  fun parse(text: String): Parsed {
    val seen = HashSet<String>()
    val out = ArrayList<Pair<String, String>>()
    val bad = ArrayList<String>()
    for (raw in text.split('\n', '\r')) {
      val line = raw.trim()
      if (line.isEmpty()) continue
      val at = line.indexOfFirst { it == ':' || it == '：' || it == '=' }
      val key = if (at < 0) "" else squash(line.substring(0, at), MAX_KEY)
      val value = if (at < 0) "" else squash(line.substring(at + 1), MAX_VALUE)
      if (key.isEmpty() || value.isEmpty()) { bad += line; continue }
      if (!seen.add(TextNormalizer.compareKey(key))) continue
      if (out.size < MAX_FIELDS) out += key to value
    }
    return Parsed(out, bad)
  }

  /** همان فهرست برای کادرِ چندخطی؛ `parse(format(x)).fields == x` */
  fun format(fields: List<Pair<String, String>>): String = fields.joinToString("\n") { (k, v) -> "$k: $v" }
}
