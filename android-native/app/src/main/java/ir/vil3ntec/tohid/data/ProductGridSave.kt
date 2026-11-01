package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGrid
import ir.vil3ntec.tohid.core.product.DeskGrid
import ir.vil3ntec.tohid.core.product.DeskGrid.Col
import ir.vil3ntec.tohid.core.product.SimilarNames

/**
 *  PRD-22 — جدولِ ویرایشِ درجا ⇄ دفتر: ردیف‌های آغازین از کالاها، ایرادهای هر خانه با کالاهای بیرونِ
 *  جدول و بارکدهای دکان، و ثبت.
 *
 *  ⛔ ثبت یا همه یا هیچ: هر ردیفِ عوض‌شده با همان `WarehouseEngine.editProduct` (همان سنجش‌ها، سابقه و
 *  تاریخچهٔ قیمت با کاربر و دلیل)، روی دفترِ همان لحظه؛ نخستین رد ⇒ هیچ‌چیز نوشته نمی‌شود.
 *  ⛔ فقط خانه‌هایی که در جدول عوض شده‌اند نوشته می‌شوند؛ بقیهٔ فیلدهای همان کالا از دفترِ **همان لحظه**
 *  می‌آیند، تا تغییرِ دستگاهِ دیگر (همگام‌سازی) در خانه‌ای که کاربر دست نزده برنگردد.
 *  ⛔ بارکد فقط بارکدِ نخست است (همان ویرایشِ تک‌بارکدی): بارکدهای دیگرِ کالا سرِ جایشان می‌مانند.
 */
object ProductGridSave {

  sealed interface Result {
    data class Ok(val data: ShopData, val saved: Int) : Result
    data class Failed(val message: String, val productId: String?) : Result
  }

  /** ردیفِ جدول برای هر کالا، به همان ترتیب */
  fun rows(products: List<Product>): List<DeskGrid.Row> = products.map { p ->
    DeskGrid.Row(
      p.id,
      listOf(
        p.name, p.category, p.unit,
        ir.vil3ntec.tohid.qty(p.purchasePrice), ir.vil3ntec.tohid.qty(p.salePrice), ir.vil3ntec.tohid.qty(p.minStock),
        p.barcodes.firstOrNull().orEmpty(),
      ),
    )
  }

  /** ایرادِ هر خانه با نامِ کالاهای بیرونِ جدول و بارکدهای همهٔ دکان (بارکدِ بسته هم) */
  fun problems(d: ShopData, sheet: DeskGrid.Sheet): Map<DeskGrid.Cell, DeskGrid.Problem> {
    val inGrid = sheet.rows.mapTo(HashSet()) { it.id }
    val outside = d.products.asSequence().filter { it.id !in inGrid }.mapTo(HashSet()) { SimilarNames.exactKey(it.name) }
    val owners = ShopStore.codeOwners(d)
    return DeskGrid.problems(sheet, outside) { owners[it] }
  }

  /**
   *  ثبتِ ردیف‌های عوض‌شده روی [d]. ایرادِ جدول ⇒ [Result.Failed] بی دست زدن؛ ردِ موتور ⇒ نامِ کالا و
   *  دلیل، و باز هیچ‌چیز نوشته نمی‌شود. [by] کاربری که در تاریخچهٔ قیمت می‌نشیند.
   */
  fun apply(d: ShopData, sheet: DeskGrid.Sheet, today: String, now: Long, newId: () -> String, by: String?): Result {
    if (problems(d, sheet).isNotEmpty()) return Result.Failed(StrGrid.fixFirst, null)
    var cur = d
    var saved = 0
    for ((i, r) in sheet.rows.withIndex()) {
      if (r == sheet.original(r.id)) continue
      val p = cur.products.find { it.id == r.id } ?: return Result.Failed(StrGrid.rowFailed(r[Col.NAME], StrGrid.gone), r.id)
      fun text(c: Col, old: String) = if (sheet.isChanged(i, c)) r[c] else old
      fun price(c: Col, old: Double) = if (sheet.isChanged(i, c)) (DeskGrid.money(r[c]) ?: 0L) / 100.0 else old
      val draft = WarehouseEngine.ProductDraft(
        name = text(Col.NAME, p.name),
        category = text(Col.CATEGORY, p.category).trim(),
        unit = text(Col.UNIT, p.unit).trim(),
        purchasePrice = price(Col.BUY, p.purchasePrice),
        salePrice = price(Col.SELL, p.salePrice),
        minStock = if (sheet.isChanged(i, Col.MIN)) (DeskGrid.quantity(r[Col.MIN]) ?: 0L) / 1000.0 else p.minStock,
        barcode = text(Col.BARCODE, p.barcodes.firstOrNull().orEmpty()),
        changedBy = by?.trim()?.ifEmpty { null },
        reason = REASON,
      )
      when (val res = WarehouseEngine.editProduct(cur, p.id, draft, today, now, newId)) {
        is WarehouseEngine.Result.Failed -> return Result.Failed(StrGrid.rowFailed(p.name, res.message), p.id)
        is WarehouseEngine.Result.Ok -> { cur = res.data; saved++ }
      }
    }
    return Result.Ok(cur, saved)
  }

  /** دلیلِ تغییرِ قیمت در تاریخچه (PRD-14) — داده است، نه متنِ صفحه */
  const val REASON = "جدولِ ویرایشِ کالا"
}
