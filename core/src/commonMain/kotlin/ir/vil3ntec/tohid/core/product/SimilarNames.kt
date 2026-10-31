package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.text.TextNormalizer

/*
 *  «نامِ مشابه» هنگامِ ثبت و واردسازیِ کالا — PRD-19 (§۶.۳).
 *
 *  نامِ **یکسان** را خودِ موتور رد می‌کند (`WarehouseEngine.duplicateName`)؛ این‌جا فقط
 *  **هشدار** است برای نامی که احتمالاً همان کالاست ولی عیناً همان نوشته نشده:
 *    • SAME_LETTERS — همان حروف با فاصله/نیم‌فاصله/اعراب/«ي ك» ِ عربی/حروفِ بزرگِ دیگر
 *                     («رب‌گوجه» و «رب گوجه»).
 *    • WORD_ORDER   — همان کلمه‌ها با ترتیبِ دیگر («سبز چای» و «چای سبز»).
 *    • SPELLING     — غلطِ تایپیِ کوچک در بودجهٔ همان `TextNormalizer.typoBudget` ِ جست‌وجو
 *                     (۴ تا ۷ نویسه یک غلط، ۸ به بالا دو؛ کوتاه‌تر هیچ).
 *  ⛔ عدد هرگز «غلطِ تایپی» نیست: «روغن ۱ لیتری» و «روغن ۵ لیتری» دو کالای جدا‌اند؛ اگر رقم‌ها
 *     فرق کنند، مشابه نیست.
 *  ⛔ متنِ ذخیره‌شده دست نمی‌خورد؛ مقایسه فقط روی کلیدهای `TextNormalizer` است.
 *
 *  `Index` برای فهرستِ بزرگ (۲۰ هزار کالا و واردسازیِ هزار ردیفی): نگاشتِ کلید و ترتیبِ کلمه،
 *  و برای غلطِ تایپی فقط هم‌طول‌ها (±بودجه) با صافیِ شمارشِ حروف پیش از فاصلهٔ ویرایشی.
 *  منطقِ خالص، بی android.*؛ آزمون: `SimilarNamesTest` (commonTest).
 */
object SimilarNames {

  enum class Reason { SAME_LETTERS, WORD_ORDER, SPELLING }

  data class Match(val index: Int, val name: String, val reason: Reason)

  /** همان کلیدِ «یکسان» ِ موتورِ انبار: فاصله‌ها یکی، «ي/ك» ⇐ «ی/ک»، حروفِ کوچک */
  fun exactKey(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").replace('ي', 'ی').replace('ك', 'ک').lowercase()

  private fun digits(key: String): String = key.filter { it in '0'..'9' }

  private fun wordsKey(name: String): String =
    TextNormalizer.searchKey(name).split(' ').filter { it.isNotEmpty() }.sorted().joinToString(" ")

  /** دو نام «مشابه»اند؟ (نامِ عیناً یکسان ⇒ null؛ آن را موتور رد می‌کند) */
  fun reason(a: String, b: String): Reason? {
    if (exactKey(a) == exactKey(b)) return null
    val ka = TextNormalizer.compareKey(a)
    val kb = TextNormalizer.compareKey(b)
    if (ka.isEmpty() || kb.isEmpty()) return null
    if (ka == kb) return Reason.SAME_LETTERS
    if (digits(ka) != digits(kb)) return null
    if (wordsKey(a) == wordsKey(b)) return Reason.WORD_ORDER
    val budget = TextNormalizer.typoBudget(minOf(ka.length, kb.length))
    if (budget == 0) return null
    return if (TextNormalizer.editDistance(ka, kb, budget) <= budget) Reason.SPELLING else null
  }

  class Index(private val names: List<String>) {
    private val exact = names.map(::exactKey)
    private val keys = names.map { TextNormalizer.compareKey(it) }
    private val byKey = HashMap<String, MutableList<Int>>()
    private val byWords = HashMap<String, MutableList<Int>>()
    private val byLength = HashMap<Int, MutableList<Int>>()

    init {
      for (i in names.indices) {
        val k = keys[i]
        if (k.isEmpty()) continue
        byKey.getOrPut(k) { ArrayList() } += i
        byWords.getOrPut(wordsKey(names[i])) { ArrayList() } += i
        byLength.getOrPut(k.length) { ArrayList() } += i
      }
    }

    /**
     *  نام‌های مشابهِ `name` (حداکثر `limit`)، قوی‌ترین دلیل اول، بعد ترتیبِ فهرست.
     *  `except` همان ردیف (ویرایشِ همان کالا)؛ نامِ عیناً یکسان برگردانده نمی‌شود.
     */
    fun find(name: String, except: Int = -1, limit: Int = 3): List<Match> {
      val ex = exactKey(name)
      val k = TextNormalizer.compareKey(name)
      if (k.isEmpty() || limit <= 0) return emptyList()
      val found = LinkedHashMap<Int, Reason>()
      fun take(i: Int, r: Reason) { if (i != except && exact[i] != ex && i !in found) found[i] = r }
      byKey[k]?.forEach { take(it, Reason.SAME_LETTERS) }
      val dk = digits(k)
      byWords[wordsKey(name)]?.forEach { if (digits(keys[it]) == dk) take(it, Reason.WORD_ORDER) }
      if (found.size < limit) {
        val budget = TextNormalizer.typoBudget(k.length)
        if (budget > 0) {
          val counts = charCounts(k)
          for (len in (k.length - budget)..(k.length + budget)) {
            val bucket = byLength[len] ?: continue
            for (i in bucket) {
              if (i in found || i == except || exact[i] == ex) continue
              val other = keys[i]
              if (digits(other) != dk) continue
              //  هر ویرایش شمارِ حروف را حداکثر دو واحد جابه‌جا می‌کند ⇒ صافیِ ارزان پیش از Levenshtein
              if (countDiff(counts, other) > 2 * budget) continue
              val b = TextNormalizer.typoBudget(minOf(k.length, other.length))
              if (b > 0 && TextNormalizer.editDistance(k, other, b) <= b) take(i, Reason.SPELLING)
            }
          }
        }
      }
      return found.entries
        .sortedWith(compareBy({ it.value.ordinal }, { it.key }))
        .take(limit)
        .map { Match(it.key, names[it.key], it.value) }
    }

    private fun charCounts(s: String): HashMap<Char, Int> {
      val m = HashMap<Char, Int>()
      for (c in s) m[c] = (m[c] ?: 0) + 1
      return m
    }

    private fun countDiff(base: Map<Char, Int>, other: String): Int {
      val m = HashMap(base)
      var extra = 0
      for (c in other) {
        val n = m[c] ?: 0
        if (n > 0) m[c] = n - 1 else extra++
      }
      return extra + m.values.sum()
    }
  }
}
