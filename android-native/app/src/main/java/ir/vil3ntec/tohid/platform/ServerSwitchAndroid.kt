package ir.vil3ntec.tohid.platform

import android.content.Context
import android.content.SharedPreferences
import ir.vil3ntec.tohid.core.config.ApiConfig
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.server.DataPartition
import ir.vil3ntec.tohid.core.server.ServerAddress
import ir.vil3ntec.tohid.core.server.ServerCompat
import ir.vil3ntec.tohid.core.server.ServerFailover
import ir.vil3ntec.tohid.core.server.ServerIdentity
import ir.vil3ntec.tohid.core.server.ServerInfo
import ir.vil3ntec.tohid.core.server.ServerProfile
import ir.vil3ntec.tohid.core.server.ServerProfiles
import ir.vil3ntec.tohid.core.server.ServerQr
import ir.vil3ntec.tohid.data.AccountVault
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.core.net.TokenStore
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 *  «هر سرور» روی گوشی و کامپیوتر — مرزِ سکو برای منطقِ `core/server` (پرچمِ دستگاهیِ `app.multi_server`،
 *  پیش‌فرض **خاموش**). شرح و قاعده‌ها: `docs/SERVERS-fa.md` و `docs/RULES-fa.md` بخشِ «🌐 هر سرور».
 *
 *  ⛔ پرچمِ خاموش ⇒ `activeUrl` همیشه `null` و `scope` همیشه خالی: `AppConfig` همان نشانیِ زمانِ ساخت را
 *  می‌دهد، `ApiConfig.reject` همان قاعدهٔ امروز است و نامِ بایگانیِ دفتر همان نامِ امروز.
 *
 *  ⛔ **عوض کردنِ سرور فقط در شروعِ فرآیند** (`applyPending`): صفحه فقط «سرورِ بعدی» را می‌نویسد و برنامه
 *  بسته می‌شود؛ بارِ بعد، پیش از هر دفتر و هر درخواستی، دفتر و عکس و نشست و حالِ همگام‌سازیِ این سرور
 *  بایگانی و مالِ آن سرور باز می‌شود. اگر وسطِ کار عوض می‌شد، درخواستی که از دفترِ سرورِ اول ساخته شده
 *  بود می‌توانست با نشانی و توکنِ سرورِ دوم برود — همان «قاطی شدنِ دفترها» که قفلِ نشانی برایش بود.
 *  هر درِ ورودِ فرآیند (`MainActivity`، `main` ِ کامپیوتر، کارگرهای پس‌زمینه) اول همین را صدا می‌زند.
 */
object ServerSwitchAndroid {

  private const val PREFS = "tohid-servers"
  private const val PROFILES = "profiles"
  private const val PENDING = "pending"
  private const val LOG = "log"
  private const val MAX_LOG = 50

  private fun prefs(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /* ------------------------------ پرچم و فهرست ------------------------------ */

  fun enabled(context: Context): Boolean = prefs(context).getBoolean(ServerProfiles.FLAG, false)

  @Volatile private var memo: Pair<String?, ServerProfiles>? = null

  fun profiles(context: Context): ServerProfiles {
    val raw = prefs(context).getString(PROFILES, null)
    memo?.let { (r, ps) -> if (r == raw && ps.default.primary == AppConfig.buildTimeBaseUrl) return ps }
    return ServerProfiles.decode(raw, AppConfig.buildTimeBaseUrl).also { memo = raw to it }
  }

  internal fun save(context: Context, ps: ServerProfiles) {
    val raw = ps.encode()
    prefs(context).edit().putString(PROFILES, raw).commit()
    memo = raw to ps
    refreshGate(context)
  }

  /** پروفایلِ فعال — `null` با پرچمِ خاموش */
  fun activeProfile(context: Context): ServerProfile? = if (enabled(context)) profiles(context).active else null

  /**
   *  نشانیِ همین حالا — `null` یعنی «همان نشانیِ زمانِ ساخت» (پرچمِ خاموش، یا سرورِ پیش‌فرض بی نشانیِ دوم).
   *  ⛔ هرگز به شبکه نمی‌رود (از رشتهٔ اصلی هم صدا زده می‌شود)؛ سنجشِ دوباره در پس‌زمینه است.
   */
  fun activeUrl(context: Context): String? {
    val p = activeProfile(context) ?: run { gate = null; return null }
    if (gate?.profile != p) refreshGate(context)
    if (p.isDefault && p.urls.size == 1) return null
    val chosen = prefs(context).getString("chosen:${p.id}", null)?.takeIf { it in p.urls } ?: p.primary
    val stale = nowMs() - prefs(context).getLong("chosenAt:${p.id}", 0) > ServerFailover.RECHECK_MS
    if (stale || forceRecheck) { forceRecheck = false; recheckSoon(context) }
    return chosen
  }

  /** دامنهٔ دادهٔ محلی — خالی با پرچمِ خاموش یا سرورِ پیش‌فرض */
  fun scope(context: Context): String = activeProfile(context)?.let { DataPartition.scope(it) }.orEmpty()

  /** کلیدِ حالِ همگام‌سازی/صفِ این سرور — با دامنهٔ خالی همان کلیدِ امروز */
  fun scopedKey(context: Context, raw: String): String {
    val s = scope(context)
    return if (s.isEmpty()) raw else DataPartition.keyIn(s, raw, "")
  }

  fun blocked(context: Context, id: String): Boolean = prefs(context).getBoolean("blocked:$id", false)

  /* ------------------------------ درِ شبکه (HttpEngine و سوکت) ------------------------------ */

  /** `wire`: پیامِ «قراردادِ برنامه و سرور نمی‌خواند» از آخرین سنجش — فقط سرورِ غیرِپیش‌فرض */
  private class Gate(val profile: ServerProfile, val blocked: Boolean, val wire: String?)

  @Volatile private var gate: Gate? = null

  private fun refreshGate(context: Context) {
    val p = activeProfile(context)
    gate = p?.let {
      Gate(it, !it.isDefault && blocked(context, it.id),
        if (it.isDefault) null else prefs(context).getString("wire:${it.id}", null))
    }
  }

  /**
   *  قاعدهٔ نشانیِ هر درخواست. نشانیِ زمانِ ساخت (و هر نشانیِ بیرون از پروفایلِ فعال) همان
   *  `ApiConfig.reject` ِ امروز؛ نشانیِ پروفایل قاعدهٔ `ServerAddress` و بستنِ سرورِ عوض‌شده.
   */
  fun reject(base: String, allowInsecure: Boolean): ApiFailure? {
    val g = gate
    val b = ServerAddress.normalize(base)
    if (g == null || b == ServerAddress.normalize(AppConfig.buildTimeBaseUrl) || b !in g.profile.urls) {
      return ApiConfig.reject(base, allowInsecure)?.let { reason ->
        if (reason == ApiConfig.Rejection.MISSING) ApiFailure.NotConfigured() else ApiFailure.BadConfiguration(reason.message)
      }
    }
    if (g.blocked) return ApiFailure.BadConfiguration(ServerIdentity.MISMATCH_MESSAGE)
    g.wire?.let { return ApiFailure.BadConfiguration(it) }
    return when (val v = ServerAddress.check(b, g.profile.lan, allowInsecure)) {
      is ServerAddress.Verdict.Ok -> null
      is ServerAddress.Verdict.Rejected ->
        if (v.reason == ServerAddress.Rejection.MISSING) ApiFailure.NotConfigured() else ApiFailure.BadConfiguration(v.reason.message)
    }
  }

  /** گواهیِ سنجاق‌شدهٔ همین نشانی (شبکهٔ دکان) روی این اتصال */
  fun prepare(connection: HttpURLConnection, base: String) {
    val pin = gate?.profile?.certPinOf(base) ?: return
    (connection as? HttpsURLConnection)?.let { PinnedTls.of(pin).applyTo(it) }
  }

  /** همان، برای سوکتِ زنده (OkHttp) */
  fun okHttp(client: OkHttpClient, base: String): OkHttpClient {
    val pin = gate?.profile?.certPinOf(base) ?: return client
    val tls = PinnedTls.of(pin)
    return client.newBuilder().sslSocketFactory(tls.factory, tls.trust).hostnameVerifier(tls.verifier).build()
  }

  /**
   *  درخواستی به نشانیِ پروفایل نرسید ⇒ درخواستِ بعدی نشانی‌ها را از نو می‌سنجد (شبکهٔ دکان ⇐ اینترنت).
   *  حداکثر هر ۳۰ ثانیه یک بار، تا سرورِ خاموش سنجشِ پشتِ سرِ هم نسازد.
   */
  fun reportFailure(base: String) {
    val g = gate ?: return
    if (ServerAddress.normalize(base) !in g.profile.urls || g.profile.urls.size < 2) return
    val now = nowMs()
    if (now - lastForced < 30_000) return
    lastForced = now
    forceRecheck = true
  }

  /* ------------------------------ سنجشِ پس‌زمینه ------------------------------ */

  @Volatile private var checking = false
  @Volatile private var forceRecheck = false
  @Volatile private var lastForced = 0L

  private fun recheckSoon(context: Context) {
    if (checking) return
    checking = true
    val app = context.applicationContext
    Thread {
      try { recheck(app) } catch (_: Throwable) { } finally { checking = false }
    }.apply { isDaemon = true; name = "tohid-server-check" }.start()
  }

  /** همهٔ نشانی‌های پروفایلِ فعال به ترتیبِ «شبکهٔ دکان، بعد اینترنت»؛ نخستین که همان سرور است */
  internal fun recheck(context: Context) {
    val p = activeProfile(context) ?: return
    val p0 = prefs(context)
    val order = ServerFailover.order(p.urls, p0.getString("chosen:${p.id}", null))
    val probes = ArrayList<ServerFailover.Probe>()
    var chosen: ServerFailover.Pick.Chosen? = null
    for (u in order) {
      val r = probe(u, p.certPinOf(u), ServerFailover.timeoutOf(u))
      val probe = ServerFailover.Probe(u, r.info)
      probes += probe
      //  سرورِ پیش‌فرض: نشانیِ اصلی‌اش در خودِ نسخه است و سنجاقش اجباری نیست (صاحب محصول سرورش را جابه‌جا می‌کند)
      val primaryOfDefault = p.isDefault && ServerAddress.normalize(u) == p.primary && r.info != null
      if (primaryOfDefault || ServerFailover.judge(p, probe) == ServerFailover.Judgement.ACCEPT) {
        chosen = ServerFailover.Pick.Chosen(ServerAddress.normalize(u), r.info!!)
        break
      }
    }
    val e = p0.edit().putLong("chosenAt:${p.id}", nowMs())
    val pick = chosen ?: ServerFailover.pick(p, probes)
    when (pick) {
      is ServerFailover.Pick.Chosen -> {
        e.putString("chosen:${p.id}", pick.url).putBoolean("blocked:${p.id}", false)
        //  سرور به‌روز شد و قراردادش دیگر با این نسخه نمی‌خواند ⇒ پیامِ روشن به‌جای خطاهای پراکنده
        val wire = ServerCompat.check(pick.info).message
        if (wire != null) e.putString("wire:${p.id}", wire) else e.remove("wire:${p.id}")
        //  سرورِ پیش‌فرض شناسه‌اش را خودش می‌گیرد (نشانی‌اش در خودِ نسخه است) — برای نشانیِ دومش
        if (p.isDefault && pick.info.serverId != null && pick.info.serverId != p.serverId &&
          ServerAddress.normalize(pick.url) == p.primary) {
          (profiles(context).update(p.copy(serverId = pick.info.serverId, lastInfo = pick.info, checkedAt = nowMs()))
            as? ServerProfiles.Change.Ok)?.let { save(context, it.profiles) }
        }
      }
      is ServerFailover.Pick.None ->
        //  ⛔ سرورِ دیگری پشتِ نشانی‌های این پروفایل و هیچ نشانیِ سالمی نیست ⇒ بسته (پیش‌فرض نه: نشانی‌اش در خودِ نسخه است)
        if (pick.mismatched.isNotEmpty() && !p.isDefault) e.putBoolean("blocked:${p.id}", true)
    }
    e.commit()
    refreshGate(context)
  }

  /* ------------------------------ یک نشانی را بپرس ------------------------------ */

  data class Probe(
    val url: String,
    val info: ServerInfo?,
    /** گواهیِ خودامضای سنجاق‌نشده: اثرِ انگشتش برای تأیید */
    val untrustedCert: String? = null,
    val error: String? = null,
  )

  /** فقط از رشتهٔ پس‌زمینه */
  fun probe(url: String, pin: String?, timeoutMs: Int): Probe {
    val base = ServerAddress.normalize(url)
    return try {
      val (code, body) = get(ApiConfig.urlOf(base, "/config/server"), pin, timeoutMs)
      val info = when {
        code in 200..299 -> ServerInfo.parse(body)
        //  سرورِ قدیمی: این مسیر را ندارد
        code == 404 || code == 401 -> get(ApiConfig.urlOf(base, "/health"), pin, timeoutMs)
          .let { (c, b) -> if (c in 200..299) ServerInfo.fromHealth(b) else null }
        else -> null
      }
      Probe(base, info, error = if (info == null) "این نشانی سرورِ این برنامه نیست" else null)
    } catch (e: javax.net.ssl.SSLException) {
      if (pin != null) Probe(base, null, error = "گواهیِ این سرور با گواهیِ سنجاق‌شده نمی‌خواند")
      else Probe(base, null, untrustedCert = PinnedTls.capture(base, timeoutMs), error = "گواهیِ این سرور شناخته نیست")
    } catch (e: Throwable) {
      Probe(base, null, error = "به این نشانی نرسید")
    }
  }

  private fun get(url: String, pin: String?, timeoutMs: Int): Pair<Int, String> {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
      if (pin != null) (c as? HttpsURLConnection)?.let { PinnedTls.of(pin).applyTo(it) }
      c.connectTimeout = timeoutMs
      c.readTimeout = timeoutMs * 2
      c.instanceFollowRedirects = false
      c.setRequestProperty("Accept", "application/json")
      c.setRequestProperty("X-App-Id", AppConfig.appId)
      val code = c.responseCode
      val stream = if (code in 200..299) c.inputStream else c.errorStream
      //  سقفِ ۶۴ کیلوبایت: پاسخِ سرورِ ناشناس نباید حافظه را پر کند
      val body = stream?.use { s ->
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (out.size() < 65_536) {
          val n = s.read(chunk)
          if (n < 0) break
          out.write(chunk, 0, minOf(n, 65_536 - out.size()))
        }
        String(out.toByteArray(), Charsets.UTF_8)
      }.orEmpty()
      return code to body
    } finally { runCatching { c.disconnect() } }
  }

  /* ------------------------------ سرورِ بعدی، در شروعِ فرآیند ------------------------------ */

  private val applyLock = Any()
  @Volatile private var applied = false

  /** «سرورِ بعدی» را می‌نویسد؛ صدازننده بعد برنامه را می‌بندد (`restartNow`) */
  internal fun schedule(context: Context, id: String) {
    prefs(context).edit().putString(PENDING, id).commit()
  }

  /**
   *  هر درِ ورودِ فرآیند، پیش از هر دفتر و درخواستی. بی «سرورِ بعدی» و با پرچمِ خاموش و سرورِ پیش‌فرض،
   *  فقط یک خواندنِ حافظه است.
   */
  fun applyPending(context: Context) {
    if (applied) return
    synchronized(applyLock) {
      if (applied) return
      applied = true
      runCatching { applyNow(context.applicationContext) }
    }
  }

  private fun applyNow(app: Context) {
    val p0 = prefs(app)
    val ps = profiles(app)
    //  پرچمِ خاموش یعنی «فقط سرورِ زمانِ ساخت»؛ اگر پیش‌تر سرورِ دیگری فعال بود، برمی‌گردیم
    val target = if (!enabled(app)) ServerProfiles.DEFAULT_ID else p0.getString(PENDING, null)
    if (target == null || target == ps.activeId || ps.byId(target) == null) {
      if (p0.contains(PENDING)) p0.edit().remove(PENDING).commit()
      return
    }
    val from = ps.active
    val to = ps.byId(target)!!
    val store = ShopStore(app)
    runBlocking {
      withContext(Dispatchers.IO) {
        //  ۱) آنچه روی میز است، زیرِ نامِ همین سرور (و همین حساب@دکان)
        val user = LedgerOwner.currentUser(app)
        val shop = LedgerOwner.currentShop(app)
        val fromKey = if (user.isBlank()) DataPartition.deskKey(from) else DataPartition.ledgerKey(from, user, shop)
        p0.edit().putString("owner:${from.id}", "$user|$shop").commit()
        val (nextUser, nextShop) = (p0.getString("owner:${to.id}", null) ?: "|").split('|', limit = 2)
          .let { it[0] to it.getOrElse(1) { "" } }
        val toKey = if (nextUser.isBlank()) DataPartition.deskKey(to) else DataPartition.ledgerKey(to, nextUser, nextShop)

        store.stashTo(fromKey)
        runCatching { PhotoStore.stashTo(app, fromKey) }
        runCatching { AccountVault.switch(app, fromKey, toKey) }
        TokenStore(app).let { t -> t.stashTo(DataPartition.scope(from)); t.openFrom(DataPartition.scope(to)) }

        //  ۲) سرورِ تازه فعال — پیش از باز کردنِ دفترش، تا هر نامی که از این‌جا ساخته می‌شود مالِ آن باشد
        val activated = (ps.activate(to.id) as ServerProfiles.Change.Ok).profiles
        save(app, activated)
        p0.edit().remove(PENDING).remove("chosen:${to.id}").putLong("chosenAt:${to.id}", 0).commit()

        //  ۳) دفتر و عکس و حالِ حسابِ آن سرور
        store.openFrom(toKey)
        runCatching { PhotoStore.openFrom(app, toKey) }
        SyncStore(app).forgetAccountState()
        SyncStateV1.setCurrentAccount(app, nextUser)
        runCatching { ShopRole.forget(app) }
        runCatching { CartStore(app).clear() }
        LedgerOwner.remember(app, nextUser, nextShop)
        //  پیشوندِ API هر سرور جداست — دوباره پیدا می‌شود
        app.getSharedPreferences("tohid-api", Context.MODE_PRIVATE).edit().remove("api-prefix").commit()
      }
    }
    //  کلیدِ صف و حالِ همگام‌سازی از دامنهٔ تازه (موتور شاید هنگامِ بایگانی با کلیدِ کهنه ساخته شده بود)
    runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(app).rebind() }
    log(app, "switch", to, "از «${from.name}»")
  }

  /** بستنِ برنامه پس از «سرورِ بعدی» — بارِ بعد، پیش از هر کاری، عوض می‌شود */
  fun restartNow(): Nothing = kotlin.system.exitProcess(0)

  /* ------------------------------ سابقه ------------------------------ */

  data class LogEntry(val at: Long, val action: String, val name: String, val fingerprint: String, val note: String)

  internal fun log(context: Context, action: String, p: ServerProfile, note: String = "") {
    val p0 = prefs(context)
    val arr = runCatching { JSONArray(p0.getString(LOG, "[]") ?: "[]") }.getOrDefault(JSONArray())
    val entry = JSONObject()
      .put("at", nowMs()).put("action", action).put("name", p.name)
      .put("fp", p.serverId?.let { ServerIdentity.fingerprint(it) }.orEmpty()).put("note", note)
      .put("role", ShopRole.current(context))
    val out = JSONArray().put(entry)
    for (i in 0 until minOf(arr.length(), MAX_LOG - 1)) out.put(arr.get(i))
    p0.edit().putString(LOG, out.toString()).apply()
  }

  fun logEntries(context: Context): List<LogEntry> = runCatching {
    val arr = JSONArray(prefs(context).getString(LOG, "[]") ?: "[]")
    (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i)
      LogEntry(o.optLong("at"), o.optString("action"), o.optString("name"), o.optString("fp"), o.optString("note"))
    }
  }.getOrDefault(emptyList())

  internal fun setEnabledRaw(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(ServerProfiles.FLAG, on).commit()
    memo = null
    refreshGate(context)
  }

  internal fun nowMs(): Long = System.currentTimeMillis()
}

/**
 *  گواهیِ سنجاق‌شده — فقط **همان** گواهی (SHA-256 ِ DER ِ گواهیِ برگ)، به‌جای مرجع‌های عمومی.
 *  برای سرورِ شبکهٔ دکان با گواهیِ خودامضا. نام‌میزبان هم با همان سنجاق سنجیده می‌شود (گواهیِ
 *  خودامضا معمولاً نامِ IP را ندارد).
 */
internal class PinnedTls private constructor(private val pin: String) {

  val trust: X509TrustManager = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
      throw CertificateException("client certificates are not used")

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
      val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
      if (!same(sha256Hex(leaf.encoded), pin)) throw CertificateException("certificate pin mismatch")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
  }

  val factory: SSLSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }.socketFactory

  val verifier = HostnameVerifier { _, session ->
    runCatching { same(sha256Hex(session.peerCertificates.first().encoded), pin) }.getOrDefault(false)
  }

  fun applyTo(c: HttpsURLConnection) {
    c.sslSocketFactory = factory
    c.hostnameVerifier = verifier
  }

  companion object {
    private val cache = HashMap<String, PinnedTls>()

    fun of(pin: String): PinnedTls = synchronized(cache) { cache.getOrPut(pin) { PinnedTls(pin) } }

    fun sha256Hex(der: ByteArray): String =
      MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { b -> "%02x".format(b) }

    private fun same(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    /**
     *  اثرِ انگشتِ گواهیِ ناشناس، برای نمایش و تأیید (اعتماد در نخستین استفاده). دست‌دادن پس از
     *  خواندنِ گواهی **همان‌جا شکسته می‌شود**: هیچ درخواستی، هیچ توکنی، هیچ داده‌ای نمی‌رود.
     */
    fun capture(url: String, timeoutMs: Int): String? {
      var seen: String? = null
      val recorder = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
          throw CertificateException("no")

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
          seen = chain?.firstOrNull()?.let { sha256Hex(it.encoded) }
          throw CertificateException("captured")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
      }
      return runCatching {
        val c = URL(ApiConfig.urlOf(url, "/health")).openConnection() as? HttpsURLConnection ?: return null
        c.sslSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(recorder), null) }.socketFactory
        c.hostnameVerifier = HostnameVerifier { _, _ -> false }
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        try { c.connect() } catch (_: Throwable) { } finally { runCatching { c.disconnect() } }
        seen
      }.getOrNull()
    }
  }
}

/**
 *  آنچه صفحهٔ «سرورها» لازم دارد — تا خودِ صفحه هیچ `Context`ی نگیرد (قاعدهٔ آیفون، `RULES-fa.md`).
 *  هر تغییر فقط کارِ صاحبِ دکان است (نقشِ `owner`؛ بی حساب هم صاحب)، با رمزِ برنامه اگر روشن است، و
 *  در سابقه می‌ماند.
 */
class ServersController(context: Context) {

  private val app = context.applicationContext

  val canManage: Boolean get() = ShopRole.current(app) == ShopRole.OWNER
  val pinRequired: Boolean get() = runCatching { LockStore(app).enabled }.getOrDefault(false)
  fun pinOk(pin: String): Boolean = runCatching { LockStore(app).matches(pin) }.getOrDefault(false)

  fun enabled(): Boolean = ServerSwitchAndroid.enabled(app)
  fun profiles(): ServerProfiles = ServerSwitchAndroid.profiles(app)
  fun blocked(id: String): Boolean = ServerSwitchAndroid.blocked(app, id)
  fun log(): List<ServerSwitchAndroid.LogEntry> = ServerSwitchAndroid.logEntries(app)
  fun fingerprintOf(p: ServerProfile): String = p.serverId?.let { ServerIdentity.fingerprint(it) }.orEmpty()

  /** روشن ⇒ فقط بخش باز می‌شود. خاموش با سرورِ دیگرِ فعال ⇒ `true`: برنامه باید بسته شود تا به پیش‌فرض برگردد */
  fun setEnabled(on: Boolean): Boolean {
    if (!canManage) return false
    val ps = ServerSwitchAndroid.profiles(app)
    ServerSwitchAndroid.setEnabledRaw(app, on)
    ServerSwitchAndroid.log(app, if (on) "flag_on" else "flag_off", ps.active)
    return !on && ps.activeId != ServerProfiles.DEFAULT_ID
  }

  /** آنچه پس از «بررسی» برای تأیید نشان داده می‌شود */
  data class Candidate(
    val urls: List<String>,
    val name: String,
    val info: ServerInfo,
    val lan: Boolean,
    val certPins: Map<String, String>,
    /** نشانیِ دومِ پروفایلی که همین سرور است */
    val alternateOf: ServerProfile?,
    /** اثرِ انگشتِ گواهیِ خودامضا که تازه دیده شد (نه از کیو‌آر) — باید با چشم سنجیده شود */
    val newCertFingerprint: String?,
  ) {
    val fingerprint: String get() = info.fingerprint
  }

  sealed class Test {
    data class Ready(val candidate: Candidate) : Test()
    data class Failed(val message: String) : Test()
    /** گواهیِ خودامضای ناشناس: با تأییدِ اثرِ انگشت دوباره بررسی می‌شود */
    data class NeedsCertTrust(val url: String, val fingerprint: String) : Test()
  }

  /**
   *  نشانی یا کیو‌آر ⇒ بررسیِ واقعی. `trustCert`: اثرِ انگشتی که صاحبِ دکان پس از `NeedsCertTrust` تأیید کرد.
   *  فقط از رشتهٔ پس‌زمینه.
   */
  suspend fun test(input: String, lan: Boolean, trustCert: String? = null): Test = withContext(Dispatchers.IO) {
    val qr = ServerQr.decode(input)
    val urls: List<String> = when (qr) {
      is ServerQr.Read.Bad -> return@withContext Test.Failed(qr.message)
      is ServerQr.Read.Ok -> qr.payload.urls
      ServerQr.Read.NotServerQr -> listOf(ServerAddress.normalize(input))
    }
    val fromQr = (qr as? ServerQr.Read.Ok)?.payload
    val pins: Map<String, String> = fromQr?.certPins.orEmpty()
    val qrId: String? = fromQr?.serverId
    val qrName: String = fromQr?.name.orEmpty()
    //  ⛔ کلیدِ «شبکهٔ محلی» فقط با دستِ صاحبِ دکان — کیو‌آر خودش روشنش نمی‌کند
    val useLan = lan
    for (u in urls) {
      val v = ServerAddress.check(u, useLan, AppConfig.allowInsecure)
      if (v is ServerAddress.Verdict.Rejected) return@withContext Test.Failed(v.reason.message)
    }
    val primary = urls.first()
    val pin = pins[primary] ?: trustCert?.let { ServerProfile.normalizeCertPin(it) }
    val r = ServerSwitchAndroid.probe(primary, pin, ServerFailover.PUBLIC_TIMEOUT_MS * 2)
    if (r.untrustedCert != null) return@withContext Test.NeedsCertTrust(primary, r.untrustedCert)
    val info = r.info ?: return@withContext Test.Failed(r.error ?: "به این نشانی نرسید")
    ServerCompat.check(info).message?.let { return@withContext Test.Failed(it) }
    if (qrId != null && info.serverId != qrId) {
      return@withContext Test.Failed("شناسهٔ این سرور با کیو‌آر نمی‌خواند — کیو‌آر مالِ سرورِ دیگری است")
    }
    var ps = ServerSwitchAndroid.profiles(app)
    //  شناسهٔ سرورِ پیش‌فرض هنوز دیده نشده ⇒ همین حالا، تا نشانیِ دومِ همان سرور پروفایلِ جدا (و دفترِ دوم) نسازد
    if (ps.default.serverId == null) {
      val d = ServerSwitchAndroid.probe(ps.default.primary, null, ServerFailover.PUBLIC_TIMEOUT_MS)
      val id = d.info?.serverId
      if (id != null) (ps.update(ps.default.copy(serverId = id, lastInfo = d.info)) as? ServerProfiles.Change.Ok)?.let {
        ServerSwitchAndroid.save(app, it.profiles)
        ps = it.profiles
      }
    }
    val same = info.serverId?.let { id -> ps.profiles.firstOrNull { it.serverId == id } }
    val allPins = if (pin != null) pins + (primary to pin) else pins
    Test.Ready(Candidate(
      urls = urls, name = qrName.ifEmpty { info.name }, info = info, lan = useLan, certPins = allPins,
      alternateOf = same, newCertFingerprint = if (trustCert != null && pins[primary] == null) pin else null,
    ))
  }

  /** افزودن پس از تأیید — یا `null`، یا پیامِ خطا */
  fun add(c: Candidate): String? {
    if (!canManage) return "فقط صاحبِ دکان سرور می‌افزاید"
    val ps = ServerSwitchAndroid.profiles(app)
    val change = if (c.alternateOf != null) {
      var cur: ServerProfiles.Change = ServerProfiles.Change.Ok(ps)
      for (u in c.urls) {
        val okPs = (cur as? ServerProfiles.Change.Ok)?.profiles ?: break
        cur = okPs.addAlternate(c.alternateOf.id, u, c.certPins[u])
      }
      (cur as? ServerProfiles.Change.Ok)?.let { ok ->
        val p = ok.profiles.byId(c.alternateOf.id)!!
        ok.profiles.update(p.copy(lan = p.lan || c.lan))
      } ?: cur
    } else {
      ps.add(ServerProfile(
        id = "p" + ServerSwitchAndroid.nowMs().toString(36),
        name = c.name, urls = c.urls, serverId = c.info.serverId, certPins = c.certPins, lan = c.lan,
        lastInfo = c.info, checkedAt = ServerSwitchAndroid.nowMs(), addedAt = ServerSwitchAndroid.nowMs(),
      ))
    }
    return when (change) {
      is ServerProfiles.Change.Refused -> change.problem.message
      is ServerProfiles.Change.Ok -> {
        ServerSwitchAndroid.save(app, change.profiles)
        val target = c.alternateOf?.let { change.profiles.byId(it.id) } ?: change.profiles.profiles.last()
        ServerSwitchAndroid.log(app, if (c.alternateOf != null) "add_url" else "add", target, c.urls.joinToString(" "))
        null
      }
    }
  }

  fun remove(id: String): String? {
    if (!canManage) return "فقط صاحبِ دکان سرور برمی‌دارد"
    val ps = ServerSwitchAndroid.profiles(app)
    val p = ps.byId(id) ?: return ServerProfiles.Problem.NOT_FOUND.message
    return when (val ch = ps.remove(id)) {
      is ServerProfiles.Change.Refused -> ch.problem.message
      is ServerProfiles.Change.Ok -> { ServerSwitchAndroid.save(app, ch.profiles); ServerSwitchAndroid.log(app, "remove", p); null }
    }
  }

  /** «سرورِ بعدی» — پس از آن برنامه بسته می‌شود (`restartNow`) */
  fun switchTo(id: String): String? {
    if (!canManage) return "فقط صاحبِ دکان سرور را عوض می‌کند"
    if (runCatching { ir.vil3ntec.tohid.sync.v3.SyncChoice.isV3(app) }.getOrDefault(false)) {
      return "همگام‌سازیِ نسخهٔ ۳ روشن است؛ پیش از عوض کردنِ سرور آن را خاموش کنید"
    }
    val ps = ServerSwitchAndroid.profiles(app)
    val p = ps.byId(id) ?: return ServerProfiles.Problem.NOT_FOUND.message
    if (p.id == ps.activeId) return null
    if (ServerSwitchAndroid.blocked(app, p.id)) return ServerIdentity.MISMATCH_MESSAGE
    ServerSwitchAndroid.schedule(app, p.id)
    ServerSwitchAndroid.log(app, "schedule", p, "از «${ps.active.name}»")
    return null
  }

  fun restartNow(): Nothing = ServerSwitchAndroid.restartNow()
}
