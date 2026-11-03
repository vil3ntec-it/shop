package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrSimpleAsk
import ir.vil3ntec.tohid.core.report.SimpleAsk
import ir.vil3ntec.tohid.core.report.SimpleAsk.Param
import ir.vil3ntec.tohid.core.report.SimpleAsk.Span
import ir.vil3ntec.tohid.core.report.SimpleAsk.Template
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.SimpleAskEngine
import ir.vil3ntec.tohid.data.SimpleAskEngine.Key
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  RPT-16 — زبانهٔ «پرسشِ ساده» در «گزارش‌ها»ی گوشی و کامپیوتر، و کلیدِ «تنظیمات».
 *
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrSimpleAsk` (LOC-06)؛ نامِ کالا، دسته، مشتری و فروشنده داده‌اند و همان می‌مانند.
 *
 *  خوانش و اجازه در `core/report/SimpleAsk`، عدد در `SimpleAskEngine` (همان `ReportEngine`)؛ این‌جا فقط نشان داده
 *  می‌شود. پیش‌فرض خاموش (`SimpleAskPref`)؛ پرسشِ پولی فقط برای `ShopRole.canSeeMoney`.
 */

internal fun simpleAskTitle(t: Template): String = when (t) {
  Template.SALES -> StrSimpleAsk.qSales
  Template.PROFIT -> StrSimpleAsk.qProfit
  Template.EXPENSES -> StrSimpleAsk.qExpenses
  Template.PURCHASES -> StrSimpleAsk.qPurchases
  Template.CASHIER_SALES -> StrSimpleAsk.qCashierSales
  Template.CATEGORY_SALES -> StrSimpleAsk.qCategorySales
  Template.PRODUCT_SALES -> StrSimpleAsk.qProductSales
  Template.PRODUCT_STOCK -> StrSimpleAsk.qProductStock
  Template.LOW_STOCK -> StrSimpleAsk.qLowStock
  Template.TOP_SELLING -> StrSimpleAsk.qTopSelling
  Template.SLOW_SELLING -> StrSimpleAsk.qSlowSelling
  Template.CUSTOMER_DEBT -> StrSimpleAsk.qCustomerDebt
  Template.DEBT_TOTAL -> StrSimpleAsk.qDebtTotal
}

/** فرمول و منبعِ هر پاسخ — همان حسابِ `SimpleAskEngine` */
internal fun simpleAskFormula(t: Template): String = when (t) {
  Template.SALES -> StrSimpleAsk.fSales
  Template.PROFIT -> StrSimpleAsk.fProfit
  Template.EXPENSES -> StrSimpleAsk.fExpenses
  Template.PURCHASES -> StrSimpleAsk.fPurchases
  Template.CASHIER_SALES -> StrSimpleAsk.fCashierSales
  Template.CATEGORY_SALES -> StrSimpleAsk.fCategorySales
  Template.PRODUCT_SALES -> StrSimpleAsk.fProductSales
  Template.PRODUCT_STOCK -> StrSimpleAsk.fProductStock
  Template.LOW_STOCK -> StrSimpleAsk.fLowStock
  Template.TOP_SELLING -> StrSimpleAsk.fTopSelling
  Template.SLOW_SELLING -> StrSimpleAsk.fSlowSelling
  Template.CUSTOMER_DEBT -> StrSimpleAsk.fCustomerDebt
  Template.DEBT_TOTAL -> StrSimpleAsk.fDebtTotal
}

private fun paramLabel(p: Param): String = when (p) {
  Param.PERIOD -> StrSimpleAsk.period
  Param.PRODUCT -> StrSimpleAsk.product
  Param.CATEGORY -> StrSimpleAsk.category
  Param.CUSTOMER -> StrSimpleAsk.customer
  Param.CASHIER -> StrSimpleAsk.cashier
}

private fun spanLabel(s: Span): String = when (s) {
  Span.TODAY -> StrSimpleAsk.today
  Span.WEEK -> StrSimpleAsk.week
  Span.MONTH -> StrSimpleAsk.month
  Span.JMONTH -> StrSimpleAsk.solarMonth
  Span.JYEAR -> StrSimpleAsk.solarYear
  Span.ALL -> StrSimpleAsk.all
}

private fun valueLabel(k: Key): String = when (k) {
  Key.NET -> StrSimpleAsk.vNet
  Key.INVOICES -> StrSimpleAsk.vInvoices
  Key.DISCOUNT -> StrSimpleAsk.vDiscount
  Key.RETURNS -> StrSimpleAsk.vReturns
  Key.COGS -> StrSimpleAsk.vCogs
  Key.GROSS_PROFIT -> StrSimpleAsk.vGrossProfit
  Key.EXPENSES -> StrSimpleAsk.vExpenses
  Key.NET_PROFIT -> StrSimpleAsk.vNetProfit
  Key.PURCHASE_TOTAL -> StrSimpleAsk.vPurchaseTotal
  Key.PURCHASE_PAID -> StrSimpleAsk.vPurchasePaid
  Key.PURCHASE_DEBT -> StrSimpleAsk.vPurchaseDebt
  Key.PURCHASES -> StrSimpleAsk.vPurchases
  Key.CATEGORY_NET -> StrSimpleAsk.vCategoryNet
  Key.CATEGORY_PRODUCTS -> StrSimpleAsk.vCategoryProducts
  Key.PRODUCT_NET -> StrSimpleAsk.vProductNet
  Key.ON_HAND -> StrSimpleAsk.vOnHand
  Key.OUT -> StrSimpleAsk.vOut
  Key.LOW -> StrSimpleAsk.vLow
  Key.SOLD -> StrSimpleAsk.vSold
  Key.DEBT_REMAINING -> StrSimpleAsk.vDebtRemaining
  Key.DEBT_GIVEN -> StrSimpleAsk.vDebtGiven
  Key.DEBT_RECEIVED -> StrSimpleAsk.vDebtReceived
  Key.DEBT_TOTAL -> StrSimpleAsk.vDebtTotal
  Key.DEBTORS -> StrSimpleAsk.vDebtors
}

private fun issueText(i: SimpleAsk.Issue): String = when (i.problem) {
  SimpleAsk.Problem.NOT_ALLOWED -> StrSimpleAsk.notAllowed
  SimpleAsk.Problem.BAD_PERIOD -> StrSimpleAsk.badPeriod
  SimpleAsk.Problem.MISSING -> StrSimpleAsk.missing(i.param?.let(::paramLabel).orEmpty())
  SimpleAsk.Problem.UNKNOWN -> StrSimpleAsk.unknown(i.param?.let(::paramLabel).orEmpty())
}

/** عدد با واحدِ خودش؛ ماندهٔ منفیِ مشتری «پیش‌پرداخت» است، نه «قرضِ منفی» — همان «قرض‌داران» */
private fun valueText(v: SimpleAskEngine.Value): String = when {
  v.key == Key.DEBT_REMAINING && v.amount < 0 -> StrSimpleAsk.credit("${money(-v.amount)} ؋")
  v.key == Key.DEBT_REMAINING && v.amount == 0.0 -> StrSimpleAsk.settled
  v.key.kind == SimpleAskEngine.Kind.MONEY -> "${money(v.amount)} ؋"
  v.key.kind == SimpleAskEngine.Kind.COUNT -> v.amount.toLong().toString()
  else -> qty(v.amount)
}

private fun SimpleAsk.Query.with(p: Param, id: String): SimpleAsk.Query = when (p) {
  Param.PRODUCT -> copy(product = id)
  Param.CATEGORY -> copy(category = id)
  Param.CUSTOMER -> copy(customer = id)
  Param.CASHIER -> copy(cashier = id)
  Param.PERIOD -> this
}

/** بخشِ «گزارش‌ها»ی گوشی (`ReportsScreen`) */
internal fun simpleAskSection(scope: LazyListScope, d: ShopData, canSeeMoney: Boolean) = with(scope) {
  item { SimpleAskPanel(d, canSeeMoney) }
}

/** زبانهٔ «پرسشِ ساده» — گوشی در فهرستِ گزارش، کامپیوتر جای جدول (`DeskReportsScreen`) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimpleAskPanel(d: ShopData, canSeeMoney: Boolean, modifier: Modifier = Modifier) {
  val today = todayIso()
  val j = remember(today) { Jalali.ofIso(today) }
  val catalog = remember(d) { SimpleAskEngine.catalog(d) }
  val templates = remember(canSeeMoney) { SimpleAsk.templates(canSeeMoney) }
  var text by rememberSaveable { mutableStateOf("") }
  var query by remember { mutableStateOf<SimpleAsk.Query?>(null) }
  var note by remember { mutableStateOf<String?>(null) }
  var period by remember { mutableStateOf(SimpleAsk.Period(Span.MONTH, j?.year ?: 1405, j?.month ?: 1)) }

  fun ask() {
    val p = SimpleAsk.parse(text, catalog, j?.year ?: 1405, j?.month ?: 1, period)
    val q = p.query
    note = when {
      q == null && p.unknown.isEmpty() -> StrSimpleAsk.notUnderstood
      q == null -> StrSimpleAsk.unknownWords(p.unknown.joinToString("، "))
      p.ambiguous != null -> StrSimpleAsk.ambiguous
      else -> null
    }
    if (q != null) { query = q; q.period?.let { period = it } }
  }

  Column(modifier) {
    Text(StrSimpleAsk.intro, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(StrSimpleAsk.noAi, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        text, { text = it }, Modifier.weight(1f), singleLine = true, label = { Text(StrSimpleAsk.input) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { ask() }),
      )
      Spacer(Modifier.width(8.dp))
      Button(onClick = ::ask, enabled = text.isNotBlank()) {
        Icon(Icons.Filled.QuestionAnswer, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(StrSimpleAsk.ask)
      }
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.danger, modifier = Modifier.padding(top = 6.dp)) }
    Spacer(Modifier.height(10.dp))
    SectionTitle(StrSimpleAsk.presets)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      templates.forEach { t ->
        FilterChip(
          selected = query?.template == t,
          onClick = { note = null; query = SimpleAsk.Query(t, period = period.takeIf { Param.PERIOD in t.needs }) },
          label = { Text(simpleAskTitle(t)) },
        )
      }
    }
    val q = query ?: return@Column
    Spacer(Modifier.height(10.dp))
    Panel(Modifier.fillMaxWidth()) {
      Text(simpleAskTitle(q.template), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      q.template.needs.forEach { p ->
        Spacer(Modifier.height(8.dp))
        if (p == Param.PERIOD) PeriodPick(q.period ?: period) { next -> period = next; query = q.copy(period = next) }
        else NamePick(paramLabel(p), catalog.named(p), q.value(p)) { id -> query = q.with(p, id) }
      }
    }
    Spacer(Modifier.height(10.dp))
    val outcome = remember(d, q, today, canSeeMoney) { SimpleAskEngine.answer(d, q, today, canSeeMoney) }
    AnswerCard(outcome)
  }
}

/** کامپیوتر: پانل جای جدول، با پیمایشِ خودش (گوشی درونِ `LazyColumn` است و پیمایش نمی‌خواهد) */
@Composable
fun DeskSimpleAsk(d: ShopData, canSeeMoney: Boolean, modifier: Modifier = Modifier) {
  SimpleAskPanel(d, canSeeMoney, modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
}

@Composable
private fun PeriodPick(p: SimpleAsk.Period, onPick: (SimpleAsk.Period) -> Unit) {
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Span.entries.forEach { s -> FilterChip(selected = p.span == s, onClick = { onPick(p.copy(span = s)) }, label = { Text(spanLabel(s)) }) }
  }
  if (p.span != Span.JMONTH && p.span != Span.JYEAR) return
  val monthly = p.span == Span.JMONTH
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = {
      onPick(if (!monthly) p.copy(jy = p.jy - 1) else if (p.jm == 1) p.copy(jy = p.jy - 1, jm = 12) else p.copy(jm = p.jm - 1))
    }) { Icon(Icons.Filled.ChevronRight, contentDescription = StrSimpleAsk.prev) }
    val months = I18n.solarMonths(I18n.lang, LocaleChoice.months)
    Text(
      if (monthly) "${months.getOrElse(p.jm - 1) { "" }} ${p.jy}" else StrSimpleAsk.year(p.jy),
      style = MaterialTheme.typography.titleSmall, color = Shop.colors.text,
    )
    IconButton(onClick = {
      onPick(if (!monthly) p.copy(jy = p.jy + 1) else if (p.jm == 12) p.copy(jy = p.jy + 1, jm = 1) else p.copy(jm = p.jm + 1))
    }) { Icon(Icons.Filled.ChevronLeft, contentDescription = StrSimpleAsk.next) }
  }
}

/** نام از فهرستِ خودِ دفتر — نوشتن فقط فهرست را تنگ می‌کند؛ پرسش با نامی که در فهرست نیست ساخته نمی‌شود */
@Composable
private fun NamePick(label: String, options: List<SimpleAsk.Named>, selected: String?, onPick: (String) -> Unit) {
  var filter by remember(label) { mutableStateOf("") }
  val chosen = options.firstOrNull { it.id == selected }?.name
  Text("$label: ${chosen ?: "—"}", style = MaterialTheme.typography.bodyMedium, color = if (chosen == null) Shop.colors.warning else Shop.colors.text)
  OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(StrSimpleAsk.search) })
  val key = SimpleAsk.normalize(filter)
  val shown = options.filter { key.isEmpty() || SimpleAsk.normalize(it.name).contains(key) }.take(8)
  if (shown.isEmpty()) EmptyNote(StrSimpleAsk.none)
  shown.forEach { o ->
    Text(
      o.name,
      Modifier.fillMaxWidth().clickable { onPick(o.id); filter = "" }.padding(vertical = 6.dp),
      color = if (o.id == selected) Shop.colors.primary else Shop.colors.text,
      fontWeight = if (o.id == selected) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

@Composable
private fun AnswerCard(outcome: SimpleAskEngine.Outcome) {
  when (outcome) {
    is SimpleAskEngine.Outcome.Rejected -> Panel(Modifier.fillMaxWidth()) {
      outcome.issues.forEach { Text(issueText(it), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.danger) }
    }
    is SimpleAskEngine.Outcome.Ok -> Panel(Modifier.fillMaxWidth()) {
      val a = outcome.answer
      val from = a.from
      val to = a.to
      Text(
        when {
          from == null || to == null -> StrSimpleAsk.now
          a.query.period?.span == Span.ALL -> StrSimpleAsk.allDates
          else -> StrSimpleAsk.range(formatDate(from), formatDate(to))
        },
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
      )
      Spacer(Modifier.height(6.dp))
      if (a.values.isEmpty()) EmptyNote(StrSimpleAsk.none)
      a.values.forEach { v -> ValueRow(v.name ?: valueLabel(v.key), valueText(v), strong = v === a.values.first()) }
      a.status?.let { s ->
        val (label, tint) = when (s) {
          "out" -> StrSimpleAsk.stOut to Shop.colors.danger
          "low" -> StrSimpleAsk.stLow to Shop.colors.warning
          else -> StrSimpleAsk.stOk to Shop.colors.success
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
      }
      if (a.names.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        a.names.take(10).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text) }
        if (a.names.size > 10) Text(StrSimpleAsk.more(a.names.size - 10), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      }
      Spacer(Modifier.height(10.dp))
      Text(StrSimpleAsk.formula, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Shop.colors.muted)
      Text(simpleAskFormula(a.query.template), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
  }
}

@Composable
private fun ValueRow(label: String, value: String, strong: Boolean) {
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, modifier = Modifier.weight(1f))
    Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, color = Shop.colors.text)
  }
}
