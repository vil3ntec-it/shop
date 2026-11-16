package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.JALALI_MONTHS
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeskData
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskCard
import ir.vil3ntec.tohid.ui.desk.DeskCell
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDataShot
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskGrid
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskInfoRow
import ir.vil3ntec.tohid.ui.desk.DeskKpi
import ir.vil3ntec.tohid.ui.desk.DeskKpiCard
import ir.vil3ntec.tohid.ui.desk.DeskKpiRow
import ir.vil3ntec.tohid.ui.desk.DeskLink
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskPage
import ir.vil3ntec.tohid.ui.desk.DeskPane
import ir.vil3ntec.tohid.ui.desk.DeskSearchField
import ir.vil3ntec.tohid.ui.desk.DeskSegmented
import ir.vil3ntec.tohid.ui.desk.DeskShareBars
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskToolbar
import ir.vil3ntec.tohid.ui.desk.deskPaneWidth
import ir.vil3ntec.tohid.ui.desk.rememberDeskLoad
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState

/*
 *  مصارف و رسیدها — **کامپیوتر** (`claude/e-desk-d3b`). همان مدل‌های گوشی (`ExpensesModel`، `ReceiptsModel`) و همان
 *  کارها (`ExpensesActions`، باز کردنِ قرض‌دار/مصرف)؛ فقط چیدمانِ میز: کارتِ KPI، یک نوارِ پالایه، جدولِ آرامِ تنبل و پنلِ کنار.
 */

private fun primary(e: androidx.compose.ui.input.key.KeyEvent): Boolean = e.isCtrlPressed || e.isMetaPressed

/**
 *  مصارف (`expenses`): سه کارت (جمعِ ماه با تغییر نسبت به ماهِ پیش و خطِ روزانه، شمارِ ردیف، بیشترین دسته)، ماه (کادرِ
 *  کشویی با «ماه…»)، دسته و جست‌وجو، «مصرفِ تازه» (Ctrl+N)؛ جدول (تاریخ، عنوان، دسته، مبلغ) و در کنار سهمِ دسته‌ها — یا
 *  مصرفِ برگزیده با «ویرایش» و «حذف» (با پرسش، همان متنِ گوشی).
 */
@Composable
internal fun DeskExpensesScreen(d: ShopData, monthArg: String?, categoryArg: String?, actions: ExpensesActions) {
  val today = todayIso()
  var month by rememberSaveable { mutableStateOf(ExpensesModel.parseMonth(monthArg) ?: today.take(7)) }
  var category by rememberSaveable { mutableStateOf(categoryArg?.takeIf { it.isNotBlank() }) }
  LaunchedEffect(monthArg) { ExpensesModel.parseMonth(monthArg)?.let { month = it } }
  var search by remember { mutableStateOf("") }
  var picked by remember { mutableStateOf<String?>(null) }
  var confirm by remember { mutableStateOf<Expense?>(null) }
  val searchFocus = remember { FocusRequester() }
  val term = search.trim()
  val shown = remember(d, month, category, term) {
    ExpensesModel.shown(d, month, category).filter { term.isEmpty() || it.title.contains(term, true) || it.category.contains(term, true) || it.notes.contains(term, true) }
  }
  val byCategory = remember(d, month) { ExpensesModel.byCategory(d, month) }
  val total = remember(d, month) { ExpensesModel.total(d, month) }
  val prev = remember(d, month) { ExpensesModel.total(d, ExpensesModel.shift(month, -1)) }
  val count = remember(d, month) { ExpensesModel.shown(d, month, null).size }
  val spark = remember(d, month) {
    val days = d.expenses.filter { it.date.startsWith(month) }.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.amount } }
    (1..31).map { day -> days["$month-" + day.toString().padStart(2, '0')] ?: 0.0 }.dropLastWhile { it == 0.0 }
  }
  val shotPick = DeskDataShot.pick.value
  LaunchedEffect(shotPick, shown.isNotEmpty()) { if (shotPick == "first") shown.firstOrNull()?.let { picked = it.id } }
  val label = MoneyText.month(month)
  //  رنگِ «مصارف» ِ نقشهٔ گوشی صورتی است؛ صاحب‌کار روی کامپیوتر صورتی نمی‌خواهد ⇒ بنفشِ آرام
  val tone = DeskHues.tone(ir.vil3ntec.tohid.ui.v3.V3Hue.VIOLET)

  DeskPage(
    Modifier.onPreviewKeyEvent { e ->
      if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
      when {
        primary(e) && e.key == Key.N -> { actions.onNew(); true }
        primary(e) && e.key == Key.F -> { runCatching { searchFocus.requestFocus() }; true }
        e.key == Key.Escape && picked != null && confirm == null -> { picked = null; true }
        else -> false
      }
    },
  ) {
    DeskKpiRow {
      val m = Modifier.weight(1f).fillMaxHeight()
      val pct = if (prev > 0.0) (((total - prev) * 100.0) / prev).toInt() else null
      DeskKpiCard(DeskKpi(StrMoney.monthTotal(label), MoneyText.afn(total), Icons.Outlined.Payments, tone, pct = pct, versus = StrDeskData.versusPrevMonth,
        fallback = StrDeskData.noPrevExpenses, higherIsBetter = false, spark = spark), m)
      DeskKpiCard(DeskKpi(StrDeskData.rowsLabel, plain(count), Icons.AutoMirrored.Outlined.ReceiptLong, DeskHues.tone(ir.vil3ntec.tohid.ui.v3.V3Hue.SKY),
        fallback = StrMoney.rows(shown.size).takeIf { shown.size != count }), m)
      val top = byCategory.maxByOrNull { it.second }
      DeskKpiCard(DeskKpi(StrReports.topCategory, top?.let { MoneyText.afn(it.second) } ?: "—", Icons.Outlined.Category, DeskHues.tone(ir.vil3ntec.tohid.ui.v3.V3Hue.AMBER),
        fallback = top?.first?.ifBlank { StrReports.noCategory } ?: "—"), m)
    }
    DeskToolbar(
      end = { DeskButton(StrMoney.newExpense, actions.onNew, kind = DeskButtonKind.PRIMARY, icon = Icons.Outlined.Payments, shortcut = "Ctrl+N") },
    ) {
      DeskSearchField(search, { search = it }, StrKit.search, Modifier.width(260.dp), searchFocus)
      val months = remember(d, today, month, I18n.lang) {
        ExpensesModel.choices(d, today, month).map { DeskOption(it, MoneyText.month(it)) } +
          DeskOption(ExpensesModel.OTHER_MONTH, StrReports.rangePickMonth, divider = true)
      }
      DeskDropdown(month, months, { m ->
        if (m == ExpensesModel.OTHER_MONTH) actions.onPickMonth(month) { p -> month = p; category = null; picked = null }
        else { month = m; category = null; picked = null }
      }, label = StrMoney.month, shown = label)
      DeskDropdown(
        category,
        listOf(DeskOption<String?>(null, StrMoney.allExpenses)) + byCategory.map { DeskOption<String?>(it.first, it.first.ifBlank { StrReports.noCategory }) },
        { category = it; picked = null },
        label = StrMoney.category,
      )
    }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
      val paneW = deskPaneWidth(maxWidth)
      val sel = shown.find { it.id == picked }
      Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
        val cols = listOf(
          DeskColumn(StrInvoices.colDate, width = 112.dp), DeskColumn(StrDeskData.colTitle, weight = 1f),
          DeskColumn(StrMoney.category, width = 160.dp), DeskColumn(ir.vil3ntec.tohid.core.i18n.StrDesk.colAmount, width = 128.dp, end = true),
        )
        DeskGrid(
          cols, shown.size, Modifier.weight(1f).fillMaxHeight(),
          key = { shown[it].id },
          selected = shown.indexOfFirst { it.id == picked }.takeIf { it >= 0 },
          onPick = { picked = shown[it].id },
          onOpen = { actions.onOpen(shown[it].id) },
          rowLabel = { shown[it].title.ifBlank { shown[it].category } },
          footer = listOf(StrInvoices.total, "", "", money(shown.sumOf { it.amount })),
          empty = { V3EmptyState(Icons.Outlined.Payments, StrMoney.noExpensesMonth, body = StrMoney.noExpensesBody, action = StrMoney.newExpense, onAction = actions.onNew) },
        ) { i, col ->
          val e = shown[i]
          when (col) {
            0 -> DeskCell(MoneyText.short(e.date, today), muted = true)
            1 -> DeskCell(e.title.ifBlank { e.category.ifBlank { StrMoney.expenseFallback } })
            2 -> DeskCell(e.category.ifBlank { StrReports.noCategory }, muted = true)
            else -> DeskCell(money(e.amount), strong = true)
          }
        }
        Box(Modifier.width(paneW).fillMaxHeight()) {
          if (sel != null) DeskPane(
            title = sel.title.ifBlank { sel.category.ifBlank { StrMoney.expenseFallback } },
            subtitle = formatDate(sel.date),
            onClose = { picked = null },
            actions = {
              DeskButton(StrMoney.edit, { actions.onOpen(sel.id) }, Modifier.weight(1f), kind = DeskButtonKind.PRIMARY, icon = Icons.Outlined.Edit)
              DeskButton(StrMoney.delete, { confirm = sel }, Modifier.weight(1f), icon = Icons.Outlined.DeleteOutline)
            },
            modifier = Modifier.fillMaxSize(),
          ) {
            DeskInfoRow(ir.vil3ntec.tohid.core.i18n.StrDesk.colAmount, MoneyText.afn(sel.amount), strong = true)
            DeskInfoRow(StrMoney.category, sel.category.ifBlank { StrReports.noCategory })
            DeskInfoRow(StrInvoices.colDate, formatDate(sel.date))
            if (sel.notes.isNotBlank()) DeskInfoRow(StrMoney.notes, sel.notes)
          } else DeskCard(Modifier.fillMaxSize(), title = StrMoney.byCategory, icon = Icons.Outlined.PieChart, tone = tone,
            action = { if (category != null) DeskLink(StrMoney.allCategories, { category = null }) }) {
            if (byCategory.isEmpty()) DeskNote(StrMoney.noExpensesMonth)
            //  زدنِ هر دسته = پالایهٔ همان دسته (همان کارِ تراشهٔ دستهٔ گوشی)؛ دستهٔ برگزیده پررنگ
            else DeskShareBars(
              byCategory.map { (it.first.ifBlank { StrReports.noCategory }) to it.second }, tone.solid, Modifier.fillMaxWidth(), format = MoneyText::afn,
              selected = byCategory.indexOfFirst { it.first == category }.takeIf { it >= 0 },
              onPick = { i -> val c = byCategory[i].first; category = if (category == c) null else c; picked = null },
            )
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
      onConfirm = { actions.onDelete(e.id); picked = null },
      onDismiss = { confirm = null },
    )
  }
}

/**
 *  رسیدها (`receipts`): زبانهٔ «رسیدهای قرض‌داران / مصارف»، سال و ماه (کادرِ کشویی)، جست‌وجوی نام؛ کارتِ جمع و شمار؛ جدولِ
 *  آرام (تاریخ، نام، یادداشت، مبلغ) — زدنِ ردیف همان قرض‌دار یا مصرف را باز می‌کند (همان کارِ گوشی).
 */
@Composable
internal fun DeskReceiptsScreen(
  d: ShopData,
  tabArg: String?,
  yearArg: Int?,
  monthArg: Int?,
  onOpenDebtor: (String) -> Unit,
  onOpenExpense: (String) -> Unit,
) {
  var tabKey by rememberSaveable { mutableStateOf(ReceiptsModel.Tab.parse(tabArg).key) }
  var year by rememberSaveable { mutableStateOf(yearArg) }
  var month by rememberSaveable { mutableStateOf(monthArg?.takeIf { it in 1..12 }) }
  var search by remember { mutableStateOf("") }
  val tab = ReceiptsModel.Tab.parse(tabKey)
  val all = rememberDeskLoad(d, tab) { ReceiptsModel.rows(d, tab) }
  val years = remember(all) { ReceiptsModel.years(all.orEmpty()) }
  val term = search.trim()
  val shown = remember(all, year, month, term) {
    ReceiptsModel.filter(all.orEmpty(), year, month, null).filter { term.isEmpty() || it.title.contains(term, true) || it.note.contains(term, true) }
  }
  val today = todayIso()
  val debtors = tab == ReceiptsModel.Tab.DEBTORS
  val hue = if (debtors) DeskHues.tone("receipts") else DeskHues.tone(ir.vil3ntec.tohid.ui.v3.V3Hue.VIOLET)
  val searchFocus = remember { FocusRequester() }

  DeskPage(
    Modifier.onPreviewKeyEvent { e ->
      if (e.type == KeyEventType.KeyDown && primary(e) && e.key == Key.F) { runCatching { searchFocus.requestFocus() }; true } else false
    },
  ) {
    DeskKpiRow {
      val m = Modifier.weight(1f).fillMaxHeight()
      DeskKpiCard(DeskKpi(StrMoney.total, MoneyText.afn(ReceiptsModel.total(shown)), Icons.AutoMirrored.Outlined.ReceiptLong, hue, fallback = StrMoney.rows(shown.size)), m)
      val thisMonth = remember(all, today) { val ym = today.take(7); all.orEmpty().filter { it.date.startsWith(ym) }.sumOf { it.amount } }
      DeskKpiCard(DeskKpi(StrDeskData.thisMonthTotal, MoneyText.afn(thisMonth), Icons.Outlined.Payments, DeskHues.tone(ir.vil3ntec.tohid.ui.v3.V3Hue.SKY)), m)
      DeskKpiCard(DeskKpi(StrDeskData.rowsLabel, plain(all.orEmpty().size), Icons.Outlined.Category, DeskHues.tone(ir.vil3ntec.tohid.ui.v3.V3Hue.BRAND)), m)
    }
    DeskToolbar {
      DeskSegmented(
        listOf(ReceiptsModel.Tab.DEBTORS to StrMoney.tabDebtorReceipts, ReceiptsModel.Tab.EXPENSES to StrMoney.tabExpenses),
        tab, { tabKey = it.key },
      )
      DeskSearchField(search, { search = it }, StrKit.search, Modifier.width(240.dp), searchFocus)
      DeskDropdown(year, listOf(DeskOption<Int?>(null, StrMoney.allYears)) + years.map { DeskOption<Int?>(it, plain(it)) }, { year = it }, label = StrMoney.year)
      DeskDropdown(month, listOf(DeskOption<Int?>(null, StrMoney.allMonths)) + JALALI_MONTHS.mapIndexed { i, n -> DeskOption<Int?>(i + 1, n) }, { month = it }, label = StrMoney.month)
    }
    val cols = listOf(
      DeskColumn(StrInvoices.colDate, width = 120.dp), DeskColumn(if (debtors) StrMoney.debtor else StrDeskData.colTitle, weight = 1f),
      DeskColumn(if (debtors) StrMoney.notes else StrMoney.category, weight = 1f), DeskColumn(ir.vil3ntec.tohid.core.i18n.StrDesk.colAmount, width = 128.dp, end = true),
    )
    val narrowed = year != null || month != null || term.isNotEmpty()
    DeskGrid(
      cols, shown.size, Modifier.fillMaxWidth().weight(1f),
      key = { tab.key + shown[it].id },
      onPick = { i ->
        val r = shown[i]
        when {
          !debtors -> onOpenExpense(r.id)
          r.linked && r.debtorId != null -> onOpenDebtor(r.debtorId)
        }
      },
      rowLabel = { shown[it].title },
      footer = listOf(StrInvoices.total, "", "", money(ReceiptsModel.total(shown))),
      empty = {
        V3EmptyState(
          Icons.AutoMirrored.Outlined.ReceiptLong,
          if (debtors) StrMoney.noReceipts else StrMoney.noExpensesFiltered,
          action = if (narrowed) StrReports.showAll else null,
          onAction = if (narrowed) ({ year = null; month = null; search = "" }) else null,
        )
      },
    ) { i, col ->
      val r = shown[i]
      when (col) {
        0 -> DeskCell(MoneyText.short(r.date, today), muted = true)
        1 -> DeskCell(r.title, muted = !r.linked)
        2 -> DeskCell(r.note, muted = true)
        else -> DeskCell(money(r.amount), strong = true)
      }
    }
  }
}
