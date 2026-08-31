package ir.vil3ntec.tohid.core.net

import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 *  سرورِ کوچکِ آزمایشی — روی سوکتِ ساده.
 *
 *  چرا `java.net.ServerSocket` و نه `com.sun.net.httpserver`: تست‌های
 *  واحدِ اندروید در برابرِ `android.jar` کامپایل می‌شوند و بسته‌های
 *  `com.sun.*` آنجا نیستند. سوکتِ ساده هم روی JVM هست و هم آنجا.
 *
 *  فقط آن‌قدر HTTP می‌فهمد که برای این تست‌ها لازم است: خطِ درخواست،
 *  سرآیندها، و بدنه به اندازهٔ `Content-Length`. هر پاسخ با
 *  `Connection: close` بسته می‌شود تا چیزی در میانه نماند.
 */
class TestHttpServer {

  /** آنچه از یک درخواست لازم داریم */
  class Request(val method: String, val path: String, val bearer: String, val body: String)

  private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
  private val routes = HashMap<String, (Request) -> Pair<Int, String>>()
  private val counts = HashMap<String, AtomicInteger>()

  /** نشانیِ ریشه — همان که به `HttpEngine` داده می‌شود */
  val baseUrl: String get() = "http://127.0.0.1:${socket.localPort}"

  private val worker = Thread {
    while (!socket.isClosed) {
      val client = try { socket.accept() } catch (_: Exception) { break }
      //  هر درخواست در نخِ خودش، وگرنه درخواستی که وسطِ کارِ دیگری
      //  می‌آید پشتِ آن می‌ماند
      Thread { serve(client) }.apply { isDaemon = true }.start()
    }
  }.apply { isDaemon = true }

  init { worker.start() }

  /** یک مسیر و پاسخش. شمارشِ درخواست‌ها خودکار است. */
  fun on(path: String, handler: (Request) -> Pair<Int, String>) {
    routes[path] = handler
    counts[path] = AtomicInteger(0)
  }

  /** چند بار به این مسیر زده شده */
  fun hits(path: String): Int = counts[path]?.get() ?: 0

  fun close() {
    runCatching { socket.close() }
  }

  private fun serve(client: Socket) {
    client.use {
      val input = BufferedInputStream(it.getInputStream())

      val requestLine = readLine(input) ?: return
      val parts = requestLine.split(" ")
      if (parts.size < 2) return
      val method = parts[0]
      //  پرسمان برای مسیریابی کنار گذاشته می‌شود
      val path = parts[1].substringBefore('?')

      var bearer = ""
      var length = 0
      while (true) {
        val header = readLine(input) ?: break
        if (header.isEmpty()) break
        val name = header.substringBefore(':').trim().lowercase()
        val value = header.substringAfter(':').trim()
        if (name == "authorization") bearer = value.removePrefix("Bearer ")
        if (name == "content-length") length = value.toIntOrNull() ?: 0
      }

      val body = if (length > 0) {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
          val n = input.read(bytes, read, length - read)
          if (n < 0) break
          read += n
        }
        String(bytes, 0, read, Charsets.UTF_8)
      } else ""

      val handler = routes[path]
      val (status, text) = if (handler == null) {
        404 to """{"error":{"code":"not_found","message":"این مسیر وجود ندارد"}}"""
      } else {
        counts[path]?.incrementAndGet()
        handler(Request(method, path, bearer, body))
      }

      val payload = text.toByteArray(Charsets.UTF_8)
      val head = buildString {
        append("HTTP/1.1 ").append(status).append(" \r\n")
        append("Content-Type: application/json; charset=utf-8\r\n")
        append("Content-Length: ").append(payload.size).append("\r\n")
        append("Connection: close\r\n\r\n")
      }
      val out = it.getOutputStream()
      out.write(head.toByteArray(Charsets.US_ASCII))
      out.write(payload)
      out.flush()
    }
  }

  /** یک خطِ `\r\n`دار — بدونِ خواندنِ بیش از اندازه، چون بدنه بعدش می‌آید */
  private fun readLine(input: BufferedInputStream): String? {
    val buffer = StringBuilder()
    while (true) {
      val c = input.read()
      if (c < 0) return if (buffer.isEmpty()) null else buffer.toString()
      if (c == '\n'.code) return buffer.toString().removeSuffix("\r")
      buffer.append(c.toChar())
    }
  }
}
