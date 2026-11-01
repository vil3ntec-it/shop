package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** فرم‌های انبار: ورود، اصلاح، ورودِ چند قلم و انبارگردانی — همه پیش از موتور */
class StockFormsModelTest {

  private val rice = Product(id = "rice", name = "برنج", unit = "کیلو", purchasePrice = 95.0, barcodes = listOf("626001"))
  private val oil = Product(id = "oil", name = "روغن", unit = "لیتر", purchasePrice = 0.0)
  private val ledger = ShopData(
    products = listOf(rice, oil),
    warehouseEntries = listOf(WarehouseEntry(id = "e1", productId = "rice", units = 12.0, price = 90.0)),
  )

  /* ------------------------------ ورود ------------------------------ */

  @Test
  fun `ورود از کالا یا بارکد آغاز می شود`() {
    val byId = EntryForm.start(ledger, "rice", null)
    assertEquals("rice", byId.productId)
    assertEquals("کیلو", byId.unit)
    assertEquals("95", byId.price)
    assertEquals("rice", EntryForm.start(ledger, null, "626001").productId)
    assertEquals("", EntryForm.start(ledger, null, "999").productId)
    assertEquals("", EntryForm.start(ledger, "gone", null).productId)
  }

  @Test
  fun `کارتن ضرب در هر کارتن مقدار است`() {
    val f = EntryForm(productId = "rice", cartonMode = true, cartons = "4", perCarton = "25", units = "7", price = "10")
    assertEquals(100.0, f.amount, 0.0)
    assertEquals(1000.0, f.total!!, 0.0)
    val d = f.draft()
    assertEquals(4.0, d.cartons, 0.0)
    assertEquals(25.0, d.perCarton, 0.0)
    assertEquals(100.0, d.units, 0.0)
    val unitMode = f.copy(cartonMode = false).draft()
    assertEquals(0.0, unitMode.cartons, 0.0)
    assertEquals(7.0, unitMode.units, 0.0)
  }

  @Test
  fun `خطای هر کادر پیش از موتور`() {
    val blank = EntryForm()
    assertEquals(setOf(EntryField.PRODUCT, EntryField.QUANTITY, EntryField.PRICE), blank.errors(ledger).keys)
    val badDate = EntryForm(productId = "rice", units = "2", price = "0", expiry = "1406/14/40")
    assertEquals(setOf(EntryField.EXPIRY), badDate.errors(ledger).keys)
    assertTrue(EntryForm(productId = "rice", units = "2", price = "0", expiry = "1406/01/30").errors(ledger).isEmpty())
  }

  @Test
  fun `تاریخ خورشیدی به iso و نادرست به علامت سوال`() {
    val ok = EntryForm(productId = "rice", units = "1", price = "1", expiry = "1406/01/30", made = "").draft()
    assertEquals("2027-04-19", ok.expiryDate)
    assertEquals("", ok.productionDate)
    assertEquals("?", EntryForm(expiry = "abc").draft().expiryDate)
  }

  @Test
  fun `پیش نویس ورود را موتور می پذیرد`() {
    val draft = EntryForm.start(ledger, "rice", null).copy(units = "3").draft()
    assertTrue(WarehouseEngine.addEntry(ledger, draft, "2026-10-20", 1L) { "x" } is WarehouseEngine.Result.Ok)
  }

  @Test
  fun `فرم دست نخورده تمیز است`() {
    val start = EntryForm.start(ledger, "rice", null)
    assertFalse(start.dirty(start))
    assertTrue(start.copy(units = "1").dirty(start))
  }

  /* ------------------------------ اصلاح ------------------------------ */

  @Test
  fun `سه گونه ی اصلاح و پیش نمایش`() {
    assertTrue(AdjustChoice.UP.increase)
    assertFalse(AdjustChoice.DOWN.increase)
    assertEquals(WarehouseEngine.AdjustKind.SUPPLIER_RETURN, AdjustChoice.RETURN.kind)
    assertFalse(AdjustChoice.RETURN.increase)
    assertEquals(130.0, AdjustForm(AdjustChoice.DOWN, "14").after(144.0)!!, 0.0)
    assertEquals(150.0, AdjustForm(AdjustChoice.UP, "6").after(144.0)!!, 0.0)
    assertNull(AdjustForm(AdjustChoice.UP, "").after(144.0))
    assertEquals(setOf(AdjustForm.AMOUNT, AdjustForm.REASON), AdjustForm().errors().keys)
    assertTrue(AdjustForm(AdjustChoice.UP, "2", "شکسته").errors().isEmpty())
  }

  /* -------------------------- ورودِ چند قلم -------------------------- */

  @Test
  fun `ردیف آماده و ردیف نیمه کاره`() {
    var s = BulkEntryState.start(ledger, "2026-10-20")
    assertFalse(s.dirty)
    assertEquals(StrStockProbe.noRows, s.problem())
    s = s.update(0) { it.pick(rice).copy(qty = "5") }
    assertEquals("95", s.rows[0].price)
    assertEquals(1, s.ready.size)
    s = s.add("کیلو").update(1) { it.copy(qty = "2") }
    assertEquals(2, s.brokenRow())
    s = s.remove(1)
    assertNull(s.brokenRow())
    assertNull(s.problem())
  }

  @Test
  fun `اسکن ردیف می سازد`() {
    var s = BulkEntryState.start(ledger, "2026-10-20").scanned(ledger, "626001", "کیلو")
    assertEquals(1, s.rows.size)
    assertEquals("rice", s.rows[0].productId)
    s = s.scanned(ledger, "777", "کیلو")
    assertEquals(2, s.rows.size)
    assertTrue(s.rows[1].newProduct)
    assertEquals("777", s.rows[1].newBarcode)
    assertTrue(s.rows[1].scanMiss)
  }

  @Test
  fun `تکثیر بی بارکد و ثبت همه با موتور`() {
    var s = BulkEntryState.start(ledger, "2026-10-20")
      .update(0) { it.asNew("555").copy(newName = "عدس", qty = "3", price = "40", unit = "کیلو") }
    s = s.duplicate(0)
    assertEquals("", s.rows[1].newBarcode)
    s = s.update(1) { it.copy(newName = "نخود") }
    val rows = s.entries(ledger)
    assertEquals(2, rows.size)
    assertEquals("عدس", rows[0].newProduct!!.name)
    assertEquals("2026-10-20", rows[0].entry.date)
    assertTrue(WarehouseEngine.addEntries(ledger, rows, "2026-10-20", 1L, IdSeq()::next) is WarehouseEngine.Result.Ok)
  }

  /* ---------------------------- انبارگردانی ---------------------------- */

  @Test
  fun `شمارش و اختلاف و عدد نادرست`() {
    val s = CountState(counts = mapOf("rice" to "۱۰", "oil" to "x", "none" to " "))
    assertEquals(10.0, s.valid["rice"]!!, 0.0)
    assertTrue(s.invalid("oil"))
    assertEquals("oil", s.firstInvalid(ledger)!!.id)
    assertEquals(-2.0, s.diff("rice", 12.0)!!, 0.0)
    assertEquals(0.0, CountState(mapOf("rice" to "12")).diff("rice", 12.0)!!, 0.0)
    assertNull(s.diff("oil", 0.0))
    assertEquals(1, s.lines(ledger).size)
    assertTrue(s.dirty)
    assertFalse(CountState(mapOf("rice" to "")).dirty)
  }

  @Test
  fun `ردیف های شمارش فعال و به ترتیب نام`() {
    val d = ledger.copy(products = ledger.products + Product(id = "x", name = "آب", active = false))
    assertEquals(listOf("برنج", "روغن"), CountState.rows(d, "").map { it.name })
    assertEquals(listOf("برنج"), CountState.rows(d, "6260").map { it.name })
  }

  private class IdSeq { private var n = 0; fun next(): String = "id${n++}" }
}

/** همان متن‌های فارسیِ جدول (زبانِ پیش‌فرضِ آزمون) */
private object StrStockProbe {
  val noRows: String get() = ir.vil3ntec.tohid.core.i18n.StrStock.errNoRows
}
