package ir.vil3ntec.tohid.core.image

import ir.vil3ntec.tohid.core.image.StorefrontMediaSpec.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** لوگو و تصویرِ سرِ ویترین (M3) — همان مرزهای `server/src/lib/storefront-media.js`. */
class StorefrontMediaSpecTest {

  @Test fun `سقف‌ها همان سرور است`() {
    assertEquals(128, Kind.LOGO.minW); assertEquals(512, Kind.LOGO.maxW); assertEquals(150 * 1024, Kind.LOGO.maxBytes)
    assertEquals(600, Kind.HEADER.minW); assertEquals(1500, Kind.HEADER.maxW); assertEquals(400 * 1024, Kind.HEADER.maxBytes)
    assertEquals("logo", Kind.LOGO.key); assertEquals("header", Kind.HEADER.key)
  }

  @Test fun `بُرش از وسط و با نسبتِ درست`() {
    assertEquals(StorefrontMediaSpec.Rect(500, 0, 3000, 3000), StorefrontMediaSpec.crop(4000, 3000, Kind.LOGO))
    assertEquals(StorefrontMediaSpec.Rect(0, 500, 3000, 3000), StorefrontMediaSpec.crop(3000, 4000, Kind.LOGO))
    assertEquals(StorefrontMediaSpec.Rect(0, 833, 3000, 1000), StorefrontMediaSpec.crop(3000, 2667, Kind.HEADER))
    assertEquals(StorefrontMediaSpec.Rect(150, 0, 3000, 1000), StorefrontMediaSpec.crop(3300, 1000, Kind.HEADER))
    for ((w, h) in listOf(4000 to 3000, 1080 to 1920, 1919 to 641, 777 to 333, 5000 to 1200)) {
      for (k in Kind.values()) {
        val r = StorefrontMediaSpec.crop(w, h, k)
        assertTrue("$w×$h $k", r.x >= 0 && r.y >= 0 && r.x + r.w <= w && r.y + r.h <= h)
        assertEquals(k.ratio, r.w / r.h)
      }
    }
  }

  @Test fun `اندازهٔ هدف در بازهٔ سرور؛ هرگز بزرگ‌نمایی؛ کوچک ⇒ خطا`() {
    assertEquals(ImagePrep.Size(512, 512), StorefrontMediaSpec.target(3000, Kind.LOGO))
    assertEquals(ImagePrep.Size(300, 300), StorefrontMediaSpec.target(300, Kind.LOGO))
    assertEquals(ImagePrep.Size(1500, 500), StorefrontMediaSpec.target(4000, Kind.HEADER))
    assertEquals(ImagePrep.Size(900, 300), StorefrontMediaSpec.target(900, Kind.HEADER))
    assertThrows(IllegalArgumentException::class.java) { StorefrontMediaSpec.target(100, Kind.LOGO) }
    assertThrows(IllegalArgumentException::class.java) { StorefrontMediaSpec.target(599, Kind.HEADER) }
    for (w in listOf(128, 300, 512, 4000)) assertTrue(StorefrontMediaSpec.accepts(StorefrontMediaSpec.target(w, Kind.LOGO), Kind.LOGO))
    for (w in listOf(600, 901, 1500, 4000)) assertTrue(StorefrontMediaSpec.accepts(StorefrontMediaSpec.target(w, Kind.HEADER), Kind.HEADER))
  }

  @Test fun `مرزِ پذیرش همان سرور`() {
    assertFalse(StorefrontMediaSpec.accepts(ImagePrep.Size(256, 200), Kind.LOGO))
    assertFalse(StorefrontMediaSpec.accepts(ImagePrep.Size(100, 100), Kind.LOGO))
    assertFalse(StorefrontMediaSpec.accepts(ImagePrep.Size(600, 600), Kind.LOGO))
    assertFalse(StorefrontMediaSpec.accepts(ImagePrep.Size(1800, 600), Kind.HEADER))
    assertFalse(StorefrontMediaSpec.accepts(ImagePrep.Size(1200, 600), Kind.HEADER))
    assertTrue(StorefrontMediaSpec.accepts(ImagePrep.Size(1275, 425), Kind.HEADER))
  }
}
