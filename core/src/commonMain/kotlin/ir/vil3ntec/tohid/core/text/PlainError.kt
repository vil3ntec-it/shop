package ir.vil3ntec.tohid.core.text

/**
 *  نگهبانِ «خطای خام» (دستهٔ ۶۳، شاخهٔ e-rows-63i): صاحبِ دکان هرگز نشانی، `http`، کدِ فنی، نامِ استثنا یا ردِ پشته
 *  نمی‌بیند. اجزای مشترکِ خطا (`V3ErrorState`، کادرِ خطای `V3Message`، حالتِ خالیِ خطاییِ `V3EmptyState`، `TohidErrorState`
 *  و پیامِ کوتاهِ خطا) هر متن را از [text] می‌گذرانند:
 *
 *   - متنِ ساده ⇒ همان، دست‌نخورده (متنِ دوستانهٔ جدول‌های `Str*`، پیامِ ردِ موتورها، متنِ ساده‌شدهٔ صفحه‌های همگام‌سازی)؛
 *   - متنی که تکهٔ فنی دارد ⇒ تکهٔ سادهٔ پیش از نخستین نشانهٔ فنی، اگر دست‌کم دو واژه است («سرور در دسترس نیست: java.net…» ⇒
 *     «سرور در دسترس نیست»)؛
 *   - وگرنه null ⇒ صفحه متنِ پیش‌فرضِ همان گونه را می‌گذارد ([kindOf]: بی اینترنت، سرور، یا دیگر).
 *
 *  منطقِ خالص (برنامهٔ دکان، کامپیوتر، آیفون و برنامهٔ خرید)؛ آزمون: `PlainErrorTest` (کامپیوتر).
 */
object PlainError {

  /** گونهٔ خطا برای تصویر و راهنمای پیش‌فرض */
  enum class Kind {
    /** اینترنت یا اتصال نیست (سیمِ کشیده) */
    OFFLINE,
    /** سرور یا خدمتِ بیرونی کار نکرد (ابر با آذرخشِ کوچک) */
    SERVER,
    /** هر چیزِ دیگر: قاعدهٔ کار، ورودی، دیسک */
    OTHER,
  }

  /** نشانه‌های فنی؛ نخستین جای هر کدام مرزِ تکهٔ ساده است */
  private val technical: List<Regex> = listOf(
    //  نشانی و پیوند
    Regex("""(?i)\b(?:https?|wss?|ftp|file|content)://\S*"""),
    Regex("""(?i)\bwww\.\S+"""),
    Regex("""(?i)\bhttps?\b"""),
    Regex("""(?i)\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|ir|af|io|dev|app|local|lan)\b(?:[/:]\S*)?"""),
    Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?\b"""),
    Regex("""(?i)\blocalhost\b"""),
    //  مسیرِ درِ سرور یا پرونده: /api/v1/… یا C:\…
    Regex("""(?:^|\s)/[\w.-]+/[\w./-]*"""),
    Regex("""(?i)\b[a-z]:\\\S*"""),
    //  نامِ استثنا و بسته: java.net.SocketTimeoutException، IOException، kotlinx.…
    Regex("""\b(?:[a-z_][\w$]*\.){2,}[A-Za-z_$][\w$]*"""),
    Regex("""\b[A-Z][A-Za-z0-9]*(?:Exception|Error|Throwable)\b"""),
    Regex("""(?i)\b(?:java|javax|kotlin|kotlinx|okhttp\d?|retrofit|sqlite|ktor)\b"""),
    //  ردِ پشته
    Regex("""(?m)^\s*at\s+[\w$.<>]+\("""),
    Regex("""(?i)\bstack\s?trace\b|\bcaused by\b"""),
    //  کدِ فنی: HTTP 500، status=404، (503)، E_TIMEOUT، ECONNREFUSED، errno
    Regex("""(?i)(?:\b(?:status|code|errno|http|err|error)|خطای|خطا|کد|وضعیت)\s*[:=#]?\s*\d{3,}\b"""),
    Regex("""\(\s*\d{3}\s*\)"""),
    Regex("""\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b"""),
    Regex("""\bE(?:CONN[A-Z]+|NOTFOUND|TIMEDOUT|ACCES|PIPE|HOSTUNREACH|NETUNREACH|ADDRINUSE)\b"""),
    Regex("""(?i)\berrno\b|\bnull ?pointer\b|\bsegfault\b"""),
    //  JSON یا HTML ِ خام
    Regex("""\{\s*"|"\s*:\s*["{\[\d]"""),
    Regex("""(?i)</?[a-z][a-z0-9]*(?:\s[^>]*)?>"""),
  )

  private val offlineWords = Regex(
    """(?i)اینترنت|انترنت|اتصال|آفلاین|شبکه|وصل نیست|انټرنېټ|اړیکه|internet|offline|connection|network|connect|unknownhost|""" +
      """socket|timed? ?out|timeout|unreachable|enotfound|econnrefused|econnreset|no route""",
  )

  private val serverWords = Regex("""(?i)سرور|server|service|\b5\d{2}\b|\bbad gateway\b|\bunavailable\b""")

  /** نخستین جای نشانهٔ فنی، یا null */
  private fun firstTechnical(s: String): Int? =
    technical.mapNotNull { r -> r.find(s)?.range?.first }.minOrNull()

  private val trailing = charArrayOf(':', '：', '—', '–', '-', '(', '[', ',', '،', '؛', ';', '.', '…', ' ', '\n', '\t', '|', '/')

  private fun words(s: String): Int = s.split(Regex("""\s+""")).count { w -> w.any { it.isLetter() } }

  /** آیا متن تکهٔ فنی دارد؟ */
  fun isTechnical(raw: String?): Boolean = raw != null && firstTechnical(raw) != null

  /**
   *  متنی که می‌شود نشان داد: ساده ⇒ همان؛ فنی ⇒ تکهٔ سادهٔ پیش از آن (دست‌کم دو واژه)؛ وگرنه null.
   *  خالی یا null ⇒ null.
   */
  fun text(raw: String?): String? {
    val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val cut = firstTechnical(s) ?: return s
    val head = s.substring(0, cut).trimEnd(*trailing).trim()
    return head.takeIf { words(it) >= 2 && firstTechnical(it) == null }
  }

  /** گونهٔ خطا از متنِ خام (پیش از پاک کردن، تا نامِ استثنای شبکه هم شمرده شود) */
  fun kindOf(vararg raw: String?): Kind {
    val all = raw.filterNotNull().joinToString(" ")
    return when {
      offlineWords.containsMatchIn(all) -> Kind.OFFLINE
      serverWords.containsMatchIn(all) -> Kind.SERVER
      else -> Kind.OTHER
    }
  }
}
