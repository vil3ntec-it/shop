package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.plain

/**
 *  کالای تازه با «موجودی در انبار» از همان فرم (خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴: «یک محصولِ تازه آوردم، دو کارتن آوردم؛ از
 *  همین‌جا که وارد می‌کنم برای انبار هم ثبت شود»).
 *
 *  ⛔ راهِ موازی نیست: هر ردیف دقیقاً همان دو گامِ امروز است — [WarehouseEngine.addProduct] (ثبتِ کالا) و، فقط اگر
 *  موجودی نوشته شده، [WarehouseEngine.addEntry] روی خروجیِ همان (همان «ورودِ کالا» ِ انبار: ردیفِ `warehouseEntries` با
 *  مقدار، واحد، فی، تاریخِ تولید و انقضا + حرکتِ `purchase_in`). مثلِ `addProducts`/`addEntries` **یا همه یا هیچ**: ورودی
 *  دفترِ فعلی، خروجی یک دفترِ تازه که صفحه با **یک** `ShopStore.save` می‌نویسد ⇒ یک نوشتنِ دیسک و همان opهای همیشگیِ
 *  «کالای تازه» و «ورودِ کالا» (همگام‌سازی چیزِ تازه‌ای نمی‌بیند). ردیفِ خراب ⇒ «ردیفِ n: دلیل» و هیچ چیز نوشته نمی‌شود.
 *
 *  دوباره زدن: همان پیش‌نویس روی دفتری که این کالا را دارد با «نامِ تکراری» ِ `addProduct` رد می‌شود ⇒ کالا و موجودیِ
 *  دوم ساخته نمی‌شوند (صفحه هم پس از نخستین ثبت دیگر نمی‌نویسد).
 *
 *  واحد: فرمِ کالای تازه واحدِ خرید (`purchaseUnits`) و واحدِ فروشِ دوم (`unitFactor`) ندارد ⇒ مقدار به **همان واحدِ
 *  کالا** است (کالای «کارتن»ی ⇐ ۲ یعنی دو کارتن؛ کالای «عدد»ی ⇐ شمارِ دانه‌ها). فیِ ورود همان قیمتِ خرید است.
 *  آزمون: `OpeningStockTest`.
 */
object OpeningStock {

  /** یادداشتِ ردیفِ انبار و حرکتِ همان (دیده می‌شود در «گردشِ کالا») */
  const val NOTE = "موجودیِ آغاز، از فرمِ کالای تازه"  // داده: یادداشتِ ذخیره‌شدهٔ ورودِ انبار

  /** موجودیِ آغاز: مقدار به واحدِ کالا (> ۰)؛ تاریخ‌ها `yyyy-MM-dd` ِ دفتر یا خالی */
  data class Stock(val qty: Double, val productionDate: String = "", val expiryDate: String = "")

  /** یک کالای فرم: پیش‌نویسِ موتور و، اگر نوشته شده، موجودیِ آغاز */
  data class Row(val product: WarehouseEngine.ProductDraft, val stock: Stock? = null)

  /** @return Ok با شناسهٔ آخرین کالای ثبت‌شده؛ Failed با دلیلِ نخستین ردیفِ خراب و دفترِ دست‌نخورده */
  fun save(d: ShopData, rows: List<Row>, today: String, now: Long, newId: () -> String): WarehouseEngine.Result {
    if (rows.isEmpty()) return WarehouseEngine.Result.Failed(StrProducts.needOneRow)
    var data = d
    var lastId = ""
    rows.forEachIndexed { index, row ->
      val no = plain(index + 1)
      val made = when (val r = WarehouseEngine.addProduct(data, row.product, now, newId)) {
        is WarehouseEngine.Result.Failed -> return WarehouseEngine.Result.Failed(StrGoods.rowWhy(no, r.message))
        is WarehouseEngine.Result.Ok -> r
      }
      data = made.data
      lastId = made.id
      val stock = row.stock ?: return@forEachIndexed
      when (val e = WarehouseEngine.addEntry(data, entry(made.id, row.product, stock), today, now, newId)) {
        is WarehouseEngine.Result.Failed -> return WarehouseEngine.Result.Failed(StrGoods.rowWhy(no, e.message))
        is WarehouseEngine.Result.Ok -> data = e.data
      }
    }
    return WarehouseEngine.Result.Ok(data, lastId)
  }

  /** همان پیش‌نویسِ «ورودِ کالا» که فرمِ انبار می‌سازد — مقدار به واحدِ کالا، فی = قیمتِ خرید، تاریخ = امروز (موتور) */
  fun entry(productId: String, product: WarehouseEngine.ProductDraft, stock: Stock): WarehouseEngine.EntryDraft =
    WarehouseEngine.EntryDraft(
      productId = productId,
      units = stock.qty,
      unit = product.unit,
      price = product.purchasePrice,
      notes = NOTE,
      expiryDate = stock.expiryDate,
      productionDate = stock.productionDate,
    )
}
