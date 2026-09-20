package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.data.repo.Backend
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
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

  @Volatile private var socket: WebSocket? = null
  @Volatile private var closedByUs = false
  @Volatile private var attempt = 0

  val connected: Boolean get() = socket != null

  fun connect() {
    if (socket != null) return
    if (!Backend.isReady(app)) return
    val token = Backend.tokens(app).accessToken ?: return
    val base = AppConfig.baseUrl(app).ifBlank { return }
    val device = runCatching { ir.vil3ntec.tohid.sync.SyncStore(app).deviceUid }.getOrDefault("")

    val url = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
      ApiEndpoints.SyncV1.LIVE_PATH +
      "?token=" + java.net.URLEncoder.encode(token, "UTF-8") +
      "&device_id=" + java.net.URLEncoder.encode(device, "UTF-8") +
      "&app=" + java.net.URLEncoder.encode(AppConfig.appId, "UTF-8")

    closedByUs = false
    socket = client.newWebSocket(
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
