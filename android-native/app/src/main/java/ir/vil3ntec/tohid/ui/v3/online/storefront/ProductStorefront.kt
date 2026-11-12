package ir.vil3ntec.tohid.ui.v3.online.storefront

import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto

/** INV-01@32 — وضعِ ویترینِ یک کالا روی صفحهٔ همان کالا */
internal sealed interface ProductStorefrontState {
  /** هنوز از سرور خوانده نشده */
  data object Loading : ProductStorefrontState
  /** روی دستگاه تعریف شده، در دفترِ سرور هنوز نیست ⇒ انتشار خاموش تا همگام شدن */
  data object NotSynced : ProductStorefrontState
  data class Ready(val p: StorefrontProductDto) : ProductStorefrontState
  data class Failed(val message: String) : ProductStorefrontState
}

/**
 *  INV-01@32 «تعریف و انتشار از یک صفحه» — منطقِ خالصِ بخشِ «ویترینِ اینترنتی» ِ صفحهٔ کالا (`products/{id}`).
 *
 *  ⛔ کالای تازه‌تعریف‌شده تا Sync v1 آن را به دفترِ سرور نرساند در ویترین گذاشته نمی‌شود: سرور برای شناسهٔ ناشناخته ۴۰۴
 *  `product_not_found` می‌دهد (`GET`/`PUT /api/shop/storefront/products/{id}`). برنامه آن ۴۰۴ را «هنوز به سرور نرسیده» می‌خواند،
 *  کلید را خاموش نگه می‌دارد و پس از هر همگام‌سازیِ موفق دوباره می‌پرسد — منطقِ همگام‌سازی دست نمی‌خورد و هیچ درخواستِ انتشاری
 *  پیش از رسیدنِ کالا فرستاده نمی‌شود.
 */
internal object ProductStorefront {
  const val NOT_FOUND = "product_not_found"

  /** پاسخِ سرور یعنی «این کالا هنوز در دفترِ سرور نیست» (نه خطای شبکه یا دسترسی) */
  fun notSynced(f: ApiFailure): Boolean = f is ApiFailure.NotFound && f.code == NOT_FOUND

  fun of(r: ApiResult<StorefrontProductDto>): ProductStorefrontState = when (r) {
    is ApiResult.Success -> ProductStorefrontState.Ready(r.value)
    is ApiResult.Failure -> if (notSynced(r.error)) ProductStorefrontState.NotSynced else ProductStorefrontState.Failed(r.error.userMessage)
  }

  /** فقط تا وقتی کالا خوانده نشده (هنوز نرسیده یا خطا) دوباره پرسیده می‌شود؛ خوانده‌شده با هر همگام‌سازی دوباره خوانده نمی‌شود */
  fun shouldLoad(s: ProductStorefrontState): Boolean = s !is ProductStorefrontState.Ready

  /** کلید فقط روی کالای خوانده‌شده و وقتی درخواستی در راه نیست */
  fun canToggle(s: ProductStorefrontState, busy: Boolean): Boolean = s is ProductStorefrontState.Ready && !busy

  /**
   *  پس از پاسخِ انتشار/برداشتن: موفق ⇒ همان ردیف با وضعِ تازه؛ ۴۰۴ ِ همان «نرسیده» (مثلاً کالا در دستگاهِ دیگری حذف شد) ⇒
   *  NotSynced؛ هر خطای دیگر ⇒ همان وضعِ پیشین و پیامِ خطا (هرگز «ثبت شد» ِ ساختگی).
   */
  fun afterSave(p: StorefrontProductDto, to: Boolean, failure: ApiFailure?): Pair<ProductStorefrontState, String?> = when {
    failure == null -> ProductStorefrontState.Ready(p.copy(published = to)) to null
    notSynced(failure) -> ProductStorefrontState.NotSynced to null
    else -> ProductStorefrontState.Ready(p) to failure.userMessage
  }

  /** زیرِ کلید: غیرفعال در دفتر، یا قیمتی که مشتری می‌بیند */
  fun subtitle(p: StorefrontProductDto): String = when {
    !p.active -> StrOnline.sfInactive
    p.onlinePriceMinor != null -> StrOnline.onlinePrice(OnlineFmt.afn(p.onlinePriceMinor))
    p.priceMinor == null -> StrOnline.noPrice
    else -> StrOnline.ledgerPrice(StorefrontModel.ledgerPrice(p))
  }
}
