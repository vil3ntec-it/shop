package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SheetCloser
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/**
 *  برگهٔ «انتخابِ مشتری» (`sale/checkout/customer`): جست‌وجو در نام، ردیفِ نخستِ «بدون
 *  قرض‌دار» (فقط فروشِ نقدی)، هر قرض‌دار با شماره و حالِ حساب (رنگ + برچسب) و ردیفِ آخرِ
 *  «قرض‌دارِ تازه». انتخاب ⇒ برگه با حرکت پایین می‌رود و بعد `onPick`.
 *
 *  @param selected مشتریِ فعلی (تیکِ برند)
 */
@Composable
fun CustomerPickerSheet(
  d: ShopData,
  selected: String?,
  onPick: (String) -> Unit,
  onNew: () -> Unit,
  onClose: () -> Unit,
  initialQuery: String = "",
) {
  val c = PhoneV3.colors
  var query by remember { mutableStateOf(initialQuery) }
  val rows = remember(d.debtors, d.transactions, query) { CustomerPickerModel.rows(d, query) }
  val stagger = rememberV3Stagger()
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  val check: @Composable () -> Unit = {
    Icon(Icons.Outlined.Check, contentDescription = StrKit.selected, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp))
  }

  //  انتخاب ⇒ نتیجه با `nav.finish` می‌رود؛ بستنِ برگه پس از آن دیگر «برگشت» نیست
  val chosen = remember { booleanArrayOf(false) }
  fun choose(action: () -> Unit, closer: V3SheetCloser) { chosen[0] = true; closer.close(action) }
  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = StrSale.pickCustomer, scroll = false) { closer ->
    V3SearchField(query, { query = it }, StrSale.debtorNameHint)
    Spacer(Modifier.height(12.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = LIST_MAX).clip(shape).border(1.dp, c.hairline, shape)) {
      item(key = "none") {
        V3ListRow(
          title = StrSale.noDebtor,
          subtitle = StrSale.noDebtorHint,
          icon = Icons.Outlined.PersonOff,
          trailing = if (selected == null) check else null,
          onClick = { choose({ onPick(CustomerPickerModel.NONE) }, closer) },
        )
      }
      itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
        V3Divider(start = 64.dp)
        V3ListRow(
          title = r.name,
          modifier = Modifier.v3AnimateItem(this).v3Stagger(stagger, r.id, i),
          subtitle = r.phone.ifBlank { null },
          icon = Icons.Outlined.Person,
          hue = V3Hue.CORAL,
          value = CustomerPickerModel.label(r.balance),
          valueColor = when (r.tone) {
            BalanceTone.OWES -> c.negative
            BalanceTone.CREDIT -> c.positive
            BalanceTone.SETTLED -> c.inkMuted
          },
          trailing = if (selected == r.id) check else null,
          onClick = { choose({ onPick(r.id) }, closer) },
        )
      }
      if (rows.isEmpty()) {
        item(key = "empty") {
          V3Divider()
          Text(
            CustomerPickerModel.emptyText(d),
            style = V3Type.callout,
            color = c.inkMuted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
          )
        }
      }
      item(key = "new") {
        V3Divider()
        V3ListRow(
          title = StrSale.newDebtor,
          icon = Icons.Outlined.PersonAdd,
          hue = V3Hue.BRAND,
          onClick = { choose(onNew, closer) },
        )
      }
    }
  }
}

/** بلندیِ بیشینهٔ فهرست درونِ برگه (AUDIT-NAV: Lazy درونِ برگه مرز می‌خواهد) */
private val LIST_MAX = 460.dp
