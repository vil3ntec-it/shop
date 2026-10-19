package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.hw.Barcodes
import ir.vil3ntec.tohid.core.hw.CutMode
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.EscPosPreview
import ir.vil3ntec.tohid.core.hw.ImageMode
import ir.vil3ntec.tohid.core.hw.Labels
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.hw.MonoPng
import ir.vil3ntec.tohid.core.hw.PrinterProfile
import ir.vil3ntec.tohid.core.hw.WeightBarcode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 *  «چاپ به PNG» بی چاپگر (HW-11): برگهٔ آزمون ⇐ بایت‌های ESC/POS ⇐ همان
 *  بایت‌ها خوانده (`EscPosPreview`) ⇐ PNG (`MonoPng`).
 *
 *  نوشتهٔ دری و پشتو این‌جا با قلمِ خودِ برنامه (Vazirmatn) و AWT کشیده
 *  می‌شود — همان کاری که پوستهٔ کامپیوتر می‌کند — تا روشن باشد که مسیرِ
 *  «تصویر ⇐ یک‌بیتی ⇐ ESC/POS ⇐ کاغذ» نوشتهٔ فارسی را سالم می‌رساند.
 *  PNG را رمزگشای مستقلِ `ImageIO` می‌خواند و نقطه‌به‌نقطه می‌سنجد.
 *
 *  با `HW_PREVIEW_DIR` پرونده‌ها همان‌جا نوشته می‌شوند (برای دیدن).
 *
 *  فقط در آزمون‌های کامپیوتر: آزمونِ واحدِ اندروید java.awt و ImageIO ندارد.
 */
class HwPreviewPngTest {

  private val out: File? = System.getenv("HW_PREVIEW_DIR")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

  private fun fontFile(name: String): File? {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "android-native/app/src/main/res/font/$name")
      if (f.isFile) return f
      val g = File(dir, "src/main/res/font/$name")
      if (g.isFile) return g
      dir = dir.parentFile
    }
    return null
  }

  /** نوشته‌ها روی کاغذی به پهنای `width` — راست‌به‌چپ، با Vazirmatn */
  private fun textBlock(width: Int, lines: List<Pair<String, Float>>): MonoImage {
    val base = fontFile("vazirmatn_regular.ttf")?.let { Font.createFont(Font.TRUETYPE_FONT, it) } ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)
    val lineH = lines.map { (it.second * 1.6f).toInt() }
    val h = lineH.sum() + 8
    val img = BufferedImage(width, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.color = Color.WHITE; g.fillRect(0, 0, width, h)
    g.color = Color.BLACK
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    var y = 4
    lines.forEachIndexed { i, (text, size) ->
      val f = base.deriveFont(mapOf(TextAttribute.SIZE to size, TextAttribute.RUN_DIRECTION to TextAttribute.RUN_DIRECTION_RTL))
      g.font = f
      val w = g.fontMetrics.stringWidth(text)
      g.drawString(text, ((width - w) / 2).coerceAtLeast(0), y + g.fontMetrics.ascent)
      y += lineH[i]
    }
    g.dispose()
    val px = img.getRGB(0, 0, width, h, null, 0, width)
    return MonoImage.fromArgb(px, width, h, width)
  }

  private fun stack(width: Int, parts: List<MonoImage>, gap: Int = 10): MonoImage {
    val out = MonoImage(width, parts.sumOf { it.height } + gap * (parts.size - 1))
    var y = 0
    parts.forEach { out.blit(it, ((width - it.width) / 2).coerceAtLeast(0), y); y += it.height + gap }
    return out
  }

  private fun testSheet(width: Int): MonoImage {
    val ruler = MonoImage(width, 34).apply {
      fill(0, 0, width, 10)
      var x = 0
      while (x < width) { fill(x, 10, 2, if (x % 80 == 0) 24 else 12); x += 8 }
      fill(width - 2, 10, 2, 24)
    }
    val head = textBlock(width, listOf(
      "برگهٔ آزمونِ چاپگر" to width * 0.07f,
      "دری: سلام، به دکانِ ما خوش آمدید — ۱۲۳۴۵" to width * 0.042f,
      "پښتو: ښه راغلاست — ټ ډ ړ ږ ښ ګ ڼ ې ۍ" to width * 0.042f,
    ))
    val ean = Barcodes.forProduct("5901234123457")!!
    val c128 = Barcodes.forProduct("TOHID-123")!!
    val scale = WeightBarcode.build(WeightBarcode.Rule("21"), "150", 1250)
    val w = Barcodes.forProduct(scale)!!
    fun bars(s: Barcodes.Symbol) = Barcodes.render(s.modules, Labels.moduleDots(s.modules.size, width).coerceAtLeast(2), 64)
    return stack(width, listOf(head, ruler, bars(ean), bars(c128), bars(w)))
  }

  private fun checkPng(png: ByteArray, paper: MonoImage) {
    val img = ImageIO.read(ByteArrayInputStream(png))
    assertEquals(paper.width, img.width)
    assertEquals(paper.height, img.height)
    for (y in 0 until paper.height step 3) for (x in 0 until paper.width) {
      val black = (img.getRGB(x, y) and 0xFFFFFF) == 0
      if (black != paper[x, y]) throw AssertionError("نقطهٔ ($x,$y) در PNG با کاغذ نمی‌خواند")
    }
  }

  @Test fun `برگهٔ آزمون — رستری ۸۰، ستونی ۵۸ با کشو — بایت ⇐ کاغذ ⇐ PNG`() {
    val cases = listOf(
      "receipt-80-raster" to PrinterProfile(paperDots = PrinterProfile.WIDTH_80MM),
      "receipt-58-column-drawer" to PrinterProfile(paperDots = PrinterProfile.WIDTH_58MM, mode = ImageMode.COLUMN, drawer = true, cut = CutMode.FULL),
    )
    for ((name, profile) in cases) {
      val sheet = testSheet(profile.paperDots)
      assertTrue("نوشته‌ای کشیده نشد", sheet.blackCount() > 2_000)
      val bytes = EscPos.document(sheet, profile, openDrawer = profile.drawer)
      val r = EscPosPreview.decode(bytes, profile.paperDots)
      assertEquals(name, emptyList<String>(), r.unknown)
      assertEquals(if (profile.drawer) 1 else 0, r.drawerKicks)
      val paper = requireNotNull(r.paper)
      assertArrayEquals(name, sheet.bits, paper.bits.copyOfRange(0, sheet.bits.size))
      val png = MonoPng.encode(paper)
      checkPng(png, paper)
      out?.let { File(it, "$name.png").writeBytes(png); File(it, "$name.bin").writeBytes(bytes) }
    }
  }

  @Test fun `برچسبِ ۴۰×۳۰ — تصویرِ TSPL و ESC-POS به PNG`() {
    val size = Labels.SIZE_40x30
    val label = MonoImage(size.widthDots, size.heightDots)
    label.blit(textBlock(size.widthDots, listOf("برنجِ سیلای ممتاز" to 26f, "۱۲۰ ؋" to 34f)), 0, 0)
    val sym = Barcodes.forProduct("5901234123457")!!
    val md = Labels.moduleDots(sym.modules.size, size.widthDots - 8)
    assertEquals(2, md)
    val bars = Barcodes.render(sym.modules, md, 84)
    label.blit(bars, (size.widthDots - bars.width) / 2, size.heightDots - bars.height - 26)
    val png = MonoPng.encode(label)
    checkPng(png, label)
    val esc = EscPosPreview.decode(Labels.escPos(label, 1), size.widthDots)
    assertArrayEquals(label.bits, requireNotNull(esc.paper).bits.copyOfRange(0, label.bits.size))
    val tspl = Labels.tspl(label, size, 1)
    assertTrue(String(tspl.copyOfRange(0, 16), Charsets.ISO_8859_1).startsWith("SIZE 40 mm,30 mm"))
    out?.let { File(it, "label-40x30.png").writeBytes(png); File(it, "label-40x30.tspl").writeBytes(tspl) }

    //  برگهٔ A4 (۳×۸، S24): ۵ برچسب، اولی در خانهٔ بالا-راست، به اندازهٔ واقعی
    val sheet = Labels.A4.sheet(label, 5)
    assertEquals(1680, sheet.width)
    assertEquals(2376, sheet.height)
    assertEquals(label.blackCount() * 5, sheet.blackCount())
    val x0 = 1400 * Labels.DOTS_PER_MM / 10 + (560 - label.width) / 2
    val y0 = 45 * Labels.DOTS_PER_MM / 10 + (288 - label.height) / 2
    for (y in 0 until label.height step 5) for (x in 0 until label.width) assertEquals(label[x, y], sheet[x0 + x, y0 + y])
    assertEquals(label.blackCount() * 24, Labels.A4.sheet(label, 99).blackCount())
    val sheetPng = MonoPng.encode(sheet)
    checkPng(sheetPng, sheet)
    out?.let { File(it, "labels-a4-3x8.png").writeBytes(sheetPng) }
  }

  @Test fun `PNG — اندازهٔ ناجور، تصویرِ بلند و پرونده‌ای که ImageIO می‌خواند`() {
    val odd = MonoImage(13, 5).apply { this[0, 0] = true; this[12, 4] = true; this[7, 2] = true }
    checkPng(MonoPng.encode(odd), odd)
    //  بیش از یک بلوکِ ۶۴ کیلوبایتیِ deflate
    val tall = MonoImage(576, 1_200).apply { for (y in 0 until 1_200 step 7) fill(0, y, 576, 1) }
    checkPng(MonoPng.encode(tall), tall)
  }
}
