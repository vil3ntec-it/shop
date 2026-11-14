package ir.vil3ntec.tohid.core.hub

import ir.vil3ntec.tohid.core.audit.HmacSha256
import ir.vil3ntec.tohid.core.audit.Sha256
import ir.vil3ntec.tohid.core.platform.secureRandomBytes
import ir.vil3ntec.tohid.core.server.ServerAddress
import ir.vil3ntec.tohid.core.server.ServerProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 *  SYN-09 — جفت کردنِ گوشی با هابِ کامپیوتر (`docs/SYN-09-HUB-fa.md` §۲).
 *
 *  - ⛔ برگهٔ جفت‌سازی را **فقط صاحب یا مدیر** روی خودِ کامپیوترِ هاب می‌سازد (هیچ درِ شبکه‌ای برگه نمی‌سازد).
 *  - برگه دو راز دارد: کدِ شش‌رقمی (برای زدن با دست) و توکنِ ۱۶۰ بیتی (فقط درونِ QR). هر دو **یک‌بارمصرف**،
 *    ۲ دقیقه، و ۵ تلاشِ نادرست ⇒ برگه می‌سوزد. برگه فقط در حافظهٔ هاب است، هرگز روی دیسک.
 *  - QR جز همان توکنِ یک‌باره رازی ندارد: نشانی، `serverId` و سنجاقِ گواهی همه عمومی‌اند.
 *  - راهِ کد: گوشی سنجاق را از QR ندارد ⇒ پیش از فرستادنِ هر رازی، هاب باید ثابت کند کد را می‌داند و آن را به
 *    **سنجاقِ گواهیِ خودش** بسته است ([proof]). میانجیِ شبکه با گواهیِ دیگر سنجاقِ دیگری دارد و اثباتش جور نمی‌شود؛
 *    توکنِ سرورِ گوشی هرگز به او نمی‌رسد. اثبات‌خواهی هم سقف دارد ([MAX_ASKS]).
 *  - جفت شدن کلیدِ ۲۵۶ بیتیِ همان دستگاه را می‌دهد؛ هاب فقط درهمش را نگه می‌دارد. کلید کنارِ توکنِ سرور
 *    می‌آید، نه به‌جایش: «همان دکان» را همچنان سرور (یا فهرستِ ۲۴ ساعتهٔ `HubAuth`) می‌گوید.
 */
data class HubTicket(
  val code: String,
  val token: String,
  /** صاحب/مدیری که برگه را ساخت (نام یا شناسه، برای فهرستِ جفت‌شده‌ها) */
  val issuedBy: String,
  val issuedAt: Long,
  val expiresAt: Long,
  val tries: Int = 0,
  val asks: Int = 0,
  val used: Boolean = false,
)

@Serializable
data class HubPeer(
  val deviceId: String,
  val shopId: String,
  /** `SHA-256("tohid-hub-peer-v1:" + کلید)` — خودِ کلید هرگز */
  val keyHash: String,
  val pairedAt: Long,
  val pairedBy: String = "",
)

@Serializable
data class HubPeers(val version: Int = 1, val peers: List<HubPeer> = emptyList())

object HubPair {
  const val TTL_MS: Long = 2 * 60_000L
  const val MAX_TRIES: Int = 5
  const val MAX_ASKS: Int = 5
  const val CODE_DIGITS: Int = 6
  const val TOKEN_BYTES: Int = 20
  const val KEY_BYTES: Int = 32
  const val NONCE_BYTES: Int = 16
  const val MAX_PEERS: Int = 64
  const val QR_PREFIX: String = "tohid-hub:1?"

  /** سرآیندِ کلیدِ دستگاهِ جفت‌شده */
  const val KEY_HEADER: String = "X-Hub-Key"

  private val PAIR_ROLES = setOf("owner", "manager")
  private val json = Json { ignoreUnknownKeys = true }

  enum class Refusal(val status: Int, val code: String) {
    ROLE(403, "hub_pair_role"),
    NONE(403, "hub_pair_none"),
    USED(403, "hub_pair_used"),
    EXPIRED(403, "hub_pair_expired"),
    LOCKED(429, "hub_pair_locked"),
    WRONG(403, "hub_pair_wrong"),
    FULL(503, "hub_pair_full"),
  }

  sealed class Issue {
    data class Ok(val ticket: HubTicket) : Issue()
    data class Denied(val reason: Refusal) : Issue()
  }

  sealed class Claim {
    data class Ok(val ticket: HubTicket) : Claim()
    data class Refused(val reason: Refusal) : Claim()
  }

  fun mayPair(role: String?): Boolean = role?.trim()?.lowercase() in PAIR_ROLES

  /** برگهٔ تازه — فقط صاحب/مدیر. برگهٔ پیشین (اگر بود) با همین کنار می‌رود */
  fun issue(role: String?, by: String, now: Long, random: (Int) -> ByteArray = ::secureRandomBytes): Issue {
    if (!mayPair(role)) return Issue.Denied(Refusal.ROLE)
    return Issue.Ok(
      HubTicket(code = codeFrom(random), token = hex(random(TOKEN_BYTES)), issuedBy = by.take(64), issuedAt = now, expiresAt = now + TTL_MS),
    )
  }

  /** شش رقمِ یکنواخت (نمونه‌گیریِ ردکننده؛ باقی‌ماندهٔ تقسیم سوگیری نمی‌سازد) */
  internal fun codeFrom(random: (Int) -> ByteArray): String {
    val limit = 4_294_000_000L // بزرگ‌ترین مضربِ ۱۰^۶ زیرِ ۲^۳۲
    repeat(64) {
      val b = random(4)
      val v = ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or
        ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
      if (v < limit) return (v % 1_000_000L).toString().padStart(CODE_DIGITS, '0')
    }
    error("تصادفیِ امن در دسترس نیست")
  }

  fun alive(t: HubTicket?, now: Long): Boolean =
    t != null && !t.used && t.tries < MAX_TRIES && now in t.issuedAt..t.expiresAt

  /**
   *  یک تلاشِ جفت‌سازی با کد یا توکن. نادرست ⇒ یک تلاش کم می‌شود؛ درست ⇒ برگه «به‌کاررفته» (تکرارِ همان راز رد).
   *  ⛔ زمانِ عقب‌رفته (پیش از ساختِ برگه) هم «گذشته» است.
   */
  fun claim(t: HubTicket?, secret: String?, now: Long): Pair<HubTicket?, Claim> {
    if (t == null) return null to Claim.Refused(Refusal.NONE)
    if (t.used) return t to Claim.Refused(Refusal.USED)
    if (t.tries >= MAX_TRIES) return t to Claim.Refused(Refusal.LOCKED)
    if (now !in t.issuedAt..t.expiresAt) return t to Claim.Refused(Refusal.EXPIRED)
    val s = secret?.trim().orEmpty()
    val ok = when {
      s.length == CODE_DIGITS && s.all { it in '0'..'9' } -> same(s, t.code)
      s.length == TOKEN_BYTES * 2 -> same(s.lowercase(), t.token)
      else -> false
    }
    if (!ok) {
      val n = t.copy(tries = t.tries + 1)
      return n to Claim.Refused(if (n.tries >= MAX_TRIES) Refusal.LOCKED else Refusal.WRONG)
    }
    val n = t.copy(used = true)
    return n to Claim.Ok(n)
  }

  /** اجازهٔ یک اثبات‌خواهیِ راهِ کد ([proof]) — هر کدام اثرِ کد را بیرون می‌دهد، پس شمرده می‌شود */
  fun ask(t: HubTicket?, now: Long): Pair<HubTicket?, Refusal?> {
    if (t == null) return null to Refusal.NONE
    if (t.used) return t to Refusal.USED
    if (now !in t.issuedAt..t.expiresAt) return t to Refusal.EXPIRED
    if (t.asks >= MAX_ASKS || t.tries >= MAX_TRIES) return t to Refusal.LOCKED
    return t.copy(asks = t.asks + 1) to null
  }

  /**
   *  اثباتِ «کد را می‌دانم و این همان گواهیِ من است»: HMAC-SHA256 با کلیدِ کد روی `side|pin|nonce`.
   *  هاب با `side = "hub"` و سنجاقِ خودش می‌سازد؛ گوشی با سنجاقِ گواهی‌ای که خودش دید می‌سنجد.
   */
  fun proof(code: String, side: String, pin: String, nonce: String): String =
    HmacSha256.hexOf("tohid-hub-pair-v1:$code".encodeToByteArray(), "$side|${pin.lowercase()}|$nonce")

  fun validNonce(n: String?): Boolean = n != null && n.length == NONCE_BYTES * 2 && n.all { it in '0'..'9' || it in 'a'..'f' }

  fun newKey(random: (Int) -> ByteArray = ::secureRandomBytes): String = hex(random(KEY_BYTES))

  fun newNonce(random: (Int) -> ByteArray = ::secureRandomBytes): String = hex(random(NONCE_BYTES))

  fun keyHash(key: String): String = Sha256.hexOf("tohid-hub-peer-v1:$key")

  /** دستگاه پیش‌تر جفت شده بود ⇒ کلیدِ تازه به‌جای کهنه؛ فهرست پر ⇒ `null` */
  fun pairPeer(p: HubPeers, peer: HubPeer): HubPeers? {
    val rest = p.peers.filter { it.deviceId != peer.deviceId }
    if (rest.size >= MAX_PEERS) return null
    return p.copy(peers = rest + peer)
  }

  /** کلیدِ درست برای همان دستگاه و همان دکانِ هاب؛ هر چیزِ دیگر ⇒ `null` */
  fun peerOf(p: HubPeers, deviceId: String?, key: String?, shopId: String): HubPeer? {
    if (deviceId.isNullOrBlank() || key.isNullOrBlank() || key.length != KEY_BYTES * 2 || shopId.isBlank()) return null
    val h = keyHash(key.trim().lowercase())
    val peer = p.peers.firstOrNull { it.deviceId == deviceId } ?: return null
    return peer.takeIf { same(it.keyHash, h) && it.shopId == shopId }
  }

  fun unpair(p: HubPeers, deviceId: String): HubPeers = p.copy(peers = p.peers.filter { it.deviceId != deviceId })

  fun encode(p: HubPeers): String = json.encodeToString(HubPeers.serializer(), p)

  fun decode(text: String?): HubPeers? = text?.let {
    runCatching { json.decodeFromString(HubPeers.serializer(), it) }.getOrNull()
  }?.takeIf { it.version == 1 }

  /* ------------------------------ QR ------------------------------ */

  data class Qr(val url: String, val serverId: String, val pin: String, val token: String)

  /** `tohid-hub:1?u=<نشانی>&s=<serverId>&p=<سنجاق>&t=<توکنِ یک‌باره>` — جز `t` هیچ رازی */
  fun qr(url: String, serverId: String, pin: String, token: String): String =
    QR_PREFIX + "u=" + enc(url) + "&s=" + enc(serverId) + "&p=" + pin.lowercase() + "&t=" + token

  /** فقط https ِ شبکهٔ دکان، سنجاقِ درست و توکنِ درست؛ هر چیزِ دیگر ⇒ `null` */
  fun parseQr(text: String?): Qr? {
    val t = text?.trim() ?: return null
    if (!t.startsWith(QR_PREFIX) || t.length > 600) return null
    val kv = t.removePrefix(QR_PREFIX).split('&').mapNotNull { part ->
      val i = part.indexOf('=')
      if (i <= 0) null else part.substring(0, i) to (dec(part.substring(i + 1)) ?: return null)
    }.toMap()
    val url = kv["u"] ?: return null
    val parts = (ServerAddress.check(url, lanAllowed = true) as? ServerAddress.Verdict.Ok)?.parts ?: return null
    if (!parts.https || parts.kind != ServerAddress.HostKind.LAN) return null
    val pin = ServerProfile.normalizeCertPin(kv["p"]) ?: return null
    val token = kv["t"]?.lowercase()?.takeIf { s -> s.length == TOKEN_BYTES * 2 && s.all { it in '0'..'9' || it in 'a'..'f' } } ?: return null
    val sid = kv["s"]?.takeIf { s -> s.length in 1..80 && s.all { it.isLetterOrDigit() && it.code < 128 || it in "_-." } } ?: return null
    return Qr(url, sid, pin, token)
  }

  private fun enc(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
      val c = (b.toInt() and 0xFF).toChar()
      if (c.isLetterOrDigit() && c.code < 128 || c in "-._~") append(c) else append('%').append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
    }
  }

  private fun dec(s: String): String? {
    val out = ArrayList<Byte>()
    var i = 0
    while (i < s.length) {
      val c = s[i]
      if (c == '%') {
        val hi = s.getOrNull(i + 1)?.digitToIntOrNull(16) ?: return null
        val lo = s.getOrNull(i + 2)?.digitToIntOrNull(16) ?: return null
        out += ((hi shl 4) or lo).toByte()
        i += 3
      } else {
        if (c.code >= 128) return null
        out += c.code.toByte()
        i++
      }
    }
    return out.toByteArray().decodeToString()
  }

  private const val HEX = "0123456789abcdef"

  fun hex(b: ByteArray): String = buildString(b.size * 2) {
    for (x in b) { append(HEX[(x.toInt() shr 4) and 0xF]); append(HEX[x.toInt() and 0xF]) }
  }

  /** مقایسهٔ هم‌زمان (زمانش به جای نخستین تفاوت بسته نیست) */
  fun same(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var d = 0
    for (i in a.indices) d = d or (a[i].code xor b[i].code)
    return d == 0
  }
}
