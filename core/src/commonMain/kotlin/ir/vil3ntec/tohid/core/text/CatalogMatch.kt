package ir.vil3ntec.tohid.core.text

/**
 *  جست‌وجوی مقاومِ کاتالوگِ صندوق — POS-04 (بخش‌های ۳.۱۳ و ۶.۱).
 *
 *  همان قاعده‌های `TextNormalizer` (همتای `text-core.js`): «ي/ی»، «ك/ک»، «ۀ/ه»، «أ/ا»، رقم‌های
 *  فارسی/عربی/لاتین، نیم‌فاصله و اعراب یکی‌اند؛ هر کلمهٔ پرس‌وجو (یا مترادفش) به هر ترتیب جایی
 *  در نام؛ و اگر چیزی پیدا نشد، غلطِ تایپی در بودجهٔ `typoBudget`.
 *
 *  ── چرا نمایه ──────────────────────────────────────────────────────
 *  کلیدها فقط **یک بار با هر تغییرِ کاتالوگ** ساخته می‌شوند (`index`)؛ هر حرف فقط چند `contains`
 *  روی رشته‌های آماده است (بودجهٔ ۱۵۰ میلی‌ثانیه با ۲۰ هزار کالا، POS-42). فاصلهٔ ویرایشی فقط
 *  وقتی سنجیده می‌شود که جورهای دقیق کم‌اند ([search]، `fuzzyBelow`).
 *
 *  ── ترتیب ──────────────────────────────────────────────────────────
 *  ۱. بارکدِ کامل؛ ۲. نامی که با پرس‌وجو آغاز می‌شود؛ ۳. همهٔ کلمه‌ها در نام (یا مترادفشان)؛
 *  ۴. آغازِ بارکد؛ ۵. غلطِ تایپی. در هر گروه به ترتیبِ نام (کلیدِ مقایسه) و سپس شناسه —
 *  پس Enter همیشه همان نخستینِ قابلِ پیش‌بینی را برمی‌دارد.
 *
 *  مترادف‌ها (`[["کچالو","سیب‌زمینی"], …]`) از بیرون می‌آیند و این‌جا ثابت نوشته نمی‌شوند.
 *
 *  PRD-02: هر کالا می‌تواند «نام‌های دیگر» (`Item.aliases`: پشتو، انگلیسی، مترادف‌های همان کالا) و
 *  «واژهٔ جست‌وجو» (`Item.keywords`) داشته باشد. نامِ دیگر هم‌پایهٔ نام است (گروهِ «آغازِ نام» و
 *  «همهٔ کلمه‌ها»)؛ واژه فقط در «همهٔ کلمه‌ها» و غلطِ تایپی. ترتیبِ درونِ گروه همان نامِ دری می‌ماند.
 *  هر دو تهی (پیش‌فرض) ⇒ همان نمایه و همان نتیجهٔ پیشین.
 *  Kotlin خالص (commonMain، کنارِ `TextNormalizer`) — بی java.* و android.*.
 */
object CatalogMatch {

  /** یک کالا برای ساختنِ نمایه */
  data class Item(
    val id: String,
    val name: String,
    val barcodes: List<String> = emptyList(),
    /** PRD-02 — نام‌های دیگرِ همین کالا (پشتو، انگلیسی، مترادف) */
    val aliases: List<String> = emptyList(),
    /** PRD-02 — واژه‌های جست‌وجو (برند، نوع…) */
    val keywords: List<String> = emptyList(),
  )

  /** کالای آماده: کلیدهای نرمالِ نام، کلمه‌ها و بارکدها */
  class Entry internal constructor(
    val id: String,
    internal val nameKey: String,
    internal val words: List<String>,
    internal val codes: List<String>,
    /** کلیدِ مقایسهٔ نام‌های دیگر — مثلِ [nameKey] */
    internal val aliasKeys: List<String> = emptyList(),
    /** کلیدِ مقایسهٔ واژه‌های جست‌وجو */
    internal val keywordKeys: List<String> = emptyList(),
  ) {
    /** کلید در نام، نامِ دیگر یا واژه هست؟ — هر متن جدا، تا تکهٔ دو متن جورِ ساختگی نسازد */
    internal fun has(key: String): Boolean =
      nameKey.contains(key) || aliasKeys.any { it.contains(key) } || keywordKeys.any { it.contains(key) }

    internal fun startsWith(key: String): Boolean = nameKey.startsWith(key) || aliasKeys.any { it.startsWith(key) }
  }

  class Index internal constructor(val entries: List<Entry>) {
    val size: Int get() = entries.size
  }

  /** یک بار با هر تغییرِ کاتالوگ */
  fun index(items: List<Item>): Index = Index(
    items.map { item ->
      val extra = item.aliases + item.keywords
      Entry(
        id = item.id,
        nameKey = TextNormalizer.compareKey(item.name),
        words = (listOf(item.name) + extra).flatMap { TextNormalizer.searchKey(it).split(' ') }.filter { it.isNotEmpty() }.distinct(),
        codes = item.barcodes.map { TextNormalizer.compareKey(it) }.filter { it.isNotEmpty() },
        aliasKeys = item.aliases.map { TextNormalizer.compareKey(it) }.filter { it.isNotEmpty() },
        keywordKeys = item.keywords.map { TextNormalizer.compareKey(it) }.filter { it.isNotEmpty() },
      )
    }.sortedWith(compareBy<Entry> { it.nameKey }.thenBy { it.id }),
  )

  /** گروهِ مترادف‌های یک کلید (خودش هم هست) — همان `TextNormalizer.matches` */
  private fun alternatives(key: String, groups: List<List<String>>): List<String> {
    if (groups.isEmpty()) return listOf(key)
    val out = mutableListOf(key)
    for (g in groups) if (key in g) for (k in g) if (k !in out) out.add(k)
    return out
  }

  /**
   *  شناسه‌های جورشده به ترتیبِ نمایش؛ پرس‌وجوی خالی ⇒ هیچ.
   *  @param fuzzyBelow غلطِ تایپی فقط وقتی جورهای دقیق کمتر از این‌اند (۰ ⇒ هرگز)
   */
  fun search(index: Index, query: String, synonyms: List<List<String>>? = null, fuzzyBelow: Int = 8): List<String> {
    val q = TextNormalizer.searchKey(query).trim()
    if (q.isEmpty()) return emptyList()
    val whole = q.replace(" ", "")
    val tokens = q.split(' ').filter { it.isNotEmpty() }
    val groups = synonyms.orEmpty().map { g -> g.map { TextNormalizer.compareKey(it) }.filter { it.isNotEmpty() } }
    val wholeAlt = alternatives(whole, groups)
    val tokenAlt = tokens.map { alternatives(it, groups) }

    val code = ArrayList<String>()
    val prefix = ArrayList<String>()
    val words = ArrayList<String>()
    val codePrefix = ArrayList<String>()
    val rest = ArrayList<Entry>()
    for (e in index.entries) {
      when {
        e.codes.any { it == whole } -> code += e.id
        e.startsWith(whole) -> prefix += e.id
        wholeAlt.any { e.has(it) } || tokenAlt.all { alt -> alt.any { e.has(it) } } -> words += e.id
        e.codes.any { it.startsWith(whole) } -> codePrefix += e.id
        else -> rest += e
      }
    }
    val exact = code + prefix + words + codePrefix
    if (exact.size >= fuzzyBelow) return exact
    val typo = ArrayList<String>()
    //  کلمه‌های کاتالوگ بسیار تکراری‌اند (برند، واحد، اندازه): فاصلهٔ هر (کلمهٔ پرس‌وجو، کلمهٔ نام) یک بار
    val near = tokens.map { HashMap<String, Boolean>() }
    for (e in rest) if (fuzzy(e, tokens, near)) typo += e.id
    return exact + typo
  }

  /** هر کلمه یا در نام هست، یا با کلمه‌ای از نام در بودجهٔ غلطِ تایپی فاصله دارد — همان `fuzzyMatches` */
  private fun fuzzy(e: Entry, tokens: List<String>, near: List<HashMap<String, Boolean>>): Boolean =
    tokens.indices.all { i ->
      val t = tokens[i]
      if (e.has(t)) return@all true
      val budget = TextNormalizer.typoBudget(t.length)
      budget > 0 && e.words.any { w ->
        near[i].getOrPut(w) { kotlin.math.abs(w.length - t.length) <= budget && distance(w, t, budget) <= budget }
      }
    }

  /**
   *  همان فاصلهٔ `TextNormalizer.editDistance` روی کلیدهای از پیش نرمال (کلمه‌های نمایه و پرس‌وجو
   *  فاصله ندارند، پس `compareKey` شان خودشان است) — بی نرمال‌سازیِ دوباره و تخصیص در هر کالا، تا
   *  غلطِ تایپی روی ۲۰ هزار کالا در بودجه بماند. بیش از [max] ⇒ max+1، زود دست می‌کشد.
   */
  internal fun distance(x: String, y: String, max: Int): Int {
    if (kotlin.math.abs(x.length - y.length) > max) return max + 1
    var prev = IntArray(y.length + 1) { it }
    var cur = IntArray(y.length + 1)
    for (i in 1..x.length) {
      cur[0] = i
      var best = i
      for (j in 1..y.length) {
        val cost = if (x[i - 1] == y[j - 1]) 0 else 1
        val v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        cur[j] = v
        if (v < best) best = v
      }
      if (best > max) return max + 1
      val t = prev; prev = cur; cur = t
    }
    return if (prev[y.length] > max) max + 1 else prev[y.length]
  }
}
