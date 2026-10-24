package ir.vil3ntec.tohid.core.safe

/**
 *  هستهٔ گاوصندوق — PAY-18 (دستورِ صاحب محصول، owner-feedback-3 بندِ B؛ طرح: SAFE-V1).
 *
 *  گاوصندوق یک دفترِ فقط‌افزودنی است: هر ردیف شمارش، ورود، خروج، انتقال از/به دخل یا
 *  تحویل به بانک (با شمارهٔ کیسه). هیچ ردیفی ویرایش یا پاک نمی‌شود.
 *
 *  ── مانده ─────────────────────────────────────────────────────────
 *  ردیف‌ها به ترتیبِ (`at`، `id`). پایه = آخرین شمارش (بی شمارش صفر)؛
 *  مانده = پایه + ورود و از‌دخل − خروج، به‌دخل و به‌بانکِ پس از آن.
 *  `id` فقط برای ترتیبِ یکتا در زمانِ برابر است، نه معنای زمان (F0-10).
 *
 *  ── شمارش ─────────────────────────────────────────────────────────
 *  شمارش خودش سندِ اصلاح است: مبلغِ شمرده و «مانده‌ای که دستگاه دید» (`expectedMinor`)
 *  کنارِ هم ثبت می‌شوند؛ اختلاف هرگز بی‌صدا یکی نمی‌شود. اختلافِ اکیداً بیش از آستانه
 *  (همان D-18ِ صندوق) دلیل می‌خواهد. اختلاف همیشه از روی دفتر دوباره حساب می‌شود؛ اگر با
 *  `expectedMinor` نخواند، ردیفی دیر رسیده که پیش از شمارش بود ⇒ «نیازمند بررسی».
 *  شمارشی که هیچ ردیفی پیش از آن نیست «شمارشِ آغاز» است: پایه را می‌گذارد، اختلاف و دلیل
 *  ندارد. اگر پیش از نخستین شمارش ورود یا خروجی ثبت شده باشد، آن شمارش اختلاف دارد و نشانش می‌دهد.
 *
 *  ── شکلِ ردیف ([check]) ───────────────────────────────────────────
 *  همان قاعده را سرور پیش از نشاندن می‌سنجد (`server/src/lib/safe-ledger.js`)؛ هر دو با
 *  `core/test-vectors/safe-v1.json` (بخشِ `shape`). ⛔ یکی را بی دیگری و بردار عوض نکنید.
 *
 *  هر مبلغ `Long` به یک‌صدمِ افغانی؛ هر ردیف تا [MAX_AMOUNT] و هر جمعِ میانی در ±(۲^۵۳−۱)
 *  ([Walk]). ردیفی که جمعی را از آن بازه بیرون ببرد مثلِ ردیفِ بدشکل کنار می‌رود (`invalid`)
 *  — نه عددِ گردشده (§۲۸.۴)، نه خطایی که خواندنِ کلِ دفتر را بشکند. Kotlin خالص است — بی
 *  android.*، بی ساعت.
 */
object SafeCore {

  const val VERSION = 1

  const val COUNT = "count"
  const val IN = "in"
  const val OUT = "out"
  const val FROM_DRAWER = "from_drawer"
  const val TO_DRAWER = "to_drawer"
  const val TO_BANK = "to_bank"

  val KINDS: List<String> = listOf(COUNT, IN, OUT, FROM_DRAWER, TO_DRAWER, TO_BANK)
  private val INFLOW = setOf(IN, FROM_DRAWER)
  private val LINKED = setOf(FROM_DRAWER, TO_DRAWER)

  const val OTHER = "other"

  /** دلیلِ ورود و خروج — همان واژه‌های ورود/خروجِ صندوق (`ShiftCore.MOVE_REASONS`) */
  val REASONS: Map<String, List<String>> = mapOf(
    IN to listOf("deposit", OTHER),
    OUT to listOf("owner_withdrawal", "petty_expense", OTHER),
  )

  const val MAX_EXACT: Long = 9_007_199_254_740_991L
  /** سقفِ هر ردیف: یک میلیارد افغانی — همان `MAX_AMOUNT_MINOR` ِ بازپرداختِ سرور (`order-refunds.js`) */
  const val MAX_AMOUNT: Long = 100_000_000_000L
  const val MAX_ID = 64
  const val MAX_TEXT = 200
  const val MAX_BAG = 40

  /** کدهای خطا — متنِ فارسی را لایهٔ برنامه می‌گذارد */
  object Code {
    const val ID_REQUIRED = "ID_REQUIRED"
    const val BAD_KIND = "BAD_KIND"
    const val INVALID_AMOUNT = "INVALID_AMOUNT"
    const val INVALID_TIME = "INVALID_TIME"
    const val TEXT_TOO_LONG = "TEXT_TOO_LONG"
    const val REASON_REQUIRED = "REASON_REQUIRED"
    const val BAD_REASON = "BAD_REASON"
    const val NOTE_REQUIRED = "NOTE_REQUIRED"
    const val BAG_REQUIRED = "BAG_REQUIRED"
    const val LINK_REQUIRED = "LINK_REQUIRED"
    const val BAD_FIELD = "BAD_FIELD"
    const val INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS"
    const val ID_CONFLICT = "ID_CONFLICT"
  }

  /** یک ردیفِ دفتر؛ فیلدهای اختیاری برای نوعِ دیگر `null`اند */
  data class Entry(
    val id: String,
    val kind: String,
    val amountMinor: Long,
    val at: Long,
    val by: String? = null,
    /** ورود/خروج: یکی از [REASONS]؛ شمارش: دلیلِ اختلاف (متنِ آزاد) */
    val reason: String? = null,
    val note: String? = null,
    /** شمارهٔ کیسه — برای [TO_BANK] لازم، برای شمارش ممنوع */
    val bagNo: String? = null,
    /** فقط شمارش: مانده‌ای که دستگاهِ شمارنده دید */
    val expectedMinor: Long? = null,
    /** فقط [FROM_DRAWER]/[TO_DRAWER]: شناسهٔ `shift_open` و ردیفِ جفتِ سابقهٔ صندوق */
    val shiftId: String? = null,
    val auditId: String? = null,
  )

  data class Line(
    val entry: Entry,
    /** اثر روی مانده: + ورود، − خروج؛ شمارش: شمرده − مانده پیش از آن */
    val deltaMinor: Long,
    /** مانده پس از همین ردیف */
    val balanceMinor: Long,
    /** فقط شمارش: اضافه (+) یا کسری (−) نسبت به ماندهٔ دفتر؛ شمارشِ آغاز (هیچ ردیفی پیش از آن) null */
    val varianceMinor: Long? = null,
    /**
     *  فقط شمارش: `expectedMinor`ِ ثبت‌شده با ماندهٔ دوباره‌حساب‌شده نمی‌خواند. شمارشِ آغاز
     *  هرگز `late` نمی‌گیرد: اختلاف ندارد و پایه را از نو می‌گذارد، پس ردیفِ دیررسِ پیش از آن
     *  هیچ عددی را عوض نمی‌کند.
     */
    val late: Boolean = false,
  )

  data class View(
    val balanceMinor: Long,
    /** null یعنی هنوز شمرده نشده */
    val lastCount: Line?,
    val inSinceCountMinor: Long,
    val outSinceCountMinor: Long,
    /** جمعِ اختلافِ همهٔ شمارش‌ها */
    val varianceTotalMinor: Long,
    /** مانده زیرِ صفر — «نیازمند شمارش»؛ هرگز به صفر بریده نمی‌شود */
    val negative: Boolean,
    /** شناسهٔ شمارش‌هایی که `late` دارند */
    val needsReview: List<String>,
    /** ردیف‌هایی که [check] را نمی‌گذرانند، شناسهٔ تکراری، یا جمعی را از ±(۲^۵۳−۱) بیرون می‌بردند — در مانده نیستند */
    val invalid: List<String>,
    /** تازه‌ترین اول */
    val history: List<Line>,
  )

  sealed interface Result<out T> {
    data class Ok<T>(val value: T, val duplicate: Boolean = false) : Result<T>
    /** [varianceMinor] فقط برای `REASON_REQUIRED`ِ شمارش */
    data class Err(val code: String, val varianceMinor: Long? = null) : Result<Nothing>
  }

  /** ورودیِ یک عملیات؛ `at` زمانِ دستگاه است و [build] آن را پس از آخرین ردیفِ دیده‌شده می‌نشاند */
  data class Input(
    val id: String?,
    val kind: String,
    val amountMinor: Long,
    val at: Long,
    val by: String? = null,
    val reason: String? = null,
    val note: String? = null,
    val bagNo: String? = null,
    val shiftId: String? = null,
    val auditId: String? = null,
  )

  private fun clean(s: String?): String? = s?.trim()?.ifEmpty { null }
  private fun tooLong(s: String?, max: Int) = s != null && s.length > max

  /** شکلِ یک ردیف، بی نگاه به بقیهٔ دفتر — null یعنی درست */
  fun check(e: Entry): String? {
    if (e.id.isBlank()) return Code.ID_REQUIRED
    if (e.kind !in KINDS) return Code.BAD_KIND
    val min = if (e.kind == COUNT) 0L else 1L
    if (e.amountMinor < min || e.amountMinor > MAX_AMOUNT) return Code.INVALID_AMOUNT
    if (e.at <= 0 || e.at > MAX_EXACT) return Code.INVALID_TIME
    if (e.id.length > MAX_ID || tooLong(e.by, MAX_TEXT) || tooLong(e.reason, MAX_TEXT) || tooLong(e.note, MAX_TEXT) ||
      tooLong(e.bagNo, MAX_BAG) || tooLong(e.shiftId, MAX_ID) || tooLong(e.auditId, MAX_ID)
    ) return Code.TEXT_TOO_LONG
    val allowed = REASONS[e.kind]
    when {
      allowed != null -> {
        if (e.reason == null) return Code.REASON_REQUIRED
        if (e.reason !in allowed) return Code.BAD_REASON
        if (e.reason == OTHER && e.note.isNullOrBlank()) return Code.NOTE_REQUIRED
      }
      e.kind != COUNT && e.reason != null -> return Code.BAD_FIELD
    }
    if (e.kind == TO_BANK && e.bagNo.isNullOrBlank()) return Code.BAG_REQUIRED
    if (e.kind == COUNT && e.bagNo != null) return Code.BAD_FIELD
    if (e.kind == COUNT) {
      val x = e.expectedMinor ?: return Code.BAD_FIELD
      if (x < -MAX_EXACT || x > MAX_EXACT) return Code.BAD_FIELD
    } else if (e.expectedMinor != null) return Code.BAD_FIELD
    if (e.kind in LINKED) {
      if (e.shiftId.isNullOrBlank() || e.auditId.isNullOrBlank()) return Code.LINK_REQUIRED
    } else if (e.shiftId != null || e.auditId != null) return Code.BAD_FIELD
    return null
  }

  private val ORDER = compareBy<Entry>({ it.at }, { it.id })

  /** ردیف‌های درست، یک بار هر شناسه (نخستین)، به ترتیبِ دفتر؛ دومی شناسهٔ کنارگذاشته‌ها */
  private fun usable(entries: List<Entry>): Pair<List<Entry>, List<String>> {
    val seen = HashSet<String>()
    val bad = ArrayList<String>()
    val ok = ArrayList<Entry>()
    for (e in entries) {
      if (check(e) != null || !seen.add(e.id)) bad += e.id else ok += e
    }
    return ok.sortedWith(ORDER) to bad
  }

  private fun signed(e: Entry): Long = if (e.kind in INFLOW) e.amountMinor else -e.amountMinor

  /** جمعِ دقیق در ±(۲^۵۳−۱) — همان سقفِ عددِ JS؛ null یعنی بیرون از بازه. دو عددِ در بازه Long را سرریز نمی‌کنند */
  private fun sum(a: Long, b: Long): Long? = (a + b).takeIf { it in -MAX_EXACT..MAX_EXACT }

  /**
   *  یک بار پیمودنِ دفترِ مرتب. هر ردیف یا کامل می‌نشیند یا ([lineOf] = null) با شناسه‌اش در
   *  [skipped] کنار می‌رود و هیچ عددی را عوض نمی‌کند — پس [view]، [balanceBefore] و [build]
   *  همیشه یک دفتر می‌بینند.
   */
  private class Walk {
    val lines = ArrayList<Line>()
    val skipped = ArrayList<String>()
    val review = ArrayList<String>()
    var balance = 0L
    var last: Line? = null
    var inSince = 0L
    var outSince = 0L
    var varianceTotal = 0L

    /** سطرِ [e] روی حالِ فعلی، بی تغییرِ آن؛ null اگر جمعی از ±(۲^۵۳−۱) بیرون برود */
    fun lineOf(e: Entry): Line? {
      if (e.kind == COUNT) {
        val d = sum(e.amountMinor, -balance) ?: return null
        //  شمارشِ آغاز (هیچ ردیفی پیش از آن) پایه می‌گذارد؛ هر شمارشِ دیگر اختلاف دارد
        val v = if (lines.isEmpty()) null else d
        sum(varianceTotal, v ?: 0L) ?: return null
        return Line(e, d, e.amountMinor, varianceMinor = v, late = v != null && e.expectedMinor != balance)
      }
      val d = signed(e)
      (if (d > 0) sum(inSince, d) else sum(outSince, -d)) ?: return null
      return Line(e, d, sum(balance, d) ?: return null)
    }

    fun step(e: Entry) {
      val line = lineOf(e) ?: run {
        skipped += e.id
        return
      }
      if (e.kind == COUNT) {
        varianceTotal += line.varianceMinor ?: 0L
        inSince = 0L
        outSince = 0L
        last = line
        if (line.late) review += e.id
      } else if (line.deltaMinor > 0) inSince += line.deltaMinor else outSince -= line.deltaMinor
      balance = line.balanceMinor
      lines += line
    }
  }

  /** پیمودنِ ردیف‌های مرتبِ [usable]، تا پیش از جایگاهِ [until] (اگر داده شود) */
  private fun walk(rows: List<Entry>, until: Entry? = null): Walk {
    val w = Walk()
    for (e in rows) {
      if (until != null && ORDER.compare(e, until) >= 0) break
      w.step(e)
    }
    return w
  }

  fun view(entries: List<Entry>): View {
    val (rows, bad) = usable(entries)
    val w = walk(rows)
    return View(
      balanceMinor = w.balance, lastCount = w.last, inSinceCountMinor = w.inSince, outSinceCountMinor = w.outSince,
      varianceTotalMinor = w.varianceTotal, negative = w.balance < 0, needsReview = w.review, invalid = bad + w.skipped,
      history = w.lines.asReversed().toList(),
    )
  }

  /** مانده درست پیش از جایگاهِ (`at`، `id`) */
  fun balanceBefore(entries: List<Entry>, at: Long, id: String): Long =
    walk(usable(entries).first, Entry(id, COUNT, 0, at)).balance

  /**
   *  سنجش و ساختِ ردیفِ تازه از روی دفترِ همین دستگاه.
   *  • زمان: دستگاه چیزی را که دیده پشتِ سر می‌گذارد، پس `at` دست‌کم یکی پس از آخرین ردیف است
   *    (ساعتِ عقب‌ماندهٔ گوشی ردیف را پیش از شمارشی نمی‌برد که همین‌جا دیده شد).
   *  • تکرارِ همان شناسه با همان محتوا `duplicate`؛ با محتوای دیگر `ID_CONFLICT`.
   *  • خروج بیش از مانده `INSUFFICIENT_FUNDS`؛ اختلافِ شمارش (جز شمارشِ آغاز) بیش از [threshold]
   *    بی دلیل `REASON_REQUIRED`؛ ردیفی که [view] کنارش می‌گذاشت (جمعی بیرون از ±(۲^۵۳−۱))
   *    `INVALID_AMOUNT`.
   */
  fun build(entries: List<Entry>, input: Input, threshold: Long): Result<Entry> {
    val id = clean(input.id) ?: return Result.Err(Code.ID_REQUIRED)
    if (input.at <= 0) return Result.Err(Code.INVALID_TIME)
    val rows = usable(entries).first
    val prior = entries.firstOrNull { it.id == id }
    val at = prior?.at ?: maxOf(input.at, (rows.lastOrNull()?.at ?: 0L) + 1)
    val w = walk(rows, Entry(id, COUNT, 0, at))
    val before = w.balance
    val e = Entry(
      id = id, kind = input.kind, amountMinor = input.amountMinor, at = at,
      by = clean(input.by), reason = clean(input.reason), note = clean(input.note), bagNo = clean(input.bagNo),
      expectedMinor = if (input.kind == COUNT) before else null,
      shiftId = clean(input.shiftId), auditId = clean(input.auditId),
    )
    check(e)?.let { return Result.Err(it) }
    if (prior != null) {
      fun same(x: Entry) = x.copy(at = 0, expectedMinor = null)
      return if (same(prior) == same(e)) Result.Ok(prior, duplicate = true) else Result.Err(Code.ID_CONFLICT)
    }
    if (e.kind == COUNT) {
      val v = e.amountMinor - before
      if (w.lines.isNotEmpty() && (if (v < 0) -v else v) > threshold && e.reason == null) return Result.Err(Code.REASON_REQUIRED, varianceMinor = v)
    } else if (e.kind !in INFLOW && e.amountMinor > before) return Result.Err(Code.INSUFFICIENT_FUNDS)
    //  ردیفِ تازه پس از همهٔ دیده‌هاست ([at])، پس [w] همان حالی است که view رویش می‌نشاندش
    if (w.lineOf(e) == null) return Result.Err(Code.INVALID_AMOUNT)
    return Result.Ok(e)
  }
}
