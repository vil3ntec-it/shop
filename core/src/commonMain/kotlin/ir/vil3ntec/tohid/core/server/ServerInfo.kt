package ir.vil3ntec.tohid.core.server

import ir.vil3ntec.tohid.core.platform.sha256

/**
 *  هویت و توانِ یک سرور — پاسخِ `GET /api/v1/config/server` (فقط خواندنی، بی راز).
 *
 *  ── قرارِ سیم ────────────────────────────────────────────────────────────────
 *  `{ kind: "tohid-server", serverId: "srv_…", name, version, wire: {min, max}, api: ["v1"],
 *     features: [...], publicUrl }`
 *
 *  • `serverId` یک بار در نخستین راه‌اندازیِ سرور ساخته و در دفترِ همان سرور (`app_config`) نگه
 *    داشته می‌شود؛ پس با جابه‌جا شدنِ سرور به کامپیوترِ دیگر (پشتیبان/بازگردانی) همان می‌ماند، و
 *    سرورِ دیگری پشتِ همان نشانی شناسهٔ دیگری دارد.
 *  • `wire` بازهٔ نسخه‌های قراردادِ برنامه است که سرور می‌پذیرد. برنامه `APP_WIRE` خودش را دارد.
 *  • سرورِ کهنه این مسیر را ندارد ⇒ برنامه `/api/health` را می‌خواند (`service: "vill3n-auth"`) و
 *    سرور را «سازگارِ قدیمی» (`legacy`) می‌شمارد: بی شناسه، با همان قابلیت‌های امروز.
 */
data class ServerInfo(
  /** شناسهٔ پایدارِ سرور؛ سرورِ قدیمی ندارد */
  val serverId: String?,
  val name: String,
  val version: String,
  val wireMin: Int,
  val wireMax: Int,
  val features: Set<String>,
  val publicUrl: String,
  /** سرورِ پیش از این مسیر — فقط `/api/health` جواب داد */
  val legacy: Boolean,
) {

  fun supports(feature: String): Boolean = feature in features

  /** نمایشِ کوتاهِ شناسه برای مقایسه با چشم */
  val fingerprint: String get() = serverId?.let { ServerIdentity.fingerprint(it) }.orEmpty()

  companion object {
    /** قراردادِ همین نسخهٔ برنامه */
    const val APP_WIRE: Int = 1

    const val KIND: String = "tohid-server"

    /**
     *  قابلیت‌هایی که هر سرورِ قدیمی (پیش از `config/server`) داشت — همان‌ها که برنامهٔ امروز
     *  بی پرسیدن به کار می‌برد. قابلیتِ تازه فقط وقتی به کار می‌رود که سرور صریحاً بگوید.
     */
    val LEGACY_FEATURES: Set<String> = setOf("auth.password", "sync.v1", "shop", "health")

    private val ID = Regex("^srv_[a-z0-9]{16,40}$")

    fun validServerId(id: String?): Boolean = id != null && ID.matches(id)

    /** پاسخِ `config/server` — `null` اگر پاسخِ سرورِ این خانواده نیست */
    fun parse(json: String?): ServerInfo? {
      val o = MiniJson.obj(json) ?: return null
      if (o["kind"] != KIND) return null
      val id = o["serverId"] as? String
      if (!validServerId(id)) return null
      val wire = o["wire"] as? Map<*, *> ?: return null
      val min = (wire["min"] as? Long)?.toInt() ?: return null
      val max = (wire["max"] as? Long)?.toInt() ?: return null
      if (min < 1 || max < min) return null
      val features = (o["features"] as? List<*>).orEmpty()
        .filterIsInstance<String>().filter { it.length in 1..64 }.take(200).toSet()
      return ServerInfo(
        serverId = id,
        name = cleanName(o["name"] as? String).ifEmpty { "سرور" },
        version = cleanText(o["version"] as? String, 32),
        wireMin = min,
        wireMax = max,
        features = features,
        publicUrl = (o["publicUrl"] as? String)?.let { ServerAddress.parse(it)?.url }.orEmpty(),
        legacy = false,
      )
    }

    /**
     *  سرورِ کهنه: پاسخِ `/api/health`. فقط سرورِ همین خانواده (`service: "vill3n-auth"`) پذیرفته
     *  است — نه هر سرورِ دیگری که «ok» می‌گوید.
     */
    fun fromHealth(json: String?): ServerInfo? {
      val o = MiniJson.obj(json) ?: return null
      if (o["service"] != "vill3n-auth" || o["server"] != "online") return null
      return ServerInfo(
        serverId = null,
        name = "سرور",
        version = cleanText(o["version"] as? String, 32),
        wireMin = 1,
        wireMax = 1,
        features = LEGACY_FEATURES,
        publicUrl = "",
        legacy = true,
      )
    }

    /**
     *  نامی که کاربر می‌بیند — بی نویسه‌های کنترلی و **بی نویسه‌های جهتِ متن** (U+202A–U+202E،
     *  U+2066–U+2069): با آن‌ها می‌شود نامی ساخت که روی صفحه چیزِ دیگری خوانده شود. نیم‌فاصله
     *  (U+200C) می‌ماند؛ فارسی بی آن غلط نوشته می‌شود.
     */
    fun cleanName(raw: String?): String = cleanText(raw, 60)

    internal fun cleanText(raw: String?, max: Int): String =
      (raw ?: "").filter { c ->
        val code = c.code
        code >= 0x20 && code != 0x7F && code !in 0x202A..0x202E && code !in 0x2066..0x2069 &&
          code != 0x200E && code != 0x200F && code != 0x061C && code != 0xFEFF
      }.trim().replace(Regex("\\s+"), " ").take(max).trim()
  }
}

/** آیا قراردادِ برنامه و سرور به هم می‌خورند */
object ServerCompat {

  enum class Result(val message: String?) {
    OK(null),
    /** سرور قراردادِ تازه‌تری می‌خواهد ⇒ برنامه به‌روز شود */
    APP_TOO_OLD("این سرور نسخهٔ تازه‌ترِ برنامه را می‌خواهد؛ اول برنامه را به‌روز کنید"),
    /** سرور از قراردادِ این برنامه کهنه‌تر است ⇒ سرور به‌روز شود */
    SERVER_TOO_OLD("این سرور قدیمی است و با این نسخهٔ برنامه کار نمی‌کند؛ سرور را به‌روز کنید"),
  }

  fun check(info: ServerInfo, appWire: Int = ServerInfo.APP_WIRE): Result = when {
    appWire < info.wireMin -> Result.APP_TOO_OLD
    appWire > info.wireMax -> Result.SERVER_TOO_OLD
    else -> Result.OK
  }
}

/**
 *  سنجاقِ هویت — «همان سرور» یعنی همان `serverId`، نه همان نشانی.
 *
 *  نخستین وصل (`FIRST_USE`) شناسه و اثرِ انگشتش را نشان می‌دهد و فقط با تأییدِ صاحبِ دکان سنجاق
 *  می‌شود (اعتماد در نخستین استفاده). پس از آن هر شناسهٔ دیگری پشتِ همان نشانی — یا سروری که
 *  ناگهان دیگر شناسه نمی‌دهد (بازگشت به «قدیمی» راهِ دور زدنِ سنجاق است) — **بسته** می‌شود.
 */
object ServerIdentity {

  enum class Pin {
    /** هنوز سنجاق نشده و سرور شناسه داد ⇒ نمایش و تأیید */
    FIRST_USE,
    /** همان سرور */
    MATCH,
    /** سرورِ دیگری پشتِ همین نشانی، یا سرورِ سنجاق‌شده‌ای که شناسه نمی‌دهد ⇒ بسته */
    MISMATCH,
    /** سرورِ قدیمی و هنوز سنجاق‌نشده ⇒ با هشدار پذیرفته (جابه‌جا شدنش را نمی‌شود فهمید) */
    LEGACY_UNPINNED,
  }

  fun check(pinned: String?, observed: String?): Pin = when {
    pinned.isNullOrEmpty() && observed.isNullOrEmpty() -> Pin.LEGACY_UNPINNED
    pinned.isNullOrEmpty() -> Pin.FIRST_USE
    observed.isNullOrEmpty() -> Pin.MISMATCH
    pinned == observed -> Pin.MATCH
    else -> Pin.MISMATCH
  }

  /** پیامِ بستن — همان که کاربر می‌بیند */
  const val MISMATCH_MESSAGE: String =
    "سرورِ پشتِ این نشانی عوض شده است. برای حفظِ دادهٔ دکان، برنامه به آن وصل نمی‌شود. " +
      "اگر خودتان سرور را عوض کرده‌اید، این سرور را از «سرورها» بردارید و دوباره بیفزایید."

  /**
   *  اثرِ انگشتِ نمایشی: ۸ بایتِ اولِ SHA-256 ِ `tohid-server:<serverId>`، چهار گروهِ چهارتایی.
   *  همان چیزی که پنلِ سرور هم نشان می‌دهد؛ صاحبِ دکان دو را با چشم مقایسه می‌کند.
   */
  fun fingerprint(serverId: String): String {
    val digest = sha256(("tohid-server:$serverId").encodeToByteArray())
    val hex = digest.take(8).joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return hex.uppercase().chunked(4).joinToString("-")
  }
}
