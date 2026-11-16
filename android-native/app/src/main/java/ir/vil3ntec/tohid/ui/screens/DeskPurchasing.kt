package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCustomerPages
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.i18n.StrPriceCheck
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrReturnPages
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSimpleAsk
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TableChart
import ir.vil3ntec.tohid.core.i18n.StrDeskData
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskCalmRow
import ir.vil3ntec.tohid.ui.desk.DeskCell
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDataShot
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskGrid
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskInfoRow
import ir.vil3ntec.tohid.ui.desk.DeskKpiCard
import ir.vil3ntec.tohid.ui.desk.DeskKpiRow
import ir.vil3ntec.tohid.ui.desk.DeskLetterTile
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskPage
import ir.vil3ntec.tohid.ui.desk.DeskPane
import ir.vil3ntec.tohid.ui.desk.DeskPaneHint
import ir.vil3ntec.tohid.ui.desk.DeskRadius
import ir.vil3ntec.tohid.ui.desk.DeskSegmented
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskSubhead
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskToolbar
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.rememberDeskLoad

/**
 *  خرید و تأمین‌کنندگان — کامپیوتر.
 *
 *  خواستهٔ صاحب مخزن: «فاکتور خرید… جدولی مثلِ اکسل». سه بخش:
 *
 *  • **فاکتورهای خرید**: جدولِ همهٔ فاکتورها (شماره، تاریخ، تأمین‌کننده، ردیف‌ها،
 *    جمع، پرداخت‌شده، مانده) با صافیِ تأمین‌کننده؛ کلیک ⇐ ردیف‌های همان فاکتور
 *    کنارش با چاپ و اکسل.
 *  • **فاکتورِ خریدِ تازه** (Ctrl+N): تأمین‌کننده، تاریخ، و جدولِ ردیف‌ها — کالا
 *    (نام یا بارکد، Enter)، مقدار، فی، انقضا. Enter روی فی ⇐ ردیفِ بعد. بارکدخوان
 *    هم ردیف می‌افزاید. «پرداخت‌شده» برای کلِ فاکتور؛ F9 ثبت، Esc انصراف.
 *  • **تأمین‌کنندگان**: کارتِ هر تأمین‌کننده با بدهیِ ما؛ داخلش صورت‌حساب
 *    (خرید، پرداخت، مانده) و F7 پرداخت.
 *
 *  منطق: `PurchaseBook` (هر ردیف همان `LedgerEngine.addPurchase`).
 */
@Composable
fun DeskPurchasingScreen(store: ShopStore, d: ShopData, snackbar: SnackbarHostState) {
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  var tab by remember { mutableStateOf(0) }
  var editing by remember { mutableStateOf(false) }
  val unitsOn = purchaseUnitsOn()
  val pending = remember { mutableStateListOf<Scan>() }
  //  عکسِ CI (`D3bShotsTest`): زبانهٔ «تأمین‌کنندگان»
  val shotTab = DeskDataShot.tab.value
  LaunchedEffect(shotTab) { if (shotTab == "suppliers") tab = 1 }

  //  بارکدخوان: کالا به فاکتورِ باز؛ اگر بسته است، فاکتورِ تازه باز می‌شود
  val latest by rememberUpdatedState(d)
  val owner = remember { Any() }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.scan.ScanRouter.register(owner) { code ->
      //  PRD-07: بارکدِ واحدِ خرید (کارتنِ ۲۴ ِ تأمین‌کنندهٔ ب) همان واحد را برمی‌گزیند — فقط با `ShopPolicy.productTools`
      val unitHit = if (unitsOn) ShopStore.purchaseUnitIndex(latest).find(code) else null
      val id = unitHit?.productId ?: ShopStore.barcodeIndex(latest)[code]
      if (id == null) { toast(StrPurchasePages.barcodeUnknown(code)); true }
      else { pending += Scan(id, unitHit?.unit?.name); editing = true; true }
    }
    onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(owner) }
  }

  //  Ctrl+N (پنجره) و دکمهٔ «+» ⇐ فاکتورِ خریدِ تازه
  val add = remember { { editing = true } }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.ui.AppNav.addAction.value = add
    onDispose { if (ir.vil3ntec.tohid.ui.AppNav.addAction.value === add) ir.vil3ntec.tohid.ui.AppNav.addAction.value = null }
  }

  if (editing) {
    NewPurchaseInvoice(store, d, pending, ::toast) { editing = false; pending.clear() }
    return
  }

  DeskPage(
    Modifier.onPreviewKeyEvent { e ->
      if (e.type == KeyEventType.KeyDown && e.isPrimaryModifier && e.key == Key.N) { editing = true; true } else false
    },
  ) {
    PurchaseKpis(d)
    DeskToolbar(
      end = {
        //  PUR-02/04: فقط با پرچمِ سرورِ `shop.purchase_orders` دیده می‌شود
        PurchaseOrdersEntry(store, d, ::toast)
        DeskButton(StrPurchasePages.newPurchaseKey, { editing = true }, kind = DeskButtonKind.PRIMARY)
      },
    ) {
      DeskSegmented(listOf(0 to StrPurchasePages.purchaseInvoices, 1 to StrPurchasePages.suppliers), tab, { tab = it })
    }
    if (tab == 0) PurchaseInvoices(d, ::toast) else Suppliers(store, d, ::toast)
  }
}

/** چهار عددِ کل: خریدِ این ماه، بدهیِ ما، شمارِ فاکتورها و تأمین‌کنندگان — کارتِ KPI ِ کامپیوتر، بیرون از رشتهٔ صفحه */
@Composable
private fun PurchaseKpis(d: ShopData) {
  val month = ir.vil3ntec.tohid.todayIso().take(7)
  val k = rememberDeskLoad(d, month) {
    listOf(
      d.purchases.filter { it.date.startsWith(month) }.sumOf { it.totalAmount },
      d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id).coerceAtLeast(0.0) },
      PurchaseBook.invoices(d, null).size.toDouble(),
    )
  }
  fun amountOrDash(v: Double?) = v?.let { "${money(it)} ؋" } ?: "—"
  DeskKpiRow {
    val m = Modifier.weight(1f).fillMaxHeight()
    DeskKpiCard(ir.vil3ntec.tohid.ui.desk.DeskKpi(StrPurchasePages.thisMonthPurchases, amountOrDash(k?.get(0)), Icons.Outlined.LocalShipping, DeskHues.tone("purchasing")), m)
    DeskKpiCard(ir.vil3ntec.tohid.ui.desk.DeskKpi(StrPurchasePages.weOweSuppliers, amountOrDash(k?.get(1)), Icons.Outlined.AccountBalanceWallet, DeskHues.tone(ir.vil3ntec.tohid.ui.v3.V3Hue.AMBER)), m)
    DeskKpiCard(ir.vil3ntec.tohid.ui.desk.DeskKpi(StrPurchasePages.purchaseInvoices, k?.get(2)?.let { ir.vil3ntec.tohid.plain(it.toInt()) } ?: "—", Icons.AutoMirrored.Outlined.ReceiptLong, DeskHues.tone(ir.vil3ntec.tohid.ui.v3.V3Hue.BRAND)), m)
    DeskKpiCard(ir.vil3ntec.tohid.ui.desk.DeskKpi(StrPurchasePages.suppliers, ir.vil3ntec.tohid.plain(d.suppliers.size), Icons.Outlined.Storefront, DeskHues.tone(ir.vil3ntec.tohid.ui.v3.V3Hue.SKY)), m)
  }
}

/* ══════════════════════════ فهرستِ فاکتورها ══════════════════════════ */

@Composable
private fun ColumnScope.PurchaseInvoices(d: ShopData, toast: (String) -> Unit) {
  var supplier by remember { mutableStateOf<String?>(null) }
  var picked by remember { mutableStateOf<String?>(null) }
  val list = rememberDeskLoad(d, supplier) { PurchaseBook.invoices(d, supplier) }.orEmpty()
  //  ماندهٔ واقعیِ هر فاکتور (پرداخت‌های بعدی و سندِ کسر FIFO) — برای رنگِ سررسید
  val remaining = remember(d) { PurchaseBook.open(d).associate { it.invoice.key to it.remaining } }
  val names = remember(d) { d.suppliers.associate { it.id to it.name } }
  val today = ir.vil3ntec.tohid.todayIso()
  val shotPick = DeskDataShot.pick.value
  LaunchedEffect(shotPick, list.isNotEmpty()) { if (shotPick == "first") list.firstOrNull()?.let { picked = it.key } }
  val c = Desk.colors

  //  یادآورِ سررسید (PUR-07) — فقط وقتی فاکتوری سررسید دارد
  SupplierDueReminder(d, today) { supplier = it }
  DeskToolbar(
    end = {
      DeskButton(StrPurchasePages.listExcel, {
        val sh = PurchaseBook.invoicesSheet(d, list, today)
        Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast(StrSale.savedTo(it)) }
      }, icon = Icons.Outlined.TableChart)
    },
  ) {
    DeskDropdown(
      supplier,
      listOf(DeskOption<String?>(null, Str.all)) + d.suppliers.map { DeskOption<String?>(it.id, it.name) },
      { supplier = it; picked = null },
      label = StrMoney.supplierLabel,
    )
    DeskNote(StrDeskData.rowsShown(ir.vil3ntec.tohid.plain(list.size)))
  }
  val inv = list.find { it.key == picked }
  BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
   val paneW = ir.vil3ntec.tohid.ui.desk.deskPaneWidth(maxWidth)
   Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
   Box(Modifier.weight(1f).fillMaxHeight()) {
    val cols = listOf(
      DeskColumn(StrInvoices.colInvoice, width = 88.dp), DeskColumn(StrInvoices.colDate, width = 104.dp), DeskColumn(StrMoney.supplierLabel, weight = 1f),
      DeskColumn(StrPurchasePages.rows, width = 60.dp, end = true), DeskColumn(StrInvoices.total, width = 120.dp, end = true),
      DeskColumn(StrInvoices.colPaid, width = 120.dp, end = true), DeskColumn(StrSimpleAsk.vDebtRemaining, width = 120.dp, end = true),
    )
    DeskGrid(
      cols, list.size, Modifier.fillMaxSize(),
      key = { list[it].key },
      selected = list.indexOfFirst { it.key == picked }.takeIf { it >= 0 },
      onPick = { picked = list[it].key },
      rowLabel = { list[it].let { v -> if (v.no > 0) "#${v.no}" else Str.purchases } },
      footer = listOf(StrInvoices.total, "", "", "", money(list.sumOf { it.total }), money(list.sumOf { it.paid }), money(list.sumOf { it.debt })),
      empty = { ir.vil3ntec.tohid.ui.v3.V3EmptyState(Icons.Outlined.LocalShipping, StrPurchasePages.noPurchasesYet) },
    ) { i, col ->
      val v = list[i]
      when (col) {
        0 -> DeskCell(if (v.isReturn) StrHistory.returns else if (v.no > 0) "#${v.no}" else "—", muted = v.isReturn)
        1 -> DeskCell(formatDate(v.date), muted = true)
        2 -> DeskCell(names[v.supplierId] ?: "—")
        3 -> DeskCell(ir.vil3ntec.tohid.plain(v.lines.size), muted = true)
        4 -> DeskCell(money(v.total), strong = true)
        5 -> DeskCell(money(v.paid), muted = true)
        else -> DeskCell(if (v.debt > 0.5) money(v.debt) else "", strong = true)
      }
    }
   }
   if (inv != null) Box(Modifier.width(paneW).fillMaxHeight()) { PurchasePane(d, inv, names, remaining, today, toast, onClose = { picked = null }) }
   }
  }
}

/** پنلِ فاکتورِ خریدِ برگزیده: ردیف‌ها، جمع‌ها، سررسید و چاپ/اکسل */
@Composable
private fun PurchasePane(d: ShopData, inv: PurchaseBook.Invoice, names: Map<String, String>, remaining: Map<String, Double>, today: String, toast: (String) -> Unit, onClose: () -> Unit) {
  val context = LocalContext.current
  val c = Desk.colors
  val late = inv.dueDate?.let { due -> !inv.isReturn && due < today && (remaining[inv.key] ?: 0.0) > 0.5 } == true
  DeskPane(
    title = if (inv.isReturn) StrPurchasePages.returnToSupplier else if (inv.no > 0) StrPurchasePages.purchaseInvoiceNo(inv.no) else Str.purchases,
    subtitle = "${names[inv.supplierId] ?: "—"} • ${formatDate(inv.date)}",
    onClose = onClose,
    actions = {
      DeskButton(StrHistory.print, { PurchaseBook.invoiceSheet(d, inv).let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } }, Modifier.weight(1f), kind = DeskButtonKind.PRIMARY, icon = Icons.Outlined.Print)
      DeskButton(StrReports.excel, {
        val sh = PurchaseBook.invoiceSheet(d, inv)
        Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast(StrSale.savedTo(it)) }
      }, Modifier.weight(1f), icon = Icons.Outlined.TableChart)
    },
  ) {
    inv.dueDate?.takeIf { !inv.isReturn }?.let { due ->
      DeskNote(StrPurchasePages.dueOn(formatDate(due)))
      if (late) Text(StrPurchasePages.overdue, style = DeskType.label, color = c.negative)
    }
    DeskSubhead(StrPurchasePages.rows + " · " + ir.vil3ntec.tohid.plain(inv.lines.size))
    Column(Modifier.fillMaxWidth()) {
      inv.lines.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = DeskSpace.x2), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(d.products.find { it.id == p.productId }?.name ?: StrHome.txDeletedItem, maxLines = 1, overflow = TextOverflow.Ellipsis, style = DeskType.body, color = c.ink)
            Text("${qtyText(p.quantity)} × " + StrPurchasePages.unitPriceUnit(money(p.purchasePrice), p.unit), style = DeskType.secondary, color = c.inkSecondary, maxLines = 1)
          }
          Text(money(p.totalAmount), style = DeskType.table, color = c.ink)
        }
        DeskDivider()
      }
    }
    DeskInfoRow(StrInvoices.total, "${money(inv.total)} ؋", strong = true)
    DeskInfoRow(StrInvoices.colPaid, money(inv.paid))
    DeskInfoRow(StrPurchasePages.onSupplierAccount, money(inv.debt), strong = inv.debt > 0.5, valueColor = if (inv.debt > 0.5) c.negative else null)
  }
}

internal fun qtyText(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

/* ══════════════════════════ فاکتورِ خریدِ تازه ══════════════════════════ */

/** یک اسکن: کالا و (PRD-07) واحدِ خریدی که بارکدش خوانده شد */
private data class Scan(val productId: String, val unit: String? = null)

/** PRD-07: واحدهای خرید در فرمِ خرید فقط با `ShopPolicy.productTools` (پیش‌فرض خاموش ⇒ همان ردیفِ واحدِ پایه) */
@Composable
internal fun purchaseUnitsOn(): Boolean {
  val context = androidx.compose.ui.platform.LocalContext.current
  return remember { ir.vil3ntec.tohid.data.ShopPolicy.productTools(context) }
}

//  ردیفِ نوشتنی (`PurchaseDraft`) و قیمتِ پیش‌فرض (`purchaseDefaultPrice`) در `DeskPurchaseForm.kt` (e-desk-d3c)

@Composable
private fun NewPurchaseInvoice(
  store: ShopStore,
  d: ShopData,
  pending: MutableList<Scan>,
  toast: (String) -> Unit,
  onClose: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val today = ir.vil3ntec.tohid.todayIso()
  val unitsOn = purchaseUnitsOn()
  var supplierId by remember { mutableStateOf(d.suppliers.maxByOrNull { s -> d.purchases.filter { it.supplierId == s.id }.maxOfOrNull { it.createdAt } ?: 0L }?.id) }
  var supplierMenu by remember { mutableStateOf(false) }
  var newSupplier by remember { mutableStateOf("") }
  //  تاریخِ خورشیدی نوشته و خوانده می‌شود (۱۴۰۵/۰۷/۲۱)؛ در دفتر میلادی می‌نشیند
  var date by remember { mutableStateOf(formatDate(today)) }
  var paid by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  //  موتورِ تازهٔ موجودی (روشن): هزینهٔ جانبیِ کلِ فاکتور (PUR-06) — روی بهای ردیف‌ها پخش می‌شود، نه بدهیِ تأمین‌کننده
  val stockOn = remember(d.auditLog, ir.vil3ntec.tohid.data.StockLedger.serverFlag) { ir.vil3ntec.tohid.data.StockLedger.isOn(d) }
  var landed by remember { mutableStateOf("") }
  var due by remember { mutableStateOf("") }
  val context = androidx.compose.ui.platform.LocalContext.current
  val lines = remember { mutableStateListOf(PurchaseDraft()) }
  val no = remember(d) { PurchaseBook.nextNo(d) }
  //  PUR-05: یک کلید برای همین فرم — F9 یا Ctrl+Enter ِ پشتِ‌سرِهم (تکرارِ کلید) یک فاکتور است
  val saveKey = remember { newId() }
  //  PUR-09: آخرین فیِ همین تأمین‌کننده زیرِ هر ردیف و Ctrl+I تاریخچهٔ کالای آخرین ردیفِ دست‌خورده
  val buys = remember(d) { ir.vil3ntec.tohid.data.PurchaseOrderBook.buys(d) }
  var touched by remember { mutableStateOf<PurchaseDraft?>(null) }
  var historyFor by remember { mutableStateOf<String?>(null) }

  //  بارکد از بارکدخوان ⇐ ردیف (همان کالا دوباره ⇐ یکی بیشتر)
  LaunchedEffect(pending.size) {
    while (pending.isNotEmpty()) {
      val scan = pending.removeAt(0)
      val id = scan.productId
      val p = d.products.find { it.id == id } ?: continue
      //  PRD-07: بارکدِ واحدِ خرید ⇒ همان واحد؛ بارکدِ کالا ⇒ واحدِ پیش‌فرضِ همین تأمین‌کننده (یا پایه)
      val unit = scan.unit ?: if (unitsOn) ir.vil3ntec.tohid.core.product.PurchaseUnits.defaultFor(p.purchaseUnits?.map(ShopStore::coreOf), supplierId)?.name else null
      val same = lines.find { it.productId == id && it.unitName == unit }
      if (same != null) same.qty = qtyText((same.qty.toDoubleOrNull() ?: 0.0) + 1)
      else {
        val empty = lines.lastOrNull()?.takeIf { it.productId.isBlank() && it.query.isBlank() }
        if (empty != null) lines.remove(empty)
        lines += PurchaseDraft(p, unit)
        lines += PurchaseDraft()
      }
    }
  }

  val total = lines.sumOf { if (it.productId.isNotBlank()) it.total() else 0.0 }
  val paidValue = paid.toDoubleOrNull() ?: 0.0

  fun save() {
    val sid = supplierId ?: return toast(StrPurchasePages.chooseSupplier)
    val ls = lines.filter { it.productId.isNotBlank() }.map {
      val exp = it.expiry.trim()
      PurchaseBook.Line(it.productId, it.qty.toDoubleOrNull() ?: 0.0, it.price.toDoubleOrNull() ?: 0.0,
        expiryDate = if (exp.isEmpty()) "" else ir.vil3ntec.tohid.Jalali.parseTyped(exp) ?: return toast(StrPurchasePages.expiryBad(exp)),
        unitName = if (unitsOn) it.unitName else null)
    }
    val iso = if (date.isBlank()) today else ir.vil3ntec.tohid.Jalali.parseTyped(date) ?: return toast(StrPurchasePages.invoiceDateBad(formatDate(today)))
    val landedValue = if (!stockOn || landed.isBlank()) 0.0 else landed.toDoubleOrNull() ?: return toast(StrPurchasePages.landedBad)
    val user = if (stockOn) runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrNull() else null
    val dueIso = if (!stockOn || due.isBlank()) null else ir.vil3ntec.tohid.Jalali.parseTyped(due) ?: return toast(StrPurchasePages.dueBad(formatDate(today)))
    when (val r = PurchaseBook.record(d, sid, ls, paidValue, iso, notes, today, System.currentTimeMillis(), ::newId, landedTotal = landedValue, userId = user, dueDate = dueIso, key = saveKey)) {
      is PurchaseBook.Result.Failed -> toast(r.message)
      is PurchaseBook.Result.Ok -> {
        scope.launch { store.save(d, r.data) }
        toast(StrPurchasePages.purchaseSaved(r.invoiceNo, money(total)))
        onClose()
      }
    }
  }

  //  کامپیوتر (`claude/e-desk-d3c`): همان فرم روی `ui/desk` — سرخط، کارتِ «مشخصات»، جدولِ ردیف‌ها با نوارِ کلیدها و کارتِ پرداخت؛
  //  کلیدها، ردیف‌ها و `save()` همان پیشین
  val dc = Desk.colors
  val supplierName = d.suppliers.find { it.id == supplierId }?.name
  DeskPage(
    Modifier.onPreviewKeyEvent { e ->
      if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
      when {
        e.key == Key.F9 || (e.isPrimaryModifier && (e.key == Key.Enter || e.key == Key.S)) -> { save(); true }
        e.key == Key.Escape -> { onClose(); true }
        e.isPrimaryModifier && e.key == Key.I -> {
          historyFor = (touched?.takeIf { it in lines && it.productId.isNotBlank() } ?: lines.lastOrNull { it.productId.isNotBlank() })?.productId
          historyFor != null
        }
        else -> false
      }
    },
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      DeskIconButton(Icons.Filled.ArrowForward, Str.back, onClose)
      Spacer(Modifier.width(DeskSpace.x2))
      Text(StrPurchasePages.newPurchaseNo(no), Modifier.weight(1f), style = DeskType.pageTitle, color = dc.ink, maxLines = 1)
      DeskButton(ir.vil3ntec.tohid.core.i18n.StrKit.cancel, onClose, kind = DeskButtonKind.GHOST, shortcut = "Esc")
    }
    //  مشخصات: تأمین‌کننده (کشویی)، «تأمین‌کنندهٔ تازه + Enter» و تاریخِ خورشیدی
    ir.vil3ntec.tohid.ui.desk.DeskCard(Modifier.fillMaxWidth(), title = StrDeskData.invoiceHead) {
      Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3), verticalAlignment = Alignment.Top) {
        DeskFormLabeled(StrMoney.supplierLabel) {
          DeskDropdown(
            supplierId, d.suppliers.map { DeskOption<String?>(it.id, it.name) }, { supplierId = it },
            Modifier.widthIn(min = 260.dp), shown = if (supplierName == null) StrMoney.pickSupplier else null,
          )
        }
        DeskFormLabeled(StrPurchasePages.newSupplierName) {
          DeskTextField(
            newSupplier, { newSupplier = it }, Modifier.width(260.dp), placeholder = StrPurchasePages.orNewSupplier,
            description = StrPurchasePages.orNewSupplier,
            onKey = { e ->
              if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                when (val r = LedgerEngine.addSupplier(d, LedgerEngine.SupplierDraft(name = newSupplier), System.currentTimeMillis(), ::newId)) {
                  is LedgerEngine.Result.Failed -> toast(r.message)
                  is LedgerEngine.Result.Ok -> { scope.launch { store.save(d, r.data) }; supplierId = r.id; newSupplier = "" }
                }
                true
              } else false
            },
          )
        }
        DeskFormLabeled(StrPurchasePages.dateSolar) {
          DeskTextField(date, { date = it }, Modifier.width(180.dp), description = StrPurchasePages.dateSolar)
        }
      }
    }

    //  ردیف‌ها — جدولِ نوشتنی (`DeskPurchaseLineRow`)
    ir.vil3ntec.tohid.ui.desk.DeskCard(Modifier.weight(1f).fillMaxWidth(), title = StrDeskData.invoiceLines, action = { DeskNote(StrDeskData.rowsShown(lines.count { it.productId.isNotBlank() })) }) {
      ir.vil3ntec.tohid.ui.desk.DeskTableHead(purchaseLineColumns())
      LazyColumn(Modifier.weight(1f)) {
        itemsIndexed(lines, key = { _, l -> System.identityHashCode(l) }) { i, l ->
          DeskPurchaseLineRow(i, l, d, ir.vil3ntec.tohid.data.PurchaseOrderBook.lastPriceHint(buys, l.productId, supplierId), unitsOn, supplierId, onTouch = { touched = l }, onNext = {
            if (i == lines.lastIndex) lines += PurchaseDraft()
            scope.launch { kotlinx.coroutines.delay(60); runCatching { lines[i + 1].focus.requestFocus() } }
          }, onRemove = { if (lines.size > 1) lines.remove(l) else { l.productId = ""; l.query = ""; l.qty = ""; l.price = ""; l.unitName = null } })
        }
      }
      Spacer(Modifier.height(DeskSpace.x2))
      DeskPurchaseKeys()
    }

    //  پرداخت و جمع: یادداشت، پرداخت‌شده («همه»)، (موتورِ تازه) هزینهٔ جانبی و سررسید، جمع و «ثبتِ فاکتور»
    ir.vil3ntec.tohid.ui.desk.DeskCard(Modifier.fillMaxWidth()) {
      Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3), verticalAlignment = Alignment.Top) {
        DeskFormLabeled(StrPurchasePages.notesSellerNo, Modifier.weight(1f)) {
          DeskTextField(notes, { notes = it }, Modifier.fillMaxWidth(), description = StrPurchasePages.notesSellerNo)
        }
        DeskFormLabeled(StrPurchasePages.paidAfn) {
          DeskTextField(
            paid, { paid = ir.vil3ntec.tohid.numericInput(it) }, Modifier.width(180.dp), textAlign = TextAlign.End, description = StrPurchasePages.paidAfn,
            trailing = { ir.vil3ntec.tohid.ui.desk.DeskLink(Str.all, { paid = Math.round(total).toString() }) },
          )
        }
        if (stockOn) {
          DeskFormLabeled(StrPurchasePages.landedAfn, Modifier.width(200.dp), note = StrPurchasePages.landedNote) {
            DeskTextField(landed, { landed = ir.vil3ntec.tohid.numericInput(it) }, Modifier.fillMaxWidth(), textAlign = TextAlign.End, description = StrPurchasePages.landedAfn)
          }
          DeskFormLabeled(StrPurchasePages.payDue, Modifier.width(170.dp), note = StrPurchasePages.optionalReminder) {
            DeskTextField(due, { due = it }, Modifier.fillMaxWidth(), description = StrPurchasePages.payDue)
          }
        }
        Column(Modifier.widthIn(min = 220.dp), horizontalAlignment = Alignment.End) {
          Text(StrPurchasePages.invoiceTotal(money(total)), style = DeskType.sectionTitle, color = dc.ink, maxLines = 1)
          Text(StrPurchasePages.toSupplierAccount(money((total - paidValue).coerceAtLeast(0.0))), style = DeskType.secondary, color = dc.negative, maxLines = 1)
          Spacer(Modifier.height(DeskSpace.x2))
          DeskButton(StrPurchasePages.saveInvoiceKey, ::save, kind = DeskButtonKind.PRIMARY, enabled = total > 0 && supplierId != null)
        }
      }
    }
  }
  LaunchedEffect(Unit) { runCatching { lines.first().focus.requestFocus() } }
  historyFor?.let { pid -> PriceHistoryDialog(d, buys, pid, supplierId.orEmpty()) { historyFor = null } }
}

/* ══════════════════════════ تأمین‌کنندگان ══════════════════════════ */

/**
 *  تأمین‌کنندگان — فهرستِ آرام در آغاز (نام، شماره • آخرین خرید، بدهیِ ما) با ردیفِ «تأمین‌کنندهٔ تازه»، و صورت‌حسابِ
 *  تأمین‌کنندهٔ برگزیده در کنار: بدهیِ ما، چهار عددِ آرام، F7 پرداخت (و «برگشت» با دفترِ موجودی)، چاپ و اکسل.
 */
@Composable
private fun ColumnScope.Suppliers(store: ShopStore, d: ShopData, toast: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  var openId by remember { mutableStateOf<String?>(null) }
  val today = ir.vil3ntec.tohid.todayIso()
  val all = rememberDeskLoad(d, today) { PurchaseBook.suppliers(d, today) }.orEmpty()
  val shotPick = DeskDataShot.pick.value
  LaunchedEffect(shotPick, all.isNotEmpty()) { if (shotPick == "first" && openId == null) openId = all.maxByOrNull { it.balance }?.supplier?.id }
  val open = all.find { it.supplier.id == openId }
  val c = Desk.colors

  BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
    val listW = (maxWidth * 0.34f).coerceIn(320.dp, 420.dp)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
      Column(Modifier.width(listW).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        fun add() {
          if (name.isBlank()) return
          when (val r = LedgerEngine.addSupplier(d, LedgerEngine.SupplierDraft(name = name, phone = phone), System.currentTimeMillis(), ::newId)) {
            is LedgerEngine.Result.Failed -> toast(r.message)
            is LedgerEngine.Result.Ok -> { scope.launch { store.save(d, r.data) }; name = ""; phone = "" }
          }
        }
        DeskTextField(name, { name = it }, StrPurchasePages.newSupplierName, Modifier.fillMaxWidth(), onEnter = ::add)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
          DeskTextField(phone, { phone = it }, StrCustomerPages.phone, Modifier.weight(1f), onEnter = ::add)
          DeskButton(StrCustomerPages.addKey, ::add, kind = DeskButtonKind.PRIMARY, enabled = name.isNotBlank())
        }
        if (all.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f).deskCard(c), contentAlignment = Alignment.Center) {
          ir.vil3ntec.tohid.ui.v3.V3EmptyState(Icons.Outlined.Storefront, StrPurchasePages.noBuyOrPay)
        } else LazyColumn(Modifier.fillMaxWidth().weight(1f).deskCard(c).padding(vertical = DeskSpace.x1)) {
          items(all, key = { it.supplier.id }) { s ->
            DeskCalmRow(
              title = s.supplier.name,
              sub = listOf(ir.vil3ntec.tohid.LocaleText.phoneDisplay(s.supplier.phone), s.lastPurchase?.let { ir.vil3ntec.tohid.ui.v3.RowWhen.of(it, today) }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" • ").ifBlank { StrCustomerPages.noPhone },
              amount = if (s.balance > 0.5) "${money(s.balance)} ؋" else StrCustomerPages.settled,
              status = if (s.balance > 0.5) StrMoney.ourDebt else StrCustomerPages.cardStats(s.invoices, money(s.bought)),
              amountColor = if (s.balance > 0.5) c.negative else null,
              selected = s.supplier.id == openId,
              //  کاشیِ حرفِ خنثی — سبز/سرخ این‌جا معنای حسابِ مشتری را می‌گرفت
              leading = { DeskLetterTile(s.supplier.name, c.inkSecondary, c.surfaceAlt) },
              onClick = { openId = s.supplier.id },
            )
          }
        }
      }
      Box(Modifier.weight(1f).fillMaxHeight()) {
        if (open == null) DeskPaneHint(Icons.Outlined.Storefront, StrDeskData.pickSupplier, Modifier.fillMaxSize())
        else key(open.supplier.id) { SupplierFile(store, d, open, today, toast) { openId = null } }
      }
    }
  }
}

/** صورت‌حسابِ یک تأمین‌کننده — پنلِ کنارِ فهرست */
@Composable
private fun SupplierFile(store: ShopStore, d: ShopData, open: PurchaseBook.SupplierSummary, today: String, toast: (String) -> Unit, onClose: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val c = Desk.colors
  var paying by remember { mutableStateOf(false) }
  var returning by remember { mutableStateOf(false) }
  var amount by remember { mutableStateOf("") }
  val stockOn = remember(d.auditLog, ir.vil3ntec.tohid.data.StockLedger.serverFlag) { ir.vil3ntec.tohid.data.StockLedger.isOn(d) }
  val ledger = remember(d, open.supplier.id) { PurchaseBook.ledger(d, open.supplier.id) }
  fun sheet() = PurchaseBook.ledgerSheet(d, open.supplier, today)
  Column(
    Modifier.fillMaxSize().deskCard(c).onPreviewKeyEvent { e ->
      if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
      when {
        e.key == Key.F7 -> { paying = true; true }
        e.key == Key.Escape && !paying && !returning -> { onClose(); true }
        else -> false
      }
    },
  ) {
    Row(Modifier.fillMaxWidth().padding(start = DeskSpace.card, end = DeskSpace.x3, top = DeskSpace.x4, bottom = DeskSpace.x3), verticalAlignment = Alignment.CenterVertically) {
      DeskLetterTile(open.supplier.name, c.inkSecondary, c.surfaceAlt, size = DeskSize.tileLarge)
      Spacer(Modifier.width(DeskSpace.x3))
      Column(Modifier.weight(1f)) {
        Text(open.supplier.name, style = DeskType.sectionTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOf(ir.vil3ntec.tohid.LocaleText.phoneDisplay(open.supplier.phone), open.supplier.address).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { StrCustomerPages.noPhone },
          style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = DeskSpace.x3)) {
        Text(StrMoney.ourDebt, style = DeskType.label, color = c.inkSecondary, maxLines = 1)
        Text("${money(open.balance)} ؋", style = DeskType.kpiSmall, color = if (open.balance > 0.5) c.negative else c.positive, maxLines = 1)
      }
      DeskIconButton(Icons.Outlined.Close, Str.close, onClose)
    }
    DeskDivider()
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = DeskSpace.card, vertical = DeskSpace.x4), verticalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
      DeskNote(
        listOfNotNull(
          StrSimpleAsk.vPurchaseTotal + ": " + money(open.bought) + " ؋",
          Str.invoices + ": " + ir.vil3ntec.tohid.plain(open.invoices),
          StrPriceCheck.lastPurchase + ": " + (open.lastPurchase?.let { formatDate(it) } ?: "—"),
          if (stockOn && open.overdue > 0.5) StrPurchasePages.overdue + ": " + money(open.overdue) + " ؋" else null,
          if (open.returned > 0.5) StrPurchasePages.creditNoteReturn + ": " + money(open.returned) + " ؋" else null,
        ).joinToString(" • "),
      )
      DeskToolbar(
        end = {
          DeskButton(StrHistory.print, { sheet().let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } }, icon = Icons.Outlined.Print)
          DeskButton(StrReports.excel, { val sh = sheet(); Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast(StrSale.savedTo(it)) } }, icon = Icons.Outlined.TableChart)
        },
      ) {
        DeskButton(StrPurchasePages.paySupplierKey, { paying = true }, kind = DeskButtonKind.PRIMARY)
        if (stockOn) DeskButton(StrPurchasePages.returnCredit, { returning = true })
      }
      if (returning) {
        SupplierReturnDialog(d, open.supplier.id, onDismiss = { returning = false }) { productId, q, price, reason, date, key ->
          when (val r = PurchaseBook.supplierReturn(
            d, open.supplier.id, productId, q, price, reason, date, today, System.currentTimeMillis(), ::newId,
            userId = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrNull(), key = key,
          )) {
            is PurchaseBook.Result.Failed -> toast(r.message)
            is PurchaseBook.Result.Ok -> { scope.launch { store.save(d, r.data) }; returning = false; toast(StrReach.returnSaved) }
          }
        }
      }
      if (paying) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        //  PUR-07: یک کلید برای همین ردیفِ پرداخت — Enter ِ پشتِ‌سرِهم یک پرداخت است
        val payKey = remember { newId() }
        fun pay() {
          val a = amount.toDoubleOrNull() ?: return
          when (val r = LedgerEngine.paySupplier(d, open.supplier.id, a, today, StrHistory.payment, today, System.currentTimeMillis(), ::newId, key = payKey)) { // داده: یادداشتِ ردیفِ دفترِ تأمین‌کننده (داده)
            is LedgerEngine.Result.Failed -> toast(r.message)
            is LedgerEngine.Result.Ok -> { scope.launch { store.save(d, r.data) }; paying = false; amount = ""; toast(StrPurchasePages.paymentSaved(money(a))) }
          }
        }
        Row(
          Modifier.fillMaxWidth().clip(DeskRadius.controlShape).background(c.positiveSoft).border(DeskSize.hairline, c.positive, DeskRadius.controlShape).padding(DeskSpace.x2),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2),
        ) {
          Text(StrPurchasePages.payTo(open.supplier.name), Modifier.weight(1f), style = DeskType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
          DeskTextField(amount, { amount = ir.vil3ntec.tohid.numericInput(it) }, StrCustomerPages.amountAfn, Modifier.width(180.dp), focus, onEnter = ::pay, onEscape = { paying = false })
          DeskButton(StrCustomerPages.saveKey, ::pay, kind = DeskButtonKind.PRIMARY, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0)
          DeskButton(Str.cancel, { paying = false }, kind = DeskButtonKind.GHOST, shortcut = "Esc")
        }
      }
      val cols = listOf(
        DeskColumn(StrCustomerPages.colRow, width = 36.dp), DeskColumn(StrInvoices.colDate, width = 96.dp), DeskColumn(StrReports.note, weight = 1f),
        DeskColumn(Str.purchases, width = 100.dp, end = true), DeskColumn(StrHistory.payment, width = 100.dp, end = true), DeskColumn(StrSimpleAsk.vDebtRemaining, width = 104.dp, end = true),
      )
      DeskGrid(
        cols, ledger.size, Modifier.fillMaxWidth().weight(1f),
        rowLabel = { ledger[it].text },
        footer = listOf("", "", StrInvoices.total, money(ledger.sumOf { it.bill }), money(ledger.sumOf { it.pay }), money(open.balance)),
        empty = { ir.vil3ntec.tohid.ui.v3.V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, StrPurchasePages.noBuyOrPay) },
      ) { i, col ->
        val l = ledger[i]
        when (col) {
          0 -> DeskCell(ir.vil3ntec.tohid.plain(l.row), muted = true)
          1 -> DeskCell(formatDate(l.date), muted = true)
          2 -> DeskCell(l.text)
          3 -> DeskCell(if (l.bill > 0) money(l.bill) else "")
          4 -> DeskCell(if (l.pay > 0) money(l.pay) else "", color = c.positive)
          else -> DeskCell(money(l.balance), strong = true)
        }
      }
    }
  }
}
