package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.gridMinSize
import ir.vil3ntec.tohid.ui.screens.DebtorListModel
import ir.vil3ntec.tohid.ui.screens.DebtorTile
import ir.vil3ntec.tohid.ui.screens.DebtorsAddButton
import ir.vil3ntec.tohid.ui.screens.DebtorsBackdrop
import ir.vil3ntec.tohid.ui.screens.DebtorsEmptyCard
import ir.vil3ntec.tohid.ui.screens.DebtorsSearchField
import ir.vil3ntec.tohid.ui.screens.DebtorsSortRow
import ir.vil3ntec.tohid.ui.screens.DebtorsStatsCard
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.PhoneDebtorsTheme
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** کارهای فهرستِ قرض‌داران — هر کدام یک مسیر */
internal class DebtorsListActions(
  val onBack: (() -> Unit)?,
  val onOpen: (String) -> Unit,
  val onEdit: (String) -> Unit,
  val onDelete: (String) -> Unit,
  val onAdd: () -> Unit,
)

/**
 *  قرض‌داران (`debtors?sort=&q=`) — شکلِ r9 «همین مدلِ دقیق» بی هیچ تغییری (`PhoneDebtors.kt`):
 *  کارتِ سه‌عددی، جستجو با میکروفون، تراشه‌های ترتیب و شبکهٔ کارت‌های سرخ/سبز با × و مداد.
 *  فقط سیم‌کشی عوض شد: کارت ⇐ `debtors/{id}`، مداد ⇐ `debtors/{id}/edit`، × ⇐ `debtors/{id}/delete`،
 *  «+» ⇐ `debtors/new`. ترتیب و جستجو از پرسشِ مسیر خوانده می‌شوند (`?sort=oldest` ِ هشدارها).
 */
@Composable
internal fun DebtorsListScreen(d: ShopData, sortArg: String?, queryArg: String?, actions: DebtorsListActions) {
  var search by rememberSaveable { mutableStateOf(queryArg.orEmpty()) }
  var sortName by rememberSaveable { mutableStateOf(DebtorListModel.Sort.parse(sortArg?.uppercase()).name) }
  //  پیوندِ تازه (هشدار، جستجو) با همان تبِ باز ⇐ همان ترتیب/جستجو
  LaunchedEffect(sortArg) { if (sortArg != null) sortName = DebtorListModel.Sort.parse(sortArg.uppercase()).name }
  LaunchedEffect(queryArg) { if (queryArg != null) search = queryArg }
  val sort = DebtorListModel.Sort.parse(sortName)
  val grid = rememberLazyGridState()
  val stagger = rememberV3Stagger()
  val hidden = MoneyUndo.hiddenDebtors
  val term = search.trim()
  //  ردیف‌ها یک بار حساب می‌شوند، نه با هر کشیدنِ فهرست (`DebtorListModel`)
  val allRows = remember(d, term, sort) { DebtorListModel.rows(d, term, sort, System.currentTimeMillis()) }
  val rows = if (hidden.isEmpty()) allRows else allRows.filterNot { it.debtor.id in hidden }
  val summary = remember(d) { DebtorListModel.summary(d) }
  val motion = Motion.enabled

  V3Scaffold(title = Str.debtors, onBack = actions.onBack, largeTitle = false) { pad ->
    PhoneDebtorsTheme {
      DebtorsBackdrop {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = gridMinSize(phone = PhoneV3Debt.tileMin, tablet = PhoneV3Debt.tileMinTablet)),
          modifier = Modifier.fillMaxSize(),
          state = grid,
          //  پایین: جای دکمهٔ گردِ «+» (۱۶ + ۵۶ + ۱۶) بالای پایینِ صفحه
          contentPadding = PaddingValues(PhoneV3Debt.gutter, 8.dp, PhoneV3Debt.gutter, pad.calculateBottomPadding() + 72.dp),
          horizontalArrangement = Arrangement.spacedBy(PhoneV3Debt.gap),
          verticalArrangement = Arrangement.spacedBy(PhoneV3Debt.gap),
        ) {
          item(key = "debtors-head", span = { GridItemSpan(maxLineSpan) }) {
            Column {
              DebtorsStatsCard(summary)
              Spacer(Modifier.height(12.dp))
              DebtorsSearchField(value = search, onValueChange = { search = it })
              Spacer(Modifier.height(10.dp))
              if (d.debtors.isNotEmpty()) {
                DebtorsSortRow(current = sort, count = rows.size) { sortName = it.name }
                Spacer(Modifier.height(4.dp))
              }
            }
          }
          if (rows.isEmpty()) {
            item(key = "debtors-empty", span = { GridItemSpan(maxLineSpan) }) {
              if (d.debtors.isEmpty()) {
                DebtorsEmptyCard(Icons.Filled.Groups, StrMoney.emptyDebtorsTitle, StrMoney.emptyDebtorsBody, actionText = StrMoney.newDebtor, onAction = actions.onAdd)
              } else {
                DebtorsEmptyCard(Icons.Filled.Search, StrMoney.noDebtorMatchTitle, StrMoney.noDebtorMatchBody)
              }
            }
          } else {
            itemsIndexed(rows, key = { _, row -> row.debtor.id }) { index, row ->
              //  ورودِ پله‌پله در نخستین نمایش؛ با عوضِ ترتیب هر کارت به جای تازه‌اش سُر می‌خورد
              Box((if (motion) Modifier.animateItem() else Modifier).v3Stagger(stagger, row.debtor.id, index)) {
                DebtorTile(
                  row = row,
                  onOpen = { actions.onOpen(row.debtor.id) },
                  onEdit = { actions.onEdit(row.debtor.id) },
                  onDelete = { actions.onDelete(row.debtor.id) },
                )
              }
            }
          }
        }
        //  دفترِ خالی دکمهٔ «قرض‌دار تازه» ِ خودش را دارد؛ آن‌جا دایره کشیده نمی‌شود (Ctrl+N می‌ماند)
        DebtorsAddButton(
          onClick = actions.onAdd,
          modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = pad.calculateBottomPadding()),
          visible = d.debtors.isNotEmpty(),
        )
      }
    }
  }
}
