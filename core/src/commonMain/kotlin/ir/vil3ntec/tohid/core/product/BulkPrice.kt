package ir.vil3ntec.tohid.core.product

/*
 *  تغییرِ گروهیِ قیمت با پیش‌نمایشِ رنگی، هشدار و تأیید — PRD-21 (§۶.۳)؛ هر ردیفِ تغییر کاربر و
 *  دلیل را در سابقه دارد (PRD-14، کارِ موتورِ انبار).
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • پول عددِ صحیح به یک‌صدمِ افغانی (`…Minor`)؛ درصد به «پایه» (۱۰۰۰ = ۱۰٪).
 *  • گرد کردن فقط `mulDivRound(a,b,c) = floorDiv(2ab + c, 2c)` (نیمه به +∞) — همان قاعدهٔ «موجودی و کاتالوگ».
 *    سپس به نزدیک‌ترین `roundToMinor` (پیش‌فرض یک افغانی) با همان قاعده.
 *  • منفی ⇒ «رد» (اعمال نمی‌شود)؛ صفر، زیرِ بهای خرید، یا تغییرِ ۵۰٪ به بالا ⇒ «هشدار».
 *  • هر هشدار ⇒ `needsConfirm`: رابط بی تأییدِ صریح اعمال نمی‌کند. بی تغییر ⇒ نه شمرده، نه نوشته.
 *  • رنگِ هر ردیف: بالا، پایین، همان، خطر (رد یا زیرِ بها).
 *  منطقِ خالص؛ آزمون: `BulkPriceTest` (commonTest).
 */
object BulkPrice {

  enum class Field { SALE, PURCHASE }

  sealed interface Op {
    /** همه همین مبلغ */
    data class SetTo(val minor: Long) : Op
    /** درصد به پایه: ۱۰۰۰ = +۱۰٪، −۵۰۰ = −۵٪ */
    data class Percent(val basisPoints: Long) : Op
    /** افزودن/کاستنِ مبلغِ ثابت */
    data class Add(val minor: Long) : Op
  }

  data class Item(val id: String, val name: String, val saleMinor: Long, val purchaseMinor: Long)

  enum class Warn { NEGATIVE, ZERO, BELOW_COST, BIG_CHANGE }

  enum class Tone { UP, DOWN, SAME, DANGER }

  data class Row(
    val id: String, val name: String, val oldMinor: Long, val newMinor: Long,
    val tone: Tone, val warns: List<Warn>,
  ) {
    val changed: Boolean get() = newMinor != oldMinor
    /** ردیفِ «رد» اعمال نمی‌شود */
    val blocked: Boolean get() = Warn.NEGATIVE in warns
    val applies: Boolean get() = changed && !blocked
  }

  data class Preview(val field: Field, val rows: List<Row>) {
    val changed: Int get() = rows.count { it.applies }
    val warned: Int get() = rows.count { it.applies && it.warns.isNotEmpty() }
    val blocked: Int get() = rows.count { it.changed && it.blocked }
    /** هر هشدار ⇒ تأییدِ صریح */
    val needsConfirm: Boolean get() = warned > 0
  }

  /** سقفِ مبلغ: ۱۰ میلیارد افغانی (به یک‌صدم) — حاصل‌ضربِ `mulDivRound` هرگز از Long بیرون نمی‌زند */
  const val MAX_MINOR = 1_000_000_000_000L

  /** ورودیِ معقول: درصد از −۱۰۰٪ تا +۱۰٬۰۰۰٪، مبلغ تا ۱۰ میلیارد افغانی؛ بیرون از آن پیش‌نمایش ساخته نمی‌شود */
  fun valid(op: Op): Boolean = when (op) {
    is Op.Percent -> op.basisPoints in -10_000L..1_000_000L
    is Op.Add -> op.minor in -MAX_MINOR..MAX_MINOR
    is Op.SetTo -> op.minor in 0L..MAX_MINOR
  }

  /** `floorDiv(2ab + c, 2c)` — گردِ نزدیک‌ترین، نیمه به +∞ */
  fun mulDivRound(a: Long, b: Long, c: Long): Long {
    require(c > 0) { "مخرج باید مثبت باشد" }
    return (2 * a * b + c).floorDiv(2 * c)
  }

  fun apply(old: Long, op: Op, roundToMinor: Long = 100): Long {
    val raw = when (op) {
      is Op.SetTo -> op.minor
      is Op.Percent -> old + mulDivRound(old, op.basisPoints, 10_000)
      is Op.Add -> old + op.minor
    }
    val step = if (roundToMinor > 0) roundToMinor else 1
    return if (step == 1L) raw else mulDivRound(raw, 1, step) * step
  }

  fun preview(items: List<Item>, field: Field, op: Op, roundToMinor: Long = 100): Preview =
    Preview(field, items.map { it ->
      val old = if (field == Field.SALE) it.saleMinor else it.purchaseMinor
      val new = apply(old, op, roundToMinor)
      val warns = ArrayList<Warn>()
      when {
        new < 0 -> warns += Warn.NEGATIVE
        new == 0L && old != 0L -> warns += Warn.ZERO
      }
      if (new > 0) {
        //  فروش زیرِ بها: قیمتِ فروشِ تازه زیرِ خرید، یا خریدِ تازه بالای فروش
        if (field == Field.SALE && it.purchaseMinor > 0 && new < it.purchaseMinor) warns += Warn.BELOW_COST
        if (field == Field.PURCHASE && it.saleMinor > 0 && new > it.saleMinor) warns += Warn.BELOW_COST
      }
      if (old > 0 && new != old && 2 * kotlin.math.abs(new - old) >= old) warns += Warn.BIG_CHANGE
      val tone = when {
        Warn.NEGATIVE in warns || Warn.BELOW_COST in warns -> Tone.DANGER
        new > old -> Tone.UP
        new < old -> Tone.DOWN
        else -> Tone.SAME
      }
      Row(it.id, it.name, old, new, tone, if (new == old) emptyList() else warns)
    })
}
