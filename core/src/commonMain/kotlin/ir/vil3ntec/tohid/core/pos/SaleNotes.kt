package ir.vil3ntec.tohid.core.pos

/**
 *  یادداشتِ ردیف و یادداشتِ فاکتور، و قالب‌های یادداشتِ تکراری — POS-11.
 *
 *  ── یک خط، کوتاه ──────────────────────────────────────────────────
 *  یادداشت روی رسیدِ ۵۸ و ۸۰ میلی‌متری چاپ می‌شود، پس [clean] آن را یک خط می‌کند (هر فاصله و
 *  خطِ تازه ⇐ یک فاصله؛ نویسهٔ کنترلی بیرون)، تا [MAX] نویسه می‌بُرد و خالی را `null` می‌کند —
 *  فاکتورِ بی یادداشت بایت‌به‌بایت همان فاکتورِ امروز است (فیلد نوشته نمی‌شود).
 *
 *  ── قالب‌ها ───────────────────────────────────────────────────────
 *  فهرستِ [MAX_TEMPLATES] قالبِ یکتا (همان [clean]، بی تکرار با هم‌سنجیِ همان متن)، هر خط یکی
 *  ([encodeTemplates] / [templates]). [insert] قالب را به یادداشتِ کنونی می‌افزاید («، » میانِ دو)
 *  و اگر همان قالب از پیش آن‌جا باشد یا جا نباشد، یادداشت را دست نمی‌زند.
 *
 *  Kotlin خالص است — بی java.*، بی android.*.
 */
object SaleNotes {

  const val MAX = 120
  const val MAX_TEMPLATES = 12
  private const val JOIN = "، "

  /** یک خط، بی نویسهٔ کنترلی، حداکثر [MAX] نویسه؛ خالی ⇒ `null` */
  fun clean(text: String?): String? {
    if (text == null) return null
    val sb = StringBuilder()
    var space = false
    for (c in text) {
      val blank = c.isWhitespace() || c.isISOControl()
      if (blank) {
        space = sb.isNotEmpty()
        continue
      }
      if (space) { sb.append(' '); space = false }
      sb.append(c)
    }
    val one = sb.toString()
    if (one.isEmpty()) return null
    return if (one.length <= MAX) one else one.take(MAX).trimEnd()
  }

  /** فهرستِ قالب‌ها از متنِ ذخیره‌شده (هر خط یکی) — خالی و تکراری بیرون، حداکثر [MAX_TEMPLATES] */
  fun templates(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    val out = ArrayList<String>()
    for (line in raw.split('\n')) {
      val t = clean(line) ?: continue
      if (t !in out) out += t
      if (out.size == MAX_TEMPLATES) break
    }
    return out
  }

  fun encodeTemplates(list: List<String>): String = templates(list.joinToString("\n")).joinToString("\n")

  /** قالبِ تازه ته فهرست؛ خالی، تکراری یا فهرستِ پر ⇒ همان فهرست */
  fun addTemplate(list: List<String>, text: String): List<String> {
    val t = clean(text) ?: return list
    if (t in list || list.size >= MAX_TEMPLATES) return list
    return list + t
  }

  fun removeTemplate(list: List<String>, text: String): List<String> = list.filter { it != text }

  /** افزودنِ [template] به یادداشتِ [current]؛ تکراری یا بیش از [MAX] ⇒ همان یادداشت */
  fun insert(current: String?, template: String): String? {
    val t = clean(template) ?: return clean(current)
    val now = clean(current) ?: return t
    if (now.split(JOIN).any { it == t }) return now
    val next = now + JOIN + t
    return if (next.length > MAX) now else next
  }
}
