package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ProductImport
import ir.vil3ntec.tohid.data.ShopData

/** حالِ صفحهٔ «ورود کالا از فایل» */
sealed interface ImportState {
  /** هنوز فایلی انتخاب نشده (یا انتخاب لغو شد) */
  data object Idle : ImportState
  data class Failed(val message: String) : ImportState
  data class Ready(val plan: ProductImport.Plan) : ImportState
}

/** حالتِ خالصِ `stock/import` (آزمون: `ImportModelTest`) — پیش‌نمایش فقط از `ProductImport.plan` */
object ImportModel {
  /** چند ردیفِ ردشده پیش از «و n ردیفِ دیگر» */
  const val SKIP_PREVIEW = 50

  /** متنِ فایل (null = خوانده نشد) ⇒ خطا یا پیش‌نمایش */
  fun read(d: ShopData, text: String?): ImportState {
    if (text == null) return ImportState.Failed(StrStock.readFailed)
    val plan = ProductImport.plan(d, text)
    return if (plan.missingName) ImportState.Failed(StrStock.noNameColumn) else ImportState.Ready(plan)
  }

  fun shownSkips(plan: ProductImport.Plan): List<ProductImport.Skip> = plan.skipped.take(SKIP_PREVIEW)

  fun hiddenSkips(plan: ProductImport.Plan): Int = (plan.skipped.size - SKIP_PREVIEW).coerceAtLeast(0)

  /** چند کالای آماده در پیش‌نمایش (همه ثبت می‌شوند؛ فقط فهرست کوتاه می‌ماند) */
  const val DRAFT_PREVIEW = 100

  fun shownDrafts(plan: ProductImport.Plan): List<ir.vil3ntec.tohid.data.WarehouseEngine.ProductDraft> = plan.drafts.take(DRAFT_PREVIEW)

  fun hiddenDrafts(plan: ProductImport.Plan): Int = (plan.drafts.size - DRAFT_PREVIEW).coerceAtLeast(0)
}
