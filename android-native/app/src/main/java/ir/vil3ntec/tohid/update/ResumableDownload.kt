package ir.vil3ntec.tohid.update

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

/**
 *  دانلودِ ادامه‌دارِ نصبی — مشترکِ اندروید و کامپیوتر (`Updater`ِ هر دو).
 *
 *  خواستهٔ صاحب محصول (۱۴۰۵/۰۷/۲۶): «اگر دانلود قطع شد نباید از صفر شروع شود.»
 *  پیش از این نیمهٔ فایل می‌ماند ولی دانلود با پیامِ «نیمه‌کاره ماند — دوباره
 *  بزنید» می‌ایستاد، و اگر اندازه معلوم نبود از صفر شروع می‌شد.
 *
 *  ── قاعده‌ها ───────────────────────────────────────────────────────
 *  • یک بار زدن = تا آخر: قطعِ اتصال یا پاسخِ کوتاه ⇒ خودکار دوباره، از
 *    بایت‌هایی که روی دیسک است (`Range`)، با فاصلهٔ ۲، ۴، ۸، ۱۶، ۳۰… ثانیه؛
 *    ۸ تلاشِ پیاپیِ **بی‌پیشرفت** ⇒ خطا (هر تکه‌ای که برسد شمارنده را صفر می‌کند).
 *  • بی‌اینترنت ⇒ صبر تا برگردد (هر ۳ ثانیه یک نگاه، بی چرخشِ CPU) و تلاش نمی‌سوزد.
 *  • دو فایل قاطی نمی‌شوند: اعتبارسنجِ پاسخِ اول (ETagِ قوی، وگرنه
 *    Last-Modified) کنارِ `.part` در `.part.meta` می‌ماند و با `If-Range`
 *    فرستاده می‌شود؛ `Content-Range` باید دقیقاً از همان بایتی شروع شود که
 *    خواسته شد و اندازهٔ کل باید با انتشار بخواند. ۲۰۰ به‌جای ۲۰۶ ⇒ از صفر،
 *    تمیز (همان بدنه، روی فایلِ خالی).
 *  • اندازه معلوم نیست ⇒ از `Content-Range`/`Content-Length` گرفته می‌شود،
 *    نه شروع از صفر.
 *  • `.part` و یادداشتش با بسته شدنِ برنامه نمی‌روند؛ دفعهٔ بعد ادامه.
 *  • فایلِ کامل فقط با تغییرِ نام از `.part` ساخته می‌شود؛ جمعِ کنترلی و
 *    امضا مثلِ پیش در `Updater.verify` سنجیده می‌شوند — این‌جا دست نخورده‌اند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  تصمیم دربارهٔ هر پاسخ در `decide` است — تابعِ خالص، آزموده در
 *  `ResumableDownloadTest`؛ دانلودِ واقعی با سرورِ محلیِ قطع‌شونده در
 *  `desktop/src/test/…/ResumableDownloadServerTest.kt`.
 */
object ResumableDownload {

  const val PART = ".part"
  const val META = ".part.meta"

  /** فاصلهٔ تلاش‌های پیاپیِ بی‌پیشرفت (ثانیه) — اندازه‌اش سقفِ تلاش‌هاست */
  val BACKOFF_SECONDS: List<Long> = listOf(2, 4, 8, 16, 30, 30, 30, 30)

  /** سقفِ صبر برای برگشتنِ اینترنت در یک بار زدن */
  const val OFFLINE_MAX_MS = 30 * 60 * 1000L
  private const val OFFLINE_POLL_MS = 3_000L

  /** سرور پشتِ هم فایلِ دیگری می‌دهد ⇒ بعد از این چند بار شروع از صفر، خطا */
  private const val MAX_RESTARTS = 3

  /** آنچه از یک پاسخ برای تصمیم لازم است */
  data class Response(
    val code: Int,
    val contentRange: String? = null,
    val contentLength: Long = -1,
    val etag: String? = null,
    val lastModified: String? = null,
  )

  sealed interface Step {
    /** بدنه از [offset] نوشته شود (۰ ⇒ فایلِ خالی)؛ [total] ‎-۱ یعنی هنوز نامعلوم */
    data class Write(val offset: Long, val total: Long, val validator: String) : Step
    /** آنچه روی دیسک است همهٔ فایل است */
    data class Complete(val total: Long) : Step
    /** نیمه به این فایل نمی‌خورد ⇒ پاک و از صفر */
    data class Restart(val why: String) : Step
    data class Fail(val code: Int, val retryable: Boolean) : Step
  }

  data class Span(val start: Long, val end: Long, val total: Long)

  /** `bytes 100-199/1000` ⇒ Span؛ total ‎-۱ یعنی `*` */
  fun parseContentRange(header: String?): Span? {
    val m = Regex("""^\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*$""", RegexOption.IGNORE_CASE)
      .find(header ?: return null) ?: return null
    val start = m.groupValues[1].toLongOrNull() ?: return null
    val end = m.groupValues[2].toLongOrNull() ?: return null
    val total = if (m.groupValues[3] == "*") -1L else m.groupValues[3].toLongOrNull() ?: return null
    if (end < start || (total >= 0 && end >= total)) return null
    return Span(start, end, total)
  }

  //  پاسخِ ۴۱۶: «bytes ستاره/۱۰۰۰» ⇒ ۱۰۰۰
  fun unsatisfiedTotal(header: String?): Long =
    Regex("""^\s*bytes\s+\*/(\d+)\s*$""", RegexOption.IGNORE_CASE)
      .find(header.orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: -1L

  /** `If-Range` فقط ETagِ **قوی** یا تاریخ می‌پذیرد؛ ETagِ ضعیف (‎W/‎) کنار می‌رود */
  fun validatorOf(etag: String?, lastModified: String?): String {
    val e = etag?.trim().orEmpty()
    if (e.length >= 2 && e.startsWith("\"") && e.endsWith("\"")) return e
    return lastModified?.trim().orEmpty()
  }

  /**
   *  با این پاسخ چه کنیم — بی شبکه، بی دیسک.
   *
   *  @param asked بایتی که از آن خواسته شد (۰ ⇒ بی `Range`)
   *  @param expected اندازهٔ اعلام‌شدهٔ انتشار (≤۰ ⇒ نامعلوم)
   *  @param known اندازه‌ای که پاسخِ قبلی گفته بود (≤۰ ⇒ نامعلوم)
   *  @param sent اعتبارسنجی که با `If-Range` رفت (خالی ⇒ نرفت)
   */
  fun decide(asked: Long, expected: Long, known: Long, sent: String, r: Response): Step {
    val validator = validatorOf(r.etag, r.lastModified)
    return when (r.code) {
      //  ۲۰۰ وقتی Range خواسته بودیم یعنی سرور Range نمی‌فهمد یا If-Range نخواند
      //  (فایل عوض شده) ⇒ همین بدنه از صفر، روی فایلِ خالی
      200 -> Step.Write(0, positive(expected, r.contentLength), validator)
      206 -> {
        val span = parseContentRange(r.contentRange) ?: return Step.Restart("Content-Range نامعتبر")
        when {
          span.start != asked -> Step.Restart("سرور از بایتِ ${span.start} فرستاد، نه $asked")
          span.total > 0 && expected > 0 && span.total != expected -> Step.Restart("اندازهٔ فایلِ سرور با انتشار نمی‌خواند")
          span.total > 0 && known > 0 && span.total != known -> Step.Restart("فایلِ سرور عوض شده (اندازه)")
          sent.isNotEmpty() && validator.isNotEmpty() && validator != sent -> Step.Restart("فایلِ سرور عوض شده (ETag)")
          else -> Step.Write(asked, positive(span.total, expected, known), validator.ifEmpty { sent })
        }
      }
      416 -> {
        val total = unsatisfiedTotal(r.contentRange)
        if (asked > 0 && total == asked && (expected <= 0 || expected == asked)) Step.Complete(asked)
        else Step.Restart("بازهٔ خواسته‌شده روی سرور نیست")
      }
      408, 425, 429, in 500..599 -> Step.Fail(r.code, retryable = true)
      else -> Step.Fail(r.code, retryable = false)
    }
  }

  sealed interface Event {
    /** [resumedFrom] > ۰ ⇒ این تکه از همان‌جا ادامه داده شد، نه از صفر */
    data class Progress(val done: Long, val total: Long, val resumedFrom: Long) : Event
    data class Retrying(val attempt: Int, val of: Int, val waitSeconds: Long, val done: Long, val total: Long, val why: String) : Event
    data class Offline(val done: Long, val total: Long) : Event
  }

  fun percent(done: Long, total: Long): Int =
    if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1

  /** یک خطِ کوتاه زیرِ نوارِ پیشرفت — «ادامه از ۴۲٪»، «منتظرِ اینترنت…» */
  fun statusText(e: Event): String? = when (e) {
    is Event.Progress -> if (e.resumedFrom > 0 && e.total > 0) "ادامه از ${percent(e.resumedFrom, e.total)}٪" else null
    is Event.Retrying -> "اتصال قطع شد — تلاشِ ${e.attempt} از ${e.of}، ${e.waitSeconds} ثانیهٔ دیگر" + resumeNote(e.done, e.total)
    is Event.Offline -> "منتظرِ اینترنت…" + resumeNote(e.done, e.total)
  }

  private fun resumeNote(done: Long, total: Long) =
    if (done > 0 && total > 0) " (ادامه از ${percent(done, total)}٪)" else ""

  /** یادداشتِ کنارِ `.part`: از کجا، با کدام اعتبارسنج، چند بایت */
  data class Meta(val url: String, val validator: String, val total: Long)

  fun partOf(out: File) = File(out.path + PART)
  fun metaOf(out: File) = File(out.path + META)

  /** فایل‌های همین دانلود — پاک‌سازیِ نسخه‌های دیگر به این‌ها دست نمی‌زند */
  fun belongsTo(f: File, out: File): Boolean =
    f.name == out.name || f.name == out.name + PART || f.name == out.name + META

  /**
   *  نیمه‌ای که نسخهٔ پیشینِ برنامه با همان نامِ نهایی گذاشته (بی `.part`) ⇒
   *  `.part` بی اعتبارسنج؛ ادامه فقط اگر `Content-Range` و اندازه بخوانند.
   */
  fun adoptLegacyPartial(out: File, expected: Long) {
    val part = partOf(out)
    if (expected > 0 && out.isFile && out.length() in 1 until expected && !part.exists()) out.renameTo(part)
  }

  fun readMeta(f: File): Meta? = runCatching {
    if (!f.isFile) return null
    val p = Properties().apply { f.inputStream().use { load(it) } }
    Meta(p.getProperty("url").orEmpty(), p.getProperty("validator").orEmpty(), p.getProperty("total")?.toLongOrNull() ?: -1L)
  }.getOrNull()

  private fun writeMeta(f: File, m: Meta) {
    val tmp = File(f.path + ".tmp")
    tmp.outputStream().use {
      Properties().apply {
        setProperty("url", m.url)
        setProperty("validator", m.validator)
        setProperty("total", m.total.toString())
      }.store(it, null)
    }
    //  ویندوز روی فایلِ موجود نام عوض نمی‌کند
    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
  }

  /**
   *  فایلِ [url] را در [out] می‌گذارد؛ تا وقتی تمام نشده در `out.part` است.
   *
   *  @param expected اندازهٔ اعلام‌شده (≤۰ ⇒ از پاسخ گرفته می‌شود)
   *  @param online «نت هست؟» — اندروید و کامپیوتر: `Backend.isOnline`
   *  @param reason متنِ فارسیِ کدِ HTTPِ ناامید‌کننده (۴۰۴ و…)
   *  @throws IllegalStateException با پیامِ فارسی؛ `.part` می‌ماند تا بارِ بعد
   */
  suspend fun fetch(
    url: String,
    out: File,
    expected: Long,
    online: () -> Boolean = { true },
    reason: (Int) -> String = { "سرورِ دانلود جواب نداد ($it)" },
    userAgent: String = "tohid-app",
    backoffSeconds: List<Long> = BACKOFF_SECONDS,
    pause: suspend (Long) -> Unit = { delay(it) },
    offlineMaxMs: Long = OFFLINE_MAX_MS,
    connectTimeoutMs: Int = 20_000,
    readTimeoutMs: Int = 60_000,
    onEvent: (Event) -> Unit = {},
  ): File {
    out.parentFile?.mkdirs()
    val part = partOf(out)
    val metaFile = metaOf(out)
    fun reset() { part.delete(); metaFile.delete() }

    var meta = if (part.isFile) readMeta(metaFile) else null.also { metaFile.delete() }
    //  نشانی عوض شده (سرورِ خودمان ⇄ GitHub): بایت‌ها می‌مانند، اعتبارسنجشان نه
    if (meta != null && meta.url != url) meta = meta.copy(url = url, validator = "")
    //  نیمهٔ فایلِ دیگری است
    if ((meta != null && expected > 0 && meta.total > 0 && meta.total != expected) ||
      (expected > 0 && part.length() > expected)
    ) { reset(); meta = null }

    var failures = 0
    var restarts = 0
    while (true) {
      currentCoroutineContext().ensureActive()
      val have = if (part.isFile) part.length() else 0L
      val total = positive(expected, meta?.total ?: -1L)
      if (total > 0 && have == total) return finish(part, metaFile, out)

      if (!online()) {
        onEvent(Event.Offline(have, total))
        if (!awaitOnline(online, pause, offlineMaxMs)) {
          throw IllegalStateException("اینترنت وصل نشد — بعد دوباره بزنید؛ از همین‌جا ادامه پیدا می‌کند")
        }
      }

      val sent = if (have > 0) meta?.validator.orEmpty() else ""
      var writeFrom = -1L
      val problem: String = try {
        val conn = open(url, have, sent, userAgent, connectTimeoutMs, readTimeoutMs)
        try {
          val r = Response(
            code = conn.responseCode,
            contentRange = conn.getHeaderField("Content-Range"),
            contentLength = conn.contentLengthLong,
            etag = conn.getHeaderField("ETag"),
            lastModified = conn.getHeaderField("Last-Modified"),
          )
          when (val step = decide(have, expected, meta?.total ?: -1L, sent, r)) {
            is Step.Complete -> return finish(part, metaFile, out)
            is Step.Restart -> {
              reset(); meta = null
              if (++restarts > MAX_RESTARTS) throw IllegalStateException("فایلِ سرور پشتِ هم عوض می‌شود — کمی بعد دوباره بزنید")
              continue
            }
            is Step.Fail -> {
              if (!step.retryable) throw IllegalStateException(reason(step.code))
              "HTTP ${step.code}"
            }
            is Step.Write -> {
              if (step.offset == 0L && have > 0 && ++restarts > MAX_RESTARTS) {
                throw IllegalStateException("فایلِ سرور پشتِ هم عوض می‌شود — کمی بعد دوباره بزنید")
              }
              val m = Meta(url, step.validator, step.total)
              writeMeta(metaFile, m)
              meta = m
              writeFrom = step.offset
              copy(conn, part, step.offset, step.total, onEvent)
              val now = part.length()
              when {
                step.total > 0 && now == step.total -> return finish(part, metaFile, out)
                step.total > 0 && now > step.total -> {
                  reset(); meta = null
                  if (++restarts > MAX_RESTARTS) throw IllegalStateException("فایلِ گرفته‌شده از اندازهٔ انتشار بزرگ‌تر شد — دوباره بزنید")
                  continue
                }
                //  اندازه را هیچ‌کس نگفت و جریان تمیز تمام شد ⇒ همین است (جمعِ کنترلی بعداً می‌سنجد)
                step.total <= 0 -> return finish(part, metaFile, out)
                else -> "پاسخ کوتاه بود"
              }
            }
          }
        } finally {
          conn.disconnect()
        }
      } catch (e: IOException) {
        e.message ?: "اتصال قطع شد"
      }

      val now = if (part.isFile) part.length() else 0L
      if (writeFrom >= 0 && now > writeFrom) failures = 0
      failures++
      val totalNow = positive(expected, meta?.total ?: -1L)
      if (failures > backoffSeconds.size) {
        val pct = percent(now, totalNow)
        throw IllegalStateException(
          "دانلود با ${backoffSeconds.size} بار تلاش تمام نشد ($problem)" +
            (if (pct > 0) "؛ $pct٪ گرفته شده و" else "؛") + " با زدنِ دوباره از همان‌جا ادامه پیدا می‌کند"
        )
      }
      val wait = backoffSeconds[failures - 1]
      onEvent(Event.Retrying(failures, backoffSeconds.size, wait, now, totalNow, problem))
      pause(wait * 1000)
    }
  }

  private suspend fun awaitOnline(online: () -> Boolean, pause: suspend (Long) -> Unit, maxMs: Long): Boolean {
    var waited = 0L
    while (!online()) {
      if (waited >= maxMs) return false
      pause(OFFLINE_POLL_MS)
      waited += OFFLINE_POLL_MS
    }
    return true
  }

  private fun open(url: String, from: Long, validator: String, ua: String, connectMs: Int, readMs: Int): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
      instanceFollowRedirects = true
      connectTimeout = connectMs
      readTimeout = readMs
      setRequestProperty("User-Agent", ua)
      //  بایت‌ها باید همان بایت‌های فایل باشند تا Range معنا داشته باشد
      setRequestProperty("Accept-Encoding", "identity")
      if (from > 0) {
        setRequestProperty("Range", "bytes=$from-")
        if (validator.isNotEmpty()) setRequestProperty("If-Range", validator)
      }
    }

  private suspend fun copy(conn: HttpURLConnection, part: File, offset: Long, total: Long, onEvent: (Event) -> Unit) {
    conn.inputStream.use { input ->
      FileOutputStream(part, offset > 0).use { output ->
        val buffer = ByteArray(64 * 1024)
        var done = offset
        var last = percent(done, total)
        onEvent(Event.Progress(done, total, offset))
        while (true) {
          currentCoroutineContext().ensureActive()
          val n = input.read(buffer)
          if (n < 0) break
          if (n == 0) continue
          output.write(buffer, 0, n)
          done += n
          val pct = percent(done, total)
          if (pct != last) { last = pct; onEvent(Event.Progress(done, total, offset)) }
        }
      }
    }
  }

  private fun finish(part: File, metaFile: File, out: File): File {
    if (out.exists() && !out.delete()) throw IOException("فایلِ قبلیِ ${out.name} پاک نشد")
    if (!part.renameTo(out)) {
      part.copyTo(out, overwrite = true)
      part.delete()
    }
    metaFile.delete()
    return out
  }

  private fun positive(vararg xs: Long): Long = xs.firstOrNull { it > 0 } ?: -1L
}
