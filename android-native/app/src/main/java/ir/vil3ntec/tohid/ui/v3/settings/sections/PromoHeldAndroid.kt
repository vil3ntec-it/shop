package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.promo.PromoHeld
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.PromoLive

/*
 *  PRM-02 — مرزِ پلتفرمِ «کدهای تخفیفِ نگه‌داشته»: درِ سرورِ کاربرِ واردشده، اجازهٔ لغوِ فاکتور و تازه‌سازیِ حالِ
 *  تخفیف‌ها پس از آزاد کردن — تا `PromoHeldScreen`/`PromoHeldState` بی وابستگیِ پلتفرم بمانند.
 */

/** درِ سرور فقط برای دارندهٔ `sales.cancel` ِ واردشده؛ وگرنه null (صفحه «فقط صاحب یا مدیر») */
@Composable
internal fun promoHeldSource(): PromoHeldSource? {
  val context = LocalContext.current
  val ok = Backend.isReady(context) && ShopRole.canCancelSale(context)
  return remember(context, ok) {
    if (!ok) null else {
      val repo = PromoLive.repository(context)
      object : PromoHeldSource {
        override suspend fun held() = repo.held()
        override suspend fun release(saleId: String) = repo.releaseHeld(saleId)
        override suspend fun changed() { PromoLive.refresh(context) }
      }
    }
  }
}

/** ۴-۲۱: بیننده صاحبِ دکان است؟ (آزاد کردنِ فاکتورِ نارسیده فقط برای او، `PromoHeld.release`) */
@Composable
internal fun promoHeldOwner(): Boolean {
  val context = LocalContext.current
  return remember(context) { ShopRole.current(context) == ShopRole.OWNER }
}

/** ۴-۲۱: ساعتِ دستگاه برای «۷ روز پس از گرفتنِ کد» (فقط مرزِ صفحه؛ سرور همان درِ `sales.cancel`) */
internal fun promoHeldNow(): Long = System.currentTimeMillis()

/** ردیفِ «کدهای تخفیفِ نگه‌داشته» در تنظیماتِ فروش: فقط با شمارشِ روشن (آخرین حالِ تخفیف‌ها) و اجازهٔ لغوِ فاکتور */
@Composable
internal fun promoHeldRow(): Boolean {
  val context = LocalContext.current
  return PromoHeld.shown(PromoLive.snap.value.redeem, remember(context) { ShopRole.canCancelSale(context) })
}
