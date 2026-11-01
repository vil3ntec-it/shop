package ir.vil3ntec.tohid.ui.v3.stock

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 *  ثبتِ نتیجهٔ موتورِ انبار — یک جا برای همهٔ صفحه‌های این گروه (جای چسبِ موقتِ
 *  `commitWarehouse` ِ پایه): نتیجهٔ درست ⇐ دفتر ذخیره، لرزشِ «ثبت شد» و پیامِ کوتاه؛
 *  رد ⇐ همان پیامِ موتور برمی‌گردد تا صفحه زیرِ کادرِ خودش نشانش دهد.
 *
 *  موتورها دست نمی‌خورند: تاریخِ امروز، زمان و شناسه همان‌هایی‌اند که صفحهٔ پیشین می‌داد.
 */
class StockWriter internal constructor(
  private val store: ShopStore,
  private val toasts: V3Toasts,
  private val scope: CoroutineScope,
  private val context: Context,
) {
  val today: String get() = todayIso()
  val now: Long get() = System.currentTimeMillis()
  val newId: () -> String = { Ulid.next() }

  /** @return null اگر ثبت شد؛ وگرنه دلیلِ ردِ موتور */
  fun commit(result: WarehouseEngine.Result, done: String): String? = when (result) {
    is WarehouseEngine.Result.Failed -> result.message
    is WarehouseEngine.Result.Ok -> {
      //  برگه‌ها پس از ثبت با `back()` می‌روند و حوزهٔ همین صفحه بسته می‌شود؛ نوشتنِ دفتر
      //  همان لحظه آغاز و بی‌لغو تمام می‌شود تا هیچ ثبتی نیمه‌کاره نماند
      scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { store.save(result.data) } }
      Haptics.success(context)
      toasts.show(done, V3Tone.SUCCESS)
      null
    }
  }

  /** ردی که جای خاصی در صفحه ندارد ⇒ پیامِ کوتاهِ خطا */
  fun fail(message: String) = toasts.show(message, V3Tone.ERROR)

  fun info(message: String) = toasts.show(message, V3Tone.SUCCESS)
}

@Composable
internal fun RouteScope.rememberStockWriter(): StockWriter {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  return remember(store, toasts) { StockWriter(store, toasts, scope, context) }
}
