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
  val pending = remember { mutableStateListOf<String>() }

  //  بارکدخوان: کالا به فاکتورِ باز؛ اگر بسته است، فاکتورِ تازه باز می‌شود
  val latest by rememberUpdatedState(d)
  val owner = remember { Any() }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.scan.ScanRouter.register(owner) { code ->
      val id = ShopStore.barcodeIndex(latest)[code]
      if (id == null) { toast("بارکدِ «$code» ثبت نشده — اول در «کالاها» بسازید"); true }
      else { pending += id; editing = true; true }
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

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.isPrimaryModifier && e.key == Key.N) { editing = true; true } else false
      }
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      TabRow(selectedTabIndex = tab, containerColor = Shop.colors.surface, modifier = Modifier.weight(1f)) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("فاکتورهای خرید") })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("تأمین‌کنندگان") })
      }
      Spacer(Modifier.width(12.dp))
      Button(onClick = { editing = true }) { Text("+ فاکتورِ خریدِ تازه  Ctrl+N") }
    }
    Spacer(Modifier.height(12.dp))
    if (tab == 0) PurchaseInvoices(d, ::toast) else Suppliers(store, d, ::toast)
  }
}

/* ══════════════════════════ فهرستِ فاکتورها ══════════════════════════ */

@Composable
private fun ColumnScope.PurchaseInvoices(d: ShopData, toast: (String) -> Unit) {
  val context = LocalContext.current
  var supplier by remember { mutableStateOf<String?>(null) }
  var menu by remember { mutableStateOf(false) }
  var picked by remember { mutableStateOf<String?>(null) }
  val list = remember(d, supplier) { PurchaseBook.invoices(d, supplier) }
  //  ماندهٔ واقعیِ هر فاکتور (پرداخت‌های بعدی و سندِ کسر FIFO) — برای رنگِ سررسید
  val remaining = remember(d) { PurchaseBook.open(d).associate { it.invoice.key to it.remaining } }
  val names = remember(d) { d.suppliers.associate { it.id to it.name } }
  val today = ir.vil3ntec.tohid.todayIso()
  val month = today.take(7)

  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    DeskKpi("خریدِ این ماه", "${money(d.purchases.filter { it.date.startsWith(month) }.sumOf { it.totalAmount })} ؋", Shop.colors.primary, Modifier.weight(1f))
    DeskKpi("بدهیِ ما به تأمین‌کنندگان", "${money(d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id).coerceAtLeast(0.0) })} ؋", Shop.colors.danger, Modifier.weight(1f))
    DeskKpi("فاکتورهای خرید", "${list.size}", Shop.colors.text, Modifier.weight(1f))
    DeskKpi("تأمین‌کنندگان", "${d.suppliers.size}", Shop.colors.text, Modifier.weight(1f))
  }
  Spacer(Modifier.height(10.dp))
  //  یادآورِ سررسید (PUR-07) — فقط وقتی فاکتوری سررسید دارد
  SupplierDueReminder(d, today) { supplier = it }
  Spacer(Modifier.height(6.dp))
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box {
      OutlinedButton(onClick = { menu = true }) { Text("تأمین‌کننده: ${supplier?.let { names[it] } ?: "همه"}") }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text("همه") }, onClick = { supplier = null; menu = false })
        d.suppliers.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { supplier = s.id; menu = false }) }
      }
    }
    Spacer(Modifier.weight(1f))
    OutlinedButton(onClick = {
      val sh = PurchaseBook.invoicesSheet(d, list, today)
      Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast("ذخیره شد: $it") }
    }) { Text("اکسلِ فهرست") }
  }
  Spacer(Modifier.height(8.dp))
  Row(Modifier.weight(1f)) {
    val cols = listOf(TCol("فاکتور", 80.dp), TCol("تاریخ", 110.dp), TCol("تأمین‌کننده"), TCol("ردیف‌ها", 70.dp, true), TCol("جمع", 130.dp, true), TCol("پرداخت‌شده", 130.dp, true), TCol("مانده", 130.dp, true))
    DeskTable(
      cols, list.size, Modifier.weight(1f),
      empty = "هنوز خریدی ثبت نشده — «فاکتورِ خریدِ تازه» (Ctrl+N).",
      selected = list.indexOfFirst { it.key == picked }.takeIf { it >= 0 },
      cell = { i, c ->
        val v = list[i]
        when (c) {
          0 -> if (v.isReturn) TCell("برگشت", Shop.colors.success, bold = true) else TCell(if (v.no > 0) "#${v.no}" else "—", Shop.colors.primary, bold = true)
          1 -> TCell(formatDate(v.date))
          2 -> TCell(names[v.supplierId] ?: "—")
          3 -> TCell(v.lines.size.toString())
          4 -> TCell(money(v.total), bold = true)
          5 -> TCell(money(v.paid), Shop.colors.success)
          else -> TCell(if (v.debt > 0.5) money(v.debt) else "", Shop.colors.danger)
        }
      },
      onClick = { picked = list[it].key },
      footer = listOf("جمع", "", "", "", money(list.sumOf { it.total }), money(list.sumOf { it.paid }), money(list.sumOf { it.debt })),
    )
    list.find { it.key == picked }?.let { inv ->
      Spacer(Modifier.width(12.dp))
      Column(
        Modifier.width(380.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Shop.colors.surface)
          .border(1.dp, Shop.colors.border, RoundedCornerShape(12.dp)).padding(14.dp),
      ) {
        Text(if (inv.isReturn) "برگشت به تأمین‌کننده (سندِ کسر)" else if (inv.no > 0) "فاکتورِ خرید #${inv.no}" else "خرید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        inv.dueDate?.takeIf { !inv.isReturn }?.let { due ->
          Text("سررسید ${formatDate(due)}", style = MaterialTheme.typography.labelSmall, color = if (due < today && (remaining[inv.key] ?: 0.0) > 0.5) Shop.colors.danger else Shop.colors.muted)
        }
        Text("${names[inv.supplierId] ?: "—"} · ${formatDate(inv.date)}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        HeadRow(listOf("کالا" to 0.dp, "مقدار" to 60.dp, "جمع" to 100.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().border(1.dp, Shop.colors.border)) {
          itemsIndexed(inv.lines, key = { _, p -> p.id }) { i, p ->
            Row(
              Modifier.fillMaxWidth().background(if (i % 2 == 1) Shop.colors.surface2.copy(alpha = 0.45f) else Shop.colors.surface).padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text(d.products.find { it.id == p.productId }?.name ?: "(کالای حذف‌شده)", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Shop.colors.text)
                Text("فی ${money(p.purchasePrice)} · ${p.unit}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
              }
              Text(qtyText(p.quantity), Modifier.width(60.dp), textAlign = TextAlign.End)
              Text(money(p.totalAmount), Modifier.width(100.dp), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
            }
          }
        }
        Spacer(Modifier.height(8.dp))
        Row { Text("جمع", Modifier.weight(1f), color = Shop.colors.muted); Text("${money(inv.total)} ؋", fontWeight = FontWeight.Bold) }
        Row { Text("پرداخت‌شده", Modifier.weight(1f), color = Shop.colors.muted); Text(money(inv.paid), color = Shop.colors.success) }
        Row { Text("مانده به حسابِ تأمین‌کننده", Modifier.weight(1f), color = Shop.colors.muted); Text(money(inv.debt), color = Shop.colors.danger, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        Row {
          Button(onClick = { PurchaseBook.invoiceSheet(d, inv).let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } }, modifier = Modifier.weight(1f)) { Text("چاپ") }
          Spacer(Modifier.width(6.dp))
          OutlinedButton(onClick = {
            val sh = PurchaseBook.invoiceSheet(d, inv)
            Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast("ذخیره شد: $it") }
          }, modifier = Modifier.weight(1f)) { Text("اکسل") }
        }
      }
    }
  }
}

internal fun qtyText(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

/* ══════════════════════════ فاکتورِ خریدِ تازه ══════════════════════════ */

private class Draft(product: Product? = null) {
  var productId by mutableStateOf(product?.id.orEmpty())
  var query by mutableStateOf(product?.name.orEmpty())
  var qty by mutableStateOf(if (product != null) "1" else "")
  var price by mutableStateOf(product?.purchasePrice?.takeIf { it > 0 }?.let { Math.round(it).toString() }.orEmpty())
  var expiry by mutableStateOf("")
  val focus = FocusRequester()
  val qtyFocus = FocusRequester()
  val priceFocus = FocusRequester()
  fun total(): Double = Math.round((qty.toDoubleOrNull() ?: 0.0) * (price.toDoubleOrNull() ?: 0.0)).toDouble()
}

@Composable
private fun NewPurchaseInvoice(
  store: ShopStore,
  d: ShopData,
  pending: MutableList<String>,
  toast: (String) -> Unit,
  onClose: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val today = ir.vil3ntec.tohid.todayIso()
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
  val lines = remember { mutableStateListOf(Draft()) }
  val no = remember(d) { PurchaseBook.nextNo(d) }

  //  بارکد از بارکدخوان ⇐ ردیف (همان کالا دوباره ⇐ یکی بیشتر)
  LaunchedEffect(pending.size) {
    while (pending.isNotEmpty()) {
      val id = pending.removeAt(0)
      val p = d.products.find { it.id == id } ?: continue
      val same = lines.find { it.productId == id }
      if (same != null) same.qty = qtyText((same.qty.toDoubleOrNull() ?: 0.0) + 1)
      else {
        val empty = lines.lastOrNull()?.takeIf { it.productId.isBlank() && it.query.isBlank() }
        if (empty != null) lines.remove(empty)
        lines += Draft(p)
        lines += Draft()
      }
    }
  }

  val total = lines.sumOf { if (it.productId.isNotBlank()) it.total() else 0.0 }
  val paidValue = paid.toDoubleOrNull() ?: 0.0

  fun save() {
    val sid = supplierId ?: return toast("تأمین‌کننده را انتخاب کنید")
    val ls = lines.filter { it.productId.isNotBlank() }.map {
      val exp = it.expiry.trim()
      PurchaseBook.Line(it.productId, it.qty.toDoubleOrNull() ?: 0.0, it.price.toDoubleOrNull() ?: 0.0,
        expiryDate = if (exp.isEmpty()) "" else ir.vil3ntec.tohid.Jalali.parseTyped(exp) ?: return toast("تاریخِ انقضای «$exp» درست نیست — مثلاً ۱۴۰۶/۰۳/۳۱"))
    }
    val iso = if (date.isBlank()) today else ir.vil3ntec.tohid.Jalali.parseTyped(date) ?: return toast("تاریخِ فاکتور درست نیست — مثلاً ${formatDate(today)}")
    val landedValue = if (!stockOn || landed.isBlank()) 0.0 else landed.toDoubleOrNull() ?: return toast("هزینهٔ جانبی درست نیست")
    val user = if (stockOn) runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrNull() else null
    val dueIso = if (!stockOn || due.isBlank()) null else ir.vil3ntec.tohid.Jalali.parseTyped(due) ?: return toast("سررسید درست نیست — مثلاً ${formatDate(today)}")
    when (val r = PurchaseBook.record(d, sid, ls, paidValue, iso, notes, today, System.currentTimeMillis(), ::newId, landedTotal = landedValue, userId = user, dueDate = dueIso)) {
      is PurchaseBook.Result.Failed -> toast(r.message)
      is PurchaseBook.Result.Ok -> {
        scope.launch { store.save(r.data) }
        toast("فاکتورِ خرید #${r.invoiceNo} ثبت شد — ${money(total)} ؋، کالاها به انبار رفت")
        onClose()
      }
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          e.key == Key.F9 || (e.isPrimaryModifier && (e.key == Key.Enter || e.key == Key.S)) -> { save(); true }
          e.key == Key.Escape -> { onClose(); true }
          else -> false
        }
      }
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowForward, "برگشت", tint = Shop.colors.text) }
      Text("فاکتورِ خریدِ تازه #$no", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      Spacer(Modifier.weight(1f))
      Text("Enter ردیفِ بعد · F9 ثبت · Esc انصراف · بارکدخوان ردیف می‌افزاید", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box {
        OutlinedButton(onClick = { supplierMenu = true }, modifier = Modifier.width(280.dp)) {
          Text("تأمین‌کننده: " + (d.suppliers.find { it.id == supplierId }?.name ?: "انتخاب کنید"), maxLines = 1)
        }
        DropdownMenu(expanded = supplierMenu, onDismissRequest = { supplierMenu = false }) {
          d.suppliers.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { supplierId = s.id; supplierMenu = false }) }
        }
      }
      Spacer(Modifier.width(8.dp))
      OutlinedTextField(
        newSupplier, { newSupplier = it }, singleLine = true, label = { Text("یا تأمین‌کنندهٔ تازه + Enter") },
        modifier = Modifier.width(240.dp).onPreviewKeyEvent { e ->
          if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
            when (val r = LedgerEngine.addSupplier(d, LedgerEngine.SupplierDraft(name = newSupplier), System.currentTimeMillis(), ::newId)) {
              is LedgerEngine.Result.Failed -> toast(r.message)
              is LedgerEngine.Result.Ok -> { scope.launch { store.save(r.data) }; supplierId = r.id; newSupplier = "" }
            }
            true
          } else false
        },
      )
      Spacer(Modifier.width(8.dp))
      OutlinedTextField(date, { date = it }, singleLine = true, label = { Text("تاریخ (خورشیدی)") }, modifier = Modifier.width(200.dp))
    }
    Spacer(Modifier.height(10.dp))

    //  ردیف‌ها — جدولِ نوشتنی
    val line = Shop.colors.border
    Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, line, RoundedCornerShape(8.dp))) {
      Row(Modifier.fillMaxWidth().background(Shop.colors.surface2).padding(horizontal = 8.dp, vertical = 8.dp)) {
        listOf("#" to 36.dp, "کالا (نام یا بارکد)" to 0.dp, "مقدار" to 100.dp, "فی (؋)" to 130.dp, "انقضا (اختیاری)" to 150.dp, "جمع" to 130.dp, "" to 40.dp).forEach { (t, w) ->
          Text(t, if (w == 0.dp) Modifier.weight(1f) else Modifier.width(w), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        }
      }
      LazyColumn(Modifier.weight(1f)) {
        itemsIndexed(lines, key = { _, l -> System.identityHashCode(l) }) { i, l ->
          LineRow(i, l, d, onNext = {
            if (i == lines.lastIndex) lines += Draft()
            scope.launch { kotlinx.coroutines.delay(60); runCatching { lines[i + 1].focus.requestFocus() } }
          }, onRemove = { if (lines.size > 1) lines.remove(l) else { l.productId = ""; l.query = ""; l.qty = ""; l.price = "" } })
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(notes, { notes = it }, singleLine = true, label = { Text("یادداشت / شمارهٔ فاکتورِ فروشنده") }, modifier = Modifier.weight(1f))
      Spacer(Modifier.width(10.dp))
      OutlinedTextField(paid, { paid = ir.vil3ntec.tohid.numericInput(it) }, singleLine = true, label = { Text("پرداخت‌شده (؋)") }, modifier = Modifier.width(170.dp))
      TextButton(onClick = { paid = Math.round(total).toString() }) { Text("همه") }
      if (stockOn) {
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
          landed, { landed = ir.vil3ntec.tohid.numericInput(it) }, singleLine = true,
          label = { Text("هزینهٔ جانبی (؋)") },
          supportingText = { Text("کرایه، گمرک — روی بهای ردیف‌ها؛ جدا در «مصارف» ثبتش نکنید") },
          modifier = Modifier.width(190.dp),
        )
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
          due, { due = it }, singleLine = true,
          label = { Text("سررسیدِ پرداخت") },
          supportingText = { Text("اختیاری — یادآور") },
          modifier = Modifier.width(160.dp),
        )
      }
      Spacer(Modifier.width(10.dp))
      Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(220.dp)) {
        Text("جمعِ فاکتور: ${money(total)} ؋", fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Text("به حسابِ تأمین‌کننده: ${money((total - paidValue).coerceAtLeast(0.0))} ؋", color = Shop.colors.danger, style = MaterialTheme.typography.bodySmall)
      }
      Spacer(Modifier.width(10.dp))
      Button(onClick = ::save, enabled = total > 0 && supplierId != null, modifier = Modifier.height(52.dp)) { Text("ثبتِ فاکتور  F9", fontWeight = FontWeight.Bold) }
    }
  }
  LaunchedEffect(Unit) { runCatching { lines.first().focus.requestFocus() } }
}

@Composable
private fun LineRow(i: Int, l: Draft, d: ShopData, onNext: () -> Unit, onRemove: () -> Unit) {
  var menu by remember { mutableStateOf(false) }
  val index = remember(d) { ShopStore.barcodeIndex(d) }
  val matches = remember(l.query, d) {
    val q = l.query.trim()
    if (q.isEmpty() || l.productId.isNotBlank()) emptyList()
    else d.products.filter { it.name.contains(q, true) || it.barcodes.any { b -> b == q } }.take(8)
  }
  fun pick(p: Product) {
    l.productId = p.id; l.query = p.name; menu = false
    if (l.qty.isBlank()) l.qty = "1"
    if (l.price.isBlank() && p.purchasePrice > 0) l.price = Math.round(p.purchasePrice).toString()
    runCatching { l.qtyFocus.requestFocus() }
  }
  fun enterOn(next: () -> Unit) = Modifier.onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { next(); true } else false
  }
  Row(
    Modifier.fillMaxWidth().background(if (i % 2 == 1) Shop.colors.surface2.copy(alpha = 0.35f) else Shop.colors.surface).padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("${i + 1}", Modifier.width(36.dp), color = Shop.colors.muted)
    Box(Modifier.weight(1f)) {
      OutlinedTextField(
        l.query,
        { l.query = it; l.productId = ""; menu = true },
        singleLine = true,
        isError = l.query.isNotBlank() && l.productId.isBlank() && matches.isEmpty(),
        modifier = Modifier.fillMaxWidth().focusRequester(l.focus).then(enterOn {
          val q = l.query.trim()
          val byCode = index[q]?.let { id -> d.products.find { it.id == id } }
          (byCode ?: matches.firstOrNull())?.let(::pick)
        }),
      )
      DropdownMenu(expanded = menu && matches.isNotEmpty(), onDismissRequest = { menu = false }) {
        matches.forEach { p ->
          DropdownMenuItem(
            text = { Text("${p.name}  ·  موجودی ${qtyText(ShopStore.stock(d, p.id))} ${p.unit}") },
            onClick = { pick(p) },
          )
        }
      }
    }
    Spacer(Modifier.width(6.dp))
    OutlinedTextField(l.qty, { l.qty = ir.vil3ntec.tohid.numericInput(it) }, singleLine = true,
      modifier = Modifier.width(94.dp).focusRequester(l.qtyFocus).then(enterOn { runCatching { l.priceFocus.requestFocus() } }))
    Spacer(Modifier.width(6.dp))
    OutlinedTextField(l.price, { l.price = ir.vil3ntec.tohid.numericInput(it) }, singleLine = true,
      modifier = Modifier.width(124.dp).focusRequester(l.priceFocus).then(enterOn(onNext)))
    Spacer(Modifier.width(6.dp))
    OutlinedTextField(l.expiry, { l.expiry = it.take(10) }, singleLine = true, placeholder = { Text("۱۴۰۶/۰۳/۳۱") },
      modifier = Modifier.width(144.dp).then(enterOn(onNext)))
    Text(if (l.productId.isNotBlank()) money(l.total()) else "", Modifier.width(130.dp), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
    IconButton(onClick = onRemove, modifier = Modifier.width(40.dp)) { Icon(Icons.Filled.Close, "حذفِ ردیف", tint = Shop.colors.muted) }
  }
}

/* ══════════════════════════ تأمین‌کنندگان ══════════════════════════ */

@Composable
private fun ColumnScope.Suppliers(store: ShopStore, d: ShopData, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var openId by remember { mutableStateOf<String?>(null) }
  val today = ir.vil3ntec.tohid.todayIso()
  val all = remember(d, today) { PurchaseBook.suppliers(d, today) }
  val open = all.find { it.supplier.id == openId }

  if (open == null) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    fun add() {
      when (val r = LedgerEngine.addSupplier(d, LedgerEngine.SupplierDraft(name = name, phone = phone), System.currentTimeMillis(), ::newId)) {
        is LedgerEngine.Result.Failed -> toast(r.message)
        is LedgerEngine.Result.Ok -> { scope.launch { store.save(r.data) }; name = ""; phone = "" }
      }
    }
    val enter = Modifier.onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { add(); true } else false }
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("تأمین‌کنندهٔ تازه — نام") }, modifier = Modifier.width(280.dp).then(enter))
      Spacer(Modifier.width(6.dp))
      OutlinedTextField(phone, { phone = it }, singleLine = true, label = { Text("تلفن") }, modifier = Modifier.width(170.dp).then(enter))
      Spacer(Modifier.width(6.dp))
      Button(onClick = ::add, enabled = name.isNotBlank()) { Text("افزودن ↵") }
    }
    Spacer(Modifier.height(10.dp))
    LazyVerticalGrid(
      columns = GridCells.Adaptive(210.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.weight(1f),
    ) {
      items(all, key = { it.supplier.id }) { s ->
        val tint = if (s.balance > 0.5) Shop.colors.danger else Shop.colors.success
        Column(
          Modifier.aspectRatio(1.15f).clip(RoundedCornerShape(14.dp)).background(Shop.colors.surface)
            .border(1.dp, Shop.colors.border, RoundedCornerShape(14.dp)).clickable { openId = s.supplier.id }.padding(14.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Shop.colors.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
              Text(s.supplier.name.take(1), fontWeight = FontWeight.Bold, color = Shop.colors.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column {
              Text(s.supplier.name, fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(ir.vil3ntec.tohid.LocaleText.phoneDisplay(s.supplier.phone).ifBlank { "بی تلفن" }, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
            }
          }
          Spacer(Modifier.weight(1f))
          Text(if (s.balance > 0.5) "بدهیِ ما" else "بی‌حساب", style = MaterialTheme.typography.labelMedium, color = tint)
          Text("${money(s.balance.coerceAtLeast(0.0))} ؋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint)
          Spacer(Modifier.weight(1f))
          Text("${s.invoices} فاکتور · ${money(s.bought)} ؋" + (s.lastPurchase?.let { " · ${formatDate(it)}" } ?: ""), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, maxLines = 1)
        }
      }
    }
    return
  }

  var paying by remember(openId) { mutableStateOf(false) }
  var returning by remember(openId) { mutableStateOf(false) }
  var amount by remember(openId) { mutableStateOf("") }
  val stockOn = remember(d.auditLog, ir.vil3ntec.tohid.data.StockLedger.serverFlag) { ir.vil3ntec.tohid.data.StockLedger.isOn(d) }
  val ledger = remember(d, openId) { PurchaseBook.ledger(d, open.supplier.id) }
  fun sheet() = PurchaseBook.ledgerSheet(d, open.supplier, today)
  Column(
    Modifier.weight(1f).onPreviewKeyEvent { e ->
      if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
      when {
        e.key == Key.F7 -> { paying = true; true }
        e.key == Key.Escape && !paying -> { openId = null; true }
        else -> false
      }
    },
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = { openId = null }) { Icon(Icons.Filled.ArrowForward, "برگشت", tint = Shop.colors.text) }
      Text(open.supplier.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      Spacer(Modifier.width(12.dp))
      Text(ir.vil3ntec.tohid.LocaleText.phoneDisplay(open.supplier.phone), color = Shop.colors.muted)
      Spacer(Modifier.weight(1f))
      Button(onClick = { paying = true }) { Text("پرداخت به تأمین‌کننده  F7") }
      if (stockOn) {
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { returning = true }) { Text("برگشت (سندِ کسر)") }
      }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = { sheet().let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } }) { Text("چاپ") }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = { val sh = sheet(); Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast("ذخیره شد: $it") } }) { Text("اکسل") }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      DeskKpi("بدهیِ ما", "${money(open.balance)} ؋", if (open.balance > 0.5) Shop.colors.danger else Shop.colors.success, Modifier.weight(1f))
      DeskKpi("جمعِ خرید", "${money(open.bought)} ؋", Shop.colors.primary, Modifier.weight(1f))
      DeskKpi("فاکتورها", "${open.invoices}", Shop.colors.text, Modifier.weight(1f))
      DeskKpi("آخرین خرید", open.lastPurchase?.let { formatDate(it) } ?: "—", Shop.colors.text, Modifier.weight(1f))
      if (stockOn && open.overdue > 0.5) DeskKpi("سررسیدگذشته", "${money(open.overdue)} ؋", Shop.colors.danger, Modifier.weight(1f))
      if (open.returned > 0.5) DeskKpi("سندِ کسر (برگشت)", "${money(open.returned)} ؋", Shop.colors.success, Modifier.weight(1f))
    }
    if (returning) {
      SupplierReturnDialog(d, open.supplier.id, onDismiss = { returning = false }) { productId, q, price, reason, date, key ->
        when (val r = PurchaseBook.supplierReturn(
          d, open.supplier.id, productId, q, price, reason, date, today, System.currentTimeMillis(), ::newId,
          userId = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrNull(), key = key,
        )) {
          is PurchaseBook.Result.Failed -> toast(r.message)
          is PurchaseBook.Result.Ok -> { scope.launch { store.save(r.data) }; returning = false; toast("برگشت و سندِ کسر ثبت شد") }
        }
      }
    }
    if (paying) {
      Spacer(Modifier.height(8.dp))
      val focus = remember { FocusRequester() }
      LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
      fun pay() {
        val a = amount.toDoubleOrNull() ?: return
        when (val r = LedgerEngine.paySupplier(d, open.supplier.id, a, today, "پرداخت", today, System.currentTimeMillis(), ::newId)) {
          is LedgerEngine.Result.Failed -> toast(r.message)
          is LedgerEngine.Result.Ok -> { scope.launch { store.save(r.data) }; paying = false; amount = ""; toast("پرداختِ ${money(a)} ؋ ثبت شد") }
        }
      }
      Surface(color = Shop.colors.success.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("پرداخت به «${open.supplier.name}»", color = Shop.colors.text)
          Spacer(Modifier.width(8.dp))
          OutlinedTextField(amount, { amount = ir.vil3ntec.tohid.numericInput(it) }, singleLine = true, label = { Text("مبلغ (؋)") },
            modifier = Modifier.width(180.dp).focusRequester(focus).onPreviewKeyEvent { e ->
              if (e.type != KeyEventType.KeyDown) false
              else when (e.key) { Key.Enter, Key.NumPadEnter -> { pay(); true }; Key.Escape -> { paying = false; true }; else -> false }
            })
          Spacer(Modifier.width(6.dp))
          Button(onClick = ::pay, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0) { Text("ثبت ↵") }
          TextButton(onClick = { paying = false }) { Text("Esc") }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    val cols = listOf(TCol("ردیف", 56.dp), TCol("تاریخ", 110.dp), TCol("شرح"), TCol("خرید", 140.dp, true), TCol("پرداخت", 140.dp, true), TCol("مانده", 140.dp, true))
    DeskTable(
      cols, ledger.size, Modifier.weight(1f),
      empty = "هنوز خرید یا پرداختی نیست.",
      cell = { i, c ->
        val l = ledger[i]
        when (c) {
          0 -> TCell(l.row.toString(), Shop.colors.muted)
          1 -> TCell(formatDate(l.date))
          2 -> TCell(l.text)
          3 -> TCell(if (l.bill > 0) money(l.bill) else "", Shop.colors.danger)
          4 -> TCell(if (l.pay > 0) money(l.pay) else "", Shop.colors.success)
          else -> TCell(money(l.balance), bold = true)
        }
      },
      footer = listOf("", "", "جمع", money(ledger.sumOf { it.bill }), money(ledger.sumOf { it.pay }), money(open.balance)),
    )
  }
}
