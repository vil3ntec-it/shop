package ir.vil3ntec.tohid.core.hw

/**
 *  HW-09 — دومین پروتکلِ ترازو: «قالبِ استانداردِ A&D» (`ST,+00012.34  g`)، که ترازوها و نشانگرهای
 *  A&D و بسیاری از نشانگرهای سازگار پیوسته یا با فرمانِ `Q` می‌فرستند:
 *    `<سرآیند>,[<GS|NT|TR>,]<عددِ علامت‌دار><واحد><CR><LF>`
 *  سرآیند: `ST` ایستا · `US` در حرکت · `OL` بیش از ظرفیت (عدد `+9999999E+19`) · `QT` شمارش (وزن نیست).
 *  پیش‌فرضِ A&D: ۲۴۰۰ بیت، ۷ بیت، توازنِ زوج، یک بیتِ پایان.
 *
 *  ترازوی پیوسته هر لحظه سطرِ تازه می‌فرستد؛ پس آخرین سطرِ **کامل** خوانده می‌شود. ⚠️ روی ترازوی
 *  واقعی آزموده نشده؛ آزمون فقط قابِ مستند را می‌خواند (`ScalePoleTest`).
 */
object AndScale : ScaleProtocol {
  override val id = "and"
  override val title = "A&D (ST/US/OL)"
  override val serial = SerialParams(baud = 2400, dataBits = 7, parity = "even", stopBits = 1)

  /** `Q` — «همین حالا یک بار بفرست» */
  override fun request(): ByteArray = byteArrayOf('Q'.code.toByte(), 0x0D, 0x0A)

  override fun complete(buffer: ByteArray): Boolean = lastLine(buffer) != null

  override fun parse(buffer: ByteArray): ScaleReading? {
    val line = lastLine(buffer) ?: return null
    val m = LINE.matchEntire(line) ?: return null
    val head = m.groupValues[1]
    if (head == "OL") return ScaleReading(kg = null, unit = "", motion = false, zero = false, over = true, under = false, error = false)
    if (head == "QT") return ScaleReading(kg = null, unit = "PCS", motion = false, zero = false, over = false, under = false, error = false)
    val value = m.groupValues[3].replace(" ", "").toDoubleOrNull() ?: return null
    val unit = m.groupValues[4].trim().uppercase()
    val kg = when (unit) {
      "KG" -> value
      "G" -> value / 1000.0
      "LB" -> value * 0.45359237
      "OZ" -> value * 0.028349523125
      else -> null
    }
    return ScaleReading(
      kg = kg,
      unit = unit,
      motion = head == "US",
      zero = kg == 0.0,
      over = false,
      under = kg != null && kg < 0.0,
      error = kg == null,
    )
  }

  /** آخرین سطرِ کاملی که سرآیندِ A&D دارد (سطرِ نیمه‌کارهٔ آخر کنار می‌رود) */
  private fun lastLine(buffer: ByteArray): String? {
    val text = buildString { buffer.forEach { append((it.toInt() and 0x7F).toChar()) } }
    val end = text.lastIndexOf('\n').takeIf { it >= 0 } ?: return null
    return text.substring(0, end).split('\n').map { it.trim('\r', ' ') }.lastOrNull { LINE.matches(it) }
  }

  private val LINE = Regex("""^(ST|US|OL|QT),(?:(GS|NT|TR),)?([+-]?\s*[0-9.]+(?:E[+-]\d+)?)\s*([A-Za-z%]{0,3})\s*$""")
}

/**
 *  جمع‌کنندهٔ خواندنِ سریال (HW-09): تکه‌ها را تا پاسخِ کامل جمع می‌کند و بافر را محدود نگه می‌دارد
 *  (ترازوی پیوسته بی‌پایان می‌فرستد؛ فقط `maxBytes` ِ آخر می‌ماند). صدازننده تکه را از درگاه می‌خواند.
 */
class SerialCollect(private val complete: (ByteArray) -> Boolean, private val maxBytes: Int = 512) {
  private var buf = ByteArray(0)

  /** @return `true` وقتی پاسخ کامل شد */
  fun add(chunk: ByteArray, from: Int = 0, len: Int = chunk.size): Boolean {
    if (len <= 0) return false
    val joined = buf + chunk.copyOfRange(from, from + len)
    buf = if (joined.size > maxBytes) joined.copyOfRange(joined.size - maxBytes, joined.size) else joined
    return complete(buf)
  }

  fun bytes(): ByteArray = buf.copyOf()
}
