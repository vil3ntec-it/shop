package ir.vil3ntec.tohid.core.server

/**
 *  پروفایلِ یک سرور روی همین دستگاه (پرچمِ `app.multi_server`، پیش‌فرض خاموش).
 *
 *  • `urls` — نخستین، نشانیِ اصلی؛ بقیه جایگزین‌های **همان** سرور (مثلاً نشانیِ شبکهٔ دکان و نشانیِ
 *    اینترنتی). جابه‌جایی میانِ آن‌ها فقط وقتی است که همان `serverId` جواب بدهد (`ServerFailover`).
 *  • `serverId` — شناسهٔ سنجاق‌شده (`ServerIdentity`). پروفایلِ پیش‌فرض آن را در نخستین وصل خودش
 *    می‌گیرد (نشانی‌اش در خودِ نسخه نشسته)؛ پروفایلِ دیگر فقط با تأییدِ صاحبِ دکان.
 *  • `certPins` — اثرِ انگشتِ SHA-256 ِ گواهیِ خودامضای هر نشانیِ شبکهٔ محلی (`https` با گواهیِ
 *    سنجاق‌شده به‌جای مرجعِ عمومی).
 *  • `lan` — کلیدِ «شبکهٔ محلی».
 */
data class ServerProfile(
  val id: String,
  val name: String,
  val urls: List<String>,
  val serverId: String? = null,
  val certPins: Map<String, String> = emptyMap(),
  val lan: Boolean = false,
  val lastInfo: ServerInfo? = null,
  val checkedAt: Long = 0,
  val addedAt: Long = 0,
) {
  val isDefault: Boolean get() = id == ServerProfiles.DEFAULT_ID
  val primary: String get() = urls.firstOrNull().orEmpty()

  /** اثرِ انگشتِ گواهیِ سنجاق‌شدهٔ این نشانی — یا `null` (اعتمادِ عادیِ سیستم) */
  fun certPinOf(url: String): String? = certPins[ServerAddress.normalize(url)]

  companion object {
    /** SHA-256 ِ گواهی: ۶۴ نویسهٔ هگزِ کوچک (دو‌نقطه و فاصله و حروفِ بزرگ پذیرفته و یکسان می‌شوند) */
    fun normalizeCertPin(raw: String?): String? {
      val hex = (raw ?: "").lowercase().filter { it != ':' && it != ' ' && it != '-' }
      return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }
  }
}

/**
 *  فهرستِ پروفایل‌ها و پروفایلِ فعال.
 *
 *  پروفایلِ پیش‌فرض (`default`) همیشه هست، نشانیِ اصلی‌اش همان نشانیِ زمانِ ساخت است و برداشتنی
 *  نیست. با پرچمِ خاموش یا فهرستِ خالی، برنامه دقیقاً همان است که امروز هست.
 */
data class ServerProfiles(
  val activeId: String,
  val profiles: List<ServerProfile>,
) {

  val active: ServerProfile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first { it.isDefault }
  val default: ServerProfile get() = profiles.first { it.isDefault }

  fun byId(id: String): ServerProfile? = profiles.firstOrNull { it.id == id }

  /** چرا تغییر پذیرفته نشد */
  enum class Problem(val message: String) {
    TOO_MANY("بیش از ${MAX_PROFILES} سرور نمی‌شود افزود"),
    DUPLICATE_ID("این پروفایل از پیش هست"),
    SAME_AS_DEFAULT("این همان سرورِ پیش‌فرضِ برنامه است؛ نشانی‌اش را به‌عنوانِ نشانیِ دومِ «سرورِ اصلی» بیفزایید"),
    SAME_SERVER("این سرور از پیش در فهرست هست؛ نشانیِ تازه را به همان بیفزایید"),
    NOT_FOUND("این سرور در فهرست نیست"),
    DEFAULT_FIXED("سرورِ پیش‌فرضِ برنامه برداشتنی نیست"),
    ACTIVE("سرورِ فعال را نمی‌شود برداشت؛ اول به سرورِ دیگری بروید"),
    NO_URL("نشانی ندارد"),
    PRIMARY_FIXED("نشانیِ اصلیِ سرورِ پیش‌فرض در خودِ برنامه نشسته و عوض نمی‌شود"),
  }

  sealed class Change {
    data class Ok(val profiles: ServerProfiles) : Change()
    data class Refused(val problem: Problem) : Change()
  }

  fun add(p: ServerProfile): Change {
    if (profiles.size >= MAX_PROFILES) return Change.Refused(Problem.TOO_MANY)
    if (p.isDefault || byId(p.id) != null) return Change.Refused(Problem.DUPLICATE_ID)
    if (p.urls.isEmpty()) return Change.Refused(Problem.NO_URL)
    if (p.serverId != null) {
      if (default.serverId == p.serverId) return Change.Refused(Problem.SAME_AS_DEFAULT)
      if (profiles.any { it.serverId == p.serverId }) return Change.Refused(Problem.SAME_SERVER)
    }
    return Change.Ok(copy(profiles = profiles + p.copy(urls = p.urls.map(ServerAddress::normalize).distinct())))
  }

  fun remove(id: String): Change {
    val p = byId(id) ?: return Change.Refused(Problem.NOT_FOUND)
    if (p.isDefault) return Change.Refused(Problem.DEFAULT_FIXED)
    if (active.id == id) return Change.Refused(Problem.ACTIVE)
    return Change.Ok(copy(profiles = profiles.filterNot { it.id == id }))
  }

  fun activate(id: String): Change {
    byId(id) ?: return Change.Refused(Problem.NOT_FOUND)
    return Change.Ok(copy(activeId = id))
  }

  /** پروفایل را با نسخهٔ تازه‌اش جایگزین می‌کند (سنجاق، آخرین آگاهی، کلیدِ شبکهٔ محلی…) */
  fun update(p: ServerProfile): Change {
    val old = byId(p.id) ?: return Change.Refused(Problem.NOT_FOUND)
    if (old.isDefault && p.primary != old.primary) return Change.Refused(Problem.PRIMARY_FIXED)
    if (p.urls.isEmpty()) return Change.Refused(Problem.NO_URL)
    if (p.serverId != null && profiles.any { it.id != p.id && it.serverId == p.serverId }) {
      return Change.Refused(if (p.isDefault || default.serverId == p.serverId) Problem.SAME_AS_DEFAULT else Problem.SAME_SERVER)
    }
    return Change.Ok(copy(profiles = profiles.map { if (it.id == p.id) p else it }))
  }

  /** نشانیِ جایگزینِ همان سرور */
  fun addAlternate(id: String, url: String, certPin: String? = null): Change {
    val p = byId(id) ?: return Change.Refused(Problem.NOT_FOUND)
    val u = ServerAddress.normalize(url)
    if (u.isEmpty()) return Change.Refused(Problem.NO_URL)
    val pins = ServerProfile.normalizeCertPin(certPin)?.let { p.certPins + (u to it) } ?: p.certPins
    return update(p.copy(urls = (p.urls + u).distinct().take(MAX_URLS), certPins = pins))
  }

  fun encode(): String = MiniJson.encode(
    mapOf(
      "v" to 1,
      "active" to activeId,
      "profiles" to profiles.map { p ->
        mapOf(
          "id" to p.id, "name" to p.name, "urls" to p.urls, "serverId" to p.serverId,
          "certPins" to p.certPins, "lan" to p.lan, "checkedAt" to p.checkedAt, "addedAt" to p.addedAt,
          "info" to p.lastInfo?.let { i ->
            mapOf(
              "serverId" to i.serverId, "name" to i.name, "version" to i.version,
              "wireMin" to i.wireMin, "wireMax" to i.wireMax, "features" to i.features.toList(),
              "publicUrl" to i.publicUrl, "legacy" to i.legacy,
            )
          },
        )
      },
    ),
  )

  companion object {
    const val DEFAULT_ID: String = "default"
    const val MAX_PROFILES: Int = 20
    const val MAX_URLS: Int = 4

    /** پرچمِ دستگاهیِ همهٔ این بخش (پیش‌فرض خاموش) */
    const val FLAG: String = "app.multi_server"

    /** فقط سرورِ زمانِ ساخت — همان رفتارِ امروز */
    fun initial(buildUrl: String): ServerProfiles = ServerProfiles(
      activeId = DEFAULT_ID,
      profiles = listOf(defaultProfile(buildUrl)),
    )

    private fun defaultProfile(buildUrl: String) =
      ServerProfile(id = DEFAULT_ID, name = "سرورِ اصلی", urls = listOf(ServerAddress.normalize(buildUrl)))

    /**
     *  خواندنِ فهرستِ ذخیره‌شده. هر خطا ⇒ همان `initial` (هرگز سرورِ ناشناسی از دادهٔ خراب).
     *
     *  اگر نشانیِ زمانِ ساخت در نسخهٔ تازه عوض شده باشد، نشانیِ اصلیِ پیش‌فرض همان تازه می‌شود و
     *  سنجاقش پاک: نسخهٔ تازه با دامنهٔ تازه یعنی صاحب محصول سرور را جابه‌جا کرده است.
     */
    fun decode(text: String?, buildUrl: String): ServerProfiles {
      val base = ServerAddress.normalize(buildUrl)
      val o = MiniJson.obj(text) ?: return initial(buildUrl)
      val list = (o["profiles"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::profileOf) }
        .distinctBy { it.id }.take(MAX_PROFILES)
      val storedDefault = list.firstOrNull { it.isDefault }
      val def = when {
        storedDefault == null -> defaultProfile(buildUrl)
        storedDefault.primary != base ->
          storedDefault.copy(urls = listOf(base) + storedDefault.urls.drop(1).filter { it != base }, serverId = null, lastInfo = null)
        else -> storedDefault
      }
      val others = list.filterNot { it.isDefault }
        .filter { p -> p.serverId == null || p.serverId != def.serverId }
      val all = listOf(def) + others
      val active = (o["active"] as? String)?.takeIf { id -> all.any { it.id == id } } ?: DEFAULT_ID
      return ServerProfiles(active, all)
    }

    private fun profileOf(m: Map<*, *>): ServerProfile? {
      val id = (m["id"] as? String)?.takeIf { it.isNotBlank() && it.length <= 40 } ?: return null
      val urls = (m["urls"] as? List<*>).orEmpty().filterIsInstance<String>()
        .mapNotNull { ServerAddress.parse(it)?.url }.distinct().take(MAX_URLS)
      if (urls.isEmpty()) return null
      val pins = (m["certPins"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
        val u = (k as? String)?.let { ServerAddress.parse(it)?.url } ?: return@mapNotNull null
        val pin = ServerProfile.normalizeCertPin(v as? String) ?: return@mapNotNull null
        u to pin
      }.toMap()
      val info = (m["info"] as? Map<*, *>)?.let { i ->
        ServerInfo(
          serverId = (i["serverId"] as? String)?.takeIf { ServerInfo.validServerId(it) },
          name = ServerInfo.cleanName(i["name"] as? String),
          version = ServerInfo.cleanText(i["version"] as? String, 32),
          wireMin = ((i["wireMin"] as? Long) ?: 1L).toInt(),
          wireMax = ((i["wireMax"] as? Long) ?: 1L).toInt(),
          features = (i["features"] as? List<*>).orEmpty().filterIsInstance<String>().toSet(),
          publicUrl = (i["publicUrl"] as? String).orEmpty(),
          legacy = i["legacy"] == true,
        )
      }
      return ServerProfile(
        id = id,
        name = ServerInfo.cleanName(m["name"] as? String).ifEmpty { "سرور" },
        urls = urls,
        serverId = (m["serverId"] as? String)?.takeIf { ServerInfo.validServerId(it) },
        certPins = pins,
        lan = m["lan"] == true,
        lastInfo = info,
        checkedAt = (m["checkedAt"] as? Long) ?: 0L,
        addedAt = (m["addedAt"] as? Long) ?: 0L,
      )
    }
  }
}
