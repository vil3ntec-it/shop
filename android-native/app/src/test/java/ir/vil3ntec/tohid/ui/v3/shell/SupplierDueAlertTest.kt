package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.SupplierPayment
import ir.vil3ntec.tohid.ui.theme.V3Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «سررسیدِ پرداختِ تأمین‌کننده» در «هشدارها» (e-rows-63b): همان ماندهٔ واقعیِ `PurchaseBook.overdue`، فوری، سرِ همان خریدها */
class SupplierDueAlertTest {
  private var n = 0
  private val d0 = ShopData(
    products = listOf(Product(id = "p1", name = "آرد", unit = "کیلو")),
    suppliers = listOf(Supplier(id = "s1", name = "پخش البرز"), Supplier(id = "s2", name = "پامیر")),
  )

  private fun buy(d: ShopData, sid: String, due: String) =
    (PurchaseBook.record(d, sid, listOf(PurchaseBook.Line("p1", 10.0, 100.0)), 0.0, "2026-11-01", "", "2026-11-01", 1, { "a${n++}" }, dueDate = due) as PurchaseBook.Result.Ok).data

  @Test fun `سررسیدِ گذشته فوری است و سرِ خریدهای سررسیدگذشته یا همان تأمین‌کننده می‌رود`() {
    var d = buy(d0, "s1", "2026-11-05")
    val one = shopAlertExtras(d, backupStale = false, backupText = "", today = "2026-11-15").supplierDue!!
    assertEquals(1, one.count)
    assertEquals("s1", one.onlySupplierId)
    val g1 = ShopAlerts.build(emptyList(), shopAlertExtras(d, false, "", "2026-11-15"))
    val item = g1.urgent.single { it.key == "supplier-due" }
    assertEquals(StrPurchases.dueAlert, item.title)
    assertEquals(V3Tone.ERROR, item.tone)
    assertEquals(ShellTargets.supplier("s1"), item.route)
    assertTrue(ShopAlerts.bellDot(g1, unseenNews = false))

    d = buy(d, "s2", "2026-11-10")
    val two = shopAlertExtras(d, false, "", "2026-11-15").supplierDue!!
    assertEquals(2, two.count)
    assertNull(two.onlySupplierId)
    assertEquals(ShellTargets.SUPPLIER_OVERDUE, ShopAlerts.build(emptyList(), shopAlertExtras(d, false, "", "2026-11-15")).urgent.single().route)
    assertEquals("purchasing?pay=overdue", ShellTargets.SUPPLIER_OVERDUE)
  }

  @Test fun `پرداختِ همان خرید، سررسیدِ آینده یا بی امروز ⇒ هیچ هشدار`() {
    val d = buy(d0, "s1", "2026-11-05")
    val paid = d.copy(supplierPayments = listOf(SupplierPayment("y1", "s1", 1000.0, "2026-11-12", PurchaseBook.payNote(1), 2)))
    assertNull(shopAlertExtras(paid, false, "", "2026-11-15").supplierDue)
    assertNull(shopAlertExtras(d, false, "", "2026-11-04").supplierDue)
    assertNull(shopAlertExtras(d, false, "").supplierDue)
    assertTrue(ShopAlerts.build(emptyList(), shopAlertExtras(paid, false, "", "2026-11-15")).urgent.isEmpty())
  }
}
