package ir.vil3ntec.tohid.core.backup

import ir.vil3ntec.tohid.core.calendar.BusinessDay
import ir.vil3ntec.tohid.core.calendar.WeekRules

/**
 *  کدام نسخه‌های خودکارِ روی دستگاه بمانند — OPS-02: «نگه‌داری ۷ نسخهٔ
 *  روزانه و ۴ نسخهٔ هفتگی».
 *
 *  ── قاعده (همان معنای رایجِ keep-last / keep-daily / keep-weekly) ────
 *  نسخه‌ها از تازه به کهنه مرتب می‌شوند (هم‌زمان‌ها با شناسه، تا جواب
 *  همیشه یکی باشد). یک نسخه می‌ماند اگر دستِ‌کم یکی از این سه را داشته باشد:
 *    • یکی از `last` تای آخر است — ۶، یعنی همان ضمانتِ پیشینِ
 *      `AutoBackup.KEEP = 6`؛ این قاعده هیچ نسخه‌ای را که دیروز می‌ماند
 *      زودتر پاک نمی‌کند، و با نسخهٔ نیم‌ساعته چند ساعتِ آخرِ کار را هم دارد؛
 *    • تازه‌ترین نسخهٔ یکی از `daily` روزِ اخیری است که نسخه دارند؛
 *    • تازه‌ترین نسخهٔ یکی از `weekly` هفتهٔ اخیری است که نسخه دارند.
 *  پس بیشینه ۶ + ۷ + ۴ = ۱۷ فایل، و معمولاً کمتر (دسته‌ها هم‌پوشانی دارند).
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── روز و هفته مالِ دکان‌اند، نه UTC و نه تقویمِ خشک ───────────────
 *  روز = `BusinessDay.businessDateAt`: به وقتِ دکان (اختلافِ ساعتِ همان
 *  لحظه، پس تغییرِ ساعتِ تابستانی هم درست است) و با «آغازِ روزِ کاری»
 *  (دکانی که تا ۲ بامداد باز است، نسخهٔ ۱:۳۰ را از دیروز می‌شمارد).
 *  هفته = `BusinessDay.weekStartOf` با آغازِ هفتهٔ دکان (پیش‌فرض شنبه).
 *  روزی که دکان بسته بوده و نسخه‌ای ندارد شمرده نمی‌شود: «۷ روزانه» یعنی
 *  ۷ روزِ کاریِ اخیر، نه ۷ روزِ تقویم.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  خالص است — بی فایل، بی ساعتِ دستگاه؛ اندروید و کامپیوتر هر دو همین را
 *  صدا می‌زنند (`LocalSnapshots.prune`).
 */
object BackupRetention {

  /** یک نسخه: شناسه (نامِ فایل) و زمانش (میلی‌ثانیهٔ یونیکس) */
  data class Snap(val id: String, val at: Long)

  data class Policy(
    val last: Int = 6,
    val daily: Int = 7,
    val weekly: Int = 4,
    /** آغازِ روزِ کاری، دقیقه پس از نیمه‌شب (۰ = نیمه‌شب) */
    val dayStartMinutes: Int = 0,
    val week: WeekRules = WeekRules(),
  )

  /**
   *  شناسهٔ نسخه‌هایی که می‌مانند. `utcOffsetMinutes(at)` اختلافِ ساعتِ دکان
   *  با UTC در همان لحظه است (کابل ۲۷۰).
   */
  fun keep(snaps: List<Snap>, utcOffsetMinutes: (Long) -> Int, policy: Policy = Policy()): Set<String> {
    val ordered = snaps.sortedWith(compareByDescending<Snap> { it.at }.thenByDescending { it.id })
    val keep = LinkedHashSet<String>()
    ordered.take(policy.last.coerceAtLeast(0)).forEach { keep += it.id }
    val days = HashSet<String>()
    val weeks = HashSet<String>()
    for (s in ordered) {
      val day = BusinessDay.businessDateAt(s.at, utcOffsetMinutes(s.at), policy.dayStartMinutes)
      if (days.size < policy.daily && days.add(day)) keep += s.id
      val week = BusinessDay.weekStartOf(day, policy.week) ?: day
      if (weeks.size < policy.weekly && weeks.add(week)) keep += s.id
    }
    return keep
  }
}
