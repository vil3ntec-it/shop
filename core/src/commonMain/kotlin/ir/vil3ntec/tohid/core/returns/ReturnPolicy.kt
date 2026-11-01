package ir.vil3ntec.tohid.core.returns

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.core.text.TextNormalizer

/**
 *  RET-09 — سیاستِ برگشت: مهلت به روز و اقلامِ غیرقابلِ برگشت، با استثنای مجاز.
 *
 *  ── سیاست ─────────────────────────────────────────────────────────
 *  • **مهلت** ([Policy.days]): برگشتِ با رسید تا [Policy.days] روز پس از تاریخِ فاکتور پذیرفته است
 *    (روزِ فروش = روزِ ۰؛ مهلتِ ۷ ⇒ تا هفت روز بعد هم). صفر یا نبودن ⇒ بی مهلت. برگشتِ بی رسید تاریخِ
 *    فروش ندارد و مهلت آن را نمی‌سنجد (تأییدِ خودش را دارد، RET-04).
 *  • **اقلامِ غیرقابلِ برگشت** ([Policy.blocked]): هر خطِ تنظیمات نامِ دسته، زیردسته، نامِ کالا یا
 *    بارکد است؛ مقایسه با کلیدِ `TextNormalizer.compareKey` (حروفِ هم‌ارز، نیم‌فاصله) و بارکد با رقمِ لاتین.
 *  • **استثنا** کارِ صدازننده است (`ReturnFlow.gate`): بیرون از سیاست برای شاگرد «تأییدِ مدیر» می‌خواهد و
 *    مدیر و صاحب خودشان استثنا می‌گذارند؛ هر استثنا در سابقهٔ سند می‌ماند.
 *
 *  پیش‌فرض خاموش: سیاستِ خالی ([Policy.on] = false) ⇒ [check] همیشه «پذیرفته» و برگشت همان امروز.
 *  ⚠️ سیاست تنظیمِ همین دستگاه است و سرور آن را نمی‌سنجد (مثلِ قیمتِ ردیف، POS-08).
 *
 *  Kotlin خالص است — بی java.*، بی android.*، بی کتابخانه.
 */
object ReturnPolicy {

  /** بیشترین مهلت (ده سال) — بیشتر یعنی عملاً بی مهلت و فقط خطای تایپ است */
  const val MAX_DAYS = 3650

  /** بیشترین شمارِ خط‌های «غیرقابلِ برگشت» و طولِ هر خط */
  const val MAX_RULES = 100
  const val MAX_RULE_LENGTH = 60

  /** کدِ دلیلِ بیرون بودن از سیاست — صفحه متنِ خودش را می‌گذارد */
  const val LATE = "late"
  const val NOT_RETURNABLE = "not_returnable"

  data class Policy(val days: Int = 0, val blocked: List<String> = emptyList()) {
    val on: Boolean get() = days > 0 || blocked.isNotEmpty()
  }

  /** آنچه از کالا لازم است — `productId` ِ قلمِ برگشتی و نام‌ها و بارکدهایش */
  data class Item(
    val id: String,
    val name: String = "",
    val category: String = "",
    val subcategory: String = "",
    val barcodes: List<String> = emptyList(),
  )

  /**
   *  @param ageDays روزهای گذشته از فاکتور تا امروز؛ `null` ⇒ بی رسید یا تاریخِ نامعتبر
   *  @param blockedIds `id` ِ قلم‌هایی که غیرقابلِ برگشت‌اند، به همان ترتیبِ ورودی و بی تکرار
   */
  data class Verdict(val late: Boolean = false, val ageDays: Int? = null, val blockedIds: List<String> = emptyList()) {
    val ok: Boolean get() = !late && blockedIds.isEmpty()
    val reasons: List<String> get() = buildList {
      if (late) add(LATE)
      if (blockedIds.isNotEmpty()) add(NOT_RETURNABLE)
    }
  }

  /** نوشتهٔ کادرِ «مهلت» ⇒ روز؛ خالی، نامعتبر یا ≤ ۰ ⇒ ۰ (بی مهلت)؛ بیش از [MAX_DAYS] ⇒ همان سقف */
  fun days(text: String?): Int {
    val t = LocaleFormat.asciiDigits(text.orEmpty()).trim()
    if (t.isEmpty() || t.length > 6 || !t.all { it in '0'..'9' }) return 0
    return t.toInt().coerceAtMost(MAX_DAYS)
  }

  /** متنِ کادرِ «غیرقابلِ برگشت» (هر خط یکی) ⇒ فهرستِ پاک: بی خطِ خالی و تکراری، حداکثر [MAX_RULES] */
  fun parseRules(text: String?): List<String> {
    val out = ArrayList<String>()
    val seen = HashSet<String>()
    for (raw in text.orEmpty().split('\n', '\r')) {
      val t = raw.trim().replace(Regex("\\s+"), " ")
      if (t.isEmpty() || t.length > MAX_RULE_LENGTH) continue
      if (!seen.add(ruleKey(t))) continue
      out += t
      if (out.size == MAX_RULES) break
    }
    return out
  }

  fun formatRules(list: List<String>): String = parseRules(list.joinToString("\n")).joinToString("\n")

  /** روزهای گذشته از [saleIso] تا [todayIso] (هر دو `YYYY-MM-DD`)؛ نامعتبر ⇒ `null` */
  fun age(saleIso: String, todayIso: String): Int? {
    val s = CivilDays.jdnOfIso(saleIso.trim().take(10)) ?: return null
    val t = CivilDays.jdnOfIso(todayIso.trim().take(10)) ?: return null
    return t - s
  }

  private fun ruleKey(s: String): String = TextNormalizer.compareKey(LocaleFormat.asciiDigits(s))

  /** این خطِ سیاست این کالا را می‌گیرد؟ */
  fun matches(rule: String, item: Item): Boolean {
    val k = ruleKey(rule)
    if (k.isEmpty()) return false
    if (listOf(item.category, item.subcategory, item.name).any { it.isNotBlank() && ruleKey(it) == k }) return true
    val code = LocaleFormat.asciiDigits(rule).trim()
    return item.barcodes.any { LocaleFormat.asciiDigits(it).trim() == code }
  }

  /**
   *  @param saleDate تاریخِ فاکتور؛ `null` ⇒ برگشتِ بی رسید (مهلت سنجیده نمی‌شود)
   *  @param items قلم‌های برگشتیِ همین سند
   */
  fun check(p: Policy, saleDate: String?, today: String, items: List<Item>): Verdict {
    if (!p.on) return Verdict()
    val age = saleDate?.let { age(it, today) }
    //  تاریخِ آینده (ساعتِ دستگاه عقب) دیر نیست؛ تاریخِ خراب را نمی‌شود سنجید و رد نمی‌شود
    val late = p.days > 0 && age != null && age > p.days
    val rules = parseRules(p.blocked.joinToString("\n"))
    val blocked = LinkedHashSet<String>()
    if (rules.isNotEmpty()) for (item in items) if (rules.any { matches(it, item) }) blocked += item.id
    return Verdict(late = late, ageDays = age, blockedIds = blocked.toList())
  }
}
