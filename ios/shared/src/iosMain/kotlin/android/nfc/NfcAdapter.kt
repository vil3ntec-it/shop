package android.nfc

import android.content.Context

/*
 *  shimِ iOS (گامِ ۹) — `pay/platform/PayDevice.kt` ِ اندروید خودش روی آیفون: iOS «شبیه‌سازیِ کارت» (HCE) را به برنامهٔ
 *  شخصِ ثالث نمی‌دهد ⇐ هیچ آداپتوری (`getDefaultAdapter` = null) و `PackageManager.hasSystemFeature` نادرست، پس
 *  `PayDevice.nfc` همان «NFC نیست» ِ `PayDeviceIos` ِ پیشین است و `HceForeground` هیچ.
 */
internal class NfcAdapter private constructor() {
  val isEnabled: Boolean get() = false

  companion object {
    fun getDefaultAdapter(context: Context): NfcAdapter? = null
  }
}
