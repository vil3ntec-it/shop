package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.DebtorListModel
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Sheet

/**
 *  «دریافت» (`debtors/receive`، از «دریافت» ِ خانه و کارتِ کشیدنی) — گامِ ۱: قرض‌دارانِ بدهکار،
 *  بیشترین قرض اول، با جستجو. زدن ⇐ گامِ ۲ (`debtors/{id}/tx?kind=receive`) روی همین برگه؛
 *  «برگشت» در گامِ ۲ دوباره همین فهرست است.
 */
@Composable
internal fun ReceivePickerSheet(d: ShopData, onPick: (String) -> Unit, onClose: () -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  val term = query.trim()
  val rows = remember(d, term) {
    DebtorListModel.rows(d, term, DebtorListModel.Sort.MOST, System.currentTimeMillis()).filter { it.owes }.filterNot { it.debtor.id in MoneyUndo.hiddenDebtors }
  }
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onClose, title = StrMoney.receiveTitlePick) {
    V3SearchField(query, { query = it }, StrMoney.receivePickHint)
    Spacer(Modifier.height(10.dp))
    if (rows.isEmpty()) {
      V3EmptyState(Icons.Outlined.PeopleOutline, if (term.isEmpty()) StrMoney.noOneOwes else StrMoney.noDebtorMatchTitle)
    } else {
      LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(PhoneV3.tileRadius)).background(c.tile),
      ) {
        itemsIndexed(rows, key = { _, r -> r.debtor.id }) { i, r ->
          if (i > 0) V3Divider(start = 16.dp)
          V3ListRow(
            title = r.debtor.name,
            subtitle = r.debtor.phone.takeIf { it.isNotBlank() }?.let { ir.vil3ntec.tohid.LocaleText.phoneDisplay(it) },
            tile = false,
            trailing = { Text(MoneyText.afn(r.balance), style = v3Text(14, FontWeight.SemiBold, 20), color = LedgerTone.OWE.ink()) },
            onClick = { onPick(r.debtor.id) },
          )
        }
      }
    }
  }
}
