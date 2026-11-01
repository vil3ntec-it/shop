package ir.vil3ntec.tohid.core.pick

import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.qr.QrCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 *  ابزارهای برگهٔ جمع‌آوری: وزنِ ترازو ⇒ کادرِ وزن (PCK-04) و چاپِ برچسبِ کیسه‌ها (PCK-09).
 */
class PickSheetToolsTest {

  @Test
  fun scaleTextRoundTripsThroughWeightMilli() {
    assertEquals("1.25", PickText.scaleText(1.25))
    assertEquals("750", PickText.scaleText(750.0))
    assertEquals("0.005", PickText.scaleText(0.005))
    assertEquals("2", PickText.scaleText(2.0004))
    assertEquals("", PickText.scaleText(0.0))
    assertEquals("", PickText.scaleText(Double.NaN))
    for (q in listOf(1.25, 0.5, 3.333, 750.0)) assertEquals(kotlin.math.round(q * 1000).toLong(), PickText.weightMilli(PickText.scaleText(q)), "$q")
  }

  @Test
  fun bagLabelsStackEveryQrWithItsCaption() {
    val bags = (1..3).map { BagLabelPrint.Bag("VLNB-12-$it-3", "#12 · $it/3") }
    val captions = mutableListOf<String>()
    val img = BagLabelPrint.image(bags, 384) { text -> captions += text; MonoImage(200, 24).also { it.fill(0, 0, 200, 24) } }
    assertEquals(listOf("#12 · 1/3", "#12 · 2/3", "#12 · 3/3"), captions)
    assertEquals(384, img.width)
    val modules = QrCode.encode("VLNB-12-1-3").size
    val dots = BagLabelPrint.moduleDots(modules, 384)
    val qrSide = (modules + 2 * QrCode.QUIET_ZONE) * dots
    assertEquals(3 * (qrSide + 24) + 2 * BagLabelPrint.GAP, img.height)
    //  نخستین خانهٔ تیرهٔ QR ِ کیسهٔ اول (گوشهٔ نشانِ یابنده) همان‌جاست که باید
    val x0 = (384 - qrSide) / 2 + QrCode.QUIET_ZONE * dots
    val y0 = QrCode.QUIET_ZONE * dots
    assertTrue(img[x0, y0] && img[x0 + dots - 1, y0 + dots - 1], "یابندهٔ QR")
    assertTrue(dots in 3..8)
    assertFailsWith<IllegalArgumentException> { BagLabelPrint.image(emptyList(), 384) { MonoImage(1, 1) } }
  }
}
