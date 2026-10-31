package ir.vil3ntec.tohid.core.hw

/**
 *  ترازوی سریال (HW-09) — «پروتکل‌های قابل افزودن».
 *
 *  فقط پروتکلی این‌جا می‌آید که **استاندارد و مستند** است؛ هیچ ترازوی
 *  ساختگی یا حدسی نیست. فعلاً یکی: NCI (پروتکلِ ECRِ Weigh-Tronix که
 *  بیشترِ ترازوهای صندوقی آن را دارند یا شبیه‌سازی می‌کنند).
 *
 *  ⚠️ روی ترازوی واقعی آزموده نشده (NOT-VERIFIED). آزمون‌ها فقط قابِ
 *  پاسخِ مستند را می‌خوانند.
 */
data class SerialParams(
  val baud: Int = 9600,
  val dataBits: Int = 7,
  /** `none` | `even` | `odd` */
  val parity: String = "even",
  val stopBits: Int = 1,
)

data class ScaleReading(
  /** وزن به کیلوگرم؛ `null` یعنی وزنی خوانده نشد */
  val kg: Double?,
  /** واحدی که ترازو فرستاد: KG، LB، OZ، G */
  val unit: String,
  val motion: Boolean,
  val zero: Boolean,
  val over: Boolean,
  val under: Boolean,
  val error: Boolean,
) {
  /** فقط وزنِ ایستا، مثبت و بی‌خطا به سبد می‌رود */
  val usable: Boolean get() = kg != null && kg > 0.0 && !motion && !over && !under && !error
}

interface ScaleProtocol {
  val id: String
  val title: String
  val serial: SerialParams
  /** پرسشِ وزن */
  fun request(): ByteArray
  /** پاسخ کامل رسیده؟ */
  fun complete(buffer: ByteArray): Boolean
  fun parse(buffer: ByteArray): ScaleReading?
}

object Scales {
  val all: List<ScaleProtocol> = listOf(NciScale, AndScale)
  fun of(id: String?): ScaleProtocol? = all.firstOrNull { it.id == id }
}

/**
 *  NCI / Weigh-Tronix ECR:
 *    پرسش:  `W<CR>`
 *    پاسخ:  `<LF>xx.xxxUU<CR><LF>[S]hh<CR><ETX>`
 *  `hh` دو بایتِ وضعیت‌اند (بیت‌های ۴ و ۵ همیشه ۱، بیتِ ۷ توازن):
 *    بایتِ ۱: بیتِ ۰ = در حرکت، بیتِ ۱ = روی صفر، بیت‌های ۲ و ۳ = خطای حافظه
 *    بایتِ ۲: بیتِ ۰ = زیرِ ظرفیت، بیتِ ۱ = بیش از ظرفیت، بیت‌های ۲ و ۳ = خطای ROM/واسنجی
 *  ترازویی که فرمان را نفهمد `<LF>?<CR><ETX>` می‌فرستد.
 */
object NciScale : ScaleProtocol {
  override val id = "nci"
  override val title = "NCI (Weigh-Tronix ECR)"
  override val serial = SerialParams()

  private const val ETX = 0x03

  override fun request(): ByteArray = byteArrayOf('W'.code.toByte(), 0x0D)

  override fun complete(buffer: ByteArray): Boolean = buffer.any { (it.toInt() and 0x7F) == ETX }

  override fun parse(buffer: ByteArray): ScaleReading? {
    val text = buildString { buffer.forEach { append(((it.toInt() and 0x7F)).toChar()) } }
    val lines = text.split('\r', '\n', ETX.toChar()).map { it.trim() }.filter { it.isNotEmpty() }
    var kg: Double? = null
    var unit = ""
    var status: Pair<Int, Int>? = null
    for (line in lines) {
      val w = WEIGHT.matchEntire(line)
      if (w != null && kg == null) {
        val value = w.groupValues[1].toDoubleOrNull() ?: continue
        unit = w.groupValues[2].uppercase()
        kg = when (unit) {
          "KG" -> value
          "G" -> value / 1000.0
          "LB" -> value * 0.45359237
          "OZ" -> value * 0.028349523125
          else -> null
        }
        continue
      }
      val s = STATUS.matchEntire(line)
      if (s != null) status = s.groupValues[1][0].code to s.groupValues[2][0].code
    }
    if (kg == null && status == null) return null
    val (b1, b2) = status ?: (0x30 to 0x30)
    return ScaleReading(
      kg = kg,
      unit = unit,
      motion = b1 and 0x01 != 0,
      zero = b1 and 0x02 != 0,
      over = b2 and 0x02 != 0,
      under = b2 and 0x01 != 0,
      error = (b1 and 0x0C) != 0 || (b2 and 0x0C) != 0,
    )
  }

  private val WEIGHT = Regex("""^(-?\d+(?:\.\d+)?)\s*(KG|kg|LB|lb|OZ|oz|G|g)$""")
  private val STATUS = Regex("""^S?([0-?])([0-?])$""")
}
