package ir.vil3ntec.tohid.core.staff

/**
 *  نسخهٔ برنامهٔ دستگاه‌های دکان — فهرستِ «دستگاه‌ها» ِ صاحب و هشدارِ کلیدهایی که همهٔ دستگاه‌ها را به‌روز می‌خواهند
 *  (نخستین: «خرید به ارزِ دیگر»، PUR-11).
 *
 *  سرور تازه‌ترین `X-App-Version` ِ درستِ هر دستگاه را نگه می‌دارد (`GET /api/shop/devices` ⇒ `appVersion`، مهاجرتِ ۹۸۰).
 *  دو خطِ ساخت شمارهٔ جدا دارند — گوشی `3.2.N` (`tohid-native.yml`) و کامپیوتر `1.0.N` (`tohid-desktop.yml`)، `N` شمارهٔ
 *  اجرای CI ِ **همان** ورک‌فلو (همان `LINES` ِ `server/src/lib/newer-fields.js`) — پس شمارهٔ گوشی و کامپیوتر با هم سنجیده
 *  نمی‌شوند. قاعده ([states]):
 *   • همین دستگاه ⇒ [State.CURRENT] (خودش است، هر چه سرور هنوز دیده باشد)؛
 *   • نسخه نگفته، بی شمارهٔ ساخت (`3.2` ِ وب، `1.0` ِ آیفون) یا خطِ ناشناخته ⇒ [State.UNKNOWN] («نامعلوم»)؛
 *   • همان خطِ این برنامه ⇒ کمتر از ساختِ همین برنامه [State.OLD] («نسخهٔ کهنه»)، وگرنه [State.CURRENT]؛
 *   • خطِ دیگر ⇒ کمتر از تازه‌ترین ساختِ همان خط در همین دکان [State.OLD]، وگرنه [State.UNCHECKED]: این برنامه
 *     نمی‌داند کدام ساختِ آن خط هم‌پایهٔ خودش است، پس «به‌روز» هم نمی‌گوید.
 *  ⛔ [gate]: کلیدی که دستگاهِ کهنه را خراب می‌کند فقط وقتی بی پرسش روشن می‌شود که **همهٔ** دستگاه‌های فعال به‌روز بودنشان
 *  ثابت باشد؛ کهنه، نامعلوم و سنجیده‌نشده همه فهرست می‌شوند؛ فهرست نرسید ⇒ [Gate.NoList] (همان هشدار و تأیید).
 *  منطقِ خالص (commonMain)؛ گوشی، کامپیوتر و آیفون همین را می‌خوانند.
 */
object DeviceBuilds {

  /** خطِ ساخت از عددِ نخستِ نسخه */
  enum class Line { PHONE, DESKTOP }

  /** یک ساخت: خط، عددِ دوم و شمارهٔ ساخت (پسوندِ `-preview` همان ساخت است) */
  data class Build(val line: Line, val minor: Int, val number: Int) : Comparable<Build> {
    override fun compareTo(other: Build): Int =
      if (minor != other.minor) minor.compareTo(other.minor) else number.compareTo(other.number)
  }

  /** همان شکلِ `server/src/lib/device-app-version.js` با درست سه بخش (بی شمارهٔ ساخت ⇒ سنجیدنی نیست) */
  private val SHAPE = Regex("^(\\d{1,4})\\.(\\d{1,7})\\.(\\d{1,7})(?:-[0-9A-Za-z.]{1,16})?$")

  /** «3.2.615»، «v1.0.366»، «3.2.615-preview» ⇒ [Build]؛ هر چیزِ دیگر ⇒ null */
  fun parse(version: String?): Build? {
    val s = version?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
    if (s.length > 32) return null
    val m = SHAPE.matchEntire(s) ?: return null
    val line = when (m.groupValues[1]) {
      "3" -> Line.PHONE
      "1" -> Line.DESKTOP
      else -> return null
    }
    val minor = m.groupValues[2].toIntOrNull() ?: return null
    val number = m.groupValues[3].toIntOrNull() ?: return null
    return Build(line, minor, number)
  }

  enum class State { CURRENT, OLD, UNKNOWN, UNCHECKED }

  /** یک ردیفِ فهرستِ دستگاه‌ها: [active] = فعال و از کار نیفتاده؛ [current] = همین دستگاه */
  data class Device(val id: String, val version: String?, val current: Boolean = false, val active: Boolean = true)

  /** وضعیتِ هر دستگاه (کلید = [Device.id]) نسبت به ساختِ همین برنامه [self] (`AppConfig.appVersion`) */
  fun states(devices: List<Device>, self: String?): Map<String, State> {
    val mine = parse(self)
    val builds = devices.associate { it.id to parse(it.version) }
    val newest = HashMap<Line, Build>()
    for (b in builds.values.filterNotNull() + listOfNotNull(mine)) {
      val top = newest[b.line]
      if (top == null || b > top) newest[b.line] = b
    }
    return devices.associate { d ->
      val b = builds[d.id]
      d.id to when {
        d.current -> State.CURRENT
        b == null -> State.UNKNOWN
        mine != null && b.line == mine.line -> if (b < mine) State.OLD else State.CURRENT
        b < (newest[b.line] ?: b) -> State.OLD
        else -> State.UNCHECKED
      }
    }
  }

  /** پیش از روشن کردنِ کلیدی که دستگاهِ کهنه را خراب می‌کند */
  sealed interface Gate {
    /** همهٔ دستگاه‌های فعال به‌روزند ⇒ همان دم روشن */
    data object Direct : Gate
    /** این دستگاه‌ها (فعال، به ترتیبِ فهرست) به‌روز بودنشان ثابت نیست ⇒ فهرست و تأییدِ صریح */
    data class Confirm(val devices: List<Pair<Device, State>>) : Gate
    /** فهرست نرسید (بی اینترنت، بی اجازه، سرورِ کهنه) ⇒ همان هشدار و تأییدِ صریح */
    data object NoList : Gate
  }

  /** [devices] = null یعنی فهرست نرسید */
  fun gate(devices: List<Device>?, self: String?): Gate {
    if (devices == null) return Gate.NoList
    val states = states(devices, self)
    val left = devices.filter { it.active }.mapNotNull { d -> states[d.id]?.takeIf { it != State.CURRENT }?.let { d to it } }
    return if (left.isEmpty()) Gate.Direct else Gate.Confirm(left)
  }
}
