package ir.vil3ntec.tohid.core.server

import ir.vil3ntec.tohid.core.platform.sha256

/**
 *  کدام نشانیِ پروفایل همین حالا — «شبکهٔ دکان اگر در دسترس است، وگرنه اینترنت».
 *
 *  • ترتیب: نشانی‌های شبکهٔ محلی پیش از اینترنتی، هر دسته به ترتیبِ فهرست؛ آخرین نشانیِ سالم در
 *    دستهٔ خودش جلو می‌افتد. مهلتِ محلی کوتاه است (دکانی که در شبکه‌اش نیست نباید منتظر بماند).
 *  • ⛔ **هرگز میانِ دو سرور**: نشانی فقط وقتی برگزیده می‌شود که `serverId` ِ همان پروفایل را بدهد.
 *    پروفایلِ سنجاق‌نشده (سرورِ قدیمی) فقط نشانیِ اصلی را دارد — جایگزینش را نمی‌شود سنجید.
 */
object ServerFailover {

  const val LAN_TIMEOUT_MS: Int = 1_500
  const val PUBLIC_TIMEOUT_MS: Int = 4_000
  /** هر چند وقت نشانیِ برگزیده دوباره سنجیده شود */
  const val RECHECK_MS: Long = 10 * 60_000L

  fun timeoutOf(url: String): Int = if (ServerAddress.isLocal(url)) LAN_TIMEOUT_MS else PUBLIC_TIMEOUT_MS

  fun order(urls: List<String>, lastGood: String? = null): List<String> {
    val clean = urls.map(ServerAddress::normalize).filter { it.isNotEmpty() }.distinct()
    val good = lastGood?.let(ServerAddress::normalize)
    fun inClass(list: List<String>) = if (good != null && good in list) listOf(good) + (list - good) else list
    val (local, public) = clean.partition { ServerAddress.isLocal(it) }
    return inClass(local) + inClass(public)
  }

  /** پاسخِ یک نشانی: `info == null` یعنی نرسید */
  data class Probe(val url: String, val info: ServerInfo?)

  enum class Judgement { ACCEPT, UNREACHABLE, MISMATCH, UNVERIFIABLE }

  /** یک نشانی به کار می‌آید یا نه */
  fun judge(profile: ServerProfile, probe: Probe): Judgement {
    val info = probe.info ?: return Judgement.UNREACHABLE
    val isPrimary = ServerAddress.normalize(probe.url) == profile.primary
    return when (ServerIdentity.check(profile.serverId, info.serverId)) {
      ServerIdentity.Pin.MATCH -> Judgement.ACCEPT
      ServerIdentity.Pin.MISMATCH -> Judgement.MISMATCH
      //  سنجاق نشده: فقط نشانیِ اصلی، و فقط سرورِ قدیمی یا نخستین وصل (که خودش تأیید می‌خواهد)
      ServerIdentity.Pin.LEGACY_UNPINNED, ServerIdentity.Pin.FIRST_USE ->
        if (isPrimary) Judgement.ACCEPT else Judgement.UNVERIFIABLE
    }
  }

  sealed class Pick {
    data class Chosen(val url: String, val info: ServerInfo) : Pick()
    /** هیچ نشانی نرسید؛ `mismatched` نشانی‌هایی که سرورِ دیگری پشتشان بود */
    data class None(val mismatched: List<String>) : Pick()
  }

  /** نخستین نشانیِ پذیرفته به همان ترتیبِ `probes` */
  fun pick(profile: ServerProfile, probes: List<Probe>): Pick {
    val mismatched = ArrayList<String>()
    for (p in probes) {
      when (judge(profile, p)) {
        Judgement.ACCEPT -> return Pick.Chosen(ServerAddress.normalize(p.url), p.info!!)
        Judgement.MISMATCH -> mismatched += ServerAddress.normalize(p.url)
        else -> Unit
      }
    }
    return Pick.None(mismatched)
  }

  /**
   *  سنجشِ نشانی‌ها یکی‌یکی به ترتیبِ [order]، تا نخستین که پذیرفته شد — همان حلقهٔ `recheck` ِ برنامهٔ دکان، حالا
   *  مشترکِ همهٔ برنامه‌ها (دکان و پیک، خریدِ مشتری). [ask] پاسخِ `config/server` (یا `/api/health`) ِ یک نشانی؛
   *  `null` یعنی نرسید.
   *
   *  سرورِ پیش‌فرض: نشانیِ اصلی‌اش در خودِ نسخه است و سنجاقش اجباری نیست (صاحب محصول سرورش را جابه‌جا می‌کند)؛
   *  هر نشانیِ دیگر فقط با همان `serverId` ِ سنجاق‌شده ([judge]).
   */
  fun choose(profile: ServerProfile, lastGood: String?, ask: (String) -> ServerInfo?): Pick {
    val probes = ArrayList<Probe>()
    for (u in order(profile.urls, lastGood)) {
      val probe = Probe(u, ask(u))
      probes += probe
      val info = probe.info ?: continue
      val primaryOfDefault = profile.isDefault && ServerAddress.normalize(u) == profile.primary
      if (primaryOfDefault || judge(profile, probe) == Judgement.ACCEPT) return Pick.Chosen(ServerAddress.normalize(u), info)
    }
    return pick(profile, probes)
  }
}

/**
 *  نشانی‌های تازهٔ **همان** سرور، از زبانِ خودِ سرور (`publicUrls` ِ `config/server`) — بی نسخهٔ تازه و بی کیو‌آر.
 *
 *  ⛔ فقط سرورِ سنجاق‌شده (همان `serverId` ِ پروفایل، پاسخ از نشانیِ خودِ پروفایل) می‌گوید؛ و هر نشانیِ گفته‌شده تا خودش
 *  همان `serverId` را ندهد ([adopt]) به پروفایل نمی‌رود. ⛔ نشانیِ شبکهٔ محلی هرگز آموخته نمی‌شود: گواهیِ خودامضا
 *  سنجاق می‌خواهد و سنجاق فقط با دستِ صاحبِ دکان (کیو‌آر یا «بررسی») — همان قاعدهٔ امروز.
 */
object ServerLearn {

  /** هر چند وقت سرورِ مورد اعتماد دوباره پرسیده شود، فقط برای نشانیِ تازه (جابه‌جایی جدا و زودتر است) */
  const val LEARN_MS: Long = 6 * 60 * 60_000L

  /** نشانی‌های اعلام‌شده‌ای که هنوز در پروفایل نیستند و قاعدهٔ نشانی را دارند — هنوز سنجیده نشده‌اند */
  fun candidates(profile: ServerProfile, from: String, info: ServerInfo, devBuild: Boolean = false): List<String> {
    val pinned = profile.serverId ?: return emptyList()
    if (info.serverId != pinned || ServerAddress.normalize(from) !in profile.urls) return emptyList()
    val room = ServerProfiles.MAX_URLS - profile.urls.size
    if (room <= 0) return emptyList()
    return (listOf(info.publicUrl) + info.publicUrls).asSequence()
      .filter { it.isNotEmpty() }
      .mapNotNull { (ServerAddress.check(it, lanAllowed = false, devBuild = devBuild) as? ServerAddress.Verdict.Ok)?.parts }
      .filter { it.kind == ServerAddress.HostKind.PUBLIC_NAME || it.kind == ServerAddress.HostKind.PUBLIC_IP }
      .map { it.url }
      .filter { it !in profile.urls }
      .distinct().take(room).toList()
  }

  /** نشانیِ آموخته فقط وقتی به پروفایل می‌رود که خودش همان `serverId` را داده باشد؛ وگرنه `null` */
  fun adopt(profile: ServerProfile, url: String, answer: ServerInfo?): ServerProfile? {
    val pinned = profile.serverId ?: return null
    if (answer == null || answer.serverId != pinned) return null
    val u = ServerAddress.normalize(url)
    if (u.isEmpty() || u in profile.urls || profile.urls.size >= ServerProfiles.MAX_URLS) return null
    return profile.copy(urls = profile.urls + u)
  }

  /** هر نشانیِ اعلام‌شده را جدا می‌پرسد ([ask]) و فقط آن‌ها را که همان سرورند می‌افزاید؛ بی تغییر ⇒ همان [profile] */
  fun learn(profile: ServerProfile, from: String, info: ServerInfo, devBuild: Boolean = false, ask: (String) -> ServerInfo?): ServerProfile {
    var p = profile
    for (c in candidates(profile, from, info, devBuild)) {
      p = adopt(p, c, runCatching { ask(c) }.getOrNull()) ?: p
    }
    return p
  }
}

/**
 *  دادهٔ محلیِ هر سرور جداست — «سرورِ دیگر» دقیقاً مثلِ «حسابِ دیگر» (`LedgerOwner`): دفتر، عکس‌ها،
 *  سایهٔ همگام‌سازی، مجوز و توکن زیرِ نامِ همان سرور بایگانی می‌شوند و با برگشتن، سرِ جایشان
 *  برمی‌گردند. پس رفتن به سرورِ دیگر هیچ‌وقت دادهٔ این یکی را آن‌جا نمی‌فرستد.
 *
 *  ⛔ نامِ بایگانیِ پروفایلِ پیش‌فرض **همان نامِ امروز** است (`حساب` یا `حساب@دکان`)، وگرنه دفترِ
 *  بایگانی‌شدهٔ همهٔ گوشی‌های امروز گم می‌شد. سرورِ دیگر نامِ هش‌شده می‌گیرد: نامِ پرونده در
 *  `ShopStore`/`PhotoStore` فقط ۴۸ نویسهٔ حرف و رقم است و پیشوندِ بلند، دکانِ دوم را از قلم
 *  می‌انداخت (دو دکان، یک بایگانی).
 */
object DataPartition {

  /** همان `LedgerOwner.key` ِ امروز */
  fun legacyKey(user: String, shop: String): String = if (shop.isBlank()) user else "$user@$shop"

  /** دامنهٔ دادهٔ یک پروفایل: خالی برای پیش‌فرض؛ شناسهٔ سرور، یا (سرورِ قدیمی) شناسهٔ پروفایل */
  fun scope(profile: ServerProfile): String = when {
    profile.isDefault -> ""
    profile.serverId != null -> "s:" + profile.serverId
    else -> "p:" + profile.id
  }

  /** نامِ بایگانیِ دفترِ «این حساب در این دکان روی این سرور» — حرف و رقم، حداکثر ۴۱ نویسه */
  fun ledgerKey(profile: ServerProfile, user: String, shop: String): String =
    keyIn(scope(profile), user, shop)

  fun keyIn(scope: String, user: String, shop: String): String {
    val legacy = legacyKey(user, shop)
    if (scope.isEmpty()) return legacy
    return "x" + hex(sha256("$scope|$legacy".encodeToByteArray())).take(40)
  }

  /** نامِ بایگانیِ «آنچه روی میز بود» هنگامِ رفتن از این سرور — حتی بی حساب (دفترِ آفلاین) */
  fun deskKey(profile: ServerProfile): String {
    val s = scope(profile).ifEmpty { "default" }
    return "d" + hex(sha256("desk|$s".encodeToByteArray())).take(40)
  }

  private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
