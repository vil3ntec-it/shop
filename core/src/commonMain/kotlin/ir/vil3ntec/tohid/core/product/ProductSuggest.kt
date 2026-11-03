package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.text.TextNormalizer

/*
 *  AI-06 (§۶.۲۲) — «پیشنهادِ دسته و نام» هنگامِ ثبتِ کالا، فقط با قاعده و فقط از دفترِ همین دکان. نه مدلِ زبانی، نه
 *  شبکه؛ پس نامش «پیشنهاد» است، نه «هوش مصنوعی».
 *
 *  ── دسته از نام ([Index]) ───────────────────────────────────────────
 *  هر واژهٔ نامِ تازه (کلیدِ `TextNormalizer.searchKey`؛ رقم، واحد و واژهٔ پیوندی کنار) در نامِ کدام کالاهای دکان آمده و آن
 *  کالاها در کدام دسته‌اند. وزنِ هر واژه برای هر دسته = شمارِ آن کالاها ÷ شمارِ دسته‌هایی که آن واژه را دارند (واژهٔ
 *  همه‌جایی کم‌وزن)؛ واژه‌ای که در نامِ **خودِ** دسته باشد یک واحد بیشتر. ترتیب: امتیاز، شمارِ کالا، نامِ دسته.
 *  ⛔ فقط دسته‌ای که هست — دستهٔ تازه هرگز ساخته نمی‌شود. حداکثر [MAX_PICKS]، هر کدام با دلیل (واژه و شمار).
 *
 *  ── نام از متن ([paste]) ────────────────────────────────────────────
 *  متنِ چسبانده‌شده (فهرستِ تأمین‌کننده، پیام) سطر به سطر؛ شمارهٔ سرِ سطر («۱.»، «-»، «•») کنار، و از هر سطر فقط
 *  نخستین پاره پیش از «،» «؛» «;» «|» یا « - » نام است. عددِ بارکدمانند (۸ تا ۱۴ رقم) و مبلغ با واحدِ پول («۶۵۰ افغانی»،
 *  «؋۶۵۰»، «قیمت: ۶۵۰») از نام برداشته می‌شوند و **هیچ‌جا نوشته نمی‌شوند** — فقط به کاربر گفته می‌شود که برداشته شد.
 *
 *  ⛔ قیمت، بارکد، وزن، برند، تاریخِ انقضا و مشخصات هرگز ساخته نمی‌شوند (§۱.۳ بندِ ۱۲): نام فقط واژه‌های خودِ متن است و
 *     حروف عوض نمی‌شوند (در پشتو «ي» و «ی» دو حرف‌اند)؛ مقدار و واحدِ درونِ نام («۵ لیتر») همان می‌ماند.
 *  ⛔ هیچ پیشنهادی بی زدنِ کاربر کادری را پر نمی‌کند (تأییدِ انسان).
 *  منطقِ خالص (commonMain، بی java.* و android.*)؛ آزمون: `ProductSuggestTest`.
 */
object ProductSuggest {

  /** بیشترین دستهٔ پیشنهادی */
  const val MAX_PICKS = 3

  /** بیشترین سطرِ یک متن */
  const val MAX_LINES = 100

  /** سقفِ نویسهٔ نامِ پیشنهادی (همان سقفِ نام‌های دیگرِ کالا، `ProductTerms.MAX_NAME`) */
  const val MAX_NAME = 120

  /** کالای دفتر: فقط نام و دسته */
  data class Known(val name: String, val category: String)

  /**
   *  یک دستهٔ پیشنهادی و دلیلش: [word] واژهٔ نام که بیشترین کالای این دسته را داشت و [count] شمارِ آن کالاها؛
   *  [byName] یعنی همین واژه در نامِ خودِ دسته است.
   */
  data class Pick(val category: String, val word: String, val count: Int, val byName: Boolean)

  private val STOP: Set<String> = setOf(
    //  واحد و بسته (بیشترِ دسته‌ها دارند؛ وزن و حجم مالِ خودِ کالاست، نه دسته)
    "کیلو", "کیلوگرم", "کیلویی", "کیلوگرمی", "گرم", "گرمی", "لیتر", "لیتری", "میلی", "میلیلیتر", "سیسی", "عدد", "عددی",
    "دانه", "دانهای", "بسته", "بستهای", "کارتن", "کارتنی", "جعبه", "متر", "متری", "سانتی", "سانتیمتر",
    "kg", "gr", "ml", "ltr", "pcs", "pc", "cm",
    //  واژهٔ پیوندی (دری، پشتو، انگلیسی)
    "و", "با", "از", "برای", "در", "به", "یک", "او", "په", "له", "سره", "لپاره", "and", "the", "of", "with", "for",
  )

  /** نویسه‌های دورِ واژه که در نمایه به کار نمی‌آیند */
  private const val EDGE = "()[]{}«»\"'.,،;؛:!?؟-–—*•|/\\+=_#"

  /** واژه‌های نمایه‌شدنیِ یک نام (کلیدِ جست‌وجو)، بی تکرار */
  internal fun words(name: String): List<String> =
    TextNormalizer.searchKey(name).split(' ')
      .map { it.trim { c -> EDGE.indexOf(c) >= 0 } }
      .filter { w -> w.length >= 2 && w.none { it in '0'..'9' } && w !in STOP }
      .distinct()

  /**
   *  نمایهٔ دسته‌های دکان. [categories] فهرستِ دسته‌ها (دستهٔ بی کالا هم، برای جورِ نام)؛ دستهٔ هر کالا هم خودش
   *  شمرده می‌شود. ساختن یک بار برای هر دفتر؛ پرسش هر بار فقط به شمارِ واژه‌های نام.
   */
  class Index(products: List<Known>, categories: List<String> = emptyList()) {
    /** واژه ⇐ (دسته ⇐ شمارِ کالا) */
    private val byWord = HashMap<String, HashMap<String, Int>>()

    /** واژهٔ نامِ دسته ⇐ دسته‌ها */
    private val byCategoryName = HashMap<String, MutableSet<String>>()

    init {
      val all = LinkedHashSet<String>()
      categories.forEach { c -> c.trim().takeIf { it.isNotEmpty() }?.let(all::add) }
      for (p in products) {
        val cat = p.category.trim()
        if (cat.isEmpty()) continue
        all += cat
        for (w in words(p.name)) {
          val m = byWord.getOrPut(w) { HashMap() }
          m[cat] = (m[cat] ?: 0) + 1
        }
      }
      for (cat in all) for (w in words(cat)) byCategoryName.getOrPut(w) { LinkedHashSet() } += cat
    }

    val isEmpty: Boolean get() = byWord.isEmpty() && byCategoryName.isEmpty()

    /** دسته‌های پیشنهادی برای [name] — بی واژهٔ شناخته ⇒ هیچ */
    fun categories(name: String, limit: Int = MAX_PICKS): List<Pick> {
      if (limit <= 0) return emptyList()
      val score = HashMap<String, Double>()
      val total = HashMap<String, Int>()
      val best = HashMap<String, Pick>()
      for (w in words(name)) {
        val cats = byWord[w]
        if (cats != null) {
          val spread = cats.size.toDouble()
          for ((cat, n) in cats) {
            score[cat] = (score[cat] ?: 0.0) + n / spread
            total[cat] = (total[cat] ?: 0) + n
            val b = best[cat]
            if (b == null || (!b.byName && n > b.count)) best[cat] = Pick(cat, w, n, byName = false)
          }
        }
        byCategoryName[w]?.forEach { cat ->
          score[cat] = (score[cat] ?: 0.0) + 1.0
          val b = best[cat]
          //  نامِ خودِ دسته روشن‌ترین دلیل است
          if (b == null || !b.byName) best[cat] = Pick(cat, w, byWord[w]?.get(cat) ?: 0, byName = true)
        }
      }
      return score.keys
        .sortedWith(compareByDescending<String> { score[it] ?: 0.0 }.thenByDescending { total[it] ?: 0 }.thenBy { it })
        .take(limit)
        .mapNotNull { best[it] }
    }
  }

  // ── نام از متن ────────────────────────────────────────────────

  /** آنچه از یک سطر برداشته شد (و هیچ‌جا نوشته نشد) */
  enum class Dropped { PRICE, BARCODE }

  /** نامِ پیشنهادیِ یک سطر؛ [exists] ⇐ نامی عیناً همین در دکان هست (موتور ردش می‌کند) */
  data class Line(val name: String, val dropped: Set<Dropped>, val exists: Boolean)

  /** [skipped] سطرِ نوشته‌داری که نامی نداشت (فقط عدد/قیمت/بارکد)؛ [truncated] بیش از [MAX_LINES] سطر */
  data class Paste(val lines: List<Line>, val skipped: Int, val truncated: Boolean) {
    /** سطرهایی که ردیف می‌شوند */
    val fresh: List<Line> get() = lines.filterNot { it.exists }
  }

  /** واحدِ پول پس از عدد («۶۵۰ افغانی») */
  private val CURRENCY: List<String> = listOf(
    "افغانیگی", "افغانی", "افغانۍ", "afghani", "afn", "afs", "af", "؋", "روپیه", "کلدار", "تومان", "ریال", "دالر", "دلار", "usd", "$",
  )

  /**
   *  واحدِ پولی که پیش از عدد هم می‌آید («؋ ۶۵۰»، «AFN 650»، «$5») — فقط نشانه و کد؛ «افغانی» ِ پیش از عدد صفت است
   *  («چای افغانی ۵۰۰ گرم»)، نه قیمت.
   */
  private val PREFIX_CURRENCY: List<String> = listOf("؋", "$", "afn", "afs", "usd")

  private val PRICE_WORDS: Set<String> = setOf("قیمت", "قیمتش", "نرخ", "بها", "فی", "بیه", "price")

  private val NUMBER = Regex("^[0-9]+(?:[.,٬٫][0-9]+)*$")

  /** سرِ سطر: «-»، «•»، «۱.»، «2)» — نه «12.5 کیلو» */
  private val BULLET = Regex("^\\s*(?:[-–—•*·▪●]+\\s*|[0-9۰-۹٠-٩]{1,3}\\s*[.)\\-:](?![0-9۰-۹٠-٩])\\s*)")

  /** جداکننده‌های پاره: «،» «؛» «;» «|» و «,» ِ پیش از فاصله (نه «1,500») و خطِ تیرهٔ میانِ دو فاصله */
  private val SEGMENT = Regex("[،؛;|\\t]|,(?=\\s|$)|\\s[-–—]\\s")

  /** نویسه‌هایی که از دو سرِ نام بریده می‌شوند (پرانتز و گیومه نه — بخشی از نام‌اند) */
  private const val NAME_EDGE = " .,،;؛:-–—*•|"

  /** جداکننده‌ای که با برداشتنِ واژه‌اش نباید گم شود */
  private const val KEEP_SEPARATORS = "،,؛;|"

  private fun latin(s: String): String = buildString(s.length) {
    for (c in s) append(
      when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c
      },
    )
  }

  /** کلیدِ ردهٔ یک واژه: رقمِ لاتین، حروفِ کوچک، بی نویسهٔ دور */
  private fun core(token: String): String = latin(token).trim { EDGE.indexOf(it) >= 0 }.lowercase()

  private fun isNumber(k: String): Boolean = k.isNotEmpty() && NUMBER.matches(k)

  private fun isBarcode(k: String): Boolean = k.length in 8..14 && k.all { it in '0'..'9' }

  private fun isCurrency(k: String): Boolean = k.isNotEmpty() && k in CURRENCY

  /** «۶۵۰؋»، «$5»، «650afn» — عدد و واحدِ پولِ چسبیده */
  private fun isGluedPrice(k: String): Boolean =
    CURRENCY.any { c -> k.length > c.length && k.endsWith(c) && isNumber(k.dropLast(c.length)) } ||
      PREFIX_CURRENCY.any { c -> k.length > c.length && k.startsWith(c) && isNumber(k.drop(c.length)) }

  /** «قیمت:650»، «نرخ=650» */
  private fun priceWordGlued(k: String): Boolean = PRICE_WORDS.any { w ->
    k.startsWith(w) && k.length > w.length && k.drop(w.length).trimStart(':', '：', '=').let { isNumber(it) || isGluedPrice(it) }
  }

  private fun isPriceWord(k: String): Boolean = k.trimEnd(':', '：', '=') in PRICE_WORDS

  /** جداکننده‌های پایانِ واژه (تا پاره‌ها با برداشتنِ مبلغ به هم نچسبند) */
  private fun tail(token: String): String = token.takeLastWhile { KEEP_SEPARATORS.indexOf(it) >= 0 || it == ')' || it == '»' }
    .filter { KEEP_SEPARATORS.indexOf(it) >= 0 }

  /** واژه‌های سطر با همان فاصله‌های `TextNormalizer` (نیم‌فاصله درونِ واژه می‌ماند) */
  private fun tokens(line: String): List<String> {
    val out = ArrayList<String>()
    val b = StringBuilder()
    for (c in line) {
      if (TextNormalizer.isSpace(c)) { if (b.isNotEmpty()) { out += b.toString(); b.clear() } } else b.append(c)
    }
    if (b.isNotEmpty()) out += b.toString()
    return out
  }

  /** یک سطر ⇐ نام (یا خالی) و آنچه برداشته شد */
  internal fun nameOf(raw: String): Pair<String, Set<Dropped>> {
    val dropped = LinkedHashSet<Dropped>()
    val tokens = tokens(raw.replaceFirst(BULLET, ""))
    val keep = ArrayList<String>(tokens.size)
    var i = 0
    /** برداشتنِ واژه‌ها تا [last]؛ جداکنندهٔ پایانِ آخرینشان می‌ماند */
    fun drop(kind: Dropped, last: Int) {
      dropped += kind
      tail(tokens[last]).takeIf { it.isNotEmpty() }?.let(keep::add)
    }
    while (i < tokens.size) {
      val k = core(tokens[i])
      val next = tokens.getOrNull(i + 1)?.let(::core).orEmpty()
      val after = tokens.getOrNull(i + 2)?.let(::core).orEmpty()
      when {
        isBarcode(k) -> { drop(Dropped.BARCODE, i); i += 1 }
        isGluedPrice(k) || priceWordGlued(k) -> {
          val n = if (isCurrency(next)) 2 else 1
          drop(Dropped.PRICE, i + n - 1); i += n
        }
        isPriceWord(k) && (isNumber(next) || isGluedPrice(next)) -> {
          val n = if (isCurrency(after)) 3 else 2
          drop(Dropped.PRICE, i + n - 1); i += n
        }
        isNumber(k) && isCurrency(next) -> { drop(Dropped.PRICE, i + 1); i += 2 }
        k in PREFIX_CURRENCY && isNumber(next) -> { drop(Dropped.PRICE, i + 1); i += 2 }
        //  «؋» یا «AFN» ِ تنها؛ «افغانی» ِ تنها صفت است و می‌ماند
        k in PREFIX_CURRENCY -> { drop(Dropped.PRICE, i); i += 1 }
        else -> { keep += tokens[i]; i += 1 }
      }
    }
    val first = keep.joinToString(" ").split(SEGMENT).map { it.trim() }.firstOrNull { it.trim { c -> NAME_EDGE.indexOf(c) >= 0 }.isNotEmpty() }
    val name = tokens(first.orEmpty()).joinToString(" ")
      .trim { NAME_EDGE.indexOf(it) >= 0 }
      .take(MAX_NAME).trim { NAME_EDGE.indexOf(it) >= 0 }
    return name to dropped
  }

  /**
   *  متن ⇐ نام‌های پیشنهادی، به ترتیبِ سطرها؛ نامِ تکراریِ همین متن یک بار. [existing] نام‌های کالاهای دکان (با همان
   *  کلیدِ «یکسان» ِ موتور، `SimilarNames.exactKey`).
   */
  fun paste(text: String, existing: Collection<String> = emptyList()): Paste = paste(text, keysOf(existing))

  /** کلیدهای «یکسان» ِ نام‌های دکان — یک بار برای هر دفتر، نه با هر نویسه */
  fun keysOf(names: Collection<String>): Set<String> = names.mapTo(HashSet(), SimilarNames::exactKey)

  /** همان [paste] با کلیدهای از پیش ساخته ([keysOf]) */
  fun paste(text: String, have: Set<String>): Paste {
    val raw = text.split('\n', '\r').filter { it.isNotBlank() }
    val seen = HashSet<String>()
    val lines = ArrayList<Line>()
    var skipped = 0
    for (r in raw.take(MAX_LINES)) {
      val (name, dropped) = nameOf(r)
      if (name.isEmpty()) { skipped++; continue }
      val key = SimilarNames.exactKey(name)
      if (!seen.add(key)) continue
      lines += Line(name, dropped, exists = key in have)
    }
    return Paste(lines, skipped, truncated = raw.size > MAX_LINES)
  }
}
