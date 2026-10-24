package ir.vil3ntec.tohid.desktop

import com.fazecast.jSerialComm.SerialPort
import ir.vil3ntec.tohid.core.hw.SerialParams
import ir.vil3ntec.tohid.hw.SerialPorts
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 *  درگاه‌های سریالِ کامپیوتر برای ترازو (HW-09) — همان jSerialComm که
 *  چاپگرِ بلوتوث/COM را می‌برد (‎android/bluetooth/Bluetooth.kt‎).
 *
 *  ⚠️ روی ترازوی واقعی آزموده نشده (NOT-VERIFIED). هیچ ترازوی ساختگی‌ای
 *  نیست: اگر درگاهی نباشد فهرست خالی است و صفحه همین را می‌گوید.
 */
object DeskSerial : SerialPorts {

  override fun list(): List<SerialPorts.Port> = runCatching {
    SerialPort.getCommPorts().map { SerialPorts.Port(it.systemPortName, it.descriptivePortName ?: it.systemPortName) }
  }.getOrDefault(emptyList())

  override fun exchange(
    port: String,
    params: SerialParams,
    request: ByteArray,
    complete: (ByteArray) -> Boolean,
    timeoutMs: Int,
  ): ByteArray {
    val p = SerialPort.getCommPort(port)
    val parity = when (params.parity) {
      "even" -> SerialPort.EVEN_PARITY
      "odd" -> SerialPort.ODD_PARITY
      else -> SerialPort.NO_PARITY
    }
    p.setComPortParameters(params.baud, params.dataBits, if (params.stopBits == 2) SerialPort.TWO_STOP_BITS else SerialPort.ONE_STOP_BIT, parity)
    p.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 1_000)
    if (!p.openPort(500)) throw IOException("درگاهِ $port باز نشد — در دستِ برنامهٔ دیگری است؟")
    try {
      if (p.writeBytes(request, request.size) < 0) throw IOException("به درگاهِ $port نوشته نشد")
      val out = ByteArrayOutputStream()
      val buf = ByteArray(64)
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        val n = p.readBytes(buf, buf.size)
        if (n > 0) {
          out.write(buf, 0, n)
          if (complete(out.toByteArray())) break
        }
      }
      return out.toByteArray()
    } finally {
      p.closePort()
    }
  }
}
