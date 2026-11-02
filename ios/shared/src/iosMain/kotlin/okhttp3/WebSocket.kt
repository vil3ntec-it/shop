package okhttp3

import ir.vil3ntec.tohid.ios.jvm.synchronized
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionWebSocketDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue
import platform.darwin.NSObject

/*
 *  shimِ iOS ِ WebSocket ِ OkHttp روی `NSURLSessionWebSocketTask` (iOS 13+) — فقط آنچه `sync/v1/LiveSocket.kt`
 *  ِ اندروید به کار می‌برد، تا همان فایل دست‌نخورده روی آیفون کار کند (سوکتِ زندهٔ «چیزی عوض شد»).
 *
 *  رخدادها همان قراردادِ OkHttp‌اند و هر سوکت دقیقاً یک پایان دارد:
 *    باز شد ⇐ `onOpen`؛ پیامِ متنی ⇐ `onMessage`؛ سرور بست ⇐ `onClosing` و سپس `onClosed` (با همان کد، مثلِ 4403)؛
 *    ما بستیم ⇐ `onClosed`؛ شکست (شبکه، دست‌دادنِ ناموفق، pong ِ نرسیده) ⇐ `onFailure` — پس از آن هیچ.
 *  `pingInterval`: هر بازه یک ping؛ pong ِ پیشین نرسیده ⇐ شکست (همان «sent ping but didn't receive pong» ِ OkHttp).
 *  گواهیِ سنجاق‌شده (`sslSocketFactory` + `hostnameVerifier`، از `ServerSwitchAndroid.okHttp` ِ همان فایلِ اندروید) ⇐
 *  پرسشِ اعتمادِ همین نشست با همان قاعدهٔ JDK (‎javax/net/ssl/IosTls.kt‎)؛ رد ⇐ `onFailure` با `SSLHandshakeException`.
 */
class OkHttpClient private constructor(
  internal val pingMs: Long,
  internal val connectMs: Long,
  internal val trust: javax.net.ssl.X509TrustManager?,
  internal val verifier: javax.net.ssl.HostnameVerifier?,
) {
  constructor() : this(0L, 10_000L, null, null)

  fun newBuilder(): Builder = Builder(pingMs, connectMs, trust, verifier)

  fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
    IosWebSocket(this, request, listener).also { it.start() }

  class Builder internal constructor(
    private var pingMs: Long,
    private var connectMs: Long,
    private var trust: javax.net.ssl.X509TrustManager?,
    private var verifier: javax.net.ssl.HostnameVerifier?,
  ) {
    constructor() : this(0L, 10_000L, null, null)

    fun sslSocketFactory(sslSocketFactory: javax.net.ssl.SSLSocketFactory, trustManager: javax.net.ssl.X509TrustManager) =
      apply { trust = trustManager }
    fun hostnameVerifier(hostnameVerifier: javax.net.ssl.HostnameVerifier) = apply { verifier = hostnameVerifier }

    fun pingInterval(interval: Long, unit: TimeUnit) = apply { pingMs = unit.toMillis(interval) }
    fun connectTimeout(timeout: Long, unit: TimeUnit) = apply { connectMs = unit.toMillis(timeout) }
    fun readTimeout(@Suppress("UNUSED_PARAMETER") timeout: Long, @Suppress("UNUSED_PARAMETER") unit: TimeUnit) = this
    fun writeTimeout(@Suppress("UNUSED_PARAMETER") timeout: Long, @Suppress("UNUSED_PARAMETER") unit: TimeUnit) = this
    fun build() = OkHttpClient(pingMs, connectMs, trust, verifier)
  }
}

class Request private constructor(val url: String, internal val headers: Map<String, String>) {
  class Builder {
    private var url = ""
    private val headers = LinkedHashMap<String, String>()
    fun url(url: String) = apply { this.url = url }
    fun header(name: String, value: String) = apply { headers[name] = value }
    fun addHeader(name: String, value: String) = apply { headers[name] = headers[name]?.let { "$it, $value" } ?: value }
    fun build() = Request(url, headers.toMap())
  }

  override fun toString(): String = "Request{url=$url}"
}

class Response internal constructor(val request: Request, val code: Int, val message: String) {
  override fun toString(): String = "Response{code=$code, message=$message, url=${request.url}}"
}

interface WebSocket {
  fun request(): Request
  fun queueSize(): Long
  fun send(text: String): Boolean
  fun close(code: Int, reason: String?): Boolean
  fun cancel()
}

abstract class WebSocketListener {
  open fun onOpen(webSocket: WebSocket, response: Response) {}
  open fun onMessage(webSocket: WebSocket, text: String) {}
  open fun onClosing(webSocket: WebSocket, code: Int, reason: String) {}
  open fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
  open fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {}
}

/** یک سوکت، یک نشستِ NSURLSession (نماینده‌اش را نگه می‌دارد تا پایان، سپس رها می‌شود) */
private class IosWebSocket(
  private val client: OkHttpClient,
  private val request: Request,
  private val listener: WebSocketListener,
) : WebSocket {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var session: NSURLSession? = null
  private var task: NSURLSessionWebSocketTask? = null
  private var ping: Job? = null

  /** بازِ شده، پس از `close` ِ ما، پایان‌یافته — هر کدام یک بار */
  private var opened = false
  private var closing = false
  private var finished = false

  /** دست‌دادن را سنجاقِ گواهی رد کرد */
  private var pinRejected = false

  override fun request(): Request = request

  override fun queueSize(): Long = 0L

  fun start() {
    val url = NSURL.URLWithString(request.url)
    if (url == null) {
      fail(IOException("bad websocket url"))
      return
    }
    val req = NSMutableURLRequest.requestWithURL(url)
    req.setTimeoutInterval(client.connectMs.coerceAtLeast(1_000L) / 1000.0)
    request.headers.forEach { (k, v) -> req.setValue(v, forHTTPHeaderField = k) }
    val config = NSURLSessionConfiguration.ephemeralSessionConfiguration
    config.HTTPShouldSetCookies = false
    val s = NSURLSession.sessionWithConfiguration(config, delegate = Delegate(this), delegateQueue = null)
    val t = s.webSocketTaskWithRequest(req)
    synchronized(this) {
      session = s
      task = t
    }
    t.resume()
  }

  override fun send(text: String): Boolean {
    val t = synchronized(this) { if (finished || closing) null else task } ?: return false
    t.sendMessage(NSURLSessionWebSocketMessage(text)) { error -> if (error != null) fail(error.asIo()) }
    return true
  }

  override fun close(code: Int, reason: String?): Boolean {
    val t = synchronized(this) {
      if (finished || closing) return false
      closing = true
      task
    } ?: return false
    t.cancelWithCloseCode(code.toLong(), reason = reason?.utf8())
    //  NSURLSession پس از بستن رخدادِ دیگری نمی‌دهد اگر سرور پاسخ ندهد؛ OkHttp تا یک دقیقه صبر می‌کند و می‌بندد
    scope.launch {
      delay(CLOSE_WAIT_MS)
      finish { listener.onClosed(this@IosWebSocket, code, reason.orEmpty()) }
    }
    return true
  }

  override fun cancel() {
    val t = synchronized(this) { task }
    t?.cancel()
    fail(IOException("Canceled"))
  }

  // ── رخدادهای NSURLSession ─────────────────────────────────────────────

  fun didOpen() {
    synchronized(this) {
      if (finished) return
      opened = true
    }
    listener.onOpen(this, Response(request, 101, "Switching Protocols"))
    receive()
    if (client.pingMs > 0) ping = scope.launch { pingLoop() }
  }

  fun didClose(code: Long, reason: NSData?) {
    val text = reason?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }.orEmpty()
    val byServer = synchronized(this) { !finished && !closing }
    if (byServer) {
      synchronized(this) { closing = true }
      listener.onClosing(this, code.toInt(), text)
    }
    finish { listener.onClosed(this, code.toInt(), text) }
  }

  fun didComplete(error: NSError?) {
    if (error == null) {
      finish { listener.onClosed(this, 1000, "") }
      return
    }
    if (synchronized(this) { pinRejected }) {
      fail(javax.net.ssl.SSLHandshakeException("certificate pin mismatch"))
      return
    }
    val http = (synchronized(this) { task }?.response as? NSHTTPURLResponse)
      ?.takeIf { !synchronized(this) { opened } }
      ?.let { Response(request, it.statusCode.toInt(), "") }
    fail(error.asIo(), http)
  }

  private fun receive() {
    val t = synchronized(this) { if (finished) null else task } ?: return
    t.receiveMessageWithCompletionHandler { message, error ->
      when {
        error != null -> fail(error.asIo())
        message != null -> {
          message.string?.let { listener.onMessage(this, it) }
          receive()
        }
      }
    }
  }

  private suspend fun pingLoop() {
    var waiting = false
    while (scope.isActive) {
      delay(client.pingMs)
      if (waiting) {
        fail(SocketTimeoutException("sent ping but didn't receive pong within ${client.pingMs}ms"))
        return
      }
      val t = synchronized(this) { if (finished) null else task } ?: return
      waiting = true
      t.sendPingWithPongReceiveHandler { error ->
        if (error != null) fail(error.asIo()) else {
          waiting = false
        }
      }
    }
  }

  /** پرسشِ اعتماد: بی سنجاق ⇐ پیش‌فرضِ iOS؛ با سنجاق ⇐ همان قاعدهٔ JDK */
  fun challenge(
    challenge: NSURLAuthenticationChallenge,
    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
  ) {
    val trust = client.trust?.let { listOf<javax.net.ssl.TrustManager>(it) }
    if (javax.net.ssl.IosTls.answer(challenge, trust, client.verifier, completionHandler) == false) synchronized(this) { pinRejected = true }
  }

  private fun fail(t: Throwable, response: Response? = null) {
    finish(cancelTask = true) { listener.onFailure(this, t, response) }
  }

  /** پایانِ یگانهٔ سوکت: یک رخداد، سپس رها کردنِ نشست و نماینده‌اش */
  private inline fun finish(cancelTask: Boolean = false, event: () -> Unit) {
    val (s, t) = synchronized(this) {
      if (finished) return
      finished = true
      session to task
    }
    ping?.cancel()
    scope.coroutineContext[Job]?.cancel()
    if (cancelTask) t?.cancel()
    event()
    s?.finishTasksAndInvalidate()
  }

  private class Delegate(private val socket: IosWebSocket) : NSObject(), NSURLSessionWebSocketDelegateProtocol {
    override fun URLSession(session: NSURLSession, webSocketTask: NSURLSessionWebSocketTask, didOpenWithProtocol: String?) {
      socket.didOpen()
    }

    override fun URLSession(session: NSURLSession, webSocketTask: NSURLSessionWebSocketTask, didCloseWithCode: Long, reason: NSData?) {
      socket.didClose(didCloseWithCode, reason)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
      socket.didComplete(didCompleteWithError)
    }

    override fun URLSession(
      session: NSURLSession,
      task: NSURLSessionTask,
      didReceiveChallenge: NSURLAuthenticationChallenge,
      completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
      socket.challenge(didReceiveChallenge, completionHandler)
    }
  }

  private companion object {
    const val CLOSE_WAIT_MS = 60_000L
  }
}

@OptIn(BetaInteropApi::class)
private fun String.utf8(): NSData? = NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding)

private fun NSError.asIo(): IOException = IOException(localizedDescription)
