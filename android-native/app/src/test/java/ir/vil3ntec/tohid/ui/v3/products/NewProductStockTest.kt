package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.ui.text.AnnotatedString
import ir.vil3ntec.tohid.data.OpeningStock
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Field
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Mode
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** بخشِ «اختیاری» ِ فرمِ «کالای جدید»: موجودی در انبار و دو تاریخِ جمع‌وجورِ خورشیدی — قاعده‌ها درجا زیرِ همان کادر */
class NewProductStockTest {
  private val full = ProductRowDraft(key = 1, name = "روغن", category = "روغن", unit = "کارتن", purchase = "2400", sale = "2650")

  @Test
  fun `تاریخ — هشت رقم، خورشیدی یا میلادی، رقمِ فارسی هم`() {
    assertEquals("14050824", NewProductStock.dateDigits("۱۴۰۵/۰۸/۲۴"))
    assertEquals("14050824", NewProductStock.dateDigits("1405-08-24-99"))
    assertEquals("2026-11-15", NewProductStock.iso("14050824"))
    assertEquals("2027-03-01", NewProductStock.iso("20270301"))
    assertEquals("", NewProductStock.iso(""))
    assertNull(NewProductStock.iso("140508"))
    assertNull(NewProductStock.iso("14051399"))
    assertNull(NewProductStock.iso("20270230"))
  }

  @Test
  fun `نمایشِ تاریخ با جداکنندهٔ خودکار و جای درستِ مکان‌نما`() {
    assertEquals("1405/08/24", DateMask.text("14050824"))
    assertEquals("1405/0", DateMask.text("14050"))
    assertEquals("1405", DateMask.text("1405"))
    val t = DateMask.filter(AnnotatedString("14050824"))
    assertEquals("1405/08/24", t.text.text)
    assertEquals(4, t.offsetMapping.originalToTransformed(4))
    assertEquals(6, t.offsetMapping.originalToTransformed(5))
    assertEquals(10, t.offsetMapping.originalToTransformed(8))
    assertEquals(4, t.offsetMapping.transformedToOriginal(5))
    assertEquals(8, t.offsetMapping.transformedToOriginal(10))
  }

  @Test
  fun `موجودی و تاریخ‌ها — درست، نادرست، و تاریخ بی موجودی`() {
    assertTrue(NewProductStock.errors(full).isEmpty())
    assertNull(NewProductStock.stockOf(full))
    assertEquals(OpeningStock.Stock(2.0, "2026-09-01", "2027-03-01"), NewProductStock.stockOf(full.copy(stock = "2", made = "14050610", expiry = "14051210")))
    assertEquals(OpeningStock.Stock(2.5), NewProductStock.stockOf(full.copy(stock = "2.5")))
    //  ۰ همان «بی موجودی» است
    assertNull(NewProductStock.stockOf(full.copy(stock = "0")))
    assertEquals(mapOf(Field.STOCK to Problem.NOT_A_NUMBER), NewProductStock.errors(full.copy(stock = ".")))
    assertEquals(mapOf(Field.STOCK to Problem.NEEDS_STOCK), NewProductStock.errors(full.copy(expiry = "14051210")))
    assertEquals(mapOf(Field.STOCK to Problem.NEEDS_STOCK), NewProductStock.errors(full.copy(stock = "0", made = "14050610")))
    assertEquals(mapOf(Field.EXPIRY to Problem.BAD_DATE), NewProductStock.errors(full.copy(stock = "1", expiry = "140512")))
    assertEquals(mapOf(Field.MADE to Problem.BAD_DATE), NewProductStock.errors(full.copy(stock = "1", made = "14051340")))
    assertEquals(mapOf(Field.MADE to Problem.MADE_AFTER_EXPIRY), NewProductStock.errors(full.copy(stock = "1", made = "14051220", expiry = "14051210")))
    //  تاریخِ انقضای گذشته رد نمی‌شود (جنسِ رسیده همان است که هست) — زیرِ کادر «منقضی» گفته می‌شود
    assertTrue(NewProductStock.errors(full.copy(stock = "1", expiry = "14050101")).isEmpty())
    assertEquals(-239L, NewProductStock.expiryDays(full.copy(expiry = "14050101"), "2026-11-15"))
    assertEquals(12L, NewProductStock.expiryDays(full.copy(expiry = "14050906"), "2026-11-15"))
    assertNull(NewProductStock.expiryDays(full.copy(expiry = "1405"), "2026-11-15"))
    assertEquals(4800.0, NewProductStock.total(full.copy(stock = "2"))!!, 0.0)
  }

  @Test
  fun `فرم — خطای اختیاری درجا، ردیف آماده نیست، و ردیفِ فقط‌موجودی دست‌نخورده نیست`() {
    val bad = full.copy(stock = "2", made = "14051220", expiry = "14051210")
    assertEquals(Problem.MADE_AFTER_EXPIRY, ProductFormModel.errors(bad, Mode.NEW)[Field.MADE])
    assertFalse(ProductFormModel.ready(bad))
    assertEquals(ProductFormModel.Check.Broken(0), ProductFormModel.check(listOf(bad)))
    assertEquals(Field.MADE, ProductFormModel.firstField(ProductFormModel.errors(bad, Mode.NEW)))
    assertFalse(ProductFormModel.untouched(ProductRowDraft(stock = "2")))
    assertFalse(ProductFormModel.untouched(ProductRowDraft(expiry = "1405")))
    //  ویرایش این بخش را ندارد ⇒ خطای آن هم ندارد
    assertTrue(ProductFormModel.errors(full.copy(stock = "x"), Mode.EDIT).isEmpty())
    //  تکثیرِ ردیف موجودی و تاریخ را هم می‌برد (بارکد نه)
    val copy = ProductFormModel.duplicate(full.copy(stock = "2", expiry = "14051210", barcode = "626"), 9)
    assertEquals("2" to "14051210", copy.stock to copy.expiry)
    assertEquals("", copy.barcode)
  }
}
