package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.data.repo.Backend
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/**
 *  سوکتِ زنده — «چیزی عوض شد» (بندِ ۲۰.۳).
 *
 *  روی خط فقط یک پیامِ کوچک می‌رود: `{"event":"changed","cursor":N}`.
 *  **خودِ داده هرگز از این در نمی‌رود**؛ برنامه بعد از این پیام Pull
 *  می‌کند. سوکت که قطع بود، کارِ دوره‌ای هر پانزده دقیقه می‌رود.
 *
 *  ── چرا OkHttp، تنها وابستگیِ تازهٔ این کار ─────────────────────────
 *  اندروید کلاینتِ WebSocket ندارد و `HttpURLConnection` — که کلِ لایهٔ
 *  شبکهٔ این برنامه روی آن است — نمی‌تواند ارتقا به WebSocket بدهد. دو
 *  راه بود: نوشتنِ RFC 6455 روی `SSLSocket` با دست، یا همین. دستی‌اش
 *  حدود صد و پنجاه خطِ ظریف است (ماسک، قطعه‌بندی، ping/pong، بستنِ
 *  تمیز) که یک اشتباهش سوکتی می‌سازد که «گاهی» کار می‌کند — و بندِ ۲۱٫۳
 *  پرامپت هم صریح همین کتابخانه را نام برده. پس کتابخانهٔ آزموده،
 *  فقط برای همین یک کار.
 *  ⚠️ بقیهٔ درخواست‌ها همچنان از `HttpEngine` می‌روند؛ این‌جا هیچ مسیرِ
 *  HTTPی عوض نشده.
 *
 *  ⛔ نشانی از `AppConfig` می‌آید و از هیچ کادری خوانده نمی‌شود — همان
 *  قفلِ همیشگی.
 *
 *  ── F0-11: توکن در آدرس نمی‌رود ────────────────────────────────────
 *  آدرسِ سوکت در لاگِ پراکسی می‌ماند. پس اول با `POST …/live/ticket`
 *  (توکن در سرآیند) یک بلیتِ یک‌بارمصرفِ ۶۰ ثانیه‌ای می‌گیریم و با
 *  `?ticket=` وصل می‌شویم. فقط سرورِ کهنه (۴۰۴) ⇒ همان `?token=` قدیم.
 */
class LiveSocket(
  context: Context,
  private val onChanged: () -> Unit,
) {

  private val app = context.applicationContext
  private val client = OkHttpClient.Builder()
    .pingInterval(30, TimeUnit.SECONDS)      // سوکتِ مرده زود معلوم شود
    .readTimeout(0, TimeUnit.MILLISECONDS)   // سوکت عمداً باز می‌ماند
    .build()

  /** درخواستِ بلیت نباید مثلِ سوکت بی‌پایان منتظر بماند */
  private val ticketClient = client.newBuilder()
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS)
    .build()

  @Volatile private var socket: WebSocket? = null
  @Volatile private var dialing = false
  @Volatile private var closedByUs = false
  @Volatile private var attempt = 0

  val connected: Boolean get() = socket != null

  /** پیامِ کوچک به دستگاه‌های دیگرِ همین حساب (سرور فقط رخدادهای شناخته را می‌پذیرد) */
  fun send(json: String): Boolean = socket?.send(json) ?: false

  companion object {
    /** سوکتِ همین برنامه — برای «گوشی به‌عنوانِ بارکدخوان» */
    @Volatile var current: LiveSocket? = null
  }

  fun connect() {
    current = this
    if (socket != null || dialing) return
    if (!Backend.isReady(app)) return
    val token = Backend.tokens(app).accessToken ?: return
    val base = AppConfig.baseUrl(app).ifBlank { return }
    //  سرورِ شبکهٔ دکان با گواهیِ سنجاق‌شده (پرچمِ `app.multi_server`)؛ وگرنه همان کلاینت
    val http = ir.vil3ntec.tohid.platform.ServerSwitchAndroid.okHttp(client, base)
    val tickets = if (http === client) ticketClient
      else http.newBuilder().readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    val device = runCatching { ir.vil3ntec.tohid.sync.SyncStore(app).deviceUid }.getOrDefault("")

    closedByUs = false
    dialing = true
    val ask = Request.Builder()
      .url(base.trimEnd('/') + ApiEndpoints.SyncV1.LIVE_TICKET_PATH)
      .header("Authorization", "Bearer $token")
      .header("X-App", AppConfig.appId)
      .post("{}".toRequestBody("application/json".toMediaType()))
      .build()
    tickets.newCall(ask).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        dialing = false
        fell()
      }

      override fun onResponse(call: Call, response: Response) {
        val cred = response.use {
          LiveCredential.from(it.code, runCatching { it.body?.string() }.getOrNull(), token)
        }
        dialing = false
        if (cred == null || closedByUs) { if (cred == null) fell(); return }
        open(LiveCredential.url(base, cred, device, AppConfig.appId), http)
      }
    })
  }

  private fun open(url: String, http: OkHttpClient = client) {
    if (socket != null) return
    socket = http.newWebSocket(
      Request.Builder().url(url).build(),
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          attempt = 0
          SyncV1Engine.of(app).liveConnected = true
          SyncV1Engine.of(app).publish()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          val event = runCatching { JSONObject(text).optString("event") }.getOrDefault("")
          if (event == "changed") runCatching { onChanged() }
          //  عکسِ کالایی روی دستگاهِ دیگر عوض شد؛ و هنگامِ وصل شدن، صفِ عکس‌ها خالی شود
          if (event == "photos" || event == "hello") ir.vil3ntec.tohid.sync.PhotoSync.kick(app)
          //  سربرگ و لوگوی فاکتور (فاکتورِ نسخهٔ ۲) برای چاپِ بی اینترنت تازه شود
          if (event == "hello") ir.vil3ntec.tohid.data.ReceiptFlag.kick(app)
          if (event == "inbox") runCatching {
            val o = JSONObject(text)
            ir.vil3ntec.tohid.sync.PhoneScanner.inboxArrived(o.optString("id"), o.optString("pair"), o.optString("product_id"))
          }
          if (event == "photo-request") runCatching {
            val o = JSONObject(text)
            ir.vil3ntec.tohid.sync.PhoneScanner.photoRequested(o.optString("pair"), o.optString("product_id"), o.optString("name"))
          }
          if (event == "scan") runCatching {
            val o = JSONObject(text)
            ir.vil3ntec.tohid.sync.PhoneScanner.received(o.optString("code"), o.optString("pair"))
          }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          fell()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          fell()
        }
      },
    )
  }

  private fun fell() {
    socket = null
    SyncV1Engine.of(app).liveConnected = false
    SyncV1Engine.of(app).publish()
    if (closedByUs) return
    /*
     *  بی‌پایان تلاش، ولی با فاصلهٔ فزاینده. سوکتی که هر ثانیه دوباره
     *  وصل شود هم باتری می‌خورد هم سرور را می‌کوبد — و کاربر هیچ‌وقت
     *  نمی‌فهمد چرا گوشی‌اش داغ است.
     */
    attempt++
    val wait = min(5 * 60_000.0, 2_000 * 2.0.pow(attempt)).toLong()
    android.os.Handler(android.os.Looper.getMainLooper())
      .postDelayed({ connect() }, wait)
  }

  fun disconnect() {
    closedByUs = true
    runCatching { socket?.close(1000, "bye") }
    socket = null
    SyncV1Engine.of(app).liveConnected = false
  }
}

/**
 *  منطقِ خالصِ F0-11 — جدا تا آزمونِ JVM بی اندروید بسنجدش.
 */
internal object LiveCredential {
  /** پاسخِ `POST …/live/ticket` ⇒ («ticket», بلیت)؛ سرورِ کهنه (۴۰۴) ⇒ («token», توکن)؛ بقیه ⇒ null */
  fun from(code: Int, body: String?, token: String): Pair<String, String>? = when {
    code in 200..299 -> runCatching { JSONObject(body ?: "").optString("ticket") }.getOrDefault("")
      .takeIf { it.isNotBlank() }?.let { "ticket" to it }
    code == 404 -> "token" to token
    else -> null
  }

  fun url(base: String, cred: Pair<String, String>, device: String, appId: String): String =
    base.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
      ApiEndpoints.SyncV1.LIVE_PATH +
      "?" + cred.first + "=" + URLEncoder.encode(cred.second, "UTF-8") +
      "&device_id=" + URLEncoder.encode(device, "UTF-8") +
      "&app=" + URLEncoder.encode(appId, "UTF-8")
}
