package ir.vil3ntec.tohid.core.config

/**
 *  پیکربندیِ اتصال — خالص، بدونِ اندروید.
 *
 *  اینجا هیچ‌چیزِ اندرویدی نیست تا بشود بدونِ گوشی و شبیه‌ساز سنجیدش.
 *  لایهٔ اندرویدیِ آن `AppConfig` است که مقدارها را از `BuildConfig` و
 *  حافظهٔ دستگاه می‌گیرد و همین تابع‌ها را صدا می‌زند.
 *
 *  قاعده‌ای که این فایل نگه می‌دارد:
 *
 *    برنامه فقط یک «دامنه» می‌شناسد، نه یک «سرور».
 *
 *  یعنی نشانیِ IP هیچ‌وقت داخلِ برنامه نمی‌نشیند. اگر سرور فردا از
 *  رایانهٔ خانه به یک VPS برود، تا وقتی همان دامنه به جای تازه اشاره
 *  کند، برنامه اصلاً نمی‌فهمد چیزی عوض شده و نسخهٔ تازه‌ای لازم نیست.
 */
object ApiConfig {

  /** نسخهٔ API — یک بار اینجا، نه پخش در مسیرها */
  const val API_VERSION: String = "v1"

  /** پیشوندِ همهٔ مسیرها */
  const val API_PREFIX: String = "/api/$API_VERSION"

  /* ------------------------------ مهلت‌ها ------------------------------ */

  /** مهلتِ برقراریِ اتصال — کوتاه، چون «سرور نیست» باید زود معلوم شود */
  const val CONNECT_TIMEOUT_MS: Int = 15_000

  /** مهلتِ خواندنِ پاسخ — بلندتر، چون یک همگام‌سازیِ بزرگ وقت می‌برد */
  const val READ_TIMEOUT_MS: Int = 30_000

  /* ------------------------------ تلاشِ دوباره ------------------------------ */

  /**
   *  حداکثر تلاشِ دوباره.
   *
   *  فقط برای درخواست‌هایی که تکرارشان بی‌خطر است (خواندن) و فقط برای
   *  خطاهایی که ممکن است گذرا باشند. «فرستادنِ فروش» هیچ‌وقت دوباره
   *  فرستاده نمی‌شود — دو بار ثبت شدنِ یک فاکتور بدتر از یک بار نرسیدنش
   *  است.
   */
  const val MAX_RETRIES: Int = 2

  /** فاصلهٔ اولین تلاشِ دوباره؛ هر بار دو برابر می‌شود */
  const val RETRY_BACKOFF_MS: Long = 800

  /* ------------------------------ نشانی ------------------------------ */

  /**
   *  نشانی را مرتب می‌کند.
   *
   *  کاربر (یا سازندهٔ نسخه) ممکن است نشانی را با `/` آخر، با فاصله، یا
   *  حتی با `/api/v1` چسبیده بنویسد — چون همان را در مرورگر دیده. هر سه
   *  را می‌گیریم و به یک شکلِ واحد می‌رسانیم، وگرنه مسیرها `/api/v1/api/v1/…`
   *  می‌شدند و هر درخواست ۴۰۴ می‌گرفت.
   *
   *  اگر طرح (`https://`) نداشته باشد، `https://` گذاشته می‌شود — نه
   *  `http://`. پیش‌فرضِ ناامن، پیش‌فرضِ غلط است.
   */
  fun normalize(raw: String?): String {
    var s = (raw ?: "").trim()
    if (s.isEmpty()) return ""

    if (!s.contains("://")) s = "https://$s"
    s = s.trimEnd('/')

    //  اگر پیشوندِ API را هم چسبانده، برش می‌داریم: نشانی باید «ریشه»
    //  باشد و پیشوند را خودِ برنامه می‌گذارد
    while (true) {
      val lower = s.lowercase()
      val cut = when {
        lower.endsWith(API_PREFIX) -> API_PREFIX.length
        lower.endsWith("/api") -> 4
        else -> 0
      }
      if (cut == 0) break
      s = s.dropLast(cut).trimEnd('/')
    }
    return s
  }

  /** چرا این نشانی پذیرفته نشد — یا `null` اگر درست است */
  fun reject(raw: String?, allowInsecure: Boolean): Rejection? {
    val s = normalize(raw)
    if (s.isEmpty()) return Rejection.MISSING

    val lower = s.lowercase()
    val https = lower.startsWith("https://")
    val http = lower.startsWith("http://")
    if (!https && !http) return Rejection.BAD_SCHEME
    if (http && !allowInsecure) return Rejection.INSECURE

    val host = hostOf(s)
    if (host.isEmpty()) return Rejection.BAD_HOST
    //  IP یعنی برنامه به «جای فیزیکیِ» سرور بسته شده. جابه‌جا شدنِ سرور
    //  آن‌وقت نسخهٔ تازه لازم دارد — دقیقاً چیزی که نباید بشود.
    if (!allowInsecure && isIpLiteral(host)) return Rejection.IP_ADDRESS
    return null
  }

  fun isValid(raw: String?, allowInsecure: Boolean): Boolean = reject(raw, allowInsecure) == null

  /** دلیلِ رد شدنِ نشانی، با متنی که می‌شود به کاربر نشان داد */
  enum class Rejection(val message: String) {
    MISSING("نشانی سرور تنظیم نشده است"),
    BAD_SCHEME("نشانی سرور باید با https:// شروع شود"),
    INSECURE("این نسخه فقط با https:// کار می‌کند"),
    BAD_HOST("نشانی سرور درست نیست"),
    IP_ADDRESS("به‌جای نشانی عددی، دامنه بدهید (مثل api.example.com)"),
  }

  /** نام میزبان از نشانی — بدون طرح، بدون پورت، بدون مسیر */
  fun hostOf(raw: String?): String {
    val s = normalize(raw)
    val afterScheme = s.substringAfter("://", s)
    val hostPort = afterScheme.substringBefore('/').substringBefore('?')
    //  نامِ کاربری در نشانی (`user@host`) کنار گذاشته می‌شود
    val bare = hostPort.substringAfterLast('@')
    return if (bare.startsWith("[")) bare.substringAfter('[').substringBefore(']')
    else bare.substringBefore(':')
  }

  /** آیا این میزبان یک نشانی عددی است (IPv4 یا IPv6) */
  fun isIpLiteral(host: String): Boolean {
    if (host.isEmpty()) return false
    if (host.contains(':')) return true                       // IPv6
    val parts = host.split('.')
    if (parts.size != 4) return false
    return parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() <= 255 }
  }

  /**
   *  نشانیِ کاملِ یک مسیر.
   *
   *  مسیرها همیشه بدونِ پیشوند نوشته می‌شوند (`/auth/login`) و پیشوند
   *  همین‌جا یک بار چسبانده می‌شود. روزی که API به `v2` برود، فقط
   *  `API_VERSION` عوض می‌شود — نه بیست‌وچند رشته در بیست‌وچند جا.
   */
  fun urlOf(base: String, path: String): String {
    val root = normalize(base)
    val clean = if (path.startsWith("/")) path else "/$path"
    return root + API_PREFIX + clean
  }
}
