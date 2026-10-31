package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.invoiceLabel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale

/**
 *  همهٔ فاکتورهای فروش — کامپیوتر.
 *
 *  جدولِ اکسلی (شماره، تاریخ، مشتری، اقلام، جمع، تخفیف، قابلِ پرداخت،
 *  پرداخت‌شده، باقی، نقد/نسیه، فروشنده، وضعیت) با جست‌وجو (شماره یا مشتری،
 *  Ctrl+F)، بازه، نوعِ پرداخت و وضعیت. کلیک ⇐ اقلامِ همان فاکتور کنارش؛
 *  «نمایش و چاپ» ⇐ فاکتورِ جدا (حرارتی یا A4). «لغو و برگشتی» همان صفحهٔ
 *  کاملِ تاریخچه را باز می‌کند.
 *
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrInvoices` (LOC-06)؛ برگهٔ اکسل/چاپِ A4 داده است و همان فارسی می‌ماند.
 */
@Composable
fun DeskInvoicesScreen(store: ShopStore, cartStore: CartStore, d: ShopData, snackbar: SnackbarHostState, onEdit: () -> Unit) {
  var classic by remember { mutableStateOf(false) }
  if (classic) {
    Column(Modifier.fillMaxSize()) {
      TextButton(onClick = { classic = false }, modifier = Modifier.padding(8.dp)) { Text(StrInvoices.backToTable) }
      Box(Modifier.weight(1f)) { SalesHistoryScreen(store, cartStore, d, snackbar, onEdit) }
    }
    return
  }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  val today = ir.vil3ntec.tohid.todayIso()
  var search by remember { mutableStateOf("") }
  var range by remember { mutableStateOf(ReportEngine.Range.MONTH) }
  var pay by remember { mutableStateOf("all") }
  var state by remember { mutableStateOf("all") }
  var picked by remember { mutableStateOf<String?>(null) }
  var show by remember { mutableStateOf<Sale?>(null) }
  val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }

  val (from, to) = ReportEngine.rangeOf(range, today)
  val names = remember(d) { d.debtors.associate { it.id to it.name } }
  val counts = remember(d) { d.saleItems.groupingBy { it.saleId }.eachCount() }
  val list = remember(d, search, from, to, pay, state) {
    val q = search.trim()
    d.sales
      .filter { it.date in from..to }
      .filter { pay == "all" || ir.vil3ntec.tohid.data.PayFlow.payGroup(it) == pay }
      .filter { state == "all" || (state == "cancelled") == (it.status == "cancelled") }
      .filter { q.isEmpty() || (it.invoiceNumber?.toString() == q || it.invoiceLabel().equals(q, ignoreCase = true)) || (it.debtorId?.let(names::get)?.contains(q, true) == true) || it.seller.contains(q, true) }
      .sortedWith(compareByDescending<Sale> { it.date }.thenByDescending { it.createdAt })
  }
  val live = list.filter { it.status != "cancelled" }

  fun sheet(): ReportCsv.Sheet {
    val rows = mutableListOf(listOf("شماره", "تاریخ", "مشتری", "اقلام", "جمع", "تخفیف", "قابل پرداخت", "پرداخت‌شده", "باقی", "نوع", "فروشنده", "وضعیت")) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
    list.forEach { s ->
      rows += listOf(s.invoiceLabel().orEmpty(), s.date, s.debtorId?.let(names::get).orEmpty(), (counts[s.id] ?: 0).toString(),
        Math.round(s.total).toString(), Math.round(s.discount).toString(), Math.round(s.finalTotal).toString(),
        Math.round(s.paidAmount).toString(), Math.round(s.remaining).toString(),
        ir.vil3ntec.tohid.data.PayFlow.payLabel(s), s.seller, if (s.status == "cancelled") "لغوشده" else "ثبت‌شده") // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
    }
    return ReportCsv.Sheet("فاکتورهای-فروش-$from-تا-$to", rows) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
  }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || !e.isPrimaryModifier) return@onPreviewKeyEvent false
        when (e.key) {
          Key.F -> { runCatching { searchFocus.requestFocus() }; true }
          Key.P -> { sheet().let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) }; true }
          Key.E -> { val sh = sheet(); Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast(StrInvoices.savedTo(it)) }; true }
          else -> false
        }
      }
      .padding(16.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      DeskKpi(StrInvoices.netSales, "${money(live.sumOf { it.finalTotal })} ؋", Shop.colors.primary, Modifier.weight(1f))
      DeskKpi(Str.invoices, "${live.size}", Shop.colors.text, Modifier.weight(1f))
      DeskKpi(StrInvoices.creditUnpaid, "${money(live.sumOf { it.remaining })} ؋", Shop.colors.danger, Modifier.weight(1f))
      DeskKpi(Str.discount, "${money(live.sumOf { it.discount })} ؋", Shop.colors.success, Modifier.weight(1f))
      DeskKpi(StrSale.cancelled, "${list.size - live.size}", Shop.colors.muted, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(search, { search = it }, singleLine = true, placeholder = { Text(StrInvoices.deskSearchHint) },
        modifier = Modifier.width(300.dp).focusRequester(searchFocus))
      Spacer(Modifier.width(8.dp))
      Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        listOf(ReportEngine.Range.TODAY to Str.today, ReportEngine.Range.WEEK to StrInvoices.last7Days, ReportEngine.Range.MONTH to StrHome.thisMonth, ReportEngine.Range.ALL to Str.all).forEach { (r, t) ->
          FilterChip(selected = range == r, onClick = { range = r }, label = { Text(t) }); Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(10.dp))
        listOf("all" to StrInvoices.allMethods, "cash" to Str.cash, "credit" to Str.credit, ir.vil3ntec.tohid.data.PayFlow.NON_CASH to StrInvoices.nonCash).forEach { (k, t) ->
          FilterChip(selected = pay == k, onClick = { pay = k }, label = { Text(t) }); Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(10.dp))
        listOf("all" to Str.all, "done" to StrInvoices.recorded, "cancelled" to StrSale.cancelled).forEach { (k, t) ->
          FilterChip(selected = state == k, onClick = { state = k }, label = { Text(t) }); Spacer(Modifier.width(4.dp))
        }
      }
      OutlinedButton(onClick = { classic = true }) { Text(StrInvoices.cancelAndReturns) }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = { val sh = sheet(); Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast(StrInvoices.savedTo(it)) } }) { Text(StrInvoices.excelKey) }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.weight(1f)) {
      val cols = listOf(
        TCol(StrInvoices.colInvoice, 80.dp), TCol(StrInvoices.colDate, 105.dp), TCol(StrPos.customer), TCol(StrInvoices.colItems, 56.dp, true), TCol(StrInvoices.colDue, 120.dp, true),
        TCol(StrInvoices.colPaid, 115.dp, true), TCol(StrInvoices.colBalance, 105.dp, true), TCol(StrInvoices.colKind, 60.dp), TCol(StrInvoices.colSeller, 100.dp), TCol(StrNav.status, 80.dp),
      )
      DeskTable(
        cols, list.size, Modifier.weight(1f),
        empty = StrInvoices.emptyRange,
        selected = list.indexOfFirst { it.id == picked }.takeIf { it >= 0 },
        cell = { i, c ->
          val s = list[i]
          val off = s.status == "cancelled"
          when (c) {
            0 -> TCell("#${s.invoiceLabel() ?: "—"}", Shop.colors.primary, bold = true)
            1 -> TCell(displayDate(s.date))
            2 -> TCell(s.debtorId?.let(names::get) ?: Str.walkIn, if (s.debtorId == null) Shop.colors.muted else Shop.colors.text)
            3 -> TCell((counts[s.id] ?: 0).toString())
            4 -> TCell(money(s.finalTotal), if (off) Shop.colors.muted else Shop.colors.text, bold = true)
            5 -> TCell(money(s.paidAmount), Shop.colors.success)
            6 -> TCell(if (s.remaining > 0.5) money(s.remaining) else "", Shop.colors.danger)
            7 -> TCell(ir.vil3ntec.tohid.data.PayFlow.payLabel(s))
            8 -> TCell(s.seller.ifBlank { "—" }, Shop.colors.muted)
            else -> TCell(if (off) StrSale.cancelled else StrInvoices.recorded, if (off) Shop.colors.danger else Shop.colors.success)
          }
        },
        onClick = { picked = list[it].id },
        footer = listOf(StrInvoices.total, "", "", "", money(live.sumOf { it.finalTotal }), money(live.sumOf { it.paidAmount }), money(live.sumOf { it.remaining }), "", "", ""),
      )
      list.find { it.id == picked }?.let { sale ->
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(380.dp).fillMaxHeight()) {
          InvoicePeek(d, sale, Modifier.weight(1f).fillMaxWidth()) { show = sale }
          Spacer(Modifier.height(6.dp))
          OutlinedButton(onClick = { saleSheet(d, sale).let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } }, modifier = Modifier.fillMaxWidth()) {
            Text(StrInvoices.printA4)
          }
        }
      }
    }
  }

  show?.let { sale -> InvoiceDialog(d = d, sale = sale, onDismiss = { show = null }, onMessage = ::toast) }
}

/** فاکتورِ فروش به شکلِ برگه — برای چاپِ A4 و اکسل */
internal fun saleSheet(d: ShopData, s: Sale): ReportCsv.Sheet {
  val customer = s.debtorId?.let { id -> d.debtors.find { it.id == id }?.name }.orEmpty()
  val rows = mutableListOf(listOf("ردیف", "کالا", "تعداد", "فی", "جمع")) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
  d.saleItems.filter { it.saleId == s.id }.forEachIndexed { i, it ->
    rows += listOf((i + 1).toString(), SalesEngine.itemName(d, it), qtyText(it.quantity), Math.round(it.unitPrice).toString(), Math.round(it.totalPrice).toString())
  }
  rows += listOf("", "جمع", "", "", Math.round(s.total).toString()) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
  if (s.discount > 0) rows += listOf("", "تخفیف", "", "", Math.round(s.discount).toString()) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
  rows += listOf("", "قابلِ پرداخت", "", "", Math.round(s.finalTotal).toString()) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
  rows += listOf("", "پرداخت‌شده", "", "", Math.round(s.paidAmount).toString()) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
  if (s.remaining > 0.5) rows += listOf("", "باقی (به حساب)", "", "", Math.round(s.remaining).toString()) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
  val who = if (customer.isNotBlank()) "-$customer" else ""
  return ReportCsv.Sheet("فاکتور-${s.invoiceLabel() ?: ""}$who-${s.date}", rows) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
}
