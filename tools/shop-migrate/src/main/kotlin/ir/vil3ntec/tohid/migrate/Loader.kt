package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.db.ShopDb

/**
 *  نشاندنِ `NewModel` در پایگاه — در یک تراکنش، به ترتیبِ ارجاع‌ها.
 *
 *  فروش همان راهی را می‌رود که برنامهٔ تازه خواهد رفت: پیش‌نویس ⇐ ردیف و
 *  پرداخت ⇐ «پایان» (حلقهٔ زنجیرهٔ هش) ⇐ مرجوعی‌ها ⇐ لغو. پس ماشه‌های
 *  طرح (ردیفِ منجمد، زنجیره، «فقط افزودنی») روی دادهٔ واقعی هم سنجیده می‌شوند.
 */
object Loader {

  fun load(db: ShopDb, m: NewModel) {
    db.transaction {
      db.shopQueries.apply {
        insertCurrency(m.currency)
        insertShop(m.shop)
        insertDevice(m.device)
        insertWarehouse(m.warehouse)
      }
      db.suppliersQueries.apply { m.suppliers.forEach { insertSupplier(it) } }
      db.catalogQueries.apply {
        m.units.forEach { insertMeasureUnit(it) }
        m.products.forEach { insertProduct(it) }
        m.conversions.forEach { insertUnitConversion(it) }
        m.barcodes.forEach { insertBarcode(it) }
        m.priceLists.forEach { insertPriceList(it) }
        m.prices.forEach { insertPrice(it) }
      }
      db.customersQueries.apply { m.customers.forEach { insertCustomer(it) } }
      db.salesQueries.apply {
        m.sales.forEach { b ->
          insertSale(b.draft)
          b.lines.forEach { insertSaleLine(it) }
          b.payments.forEach { insertSalePayment(it) }
          finalizeSale(
            finalizedAt = b.draft.createdAt, chainSeq = b.chainSeq, prevHash = b.prevHash, hash = b.hash, id = b.draft.id,
          )
        }
        m.returns.forEach { insertSaleReturn(it) }
        m.cancels.forEach { cancelSale(reason = it.reason, cancelledAt = it.at, id = it.saleId) }
      }
      db.customersQueries.apply { m.ledger.forEach { insertLedgerEntry(it) } }
      db.stockQueries.apply { m.stockMoves.forEach { insertStockMove(it) } }
      db.suppliersQueries.apply {
        m.invoices.forEach { insertSupplierInvoice(it) }
        m.supplierPayments.forEach { insertSupplierPayment(it) }
        m.expenses.forEach { insertExpense(it) }
      }
      db.cashQueries.apply { m.shifts.forEach { insertCashShift(it) } }
      db.auditQueries.apply { m.audit.forEach { insertAudit(it) } }
      db.shopQueries.apply { m.legacyIds.forEach { insertLegacyId(it) } }
    }
  }
}
