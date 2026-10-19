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

  /** همان الگوی سرور (`SLUG_RE`) */
  val SLUG_RE = Regex("^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$")

  /** همان فهرستِ رزروِ سرور */
  val RESERVED: Set<String> = setOf(
    "api", "www", "admin", "panel", "portal", "sdk", "shop", "store", "stores", "beta", "app", "apps",
    "pump", "mail", "smtp", "imap", "pop", "ftp", "ns1", "ns2", "cdn", "static", "assets", "help",
    "support", "status", "docs", "blog", "dev", "test", "staging", "login", "auth", "account",
    "billing", "pay", "payment", "download", "downloads", "home", "vill3n", "tohid", "official",
  )

  /** روزهای هفته به ترتیبِ سرور: ۰ = یکشنبه */
  val DAY_NAMES = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")

  /** ترتیبِ نمایش برای کاربرِ افغانستان: از شنبه */
  val DISPLAY_DAYS = listOf(6, 0, 1, 2, 3, 4, 5)

  const val MAX_PRICE_MINOR = 100_000_000_000L
  const val MAX_FEE_MINOR = 100_000_000L
  const val MAX_QTY_MILLI = 10_000_000L

  /* ------------------------------ ورودی ------------------------------ */

  /** رقمِ فارسی/عربی ⇒ لاتین؛ «٫» ⇒ «.»؛ جداکنندهٔ هزارگان حذف */
  fun asciiDigits(raw: String): String = buildString {
    for (c in raw.trim()) when (c) {
      in '۰'..'۹' -> append('0' + (c - '۰'))
      in '٠'..'٩' -> append('0' + (c - '٠'))
      '٫' -> append('.')
      '٬', ',', '،', ' ', '‌' -> Unit
      else -> append(c)
    }
  }

  /** نشانیِ ویترین — پیامِ خطا یا null */
  fun slugError(raw: String): String? {
    val s = raw.trim().lowercase()
    return when {
      s.isEmpty() -> "نشانی لازم است"
      !SLUG_RE.matches(s) || s.contains("--") -> "۳ تا ۳۲ حرفِ کوچکِ انگلیسی، رقم یا خطِ تیره"
      s in RESERVED -> "این نشانی رزرو است"
      else -> null
    }
  }

  /** «۱۲۰٫۵» ⇒ ۱۲۰۵۰ (یک‌صدم)؛ خالی ⇒ null؛ نادرست یا منفی ⇒ IllegalArgumentException */
  fun parseMinor(raw: String, max: Long = MAX_PRICE_MINOR): Long? {
    val s = asciiDigits(raw)
    if (s.isEmpty()) return null
    require(Regex("^\\d{1,12}(\\.\\d{1,2})?$").matches(s)) { "مبلغ درست نیست" }
    val parts = s.split('.')
    val minor = parts[0].toLong() * 100 + (parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L)
    require(minor <= max) { "مبلغ بیش از حد بزرگ است" }
    return minor
  }

  /** ۱۲۰۵۰ ⇒ «120.5»، ۳۰۰۰۰ ⇒ «300» — برای کادرِ ویرایش */
  fun formatMinor(minor: Long): String {
    val whole = minor / 100
    val frac = minor % 100
    return if (frac == 0L) "$whole" else "$whole." + pad(frac, 2).trimEnd('0')
  }

  /** «۲٫۵» ⇒ ۲۵۰۰ (هزارم)؛ خالی ⇒ null */
  fun parseQtyMilli(raw: String): Long? {
    val s = asciiDigits(raw)
    if (s.isEmpty()) return null
    require(Regex("^\\d{1,6}(\\.\\d{1,3})?$").matches(s)) { "مقدار درست نیست" }
    val parts = s.split('.')
    val milli = parts[0].toLong() * 1000 + (parts.getOrNull(1)?.padEnd(3, '0')?.toLong() ?: 0L)
    require(milli in 1..MAX_QTY_MILLI) { "مقدار باید بیش از صفر باشد" }
    return milli
  }

  fun formatQtyMilli(milli: Long): String {
    val whole = milli / 1000
    val frac = milli % 1000
    return if (frac == 0L) "$whole" else "$whole." + pad(frac, 3).trimEnd('0')
  }

  /** «8:30» ⇒ ۵۱۰؛ «24:00» ⇒ ۱۴۴۰؛ نادرست ⇒ null */
  fun parseHm(raw: String): Int? {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(asciiDigits(raw).replace('.', ':')) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (min > 59 || h > 24 || (h == 24 && min != 0)) return null
    return h * 60 + min
  }

  fun formatHm(minutes: Int): String = pad(minutes / 60L, 2) + ":" + pad(minutes % 60L, 2)

  /** بی `String.format`: آن رقم را به زبانِ دستگاه (فارسی) می‌نوشت */
  private fun pad(n: Long, width: Int): String = n.toString().padStart(width, '0')

  /** نتیجهٔ خواندنِ ساعت‌های یک روز */
  data class DayHours(val ranges: List<Pair<Int, Int>>, val error: String? = null)

  /**
   *  «08:00-12:00، 14:00-20:00» ⇒ بازه‌ها (دقیقه، `[آغاز، پایان)`). خالی = آن روز
   *  تعطیل. همان قاعدهٔ سرور: حداکثر ۴ بازه، پایان پس از آغاز، بی هم‌پوشانی.
   *  شبِ پس از نیمه‌شب در روزِ بعد نوشته می‌شود (۰۰:۰۰ تا …).
   */
  fun parseDay(raw: String): DayHours {
    val s = raw.trim()
    if (s.isEmpty()) return DayHours(emptyList())
    val parts = s.split(',', '،', ';', '؛').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size > 4) return DayHours(emptyList(), "حداکثر ۴ بازه در روز")
    val ranges = mutableListOf<Pair<Int, Int>>()
    for (p in parts) {
      val ends = p.split('-', '–', '—').map { it.trim() }
      if (ends.size != 2) return DayHours(emptyList(), "هر بازه مثلِ 08:00-12:00 نوشته شود")
      val a = parseHm(ends[0]) ?: return DayHours(emptyList(), "ساعتِ «${ends[0]}» درست نیست")
      val b = parseHm(ends[1]) ?: return DayHours(emptyList(), "ساعتِ «${ends[1]}» درست نیست")
      if (a >= 1440 || b <= a) return DayHours(emptyList(), "پایانِ بازه باید پس از آغاز باشد (شبِ پس از ۲۴ را در روزِ بعد بنویسید)")
      ranges += a to b
    }
    ranges.sortBy { it.first }
    for (i in 1 until ranges.size) {
      if (ranges[i].first < ranges[i - 1].second) return DayHours(emptyList(), "بازه‌ها روی هم افتاده‌اند")
    }
    return DayHours(ranges)
  }

  fun formatDay(ranges: List<Pair<Int, Int>>): String =
    ranges.joinToString("، ") { "${formatHm(it.first)}-${formatHm(it.second)}" }

  /** «2026-10-20، 2026-11-01» ⇒ فهرست؛ خطا با متن */
  fun parseClosedDates(raw: String): Pair<List<String>, String?> {
    val items = raw.split(',', '،', ' ', '\n', '\t').map { asciiDigits(it) }.filter { it.isNotEmpty() }
    if (items.size > 60) return emptyList<String>() to "حداکثر ۶۰ روزِ تعطیل"
    for (d in items) if (!validDate(d)) return emptyList<String>() to "تاریخِ «$d» باید مثلِ 2026-10-20 باشد"
    return items.distinct().sorted() to null
  }

  fun validDate(s: String): Boolean {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(s) ?: return false
    val (y, mo, d) = m.destructured
    val month = mo.toInt()
    val day = d.toInt()
    if (month !in 1..12 || day < 1) return false
    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y.toInt(), month - 1, 1) }
    return day <= c.getActualMaximum(Calendar.DAY_OF_MONTH)
  }

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

  fun toFa(s: String): String = buildString { for (ch in s) append(if (ch in '0'..'9') '۰' + (ch - '0') else ch) }

  /* ------------------------------ صفِ اعلام (PRES-02/08/09) ------------------------------ */

  /**
   *  اعلامی که هنوز به سرور نرسیده. همان `eventId` و همان زمانِ دستگاه دوباره
   *  فرستاده می‌شود ⇒ سرور تکرار را بی‌اثر می‌کند و اعلامِ کهنه وضعیتِ تازه‌ترِ
   *  دستگاهِ دیگر را برنمی‌گرداند.
   */
  data class PendingPresence(val eventId: String, val mode: String, val declaredUntil: Long?, val clientEventAt: Long)

  private val EVENT_RE = Regex("^[A-Za-z0-9_.:-]{8,80}$")
  private val MODES = setOf("auto", "online", "online_until", "offline", "closed")

  fun newEventId(now: Long, random: Long): String =
    "pres-${now.toString(36)}-${java.lang.Long.toHexString(random and 0x7fffffffffffL)}"

  fun pending(mode: String, declaredUntil: Long?, now: Long, random: Long): PendingPresence {
    require(mode in MODES) { "bad mode" }
    require(mode != "online_until" || declaredUntil != null) { "declaredUntil required" }
    return PendingPresence(newEventId(now, random), mode, if (mode == "online_until") declaredUntil else null, now)
  }

  fun encode(p: PendingPresence): String = listOf(p.eventId, p.mode, p.declaredUntil?.toString() ?: "", p.clientEventAt.toString()).joinToString("|")

  fun decode(raw: String?): PendingPresence? {
    val parts = raw?.split('|') ?: return null
    if (parts.size != 4) return null
    val (id, mode, until, at) = parts
    if (!EVENT_RE.matches(id) || mode !in MODES) return null
    val clientAt = at.toLongOrNull() ?: return null
    val du = until.takeIf { it.isNotEmpty() }?.toLongOrNull()
    if (mode == "online_until" && du == null) return null
    return PendingPresence(id, mode, du, clientAt)
  }

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
