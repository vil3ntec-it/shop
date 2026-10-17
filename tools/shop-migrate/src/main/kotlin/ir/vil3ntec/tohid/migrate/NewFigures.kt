package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.db.ShopDb

/**
 *  «محاسبهٔ مدلِ تازه» — فقط با پرسش‌های SQLِ همین طرح روی پایگاهی که
 *  `Loader` پر کرده؛ همه عددِ صحیح، بی Double. مقیاسِ هر کلید در `Keys`.
 */
object NewFigures {

  fun of(db: ShopDb, m: NewModel, months: List<Pair<Int, Int>>): LinkedHashMap<String, Long> {
    val out = LinkedHashMap<String, Long>()
    val rc = db.reconcileQueries.rowCounts().executeAsOne()
    out["rows/product"] = rc.product
    out["rows/customer"] = rc.customer
    out["rows/supplier"] = rc.supplier
    out["rows/sale"] = rc.sale
    out["rows/saleLine"] = rc.saleLine
    out["rows/saleReturn"] = rc.saleReturn
    out["rows/customerLedgerEntry"] = rc.customerLedgerEntry
    out["rows/expense"] = rc.expense
    out["rows/supplierInvoice"] = rc.supplierInvoice
    out["rows/supplierPayment"] = rc.supplierPaymentStandalone
    out["rows/stockReceipt"] = rc.stockMoveFromEntries
    out["rows/auditLog"] = rc.auditLog
    out["rows/cashShift"] = rc.cashShift
    out["rows/barcode"] = rc.barcode
    out["rows/priceChange"] = rc.priceClosed

    val sales = db.salesQueries
    val all = period(db, LegacyFigures.ALL_FROM, LegacyFigures.ALL_TO)
    val t = sales.salesTotals(LegacyFigures.ALL_FROM, LegacyFigures.ALL_TO).executeAsOne()
    out["sales/count"] = t.cnt
    out["sales/gross"] = t.subtotalMinor
    out["sales/discount"] = t.discountMinor
    out["sales/net"] = t.totalMinor
    out["sales/cogs"] = all.cogs
    out["sales/returnAmount"] = all.returnAmount
    out["sales/expenses"] = all.expenses
    out["sales/grossProfit"] = all.grossProfit
    out["sales/netProfit"] = all.netProfit

    val pay = sales.paymentTotals().executeAsList().associate { it.method to it.totalMinor }
    val ledger = db.customersQueries.ledgerTotals().executeAsList().associate { it.kind to (it.totalMinor ?: 0L) }
    out["pay/salesCash"] = pay["cash"] ?: 0L
    out["pay/salesOnAccount"] = pay["account"] ?: 0L
    out["pay/customerGive"] = ledger["give"] ?: 0L
    out["pay/customerReceive"] = -(ledger["receive"] ?: 0L)
    out["pay/supplierPaid"] = db.suppliersQueries.supplierPaidTotal().executeAsOne()

    db.customersQueries.balanceByCustomer().executeAsList().forEach { b ->
      m.customerLegacy[b.id]?.let { out["customer/$it"] = b.balanceMinor }
    }
    db.suppliersQueries.balanceBySupplier().executeAsList().forEach { b ->
      m.supplierLegacy[b.id]?.let { out["supplier/$it"] = b.balanceMinor }
    }
    val stock = db.stockQueries.stockByProduct().executeAsList().associate { it.productId to (it.qtyMilli ?: 0L) }
    m.productLegacy.forEach { (id, legacy) -> out["stock/$legacy"] = stock[id] ?: 0L }

    months.forEach { (jy, jm) ->
      val (from, to) = LegacyFigures.jalaliMonth(jy, jm)
      val p = period(db, from, to)
      val key = LegacyFigures.monthKey(jy, jm)
      out["month/$key/sales"] = p.net
      out["month/$key/grossProfit"] = p.grossProfit
      out["month/$key/netProfit"] = p.netProfit
    }
    return out
  }

  /** سودِ یک بازه، به «هزارمِ واحدِ کوچک» (مقیاسِ `Scale.COST`) */
  data class Period(val net: Long, val cogs: Long, val returnAmount: Long, val expenses: Long, val grossProfit: Long, val netProfit: Long)

  fun period(db: ShopDb, from: String, to: String): Period {
    val sales = db.salesQueries
    val net = sales.salesTotals(from, to).executeAsOne().totalMinor
    val cogs = sales.cogsMilliMinor(from, to).executeAsOne()
    val ret = sales.returnTotals(from, to).executeAsOne()
    val expenses = db.suppliersQueries.expenseTotal(from, to).executeAsOne()
    val k = Scale.QTY
    val grossProfit = net * k - cogs - (ret.amountMinor * k - ret.costMilliMinor)
    return Period(net, cogs, ret.amountMinor, expenses, grossProfit, grossProfit - expenses * k)
  }

  /** عددهای اطلاعی (مقایسه نمی‌شوند) */
  fun info(db: ShopDb, m: NewModel): LinkedHashMap<String, Long> {
    val rc = db.reconcileQueries.rowCounts().executeAsOne()
    return linkedMapOf(
      "stockMove" to rc.stockMove,
      "salePayment" to rc.salePayment,
      "measureUnit" to rc.measureUnit,
      "legacyId" to rc.legacyId,
      "placeholderProducts" to (m.products.size - m.productLegacy.size).toLong(),
      "placeholderCustomers" to (m.customers.size - m.customerLegacy.size).toLong(),
      "placeholderSuppliers" to (m.suppliers.size - m.supplierLegacy.size).toLong(),
    )
  }
}
