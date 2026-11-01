package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry

/** دفترِ کوچکِ آزمون‌های فروش: سه کالا (دانه‌ای، وزنی، تمام‌شده)، یک کالای غیرفعال و سه قرض‌دار */
object SalesTestLedger {
  val soap = Product(id = "soap", name = "صابون", unit = "عدد", salePrice = 50.0, barcodes = listOf("626001003"))
  val rice = Product(id = "rice", name = "برنج", unit = "کیلوگرم", salePrice = 120.0, barcodes = listOf("626001007"))
  val tea = Product(id = "tea", name = "چای", unit = "عدد", salePrice = 80.0, barcodes = listOf("626009001"))
  val old = Product(id = "old", name = "کالای کهنه", unit = "عدد", salePrice = 10.0, barcodes = listOf("111222333"), active = false)
  val carton = Product(id = "carton", name = "نوشابه", unit = "کارتن", salePrice = 600.0, saleUnit = "بطری", unitFactor = 1.0 / 12)

  val ledger = ShopData(
    products = listOf(soap, rice, tea, old, carton),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "soap", units = 3.0),
      WarehouseEntry(id = "w2", productId = "rice", units = 10.0),
      WarehouseEntry(id = "w3", productId = "old", units = 5.0),
      WarehouseEntry(id = "w4", productId = "carton", units = 4.0),
    ),
    debtors = listOf(
      Debtor(id = "d1", name = "احمد کریمی", phone = "0700111222"),
      Debtor(id = "d2", name = "محمود", phone = ""),
      Debtor(id = "d3", name = "Karim"),
    ),
    transactions = listOf(
      DebtTransaction(id = "t1", debtorId = "d1", type = "give", amount = 1200.0),
      DebtTransaction(id = "t2", debtorId = "d3", type = "receive", amount = 300.0),
    ),
    sales = listOf(Sale(id = "s1", invoiceNumber = 1, status = "completed"), Sale(id = "s2", invoiceNumber = 2, status = "cancelled")),
    saleItems = listOf(
      SaleItem(id = "i1", saleId = "s1", productId = "tea", quantity = 1.0),
      SaleItem(id = "i2", saleId = "s2", productId = "soap", quantity = 1.0),
    ),
  )
}
