package android.os

import java.util.Timer
import kotlin.concurrent.schedule

class Bundle

class CancellationSignal { fun cancel() {} }

class Looper private constructor() {
  companion object {
    private val main = Looper()
    fun getMainLooper(): Looper = main
    fun myLooper(): Looper? = main
  }
}

/** `Handler` — کارِ تأخیری روی یک رشتهٔ پس‌زمینه. */
class Handler(@Suppress("UNUSED_PARAMETER") looper: Looper? = null) {
  private val timer by lazy { Timer("handler", true) }
  fun post(r: Runnable): Boolean { javax.swing.SwingUtilities.invokeLater(r); return true }
  fun postDelayed(r: Runnable, ms: Long): Boolean { timer.schedule(ms) { javax.swing.SwingUtilities.invokeLater(r) }; return true }
  fun removeCallbacksAndMessages(token: Any?) {}
}

/** کامپیوتر لرزاننده ندارد. `hasVibrator` همیشه `false`. */
open class Vibrator {
  fun hasVibrator(): Boolean = false
  fun vibrate(effect: VibrationEffect) {}
  fun vibrate(ms: Long) {}
  fun cancel() {}
}

class VibratorManager { val defaultVibrator: Vibrator = Vibrator() }

class VibrationEffect private constructor() {
  companion object {
    const val DEFAULT_AMPLITUDE = -1
    fun createWaveform(timings: LongArray, repeat: Int) = VibrationEffect()
    fun createOneShot(ms: Long, amplitude: Int) = VibrationEffect()
  }
}
