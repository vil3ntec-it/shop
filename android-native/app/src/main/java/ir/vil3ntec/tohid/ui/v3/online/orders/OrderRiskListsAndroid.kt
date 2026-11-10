package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/*
 *  پروندهٔ سکو (`*Android.kt`، قاعدهٔ «وابستگیِ پلتفرم فقط در *Android.kt»): مخزنِ سفارش‌ها `Context` می‌خواهد؛
 *  کشیدنِ صفحه بی‌پلتفرم در `RiskListsContent` (`OrderRiskListsV3.kt`) است.
 */

/** RSK-04 — مسیرِ `orders/risk-lists` (`OnlineRoutes`): فقط مخزن از `Context`؛ بقیه [RiskListsContent]. */
@Composable
internal fun RouteScope.RiskListsRoute() {
  val context = LocalContext.current
  val repo = remember(context) { OrdersHub.repo(context) }
  RiskListsContent(repo)
}
