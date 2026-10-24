package ir.vil3ntec.tohid.core.hw

/**
 *  ESC/POS — بایت‌هایی که چاپگرِ حرارتی می‌فهمد (HW-01، HW-03، HW-11).
 *
 *  ── چرا همه‌چیز تصویر است ──────────────────────────────────────────
 *  رسیدِ دری و پشتو به‌صورت **تصویر** چاپ می‌شود، نه متن. جدولِ نویسهٔ
 *  چاپگر (`ESC t`) راهِ فارسی نیست: PC864 و WPC1256 حروفِ پشتو (ټ ډ ړ ږ ښ
 *  ګ ڼ ې ۍ) را ندارند، شکلِ پیوستهٔ حروف را خودشان نمی‌سازند و شمارهٔ
 *  جدول از سازنده‌ای به سازندهٔ دیگر فرق می‌کند. پس این‌جا هیچ متنی
 *  فرستاده نمی‌شود و «جدولِ نویسه» در پروفایل نیست — عمداً.
 *
 *  ── دو راهِ فرستادنِ تصویر ─────────────────────────────────────────
 *    • `GS v 0` (رستری) — پیش‌فرض؛ همان بایت‌های نسخه‌های پیشین.
 *    • `ESC *` (ستونیِ ۲۴ نقطه‌ای) — برای چاپگرهایی که `GS v 0` را
 *      نمی‌شناسند و به‌جای رسید نویسهٔ بی‌معنا می‌زنند.
 *  ──────────────────────────────────────────────────────────────────
 */
enum class ImageMode(val key: String) {
  RASTER("raster"),
  COLUMN("column");

  companion object {
    fun of(key: String?): ImageMode = entries.firstOrNull { it.key == key } ?: RASTER
  }
}

enum class CutMode(val key: String) {
  /** `GS V B 0` — جلو بردن تا تیغه و برشِ نیمه؛ رفتارِ همیشگی */
  PARTIAL("partial"),
  /** `GS V A 0` — برشِ کامل */
  FULL("full"),
  /** چاپگرِ بی‌تیغه — فقط کاغذ جلو می‌رود */
  NONE("none");

  companion object {
    fun of(key: String?): CutMode = entries.firstOrNull { it.key == key } ?: PARTIAL
  }
}

/**
 *  تنظیماتِ یک چاپگر. پیش‌فرضِ هر فیلد **رفتارِ امروز** است؛ چاپگری که
 *  پیش از این ثبت شده بی‌هیچ تغییری همان بایت‌ها را می‌گیرد.
 */
data class PrinterProfile(
  /** عرضِ کاغذ بر حسب نقطه: ۵۸ میلی‌متر = ۳۸۴، ۸۰ میلی‌متر = ۵۷۶ */
  val paperDots: Int = WIDTH_58MM,
  val mode: ImageMode = ImageMode.RASTER,
  val cut: CutMode = CutMode.PARTIAL,
  /** سطرهای خالی پیش از برش */
  val feedLines: Int = 3,
  /** HW-03: باز کردنِ کشوی پول پس از فروشِ نقدی — پیش‌فرض خاموش */
  val drawer: Boolean = false,
  /** پایهٔ کشو: ۰ = پینِ ۲ (رایج)، ۱ = پینِ ۵ */
  val drawerPin: Int = 0,
  /** نشانِ دکان بالای رسید — پیش‌فرض خاموش */
  val logo: Boolean = false,
) {
  companion object {
    const val WIDTH_58MM = 384
    const val WIDTH_80MM = 576

    /** هر عددی جز ۵۷۶ همان ۵۸ است — مثلِ `paperDots()`ِ وب */
    fun paper(dots: Int): Int = if (dots >= WIDTH_80MM) WIDTH_80MM else WIDTH_58MM
  }
}

/** پاسخِ `DLE EOT 4` — حسگرِ کاغذ */
data class PaperStatus(val nearEnd: Boolean, val out: Boolean)

object EscPos {

  /** `ESC @` — چاپگر از نو */
  val INIT = byteArrayOf(0x1B, 0x40)

  /** تعدادِ سطر در هر تکهٔ `GS v 0` — بافرِ این چاپگرها کوچک است */
  const val BAND_ROWS = 128

  /** پرسشِ وضعیتِ کاغذ — `DLE EOT 4` */
  val PAPER_STATUS = byteArrayOf(0x10, 0x04, 0x04)

  /**
   *  کلِ کارِ چاپ. ترتیب:
   *    `ESC @` ← (کشو) ← تصویر ← سطرهای خالی ← برش
   *
   *  `openDrawer` فقط برای بردارهای مشترک و پیش‌نمایش است. صفِ چاپ هرگز
   *  کشو را درونِ کار نمی‌گذارد: ضربهٔ کشو جدا و یک‌باره می‌رود
   *  (`drawerOnly`، `KickOnce`) و `withoutDrawerKick` هر بایتِ صف را پاک می‌کند.
   *
   *  تصویرِ پهن‌تر از کاغذ پذیرفته نمی‌شود — چاپگر لبه‌اش را بی‌صدا می‌بُرد.
   */
  fun document(image: MonoImage, profile: PrinterProfile, openDrawer: Boolean = false): ByteArray {
    require(image.width <= profile.paperDots) {
      "پهنای تصویر (${image.width} نقطه) از پهنای کاغذ (${profile.paperDots} نقطه) بیشتر است"
    }
    val out = Bytes()
    out.add(INIT)
    if (openDrawer) out.add(drawerKick(profile.drawerPin))
    out.add(if (profile.mode == ImageMode.COLUMN) column(image) else raster(image))
    out.add(feed(profile.feedLines))
    out.add(cut(profile.cut))
    return out.toByteArray()
  }

  /** فقط کشو — یک‌باره و بیرون از صف: پس از فروشِ نقدی یا «باز کردنِ کشو» */
  fun drawerOnly(profile: PrinterProfile): ByteArray = INIT + drawerKick(profile.drawerPin)

  /**
   *  ضربهٔ کشو (`ESC p m t1 t2`) از سرِ کار برداشته می‌شود — همان جایی که
   *  `document` می‌گذارد (پس از `ESC @`، یا سرِ کار). تصویر با `GS v 0`
   *  یا `ESC 3` آغاز می‌شود، پس این پنج بایت با دادهٔ تصویر قاطی نمی‌شوند.
   */
  fun withoutDrawerKick(bytes: ByteArray): ByteArray {
    fun kickAt(i: Int) = bytes.size >= i + 5 && bytes[i] == 0x1B.toByte() && bytes[i + 1] == 0x70.toByte()
    return when {
      kickAt(0) -> bytes.copyOfRange(5, bytes.size)
      bytes.size >= 2 && bytes[0] == INIT[0] && bytes[1] == INIT[1] && kickAt(2) -> INIT + bytes.copyOfRange(7, bytes.size)
      else -> bytes
    }
  }

  /**
   *  `GS v 0` — تصویر تکه‌تکه، هر تکه `BAND_ROWS` سطر.
   *  عیناً همان بایت‌های `ThermalPrinter.writeImage` و `toEscPos`ِ وب.
   */
  fun raster(image: MonoImage, bandRows: Int = BAND_ROWS): ByteArray {
    val out = Bytes()
    val rb = image.rowBytes
    var top = 0
    while (top < image.height) {
      val rows = minOf(bandRows, image.height - top)
      out.add(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
      out.add(byteArrayOf((rb and 0xFF).toByte(), ((rb shr 8) and 0xFF).toByte()))
      out.add(byteArrayOf((rows and 0xFF).toByte(), ((rows shr 8) and 0xFF).toByte()))
      out.add(image.bits, top * rb, rows * rb)
      top += rows
    }
    return out.toByteArray()
  }

  /**
   *  `ESC *` با چگالیِ ۳۳ (۲۴ نقطه، دوبرابر) — راهِ چاپگرهای قدیمی‌تر.
   *
   *  فاصلهٔ سطر اول ۲۴ نقطه می‌شود (`ESC 3 24`) تا نوارها بی‌درز به هم
   *  بچسبند، و آخر سر به پیش‌فرض برمی‌گردد (`ESC 2`). هر ستون سه بایت است
   *  و بیتِ پرارزش نقطهٔ بالایی.
   */
  fun column(image: MonoImage): ByteArray {
    val out = Bytes()
    val w = image.width
    out.add(byteArrayOf(0x1B, 0x33, 24))
    var top = 0
    while (top < image.height) {
      out.add(byteArrayOf(0x1B, 0x2A, 33, (w and 0xFF).toByte(), ((w shr 8) and 0xFF).toByte()))
      val band = ByteArray(w * 3)
      for (x in 0 until w) for (k in 0 until 3) {
        var b = 0
        for (bit in 0 until 8) if (image[x, top + k * 8 + bit]) b = b or (0x80 ushr bit)
        band[x * 3 + k] = b.toByte()
      }
      out.add(band)
      out.add(byteArrayOf(0x0A))
      top += 24
    }
    out.add(byteArrayOf(0x1B, 0x32))
    return out.toByteArray()
  }

  /**
   *  `ESC p m t1 t2` — ضربهٔ کشوی پول از راهِ چاپگر (HW-03).
   *  زمان‌ها بر حسبِ ۲ میلی‌ثانیه‌اند؛ ۵۰ و ۵۰۰ میلی‌ثانیه همان
   *  «ESC p 0 25 250»ِ رایج است.
   */
  fun drawerKick(pin: Int = 0, onMs: Int = 50, offMs: Int = 500): ByteArray =
    byteArrayOf(0x1B, 0x70, if (pin == 1) 1 else 0, units(onMs).toByte(), units(offMs).toByte())

  private fun units(ms: Int): Int = (ms / 2).coerceIn(1, 255)

  fun feed(lines: Int): ByteArray = ByteArray(lines.coerceIn(0, 20)) { 0x0A }

  fun cut(mode: CutMode): ByteArray = when (mode) {
    CutMode.PARTIAL -> byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    CutMode.FULL -> byteArrayOf(0x1D, 0x56, 0x41, 0x00)
    CutMode.NONE -> ByteArray(0)
  }

  /**
   *  پاسخِ `DLE EOT 4`. بیت‌های ۱ و ۴ همیشه ۱ و بیت‌های ۰ و ۷ همیشه ۰اند؛
   *  بایتی که این را ندارد پاسخِ وضعیت نیست (`null`).
   *    بیت‌های ۲ و ۳ ⇒ کاغذ رو به پایان · بیت‌های ۵ و ۶ ⇒ کاغذ تمام
   */
  fun parsePaperStatus(b: Int): PaperStatus? {
    val v = b and 0xFF
    if ((v and 0x93) != 0x12) return null
    return PaperStatus(nearEnd = (v and 0x0C) != 0, out = (v and 0x60) != 0)
  }

  /** جمع‌کنندهٔ کوچکِ بایت — `ByteArrayOutputStream` در commonMain نیست */
  internal class Bytes {
    private var buf = ByteArray(1024)
    private var size = 0
    fun add(b: ByteArray) = add(b, 0, b.size)
    fun add(b: ByteArray, from: Int, len: Int) {
      if (size + len > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + len))
      b.copyInto(buf, size, from, from + len)
      size += len
    }
    fun toByteArray(): ByteArray = buf.copyOf(size)
  }
}
