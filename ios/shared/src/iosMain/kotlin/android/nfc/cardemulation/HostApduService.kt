package android.nfc.cardemulation

import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/*
 *  shimِ iOS (گامِ ۶) — `pay/platform/HesabPayHceService.kt` ِ اندروید (برچسبِ NFC ِ حساب‌پی با HCE) روی آیفون کامپایل
 *  می‌شود ولی هرگز ساخته نمی‌شود: iOS شبیه‌سازیِ کارت (HCE) را به برنامهٔ شخصِ ثالث برای این کار نمی‌دهد و هیچ
 *  مانیفستی آن را صدا نمی‌زند (`PayDeviceIos` ⇐ «NFC نیست»). فقط همان API، بی رفتار.
 */
abstract class HostApduService : android.app.Service() {
  final override fun onBind(intent: Intent?): IBinder? = null
  abstract fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray?
  abstract fun onDeactivated(reason: Int)
  fun sendResponseApdu(responseApdu: ByteArray?) {}
  fun notifyUnhandled() {}

  companion object {
    const val DEACTIVATION_LINK_LOSS = 0
    const val DEACTIVATION_DESELECTED = 1
  }
}
