package android.nfc.cardemulation

import android.app.Activity
import android.content.ComponentName
import android.nfc.NfcAdapter

/** shimِ iOS (گامِ ۹) — `PayDevice.HceForeground`؛ بی `NfcAdapter` هرگز ساخته نمی‌شود (‎android/nfc/NfcAdapter.kt‎) */
internal class CardEmulation private constructor() {
  fun setPreferredService(activity: Activity, service: ComponentName): Boolean = false

  fun unsetPreferredService(activity: Activity): Boolean = false

  companion object {
    fun getInstance(adapter: NfcAdapter): CardEmulation = CardEmulation()
  }
}
