package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.sync.v1.LivePort
import ir.vil3ntec.tohid.sync.v1.LiveSocket
import ir.vil3ntec.tohid.sync.v1.LiveTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException

/**
 *  S76/S77 — سرور و سکوی جعلی برای سوکتِ زندهٔ برنامه (`sync/v1/LiveSocket.kt`): راهِ سوکت، بلیت، ساعت و زمان‌بند
 *  همه در دستِ آزمون‌اند، پس قطع، وصلِ دوباره، رخدادِ تکراری و رخدادِ دیرِ سوکتِ کهنه بی شبکه و بی صبرِ واقعی سنجیده می‌شوند.
 *  همان `LiveSocket` ِ برنامه است (همان سازنده با [LivePort] ِ جعلی)، نه کپیِ منطقش.
 */
internal class FakeLiveWs(val url: String, val listener: WebSocketListener) : WebSocket {
  val sent = mutableListOf<String>()
  /** کدهای بستنی که برنامه فرستاد */
  val closes = mutableListOf<Int>()

  override fun request(): Request = Request.Builder().url(url).build()
  override fun queueSize(): Long = 0
  override fun send(text: String): Boolean { sent += text; return true }
  override fun send(bytes: ByteString): Boolean = false
  override fun close(code: Int, reason: String?): Boolean { closes += code; return true }
  override fun cancel() {}

  //  ── سمتِ سرور/شبکه ──
  fun opens() = listener.onOpen(this, Response.Builder().request(request()).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build())
  fun says(text: String) = listener.onMessage(this, text)
  fun drops() = listener.onFailure(this, IOException("connection reset"), null)
  /** سرور می‌بندد: `onClosing` و پس از پاسخِ برنامه `onClosed` (همان ترتیبِ OkHttp) */
  fun serverCloses(code: Int) { listener.onClosing(this, code, ""); listener.onClosed(this, code, "") }
}

internal class FakeLivePort : LivePort {
  /** false ⇒ حالتِ آموزشی، بی حساب یا بی سرور */
  var reachable = true
  var tickets = 0
  /** بلیتِ سرور؛ خطا ⇒ «اینترنت نیست» */
  var ticket: suspend () -> Pair<String, String>? = { "ticket" to "T${++tickets}" }
  var clock = 1_000_000L
  /** (زمانِ اجرا، کار) — زمان‌بندِ جعلی؛ فقط [advance] جلو می‌بردش */
  val timers = mutableListOf<Pair<Long, () -> Unit>>()
  /** فاصلهٔ هر «دوباره وصل شو» که سوکت خواست */
  val waits = mutableListOf<Long>()
  val sockets = mutableListOf<FakeLiveWs>()
  /** تاریخچهٔ نشانِ «زنده/آفلاین» */
  val lamp = mutableListOf<Boolean>()
  var publishes = 0
  /** رخدادهایی که به کارهای سکو (عکس، پرچم، بارکدخوانِ گوشی) رسید */
  val platform = mutableListOf<String>()

  override fun target(): LiveTarget? = if (!reachable) null else
    LiveTarget("ACCESS-TOKEN", "https://shop.example", "dev-1", "shop") { url, listener ->
      FakeLiveWs(url, listener).also { sockets += it }
    }

  override suspend fun credential(token: String): Pair<String, String>? = ticket()
  override fun now(): Long = clock
  override fun later(ms: Long, run: () -> Unit) { waits += ms; timers += (clock + ms) to run }
  override fun live(on: Boolean) { lamp += on }
  override fun publish() { publishes++ }
  override fun message(event: String, text: String) { platform += event }

  /** ساعت را جلو ببر و هر کارِ سررسیده را به ترتیب اجرا کن */
  fun advance(ms: Long) {
    clock += ms
    while (true) {
      val due = timers.filter { it.first <= clock }.minByOrNull { it.first } ?: return
      timers.remove(due)
      due.second()
    }
  }

  val last: FakeLiveWs get() = sockets.last()

  /** همان `LiveSocket` ِ برنامه روی این سکو؛ کارِ بلیت بی رشتهٔ دیگر (Unconfined) */
  fun socket(onChanged: () -> Unit): LiveSocket = LiveSocket(this, onChanged, CoroutineScope(Dispatchers.Unconfined))
}
