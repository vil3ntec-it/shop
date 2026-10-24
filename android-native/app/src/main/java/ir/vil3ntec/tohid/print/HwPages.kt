package ir.vil3ntec.tohid.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.text.Layout
import android.text.TextPaint
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.R
import ir.vil3ntec.tohid.core.hw.Barcodes
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.EscPosPreview
import ir.vil3ntec.tohid.core.hw.Labels
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.data.ReceiptProfileStore
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import java.io.File

/**
 *  برگه‌هایی که روی چاپگر می‌روند، جز خودِ فاکتور (`Receipt`):
 *  برگهٔ آزمون (HW-11)، برچسبِ کالا (HW-04)، نشانِ دکان، و پیش‌نمایشِ
 *  «چاپ روی کاغذِ خیالی».
 *
 *  نوشته‌ها با قلمِ برنامه روی تصویر کشیده می‌شوند (دری و پشتو بی تصویر
 *  درست چاپ نمی‌شوند) و بارکد و خط‌کش در هستهٔ یک‌بیتی (`core/hw`) روی
 *  همان تصویر چسبانده می‌شوند — بی `drawBitmap`، تا کامپیوتر هم همین را بکشد.
 */
object HwPages {

  /* ============================ نوشته ⇐ تصویر ============================ */

  enum class Style { TITLE, HEAD, BODY, SMALL }

  data class Line(val text: String, val style: Style = Style.BODY, val align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER)

  /** چند سطرِ نوشته روی کاغذی به پهنای `width` — یک‌بیتی */
  fun text(context: Context, width: Int, lines: List<Line>, scale: Float = 1f): MonoImage {
    val regular = Receipt.font(context, R.font.vazirmatn_regular)
    val bold = Receipt.font(context, R.font.vazirmatn_bold)
    val unit = width.coerceAtMost(PAPER_FONT_BASE) * scale
    fun paint(s: Style): TextPaint = when (s) {
      Style.TITLE -> Receipt.paint(bold, unit * 0.075f)
      Style.HEAD -> Receipt.paint(bold, unit * 0.052f)
      Style.BODY -> Receipt.paint(regular, unit * 0.044f)
      Style.SMALL -> Receipt.paint(regular, unit * 0.036f)
    }
    val pad = (width * 0.03f).toInt()
    val inner = (width - pad * 2).coerceAtLeast(1)
    val layouts = lines.map { Receipt.layout(it.text, paint(it.style), inner, it.align) }
    val height = (layouts.sumOf { it.height } + pad).coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    var y = pad / 2f
    layouts.forEach { l ->
      canvas.save(); canvas.translate(pad.toFloat(), y); l.draw(canvas); canvas.restore()
      y += l.height
    }
    val px = IntArray(width * height)
    bitmap.getPixels(px, 0, width, 0, 0, width, height)
    bitmap.recycle()
    return MonoImage.fromArgb(px, width, height, width)
  }

  /** پهنای پایهٔ اندازهٔ قلم — همان نسبتِ `Receipt` روی ۸۰ میلی‌متر */
  private const val PAPER_FONT_BASE = 576

  /** چند تصویر زیرِ هم، هر کدام وسطِ کاغذ */
  fun stack(width: Int, parts: List<MonoImage>, gap: Int = 8): MonoImage {
    val h = parts.sumOf { it.height } + gap * (parts.size - 1).coerceAtLeast(0)
    val out = MonoImage(width, h.coerceAtLeast(1))
    var y = 0
    parts.forEach { p -> out.blit(p, ((width - p.width) / 2).coerceAtLeast(0), y); y += p.height + gap }
    return out
  }

  /* ============================ فاکتور ============================ */

  /**
   *  بایت‌های فاکتور با پروفایلِ چاپگر: نشانِ دکان (اگر روشن است)، خودِ
   *  فاکتور، و کشو (اگر خواسته شد). بی نشان و بی کشو عیناً همان
   *  `ThermalPrinter.render`ِ پیشین است.
   */
  fun saleBytes(
    context: Context,
    d: ShopData,
    sale: Sale,
    printer: PrinterRegistry.Saved,
    openDrawer: Boolean,
    copyMark: String? = null,
  ): ByteArray {
    val profile = printer.profile()
    val receipt = Receipts.mono(context, d, sale, profile.paperDots, copyMark)
    //  لوگوی دکان از فاکتورِ نسخهٔ ۲ (سربرگِ همگام) خودش در فاکتور است؛ نشانِ محلیِ همین
    //  دستگاه فقط برای دکانی می‌ماند که هنوز لوگوی همگام ندارد
    val legacy = if (profile.logo && ReceiptProfileStore.current(context).logoHash == null) logoMono(context, profile.paperDots) else null
    val page = if (legacy != null) stack(profile.paperDots, listOf(legacy, receipt), gap = 12) else receipt
    return EscPos.document(page, profile, openDrawer)
  }

  /* ============================ برگهٔ آزمون ============================ */

  /**
   *  برگهٔ آزمون: نامِ چاپگر و پروفایل، خط‌کشِ تمام‌عرض (اگر دو سرش بریده
   *  شود عرضِ کاغذ غلط است)، نمونهٔ دری و پشتو، EAN-13 و Code128.
   */
  fun testImage(context: Context, s: PrinterRegistry.Saved): MonoImage {
    val p = s.profile()
    val w = p.paperDots
    val mm = if (w >= ir.vil3ntec.tohid.core.hw.PrinterProfile.WIDTH_80MM) "۸۰" else "۵۸"
    val head = text(context, w, listOf(
      Line("برگهٔ آزمونِ چاپگر", Style.TITLE),
      Line(s.name, Style.HEAD),
      Line(if (s.link.isBlank()) "پیش‌نمایش — بی چاپگر" else "${PrinterRegistry.kind(s.link)} · ${PrinterRegistry.label(s.link)}", Style.SMALL),
      Line("کاغذ $mm میلی‌متر ($w نقطه) · ${if (p.mode.key == "column") "ستونی ESC *" else "رستری GS v 0"} · برش: ${cutLabel(p.cut.key)}", Style.SMALL),
      Line("کشو: ${if (p.drawer) "پس از فروشِ نقدی" else "خاموش"}", Style.SMALL),
      Line(ir.vil3ntec.tohid.formatMillis(System.currentTimeMillis()), Style.SMALL),
    ))
    val ruler = ruler(w)
    val samples = text(context, w, listOf(
      Line("دری: سلام، به دکانِ ما خوش آمدید — ۱۲۳۴۵۶۷۸۹۰", Style.BODY, Layout.Alignment.ALIGN_NORMAL),
      Line("پښتو: ښه راغلاست — ټ ډ ړ ږ ښ ګ ڼ ې ۍ", Style.BODY, Layout.Alignment.ALIGN_NORMAL),
      Line("Latin 0123456789 — ABC xyz", Style.BODY, Layout.Alignment.ALIGN_NORMAL),
    ))
    val ean = Barcodes.forProduct(SAMPLE_EAN)!!
    val c128 = Barcodes.forProduct(SAMPLE_128)!!
    val eanImg = Barcodes.render(ean.modules, Labels.moduleDots(ean.modules.size, w).coerceAtLeast(2), 64)
    val cImg = Barcodes.render(c128.modules, Labels.moduleDots(c128.modules.size, w).coerceAtLeast(2), 64)
    val tail = text(context, w, listOf(
      Line("$SAMPLE_EAN (EAN-13) · $SAMPLE_128 (Code128)", Style.SMALL),
      Line("اگر دو سرِ خطِ سیاه بریده نشده و نوشته‌ها کامل‌اند، چاپگر درست تنظیم است.", Style.SMALL),
    ))
    return stack(w, listOfNotNull(
      if (p.logo) logoMono(context, w) else null,
      head, ruler, samples, eanImg, cImg, tail,
    ), gap = 10)
  }

  const val SAMPLE_EAN = "5901234123457"
  const val SAMPLE_128 = "TOHID-123"

  private fun cutLabel(key: String) = when (key) { "full" -> "کامل"; "none" -> "بی‌برش"; else -> "نیمه" }

  /** نوارِ تمام‌عرض و درجه‌های هر میلی‌متر (هر ۱۰ میلی‌متر بلندتر) */
  private fun ruler(width: Int): MonoImage {
    val img = MonoImage(width, 34)
    img.fill(0, 0, width, 10)
    var x = 0
    while (x < width) {
      val tall = x % (Labels.DOTS_PER_MM * 10) == 0
      img.fill(x, 10, 2, if (tall) 24 else 12)
      x += Labels.DOTS_PER_MM
    }
    img.fill(width - 2, 10, 2, 24)
    return img
  }

  /** بایت‌های برگهٔ آزمون — چاپگرِ برچسب یک برچسبِ نمونه می‌گیرد */
  fun testBytes(context: Context, s: PrinterRegistry.Saved): ByteArray {
    if (s.isLabel) {
      val img = label(context, "برچسبِ آزمون — ${s.name}", LocaleText.afghani(120.0, LocaleChoice.digits, LocaleChoice.symbol), SAMPLE_EAN).image
        ?: error("برچسبِ آزمون کشیده نشد")
      return labelBytes(s, img, 1)
    }
    return EscPos.document(testImage(context, s), s.profile())
  }

  /* ============================ برچسبِ کالا ============================ */

  data class LabelResult(val image: MonoImage?, val error: String? = null)

  /**
   *  برچسبِ ۴۰×۳۰ (S24): نامِ دری، قیمت، بارکد و خودِ کد.
   *  بارکد همان رشتهٔ ثبت‌شده روی کالاست؛ اگر با خطِ ۰٫۲۵ میلی‌متر جا نشود
   *  برچسب ساخته نمی‌شود و دلیلش گفته می‌شود — برچسبِ ناخوانا بدتر از نبودنش است.
   */
  fun label(context: Context, name: String, price: String, code: String, size: Labels.Size = Labels.SIZE_40x30): LabelResult {
    val w = size.widthDots
    val h = size.heightDots
    val clean = ir.vil3ntec.tohid.normalizeDigits(code.trim())
    val symbol = Barcodes.forProduct(clean)
      ?: return LabelResult(null, if (clean.isEmpty()) "این کالا بارکد ندارد" else "بارکدِ «$clean» در Code128 نمی‌گنجد (فقط حرف و رقمِ لاتین)")
    val quiet = 10
    val md = Labels.moduleDots(symbol.modules.size, w - 8, quiet)
    if (md == 0) return LabelResult(null, "بارکدِ «$clean» برای برچسبِ ${size.widthMm} میلی‌متری بلند است")
    val bars = Barcodes.render(symbol.modules, md, 84, quiet)
    val shortName = if (name.length > 38) name.take(37) + "…" else name
    val top = text(context, w, listOf(Line(shortName, Style.HEAD), Line(price, Style.TITLE)), scale = 1.5f)
    val bottom = text(context, w, listOf(Line(clean, Style.SMALL)), scale = 1.4f)
    val out = MonoImage(w, h)
    out.blit(top, 0, 0)
    val barsY = (h - bars.height - bottom.height).coerceAtLeast(top.height.coerceAtMost(h / 2))
    out.blit(bars, ((w - bars.width) / 2).coerceAtLeast(0), barsY)
    out.blit(bottom, 0, (barsY + bars.height + 2).coerceAtMost(h - 1))
    return LabelResult(out)
  }

  /** برچسبِ نمونه برای پیش‌نمایش و برگهٔ آزمون */
  fun sampleLabel(context: Context): MonoImage? =
    label(context, "برچسبِ نمونه", LocaleText.afghani(120.0, LocaleChoice.digits, LocaleChoice.symbol), SAMPLE_EAN).image

  fun labelBytes(s: PrinterRegistry.Saved, image: MonoImage, copies: Int): ByteArray =
    if (s.labelLang == PrinterRegistry.LANG_ESCPOS) Labels.escPos(image, copies)
    else Labels.tspl(image, Labels.SIZE_40x30, copies)

  /* ============================ نشانِ دکان ============================ */

  private fun logoFile(context: Context) = File(context.filesDir, "printer-logo.png")

  fun hasLogo(context: Context): Boolean = logoFile(context).isFile

  /** نشان از گالری/فایل؛ حداکثر ۵۷۶ نقطه پهنا ذخیره می‌شود */
  fun saveLogo(context: Context, uri: Uri): String? = runCatching {
    val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
      ?: return "تصویر خوانده نشد"
    val w = minOf(src.width, 576)
    val h = (src.height.toFloat() * w / src.width).toInt().coerceAtLeast(1)
    val scaled = if (w == src.width) src else Bitmap.createScaledBitmap(src, w, h, true)
    logoFile(context).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    null
  }.getOrElse { "نشان ذخیره نشد: ${it.message}" }

  fun removeLogo(context: Context) { logoFile(context).delete() }

  /** نشان، یک‌بیتی، حداکثر ۶۰٪ِ پهنای کاغذ و ۱۶۰ نقطه بلندی */
  fun logoMono(context: Context, paperDots: Int): MonoImage? {
    val f = logoFile(context)
    if (!f.isFile) return null
    val src = BitmapFactory.decodeFile(f.path) ?: return null
    val maxW = (paperDots * 0.6f).toInt()
    var w = minOf(src.width, maxW)
    var h = (src.height.toFloat() * w / src.width).toInt().coerceAtLeast(1)
    if (h > 160) { w = (w * 160f / h).toInt().coerceAtLeast(1); h = 160 }
    val scaled = Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    scaled.getPixels(px, 0, w, 0, 0, w, h)
    return MonoImage.fromArgb(px, w, h, w)
  }

  /* ============================ پیش‌نمایش ============================ */

  data class Preview(val bitmap: Bitmap?, val events: List<String>, val unknown: List<String>)

  /**
   *  «چاپ روی کاغذِ خیالی»: بایت‌های واقعی خوانده و کشیده می‌شوند
   *  (`EscPosPreview`) — همان چیزی که چاپگر بیرون می‌دهد، بی کاغذ.
   */
  fun previewEscPos(bytes: ByteArray, paperDots: Int): Preview {
    val r = EscPosPreview.decode(bytes, paperDots)
    return Preview(r.paper?.let { ThermalPrinter.toBitmap(it) }, r.events, r.unknown)
  }
}
