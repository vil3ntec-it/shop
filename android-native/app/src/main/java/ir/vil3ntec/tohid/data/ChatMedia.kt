package ir.vil3ntec.tohid.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import ir.vil3ntec.tohid.core.chat.ChatImage
import ir.vil3ntec.tohid.core.chat.ChatLimits
import ir.vil3ntec.tohid.ui.Platform
import java.io.ByteArrayOutputStream
import java.io.File

/**
 *  رسانهٔ چتِ دکان روی خودِ دستگاه (v3 §۳۳.۲؛ CHAT-05/06/07).
 *
 *  ── عکس ──────────────────────────────────────────────────────────────
 *  پیش از فرستادن: کوچک (≤ ۱۶۰۰px)، چرخیده طبقِ EXIF، روی زمینهٔ سفید و
 *  از نو JPEG — پس EXIF (مکان، دستگاه، زمان) دیگر در فایل نیست. پیش‌نمایشِ
 *  ≤ ۳۲۰px هم همین‌جا ساخته می‌شود. اگر در سقفِ سرور جا نشد، اول کیفیت و
 *  بعد ابعاد پایین می‌آید؛ هرگز بی‌صدا بریده نمی‌شود.
 *
 *  ── صدا ──────────────────────────────────────────────────────────────
 *  اندروید ۱۰ به بعد: Opus در OGG؛ پیش‌تر: AAC در MP4 — هر دو را سرور از
 *  روی بایت می‌شناسد. ۲۴ کیلوبیت و تک‌کاناله، مناسبِ گفتار.
 *  ⛔ کامپیوتر (JVM) رمزگذارِ Opus/AAC ندارد: دکمهٔ ضبط آن‌جا **پنهان** است
 *  و پیامِ صوتیِ رسیده با «ذخیرهٔ فایل» باز می‌شود — نه دکمه‌ای که کاری نکند.
 */
object ChatMedia {

  class Prepared(val main: ByteArray, val thumb: ByteArray, val width: Int, val height: Int)

  fun prepareImage(context: Context, uri: Uri, limits: ChatLimits): Result<Prepared> = runCatching {
    val head = context.contentResolver.openInputStream(uri)?.use { input ->
      val buf = ByteArray(256 * 1024)
      var n = 0
      while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
      buf.copyOf(n)
    } ?: error("عکس خوانده نشد")
    val orientation = ChatImage.orientationOf(head)
    val decoded = PhotoStore.decode(context, uri) ?: error("این فایل عکس نیست یا خوانده نشد")
    //  اول کوچک، بعد چرخش: حافظه به اندازهٔ عکسِ نهایی، نه عکسِ دوربین
    val (w, h) = ChatImage.fit(decoded.width, decoded.height, ChatImage.MAIN_PX)
    val scaled = if (w != decoded.width || h != decoded.height) Bitmap.createScaledBitmap(decoded, w, h, true) else decoded
    val px = IntArray(w * h)
    scaled.getPixels(px, 0, w, 0, 0, w, h)
    val (opx, ow, oh) = ChatImage.orient(ChatImage.flattenOnWhite(px), w, h, orientation)
    val oriented = Bitmap.createBitmap(opx, ow, oh, Bitmap.Config.ARGB_8888)
    val main = encodeWithin(oriented, limits.imageMax, ChatImage.MAIN_QUALITY)
    val (tw, th) = ChatImage.fit(main.second, main.third, ChatImage.THUMB_PX)
    val thumb = encodeWithin(Bitmap.createScaledBitmap(oriented, tw, th, true), limits.thumbMax, ChatImage.THUMB_QUALITY)
    Prepared(main.first, thumb.first, main.second, main.third)
  }

  /** @return (بایت‌ها، پهنا، بلندی) */
  private fun encodeWithin(source: Bitmap, cap: Long, startQuality: Int): Triple<ByteArray, Int, Int> {
    var bmp = source
    var q = startQuality
    while (true) {
      val out = ByteArrayOutputStream()
      bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
      val bytes = out.toByteArray()
      if (cap <= 0 || bytes.size <= cap) return Triple(bytes, bmp.width, bmp.height)
      if (q > ChatImage.MIN_QUALITY) { q = maxOf(ChatImage.MIN_QUALITY, q - 15); continue }
      if (maxOf(bmp.width, bmp.height) <= 160) error("عکس در سقفِ مجاز جا نشد")
      val (nw, nh) = ChatImage.fit(bmp.width, bmp.height, maxOf(bmp.width, bmp.height) * 3 / 4)
      bmp = Bitmap.createScaledBitmap(bmp, nw, nh, true)
      q = startQuality
    }
  }

  // ════════════════════════════════════════════════════════════════════
  //  صدا
  // ════════════════════════════════════════════════════════════════════

  /** آیا این دستگاه پیامِ صوتی ضبط/پخش می‌کند (اندروید: بله؛ کامپیوتر: نه). */
  val voiceSupported: Boolean get() = !Platform.DESKTOP

  const val VOICE_MAX_MS = 5 * 60 * 1000L

  class Recorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    private var started = 0L
    var file: File? = null
      private set

    val elapsedMs: Long get() = if (started == 0L) 0 else System.currentTimeMillis() - started

    fun start() {
      check(voiceSupported) { "ضبطِ صدا روی این دستگاه نیست" }
      val opus = Build.VERSION.SDK_INT >= 29
      val f = File(context.cacheDir, "chat-voice-${System.currentTimeMillis()}.${if (opus) "ogg" else "m4a"}")
      @Suppress("DEPRECATION")
      val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
      try {
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        if (Build.VERSION.SDK_INT >= 29) {
          r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
          r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
        } else {
          r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
          r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
        r.setAudioChannels(1)
        r.setAudioSamplingRate(16_000)
        r.setAudioEncodingBitRate(24_000)
        r.setMaxDuration(VOICE_MAX_MS.toInt())
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
      } catch (e: Throwable) {
        runCatching { r.release() }
        f.delete()
        throw e
      }
      rec = r
      file = f
      started = System.currentTimeMillis()
    }

    /** @return (بایت‌ها، مدت) — یا null اگر چیزی ضبط نشد. فایلِ موقت پاک می‌شود. */
    fun stop(): Pair<ByteArray, Long>? {
      val r = rec ?: return null
      val ms = elapsedMs
      val ok = runCatching { r.stop() }.isSuccess
      runCatching { r.release() }
      rec = null
      started = 0
      val f = file
      file = null
      val bytes = if (ok) f?.takeIf { it.exists() }?.readBytes() else null
      f?.delete()
      return bytes?.takeIf { it.isNotEmpty() }?.let { it to ms }
    }

    fun cancel() { stop() }
  }

  /** پخشِ یک پیامِ صوتی از بایت‌ها (فایلِ موقت در کش). */
  class Player(private val context: Context) {
    private var mp: MediaPlayer? = null
    private var tmp: File? = null
    var playingId: String? = null
      private set

    fun play(id: String, bytes: ByteArray, onDone: () -> Unit) {
      stop()
      val f = File(context.cacheDir, "chat-play-${id.filter { it.isLetterOrDigit() }}")
      f.writeBytes(bytes)
      val p = MediaPlayer()
      p.setDataSource(f.absolutePath)
      p.setOnCompletionListener { stop(); onDone() }
      p.prepare()
      p.start()
      mp = p
      tmp = f
      playingId = id
    }

    fun stop() {
      mp?.let { runCatching { it.stop() }; runCatching { it.release() } }
      mp = null
      tmp?.delete()
      tmp = null
      playingId = null
    }
  }
}
