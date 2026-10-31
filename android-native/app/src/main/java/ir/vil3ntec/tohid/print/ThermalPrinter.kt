package ir.vil3ntec.tohid.print

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.OutputStream
import ir.vil3ntec.tohid.core.i18n.StrLocalNet
import ir.vil3ntec.tohid.hw.LocalNetworkAccess
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 *  چاپ روی چاپگرِ حرارتی — همان ۵۸ و ۸۰ میلی‌متری‌های دکان.
 *
 *  چرا جدا از چاپگرِ اندروید: این چاپگرها معمولاً در چارچوبِ چاپِ اندروید
 *  دیده نمی‌شوند. با آن‌ها باید مستقیم حرف زد: دستورهای ESC/POS روی هر
 *  سیمی که وصلشان می‌کند.
 *
 *  فاکتور به‌صورت تصویر فرستاده می‌شود، نه متن. دلیلش فارسی است: این
 *  چاپگرها جدولِ نویسه‌های فارسی ندارند و متنِ فارسی را به‌هم‌ریخته یا
 *  علامت سؤال چاپ می‌کنند. تصویر همان چیزی است که روی صفحه دیده می‌شود.
 *
 *  ── سه راهِ وصل شدن ────────────────────────────────────────────────
 *  تا دیروز فقط **بلوتوث** بود. ولی چاپگرِ هر دکان یک‌جور وصل می‌شود و
 *  آن یکی راه، رایج‌ترینش هم نیست:
 *
 *    • **بلوتوث** — چاپگرهای کوچکِ سیار، جفت‌شده در تنظیماتِ گوشی
 *    • **شبکه/وای‌فای** — چاپگرهای ۸۰ میلی‌متریِ روی پیشخان. یک IP در
 *      شبکهٔ دکان دارند و روی درگاه ۹۱۰۰ خام گوش می‌دهند
 *    • **سیم (USB/OTG)** — چاپگر با کابل به گوشی یا تبلت
 *
 *  هر سه **همان بایت‌ها** را می‌گیرند؛ فقط لوله فرق می‌کند. پس
 *  `render()` یک بار ساخته می‌شود و سه فرستنده همان را می‌برند — نه سه
 *  کپی از منطقِ ESC/POS که روزی یکی‌شان اصلاح شود و دو تای دیگر نه.
 *  ──────────────────────────────────────────────────────────────────
 */
object ThermalPrinter {

  /** پروفایل پورت سریالِ بلوتوث — همان چیزی که این چاپگرها ارائه می‌دهند */
  private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

  /** درگاهِ استانداردِ چاپِ خام روی شبکه — تقریباً همهٔ چاپگرها همین را دارند */
  const val RAW_PORT = 9100

  /** عرضِ کاغذ بر حسب نقطه: ۵۸ میلی‌متر = ۳۸۴، ۸۰ میلی‌متر = ۵۷۶ */
  const val WIDTH_58MM = 384
  const val WIDTH_80MM = 576

  private const val CONNECT_TIMEOUT_MS = 6_000
  private const val WRITE_TIMEOUT_MS = 8_000

  data class Printer(val name: String, val address: String)

  /* ============================ کدام چاپگر ============================ */

  /**
   *  چاپگرِ انتخاب‌شده — و اینکه با چه چیزی وصل است.
   *
   *  به شکلِ یک رشته ذخیره می‌شود تا همان یک کلیدِ `printer_address` که
   *  از قبل بود کافی باشد. نشانیِ لختِ بلوتوث (بدونِ پیشوند) هم پذیرفته
   *  است، پس گوشی‌ای که دیروز چاپگرش را انتخاب کرده بود، امروز بی‌هیچ
   *  کاری همان را دارد.
   */
  sealed class Link {

    data class Bluetooth(val address: String) : Link()
    data class Network(val host: String, val port: Int = RAW_PORT) : Link()

    /**
     *  @param deviceName نامِ دستگاه در `UsbManager` — نه نامِ نمایشی.
     *    شمارهٔ دستگاه با هر بار وصل شدن عوض می‌شود، پس همین نام تنها
     *    چیزی است که می‌شود ذخیره‌اش کرد.
     */
    data class Usb(val deviceName: String) : Link()

    /** برای نوشتن در حافظهٔ گوشی */
    fun save(): String = when (this) {
      is Bluetooth -> "bt:$address"
      is Network -> "net:$host:$port"
      is Usb -> "usb:$deviceName"
    }

    /** چیزی که به کاربر نشان داده می‌شود */
    fun label(): String = when (this) {
      is Bluetooth -> address
      is Network -> if (port == RAW_PORT) host else "$host:$port"
      is Usb -> deviceName.substringAfterLast('/')
    }

    companion object {
      /**
       *  خواندنِ همان رشته.
       *
       *  ⚠️ رشتهٔ بی‌پیشوند «بلوتوث» فهمیده می‌شود، چون نسخه‌های قبلی
       *  نشانیِ MAC را لخت می‌نوشتند. اگر این را برداریم، چاپگرِ هر
       *  دکانی که از قبل تنظیم شده «پیدا نمی‌شود» — بی آنکه کسی
       *  بفهمد چرا.
       */
      fun parse(raw: String?): Link? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return when {
          s.startsWith("bt:") -> Bluetooth(s.removePrefix("bt:"))
          s.startsWith("usb:") -> Usb(s.removePrefix("usb:"))
          s.startsWith("net:") -> {
            val rest = s.removePrefix("net:")
            val host = rest.substringBeforeLast(':', rest)
            val port = rest.substringAfterLast(':', "").toIntOrNull() ?: RAW_PORT
            if (host.isBlank()) null else Network(host, port)
          }
          //  نشانیِ لختِ نسخه‌های قبلی
          else -> Bluetooth(s)
        }
      }
    }
  }

  /* ============================ پیدا کردنِ چاپگرها ============================ */

  /** چاپگرهایی که قبلاً در تنظیماتِ بلوتوثِ گوشی جفت شده‌اند */
  fun paired(context: Context): List<Printer> {
    val adapter = adapter(context) ?: return emptyList()
    return try {
      adapter.bondedDevices.orEmpty().map { Printer(it.name ?: it.address, it.address) }
    } catch (e: SecurityException) {
      emptyList()
    }
  }

  fun adapter(context: Context): BluetoothAdapter? =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

  fun usbManager(context: Context): UsbManager? =
    context.getSystemService(Context.USB_SERVICE) as? UsbManager

  /**
   *  چاپگرهای وصل‌شده با سیم.
   *
   *  فقط دستگاه‌هایی که واقعاً چاپگرند: ردهٔ ۷ در USB یعنی «Printer».
   *  بی این صافی، هر چیزی که به کابل وصل است — ماوس، حافظه، شارژر
   *  هوشمند — در فهرست می‌آمد و کاربر باید از بینشان حدس می‌زد.
   */
  fun usbPrinters(context: Context): List<Printer> {
    val manager = usbManager(context) ?: return emptyList()
    return manager.deviceList.orEmpty().values
      .filter { printerInterface(it) != null }
      .map { Printer(it.productName ?: it.deviceName.substringAfterLast('/'), it.deviceName) }
  }

  fun usbDevice(context: Context, deviceName: String): UsbDevice? =
    usbManager(context)?.deviceList?.get(deviceName)

  private fun printerInterface(device: UsbDevice): UsbInterface? {
    for (i in 0 until device.interfaceCount) {
      val candidate = device.getInterface(i)
      if (candidate.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return candidate
    }
    return null
  }

  private fun bulkOut(iface: UsbInterface): UsbEndpoint? {
    for (i in 0 until iface.endpointCount) {
      val ep = iface.getEndpoint(i)
      if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
        ep.direction == UsbConstants.USB_DIR_OUT
      ) return ep
    }
    return null
  }

  /* ============================ چاپ ============================ */

  /**
   *  چاپِ یک تصویر روی چاپگرِ داده‌شده.
   *
   *  خطا **برگردانده** می‌شود، نه پرتاب: چاپ نشدنِ فاکتور یک اتفاقِ
   *  عادیِ دکان است (چاپگر خاموش، کاغذ تمام، از شبکه افتاده) و کاربر
   *  باید پیامش را ببیند، نه اینکه هیچ اتفاقی نیفتد.
   *
   *  @return `null` یعنی چاپ شد؛ وگرنه پیامِ فارسیِ خطا.
   */
  fun print(context: Context, link: Link, bitmap: Bitmap, paperWidth: Int): String? {
    val bytes = try {
      render(bitmap, paperWidth)
    } catch (e: OutOfMemoryError) {
      return "فاکتور برای حافظهٔ این گوشی بزرگ است"
    } catch (e: Exception) {
      return "فاکتور آماده نشد: ${e.message ?: "خطای ناشناخته"}"
    }

    return send(context, link, bytes)
  }

  /**
   *  یک فرستنده در هر لحظه: صفِ چاپ، ضربهٔ کشو، برگهٔ آزمون، چاپِ قدیمی
   *  (`PrintJob`) و «بررسیِ اتصال» هم‌زمان به چاپگر نمی‌نویسند — بایت‌های دو
   *  کار در هم نمی‌روند و بلوتوث دو اتصالِ هم‌زمان نمی‌خواهد.
   */
  private val wire = ReentrantLock()

  /** نتیجهٔ فرستادن: `error == null` یعنی رفت؛ `wrote` یعنی دستِ‌کم یک تکه به چاپگر سپرده شد */
  data class Sent(val error: String?, val wrote: Boolean)

  /**
   *  بایت‌های آماده روی همان سه راه — صفِ چاپ، برچسب، کشو و برگهٔ آزمون.
   *  @return `null` یعنی رفت؛ وگرنه پیامِ فارسیِ خطا.
   */
  fun send(context: Context, link: Link, bytes: ByteArray): String? = wire.withLock { sendRaw(context, link, bytes).error }

  /**
   *  همان، ولی «همین حالا یا هرگز» (ضربهٔ کشو): اگر چاپگر تا `waitMs` آزاد
   *  نشد یا `admit` پس از گرفتنِ نوبت «نه» گفت، هیچ نمی‌رود (`null`).
   */
  fun sendWithin(context: Context, link: Link, bytes: ByteArray, waitMs: Long, admit: () -> Boolean = { true }): Sent? {
    if (!wire.tryLock(waitMs, TimeUnit.MILLISECONDS)) return null
    try {
      if (!admit()) return null
      return sendRaw(context, link, bytes)
    } finally {
      wire.unlock()
    }
  }

  private fun sendRaw(context: Context, link: Link, bytes: ByteArray): Sent {
    val wrote = AtomicBoolean(false)
    val err = runCatching {
      when (link) {
        is Link.Bluetooth -> sendBluetooth(context, link.address, bytes, wrote)
        is Link.Network -> if (LocalNetworkAccess.blocked(context, link.host)) StrLocalNet.denied else sendNetwork(link.host, link.port, bytes, wrote)
        is Link.Usb -> sendUsb(context, link.deviceName, bytes, wrote)
      }
    }.getOrElse { it.message ?: "چاپ نشد" }
    return Sent(err, wrote.get())
  }

  /**
   *  بررسیِ سلامت (HW-11، هشدارِ HW-02): وصل می‌شود و `DLE EOT 1/2/3/4` را **یکی‌یکی**
   *  می‌پرسد (آفلاین، درِ باز، کاغذِ تمام یا رو به پایان، تیغه و خطا). چیزی چاپ نمی‌شود.
   *  چاپگری که پاسخ ندهد هنوز ممکن است چاپ کند — بعضی چاپگرها اصلاً وضعیت نمی‌گویند؛
   *  همین گفته می‌شود.
   *
   *  ⚠️ روی چاپگرِ واقعی آزموده نشده (NOT-VERIFIED)؛ ترتیبِ پرسش‌ها، خواندنِ پاسخ و
   *  پیام (`core/hw/PrinterStatus`) آزمون دارند (`PrinterDrawerTest`).
   */
  fun probe(context: Context, link: Link, full: Boolean = false): Probe = wire.withLock { probeNow(context, link, full) }

  /** `full` = «ابزارهای تازهٔ دستگاه» (`HwTools`، پیش‌فرض خاموش): چهار پرسش؛ وگرنه فقط کاغذ، مثلِ پیش */
  private fun probeNow(context: Context, link: Link, full: Boolean): Probe = when (link) {
    is Link.Network -> if (LocalNetworkAccess.blocked(context, link.host)) Probe(false, StrLocalNet.denied) else probeStream(link.label(), full) {
      val socket = Socket()
      socket.connect(InetSocketAddress(link.host, link.port), CONNECT_TIMEOUT_MS)
      socket.soTimeout = PROBE_READ_MS
      socket
    }
    is Link.Bluetooth -> {
      val adapter = adapter(context)
      when {
        adapter == null -> Probe(false, "بلوتوث روی این دستگاه نیست")
        !adapter.isEnabled -> Probe(false, "بلوتوث خاموش است")
        else -> probeStream(link.address, full) {
          val s = adapter.getRemoteDevice(link.address).createRfcommSocketToServiceRecord(SPP)
          adapter.cancelDiscovery()
          s.connect()
          s
        }
      }
    }
    is Link.Usb -> {
      val manager = usbManager(context)
      val device = manager?.deviceList?.get(link.deviceName)
      when {
        device == null -> Probe(false, "چاپگرِ سیمی پیدا نشد — کابل وصل است؟")
        manager?.hasPermission(device) != true -> Probe(false, "اجازهٔ دسترسی به چاپگرِ سیمی داده نشده")
        else -> Probe(true, "وصل است — وضعیتِ کاغذ از راهِ سیم خوانده نمی‌شود")
      }
    }
  }

  data class Probe(val ok: Boolean, val message: String, val paper: ir.vil3ntec.tohid.core.hw.PaperStatus? = null)

  private const val PROBE_READ_MS = 1_500

  /** رفتارِ پیشین (تنظیمِ تازه خاموش): فقط `DLE EOT 4`، همان پیام‌ها */
  private fun paperOnly(label: String, input: java.io.InputStream, output: java.io.OutputStream): Probe {
    output.write(ir.vil3ntec.tohid.core.hw.EscPos.PAPER_STATUS)
    output.flush()
    val b = try { input.read() } catch (e: java.io.InterruptedIOException) { -1 }
    val paper = if (b >= 0) ir.vil3ntec.tohid.core.hw.EscPos.parsePaperStatus(b) else null
    return when {
      paper == null -> Probe(true, "وصل شد ($label) — چاپگر وضعیتِ کاغذ را نگفت")
      paper.out -> Probe(false, "کاغذ تمام شده است", paper)
      paper.nearEnd -> Probe(true, "وصل است — کاغذ رو به پایان است", paper)
      else -> Probe(true, "وصل است و کاغذ دارد", paper)
    }
  }

  /**
   *  یک بایت تا `timeoutMs`، وگرنه ‎-1. سوکتِ بلوتوث مهلتِ خواندن ندارد و `read()` ِ
   *  چاپگرِ بی‌پاسخ برای همیشه می‌ماند؛ پس با `available()` منتظر می‌مانیم.
   */
  private fun readByteWithin(input: java.io.InputStream, timeoutMs: Long): Int {
    val until = System.currentTimeMillis() + timeoutMs
    return try {
      while (input.available() <= 0) {
        if (System.currentTimeMillis() >= until) return -1
        Thread.sleep(20)
      }
      input.read()
    } catch (e: java.io.InterruptedIOException) {
      -1
    }
  }

  private fun probeStream(label: String, full: Boolean, open: () -> java.io.Closeable): Probe {
    var conn: java.io.Closeable? = null
    return try {
      conn = open()
      val (input, output) = when (val c = conn) {
        is Socket -> c.getInputStream() to c.getOutputStream()
        is android.bluetooth.BluetoothSocket -> c.inputStream to c.outputStream
        else -> return Probe(true, "وصل شد")
      }
      if (!full) return paperOnly(label, input, output)
      val ask = ir.vil3ntec.tohid.core.hw.PrinterStatus.Probe()
      while (true) {
        val q = ask.next() ?: break
        output.write(q)
        output.flush()
        ask.answer(readByteWithin(input, PROBE_READ_MS.toLong()))
      }
      val state = ask.state()
      val v = ir.vil3ntec.tohid.core.hw.PrinterStatus.verdict(state, label)
      Probe(v.ok, v.message, state.paper)
    } catch (e: SecurityException) {
      Probe(false, "اجازهٔ بلوتوث داده نشده")
    } catch (e: Exception) {
      Probe(false, "وصل نشد ($label): ${e.message ?: "پاسخی نیامد"}")
    } finally {
      try { conn?.close() } catch (e: Exception) { /* بسته شده */ }
    }
  }

  /**
   *  همان، با نشانیِ خام — برای کدی که از قبل این‌طور صدا می‌زد.
   *
   *  ⚠️ سرِ جایش می‌ماند: `Link.parse` نشانیِ لختِ بلوتوث را هم می‌فهمد،
   *  پس این یکی هر سه راه را می‌گیرد و چیزی نمی‌شکند.
   */
  fun print(context: Context, address: String, bitmap: Bitmap, paperWidth: Int): String? {
    val link = Link.parse(address) ?: return "چاپگری انتخاب نشده است"
    return print(context, link, bitmap, paperWidth)
  }

  /* ---------------------------- بلوتوث ---------------------------- */

  private fun sendBluetooth(context: Context, address: String, bytes: ByteArray, wrote: AtomicBoolean): String? {
    val adapter = adapter(context) ?: return "بلوتوث روی این گوشی نیست"
    if (!adapter.isEnabled) return "بلوتوث خاموش است"

    val device: BluetoothDevice = try {
      adapter.getRemoteDevice(address)
    } catch (e: Exception) {
      return "چاپگر پیدا نشد"
    }

    var socket: android.bluetooth.BluetoothSocket? = null
    return try {
      socket = device.createRfcommSocketToServiceRecord(SPP)
      adapter.cancelDiscovery()
      socket.connect()
      socket.outputStream.use { out -> writeChunked(out, bytes, wrote) }
      null
    } catch (e: SecurityException) {
      "اجازهٔ بلوتوث داده نشده"
    } catch (e: Exception) {
      "چاپ نشد: ${e.message ?: "اتصال برقرار نشد"}"
    } finally {
      try { socket?.close() } catch (e: Exception) { /* بسته شده */ }
    }
  }

  /* ---------------------------- شبکه / وای‌فای ---------------------------- */

  /**
   *  چاپگرِ روی شبکه — درگاهِ ۹۱۰۰، خام و بی هیچ پروتکلی.
   *
   *  مهلت‌ها عمدی‌اند: چاپگری که خاموش است یا از شبکه افتاده، بدونِ
   *  مهلت **دقیقه‌ها** برنامه را نگه می‌دارد و فروشنده فکر می‌کند
   *  برنامه هنگ کرده. شش ثانیه برای یک شبکهٔ محلی خیلی زیاد هم هست.
   */
  private fun sendNetwork(host: String, port: Int, bytes: ByteArray, wrote: AtomicBoolean): String? {
    var socket: Socket? = null
    return try {
      socket = Socket()
      socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
      socket.soTimeout = WRITE_TIMEOUT_MS
      //  بی این، بستنِ سوکت می‌تواند بایت‌های آخر را دور بریزد و
      //  فاکتور نصفه چاپ شود
      socket.setSoLinger(true, 5)
      socket.getOutputStream().use { out -> writeChunked(out, bytes, wrote) }
      null
    } catch (e: java.net.SocketTimeoutException) {
      "چاپگر در شبکه جواب نداد ($host) — روشن است؟"
    } catch (e: java.net.ConnectException) {
      "به چاپگر وصل نشد ($host) — نشانی و شبکه را ببینید"
    } catch (e: java.net.UnknownHostException) {
      "نشانیِ چاپگر شناخته نشد ($host)"
    } catch (e: Exception) {
      "چاپ نشد: ${e.message ?: "اتصال برقرار نشد"}"
    } finally {
      try { socket?.close() } catch (e: Exception) { /* بسته شده */ }
    }
  }

  /* ---------------------------- سیم (USB) ---------------------------- */

  /**
   *  چاپگرِ وصل با کابل.
   *
   *  ⚠️ اجازهٔ USB را خودِ اندروید با یک پنجره می‌گیرد و بدونِ آن
   *  `openDevice` خالی برمی‌گردد. صفحه پیش از چاپ اجازه را می‌خواهد؛
   *  اینجا فقط پیامِ روشنش گفته می‌شود تا کاربر بداند چه شده.
   */
  private fun sendUsb(context: Context, deviceName: String, bytes: ByteArray, wrote: AtomicBoolean): String? {
    val manager = usbManager(context) ?: return "این گوشی از چاپگرِ سیمی پشتیبانی نمی‌کند"
    val device = manager.deviceList?.get(deviceName)
      ?: return "چاپگرِ سیمی پیدا نشد — کابل وصل است؟"

    if (!manager.hasPermission(device)) return "اجازهٔ دسترسی به چاپگرِ سیمی داده نشده"

    val iface = printerInterface(device) ?: return "این دستگاه چاپگر نیست"
    val endpoint = bulkOut(iface) ?: return "راهِ فرستادن روی این چاپگر پیدا نشد"

    var connection: UsbDeviceConnection? = null
    return try {
      connection = manager.openDevice(device) ?: return "چاپگرِ سیمی باز نشد"
      if (!connection.claimInterface(iface, true)) return "چاپگرِ سیمی در دستِ برنامهٔ دیگری است"

      var sent = 0
      while (sent < bytes.size) {
        val size = minOf(CHUNK, bytes.size - sent)
        val slice = bytes.copyOfRange(sent, sent + size)
        val moved = connection.bulkTransfer(endpoint, slice, slice.size, WRITE_TIMEOUT_MS)
        if (moved < 0) return "چاپ نیمه‌کاره ماند — کابل را ببینید"
        wrote.set(true)
        sent += size
      }
      null
    } catch (e: Exception) {
      "چاپ نشد: ${e.message ?: "اتصالِ سیمی برقرار نشد"}"
    } finally {
      try { connection?.releaseInterface(iface) } catch (e: Exception) { /* رها شده */ }
      try { connection?.close() } catch (e: Exception) { /* بسته شده */ }
    }
  }

  /* ============================ ساختنِ بایت‌ها ============================ */

  /** تکهٔ نوشتن — بافرِ این چاپگرها کوچک است و یک‌جا نمی‌پذیرد */
  private const val CHUNK = 4096

  private fun writeChunked(out: OutputStream, bytes: ByteArray, wrote: AtomicBoolean) {
    var sent = 0
    while (sent < bytes.size) {
      val size = minOf(CHUNK, bytes.size - sent)
      out.write(bytes, sent, size)
      wrote.set(true)
      out.flush()
      sent += size
    }
  }

  /**
   *  کلِ کارِ چاپ، به شکلِ بایت.
   *
   *  یک بار ساخته می‌شود و هر سه راه همین را می‌برند. تا دیروز این
   *  منطق داخلِ فرستندهٔ بلوتوث بود؛ اگر همان‌جا می‌ماند، هر راهِ تازه
   *  یک کپی از ESC/POS می‌خواست و روزی یکی‌شان اصلاح می‌شد و بقیه نه.
   */
  fun render(bitmap: Bitmap, paperWidth: Int): ByteArray =
    renderWith(bitmap, ir.vil3ntec.tohid.core.hw.PrinterProfile(paperDots = paperWidth))

  /**
   *  همان، با پروفایلِ چاپگر (حالتِ `ESC *`، برش، کشو). بایت‌ها را
   *  `core/hw/EscPos` می‌سازد — همان بایت‌هایی که پیاده‌سازیِ مرجعِ
   *  `server/test/hw-core-ref.js` با بردارهای `hw-v1.json` می‌سازد؛ پیش‌فرضِ پروفایل عیناً بایت‌های پیشینِ این تابع است و آزمونِ
   *  `HwCoreTest` همین را می‌پاید.
   */
  fun renderWith(
    bitmap: Bitmap,
    profile: ir.vil3ntec.tohid.core.hw.PrinterProfile,
    openDrawer: Boolean = false,
  ): ByteArray = ir.vil3ntec.tohid.core.hw.EscPos.document(toMono(bitmap, profile.paperDots), profile, openDrawer)

  /** تصویر ⇐ یک‌بیتی به عرضِ کاغذ (نسبت نگه داشته می‌شود) */
  fun toMono(bitmap: Bitmap, paperWidth: Int): ir.vil3ntec.tohid.core.hw.MonoImage {
    val scaled = scale(bitmap, paperWidth)
    val px = IntArray(scaled.width * scaled.height)
    scaled.getPixels(px, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    if (scaled !== bitmap) scaled.recycle()
    return ir.vil3ntec.tohid.core.hw.MonoImage.fromArgb(px, scaled.width, scaled.height, paperWidth)
  }

  /** پیش‌نمایش: یک‌بیتی ⇐ تصویرِ سیاه‌وسفید برای صفحه */
  fun toBitmap(image: ir.vil3ntec.tohid.core.hw.MonoImage): Bitmap {
    val px = IntArray(image.width * image.height)
    for (y in 0 until image.height) for (x in 0 until image.width) {
      px[y * image.width + x] = if (image[x, y]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(px, image.width, image.height, Bitmap.Config.ARGB_8888)
  }

  /** تصویر را به عرضِ کاغذ می‌رساند و نسبتش را نگه می‌دارد */
  private fun scale(source: Bitmap, width: Int): Bitmap {
    if (source.width == width) return source
    val height = (source.height.toFloat() * width / source.width).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, width, height, true)
  }
}
