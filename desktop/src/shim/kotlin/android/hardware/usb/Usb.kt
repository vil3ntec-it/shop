package android.hardware.usb

import android.app.PendingIntent
import java.io.ByteArrayOutputStream
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import javax.print.attribute.HashPrintRequestAttributeSet
import javax.print.attribute.standard.JobName

/**
 *  «چاپگرِ سیمی» روی کامپیوتر = **چاپگرهای نصب‌شده در سیستم**.
 *
 *  روی ویندوز و مک چاپگرِ حرارتیِ USB با راه‌اندازِ خودش نصب می‌شود و
 *  برنامه‌ها مستقیم به درگاهِ USB دست نمی‌زنند. پس این‌جا هر چاپگرِ
 *  سیستم یک «دستگاهِ USB» با ردهٔ چاپگر (۷) و یک راهِ فرستادن است، و
 *  `bulkTransfer` بایت‌ها را جمع می‌کند تا `close()` همه را یک‌جا و
 *  **خام** (RAW) به صفِ همان چاپگر بدهد — همان ESC/POSی که
 *  `ThermalPrinter.render` ساخته، بی هیچ کپی‌ای.
 *
 *  اجازه لازم نیست (`hasPermission` همیشه `true`).
 */
class UsbManager {
  val deviceList: HashMap<String, UsbDevice>
    get() = HashMap<String, UsbDevice>().apply {
      runCatching { PrintServiceLookup.lookupPrintServices(null, null) }.getOrDefault(emptyArray())
        .forEach { s -> put("sys:" + s.name, UsbDevice(s)) }
    }

  fun hasPermission(device: UsbDevice): Boolean = true
  fun requestPermission(device: UsbDevice, intent: PendingIntent?) {}
  fun openDevice(device: UsbDevice): UsbDeviceConnection? = UsbDeviceConnection(device.service)
}

class UsbDevice internal constructor(internal val service: PrintService) {
  val deviceName: String get() = "sys:" + service.name
  val productName: String? get() = service.name
  val manufacturerName: String? get() = null
  val interfaceCount: Int get() = 1
  fun getInterface(i: Int): UsbInterface = UsbInterface()
}

class UsbInterface internal constructor() {
  val interfaceClass: Int = UsbConstants.USB_CLASS_PRINTER
  val endpointCount: Int = 1
  fun getEndpoint(i: Int): UsbEndpoint = UsbEndpoint()
}

class UsbEndpoint internal constructor() {
  val type: Int = UsbConstants.USB_ENDPOINT_XFER_BULK
  val direction: Int = UsbConstants.USB_DIR_OUT
}

class UsbDeviceConnection internal constructor(private val service: PrintService) {
  private val buffer = ByteArrayOutputStream()
  private var failed: Exception? = null

  fun claimInterface(iface: UsbInterface, force: Boolean): Boolean = true
  fun releaseInterface(iface: UsbInterface): Boolean = true

  fun bulkTransfer(ep: UsbEndpoint, bytes: ByteArray, length: Int, timeout: Int): Int {
    buffer.write(bytes, 0, length)
    return length
  }

  /** همهٔ بایت‌ها یک کارِ چاپ‌اند — خام، بی راه‌اندازِ گرافیکی */
  fun close() {
    val bytes = buffer.toByteArray()
    if (bytes.isEmpty()) return
    val job = service.createPrintJob()
    val attrs = HashPrintRequestAttributeSet().apply { add(JobName("Tohid", null)) }
    job.print(SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), attrs)
  }
}

object UsbConstants {
  const val USB_CLASS_PRINTER = 7
  const val USB_ENDPOINT_XFER_BULK = 2
  const val USB_DIR_OUT = 0
  const val USB_DIR_IN = 0x80
}
