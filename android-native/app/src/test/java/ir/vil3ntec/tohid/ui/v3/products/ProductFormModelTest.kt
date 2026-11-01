package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Check
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Field
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Mode
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** فرمِ کالا: همان قاعده‌های `BulkProductSheet` (تازه) و `ProductDialog` (ویرایش)، درجا */
class ProductFormModelTest {
  private val full = ProductRowDraft(key = 1, name = "بیسکویت", category = "خوراکی", unit = "عدد", purchase = "80", sale = "100")

  @Test
  fun `ردیفِ آماده، نیمه‌پر و دست‌نخورده`() {
    assertTrue(ProductFormModel.ready(full))
    assertFalse(ProductFormModel.ready(full.copy(unit = "")))
    assertFalse(ProductFormModel.ready(full.copy(purchase = "")))
    assertFalse(ProductFormModel.ready(full.copy(sale = ".")))
    //  دسته و واحدِ تنها ردیف را «لمس‌شده» نمی‌کند (همان قاعدهٔ پیشین)
    assertTrue(ProductFormModel.untouched(ProductRowDraft(category = "خوراکی", unit = "عدد")))
    assertFalse(ProductFormModel.untouched(ProductRowDraft(barcode = "626")))
  }

  @Test
  fun `ذخیرهٔ همه — ردیفِ نیمه‌پر، هیچ ردیفِ کامل، یا پیش‌نویس‌ها`() {
    val blank = ProductRowDraft(key = 2)
    val half = ProductRowDraft(key = 3, name = "چای")
    assertEquals(Check.Broken(1), ProductFormModel.check(listOf(full, half, blank)))
    assertEquals(Check.Empty, ProductFormModel.check(listOf(blank)))
    val ok = ProductFormModel.check(listOf(blank, full)) as Check.Ok
    assertEquals(1, ok.drafts.size)
    assertEquals(1, ProductFormModel.readyCount(listOf(blank, full, half)))
  }

  @Test
  fun `پیش‌نویسِ کالای تازه همان ProductRow‌draft است`() {
    val d = ProductFormModel.draftNew(full.copy(minStock = "", barcode = "626"))
    assertEquals("بیسکویت", d.name)
    assertEquals(80.0, d.purchasePrice, 0.0)
    assertEquals(100.0, d.salePrice, 0.0)
    assertEquals(0.0, d.minStock, 0.0)
    assertEquals("626", d.barcode)
    //  ثبتِ گروهی فیلدهای «بیشتر» را نمی‌فرستد (null = دست نمی‌خورد)
    assertNull(d.subcategory); assertNull(d.active); assertNull(d.unitFactor)
    assertEquals(-1.0, ProductFormModel.draftNew(full.copy(sale = "")).salePrice, 0.0)
  }

  @Test
  fun `پیش‌نویسِ ویرایش همان دکمهٔ ذخیرهٔ ProductDialog است`() {
    val r = full.copy(purchase = "", saleUnit = "گرم", unitFactor = "x", active = false, subcategory = "کیک", supplierId = "sup")
    val d = ProductFormModel.draftEdit(r)
    assertEquals(0.0, d.purchasePrice, 0.0)
    assertTrue(d.unitFactor!!.isNaN())
    assertEquals(false, d.active)
    assertEquals("کیک", d.subcategory)
    assertEquals("sup", d.supplierId)
    assertEquals(1.0, ProductFormModel.draftEdit(full).unitFactor!!, 0.0)
    assertEquals(0.001, ProductFormModel.draftEdit(r.copy(unitFactor = "0.001")).unitFactor!!, 0.0)
  }

  @Test
  fun `فرمِ ویرایش از کالا — همان ProductFormState‌of`() {
    val p = Product("p", name = "برنج", category = "خوراکی", unit = "کیلوگرم", purchasePrice = 120.0, salePrice = 135.5, minStock = 0.0,
      barcodes = listOf("1", "2"), saleUnit = "گرم", unitFactor = 0.001, active = false)
    val r = ProductFormModel.of(p)
    assertEquals("120", r.purchase)
    assertEquals("135.5", r.sale)
    assertEquals("", r.minStock)
    assertEquals("1", r.barcode)
    assertEquals("0.001", r.unitFactor)
    assertFalse(r.active)
    assertEquals("", ProductFormModel.of(p.copy(saleUnit = "")).unitFactor)
  }

  @Test
  fun `خطای درجای هر کادر — تازه سخت‌گیرتر از ویرایش`() {
    val empty = ProductRowDraft()
    assertEquals(
      mapOf(Field.NAME to Problem.REQUIRED, Field.CATEGORY to Problem.REQUIRED, Field.UNIT to Problem.REQUIRED, Field.PURCHASE to Problem.REQUIRED, Field.SALE to Problem.REQUIRED),
      ProductFormModel.errors(empty, Mode.NEW),
    )
    assertEquals(mapOf(Field.NAME to Problem.REQUIRED, Field.SALE to Problem.REQUIRED), ProductFormModel.errors(empty, Mode.EDIT))
    assertEquals(mapOf(Field.PURCHASE to Problem.NOT_A_NUMBER), ProductFormModel.errors(full.copy(purchase = "."), Mode.EDIT))
    assertEquals(mapOf(Field.UNIT_FACTOR to Problem.REQUIRED), ProductFormModel.errors(full.copy(saleUnit = "گرم"), Mode.EDIT))
    assertEquals(mapOf(Field.UNIT_FACTOR to Problem.NOT_A_NUMBER), ProductFormModel.errors(full.copy(saleUnit = "گرم", unitFactor = "0"), Mode.EDIT))
    assertTrue(ProductFormModel.errors(full, Mode.NEW).isEmpty())
    assertEquals(Field.NAME, ProductFormModel.firstField(ProductFormModel.errors(empty, Mode.NEW)))
    assertEquals(Field.SALE, ProductFormModel.firstField(mapOf(Field.UNIT to Problem.REQUIRED, Field.SALE to Problem.REQUIRED)))
  }

  @Test
  fun `خطا پس از ترکِ کادر یا پس از ذخیره دیده می‌شود`() {
    val errs = ProductFormModel.errors(ProductRowDraft(key = 4), Mode.NEW)
    assertTrue(FormTouched().visible(4, errs).isEmpty())
    assertEquals(setOf(Field.NAME), FormTouched().touch(4, Field.NAME).visible(4, errs).keys)
    assertTrue(FormTouched().touch(5, Field.NAME).visible(4, errs).isEmpty())
    assertEquals(errs, FormTouched(all = true).visible(4, errs))
  }

  @Test
  fun `سودِ هر واحد، تکثیر و تراشهٔ برگشت‌پذیر`() {
    assertEquals(20.0, ProductFormModel.unitProfit(full)!!, 0.0)
    assertEquals(-20.0, ProductFormModel.unitProfit(full.copy(sale = "60"))!!, 0.0)
    assertNull(ProductFormModel.unitProfit(full.copy(purchase = "")))
    val copy = ProductFormModel.duplicate(full.copy(barcode = "626"), 9)
    assertEquals(9, copy.key); assertEquals("", copy.barcode); assertEquals(full.name, copy.name)
    assertEquals("", ProductFormModel.toggle("عدد", "عدد"))
    assertEquals("کارتن", ProductFormModel.toggle("عدد", "کارتن"))
    val rows = listOf(full, full.copy(key = 2))
    assertEquals("x", ProductFormModel.update(rows, 2) { it.copy(name = "x") }[1].name)
    assertEquals(full.name, ProductFormModel.update(rows, 2) { it.copy(name = "x") }[0].name)
  }
}
