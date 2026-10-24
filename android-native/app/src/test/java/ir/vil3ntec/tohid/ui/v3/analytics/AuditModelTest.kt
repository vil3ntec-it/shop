package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** سابقهٔ عملیات: تراشه‌های سریع، فیلتر، گروهِ روزانه و مقصدِ هر ردیف */
class AuditModelTest {
  private fun e(id: String, type: String, date: String = "2026-10-24", ref: String? = null, at: Long = 0) =
    AuditEntry(id, type = type, date = date, refId = ref, createdAt = at)

  @Test
  fun `پنج نوعِ پرتکرار به ترتیبِ شمار و سپس ترتیبِ جدول`() {
    val log = listOf(e("1", "sale"), e("2", "sale"), e("3", "return"), e("4", "purchase"), e("5", "cancel_sale"),
      e("6", "price_change"), e("7", "data_export"), e("8", "data_export"))
    assertEquals(listOf("sale", "data_export", "cancel_sale", "return", "purchase"), AuditModel.quickKinds(log))
    val sheet = AuditModel.sheetKinds(log + e("9", "mystery"))
    assertEquals(AuditModel.KINDS.size + 1, sheet.size)
    assertEquals("mystery" to 1, sheet.last())
    assertEquals("sale" to 2, sheet.first())
  }

  @Test
  fun `فیلتر تازه‌ترین را اول و گروه را روز به روز می‌دهد`() {
    val log = listOf(e("a", "sale", "2026-10-23", at = 5), e("b", "sale", "2026-10-24", at = 1), e("c", "return", "2026-10-24", at = 9))
    assertEquals(listOf("c", "b", "a"), AuditModel.filter(log, null).map { it.id })
    assertEquals(listOf("b", "a"), AuditModel.filter(log, "sale").map { it.id })
    val days = AuditModel.byDay(AuditModel.filter(log, null))
    assertEquals(listOf("2026-10-24", "2026-10-23"), days.map { it.first })
    assertEquals(2, days.first().second.size)
  }

  @Test
  fun `پاک‌کردن‌ها قرصِ حذف دارند`() {
    assertTrue(AuditModel.isDeletion("delete_product"))
    assertTrue(AuditModel.isDeletion("expense_delete"))
    assertFalse(AuditModel.isDeletion("sale"))
  }

  @Test
  fun `مقصد فقط وقتی مورد هنوز در دفتر است`() {
    val d = ShopData(
      sales = listOf(Sale("s1")),
      products = listOf(Product("p1")),
      debtors = listOf(Debtor("c1")),
      transactions = listOf(DebtTransaction("t1", debtorId = "c1")),
      suppliers = listOf(Supplier("sp1")),
      purchases = listOf(Purchase("pu1", supplierId = "sp1")),
      warehouseEntries = listOf(WarehouseEntry("w1", productId = "p1")),
    )
    assertEquals("sales/s1", AuditModel.target(e("1", "cancel_sale", ref = "s1"), d))
    assertNull(AuditModel.target(e("2", "sale", ref = "gone"), d))
    assertEquals("products/p1", AuditModel.target(e("3", "price_change", ref = "p1"), d))
    assertEquals("debtors/c1", AuditModel.target(e("4", "customer_payment", ref = "c1"), d))
    assertEquals("debtors/c1", AuditModel.target(e("5", "debt_due_set", ref = "t1"), d))
    assertEquals("purchasing/suppliers/sp1", AuditModel.target(e("6", "purchase", ref = "pu1"), d))
    assertEquals("stock/p1", AuditModel.target(e("7", "stock_adjustment", ref = "w1"), d))
    assertEquals("sales/drawer", AuditModel.target(e("8", "shift_open"), d))
    assertNull(AuditModel.target(e("9", "delete_product", ref = "p9"), d))
    assertNull(AuditModel.target(e("10", "data_export"), d))
  }
}
