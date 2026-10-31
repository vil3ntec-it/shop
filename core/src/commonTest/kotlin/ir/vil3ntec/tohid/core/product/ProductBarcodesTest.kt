package ir.vil3ntec.tohid.core.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** PRD-04: چند بارکد برای هر کالا؛ تکراری بین کالاها نه؛ ویرایشِ فرمِ قدیمی بارکدِ دوم را نمی‌اندازد. */
class ProductBarcodesTest {

  @Test fun parseSplitsCleansAndKeepsOrder() {
    assertEquals(listOf("6260000000017", "036000291452", "AB-12"),
      ProductBarcodes.parse(" ۶۲۶۰۰۰۰۰۰۰۰۱۷، 036000291452 ,AB-12\n6260000000017 ;"))
    assertEquals(emptyList(), ProductBarcodes.parse("  ، , ;"))
  }

  @Test fun legacySingleFieldEditKeepsTheOtherBarcodes() {
    val existing = listOf("111", "222", "333")
    assertEquals(listOf("999", "222", "333"), ProductBarcodes.mergePrimary("999", existing))
    //  همان بارکدِ نخست ⇒ هیچ تغییری
    assertEquals(existing, ProductBarcodes.mergePrimary("111", existing))
    //  پاک کردنِ کادر فقط بارکدِ نخست را برمی‌دارد
    assertEquals(listOf("222", "333"), ProductBarcodes.mergePrimary("  ", existing))
    //  بارکدِ نخستِ تازه همان دومی ⇒ یک بار
    assertEquals(listOf("222", "333"), ProductBarcodes.mergePrimary("222", existing))
    assertEquals(listOf("5"), ProductBarcodes.mergePrimary("5", emptyList()))
  }

  @Test fun barcodeOfAnotherProductIsAConflictButOwnIsNot() {
    val owners = mapOf("111" to "p1", "222" to "p2")
    assertEquals("222", ProductBarcodes.conflict(listOf("111", "222", "333"), "p1") { owners[it] })
    assertNull(ProductBarcodes.conflict(listOf("111", "333"), "p1") { owners[it] })
    //  کالای تازه (بی شناسه): هر بارکدِ موجود تعارض است
    assertEquals("111", ProductBarcodes.conflict(listOf("111"), null) { owners[it] })
  }
}
