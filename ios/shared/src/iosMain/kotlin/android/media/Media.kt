package android.media

import android.content.Context
import java.io.IOException

/*
 *  shimِ iOS ِ ضبط و پخشِ صدای اندروید (پیامِ صوتیِ گفت‌وگو). AVAudioRecorder/AVAudioPlayer و اجازهٔ میکروفون
 *  گامِ ۳ است؛ تا آن روز `prepare` همان IOException ِ «نمی‌شود» ِ اندروید را می‌دهد و کدِ مشترک پیامِ خطایش را
 *  نشان می‌دهد — نه ضبطِ بی‌صدا.
 */
class MediaRecorder(context: Context? = null) {
  object AudioSource { const val MIC = 1 }
  object OutputFormat { const val MPEG_4 = 2; const val OGG = 11 }
  object AudioEncoder { const val AAC = 3; const val OPUS = 7 }

  fun setAudioSource(s: Int) {}
  fun setOutputFormat(f: Int) {}
  fun setAudioEncoder(e: Int) {}
  fun setAudioChannels(n: Int) {}
  fun setAudioSamplingRate(r: Int) {}
  fun setAudioEncodingBitRate(r: Int) {}
  fun setMaxDuration(ms: Int) {}
  fun setOutputFile(path: String) {}
  fun setOutputFile(file: java.io.File) {}
  fun prepare(): Unit = throw IOException("iOS: ضبطِ صدا هنوز نیست")
  fun start(): Unit = throw IllegalStateException("iOS: ضبطِ صدا هنوز نیست")
  fun stop() {}
  fun release() {}
  val maxAmplitude: Int get() = 0
}

class MediaPlayer {
  fun interface OnCompletionListener { fun onCompletion(mp: MediaPlayer) }

  fun setDataSource(path: String) {}
  fun setOnCompletionListener(l: OnCompletionListener?) {}
  fun prepare(): Unit = throw IOException("iOS: پخشِ صدا هنوز نیست")
  fun start() {}
  fun stop() {}
  fun pause() {}
  fun release() {}
  val isPlaying: Boolean get() = false
  val duration: Int get() = 0
  val currentPosition: Int get() = 0
}
