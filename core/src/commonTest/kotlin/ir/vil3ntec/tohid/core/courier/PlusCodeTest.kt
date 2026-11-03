package ir.vil3ntec.tohid.core.courier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** S33 — Plus Code از پینِ نشانی: بردارهای مرجعِ OLC (encoding.csv) و مرزها */
class PlusCodeTest {

  @Test
  fun referenceVectorsOfTheOpenLocationCodeProject() {
    assertEquals("8FVC2222+22", PlusCode.encode(47.0000625, 8.0000625))
    assertEquals("7FG49QCJ+2V", PlusCode.encode(20.3700625, 2.7821875))
    assertEquals("4VCPPQGP+Q9", PlusCode.encode(-41.2730625, 174.7859375))
  }

  @Test
  fun bordersClipLatitudeAndWrapLongitude() {
    //  عرضِ ۹۰ یک پله پایین؛ بیش از ۹۰ همان ۹۰
    assertEquals("CFX3X2X2+X2", PlusCode.encode(90.0, 1.0))
    assertEquals("CFX3X2X2+X2", PlusCode.encode(95.0, 1.0))
    //  طولِ ۱۸۰ همان −۱۸۰ است؛ ۵۴۰ هم
    assertEquals(PlusCode.encode(1.0, -180.0), PlusCode.encode(1.0, 180.0))
    assertEquals(PlusCode.encode(1.0, -180.0), PlusCode.encode(1.0, 540.0))
    assertEquals("22222222+22", PlusCode.encode(-90.0, -180.0))
    //  پینِ کابل: یک کد، همیشه همان
    assertEquals("8J6FH645+42", PlusCode.encode(34.5553, 69.2075))
  }

  @Test
  fun invalidCoordinatesGiveNoCode() {
    assertNull(PlusCode.encode(Double.NaN, 69.2))
    assertNull(PlusCode.encode(34.5, Double.POSITIVE_INFINITY))
  }
}
