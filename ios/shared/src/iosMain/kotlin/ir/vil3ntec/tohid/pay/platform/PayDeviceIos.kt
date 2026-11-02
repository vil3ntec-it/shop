package ir.vil3ntec.tohid.pay.platform

import android.content.Context
import androidx.compose.runtime.Composable

/*
 *  جایگزینِ iOS ِ `pay/platform/PayDevice.kt` (همان API ِ ‎PayDeviceDesk‎ ِ کامپیوتر). NFC ِ «شبیه‌سازیِ کارت»
 *  (HCE) را iOS به برنامه‌ها نمی‌دهد و نمایشگرِ دومِ مشتری روی آیفون نیست ⇒ همان «NFC نیست» و بی صفحهٔ مشتری.
 */
object PayDevice {
  enum class Nfc { NONE, OFF, READY }

  fun nfc(context: Context): Nfc = Nfc.NONE

  fun openNfcSettings(context: Context) = Unit

  @Composable
  fun HceForeground(active: Boolean) = Unit

  @Composable
  fun CustomerScreen(active: Boolean, content: @Composable () -> Unit) = Unit
}
