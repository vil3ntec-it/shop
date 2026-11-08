package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.PriceList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-18 «قیمتِ خاصِ مشتری» روی دفترِ برنامه: نوشتن با سابقه و مجوز (`CustomerPrices`)، سبدِ صندوق
 *  (`CustomerPriceCart`)، مُهرِ «قیمتِ خاص» روی قلمِ ثبت‌شده (`SalesEngine.record`) و برچسبِ رسید؛ پرچمِ خاموش ⇒ JSON ِ
 *  همان.
 */
class CustomerPricesTest {

  private val tea = Product(id = "tea", name = "چای", salePrice = 100.0, wholesalePrice = 90.0)
  private val sugar = Product(id = "sugar", name = "بوره", salePrice = 50.0)
  private val oil = Product(id = "oil", name = "روغن", salePrice = 150.0, packs = listOf(ProductPack(name = "کارتن", sizeMilli = 12_000, priceMinor = 165_000)))
  private val base = ShopData(
    products = listOf(tea, sugar, oil),
    debtors = listOf(
      Debtor(id = "c1", name = "احمد"),
      Debtor(id = "c2", name = "کریم", priceList = "wholesale"),
      Debtor(id = "gone", name = "قدیم", mergedInto = "c1"),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "tea", units = 100.0),
      WarehouseEntry(id = "w2", productId = "sugar", units = 100.0),
      WarehouseEntry(id = "w3", productId = "oil", units = 100.0),
    ),
  )
  private var n = 0
  private val newId = { "id${++n}" }

  private fun ok(r: DebtorEngine.Result): ShopData = (r as? DebtorEngine.Result.Ok)?.data ?: error("رد شد: $r")
  private fun failed(r: DebtorEngine.Result): String = (r as? DebtorEngine.Result.Failed)?.message ?: error("نباید می‌گذشت: $r")
  private fun save(d: ShopData, id: String, e: SpecialPrice, replacing: String? = null, allowed: Boolean = true) =
    CustomerPrices.save(d, id, replacing, e, allowed, "مدیر", "2026-11-08", 7L, newId)

  @Test fun addEditRemoveWithAudit() {
    val d1 = ok(save(base, "c1", SpecialPrice("tea", 9_500)))
    assertEquals(listOf(SpecialPrice("tea", 9_500)), d1.debtors.first { it.id == "c1" }.specialPrices)
    val a1 = d1.auditLog.single()
    assertEquals(CustomerPrices.AUDIT_SET, a1.type)
    assertEquals("مدیر", a1.by)
    assertEquals("c1", a1.refId)
    assertTrue(a1.notes, a1.notes.contains("چای") && a1.notes.contains("95"))
    //  همان ردیف دوباره ⇒ همان دفتر و بی سابقه
    assertSame(d1, ok(save(d1, "c1", SpecialPrice("tea", 9_500), replacing = "tea")))
    //  ویرایش با بازه
    val d2 = ok(save(d1, "c1", SpecialPrice("tea", 9_300, "2026-11-01", "2026-11-30"), replacing = "tea"))
    assertEquals(listOf(SpecialPrice("tea", 9_300, "2026-11-01", "2026-11-30")), d2.debtors.first { it.id == "c1" }.specialPrices)
    assertTrue(d2.auditLog.last().notes.contains("95") && d2.auditLog.last().notes.contains("93"))
    //  حذفِ آخرین ⇒ `null` (همان قرض‌دارِ بی قیمتِ خاص)
    val d3 = ok(CustomerPrices.remove(d2, "c1", "tea", true, "مدیر", "2026-11-08", 8L, newId))
    assertNull(d3.debtors.first { it.id == "c1" }.specialPrices)
    assertEquals(CustomerPrices.AUDIT_REMOVE, d3.auditLog.last().type)
    assertEquals(3, d3.auditLog.size)
  }

  @Test fun refusals() {
    assertTrue(failed(save(base, "c1", SpecialPrice("tea", 9_500), allowed = false)).isNotBlank())
    assertTrue(failed(save(base, "gone", SpecialPrice("tea", 9_500))).isNotBlank())
    assertTrue(failed(save(base, "c1", SpecialPrice("ghost", 9_500))).isNotBlank())
    assertTrue(failed(save(base, "c1", SpecialPrice("tea", 0))).isNotBlank())
    assertTrue(failed(save(base, "c1", SpecialPrice("tea", 9_500, "2026-12-01", "2026-11-01"))).isNotBlank())
    val d1 = ok(save(base, "c1", SpecialPrice("tea", 9_500)))
    //  هر کالا یک ردیف: افزودنِ دوباره ردِ «تکراری»
    assertTrue(failed(save(d1, "c1", SpecialPrice("tea", 9_000))).isNotBlank())
    assertTrue(failed(CustomerPrices.remove(d1, "c1", "tea", false, null, "2026-11-08", 1L, newId)).isNotBlank())
    //  هیچ ردی چیزی ننوشت
    assertEquals(1, d1.auditLog.size)
  }

  @Test fun tillAppliesSpecialAndCustomerSwitchReprices() {
    val d = ok(save(base, "c1", SpecialPrice("tea", 9_500)))
    val cart = listOf(
      SalesEngine.CartLine("tea", 1.0),
      SalesEngine.CartLine("sugar", 2.0, unitPrice = 45.0),
      SalesEngine.CartLine("free1", 1.0, unitPrice = 30.0, label = "خدمت"),
    )
    val a = CustomerPriceCart.reprice(d, cart, PriceList.onCustomer(PriceList.FRESH, ""), "c1", "2026-11-08", true)
    assertEquals(listOf(95.0, 45.0, 30.0), a.cart.map { it.unitPrice })
    assertEquals(listOf(95.0, null, null), a.cart.map { it.specialPrice })
    assertEquals(1, a.changed)
    //  بی تغییر ⇒ همان سبد
    assertSame(a.cart, CustomerPriceCart.reprice(d, a.cart, PriceList.onCustomer(PriceList.FRESH, ""), "c1", "2026-11-08", true).cart)
    //  مشتریِ عمده بی قیمتِ خاص: چای ⇒ عمده، فیِ دستیِ بوره دست نخورد
    val sb = PriceList.onCustomer(PriceList.onCustomer(PriceList.FRESH, ""), "wholesale")
    val b = CustomerPriceCart.reprice(d, a.cart, sb, "c2", "2026-11-08", true)
    assertEquals(listOf(90.0, 45.0, 30.0), b.cart.map { it.unitPrice })
    assertEquals(listOf(null, null, null), b.cart.map { it.specialPrice })
    assertEquals(1, CustomerPriceCart.changedSince(a.cart, b.cart))
    //  فیِ دستی بی اجازهٔ تغییرِ قیمت ⇒ منبعِ بعدی
    assertEquals(listOf(90.0, null, 30.0), CustomerPriceCart.reprice(d, b.cart, sb, "c2", "2026-11-08", false).cart.map { it.unitPrice })
    //  بازهٔ گذشته ⇒ خرده
    val expired = ok(save(d, "c1", SpecialPrice("tea", 9_500, null, "2026-11-07"), replacing = "tea"))
    assertEquals(listOf(null, 45.0, 30.0), CustomerPriceCart.reprice(expired, a.cart, PriceList.onCustomer(PriceList.FRESH, ""), "c1", "2026-11-08", true).cart.map { it.unitPrice })
  }

  @Test fun packListLineIsNotOverridden() {
    val d = ok(save(base, "c1", SpecialPrice("oil", 14_000)))
    val perPiece = PackScan.linePrice(ShopStore.coreOf(oil.packs!!.first()))!!
    val cart = listOf(SalesEngine.CartLine("oil", 24.0, unitPrice = perPiece), SalesEngine.CartLine("tea", 1.0))
    val r = CustomerPriceCart.reprice(d, cart, PriceList.FRESH, "c1", "2026-11-08", true)
    assertEquals(perPiece, r.cart[0].unitPrice!!, 1e-9)
    assertNull(r.cart[0].specialPrice)
  }

  @Test fun recordStampsSpecialOnlyAtSpecialPrice() {
    val d = ok(save(base, "c1", SpecialPrice("tea", 9_500)))
    val at = CustomerPriceCart.reprice(d, listOf(SalesEngine.CartLine("tea", 1.0), SalesEngine.CartLine("sugar", 1.0)), PriceList.FRESH, "c1", "2026-11-08", true).cart
    val items = (SalesEngine.record(d, at, SalesEngine.Checkout(), "2026-11-08", 1L, newId) as SalesEngine.Result.Ok).data.saleItems
    assertEquals("special", items.first { it.productId == "tea" }.priceSource)
    assertNull(items.first { it.productId == "sugar" }.priceSource)
    assertEquals(95.0, items.first { it.productId == "tea" }.unitPrice, 1e-9)
    //  قیمتِ دستی پس از قیمتِ خاص ⇒ دیگر «قیمتِ خاص» نیست
    val edited = SalesEngine.setLinePrice(at, "tea", 94.0)
    val e = (SalesEngine.record(d, edited, SalesEngine.Checkout(), "2026-11-08", 1L, newId) as SalesEngine.Result.Ok).data.saleItems
    assertNull(e.first { it.productId == "tea" }.priceSource)
    //  رسید: برچسب کنارِ یادداشت؛ بی منبع ⇒ همان یادداشت
    val item = items.first { it.productId == "tea" }
    assertEquals("قیمتِ خاص", CustomerPriceCart.noteWith(item))
    assertEquals("قیمتِ خاص · بی شکر", CustomerPriceCart.noteWith(item.copy(note = "بی شکر")))
    assertEquals("بی شکر", CustomerPriceCart.noteWith(item.copy(priceSource = null, note = "بی شکر")))
    assertNull(CustomerPriceCart.noteWith(item.copy(priceSource = null)))
  }

  @Test fun flagOffLedgerIsByteForByteTheSame() {
    val json = Json { encodeDefaults = true }
    //  قرض‌دار و قلمِ بی قیمتِ خاص کلیدِ تازه نمی‌گیرند (نخستین تفاضلِ همگام‌سازی op نمی‌سازد)
    assertFalse(json.encodeToString(Debtor(id = "c1", name = "احمد")).contains("specialPrices"))
    assertFalse(json.encodeToString(SaleItem(id = "i1", productId = "tea", unitPrice = 100.0)).contains("priceSource"))
    //  سبدِ بی قیمتِ خاص (خاموش هرگز `specialPrice` نمی‌نشاند) ⇒ قلم بی `priceSource`
    val items = (SalesEngine.record(base, listOf(SalesEngine.CartLine("tea", 1.0, unitPrice = 95.0)), SalesEngine.Checkout(), "2026-11-08", 1L, newId) as SalesEngine.Result.Ok).data.saleItems
    assertNull(items.single().priceSource)
    assertEquals(SpecialPrice("tea", 9_500), json.decodeFromString<SpecialPrice>("""{"productId":"tea","priceMinor":9500}"""))
  }

  @Test fun mergeKeepsSurvivorPrices() {
    val d = ok(save(ok(save(base, "c1", SpecialPrice("tea", 9_500))), "c2", SpecialPrice("tea", 9_000)))
    val withSugar = ok(save(d, "c2", SpecialPrice("sugar", 4_500)))
    val r = CustomerMerge.preview(withSugar, "c1", "c2") as? CustomerMerge.Result.Ok ?: error("ادغام رد شد")
    //  ماندگار برنده؛ کالایی که فقط ادغام‌شونده دارد هم می‌ماند
    assertEquals(listOf(SpecialPrice("tea", 9_500), SpecialPrice("sugar", 4_500)), r.preview.result.specialPrices)
  }
}
