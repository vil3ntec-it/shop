package ir.vil3ntec.tohid.core.storefront

import java.util.Calendar
import java.util.TimeZone

/**
 *  منطقِ خالصِ صفحهٔ «ویترین» ِ صاحب دکان — مشترکِ گوشی و کامپیوتر.
 *
 *  سرور (`server/src/lib/storefront.js`) داورِ نهایی است؛ این‌جا فقط همان
 *  قاعده‌ها پیش از فرستادن سنجیده می‌شوند تا خطا کنارِ همان کادر دیده شود،
 *  و برچسب‌های وضعیت همان باشند که مشتری می‌بیند (§۳۱.۱، PRES-01..09).
 *
 *  ⛔ پول به یک‌صدمِ افغانی (Long) و مقدار به هزارم — بی ممیزِ شناور در دادهٔ رفت‌وبرگشتی.
 *  ⛔ ساعت به منطقهٔ زمانیِ **دکان**، نه گوشی (§۳۱.۱).
 */
object StorefrontOwner {

  /*
   *  قاعده‌های ورودی در `core` (commonMain، `StorefrontRules`) هستند تا آیفون و آزمونِ بی‌دستگاه
   *  همان را داشته باشند؛ این‌جا فقط همان نام‌های قدیمی برای صفحه‌ها و آزمون‌های موجود.
   */
  val SLUG_RE: Regex get() = StorefrontRules.SLUG_RE
  val RESERVED: Set<String> get() = StorefrontRules.RESERVED

  /** روزهای هفته به ترتیبِ سرور: ۰ = یکشنبه */
  val DAY_NAMES = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")

  /** ترتیبِ نمایش برای کاربرِ افغانستان: از شنبه */
  val DISPLAY_DAYS = listOf(6, 0, 1, 2, 3, 4, 5)

  const val MAX_PRICE_MINOR = StorefrontRules.MAX_PRICE_MINOR
  const val MAX_FEE_MINOR = StorefrontRules.MAX_FEE_MINOR
  const val MAX_QTY_MILLI = StorefrontRules.MAX_QTY_MILLI

  fun asciiDigits(raw: String): String = StorefrontRules.asciiDigits(raw)
  fun slugError(raw: String): String? = StorefrontRules.slugError(raw)
  fun parseMinor(raw: String, max: Long = MAX_PRICE_MINOR): Long? = StorefrontRules.parseMinor(raw, max)
  fun formatMinor(minor: Long): String = StorefrontRules.formatMinor(minor)
  fun parseQtyMilli(raw: String): Long? = StorefrontRules.parseQtyMilli(raw)
  fun formatQtyMilli(milli: Long): String = StorefrontRules.formatQtyMilli(milli)
  fun parseHm(raw: String): Int? = StorefrontRules.parseHm(raw)
  fun formatHm(minutes: Int): String = StorefrontRules.formatHm(minutes)
  fun parseDay(raw: String): StorefrontRules.DayHours = StorefrontRules.parseDay(raw)
  fun formatDay(ranges: List<Pair<Int, Int>>): String = StorefrontRules.formatDay(ranges)
  fun parseClosedDates(raw: String): Pair<List<String>, String?> = StorefrontRules.parseClosedDates(raw)
  fun validDate(s: String): Boolean = StorefrontRules.validDate(s)

  private fun pad(n: Long, width: Int): String = n.toString().padStart(width, '0')

  /* ------------------------------ وضعیت (§۳۱.۱) ------------------------------ */

  enum class Tone { OK, WARN, OFF }

  /** «HH:MM» به ساعتِ دکان */
  fun clock(at: Long, timezone: String): String {
    val c = Calendar.getInstance(TimeZone.getTimeZone(timezone)).apply { timeInMillis = at }
    return pad(c.get(Calendar.HOUR_OF_DAY).toLong(), 2) + ":" + pad(c.get(Calendar.MINUTE).toLong(), 2)
  }

  /** برچسبِ وضعیت — همان جمله‌هایی که مشتری در ویترین می‌بیند */
  fun badgeLabel(badge: String, declaredUntil: Long?, timezone: String): String = when (badge) {
    "open_online" -> "باز است · آنلاین"
    "open_unconfirmed" -> "باز است · اتصال تأیید نشده"
    "declared_until" -> if (declaredUntil != null) "تا ساعتِ ${toFa(clock(declaredUntil, timezone))} آنلاین اعلام شده" else "آنلاین اعلام شده"
    "closed" -> "بسته است"
    "offline" -> "موقتاً آفلاین"
    else -> "وضعیت نامعلوم"
  }

  fun badgeTone(badge: String): Tone = when (badge) {
    "open_online", "declared_until" -> Tone.OK
    "open_unconfirmed" -> Tone.WARN
    else -> Tone.OFF
  }

  fun acceptanceLabel(acceptance: String): String = when (acceptance) {
    "accepting" -> "سفارش همین حالا به دکان می‌رسد."
    "delayed" -> "سفارش ثبت می‌شود، ولی مشتری می‌بیند که دیرتر بررسی می‌شود."
    "later" -> "سفارش ثبت می‌شود و مشتری می‌بیند که پس از باز شدنِ دکان بررسی می‌شود."
    else -> ""
  }

  /** آخرین تأییدِ اتصال — فقط زمانِ واقعیِ ثبت‌شده؛ نبودش «هنوز تأیید نشده» (نه «همین حالا») */
  fun connectionLabel(lastConfirmedAt: Long?, timezone: String): String =
    if (lastConfirmedAt == null || lastConfirmedAt <= 0) "اتصالِ دستگاه‌های دکان هنوز تأیید نشده"
    else "آخرین تأییدِ اتصال: ${toFa(clock(lastConfirmedAt, timezone))}"

  /** نامِ اعلامِ مالک */
  fun modeLabel(mode: String): String = when (mode) {
    "auto" -> "طبقِ ساعتِ کاری"
    "online" -> "آنلاین، بی زمانِ پایان"
    "online_until" -> "آنلاین تا ساعتِ مشخص"
    "offline" -> "آفلاین"
    "closed" -> "تعطیل/بسته"
    else -> mode
  }

  /**
   *  «آنلاین تا HH:MM» ⇒ نخستین همین ساعت **به وقتِ دکان** که هنوز نگذشته
   *  (اگر امروز گذشته، فردا). سرور حداقل یک دقیقه بعد و حداکثر ۷ روز را می‌پذیرد.
   */
  fun declaredUntilFor(minutesOfDay: Int, now: Long, timezone: String): Long {
    val c = Calendar.getInstance(TimeZone.getTimeZone(timezone)).apply {
      timeInMillis = now
      set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
      add(Calendar.MINUTE, minutesOfDay)
    }
    if (c.timeInMillis <= now + 60_000) c.add(Calendar.DAY_OF_MONTH, 1)
    return c.timeInMillis
  }

  fun toFa(s: String): String = StorefrontRules.toFa(s)

  /* ------------------------------ صفِ اعلام (PRES-02/08/09) ------------------------------ */

  /** اعلامِ نرسیده — حالا در `core` (`PresenceDeclaration`، صفِ `StorefrontOutbox`) */
  fun newEventId(now: Long, random: Long): String = PresenceDeclaration.newEventId(now, random)
  fun pending(mode: String, declaredUntil: Long?, now: Long, random: Long): PresenceDeclaration =
    PresenceDeclaration.create(mode, declaredUntil, now, random)
  fun encode(p: PresenceDeclaration): String = PresenceDeclaration.encode(p)
  fun decode(raw: String?): PresenceDeclaration? = PresenceDeclaration.decode(raw)

  /** پیامِ هشدار وقتی اعلام هنوز به سرور نرسیده (§۳۱.۲ بندِ ۲) */
  const val NOT_CONFIRMED = "این تغییر هنوز به سرور نرسیده؛ وضعیتِ آنلاینِ شما برای مشتری تأیید نشده است."

  /* ------------------------------ انتشارِ گروهی ------------------------------ */

  data class BulkReport(val ok: Int, val failed: List<Pair<String, String>>) {
    val summary: String get() =
      if (failed.isEmpty()) "${toFa(ok.toString())} کالا انجام شد"
      else "${toFa(ok.toString())} انجام شد، ${toFa(failed.size.toString())} نشد"
  }

  fun bulkReport(results: List<Pair<String, String?>>): BulkReport =
    BulkReport(results.count { it.second == null }, results.mapNotNull { (id, err) -> err?.let { id to it } })
}
