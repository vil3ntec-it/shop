package ir.vil3ntec.tohid.hw

import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.BurstGate
import ir.vil3ntec.tohid.core.hw.DeviceKind
import ir.vil3ntec.tohid.core.hw.DeviceRoster
import ir.vil3ntec.tohid.core.hw.DeviceState
import ir.vil3ntec.tohid.core.hw.DeviceVerdict
import ir.vil3ntec.tohid.core.hw.Discovery
import ir.vil3ntec.tohid.core.hw.PoleDisplay
import ir.vil3ntec.tohid.core.hw.Reconnect
import ir.vil3ntec.tohid.core.hw.SavedDevice
import ir.vil3ntec.tohid.core.hw.Scales
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.scan.ScanCashRun
import ir.vil3ntec.tohid.scan.ScanInbox
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 *  «دستگاه‌ها» — وصلِ هم‌زمانِ چند دستگاهِ بلوتوث/سریال، وصلِ دوبارهٔ خودکار و وضعیتِ هر کدام (منطق: `core/hw/DeviceHub.kt`).
 *
 *  همین پرونده روی گوشی و کامپیوتر است: «بلوتوث» ِ کامپیوتر درگاهِ COM است (`android/bluetooth/Bluetooth.kt` ِ
 *  کامپیوتر با jSerialComm)، پس بارکدخوانِ SPP ِ جفت‌شده در ویندوز و بارکدخوانِ USB-CDC (درگاهِ COM) هم از همین راه
 *  خوانده می‌شوند — همان راهِ ترازوی HW-09.
 *
 *  - **بارکدخوان** (نقشِ SCANNER، «وصلِ خودکار» روشن): یک رشتهٔ خواننده برای هر کدام که وصل می‌ماند و پس از قطع با
 *    پله‌های `Reconnect` برمی‌گردد. هر سطر از `BurstGate` می‌گذرد: تک‌اسکن ⇒ `ScanInbox.arrive`، بستهٔ حافظه ⇒
 *    `ScanInbox.importMemory`. روی اندروید سرویسِ پیش‌رو (`ScanServiceAndroid`) نگهشان می‌دارد؛ روی کامپیوتر سینی.
 *  - **چاپگر، ترازو، نمایشگر**: وصلِ دائم نمی‌خواهند (چاپگرِ بلوتوثی یک وصل بیشتر نمی‌پذیرد و چاپ خودش وصل می‌شود)؛
 *    وضعیتشان از آزمونِ کوتاه (`probe`) و آزمونِ چاپ/وزن/نمایش است.
 *
 *  ⚠️ روی بارکدخوانِ واقعی آزموده نشده (NOT-VERIFIED)؛ دروازهٔ بسته و صف آزمون دارند (`ScanQueueTest`).
 */
object DeviceLinks {
  private const val PREFS = "tohid"
  private const val KEY = "device_roster"
  /** سطرِ بی پایان‌نویسه (بارکدخوانی که Enter نمی‌فرستد) پس از این سکوت تمام است */
  private const val PARTIAL_MS = 300L
  private const val TICK_MS = 40L

  private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

  /** وضعیتِ هر دستگاه با کلیدِ `Discovery.key` */
  val states = mutableStateMapOf<String, DeviceState>()

  /** پیامِ آخرین آزمون یا خطای هر دستگاه */
  val notes = mutableStateMapOf<String, String>()

  /** نوع‌شناسیِ آخرین جست‌وجو — برای «حالتِ صفحه‌کلید» وقتی وصلِ سریال نشد */
  val verdicts = ConcurrentHashMap<String, DeviceVerdict>()

  /** فهرستِ ثبت‌شده (همان پرونده‌ای که صفحه می‌خواند) */
  val roster = mutableStateOf<List<SavedDevice>>(emptyList())

  private val workers = ConcurrentHashMap<String, Worker>()
  @Volatile private var ticker: Thread? = null
  @Volatile private var loaded = false

  fun load(context: Context): List<SavedDevice> {
    if (!loaded) {
      roster.value = DeviceRoster.decode(runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) }.getOrNull())
      loaded = true
    }
    return roster.value
  }

  fun save(context: Context, list: List<SavedDevice>) {
    roster.value = list
    loaded = true
    runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, DeviceRoster.encode(list)).apply() }
  }

  fun key(link: String): String = Discovery.key(link)

  fun state(link: String): DeviceState? = states[key(link)]

  private fun address(link: String): String = link.trim().removePrefix("bt:")

  /** سوکتِ SPP (کامپیوتر: درگاهِ COM) — وصل‌شده؛ نشد ⇒ استثنا */
  fun open(context: Context, link: String): BluetoothSocket {
    val adapter = ThermalPrinter.adapter(context) ?: throw java.io.IOException("بلوتوث روی این دستگاه نیست")
    if (!adapter.isEnabled) throw IllegalStateException("off")
    runCatching { adapter.cancelDiscovery() }
    val device = adapter.getRemoteDevice(address(link))
    return try {
      device.createRfcommSocketToServiceRecord(SPP).also { it.connect() }
    } catch (e: java.io.IOException) {
      //  بعضی بارکدخوان‌های ارزان فقط وصلِ ناامن را می‌پذیرند
      device.createInsecureRfcommSocketToServiceRecord(SPP).also { it.connect() }
    }
  }

  /**
   *  کارگرها را با فهرست هم‌سو می‌کند: هر بارکدخوانِ «وصلِ خودکار» یک خواننده؛ بقیه بسته. سرویسِ پیش‌رو و سینیِ
   *  کامپیوتر همین را صدا می‌زنند.
   */
  @Synchronized fun syncWorkers(context: Context) {
    val app = context.applicationContext ?: context
    ScanInbox.load(app)
    val want = DeviceRoster.backgroundScanners(load(app)).associateBy { key(it.link) }
    workers.keys.filter { !want.containsKey(it) }.forEach { k -> workers.remove(k)?.stop(); states.remove(k) }
    want.forEach { (k, d) -> if (!workers.containsKey(k)) workers[k] = Worker(app, d).also { it.start() } }
    if (workers.isNotEmpty() && ticker == null) startTicker()
    //  صفی که پیش از بسته شدنِ برنامه «نقد» خورده بود
    if (workers.isNotEmpty()) ScanCashRun.kick(app)
  }

  @Synchronized fun stopWorkers() {
    workers.values.forEach { it.stop() }
    workers.keys.forEach { states.remove(it) }
    workers.clear()
  }

  /** بارکدخوانی که همین حالا خوانده می‌شود */
  fun running(): Int = workers.values.count { states[it.key] == DeviceState.CONNECTED }

  private fun startTicker() {
    ticker = Thread({
      while (true) {
        try { Thread.sleep(TICK_MS) } catch (e: InterruptedException) { return@Thread }
        val now = System.currentTimeMillis()
        workers.values.forEach { runCatching { it.tick(now) } }
      }
    }, "scanner-ticker").apply { isDaemon = true; start() }
  }

  /* ─────────────────────────────── آزمون‌ها ─────────────────────────────── */

  /** وصلِ کوتاه: چاپگر با `ThermalPrinter.probe`، بقیه با باز و بسته کردنِ سوکت. خطا ⇒ پیام */
  fun probe(context: Context, d: SavedDevice): String? {
    val k = key(d.link)
    if (workers.containsKey(k)) return if (states[k] == DeviceState.CONNECTED) null else notes[k]
    states[k] = DeviceState.CONNECTING
    val error = when (d.kind) {
      DeviceKind.PRINTER -> ThermalPrinter.probe(context, ThermalPrinter.Link.Bluetooth(address(d.link))).let { if (it.ok) null else it.message }
      else -> try { open(context, d.link).close(); null } catch (e: SecurityException) { NO_PERMISSION } catch (e: Exception) { e.message ?: OFFLINE }
    }
    states[k] = when {
      error == null -> DeviceState.CONNECTED
      error == NO_PERMISSION -> DeviceState.NO_PERMISSION
      verdicts[k]?.keyboard == true -> DeviceState.KEYBOARD_MODE
      else -> DeviceState.OFFLINE
    }
    if (error != null) notes[k] = error else notes.remove(k)
    //  آزمونِ کوتاه وصل نگه نمی‌دارد — سبز یعنی «همین حالا رسید»
    return error
  }

  /** چاپِ شناسایی (ASCII، بی صفِ چاپ) */
  fun testPrint(context: Context, d: SavedDevice): String? =
    ThermalPrinter.send(context, ThermalPrinter.Link.Bluetooth(address(d.link)), ir.vil3ntec.tohid.core.hw.EasyTestPrint.bytes(d.link))

  /** خواندنِ یک وزن با همهٔ پروتکل‌های شناخته — نخستین پاسخِ خوانا */
  fun testWeigh(context: Context, d: SavedDevice): String {
    val port = address(d.link)
    var last = OFFLINE
    for (p in Scales.all) {
      val r = if (HwPlatform.serial != null) ScaleReader.read(port, p) else exchange(context, d.link, p.request(), p::complete, 1_500)
        ?.let { bytes -> p.parse(bytes)?.let { ScaleReader.Result.Ok(it) } ?: ScaleReader.Result.Failed("پاسخ خوانده نشد") }
        ?: ScaleReader.Result.Failed(OFFLINE)
      when (r) {
        is ScaleReader.Result.Ok -> return "${p.id.uppercase()}: ${r.reading.kg ?: "—"} kg"
        is ScaleReader.Result.Failed -> last = r.message
      }
    }
    return last
  }

  /** نمایشگرِ مشتری: دو سطرِ آزمون */
  fun testDisplay(context: Context, d: SavedDevice): String? {
    val frame = PoleDisplay.frame("TOHID  TEST", "0123456789")
    return if (HwPlatform.serial != null) Pole.test(address(d.link))
    else if (exchange(context, d.link, frame, { true }, 50) != null) null else OFFLINE
  }

  /** فرستادن و خواندن روی سوکتِ SPP (گوشی) تا پاسخِ کامل یا مهلت */
  private fun exchange(context: Context, link: String, request: ByteArray, complete: (ByteArray) -> Boolean, timeoutMs: Long): ByteArray? = runCatching {
    open(context, link).use { s ->
      s.outputStream.write(request); s.outputStream.flush()
      val out = ByteArrayOutputStream()
      val end = System.currentTimeMillis() + timeoutMs
      val buf = ByteArray(256)
      while (System.currentTimeMillis() < end && !complete(out.toByteArray())) {
        if (s.inputStream.available() > 0) { val n = s.inputStream.read(buf); if (n < 0) break; out.write(buf, 0, n) } else Thread.sleep(20)
      }
      out.toByteArray()
    }
  }.getOrNull()

  const val NO_PERMISSION = "اجازهٔ «دستگاه‌های نزدیک» داده نشده"
  const val OFFLINE = "دستگاه پاسخ نداد — روشن و نزدیک است؟"

  /** خوانندهٔ یک بارکدخوان */
  private class Worker(private val app: Context, private val device: SavedDevice) {
    val key: String = DeviceLinks.key(device.link)
    @Volatile private var running = true
    @Volatile private var socket: BluetoothSocket? = null
    private val lock = Any()
    private val gate = BurstGate.Gate()
    private val line = ByteArrayOutputStream()
    private var lastByteAt = 0L
    private val thread = Thread({ loop() }, "scanner-" + device.link).apply { isDaemon = true }

    fun start() = thread.start()

    fun stop() {
      running = false
      runCatching { socket?.close() }
      thread.interrupt()
    }

    private fun set(s: DeviceState) { if (running) states[key] = s }

    private fun pause(ms: Long) { try { Thread.sleep(ms) } catch (e: InterruptedException) { running = false } }

    private fun loop() {
      var attempt = 0
      while (running) {
        set(DeviceState.CONNECTING)
        val s = try {
          open(app, device.link)
        } catch (e: SecurityException) {
          set(DeviceState.NO_PERMISSION); notes[key] = NO_PERMISSION; pause(Reconnect.CAP_MS); continue
        } catch (e: IllegalStateException) {
          set(DeviceState.BLUETOOTH_OFF); pause(Reconnect.delayMs(attempt)); attempt++; continue
        } catch (e: Exception) {
          set(if (verdicts[key]?.keyboard == true) DeviceState.KEYBOARD_MODE else DeviceState.OFFLINE)
          notes[key] = e.message ?: OFFLINE
          pause(Reconnect.delayMs(attempt)); attempt++; continue
        }
        socket = s
        val at = System.currentTimeMillis()
        set(DeviceState.CONNECTED); notes.remove(key)
        try { read(s) } catch (e: Exception) { /* قطع شد */ }
        runCatching { s.close() }
        socket = null
        emit(synchronized(lock) { endLine(System.currentTimeMillis()) + gate.flush() })
        attempt = Reconnect.nextAttempt(attempt, System.currentTimeMillis() - at)
        if (running) { set(DeviceState.OFFLINE); pause(Reconnect.delayMs(attempt)) }
      }
    }

    private fun read(s: BluetoothSocket) {
      val input = s.inputStream
      val buf = ByteArray(512)
      while (running) {
        //  درگاهِ COM ِ کامپیوتر با سکوت «مهلت» می‌دهد — قطع نیست
        val n = try { input.read(buf) } catch (e: InterruptedIOException) { continue }
        if (n < 0) return
        val now = System.currentTimeMillis()
        val out = synchronized(lock) {
          val acc = ArrayList<BurstGate.Out>()
          for (i in 0 until n) {
            val b = buf[i].toInt()
            if (b == '\r'.code || b == '\n'.code) acc += endLine(now) else line.write(b)
          }
          lastByteAt = now
          acc
        }
        emit(out)
      }
    }

    /** باید زیرِ `lock` صدا زده شود */
    private fun endLine(now: Long): List<BurstGate.Out> {
      if (line.size() == 0) return emptyList()
      val text = String(line.toByteArray(), Charsets.UTF_8)
      line.reset()
      return gate.offer(text, now)
    }

    fun tick(now: Long) {
      val out = synchronized(lock) {
        val partial = if (line.size() > 0 && now - lastByteAt > PARTIAL_MS) endLine(lastByteAt) else emptyList()
        partial + gate.tick(now)
      }
      emit(out)
    }

    private fun emit(out: List<BurstGate.Out>) = out.forEach { o ->
      val queued = when (o) {
        is BurstGate.Out.Live -> ScanInbox.arrive(device.link, o.line, o.atMs)
        is BurstGate.Out.Burst -> ScanInbox.importMemory(device.link, o.lines, o.atMs) > 0
      }
      //  «کارتِ فرمان» (پیش‌فرض خاموش ⇒ هیچ): فاکتورِ «نقد»‌خورده ثبت یا نگه‌داشته می‌شود (`ScanCashRun`)
      if (queued) ScanCashRun.kick(app)
    }
  }
}
