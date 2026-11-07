package ir.vil3ntec.tohid.platform

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.hub.HubRelay
import ir.vil3ntec.tohid.core.hub.HubRelayPort
import ir.vil3ntec.tohid.core.hub.HubRoute
import ir.vil3ntec.tohid.core.server.ServerFailover
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.InputStream
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 *  SYN-09 — مرزِ سکو برای «دستگاه ⇐ هاب» (`core/hub/HubRelay.kt`، `docs/SYN-09-HUB-fa.md` §۱ و §۴).
 *
 *  موتورِ v3 فقط وقتی صدایش می‌زند که push **به سرور نرسید**. این‌جا:
 *  - پرچمِ `app.multi_server`، تنظیمِ `hub.local` و نشانی و سنجاقِ هاب (`hub.url`/`hub.pin` ِ `tohid-servers`) باید باشند؛
 *  - `HubRoute.choose` با پروفایلِ فعال: https، شبکهٔ دکان، سنجاق، و `hello` ِ هاب با **همان `serverId`**؛
 *  - همان بدنه، همان توکن و سرآیندهای دستگاه، روی همان گواهیِ سنجاق‌شده (`PinnedTls`)، بی پراکسی.
 *  پاسخ فقط شمرده می‌شود؛ موتور هیچ ورودی‌ای را با آن از صف برنمی‌دارد.
 */
internal class HubRelayAndroid private constructor(private val app: Context) : HubRelayPort {

  override suspend fun relay(body: JsonObject): HubRelay.Outcome = withContext(Dispatchers.IO) { relayNow(body) }

  private fun relayNow(body: JsonObject): HubRelay.Outcome {
    val prefs = app.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE)
    if (!ServerSwitchAndroid.enabled(app) || !prefs.getBoolean(HubRoute.SETTING_ON, false)) {
      return HubRelay.Outcome(why = HubRoute.Why.OFF)
    }
    val url = prefs.getString(HubRoute.SETTING_URL, null)
    val pin = prefs.getString(HubRoute.SETTING_PIN, null)
    val profile = ServerSwitchAndroid.activeProfile(app)
    //  همهٔ شرط‌های بی شبکه پیش از هر تماس (HUB_UNREACHABLE یعنی فقط `hello` مانده)
    val pre = HubRoute.choose(true, profile, cloudReachable = false, hubUrl = url, hubPin = pin, hub = null)
    if (pre.why != HubRoute.Why.HUB_UNREACHABLE) return HubRelay.Outcome(why = pre.why)
    //  HW-13: با هدفِ API 37 بی `ACCESS_LOCAL_NETWORK` وصل شدن به هابِ شبکهٔ دکان مهلت می‌خورد ⇒ همان دم، بی تماس
    //  (هدفِ امروز ۳۵ ⇒ هرگز بسته نیست؛ همان قاعدهٔ چاپگرِ شبکه، `LocalNetworkAccess`)
    val host = (ir.vil3ntec.tohid.core.server.ServerAddress.check(url!!, lanAllowed = true) as? ir.vil3ntec.tohid.core.server.ServerAddress.Verdict.Ok)?.parts?.host.orEmpty()
    if (ir.vil3ntec.tohid.hw.LocalNetworkAccess.blocked(app, host)) return HubRelay.Outcome(refused = LOCAL_NETWORK_DENIED)
    val token = Backend.tokens(app).accessToken ?: return HubRelay.Outcome(refused = "hub_no_session")
    val device = (body["device_id"] as? JsonPrimitive)?.content.orEmpty()
    val base = url!!.trimEnd('/')
    val headers = mapOf(
      "Authorization" to "Bearer $token",
      "X-Device" to device,
      "X-App" to AppConfig.appId,
      "X-App-Version" to AppConfig.appVersion,
    )
    val hello = call("GET", "$base/hub/v1/hello", headers, null, pin!!)
    val info = if (hello.first == 200) HubRoute.parseHello(hello.second) else null
    val d = HubRoute.choose(true, profile, cloudReachable = false, hubUrl = url, hubPin = pin, hub = info)
    if (d.route != HubRoute.Route.HUB) return HubRelay.Outcome(why = d.why)
    val r = call("POST", "$base/hub/v1/push", headers + ("X-Server-Id" to profile!!.serverId!!), body.toString(), pin)
    return HubRelay.interpret(r.first, r.second, HubRelay.opIdsOf(body))
  }

  /** (کد، بدنه)؛ نرسید ⇒ صفر */
  private fun call(method: String, url: String, headers: Map<String, String>, body: String?, pin: String): Pair<Int, String?> = try {
    val c = URL(url).openConnection(Proxy.NO_PROXY) as HttpsURLConnection
    PinnedTls.of(ir.vil3ntec.tohid.core.server.ServerProfile.normalizeCertPin(pin)!!).applyTo(c)
    val timeout = ServerFailover.timeoutOf(url)
    c.connectTimeout = timeout
    c.readTimeout = timeout
    c.requestMethod = method
    c.instanceFollowRedirects = false
    for ((k, v) in headers) c.setRequestProperty(k, v)
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

  companion object {
    /** همان `PREFS` ِ `ServerSwitchAndroid` */
    private const val SERVER_PREFS = "tohid-servers"
    private const val MAX_REPLY = 1024 * 1024
    /** HW-13: اجازهٔ «شبکهٔ محلی» داده نشده — موتور فقط `pending` را می‌شمارد، پس صف دست نمی‌خورد */
    const val LOCAL_NETWORK_DENIED = "hub_local_network_denied"

    /** پرچمِ `app.multi_server` خاموش ⇒ `null` ⇒ موتور همان امروز (پرچم فقط در شروعِ فرآیند عوض می‌شود) */
    fun of(context: Context): HubRelayAndroid? =
      if (ServerSwitchAndroid.enabled(context)) HubRelayAndroid(context.applicationContext) else null
  }
}
