package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 *  لرزشِ گوشی — فقط برای چند رویدادِ مهم.
 *
 *  لرزش برای هر لمس، بعد از ده دقیقه آزاردهنده می‌شود و کاربر خاموشش
 *  می‌کند؛ آن‌وقت لرزشِ مهم را هم دیگر حس نمی‌کند. پس فقط سه جا:
 *  اسکنِ موفق، ثبتِ فروش، و تأییدِ پرداخت.
 *
 *  الگوها عمداً فرق دارند: فروشنده باید بدونِ نگاه‌کردن به صفحه بفهمد چه
 *  اتفاقی افتاده — یک ضربِ کوتاه یعنی اسکن شد، سه ضرب یعنی فروش ثبت شد.
 */
object Haptics {

  /** فروش یا پرداخت ثبت شد — سه ضربِ کوتاه، همان الگوی نسخهٔ وب */
  fun success(context: Context) = pattern(context, longArrayOf(0, 40, 40, 40, 40, 40))

  /** تأییدِ یک کارِ مهم — یک ضربِ محکم */
  fun confirm(context: Context) = pattern(context, longArrayOf(0, 70))

  private fun pattern(context: Context, timings: LongArray) {
    runCatching {
      val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
      }
      if (!vibrator.hasVibrator()) return
      vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
    }
  }
}
