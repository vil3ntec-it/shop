package ir.vil3ntec.tohid.core.server

/**
 *  نشانیِ یک سرور — مرتب کردن، شناختنِ نوعِ میزبان و قاعدهٔ پذیرفتن (پرچمِ `app.multi_server`).
 *
 *  ── چرا نشانیِ دستی برداشته شده بود و حالا با چه قاعده‌ای برمی‌گردد ─────────────
 *  `docs/RULES-fa.md` («یک سرور، یک حساب») نشانی را قفل کرده بود: کاربر نباید بتواند دفترِ دکان را
 *  به سرورِ دیگری بفرستد، و شبکهٔ بی‌رمز (`http`) توکنِ ورود را به هر کسِ روی همان وای‌فای می‌داد
 *  (`network_security.xml`). صاحب محصول حالا «هر سرور» را خواسته (۱۴۰۵/۰۸/۰۸)؛ این فایل همان دو
 *  دلیل را نگه می‌دارد:
 *
 *  • **فقط `https`.** `http` فقط در نسخهٔ آزمایشی، و روی همین کامپیوتر (`localhost`) که از شبکه
 *    بیرون نمی‌رود. سرورِ شبکهٔ محلی هم `https` است — با گواهیِ خودامضای **سنجاق‌شده**
 *    (اثرِ انگشتِ گواهی در پروفایل)، چون سنجاقِ شناسهٔ سرور جلوی شنودِ رمزنشده را نمی‌گیرد.
 *  • **میزبانِ شبکهٔ محلی** (۱۰/۸، ۱۷۲.۱۶/۱۲، ۱۹۲.۱۶۸/۱۶، ۱۶۹.۲۵۴/۱۶، fc00::/7، fe80::/10، `.local`،
 *    `.lan`، `.home.arpa`، `.internal`، نامِ تک‌بخشی) فقط با کلیدِ «شبکهٔ محلی» پذیرفته است.
 *  • **نامِ کاربری در نشانی** (`https://api.vill3n.top@evil.example`) همیشه رد می‌شود — ترفندِ
 *    فریبِ کهنه‌ای است که نشانیِ آشنا را جلوی چشم می‌گذارد و میزبانِ دیگری را صدا می‌زند.
 *  • پرس‌وجو (`?`) و `#` در نشانیِ ریشه جایی ندارند.
 */
object ServerAddress {

  /** نوعِ میزبان */
  enum class HostKind { PUBLIC_NAME, PUBLIC_IP, LAN, LOOPBACK }

  /** نشانیِ خرد‌شده */
  data class Parts(
    val scheme: String,
    val host: String,
    val port: Int?,
    val path: String,
    val kind: HostKind,
  ) {
    val https: Boolean get() = scheme == "https"
    /** همان نشانیِ مرتب، برای مقایسه و ذخیره */
    val url: String
      get() {
        val h = if (host.contains(':')) "[$host]" else host
        return "$scheme://$h" + (port?.let { ":$it" } ?: "") + path
      }
  }

  /** چرا نشانی پذیرفته نشد — متنِ فارسی برای خودِ کاربر */
  enum class Rejection(val message: String) {
    MISSING("نشانیِ سرور را بنویسید"),
    MALFORMED("نشانیِ سرور درست نیست"),
    BAD_SCHEME("نشانی باید با https:// شروع شود"),
    INSECURE("این برنامه فقط با https:// وصل می‌شود؛ http رمز ندارد و هر کس روی همان شبکه توکنِ ورود را می‌بیند"),
    USERINFO("نشانیِ دارای «@» پذیرفته نیست — این ترفند، سرورِ دیگری را پشتِ نامِ آشنا پنهان می‌کند"),
    QUERY("نشانیِ سرور «?» یا «#» ندارد"),
    LAN_OFF("این نشانیِ شبکهٔ محلی است؛ اول «شبکهٔ محلی» را روشن کنید"),
  }

  /** نتیجهٔ سنجش */
  sealed class Verdict {
    data class Ok(val parts: Parts) : Verdict()
    data class Rejected(val reason: Rejection) : Verdict()
  }

  /**
   *  نشانی را به یک شکل می‌رساند — همان قاعدهٔ `ApiConfig.normalize`: فاصله و `/` آخر و
   *  `/api/v1` یا `/api` ِ چسبیده بریده، و بی طرح ⇒ `https://` (پیش‌فرضِ ناامن، پیش‌فرضِ غلط است).
   */
  fun normalize(raw: String?): String {
    var s = (raw ?: "").trim()
    if (s.isEmpty()) return ""
    if (!s.contains("://")) s = "https://$s"
    s = s.trimEnd('/')
    while (true) {
      val lower = s.lowercase()
      val cut = when {
        lower.endsWith("/api/v1") -> 7
        lower.endsWith("/api") -> 4
        else -> 0
      }
      if (cut == 0) break
      s = s.dropLast(cut).trimEnd('/')
    }
    return s
  }

  /** خرد کردنِ نشانی — `null` اگر اصلاً نشانی نیست */
  fun parse(raw: String?): Parts? = (split(normalize(raw)) as? Split.Good)?.parts

  /**
   *  قاعدهٔ پذیرفتن.
   *
   *  @param lanAllowed کلیدِ «شبکهٔ محلی» ِ همان پروفایل
   *  @param devBuild نسخهٔ آزمایشی (`BuildConfig.DEBUG`) — فقط آن‌جا `http` به هر میزبانی
   */
  fun check(raw: String?, lanAllowed: Boolean, devBuild: Boolean = false): Verdict {
    val s = normalize(raw)
    if (s.isEmpty()) return Verdict.Rejected(Rejection.MISSING)
    val parts = when (val sp = split(s)) {
      is Split.Bad -> return Verdict.Rejected(sp.reason)
      is Split.Good -> sp.parts
    }
    val local = parts.kind == HostKind.LAN || parts.kind == HostKind.LOOPBACK
    if (local && !lanAllowed) return Verdict.Rejected(Rejection.LAN_OFF)
    //  `http` فقط روی همین کامپیوتر (از شبکه بیرون نمی‌رود) یا در نسخهٔ آزمایشی
    if (!parts.https && !devBuild && parts.kind != HostKind.LOOPBACK) return Verdict.Rejected(Rejection.INSECURE)
    return Verdict.Ok(parts)
  }

  /** آیا این نشانی در شبکهٔ محلی (یا همین کامپیوتر) است */
  fun isLocal(raw: String?): Boolean =
    parse(raw)?.kind.let { it == HostKind.LAN || it == HostKind.LOOPBACK }

  /* ------------------------------ درونی ------------------------------ */

  private sealed class Split {
    data class Good(val parts: Parts) : Split()
    data class Bad(val reason: Rejection) : Split()
  }

  private fun split(s: String): Split {
    val schemeEnd = s.indexOf("://")
    if (schemeEnd <= 0) return Split.Bad(Rejection.MALFORMED)
    val scheme = s.substring(0, schemeEnd).lowercase()
    if (scheme != "https" && scheme != "http") return Split.Bad(Rejection.BAD_SCHEME)
    val rest = s.substring(schemeEnd + 3)
    if (rest.contains('?') || rest.contains('#')) return Split.Bad(Rejection.QUERY)
    val slash = rest.indexOf('/')
    val authority = if (slash < 0) rest else rest.substring(0, slash)
    val path = if (slash < 0) "" else rest.substring(slash).trimEnd('/')
    if (authority.contains('@')) return Split.Bad(Rejection.USERINFO)
    if (authority.isEmpty() || authority.any { it.isWhitespace() || it == '\\' }) return Split.Bad(Rejection.MALFORMED)
    if (path.any { it.isWhitespace() || it == '\\' } || path.contains("..")) return Split.Bad(Rejection.MALFORMED)

    val host: String
    val portText: String?
    if (authority.startsWith("[")) {
      val close = authority.indexOf(']')
      if (close < 0) return Split.Bad(Rejection.MALFORMED)
      host = authority.substring(1, close).lowercase()
      val after = authority.substring(close + 1)
      portText = when {
        after.isEmpty() -> null
        after.startsWith(":") -> after.substring(1)
        else -> return Split.Bad(Rejection.MALFORMED)
      }
      if (!isIpv6(host)) return Split.Bad(Rejection.MALFORMED)
    } else {
      if (authority.count { it == ':' } > 1) return Split.Bad(Rejection.MALFORMED)
      host = authority.substringBefore(':').lowercase().trimEnd('.')
      portText = if (authority.contains(':')) authority.substringAfter(':') else null
      if (!validName(host)) return Split.Bad(Rejection.MALFORMED)
    }
    val port = when (portText) {
      null -> null
      else -> portText.takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isDigit) }
        ?.toInt()?.takeIf { it in 1..65535 } ?: return Split.Bad(Rejection.MALFORMED)
    }
    return Split.Good(Parts(scheme, host, port, path, kindOf(host)))
  }

  private fun validName(host: String): Boolean {
    if (host.isEmpty() || host.length > 253) return false
    if (ipv4(host) != null) return true
    return host.split('.').all { label ->
      label.isNotEmpty() && label.length <= 63 && !label.startsWith('-') && !label.endsWith('-') &&
        label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
    }
  }

  private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal")

  /** نوعِ میزبان */
  fun kindOf(host: String): HostKind {
    val h = host.lowercase().trimEnd('.')
    if (h == "localhost" || h.endsWith(".localhost")) return HostKind.LOOPBACK
    ipv4(h)?.let { o ->
      return when {
        o[0] == 127 -> HostKind.LOOPBACK
        o[0] == 10 -> HostKind.LAN
        o[0] == 172 && o[1] in 16..31 -> HostKind.LAN
        o[0] == 192 && o[1] == 168 -> HostKind.LAN
        o[0] == 169 && o[1] == 254 -> HostKind.LAN
        else -> HostKind.PUBLIC_IP
      }
    }
    if (h.contains(':')) {
      if (h == "::1") return HostKind.LOOPBACK
      val first = h.substringBefore(':').ifEmpty { "0" }.padStart(4, '0')
      return when {
        first.startsWith("fc") || first.startsWith("fd") -> HostKind.LAN       // fc00::/7
        first.startsWith("fe8") || first.startsWith("fe9") ||
          first.startsWith("fea") || first.startsWith("feb") -> HostKind.LAN   // fe80::/10
        else -> HostKind.PUBLIC_IP
      }
    }
    if (!h.contains('.')) return HostKind.LAN                                   // نامِ تک‌بخشیِ شبکهٔ درونی
    if (LOCAL_SUFFIXES.any { h.endsWith(it) }) return HostKind.LAN
    return HostKind.PUBLIC_NAME
  }

  /** چهار بخشِ IPv4 — یا `null` */
  private fun ipv4(h: String): IntArray? {
    val parts = h.split('.')
    if (parts.size != 4) return null
    val out = IntArray(4)
    for ((i, p) in parts.withIndex()) {
      if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit)) return null
      val n = p.toInt()
      if (n > 255) return null
      out[i] = n
    }
    return out
  }

  private fun isIpv6(h: String): Boolean =
    h.contains(':') && h.length <= 45 && h.all { it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' }
}
