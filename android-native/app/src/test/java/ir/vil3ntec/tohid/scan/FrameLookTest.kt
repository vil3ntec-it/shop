package ir.vil3ntec.tohid.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 *  اندازه‌گیریِ فریم، روی تصویرهای ساختگی که جوابشان از پیش معلوم است.
 *
 *  همهٔ تصمیمِ چراغ روی این پنج عدد سوار است؛ اگر اینجا غلط باشد، چراغ
 *  در تاریکی خاموش می‌ماند و زیرِ نور روشن می‌شود و کسی هم نمی‌فهمد چرا.
 */
class FrameLookTest {

  private val size = 256

  /** یک تصویرِ Y با تابعی که برای هر نقطه روشنایی می‌دهد */
  private fun frame(pixelStride: Int = 1, value: (Int, Int) -> Int): ByteBuffer {
    val rowStride = size * pixelStride
    val buffer = ByteBuffer.allocate(rowStride * size)
    for (y in 0 until size) {
      for (x in 0 until size) {
        buffer.put(y * rowStride + x * pixelStride, value(x, y).toByte())
      }
    }
    return buffer
  }

  private fun look(
    pixelStride: Int = 1,
    roi: FloatArray = floatArrayOf(0.25f, 0.25f, 0.75f, 0.75f),
    value: (Int, Int) -> Int,
  ): Look = FrameLook.measure(
    y = frame(pixelStride, value),
    rowStride = size * pixelStride,
    pixelStride = pixelStride,
    width = size,
    height = size,
    roi = roi,
  )

  @Test
  fun `تصویرِ یکدست، روشنایی همان است و کنتراست صفر`() {
    val out = look { _, _ -> 40 }
    assertEquals(40, out.luma)
    assertEquals(40, out.roiLuma)
    assertEquals("یکدست یعنی بی‌کنتراست", 0, out.roiSpread)
    assertEquals(0f, out.roiClipped, 0.001f)
    assertTrue(out.usable)
  }

  @Test
  fun `دکانِ تاریک با بارکدِ زیرِ لامپ`() {
    /*
     *  همان حالتی که قاعدهٔ «تصمیم با ناحیه است نه کل» برایش نوشته شد:
     *  کلِ کادر تاریک است ولی خودِ بارکد نورِ کافی دارد. اگر روشناییِ کل
     *  را ملاک بگیریم، چراغ بی‌جهت روشن می‌شود.
     */
    val out = look { x, y ->
      val inside = x in 64..192 && y in 64..192
      if (inside) 150 else 20
    }
    assertTrue("کلِ کادر باید تاریک دیده شود", out.luma < 70)
    assertTrue("ولی ناحیهٔ بارکد روشن است", out.roiLuma > 140)
  }

  @Test
  fun `خطوطِ سیاه و سفید یعنی کنتراستِ بالا`() {
    val out = look { x, _ -> if ((x / 4) % 2 == 0) 0 else 255 }
    assertTrue("بارکدِ واقعی کنتراست دارد", out.roiSpread > 200)
  }

  @Test
  fun `ناحیهٔ سوخته، سوختگی را گزارش می‌کند`() {
    //  همان بازتابِ چراغ روی پلاستیکِ براق: ناحیه یک‌دستِ سفید می‌شود
    val out = look { x, y ->
      if (x in 64..192 && y in 64..192) 255 else 30
    }
    assertEquals("همهٔ ناحیه سوخته است", 1f, out.roiClipped, 0.01f)
    assertEquals("و کنتراستی نمانده", 0, out.roiSpread)
  }

  @Test
  fun `یک نقطهٔ سوخته کنتراست را دروغین بالا نمی‌برد`() {
    /*
     *  چرا صدک و نه «بیشینه منهای کمینه»: یک بازتابِ نقطه‌ای روی تصویرِ
     *  یکدست، با کمینه‌بیشینه کنتراست را ۲۲۵ نشان می‌داد — و چراغ فکر
     *  می‌کرد بارکدِ خوش‌کنتراستی جلویش است.
     */
    val out = look { x, y -> if (x == 128 && y == 128) 255 else 30 }
    assertTrue("نویزِ نقطه‌ای نباید کنتراست بسازد", out.roiSpread < 10)
  }

  @Test
  fun `گامِ پیکسل بزرگ‌تر از یک هم درست خوانده می‌شود`() {
    //  بعضی دوربین‌ها صفحهٔ Y را با فاصله می‌دهند؛ خواندنِ بی‌توجه به
    //  گام، بایتِ کناری را روشنایی می‌خواند و همه‌چیز به هم می‌ریزد
    val out = look(pixelStride = 2) { _, _ -> 90 }
    assertEquals(90, out.roiLuma)
  }

  @Test
  fun `ورودیِ نامعتبر، هیچ می‌دهد`() {
    val out = FrameLook.measure(
      ByteBuffer.allocate(16), rowStride = 0, pixelStride = 1,
      width = 0, height = 0, roi = floatArrayOf(0f, 0f, 1f, 1f),
    )
    assertFalse("بی‌داده نباید قابلِ تصمیم باشد", out.usable)
  }

  @Test
  fun `ناحیهٔ بیرون از تصویر، جایش کلِ فریم می‌نشیند`() {
    //  کادرِ بارکد ممکن است در فاصلهٔ بین دو فریم از تصویر بیرون بیفتد؛
    //  آن‌وقت بی‌جواب ماندن بدتر از جوابِ درشت است
    val out = look(roi = floatArrayOf(0.99f, 0.99f, 1f, 1f)) { _, _ -> 77 }
    assertTrue(out.usable)
    assertEquals(77, out.roiLuma)
  }

  @Test
  fun `هزینه با بزرگ شدنِ تصویر بالا نمی‌رود`() {
    /*
     *  ادعای «ارزان» باید سنجیده شود، نه نوشته: تعدادِ نقطه‌ها روی تصویرِ
     *  چهار برابر باید تقریباً همان بماند، چون گامِ تور درشت‌تر می‌شود.
     */
    val small = look { _, _ -> 50 }.samples
    val bigSide = size * 4
    val big = FrameLook.measure(
      y = ByteBuffer.allocate(bigSide * bigSide),
      rowStride = bigSide, pixelStride = 1,
      width = bigSide, height = bigSide,
      roi = floatArrayOf(0.25f, 0.25f, 0.75f, 0.75f),
    ).samples
    assertTrue("نقطه‌ها نباید با رزولوشن زیاد شوند: $small در برابر $big", big <= small + 200)
  }
}
