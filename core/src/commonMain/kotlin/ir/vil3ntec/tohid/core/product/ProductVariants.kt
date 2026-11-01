package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.text.TextNormalizer

/*
 *  تنوع (رنگ، اندازه) با ماتریس — PRD-11، §۶.۳.
 *
 *  کالای «مدل» ویژگی‌ها را دارد («رنگ: قرمز، آبی» و «اندازه: S، M، L»)؛ هر ترکیب یک کالای جداست با بارکد،
 *  قیمت و موجودیِ خودش («پیراهن — قرمز / M») که به مدل اشاره می‌کند. پس فروش، انبار، برچسب و گزارش همان
 *  راهِ کالای عادی‌اند و هیچ‌جا «نیمه‌کالا» نیست.
 *
 *  ── قاعده‌ها ────────────────────────────────────────────────────────
 *  • حداکثر [MAX_AXES] ویژگی، هر کدام [MAX_VALUES] مقدار، و روی هم [MAX_COMBOS] ترکیب.
 *  • ترکیب با کلیدِ مقایسه شناخته می‌شود («ي/ی»، نیم‌فاصله، بزرگیِ لاتین): ترکیبی که تنوعش هست دوباره
 *    ساخته نمی‌شود؛ مقداری که از فهرست برداشته شود تنوعش را پاک نمی‌کند (موجودی و سابقه دارد).
 *  • ماتریس: سطرها مقدارهای ویژگیِ نخست، ستون‌ها ترکیبِ بقیه (یک ستون اگر یک ویژگی).
 *
 *  منطقِ خالص (commonMain، بی java.* و android.*)؛ آزمون: `ProductVariantsTest`.
 */
/** یک ترکیب: (ویژگی، مقدار) به ترتیبِ ویژگی‌ها */
typealias VariantCombo = List<Pair<String, String>>

object ProductVariants {

  const val MAX_AXES = 3
  const val MAX_VALUES = 20
  const val MAX_COMBOS = 100

  data class Axis(val name: String, val values: List<String>)

  enum class Problem { BAD_LINE, DUP_AXIS, TOO_MANY_AXES, TOO_MANY_VALUES, TOO_MANY_COMBOS }

  data class Parsed(val axes: List<Axis>, val problems: List<Pair<String, Problem>>)

  /** کادرِ فرم ⇒ ویژگی‌ها: هر خط «ویژگی: مقدار، مقدار…» (مقدارها مثلِ `ProductTerms.parse`) */
  fun parse(text: String): Parsed {
    val axes = ArrayList<Axis>()
    val bad = ArrayList<Pair<String, Problem>>()
    for (raw in text.split('\n', '\r')) {
      val line = raw.trim()
      if (line.isEmpty()) continue
      val cut = line.indexOf(':')
      val name = if (cut <= 0) "" else ProductTerms.cleanName(line.substring(0, cut)).take(ProductTerms.MAX_TERM)
      val values = if (cut <= 0) emptyList() else ProductTerms.parse(line.substring(cut + 1))
      when {
        name.isEmpty() || values.isEmpty() -> bad += line to Problem.BAD_LINE
        axes.any { TextNormalizer.compareKey(it.name) == TextNormalizer.compareKey(name) } -> bad += line to Problem.DUP_AXIS
        axes.size == MAX_AXES -> bad += line to Problem.TOO_MANY_AXES
        values.size > MAX_VALUES -> bad += line to Problem.TOO_MANY_VALUES
        else -> axes += Axis(name, values)
      }
    }
    val total = axes.fold(1L) { n, a -> n * a.values.size }
    if (axes.isNotEmpty() && total > MAX_COMBOS) return Parsed(emptyList(), bad + (text.trim() to Problem.TOO_MANY_COMBOS))
    return Parsed(axes, bad)
  }

  fun format(axes: List<Axis>): String = axes.joinToString("\n") { "${it.name}: ${ProductTerms.format(it.values)}" }

  /** همهٔ ترکیب‌ها به ترتیبِ ویژگی‌ها و مقدارها (ضربِ دکارتی)؛ بی ویژگی ⇒ تهی */
  fun combos(axes: List<Axis>): List<VariantCombo> {
    if (axes.isEmpty()) return emptyList()
    var out: List<VariantCombo> = listOf(emptyList())
    for (a in axes) out = out.flatMap { c -> a.values.map { v -> c + (a.name to v) } }
    return out
  }

  /** کلیدِ مقایسهٔ یک ترکیب — همان ترکیب با املای دیگر همین کلید را دارد */
  fun key(combo: VariantCombo): String =
    combo.joinToString("|") { (k, v) -> TextNormalizer.compareKey(k) + "=" + TextNormalizer.compareKey(v) }

  /** «پیراهن — قرمز / M» */
  fun name(base: String, combo: VariantCombo): String = base.trim() + " — " + combo.joinToString(" / ") { it.second }

  /** ترکیب‌هایی که هنوز تنوع ندارند؛ [existing] ترکیبِ تنوع‌های موجودِ همین مدل */
  fun missing(axes: List<Axis>, existing: List<VariantCombo>): List<VariantCombo> {
    val have = existing.mapTo(HashSet()) { key(it) }
    return combos(axes).filter { key(it) !in have }
  }

  /** ماتریسِ نمایش: سطرها، ستون‌ها و شناسهٔ تنوعِ هر خانه (یا null) */
  data class Matrix(val rows: List<String>, val cols: List<String>, val cells: Map<Pair<Int, Int>, String>)

  fun matrix(axes: List<Axis>, variants: List<Pair<String, VariantCombo>>): Matrix {
    if (axes.isEmpty()) return Matrix(emptyList(), emptyList(), emptyMap())
    val rows = axes.first().values
    val rest = combos(axes.drop(1))
    val cols = if (rest.isEmpty()) listOf("") else rest.map { c -> c.joinToString(" / ") { it.second } }
    val byKey = variants.associate { (id, c) -> key(c) to id }
    val cells = HashMap<Pair<Int, Int>, String>()
    for ((i, r) in rows.withIndex()) {
      val head = listOf(axes.first().name to r)
      for (j in cols.indices) {
        val combo = if (rest.isEmpty()) head else head + rest[j]
        byKey[key(combo)]?.let { cells[i to j] = it }
      }
    }
    return Matrix(rows, cols, cells)
  }
}
