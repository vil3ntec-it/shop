package android.media

import android.content.Context

/**
 *  `MediaRecorder` و `MediaPlayer` ِ اندروید روی کامپیوتر — **فقط برای کامپایل**.
 *
 *  JVM رمزگذار/رمزگشای Opus و AAC ندارد، پس پیامِ صوتیِ چتِ دکان روی کامپیوتر
 *  ضبط و پخش نمی‌شود: `ChatMedia.voiceSupported` این‌جا false است، دکمهٔ ضبط
 *  پنهان می‌ماند و صدای رسیده با «ذخیرهٔ فایل» باز می‌شود. اگر کسی با این حال
 *  این‌ها را صدا بزند، خطای روشن می‌گیرد، نه سکوت.
 */
class MediaRecorder() {
  @Suppress("UNUSED_PARAMETER") constructor(context: Context) : this()

  object AudioSource { const val MIC = 1 }
  object OutputFormat { const val MPEG_4 = 2; const val OGG = 11 }
  object AudioEncoder { const val AAC = 3; const val OPUS = 7 }

  private fun no(): Nothing = throw UnsupportedOperationException("ضبطِ صدا روی کامپیوتر پشتیبانی نمی‌شود")

  fun setAudioSource(source: Int): Unit = no()
  fun setOutputFormat(format: Int): Unit = no()
  fun setAudioEncoder(encoder: Int): Unit = no()
  fun setAudioChannels(channels: Int): Unit = no()
  fun setAudioSamplingRate(rate: Int): Unit = no()
  fun setAudioEncodingBitRate(rate: Int): Unit = no()
  fun setMaxDuration(ms: Int): Unit = no()
  fun setOutputFile(path: String): Unit = no()
  fun prepare(): Unit = no()
  fun start(): Unit = no()
  fun stop(): Unit = no()
  fun release() {}
}

class MediaPlayer {
  fun interface OnCompletionListener { fun onCompletion(mp: MediaPlayer?) }

  private fun no(): Nothing = throw UnsupportedOperationException("پخشِ صدای Opus/AAC روی کامپیوتر پشتیبانی نمی‌شود")

  fun setDataSource(path: String): Unit = no()
  fun setOnCompletionListener(listener: OnCompletionListener?) {}
  fun prepare(): Unit = no()
  fun start(): Unit = no()
  fun stop() {}
  fun release() {}
}
