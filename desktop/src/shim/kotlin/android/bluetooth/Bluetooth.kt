package android.bluetooth

import com.fazecast.jSerialComm.SerialPort
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 *  بلوتوث روی کامپیوتر = **درگاه‌های سریال (COM)**.
 *
 *  JVM بلوتوثِ کلاسیک ندارد؛ ولی وقتی چاپگرِ حرارتیِ بلوتوثی (یا
 *  بارکدخوانِ حالتِ SPP) در تنظیماتِ بلوتوثِ ویندوز جفت می‌شود، ویندوز
 *  برایش یک درگاهِ COM می‌سازد (مک: ‎/dev/tty.‎…، لینوکس: ‎/dev/rfcomm‎…).
 *  همان درگاه با jSerialComm باز می‌شود و همان بایت‌های ESC/POS می‌رود —
 *  پس `ThermalPrinter.sendBluetooth`ِ اندروید بی هیچ تغییری روی
 *  کامپیوتر هم چاپ می‌کند. چاپگرِ سریالِ سیمی (RS-232 یا USB-to-Serial)
 *  هم از همین راه است.
 *
 *  «نشانی» همان نامِ سیستمیِ درگاه است (COM5، ‎/dev/tty.Printer-SPP‎).
 */
class BluetoothManager { val adapter: BluetoothAdapter? = SerialAdapter }

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

private object SerialAdapter : BluetoothAdapter() {
  /** همهٔ درگاه‌های سریال؛ بلوتوثی‌ها اول */
  override val bondedDevices: Set<BluetoothDevice>
    get() = runCatching { SerialPort.getCommPorts().toList() }.getOrDefault(emptyList())
      .sortedByDescending { looksBluetooth(it) }
      .map { SerialDevice(it.systemPortName, describe(it)) }
      .toCollection(LinkedHashSet())

  override val isEnabled: Boolean get() = true
  override fun getRemoteDevice(address: String): BluetoothDevice = SerialDevice(address, address)
  override fun cancelDiscovery(): Boolean = true

  private fun looksBluetooth(p: SerialPort): Boolean {
    val text = (p.systemPortName + " " + p.descriptivePortName + " " + p.portDescription).lowercase()
    return "bluetooth" in text || "rfcomm" in text || "spp" in text || "bth" in text
  }

  private fun describe(p: SerialPort): String {
    val d = p.descriptivePortName?.takeIf { it.isNotBlank() } ?: p.systemPortName
    return if (d.contains(p.systemPortName)) d else "$d (${p.systemPortName})"
  }
}

private class SerialDevice(override val address: String, override val name: String?) : BluetoothDevice() {
  override fun createRfcommSocketToServiceRecord(uuid: UUID): BluetoothSocket = SerialSocket(address)
  override fun createInsecureRfcommSocketToServiceRecord(uuid: UUID): BluetoothSocket = SerialSocket(address)
}

private class SerialSocket(private val portName: String) : BluetoothSocket() {
  private var port: SerialPort? = null

  override fun connect() {
    val p = SerialPort.getCommPort(portName)
    //  سرعتِ رایجِ چاپگرهای حرارتی؛ روی درگاهِ بلوتوث نادیده گرفته می‌شود
    p.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
    p.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING or SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 2000, 8000)
    if (!p.openPort(2000)) throw java.io.IOException("درگاهِ $portName باز نشد — چاپگر روشن و جفت‌شده است؟")
    port = p
  }

  override val outputStream: OutputStream
    get() = port?.outputStream ?: throw java.io.IOException("درگاه باز نیست")
  override val inputStream: InputStream
    get() = port?.inputStream ?: throw java.io.IOException("درگاه باز نیست")
  override val isConnected: Boolean get() = port?.isOpen == true

  override fun close() {
    //  پیش از بستن، بایت‌های در راه به چاپگر برسند
    runCatching { port?.flushIOBuffers() }
    runCatching { port?.closePort() }
    port = null
  }
}
