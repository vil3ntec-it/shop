package ir.vil3ntec.tohid.core.ops

import ir.vil3ntec.tohid.core.platform.sha256
import ir.vil3ntec.tohid.sync.v1.Ulid

/*
 *  OPS-03 — گزارشِ از کار افتادنِ برنامهٔ نصبی، با شناسه و بی دادهٔ شخصی.
 *
 *  ── چه می‌رود ─────────────────────────────────────────────────────────
 *  فقط نامِ کلاسِ خطا، پیامش و Stack ِ کوتاه — پس از پوشاندنِ هر چیزی که به کسی
 *  برگردد (ایمیل، شمارهٔ افغانستان/ایران با رقمِ لاتین، فارسی یا عربی، توکنِ بلند،
 *  و هر رشتهٔ ۷ رقمی یا بلندتر: کارت، شناسه، مبلغِ دقیق). نه لاگ، نه دادهٔ دفتر، نه نامِ
 *  دکان. پوشاندن همین‌جا روی دستگاه و **پیش از نوشتن روی دیسک** است؛ سرور
 *  (`server/src/routes/errors.js`) هم دوباره می‌پوشاند.
 *
 *  ── شناسه ─────────────────────────────────────────────────────────────
 *  `cer_<ULID>` روی همان دستگاه ساخته می‌شود و به کاربر نشان داده می‌شود («این کد را به
 *  پشتیبانی بگویید»)؛ سرور همان را شناسهٔ ردیف می‌کند و فرستادنِ دوباره (نت قطع شد پیش از
 *  پاسخ) ردیفِ دوم نمی‌سازد.
 *
 *  ── صف ────────────────────────────────────────────────────────────────
 *  حلقهٔ خطا (همان خطا پشتِ هم) یک ردیف با شمارنده است، نه صد ردیف؛ حداکثر [MAX_PENDING]
 *  گزارش، و گزارشِ کهنه‌تر از [TTL_MS] دور ریخته می‌شود. منطقِ خالص؛ نوشتن روی دیسک،
 *  نصبِ گیرندهٔ خطا و فرستادن کارِ برنامه است (`android-native/…/data/platform/CrashReporter.kt`).
 */

/** یک گزارشِ پوشانده‌شده — همان که روی دیسک می‌ماند و می‌رود. */
data class CrashReport(
  val id: String,
  val atMs: Long,
  val appVersion: String,
  val message: String,
  val stack: String,
  /** اثرِ کلاس‌ها و چند قابِ اول — برای یکی کردنِ حلقهٔ خطا */
  val signature: String,
  val count: Int = 1,
  /** کد به کاربر نشان داده شد */
  val shown: Boolean = false,
  /** سرور گرفت (۲xx) */
  val sent: Boolean = false,
)

object CrashReports {

  const val ID_PREFIX = "cer_"
  const val MAX_MESSAGE = 500
  const val MAX_STACK = 4000
  const val MAX_PENDING = 20
  const val TTL_MS = 7L * 24 * 60 * 60 * 1000
  /** همان خطا در این بازه ⇒ همان ردیف، شمارنده بالا */
  const val DEDUPE_MS = 60L * 60 * 1000
  /** کد فقط برای خطای تازه نشان داده می‌شود */
  const val NOTICE_MS = 24L * 60 * 60 * 1000

  val ID = Regex("^cer_[0-9A-HJKMNP-TV-Z]{26}$")

  /** هر رقم در سه خط: لاتین، فارسی (U+06F0) و عربی (U+0660) */
  private const val D = "[0-9۰-۹٠-٩]"
  private fun dg(vararg ds: Int): String =
    ds.joinToString("", "[", "]") { d -> "$d" + ('۰' + d) + ('٠' + d) }
  private val EMAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+")
  //  همان دو شکلِ سرور (۰۷xxxxxxxx افغانستان، ۰۹xxxxxxxxx ایران، با یا بی کدِ کشور)، در هر سه خطِ رقم
  private val PHONE_AF = Regex("(?<!$D)(?:\\+?${dg(9)}${dg(3, 8)}[\\s-]?)?${dg(0)}?${dg(7)}$D{8}(?!$D)")
  private val PHONE_IR = Regex("(?<!$D)(?:\\+?${dg(9)}${dg(8)}[\\s-]?)?${dg(0)}?${dg(9)}$D{9}(?!$D)")
  private val TOKEN = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{32,}(?![A-Za-z0-9_-])")
  private val LONG_NUMBER = Regex("$D{7,}")
  private val FRAME = Regex("^\\s*at\\s+(.+)$")

  fun newId(nowMs: Long): String = ID_PREFIX + Ulid.next(nowMs)

  /** هر چیزی که به کسی برگردد پوشانده می‌شود؛ بعد بریده به [limit] نویسه. */
  fun scrub(text: String, limit: Int): String {
    var s = text
    s = EMAIL.replace(s, "[ایمیل]")
    s = PHONE_AF.replace(s, "[شماره]")
    s = PHONE_IR.replace(s, "[شماره]")
    s = TOKEN.replace(s, "[توکن]")
    s = LONG_NUMBER.replace(s, "[عدد]")
    return if (s.length <= limit) s else s.substring(0, limit)
  }

  /** خطای گرفته‌نشده ⇒ گزارشِ پوشانده‌شده. خودش هرگز خطا نمی‌دهد (برنامه دارد می‌شکند). */
  fun fromThrowable(t: Throwable, appVersion: String, nowMs: Long, id: String = newId(nowMs)): CrashReport {
    val name = t::class.simpleName ?: "Throwable"
    val raw = runCatching { t.stackTraceToString() }.getOrDefault(name)
    val stack = scrub(raw, MAX_STACK)
    val message = scrub(listOfNotNull(name, t.message?.takeIf { it.isNotBlank() }).joinToString(": "), MAX_MESSAGE)
    return CrashReport(
      id = id, atMs = nowMs, appVersion = scrub(appVersion, 32),
      message = message, stack = stack, signature = signatureOf(name, stack),
    )
  }

  /**
   *  اثرِ یک خطا: نامِ کلاس و پنج قابِ اول (بی پیام، پس پیامِ عوض‌شونده — شماره، زمان —
   *  دو خطای یکسان را دو تا نمی‌کند). ۱۶ نویسهٔ hex از SHA-256.
   */
  fun signatureOf(name: String, stack: String): String {
    val frames = stack.lineSequence().mapNotNull { FRAME.find(it)?.groupValues?.get(1)?.trim() }.take(5).toList()
    val bytes = sha256((listOf(name) + frames).joinToString("\n").encodeToByteArray())
    return bytes.take(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
  }

  /** کهنه‌تر از [TTL_MS] و گزارشِ با شناسهٔ نادرست بیرون */
  fun prune(pending: List<CrashReport>, nowMs: Long): List<CrashReport> =
    pending.filter { ID.matches(it.id) && nowMs - it.atMs in 0..TTL_MS }

  /**
   *  گزارشِ تازه به صف: همان اثر در [DEDUPE_MS] ِ اخیر ⇒ همان ردیف با شمارندهٔ بالاتر
   *  (و هنوز نفرستاده)؛ وگرنه ردیفِ تازه. حداکثر [MAX_PENDING]، تازه‌ترها می‌مانند.
   */
  fun enqueue(pending: List<CrashReport>, report: CrashReport, nowMs: Long): List<CrashReport> {
    val live = prune(pending, nowMs)
    //  فقط با ردیفِ هنوز نفرستاده یکی می‌شود؛ تکرار پس از فرستادن ردیفِ تازه است
    val same = live.lastOrNull { !it.sent && it.signature == report.signature && report.atMs - it.atMs in 0..DEDUPE_MS }
    val next = if (same != null) {
      live.map { if (it.id == same.id) it.copy(count = it.count + 1) else it }
    } else live + report
    return next.sortedBy { it.atMs }.takeLast(MAX_PENDING)
  }

  /** آن‌چه باید فرستاده شود (زنده و هنوز نفرستاده)، کهنه‌ترین اول */
  fun toSend(pending: List<CrashReport>, nowMs: Long): List<CrashReport> =
    prune(pending, nowMs).filter { !it.sent }.sortedBy { it.atMs }

  /** پاسخِ ۲xx ِ سرور ⇒ «فرستاده» (ردیف تا نشان دادنِ کد می‌ماند) */
  fun markSent(pending: List<CrashReport>, ids: Set<String>): List<CrashReport> =
    pending.map { if (it.id in ids) it.copy(sent = true) else it }

  /**
   *  کارِ تمام‌شده بیرون: فرستاده و (نشان‌داده یا کهنه‌تر از [NOTICE_MS] که دیگر نشان داده نمی‌شود)؛
   *  به‌اضافهٔ [prune].
   */
  fun settle(pending: List<CrashReport>, nowMs: Long): List<CrashReport> =
    prune(pending, nowMs).filter { !(it.sent && (it.shown || nowMs - it.atMs > NOTICE_MS)) }

  /** تازه‌ترین گزارشِ نشان‌داده‌نشدهٔ [NOTICE_MS] ِ اخیر — یک بار به کاربر گفته می‌شود */
  fun notice(pending: List<CrashReport>, nowMs: Long): CrashReport? =
    pending.filter { !it.shown && nowMs - it.atMs in 0..NOTICE_MS }.maxByOrNull { it.atMs }

  fun markShown(pending: List<CrashReport>, id: String): List<CrashReport> =
    pending.map { if (it.id == id) it.copy(shown = true) else it }

  /**
   *  بدنهٔ `POST /api/errors` — همان نام‌های سرور. شمارنده در `log_tail` (لاگی نمی‌رود).
   *  شناسهٔ دستگاه و نسخه را سرآیندهای همیشگیِ برنامه (`X-Device`، `X-App-Version`) می‌برند.
   */
  fun payload(r: CrashReport): Map<String, String> = mapOf(
    "report_id" to r.id,
    "app" to "shop",
    "app_version" to r.appVersion,
    "message" to r.message,
    "stack" to r.stack,
    "log_tail" to "count=${r.count}",
  )
}
