package ir.vil3ntec.tohid.core.text

/**
 *  کلیدِ جست‌وجو و کلیدِ مقایسه (بخشِ ۳.۱۳).
 *
 *  ⛔ متنِ ذخیره‌شده هرگز عوض نمی‌شود. نرمال‌سازی فقط روی «کلید» است —
 *  در پشتو «ي» و «ی» دو حرفِ جدا‌اند و متنِ اصلی باید همان بماند.
 *
 *  همتای دقیقِ `license/text-core.js` ِ وب؛ هر دو با
 *  `core/test-vectors/text-normalize.json` سنجیده می‌شوند. رفتارِ یکی را
 *  بی دیگری و بی فایلِ مرجع عوض نکنید.
 *
 *  همهٔ حروفِ عربی‌نویس در BMP‌اند، پس کار روی `Char` (UTF-16) همان کارِ
 *  `charCodeAt` در JS است و دو پیاده‌سازی یک نتیجه می‌دهند.
 */
object TextNormalizer {

  /** نویسه‌های نامرئیِ کپی/چسباندن: نیم‌فاصله، ZWJ، نشانه‌های جهت، BOM، خط‌تیرهٔ نرم */
  internal fun isInvisible(c: Char): Boolean =
    c == '‌' || c == '‍' || c == '‎' || c == '‏' || c == '؜' ||
      c in '‪'..'‮' || c in '⁦'..'⁩' || c == '﻿' || c == '­'

  /** همان فهرستِ فاصله‌های JS — عمداً نه `isWhitespace` ِ JVM که با مرورگر فرق دارد */
  internal fun isSpace(c: Char): Boolean =
    c == ' ' || c in '\u0009'..'\u000D' || c == ' ' || c == ' ' ||
      c in ' '..' ' || c == ' ' || c == ' ' || c == ' ' ||
      c == ' ' || c == '　'

  /**
   *  کلیدِ جست‌وجو: حروفِ هم‌ارز یکی، اعراب و کشیده حذف، نیم‌فاصله حذف،
   *  ارقام لاتین، «٫» و «,» نقطه، حروفِ لاتین کوچک، فاصله‌ها یکی.
   */
  fun searchKey(text: String): String {
    val out = StringBuilder(text.length)
    var pendingSpace = false
    for (c in text) {
      val ch: Char = when {
        c == 'ي' || c == 'ى' -> 'ی'                       // ي ى → ی
        c == 'ك' -> 'ک'                                        // ك → ک
        c == 'ۀ' -> 'ه'                                        // ۀ → ه
        c == 'أ' || c == 'إ' || c == 'آ' -> 'ا'      // أ إ آ → ا
        c in 'ً'..'ٟ' || c == 'ٰ' || c == 'ـ' -> continue  // اعراب، کشیده
        c in '۰'..'۹' -> '0' + (c - '۰')
        c in '٠'..'٩' -> '0' + (c - '٠')
        c == '٫' || c == ',' -> '.'                                 // ٫ و , → .
        isInvisible(c) -> continue
        isSpace(c) -> { pendingSpace = out.isNotEmpty(); continue }
        c in 'A'..'Z' -> c + 32                                          // فقط ASCII؛ بی‌وابستگی به زبانِ دستگاه
        else -> c
      }
      if (pendingSpace) { out.append(' '); pendingSpace = false }
      out.append(ch)
    }
    return out.toString()
  }

  /** کلیدِ مقایسه: همان کلیدِ جست‌وجو بی هیچ فاصله — «می‌خواهم» = «میخواهم» = «می خواهم» */
  fun compareKey(text: String): String = searchKey(text).replace(" ", "")

  /** گروهِ مترادف‌های یک کلید (خودش هم هست) */
  private fun alternatives(key: String, groups: List<List<String>>?): List<String> {
    val out = mutableListOf(key)
    groups ?: return out
    for (group in groups) {
      val keys = group.map(::compareKey).filter { it.isNotEmpty() }
      if (key !in keys) continue
      for (k in keys) if (k !in out) out.add(k)
    }
    return out
  }

  /**
   *  آیا متن با پرس‌وجو جور است: هر کلمهٔ پرس‌وجو (یا مترادفش) جایی در
   *  کلیدِ مقایسهٔ متن باشد، به هر ترتیب. اگر کلِ پرس‌وجو خودش یک مترادف
   *  باشد («بادنجان رومی»)، مترادفِ کامل هم پذیرفته است. پرس‌وجوی خالی همه را می‌گیرد.
   *
   *  synonyms: فرهنگِ مترادف‌ها، مثلاً `[["کچالو","سیب‌زمینی"], …]`. فرهنگ از
   *  پنل می‌آید و این‌جا ثابت نوشته نمی‌شود.
   */
  fun matches(text: String, query: String, synonyms: List<List<String>>? = null): Boolean {
    val q = searchKey(query)
    if (q.isEmpty()) return true
    val hay = compareKey(text)
    if (alternatives(q.replace(" ", ""), synonyms).any { hay.contains(it) }) return true
    return q.split(' ').all { token -> alternatives(token, synonyms).any { hay.contains(it) } }
  }

  /**
   *  فاصلهٔ ویرایشیِ محدود (Levenshtein) روی کلیدِ مقایسه. اگر از max
   *  بیشتر شود همان max+1 برمی‌گردد — زود دست می‌کشد.
   */
  fun editDistance(a: String, b: String, max: Int): Int {
    val x = compareKey(a)
    val y = compareKey(b)
    if (kotlin.math.abs(x.length - y.length) > max) return max + 1
    var prev = IntArray(y.length + 1) { it }
    for (i in 1..x.length) {
      val cur = IntArray(y.length + 1)
      cur[0] = i
      var best = i
      for (j in 1..y.length) {
        val cost = if (x[i - 1] == y[j - 1]) 0 else 1
        val v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        cur[j] = v
        if (v < best) best = v
      }
      if (best > max) return max + 1
      prev = cur
    }
    return if (prev[y.length] > max) max + 1 else prev[y.length]
  }

  /** چند غلطِ تایپی برای کلمه‌ای به این طول بخشیده می‌شود: ≤۳ هیچ، ۴ تا ۷ یکی، ۸ به بالا دو */
  fun typoBudget(length: Int): Int = if (length <= 3) 0 else if (length <= 7) 1 else 2

  /**
   *  جست‌وجوی بخشنده: هر کلمهٔ پرس‌وجو یا در متن هست، یا با کلمه‌ای از
   *  متن در بودجهٔ غلطِ تایپی فاصله دارد.
   */
  fun fuzzyMatches(text: String, query: String): Boolean {
    val q = searchKey(query)
    if (q.isEmpty()) return true
    val hay = compareKey(text)
    val words = searchKey(text).split(' ')
    return q.split(' ').all { t ->
      if (hay.contains(t)) return@all true
      val budget = typoBudget(t.length)
      budget > 0 && words.any { editDistance(it, t, budget) <= budget }
    }
  }
}
