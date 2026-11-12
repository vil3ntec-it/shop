package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.core.i18n.StrTrust
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.ui.screens.CampaignsPane
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import ir.vil3ntec.tohid.ui.screens.StorefrontMediaPanel
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths

/**
 *  «…» ِ ویترین: «پیوند و QR» ِ کارزار (برگهٔ ویترینِ پیشین، `CampaignsPane`) و «عکس و ویدیو» (M3، پرچمِ جدای
 *  `shop.storefront.media`، `StorefrontMediaPanel`) — همان اجزای `ui/screens`، هر کدام صفحهٔ خودش زیرِ ویترین؛ و
 *  «شکایت و گزارشِ خریداران» (ABUSE-01، پرچمِ `market.trust`، `TrustCasesV3.kt`).
 */
internal fun RouteScope.storefrontExtras(cfg: StorefrontConfigDto?): List<V3MenuItem> = buildList {
  add(V3MenuItem(Icons.Outlined.QrCode2, StrReach.campaigns) { nav.open(OnlinePaths.STOREFRONT_CAMPAIGNS) })
  if (cfg?.settings != null && cfg.media) add(V3MenuItem(Icons.Outlined.PermMedia, StrReach.media) { nav.open(OnlinePaths.STOREFRONT_MEDIA) })
  //  ABUSE-01: فقط با پرچمِ `market.trust` ِ همین دکان (`TrustHub`، بارِ ویترین)
  if (TrustHub.enabled) add(V3MenuItem(Icons.Outlined.Flag, StrTrust.title) { nav.open(OnlinePaths.STOREFRONT_TRUST) })
}

@Composable
internal fun RouteScope.StorefrontCampaignsRoute() {
  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
    CampaignsPane(StorefrontGate.config.value?.settings?.title.orEmpty(), snackbar)
  }
}

@Composable
internal fun RouteScope.StorefrontMediaRoute() {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
    StorefrontMediaPanel(snackbar)
  }
}
