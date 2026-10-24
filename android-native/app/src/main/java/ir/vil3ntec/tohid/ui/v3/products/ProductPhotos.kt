package ir.vil3ntec.tohid.ui.v3.products

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableIntStateOf
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.PhotoSync
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import kotlinx.coroutines.CoroutineScope

/**
 *  ثبتِ عکسِ کالا — همان کاری که «محصولات» و صفحهٔ کالا هر کدام جدا می‌کردند،
 *  حالا یک‌جا: عکس در `PhotoStore`، نشانهٔ `photo` روی خودِ کالا (همان فیلدی که
 *  وب می‌نویسد)، خبر به همگام‌سازیِ عکس، و بالا رفتنِ `version` تا عکسِ کوچکِ
 *  فهرست و صفحهٔ کالا تازه شود. (بانکِ تصاویر، #291، هم همین‌جا وصل می‌شود.)
 */
internal object ProductPhotos {
  /** با هر عکسِ تازهٔ همین دستگاه بالا می‌رود (`PhotoSync.version` برای عکسِ دستگاه‌های دیگر است) */
  val version = mutableIntStateOf(0)

  fun save(context: Context, store: ShopStore, scope: CoroutineScope, toasts: V3Toasts, productId: String, bitmap: Bitmap) {
    PhotoStore.saveBitmap(context, productId, bitmap)
      .onSuccess {
        val d = store.data.value
        persist(scope, store, d.copy(products = d.products.map { if (it.id == productId) it.copy(photo = true) else it }))
        PhotoSync.changed(context, productId)
        version.intValue++
        toasts.show(StrProducts.photoSaved)
      }
      .onFailure { toasts.show(StrProducts.photoFailed) }
  }
}
