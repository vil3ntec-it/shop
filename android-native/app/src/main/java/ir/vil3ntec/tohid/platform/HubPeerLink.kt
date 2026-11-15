package ir.vil3ntec.tohid.platform

import ir.vil3ntec.tohid.core.hub.HubPair
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.server.ServerProfile
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncV1Client
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.InputStream
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 *  S29 / SYN-09 — سمتِ گوشی ِ هابِ کامپیوتر برای همگام‌سازیِ v1 (`docs/SYN-09-HUB-fa.md` §۲ و §۴.۱). بی `Context`:
 *  همین در برنامه (`HubLinkV1Android`) و در آزمونِ کامپیوتر با دو گوشیِ ساختگی و سرورِ واقعی.
 *
 *  - هر تماس روی گواهیِ **سنجاق‌شدهٔ** هاب (`PinnedTls`)، بی پراکسی، بی دنبال کردنِ تغییرِ مسیر.
 *  - ⛔ سرور همیشه اول: این‌ها فقط وقتی صدا زده می‌شوند که push/pull ِ سرور **نرسید** ([cloudDown]).
 *  - ⛔ صف دست نمی‌خورد: پاسخِ هاب «در انتظار» است؛ op فقط با پاسخِ خودِ سرور از صفِ گوشی بیرون می‌رود.
 *  - op ِ دستگاهِ دیگر از هاب با همان راهِ نشاندنِ pull (`absorb`، cursor ِ سرور دست‌نخورده) می‌نشیند؛ pull ِ بعدیِ
 *    سرور همان opها را به ترتیبِ سرور دوباره می‌آورد و مرجع است. op ای که گوشی از سرور دیده بود دوباره نمی‌نشیند.
 */
class HubPeerLink(
  val url: String,
  val pin: String,
  private val key: String,
  val serverId: String,
  /** `Authorization`، `X-Device`، `X-App`، `X-App-Version` (و `X-Branch-Id` اگر هست) — همان سرآیندهای سرور */
  private val identity: () -> Map<String, String>,
  private val timeoutMs: Int = 4_000,
) {
  data class Relay(val status: Int, val accepted: Boolean, val queued: Int, val error: String = "")

  data class Feed(val epoch: String, val next: Long, val more: Boolean, val ops: List<SyncOp>)

  /** همان بدنهٔ `POST /sync/v1/push` ⇒ ته ِ دفترِ هاب */
  fun relay(body: JsonObject): Relay {
    val (st, text) = call("POST", "/hub/v1/ops/push", body.toString())
    val o = parse(text)
    if (st != 200 || o == null) return Relay(st, false, 0, errorOf(o))
    return Relay(st, (o["accepted"] as? JsonPrimitive)?.content == "true", (o["queued"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0)
  }

  /** opهای دستگاه‌های دیگر پس از [since]؛ op ای که `server_seq` ش ≤ [cloudCursor] است را هاب نمی‌دهد */
  fun feed(since: Long, cloudCursor: Long): Feed? {
    val (st, text) = call("GET", "/hub/v1/ops?since=$since&cursor=$cloudCursor", null)
    val o = parse(text)?.takeIf { st == 200 && (it["ok"] as? JsonPrimitive)?.content == "true" } ?: return null
    val ops = (o["ops"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(SyncOp::fromJson) }.filter { it.opId.isNotEmpty() }
    return Feed(
      epoch = (o["epoch"] as? JsonPrimitive)?.content.orEmpty(),
      next = (o["next"] as? JsonPrimitive)?.content?.toLongOrNull() ?: since,
      more = (o["more"] as? JsonPrimitive)?.content == "true",
      ops = ops,
    )
  }

  private fun call(method: String, path: String, body: String?): Pair<Int, String?> =
    HubHttp.call(method, url.trimEnd('/') + path, identity() + mapOf(HubPair.KEY_HEADER to key, "X-Server-Id" to serverId), body, pin, timeoutMs)

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    internal fun parse(text: String?): JsonObject? = text?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    internal fun errorOf(o: JsonObject?): String = (o?.get("error") as? JsonPrimitive)?.content.orEmpty()

    /** «سرور نرسید» — خطای شبکه، نه پاسخِ HTTP ِ سرور (پاسخِ خطا یعنی سرور هست و هاب جای آن نمی‌نشیند) */
    fun cloudDown(error: Throwable): Boolean = when (error) {
      is ApiFailure.Offline, is ApiFailure.Unreachable, is ApiFailure.Timeout -> true
      is ApiFailure -> false
      is java.io.IOException -> true
      else -> false
    }
  }
}

/**
 *  یک دورِ «سرور نرسید»: همهٔ صف (دسته‌دسته، همان برشِ `OpQueue.cut`) به هاب، سپس opهای دستگاه‌های دیگر از آن.
 *  [Store] cursor ِ هاب، `epoch` ِ دفترش و حلقهٔ `op_id`هایی را که گوشی از خودِ سرور گرفت نگه می‌دارد.
 */
class HubExchangeV1(private val link: HubPeerLink, private val store: Store) {

  interface Store {
    var since: Long
    var epoch: String
    /** کدام از این `op_id`ها را گوشی پیش‌تر از خودِ سرور گرفته است */
    fun seenFromCloud(ids: Collection<String>): Set<String>
    fun sawFromCloud(ids: Collection<String>)
  }

  data class Outcome(val relayed: Int = 0, val received: Int = 0, val refused: String = "")

  /**
   *  @param absorb همان نشاندنِ pull با cursor ِ دست‌نخورده (`SyncV1Engine.absorbRemote(…, cursor = null)`)
   */
  fun exchange(
    deviceId: String, queued: List<SyncOp>, queuedCount: Int, caps: (List<SyncOp>) -> Boolean, schema: Int,
    since: Long?, cloudCursor: Long, absorb: (List<SyncOp>) -> Unit,
  ): Outcome {
    var relayed = 0
    var rest = queued
    var rounds = 0
    while (rest.isNotEmpty() && rounds++ < MAX_BATCHES) {
      val batch = OpQueue.cut(rest, rest.map { it.toJson().toString().encodeToByteArray().size + 1 })
      if (batch.isEmpty()) break
      val r = link.relay(SyncV1Client.pushBody(deviceId, batch, queuedCount, caps(batch), schema, since))
      if (r.status != 200) return Outcome(relayed, 0, r.error.ifEmpty { "hub_${r.status}" })
      relayed += batch.size
      rest = rest.drop(batch.size)
    }
    var received = 0
    var pages = 0
    while (pages++ < MAX_PAGES) {
      val f = link.feed(store.since, cloudCursor) ?: break
      if (f.epoch != store.epoch) {
        //  دفترِ هاب از نو ساخته شد ⇒ از آغازِ دفترِ تازه (opهای تکراری با «دیده از سرور» و یکتاییِ op کنار می‌روند)
        store.epoch = f.epoch
        if (store.since != 0L) { store.since = 0; continue }
      }
      val seen = store.seenFromCloud(f.ops.map { it.opId })
      val fresh = f.ops.filter { it.opId !in seen }
      if (fresh.isNotEmpty()) { absorb(fresh); received += fresh.size }
      store.since = f.next
      if (!f.more) break
    }
    return Outcome(relayed, received)
  }

  companion object {
    const val MAX_BATCHES = 20
    const val MAX_PAGES = 20
  }
}

/** HTTPS ِ سنجاق‌شده به هاب — همان `HubRelayAndroid.call` (بی پراکسی، بی redirect، پاسخ با سقف) */
internal object HubHttp {
  private const val MAX_REPLY = 2 * 1024 * 1024

  fun call(method: String, url: String, headers: Map<String, String>, body: String?, pin: String, timeoutMs: Int): Pair<Int, String?> = try {
    val norm = ServerProfile.normalizeCertPin(pin) ?: return 0 to null
    val c = URL(url).openConnection(Proxy.NO_PROXY) as HttpsURLConnection
    PinnedTls.of(norm).applyTo(c)
    c.connectTimeout = timeoutMs
    c.readTimeout = timeoutMs
    c.requestMethod = method
    c.instanceFollowRedirects = false
    for ((k, v) in headers) if (v.isNotEmpty()) c.setRequestProperty(k, v)
    c.setRequestProperty("Accept", "application/json")
    if (body != null) {
      val bytes = body.toByteArray(Charsets.UTF_8)
      c.doOutput = true
      c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
      c.setFixedLengthStreamingMode(bytes.size)
      c.outputStream.use { it.write(bytes) }
    }
    val status = c.responseCode
    val text = (if (status >= 400) c.errorStream else c.inputStream)?.let { readCapped(it) }
    c.disconnect()
    status to text
  } catch (_: Exception) {
    0 to null
  }

  private fun readCapped(input: InputStream): String? = input.use { s ->
    val out = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
      val n = s.read(chunk)
      if (n < 0) break
      out.write(chunk, 0, n)
      if (out.size() > MAX_REPLY) return null
    }
    String(out.toByteArray(), Charsets.UTF_8)
  }
}

/**
 *  جفت کردنِ گوشی با هاب (`HubPair`): QR (نشانی، سنجاق و توکنِ یک‌باره) یا کدِ شش‌رقمی روی نشانیِ پیدا‌شده (mDNS).
 *  ⛔ راهِ کد: پیش از فرستادنِ توکنِ سرور، گواهیِ هاب دیده می‌شود (دست‌دادن همان‌جا می‌شکند، `PinnedTls.capture`)،
 *     سپس روی **همان** سنجاق اثباتِ هاب سنجیده می‌شود؛ جور نشد ⇒ هیچ چیزی نمی‌رود.
 */
object HubPairClient {
  data class Paired(val url: String, val pin: String, val key: String, val serverId: String)

  sealed class Result {
    data class Ok(val paired: Paired) : Result()
    data class Failed(val code: String) : Result()
  }

  fun withQr(qrText: String, identity: Map<String, String>, timeoutMs: Int = 5_000): Result {
    val q = HubPair.parseQr(qrText) ?: return Result.Failed("hub_bad_qr")
    return claim(q.url, q.pin, q.token, identity, timeoutMs, expectServer = q.serverId)
  }

  fun withCode(url: String, code: String, identity: Map<String, String>, timeoutMs: Int = 5_000, random: (Int) -> ByteArray = ::randomBytes): Result {
    val c = code.filter { it.isDigit() }
    if (c.length != HubPair.CODE_DIGITS) return Result.Failed("hub_pair_wrong")
    val pin = PinnedTls.capture(url, timeoutMs)?.let(ServerProfile::normalizeCertPin) ?: return Result.Failed("hub_unreachable")
    val nonce = HubPair.newNonce(random)
    val (st, text) = HubHttp.call("POST", url.trimEnd('/') + "/hub/v1/pair/hello", emptyMap(), buildJsonObject { put("nonce", nonce) }.toString(), pin, timeoutMs)
    val o = HubPeerLink.parse(text)
    if (st != 200 || o == null) return Result.Failed(HubPeerLink.errorOf(o).ifEmpty { "hub_unreachable" })
    val proof = (o["proof"] as? JsonPrimitive)?.content.orEmpty()
    if (!HubPair.same(proof, HubPair.proof(c, "hub", pin, nonce))) return Result.Failed("hub_not_trusted")
    return claim(url, pin, c, identity, timeoutMs, expectServer = null)
  }

  private fun claim(url: String, pin: String, secret: String, identity: Map<String, String>, timeoutMs: Int, expectServer: String?): Result {
    val (st, text) = HubHttp.call("POST", url.trimEnd('/') + "/hub/v1/pair", identity, buildJsonObject { put("secret", secret) }.toString(), pin, timeoutMs)
    val o = HubPeerLink.parse(text)
    if (st != 200 || o == null) return Result.Failed(HubPeerLink.errorOf(o).ifEmpty { "hub_unreachable" })
    val key = (o["key"] as? JsonPrimitive)?.content?.takeIf { it.length == HubPair.KEY_BYTES * 2 } ?: return Result.Failed("hub_bad_reply")
    val sid = (o["server_id"] as? JsonPrimitive)?.content.orEmpty()
    if (sid.isEmpty() || (expectServer != null && sid != expectServer)) return Result.Failed("hub_other_server")
    return Result.Ok(Paired(url.trimEnd('/'), pin, key, sid))
  }

  private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { java.security.SecureRandom().nextBytes(it) }
}
