package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrPurchaseUnits
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.PurchaseUnit
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-07 — واحدِ خرید در فرمِ «ثبت خرید» ِ گوشیِ نسخهٔ ۳ (`PurchaseFormModel`): همان حسابِ فاکتورِ خریدِ کامپیوتر
 *  (`PurchaseBook.inUnitOf/unitWhy`، `PurchaseUnits.baseCost`) و همان فیِ سفارشِ خرید (`PoForms.repriced`). چای با واحدِ پایهٔ
 *  «دانه»: کارتنِ ۱۲ (تأمین‌کنندهٔ پیش‌فرض الف، بارکدِ ۴۴۴) و کارتنِ ۲۴ (ب، ۵۵۵)؛ بارکدِ دانه ۱۱۱؛ برنج بی واحدِ خرید.
 */
class PurchaseFormUnitsTest {

  private val tea = Product(
    id = "tea", name = "چای", unit = "دانه", purchasePrice = 40.0, salePrice = 50.0, barcodes = listOf("111"),
    purchaseUnits = listOf(PurchaseUnit("کارتن ۱۲", 12_000, "supA", "444"), PurchaseUnit("کارتن ۲۴", 24_000, "supB", "555")),
  )
  private val rice = Product(id = "rice", name = "برنج", unit = "دانه", purchasePrice = 10.0, salePrice = 12.0, barcodes = listOf("222"))
  private val d0 = ShopData(
    products = listOf(tea, rice),
    suppliers = listOf(Supplier(id = "supA", name = "الف"), Supplier(id = "supB", name = "ب"), Supplier(id = "supC", name = "ج")),
  )
  private val index = ShopStore.barcodeIndex(d0)
  private val units = ShopStore.purchaseUnitIndex(d0)

  private fun form(supplier: String) = PurchaseForm(supplierId = supplier, date = "2026-11-01")

  private fun record(f: PurchaseForm): ShopData {
    var n = 0
    val r = PurchaseBook.record(
      d0, f.supplierId!!, PurchaseFormModel.lines(f) { id -> d0.products.first { it.id == id }.unit }, PurchaseFormModel.paid(f),
      f.date, "", "2026-11-01", 1L, { "pf-${n++}" },
    )
    return (r as? PurchaseBook.Result.Ok)?.data ?: error("رد شد: $r")
  }

  private fun added(s: PurchaseFormModel.Scan): PurchaseFormModel.Scan.Added = s as? PurchaseFormModel.Scan.Added ?: error("افزوده نشد: $s")

  @Test fun `ردیف به واحدِ خرید — فی به نسبتِ ضریب، زیرنویسِ واحدِ پایه، سنجش و ردیفِ فاکتور`() {
    val base = PurchaseFormModel.pickProduct(form("supB"), tea).copy(quantity = "2")
    assertNull(base.unitName)
    assertEquals("40", base.price)
    val carton = PurchaseFormModel.pickUnit(base, d0, -1, "کارتن ۲۴")
    assertEquals(base.copy(unitName = "کارتن ۲۴", price = "960"), carton)
    assertEquals(listOf("کارتن ۱۲", "کارتن ۲۴"), PurchaseFormModel.units(d0, "tea").map { it.name })
    assertTrue(PurchaseFormModel.units(d0, "rice").isEmpty())
    assertEquals(StrPurchaseUnits.lineBase(qty(48.0), "دانه", money(40.0)), PurchaseFormModel.unitLine(d0, "tea", carton.unitName, carton.quantity, carton.price))
    assertNull("ردیفِ پایه زیرنویس ندارد", PurchaseFormModel.unitLine(d0, "tea", null, "2", "40"))
    assertNull("مقدارِ نادرست زیرنویس ندارد", PurchaseFormModel.unitLine(d0, "tea", "کارتن ۲۴", "", "960"))
    //  کارتنِ ۲۴ ⇒ ۱۲ ⇒ پایه: فیِ هر دانه همان ۴۰؛ واحدی که روی کالا نیست بی اثر
    assertEquals("480", PurchaseFormModel.pickUnit(carton, d0, -1, "کارتن ۱۲").price)
    assertEquals(base, PurchaseFormModel.pickUnit(carton, d0, -1, null))
    assertEquals(carton, PurchaseFormModel.pickUnit(carton, d0, -1, "بوری"))
    //  ردیفِ واحددار ⇒ فاکتورِ دفترِ خرید (`PurchaseBook.record`، همان فاکتورِ کامپیوتر)، نه راهِ تک‌ردیفه
    assertFalse(PurchaseFormModel.multi(base))
    assertTrue(PurchaseFormModel.multi(carton))
    assertEquals(
      listOf(PurchaseBook.Line("tea", 2.0, 960.0, unit = "دانه", unitName = "کارتن ۲۴")),
      PurchaseFormModel.lines(carton) { "دانه" },
    )
    //  سنجشِ همان ثبت: ۰٫۰۱ کارتنِ ۲۴ = ۰٫۲۴ دانه (دانه اعشار نمی‌گیرد)، واحدی که دیگر روی کالا نیست
    assertFalse(PurchaseFormModel.errors(carton, d0).any)
    assertEquals(StrPurchaseUnits.wholeBase("دانه"), PurchaseFormModel.errors(carton.copy(quantity = "0.01"), d0).unit)
    assertEquals(StrPurchaseUnits.unknownUnit, PurchaseFormModel.errors(carton.copy(unitName = "بوری"), d0).unit)
    assertTrue(PurchaseFormModel.errors(carton.copy(unitName = "بوری"), d0).any)
    //  ردیفِ افزوده هم همان
    val two = PurchaseFormModel.pickRowProduct(PurchaseFormModel.addRow(carton), 0, tea).let { PurchaseFormModel.setRow(it, 0, it.more[0].copy(quantity = "1")) }
    val twoCartons = PurchaseFormModel.pickUnit(two, d0, 0, "کارتن ۱۲")
    assertEquals(PurchaseRow("tea", "1", "480", "", "کارتن ۱۲"), twoCartons.more[0])
    assertEquals(StrPurchaseUnits.wholeBase("دانه"), PurchaseFormModel.errors(PurchaseFormModel.setRow(twoCartons, 0, twoCartons.more[0].copy(quantity = "0.1")), d0).rows[0].unit)
    val d = record(twoCartons)
    assertEquals(48.0 + 12.0, ShopStore.stock(d, "tea"), 1e-9)
    assertEquals(listOf("کارتن ۲۴", "کارتن ۱۲"), d.purchases.map { it.unitName })
  }

  @Test fun `یک کارتنِ ۲۴ به ۱۰۰۰ — موجودی ۲۴ دانه و ارزش و بدهی دقیقاً ۱۰۰۰`() {
    val f = PurchaseFormModel.pickUnit(PurchaseFormModel.pickProduct(form("supB"), tea), d0, -1, "کارتن ۲۴").copy(quantity = "1", price = "1000")
    assertFalse(PurchaseFormModel.errors(f, d0).any)
    assertEquals(1000.0, PurchaseFormModel.total(f), 0.0)
    assertEquals(StrPurchaseUnits.lineBase(qty(24.0), "دانه", money(41.67)), PurchaseFormModel.unitLine(d0, "tea", f.unitName, f.quantity, f.price))
    val d = record(f)
    assertEquals(24.0, ShopStore.stock(d, "tea"), 1e-9)
    val p = d.purchases.single()
    assertEquals(1000.0, p.totalAmount, 0.0)
    assertEquals(41.67, p.purchasePrice, 1e-9)
    assertEquals(1000.0, ShopStore.supplierDebt(d, "supB"), 0.0)
    val w = d.warehouseEntries.single()
    assertEquals(24.0, w.units, 1e-9)
    assertEquals("ارزشِ انبار جمعِ دقیقِ ردیف است، نه ۲۴ × ۴۱٫۶۷ = ۱۰۰۰٫۰۸", 100_000L, w.costMinor)
  }

  @Test fun `بارکدِ واحدِ خرید همان واحد را برمی‌گزیند و بارکدِ کالا واحدِ پیش‌فرضِ تأمین‌کننده را`() {
    val a = added(PurchaseFormModel.scan(form("supA"), d0, "555", index, units))
    assertEquals("کارتن ۲۴", a.unit)
    assertEquals(form("supA").copy(productId = "tea", quantity = "1", price = "960", unitName = "کارتن ۲۴"), a.form)
    //  همان بارکد با رقمِ فارسیِ بارکدخوان ⇒ همان ردیف یکی بیشتر
    val b = added(PurchaseFormModel.scan(a.form, d0, "۵۵۵", index, units))
    assertEquals("2", b.form.quantity)
    assertTrue(b.form.more.isEmpty())
    //  بارکدِ خودِ چای با تأمین‌کنندهٔ الف ⇒ کارتنِ ۱۲ (واحدِ پیش‌فرضِ الف) در ردیفِ تازه
    val c = added(PurchaseFormModel.scan(b.form, d0, "111", index, units))
    assertNull(c.unit)
    assertEquals(PurchaseRow("tea", "1", "480", "", "کارتن ۱۲"), c.form.more.single())
    //  بارکدِ واحدِ همان ردیف ⇒ همان ردیف؛ کالای بی واحدِ خرید به پایه؛ بارکدِ ناشناخته
    val e = added(PurchaseFormModel.scan(c.form, d0, "444", index, units))
    assertEquals("2", e.form.more.single().quantity)
    val r = added(PurchaseFormModel.scan(e.form, d0, "222", index, units))
    assertEquals(PurchaseRow("rice", "1", "10", "", null), r.form.more[1])
    assertEquals(PurchaseFormModel.Scan.Unknown("999"), PurchaseFormModel.scan(r.form, d0, "999", index, units))
    //  ردیفِ خالیِ افزوده پیش از ردیفِ تازه پُر می‌شود
    val gap = added(PurchaseFormModel.scan(PurchaseFormModel.addRow(a.form), d0, "222", index, units))
    assertEquals(1, gap.form.more.size)
    assertEquals("rice", gap.form.more[0].productId)
    //  ثبت: ۲ کارتنِ ۲۴ + ۲ کارتنِ ۱۲ = ۷۲ دانه
    val d = record(r.form)
    assertEquals(72.0, ShopStore.stock(d, "tea"), 1e-9)
    assertEquals(1.0, ShopStore.stock(d, "rice"), 1e-9)
    //  سقفِ ردیف: کالای تازه پذیرفته نمی‌شود، همان کالا و همان واحد هنوز یکی بیشتر
    var full = a.form
    repeat(PurchaseFormModel.MAX_ROWS - 1) { full = PurchaseFormModel.addRow(full) }
    full = full.copy(more = full.more.map { it.copy(productId = "rice", quantity = "1", price = "10") })
    assertEquals(PurchaseFormModel.MAX_ROWS - 1, full.more.size)
    assertEquals(PurchaseFormModel.Scan.Full, PurchaseFormModel.scan(full, d0, "111", index, units))
    assertEquals("2", added(PurchaseFormModel.scan(full, d0, "555", index, units)).form.quantity)
  }

  @Test fun `واحدِ پیش‌فرضِ تأمین‌کننده هنگامِ افزودنِ کالا پیش‌گزیده و قابلِ عوض کردن است`() {
    val a = PurchaseFormModel.pickProduct(form("supA"), tea, unitsOn = true)
    assertEquals("کارتن ۱۲", a.unitName)
    assertEquals("480", a.price)
    val b = PurchaseFormModel.pickProduct(form("supB"), tea, unitsOn = true)
    assertEquals("کارتن ۲۴", b.unitName)
    assertEquals("960", b.price)
    //  تأمین‌کنندهٔ بی واحدِ پیش‌فرض، بی تأمین‌کننده، یا کالای بی واحدِ خرید ⇒ واحدِ پایه
    assertNull(PurchaseFormModel.pickProduct(form("supC"), tea, unitsOn = true).unitName)
    assertNull(PurchaseFormModel.pickProduct(PurchaseForm(), tea, unitsOn = true).unitName)
    assertEquals(PurchaseFormModel.pickProduct(form("supA"), rice), PurchaseFormModel.pickProduct(form("supA"), rice, unitsOn = true))
    //  ردیفِ افزوده همان
    assertEquals(PurchaseRow("tea", "", "960", "", "کارتن ۲۴"), PurchaseFormModel.pickRowProduct(PurchaseFormModel.addRow(b), 0, tea, unitsOn = true).more[0])
    //  قیمتِ نوشتهٔ کاربر می‌ماند؛ کالای دیگر واحدِ کهنه را نمی‌برد
    assertEquals("55", PurchaseFormModel.pickProduct(form("supA").copy(price = "55"), tea, unitsOn = true).price)
    assertNull(PurchaseFormModel.pickProduct(a, rice, unitsOn = true).unitName)
    //  کاربر عوضش می‌کند: پایه ⇒ فیِ هر دانه
    assertEquals(form("supA").copy(productId = "tea", price = "40"), PurchaseFormModel.pickUnit(a, d0, -1, null))
  }

  @Test fun `پرچمِ خاموش — همان فرم، همان ردیف‌ها و همان بدنهٔ فاکتور بایت‌به‌بایت`() {
    //  بی پرچم: هیچ واحدی پیش‌گزیده نمی‌شود و بارکدِ واحد ناشناخته است (همان پیشین)
    val f = PurchaseFormModel.pickProduct(form("supB"), tea).copy(quantity = "24")
    assertEquals(form("supB").copy(productId = "tea", quantity = "24", price = "40"), f)
    assertEquals(PurchaseFormModel.Scan.Unknown("555"), PurchaseFormModel.scan(form("supB"), d0, "555", index))
    assertNull(added(PurchaseFormModel.scan(form("supB"), d0, "111", index)).form.unitName)
    val withRow = PurchaseFormModel.pickRowProduct(PurchaseFormModel.addRow(f), 0, rice).let { PurchaseFormModel.setRow(it, 0, it.more[0].copy(quantity = "3")) }
    assertNull(withRow.more[0].unitName)
    //  همان ردیف‌های پیشین (بی `unitName`) و همان پیش‌نویسِ تک‌ردیفه
    assertEquals(
      listOf(PurchaseBook.Line("tea", 24.0, 40.0, unit = "دانه"), PurchaseBook.Line("rice", 3.0, 10.0, unit = "دانه")),
      PurchaseFormModel.lines(withRow) { "دانه" },
    )
    assertFalse(PurchaseFormModel.multi(f))
    assertNull(PurchaseFormModel.draft(f, "دانه").inUnit)
    assertEquals(PurchaseFormModel.errors(f), PurchaseFormModel.errors(f, d0))
    //  بدنهٔ دفترِ ثبت‌شده همان بدنهٔ ردیف‌های پیشین است، بایت‌به‌بایت، و فیلدِ واحد در آن نیست
    val legacy = listOf(PurchaseBook.Line("tea", 24.0, 40.0, unit = "دانه"), PurchaseBook.Line("rice", 3.0, 10.0, unit = "دانه"))
    var n = 0
    val old = (PurchaseBook.record(d0, "supB", legacy, 0.0, "2026-11-01", "", "2026-11-01", 1L, { "pf-${n++}" }) as PurchaseBook.Result.Ok).data
    val now = record(withRow)
    val json = Json.encodeToString(ShopData.serializer(), now)
    assertEquals(Json.encodeToString(ShopData.serializer(), old), json)
    assertFalse(json.contains("unitName"))
    //  پرچم پس از انتخابِ واحد خاموش شد ⇒ همه به واحدِ پایه و فیِ هر دانه (همان فرمِ بی واحد)
    val carton = PurchaseFormModel.pickUnit(f.copy(quantity = "1"), d0, -1, "کارتن ۲۴")
    assertTrue(PurchaseFormModel.hasUnits(carton))
    val off = PurchaseFormModel.withoutUnits(PurchaseFormModel.pickUnit(PurchaseFormModel.addRow(carton).let { PurchaseFormModel.pickRowProduct(it, 0, tea, unitsOn = true) }, d0, 0, "کارتن ۱۲"), d0)
    assertFalse(PurchaseFormModel.hasUnits(off))
    assertEquals(f.copy(quantity = "1"), off.copy(more = emptyList()))
    assertEquals(PurchaseRow("tea", "", "40", "", null), off.more.single())
  }
}
