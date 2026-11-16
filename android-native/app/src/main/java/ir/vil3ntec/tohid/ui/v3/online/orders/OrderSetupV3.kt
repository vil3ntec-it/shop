package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderFlow
import ir.vil3ntec.tohid.core.orders.OrderFlow
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3DataRow
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3RowLook
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import androidx.compose.foundation.layout.padding
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3RowAction
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type

/** برچسبِ آزمون: تنها ردیفِ وضعیتِ زیرِ برگه‌ها */
const val ORDER_SETUP_ROW = "order-setup-row"

/**
 *  تنها ردیفِ وضعیتِ صف (`OrderSetupModel`): «۲ کار برای راه‌اندازیِ سفارشِ آنلاین ←» ⇒ برگهٔ فهرست، یا «سفارشِ آنلاین برای
 *  این دکان خاموش است» با «پشتیبانی». آماده ⇒ هیچ (هیچ ردیفی، هیچ جایی).
 */
@Composable
internal fun OrderSetupRow(status: OrderSetupModel.Status, onOpen: () -> Unit, onSupport: () -> Unit) {
  when (status) {
    OrderSetupModel.Status.Ready -> Unit
    is OrderSetupModel.Status.Todo -> V3Section(null, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
      V3DataRow(
        StrOrderFlow.setupCount(status.tasks.size), Modifier.testTag(ORDER_SETUP_ROW),
        icon = Icons.Outlined.Checklist, hue = V3Hue.AMBER, onClick = onOpen,
      )
    }
    is OrderSetupModel.Status.Off -> V3Section(null, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
      V3DataRow(
        StrOrderFlow.setupOff, Modifier.testTag(ORDER_SETUP_ROW),
        icon = Icons.Outlined.PowerSettingsNew, hue = V3Hue.AMBER,
        action = V3RowAction(StrOrderFlow.fixSupport, null, onClick = onSupport), onClick = onSupport,
      )
    }
  }
}

/** برگهٔ فهرستِ کارها: هر کار یک ردیف با **یک** دکمه (همان صفحهٔ برنامه، `openFix`) */
@Composable
internal fun OrderSetupSheet(tasks: List<OrderSetupModel.Task>, onFix: (OrderFlow.Fix) -> Unit, onDismiss: () -> Unit) {
  V3Sheet(onDismiss = onDismiss, title = StrOrderFlow.setupTitle, detents = false) { closer ->
    Text(StrOrderFlow.setupHint, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(4.dp))
    V3Section(null) {
      tasks.forEachIndexed { i, t ->
        if (i > 0) V3Divider(start = 64.dp)
        //  نامِ کار یک خطِ کامل، «چه باید کرد» تا سه خط، و **یک** دکمه زیرش (قرصِ کنارِ نام نامِ کار را می‌برید)
        V3ListRow(
          StrOrderFlow.blocker(t.code), subtitle = StrOrderFlow.blockerHow(t.code), subtitleLines = 3,
          icon = taskIcon(t.fix), hue = V3Hue.AMBER, chevron = false, look = V3RowLook.LIST,
          below = {
            V3Button(
              taskLabel(t), { closer.close { onFix(t.fix) } }, Modifier.padding(top = 10.dp),
              kind = V3ButtonKind.SECONDARY, height = 38.dp,
            )
          },
        )
      }
    }
    Spacer(Modifier.height(12.dp))
  }
}

internal fun taskLabel(t: OrderSetupModel.Task): String = when (t.code) {
  "storefront_missing" -> StrOrderFlow.doStorefront
  "storefront_hidden" -> StrOrderFlow.doStorefrontOn
  "no_products" -> StrOrderFlow.doProducts
  else -> fixLabel(t.fix) ?: StrOrderFlow.fixSupport
}

private fun taskIcon(fix: OrderFlow.Fix) = when (fix) {
  OrderFlow.Fix.PRODUCTS -> Icons.Outlined.Inventory2
  OrderFlow.Fix.SUBSCRIBE -> Icons.Outlined.WorkspacePremium
  else -> Icons.Outlined.Storefront
}
