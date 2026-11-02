package ir.vil3ntec.tohid.core.text

/**
 *  LOC-08 — زبانِ «دری» = همان نوشتهٔ فارسی + یک لایهٔ واژه (overlay)، نه نسخهٔ چهارمِ هر نوشته.
 *
 *  فارسی هرگز عوض نمی‌شود؛ وقتی کاربر «دری» را برگزیند، نوشتهٔ فارسی از [of] می‌گذرد:
 *   1. [PHRASES]: نوشتهٔ کاملی که واژه‌به‌واژه درست نمی‌شود ⇒ متنِ دریِ کامل (کلید = خودِ متنِ فارسی).
 *   2. [WORDS]: فقط واژه‌های روشنِ ایرانی که همتای دریِ رایج و بی‌ابهام دارند — واژهٔ کامل، نه تکه‌ای
 *      از واژهٔ دیگر («داستان» ≠ «استان»، «درصدد» ≠ «درصد»)، با پسوندهای شناخته («‌ها»، «ِ»، «ی»، «ان»…).
 *      پسوندِ ناشناخته یا چسبیدن با نیم‌فاصله به واژهٔ پیشین ⇒ همان فارسی می‌ماند (در شک، دست نزن).
 *   3. [KEEP]: نامی که هرگز عوض نمی‌شود («فروشگاه من»، نامِ برنامه).
 *
 *  خط، راست‌به‌چپ، رقم و قالبِ پول همان فارسی است. کدِ مشترک (commonMain) تا اندروید، کامپیوتر و آیفون
 *  یکی باشند. فهرست را صاحب‌کار واژه‌به‌واژه بازبینی می‌کند؛ واژهٔ تازه فقط با آزمون (`DariTextTest`).
 *  دادهٔ کاربر هرگز عوض نمی‌شود: هر جای خالیِ متنِ فارسیِ `Str*` با `asIs(…)` (`core/i18n/I18n.kt`) در
 *  [protect] پیچیده می‌شود و [of] فقط متنِ خودِ برنامه را می‌گرداند — «گوشی سامسونگ» ِ نامِ کالا یا
 *  «فروشگاه کریم» ِ نامِ مشتری در پیامِ دری همان‌طور که نوشته شده دیده می‌شود.
 */
object DariText {

  /**
   *  یک واژه. [vowelEnd]: فارسی به واکه ختم می‌شود («گوشی»، «هزینه») و دری به همخوان، پس «ٔ» ⇒ «ِ»،
   *  «‌ای» ⇒ «ی» و «‌اش» ⇒ «ش». [person]: جمعِ «ان» هم دارد. [plural]: جمعِ مکسر («مصارف»).
   */
  class Word(
    val fa: String,
    val dari: String,
    val vowelEnd: Boolean = false,
    val person: Boolean = false,
    val plural: String? = null,
  )

  /** واژه‌نامه — همین جدول در PR برای بازبینیِ صاحب‌کار است */
  val WORDS: List<Word> = listOf(
    Word("کامپیوتر", "کمپیوتر"),
    Word("رایانه", "کمپیوتر", vowelEnd = true),
    Word("گوشی", "مبایل", vowelEnd = true),
    Word("موبایل", "مبایل"),
    Word("تلفنِ همراه", "مبایل"),
    Word("تلفن همراه", "مبایل"),
    Word("انبار", "گدام"),
    Word("درصد", "فیصد"),
    Word("کسری", "کمبود", vowelEnd = true),
    Word("بدهکار", "قرض‌دار", person = true),
    Word("کارمند", "شاگرد", person = true),
    Word("فروشگاه", "دکان"),
    Word("هزینهٔ ارسال", "کرایهٔ ارسال"),
    Word("هزینه", "مصرف", vowelEnd = true, plural = "مصارف"),
    Word("خیابان", "سرک"),
    Word("دانشگاه", "پوهنتون"),
    Word("استان", "ولایت"),
    Word("خودرو", "موتر", vowelEnd = true),
  ).sortedByDescending { it.fa.length }

  /** نوشتهٔ کامل ⇒ دری، جایی که واژه‌نامه کافی نیست. کلید باید متنِ فارسیِ یکی از `Str*` باشد (آزمون). */
  val PHRASES: Map<String, String> = mapOf(
    "قرض‌دارِ بدهکار" to "قرض‌دارانی که مانده دارند",
    "تحویل و هزینه" to "تحویل و کرایه",
  )

  /** نام‌هایی که هرگز عوض نمی‌شوند */
  val KEEP: List<String> = listOf("فروشگاه من")

  private const val ZWNJ = '\u200C'
  private const val KASRA = "\u0650"
  private const val HAMZA_ABOVE = "\u0654"

  /** پسوندِ واژهٔ واکه‌پایان ⇒ همان پسوند پس از دریِ همخوان‌پایان */
  private val VOWEL_TAILS: Map<String, String> = mapOf(
    "" to "",
    HAMZA_ABOVE to KASRA,
    "${ZWNJ}ها" to "${ZWNJ}ها",
    "${ZWNJ}های" to "${ZWNJ}های",
    "${ZWNJ}ای" to "ی",
    "${ZWNJ}اش" to "ش",
    "${ZWNJ}ام" to "م",
    "${ZWNJ}ات" to "ت",
    "${ZWNJ}مان" to "${ZWNJ}مان",
    "${ZWNJ}تان" to "${ZWNJ}تان",
    "${ZWNJ}شان" to "${ZWNJ}شان",
    "مان" to "مان",
    "تان" to "تان",
    "شان" to "شان",
  )

  /** پسوندهای واژهٔ همخوان‌پایان — همان‌طور که هست می‌ماند */
  private val CONSONANT_TAILS: Set<String> = setOf(
    "", "ها", "${ZWNJ}ها", "های", "${ZWNJ}های", "ی",
    "م", "ت", "ش", "مان", "تان", "شان", "${ZWNJ}مان", "${ZWNJ}تان", "${ZWNJ}شان",
    "یم", "ید", "ند",
  )

  /** حرفِ همان واژه: حرف‌های خطِ عربی، نیم‌فاصله و اعراب (رقم و نشانه جداکننده‌اند) */
  private fun inWord(c: Char): Boolean =
    c == ZWNJ || c in '\u0620'..'\u065F' || c == '\u0670' || c in '\u066E'..'\u06D5' || c in '\u06FA'..'\u06FF'

  /** دریِ واژه با پسوندش؛ پسوندِ ناشناخته ⇒ null (فارسی می‌ماند) */
  private fun render(w: Word, suffix: String): String? {
    val kasra = suffix.endsWith(KASRA)
    val core = if (kasra) suffix.dropLast(1) else suffix
    val k = if (kasra) KASRA else ""
    if (w.plural != null) {
      when (core) {
        "ها", "${ZWNJ}ها" -> return w.plural + k
        "های", "${ZWNJ}های" -> return w.plural + KASRA
      }
    }
    if (w.vowelEnd) {
      val t = VOWEL_TAILS[core] ?: return null
      return if (kasra && t.endsWith(KASRA)) null else w.dari + t + k
    }
    if (core in CONSONANT_TAILS || (w.person && (core == "ان" || core == "انی"))) return w.dari + core + k
    return null
  }

  private fun hasPersian(s: String): Boolean = s.any { it in '\u0600'..'\u06FF' }

  /** آغاز و پایانِ مقدارِ جای خالی (نویسهٔ کاربردِ خصوصی؛ هرگز دیده نمی‌شود) */
  const val OPEN: Char = '\uE000'
  const val CLOSE: Char = '\uE001'

  /** مقدارِ جای خالی (نامِ کالا، مشتری، دکان، عدد…) که [of] دست نمی‌زند */
  fun protect(value: String): String = "$OPEN$value$CLOSE"

  /** نشانه‌های [protect] را برمی‌دارد؛ بی نشانه ⇒ همان رشته */
  fun strip(s: String): String =
    if (s.indexOf(OPEN) < 0 && s.indexOf(CLOSE) < 0) s else s.filter { it != OPEN && it != CLOSE }

  /**
   *  نوشتهٔ فارسی ⇒ دری. بخشِ [protect]‌شده (دادهٔ کاربر) بایت‌به‌بایت همان می‌ماند و فقط متنِ
   *  خودِ برنامه از فهرست می‌گذرد. نوشتهٔ بی واژهٔ فهرست و بی نشانه، همان رشته (بی کپی) برمی‌گردد.
   */
  fun of(fa: String): String {
    PHRASES[fa]?.let { return it }
    if (fa.indexOf(OPEN) < 0 && fa.indexOf(CLOSE) < 0) return if (hasPersian(fa)) words(fa, false, false) else fa
    val sb = StringBuilder(fa.length)
    var i = 0
    var gluedLeft = false
    while (i < fa.length) {
      val open = fa.indexOf(OPEN, i).let { if (it < 0) fa.length else it }
      var close = fa.length
      if (open < fa.length) {
        var depth = 0
        var j = open
        while (j < fa.length) {
          if (fa[j] == OPEN) depth++ else if (fa[j] == CLOSE && --depth == 0) break
          j++
        }
        close = j
      }
      val seg = strip(fa.substring(i, open))
      val inside = if (open < fa.length) strip(fa.substring(open + 1, minOf(close, fa.length))) else ""
      //  واژه‌ای که به دادهٔ کاربر چسبیده (بی فاصله) تکه‌ای از واژهٔ دیگر است ⇒ دست نمی‌خورد
      val gluedRight = inside.isNotEmpty() && inWord(inside[0])
      sb.append(words(seg, gluedLeft, gluedRight)).append(inside)
      gluedLeft = when {
        inside.isNotEmpty() -> inWord(inside[inside.length - 1])
        seg.isNotEmpty() -> inWord(seg[seg.length - 1])
        else -> gluedLeft
      }
      i = close + 1
    }
    return sb.toString()
  }

  /** واژه‌نامه روی یک تکه متنِ برنامه؛ [gluedLeft]/[gluedRight]: دادهٔ کاربر بی فاصله پیش/پس از آن */
  private fun words(fa: String, gluedLeft: Boolean, gluedRight: Boolean): String {
    if (!hasPersian(fa)) return fa
    var out: StringBuilder? = null
    var i = 0
    var copied = 0
    scan@ while (i < fa.length) {
      if (if (i == 0) !gluedLeft else !inWord(fa[i - 1])) {
        for (k in KEEP) if (fa.startsWith(k, i)) { i += k.length; continue@scan }
        for (w in WORDS) {
          if (!fa.startsWith(w.fa, i)) continue
          var j = i + w.fa.length
          while (j < fa.length && inWord(fa[j])) j++
          if (j == fa.length && gluedRight) continue
          val dari = render(w, fa.substring(i + w.fa.length, j)) ?: continue
          val sb = out ?: StringBuilder(fa.length + 8).also { out = it }
          sb.appendRange(fa, copied, i).append(dari)
          i = j
          copied = j
          continue@scan
        }
      }
      i++
    }
    val sb = out ?: return fa
    return sb.appendRange(fa, copied, fa.length).toString()
  }
}
