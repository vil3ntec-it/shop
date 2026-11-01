package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  نوارِ چسبانِ پایینِ جزئیات: گامِ اصلی (برند)، نخستین کارِ خطر (رد/لغو، قرمز) و «…» برای
 *  بقیهٔ گذارهای مجاز (برگهٔ کارها). همه از `OrdersModel.bar` — یعنی فقط `OrderStates`.
 */
@Composable
internal fun OrderActionBarContent(bar: OrdersModel.Bar, busy: String?, failure: String?, onStep: (String) -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  var more by remember { mutableStateOf(false) }
  val rest = bar.others + bar.danger.drop(1)
  Column(
    modifier
      .fillMaxWidth()
      .background(c.bg)
      .windowInsetsPadding(if (LocalV3SystemBottom.current != null) WindowInsets(0) else WindowInsets.navigationBars),
  ) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
      if (failure != null) {
        Text(failure, style = V3Type.footnote, color = PhoneV3Roles.errorText(c), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (rest.isNotEmpty()) V3IconButton(Icons.Outlined.MoreHoriz, StrOnline.moreActions, size = 48.dp) { more = true }
        bar.danger.firstOrNull()?.let { to ->
          V3Button(StrOnline.orderAction(to), { onStep(to) }, Modifier.weight(1f), kind = V3ButtonKind.DANGER, enabled = busy == null, haptic = true)
        }
        bar.primary?.let { to ->
          V3Button(StrOnline.orderAction(to), { onStep(to) }, Modifier.weight(1.4f), enabled = busy == null || busy == to, loading = busy == to, haptic = true)
        }
      }
    }
  }
  if (more) {
    V3Sheet(onDismiss = { more = false }, title = StrOnline.moreActions, detents = false) { closer ->
      V3Card(padding = PaddingValues(vertical = 4.dp)) {
        rest.forEachIndexed { i, to ->
          if (i > 0) V3Divider(start = 22.dp)
          V3ListRow(StrOnline.orderAction(to), tile = false, danger = OrdersModel.danger(to), onClick = { closer.close { onStep(to) } })
        }
      }
    }
  }
}
