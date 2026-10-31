package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.text.TextNormalizer

/*
 *  نام‌های دیگر و واژه‌های جست‌وجوی کالا (PRD-02) و محلِ قفسه (PRD-26) — §۶.۳.
 *
 *  ── PRD-02 ─────────────────────────────────────────────────────────
 *  نامِ دری همان `Product.name` است و هر جا که بود همان می‌ماند. این‌جا فقط آن‌چه برای **پیدا کردن**
 *  لازم است: نامِ پشتو، نامِ انگلیسی، «مترادف» (نامِ دیگرِ همین کالا که مشتری می‌گوید: «کچالو» برای
 *  «سیب‌زمینی»، «Coke» برای «کوکاکولا») و «واژهٔ جست‌وجو» (برند، نوع، کاربرد). متنِ ذخیره‌شده دست
 *  نمی‌خورد؛ نرمال‌سازی فقط روی کلید است (`TextNormalizer`: در پشتو «ي» و «ی» دو حرف‌اند و متن همان
 *  می‌ماند). مترادفِ همین کالاست، نه فرهنگِ مترادفِ دکان (§۳.۱۳، آن از پنل می‌آید).
 *
 *  ── PRD-26 ─────────────────────────────────────────────────────────
 *  راهرو، قفسه، طبقه: سه کادرِ کوتاهِ آزاد (عدد یا حرف: «۳»، «B»، «بالا»)؛ هر کدام جدا خالی می‌تواند
 *  باشد. برای جمع‌آوریِ سفارش کنارِ قلم دیده می‌شود.
 *
 *  منطقِ خالص (commonMain، بی java.* و android.*)؛ آزمون: `ProductInfoTest`. مدلِ دفتر و فرم در
 *  برنامه‌اند (`Product.namePs`… ، `ProductInfoMap`) و همه پشتِ تنظیمِ خاموشِ همین دستگاه.
 */

/** فهرستِ واژه‌ها: کادرِ متنی ⇄ فهرستِ ذخیره */
object ProductTerms {

  /** سقفِ واژه در هر فهرست — فهرستِ بی‌حد نمایه و فرم را سنگین می‌کند */
  const val MAX_TERMS = 20

  /** سقفِ نویسهٔ هر واژه */
  const val MAX_TERM = 60

  /** سقفِ نویسهٔ نامِ پشتو/انگلیسی */
  const val MAX_NAME = 120

  /** فاصله‌ها (و نیم‌فاصلهٔ آغاز/پایان) یکی و بریده — متنِ میانه دست نمی‌خورد */
  private fun squash(s: String, max: Int): String =
    s.trim { it.isWhitespace() || it == '‌' }.split(Regex("\\s+")).joinToString(" ").take(max).trim()

  /**
   *  «کچالو، سیب زمینی; potato» ⇒ `[کچالو, سیب زمینی, potato]`. جداکننده: «،» «,» «؛» «;» و خطِ
   *  تازه (فاصله نه: واژه می‌تواند دوکلمه‌ای باشد). تهی بیرون؛ تکراری با کلیدِ مقایسه («ي/ی»، نیم‌فاصله،
   *  بزرگ/کوچکِ لاتین) یک بار و همان نخستین؛ حداکثر [MAX_TERMS] واژه و هر کدام [MAX_TERM] نویسه.
   */
  fun parse(text: String): List<String> {
    val seen = HashSet<String>()
    val out = ArrayList<String>()
    for (part in text.split(',', '،', ';', '؛', '\n', '\r')) {
      val t = squash(part, MAX_TERM)
      if (t.isEmpty()) continue
      val key = TextNormalizer.compareKey(t)
      if (key.isEmpty() || !seen.add(key)) continue
      out += t
      if (out.size == MAX_TERMS) break
    }
    return out
  }

  /** همان فهرست برای کادرِ متنی؛ `parse(format(x)) == x` برای هر فهرستِ ساختهٔ `parse` */
  fun format(terms: List<String>): String = terms.joinToString("، ")

  /** نامِ تک (پشتو یا انگلیسی): فاصله‌ها یکی و حداکثر [MAX_NAME] */
  fun cleanName(text: String): String = squash(text, MAX_NAME)

  /**
   *  پرس‌وجو در **یکی** از این متن‌ها هست؟ — همان `TextNormalizer.matches` (حروفِ هم‌ارز، رقمِ فارسی،
   *  کلمه‌ها به هر ترتیب) روی هر متن جدا، تا تکهٔ پایانِ یکی و آغازِ دیگری جورِ ساختگی نسازد.
   *  پرس‌وجوی خالی ⇒ نه (جورِ «همه» کارِ صدازننده است).
   */
  fun matchesAny(texts: List<String>, query: String): Boolean {
    if (TextNormalizer.searchKey(query).isBlank()) return false
    return texts.any { it.isNotBlank() && TextNormalizer.matches(it, query) }
  }
}

/** محلِ قفسهٔ یک کالا — PRD-26. هر بخش خالی یعنی «ثبت نشده»، نه صفر. */
data class ShelfLocation(val aisle: String = "", val shelf: String = "", val level: String = "") {

  enum class Part { AISLE, SHELF, LEVEL }

  val isEmpty: Boolean get() = aisle.isEmpty() && shelf.isEmpty() && level.isEmpty()

  /** بخش‌های پُر، به ترتیبِ راهرو ⇐ قفسه ⇐ طبقه — برچسبِ هر بخش با زبانِ صفحه کارِ برنامه است */
  fun parts(): List<Pair<Part, String>> = buildList {
    if (aisle.isNotEmpty()) add(Part.AISLE to aisle)
    if (shelf.isNotEmpty()) add(Part.SHELF to shelf)
    if (level.isNotEmpty()) add(Part.LEVEL to level)
  }

  companion object {
    /** سقفِ نویسهٔ هر بخش — برچسبِ کوتاه، نه یادداشت */
    const val MAX = 12

    private fun part(s: String?): String =
      s.orEmpty().trim { it.isWhitespace() || it == '‌' }.split(Regex("\\s+")).joinToString(" ").take(MAX).trim()

    /** از کادرها یا از مدلِ دفتر (`null` = خالی) */
    fun of(aisle: String?, shelf: String?, level: String?): ShelfLocation =
      ShelfLocation(part(aisle), part(shelf), part(level))
  }
}

/**
 *  همهٔ این‌ها برای یک کالا — شکلِ تمیزِ فرم. `of` کادرها را تمیز می‌کند؛ [isEmpty] یعنی
 *  هیچ‌چیز ثبت نشده (مدل همه را `null` می‌گذارد و ردیف بایت‌به‌بایت همان پیشین است).
 */
data class ProductInfo(
  val namePs: String = "",
  val nameEn: String = "",
  val synonyms: List<String> = emptyList(),
  val keywords: List<String> = emptyList(),
  val location: ShelfLocation = ShelfLocation(),
  /** PRD-24 — شناسهٔ کالاهای جایگزین به ترتیبِ ترجیح (`ProductAlternatives`) */
  val alternatives: List<String> = emptyList(),
  /** PRD-25 — فیلدهای سفارشی «نام: مقدار» به ترتیبِ ورود (`CustomFields`) */
  val custom: List<Pair<String, String>> = emptyList(),
) {
  val isEmpty: Boolean
    get() = namePs.isEmpty() && nameEn.isEmpty() && synonyms.isEmpty() && keywords.isEmpty() && location.isEmpty &&
      alternatives.isEmpty() && custom.isEmpty()

  /** نام‌های دیگرِ کالا (پشتو، انگلیسی، مترادف‌ها) — در جست‌وجو هم‌پایهٔ نام */
  val aliases: List<String> get() = listOfNotNull(namePs.ifEmpty { null }, nameEn.ifEmpty { null }) + synonyms

  /** واژه‌های جست‌وجو و مقدارِ فیلدهای سفارشی — در جست‌وجو «همهٔ کلمه‌ها» */
  val searchWords: List<String> get() = keywords + custom.map { it.second }

  /** پرس‌وجو در یکی از نام‌های دیگر، واژه‌ها یا مقدارِ فیلدِ سفارشی هست؟ (نامِ دری کارِ همان جست‌وجوی پیشین است) */
  fun matches(query: String): Boolean = ProductTerms.matchesAny(aliases + searchWords, query)

  companion object {
    fun of(
      namePs: String?,
      nameEn: String?,
      synonyms: String?,
      keywords: String?,
      aisle: String?,
      shelf: String?,
      level: String?,
      alternatives: List<String> = emptyList(),
      custom: String? = null,
    ): ProductInfo = ProductInfo(
      namePs = ProductTerms.cleanName(namePs.orEmpty()),
      nameEn = ProductTerms.cleanName(nameEn.orEmpty()),
      synonyms = ProductTerms.parse(synonyms.orEmpty()),
      keywords = ProductTerms.parse(keywords.orEmpty()),
      location = ShelfLocation.of(aisle, shelf, level),
      alternatives = alternatives.distinct().take(ProductAlternatives.MAX),
      custom = CustomFields.parse(custom.orEmpty()).fields,
    )
  }
}
