package ir.vil3ntec.tohid.desktop.hub

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import ir.vil3ntec.tohid.core.hub.CloudAnswer
import ir.vil3ntec.tohid.core.hub.CloudCheck
import ir.vil3ntec.tohid.core.hub.HubAuth
import ir.vil3ntec.tohid.core.hub.HubBox
import ir.vil3ntec.tohid.core.hub.HubCloud
import ir.vil3ntec.tohid.core.hub.HubForwarder
import ir.vil3ntec.tohid.core.hub.HubLimiter
import ir.vil3ntec.tohid.core.hub.HubLimits
import ir.vil3ntec.tohid.core.hub.HubOffer
import ir.vil3ntec.tohid.core.hub.HubParse
import ir.vil3ntec.tohid.core.hub.HubPasses
import ir.vil3ntec.tohid.core.hub.HubQueue
import ir.vil3ntec.tohid.core.hub.HubQueueState
import ir.vil3ntec.tohid.core.hub.HubRoute
import ir.vil3ntec.tohid.core.hub.HubVerdict
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 *  SYN-09 (§۳.۸.۵) — هابِ محلیِ کامپیوترِ دکان: push ِ v3 ِ دستگاه‌های شبکهٔ دکان را می‌گیرد، در صفِ
 *  پایدار نگه می‌دارد و به ترتیب، با همان `op_id`ها، به سرور می‌دهد (`docs/SYN-09-HUB-fa.md`).
 *
 *  - ⛔ فقط شبکهٔ دکان: درخواستِ نشانیِ عمومی ۴۰۳ (نشانیِ خصوصی، link-local، ULA و همین کامپیوتر).
 *  - ⛔ فقط TLS با گواهیِ سنجاق‌شدهٔ خودِ هاب (`HubTls`)؛ http اصلاً گوش داده نمی‌شود.
 *  - ⛔ فقط دکانِ خودِ همین کامپیوتر: توکنِ دکانِ دیگر ۴۰۳ `hub_other_shop` و دسته‌اش هرگز به صف نمی‌رسد.
 *  - ⛔ هر درخواست توکن می‌خواهد (`HubAuth`)؛ هاب توکن را **فقط در حافظه** نگه می‌دارد تا دستهٔ همان دستگاه را
 *    با همان توکن بفرستد. روی دیسک فقط درهمِ توکن، صف و کلیدِ TLS — هر سه با `seal` (همان `SecretBox`).
 *  - ⛔ هر IP سقفِ درخواست دارد و سقفِ جدایی برای سنجشِ توکن نزدِ سرور (تا هاب ابزارِ کوبیدنِ سرور نشود).
 *  - ⚠️ پاسخِ ۲۰۰ ِ هاب «رسید به هاب» است، نه «رسید به سرور»: وضعیت‌ها `hub_queued`/`hub_settled`اند، نه
 *    وضعیت‌های سرور؛ دستگاه op را تا پاسخِ خودِ سرور نگه می‌دارد.
 */
internal class HubServer(
  private val dir: File,
  private val tls: HubTls,
  private val serverId: String,
  /** دکانِ حسابِ خودِ همین کامپیوتر — فقط توکنِ همین دکان پذیرفته است */
  private val shopId: String,
  private val cloud: HubCloud,
  private val seal: (String) -> String,
  private val open: (String) -> String?,
  private val clock: () -> Long = System::currentTimeMillis,
  /** آزمون‌ها `false` می‌دهند و `forwardNow` را خودشان صدا می‌زنند */
  private val autoForward: Boolean = true,
  private val limits: HubLimits = HubLimits(),
  private val requestsPerMinute: Int = 120,
  private val requestBurst: Int = 60,
) {
  private val lock = Any()
  private var queue: HubQueueState = loadQueue()
  private var passes: HubPasses = loadPasses()
  /** شناسهٔ دستگاه ⇒ توکنِ همین حالا. ⛔ هرگز روی دیسک */
  private val tokens = HashMap<String, String>()
  private val requests = HubLimiter(requestsPerMinute, requestBurst)
  private val verifies = HubLimiter(perMinute = 6, burst = 3)

  private var server: HttpsServer? = null
  private var pool: ExecutorService? = null
  private var forwarder: ScheduledExecutorService? = null
  private val kicked = AtomicBoolean(false)
  @Volatile private var attempt = 0
  /** پس از پاسخِ «بعداً» ِ سرور، تا این لحظه دوری شروع نمی‌شود */
  @Volatile private var notBefore = 0L

  private val box = object : HubBox {
    override fun read(): HubQueueState = synchronized(lock) { queue }
    override fun update(change: (HubQueueState) -> HubQueueState): HubQueueState = synchronized(lock) {
      val n = change(queue)
      if (n !== queue) {
        save(QUEUE_FILE, HubQueue.encode(n))
        queue = n
      }
      n
    }
  }
  private val forward = HubForwarder(box, cloud, limits)

  /** بدنهٔ درخواستِ جاری تا ته خوانده شد (`readLimited` ِ موفق)؟ هر exchange از `handle` تا `ex.close()` روی یک رشتهٔ `tohid-hub` است. */
  private val bodyRead: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

  val port: Int get() = server?.address?.port ?: -1

  fun start(bind: InetSocketAddress) {
    val s = HttpsServer.create(bind, 32)
    s.httpsConfigurator = object : HttpsConfigurator(tls.context) {
      override fun configure(params: HttpsParameters) {
        val p = tls.context.defaultSSLParameters
        p.protocols = arrayOf("TLSv1.3", "TLSv1.2")
        p.needClientAuth = false
        params.setSSLParameters(p)
      }
    }
    s.createContext("/") { ex -> handle(ex) }
    val workers = Executors.newFixedThreadPool(4) { r -> Thread(r, "tohid-hub").apply { isDaemon = true } }
    s.executor = workers
    s.start()
    server = s
    pool = workers
    if (autoForward) {
      val f = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "tohid-hub-forward").apply { isDaemon = true } }
      f.scheduleWithFixedDelay({ runCatching { box.update { HubQueue.expire(it, clock(), limits) } }; kick() }, 5, 30, TimeUnit.SECONDS)
      forwarder = f
    }
  }

  fun stop() {
    server?.stop(0)
    pool?.shutdownNow()
    forwarder?.shutdownNow()
    server = null
    pool = null
    forwarder = null
  }

  /** یک دورِ فرستادن، همین‌جا (آزمون و `kick`) */
  fun forwardNow(): HubForwarder.Pass {
    val pass = forward.drain(
      tokenOf = { d -> synchronized(lock) { tokens[d] } },
      onAuthFailed = { d, t ->
        synchronized(lock) {
          if (tokens[d] == t) tokens.remove(d)
          val n = HubAuth.revoke(passes, t)
          if (n != passes) { save(PASSES_FILE, HubAuth.encode(n)); passes = n }
        }
      },
    )
    attempt = if (pass.retry) attempt + 1 else 0
    notBefore = if (pass.retry) clock() + HubForwarder.backoffMs(attempt - 1) else 0L
    return pass
  }

  fun snapshot(): HubQueueState = box.read()

  private fun kick() {
    val f = forwarder ?: return
    if (clock() < notBefore || !kicked.compareAndSet(false, true)) return
    runCatching {
      f.execute {
        kicked.set(false)
        val pass = runCatching { forwardNow() }.getOrNull()
        if (pass?.retry == true) f.schedule({ kick() }, (notBefore - clock()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
      }
    }.onFailure { kicked.set(false) }
  }

  /* ------------------------------ درخواست ------------------------------ */

  private fun handle(ex: HttpExchange) {
    bodyRead.set(false)
    try {
      val remote = ex.remoteAddress?.address
      if (remote == null || !lanPeer(remote)) return reply(ex, 403, error("hub_lan_only"))
      val ip = remote.hostAddress
      if (!synchronized(lock) { requests.allow(ip, clock()) }) return reply(ex, 429, error("hub_rate_limited"), retryAfter = 10)
      val path = ex.requestURI.path
      val method = ex.requestMethod
      val isHello = method == "GET" && path == "/hub/v1/hello"
      val isPush = method == "POST" && path == "/hub/v1/push"
      if (!isHello && !isPush) return reply(ex, 404, error("hub_not_found"))

      val token = bearer(ex.requestHeaders.getFirst("Authorization"))
      val device = ex.requestHeaders.getFirst("X-Device")?.trim()
      val app = header(ex, "X-App")
      val snap = synchronized(lock) { passes }
      val decided = HubAuth.decide(snap, token, device, shopId, clock()) {
        if (synchronized(lock) { verifies.allow(ip, clock()) }) cloud.verify(token!!, device!!, app)
        else CloudAnswer(CloudCheck.UNREACHABLE)
      }
      synchronized(lock) {
        val n = HubAuth.apply(passes, decided.change, shopId)
        if (n != passes) { save(PASSES_FILE, HubAuth.encode(n)); passes = n }
      }
      val v = decided.verdict
      if (v is HubVerdict.Refuse) return reply(ex, v.reason.status, error(v.reason.code))
      synchronized(lock) { tokens[device!!] = token!! }

      if (isHello) {
        val queued = synchronized(lock) { HubQueue.queuedFor(queue, device!!) }
        if (queued > 0) kick()
        return reply(ex, 200, buildJsonObject {
          put("ok", true)
          put("hub", HubRoute.PROTOCOL)
          put("server_id", serverId)
          put("queued", queued)
          put("server_time", clock())
        }.toString())
      }
      push(ex, device!!, app)
    } catch (t: Throwable) {
      runCatching { reply(ex, 500, error("hub_error")) }
    } finally {
      bodyRead.remove()
      ex.close()
    }
  }

  private fun push(ex: HttpExchange, device: String, app: String) {
    if (ex.requestHeaders.getFirst("X-Server-Id")?.trim() != serverId) return reply(ex, 409, error("hub_other_server"))
    val declared = ex.requestHeaders.getFirst("Content-Length")?.trim()?.toLongOrNull()
    if (declared != null && declared > limits.maxBodyBytes) return reply(ex, 413, error("hub_too_large"))
    val bytes = readLimited(ex.requestBody, limits.maxBodyBytes) ?: return reply(ex, 413, error("hub_too_large"))
    bodyRead.set(true)
    val text = utf8(bytes) ?: return reply(ex, 400, error("hub_bad_json"))
    val ids = when (val p = HubQueue.parse(text, device, limits)) {
      is HubParse.Bad -> return reply(ex, 400, error(p.code))
      is HubParse.Ok -> p.opIds
    }
    val version = header(ex, "X-App-Version")
    val result = synchronized(lock) {
      val (n, r) = HubQueue.offer(queue, device, ids, text, app, version, clock(), limits)
      if (r is HubOffer.Accepted) {
        save(QUEUE_FILE, HubQueue.encode(n))
        queue = n
      }
      r
    }
    if (result is HubOffer.Full) return reply(ex, 503, error(result.code), retryAfter = 30)
    val (settled, queuedNow) = synchronized(lock) { queue.settled.toHashSet() to HubQueue.queuedFor(queue, device) }
    kick()
    reply(ex, 200, buildJsonObject {
      put("ok", true)
      put("hub", true)
      put("accepted", result is HubOffer.Accepted)
      put("queued", queuedNow)
      put("results", JsonArray(ids.map { id ->
        buildJsonObject {
          put("op_id", id)
          put("status", if (id in settled) "hub_settled" else "hub_queued")
        }
      }))
      put("server_time", clock())
    }.toString())
  }

  /* ------------------------------ کمکی ------------------------------ */

  private fun reply(ex: HttpExchange, status: Int, body: String, retryAfter: Int = 0) {
    val b = body.toByteArray(Charsets.UTF_8)
    ex.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    ex.responseHeaders.set("Cache-Control", "no-store")
    if (retryAfter > 0) ex.responseHeaders.set("Retry-After", retryAfter.toString())
    if (unreadBody(ex)) ex.responseHeaders.set("Connection", "close")
    ex.sendResponseHeaders(status, b.size.toLong())
    ex.responseBody.use { it.write(b) }
  }

  private fun error(code: String) = buildJsonObject { put("ok", false); put("error", code) }.toString()

  /**
   *  ⛔ پاسخ پیش از خواندنِ بدنه (۴۰۱/۴۰۳/۴۰۹/۴۱۳/۴۲۹، ۴۰۴ ِ POST و ۵۰۰) ⇒ `Connection: close`.
   *  `jdk.httpserver` باقیِ بدنه را **پس از** فرستادنِ پاسخ، در `ex.close()`، از همان اتصالِ TLS می‌خواند؛
   *  دستگاه همان لحظه درخواستِ بعدی را روی همان اتصالِ keep-alive می‌فرستد و آن خواندن رکوردِ TLS ِ درخواستِ
   *  بعدی را هم در بافرِ `SSLStreams` می‌کشد ⇒ سوکت دیگر «خواندنی» نمی‌شود و درخواستِ بعدی هرگز جواب نمی‌گیرد
   *  (`SocketTimeoutException: Read timed out` در دستگاه؛ روی ماشینِ پرکار در `HubServerTest` ِ ۴۰۳/۴۰۹/۴۱۳ دیده شد).
   *  بستنِ همان اتصال یعنی درخواستِ بعدی روی اتصالِ تازه؛ ۲۰۰ و ۴۰۰ ِ پس از خواندنِ کاملِ بدنه keep-alive می‌مانند.
   */
  private fun unreadBody(ex: HttpExchange): Boolean {
    if (bodyRead.get()) return false
    val h = ex.requestHeaders
    if (h.containsKey("Transfer-Encoding")) return true
    val len = h.getFirst("Content-Length") ?: return false
    return len.trim().toLongOrNull()?.let { it > 0 } ?: true
  }

  private fun bearer(h: String?): String? =
    h?.trim()?.takeIf { it.length > 7 && it.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true) }?.substring(7)?.trim()

  /** `X-App`/`X-App-Version`: کوتاه و بی نویسهٔ ناامن، وگرنه خالی */
  private fun header(ex: HttpExchange, name: String): String =
    ex.requestHeaders.getFirst(name)?.trim()?.takeIf { v ->
      v.length <= 32 && v.all { it.isLetterOrDigit() && it.code < 128 || it in "._-+" }
    }.orEmpty()

  private fun readLimited(input: InputStream, max: Int): ByteArray? {
    val buf = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
      val n = input.read(chunk)
      if (n < 0) break
      buf.write(chunk, 0, n)
      if (buf.size() > max) return null
    }
    return buf.toByteArray()
  }

  private fun utf8(b: ByteArray): String? = runCatching {
    Charsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(b)).toString()
  }.getOrNull()

  private fun save(name: String, text: String) = HubFiles.writeAtomic(File(dir, name), seal(text).toByteArray(Charsets.UTF_8))

  private fun loadText(name: String): String? {
    val f = File(dir, name)
    if (!f.isFile) return null
    return runCatching { open(f.readText(Charsets.UTF_8)) }.getOrNull()
  }

  /** خراب ⇒ کنار گذاشته و صفِ خالی (دستگاه‌ها هنوز opهایشان را دارند) */
  private fun loadQueue(): HubQueueState {
    dir.mkdirs()
    HubFiles.ownerOnly(dir)
    val f = File(dir, QUEUE_FILE)
    if (!f.isFile) return HubQueueState()
    return HubQueue.decode(loadText(QUEUE_FILE)) ?: HubQueueState().also { HubFiles.setAside(f, clock()) }
  }

  private fun loadPasses(): HubPasses {
    val f = File(dir, PASSES_FILE)
    if (!f.isFile) return HubPasses()
    return HubAuth.decode(loadText(PASSES_FILE)) ?: HubPasses().also { HubFiles.setAside(f, clock()) }
  }

  companion object {
    const val QUEUE_FILE = "hub-queue.sealed"
    const val PASSES_FILE = "hub-passes.sealed"

    /** همین کامپیوتر، نشانیِ خصوصیِ IPv4، link-local و ULA ِ IPv6 (fc00::/7) */
    fun lanPeer(a: InetAddress): Boolean =
      a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress ||
        (a is Inet6Address && (a.address[0].toInt() and 0xFE) == 0xFC)
  }
}

/** نوشتنِ اتمی و فقط-صاحب — همان الگوی دفترِ کامپیوتر: فایلِ موقت، fsync، جابه‌جاییِ اتمی */
internal object HubFiles {
  fun writeAtomic(f: File, bytes: ByteArray) {
    f.parentFile?.mkdirs()
    val tmp = File(f.parentFile, f.name + ".tmp")
    FileOutputStream(tmp).use { out ->
      out.write(bytes)
      out.fd.sync()
    }
    ownerOnly(tmp)
    Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }

  fun ownerOnly(f: File) {
    runCatching {
      f.setReadable(false, false); f.setReadable(true, true)
      f.setWritable(false, false); f.setWritable(true, true)
      if (f.isDirectory) { f.setExecutable(false, false); f.setExecutable(true, true) }
    }
  }

  fun setAside(f: File, now: Long) {
    runCatching { f.renameTo(File(f.parentFile, f.name + ".bad-$now")) }
  }
}
