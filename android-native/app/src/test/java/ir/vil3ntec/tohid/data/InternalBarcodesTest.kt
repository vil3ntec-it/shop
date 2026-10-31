package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.hw.WeightBarcode
import ir.vil3ntec.tohid.core.product.InternalBarcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-05 روی دفترِ دکان: فقط کالای انتخاب‌شدهٔ بی‌بارکد کد می‌گیرد، کدِ تکراری میانِ کالاها نیست، سابقه
 *  ردِ «بارکد عوض شد» دارد، و قاعدهٔ ترازو پیشوندش را از بارکدِ داخلی می‌گیرد.
 */
class InternalBarcodesTest {

  private var seq = 0
  private fun newId(): String = "id${seq++}"

  private val d = ShopData(
    products = listOf(
      Product(id = "01JBREAD", name = "نان", unit = "عدد"),
      Product(id = "01JEGG", name = "تخم‌مرغ", unit = "عدد", barcodes = listOf("")),
      Product(id = "01JTEA", name = "چای", unit = "بسته", barcodes = listOf("6260000000017")),
      Product(id = "01JSALT", name = "نمک", unit = "بسته"),
    ),
  )

  private fun ok(r: WarehouseEngine.Result): ShopData = (r as WarehouseEngine.Result.Ok).data

  @Test
  fun `فقط کالای انتخاب‌شدهٔ بی‌بارکد کد می‌گیرد و بقیه دست نمی‌خورند`() {
    val out = ok(InternalBarcodes.assign(d, setOf("01JBREAD", "01JEGG", "01JTEA"), emptyList(), "2026-10-31", 1L, ::newId))
    val bread = out.products.single { it.id == "01JBREAD" }.barcodes
    val egg = out.products.single { it.id == "01JEGG" }.barcodes
    assertEquals(1, bread.size)
    assertEquals(1, egg.size)
    assertTrue(InternalBarcode.isInternal(bread.single()))
    assertTrue(InternalBarcode.isInternal(egg.single()))
    assertTrue(bread.single() != egg.single())
    //  بارکددار و انتخاب‌نشده همان
    assertEquals(listOf("6260000000017"), out.products.single { it.id == "01JTEA" }.barcodes)
    assertEquals(emptyList<String>(), out.products.single { it.id == "01JSALT" }.barcodes)
    //  ردپا در سابقه، یکی برای هر کالا
    val audit = out.auditLog.filter { it.type == "product_edit" }
    assertEquals(setOf("01JBREAD", "01JEGG"), audit.map { it.refId }.toSet())
    assertTrue(audit.all { it.notes.contains("بارکد عوض شد") })
    //  همان کالا ⇒ همان کد، روی هر دستگاه
    assertEquals(bread, ok(InternalBarcodes.assign(d, setOf("01JBREAD"), emptyList(), "2026-10-31", 2L, ::newId)).products.single { it.id == "01JBREAD" }.barcodes)
  }

  @Test
  fun `کدِ گرفته‌شده به کالای دیگر داده نمی‌شود`() {
    val first = ok(InternalBarcodes.assign(d, setOf("01JBREAD"), emptyList(), "2026-10-31", 1L, ::newId))
    val code = first.products.single { it.id == "01JBREAD" }.barcodes.single()
    //  کالای دیگری با همان کدِ «نان» از پیش ثبت شده ⇒ نان کدِ دیگری می‌گیرد
    val clash = d.copy(products = d.products + Product(id = "01JOTHER", name = "دیگر", barcodes = listOf(code)))
    val out = ok(InternalBarcodes.assign(clash, setOf("01JBREAD"), emptyList(), "2026-10-31", 1L, ::newId))
    val again = out.products.single { it.id == "01JBREAD" }.barcodes.single()
    assertTrue(again != code)
    val index = ShopStore.barcodeIndex(out)
    assertEquals("01JOTHER", index[code])
    assertEquals("01JBREAD", index[again])
  }

  @Test
  fun `قاعدهٔ ترازو پیشوندِ خودش را می‌بندد و بی پیشوندِ آزاد چیزی نوشته نمی‌شود`() {
    val out = ok(InternalBarcodes.assign(d, setOf("01JBREAD", "01JSALT"), listOf(WeightBarcode.Rule(prefix = "20")), "2026-10-31", 1L, ::newId))
    assertTrue(out.products.filter { it.id == "01JBREAD" || it.id == "01JSALT" }.all { it.barcodes.single().startsWith("29") })
    val blocked = InternalBarcodes.assign(d, setOf("01JBREAD"), WeightBarcode.standardRules(), "2026-10-31", 1L, ::newId)
    assertTrue(blocked is WarehouseEngine.Result.Failed)
  }

  @Test
  fun `همهٔ انتخاب‌ها بارکد دارند ⇒ رد، بی نوشتن`() {
    assertTrue(InternalBarcodes.assign(d, setOf("01JTEA"), emptyList(), "2026-10-31", 1L, ::newId) is WarehouseEngine.Result.Failed)
    assertEquals(listOf("01JBREAD", "01JEGG"), InternalBarcodes.missing(d, listOf("01JEGG", "01JBREAD", "01JTEA")).map { it.id })
  }
}
