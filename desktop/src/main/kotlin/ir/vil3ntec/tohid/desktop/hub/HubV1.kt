package ir.vil3ntec.tohid.desktop.hub

import com.sun.net.httpserver.HttpExchange
import ir.vil3ntec.tohid.core.hub.HubAppend
import ir.vil3ntec.tohid.core.hub.HubEntry
import ir.vil3ntec.tohid.core.hub.HubForwarder
import ir.vil3ntec.tohid.core.hub.HubLimiter
import ir.vil3ntec.tohid.core.hub.HubLog
import ir.vil3ntec.tohid.core.hub.HubLogBox
import ir.vil3ntec.tohid.core.hub.HubLogForwarder
import ir.vil3ntec.tohid.core.hub.HubLogLimits
import ir.vil3ntec.tohid.core.hub.HubLogState
import ir.vil3ntec.tohid.core.hub.HubMarks
import ir.vil3ntec.tohid.core.hub.HubPair
import ir.vil3ntec.tohid.core.hub.HubParse
import ir.vil3ntec.tohid.core.hub.HubPeer
import ir.vil3ntec.tohid.core.hub.HubPeers
import ir.vil3ntec.tohid.core.hub.HubQueue
import ir.vil3ntec.tohid.core.hub.HubTicket
import ir.vil3ntec.tohid.core.hub.HubV1Cloud
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream

/**
 *  S29 / SYN-09 — بخشِ v1 ِ هاب: جفت‌سازی، دفترِ فقط‌افزودنیِ opهای v1 و خوراکِ opهای دستگاه‌های دیگر
 *  (`docs/SYN-09-HUB-fa.md` §۲ و §۴.۱). همان سرورِ HTTPS، همان سقفِ هر IP و همان ورودِ `HubServer.authorize`.
 *
 *  | در | ورود | کار |
 *  |---|---|---|
 *  | `POST /hub/v1/pair/hello` | هیچ (هنوز راز نمی‌رود) | اثباتِ «کد را می‌دانم» بسته به سنجاقِ همین گواهی |
 *  | `POST /hub/v1/pair` | توکنِ سرورِ همان دکان | کد یا توکنِ یک‌باره ⇒ کلیدِ همان دستگاه |
 *  | `POST /hub/v1/ops/push` | کلیدِ جفت‌شده **و** توکن | همان بدنهٔ push ِ v1 ⇒ ته ِ دفتر |
 *  | `GET /hub/v1/ops?since=&cursor=` | کلیدِ جفت‌شده **و** توکن | opهای دستگاه‌های دیگر |
 *
 *  ⛔ دستگاهِ جفت‌نشده هیچ چیزی از دفتر نمی‌بیند (۴۰۳ `hub_not_paired` پیش از هر تماس با سرور).
 *  ⛔ هاب هیچ op ای را در دفترِ سرور «اعمال» نمی‌کند؛ فقط با توکنِ خودِ دستگاه از همان راهِ push ِ برنامه می‌فرستد.
 *  روی دیسک: دفتر (هر خط مُهرشده، فقط افزودن و fsync)، نشانه‌ها و فهرستِ جفت‌شده‌ها — همه با `seal`. برگه فقط حافظه.
 */
internal class HubV1(
  private val hub: HubServer,
  private val dir: File,
  private val shopId: String,
  private val pin: String,
  cloud: HubV1Cloud,
  private val seal: (String) -> String,
  private val open: (String) -> String?,
  private val clock: () -> Long,
  private val random: (Int) -> ByteArray,
  private val limits: HubLogLimits,
) {
  data class Seen(val at: Long, val ip: String)

  data class Status(
    val peers: List<HubPeer>,
    val seen: Map<String, Seen>,
    val pending: Int,
    val forwarded: Long,
    val dropped: Long,
    val ticket: HubTicket?,
  )

  private val lock = Any()
  private val logFile = File(dir, LOG_FILE)
  private var log: HubLogState = loadLog()
  private var peers: HubPeers = loadPeers()
  private var ticket: HubTicket? = null
  private val seen = LinkedHashMap<String, Seen>()
  private val pairs = HubLimiter(perMinute = 10, burst = 5)

  private val box = object : HubLogBox {
    override fun read(): HubLogState = synchronized(lock) { log }
    override fun update(change: (HubLogState) -> HubLogState): HubLogState = synchronized(lock) {
      val n = change(log)
      if (n !== log) { persist(log, n); log = n }
      n
    }
  }
  private val forwarder = HubLogForwarder(box, cloud, limits)

  fun forwardNow(): HubForwarder.Pass = forwarder.drain(tokenOf = hub::tokenOf, onAuthFailed = hub::authFailed)

  fun compact() { box.update { HubLog.compact(it, clock(), limits) } }

  fun snapshot(): HubLogState = box.read()

  /* ------------------------------ صفحهٔ هاب (فقط همین کامپیوتر) ------------------------------ */

  /** برگهٔ تازه — ⛔ فقط صاحب/مدیر؛ هیچ درِ شبکه‌ای این را صدا نمی‌زند */
  fun newTicket(role: String?, by: String): HubPair.Issue = synchronized(lock) {
    val r = HubPair.issue(role, by, clock(), random)
    if (r is HubPair.Issue.Ok) ticket = r.ticket
    r
  }

  fun cancelTicket() = synchronized(lock) { ticket = null }

  fun unpair(deviceId: String) = synchronized(lock) {
    val n = HubPair.unpair(peers, deviceId)
    if (n != peers) { hub.save(PEERS_FILE, HubPair.encode(n)); peers = n }
    seen.remove(deviceId)
  }

  fun status(): Status = synchronized(lock) {
    val closed = log.marks
    Status(
      peers.peers.filter { it.shopId == shopId }, LinkedHashMap(seen), HubLog.pending(log).size,
      closed.forwarded, closed.droppedCount, ticket?.takeIf { HubPair.alive(it, clock()) },
    )
  }

  /* ------------------------------ درخواست ------------------------------ */

  fun handle(ex: HttpExchange, method: String, path: String, ip: String) {
    when {
      method == "POST" && path == "/hub/v1/pair/hello" -> challenge(ex, ip)
      method == "POST" && path == "/hub/v1/pair" -> pair(ex, ip)
      method == "POST" && path == "/hub/v1/ops/push" -> push(ex, ip)
      method == "GET" && path == "/hub/v1/ops" -> feed(ex, ip)
      else -> hub.reply(ex, 404, hub.error("hub_not_found"))
    }
  }

  private fun pairAllowed(ex: HttpExchange, ip: String): Boolean {
    if (synchronized(lock) { pairs.allow(ip, clock()) }) return true
    hub.reply(ex, 429, hub.error("hub_rate_limited"), retryAfter = 30)
    return false
  }

  /** راهِ کد: هاب پیش از هر رازِ گوشی ثابت می‌کند کد را می‌داند و آن را به سنجاقِ گواهیِ خودش بسته است */
  private fun challenge(ex: HttpExchange, ip: String) {
    if (!pairAllowed(ex, ip)) return
    val text = hub.readBody(ex, SMALL_BODY) ?: return
    val nonce = field(text, "nonce")?.lowercase()
    if (!HubPair.validNonce(nonce)) return hub.reply(ex, 400, hub.error("hub_bad_nonce"))
    val (code, refusal) = synchronized(lock) {
      val (t, why) = HubPair.ask(ticket, clock())
      ticket = t
      (if (why == null) t?.code else null) to why
    }
    if (refusal != null || code == null) {
      val r = refusal ?: HubPair.Refusal.NONE
      return hub.reply(ex, r.status, hub.error(r.code))
    }
    hub.reply(ex, 200, buildJsonObject {
      put("ok", true)
      put("hub", HubLog.PROTOCOL)
      put("proof", HubPair.proof(code, "hub", pin, nonce!!))
    }.toString())
  }

  private fun pair(ex: HttpExchange, ip: String) {
    if (!pairAllowed(ex, ip)) return
    //  برگه‌ای نیست/سوخته ⇒ همان دم، بی تماس با سرور
    val early = synchronized(lock) { earlyRefusal(ticket) }
    if (early != null) return hub.reply(ex, early.status, hub.error(early.code))
    val text = hub.readBody(ex, SMALL_BODY) ?: return
    val who = hub.authorize(ex, ip) ?: return
    val secret = field(text, "secret")
    val reply = synchronized(lock) {
      val (t, claim) = HubPair.claim(ticket, secret, clock())
      ticket = t
      when (claim) {
        is HubPair.Claim.Refused -> claim.reason to null
        is HubPair.Claim.Ok -> {
          val key = HubPair.newKey(random)
          val n = HubPair.pairPeer(peers, HubPeer(who.device, shopId, HubPair.keyHash(key), clock(), claim.ticket.issuedBy))
          if (n == null) HubPair.Refusal.FULL to null
          else {
            hub.save(PEERS_FILE, HubPair.encode(n))
            peers = n
            seen[who.device] = Seen(clock(), ip)
            null to key
          }
        }
      }
    }
    val (refused, key) = reply
    if (refused != null) return hub.reply(ex, refused.status, hub.error(refused.code))
    hub.reply(ex, 200, buildJsonObject {
      put("ok", true)
      put("hub", HubLog.PROTOCOL)
      put("key", key!!)
      put("server_id", hub.serverId)
      put("pin", pin)
    }.toString())
  }

  private fun earlyRefusal(t: HubTicket?): HubPair.Refusal? = when {
    t == null -> HubPair.Refusal.NONE
    t.used -> HubPair.Refusal.USED
    t.tries >= HubPair.MAX_TRIES -> HubPair.Refusal.LOCKED
    clock() !in t.issuedAt..t.expiresAt -> HubPair.Refusal.EXPIRED
    else -> null
  }

  /**
   *  ⛔ پیش از هر چیز: همین دستگاه با همین کلید جفت شده؟ نه ⇒ ۴۰۳ — هیچ تماسی با سرور و هیچ داده‌ای.
   *  سپس توکن (همان دکان) و `X-Server-Id`.
   */
  private fun paired(ex: HttpExchange, ip: String): HubServer.Who? {
    val device = ex.requestHeaders.getFirst("X-Device")?.trim()
    val key = ex.requestHeaders.getFirst(HubPair.KEY_HEADER)?.trim()
    val peer = synchronized(lock) { HubPair.peerOf(peers, device, key, shopId) }
    if (peer == null) { hub.reply(ex, 403, hub.error("hub_not_paired")); return null }
    val who = hub.authorize(ex, ip) ?: return null
    if (!hub.sameServer(ex)) return null
    synchronized(lock) {
      seen.remove(who.device)
      seen[who.device] = Seen(clock(), ip)
      while (seen.size > HubPair.MAX_PEERS) seen.remove(seen.keys.first())
    }
    return who
  }

  private fun push(ex: HttpExchange, ip: String) {
    val who = paired(ex, ip) ?: return
    val text = hub.readBody(ex, limits.queue.maxBodyBytes) ?: return
    val ids = when (val p = HubQueue.parse(text, who.device, limits.queue)) {
      is HubParse.Bad -> return hub.reply(ex, 400, hub.error(p.code))
      is HubParse.Ok -> p.opIds
    }
    val version = hub.header(ex, "X-App-Version")
    val branch = branchOf(ex.requestHeaders.getFirst("X-Branch-Id"))
    val r = synchronized(lock) {
      val (n, r) = HubLog.append(log, who.device, ids, text, who.app, version, branch, clock(), limits)
      if (r is HubAppend.Accepted) { persist(log, n); log = n }
      r
    }
    if (r is HubAppend.Full) return hub.reply(ex, 503, hub.error(r.code), retryAfter = 30)
    val (sent, pendingNow) = synchronized(lock) { log.marks.serverSeq.keys to HubLog.pendingFor(log, who.device) }
    hub.kick()
    hub.reply(ex, 200, buildJsonObject {
      put("ok", true)
      put("hub", true)
      put("accepted", r is HubAppend.Accepted)
      put("queued", pendingNow)
      put("results", JsonArray(ids.map { id ->
        buildJsonObject {
          put("op_id", id)
          put("status", if (id in sent) "hub_settled" else "hub_queued")
        }
      }))
      put("server_time", clock())
    }.toString())
  }

  private fun feed(ex: HttpExchange, ip: String) {
    val who = paired(ex, ip) ?: return
    val q = query(ex.requestURI.rawQuery)
    val since = q["since"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
    val cursor = q["cursor"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
    val f = synchronized(lock) { HubLog.feed(log, who.device, since, cursor, limits) }
    hub.reply(ex, 200, buildJsonObject {
      put("ok", true)
      put("hub", HubLog.PROTOCOL)
      put("epoch", f.epoch)
      put("next", f.next)
      put("more", f.more)
      put("ops", JsonArray(f.ops))
      put("server_time", clock())
    }.toString())
  }

  /* ------------------------------ دیسک ------------------------------ */

  /** افزودنِ یک دسته به ته ⇒ یک خطِ مُهرشده و fsync؛ هر تغییرِ دیگرِ دسته‌ها (فشرده‌سازی) ⇒ بازنویسیِ اتمی */
  private fun persist(old: HubLogState, n: HubLogState) {
    if (n.entries !== old.entries) {
      val appended = n.entries.size == old.entries.size + 1 && old.entries.indices.all { n.entries[it] === old.entries[it] }
      if (appended) appendLine(n.entries.last()) else rewrite(n.entries)
    }
    if (n.marks != old.marks) hub.save(MARKS_FILE, HubLog.encodeMarks(n.marks))
  }

  private fun line(e: HubEntry): String = seal(HubLog.encodeEntry(e)).also { check('\n' !in it && '\r' !in it) { "مُهر چندخطی است" } }

  private fun appendLine(e: HubEntry) {
    FileOutputStream(logFile, true).use { out ->
      out.write((line(e) + "\n").toByteArray(Charsets.UTF_8))
      out.fd.sync()
    }
    HubFiles.ownerOnly(logFile)
  }

  private fun rewrite(entries: List<HubEntry>) =
    HubFiles.writeAtomic(logFile, entries.joinToString("") { line(it) + "\n" }.toByteArray(Charsets.UTF_8))

  /**
   *  خطِ نیمه‌نوشته (سقوط وسطِ افزودن) یا خراب کنار می‌رود و بقیه می‌مانند (نسخهٔ پیشین `.bad-<زمان>`)؛
   *  نشانه‌های گم‌شده ⇒ `epoch` ِ تازه (گوشی cursor ِ هابش را صفر می‌کند) و همهٔ دسته‌ها دوباره «باز» — سرور
   *  تکرار را «duplicate» می‌کند.
   */
  private fun loadLog(): HubLogState {
    dir.mkdirs()
    HubFiles.ownerOnly(dir)
    val entries = ArrayList<HubEntry>()
    var bad = 0
    if (logFile.isFile) {
      for (l in logFile.readLines(Charsets.UTF_8)) {
        if (l.isBlank()) continue
        val e = runCatching { open(l) }.getOrNull()?.let(HubLog::decodeEntry)
        if (e == null || (entries.isNotEmpty() && e.seq <= entries.last().seq)) bad++ else entries += e
      }
    }
    if (bad > 0) {
      runCatching { logFile.copyTo(File(dir, "$LOG_FILE.bad-${clock()}"), overwrite = true) }
      rewrite(entries)
    }
    val marks = HubLog.decodeMarks(hub.loadText(MARKS_FILE))
      ?: HubMarks(epoch = HubPair.hex(random(8))).also { m ->
        File(dir, MARKS_FILE).takeIf { it.isFile }?.let { HubFiles.setAside(it, clock()) }
        hub.save(MARKS_FILE, HubLog.encodeMarks(m))
      }
    return HubLogState(entries, marks.copy(compactedTo = maxOf(marks.compactedTo, 0)))
  }

  private fun loadPeers(): HubPeers {
    val f = File(dir, PEERS_FILE)
    if (!f.isFile) return HubPeers()
    return HubPair.decode(hub.loadText(PEERS_FILE)) ?: HubPeers().also { HubFiles.setAside(f, clock()) }
  }

  /* ------------------------------ کمکی ------------------------------ */

  private fun field(text: String, name: String): String? = runCatching {
    ((kotlinx.serialization.json.Json.parseToJsonElement(text) as JsonObject)[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
  }.getOrNull()

  private fun query(raw: String?): Map<String, String> =
    raw.orEmpty().split('&').mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()

  companion object {
    const val LOG_FILE = "hub-v1-log.sealed"
    const val MARKS_FILE = "hub-v1-marks.sealed"
    const val PEERS_FILE = "hub-peers.sealed"
    private const val SMALL_BODY = 4 * 1024

    fun owns(path: String): Boolean = path == "/hub/v1/pair" || path == "/hub/v1/pair/hello" || path == "/hub/v1/ops" || path == "/hub/v1/ops/push"

    /** `X-Branch-Id` ِ دستگاه: کوتاه و بی نویسهٔ ناامن، وگرنه خالی (همان قاعدهٔ `X-App`) */
    fun branchOf(v: String?): String =
      v?.trim()?.takeIf { s -> s.length in 1..64 && s.all { it.isLetterOrDigit() && it.code < 128 || it in "._-" } }.orEmpty()
  }
}
