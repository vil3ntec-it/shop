package ir.vil3ntec.tohid.core.image

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 *  IMG-PRO-16 (§۳۶.۱۲) — «نسخهٔ نهایی در اندازهٔ کوچک و بزرگ روی صفحه‌های کوچک و بزرگ دیده می‌شود؛ نوشته و لبه‌ها در حدِ
 *  وضوحِ منبع طبیعی‌اند و فشرده‌سازی بیش‌ازحد نیست.» منطقِ خالص، commonMain (گوشی، کامپیوتر و آیفون)؛ نمایشگر
 *  `ui/v3/products/PhotoVersionsPage.kt`.
 *
 *  ── سنجهٔ فشرده‌سازی ([measure]) — فقط از سرآیند، بی رمزگشاییِ پیکسل ─────────────────────────
 *   JPEG  کیفیتِ تخمینی از جدولِ کوانتیزهٔ روشنایی (DQT، جدولِ ۰) با همان مقیاسِ IJG/libjpeg که `Bitmap.compress`
 *         (اندروید) و ImageIO (کامپیوتر) می‌سازند: `S = جمعِ جدول ÷ جمعِ جدولِ استاندارد × ۱۰۰`؛ S ≤ ۱۰۰ ⇒
 *         `q = (۲۰۰ − S) ÷ ۲`، وگرنه `q = ۵۰۰۰ ÷ S`. ابعاد از SOF؛ JPEG ِ بی DQT یا بی SOF ⇒ «نامعلوم».
 *   PNG   بی‌اتلاف ⇒ کیفیت ندارد (هرگز «فشرده‌سازیِ زیاد» نیست)؛ ابعاد از IHDR.
 *   WebP  ابعاد از VP8/VP8L/VP8X؛ کیفیتِ WebP ِ بااتلاف از سرآیند خوانا نیست ⇒ «نامعلوم»، نه حدس.
 *  ⛔ هر چیزِ خراب یا ناشناخته ⇒ [Kind.OTHER] با ابعاد/کیفیتِ null — هرگز استثنا (صفحه فقط «نامعلوم» می‌گوید).
 *
 *  ── داوری ([verdict]) ──────────────────────────────────────────────────────────────────
 *   JPEG با کیفیتِ کمتر از [FLOOR] ⇒ «فشرده‌سازیِ زیاد» (نوشته و لبه‌ها دندانه/لکه می‌گیرند). پله‌های خودِ برنامه
 *   (`ImagePrep.QUALITIES`) همه بالای این مرزند، پس عکسی که برنامه می‌سازد هرگز این نشان را نمی‌گیرد (آزمون).
 *   نسخهٔ کوچکِ بزرگ‌تر از نسخهٔ بزرگ، یا با نسبتِ دیگر (بیش از ۲٪، همان مرزِ سرور) ⇒ «نسخهٔ کوچک ناجور».
 *   ضلعِ بلندِ نسخهٔ بزرگ کمتر از [SMALL_SIDE] ⇒ «منبع کوچک است» (بزرگ‌تر نشان دادنش تار می‌شود).
 *
 *  ── نمایش بی بزرگ‌نمایی ([fit]) ────────────────────────────────────────────────────────
 *   هر نسخه در هر قاب حداکثر به اندازهٔ پیکسلِ خودش کشیده می‌شود (کوچک‌نمایی آری، بزرگ‌نمایی هرگز)؛ یعنی روی صفحهٔ
 *   بزرگ هم نوشته و لبه همان وضوحِ منبع را دارد و تارِ کشیده نمی‌شود.
 */
object ImageQuality {

  /** زیرِ این کیفیتِ JPEG (مقیاسِ IJG) ⇒ فشرده‌سازیِ زیاد */
  const val FLOOR = 50
  /** از این به بالا ⇒ «تیز» */
  const val GOOD = 75
  /** ضلعِ بلندِ نسخهٔ بزرگ کمتر از این ⇒ منبع کوچک (همان `MIN_SIDE` ِ سرور و برنامه) */
  const val SMALL_SIDE = 300
  /** مرزِ نسبتِ دو نسخه — همان ۲٪ ِ سرور (`ImagePrep.aspectOk`) */
  const val ASPECT_TOLERANCE = 0.02

  enum class Kind { JPEG, PNG, WEBP, OTHER }

  data class Measure(
    val kind: Kind,
    val width: Int?,
    val height: Int?,
    val bytes: Int,
    /** کیفیتِ تخمینیِ JPEG (۱ تا ۱۰۰)؛ null ⇒ بی‌اتلاف یا نامعلوم */
    val quality: Int?,
  ) {
    val pixels: Long? get() = if (width != null && height != null) width.toLong() * height else null
    /** بیت بر پیکسل؛ null ⇒ ابعادِ نامعلوم */
    val bitsPerPixel: Double? get() = pixels?.takeIf { it > 0 }?.let { bytes * 8.0 / it }
    val longSide: Int? get() = if (width != null && height != null) max(width, height) else null
  }

  enum class Flag {
    /** کیفیتِ JPEG کمتر از [FLOOR] */
    OVER_COMPRESSED,
    /** نسخهٔ کوچک بزرگ‌تر از نسخهٔ بزرگ است یا نسبتش فرق دارد */
    THUMB_MISMATCH,
    /** ضلعِ بلندِ نسخهٔ بزرگ کمتر از [SMALL_SIDE] */
    SMALL_SOURCE,
    /** ابعاد یا نوع از سرآیند خوانده نشد */
    UNREADABLE,
  }

  enum class Grade { SHARP, ACCEPTABLE, OVER_COMPRESSED, LOSSLESS, UNKNOWN }

  /** درجهٔ فشرده‌سازیِ یک نسخه */
  fun grade(m: Measure): Grade = when {
    m.kind == Kind.PNG -> Grade.LOSSLESS
    m.quality == null -> Grade.UNKNOWN
    m.quality < FLOOR -> Grade.OVER_COMPRESSED
    m.quality >= GOOD -> Grade.SHARP
    else -> Grade.ACCEPTABLE
  }

  /** نشان‌های دو نسخهٔ نهایی (کوچک و بزرگ)؛ خالی ⇒ هر دو درست */
  fun verdict(thumb: Measure?, display: Measure?): List<Flag> = buildList {
    val all = listOfNotNull(thumb, display)
    if (all.any { it.width == null || it.kind == Kind.OTHER }) add(Flag.UNREADABLE)
    if (all.any { grade(it) == Grade.OVER_COMPRESSED }) add(Flag.OVER_COMPRESSED)
    val tw = thumb?.width; val th = thumb?.height; val dw = display?.width; val dh = display?.height
    if (tw != null && th != null && dw != null && dh != null && tw > 0 && th > 0 && dw > 0 && dh > 0) {
      val tr = tw.toDouble() / th
      val dr = dw.toDouble() / dh
      if (tw > dw || th > dh || kotlin.math.abs(tr - dr) / dr > ASPECT_TOLERANCE) add(Flag.THUMB_MISMATCH)
    }
    display?.longSide?.let { if (it < SMALL_SIDE) add(Flag.SMALL_SOURCE) }
  }

  /** اندازهٔ نمایشِ یک نسخه در قابِ [boxW]×[boxH] پیکسل: کوچک‌نمایی آری، بزرگ‌نمایی هرگز */
  fun fit(w: Int, h: Int, boxW: Int, boxH: Int): Pair<Int, Int> {
    if (w <= 0 || h <= 0) return 0 to 0
    val s = min(1.0, min(boxW.coerceAtLeast(1).toDouble() / w, boxH.coerceAtLeast(1).toDouble() / h))
    return max(1, (w * s).roundToInt()) to max(1, (h * s).roundToInt())
  }

  /** نسخه در این قاب به اندازهٔ پیکسلِ خودش است (قاب از عکس بزرگ‌تر است) — یعنی صفحهٔ بزرگ آن را نمی‌کشد */
  fun nativeIn(w: Int, h: Int, boxW: Int, boxH: Int): Boolean = w in 1..boxW && h in 1..boxH

  // ── سرآیند ──────────────────────────────────────────────────

  /** جدولِ کوانتیزهٔ استانداردِ روشنایی (پیوستِ K ِ JPEG، کیفیتِ ۵۰ ِ IJG)؛ جمع فقط به کار می‌آید، پس ترتیب مهم نیست */
  private val STD_LUMA = intArrayOf(
    16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55,
    14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
    18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
    49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99,
  )
  private val STD_SUM = STD_LUMA.sum()

  /** جدولِ IJG برای کیفیتِ [q] (همان `jpeg_quality_scaling` و گردِ libjpeg) — برای آزمون و بردار */
  fun ijgTable(q: Int): IntArray {
    val qq = q.coerceIn(1, 100)
    val scale = if (qq < 50) 5000 / qq else 200 - qq * 2
    return IntArray(64) { ((STD_LUMA[it] * scale + 50) / 100).coerceIn(1, 255) }
  }

  /** کیفیتِ تخمینی از جدولِ روشنایی؛ جدولِ بدشکل ⇒ null */
  fun qualityOf(table: IntArray): Int? {
    if (table.size != 64 || table.any { it <= 0 }) return null
    //  جدولِ همه-یک فقط از کیفیتِ ۱۰۰ می‌آید (گردِ ۹۹ ⇒ ۱۰۰)
    if (table.all { it == 1 }) return 100
    val s = table.sum() * 100.0 / STD_SUM
    val q = if (s <= 100.0) (200.0 - s) / 2.0 else 5000.0 / s
    return q.roundToInt().coerceIn(1, 100)
  }

  private fun u8(b: ByteArray, i: Int): Int = b[i].toInt() and 0xff
  private fun u16be(b: ByteArray, i: Int): Int = (u8(b, i) shl 8) or u8(b, i + 1)
  private fun u16le(b: ByteArray, i: Int): Int = u8(b, i) or (u8(b, i + 1) shl 8)
  private fun u24le(b: ByteArray, i: Int): Int = u8(b, i) or (u8(b, i + 1) shl 8) or (u8(b, i + 2) shl 16)
  private fun u32be(b: ByteArray, i: Int): Long = (u16be(b, i).toLong() shl 16) or u16be(b, i + 2).toLong()

  /** سنجهٔ یک فایل از سرآیند؛ هیچ‌گاه استثنا نمی‌دهد */
  fun measure(b: ByteArray): Measure = runCatching { measureOrNull(b) }.getOrNull() ?: Measure(Kind.OTHER, null, null, b.size, null)

  private fun measureOrNull(b: ByteArray): Measure? = when {
    b.size >= 4 && u8(b, 0) == 0xFF && u8(b, 1) == 0xD8 -> jpeg(b)
    b.size >= 24 && u8(b, 0) == 0x89 && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() -> png(b)
    b.size >= 30 && b.copyOfRange(0, 4).decodeToString() == "RIFF" && b.copyOfRange(8, 12).decodeToString() == "WEBP" -> webp(b)
    else -> null
  }

  private fun png(b: ByteArray): Measure? {
    if (b.copyOfRange(12, 16).decodeToString() != "IHDR") return null
    val w = u32be(b, 16)
    val h = u32be(b, 20)
    if (w !in 1..Int.MAX_VALUE.toLong() || h !in 1..Int.MAX_VALUE.toLong()) return null
    return Measure(Kind.PNG, w.toInt(), h.toInt(), b.size, null)
  }

  private fun webp(b: ByteArray): Measure? {
    val chunk = b.copyOfRange(12, 16).decodeToString()
    val (w, h) = when (chunk) {
      //  VP8 ِ بااتلاف: پس از ۳ بایتِ قاب و نشانِ ۹d 01 2a، عرض و ارتفاعِ ۱۴بیتی
      "VP8 " -> {
        if (b.size < 30 || u8(b, 23) != 0x9d || u8(b, 24) != 0x01 || u8(b, 25) != 0x2a) return null
        (u16le(b, 26) and 0x3fff) to (u16le(b, 28) and 0x3fff)
      }
      //  VP8L ِ بی‌اتلاف: نشانِ 0x2f، سپس ۱۴ بیت عرض−۱ و ۱۴ بیت ارتفاع−۱
      "VP8L" -> {
        if (b.size < 25 || u8(b, 20) != 0x2f) return null
        val v = u8(b, 21) or (u8(b, 22) shl 8) or (u8(b, 23) shl 16) or (u8(b, 24) shl 24)
        ((v and 0x3fff) + 1) to (((v ushr 14) and 0x3fff) + 1)
      }
      "VP8X" -> (u24le(b, 24) + 1) to (u24le(b, 27) + 1)
      else -> return null
    }
    if (w <= 0 || h <= 0) return null
    return Measure(Kind.WEBP, w, h, b.size, null)
  }

  private fun jpeg(b: ByteArray): Measure {
    var i = 2
    var luma: IntArray? = null
    var w: Int? = null
    var h: Int? = null
    while (i + 4 <= b.size) {
      if (u8(b, i) != 0xFF) break
      var marker = u8(b, i + 1)
      //  پرکنندهٔ 0xFF
      while (marker == 0xFF && i + 2 < b.size) { i++; marker = u8(b, i + 1) }
      if (marker == 0xD9 || marker == 0xDA) break
      if (marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
      val len = u16be(b, i + 2)
      val start = i + 4
      val end = i + 2 + len
      if (len < 2 || end > b.size) break
      when {
        marker == 0xDB -> {
          var j = start
          while (j < end) {
            val pq = u8(b, j) shr 4
            val tq = u8(b, j) and 15
            j++
            val n = if (pq == 0) 64 else 128
            if (j + n > end) break
            if (tq == 0 && luma == null) {
              luma = IntArray(64) { k -> if (pq == 0) u8(b, j + k) else u16be(b, j + 2 * k) }
            }
            j += n
          }
        }
        marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC -> {
          if (start + 5 <= end && h == null) {
            h = u16be(b, start + 1)
            w = u16be(b, start + 3)
          }
        }
      }
      i = end
    }
    val ok = w != null && h != null && w > 0 && h > 0
    return Measure(if (ok) Kind.JPEG else Kind.OTHER, if (ok) w else null, if (ok) h else null, b.size, luma?.let(::qualityOf))
  }

  // ── پاسخِ «کدام عکس برای این کالا» (`GET /shop/images/products/:id`) ───────────────

  /** نسخه‌های نهاییِ عکسِ بانکِ یک کالا: نشانیِ فایل‌ها (`urls`) و اینکه از کجاست */
  data class Shown(
    val source: String,
    val imageId: String,
    val version: Int,
    val thumbUrl: String?,
    val displayUrl: String?,
    val zoomUrl: String?,
  ) {
    /** عکسِ بانک با دست‌کم یک نسخهٔ دیدنی */
    val hasVersions: Boolean get() = thumbUrl != null || displayUrl != null
  }

  /** نشانیِ فایلی که سرور داد ⇒ مسیرِ درِ کاربر؛ هر نشانیِ دیگری (بیرونی، پوشهٔ دیگر) ⇒ null — همان قاعدهٔ `ImageBank.file` */
  fun filePath(url: String?): String? {
    val p = url?.trim()?.removePrefix("/api/v1")?.removePrefix("/api") ?: return null
    return p.takeIf { it.startsWith("/shop/images/file/") && ".." !in it && "//" !in it }
  }

  private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content

  fun shownOf(o: JsonObject): Shown {
    val urls = o["urls"] as? JsonObject
    return Shown(
      source = o["source"].str() ?: "none",
      imageId = o["image_id"].str().orEmpty(),
      version = (o["version"] as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.longOrNull?.toInt() ?: 0,
      thumbUrl = urls?.get("thumb").str(),
      displayUrl = urls?.get("display").str(),
      zoomUrl = urls?.get("zoom").str(),
    )
  }
}
