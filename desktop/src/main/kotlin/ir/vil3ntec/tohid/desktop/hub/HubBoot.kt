package ir.vil3ntec.tohid.desktop.hub

import android.content.Context
import ir.vil3ntec.tohid.core.config.ApiConfig
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.hub.CloudAnswer
import ir.vil3ntec.tohid.core.hub.CloudCheck
import ir.vil3ntec.tohid.core.hub.HubBatch
import ir.vil3ntec.tohid.core.hub.HubCloud
import ir.vil3ntec.tohid.core.hub.HubEntry
import ir.vil3ntec.tohid.core.hub.HubPair
import ir.vil3ntec.tohid.core.hub.HubReply
import ir.vil3ntec.tohid.core.hub.HubRoute
import ir.vil3ntec.tohid.core.hub.HubV1Cloud
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.SecretBox
import ir.vil3ntec.tohid.platform.HubPanel
import ir.vil3ntec.tohid.platform.ServerSwitchAndroid
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  SYN-09 — روشن کردنِ هاب در شروعِ برنامهٔ کامپیوتر (`Main.kt`، پس از `applyPending`).
 *
 *  ⛔ پیش‌فرض خاموش، دو قفل: پرچمِ `app.multi_server` **و** تنظیمِ همین دستگاه `hub.local` (در همان
 *     فایلِ `tohid-servers`؛ همگام نمی‌شود). هر کدام خاموش ⇒ هیچ پورتی باز نمی‌شود، هیچ فایلی ساخته نمی‌شود.
 *  ⛔ سرورِ فعال باید `serverId` ِ سنجاق‌شده داشته باشد؛ بی آن هاب نمی‌تواند «همان سرور» را ثابت کند و روشن نمی‌شود.
 *  ⛔ همین کامپیوتر باید دکان داشته باشد (`LedgerOwner.currentShop`)؛ هاب فقط توکنِ همان دکان را می‌پذیرد.
 *  S29 (از `claude/e-rows-60h`): صفحهٔ «هاب» (`HubPanel`، کارتِ «هابِ شبکهٔ دکان» در «دستگاه‌ها»)، کشفِ mDNS
 *  (`HubAnnounce`)، جفت‌سازی با کد/QR و دفترِ v1 (`HubV1`). قاعدهٔ فایروالِ نصاب مانده است (`docs/SYN-09-HUB-fa.md` §۱۰).
 */
object HubBoot {
  const val SETTING: String = HubRoute.SETTING_ON
  const val PORT: Int = 47110
  /** همان `PREFS` ِ `ServerSwitchAndroid` */
  private const val SERVER_PREFS = "tohid-servers"

  @Volatile private var running: HubServer? = null
  @Volatile private var announce: HubAnnounce? = null
  @Volatile private var pin: String = ""

  /** شروعِ برنامه: پرچمِ بالا روشن ⇒ صفحهٔ هاب پیدا می‌شود؛ تنظیمِ `hub.local` هم روشن ⇒ هاب همین حالا */
  fun startIfEnabled(context: Context) {
    if (!ServerSwitchAndroid.enabled(context)) return
    HubPanel.port = DesktopHubPanel(context.applicationContext)
    if (!context.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE).getBoolean(SETTING, false)) return
    start(context)
  }

  /** @return `null` ⇒ روشن؛ وگرنه کدِ دلیل (`HubPanel.Why`) */
  @Synchronized
  fun start(context: Context): String? {
    if (!ServerSwitchAndroid.enabled(context)) return HubPanel.Why.FLAG_OFF
    val sid = ServerSwitchAndroid.activeProfile(context)?.serverId ?: return HubPanel.Why.NO_SERVER_ID
    val shop = LedgerOwner.currentShop(context).trim().ifEmpty { return HubPanel.Why.NO_SHOP }
    if (running != null) return null
    //  کلیدِ نشست همین حالا در دسترس نیست (کلیددانِ قفل، `SecretBox`) ⇒ هاب نه: کلیدِ TLS، توکن‌ها و صفش با رازی
    //  که پس از بستن نمی‌ماند رونویسی و برای همیشه گم می‌شدند. اجرای بعد که کلید برگشت، همان‌ها باز می‌شوند.
    if (!SecretBox.persistent()) return HubPanel.Why.NO_KEY
    //  درخواستِ کُند نخِ هاب را نگه ندارد (فقط وقتی هاب روشن است؛ پیش‌تر کسی تنظیمش نکرده باشد)
    if (System.getProperty("sun.net.httpserver.maxReqTime") == null) System.setProperty("sun.net.httpserver.maxReqTime", "30")
    if (System.getProperty("sun.net.httpserver.maxRspTime") == null) System.setProperty("sun.net.httpserver.maxRspTime", "30")
    val dir = File(DesktopContext.root, "hub")
    val tls = HubTls.loadOrCreate(dir, SecretBox::seal, SecretBox::open)
    val cloud = HubCloudHttp(base = { AppConfig.baseUrl(context) }, prepare = ServerSwitchAndroid::prepare)
    val server = HubServer(dir, tls, sid, shop, cloud, SecretBox::seal, SecretBox::open, v1Cloud = cloud)
    try {
      server.start(InetSocketAddress(PORT))
    } catch (_: java.io.IOException) {
      return HubPanel.Why.PORT_BUSY
    }
    running = server
    pin = tls.pin
    context.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE).edit().putBoolean(SETTING, true).apply()
    //  کشف: اگر درگاهِ ۵۳۵۳ گرفته است (سرویسِ دیگری)، هاب بی کشف هم کار می‌کند (QR نشانی دارد)
    val tag = tls.pin.take(4)
    announce = runCatching {
      HubAnnounce("Tohid Hub $tag", "tohid-hub-$tag", PORT, HubAnnounce::lanIpv4).also { it.start() }
    }.getOrNull()
    return null
  }

  @Synchronized
  fun stop(context: Context) {
    announce?.stop(); announce = null
    running?.stop(); running = null
    context.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE).edit().putBoolean(SETTING, false).apply()
  }

  internal fun current(): HubServer? = running

  /** صفحهٔ «هاب» ِ کامپیوتر — فقط همین فرایند؛ هیچ درِ شبکه‌ای به این‌ها نمی‌رسد */
  private class DesktopHubPanel(private val app: Context) : HubPanel.Port {
    override fun state(): HubPanel.State {
      val s = running
      val v = s?.v1?.status()
      val ip = HubAnnounce.lanIpv4().orEmpty()
      val url = if (ip.isEmpty()) "" else "https://$ip:$PORT"
      val ticket = v?.ticket
      return HubPanel.State(
        running = s != null,
        address = url,
        pin = if (s != null) pin else "",
        devices = v?.peers.orEmpty().map { p ->
          val seen = v?.seen?.get(p.deviceId)
          HubPanel.Device(p.deviceId, p.pairedAt, p.pairedBy, seen?.at, seen?.ip.orEmpty())
        },
        pending = v?.pending ?: 0,
        forwarded = v?.forwarded ?: 0,
        pairing = ticket?.let { t ->
          HubPanel.Pairing(t.code, if (url.isEmpty()) "" else HubPair.qr(url, s.serverId, pin, t.token), t.expiresAt)
        },
        discoverable = announce != null,
      )
    }

    override fun start(): String? = start(app)

    override fun stop() = stop(app)

    /** ⛔ فقط صاحب/مدیرِ همین کامپیوتر (`ShopRole.current`، کاربرِ صندوق هرگز بالاتر از حسابِ دستگاه) */
    override fun pair(): String? {
      val v = running?.v1 ?: return HubPanel.Why.NOT_RUNNING
      val by = runCatching { ir.vil3ntec.tohid.sync.SyncStore(app).accountName }.getOrDefault("")
      return when (val r = v.newTicket(ShopRole.current(app), by)) {
        is HubPair.Issue.Ok -> null
        is HubPair.Issue.Denied -> HubPanel.Why.ROLE
      }
    }

    override fun cancelPairing() { running?.v1?.cancelTicket() }

    override fun unpair(deviceId: String) {
      if (!HubPair.mayPair(ShopRole.current(app))) return
      running?.v1?.unpair(deviceId)
    }
  }
}

/**
 *  درگاهِ سرور برای هاب: همان مسیرهای `/sync/v3` ِ برنامه، با توکن و سرآیندهای **همان دستگاه** و همان
 *  سنجاقِ گواهیِ پروفایلِ فعال (`ServerSwitchAndroid.prepare`). بدنه همان بایت‌هایی است که دستگاه داد.
 */
internal class HubCloudHttp(
  private val base: () -> String,
  private val prepare: (HttpURLConnection, String) -> Unit = { _, _ -> },
) : HubCloud, HubV1Cloud {

  override fun push(batch: HubBatch, token: String): HubReply =
    call("POST", "/sync/v3/push", token, batch.deviceId, batch.app, batch.appVersion, batch.body)

  /** S29: همان `POST /sync/v1/push` ِ برنامه — همان بدنه، توکن، `X-Device`، `X-App-Version` و `X-Branch-Id` ِ دستگاه */
  override fun pushV1(entry: HubEntry, token: String): HubReply =
    call("POST", "/sync/v1/push", token, entry.deviceId, entry.app, entry.appVersion, entry.body, entry.branch)

  /**
   *  دو پرسش با همان توکن: `GET /sync/v3/hello` (نشستِ زنده، فقط دکان، پیوندِ دستگاه) و `GET /me` (`shop.id` ِ
   *  همان دکانی که نشست به آن بسته است — همان `membershipOf` ِ نشست که حسابِ v3 را هم می‌سازد).
   */
  override fun verify(token: String, deviceId: String, app: String): CloudAnswer {
    val r = call("GET", "/sync/v3/hello", token, deviceId, app, "", null).let { h ->
      //  ۴۰۴: v3 روی سرور خاموش است ⇒ همان سنجش با درِ v1 (S29: دفترِ v1 ِ هاب)
      if (h.status == 404) call("GET", "/sync/v1/status?device_id=" + java.net.URLEncoder.encode(deviceId, "UTF-8"), token, deviceId, app, "", null) else h
    }
    when (r.status) {
      200 -> Unit
      401, 403, 404 -> return CloudAnswer(CloudCheck.INVALID)
      else -> return CloudAnswer(CloudCheck.UNREACHABLE)
    }
    val me = call("GET", "/me", token, deviceId, app, "", null)
    return when (me.status) {
      200 -> shopOf(me.body)?.let { CloudAnswer(CloudCheck.VALID, it) } ?: CloudAnswer(CloudCheck.INVALID)
      401, 403 -> CloudAnswer(CloudCheck.INVALID)
      else -> CloudAnswer(CloudCheck.UNREACHABLE)
    }
  }

  private fun shopOf(body: String?): String? = runCatching {
    val shop = (Json.parseToJsonElement(body.orEmpty()) as JsonObject)["shop"] as? JsonObject
    //  شناسهٔ دکان رشته یا عدد (bigint ِ PostgreSQL)؛ null/بولی نه
    (shop?.get("id") as? JsonPrimitive)?.takeIf { it.isString || it.content.toLongOrNull() != null }?.content?.trim()?.takeIf { it.isNotEmpty() }
  }.getOrNull()

  private fun call(
    method: String, path: String, token: String, device: String, app: String, version: String, body: String?,
    branch: String = "",
  ): HubReply = try {
    val b = base()
    val c = URL(ApiConfig.urlOf(b, path)).openConnection() as HttpURLConnection
    prepare(c, b)
    c.requestMethod = method
    c.connectTimeout = ApiConfig.CONNECT_TIMEOUT_MS
    c.readTimeout = ApiConfig.READ_TIMEOUT_MS
    c.instanceFollowRedirects = false
    c.setRequestProperty("Authorization", "Bearer $token")
    c.setRequestProperty("X-Device", device)
    if (app.isNotEmpty()) c.setRequestProperty("X-App", app)
    if (version.isNotEmpty()) c.setRequestProperty("X-App-Version", version)
    if (branch.isNotEmpty()) c.setRequestProperty("X-Branch-Id", branch)
    c.setRequestProperty("Accept", "application/json")
    if (body != null) {
      val bytes = body.toByteArray(Charsets.UTF_8)
      c.doOutput = true
      c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
      c.setFixedLengthStreamingMode(bytes.size)
      c.outputStream.use { it.write(bytes) }
    }
    val status = c.responseCode
    val text = (if (status >= 400) c.errorStream else c.inputStream)?.let { readCapped(it, 4 * 1024 * 1024) }
    c.disconnect()
    HubReply(status, text)
  } catch (_: Exception) {
    HubReply(0, null)
  }

  private fun readCapped(input: InputStream, max: Int): String? = input.use { s ->
    val out = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
      val n = s.read(chunk)
      if (n < 0) break
      out.write(chunk, 0, n)
      if (out.size() > max) return null
    }
    out.toString(Charsets.UTF_8)
  }
}
