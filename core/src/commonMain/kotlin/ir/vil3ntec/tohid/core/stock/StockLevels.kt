package ir.vil3ntec.tohid.core.stock

/**
 *  حدهای موجودیِ یک کالا — INV-12: حدِ هشدار (کمینه)، نقطهٔ سفارش و بیشینه، با ویرایشِ گروهی.
 *
 *  خالص (commonMain، §۴۰.۳). مقدار به **هزارمِ واحد** (Long)، همان قاعدهٔ `Qty`. `null` یعنی «تعریف نشده».
 *
 *  معنا (رایج در انبارداری، «min/max با نقطهٔ سفارش»):
 *  - **کمینه** (`min`، همان «حد کم بودن» ِ امروز): موجودی به آن رسید ⇒ هشدارِ «موجودی کم».
 *  - **نقطهٔ سفارش** (`reorder`): موجودی به آن رسید ⇒ «وقتِ سفارش» — پیش از رسیدن به کمینه.
 *  - **بیشینه** (`max`): سفارش تا همین‌جا پر می‌کند؛ موجودیِ بیش از آن «بیش از حد» است (پولِ خوابیده).
 *  پس ترتیبِ درست: کمینه ≤ نقطهٔ سفارش < بیشینه.
 */
data class StockLevels(val minMilli: Long? = null, val reorderMilli: Long? = null, val maxMilli: Long? = null)

object StockLevelRules {
  /** سقفِ هر حد: یک میلیارد واحد (به هزارم) — بیش از آن خطای تایپ است، نه کالای واقعی */
  const val MAX_MILLI: Long = 1_000_000_000_000L

  enum class Problem(val fa: String) {
    NEGATIVE("حدِ موجودی منفی نمی‌شود"),
    TOO_LARGE("عددِ حد بیش از اندازه است"),
    REORDER_BELOW_MIN("نقطهٔ سفارش کمتر از حدِ کم بودن است"),
    MAX_ZERO("بیشینه باید بیشتر از صفر باشد"),
    MAX_NOT_ABOVE_REORDER("بیشینه باید بیشتر از نقطهٔ سفارش باشد"),
    MAX_BELOW_MIN("بیشینه کمتر از حدِ کم بودن است"),
  }

  /** ایرادهای یک مجموعه حد؛ خالی یعنی درست. */
  fun problems(l: StockLevels): List<Problem> {
    val out = ArrayList<Problem>()
    val all = listOfNotNull(l.minMilli, l.reorderMilli, l.maxMilli)
    if (all.any { it < 0 }) out += Problem.NEGATIVE
    if (all.any { it > MAX_MILLI }) out += Problem.TOO_LARGE
    if (out.isNotEmpty()) return out
    val min = l.minMilli
    val reorder = l.reorderMilli
    val max = l.maxMilli
    if (min != null && reorder != null && reorder < min) out += Problem.REORDER_BELOW_MIN
    if (max != null) {
      if (max == 0L) out += Problem.MAX_ZERO
      else {
        if (reorder != null && max <= reorder) out += Problem.MAX_NOT_ABOVE_REORDER
        if (min != null && max < min) out += Problem.MAX_BELOW_MIN
      }
    }
    return out
  }

  /** وضعیتِ موجودی نسبت به حدها؛ ترتیبِ اولویت همان ترتیبِ این فهرست است. */
  enum class State { OUT, BELOW_MIN, REORDER, OK, OVER_MAX }

  /**
   *  - موجودیِ ≤ ۰ ⇒ OUT (تمام‌شده)
   *  - کمینهٔ بیش از صفر و موجودی ≤ کمینه ⇒ BELOW_MIN (همان «موجودی کم» ِ امروز: `0 < s ≤ minStock`)
   *  - موجودی ≤ نقطهٔ سفارش ⇒ REORDER
   *  - موجودی > بیشینه ⇒ OVER_MAX
   *  - وگرنه OK
   */
  fun state(onHandMilli: Long, l: StockLevels): State {
    val min = l.minMilli
    val reorder = l.reorderMilli
    val max = l.maxMilli
    return when {
      onHandMilli <= 0 -> State.OUT
      min != null && min > 0 && onHandMilli <= min -> State.BELOW_MIN
      reorder != null && onHandMilli <= reorder -> State.REORDER
      max != null && onHandMilli > max -> State.OVER_MAX
      else -> State.OK
    }
  }

  /**
   *  پیشنهادِ مقدارِ سفارش (هزارم): فقط وقتی وضعیت OUT، BELOW_MIN یا REORDER است **و** بیشینه
   *  تعریف شده: بیشینه − موجودی. موجودیِ منفی (فروشِ بیش از ورود) صفر حساب می‌شود، چون عددِ منفی
   *  کالای واقعی نیست و نباید سفارش را بزرگ‌تر کند. بی بیشینه ⇒ `null` («تا کجا پر شود» معلوم نیست؛
   *  عددی از خود ساخته نمی‌شود). سفارشی هم ثبت نمی‌شود — فقط پیشنهاد است (INV-13 جداست).
   */
  fun suggestOrderMilli(onHandMilli: Long, l: StockLevels): Long? {
    val max = l.maxMilli ?: return null
    if (state(onHandMilli, l) !in NEEDS_ORDER) return null
    val have = if (onHandMilli < 0) 0L else onHandMilli
    return (max - have).takeIf { it > 0 }
  }

  val NEEDS_ORDER: Set<State> = setOf(State.OUT, State.BELOW_MIN, State.REORDER)

  // ═══════════════════════════════ ویرایشِ گروهی ═══════════════════════════════

  /** تغییرِ یک حد در کارِ گروهی */
  sealed interface Edit {
    /** دست نخورد */
    data object Keep : Edit
    /** پاک شود (تعریف‌نشده) */
    data object Clear : Edit
    data class To(val milli: Long) : Edit
  }

  data class Patch(val min: Edit = Edit.Keep, val reorder: Edit = Edit.Keep, val max: Edit = Edit.Keep) {
    val isNoop: Boolean get() = min == Edit.Keep && reorder == Edit.Keep && max == Edit.Keep
  }

  data class Planned(val id: String, val before: StockLevels, val after: StockLevels, val problems: List<Problem>) {
    val ok: Boolean get() = problems.isEmpty()
    val changes: Boolean get() = ok && before != after
  }

  fun apply(l: StockLevels, p: Patch): StockLevels = StockLevels(
    minMilli = edit(l.minMilli, p.min),
    reorderMilli = edit(l.reorderMilli, p.reorder),
    maxMilli = edit(l.maxMilli, p.max),
  )

  /**
   *  پیش‌نمایشِ ویرایشِ گروهی: برای هر کالا حدِ پیش و پس و ایرادها. ردیفی که حدِ پس از تغییرش با
   *  هم نمی‌خواند **عوض نمی‌شود** و ایرادش گزارش می‌شود (بقیه عوض می‌شوند). ترتیب: ناموفق‌ها اول
   *  (RULES: «گزارشِ هر ردیف با ناموفق‌ها اول»)، سپس همان ترتیبِ ورودی.
   */
  fun plan(rows: List<Pair<String, StockLevels>>, patch: Patch): List<Planned> {
    val planned = rows.map { (id, before) ->
      val after = apply(before, patch)
      Planned(id, before, after, problems(after))
    }
    return planned.filter { !it.ok } + planned.filter { it.ok }
  }

  private fun edit(v: Long?, e: Edit): Long? = when (e) {
    Edit.Keep -> v
    Edit.Clear -> null
    is Edit.To -> e.milli
  }
}
