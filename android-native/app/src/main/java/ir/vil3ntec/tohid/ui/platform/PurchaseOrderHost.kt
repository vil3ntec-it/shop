package ir.vil3ntec.tohid.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.PurchaseOrderRepository

/**
 *  مرزِ سکوی صفحهٔ «سفارش‌های خرید» (PUR-02/04، §۴۰.۳ و قاعدهٔ «کدِ تازه برای آیفون»): `Context` و ساعتِ
 *  دستگاه فقط در همین پوشهٔ `platform/`؛ صفحه (`ui/screens/PurchaseOrders.kt`) و منطق
 *  (`data/PurchaseOrderBook.kt`، `core/purchase`) فقط این رابط را می‌بینند. برنامهٔ آیفون پیاده‌سازیِ خودش را
 *  می‌دهد.
 */
interface PurchaseOrderHost {
  val repo: PurchaseOrderRepository

  /** زمانِ ثبتِ ردیف‌های دفتر (میلی‌ثانیه) */
  fun now(): Long
}

/** پیاده‌سازیِ اندروید و کامپیوتر (همان `Context` ِ صفحه) */
@Composable
fun rememberPurchaseOrderHost(): PurchaseOrderHost {
  val context = LocalContext.current
  return remember(context) {
    object : PurchaseOrderHost {
      override val repo = PurchaseOrderRepository(Backend.api(context))
      override fun now(): Long = System.currentTimeMillis()
    }
  }
}
