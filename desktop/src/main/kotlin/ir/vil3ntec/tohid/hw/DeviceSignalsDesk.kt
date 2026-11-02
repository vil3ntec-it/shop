package ir.vil3ntec.tohid.hw

import android.content.Context
import com.fazecast.jSerialComm.SerialPort
import ir.vil3ntec.tohid.core.hw.DeviceSignal
import ir.vil3ntec.tohid.core.hw.DeviceTransport

/**
 *  «دستگاه‌ها» روی کامپیوتر: همان API ِ `hw/DeviceSignalsAndroid.kt`. هر درگاهِ COM یک دستگاه است — بلوتوثِ SPP ِ
 *  جفت‌شده در ویندوز (Standard Serial over Bluetooth link)، بارکدخوان یا ترازوی USB-CDC، مبدلِ USB به سریال. نوع از
 *  نام و شرحِ درگاه (`DeviceClassifier.byName`). جست‌وجوی بلوتوث نیست: جفت کردن در تنظیماتِ بلوتوثِ سیستم است.
 */
object DeviceSignals {
  const val SEARCH_MS = 0L

  fun search(): Boolean = false

  fun permissions(): List<String> = emptyList()

  fun granted(context: Context): Boolean = true

  fun paired(context: Context): List<DeviceSignal> = runCatching { SerialPort.getCommPorts().toList() }.getOrDefault(emptyList()).map { p ->
    DeviceSignal(
      link = "bt:" + p.systemPortName,
      name = p.systemPortName,
      transport = DeviceTransport.SERIAL,
      paired = true,
      description = listOfNotNull(p.descriptivePortName, p.portDescription).filter { it.isNotBlank() }.distinct().joinToString(" "),
    )
  }

  suspend fun nearby(context: Context, ms: Long, onFound: (DeviceSignal) -> Unit): String? =
    "روی کامپیوتر دستگاهِ بلوتوثی را در تنظیماتِ بلوتوثِ سیستم جفت کنید؛ درگاهِ COM ِ آن همین‌جا می‌آید"

  fun bond(context: Context, address: String): Boolean = false
}
