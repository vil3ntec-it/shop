package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
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
 *
 *  ── S76/S77: آزمودنی بی شبکه ────────────────────────────────────────
 *  راهِ سوکت، بلیت، ساعت، زمان‌بند و نشانِ «زنده/آفلاین» از [LivePort] می‌آیند؛ سازندهٔ عمومی همان
 *  سکوی واقعی ([AppLivePort]) را می‌دهد و آزمونِ JVM جعلی‌اش را (`ScenarioS76Test`، `ScenarioS77Test`).
 */
class LiveSocket internal constructor(
  private val port: LivePort,
  private val onChanged: () -> Unit,
  /** فقط برای گرفتنِ بلیت؛ هر کارِ سوکت روی رشتهٔ OkHttp است */
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

  constructor(context: Context, onChanged: () -> Unit) : this(AppLivePort(context.applicationContext), onChanged)

  @Volatile private var socket: WebSocket? = null
  @Volatile private var dialing = false
  @Volatile private var closedByUs = false
  @Volatile private var attempt = 0
  @Volatile private var openedAt = 0L
  /** نسلِ سوکت: رخدادِ سوکتِ کهنه (پس از افتادن یا جایگزینی) بی‌اثر است و هر سوکت یک بار می‌افتد */
  @Volatile private var generation = 0

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
    val target = port.target() ?: return

    closedByUs = false
    dialing = true
    scope.launch {
      val cred = runCatching { port.credential(target.token) }.getOrNull()
      dialing = false
      if (cred == null || closedByUs) { if (cred == null) fell(null); return@launch }
      open(LiveCredential.url(target.base, cred, target.device, target.appId), target)
    }
  }

  private fun open(url: String, target: LiveTarget) {
    if (socket != null) return
    val gen = synchronized(this) { ++generation }
    val ws = target.dial(
      url,
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          if (gen != generation) return
          attempt = 0
          openedAt = port.now()
          port.live(true)
          port.publish()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          val event = runCatching { JSONObject(text).optString("event") }.getOrDefault("")
          //  «hello» هم: هر وصل (از جمله پس از بسته‌شدنِ 4403/4401) یک Pull — خبری که در
          //  فاصلهٔ قطع آمد گم نشود
          if (event == "changed" || event == "hello") runCatching { onChanged() }
          port.message(event, text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          fell(gen)
        }

        /**
         *  سرور بست (4401 دسترسی رفت/عوض شد، 4403 توکن پیر شد، …). OkHttp خودش پاسخِ بستن را
         *  نمی‌فرستد و تا آن نرود `onClosed` نمی‌آید؛ پیش‌تر برنامه تا شکستِ ping (تا یک دقیقه)
         *  گمان می‌کرد وصل است و اسکنِ گوشی به کامپیوتری می‌رفت که در اتاق نبود.
         */
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
          runCatching { webSocket.close(1000, null) }
          fell(gen, code)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          fell(gen, code)
        }
      },
    )
    //  اگر پیش از همین خط افتاد (نسل گذشت)، سوکتِ مرده «وصل» نشان داده نمی‌شود
    synchronized(this) { if (gen == generation) socket = ws }
  }

  /** @param gen نسلِ سوکتی که افتاد (null = بلیت نشد)؛ هر نسل فقط یک بار به حساب می‌آید */
  private fun fell(gen: Int?, code: Int = 0) {
    synchronized(this) {
      if (gen != null) {
        if (gen != generation) return
        generation++
      }
      socket = null
    }
    port.live(false)
    port.publish()
    if (closedByUs) return
    /*
     *  بی‌پایان تلاش، ولی با فاصلهٔ فزاینده. سوکتی که هر ثانیه دوباره
     *  وصل شود هم باتری می‌خورد هم سرور را می‌کوبد — و کاربر هیچ‌وقت
     *  نمی‌فهمد چرا گوشی‌اش داغ است.
     *
     *  ⚠️ جز یک حالت: 4403 (توکن سرِ موعد پیر شد) روی سوکتی که دست‌کم ده ثانیه زنده بود ⇒
     *  یک ثانیه بعد، با بلیتِ توکنِ تازه (`credential`). همین است که کامپیوترِ صندوق هر ساعت
     *  فقط لحظه‌ای از اتاق بیرون است. سوکتی که بلافاصله دوباره 4403 بخورد همان راهِ فزاینده را می‌رود.
     */
    val quick = code == 4403 && port.now() - openedAt >= 10_000
    val wait = if (quick) {
      attempt = 0
      1_000L
    } else {
      attempt++
      min(5 * 60_000.0, 2_000 * 2.0.pow(attempt)).toLong()
    }
    //  S77: `disconnect()` در همین صبر پایان است — این کار سوکتِ رهاشده را زنده نمی‌کند (پیش‌تر `connect()` پرچمِ
    //  «ما بستیم» را پاک می‌کرد و سوکتی بی مالک تا پایانِ فرایند می‌ماند: هر خبر دو بار و `current` به نمونهٔ رهاشده).
    //  `connect()` ِ صریح همچنان دوباره وصل می‌کند.
    port.later(wait) { if (!closedByUs) connect() }
  }

  fun disconnect() {
    closedByUs = true
    runCatching { socket?.close(1000, "bye") }
    socket = null
    port.live(false)
  }
}

/**
 *  S76/S77: هر چه سوکتِ زنده بیرون از خودش لازم دارد — دروازهٔ وصل، بلیت، راهِ سوکت (transport)، ساعت، زمان‌بند،
 *  نشانِ «زنده/آفلاین» و کارهای سکوی پس از پیام. واقعی‌اش [AppLivePort] است (اندروید، کامپیوتر و آیفون با shimها)؛
 *  آزمونِ JVM ِ سناریوها جعلی‌اش را می‌دهد. منطقِ وصل/قطع/فاصلهٔ فزاینده/نسلِ سوکت همه در خودِ [LiveSocket] می‌ماند.
 */
internal interface LivePort {
  /** null ⇒ حالا وصل نشو (حالتِ آموزشی، بی حساب یا بی نشانیِ سرور) */
  fun target(): LiveTarget?
  /** («ticket»، بلیت) یا برای سرورِ کهنه («token»، توکن)؛ null یا خطا ⇒ دوباره با فاصله */
  suspend fun credential(token: String): Pair<String, String>?
  /** ساعت (میلی‌ثانیه) — فقط برای «سوکت دست‌کم ده ثانیه زنده بود» ِ 4403 */
  fun now(): Long
  /** زمان‌بند: [run] پس از [ms] میلی‌ثانیه روی رشتهٔ اصلی */
  fun later(ms: Long, run: () -> Unit)
  /** نشانِ «زنده/آفلاین» ِ همگام‌سازی (`SyncV1Engine.liveConnected`) */
  fun live(on: Boolean)
  /** وضعیتِ تازهٔ همگام‌سازی را به صفحه‌ها بده (`SyncV1Engine.publish`) */
  fun publish()
  /** کارهای سکو پس از هر پیام (عکس‌ها، پرچم‌ها، گوشی به‌عنوانِ بارکدخوان)؛ Pull را خودِ سوکت صدا می‌زند */
  fun message(event: String, text: String)
}

/** جای وصل: توکنِ همین لحظه، نشانیِ سرور، شناسهٔ دستگاه، و راهِ سوکت (OkHttp با گواهیِ سنجاق‌شدهٔ همان سرور) */
internal class LiveTarget(
  val token: String,
  val base: String,
  val device: String,
  val appId: String,
  val dial: (url: String, listener: WebSocketListener) -> WebSocket,
)

/** همان کارهای پیشینِ [LiveSocket] روی سکوی واقعی، بی هیچ تغییری */
private class AppLivePort(private val app: Context) : LivePort {
  private val client = OkHttpClient.Builder()
    .pingInterval(30, TimeUnit.SECONDS)      // سوکتِ مرده زود معلوم شود
    .readTimeout(0, TimeUnit.MILLISECONDS)   // سوکت عمداً باز می‌ماند
    .build()

  override fun target(): LiveTarget? {
    //  OPS-07: حالتِ آموزشی ⇒ نه سوکت، نه Pull ِ پس از رخداد
    if (!ir.vil3ntec.tohid.data.TrainingMode.networkAllowed()) return null
    if (!Backend.isReady(app)) return null
    val token = Backend.tokens(app).accessToken ?: return null
    val base = AppConfig.baseUrl(app).ifBlank { return null }
    //  سرورِ شبکهٔ دکان با گواهیِ سنجاق‌شده (پرچمِ `app.multi_server`)؛ وگرنه همان کلاینت.
    //  بلیت از `ApiClient` می‌رود که `HttpEngine` ِ آن همان گواهی را می‌سنجد (`ServerSwitchAndroid.prepare`)
    val http = ir.vil3ntec.tohid.platform.ServerSwitchAndroid.okHttp(client, base)
    val device = runCatching { ir.vil3ntec.tohid.sync.SyncStore(app).deviceUid }.getOrDefault("")
    return LiveTarget(token, base, device, AppConfig.appId) { url, listener ->
      http.newWebSocket(Request.Builder().url(url).build(), listener)
    }
  }

  /**
   *  بلیت از **همان راهِ همیشگیِ** `ApiClient`: توکنِ پیر (۴۰۱) یک بار و پشتِ همان قفل
   *  تازه می‌شود و درخواست دوباره می‌رود (S78 — سرور سوکت را سرِ انقضای توکن با 4403 می‌بندد؛
   *  پیش‌تر بلیت با OkHttpِ خام می‌رفت، ۴۰۱ فقط «دوباره با فاصله» بود و تا کارِ دیگری توکن را
   *  تازه نمی‌کرد، سوکت چند دقیقه قطع می‌ماند). تازه‌سازیِ ردشده همان «دوباره وارد شوید» ِ همه‌جاست.
   *  فقط سرورِ کهنه (۴۰۴ با هر دو پیشوند) ⇒ همان `?token=` قدیم.
   */
  override suspend fun credential(token: String): Pair<String, String>? = try {
    val body = Backend.api(app).post(ApiEndpoints.SyncV1.LIVE_TICKET)
    LiveCredential.from(200, body.toString(), token)
  } catch (_: ApiFailure.NotFound) {
    LiveCredential.from(404, null, Backend.tokens(app).accessToken ?: token)
  }

  override fun now(): Long = System.currentTimeMillis()

  override fun later(ms: Long, run: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper())
      .postDelayed({ run() }, ms)
  }

  override fun live(on: Boolean) {
    SyncV1Engine.of(app).liveConnected = on
  }

  override fun publish() {
    SyncV1Engine.of(app).publish()
  }

  override fun message(event: String, text: String) {
    //  عکسِ کالایی روی دستگاهِ دیگر عوض شد؛ و هنگامِ وصل شدن، صفِ عکس‌ها خالی شود
    if (event == "photos" || event == "hello") ir.vil3ntec.tohid.sync.PhotoSync.kick(app)
    //  سربرگ و لوگوی فاکتور (فاکتورِ نسخهٔ ۲) برای چاپِ بی اینترنت تازه شود؛ و پرچمِ ادغامِ دفتر
    if (event == "hello") {
      ir.vil3ntec.tohid.data.ReceiptFlag.kick(app)
      ir.vil3ntec.tohid.data.LedgerMergeFlag.kick(app)
      ir.vil3ntec.tohid.data.LoyaltyShop.kick(app)   //  PRM-13: قاعدهٔ یکسانِ امتیازِ دکان (پرچمِ خاموش ⇒ همان دستگاه)
    }
    //  پرچمِ موتورِ تازهٔ موجودی (`shop.stock_v2`، پیش‌فرض خاموش)
    if (event == "hello") ir.vil3ntec.tohid.data.StockFlag.kick(app)
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
      "&app=" + URLEncoder.encode(appId, "UTF-8") +
      //  S78: این نسخه پس از بسته‌شدنِ سرِ انقضای توکن (4403) با توکنِ تازه برمی‌گردد — پس سرور
      //  سوکتش را سرِ موعد می‌بندد. برنامه‌ای که این را نفرستد، تا لغوِ واقعی باز می‌ماند.
      "&caps=" + CAPS

  /** توانایی‌هایی که این کلاینت به سرور اعلام می‌کند (فهرستِ جداشده با ویرگول) */
  const val CAPS = "expiry"
}
