package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3HeroCard
import ir.vil3ntec.tohid.ui.v3.V3HeroChip
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules

/** کارهای صفحهٔ مصارف — برگه‌ها مسیرند؛ ماه و دسته نتیجهٔ برگهٔ خودشان را برمی‌گردانند */
internal class ExpensesActions(
  val onBack: (() -> Unit)?,
  val onNew: () -> Unit,
  val onOpen: (String) -> Unit,
  /** `LedgerEngine.deleteExpense` (ردپا در «دفترچهٔ ثبت») */
  val onDelete: (String) -> Unit,
  /** `expenses/month` ⇐ ماهِ انتخاب‌شده */
  val onPickMonth: (current: String, then: (String) -> Unit) -> Unit,
  /** `expenses/breakdown` ⇐ دستهٔ انتخاب‌شده («» یعنی همه) */
  val onBreakdown: (month: String, category: String?, then: (String?) -> Unit) -> Unit,
)

/**
 *  مصارف (`expenses?month=&category=`): زیرِ عنوان ‹ ماه › (زدنِ نام ⇐ برگهٔ ماه)، کادرِ بزرگِ
 *  «جمع مصارف ماه» با شمارنده، نوارِ رنگیِ دسته‌ها با تراشهٔ هر دسته (زدن = فیلتر)، و ردیف‌ها با
 *  آیکنِ رنگِ همراهِ بخش و مبلغ؛ کشیدن ⇐ «حذف» با پرسش (متنِ امروز). «مصرف تازه» شناور.
 *  مبلغ‌ها دیگر سرخ نیستند: سرخ فقط برای خطر است (THEME-V2) — رنگِ مصارف رنگِ همراهِ خودش است.
 */
@Composable
internal fun ExpensesScreen(d: ShopData, monthArg: String?, categoryArg: String?, actions: ExpensesActions) {
  val today = todayIso()
  var month by rememberSaveable { mutableStateOf(ExpensesModel.parseMonth(monthArg) ?: today.take(7)) }
  var category by rememberSaveable { mutableStateOf(categoryArg?.takeIf { it.isNotBlank() }) }
  LaunchedEffect(monthArg) { ExpensesModel.parseMonth(monthArg)?.let { month = it } }
  var confirm by remember { mutableStateOf<Expense?>(null) }
  val shown = remember(d, month, category) { ExpensesModel.shown(d, month, category) }
  val byCategory = remember(d, month) { ExpensesModel.byCategory(d, month) }
  val total = rememberCountUp(remember(d, month) { ExpensesModel.total(d, month) })
  val label = MoneyText.month(month)
  val stagger = rememberV3Stagger(month, category)
  val hue = V3Modules.hueOf("expenses")

  V3Scaffold(
    title = Str.expenses,
    onBack = actions.onBack,
    chips = {
      MonthSwitcher(
        label = label,
        onPrev = { month = ExpensesModel.shift(month, -1); category = null },
        onNext = { month = ExpensesModel.shift(month, 1); category = null },
        onPick = { actions.onPickMonth(month) { m -> month = m; category = null } },
      )
    },
    fab = { expanded -> V3Fab(Icons.Outlined.Payments, StrMoney.newExpense, expanded, actions.onNew) },
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 72.dp),
      ) {
        item(key = "hero") {
          V3HeroCard(
            title = StrMoney.monthTotal(label),
            value = MoneyText.afn(total),
            caption = StrMoney.rows(shown.size),
            chips = if (byCategory.isEmpty()) emptyList() else listOf(
              V3HeroChip(StrMoney.byCategory, Icons.Outlined.PieChart) { actions.onBreakdown(month, category) { c -> category = c?.takeIf { it.isNotBlank() } } },
            ),
          )
          Spacer(Modifier.height(12.dp))
        }
        if (byCategory.isNotEmpty()) {
          item(key = "categories") {
            CategoryBreakdown(byCategory, category, onPick = { c -> category = if (category == c) null else c })
            Spacer(Modifier.height(12.dp))
          }
        }
        item(key = "list-title") {
          Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
              if (category == null) StrMoney.allExpenses else StrMoney.categoryExpenses(category.orEmpty()),
              style = v3Text(15, FontWeight.Bold, 20),
              color = PhoneV3.colors.ink,
              modifier = Modifier.weight(1f),
            )
            if (category != null) V3TextButton(StrMoney.allCategories, { category = null })
          }
        }
        if (shown.isEmpty()) {
          item(key = "empty") {
            V3Card {
              V3EmptyState(Icons.Outlined.Payments, StrMoney.noExpensesMonth, body = StrMoney.noExpensesBody, action = StrMoney.newExpense, onAction = actions.onNew)
            }
          }
        } else {
          itemsIndexed(shown, key = { _, e -> e.id }) { i, e ->
            val r = PhoneV3.groupRadius
            val first = i == 0
            val last = i == shown.lastIndex
            val shape = RoundedCornerShape(topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp, bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp)
            Column(Modifier.v3AnimateItem(this).v3Stagger(stagger, e.id, i).clip(shape).background(PhoneV3.colors.card)) {
              V3ListRow(
                title = e.title.ifBlank { e.category },
                subtitle = listOf(e.category, MoneyText.date(e.date)).filter { it.isNotBlank() }.joinToString(" — "),
                icon = Icons.Outlined.Payments,
                hue = hue,
                trailing = { Text(MoneyText.afn(e.amount), style = v3Text(14, FontWeight.SemiBold, 20), color = PhoneV3.colors.ink) },
                endActions = listOf(V3SwipeAction(Icons.Outlined.DeleteOutline, StrMoney.delete, V3ButtonKind.DANGER) { confirm = e }),
                onClick = { actions.onOpen(e.id) },
              )
              if (!last) V3Divider(start = 64.dp)
            }
          }
        }
      }
    }
  }

  confirm?.let { e ->
    V3ConfirmSheet(
      title = StrMoney.deleteExpenseTitle,
      message = StrMoney.deleteExpenseBody(e.title, money(e.amount)),
      confirm = StrMoney.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = { actions.onDelete(e.id) },
      onDismiss = { confirm = null },
    )
  }
}

/** ‹ «میزان ۱۴۰۵» › — زدنِ نام ⇐ برگهٔ ماه؛ فلش‌ها آینه در راست‌به‌چپ (پیش = سمتِ آغاز) */
@Composable
private fun MonthSwitcher(label: String, onPrev: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
    V3IconButton(if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft, StrMoney.prevMonth, size = 40.dp, onClick = onPrev)
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
      Text(
        label,
        style = v3Text(17, FontWeight.SemiBold, 24),
        color = PhoneV3.colors.ink,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).v3Pressable(label = StrMoney.pickMonth, onClick = onPick).padding(horizontal = 14.dp, vertical = 6.dp),
      )
    }
    V3IconButton(if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, StrMoney.nextMonth, size = 40.dp, onClick = onNext)
  }
}
