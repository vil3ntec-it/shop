package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.JALALI_MONTHS
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3PickerSheet
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Stat
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules

private enum class ReceiptsPicker { YEAR, MONTH, DEBTOR }

/**
 *  رسیدها (`receipts?tab=&y=&m=`): دو تبِ «رسیدهای قرض‌داران | مصارف» با نشانگرِ لغزان، تراشه‌های
 *  سال/ماه (و قرض‌دار در تبِ اول) که هر کدام برگهٔ انتخابِ خودش را باز می‌کند، کارتِ «جمع» با
 *  شمارنده، و ردیف‌ها: رسید ⇐ حسابِ همان قرض‌دار، مصرف ⇐ برگهٔ همان مصرف.
 */
@Composable
internal fun ReceiptsScreen(
  d: ShopData,
  tabArg: String?,
  yearArg: Int?,
  monthArg: Int?,
  onBack: (() -> Unit)?,
  onOpenDebtor: (String) -> Unit,
  onOpenExpense: (String) -> Unit,
) {
  var tabKey by rememberSaveable { mutableStateOf(ReceiptsModel.Tab.parse(tabArg).key) }
  var year by rememberSaveable { mutableStateOf(yearArg) }
  var month by rememberSaveable { mutableStateOf(monthArg?.takeIf { it in 1..12 }) }
  var who by rememberSaveable { mutableStateOf<String?>(null) }
  var picker by remember { mutableStateOf<ReceiptsPicker?>(null) }
  val tab = ReceiptsModel.Tab.parse(tabKey)
  val all = remember(d, tab) { ReceiptsModel.rows(d, tab) }
  val years = remember(all) { ReceiptsModel.years(all) }
  val shown = ReceiptsModel.filter(all, year, month, who.takeIf { tab == ReceiptsModel.Tab.DEBTORS })
  val stagger = rememberV3Stagger(tab, year, month, who)
  val months = JALALI_MONTHS

  V3Scaffold(
    title = Str.receipts,
    onBack = onBack,
    chips = {
      Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        V3Segmented(
          listOf(StrMoney.tabDebtorReceipts, StrMoney.tabExpenses),
          selected = tab.ordinal,
          onSelect = { i -> tabKey = ReceiptsModel.Tab.entries[i].key; who = null },
        )
      }
    },
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
      ) {
        item(key = "filters") {
          V3ChipRow(contentPadding = PaddingValues(0.dp)) {
            V3Chip(year?.let { plain(it) } ?: StrMoney.allYears, selected = year != null, icon = Icons.Outlined.CalendarMonth, onClick = { picker = ReceiptsPicker.YEAR })
            V3Chip(month?.let { months[it - 1] } ?: StrMoney.allMonths, selected = month != null, icon = Icons.Outlined.Event, onClick = { picker = ReceiptsPicker.MONTH })
            if (tab == ReceiptsModel.Tab.DEBTORS && d.debtors.isNotEmpty()) {
              V3Chip(
                who?.let { id -> d.debtors.find { it.id == id }?.name } ?: StrMoney.allDebtors,
                selected = who != null,
                icon = Icons.Outlined.Person,
                onClick = { picker = ReceiptsPicker.DEBTOR },
              )
            }
          }
          Spacer(Modifier.height(12.dp))
          V3Stat(
            label = StrMoney.total,
            value = ReceiptsModel.total(shown),
            format = MoneyText::afn,
            modifier = Modifier.fillMaxWidth(),
            hue = if (tab == ReceiptsModel.Tab.DEBTORS) V3Modules.hueOf("receipts") else V3Modules.hueOf("expenses"),
            caption = StrMoney.rows(shown.size),
          )
          Spacer(Modifier.height(16.dp))
        }
        if (shown.isEmpty()) {
          item(key = "empty") {
            V3Card {
              V3EmptyState(
                Icons.AutoMirrored.Outlined.ReceiptLong,
                if (tab == ReceiptsModel.Tab.DEBTORS) StrMoney.noReceipts else StrMoney.noExpensesFiltered,
              )
            }
          }
        } else {
          itemsIndexed(shown, key = { _, r -> tab.key + r.id }) { i, r ->
            ReceiptRow(
              row = r,
              first = i == 0,
              last = i == shown.lastIndex,
              tab = tab,
              modifier = Modifier.v3AnimateItem(this).v3Stagger(stagger, r.id, i),
              onClick = when {
                tab == ReceiptsModel.Tab.EXPENSES -> ({ onOpenExpense(r.id) })
                r.linked && r.debtorId != null -> ({ onOpenDebtor(r.debtorId) })
                else -> null
              },
            )
          }
        }
      }
    }
  }

  when (picker) {
    ReceiptsPicker.YEAR -> V3PickerSheet(
      title = StrMoney.year,
      options = listOf(StrMoney.allYears) + years.map { plain(it) },
      selected = year?.let { plain(it) } ?: StrMoney.allYears,
      onPick = { v -> year = v.toIntOrNull(); if (year == null) month = null },
      onDismiss = { picker = null },
      allowNew = false,
    )
    ReceiptsPicker.MONTH -> V3PickerSheet(
      title = StrMoney.month,
      options = listOf(StrMoney.allMonths) + months,
      selected = month?.let { months[it - 1] } ?: StrMoney.allMonths,
      onPick = { v -> month = months.indexOf(v).takeIf { it >= 0 }?.plus(1) },
      onDismiss = { picker = null },
      allowNew = false,
    )
    ReceiptsPicker.DEBTOR -> DebtorFilterSheet(d, who, onPick = { who = it }, onDismiss = { picker = null })
    null -> Unit
  }
}

/** ردیفِ یک رسید/مصرف درونِ یک کارتِ پیوسته (گوشه‌ها فقط سرِ اول و آخر) */
@Composable
private fun ReceiptRow(row: ReceiptsModel.Row, first: Boolean, last: Boolean, tab: ReceiptsModel.Tab, modifier: Modifier, onClick: (() -> Unit)?) {
  val c = PhoneV3.colors
  val r = PhoneV3.groupRadius
  val shape = RoundedCornerShape(topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp, bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp)
  val receipt = tab == ReceiptsModel.Tab.DEBTORS
  Box(modifier.clip(shape).background(c.card)) {
    androidx.compose.foundation.layout.Column {
      V3ListRow(
        title = row.title,
        subtitle = listOf(MoneyText.date(row.date), row.note).filter { it.isNotBlank() }.joinToString(" — "),
        icon = if (receipt) Icons.Outlined.SouthWest else Icons.Outlined.Payments,
        hue = if (receipt) V3Modules.hueOf("receipts") else V3Modules.hueOf("expenses"),
        trailing = {
          Text(
            MoneyText.afn(row.amount),
            style = v3Text(14, FontWeight.SemiBold, 20),
            color = if (receipt) LedgerTone.SETTLED.ink() else c.ink,
          )
        },
        onClick = onClick,
      )
      if (!last) V3Divider(start = 64.dp)
    }
  }
}

/** قرض‌دار برای فیلتر — با شناسه، نه با نام (دو نفر ممکن است هم‌نام باشند) */
@Composable
private fun DebtorFilterSheet(d: ShopData, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
  val options = remember(d) { listOf(PickOption(null, StrMoney.allDebtors)) + d.debtors.map { PickOption(it.id, it.name, it.phone.ifBlank { null }) } }
  IdPickerSheet(StrMoney.debtor, options, selected, onPick, onDismiss)
}
