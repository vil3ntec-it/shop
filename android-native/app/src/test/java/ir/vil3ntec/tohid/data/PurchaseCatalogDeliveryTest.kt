package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrPurchaseWorkflow
import ir.vil3ntec.tohid.core.sync.v3.Hlc
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v3.V3Convert
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseCatalogDeliveryTest {
  private val day = "2026-11-28"
  private val deliveryDay = "2026-11-30"
  private val known = Product("tea", name = "چای سبز", category = "نوشیدنی", unit = "عدد", purchasePrice = 20.0, salePrice = 40.0, barcodes = listOf("10"))
  private val supplierId = "supplier"
  private val base = ShopData(products = listOf(known), suppliers = listOf(Supplier(supplierId, name = "پخش")))
  private var nextId = 0

  private fun record(lines: List<PurchaseBook.Line>, pending: Boolean = false, paid: Double = 0.0) =
    PurchaseBook.record(
      base, supplierId, lines, paid, day, "", day, 100, { "new-${nextId++}" },
      key = "invoice", awaitDelivery = pending,
    )

  @Test fun categoryAndNameReuseTheProductWhileABarcodeOnlyLineFindsTheSameProduct() {
    val result = record(listOf(
      PurchaseBook.Line("", 10.0, 600.0, productName = "چاي سبز", category = "نوشیدنی", barcode = "۲۰",
        unitName = "carton", factorMilli = 25_000, productionDate = "2026-11-01", expiryDate = "2027-01-01"),
      PurchaseBook.Line("", 2.0, 24.0, barcode = "۱۰"),
    )) as PurchaseBook.Result.Ok
    val data = result.data
    assertEquals(listOf("tea", "tea"), data.purchases.map { it.productId })
    assertEquals(listOf("10", "20"), data.products.single().barcodes)
    assertEquals(252.0, ShopStore.stock(data, known.id), 0.0)
    assertEquals(24.0, data.products.single().purchasePrice, 0.0)
    assertEquals(40.0, data.products.single().salePrice, 0.0)
    assertEquals("2026-11-01", data.warehouseEntries.first().productionDate)
    assertEquals("2027-01-01", data.warehouseEntries.first().expiryDate)
    assertEquals(6048.0, ShopStore.supplierDebt(data, supplierId), 0.0)
    assertFalse(LedgerDiff.toJson(data).getValue("purchases").toString().contains("\"delivery\""))
  }

  @Test fun deliveryUsesSyncedRowsAndConcurrentConfirmationsNeverDoubleStockOrDebt() {
    val purchase = record(listOf(
      PurchaseBook.Line("", 10.0, 600.0, productName = "چای کوهی", category = "نوشیدنی", salePrice = 30.0,
        barcode = "9504000370640", unitName = "carton", factorMilli = 25_000,
        productionDate = "2026-11-01", expiryDate = "2027-02-02"),
    ), pending = true, paid = 1000.0) as PurchaseBook.Result.Ok
    val pending = purchase.data
    val id = pending.purchases.single().id
    val product = pending.products.single { it.id != known.id }
    val original = LedgerDiff.toJson(base)
    val recorded = LedgerDiff.toJson(pending)
    val invoiceOps = LedgerDiff.diff(LedgerDiff.shadowOf(original), recorded, 100)
    val purchaseOp = invoiceOps.single { it.table == "purchases" }
    val native = V3Convert.fromLegacyOp(purchaseOp, Hlc.format(1762500000000, 0, "delivery-test"))
    assertTrue(native.reason, native.native)
    assertEquals(purchaseOp.fields!!.getValue("delivery"), native.ops.single().getValue("fields").jsonObject.getValue("delivery"))

    val otherDevice = LedgerDiff.fromJson(LedgerDiff.applyRemote(original, invoiceOps))
    assertEquals(0.0, ShopStore.stock(otherDevice, product.id), 0.0)
    assertEquals(5000.0, ShopStore.supplierDebt(otherDevice, supplierId), 0.0)
    assertEquals(0.0, PurchaseBook.returnable(otherDevice, supplierId, product.id), 0.0)
    val pendingView = PurchaseViews.all(otherDevice, day).single()
    assertTrue(pendingView.awaitingDelivery)
    assertEquals("2027-02-02", pendingView.lines.single().expiry)

    val received = PurchaseBook.confirmDelivery(otherDevice, setOf(id), deliveryDay, 200) as PurchaseBook.Result.Ok
    val parallel = PurchaseBook.confirmDelivery(pending, setOf(id), deliveryDay, 201) as PurchaseBook.Result.Ok
    val receivedOps = LedgerDiff.diff(LedgerDiff.shadowOf(recorded), LedgerDiff.toJson(received.data), 200)
    assertFalse(receivedOps.any { it.table == "purchases" || it.table == "supplierPayments" })
    val combined = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(parallel.data), receivedOps))
    assertEquals(1, combined.warehouseEntries.size)
    assertEquals(1, combined.stockMovements.size)
    assertEquals(250.0, ShopStore.stock(combined, product.id), 0.0)
    assertEquals(24.0, ShopStore.product(combined, product.id)!!.purchasePrice, 0.0)
    assertEquals(30.0, ShopStore.product(combined, product.id)!!.salePrice, 0.0)
    assertEquals(deliveryDay, combined.warehouseEntries.single().date)
    assertEquals(600_000L, combined.warehouseEntries.single().costMinor)
    assertEquals(5000.0, ShopStore.supplierDebt(combined, supplierId), 0.0)
    assertEquals(250.0, PurchaseBook.returnable(combined, supplierId, product.id), 0.0)
    assertFalse(PurchaseViews.all(combined, deliveryDay).single().awaitingDelivery)
    assertTrue(PurchaseBook.confirmDelivery(combined, setOf(id), deliveryDay, 300) is PurchaseBook.Result.Failed)
  }

  @Test fun offlinePurchasesWithTheSameNumberKeepSeparateReceiptsAndPayments() {
    val first = record(listOf(PurchaseBook.Line("tea", 2.0, 25.0)), pending = true) as PurchaseBook.Result.Ok
    val second = PurchaseBook.record(
      base, supplierId, listOf(PurchaseBook.Line("tea", 5.0, 25.0)), 0.0, day, "", day, 101,
      { "new-${nextId++}" }, key = "another-invoice", awaitDelivery = true,
    ) as PurchaseBook.Result.Ok
    val combined = first.data.copy(purchases = first.data.purchases + second.data.purchases)
    val invoices = PurchaseBook.invoices(combined)
    assertEquals(listOf(1, 1), invoices.map { it.no })
    assertEquals(2, invoices.map { it.key }.distinct().size)
    val received = PurchaseBook.confirmDelivery(combined, setOf(second.data.purchases.single().id), deliveryDay, 200) as PurchaseBook.Result.Ok
    assertEquals(5.0, ShopStore.stock(received.data, "tea"), 0.0)
    assertEquals(1, PurchaseViews.all(received.data, deliveryDay).count { it.awaitingDelivery })
    val paid = LedgerEngine.paySupplier(
      received.data, supplierId, 125.0, deliveryDay, PurchaseBook.payNote(1), deliveryDay, 300,
      { "new-${nextId++}" }, purchaseInvoiceId = "another-invoice",
    ) as LedgerEngine.Result.Ok
    assertEquals(mapOf("invoice" to 50.0, "another-invoice" to 0.0),
      PurchaseBook.open(paid.data).associate { it.invoice.deliveryInvoiceId to it.remaining })
  }

  @Test fun conflictingBarcodeRejectsTheEntireMultiLinePurchase() {
    val failure = record(listOf(
      PurchaseBook.Line("", 2.0, 20.0, productName = "چای کوهی", category = "نوشیدنی", salePrice = 30.0),
      PurchaseBook.Line("", 1.0, 80.0, productName = "آب", category = "نوشیدنی", barcode = "10", salePrice = 100.0),
    ))
    assertTrue(failure is PurchaseBook.Result.Failed)
    assertTrue((failure as PurchaseBook.Result.Failed).message.contains(StrPurchaseWorkflow.catalogConflict))
    assertEquals(listOf(known), base.products)
    assertTrue(base.purchases.isEmpty())
  }
}
