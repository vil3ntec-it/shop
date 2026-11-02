package ir.vil3ntec.tohid.desktop.hub

import android.content.Context
import ir.vil3ntec.tohid.core.config.ApiConfig
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.hub.CloudAnswer
import ir.vil3ntec.tohid.core.hub.CloudCheck
import ir.vil3ntec.tohid.core.hub.HubBatch
import ir.vil3ntec.tohid.core.hub.HubCloud
import ir.vil3ntec.tohid.core.hub.HubReply
import ir.vil3ntec.tohid.core.hub.HubRoute
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.SecretBox
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
 *  ⚠️ این برش صفحه ندارد؛ کشف با mDNS، جفت‌سازیِ شش‌رقمی و قاعدهٔ فایروالِ نصاب مانده‌اند (`docs/SYN-09-HUB-fa.md` §۸).
 */
object HubBoot {
  const val SETTING: String = HubRoute.SETTING_ON
  const val PORT: Int = 47110
  /** همان `PREFS` ِ `ServerSwitchAndroid` */
  private const val SERVER_PREFS = "tohid-servers"

  @Volatile private var running: HubServer? = null

  fun startIfEnabled(context: Context) {
    if (!ServerSwitchAndroid.enabled(context)) return
    if (!context.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE).getBoolean(SETTING, false)) return
    val sid = ServerSwitchAndroid.activeProfile(context)?.serverId ?: return
    val shop = LedgerOwner.currentShop(context).trim().ifEmpty { return }
    if (running != null) return
    //  درخواستِ کُند نخِ هاب را نگه ندارد (فقط وقتی هاب روشن است؛ پیش‌تر کسی تنظیمش نکرده باشد)
    if (System.getProperty("sun.net.httpserver.maxReqTime") == null) System.setProperty("sun.net.httpserver.maxReqTime", "30")
    if (System.getProperty("sun.net.httpserver.maxRspTime") == null) System.setProperty("sun.net.httpserver.maxRspTime", "30")
    val dir = File(DesktopContext.root, "hub")
    val tls = HubTls.loadOrCreate(dir, SecretBox::seal, SecretBox::open)
    val cloud = HubCloudHttp(base = { AppConfig.baseUrl(context) }, prepare = ServerSwitchAndroid::prepare)
    running = HubServer(dir, tls, sid, shop, cloud, SecretBox::seal, SecretBox::open).also { it.start(InetSocketAddress(PORT)) }
  }
}

/**
 *  درگاهِ سرور برای هاب: همان مسیرهای `/sync/v3` ِ برنامه، با توکن و سرآیندهای **همان دستگاه** و همان
 *  سنجاقِ گواهیِ پروفایلِ فعال (`ServerSwitchAndroid.prepare`). بدنه همان بایت‌هایی است که دستگاه داد.
 */
internal class HubCloudHttp(
  private val base: () -> String,
  private val prepare: (HttpURLConnection, String) -> Unit = { _, _ -> },
) : HubCloud {

  override fun push(batch: HubBatch, token: String): HubReply =
    call("POST", "/sync/v3/push", token, batch.deviceId, batch.app, batch.appVersion, batch.body)

  /**
   *  دو پرسش با همان توکن: `GET /sync/v3/hello` (نشستِ زنده، فقط دکان، پیوندِ دستگاه) و `GET /me` (`shop.id` ِ
   *  همان دکانی که نشست به آن بسته است — همان `membershipOf` ِ نشست که حسابِ v3 را هم می‌سازد).
   */
  override fun verify(token: String, deviceId: String, app: String): CloudAnswer {
    val r = call("GET", "/sync/v3/hello", token, deviceId, app, "", null)
    when (r.status) {
      200 -> Unit
      //  ۴۰۴: v3 روی سرور خاموش است — هاب چیزی برای فرستادن ندارد
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
    (shop?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
  }.getOrNull()

  private fun call(
    method: String, path: String, token: String, device: String, app: String, version: String, body: String?,
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
