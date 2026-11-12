package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.TrustRepository
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/*
 *  پروندهٔ سکو (`*Android.kt`): مخزنِ «شکایت و گزارش» `Context` می‌خواهد؛ کشیدنِ صفحه بی‌پلتفرم در `TrustCasesV3.kt` است.
 */

/** ABUSE-01 — مسیرِ `storefront/trust` (`OnlineRoutes`): مخزن، ساعت و نامِ کالا از دفترِ همین دکان؛ بقیه [TrustCasesContent]. */
@Composable
internal fun RouteScope.TrustCasesRoute() {
  val context = LocalContext.current
  val repo = remember(context) { TrustRepository(Backend.api(context)) }
  val products = data.products
  TrustCasesContent(
    repo,
    now = { System.currentTimeMillis() },
    productName = { id -> products.firstOrNull { it.id == id }?.name },
    say = { toasts.show(it) },
  )
}

/** با بارِ ویترین: آیا «شکایت و گزارش» برای این دکان روشن است؟ (مدخلِ «…» ِ ویترین، `storefrontExtras`) */
@Composable
internal fun TrustHubEffect() {
  val context = LocalContext.current
  LaunchedEffect(Unit) { if (Backend.isReady(context)) TrustHub.refresh(TrustRepository(Backend.api(context))) }
}
