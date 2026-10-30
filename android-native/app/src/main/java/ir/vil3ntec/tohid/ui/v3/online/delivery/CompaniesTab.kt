package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.ExternalCompany
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.DraftTag
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersModel
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem

/**
 *  برگهٔ «شرکت‌های بیرونی» (DLV-19): هر شرکت از خودِ سپردن‌ها — کرایهٔ آخرین سپردن، تحویل‌شده از
 *  همه، آخرین بار، شمارِ زنده — و «تطبیقِ صورت‌حساب» فقط برای صاحب و مدیر (`delivery.settle`).
 */
@Composable
internal fun CompaniesTab(d: DeskData, cfg: DeskConfig, onInvoice: (ExternalCompany) -> Unit, pad: PaddingValues, header: @Composable () -> Unit) {
  val c = PhoneV3.colors
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
    item(key = "head") { header() }
    if (d.companies.isEmpty()) item(key = "empty") {
      V3EmptyState(Icons.Outlined.LocalShipping, StrOnline.dlNoCompanies, body = StrOnline.dlNoCompaniesBody)
    }
    items(d.companies, key = { it.name }) { co ->
      V3Card(Modifier.padding(top = 10.dp).v3AnimateItem(this), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(co.name, style = V3Type.headline, color = c.ink, modifier = Modifier.weight(1f))
          if (co.live > 0) DraftTag(StrOnline.dlLiveCount(co.live))
        }
        Spacer(Modifier.height(6.dp))
        Text(
          StrOnline.dlCompanyLine(OnlineFmt.afn(co.lastFareMinor), co.delivered, co.assignments, if (co.lastUsedAt > 0) OrdersModel.stamp(co.lastUsedAt) else "—"),
          style = V3Type.footnote, color = c.inkMuted,
        )
        if (cfg.perms.settle) {
          Spacer(Modifier.height(12.dp))
          V3Button(StrOnline.dlInvoiceCheck, { onInvoice(co) }, kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.ReceiptLong, height = 38.dp)
        }
      }
    }
  }
}
