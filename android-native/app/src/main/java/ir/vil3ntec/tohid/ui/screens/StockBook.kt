package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.i18n.*
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockActions
import ir.vil3ntec.tohid.data.StockCountDraftStore
import ir.vil3ntec.tohid.data.StockLedger
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.common.BulkAction
import ir.vil3ntec.tohid.ui.components.common.BulkActionBar
import ir.vil3ntec.tohid.ui.components.common.FilterMenu
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.SearchBox
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatCard
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.components.common.TableToolbar
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 *  «دفترِ موجودی» — سیم‌کشیِ هستهٔ موجودی و بها (PRD-15/16، INV-02/04/05/09/10/18/19/20، PUR-06).
 *  مشترکِ گوشی و کامپیوتر. همهٔ عددها از `StockLedger` (روی `core/stock/StockCore`)
 *  می‌آیند و همهٔ نوشتن‌ها از `StockActions`؛ این‌جا فقط نشان داده و پرسیده می‌شود.
 *
 *  ⛔ پشتِ تنظیمِ دکان (`stock_mode`، پیش‌فرض خاموش). خاموش ⇒ این صفحه فقط می‌گوید
 *  کجا روشنش کنند؛ هیچ عدد یا کاری نشان نمی‌دهد.
 */

/**
 *  درخواست‌های بیرون از صفحه: زبانهٔ «دفترِ موجودی» (دکمهٔ «انبارگردانی»ِ انبار ⇐ "count")
 *  و فرمان‌های `DevShot` برای عکس‌های CIِ کامپیوتر (`enable`، `count` — در برنامهٔ دستِ
 *  کاربر هرگز تنظیم نمی‌شوند).
 */
object StockBookNav {
  /** "pos" · "ledger" · "review" · "count" */
  val tab = mutableStateOf<String?>(null)
  /** فقط DevShot: روشن کردنِ تنظیم با همان راهِ کارتِ تنظیمات */
  val enable = mutableStateOf(false)
  /** فقط DevShot: شروعِ شمارش و پر کردنِ n کالای اول با «موجودی − ۱» */
  val count = mutableStateOf(0)
  /** باز کردنِ پنجرهٔ انتقال (DevShot) */
  val transfer = mutableStateOf(false)
}

private enum class BookTab(val id: String, private val text: () -> String) {
  POS("pos", { StrStockPages.tabPositions }), LEDGER("ledger", { StrStockPages.tabLedger }), REVIEW("review", { StrOnline.ffReview }), COUNT("count", { StrSafe.count }); val label: String get() = text()
}

private fun minorText(minor: Long): String {
  val neg = minor < 0
  val a = kotlin.math.abs(minor)
  val whole = ir.vil3ntec.tohid.money((a / 100).toDouble())
  val frac = (a % 100).toInt()
  return (if (neg) "−" else "") + whole + if (frac == 0) "" else "." + frac.toString().padStart(2, '0').trimEnd('0')
}

private fun milliText(milli: Long): String = (if (milli < 0) "−" else "") + qty(kotlin.math.abs(milli) / 1000.0)

@Composable
fun StockBookScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  onSettings: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val on = remember(d.auditLog, StockLedger.serverFlag) { StockLedger.isOn(d) }
  val user = remember(context) { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  fun save(next: ShopData, done: String) { scope.launch { store.save(d, next) }; toast(done) }

  //  عکسِ CI: روشن کردن با همان راهِ کارتِ تنظیمات
  val shotEnable by StockBookNav.enable
  LaunchedEffect(shotEnable, on) {
    if (shotEnable && !on && StockLedger.serverFlag) store.save(d, StockActions.setMode(d, true, user, todayIso(), System.currentTimeMillis(), ::newId))
  }

  if (!on) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
      Panel(Modifier.widthIn(max = 560.dp)) {
        Text(StrStockPages.engineOff, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(6.dp))
        Text(
          if (StockLedger.serverFlag) StrStockPages.engineOffBody
          else StrStockPages.notOpenYet,
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
        //  پرچمِ `shop.stock_v2` خاموش ⇒ کارتِ تنظیمات پنهان است، پس دکمه‌اش هم نه
        if (StockLedger.serverFlag && ShopRole.canOpenSettings(context)) {
          Spacer(Modifier.height(12.dp))
          Button(onClick = onSettings) { Text(StrOnline.sfGoSettings) }
        }
      }
    }
    return
  }

  //  بازپخشِ دفتر بیرون از رشتهٔ صفحه — دفترِ بزرگ صفحه را نمی‌ایستاند
  val view by produceState<StockLedger.View?>(null, d) { value = withContext(Dispatchers.Default) { StockLedger.replay(d) } }
  var tab by rememberSaveable { mutableStateOf(BookTab.POS) }
  val askedTab by StockBookNav.tab
  LaunchedEffect(askedTab) {
    BookTab.entries.firstOrNull { it.id == askedTab }?.let { tab = it }
    StockBookNav.tab.value = null
  }
  var transferFor by remember { mutableStateOf<String?>(null) }
  val askedTransfer by StockBookNav.transfer
  LaunchedEffect(askedTransfer) { if (askedTransfer) { transferFor = ""; StockBookNav.transfer.value = false } }
  var posFilter by rememberSaveable { mutableStateOf<String?>(null) }

  var pendingCsv by remember { mutableStateOf<ReportCsv.Sheet?>(null) }
  val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    val sheet = pendingCsv
    pendingCsv = null
    if (uri == null || sheet == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { it.write(ReportCsv.text(sheet).toByteArray(Charsets.UTF_8)) } ?: error(StrReports.csvFailed)
        }
      }.onSuccess { toast(StrStockPages.exported) }.onFailure { toast(StrReports.csvFailed) }
    }
  }

  val v = view
  if (v == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    return
  }
  val today = todayIso()
  val stats = remember(v, d) { bookStats(d, v, today) }

  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(StrReach.stockBook, style = MaterialTheme.typography.titleLarge, color = Shop.colors.text)
        Text(StrStockPages.costNote, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      }
      Button(onClick = { transferFor = "" }) {
        Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(StrPlaces.transferTitle)
      }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatCard(StrStockPages.stockValue, minorText(stats.valueMinor) + " ؋", StrStockPages.atAvgApprox, Modifier.widthIn(min = 170.dp),
        selected = tab == BookTab.POS && posFilter == null) { tab = BookTab.POS; posFilter = null }
      StatCard(StrStockPages.negativeItems, stats.negative.toString(), StrHome.justNow, Modifier.widthIn(min = 150.dp),
        selected = tab == BookTab.POS && posFilter == "neg") { tab = BookTab.POS; posFilter = "neg" }
      StatCard(StrStockPages.openTasks, stats.openTasks.toString(), StrStockPages.negOrExpired, Modifier.widthIn(min = 150.dp),
        selected = tab == BookTab.REVIEW) { tab = BookTab.REVIEW }
      StatCard(StrStockPages.expiredLots, stats.expiredLots.toString(), StrSettings.notSold, Modifier.widthIn(min = 150.dp),
        selected = tab == BookTab.POS && posFilter == "expired") { tab = BookTab.POS; posFilter = "expired" }
    }
    Spacer(Modifier.height(12.dp))
    SegmentedTabs(
      BookTab.entries.map { SegmentTab(it, it.label, if (it == BookTab.REVIEW && stats.openTasks > 0) stats.openTasks else null) },
      tab, { tab = it },
    )
    Spacer(Modifier.height(10.dp))
    Box(Modifier.weight(1f)) {
      when (tab) {
        BookTab.POS -> PositionsTab(d, v, today, posFilter, onTransfer = { transferFor = it })
        BookTab.LEDGER -> LedgerTab(d, v) { sheet -> pendingCsv = sheet; saveCsv.launch("${sheet.name}.csv") }
        BookTab.REVIEW -> ReviewTab(d, v) { ids -> save(StockActions.ack(d, v, ids, user, today, System.currentTimeMillis(), ::newId), StrStockPages.reviewedSaved) }
        BookTab.COUNT -> CountTab(d, v, user, onSaved = { next, n -> save(next, StrStockPages.countSaved(n)) }, onFail = ::toast)
      }
    }
  }

  transferFor?.let { pid ->
    TransferDialog(d, v, pid.ifEmpty { null }, onDismiss = { transferFor = null }) { productId, from, to, q, lot, reason ->
      when (val r = StockActions.transfer(d, v, productId, from, to, q, lot, reason, user, today, System.currentTimeMillis(), ::newId)) {
        is StockActions.Result.Failed -> toast(r.message)
        is StockActions.Result.Ok -> { save(r.data, StrPlaces.moved); transferFor = null }
      }
    }
  }
}

private data class BookStats(val valueMinor: Long, val negative: Int, val openTasks: Int, val expiredLots: Int)

private fun bookStats(d: ShopData, v: StockLedger.View, today: String): BookStats {
  var neg = 0
  var expired = 0
  for (pid in v.books.keys) {
    val pv = StockLedger.productView(v, pid)
    if (pv.qtyMilli < 0) neg++
    expired += pv.lots.count { it.qtyMilli > 0 && it.expiryDate != null && it.expiryDate < today }
  }
  return BookStats(StockLedger.totalValueMinor(v), neg, v.tasks.count { !it.acked }, expired)
}

/* ============================== جایگاه‌ها ============================== */

private data class PosRow(val id: String, val name: String, val unit: String, val pv: StockLedger.ProductView)

@Composable
private fun LotPills(lots: List<StockLedger.LotLine>, today: String) {
  if (lots.isEmpty()) { Text("—", color = Shop.colors.muted, style = MaterialTheme.typography.labelSmall); return }
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (l in lots) {
      val tone = when {
        l.qtyMilli < 0 -> PillTone.DANGER
        l.expiryDate == null -> PillTone.NEUTRAL
        l.expiryDate < today -> PillTone.DANGER
        (StockLedger.daysBetween(today, l.expiryDate) ?: Int.MAX_VALUE) <= 30 -> PillTone.WARNING
        else -> PillTone.SUCCESS
      }
      val parts = listOfNotNull(StockActions.lotLabel(l.lotId), milliText(l.qtyMilli), l.expiryDate?.let { formatDate(it) }, l.location.takeIf { it.isNotEmpty() })
      StatusPill(parts.joinToString(" · "), tone)
    }
  }
}

@Composable
private fun PositionsTab(d: ShopData, v: StockLedger.View, today: String, filter: String?, onTransfer: (String) -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  var loc by rememberSaveable { mutableStateOf<String?>(null) }
  val mode = rememberViewMode("stockbook", ScreenKind.MANAGEMENT)
  val locations = remember(d) { StockLedger.locations(d) }
  val rows = remember(d, v, query, loc, filter) {
    val names = d.products.associateBy { it.id }
    (d.products.map { it.id } + v.books.keys).distinct().mapNotNull { pid ->
      val p = names[pid]
      val pv = StockLedger.productView(v, pid)
      if (p != null && !p.active && pv.qtyMilli == 0L) return@mapNotNull null
      if (pv.locations.isEmpty()) return@mapNotNull null
      PosRow(pid, p?.name ?: StrHome.txDeletedItem, p?.unit.orEmpty(), pv)
    }.filter { r -> query.isBlank() || r.name.contains(query.trim(), ignoreCase = true) }
      .filter { r -> loc == null || r.pv.locations.any { it.location == loc && it.qtyMilli != 0L } }
      .filter { r ->
        when (filter) {
          "neg" -> r.pv.qtyMilli < 0
          "expired" -> r.pv.lots.any { it.qtyMilli > 0 && it.expiryDate != null && it.expiryDate < today }
          else -> true
        }
      }
      .sortedBy { it.name }
  }
  fun locText(pv: StockLedger.ProductView) =
    pv.locations.filter { it.qtyMilli != 0L }.joinToString(" · ") { "${StockActions.locationLabel(it.location)}: ${milliText(it.qtyMilli)}" }.ifEmpty { "—" }
  fun unitText(pv: StockLedger.ProductView) = pv.unitCostMinor?.let { minorText(it) } ?: StrOnline.presShortUnknown

  val posColumns = listOf(
    TableColumn<PosRow>(StrMoney.item, 220.dp, sortKey = { it.name }) { it.name },
    TableColumn<PosRow>(StrSimpleAsk.vOnHand, 110.dp, sortKey = { it.pv.qtyMilli }, numeric = true,
      tint = { r -> if (r.pv.qtyMilli < 0) Shop.colors.danger else Shop.colors.text }) { "${milliText(it.pv.qtyMilli)} ${it.unit}".trim() },
    TableColumn<PosRow>(StrStockPages.avgUnitCost, 130.dp, sortKey = { it.pv.unitCostMinor ?: Long.MIN_VALUE }, numeric = true) { unitText(it.pv) },
    TableColumn<PosRow>(StrStockPages.value, 120.dp, sortKey = { it.pv.valueMinor }, numeric = true) { minorText(it.pv.valueMinor) },
    TableColumn<PosRow>(StrStockPages.places, 200.dp) { locText(it.pv) },
    TableColumn<PosRow>(StrStockPages.lotsFefo, 220.dp) { r -> r.pv.lots.joinToString(" · ") { "${StockActions.lotLabel(it.lotId)} ${milliText(it.qtyMilli)}" + (it.expiryDate?.let { e -> " (${formatDate(e)})" } ?: "") }.ifEmpty { "—" } },
  )
  val posState = rememberTableState(posColumns)

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
      TableToolbar(
        query = query, onQuery = { query = it }, placeholder = StrStockPages.searchItem, viewMode = mode,
        filter = {
          FilterMenu(StrPlaces.place, locations.map { it to StockActions.locationLabel(it) }, loc, { loc = it }, StrStockPages.allPlaces)
        },
      )
      Spacer(Modifier.height(6.dp))
      Text(
        StrStockPages.fefoNote,
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
    }
    if (rows.isEmpty()) {
      item { EmptyNote(StrStockPages.noItemsFilter) }
    } else if (mode.mode == ViewMode.TABLE) {
      dataTable(sortRows(rows, posColumns, posState), posColumns, posState, key = { it.id }, onOpen = { onTransfer(it.id) }, onMenu = { onTransfer(it.id) })
    } else {
      items(rows, key = { it.id }) { r ->
        Panel(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, modifier = Modifier.weight(1f))
            if (r.pv.qtyMilli < 0) StatusPill(StrStockPages.negative, PillTone.DANGER)
          }
          Spacer(Modifier.height(6.dp))
          KeyValue(StrSimpleAsk.vOnHand, "${milliText(r.pv.qtyMilli)} ${r.unit}".trim())
          KeyValue(StrStockPages.avgUnitCost, unitText(r.pv))
          KeyValue(StrStockPages.value, minorText(r.pv.valueMinor))
          KeyValue(StrStockPages.places, locText(r.pv))
          Spacer(Modifier.height(6.dp))
          LotPills(r.pv.lots, today)
          Spacer(Modifier.height(4.dp))
          TextButton(onClick = { onTransfer(r.id) }) { Text(StrPlaces.transfer) }
        }
      }
    }
  }
}

@Composable
private fun KeyValue(k: String, v: String, tint: androidx.compose.ui.graphics.Color = Shop.colors.text) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    Text(k, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, modifier = Modifier.weight(1f))
    Text(v, style = MaterialTheme.typography.bodySmall, color = tint, fontWeight = FontWeight.Medium)
  }
}

/* ============================== دفترِ حرکت (INV-02) ============================== */

@Composable
private fun LedgerTab(d: ShopData, v: StockLedger.View, onExport: (ReportCsv.Sheet) -> Unit) {
  val names = remember(d) { d.products.associate { it.id to it.name } }
  var product by rememberSaveable { mutableStateOf<String?>(null) }
  var type by rememberSaveable { mutableStateOf<String?>(null) }
  var who by rememberSaveable { mutableStateOf<String?>(null) }
  var loc by rememberSaveable { mutableStateOf<String?>(null) }
  var from by rememberSaveable { mutableStateOf("") }
  var to by rememberSaveable { mutableStateOf("") }
  var limit by rememberSaveable { mutableStateOf(300) }
  val fromIso = from.takeIf { it.isNotBlank() }?.let { ir.vil3ntec.tohid.Jalali.parseTyped(it) }
  val toIso = to.takeIf { it.isNotBlank() }?.let { ir.vil3ntec.tohid.Jalali.parseTyped(it) }
  val rows = remember(v, product, type, who, loc, fromIso, toIso) {
    StockLedger.filterRows(
      v.rows,
      StockLedger.RowFilter(
        productId = product, location = loc, types = type?.let { setOf(it) }, userId = who,
        from = fromIso?.let { StockLedger.dayMs(it) },
        to = toIso?.let { StockLedger.dayMs(it) + 86_399_999L },
      ),
    ).asReversed()
  }
  val users = remember(v) { v.rows.map { it.userId }.distinct().sorted() }
  val prods = remember(v, names) { v.rows.map { it.productId }.distinct().map { it to (names[it] ?: StrHome.txDeletedItem) }.sortedBy { it.second } }
  val locations = remember(d) { StockLedger.locations(d) }

  val ledgerColumns = listOf(
    TableColumn<StockLedger.Row>(StrMoney.item, 200.dp) { names[it.productId] ?: StrHome.txDeletedItem },
    TableColumn<StockLedger.Row>(StrHistory.day, 100.dp) { formatDate(it.day) },
    TableColumn<StockLedger.Row>(StrInvoices.colKind, 130.dp) { StockActions.TYPE_LABEL[it.type] ?: it.type },
    TableColumn<StockLedger.Row>(StrPlaces.place, 130.dp) { StockActions.locationLabel(it.location) },
    TableColumn<StockLedger.Row>(StrPlaces.amount, 90.dp, numeric = true, tint = { r -> if (r.qtyMilli < 0) Shop.colors.danger else Shop.colors.success }) {
      (if (it.qtyMilli > 0) "+" else "") + milliText(it.qtyMilli)
    },
    TableColumn<StockLedger.Row>(StrStockPages.provisionalCost, 110.dp, numeric = true) { minorText(it.costMinor) + if (it.costAdjustMinor != 0L) StrStockPages.adjusted(minorText(it.costAdjustMinor)) else "" },
    TableColumn<StockLedger.Row>(StrShift.user, 110.dp) { StockActions.userLabel(it.userId) },
    TableColumn<StockLedger.Row>(StrSafe.reason, 180.dp) { it.reason },
    TableColumn<StockLedger.Row>(StrReports.colSeries, 100.dp) { if (it.lotId.isEmpty()) "" else StockActions.lotLabel(it.lotId) },
  )
  val ledgerState = rememberTableState(ledgerColumns)

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
    item {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterMenu(StrMoney.item, prods, product, { product = it }, StrStockPages.allItems)
        FilterMenu(StrInvoices.colKind, StockActions.TYPE_LABEL.entries.map { it.key to it.value }, type, { type = it }, StrStockPages.allTypes)
        FilterMenu(StrShift.user, users.map { it to StockActions.userLabel(it) }, who, { who = it }, StrStockPages.allUsers)
        FilterMenu(StrPlaces.place, locations.map { it to StockActions.locationLabel(it) }, loc, { loc = it }, StrStockPages.allPlaces)
        OutlinedTextField(from, { from = it }, label = { Text(StrReports.from) }, placeholder = { Text("۱۴۰۵/۰۷/۰۱") }, singleLine = true,
          isError = from.isNotBlank() && fromIso == null, modifier = Modifier.width(140.dp))
        OutlinedTextField(to, { to = it }, label = { Text(StrReports.to) }, singleLine = true,
          isError = to.isNotBlank() && toIso == null, modifier = Modifier.width(140.dp))
        OutlinedButton(enabled = rows.isNotEmpty(), onClick = {
          val sheetRows = listOf(StockLedger.EXPORT_HEADER) + StockLedger.exportRows(rows.asReversed()) { names[it] ?: it }
          onExport(ReportCsv.Sheet("دفتر-حرکت-کالا-${todayIso()}", sheetRows)) // داده: نامِ پروندهٔ CSV (خروجی)
        }) { Text(StrStock.exportCsv) }
      }
      Spacer(Modifier.height(6.dp))
      Text(
        StrStockPages.ledgerNote(rows.size),
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
      Spacer(Modifier.height(6.dp))
    }
    if (rows.isEmpty()) {
      item { EmptyNote(StrStockPages.noMovesFilter) }
    } else {
      dataTable(rows.take(limit), ledgerColumns, ledgerState, key = { it.id }, onOpen = {}, onMenu = {})
      if (rows.size > limit) item { TextButton(onClick = { limit += 300 }) { Text(StrStockPages.moreRows(rows.size - limit)) } }
    }
  }
}

/* ============================== وظیفهٔ بررسی (INV-19) ============================== */

@Composable
private fun ReviewTab(d: ShopData, v: StockLedger.View, onAck: (Collection<String>) -> Unit) {
  val names = remember(d) { d.products.associate { it.id to it.name } }
  var showAcked by rememberSaveable { mutableStateOf(false) }
  var selected by remember { mutableStateOf(setOf<String>()) }
  val tasks = remember(v, showAcked) { v.tasks.filter { showAcked || !it.acked }.asReversed() }
  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        Text(
          StrStockPages.reviewNote,
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = showAcked, onCheckedChange = { showAcked = it })
          Text(StrStockPages.alsoReviewed, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
        }
      }
      if (tasks.isEmpty()) item { EmptyNote(StrStockPages.noOpenTasks) }
      items(tasks, key = { it.id }) { t ->
        Panel(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (!t.acked) Checkbox(checked = t.id in selected, onCheckedChange = { selected = if (it) selected + t.id else selected - t.id })
            Text(names[t.productId] ?: StrHome.txDeletedItem, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, modifier = Modifier.weight(1f))
            if (t.acked) StatusPill(StrOnline.ffAck, PillTone.SUCCESS)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            t.reasons.forEach { r ->
              StatusPill(StockActions.REASON_LABEL[r] ?: r, when (r) { "negative_stock" -> PillTone.DANGER; "expired_sold" -> PillTone.WARNING; else -> PillTone.NEUTRAL })
            }
          }
          Spacer(Modifier.height(6.dp))
          KeyValue(StrHistory.day, formatDate(t.day))
          KeyValue(StrPlaces.place, StockActions.locationLabel(t.location))
          if (t.shortMilli != 0L) KeyValue(StrStockPages.shortage, milliText(t.shortMilli), Shop.colors.danger)
          KeyValue(StrStockPages.stockAfterDoc, milliText(t.qtyAfterMilli))
          if (!t.acked) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onAck(listOf(t.id)) }) { Text(StrOnline.ffAck) }
          }
        }
      }
    }
    if (selected.isNotEmpty()) {
      BulkActionBar(
        count = selected.size,
        actions = listOf(BulkAction(StrOnline.ffAck, Icons.Filled.CheckCircle) { onAck(selected); selected = emptySet() }),
        onClose = { selected = emptySet() },
        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
      )
    }
  }
}

/* ============================== شمارش (INV-05/09/10، D-19) ============================== */

@Composable
private fun CountTab(
  d: ShopData,
  v: StockLedger.View,
  user: String,
  onSaved: (ShopData, String) -> Unit,
  onFail: (String) -> Unit,
) {
  val context = LocalContext.current
  var draft by remember { mutableStateOf(StockCountDraftStore.load(context)) }
  //  INV-10: هر عددِ تازه لحظهٔ خودش را می‌گیرد (`StockActions.stamp`) تا فروشِ وسطِ شمارش دو بار کم نشود
  fun keep(next: StockActions.CountDraft?) {
    val n = next?.let { StockActions.stamp(draft, it, ir.vil3ntec.tohid.data.ReportBridge.nowMs()) }
    draft = n; StockCountDraftStore.save(context, n)
  }
  var query by rememberSaveable { mutableStateOf("") }
  var cat by rememberSaveable { mutableStateOf<String?>(null) }
  var confirm by remember { mutableStateOf<String?>(null) }

  //  عکسِ CI: شمارشِ نمونه
  val shotCount by StockBookNav.count
  LaunchedEffect(shotCount) {
    if (shotCount > 0) {
      val start = StockActions.startCount(d, v, System.currentTimeMillis())
      val picks = d.products.filter { it.active }.take(shotCount)
      keep(start.copy(counts = picks.associate { p -> p.id to qty(maxOf(0L, (start.snapshot[p.id] ?: 0L) - 1000L) / 1000.0) }))
    }
  }

  val dr = draft
  if (dr == null) {
    Panel(Modifier.fillMaxWidth()) {
      Text(StrStockPages.countTitle, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
      Spacer(Modifier.height(6.dp))
      Text(
        StrStockPages.countNote,
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
      )
      Spacer(Modifier.height(12.dp))
      Button(onClick = { keep(StockActions.startCount(d, v, System.currentTimeMillis())) }) { Text(StrStockPages.startCount) }
    }
    return
  }
  val review = remember(d, v, dr) { StockActions.countReview(d, v, dr) }
  val invalid = remember(dr) { StockActions.invalidCounts(dr).toSet() }
  val byLine = remember(review) { review.lines.associateBy { it.productId } }
  val cats = remember(d) { d.products.map { it.category }.filter { it.isNotBlank() }.distinct().sorted() }
  val list = remember(d, query, cat) {
    d.products.filter { it.active }
      .filter { cat == null || it.category == cat }
      .filter { p -> query.isBlank() || p.name.contains(query.trim(), ignoreCase = true) || p.barcodes.any { it.contains(query.trim()) } }
      .sortedBy { it.name }
  }

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(StrStock.counted, review.lines.size.toString(), StrStockPages.startedAt(ir.vil3ntec.tohid.spanText(dr.startedAt)), Modifier.widthIn(min = 130.dp))
        StatCard(StrStockPages.matched, review.matched.toString(), StrInvoices.noDifference, Modifier.widthIn(min = 120.dp))
        StatCard(StrStockPages.variance, review.variance.toString(), StrStockPages.willSave, Modifier.widthIn(min = 120.dp))
        StatCard(StrStockPages.uncounted, review.uncounted.toString(), StrStockPages.untouched, Modifier.widthIn(min = 130.dp))
        StatCard(StrStockPages.financialEffect, minorText(review.valueMinor) + " ؋", "+${minorText(review.gainMinor)} / −${minorText(review.lossMinor)}", Modifier.widthIn(min = 170.dp))
      }
      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        SearchBox(query, { query = it }, StrStockPages.searchNameBarcode, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        FilterMenu(StrGrid.category, cats.map { it to it }, cat, { cat = it }, StrCount.allCategories)
      }
      Spacer(Modifier.height(6.dp))
      Text(StrStockPages.uncountedNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
    }
    items(list, key = { it.id }) { p ->
      val sys = byLine[p.id]?.systemMilli ?: dr.snapshot[p.id] ?: StockLedger.productView(v, p.id).qtyMilli
      val line = byLine[p.id]
      val diff = line?.diffMilli
      Row(
        Modifier.fillMaxWidth().clip(Shape.card).background(Shop.colors.surfaceSolid).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(p.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          Text(
            StrCount.system(milliText(sys), p.unit) + (if (diff != null && diff != 0L) " · ${if (diff > 0) "+" else "−"}${qty(kotlin.math.abs(diff) / 1000.0)}" else ""),
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
          )
        }
        when {
          diff == null -> {}
          diff == 0L -> StatusPill(StrStockPages.matched, PillTone.SUCCESS)
          diff > 0 -> StatusPill(StrStockPages.increase, PillTone.SUCCESS)
          else -> {
            StatusPill(StrOnline.dlShort, PillTone.DANGER)
            Spacer(Modifier.width(6.dp))
            FilterMenu(
              StrSafe.reason, StockActions.LOSS_KINDS, dr.reasons[p.id] ?: "count",
              { k -> keep(dr.copy(reasons = dr.reasons + (p.id to (k ?: "count")))) }, StrStockPages.countFix,
            )
          }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
          value = dr.counts[p.id].orEmpty(),
          onValueChange = { raw -> keep(dr.copy(counts = if (raw.isBlank()) dr.counts - p.id else dr.counts + (p.id to raw))) },
          placeholder = { Text(StrStock.counted) },
          singleLine = true,
          isError = p.id in invalid,
          keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
          modifier = Modifier.width(110.dp),
        )
      }
    }
    item {
      OutlinedTextField(dr.note, { keep(dr.copy(note = it)) }, label = { Text(StrInvoices.noteOptional) }, singleLine = true, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { confirm = "drop" }) { Text(StrStockPages.dropCount) }
        Button(enabled = review.variance > 0 && invalid.isEmpty(), onClick = { confirm = "save" }) { Text(StrStockPages.saveDiffs(review.variance)) }
      }
    }
  }

  confirm?.let { what ->
    AlertDialog(
      onDismissRequest = { confirm = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(if (what == "save") StrStockPages.saveDiffsAsk(review.variance) else StrStockPages.dropAsk, color = Shop.colors.text) },
      text = {
        Text(
          if (what == "save") StrStockPages.saveDiffsBody(minorText(review.valueMinor), review.uncounted)
          else StrStockPages.dropBody,
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          confirm = null
          if (what == "drop") { keep(null); return@TextButton }
          when (val r = StockActions.saveCount(d, v, dr, user, todayIso(), System.currentTimeMillis(), ::newId)) {
            is StockActions.Result.Failed -> onFail(r.message)
            is StockActions.Result.Ok -> { keep(null); onSaved(r.data, r.id) }
          }
        }) { Text(if (what == "save") StrStockPages.saveIt else StrStockPages.setAside) }
      },
      dismissButton = { TextButton(onClick = { confirm = null }) { Text(Str.cancel) } },
    )
  }
}

/* ============================== انتقال (INV-20) ============================== */

@Composable
private fun TransferDialog(
  d: ShopData,
  v: StockLedger.View,
  initialProduct: String?,
  onDismiss: () -> Unit,
  onSave: (productId: String, from: String, to: String, quantity: Double, lotId: String?, reason: String) -> Unit,
) {
  var productId by remember { mutableStateOf(initialProduct) }
  var search by remember { mutableStateOf("") }
  val locations = remember(d) { StockLedger.locations(d) }
  var from by remember { mutableStateOf(StockLedger.MAIN) }
  var to by remember { mutableStateOf(locations.getOrNull(1) ?: "") }
  var newPlace by remember { mutableStateOf(locations.size < 2) }
  var fresh by remember { mutableStateOf("") }
  var amount by remember { mutableStateOf("") }
  var lot by remember { mutableStateOf<String?>(null) }
  var reason by remember { mutableStateOf("") }
  val pv = productId?.let { StockLedger.productView(v, it) }
  val lots = pv?.lots?.filter { it.location == from && it.qtyMilli > 0 && it.lotId.isNotEmpty() }.orEmpty()

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
      Surface(color = Shop.colors.surfaceSolid, shape = Shape.dialog, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
          Text(StrPlaces.transferTitle, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
          Text(StrStockPages.transferNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          Spacer(Modifier.height(12.dp))
          val chosen = d.products.find { it.id == productId }
          if (chosen != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(chosen.name, style = MaterialTheme.typography.bodyLarge, color = Shop.colors.text, modifier = Modifier.weight(1f))
              TextButton(onClick = { productId = null; lot = null }) { Text(StrPlaces.otherProduct) }
            }
            pv?.let { Text(it.locations.filter { l -> l.qtyMilli != 0L }.joinToString(" · ") { l -> "${StockActions.locationLabel(l.location)}: ${milliText(l.qtyMilli)}" }, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted) }
          } else {
            SearchBox(search, { search = it }, StrPlaces.search, Modifier.fillMaxWidth())
            d.products.filter { it.active && (search.isBlank() || it.name.contains(search.trim(), ignoreCase = true)) }.sortedBy { it.name }.take(6).forEach { p ->
              Text(
                p.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text,
                modifier = Modifier.fillMaxWidth().clip(Shape.button).clickable { productId = p.id }.padding(10.dp),
              )
            }
          }
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(StrPlaces.from, color = Shop.colors.muted)
            FilterMenu(StrPlaces.origin, locations.map { it to StockActions.locationLabel(it) }, from, { from = it ?: StockLedger.MAIN; lot = null }, StockActions.MAIN_LABEL)
            Text(StrPlaces.to, color = Shop.colors.muted)
            if (!newPlace) {
              FilterMenu(StrPlaces.destination, locations.map { it to StockActions.locationLabel(it) }, to, { to = it ?: StockLedger.MAIN }, StockActions.MAIN_LABEL)
            }
            TextButton(onClick = { newPlace = !newPlace }) { Text(if (newPlace) StrStockPages.fromList else StrStockPages.newPlaceBtn) }
          }
          if (newPlace) {
            OutlinedTextField(fresh, { fresh = it.take(40) }, label = { Text(StrPlaces.newPlace) }, singleLine = true, modifier = Modifier.fillMaxWidth())
          }
          Spacer(Modifier.height(8.dp))
          NumberField(amount, { amount = it }, StrPlaces.amount, Modifier.fillMaxWidth())
          if (lots.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FilterMenu(
              StrReports.colSeries, lots.map { it.lotId to "${StockActions.lotLabel(it.lotId)} · ${milliText(it.qtyMilli)}" + (it.expiryDate?.let { e -> " · ${formatDate(e)}" } ?: "") },
              lot, { lot = it }, StrStockPages.byFefo,
            )
          }
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(reason, { reason = it.take(80) }, label = { Text(StrInvoices.reasonOptional) }, singleLine = true, modifier = Modifier.fillMaxWidth())
          Spacer(Modifier.height(16.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(Str.cancel) }
            Button(
              enabled = productId != null,
              onClick = {
                val dest = if (newPlace) StockActions.locationOf(fresh) else to
                onSave(productId ?: return@Button, from, dest, amount.toDoubleOrNull() ?: 0.0, lot, reason)
              },
              modifier = Modifier.weight(1f),
            ) { Text(StrPlaces.transfer) }
          }
        }
      }
    }
  }
}

/* ============================== تنظیمات: کارتِ «موجودی و بها» ============================== */

/** فقط وقتی موتور روشن است: ارزش به بهای میانگین، وظیفه‌های باز و سری‌های منقضی؛ زدنِ هر کارت ⇐ «دفترِ موجودی» */
@Composable
fun StockStrip(d: ShopData, onOpen: () -> Unit) {
  val on = remember(d.auditLog, StockLedger.serverFlag) { StockLedger.isOn(d) }
  if (!on) return
  val view by produceState<StockLedger.View?>(null, d) { value = withContext(Dispatchers.Default) { StockLedger.replay(d) } }
  val v = view ?: return
  val stats = remember(v, d) { bookStats(d, v, todayIso()) }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    StatCard(StrStockPages.stockValueAvg, minorText(stats.valueMinor) + " ؋", StrStockPages.approxD23, Modifier.widthIn(min = 190.dp), onClick = onOpen)
    StatCard(StrStockPages.openTasks, stats.openTasks.toString(), if (stats.openTasks > 0) StrStockPages.negOrExpired else StrStockPages.allReviewed, Modifier.widthIn(min = 160.dp), onClick = onOpen)
    StatCard(StrStockPages.expiredLots, stats.expiredLots.toString(), StrSettings.notSold, Modifier.widthIn(min = 160.dp), onClick = onOpen)
  }
}
