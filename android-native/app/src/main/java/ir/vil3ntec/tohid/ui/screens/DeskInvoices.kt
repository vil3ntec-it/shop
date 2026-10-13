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
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  همهٔ فاکتورهای فروش — کامپیوتر.
 *
 *  جدولِ اکسلی (شماره، تاریخ، مشتری، اقلام، جمع، تخفیف، قابلِ پرداخت،
 *  پرداخت‌شده، باقی، نقد/نسیه، فروشنده، وضعیت) با جست‌وجو (شماره یا مشتری،
 *  Ctrl+F)، بازه، نوعِ پرداخت و وضعیت. کلیک ⇐ اقلامِ همان فاکتور کنارش؛
 *  «نمایش و چاپ» ⇐ فاکتورِ جدا (حرارتی یا A4). «لغو و برگشتی» همان صفحهٔ
 *  کاملِ تاریخچه را باز می‌کند.
 */
@Composable
fun DeskInvoicesScreen(store: ShopStore, cartStore: CartStore, d: ShopData, snackbar: SnackbarHostState, onEdit: () -> Unit) {
  var classic by remember { mutableStateOf(false) }
  if (classic) {
    Column(Modifier.fillMaxSize()) {
      TextButton(onClick = { classic = false }, modifier = Modifier.padding(8.dp)) { Text("← برگشت به جدولِ فاکتورها") }
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
      .filter { pay == "all" || (pay == "credit") == (it.paymentMethod == "credit") }
      .filter { state == "all" || (state == "cancelled") == (it.status == "cancelled") }
      .filter { q.isEmpty() || (it.invoiceNumber?.toString() == q || it.invoiceLabel().equals(q, ignoreCase = true)) || (it.debtorId?.let(names::get)?.contains(q, true) == true) || it.seller.contains(q, true) }
      .sortedWith(compareByDescending<Sale> { it.date }.thenByDescending { it.createdAt })
  }
  val live = list.filter { it.status != "cancelled" }

  fun sheet(): ReportCsv.Sheet {
    val rows = mutableListOf(listOf("شماره", "تاریخ", "مشتری", "اقلام", "جمع", "تخفیف", "قابل پرداخت", "پرداخت‌شده", "باقی", "نوع", "فروشنده", "وضعیت"))
    list.forEach { s ->
      rows += listOf(s.invoiceLabel().orEmpty(), s.date, s.debtorId?.let(names::get).orEmpty(), (counts[s.id] ?: 0).toString(),
        Math.round(s.total).toString(), Math.round(s.discount).toString(), Math.round(s.finalTotal).toString(),
        Math.round(s.paidAmount).toString(), Math.round(s.remaining).toString(),
        if (s.paymentMethod == "credit") "نسیه" else "نقد", s.seller, if (s.status == "cancelled") "لغوشده" else "ثبت‌شده")
    }
    return ReportCsv.Sheet("فاکتورهای-فروش-$from-تا-$to", rows)
  }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@onPreviewKeyEvent false
        when (e.key) {
          Key.F -> { runCatching { searchFocus.requestFocus() }; true }
          Key.P -> { sheet().let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) }; true }
          Key.E -> { val sh = sheet(); Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast("ذخیره شد: $it") }; true }
          else -> false
        }
      }
      .padding(16.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      DeskKpi("فروشِ خالص", "${money(live.sumOf { it.finalTotal })} ؋", Shop.colors.primary, Modifier.weight(1f))
      DeskKpi("فاکتورها", "${live.size}", Shop.colors.text, Modifier.weight(1f))
      DeskKpi("نسیه (باقی)", "${money(live.sumOf { it.remaining })} ؋", Shop.colors.danger, Modifier.weight(1f))
      DeskKpi("تخفیف", "${money(live.sumOf { it.discount })} ؋", Shop.colors.success, Modifier.weight(1f))
      DeskKpi("لغوشده", "${list.size - live.size}", Shop.colors.muted, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(search, { search = it }, singleLine = true, placeholder = { Text("شمارهٔ فاکتور، مشتری یا فروشنده — Ctrl+F") },
        modifier = Modifier.width(300.dp).focusRequester(searchFocus))
      Spacer(Modifier.width(8.dp))
      Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        listOf(ReportEngine.Range.TODAY to "امروز", ReportEngine.Range.WEEK to "۷ روز", ReportEngine.Range.MONTH to "این ماه", ReportEngine.Range.ALL to "همه").forEach { (r, t) ->
          FilterChip(selected = range == r, onClick = { range = r }, label = { Text(t) }); Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(10.dp))
        listOf("all" to "نقد و نسیه", "cash" to "نقد", "credit" to "نسیه").forEach { (k, t) ->
          FilterChip(selected = pay == k, onClick = { pay = k }, label = { Text(t) }); Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(10.dp))
        listOf("all" to "همه", "done" to "ثبت‌شده", "cancelled" to "لغوشده").forEach { (k, t) ->
          FilterChip(selected = state == k, onClick = { state = k }, label = { Text(t) }); Spacer(Modifier.width(4.dp))
        }
      }
      OutlinedButton(onClick = { classic = true }) { Text("لغو و برگشتی") }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = { val sh = sheet(); Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast("ذخیره شد: $it") } }) { Text("اکسل  Ctrl+E") }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.weight(1f)) {
      val cols = listOf(
        TCol("فاکتور", 80.dp), TCol("تاریخ", 105.dp), TCol("مشتری"), TCol("اقلام", 56.dp, true), TCol("قابل پرداخت", 120.dp, true),
        TCol("پرداخت‌شده", 115.dp, true), TCol("باقی", 105.dp, true), TCol("نوع", 60.dp), TCol("فروشنده", 100.dp), TCol("وضعیت", 80.dp),
      )
      DeskTable(
        cols, list.size, Modifier.weight(1f),
        empty = "در این بازه فاکتوری نیست.",
        selected = list.indexOfFirst { it.id == picked }.takeIf { it >= 0 },
        cell = { i, c ->
          val s = list[i]
          val off = s.status == "cancelled"
          when (c) {
            0 -> TCell("#${s.invoiceLabel() ?: "—"}", Shop.colors.primary, bold = true)
            1 -> TCell(formatDate(s.date))
            2 -> TCell(s.debtorId?.let(names::get) ?: "مشتریِ گذری", if (s.debtorId == null) Shop.colors.muted else Shop.colors.text)
            3 -> TCell((counts[s.id] ?: 0).toString())
            4 -> TCell(money(s.finalTotal), if (off) Shop.colors.muted else Shop.colors.text, bold = true)
            5 -> TCell(money(s.paidAmount), Shop.colors.success)
            6 -> TCell(if (s.remaining > 0.5) money(s.remaining) else "", Shop.colors.danger)
            7 -> TCell(if (s.paymentMethod == "credit") "نسیه" else "نقد")
            8 -> TCell(s.seller.ifBlank { "—" }, Shop.colors.muted)
            else -> TCell(if (off) "لغوشده" else "ثبت‌شده", if (off) Shop.colors.danger else Shop.colors.success)
          }
        },
        onClick = { picked = list[it].id },
        footer = listOf("جمع", "", "", "", money(live.sumOf { it.finalTotal }), money(live.sumOf { it.paidAmount }), money(live.sumOf { it.remaining }), "", "", ""),
      )
      list.find { it.id == picked }?.let { sale ->
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(380.dp).fillMaxHeight()) {
          InvoicePeek(d, sale, Modifier.weight(1f).fillMaxWidth()) { show = sale }
          Spacer(Modifier.height(6.dp))
          OutlinedButton(onClick = { saleSheet(d, sale).let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } }, modifier = Modifier.fillMaxWidth()) {
            Text("چاپِ A4 (برگهٔ کامل)")
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
  val rows = mutableListOf(listOf("ردیف", "کالا", "تعداد", "فی", "جمع"))
  d.saleItems.filter { it.saleId == s.id }.forEachIndexed { i, it ->
    rows += listOf((i + 1).toString(), SalesEngine.itemName(d, it), qtyText(it.quantity), Math.round(it.unitPrice).toString(), Math.round(it.totalPrice).toString())
  }
  rows += listOf("", "جمع", "", "", Math.round(s.total).toString())
  if (s.discount > 0) rows += listOf("", "تخفیف", "", "", Math.round(s.discount).toString())
  rows += listOf("", "قابلِ پرداخت", "", "", Math.round(s.finalTotal).toString())
  rows += listOf("", "پرداخت‌شده", "", "", Math.round(s.paidAmount).toString())
  if (s.remaining > 0.5) rows += listOf("", "باقی (به حساب)", "", "", Math.round(s.remaining).toString())
  val who = if (customer.isNotBlank()) "-$customer" else ""
  return ReportCsv.Sheet("فاکتور-${s.invoiceLabel() ?: ""}$who-${s.date}", rows)
}
