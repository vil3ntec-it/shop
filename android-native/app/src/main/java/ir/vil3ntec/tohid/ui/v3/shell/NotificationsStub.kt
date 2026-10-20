package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.screens.rememberAlerts
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/**
 *  صفحهٔ موقتِ «هشدارها» — همان فهرستِ زنگِ سربرگِ قدیم (`rememberAlerts`: اشتراک،
 *  پشتیبان، کالا، قرض، خبرهای دکان) به‌صورتِ صفحه، و هر ردیف مستقیم به صفحهٔ
 *  خودش. گروهِ پوسته (G10) مرکزِ اعلان‌های کامل (`notifications?tab=alerts|news`)
 *  را جایش می‌سازد.
 */
@Composable
internal fun RouteScope.NotificationsStub() {
  val alerts = rememberAlerts(data)
  val stagger = rememberV3Stagger(alerts.size)
  V3Scaffold(title = StrKit.notifications, onBack = if (nav.canBack) ({ back() }) else null) { pad ->
    if (alerts.isEmpty()) {
      Box(Modifier.fillMaxSize().padding(pad)) {
        V3EmptyState(Icons.Outlined.NotificationsNone, StrKit.notificationsEmpty, body = StrKit.notificationsEmptyBody)
      }
    } else {
      LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = pad.calculateBottomPadding())) {
        item {
          V3Card(padding = PaddingValues(vertical = 4.dp)) {
            alerts.forEachIndexed { i, a ->
              if (i > 0) V3Divider(start = 16.dp)
              V3ListRow(
                title = a.title,
                subtitle = a.detail,
                value = a.value,
                modifier = Modifier.v3Stagger(stagger, a.title + a.target, i),
                onClick = { nav.open(a.target) },
              )
            }
          }
        }
      }
    }
  }
}
