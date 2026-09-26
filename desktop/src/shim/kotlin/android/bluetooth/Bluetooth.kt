package android.bluetooth

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 *  بلوتوثِ کلاسیک (SPP) از JVM در دسترس نیست. `BluetoothManager.adapter`
 *  این‌جا `null` است و `ThermalPrinter` پیامِ خودش را می‌دهد. چاپگرِ
 *  بلوتوثیِ جفت‌شده روی ویندوز و مک به‌شکلِ **چاپگرِ سیستم** دیده می‌شود
 *  و از برگهٔ «سیم» (چاپگرهای نصب‌شده) در دسترس است.
 */
class BluetoothManager { val adapter: BluetoothAdapter? = null }

abstract class BluetoothAdapter {
  abstract val bondedDevices: Set<BluetoothDevice>?
  abstract val isEnabled: Boolean
  abstract fun getRemoteDevice(address: String): BluetoothDevice
  abstract fun cancelDiscovery(): Boolean
}

abstract class BluetoothDevice {
  abstract val name: String?
  abstract val address: String
  abstract fun createRfcommSocketToServiceRecord(uuid: UUID): BluetoothSocket
  abstract fun createInsecureRfcommSocketToServiceRecord(uuid: UUID): BluetoothSocket
}

abstract class BluetoothSocket : java.io.Closeable {
  abstract val outputStream: OutputStream
  abstract val inputStream: InputStream
  abstract val isConnected: Boolean
  abstract fun connect()
}
