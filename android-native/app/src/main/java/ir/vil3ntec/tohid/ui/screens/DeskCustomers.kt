package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.StrDebtor
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.data.isOnlineOrder
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
import androidx.compose.foundation.verticalScroll
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
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.CustomerEngine
import ir.vil3ntec.tohid.data.CustomerProfileForm
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtEntry
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.softCard
import ir.vil3ntec.tohid.ui.theme.tabular
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
fun DeskCustomersScreen(store: ShopStore, d: ShopData, snackbar: SnackbarHostState, onOpenOrder: ((String) -> Unit)? = null) { // CRM-07: سفارشِ پرونده ⇐ `orders/{id}`
  var openId by remember { mutableStateOf<String?>(null) }
  //  عکس‌های خودکار (`DevShot`، فرمانِ cust:first) ⇐ پروندهٔ بدهکارترین مشتری
  val shotOpen = DeskCustomersShot.open.value
  LaunchedEffect(shotOpen) {
    if (shotOpen == "first") openId = CustomerEngine.summaries(d).maxByOrNull { it.balance }?.id
    else if (shotOpen == "close") openId = null
  }
  val open = openId?.let { id -> d.debtors.find { it.id == id } }
  if (open == null) CustomerCards(store, d, snackbar) { openId = it }
  else CustomerFile(store, d, snackbar, open.id, onOpenOrder) { openId = null }
}

/** فقط برای عکس‌های خودکارِ CI (`desktop/.../DevShot.kt`) — در کارِ عادی دست نمی‌خورد */
object DeskCustomersShot {
  val open = mutableStateOf<String?>(null)
  val tab = mutableStateOf(-1)
}

/* ══════════════════════════ بیرون: کارت‌ها ══════════════════════════ */

private enum class Filter(private val text: () -> String) { ALL({ ir.vil3ntec.tohid.core.i18n.Str.all }), DEBT({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.owing }), LATE({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lateFilter }), BUYERS({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.buyers }), CLEAR({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noBalance }); val label: String get() = text() }
private enum class Sort(private val text: () -> String) { DEBT({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.sortMostOwed }), RECENT({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.sortLatest }), BOUGHT({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.sortMostBought }), NAME({ ir.vil3ntec.tohid.core.i18n.StrGrid.name }); val label: String get() = text() }

@Composable
private fun CustomerCards(store: ShopStore, d: ShopData, snackbar: SnackbarHostState, onOpen: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  var search by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf(Filter.ALL) }
  var sort by remember { mutableStateOf(Sort.DEBT) }
  var sortMenu by remember { mutableStateOf(false) }
  //  نمای کارتی/جدولی (VISUAL-DIRECTION §۱٫۵) — پیش‌فرضِ مدیریت جدول؛ جست‌وجو،
  //  صافی و ترتیب با عوض‌شدنِ نما دست نمی‌خورند
  val view = rememberViewMode("customers", ScreenKind.MANAGEMENT)
  //  پروندهٔ CRM (CRM-02/10/11) — فقط با «گزارش‌های مشتری» روشن (`ShopPolicy.crmReports`، پیش‌فرض خاموش)
  val crm = ShopPolicy.crmReports(context)
  var tagFilter by remember { mutableStateOf<String?>(null) }
  var mergeOpen by remember { mutableStateOf(false) }

  //  ادغام‌شده‌ها بیرون (CRM-10)؛ با CRM روشن جست‌وجو روی هر دو شماره و برچسب و صافیِ برچسب
  val all = remember(d) { CustomerCrm.active(d).map { it.id }.toSet().let { on -> CustomerEngine.summaries(d).filter { it.id in on } } }
  val list = remember(all, search, filter, sort, tagFilter, crm) {
    val shown = CustomerProfileForm.visible(d, search, tagFilter, crm).map { it.id }.toSet()
    all.filter { it.id in shown }
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
    val rows = mutableListOf(listOf("نام", "تلفن", "مانده", "روزِ بدهی", "تعدادِ فاکتور", "جمعِ خرید", "آخرین خرید", "آخرین پرداخت")) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
    list.forEach { s ->
      rows += listOf(s.name, s.phone, Math.round(s.balance).toString(), s.debtDays?.toString().orEmpty(), s.invoices.toString(),
        Math.round(s.bought).toString(), s.lastPurchase.orEmpty(), s.lastPaid.orEmpty())
    }
    val sheet = ReportCsv.Sheet("مشتری‌ها-${ir.vil3ntec.tohid.todayIso()}", rows) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
    Platform.saveFile("${sheet.name}.xlsx", Xlsx.of(sheet))?.let { toast(ir.vil3ntec.tohid.core.i18n.StrSale.savedTo(it)) }
  }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.isPrimaryModifier && e.key == Key.E) { excel(); true } else false
      }
      .padding(16.dp),
  ) {
    //  چهار عددِ کل
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      DeskKpi(ir.vil3ntec.tohid.core.i18n.StrReports.totalReceivable, "${money(totalDebt)} ؋", Shop.colors.danger, Modifier.weight(1f))
      DeskKpi(ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vDebtors, ir.vil3ntec.tohid.core.i18n.StrHome.people(debtors), Shop.colors.warning, Modifier.weight(1f))
      DeskKpi(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lateKpi, ir.vil3ntec.tohid.core.i18n.StrHome.people(late), if (late > 0) Shop.colors.danger else Shop.colors.success, Modifier.weight(1f))
      DeskKpi(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.monthKpi, "${money(monthBought)} ؋", Shop.colors.primary, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))

    //  نوارِ ابزار
    Row(verticalAlignment = Alignment.CenterVertically) {
      //  نسخهٔ ۲: همان کادرِ جست‌وجوی مشترکِ فهرست‌ها (`SearchBox`) — گرد، نرم، با ×
      ir.vil3ntec.tohid.ui.components.common.SearchBox(search, { search = it }, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.searchHint, Modifier.width(280.dp))
      Spacer(Modifier.width(8.dp))
      Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
        Filter.values().forEach { f ->
          FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
          Spacer(Modifier.width(6.dp))
        }
      }
      Box {
        OutlinedButton(onClick = { sortMenu = true }) { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.sortBy(sort.label)) }
        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
          Sort.values().forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortMenu = false }) }
        }
      }
      Spacer(Modifier.width(6.dp))
      ViewModeToggle(view)
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = ::excel) { Text(ir.vil3ntec.tohid.core.i18n.StrInvoices.excelKey) }
    }
    Spacer(Modifier.height(8.dp))
    if (crm) CrmListTools(d, tagFilter, { tagFilter = it }, onMerge = { mergeOpen = true })
    NewCustomerRow(store, d, ::toast) { onOpen(it) }
    Spacer(Modifier.height(4.dp))
    Text(
      ir.vil3ntec.tohid.core.i18n.StrCustomerPages.listHint(list.size, all.size, if (view.mode == ViewMode.TABLE) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.rowWord else ir.vil3ntec.tohid.core.i18n.StrCustomerPages.cardWord),
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
    )
    Spacer(Modifier.height(8.dp))

    if (list.isEmpty()) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (all.isEmpty()) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noneYet else ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noneMatch, color = Shop.colors.muted)
      }
    } else if (view.mode == ViewMode.TABLE) {
      //  همان فهرست، همان صافی و ترتیب — جدولِ خط‌دار مثلِ داخلِ پرونده
      val cols = listOf(
        //  ستونِ نام اولویت دارد: ستون‌های ثابت به‌اندازهٔ محتوا (بازبینیِ ۱۴۰۵/۰۷/۲۷)
        TCol(ir.vil3ntec.tohid.core.i18n.StrGrid.name), TCol(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.phone, 118.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vDebtRemaining, 104.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.debtDays, 76.dp, true),
        TCol(ir.vil3ntec.tohid.core.i18n.Str.invoices, 70.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vPurchaseTotal, 104.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrPriceCheck.lastPurchase, 98.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lastPaid, 98.dp),
      )
      DeskTable(
        cols, list.size, Modifier.weight(1f),
        empty = "",
        cell = { i, c ->
          val r = list[i]
          when (c) {
            0 -> TCell(r.name, bold = true)
            1 -> TCell(ir.vil3ntec.tohid.ui.components.customerPhone(r.phone, ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts()), Shop.colors.muted)
            2 -> TCell(if (kotlin.math.abs(r.balance) < 0.5) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.settled else money(r.balance), stateColor(r.balance), bold = true)
            3 -> TCell(r.debtDays?.toString().orEmpty(), if ((r.debtDays ?: 0) > 60) Shop.colors.danger else Shop.colors.muted)
            4 -> TCell(r.invoices.toString())
            5 -> TCell(money(r.bought))
            6 -> TCell(r.lastPurchase?.let { formatDate(it) } ?: "—", Shop.colors.muted)
            else -> TCell(r.lastPaid?.let { formatDate(it) } ?: "—", Shop.colors.success)
          }
        },
        onClick = { i -> onOpen(list[i].id) },
        footer = listOf(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.totalOf(list.size), "", money(list.sumOf { it.balance }), "", list.sumOf { it.invoices }.toString(), money(list.sumOf { it.bought }), "", ""),
      )
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
  if (mergeOpen && crm) CustomerMergeDialog(d, store, onDismiss = { mergeOpen = false }, toast = ::toast)
}

@Composable
internal fun DeskKpi(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
  //  نسخهٔ ۲: کارتِ نرم (سایه، گوشهٔ ۱۶)، نقطهٔ رنگیِ معنا کنارِ عنوان و عددِ درشتِ هم‌پهنا
  Column(
    modifier
      .softCard(Shop.colors, Shape.card)
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
      Spacer(Modifier.width(8.dp))
      Text(label, style = MaterialTheme.typography.labelLarge, color = Shop.colors.muted, maxLines = 1)
    }
    Spacer(Modifier.height(8.dp))
    Text(value, style = MaterialTheme.typography.headlineSmall.tabular(), fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
  }
}

@Composable
internal fun stateColor(balance: Double) = when {
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
      //  نسخهٔ ۲: کارتِ نرم؛ بدهکار را نقطه و رنگِ مانده نشان می‌دهد نه خطِ دورِ رنگی
      .softCard(Shop.colors, Shape.card)
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
        Text(ir.vil3ntec.tohid.ui.components.customerPhone(s.phone, ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts()).ifBlank { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noPhone }, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }
    }
    Spacer(Modifier.weight(1f))
    Text(
      when { s.balance > 0.5 -> ir.vil3ntec.tohid.core.i18n.StrCustomerPages.owing; s.balance < -0.5 -> ir.vil3ntec.tohid.core.i18n.StrCustomerPages.inCredit; else -> ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noBalance },
      style = MaterialTheme.typography.labelMedium, color = tint,
    )
    Text("${money(kotlin.math.abs(s.balance))} ؋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
    Spacer(Modifier.weight(1f))
    HorizontalDivider(color = Shop.colors.border)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.cardStats(s.invoices, money(s.bought)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.text, maxLines = 1)
        Text(s.lastPurchase?.let { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lastBoughtOn(formatDate(it)) } ?: ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noPurchases, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, maxLines = 1)
      }
      s.debtDays?.let { days ->
        val c = when { days > 60 -> Shop.colors.danger; days > 30 -> Shop.colors.warning; else -> Shop.colors.muted }
        Text(
          ir.vil3ntec.tohid.core.i18n.Str.days(days),
          Modifier.clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
          style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c,
        )
      }
    }
  }
}

/* ══════════════════════════ داخل: پروندهٔ مشتری ══════════════════════════ */

private enum class FileTab(private val text: () -> String) { LEDGER({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.tabStatement }), INVOICES({ ir.vil3ntec.tohid.core.i18n.Str.invoices }), ITEMS({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.tabItems }); val label: String get() = text() }

@Composable
private fun CustomerFile(store: ShopStore, d: ShopData, snackbar: SnackbarHostState, id: String, onOpenOrder: ((String) -> Unit)?, onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  val debtor = d.debtors.first { it.id == id }
  val s = remember(d, id) { CustomerEngine.summary(d, debtor) }
  val today = ir.vil3ntec.tohid.todayIso()

  var tab by remember { mutableStateOf(FileTab.LEDGER) }
  var entry by remember { mutableStateOf<DebtorEngine.Kind?>(null) }
  val entryKey = remember(entry) { newId() }  // شناسهٔ ردیفِ قرض، یکی برای هر ورود ⇒ دو Enter یا تأییدِ «بیش از سقف» یک ردیف
  var picked by remember { mutableStateOf<String?>(null) }
  var showInvoice by remember { mutableStateOf<Sale?>(null) }
  //  دفترِ قرض (v3 §22.2-A5): ردیفِ انتخاب‌شدهٔ صورت‌حساب، رسید، سررسید، سقفِ اعتبار
  val book = remember(d, id) { DebtBook.book(d, id, today) }
  var pickedTx by remember { mutableStateOf<String?>(null) }
  var receiptFor by remember { mutableStateOf<String?>(null) }
  var dueFor by remember { mutableStateOf<String?>(null) }
  var limitOpen by remember { mutableStateOf(false) }
  var overLimit by remember { mutableStateOf<Triple<Double, String, String?>?>(null) }
  val crm = ShopPolicy.crmReports(context)
  var profileOpen by remember { mutableStateOf(false) }
  fun record(kind: DebtorEngine.Kind, amount: Double, notes: String, due: String?, over: Boolean) {
    val e = DebtEntry.Entry(id, kind, amount, today, notes, due, over)
    val done = if (kind == DebtorEngine.Kind.RECEIVE) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.receivedSaved(money(amount)) else ir.vil3ntec.tohid.core.i18n.StrCustomerPages.creditSaved(money(amount))
    //  ⛔ روی دفترِ همین لحظهٔ دیسک (`DebtEntry`)، نه `d` ِ صفحه: ردیفی که Pull در این فاصله آورده گم نمی‌شود
    scope.launch {
      DebtEntry.record(store, entryKey, e, today, System.currentTimeMillis()).show(::toast, done) { if (entry == kind) entry = null; tab = FileTab.LEDGER }
    }
  }
  val shotTab = DeskCustomersShot.tab.value
  LaunchedEffect(shotTab) { FileTab.values().getOrNull(shotTab)?.let { tab = it } }

  fun sheet(): ReportCsv.Sheet = when (tab) {
    FileTab.LEDGER -> CustomerEngine.ledgerSheet(d, debtor, today)
    FileTab.INVOICES -> CustomerEngine.invoicesSheet(d, debtor, today)
    FileTab.ITEMS -> CustomerEngine.itemsSheet(d, debtor, today)
  }
  //  CRM-15: «چه کسی پرونده را دید» — باز شدن (شمارهٔ کامل در سرِ پرونده ⇒ «شماره») و هر چاپ/اکسل
  val viewed = rememberCustomerAccessNote(store)
  val contacts = ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts()
  LaunchedEffect(id) { viewed(id, ir.vil3ntec.tohid.data.CustomerAccessLog.openKind(contacts, debtor)) }
  fun print() { viewed(id, ir.vil3ntec.tohid.core.crm.CustomerAccess.Kind.EXPORT); sheet().let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } }
  fun excel() {
    val sh = sheet()
    Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { viewed(id, ir.vil3ntec.tohid.core.crm.CustomerAccess.Kind.EXPORT); toast(ir.vil3ntec.tohid.core.i18n.StrSale.savedTo(it)) }
  }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          e.key == Key.F7 -> { entry = DebtorEngine.Kind.RECEIVE; true }
          e.key == Key.F6 -> { entry = DebtorEngine.Kind.GIVE; true }
          e.isPrimaryModifier && e.key == Key.P -> { print(); true }
          e.isPrimaryModifier && e.key == Key.E -> { excel(); true }
          e.key == Key.Escape && entry == null && showInvoice == null && receiptFor == null && dueFor == null && !limitOpen && !profileOpen && overLimit == null -> { onBack(); true }
          else -> false
        }
      }
      .padding(16.dp),
  ) {
    //  سرِ پرونده
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowForward, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.backToCards, tint = Shop.colors.text) }
      val tint = stateColor(s.balance)
      Box(Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Text(debtor.name.trim().take(1).ifBlank { "؟" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(debtor.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Text(listOf(ir.vil3ntec.tohid.ui.components.customerPhone(debtor.phone, ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts()), DebtorEngine.stateText(s.balance), debtor.notes).filter { it.isNotBlank() }.joinToString(" · "),
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        CrmExtraLine(debtor)
      }
      Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.escBack, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      DeskKpi(ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vDebtRemaining, "${money(s.balance)} ؋", stateColor(s.balance), Modifier.weight(1f))
      DeskKpi(ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vPurchaseTotal, "${money(s.bought)} ؋", Shop.colors.primary, Modifier.weight(1f))
      DeskKpi(ir.vil3ntec.tohid.core.i18n.Str.invoices, "${s.invoices}", Shop.colors.text, Modifier.weight(1f))
      DeskKpi(ir.vil3ntec.tohid.core.i18n.StrPriceCheck.lastPurchase, s.lastPurchase?.let { formatDate(it) } ?: "—", Shop.colors.text, Modifier.weight(1f))
      DeskKpi(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lastPaid, s.lastPaid?.let { formatDate(it) } ?: "—", Shop.colors.success, Modifier.weight(1f))
    }
    book?.let {
      Spacer(Modifier.height(8.dp))
      DebtBookBanner(it)
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Button(onClick = { entry = DebtorEngine.Kind.RECEIVE }) { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.receiveKey) }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = { entry = DebtorEngine.Kind.GIVE }) { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.creditKey) }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = { limitOpen = true }) { Text(ir.vil3ntec.tohid.core.i18n.StrExpenses.auditCreditLimitSet) }
      if (crm) { Spacer(Modifier.width(6.dp)); OutlinedButton(onClick = { profileOpen = true }) { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.secondNumber) } }
      Spacer(Modifier.weight(1f))
      OutlinedButton(onClick = ::print) { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.printTab(tab.label)) }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = ::excel) { Text(ir.vil3ntec.tohid.core.i18n.StrInvoices.excelKey) }
      StatementImageButton(d, id, ::toast, Modifier.padding(start = 6.dp))
      CrmPricingButton(store, d, id, ::toast, Modifier.padding(start = 6.dp))
      CustomerPricesButton(store, d, id, ::toast, Modifier.padding(start = 6.dp)) // POS-18: همان دکمهٔ پروندهٔ گوشی، پیش‌فرض خاموش
      CustomerCardButton(store, d, id, ::toast, Modifier.padding(start = 6.dp)) // CRM-16، پیش‌فرض خاموش
      StoreCreditGrantButton(store, d, id, ::toast, Modifier.padding(start = 6.dp)) // PRM-15: همان دکمهٔ پروندهٔ گوشی (`GiftCardPref`)
      CustomerErasureButton(d.debtors.find { it.id == id }?.phone, Modifier.padding(start = 6.dp)) // SEC-10، فقط صاحب و پرچمِ روشن
    }
    entry?.let { kind ->
      Spacer(Modifier.height(8.dp))
      key(id, kind) {
        EntryRow(kind, onCancel = { entry = null }) { amount, notes, due ->
          //  CRM-03: بردِ بیش از سقف فقط با تأییدِ صریح؛ مشتریِ بی سقف همان رفتارِ همیشه
          val credit = if (kind == DebtorEngine.Kind.GIVE) DebtBook.creditCheck(d, id, DebtBook.minor(amount)) else null
          if (credit != null && credit.over) overLimit = Triple(amount, notes, due)
          else record(kind, amount, notes, due, over = false)
        }
      }
    }
    //  PRM-13: مانده و سابقهٔ امتیازِ وفاداری (`LoyaltyPref` خاموش و بی ردیف ⇒ هیچ)
    ir.vil3ntec.tohid.ui.components.pos.LoyaltyHistory(d, id, Modifier.padding(top = 8.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState()), store, ::toast)
    ir.vil3ntec.tohid.ui.components.pos.LayawayCustomerPanel(d, store, id, ::toast) // POS-27: امانی‌های همین مشتری (`LayawayPref`)
    CustomerOnlineOrdersPanel(d, id, Modifier.padding(top = 8.dp), onOpen = onOpenOrder) // CRM-07: سفارش‌های اینترنتیِ باز/لغوشده (بخشِ سفارش و اجازهٔ «تماس»)
    CustomerAccessPanel(d, id, Modifier.padding(top = 8.dp).heightIn(max = 180.dp).verticalScroll(rememberScrollState())) // CRM-15: فقط صاحب و `audit.view`
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
      FileTab.LEDGER -> Row(Modifier.weight(1f)) {
        val cols = listOf(TCol(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.colRow, 56.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colDate, 110.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrReports.note), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice, 90.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.colGive, 130.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.colReceive, 130.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vDebtRemaining, 140.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.colDue, 170.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
          DeskTable(
            cols, ledger.size, Modifier.weight(1f),
            empty = ir.vil3ntec.tohid.core.i18n.StrCustomerPages.ledgerEmpty,
            selected = ledger.indexOfFirst { it.tx.id == pickedTx }.takeIf { it >= 0 },
            cell = { i, c ->
              val l = ledger[i]
              when (c) {
                0 -> TCell(l.row.toString(), Shop.colors.muted)
                1 -> TCell(formatDate(l.tx.date))
                2 -> TCell(l.tx.notes.ifBlank { if (l.give > 0) ir.vil3ntec.tohid.core.i18n.StrMoney.giveTag else ir.vil3ntec.tohid.core.i18n.StrMoney.receiveTag })
                3 -> TCell(l.invoiceLabel?.let { "#$it" }.orEmpty(), Shop.colors.primary, link = l.saleId != null)
                4 -> TCell(if (l.give > 0) money(l.give) else "", Shop.colors.danger)
                5 -> TCell(if (l.receive > 0) money(l.receive) else "", Shop.colors.success)
                6 -> TCell(money(l.balance), bold = true)
                else -> book?.entry(l.tx.id)?.takeIf { it.give && it.amount.minor > 0 }?.let { e ->
                  val due = e.dueDate?.let { formatDate(it) + " · " }.orEmpty()
                  TCell(
                    due + if (e.paid) ir.vil3ntec.tohid.core.i18n.StrMoney.settled else ir.vil3ntec.tohid.core.i18n.StrDebtPages.openAmount(DebtBook.show(e.open)),
                    when { e.overdue -> Shop.colors.danger; e.paid -> Shop.colors.success; else -> Shop.colors.warning },
                  )
                } ?: TCell("")
              }
            },
            onClick = { i -> pickedTx = ledger[i].tx.id },
            footer = listOf("", "", ir.vil3ntec.tohid.core.i18n.StrInvoices.total, "", money(ledger.sumOf { it.give }), money(ledger.sumOf { it.receive }), money(s.balance), book?.overdueAmount?.takeIf { it.minor > 0 }?.let { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.overdueAmount(DebtBook.show(it)) }.orEmpty()),
          )
          Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.ledgerNote,
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
        //  جزئیاتِ ردیفِ انتخاب‌شده — کنارِ جدول، مثلِ اقلامِ فاکتور در زبانهٔ «فاکتورها»
        val e = pickedTx?.let { book?.entry(it) }
        if (book != null && e != null) {
          Spacer(Modifier.width(12.dp))
          Surface(color = Shop.colors.surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.width(380.dp).fillMaxHeight()) {
            Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
              Text(
                ir.vil3ntec.tohid.core.i18n.StrCustomerPages.rowDetail(e.row, if (e.give) ir.vil3ntec.tohid.core.i18n.StrMoney.giveTag else ir.vil3ntec.tohid.core.i18n.StrMoney.receiveTag, DebtBook.show(e.amount)),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text,
              )
              Text(formatDate(e.tx.date) + (e.invoiceLabel?.let { ir.vil3ntec.tohid.core.i18n.StrDebtPages.invoiceTail(it) } ?: ""), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
              Spacer(Modifier.height(8.dp))
              DebtEntryDetail(book, e, maxLines = 40)
              Spacer(Modifier.height(10.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { receiptFor = e.tx.id }) { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.receiptBtn) }
                if (e.give) OutlinedButton(onClick = { dueFor = e.tx.id }) { Text(ir.vil3ntec.tohid.core.i18n.StrMoney.due) }
                e.sale?.let { sale -> OutlinedButton(onClick = { picked = sale.id; tab = FileTab.INVOICES }) { Text(ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice) } }
              }
            }
          }
        }
      }

      FileTab.INVOICES -> Row(Modifier.weight(1f)) {
        //  زبانه باز شد و فاکتوری انتخاب نشده ⇐ تازه‌ترین، تا اقلامش همان‌جا دیده شود
        LaunchedEffect(invoices) { if (picked == null || invoices.none { it.id == picked }) picked = invoices.firstOrNull()?.id }
        val counts = remember(d) { d.saleItems.groupingBy { it.saleId }.eachCount() }
        val cols = listOf(TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice, 80.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colDate, 110.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colItems, 60.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.total, 0.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.payable, 0.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colPaid, 0.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colBalance, 0.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colKind, 70.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrMoney.status, 80.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
          DeskTable(
            cols, invoices.size, Modifier.weight(1f),
            empty = ir.vil3ntec.tohid.core.i18n.StrCustomerPages.invoicesEmpty,
            selected = invoices.indexOfFirst { it.id == picked }.takeIf { it >= 0 },
            cell = { i, c ->
              val v = invoices[i]
              val off = v.status == "cancelled"
              when (c) {
                0 -> TCell("#${v.invoiceLabel() ?: "—"}", Shop.colors.primary, bold = true)
                1 -> TCell(formatDate(v.date))
                2 -> TCell((counts[v.id] ?: 0).toString())
                3 -> TCell(money(v.total), if (off) Shop.colors.muted else Shop.colors.text)
                4 -> TCell(money(v.finalTotal), if (off) Shop.colors.muted else Shop.colors.text, bold = true)
                5 -> TCell(money(v.paidAmount), Shop.colors.success)
                6 -> TCell(if (v.remaining > 0.5) money(v.remaining) else "", Shop.colors.danger)
                7 -> TCell(ir.vil3ntec.tohid.data.PayFlow.payLabel(v))
                //  CRM-07: فاکتورِ سفارشِ اینترنتیِ تحویل‌شده نشانِ «اینترنتی» دارد
                else -> TCell(
                  when { off -> ir.vil3ntec.tohid.core.i18n.StrSale.cancelled; v.isOnlineOrder() -> StrDebtor.online; else -> ir.vil3ntec.tohid.core.i18n.StrInvoices.recorded },
                  when { off -> Shop.colors.danger; v.isOnlineOrder() -> Shop.colors.primary; else -> Shop.colors.success },
                )
              }
            },
            onClick = { i -> picked = invoices[i].id },
            footer = listOf(ir.vil3ntec.tohid.core.i18n.StrInvoices.total, "", "", money(invoices.filter { it.status != "cancelled" }.sumOf { it.total }),
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
        val cols = listOf(TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colDate, 110.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice, 80.dp), TCol(ir.vil3ntec.tohid.core.i18n.StrMoney.item), TCol(ir.vil3ntec.tohid.core.i18n.StrSale.qty, 80.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrOnline.unitPrice, 120.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrInvoices.total, 130.dp, true), TCol(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.colReturned, 80.dp, true))
        DeskTable(
          cols, items.size, Modifier.weight(1f),
          empty = ir.vil3ntec.tohid.core.i18n.StrCustomerPages.itemsEmpty,
          cell = { i, c ->
            val l = items[i]
            when (c) {
              0 -> TCell(formatDate(l.date))
              1 -> TCell("#${l.invoiceLabel ?: "—"}", Shop.colors.primary, link = true)
              2 -> TCell(l.name)
              3 -> TCell(qty(l.quantity))
              4 -> TCell(money(l.unitPrice))
              5 -> TCell(money(l.total), bold = true)
              else -> TCell(if (l.returned > 0) qty(l.returned) else "", Shop.colors.danger)
            }
          },
          onClick = { i -> picked = items[i].saleId; tab = FileTab.INVOICES },
          footer = listOf("", "", ir.vil3ntec.tohid.core.i18n.StrInvoices.total, "", "", money(items.sumOf { it.total }), ""),
        )
      }
    }
  }

  showInvoice?.let { sale ->
    InvoiceDialog(d = d, sale = sale, onDismiss = { showInvoice = null }, onMessage = ::toast)
  }

  receiptFor?.let { txId ->
    DebtReceiptDialog(
      d = d, debtorId = id, txId = txId,
      onDismiss = { receiptFor = null },
      onDue = if (book?.entry(txId)?.give == true) ({ dueFor = txId; receiptFor = null }) else null,
    )
  }
  dueFor?.let { txId ->
    val e = book?.entry(txId)
    if (e == null) {
      LaunchedEffect(txId) { dueFor = null }
    } else {
      DueDateDialog(e, onDismiss = { dueFor = null }) { due ->
        when (val r = DebtorEngine.setDueDate(d, txId, due)) {
          is DebtorEngine.Result.Failed -> toast(r.message)
          is DebtorEngine.Result.Ok -> { scope.launch { store.save(d, r.data) }; dueFor = null; toast(ir.vil3ntec.tohid.core.i18n.StrMoney.dueSaved) }
        }
      }
    }
  }
  overLimit?.let { (amount, notes, due) ->
    val credit = DebtBook.creditCheck(d, id, DebtBook.minor(amount))
    if (credit == null) {
      LaunchedEffect(amount) { overLimit = null }
    } else {
      OverLimitDialog(
        credit,
        onConfirm = { record(DebtorEngine.Kind.GIVE, amount, notes, due, over = true); overLimit = null },
        onDismiss = { overLimit = null },
      )
    }
  }
  if (limitOpen) DeskCreditLimitDialog(d, debtor, { toast(it) }, onDismiss = { limitOpen = false }) { next ->
    scope.launch { store.save(d, next) }; limitOpen = false; toast(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.creditLimitSaved)
  }
  if (profileOpen && crm) {
    CrmProfileDialog(debtor, onDismiss = { profileOpen = false }) { phone2, tags ->
      when (val r = CustomerProfileForm.setProfile(d, id, phone2, tags)) {
        is DebtorEngine.Result.Failed -> toast(r.message)
        is DebtorEngine.Result.Ok -> { scope.launch { store.save(d, r.data) }; profileOpen = false; toast(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.profileSaved) }
      }
    }
  }
}

private fun qty(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

/** اقلامِ یک فاکتور کنارِ جدول، با دکمهٔ «نمایش و چاپ» که فاکتور را جدا باز می‌کند */
@Composable
internal fun InvoicePeek(d: ShopData, sale: Sale, modifier: Modifier, onShow: () -> Unit) {
  val lines = remember(d, sale.id) { d.saleItems.filter { it.saleId == sale.id } }
  Column(
    modifier
      .softCard(Shop.colors, Shape.card)
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.ReceiptLong, null, tint = Shop.colors.primary)
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Text(ir.vil3ntec.tohid.core.i18n.StrDebtPages.invoiceNo(sale.invoiceLabel() ?: "—"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Text(listOf(formatDate(sale.date), sale.seller.takeIf { it.isNotBlank() }?.let { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.sellerIs(it) }).filterNotNull().joinToString(" · "),
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }
    }
    Spacer(Modifier.height(10.dp))
    HeadRow(listOf(ir.vil3ntec.tohid.core.i18n.StrMoney.item to 0.dp, ir.vil3ntec.tohid.core.i18n.StrSale.qty to 56.dp, ir.vil3ntec.tohid.core.i18n.StrInvoices.total to 100.dp))
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
      itemsIndexed(lines, key = { _, l -> l.id }) { i, l ->
        Row(
          Modifier.fillMaxWidth().background(if (i % 2 == 1) Shop.colors.surface2 else Shop.colors.surface).padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(SalesEngine.itemName(d, l), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
            Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.unitPriceIs(money(l.unitPrice)) + if (l.returnedQty > 0) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.returnedQty(qty(l.returnedQty)) else "", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          }
          Text(qty(l.quantity), Modifier.width(56.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
          Text(money(l.totalPrice), Modifier.width(100.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    @Composable fun line(k: String, v: String, c: Color = Shop.colors.text, b: Boolean = false) =
      Row { Text(k, Modifier.weight(1f), color = Shop.colors.muted, style = MaterialTheme.typography.bodySmall); Text(v, color = c, fontWeight = if (b) FontWeight.Bold else null) }
    line(ir.vil3ntec.tohid.core.i18n.StrInvoices.total, money(sale.total))
    if (sale.discount > 0) line(ir.vil3ntec.tohid.core.i18n.Str.discount, money(sale.discount), Shop.colors.success)
    line(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.payable, "${money(sale.finalTotal)} ؋", b = true)
    line(ir.vil3ntec.tohid.core.i18n.StrInvoices.colPaid, money(sale.paidAmount), Shop.colors.success)
    if (sale.remaining > 0.5) line(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.remainingOnAccount, money(sale.remaining), Shop.colors.danger, true)
    if (sale.status == "cancelled") Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.cancelledWhy(sale.cancelReason.ifBlank { ir.vil3ntec.tohid.core.i18n.StrShrink.noReason }), color = Shop.colors.danger, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(10.dp))
    Button(onClick = onShow, modifier = Modifier.fillMaxWidth()) { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.showPrint) }
  }
}

/* ══════════════════════════ جدولِ اکسلی ══════════════════════════ */

/** ستون — پهنای ۰ یعنی «باقی‌مانده»؛ `end` ⇒ عدد، راست‌چینِ انتها */
internal data class TCol(val title: String, val width: Dp = 0.dp, val end: Boolean = false)
internal data class TCell(val text: String, val color: Color? = null, val bold: Boolean = false, val link: Boolean = false)

/**
 *  جدولِ مدیریت: سرستون، ردیف‌های یکی‌درمیان، ردیفِ جمع در پایین. کلیک روی ردیف ⇐ `onClick`.
 *
 *  نسخهٔ ۲ (نقدِ ۱۴۰۵/۰۷/۲۷: «همه‌جا خطِ تیره»): خطِ عمودی و افقیِ خانه‌ها برداشته
 *  شد؛ ردیف‌ها را زمینهٔ یکی‌درمیان و ارتفاعِ راحت جدا می‌کند و کلِ جدول یک کارتِ نرم است.
 */
@Composable
internal fun DeskTable(
  cols: List<TCol>,
  count: Int,
  modifier: Modifier,
  empty: String,
  cell: @Composable (Int, Int) -> TCell,
  onClick: (Int) -> Unit = {},
  footer: List<String>? = null,
  selected: Int? = null,
) {
  @Composable fun RowScope.box(c: TCol, content: @Composable () -> Unit) {
    Box(
      (if (c.width == 0.dp) Modifier.weight(1f) else Modifier.width(c.width))
        .fillMaxHeight()
        .padding(horizontal = 12.dp, vertical = 9.dp),
      contentAlignment = if (c.end) Alignment.CenterEnd else Alignment.CenterStart,
    ) { content() }
  }
  Column(modifier.fillMaxWidth().softCard(Shop.colors, Shape.card)) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Shop.colors.surface2)) {
      cols.forEach { c -> box(c) { Text(c.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.muted, maxLines = 1) } }
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
      if (count == 0) item {
        Text(empty, Modifier.fillMaxWidth().padding(28.dp), color = Shop.colors.muted, textAlign = TextAlign.Center)
      }
      items(count) { i ->
        val bg = when {
          i == selected -> Shop.colors.primaryTint
          i % 2 == 1 -> Shop.colors.surface2
          else -> Shop.colors.surface
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(bg).clickable { onClick(i) }) {
          cols.forEachIndexed { ci, c ->
            val v = cell(i, ci)
            box(c) {
              //  ستونِ اصلی (پهنای ۰ = باقیِ جا، معمولاً نام) تا دو خط می‌شکند، بریده نمی‌شود
              Text(
                v.text, maxLines = if (c.width == 0.dp) 2 else 1, overflow = TextOverflow.Ellipsis,
                style = if (c.end) MaterialTheme.typography.bodyMedium.tabular() else MaterialTheme.typography.bodyMedium,
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
        cols.forEachIndexed { ci, c -> box(c) { Text(f.getOrElse(ci) { "" }, style = MaterialTheme.typography.bodyMedium.tabular(), fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1) } }
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
  val scope = rememberCoroutineScope(); val limitRules = ir.vil3ntec.tohid.ui.components.rememberCreditLimitRules() // CRM-03 #۲۵
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  fun save() {
    when (val r = DebtorEngine.add(d, ir.vil3ntec.tohid.data.CreditLimitGate.quickDraft(limitRules, DebtorEngine.DebtorDraft(name = name, phone = phone)), System.currentTimeMillis(), ::newId)) {
      is DebtorEngine.Result.Failed -> toast(r.message)
      is DebtorEngine.Result.Ok -> { scope.launch { store.save(d, r.data) }; onAdded(r.id); name = ""; phone = "" }
    }
  }
  val enter = Modifier.onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { save(); true } else false
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(name, { name = it }, label = { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.newCustomerName) }, singleLine = true, modifier = Modifier.width(280.dp).then(enter))
    Spacer(Modifier.width(6.dp))
    OutlinedTextField(phone, { phone = it }, label = { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.phone) }, singleLine = true, modifier = Modifier.width(170.dp).then(enter))
    Spacer(Modifier.width(6.dp))
    Button(onClick = ::save, enabled = name.isNotBlank()) { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.addKey) }
  }
}

/** ردیفِ ثبتِ دریافت/قرض — مبلغ، شرح، Enter */
@Composable
private fun EntryRow(kind: DebtorEngine.Kind, onCancel: () -> Unit, onSave: (Double, String, String?) -> Unit) {
  var amount by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  //  سررسیدِ برد (اختیاری) — خورشیدی نوشته می‌شود، مثلِ کادرِ تاریخ
  var dueText by remember { mutableStateOf("") }
  val due = if (dueText.isBlank()) null else ir.vil3ntec.tohid.Jalali.parseTyped(dueText)
  val dueBroken = dueText.isNotBlank() && due == null
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  fun go() { if (!dueBroken) amount.toDoubleOrNull()?.let { onSave(it, notes.trim(), due) } }
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
      Text(if (receive) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.receivePayment else ir.vil3ntec.tohid.core.i18n.StrCustomerPages.manualCredit, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
      Spacer(Modifier.width(8.dp))
      OutlinedTextField(amount, { amount = ir.vil3ntec.tohid.numericInput(it) }, label = { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.amountAfn) }, singleLine = true, modifier = Modifier.width(160.dp).focusRequester(focus).then(keys))
      Spacer(Modifier.width(6.dp))
      OutlinedTextField(notes, { notes = it }, label = { Text(ir.vil3ntec.tohid.core.i18n.StrReports.note) }, singleLine = true, modifier = Modifier.weight(1f).then(keys))
      if (!receive) {
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
          dueText, { dueText = ir.vil3ntec.tohid.latinDigits(it).filter { c -> c.isDigit() || c == '/' || c == '-' } },
          label = { Text(ir.vil3ntec.tohid.core.i18n.StrMoney.dueField) }, placeholder = { Text("۱۴۰۵/۰۸/۰۱") },
          singleLine = true, isError = dueBroken, modifier = Modifier.width(170.dp).then(keys),
        )
      }
      Spacer(Modifier.width(6.dp))
      Button(onClick = ::go, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && !dueBroken) { Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.saveKey) }
      TextButton(onClick = onCancel) { Text("Esc") }
    }
  }
}
