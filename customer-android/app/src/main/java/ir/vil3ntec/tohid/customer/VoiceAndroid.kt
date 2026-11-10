package ir.vil3ntec.tohid.customer

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.customer.ui.VoiceDevice
import java.io.File
import java.io.IOException

/**
 *  پیامِ صوتیِ گفت‌وگو روی اندروید (CHAT-07) — همان `VoiceDevice` ِ صفحه‌های مشترک:
 *    ضبط ── AAC در MP4، تک‌کاناله، ۳۲ کیلوبیت (همان نوعِ نخستِ ویترین: برنامهٔ آیفونِ دکان Opus/WebM را پخش نمی‌کند؛
 *           سرور نوع را از بایت می‌شناسد). ⛔ اجازهٔ میکروفون فقط با زدنِ دکمه (`RECORD_AUDIO`، پرسشِ سیستم)؛ فایلِ موقت در
 *           کش و پس از هر پایان یا لغو پاک؛ میکروفون با هر بیرون‌رفتن بسته.
 *    پخش ── `MediaPlayer` از فایلِ موقتِ کش (بایت‌ها با بلیت از سرور آمده‌اند)؛ رمزگشا نشناخت ⇒ false (پیامِ روشن).
 *  [activity] فقط برای پرسشِ اجازه و پوشهٔ کش؛ این شیء باید پیش از `onCreate` ساخته شود (`registerForActivityResult`).
 */
class VoiceAndroid(private val activity: ComponentActivity) : VoiceDevice {
  private var asked: ((String?) -> Unit)? = null
  private val ask = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    val done = asked ?: return@registerForActivityResult
    asked = null
    done(if (granted) begin() else VoiceDevice.DENIED)
  }
  private var rec: MediaRecorder? = null
  private var file: File? = null
  private var player: MediaPlayer? = null
  private var played: File? = null

  override val canRecord: Boolean
    get() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

  override fun record(done: (String?) -> Unit) {
    if (!canRecord) return done(VoiceDevice.NO_MIC)
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
      done(begin())
    } else {
      asked = done
      ask.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  /** null = در حالِ ضبط؛ وگرنه دلیل: آماده نشد ⇒ FAIL، آغاز نشد (میکروفون دستِ دیگری) ⇒ BUSY */
  private fun begin(): String? {
    cancel()
    val f = File(activity.cacheDir, "chat-voice-" + System.currentTimeMillis() + ".m4a")
    @Suppress("DEPRECATION")
    val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(activity) else MediaRecorder()
    try {
      r.setAudioSource(MediaRecorder.AudioSource.MIC)
      r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      r.setAudioChannels(1)
      r.setAudioSamplingRate(16_000)
      r.setAudioEncodingBitRate(BIT_RATE)
      r.setOutputFile(f.absolutePath)
      r.prepare()
    } catch (_: Exception) {
      runCatching { r.release() }
      f.delete()
      return VoiceDevice.FAIL
    }
    try {
      r.start()
    } catch (_: Exception) {
      runCatching { r.release() }
      f.delete()
      return VoiceDevice.BUSY
    }
    rec = r
    file = f
    return null
  }

  override fun recorded(): Long = file?.length() ?: 0

  override fun finish(): ByteArray? {
    val r = rec ?: return null
    val ok = runCatching { r.stop() }.isSuccess   //  ضبطِ بسیار کوتاه ⇒ stop خطا می‌دهد ⇒ «چیزی ضبط نشد»
    runCatching { r.release() }
    rec = null
    val f = file
    file = null
    val bytes = try {
      if (ok) f?.takeIf { it.exists() }?.readBytes() else null
    } catch (_: IOException) {
      null
    }
    f?.delete()
    return bytes?.takeIf { it.isNotEmpty() }
  }

  override fun cancel() {
    asked = null
    rec?.let { r ->
      runCatching { r.stop() }
      runCatching { r.release() }
    }
    rec = null
    file?.delete()
    file = null
  }

  override fun canPlay(mime: String?): Boolean = mime == null || mime.startsWith("audio/")

  override fun play(bytes: ByteArray, ended: () -> Unit): Boolean {
    stopPlay()
    val f = File(activity.cacheDir, "chat-play-" + System.currentTimeMillis())
    val p = MediaPlayer()
    return try {
      f.writeBytes(bytes)
      p.setDataSource(f.absolutePath)
      p.setOnCompletionListener { ended() }
      p.prepare()
      p.start()
      player = p
      played = f
      true
    } catch (_: Exception) {
      runCatching { p.release() }
      f.delete()
      false
    }
  }

  override fun pause() {
    player?.let { p -> runCatching { if (p.isPlaying) p.pause() } }
  }

  override fun resume() {
    player?.let { p -> runCatching { p.start() } }
  }

  override fun stopPlay() {
    player?.let { p ->
      runCatching { p.stop() }
      runCatching { p.release() }
    }
    player = null
    played?.delete()
    played = null
  }

  override fun position(): Long = player?.let { p -> runCatching { p.currentPosition.toLong() }.getOrNull() } ?: 0

  override fun length(): Long = player?.let { p -> runCatching { p.duration.toLong() }.getOrNull()?.takeIf { it > 0 } } ?: 0

  /** `onDestroy`: میکروفون و پخش بسته، فایل‌های موقت پاک */
  fun release() {
    cancel()
    stopPlay()
  }

  private companion object {
    const val BIT_RATE = 32_000
  }
}
