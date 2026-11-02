package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.hw.Barcodes
import ir.vil3ntec.tohid.core.hw.Ean13
import ir.vil3ntec.tohid.core.hw.Labels
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.product.LabelPlan
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S24 — چاپِ برچسب (PRD-23، HW-04) روی هستهٔ برچسب: برنامهٔ چاپ (`LabelPlan`)، بارکد (`Barcodes`، EAN-13) در برچسبِ ۴۰×۳۰
 *  با پهنای ماژولِ دست‌کم ۰٫۲۵ میلی‌متر روی ۲۰۳dpi (`Labels.moduleDots`)، فرمانِ TSPL، و برگهٔ A4 ِ ۳ در ۸
 *  (`Labels.A4`). «اسکنِ برچسب همان کالا را برمی‌گرداند» با یک رمزگشای مستقلِ EAN-13 روی همان تصویرِ چاپی
 *  و `BarcodeMatch` سنجیده می‌شود. در برنامه پشتِ تنظیمِ خاموشِ `label_tools`.
 *
 *  ماندهٔ سناریو (pending، نه سرخ — سخت‌افزار): چاپِ واقعی روی چاپگرِ برچسبِ ۲۰۳dpi و برگهٔ A4، و خواندنِ برچسبِ چاپ‌شده
 *  با یک بارکدخوانِ ارزانِ واقعی. نام و قیمتِ دری روی برچسب با `HwPages.label` (بومِ اندروید) کشیده می‌شود و در JVM
 *  آزمودنی نیست.
 */
class ScenarioS24Test {

  private val code = "626000000020".let { it + Ean13.checkDigit(it) }
  private val milk = Product(id = "milk", name = "شیرِ پاستوریزه", unit = "دانه", salePrice = 50.0, barcodes = listOf(code))
  private val d = ShopData(products = listOf(milk))

  /** رمزگشای مستقلِ EAN-13 از یک سطرِ تصویر — همان کارِ بارکدخوان، بی کتابخانه */
  private fun decode(img: MonoImage, dots: Int, quiet: Int = 10): String {
    val y = img.height / 2
    val m = (0 until 95).joinToString("") { if (img[(quiet + it) * dots + dots / 2, y]) "1" else "0" }
    fun bits(from: Int) = m.substring(from, from + 7)
    val l = listOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
    val g = l.map { p -> p.map { if (it == '0') '1' else '0' }.joinToString("").reversed() }
    val r = l.map { p -> p.map { if (it == '0') '1' else '0' }.joinToString("") }
    val parity = listOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLG", "LGLGLG", "LGLGGL", "LGGLGL")
    require(m.substring(0, 3) == "101" && m.substring(45, 50) == "01010" && m.substring(92) == "101") { "نگهبان‌ها نیستند" }
    val left = (0 until 6).map { i -> bits(3 + 7 * i).let { b -> l.indexOf(b).takeIf { it >= 0 }?.let { it to 'L' } ?: (g.indexOf(b) to 'G') } }
    val right = (0 until 6).map { i -> r.indexOf(bits(50 + 7 * i)) }
    require(left.none { it.first < 0 } && right.none { it < 0 }) { "نماد ناخوانا" }
    val first = parity.indexOf(left.joinToString("") { it.second.toString() })
    return "$first" + left.joinToString("") { it.first.toString() } + right.joinToString("")
  }

  @Test
  fun `S24 — برچسبِ ۴۰×۳۰ با بارکدِ خوانا روی ۲۰۳dpi و اسکنش همان کالا`() {
    val plan = LabelPlan.plan(listOf(LabelPlan.Item("milk", 3, hasCode = true)), LabelPlan.Kind.BARCODE)
    assertEquals(listOf("milk", "milk", "milk"), plan.labels)

    val sym = Barcodes.forProduct(code)!!
    assertEquals(Barcodes.Kind.EAN13, sym.kind)
    val size = Labels.SIZE_40x30
    val dots = Labels.moduleDots(sym.modules.size, size.widthDots)
    //  باریک‌ترین خط ≥ ۰٫۲۵ میلی‌متر = ۲ نقطه با ۸ نقطه در میلی‌متر
    assertTrue("ماژول $dots نقطه", dots >= 2)
    val img = Barcodes.render(sym.modules, dots, heightDots = 80)
    assertTrue(img.width <= size.widthDots)

    val tspl = String(Labels.tspl(img, size, copies = 3), Charsets.ISO_8859_1)
    assertTrue(tspl.startsWith("SIZE 40 mm,30 mm"))
    assertTrue(tspl.endsWith("PRINT 3,1\r\n"))

    val read = decode(img, dots)
    assertEquals(code, read)
    assertEquals(BarcodeMatch.Hit.Product("milk", true), BarcodeMatch.find(read, ShopStore.barcodeIndex(d)))
  }

  @Test
  fun `S24 — همان برچسب روی برگهٔ A4 در قالبِ ۳ در ۸`() {
    assertEquals(3, Labels.A4.COLS)
    assertEquals(8, Labels.A4.ROWS)
    val sym = Barcodes.forProduct(code)!!
    val label = Barcodes.render(sym.modules, 2, 80)
    assertEquals(1, Labels.A4.sheets(List(24) { label }).size)
    assertEquals(2, Labels.A4.sheets(List(25) { label }).size)
    //  ۲۴ خانه، همه جدا
    assertEquals(24, (0 until 24).map { Labels.A4.cell(it) }.toSet().size)
  }
}
