package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.product.SimilarNames
import ir.vil3ntec.tohid.core.product.UnitSale

/**
 *  قاعده‌های پیش‌نویسِ کالا که افزودن و ویرایش (`WarehouseEngine.addProduct`/`editProduct`) هر دو می‌سنجند، به همان ترتیبِ پیام:
 *   • واحد (PRD-07): واحدِ پایهٔ کالای گردش‌دار عوض نمی‌شود ([unitLocked])؛ واحدِ فروشِ دوم ضریب می‌خواهد؛ ضریب مثبت، و ضریبِ
 *     **تازه** دقیق به هزارم (`UnitSale.exactMilli`) — ضریبِ کهنهٔ دست‌نخورده (۱/۲۴) ویرایشِ نام یا قیمت را قفل نمی‌کند.
 *   • قیمت: فروش، خرید و عمده (PRD-12؛ عمده فقط وقتی فرم آن را دارد، `ProductDraft.wholesalePrice`) عددِ نامنفی.
 *  منطقِ خالص؛ آزمون: `SaleUnitExactTest`، `WholesalePriceTest`، `ProductRulesTest`.
 */
internal object ProductDraftRules {

  /** نخستین ایراد یا `null`؛ [existing] = کالای ویرایشی (`null` = کالای تازه) */
  fun problem(d: ShopData, draft: WarehouseEngine.ProductDraft, existing: Product?): String? {
    unitLocked(d, existing, draft.unit)?.let { return it }
    //  ۲-۱۶ (R2 الف): ضریبِ ذخیره فقط وقتی «کهنهٔ دست‌نخورده» است که کالا همان وقت واحدِ فروشِ دوم داشت — ضریبِ خوابیده (گذاشته
    //  بی واحدِ فروش) با روشن شدنِ واحدِ فروش تازه است و باید دقیق باشد (همان `product-unit-rules.reasonOf` ِ سرور)
    val before = existing?.takeIf { it.saleUnit.isNotBlank() }?.unitFactor
    factorProblem(draft.unit, draft.saleUnit ?: existing?.saleUnit, draft.unitFactor ?: existing?.unitFactor, before)?.let { return it }
    if (draft.salePrice.isNaN() || draft.salePrice < 0) return StrGoods.salePriceBad
    if (draft.purchasePrice.isNaN() || draft.purchasePrice < 0) return StrGoods.buyPriceBad
    return draft.wholesalePrice?.takeIf { it.isNaN() || it < 0 }?.let { StrGoods.wholesaleBad }
  }

  /** واحدِ فروش و ضریب با هم معنی دارند؛ ضریب عددِ مثبت، و اگر عوض شده (≠ [before]) دقیقاً به هزارم */
  private fun factorProblem(unit: String, saleUnit: String?, factor: Double?, before: Double?): String? {
    val su = saleUnit?.trim().orEmpty()
    if (su.isEmpty()) return null
    if (su == unit.trim()) return StrGoods.saleUnitSame
    val f = factor ?: return StrGoods.factorNeeded
    if (f.isNaN() || f.isInfinite() || f <= 0) return StrGoods.factorPositive
    return if (f != before && UnitSale.exactMilli(f) == null) StrGoods.factorExact else null
  }

  /**
   *  PRD-07 — واحدِ پایه را فقط کالای بی گردش عوض می‌کند: ورود، فروش، خرید و حرکت به همان واحد نوشته شده‌اند و با واحدِ تازه
   *  بی‌صدا دوباره خوانده می‌شدند (۴۸ کیلو ⇒ «۴۸ گرم»، قیمتِ هر کیلو ⇒ قیمتِ هر گرم). همان واحد با فاصله یا حرفِ عربی و
   *  واحدِ خالیِ کهنه که تازه نوشته می‌شود همان رفتارِ پیشین‌اند. ⛔ ۲-۱۶ (R2 الف): خالی کردنِ واحدِ کالای گردش‌دار هم تغییرِ واحد
   *  است (گامِ اولِ «خالی، سپس واحدِ تازه»؛ همان `product-unit-rules.reasonOf` ِ سرور).
   */
  fun unitLocked(d: ShopData, existing: Product?, unit: String): String? {
    val was = existing?.unit?.trim().orEmpty()
    if (existing == null || was.isEmpty()) return null
    if (unit.isNotBlank() && SimilarNames.exactKey(unit) == SimilarNames.exactKey(was)) return null
    val id = existing.id
    val moved = d.warehouseEntries.any { it.productId == id } || d.saleItems.any { it.productId == id } ||
      d.stockMovements.any { it.productId == id } || d.purchases.any { it.productId == id }
    return if (moved) StrGoods.unitHasHistory(was) else null
  }
}
