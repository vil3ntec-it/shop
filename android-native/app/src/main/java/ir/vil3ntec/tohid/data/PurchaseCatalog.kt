package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.i18n.StrPurchaseWorkflow
import ir.vil3ntec.tohid.core.product.ProductBarcodes
import ir.vil3ntec.tohid.core.product.SimilarNames

/** Catalog matching shared by the purchase form and the write against the latest ledger. */
internal object PurchaseCatalog {
  data class Match(val product: Product? = null, val attachesBarcode: Boolean = false, val problem: String? = null)

  class Index(products: List<Product>) {
    private val byId = ShopStore.productsById(products)
    private val byName = products.groupBy { nameKey(it.name) to nameKey(it.category) }
    private val byBarcode = products.flatMap { p ->
      val codes = p.barcodes + p.packs.orEmpty().flatMap { it.barcodes } + p.purchaseUnits.orEmpty().mapNotNull { it.barcode }
      codes.flatMap(ProductBarcodes::parse).distinct().map { it to p }
    }.groupBy({ it.first }, { it.second })

    fun resolve(id: String?, name: String, category: String, rawBarcode: String?): Match {
      val code = barcode(rawBarcode)
      if (!rawBarcode.isNullOrBlank() && code == null) return Match(problem = StrPurchaseWorkflow.invalidBarcode)
      val owners = byBarcode[code].orEmpty()
      val chosen = id?.takeIf(String::isNotBlank)?.let { byId[it] ?: return Match(problem = StrGoods.productNotFound) }
      if (chosen != null) {
        if (owners.isNotEmpty() && owners.none { it.id == chosen.id }) return Match(problem = StrPurchaseWorkflow.catalogConflict)
        return Match(chosen, code != null && owners.isEmpty())
      }
      val named = byName[nameKey(name) to nameKey(category)].orEmpty().takeIf { name.isNotBlank() }.orEmpty()
      val candidates = if (owners.isEmpty()) named else if (name.isBlank()) owners else {
        owners.filter { p -> named.any { it.id == p.id } }.ifEmpty { return Match(problem = StrPurchaseWorkflow.catalogConflict) }
      }
      if (candidates.size > 1) return Match(problem = StrPurchaseWorkflow.catalogAmbiguous)
      return Match(candidates.singleOrNull(), code != null && owners.isEmpty())
    }
  }

  private fun nameKey(value: String) = SimilarNames.exactKey(value)
  fun barcode(value: String?): String? = ProductBarcodes.parse(value.orEmpty()).singleOrNull()?.takeIf { it.length <= 128 }

  fun write(
    d: ShopData, line: PurchaseBook.Line, match: Match, supplierId: String, today: String, now: Long, newId: () -> String,
  ): WarehouseEngine.Result {
    match.problem?.let { return WarehouseEngine.Result.Failed(it) }
    val product = match.product
    val price = line.salePrice
    if (price != null && (!price.isFinite() || price < 0)) return WarehouseEngine.Result.Failed(StrGoods.salePriceBad)
    val barcode = barcode(line.barcode)
    if (product == null) {
      if (line.productName.isBlank()) return WarehouseEngine.Result.Failed(StrLedger.pickProduct)
      if (price == null) return WarehouseEngine.Result.Failed(StrGoods.salePriceBad)
      return WarehouseEngine.addProduct(
        d, WarehouseEngine.ProductDraft(
          name = line.productName, category = line.category, unit = DEFAULT_UNITS.first(), salePrice = price,
          barcode = barcode.orEmpty(), supplierId = supplierId,
        ), now, newId,
      )
    }
    val addBarcode = barcode?.takeIf { match.attachesBarcode }
    if (addBarcode == null && (price == null || price == product.salePrice)) return WarehouseEngine.Result.Ok(d, id = product.id)
    return WarehouseEngine.editProduct(
      d, product.id, WarehouseEngine.ProductDraft(
        name = product.name, category = product.category, unit = product.unit, purchasePrice = product.purchasePrice,
        salePrice = price ?: product.salePrice, minStock = product.minStock,
        barcodes = product.barcodes + listOfNotNull(addBarcode),
      ), today, now, newId,
    )
  }
}
