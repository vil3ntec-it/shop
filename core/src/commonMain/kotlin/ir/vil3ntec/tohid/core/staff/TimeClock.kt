package ir.vil3ntec.tohid.core.staff

import ir.vil3ntec.tohid.core.calendar.BusinessDay

/**
 *  STF-07 — ثبتِ ورود و خروجِ کارمند (§۶.۹). کاتلینِ خالص، بی ساعت و بی ذخیره: برنامه «زدن»ها
 *  (`Punch`) را می‌دهد و این‌جا فقط سنجیده و جفت می‌شوند.
 *
 *  قاعده‌ها:
 *  • هر کارمند در هر لحظه یا «بیرون» است یا «سرِ کار»؛ زدنِ بعدی همیشه برعکسِ زدنِ قبلی است
 *    (`next`). دو بار پشتِ هم «ورود» (دو ضربه، یا دو دستگاه) پیش از ثبت رد می‌شود (`check`).
 *  • زدنِ تازه پیش از آخرین زدنِ همان کارمند پذیرفته نمی‌شود: عقب کشیدنِ ساعتِ دستگاه راهِ
 *    کوتاه کردنِ ساعتِ کار نیست.
 *  • جفت کردن قطعی است: به ترتیبِ زمان و سپس شناسه، و زدنِ تکراری (همان `id`، مثلاً با
 *    همگام‌سازیِ دوباره) یک بار شمرده می‌شود.
 *  • هر چیزِ ناجور **شمرده نمی‌شود و نشان داده می‌شود**، نه این‌که حدس زده شود: ورودِ بی خروج
 *    (`MISSING_OUT`)، خروجِ بی ورود (`MISSING_IN`)، نوبتِ بلندتر از سقف (`TOO_LONG`، پیش‌فرض
 *    ۱۶ ساعت) و نوبتِ هنوز باز (`OPEN`). درست کردنشان کارِ مدیر است، با زدنِ تازه.
 *  • روزِ هر نوبت روزِ کاریِ **ورود** است (`BusinessDay.businessDateAt`)، پس نوبتِ شبی که از
 *    نیمه‌شب می‌گذرد دو تکه نمی‌شود.
 */
object TimeClock {

  enum class Kind(val code: String) { IN("in"), OUT("out") }

  data class Punch(val id: String, val staffId: String, val kind: Kind, val atMs: Long, val deviceId: String = "")

  enum class Issue(val code: String) {
    MISSING_OUT("missing_out"), MISSING_IN("missing_in"), TOO_LONG("too_long"), OPEN("open"),
  }

  /** چرا زدنِ تازه پذیرفته نمی‌شود */
  enum class Rejection(val code: String) {
    DUPLICATE("duplicate"), SAME_KIND("same_kind"), BEFORE_LAST("before_last"), BAD_INPUT("bad_input"),
  }

  /**
   *  یک نوبت. `minutes` فقط برای نوبتِ سالم پر است (بی `issue`)؛ `rawMinutes` مدتِ خام است هرجا
   *  که حساب‌شدنی است (نوبتِ باز تا `nowMs`) تا مدیر ببیند، ولی در جمع نمی‌آید.
   */
  data class Span(
    val staffId: String,
    val inId: String?,
    val outId: String?,
    val startMs: Long?,
    val endMs: Long?,
    val businessDate: String,
    val minutes: Long,
    val rawMinutes: Long?,
    val issue: Issue?,
  )

  data class DayTotal(val staffId: String, val businessDate: String, val minutes: Long, val issues: Int)

  const val DEFAULT_MAX_MINUTES: Long = 16 * 60

  private fun ordered(punches: List<Punch>): List<Punch> =
    punches.distinctBy { it.id }.sortedWith(compareBy<Punch> { it.atMs }.thenBy { it.id })

  /** زدنِ بعدیِ این کارمند: «ورود» اگر بیرون است، «خروج» اگر سرِ کار است. */
  fun next(punches: List<Punch>, staffId: String): Kind =
    if (ordered(punches).lastOrNull { it.staffId == staffId }?.kind == Kind.IN) Kind.OUT else Kind.IN

  /** آیا `p` را می‌شود ثبت کرد؟ ⇒ `null` یعنی بله. */
  fun check(punches: List<Punch>, p: Punch): Rejection? {
    if (p.id.isBlank() || p.staffId.isBlank() || p.atMs <= 0) return Rejection.BAD_INPUT
    if (punches.any { it.id == p.id }) return Rejection.DUPLICATE
    val last = ordered(punches).lastOrNull { it.staffId == p.staffId } ?: return null
    if (p.atMs < last.atMs) return Rejection.BEFORE_LAST
    if (p.kind == last.kind) return Rejection.SAME_KIND
    return null
  }

  /**
   *  نوبت‌های همهٔ کارمندان، به ترتیبِ کارمند و زمان. `utcOffsetMinutes` اختلافِ ساعتِ دکان با UTC
   *  (کابل ۲۷۰) و `dayStartMinutes` آغازِ روزِ کاریِ دکان است (همان `BusinessDay`).
   */
  fun spans(
    punches: List<Punch>,
    nowMs: Long,
    utcOffsetMinutes: Int,
    dayStartMinutes: Int = 0,
    maxMinutes: Long = DEFAULT_MAX_MINUTES,
  ): List<Span> {
    require(maxMinutes > 0) { "سقفِ نوبت باید مثبت باشد: $maxMinutes" }
    fun day(ms: Long) = BusinessDay.businessDateAt(ms, utcOffsetMinutes, dayStartMinutes)
    val out = ArrayList<Span>()
    for ((staff, list) in ordered(punches).groupBy { it.staffId }.entries.sortedBy { it.key }.map { it.key to it.value }) {
      var open: Punch? = null
      for (p in list) {
        if (p.kind == Kind.IN) {
          open?.let { out += Span(staff, it.id, null, it.atMs, null, day(it.atMs), 0, null, Issue.MISSING_OUT) }
          open = p
        } else {
          val o = open
          if (o == null) {
            out += Span(staff, null, p.id, null, p.atMs, day(p.atMs), 0, null, Issue.MISSING_IN)
          } else {
            val raw = (p.atMs - o.atMs) / 60_000L
            val tooLong = raw > maxMinutes
            out += Span(staff, o.id, p.id, o.atMs, p.atMs, day(o.atMs), if (tooLong) 0 else raw, raw, if (tooLong) Issue.TOO_LONG else null)
            open = null
          }
        }
      }
      open?.let {
        val raw = if (nowMs > it.atMs) (nowMs - it.atMs) / 60_000L else 0L
        out += Span(staff, it.id, null, it.atMs, null, day(it.atMs), 0, raw, Issue.OPEN)
      }
    }
    return out
  }

  /** جمعِ دقیقه‌های سالمِ هر کارمند در هر روزِ کاری، با شمارِ نوبت‌های ناجورِ همان روز. */
  fun totals(spans: List<Span>): List<DayTotal> =
    spans.groupBy { it.staffId to it.businessDate }
      .map { (k, v) -> DayTotal(k.first, k.second, v.sumOf { it.minutes }, v.count { it.issue != null }) }
      .sortedWith(compareBy<DayTotal> { it.staffId }.thenBy { it.businessDate })
}
