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
