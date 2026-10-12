package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.CustomerEngine
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  مشتری‌ها و قرض‌داران — کامپیوتر.
 *
 *  خواستهٔ صاحب مخزن: «بیرون حساب‌هاشون مربعی باشه مثل موبایل و داخلش کادری
 *  یا شبیه اکسل ستونی و بردگی تاریخ… و فاکتور یارو هم باشه… هر چیزی که
 *  برمی‌داره دیده بشه… فاکتور هم جدا دیده بشه… کسایی که خرید می‌کنن هم باید
 *  ثبت بشه و فاکتورشونو نشون داده بشه.»
 *
 *  • **بیرون**: کارتِ مربعیِ هر مشتری — مانده (رنگی)، «چند روز است»، تعدادِ
 *    فاکتور، آخرین خرید. بالایش چهار عددِ کل، جست‌وجو، صافی (بدهکار، دیرکرد،
 *    خریدارِ نقدی، بی‌حساب) و مرتب‌سازی. ردیفِ «مشتریِ تازه» همان‌جا.
 *  • **داخل** (کلیک روی کارت): سه زبانه، هر سه جدولِ خط‌دارِ اکسلی:
 *    «صورت‌حساب» (ردیف، تاریخ، شرح، فاکتور، برد، رسید، مانده)،
 *    «فاکتورها» (کلیک ⇐ اقلامِ همان فاکتور کنارش، «نمایش و چاپ» ⇐ فاکتورِ جدا)،
 *    «اقلامِ برداشته» (هر کالایی که برده، با تاریخ و فاکتور).
 *  • F7 دریافت، F6 قرضِ دستی، Ctrl+P چاپ و Ctrl+E اکسلِ **همان زبانه**، Esc برگشت.
 *
 *  همهٔ عددها از `CustomerEngine` — از خودِ دفتر، هیچ عددی جدا ذخیره نمی‌شود.
 */
@Composable
fun DeskCustomersScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
) {
  var openId by remember { mutableStateOf<String?>(null) }
  //  عکس‌های خودکار (`DevShot`، فرمانِ cust:first) ⇐ پروندهٔ بدهکارترین مشتری
  val shotOpen = DeskCustomersShot.open.value
  LaunchedEffect(shotOpen) {
    if (shotOpen == "first") openId = CustomerEngine.summaries(d).maxByOrNull { it.balance }?.id
    else if (shotOpen == "close") openId = null
  }
  val open = openId?.let { id -> d.debtors.find { it.id == id } }
  if (open == null) CustomerCards(store, d, snackbar) { openId = it }
  else CustomerFile(store, d, snackbar, open.id) { openId = null }
}

/** فقط برای عکس‌های خودکارِ CI (`desktop/.../DevShot.kt`) — در کارِ عادی دست نمی‌خورد */
object DeskCustomersShot {
  val open = mutableStateOf<String?>(null)
  val tab = mutableStateOf(-1)
}

/* ══════════════════════════ بیرون: کارت‌ها ══════════════════════════ */

private enum class Filter(val label: String) { ALL("همه"), DEBT("بدهکار"), LATE("دیرکرد +۶۰ روز"), BUYERS("خریدار"), CLEAR("بی‌حساب") }
private enum class Sort(val label: String) { DEBT("بیشترین بدهی"), RECENT("تازه‌ترین خرید"), BOUGHT("بیشترین خرید"), NAME("نام") }

@Composable
private fun CustomerCards(store: ShopStore, d: ShopData, snackbar: SnackbarHostState, onOpen: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  var search by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf(Filter.ALL) }
  var sort by remember { mutableStateOf(Sort.DEBT) }
  var sortMenu by remember { mutableStateOf(false) }

  val all = remember(d) { CustomerEngine.summaries(d) }
  val list = remember(all, search, filter, sort) {
    val q = search.trim()
    all.filter { q.isEmpty() || it.name.contains(q, true) || it.phone.contains(q) }
      .filter {
        when (filter) {
          Filter.ALL -> true
          Filter.DEBT -> it.balance > 0.5
          Filter.LATE -> (it.debtDays ?: 0) > 60
          Filter.BUYERS -> it.invoices > 0
          Filter.CLEAR -> it.balance in -0.5..0.5
        }
      }
      .sortedWith(
        when (sort) {
          Sort.DEBT -> compareByDescending<CustomerEngine.Summary> { it.balance }.thenBy { it.name }
          Sort.RECENT -> compareByDescending<CustomerEngine.Summary> { it.lastPurchase ?: "" }.thenBy { it.name }
          Sort.BOUGHT -> compareByDescending<CustomerEngine.Summary> { it.bought }.thenBy { it.name }
          Sort.NAME -> compareBy { it.name }
        },
      )
  }
  val month = ir.vil3ntec.tohid.todayIso().take(7)
  val totalDebt = all.sumOf { it.balance.coerceAtLeast(0.0) }
  val debtors = all.count { it.balance > 0.5 }
  val late = all.count { (it.debtDays ?: 0) > 60 }
  val monthBought = remember(d, month) {
    d.sales.filter { it.debtorId != null && it.status != "cancelled" && it.date.startsWith(month) }.sumOf { it.finalTotal }
  }

  fun excel() {
    val rows = mutableListOf(listOf("نام", "تلفن", "مانده", "روزِ بدهی", "تعدادِ فاکتور", "جمعِ خرید", "آخرین خرید", "آخرین پرداخت"))
    list.forEach { s ->
      rows += listOf(s.name, s.phone, Math.round(s.balance).toString(), s.debtDays?.toString().orEmpty(), s.invoices.toString(),
        Math.round(s.bought).toString(), s.lastPurchase.orEmpty(), s.lastPaid.orEmpty())
    }
    val sheet = ReportCsv.Sheet("مشتری‌ها-${ir.vil3ntec.tohid.todayIso()}", rows)
    Platform.saveFile("${sheet.name}.xlsx", Xlsx.of(sheet))?.let { toast("ذخیره شد: $it") }
  }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.E) { excel(); true } else false
      }
      .padding(16.dp),
  ) {
    //  چهار عددِ کل
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Kpi("طلبِ کل", "${money(totalDebt)} ؋", Shop.colors.danger, Modifier.weight(1f))
      Kpi("بدهکاران", "$debtors نفر", Color(0xFFFF9800), Modifier.weight(1f))
      Kpi("دیرکرد بیش از ۶۰ روز", "$late نفر", if (late > 0) Shop.colors.danger else Shop.colors.success, Modifier.weight(1f))
      Kpi("خریدِ مشتری‌ها در این ماه", "${money(monthBought)} ؋", Shop.colors.primary, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))

    //  نوارِ ابزار
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = search, onValueChange = { search = it }, singleLine = true,
        placeholder = { Text("جست‌وجوی نام یا تلفن") }, modifier = Modifier.width(280.dp),
      )
      Spacer(Modifier.width(8.dp))
      Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
        Filter.values().forEach { f ->
          FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
          Spacer(Modifier.width(6.dp))
        }
      }
      Box {
        OutlinedButton(onClick = { sortMenu = true }) { Text("مرتب: ${sort.label}") }
        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
          Sort.values().forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortMenu = false }) }
        }
      }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = ::excel) { Text("اکسل  Ctrl+E") }
    }
    Spacer(Modifier.height(8.dp))
    NewCustomerRow(store, d, ::toast) { onOpen(it) }
    Spacer(Modifier.height(4.dp))
    Text(
      "${list.size} از ${all.size} مشتری — روی کارت بزنید تا صورت‌حساب، فاکتورها و اقلامش باز شود.",
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
    )
    Spacer(Modifier.height(8.dp))

    if (list.isEmpty()) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (all.isEmpty()) "هنوز مشتری‌ای نیست — نام را در «مشتریِ تازه» بنویسید و Enter بزنید." else "با این صافی کسی نیست.", color = Shop.colors.muted)
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(210.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        items(list, key = { it.id }) { s -> CustomerCard(s) { onOpen(s.id) } }
      }
    }
  }
}

@Composable
private fun Kpi(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
  Column(
    modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Shop.colors.surface)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(12.dp))
      .padding(14.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
    Spacer(Modifier.height(4.dp))
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
  }
}

@Composable
private fun stateColor(balance: Double) = when {
  balance > 0.5 -> Shop.colors.danger
  balance < -0.5 -> Shop.colors.success
  else -> Shop.colors.muted
}

/** کارتِ مربعیِ هر مشتری */
@Composable
private fun CustomerCard(s: CustomerEngine.Summary, onClick: () -> Unit) {
  val tint = stateColor(s.balance)
  Column(
    Modifier
      .aspectRatio(1f)
      .clip(RoundedCornerShape(14.dp))
      .background(Shop.colors.surface)
      .border(1.dp, if (s.balance > 0.5) tint.copy(alpha = 0.45f) else Shop.colors.border, RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Text(s.name.trim().take(1).ifBlank { "؟" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint)
      }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Text(s.phone.ifBlank { "بی تلفن" }, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }
    }
    Spacer(Modifier.weight(1f))
    Text(
      when { s.balance > 0.5 -> "بدهکار"; s.balance < -0.5 -> "طلبکار (پیش‌پرداخت)"; else -> "بی‌حساب" },
      style = MaterialTheme.typography.labelMedium, color = tint,
    )
    Text("${money(kotlin.math.abs(s.balance))} ؋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
    Spacer(Modifier.weight(1f))
    HorizontalDivider(color = Shop.colors.border)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("${s.invoices} فاکتور · ${money(s.bought)} ؋", style = MaterialTheme.typography.labelSmall, color = Shop.colors.text, maxLines = 1)
        Text(s.lastPurchase?.let { "آخرین خرید ${formatDate(it)}" } ?: "هنوز خریدی نیست", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, maxLines = 1)
      }
      s.debtDays?.let { days ->
        val c = when { days > 60 -> Shop.colors.danger; days > 30 -> Color(0xFFFF9800); else -> Shop.colors.muted }
        Text(
          "$days روز",
          Modifier.clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
          style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c,
        )
      }
    }
  }
}

/* ══════════════════════════ داخل: پروندهٔ مشتری ══════════════════════════ */

private enum class FileTab(val label: String) { LEDGER("صورت‌حساب"), INVOICES("فاکتورها"), ITEMS("اقلامِ برداشته") }

@Composable
private fun CustomerFile(store: ShopStore, d: ShopData, snackbar: SnackbarHostState, id: String, onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  val debtor = d.debtors.first { it.id == id }
  val s = remember(d, id) { CustomerEngine.summary(d, debtor) }
  val today = ir.vil3ntec.tohid.todayIso()

  var tab by remember { mutableStateOf(FileTab.LEDGER) }
  var entry by remember { mutableStateOf<DebtorEngine.Kind?>(null) }
  var picked by remember { mutableStateOf<String?>(null) }
  var showInvoice by remember { mutableStateOf<Sale?>(null) }
  val shotTab = DeskCustomersShot.tab.value
  LaunchedEffect(shotTab) { FileTab.values().getOrNull(shotTab)?.let { tab = it } }

  fun sheet(): ReportCsv.Sheet = when (tab) {
    FileTab.LEDGER -> CustomerEngine.ledgerSheet(d, debtor, today)
    FileTab.INVOICES -> CustomerEngine.invoicesSheet(d, debtor, today)
    FileTab.ITEMS -> CustomerEngine.itemsSheet(d, debtor, today)
  }
  fun print() { sheet().let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } }
  fun excel() { val sh = sheet(); Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast("ذخیره شد: $it") } }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          e.key == Key.F7 -> { entry = DebtorEngine.Kind.RECEIVE; true }
          e.key == Key.F6 -> { entry = DebtorEngine.Kind.GIVE; true }
          e.isCtrlPressed && e.key == Key.P -> { print(); true }
          e.isCtrlPressed && e.key == Key.E -> { excel(); true }
          e.key == Key.Escape && entry == null && showInvoice == null -> { onBack(); true }
          else -> false
        }
      }
      .padding(16.dp),
  ) {
    //  سرِ پرونده
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowForward, "برگشت به کارت‌ها", tint = Shop.colors.text) }
      val tint = stateColor(s.balance)
      Box(Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Text(debtor.name.trim().take(1).ifBlank { "؟" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(debtor.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Text(listOf(debtor.phone, DebtorEngine.stateText(s.balance), debtor.notes).filter { it.isNotBlank() }.joinToString(" · "),
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      }
      Text("Esc برگشت", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Kpi("مانده", "${money(s.balance)} ؋", stateColor(s.balance), Modifier.weight(1f))
      Kpi("جمعِ خرید", "${money(s.bought)} ؋", Shop.colors.primary, Modifier.weight(1f))
      Kpi("فاکتورها", "${s.invoices}", Shop.colors.text, Modifier.weight(1f))
      Kpi("آخرین خرید", s.lastPurchase?.let { formatDate(it) } ?: "—", Shop.colors.text, Modifier.weight(1f))
      Kpi("آخرین پرداخت", s.lastPaid?.let { formatDate(it) } ?: "—", Shop.colors.success, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Button(onClick = { entry = DebtorEngine.Kind.RECEIVE }) { Text("دریافتِ وجه  F7") }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = { entry = DebtorEngine.Kind.GIVE }) { Text("قرضِ دستی  F6") }
      Spacer(Modifier.weight(1f))
      OutlinedButton(onClick = ::print) { Text("چاپِ «${tab.label}»  Ctrl+P") }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = ::excel) { Text("اکسل  Ctrl+E") }
    }
    entry?.let { kind ->
      Spacer(Modifier.height(8.dp))
      key(id, kind) {
        EntryRow(kind, onCancel = { entry = null }) { amount, notes ->
          when (val r = DebtorEngine.addTransaction(d, id, kind, amount, today, notes, today, System.currentTimeMillis(), ::newId)) {
            is DebtorEngine.Result.Failed -> toast(r.message)
            is DebtorEngine.Result.Ok -> {
              scope.launch { store.save(r.data) }
              entry = null
              tab = FileTab.LEDGER
              toast(if (kind == DebtorEngine.Kind.RECEIVE) "دریافتِ ${money(amount)} ؋ ثبت شد" else "قرضِ ${money(amount)} ؋ ثبت شد")
            }
          }
        }
      }
    }
    Spacer(Modifier.height(10.dp))

    //  زبانه‌ها
    val invoices = remember(d, id) { CustomerEngine.invoices(d, id) }
    val ledger = remember(d, id) { CustomerEngine.ledger(d, id) }
    val items = remember(d, id) { CustomerEngine.items(d, id) }
    TabRow(selectedTabIndex = tab.ordinal, containerColor = Shop.colors.surface) {
      FileTab.values().forEach { t ->
        val n = when (t) { FileTab.LEDGER -> ledger.size; FileTab.INVOICES -> invoices.size; FileTab.ITEMS -> items.size }
        Tab(selected = tab == t, onClick = { tab = t }, text = { Text("${t.label} ($n)") })
      }
    }
    Spacer(Modifier.height(8.dp))

    when (tab) {
      FileTab.LEDGER -> {
        val cols = listOf(Col("ردیف", 56.dp), Col("تاریخ", 110.dp), Col("شرح"), Col("فاکتور", 90.dp), Col("برد (بدهکار)", 130.dp, true), Col("رسید (بستانکار)", 130.dp, true), Col("مانده", 140.dp, true))
        Sheet(
          cols, ledger.size, Modifier.weight(1f),
          empty = "هنوز ردیفی نیست — قرضِ نسیه و دریافت‌ها این‌جا می‌آیند.",
          cell = { i, c ->
            val l = ledger[i]
            when (c) {
              0 -> Cell(l.row.toString(), Shop.colors.muted)
              1 -> Cell(formatDate(l.tx.date))
              2 -> Cell(l.tx.notes.ifBlank { if (l.give > 0) "برد" else "رسید" })
              3 -> Cell(l.invoiceNumber?.let { "#$it" }.orEmpty(), Shop.colors.primary, link = l.saleId != null)
              4 -> Cell(if (l.give > 0) money(l.give) else "", Shop.colors.danger)
              5 -> Cell(if (l.receive > 0) money(l.receive) else "", Shop.colors.success)
              else -> Cell(money(l.balance), bold = true)
            }
          },
          onClick = { i -> ledger[i].saleId?.let { picked = it; tab = FileTab.INVOICES } },
          footer = listOf("", "", "جمع", "", money(ledger.sumOf { it.give }), money(ledger.sumOf { it.receive }), money(s.balance)),
        )
        Text("اشتباه پاک نمی‌شود: با ردیفِ مخالف («دریافت» یا «قرضِ دستی») و شرحِ «اصلاحِ ردیفِ …» درست کنید تا ردش بماند. روی شمارهٔ فاکتور بزنید تا خودِ فاکتور باز شود.",
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }

      FileTab.INVOICES -> Row(Modifier.weight(1f)) {
        //  زبانه باز شد و فاکتوری انتخاب نشده ⇐ تازه‌ترین، تا اقلامش همان‌جا دیده شود
        LaunchedEffect(invoices) { if (picked == null || invoices.none { it.id == picked }) picked = invoices.firstOrNull()?.id }
        val counts = remember(d) { d.saleItems.groupingBy { it.saleId }.eachCount() }
        val cols = listOf(Col("فاکتور", 80.dp), Col("تاریخ", 110.dp), Col("اقلام", 60.dp, true), Col("جمع", 0.dp, true), Col("قابلِ پرداخت", 0.dp, true), Col("پرداخت‌شده", 0.dp, true), Col("باقی", 0.dp, true), Col("نوع", 70.dp), Col("وضعیت", 80.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
          Sheet(
            cols, invoices.size, Modifier.weight(1f),
            empty = "این مشتری هنوز فاکتوری ندارد. در صندوق با F2 مشتری را روی فاکتور بگذارید — نقدی هم ثبت می‌شود.",
            selected = invoices.indexOfFirst { it.id == picked }.takeIf { it >= 0 },
            cell = { i, c ->
              val v = invoices[i]
              val off = v.status == "cancelled"
              when (c) {
                0 -> Cell("#${v.invoiceNumber ?: "—"}", Shop.colors.primary, bold = true)
                1 -> Cell(formatDate(v.date))
                2 -> Cell((counts[v.id] ?: 0).toString())
                3 -> Cell(money(v.total), if (off) Shop.colors.muted else Shop.colors.text)
                4 -> Cell(money(v.finalTotal), if (off) Shop.colors.muted else Shop.colors.text, bold = true)
                5 -> Cell(money(v.paidAmount), Shop.colors.success)
                6 -> Cell(if (v.remaining > 0.5) money(v.remaining) else "", Shop.colors.danger)
                7 -> Cell(if (v.paymentMethod == "credit") "نسیه" else "نقد")
                else -> Cell(if (off) "لغوشده" else "ثبت‌شده", if (off) Shop.colors.danger else Shop.colors.success)
              }
            },
            onClick = { i -> picked = invoices[i].id },
            footer = listOf("جمع", "", "", money(invoices.filter { it.status != "cancelled" }.sumOf { it.total }),
              money(s.bought), money(invoices.filter { it.status != "cancelled" }.sumOf { it.paidAmount }),
              money(invoices.filter { it.status != "cancelled" }.sumOf { it.remaining }), "", ""),
          )
        }
        //  اقلامِ فاکتورِ انتخاب‌شده — کنارِ جدول
        invoices.find { it.id == picked }?.let { sale ->
          Spacer(Modifier.width(12.dp))
          InvoicePeek(d, sale, Modifier.width(380.dp).fillMaxHeight()) { showInvoice = sale }
        }
      }

      FileTab.ITEMS -> {
        val cols = listOf(Col("تاریخ", 110.dp), Col("فاکتور", 80.dp), Col("کالا"), Col("تعداد", 80.dp, true), Col("فی", 120.dp, true), Col("جمع", 130.dp, true), Col("برگشتی", 80.dp, true))
        Sheet(
          cols, items.size, Modifier.weight(1f),
          empty = "هنوز کالایی نبرده.",
          cell = { i, c ->
            val l = items[i]
            when (c) {
              0 -> Cell(formatDate(l.date))
              1 -> Cell("#${l.invoiceNumber ?: "—"}", Shop.colors.primary, link = true)
              2 -> Cell(l.name)
              3 -> Cell(qty(l.quantity))
              4 -> Cell(money(l.unitPrice))
              5 -> Cell(money(l.total), bold = true)
              else -> Cell(if (l.returned > 0) qty(l.returned) else "", Shop.colors.danger)
            }
          },
          onClick = { i -> picked = items[i].saleId; tab = FileTab.INVOICES },
          footer = listOf("", "", "جمع", "", "", money(items.sumOf { it.total }), ""),
        )
      }
    }
  }

  showInvoice?.let { sale ->
    InvoiceDialog(d = d, sale = sale, onDismiss = { showInvoice = null }, onMessage = ::toast)
  }
}

private fun qty(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

/** اقلامِ یک فاکتور کنارِ جدول، با دکمهٔ «نمایش و چاپ» که فاکتور را جدا باز می‌کند */
@Composable
private fun InvoicePeek(d: ShopData, sale: Sale, modifier: Modifier, onShow: () -> Unit) {
  val lines = remember(d, sale.id) { d.saleItems.filter { it.saleId == sale.id } }
  Column(
    modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Shop.colors.surface)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(12.dp))
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.ReceiptLong, null, tint = Shop.colors.primary)
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Text("فاکتور #${sale.invoiceNumber ?: "—"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Text(listOf(formatDate(sale.date), sale.seller.takeIf { it.isNotBlank() }?.let { "فروشنده: $it" }).filterNotNull().joinToString(" · "),
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }
    }
    Spacer(Modifier.height(10.dp))
    HeadRow(listOf("کالا" to 0.dp, "تعداد" to 56.dp, "جمع" to 100.dp))
    LazyColumn(Modifier.weight(1f).fillMaxWidth().border(1.dp, Shop.colors.border)) {
      itemsIndexed(lines, key = { _, l -> l.id }) { i, l ->
        Row(
          Modifier.fillMaxWidth().background(if (i % 2 == 1) Shop.colors.surface2.copy(alpha = 0.45f) else Shop.colors.surface).padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(SalesEngine.itemName(d, l), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
            Text("فی ${money(l.unitPrice)}" + if (l.returnedQty > 0) " · برگشتی ${qty(l.returnedQty)}" else "", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          }
          Text(qty(l.quantity), Modifier.width(56.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
          Text(money(l.totalPrice), Modifier.width(100.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    @Composable fun line(k: String, v: String, c: Color = Shop.colors.text, b: Boolean = false) =
      Row { Text(k, Modifier.weight(1f), color = Shop.colors.muted, style = MaterialTheme.typography.bodySmall); Text(v, color = c, fontWeight = if (b) FontWeight.Bold else null) }
    line("جمع", money(sale.total))
    if (sale.discount > 0) line("تخفیف", money(sale.discount), Shop.colors.success)
    line("قابلِ پرداخت", "${money(sale.finalTotal)} ؋", b = true)
    line("پرداخت‌شده", money(sale.paidAmount), Shop.colors.success)
    if (sale.remaining > 0.5) line("باقی (به حساب)", money(sale.remaining), Shop.colors.danger, true)
    if (sale.status == "cancelled") Text("لغوشده — ${sale.cancelReason.ifBlank { "بی دلیل" }}", color = Shop.colors.danger, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(10.dp))
    Button(onClick = onShow, modifier = Modifier.fillMaxWidth()) { Text("نمایش و چاپِ فاکتور") }
  }
}

/* ══════════════════════════ جدولِ اکسلی ══════════════════════════ */

/** ستون — پهنای ۰ یعنی «باقی‌مانده»؛ `end` ⇒ عدد، راست‌چینِ انتها */
private data class Col(val title: String, val width: Dp = 0.dp, val end: Boolean = false)
private data class Cell(val text: String, val color: Color? = null, val bold: Boolean = false, val link: Boolean = false)

/**
 *  جدولِ خط‌دار مثلِ اکسل: سرستونِ پررنگ، خطِ عمودی میانِ ستون‌ها، ردیف‌های
 *  یکی‌درمیان، ردیفِ جمع در پایین. کلیک روی ردیف ⇐ `onClick`.
 */
@Composable
private fun Sheet(
  cols: List<Col>,
  count: Int,
  modifier: Modifier,
  empty: String,
  cell: @Composable (Int, Int) -> Cell,
  onClick: (Int) -> Unit = {},
  footer: List<String>? = null,
  selected: Int? = null,
) {
  val line = Shop.colors.border
  @Composable fun RowScope.box(c: Col, content: @Composable () -> Unit) {
    Box(
      (if (c.width == 0.dp) Modifier.weight(1f) else Modifier.width(c.width))
        .fillMaxHeight()
        .border(0.5.dp, line)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      contentAlignment = if (c.end) Alignment.CenterEnd else Alignment.CenterStart,
    ) { content() }
  }
  Column(modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, line, RoundedCornerShape(8.dp))) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Shop.colors.surface2)) {
      cols.forEach { c -> box(c) { Text(c.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1) } }
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
      if (count == 0) item {
        Text(empty, Modifier.fillMaxWidth().padding(28.dp), color = Shop.colors.muted, textAlign = TextAlign.Center)
      }
      items(count) { i ->
        val bg = when {
          i == selected -> Shop.colors.primary.copy(alpha = 0.14f)
          i % 2 == 1 -> Shop.colors.surface2.copy(alpha = 0.4f)
          else -> Shop.colors.surface
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(bg).clickable { onClick(i) }) {
          cols.forEachIndexed { ci, c ->
            val v = cell(i, ci)
            box(c) {
              Text(
                v.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (v.bold) FontWeight.SemiBold else null,
                color = v.color ?: Shop.colors.text,
                textDecoration = if (v.link) androidx.compose.ui.text.style.TextDecoration.Underline else null,
              )
            }
          }
        }
      }
    }
    footer?.let { f ->
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Shop.colors.surface2)) {
        cols.forEachIndexed { ci, c -> box(c) { Text(f.getOrElse(ci) { "" }, fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1) } }
      }
    }
  }
}

@Composable
internal fun HeadRow(cols: List<Pair<String, Dp>>) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Shop.colors.surface2).padding(horizontal = 8.dp, vertical = 7.dp),
  ) {
    cols.forEachIndexed { i, (t, w) ->
      Text(
        t, if (w == 0.dp) Modifier.weight(1f) else Modifier.width(w),
        style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted,
        textAlign = if (i > 0 && w != 0.dp) TextAlign.End else TextAlign.Start,
      )
    }
  }
}

@Composable
private fun NewCustomerRow(store: ShopStore, d: ShopData, toast: (String) -> Unit, onAdded: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  fun save() {
    when (val r = DebtorEngine.add(d, DebtorEngine.DebtorDraft(name = name, phone = phone), System.currentTimeMillis(), ::newId)) {
      is DebtorEngine.Result.Failed -> toast(r.message)
      is DebtorEngine.Result.Ok -> { scope.launch { store.save(r.data) }; onAdded(r.id); name = ""; phone = "" }
    }
  }
  val enter = Modifier.onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { save(); true } else false
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(name, { name = it }, label = { Text("مشتریِ تازه — نام") }, singleLine = true, modifier = Modifier.width(280.dp).then(enter))
    Spacer(Modifier.width(6.dp))
    OutlinedTextField(phone, { phone = it }, label = { Text("تلفن") }, singleLine = true, modifier = Modifier.width(170.dp).then(enter))
    Spacer(Modifier.width(6.dp))
    Button(onClick = ::save, enabled = name.isNotBlank()) { Text("افزودن ↵") }
  }
}

/** ردیفِ ثبتِ دریافت/قرض — مبلغ، شرح، Enter */
@Composable
private fun EntryRow(kind: DebtorEngine.Kind, onCancel: () -> Unit, onSave: (Double, String) -> Unit) {
  var amount by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  fun go() { amount.toDoubleOrNull()?.let { onSave(it, notes.trim()) } }
  val keys = Modifier.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (e.key) {
      Key.Enter, Key.NumPadEnter -> { go(); true }
      Key.Escape -> { onCancel(); true }
      else -> false
    }
  }
  val receive = kind == DebtorEngine.Kind.RECEIVE
  Surface(
    color = (if (receive) Shop.colors.success else Shop.colors.danger).copy(alpha = 0.08f),
    shape = RoundedCornerShape(8.dp),
  ) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(if (receive) "دریافتِ وجه" else "قرضِ دستی", style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
      Spacer(Modifier.width(8.dp))
      OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("مبلغ (؋)") }, singleLine = true, modifier = Modifier.width(160.dp).focusRequester(focus).then(keys))
      Spacer(Modifier.width(6.dp))
      OutlinedTextField(notes, { notes = it }, label = { Text("شرح") }, singleLine = true, modifier = Modifier.weight(1f).then(keys))
      Spacer(Modifier.width(6.dp))
      Button(onClick = ::go, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0) { Text("ثبت ↵") }
      TextButton(onClick = onCancel) { Text("Esc") }
    }
  }
}
