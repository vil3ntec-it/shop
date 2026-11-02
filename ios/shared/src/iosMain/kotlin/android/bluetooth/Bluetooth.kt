package android.bluetooth

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/*
 *  shimِ iOS ِ بلوتوثِ کلاسیکِ اندروید — فقط برای نوع. iOS به برنامه‌ها SPP (بلوتوثِ کلاسیک) نمی‌دهد
 *  (‎docs/IOS-PLAN-fa.md‎ §۵.۱)؛ پس `adapter` همیشه null است و کدِ مشترک «بلوتوث نیست» را می‌بیند.
 *  چاپگرِ شبکه (Socket) کار می‌کند.
 */
class BluetoothAdapter private constructor() {
  val isEnabled: Boolean get() = false
  val bondedDevices: Set<BluetoothDevice> get() = emptySet()
  fun getRemoteDevice(address: String): BluetoothDevice = BluetoothDevice(address)
  fun cancelDiscovery(): Boolean = false
  fun startDiscovery(): Boolean = false
  val isDiscovering: Boolean get() = false

  companion object {
    fun getDefaultAdapter(): BluetoothAdapter? = null
  }
}

class BluetoothManager private constructor() {
  val adapter: BluetoothAdapter? get() = null
}

class BluetoothDevice internal constructor(val address: String) {
  val name: String? get() = null
  val bondState: Int get() = BOND_NONE
  fun createRfcommSocketToServiceRecord(uuid: UUID): BluetoothSocket = throw java.io.IOException("iOS: بلوتوثِ کلاسیک نیست")
  fun createInsecureRfcommSocketToServiceRecord(uuid: UUID): BluetoothSocket = createRfcommSocketToServiceRecord(uuid)

  companion object {
    const val BOND_NONE = 10
    const val BOND_BONDED = 12
    const val ACTION_FOUND = "android.bluetooth.device.action.FOUND"
    const val EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE"
  }
}

class BluetoothSocket private constructor() : Closeable {
  val inputStream: InputStream get() = throw java.io.IOException("iOS: بلوتوثِ کلاسیک نیست")
  val outputStream: OutputStream get() = throw java.io.IOException("iOS: بلوتوثِ کلاسیک نیست")
  val isConnected: Boolean get() = false
  fun connect(): Unit = throw java.io.IOException("iOS: بلوتوثِ کلاسیک نیست")
  override fun close() {}
}
