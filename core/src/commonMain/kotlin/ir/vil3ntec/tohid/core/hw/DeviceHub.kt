package ir.vil3ntec.tohid.core.hw

/**
 *  «دستگاه‌ها» (HW-12/HW-05/HW-09/HW-10، درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱): همهٔ دستگاه‌های بلوتوث و سریالِ دکان در یک
 *  فهرست — یک «جست‌وجو»، نوعِ هر دستگاه خودکار، چند دستگاهِ هم‌زمان، وصلِ دوبارهٔ خودکار و وضعیتِ سبز/سرخ.
 *
 *  فقط منطق (بی اندروید، بی سوکت): گوشی، تبلت، کامپیوتر و آیفون همین را می‌خوانند. نشانه‌ها را مرزِ پلتفرم
 *  می‌دهد — اندروید ردهٔ بلوتوث و سرویس‌های BLE، کامپیوتر نام و شرحِ درگاهِ COM.
 *
 *  ── نوع از کجا ─────────────────────────────────────────────────────
 *    ۱. ردهٔ دستگاه (Class of Device): «Imaging + چاپگر» ⇒ چاپگر؛ «Peripheral + اسکنرِ دستی» (‎0x20) ⇒ بارکدخوان؛
 *       «Health + ترازو» ⇒ ترازو؛ گوشی، رایانه، صدا، پوشیدنی ⇒ «دیگر» (دستگاهِ دکان نیست).
 *    ۲. سرویس‌های BLE: ترازوی وزن (‎0x181D)، چاپگرِ BLE ِ رایج (‎0x18F0 و ‎e7810a71…)، HID (‎0x1812 ⇒ حالتِ صفحه‌کلید).
 *    ۳. نام: الگوهای رایجِ بازار (ترازو پیش از بارکدخوان، بارکدخوان پیش از چاپگر — «POS Scanner» بارکدخوان است).
 *  رده‌ای که روشن است بر نام برتری دارد؛ نامِ روشن بر ردهٔ «نامشخص».
 *
 *  ── حالتِ صفحه‌کلید (HID) ─────────────────────────────────────────
 *  بارکدخوانی که مثلِ صفحه‌کلید وصل است فقط وقتی برنامه جلوی چشم است چیزی می‌فرستد؛ در پس‌زمینه اسکنش گم می‌شود.
 *  پس `keyboard` جدا گزارش می‌شود تا صفحه راهنمای «حالتِ SPP» را نشان دهد (بارکدِ تنظیمِ دفترچهٔ خودِ بارکدخوان).
 */
enum class DeviceKind { PRINTER, SCANNER, SCALE, DISPLAY, OTHER, UNKNOWN }

/** راهِ وصل: بلوتوثِ کلاسیک، BLE، هر دو، یا درگاهِ سریال (COM ِ کامپیوتر: بلوتوثِ SPP یا USB-CDC) */
enum class DeviceTransport { CLASSIC, BLE, DUAL, SERIAL }

/**
 *  آنچه پلتفرم دربارهٔ یک دستگاه می‌داند. [link] همان قالبِ `printer_address`: ‎`bt:AA:BB:…`، کامپیوتر ‎`bt:COM5`
 *  (درگاهِ COM همان «بلوتوث» ِ کامپیوتر است — `android/bluetooth/Bluetooth.kt` ِ کامپیوتر).
 */
data class DeviceSignal(
  val link: String,
  val name: String,
  /** ردهٔ کاملِ دستگاه (`BluetoothClass.deviceClass`)؛ `null` ⇒ نامعلوم (BLE یا COM) */
  val btClass: Int? = null,
  /** شناسه‌های سرویس (۱۲۸ بیتی یا ۱۶ بیتی، هر شکلی) */
  val services: Set<String> = emptySet(),
  val transport: DeviceTransport = DeviceTransport.CLASSIC,
  val paired: Boolean = false,
  /** شرحِ درگاه روی کامپیوتر («Standard Serial over Bluetooth link (COM5)») */
  val description: String = "",
)

/**
 *  @param keyboard دستگاه خودش را صفحه‌کلید معرفی می‌کند (HID) — اسکنش در پس‌زمینه گرفته نمی‌شود
 *  @param serial سرویسِ سریال (SPP یا UART ِ BLE) دارد یا درگاهِ COM است — اسکن در پس‌زمینه گرفتنی است
 *  @param sure نوع از رده یا سرویس یا نامِ روشن آمده، نه از حدس
 */
data class DeviceVerdict(val kind: DeviceKind, val keyboard: Boolean = false, val serial: Boolean = false, val sure: Boolean = false)

object DeviceClassifier {
  const val MAJOR_MASK = 0x1F00
  const val COMPUTER = 0x0100
  const val PHONE = 0x0200
  const val NETWORK = 0x0300
  const val AUDIO_VIDEO = 0x0400
  const val PERIPHERAL = 0x0500
  const val IMAGING = 0x0600
  const val WEARABLE = 0x0700
  const val TOY = 0x0800
  const val HEALTH = 0x0900

  /** Peripheral: بیت‌های ۶-۷ صفحه‌کلید/اشاره‌گر؛ بیت‌های ۲-۵ نوع — ‎0x20 «اسکنرِ دستیِ بارکد و RFID» */
  const val PERIPHERAL_KEYBOARD = 0x40
  const val PERIPHERAL_SUBTYPE_MASK = 0x3C
  const val PERIPHERAL_BARCODE = 0x20
  const val PERIPHERAL_CARD_READER = 0x18

  /** Imaging: بیت‌های ۴ تا ۷ — نمایشگر ‎0x10، دوربین ‎0x20، اسکنرِ کاغذ ‎0x40، چاپگر ‎0x80 */
  const val IMAGING_DISPLAY = 0x10
  const val IMAGING_PRINTER = 0x80

  /** Health: ‎0x0C ترازوی وزن */
  const val HEALTH_SUBTYPE_MASK = 0xFC
  const val HEALTH_SCALE = 0x0C

  /** سرویس‌های شناخته — ۱۶ بیتیِ پایهٔ بلوتوث یا ۱۲۸ بیتیِ کامل */
  const val SPP = "1101"
  const val HID = "1812"
  const val WEIGHT_SCALE = "181d"
  const val BLE_PRINTER = "18f0"
  const val BLE_PRINTER_2 = "e7810a71-73ae-499d-8c15-faa9aef0c3f2"
  const val NORDIC_UART = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

  private val SCALE_HINTS = listOf("scale", "weigh", "ترازو", "acs-", "dibal", "toledo", "mettler", "kern ")
  private val SCANNER_HINTS = listOf(
    "scan", "barcode", "bar code", "netum", "inateck", "eyoyo", "symcode", "tera-", "honeywell", "datalogic",
    "opticon", "unitech", "zebra ds", "ds22", "ct10", "bs-", "nt-1", "nt-w", "1d ", "2d ", "qr ", "بارکد",
  )
  private val DISPLAY_HINTS = listOf("display", "vfd", "pole", "customer", "نمایشگر")

  /** ‎`0000xxxx-0000-1000-8000-00805f9b34fb` ⇒ ‎`xxxx`؛ بقیه همان (کوچک) */
  fun short(uuid: String): String {
    val u = uuid.trim().lowercase()
    val base = "-0000-1000-8000-00805f9b34fb"
    return if (u.length == 36 && u.endsWith(base) && u.startsWith("0000")) u.substring(4, 8) else u
  }

  private fun hint(name: String, list: List<String>): Boolean {
    val n = " " + name.lowercase() + " "
    return list.any { n.contains(it) }
  }

  /** نوع از نام (و شرحِ درگاه) — ترازو، بارکدخوان، نمایشگر، چاپگر؛ هیچ ⇒ `null` */
  fun byName(text: String): DeviceKind? = when {
    hint(text, SCALE_HINTS) -> DeviceKind.SCALE
    hint(text, SCANNER_HINTS) -> DeviceKind.SCANNER
    hint(text, DISPLAY_HINTS) -> DeviceKind.DISPLAY
    BtClass.nameLooksLikePrinter(text) -> DeviceKind.PRINTER
    else -> null
  }

  /** نوع از ردهٔ دستگاه؛ ردهٔ نامشخص ⇒ `null`. [keyboard] جدا: `keyboardClass` */
  fun byClass(cod: Int): DeviceKind? {
    val major = cod and MAJOR_MASK
    return when (major) {
      IMAGING -> when {
        cod and IMAGING_PRINTER != 0 -> DeviceKind.PRINTER
        cod and IMAGING_DISPLAY != 0 -> DeviceKind.DISPLAY
        else -> null
      }
      PERIPHERAL -> when (cod and PERIPHERAL_SUBTYPE_MASK) {
        PERIPHERAL_BARCODE -> DeviceKind.SCANNER
        PERIPHERAL_CARD_READER -> DeviceKind.OTHER
        else -> null
      }
      HEALTH -> if (cod and HEALTH_SUBTYPE_MASK == HEALTH_SCALE) DeviceKind.SCALE else DeviceKind.OTHER
      COMPUTER, PHONE, NETWORK, AUDIO_VIDEO, WEARABLE, TOY -> DeviceKind.OTHER
      else -> null
    }
  }

  fun keyboardClass(cod: Int): Boolean = cod and MAJOR_MASK == PERIPHERAL && cod and PERIPHERAL_KEYBOARD != 0

  fun classify(s: DeviceSignal): DeviceVerdict {
    val services = s.services.map(::short).toSet()
    val text = (s.name + " " + s.description).trim()
    val named = byName(text)
    val classed = s.btClass?.let(::byClass)
    val keyboard = (s.btClass?.let(::keyboardClass) ?: false) || HID in services
    val serial = s.transport == DeviceTransport.SERIAL || SPP in services || NORDIC_UART in services
    val serviced = when {
      WEIGHT_SCALE in services -> DeviceKind.SCALE
      BLE_PRINTER in services || BLE_PRINTER_2 in services -> DeviceKind.PRINTER
      else -> null
    }
    val kind = when {
      classed != null && classed != DeviceKind.OTHER -> classed
      serviced != null -> serviced
      //  ردهٔ روشنِ غیرِ دکان (گوشی، هدفون…) — نامِ «Printer phone» عوضش نمی‌کند
      classed == DeviceKind.OTHER -> DeviceKind.OTHER
      //  صفحه‌کلیدِ بی رده با نامِ بارکدخوان: بارکدخوانِ حالتِ HID
      named != null -> named
      keyboard -> DeviceKind.OTHER
      else -> DeviceKind.UNKNOWN
    }
    val sure = (classed != null && classed == kind) || (serviced != null && serviced == kind) || (named != null && named == kind)
    return DeviceVerdict(kind, keyboard = keyboard && kind == DeviceKind.SCANNER, serial = serial, sure = sure)
  }

  /** دستگاهی که در فهرستِ «دستگاه‌ها» می‌آید: «دیگر» (گوشی، هدفون، صفحه‌کلیدِ معمولی) نه */
  fun shopDevice(v: DeviceVerdict): Boolean = v.kind != DeviceKind.OTHER
}

/** وضعیتِ هر دستگاه — سبز فقط [CONNECTED] */
enum class DeviceState { CONNECTED, CONNECTING, OFFLINE, KEYBOARD_MODE, NO_PERMISSION, BLUETOOTH_OFF }

/**
 *  یک دستگاهِ ثبت‌شده. [kind] همان نقشی است که صاحب‌کار گذاشته (پیش‌فرض: نوعِ خودکار). [auto] ⇒ وصلِ خودکار و
 *  دوباره‌وصل پس از قطع؛ برای بارکدخوان همین یعنی «گرفتنِ اسکن در پس‌زمینه» (کلیدِ جدا ندارد).
 */
data class SavedDevice(val link: String, val name: String, val kind: DeviceKind, val auto: Boolean = true)

object DeviceRoster {
  private const val SEP = '\t'

  private fun clean(s: String): String = s.map { if (it.code < 32) ' ' else it }.joinToString("").trim()

  /** یک سطر برای هر دستگاه: ‎`link⇥kind⇥auto⇥name` */
  fun encode(list: List<SavedDevice>): String = list.joinToString("\n") {
    listOf(clean(it.link), it.kind.name, if (it.auto) "1" else "0", clean(it.name)).joinToString(SEP.toString())
  }

  /** سطرِ نادرست کنار می‌رود؛ نشانیِ تکراری فقط یک بار (اولی) */
  fun decode(raw: String?): List<SavedDevice> {
    if (raw.isNullOrBlank()) return emptyList()
    val out = LinkedHashMap<String, SavedDevice>()
    raw.lineSequence().forEach { line ->
      val p = line.split(SEP)
      if (p.size < 4 || p[0].isBlank()) return@forEach
      val kind = DeviceKind.entries.firstOrNull { it.name == p[1] } ?: return@forEach
      val key = Discovery.key(p[0])
      if (key !in out) out[key] = SavedDevice(p[0], p.subList(3, p.size).joinToString(" "), kind, p[2] == "1")
    }
    return out.values.toList()
  }

  /** افزودن یا به‌روز کردن (هم‌نشانی جایگزین می‌شود، جایش در فهرست می‌ماند) */
  fun upsert(list: List<SavedDevice>, d: SavedDevice): List<SavedDevice> {
    val k = Discovery.key(d.link)
    return if (list.any { Discovery.key(it.link) == k }) list.map { if (Discovery.key(it.link) == k) d else it } else list + d
  }

  fun remove(list: List<SavedDevice>, link: String): List<SavedDevice> = list.filterNot { Discovery.key(it.link) == Discovery.key(link) }

  /** بارکدخوان‌هایی که باید وصل بمانند (اسکن در پس‌زمینه) — بی بارکدخوان ⇒ سرویسِ پس‌زمینه هم نه */
  fun backgroundScanners(list: List<SavedDevice>): List<SavedDevice> = list.filter { it.auto && it.kind == DeviceKind.SCANNER }

  /** همهٔ دستگاه‌هایی که وصلِ خودکار دارند */
  fun autoConnect(list: List<SavedDevice>): List<SavedDevice> = list.filter { it.auto }

  /**
   *  فهرستِ صفحه: ثبت‌شده‌ها اول (به ترتیبِ ثبت)، بعد یافته‌های تازهٔ دستگاهِ دکان (نوعِ روشن اول، بعد نام).
   *  یافته‌ای که ثبت شده دوباره نمی‌آید؛ «دیگر» (گوشی، هدفون) هرگز.
   */
  fun merge(saved: List<SavedDevice>, found: List<Pair<DeviceSignal, DeviceVerdict>>): List<Pair<DeviceSignal, DeviceVerdict>> {
    val keys = saved.map { Discovery.key(it.link) }.toSet()
    val seen = HashSet<String>()
    return found.filter { (s, v) -> DeviceClassifier.shopDevice(v) && Discovery.key(s.link) !in keys && seen.add(Discovery.key(s.link)) }
      .sortedWith(compareBy({ it.second.kind == DeviceKind.UNKNOWN }, { it.second.kind.ordinal }, { it.first.name.lowercase() }))
  }
}

/**
 *  وصلِ دوباره پس از قطع: ۱، ۲، ۵، ۱۰، ۳۰ ثانیه و بعد هر ۶۰ ثانیه — بی پایان تا دستگاه برگردد یا از فهرست برود.
 *  وصلی که دست‌کم [STABLE_MS] ماند شمارنده را صفر می‌کند (دستگاهِ خاموش‌وروشن‌شده زود برمی‌گردد).
 */
object Reconnect {
  val STEPS_MS: List<Long> = listOf(1_000, 2_000, 5_000, 10_000, 30_000)
  const val CAP_MS = 60_000L
  const val STABLE_MS = 30_000L

  fun delayMs(attempt: Int): Long = if (attempt < 0) STEPS_MS.first() else STEPS_MS.getOrElse(attempt) { CAP_MS }

  /** شمارهٔ تلاشِ بعدی پس از قطع: وصلِ پایدار ⇒ از نو */
  fun nextAttempt(attempt: Int, connectedForMs: Long): Int = if (connectedForMs >= STABLE_MS) 0 else attempt + 1
}
