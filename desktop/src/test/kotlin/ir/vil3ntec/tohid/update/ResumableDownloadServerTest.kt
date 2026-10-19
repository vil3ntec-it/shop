package ir.vil3ntec.tohid.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 *  دانلودِ ادامه‌دار با سرورِ واقعیِ محلی (`com.sun.net.httpserver`) که عمداً
 *  وسطِ فایل اتصال را می‌بُرد و Range/206/If-Range را مثلِ سرورِ ما
 *  (`res.download`) و GitHub جواب می‌دهد.
 *
 *  سنجه‌ها: فایلِ نهایی بایت‌به‌بایت همان است و هیچ بایتی دو بار فرستاده
 *  نشده — یعنی هیچ تلاشی از صفر شروع نکرده.
 *
 *  (روی JVMِ کامپیوتر، چون تست‌های اندروید `com.sun.*` ندارند؛ کدِ زیرِ آزمون
 *  همان `ResumableDownload`ِ مشترکِ اندروید است.)
 */
class ResumableDownloadServerTest {

  private val dir: File = Files.createTempDirectory("resume").toFile()
  private val body = Random(7).nextBytes(3_000_000)
  private val servers = mutableListOf<Flaky>()

  @After fun tearDown() {
    servers.forEach { it.stop() }
    dir.deleteRecursively()
  }

  /** سرورِ قطع‌شونده: [cuts] پاسخِ اول پس از [cutAfter] بایت قطع می‌شوند */
  private inner class Flaky(var content: ByteArray, var etag: String, var cuts: Int, val cutAfter: Int) {
    val ranges: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    val ifRanges: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    val sent = AtomicLong()
    /** بعد از قطع‌ها این‌قدر ۵۰۳ (سرورِ ازکارافتاده) */
    var unavailable = 0
    /** بی Content-Length (اندازه نامعلوم) */
    var chunked = false
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
      createContext("/f") { handle(it) }
      start()
    }
    val url get() = "http://127.0.0.1:${server.address.port}/f"
    fun stop() = server.stop(0)

    /**
     *  «قطع» = استثنا از دستگیره بی بستنِ درستِ پاسخ: سرور اتصال را همان‌جا
     *  می‌بندد (بی پایانِ chunk و کمتر از Content-Length)، مثلِ قطعِ واقعیِ شبکه.
     */
    private fun handle(ex: HttpExchange) {
      val range = ex.requestHeaders.getFirst("Range")
      val ifRange = ex.requestHeaders.getFirst("If-Range")
      ranges += range
      ifRanges += ifRange
      if (unavailable > 0 && cuts == 0) {
        unavailable--
        ex.sendResponseHeaders(503, -1)
        ex.close()
        return
      }
      val honour = range != null && (ifRange == null || ifRange == etag)
      val start = if (honour) Regex("bytes=(\\d+)-").find(range!!)!!.groupValues[1].toInt() else 0
      val len = content.size - start
      ex.responseHeaders.add("ETag", etag)
      ex.responseHeaders.add("Accept-Ranges", "bytes")
      if (honour) ex.responseHeaders.add("Content-Range", "bytes $start-${content.size - 1}/${content.size}")
      ex.sendResponseHeaders(if (honour) 206 else 200, if (chunked) 0 else len.toLong())
      val cut = cuts > 0
      val n = if (cut) minOf(cutAfter, len) else len
      ex.responseBody.write(content, start, n)
      ex.responseBody.flush()
      sent.addAndGet(n.toLong())
      if (cut) {
        cuts--
        throw IOException("قطعِ عمدی")
      }
      ex.close()
    }
  }

  private fun flaky(cuts: Int, cutAfter: Int = 700_000, content: ByteArray = body, etag: String = "\"v1\"") =
    Flaky(content, etag, cuts, cutAfter).also { servers += it }

  private val pauses = mutableListOf<Long>()
  private val events = mutableListOf<ResumableDownload.Event>()

  private suspend fun fetch(url: String, out: File, expected: Long, backoff: List<Long> = ResumableDownload.BACKOFF_SECONDS, online: () -> Boolean = { true }) =
    ResumableDownload.fetch(
      url, out, expected,
      online = online,
      backoffSeconds = backoff,
      pause = { pauses += it },
      onEvent = { events += it },
    )

  private fun starts(s: Flaky) = s.ranges.map { r -> r?.let { Regex("bytes=(\\d+)-").find(it)!!.groupValues[1].toLong() } }

  @Test
  fun `چند بار قطع وسطِ فایل ⇒ یک بار زدن، بایت‌به‌بایت همان، هیچ بار از صفر`() = runBlocking {
    val s = flaky(cuts = 4)
    val out = File(dir, "app.bin")
    fetch(s.url, out, body.size.toLong())

    assertArrayEquals(body, out.readBytes())
    assertEquals("هر بایت فقط یک بار فرستاده شد", body.size.toLong(), s.sent.get())
    val st = starts(s)
    assertEquals(5, st.size)
    assertNull("فقط درخواستِ اول بی Range", st[0])
    assertEquals(listOf(700_000L, 1_400_000L, 2_100_000L, 2_800_000L), st.drop(1))
    assertTrue("If-Range با ETagِ پاسخِ اول", s.ifRanges.drop(1).all { it == "\"v1\"" })
    assertEquals("فاصله‌ها: پس از هر پیشرفت دوباره از ۲ ثانیه", listOf(2000L, 2000L, 2000L, 2000L), pauses)
    assertTrue(events.any { ResumableDownload.statusText(it) == "ادامه از 23٪" })
    assertFalse(ResumableDownload.partOf(out).exists())
    assertFalse(ResumableDownload.metaOf(out).exists())
  }

  @Test
  fun `نیمه با بسته شدنِ برنامه می‌ماند و بارِ بعد ادامه پیدا می‌کند`() = runBlocking {
    val s = flaky(cuts = 1)
    s.unavailable = 3
    val out = File(dir, "app.bin")
    try {
      fetch(s.url, out, body.size.toLong(), backoff = listOf(1, 1))
      fail("باید خطا می‌داد")
    } catch (e: IllegalStateException) {
      assertTrue(e.message!!, e.message!!.contains("ادامه پیدا می‌کند"))
    }
    assertEquals(700_000L, ResumableDownload.partOf(out).length())
    assertEquals("\"v1\"", ResumableDownload.readMeta(ResumableDownload.metaOf(out))!!.validator)

    //  «برنامه دوباره باز شد»: فراخوانیِ تازه، همان پوشه
    s.unavailable = 0
    val before = s.ranges.size
    fetch(s.url, out, body.size.toLong())
    assertArrayEquals(body, out.readBytes())
    assertEquals("bytes=700000-", s.ranges[before])
    assertEquals("\"v1\"", s.ifRanges[before])
    assertEquals(body.size.toLong(), s.sent.get())
  }

  @Test
  fun `اندازهٔ نامعلوم ⇒ از Content-Range، نه از صفر`() = runBlocking {
    val s = flaky(cuts = 2)
    s.chunked = true
    val out = File(dir, "app.bin")
    fetch(s.url, out, expected = 0)
    assertArrayEquals(body, out.readBytes())
    assertEquals(body.size.toLong(), s.sent.get())
    assertEquals(listOf(null, 700_000L, 1_400_000L), starts(s))
  }

  @Test
  fun `فایلِ سرور عوض شد ⇒ If-Range نخواند، ۲۰۰، از صفر و تمیز`() = runBlocking {
    val s = flaky(cuts = 1)
    val out = File(dir, "app.bin")
    s.unavailable = 1
    runCatching { fetch(s.url, out, body.size.toLong(), backoff = listOf(1)) }
    assertEquals(700_000L, ResumableDownload.partOf(out).length())

    val fresh = Random(8).nextBytes(body.size)
    s.content = fresh
    s.etag = "\"v2\""
    s.unavailable = 0
    fetch(s.url, out, body.size.toLong())
    assertArrayEquals("هیچ تکه‌ای از فایلِ قبلی نماند", fresh, out.readBytes())
  }

  @Test
  fun `بی‌اینترنت ⇒ صبر، بی درخواست و بی سوختنِ تلاش`() = runBlocking {
    val s = flaky(cuts = 0)
    val out = File(dir, "app.bin")
    var checks = 0
    fetch(s.url, out, body.size.toLong(), online = { ++checks > 4 })
    assertArrayEquals(body, out.readBytes())
    assertEquals(1, s.ranges.size)
    assertEquals("یک نگاهِ اول و سه صبرِ ۳ ثانیه‌ای", List(3) { 3000L }, pauses)
    assertTrue(events.first() is ResumableDownload.Event.Offline)
  }

  @Test
  fun `۴۰۴ ⇒ همان دم خطا، بی تلاشِ بی‌فایده`() = runBlocking {
    val s = flaky(cuts = 0)
    val out = File(dir, "app.bin")
    try {
      fetch(s.url.replace("/f", "/nothing"), out, body.size.toLong())
      fail("باید خطا می‌داد")
    } catch (e: IllegalStateException) {
      assertEquals("سرورِ دانلود جواب نداد (404)", e.message)
    }
    assertTrue(pauses.isEmpty())
  }
}
