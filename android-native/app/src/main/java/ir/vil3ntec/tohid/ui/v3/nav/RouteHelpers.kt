package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ir.vil3ntec.tohid.data.WarehouseEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 *  مسیرِ ثبت‌شده‌ای که صفحهٔ خودش را هنوز ندارد (گروهِ صاحبش می‌سازد): پیوند کار
 *  می‌کند و روی نزدیک‌ترین صفحهٔ امروز می‌نشیند (`V3Nav.redirect`).
 */
@Composable
fun RouteScope.LandOn(path: String) {
  LaunchedEffect(args.path) { nav.redirect(path) }
}

/**
 *  چسبِ موقتِ پنجره‌های امروزِ انبار/کالا که مسیر شده‌اند — همان کارِ `apply`ِ
 *  `WarehouseScreen`: نتیجهٔ موتور یا نوشته و «ثبت شد»، یا دلیلِ رد. گروه‌های
 *  کالا و انبار با فرم‌های خودشان جایش را می‌گیرند (ORDER.md، فاز ۳ پاک می‌شود).
 */
internal fun RouteScope.commitWarehouse(
  scope: CoroutineScope,
  result: WarehouseEngine.Result,
  done: String,
  after: (WarehouseEngine.Result.Ok) -> Unit = {},
) {
  when (result) {
    is WarehouseEngine.Result.Failed -> toasts.show(result.message)
    is WarehouseEngine.Result.Ok -> {
      scope.launch { store.save(result.data) }
      toasts.show(done)
      after(result)
    }
  }
}
