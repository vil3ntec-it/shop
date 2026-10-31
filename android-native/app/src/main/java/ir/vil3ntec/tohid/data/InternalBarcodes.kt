package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.hw.WeightBarcode
import ir.vil3ntec.tohid.core.i18n.StrLabel
import ir.vil3ntec.tohid.core.product.InternalBarcode

/**
 *  بارکدِ داخلی برای کالای بی‌بارکد (PRD-05) — سمتِ دفترِ دکان برای `core/product/InternalBarcode`.
 *
 *  فقط کالاهای انتخاب‌شده‌ای که هیچ بارکدی ندارند کد می‌گیرند؛ کالای بارکددار دست نمی‌خورد. کدِ هر کالا از
 *  شناسهٔ ثابتش ساخته می‌شود و با بارکدهای همهٔ کالاها و کدهای همین دور سنجیده می‌شود (بارکد بین کالاها تکراری
 *  نمی‌شود، PRD-04). ثبت از راهِ `WarehouseEngine.bulkUpdate` است، پس هر کالا ردیفِ «ویرایشِ گروهی… بارکد عوض شد»
 *  را در سابقه می‌گیرد و همگام‌سازی چیزِ تازه‌ای نمی‌خواهد. `rules` قاعده‌های ترازوی همین دستگاه (`ScaleLabels`).
 */
object InternalBarcodes {

  /** کالاهای بی‌بارکد از میانِ `ids` */
  fun missing(d: ShopData, ids: Collection<String>): List<Product> {
    val want = ids.toSet()
    return d.products.filter { it.id in want && it.barcodes.none { b -> b.isNotBlank() } }
  }

  fun assign(
    d: ShopData,
    ids: Collection<String>,
    rules: List<WeightBarcode.Rule>,
    today: String,
    now: Long,
    newId: () -> String,
  ): WarehouseEngine.Result {
    val taken = HashSet(ShopStore.barcodeIndex(d).keys)
    val codes = LinkedHashMap<String, String>()
    for (p in missing(d, ids)) {
      when (val r = InternalBarcode.generate(p.id, { it in taken }, rules)) {
        is InternalBarcode.Result.Ok -> { codes[p.id] = r.code; taken += r.code }
        InternalBarcode.Result.NoFreePrefix -> return WarehouseEngine.Result.Failed(StrLabel.noFreePrefix)
        InternalBarcode.Result.Exhausted -> return WarehouseEngine.Result.Failed(StrLabel.noFreeCode)
      }
    }
    if (codes.isEmpty()) return WarehouseEngine.Result.Failed(StrLabel.allHaveCodes)
    return WarehouseEngine.bulkUpdate(d, codes.keys, today, now, newId) { p ->
      codes[p.id]?.let { code -> p.copy(barcodes = listOf(code)) } ?: p
    }
  }
}
