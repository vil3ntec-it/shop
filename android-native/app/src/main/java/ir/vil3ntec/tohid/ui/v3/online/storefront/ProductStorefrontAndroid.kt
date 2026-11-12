package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.v1.rememberSyncV1Status
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import kotlinx.coroutines.launch

/*
 *  INV-01@32 — مرزِ پلتفرمِ بخشِ «ویترینِ اینترنتی» ِ صفحهٔ کالا: مخزن از `Backend.api(context)`، لرزش، حالِ همگام‌سازی و
 *  `AutoSync`. منطقِ خالص (`ProductStorefront`) در `ProductStorefront.kt`.
 */

/**
 *  بخشِ «ویترینِ اینترنتی» ِ صفحهٔ کالا — فقط وقتی ویترینِ دکان روشن است و کاربر اجازهٔ مدیریتش را دارد (`StorefrontGate.visible`).
 *  @param recheck زمانِ آخرین همگام‌سازیِ موفق؛ عوض شد و کالا هنوز «نرسیده» بود ⇒ دوباره پرسیده می‌شود
 */
@Composable
internal fun ProductStorefrontSection(
  productId: String,
  recheck: Long,
  onSyncNow: () -> Unit,
  onDetails: () -> Unit,
  toast: (String) -> Unit,
) {
  if (!StorefrontGate.visible) return
  val context = LocalContext.current
  val repo = remember(context) { StorefrontRepository(Backend.api(context)) }
  val scope = rememberCoroutineScope()
  var state by remember(productId) { mutableStateOf<ProductStorefrontState>(ProductStorefrontState.Loading) }
  var busy by remember(productId) { mutableStateOf(false) }
  var failure by remember(productId) { mutableStateOf<String?>(null) }
  var asks by remember(productId) { mutableIntStateOf(0) }
  LaunchedEffect(productId, recheck, asks) {
    if (!ProductStorefront.shouldLoad(state)) return@LaunchedEffect
    val next = ProductStorefront.of(repo.product(productId))
    (next as? ProductStorefrontState.Ready)?.let { StorefrontHub.put(it.p) }
    state = next
  }

  Column {
    V3Section(Str.storefront, footer = failure) {
      when (val s = state) {
        ProductStorefrontState.Loading -> V3ListRow(StrProducts.storefrontChecking, icon = Icons.Outlined.Storefront, chevron = false)
        ProductStorefrontState.NotSynced -> V3ToggleRow(
          StrOnline.sfInStore, checked = false, onCheckedChange = {}, subtitle = StrProducts.storefrontNotSynced,
          icon = Icons.Outlined.Storefront, enabled = false,
        )
        is ProductStorefrontState.Failed -> V3ListRow(
          s.message, icon = Icons.Outlined.CloudOff, value = StrKit.retry, chevron = false,
          onClick = { state = ProductStorefrontState.Loading; asks++ },
        )
        is ProductStorefrontState.Ready -> {
          V3ToggleRow(
            StrOnline.sfInStore, checked = s.p.published, subtitle = ProductStorefront.subtitle(s.p),
            icon = Icons.Outlined.Storefront, enabled = ProductStorefront.canToggle(s, busy),
            onCheckedChange = { to ->
              busy = true
              failure = null
              scope.launch {
                var err: ApiFailure? = null
                repo.setProduct(s.p.id, to, s.p.onlinePriceMinor, s.p.maxQtyMilli).onFailure { err = it }
                val (next, message) = ProductStorefront.afterSave(s.p, to, err)
                (next as? ProductStorefrontState.Ready)?.let { StorefrontHub.put(it.p) }
                state = next
                failure = message
                busy = false
                if (err == null) {
                  Haptics.success(context)
                  toast(if (to) StrProducts.storefrontPublished else StrProducts.storefrontRemoved)
                }
              }
            },
          )
          V3Divider(start = 56.dp)
          V3ListRow(StrProducts.storefrontDetails, onClick = onDetails)
        }
      }
    }
    if (state == ProductStorefrontState.NotSynced) {
      V3Message(
        V3Tone.WARNING, StrProducts.storefrontNotSynced, Modifier.padding(top = 8.dp),
        //  همگام‌سازی همین حالا + یک پرسشِ بی‌درنگ؛ رسیدنِ واقعی را `recheck` (پایانِ همگام‌سازیِ موفق) می‌آورد
        body = StrProducts.storefrontNotSyncedBody, action = StrAccount.syncNow, onAction = { onSyncNow(); asks++ },
      )
    }
  }
}

/**
 *  سیم‌کشیِ بخش در صفحهٔ کالا (`ProductsRoutes`، همان راهِ `BankPhotoSlot`): پایانِ همگام‌سازیِ موفقِ Sync v1 (`lastOkAt`) ⇒
 *  کالای «نرسیده» دوباره پرسیده می‌شود؛ «همین حالا همگام کن» همان `AutoSync.now`؛ «جزئیات» همان برگهٔ `storefront/products/{id}`.
 */
@Composable
internal fun RouteScope.ProductStorefrontSlot(productId: String) {
  val context = LocalContext.current
  ProductStorefrontSection(
    productId = productId,
    recheck = rememberSyncV1Status(context)?.lastOkAt ?: 0L,
    onSyncNow = { AutoSync.now(context, store) },
    onDetails = { nav.open(OnlinePaths.storefrontProduct(productId)) },
    toast = { toasts.show(it) },
  )
}
