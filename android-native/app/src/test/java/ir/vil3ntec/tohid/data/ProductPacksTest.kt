package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.PackBarcodes
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-04 — بارکدِ هر واحد در برنامه: ثبت و ویرایشِ بسته‌ها در موتورِ انبار (پشتِ `ShopPolicy.productTools`)،
 *  یکتاییِ بارکد بین کالاها و بسته‌ها، و اسکنِ کارتن در صندوقِ کامپیوتر (`PosSearch`). منطقِ خالص در core:
 *  `PackBarcodesTest`.
 */
class ProductPacksTest {

  private var seq = 0
  private fun newId(): String = "t${seq++}"

  private val carton = ProductPack("کارتن", 24_000, listOf("6260000000024"))
  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", unit = "دانه", salePrice = 50.0, barcodes = listOf("111"), packs = listOf(carton)),
      Product(id = "p2", name = "چای", unit = "بسته", salePrice = 100.0, barcodes = listOf("222")),
    ),
  )

  private fun ok(r: WarehouseEngine.Result) = (r as? WarehouseEngine.Result.Ok) ?: error("رد شد: $r")
  private fun failed(r: WarehouseEngine.Result) = (r as? WarehouseEngine.Result.Failed)?.message ?: error("ثبت شد: $r")

  @Test fun `PRD-04 کالای تازه با کارتنِ ۲۴ دانه‌ای و بارکدِ خودش`() {
    val r = ok(WarehouseEngine.addProduct(base, WarehouseEngine.ProductDraft(
      name = "قند", unit = "دانه", salePrice = 10.0, barcodes = listOf("333"),
      packs = listOf(PackBarcodes.Draft("کارتن", "۲۴", "444، 445"), PackBarcodes.Draft()),
    ), 0, ::newId))
    assertEquals(listOf(ProductPack("کارتن", 24_000, listOf("444", "445"))), r.data.products.first { it.id == r.id }.packs)
    //  بی بسته ⇒ همان JSON ِ پیشین (null نوشته نمی‌شود)
    val plain = ok(WarehouseEngine.addProduct(base, WarehouseEngine.ProductDraft(name = "نمک", unit = "دانه", salePrice = 5.0, packs = emptyList()), 0, ::newId))
    val p = plain.data.products.first { it.id == plain.id }
    assertNull(p.packs)
    val json = Json { encodeDefaults = true }.encodeToString(Product.serializer(), p)
    assertFalse(json, "\"packs\"" in json)
  }

  @Test fun `PRD-04 بارکد بین کالاها و بسته‌ها تکراری نمی‌شود`() {
    //  بارکدِ بستهٔ تازه = بارکدِ کالای دیگر
    assertTrue(failed(WarehouseEngine.addProduct(base, WarehouseEngine.ProductDraft(
      name = "قند", unit = "دانه", salePrice = 10.0, packs = listOf(PackBarcodes.Draft("کارتن", "24", "222")),
    ), 0, ::newId)).contains("کالای دیگری"))
    //  بارکدِ کالای تازه = بارکدِ کارتنِ کالای دیگر (فرمِ قدیمیِ تک‌بارکدی هم)
    assertEquals("این بارکد قبلاً برای کالای دیگری ثبت شده", failed(WarehouseEngine.addProduct(base,
      WarehouseEngine.ProductDraft(name = "قند", unit = "دانه", salePrice = 10.0, barcode = "6260000000024"), 0, ::newId)))
    //  ویرایشِ تک‌بارکدی: بارکدِ کالا = بارکدِ کارتنِ خودش ⇒ رد (یک اسکن هم ۱ بود هم ۲۴)
    assertTrue(failed(WarehouseEngine.editProduct(base, "p1",
      WarehouseEngine.ProductDraft(name = "روغن", unit = "دانه", salePrice = 50.0, barcode = "6260000000024"),
      "2026-10-30", 0, ::newId)).contains("کارتن"))
  }

  @Test fun `PRD-04 ویرایشِ بی بسته بسته‌ها را نگه می‌دارد و واحد بی بستهٔ تازه عوض نمی‌شود`() {
    val kept = ok(WarehouseEngine.editProduct(base, "p1",
      WarehouseEngine.ProductDraft(name = "روغن", unit = "دانه", salePrice = 55.0, barcode = "111"), "2026-10-30", 0, ::newId))
    assertEquals(listOf(carton), kept.data.products.first { it.id == "p1" }.packs)
    assertTrue(failed(WarehouseEngine.editProduct(base, "p1",
      WarehouseEngine.ProductDraft(name = "روغن", unit = "کارتن", salePrice = 50.0, barcode = "111"), "2026-10-30", 0, ::newId)).contains("بسته"))
    //  فرمِ پرچم‌دار: فهرستِ خالی ⇒ همه برداشته، با ردِ سابقه
    val cleared = ok(WarehouseEngine.editProduct(base, "p1",
      WarehouseEngine.ProductDraft(name = "روغن", unit = "دانه", salePrice = 50.0, barcodes = listOf("111"), packs = emptyList()),
      "2026-10-30", 0, ::newId))
    assertNull(cleared.data.products.first { it.id == "p1" }.packs)
    assertTrue(cleared.data.auditLog.any { it.type == "product_edit" && "بسته" in it.notes })
  }

  @Test fun `PRD-04 اسکنِ کارتن در صندوق ۲۴ دانه و ۳ ضرب در کارتن ۷۲`() {
    val packs = ShopStore.packIndex(base)
    val index = ShopStore.barcodeIndex(base)
    assertEquals(PosHit.Add(base.products[0], 24.0), PosSearch.resolve("6260000000024", index, base.products, emptyList(), 0, packs))
    assertEquals(PosHit.Add(base.products[0], 72.0), PosSearch.resolve("3*6260000000024", index, base.products, emptyList(), 0, packs))
    //  بارکدِ خودِ کالا همان ۱
    assertEquals(PosHit.Add(base.products[0], 1.0), PosSearch.resolve("111", index, base.products, emptyList(), 0, packs))
    //  بی فهرستِ بسته (صدازنندهٔ قدیمی) کارتن «ناشناس» است، نه «۱ دانه»
    assertEquals(PosHit.Unknown("6260000000024"), PosSearch.resolve("6260000000024", index, base.products, emptyList(), 0))
    //  کالای غیرفعال
    val off = base.copy(products = base.products.map { if (it.id == "p1") it.copy(active = false) else it })
    assertTrue(PosSearch.resolve("6260000000024", index, off.products, emptyList(), 0, ShopStore.packIndex(off)) is PosHit.Inactive)
    assertEquals(mapOf("111" to "p1", "222" to "p2", "6260000000024" to "p1"), ShopStore.codeOwners(base))
    assertEquals(ShopStore.barcodeIndex(base), ShopStore.codeOwners(base.copy(products = base.products.map { it.copy(packs = null) })))
  }

  @Test fun `PRD-04 کدی که هم بارکد بسته است و هم بارکد کالا مبهم است - نه ۲۴ دانه نه یک`() {
    //  تعارضِ آفلاین: دستگاهِ دیگر همان بارکدِ کارتنِ «روغن» را به «نوشابه» داد
    val clash = base.copy(products = base.products + Product(id = "p3", name = "نوشابه", unit = "دانه", salePrice = 20.0,
      barcodes = listOf("6260000000024")))
    val packs = ShopStore.packIndex(clash)
    val index = ShopStore.barcodeIndex(clash)
    assertEquals(PosHit.Ambiguous("6260000000024"), PosSearch.resolve("6260000000024", index, clash.products, emptyList(), 0, packs))
    assertEquals(PosHit.Ambiguous("6260000000024"), PosSearch.resolve("3*6260000000024", index, clash.products, emptyList(), 0, packs))
    assertEquals(PosHit.Ambiguous("۶۲۶۰۰۰۰۰۰۰۰۲۴"), PosSearch.resolve("۶۲۶۰۰۰۰۰۰۰۰۲۴", index, clash.products, emptyList(), 0, packs))
    //  پیشنهادِ برجسته هم جای مبهم را نمی‌گیرد
    assertEquals(PosHit.Ambiguous("6260000000024"), PosSearch.resolve("6260000000024", index, clash.products, clash.products, 0, packs))
    //  فقط بارکدِ کامل: کالایی که نیمهٔ بارکدِ کارتن را دارد (ITF) کارتن را مبهم نمی‌کند
    val part = base.copy(products = base.products + Product(id = "p3", name = "نوشابه", unit = "دانه", salePrice = 20.0,
      barcodes = listOf("626000000002")))
    assertEquals(PosHit.Add(part.products[0], 24.0),
      PosSearch.resolve("6260000000024", ShopStore.barcodeIndex(part), part.products, emptyList(), 0, ShopStore.packIndex(part)))
    assertTrue(PosSearch.isItemCode("۶۲۶۰۰۰۰۰۰۰۰۲۴", index))
    assertFalse(PosSearch.isItemCode("6260000000024", ShopStore.barcodeIndex(part)))
  }
}
