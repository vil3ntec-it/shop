package ir.vil3ntec.tohid.core.sync

/**
 *  SYN-12 (§۶.۱۲، §۴.۱): همگام‌سازی چرا نرسید — شبکهٔ خودِ دستگاه، سرور، یا راهِ میانِ این دو
 *  (قطعیِ سراسری) — و آیا کاربر باید کاری بکند. یک تابعِ خالص تا هر دو برنامه یک‌جور ببینند.
 *
 *  ⛔ **هشدارِ بی‌مورد نه:** قطعیِ کوتاه (کم‌تر از [SUSTAIN_FAILURES] شکست یا [SUSTAIN_MS]) فقط «دوباره
 *     می‌کوشد» است؛ قطعیِ ماندگار «اعلان» (نوارِ آرام، بی سرخ)؛ سرخ فقط وقتی کاربر کاری دارد (نشست،
 *     خطای واقعی). چیزی پنهان نمی‌شود: `lastError` همان می‌ماند و نقطه هرگز سبز نمی‌شود.
 *  ⛔ نشست و خطای واقعی بالاتر از «آفلاین» می‌نشینند (همان قاعدهٔ همیشگیِ چراغ)؛ شکستِ شبکه یا سرور
 *     روی دستگاهِ بی‌نت «آفلاین» است — حالِ امروز.
 *  ⛔ هیچ چیزی از صف، فاصلهٔ تلاش (۲ث × ۲ⁿ ±۲۵٪ تا ۵ دقیقه) یا `Retry-After` این‌جا عوض نمی‌شود.
 *
 *  `status`: کدِ HTTP ِ شکستِ آخر؛ [NO_RESPONSE] = بی پاسخ (قطعِ شبکه، مهلت)؛ [LOCAL] = نه شبکه نه HTTP
 *  (دیسک، ژورنال) یا ناشناخته ⇒ همان سرخِ پیشین.
 *
 *  قرینهٔ `outageOf` / `outageStreak` / `outageFrom` / `dotOf` در `license/sync-core.js`؛ هر دو با
 *  `core/test-vectors/sync-outage-v1.json`. Kotlinِ خالص (commonMain)، بی java.*.
 */
object SyncOutage {
  /** دست‌کم این شمار شکستِ پیاپی… */
  const val SUSTAIN_FAILURES: Int = 2

  /** …و این مدت از نخستین شکستِ همین رشته تا «قطعی» شمرده شود */
  const val SUSTAIN_MS: Long = 60_000L

  /** پاسخِ HTTP نیامد */
  const val NO_RESPONSE: Int = 0

  /** شکستِ محلی یا ناشناخته */
  const val LOCAL: Int = -1

  enum class Kind(val id: String) {
    OK("ok"),
    /** شبکهٔ خودِ دستگاه */
    OFFLINE("offline"),
    /** قطعیِ کوتاه — خودش دوباره می‌فرستد */
    RETRYING("retrying"),
    /** دستگاه شبکه دارد و سرور پاسخی نمی‌دهد: اینترنتِ بیرون (قطعیِ سراسری) یا خودِ سرور */
    UNREACHABLE("unreachable"),
    /** سرور پاسخ داد ولی از کار افتاده یا پرکار است (۵xx، ۴۲۹) */
    SERVER("server"),
    /** نشست یا اجازه — کاربر باید دوباره وارد شود */
    AUTH("auth"),
    /** خطای واقعی (۴xx ِ دیگر، دیسک) */
    ERROR("error"),
  }

  enum class Level(val id: String) { NONE("none"), NOTICE("notice"), ALARM("alarm") }

  enum class Dot(val id: String) { GREEN("green"), YELLOW("yellow"), GREY("grey"), RED("red") }

  data class Verdict(val kind: Kind, val level: Level) {
    /** سرخ — کاربر کاری دارد */
    val alarm: Boolean get() = level == Level.ALARM
  }

  /** رشتهٔ شکستِ پیاپیِ جاری، در حافظهٔ موتور (نه روی دیسک): کدِ آخر و زمانِ نخستین شکست */
  data class Streak(val status: Int, val code: String, val error: String, val since: Long)

  private val AUTH_CODES = setOf("session_expired", "no_session", "session_required")
  private val OFFLINE_CODES = setOf("offline")

  /** بی پاسخ یا مهلتِ بارگذاری (۴۰۸ ِ میان‌راهی روی 2G) */
  fun network(status: Int): Boolean = status == NO_RESPONSE || status == 408

  /** سرور پاسخ داد و خودش گرفتار است */
  fun serverSide(status: Int): Boolean = status == 429 || status in 500..599

  fun level(kind: Kind): Level = when (kind) {
    Kind.AUTH, Kind.ERROR -> Level.ALARM
    Kind.UNREACHABLE, Kind.SERVER -> Level.NOTICE
    else -> Level.NONE
  }

  /**
   *  @param online شبکهٔ دستگاه از نگاهِ سیستم
   *  @param failed دورِ آخر شکست خورد (`lastError` هست)
   *  @param failures شکست‌های پیاپی (`attempt` ِ موتور)
   *  @param streakMs از نخستین شکستِ همین رشته تا حالا (۰ = تازه یا ناشناخته)
   */
  fun classify(online: Boolean, failed: Boolean, status: Int, code: String, failures: Int, streakMs: Long): Verdict {
    val offlineCode = code in OFFLINE_CODES
    val kind = when {
      !failed -> if (online) Kind.OK else Kind.OFFLINE
      code in AUTH_CODES || status == 401 || status == 403 -> Kind.AUTH
      !(offlineCode || network(status) || serverSide(status)) -> Kind.ERROR
      !online || offlineCode -> Kind.OFFLINE
      failures < SUSTAIN_FAILURES || streakMs < SUSTAIN_MS -> Kind.RETRYING
      network(status) -> Kind.UNREACHABLE
      else -> Kind.SERVER
    }
    return Verdict(kind, level(kind))
  }

  /**
   *  شکستِ تازه ⇒ رشتهٔ تازه یا ادامهٔ همان. ادامه فقط وقتی `lastError` ِ پیش از این شکست همان
   *  خطای رشته بود (دستگاه هنوز در همان شکست بود)؛ دورِ موفق یا خطای نویسندهٔ دیگر ⇒ از نو.
   */
  fun next(prev: Streak?, lastErrorBefore: String, status: Int, code: String, error: String, now: Long): Streak {
    val since = if (prev != null && lastErrorBefore.isNotBlank() && prev.error == lastErrorBefore) prev.since else now
    return Streak(status, code, error, since)
  }

  /**
   *  حکمِ همین حالا از رشتهٔ در حافظه و `lastError` ِ ماندگار. رشته‌ای که خطایش همین `lastError`
   *  نیست (پس از بازگشایی، یا نوشتهٔ جای دیگر) ⇒ کدِ ناشناخته ⇒ همان سرخِ پیشین.
   */
  fun of(streak: Streak?, lastError: String, online: Boolean, failures: Int, now: Long): Verdict {
    val failed = lastError.isNotBlank()
    val s = if (failed && streak != null && streak.error == lastError) streak else null
    return classify(
      online, failed,
      s?.status ?: LOCAL, s?.code.orEmpty(), failures,
      if (s == null) 0L else maxOf(0L, now - s.since),
    )
  }

  /** چراغ: سرخ فقط با `alarm`؛ شکستِ بی هشدار هرگز سبز نیست (زرد، یا خاکستری بی‌نت) */
  fun dot(error: Boolean, alarm: Boolean, online: Boolean, signedIn: Boolean, configured: Boolean, queued: Int, busy: Boolean): Dot = when {
    error && alarm -> Dot.RED
    !online || !signedIn || !configured -> Dot.GREY
    error || queued > 0 || busy -> Dot.YELLOW
    else -> Dot.GREEN
  }
}
