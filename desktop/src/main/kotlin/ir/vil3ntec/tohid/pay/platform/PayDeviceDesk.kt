package ir.vil3ntec.tohid.pay.platform

import android.content.Context
import androidx.compose.runtime.Composable

/**
 *  جایگزینِ کامپیوتریِ `android-native/…/pay/platform/PayDevice.kt` با همان API
 *  (`replaced` در `build.gradle.kts`): کامپیوتر NFC ندارد، پس QR ِ حساب‌پی بی «گوشی را نزدیک کنید».
 */
object PayDevice {

  enum class Nfc { NONE, OFF, READY }

  @Suppress("UNUSED_PARAMETER")
  fun nfc(context: Context): Nfc = Nfc.NONE

  @Suppress("UNUSED_PARAMETER")
  fun openNfcSettings(context: Context) = Unit

  @Suppress("UNUSED_PARAMETER")
  @Composable
  fun HceForeground(active: Boolean) = Unit

  /** کامپیوتر پنجرهٔ دومِ خودش را دارد (`CustomerDisplayScreen`، HW-10) که همان `CustomerPay` را می‌خواند */
  @Suppress("UNUSED_PARAMETER")
  @Composable
  fun CustomerScreen(active: Boolean, content: @Composable () -> Unit) = Unit
}
