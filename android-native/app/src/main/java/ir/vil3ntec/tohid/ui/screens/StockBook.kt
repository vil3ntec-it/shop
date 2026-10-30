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

private enum class BookTab(val id: String, val label: String) {
  POS("pos", "جایگاه‌ها"), LEDGER("ledger", "دفترِ حرکت"), REVIEW("review", "بررسی"), COUNT("count", "شمارش")
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
  fun save(next: ShopData, done: String) { scope.launch { store.save(next) }; toast(done) }

  //  عکسِ CI: روشن کردن با همان راهِ کارتِ تنظیمات
  val shotEnable by StockBookNav.enable
  LaunchedEffect(shotEnable, on) {
    if (shotEnable && !on && StockLedger.serverFlag) store.save(StockActions.setMode(d, true, user, todayIso(), System.currentTimeMillis(), ::newId))
  }

  if (!on) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
      Panel(Modifier.widthIn(max = 560.dp)) {
        Text("موتورِ تازهٔ موجودی و بها خاموش است", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(6.dp))
        Text(
          if (StockLedger.serverFlag) "بهای میانگینِ موزون، سری و انقضا، دفترِ حرکت، شمارش با بازبینی و انتقال بین محل‌ها با روشن کردنِ «موجودی و بهای تمام‌شده» در تنظیمات می‌آیند."
          else "این بخش هنوز برای دکانِ شما باز نشده است؛ انبار و فروش مثلِ همیشه کار می‌کنند.",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
        //  پرچمِ `shop.stock_v2` خاموش ⇒ کارتِ تنظیمات پنهان است، پس دکمه‌اش هم نه
        if (StockLedger.serverFlag && ShopRole.canOpenSettings(context)) {
          Spacer(Modifier.height(12.dp))
          Button(onClick = onSettings) { Text("رفتن به تنظیمات") }
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
          context.contentResolver.openOutputStream(uri)?.use { it.write(ReportCsv.text(sheet).toByteArray(Charsets.UTF_8)) } ?: error("فایل ساخته نشد")
        }
      }.onSuccess { toast("خروجی ساخته شد") }.onFailure { toast("فایل ساخته نشد") }
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
        Text("دفترِ موجودی", style = MaterialTheme.typography.titleLarge, color = Shop.colors.text)
        Text("بها به میانگینِ موزون و «تقریبی» است تا روشِ بها قطعی شود (D-23).", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      }
      Button(onClick = { transferFor = "" }) {
        Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("انتقال بین محل‌ها")
      }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatCard("ارزشِ موجودی", minorText(stats.valueMinor) + " ؋", "به بهای میانگین، تقریبی", Modifier.widthIn(min = 170.dp),
        selected = tab == BookTab.POS && posFilter == null) { tab = BookTab.POS; posFilter = null }
      StatCard("کالای با موجودیِ منفی", stats.negative.toString(), "همین حالا", Modifier.widthIn(min = 150.dp),
        selected = tab == BookTab.POS && posFilter == "neg") { tab = BookTab.POS; posFilter = "neg" }
      StatCard("وظیفهٔ بررسیِ باز", stats.openTasks.toString(), "موجودیِ منفی یا فروشِ منقضی", Modifier.widthIn(min = 150.dp),
        selected = tab == BookTab.REVIEW) { tab = BookTab.REVIEW }
      StatCard("سریِ منقضیِ دارای موجودی", stats.expiredLots.toString(), "فروخته نمی‌شود", Modifier.widthIn(min = 150.dp),
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
        BookTab.REVIEW -> ReviewTab(d, v) { ids -> save(StockActions.ack(d, v, ids, user, today, System.currentTimeMillis(), ::newId), "ثبت شد: بررسی شد") }
        BookTab.COUNT -> CountTab(d, v, user, onSaved = { next, n -> save(next, "شمارش ثبت شد — $n اختلاف اصلاح شد") }, onFail = ::toast)
      }
    }
  }

  transferFor?.let { pid ->
    TransferDialog(d, v, pid.ifEmpty { null }, onDismiss = { transferFor = null }) { productId, from, to, q, lot, reason ->
      when (val r = StockActions.transfer(d, v, productId, from, to, q, lot, reason, user, today, System.currentTimeMillis(), ::newId)) {
        is StockActions.Result.Failed -> toast(r.message)
        is StockActions.Result.Ok -> { save(r.data, "انتقال ثبت شد"); transferFor = null }
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
      PosRow(pid, p?.name ?: "(کالای حذف‌شده)", p?.unit.orEmpty(), pv)
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
  fun unitText(pv: StockLedger.ProductView) = pv.unitCostMinor?.let { minorText(it) } ?: "نامعلوم"

  val posColumns = listOf(
    TableColumn<PosRow>("کالا", 220.dp, sortKey = { it.name }) { it.name },
    TableColumn<PosRow>("موجودی", 110.dp, sortKey = { it.pv.qtyMilli }, numeric = true,
      tint = { r -> if (r.pv.qtyMilli < 0) Shop.colors.danger else Shop.colors.text }) { "${milliText(it.pv.qtyMilli)} ${it.unit}".trim() },
    TableColumn<PosRow>("بهای میانگینِ واحد", 130.dp, sortKey = { it.pv.unitCostMinor ?: Long.MIN_VALUE }, numeric = true) { unitText(it.pv) },
    TableColumn<PosRow>("ارزش", 120.dp, sortKey = { it.pv.valueMinor }, numeric = true) { minorText(it.pv.valueMinor) },
    TableColumn<PosRow>("محل‌ها", 200.dp) { locText(it.pv) },
    TableColumn<PosRow>("سری‌ها (FEFO)", 220.dp) { r -> r.pv.lots.joinToString(" · ") { "${StockActions.lotLabel(it.lotId)} ${milliText(it.qtyMilli)}" + (it.expiryDate?.let { e -> " (${formatDate(e)})" } ?: "") }.ifEmpty { "—" } },
  )
  val posState = rememberTableState(posColumns)

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
      TableToolbar(
        query = query, onQuery = { query = it }, placeholder = "جستجوی کالا…", viewMode = mode,
        filter = {
          FilterMenu("محل", locations.map { it to StockActions.locationLabel(it) }, loc, { loc = it }, "همهٔ محل‌ها")
        },
      )
      Spacer(Modifier.height(6.dp))
      Text(
        "ترتیبِ برداشتِ فروش (FEFO): انقضای زودتر اول؛ سری‌های بی‌تاریخ بعد از تاریخ‌دارها و به ترتیبِ دریافت. سریِ منقضی فروخته نمی‌شود؛ سریِ اسکن‌شده سرِ صندوق اول برداشته می‌شود.",
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
    }
    if (rows.isEmpty()) {
      item { EmptyNote("کالایی با این فیلتر نیست") }
    } else if (mode.mode == ViewMode.TABLE) {
      dataTable(sortRows(rows, posColumns, posState), posColumns, posState, key = { it.id }, onOpen = { onTransfer(it.id) }, onMenu = { onTransfer(it.id) })
    } else {
      items(rows, key = { it.id }) { r ->
        Panel(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, modifier = Modifier.weight(1f))
            if (r.pv.qtyMilli < 0) StatusPill("منفی", PillTone.DANGER)
          }
          Spacer(Modifier.height(6.dp))
          KeyValue("موجودی", "${milliText(r.pv.qtyMilli)} ${r.unit}".trim())
          KeyValue("بهای میانگینِ واحد", unitText(r.pv))
          KeyValue("ارزش", minorText(r.pv.valueMinor))
          KeyValue("محل‌ها", locText(r.pv))
          Spacer(Modifier.height(6.dp))
          LotPills(r.pv.lots, today)
          Spacer(Modifier.height(4.dp))
          TextButton(onClick = { onTransfer(r.id) }) { Text("انتقال") }
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
  val prods = remember(v, names) { v.rows.map { it.productId }.distinct().map { it to (names[it] ?: "(کالای حذف‌شده)") }.sortedBy { it.second } }
  val locations = remember(d) { StockLedger.locations(d) }

  val ledgerColumns = listOf(
    TableColumn<StockLedger.Row>("کالا", 200.dp) { names[it.productId] ?: "(کالای حذف‌شده)" },
    TableColumn<StockLedger.Row>("روز", 100.dp) { formatDate(it.day) },
    TableColumn<StockLedger.Row>("نوع", 130.dp) { StockActions.TYPE_LABEL[it.type] ?: it.type },
    TableColumn<StockLedger.Row>("محل", 130.dp) { StockActions.locationLabel(it.location) },
    TableColumn<StockLedger.Row>("مقدار", 90.dp, numeric = true, tint = { r -> if (r.qtyMilli < 0) Shop.colors.danger else Shop.colors.success }) {
      (if (it.qtyMilli > 0) "+" else "") + milliText(it.qtyMilli)
    },
    TableColumn<StockLedger.Row>("بهای موقت", 110.dp, numeric = true) { minorText(it.costMinor) + if (it.costAdjustMinor != 0L) " (تعدیل ${minorText(it.costAdjustMinor)})" else "" },
    TableColumn<StockLedger.Row>("کاربر", 110.dp) { StockActions.userLabel(it.userId) },
    TableColumn<StockLedger.Row>("دلیل", 180.dp) { it.reason },
    TableColumn<StockLedger.Row>("سری", 100.dp) { if (it.lotId.isEmpty()) "" else StockActions.lotLabel(it.lotId) },
  )
  val ledgerState = rememberTableState(ledgerColumns)

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
    item {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterMenu("کالا", prods, product, { product = it }, "همهٔ کالاها")
        FilterMenu("نوع", StockActions.TYPE_LABEL.entries.map { it.key to it.value }, type, { type = it }, "همهٔ نوع‌ها")
        FilterMenu("کاربر", users.map { it to StockActions.userLabel(it) }, who, { who = it }, "همهٔ کاربرها")
        FilterMenu("محل", locations.map { it to StockActions.locationLabel(it) }, loc, { loc = it }, "همهٔ محل‌ها")
        OutlinedTextField(from, { from = it }, label = { Text("از") }, placeholder = { Text("۱۴۰۵/۰۷/۰۱") }, singleLine = true,
          isError = from.isNotBlank() && fromIso == null, modifier = Modifier.width(140.dp))
        OutlinedTextField(to, { to = it }, label = { Text("تا") }, singleLine = true,
          isError = to.isNotBlank() && toIso == null, modifier = Modifier.width(140.dp))
        OutlinedButton(enabled = rows.isNotEmpty(), onClick = {
          val sheetRows = listOf(StockLedger.EXPORT_HEADER) + StockLedger.exportRows(rows.asReversed()) { names[it] ?: it }
          onExport(ReportCsv.Sheet("دفتر-حرکت-کالا-${todayIso()}", sheetRows))
        }) { Text("خروجی CSV") }
      }
      Spacer(Modifier.height(6.dp))
      Text(
        "${rows.size} حرکت · فقط افزودنی: هیچ ردیفی ویرایش یا پاک نمی‌شود؛ اصلاح یک سندِ تازه است.",
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
      Spacer(Modifier.height(6.dp))
    }
    if (rows.isEmpty()) {
      item { EmptyNote("حرکتی با این فیلتر نیست") }
    } else {
      dataTable(rows.take(limit), ledgerColumns, ledgerState, key = { it.id }, onOpen = {}, onMenu = {})
      if (rows.size > limit) item { TextButton(onClick = { limit += 300 }) { Text("${rows.size - limit} ردیفِ دیگر") } }
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
          "فروشِ ثبت‌شده هرگز رد نمی‌شود: وقتی موجودی منفی شد یا از سریِ منقضی فروخته شد، این‌جا یک وظیفه ساخته می‌شود تا مدیر بشمارد یا ورود را ثبت کند.",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = showAcked, onCheckedChange = { showAcked = it })
          Text("بررسی‌شده‌ها هم", style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
        }
      }
      if (tasks.isEmpty()) item { EmptyNote("وظیفهٔ بازی نیست — موجودیِ منفی یا فروشِ منقضی پیدا نشد") }
      items(tasks, key = { it.id }) { t ->
        Panel(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (!t.acked) Checkbox(checked = t.id in selected, onCheckedChange = { selected = if (it) selected + t.id else selected - t.id })
            Text(names[t.productId] ?: "(کالای حذف‌شده)", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, modifier = Modifier.weight(1f))
            if (t.acked) StatusPill("بررسی شد", PillTone.SUCCESS)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            t.reasons.forEach { r ->
              StatusPill(StockActions.REASON_LABEL[r] ?: r, when (r) { "negative_stock" -> PillTone.DANGER; "expired_sold" -> PillTone.WARNING; else -> PillTone.NEUTRAL })
            }
          }
          Spacer(Modifier.height(6.dp))
          KeyValue("روز", formatDate(t.day))
          KeyValue("محل", StockActions.locationLabel(t.location))
          if (t.shortMilli != 0L) KeyValue("کمبود", milliText(t.shortMilli), Shop.colors.danger)
          KeyValue("موجودی پس از سند", milliText(t.qtyAfterMilli))
          if (!t.acked) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onAck(listOf(t.id)) }) { Text("بررسی شد") }
          }
        }
      }
    }
    if (selected.isNotEmpty()) {
      BulkActionBar(
        count = selected.size,
        actions = listOf(BulkAction("بررسی شد", Icons.Filled.CheckCircle) { onAck(selected); selected = emptySet() }),
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
  fun keep(next: StockActions.CountDraft?) { draft = next; StockCountDraftStore.save(context, next) }
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
      Text("شمارشِ موجودی", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
      Spacer(Modifier.height(6.dp))
      Text(
        "موجودیِ برنامهٔ همین لحظه یادداشت می‌شود و اختلاف نسبت به همان حساب می‌شود؛ پس فروش یا خریدِ وسطِ شمارش اختلافِ دروغ نمی‌سازد. می‌شود فقط یک دسته یا چند کالا را شمرد. کالاهای شمرده‌نشده دست نمی‌خورند.",
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
      )
      Spacer(Modifier.height(12.dp))
      Button(onClick = { keep(StockActions.startCount(d, v, System.currentTimeMillis())) }) { Text("شروعِ شمارش") }
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
        StatCard("شمرده", review.lines.size.toString(), "شروع: ${ir.vil3ntec.tohid.spanText(dr.startedAt)}", Modifier.widthIn(min = 130.dp))
        StatCard("منطبق", review.matched.toString(), "بی اختلاف", Modifier.widthIn(min = 120.dp))
        StatCard("مغایر", review.variance.toString(), "ثبت می‌شود", Modifier.widthIn(min = 120.dp))
        StatCard("شمرده‌نشده", review.uncounted.toString(), "دست نمی‌خورند", Modifier.widthIn(min = 130.dp))
        StatCard("اثرِ مالی (تقریبی)", minorText(review.valueMinor) + " ؋", "+${minorText(review.gainMinor)} / −${minorText(review.lossMinor)}", Modifier.widthIn(min = 170.dp))
      }
      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        SearchBox(query, { query = it }, "جستجو با نام یا بارکد…", Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        FilterMenu("دسته", cats.map { it to it }, cat, { cat = it }, "همهٔ دسته‌ها")
      }
      Spacer(Modifier.height(6.dp))
      Text("کالاهای شمرده‌نشده دست نمی‌خورند؛ صفر کردنشان فقط با شمردنِ صفر.", style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
    }
    items(list, key = { it.id }) { p ->
      val sys = dr.snapshot[p.id] ?: StockLedger.productView(v, p.id).qtyMilli
      val line = byLine[p.id]
      val diff = line?.diffMilli
      Row(
        Modifier.fillMaxWidth().clip(Shape.card).background(Shop.colors.surfaceSolid).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(p.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          Text(
            "برنامه: ${milliText(sys)} ${p.unit}" + (if (diff != null && diff != 0L) " · ${if (diff > 0) "+" else "−"}${qty(kotlin.math.abs(diff) / 1000.0)}" else ""),
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
          )
        }
        when {
          diff == null -> {}
          diff == 0L -> StatusPill("منطبق", PillTone.SUCCESS)
          diff > 0 -> StatusPill("افزایش", PillTone.SUCCESS)
          else -> {
            StatusPill("کسری", PillTone.DANGER)
            Spacer(Modifier.width(6.dp))
            FilterMenu(
              "دلیل", StockActions.LOSS_KINDS, dr.reasons[p.id] ?: "count",
              { k -> keep(dr.copy(reasons = dr.reasons + (p.id to (k ?: "count")))) }, "اصلاحِ شمارش",
            )
          }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
          value = dr.counts[p.id].orEmpty(),
          onValueChange = { raw -> keep(dr.copy(counts = if (raw.isBlank()) dr.counts - p.id else dr.counts + (p.id to raw))) },
          placeholder = { Text("شمرده") },
          singleLine = true,
          isError = p.id in invalid,
          keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
          modifier = Modifier.width(110.dp),
        )
      }
    }
    item {
      OutlinedTextField(dr.note, { keep(dr.copy(note = it)) }, label = { Text("یادداشت (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { confirm = "drop" }) { Text("کنار گذاشتنِ شمارش") }
        Button(enabled = review.variance > 0 && invalid.isEmpty(), onClick = { confirm = "save" }) { Text("ثبتِ ${review.variance} اختلاف") }
      }
    }
  }

  confirm?.let { what ->
    AlertDialog(
      onDismissRequest = { confirm = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(if (what == "save") "ثبتِ ${review.variance} اختلافِ شمارش؟" else "شمارش کنار گذاشته شود؟", color = Shop.colors.text) },
      text = {
        Text(
          if (what == "save") "اثرِ مالیِ تقریبی ${minorText(review.valueMinor)} افغانی. هر اختلاف یک سندِ اصلاح با کاربر، دلیل و زمان می‌شود. ${review.uncounted} کالای شمرده‌نشده دست نمی‌خورد."
          else "عددهای شمرده‌شده پاک می‌شوند؛ چیزی در دفتر ثبت نشده است.",
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
        }) { Text(if (what == "save") "ثبت کن" else "کنار بگذار") }
      },
      dismissButton = { TextButton(onClick = { confirm = null }) { Text("انصراف") } },
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
          Text("انتقال بین محل‌ها", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
          Text("موجودیِ کلِ کالا عوض نمی‌شود؛ فقط جایش. بها به میانگینِ مبدأ می‌رود.", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          Spacer(Modifier.height(12.dp))
          val chosen = d.products.find { it.id == productId }
          if (chosen != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(chosen.name, style = MaterialTheme.typography.bodyLarge, color = Shop.colors.text, modifier = Modifier.weight(1f))
              TextButton(onClick = { productId = null; lot = null }) { Text("کالای دیگر") }
            }
            pv?.let { Text(it.locations.filter { l -> l.qtyMilli != 0L }.joinToString(" · ") { l -> "${StockActions.locationLabel(l.location)}: ${milliText(l.qtyMilli)}" }, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted) }
          } else {
            SearchBox(search, { search = it }, "نامِ کالا…", Modifier.fillMaxWidth())
            d.products.filter { it.active && (search.isBlank() || it.name.contains(search.trim(), ignoreCase = true)) }.sortedBy { it.name }.take(6).forEach { p ->
              Text(
                p.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text,
                modifier = Modifier.fillMaxWidth().clip(Shape.button).clickable { productId = p.id }.padding(10.dp),
              )
            }
          }
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("از", color = Shop.colors.muted)
            FilterMenu("مبدأ", locations.map { it to StockActions.locationLabel(it) }, from, { from = it ?: StockLedger.MAIN; lot = null }, StockActions.MAIN_LABEL)
            Text("به", color = Shop.colors.muted)
            if (!newPlace) {
              FilterMenu("مقصد", locations.map { it to StockActions.locationLabel(it) }, to, { to = it ?: StockLedger.MAIN }, StockActions.MAIN_LABEL)
            }
            TextButton(onClick = { newPlace = !newPlace }) { Text(if (newPlace) "از فهرست" else "+ محلِ تازه") }
          }
          if (newPlace) {
            OutlinedTextField(fresh, { fresh = it.take(40) }, label = { Text("نامِ محلِ تازه، مثلاً انبارِ پشتی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          }
          Spacer(Modifier.height(8.dp))
          NumberField(amount, { amount = it }, "مقدار", Modifier.fillMaxWidth())
          if (lots.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FilterMenu(
              "سری", lots.map { it.lotId to "${StockActions.lotLabel(it.lotId)} · ${milliText(it.qtyMilli)}" + (it.expiryDate?.let { e -> " · ${formatDate(e)}" } ?: "") },
              lot, { lot = it }, "به ترتیبِ FEFO",
            )
          }
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(reason, { reason = it.take(80) }, label = { Text("دلیل (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          Spacer(Modifier.height(16.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
            Button(
              enabled = productId != null,
              onClick = {
                val dest = if (newPlace) StockActions.locationOf(fresh) else to
                onSave(productId ?: return@Button, from, dest, amount.toDoubleOrNull() ?: 0.0, lot, reason)
              },
              modifier = Modifier.weight(1f),
            ) { Text("انتقال") }
          }
        }
      }
    }
  }
}

/* ============================== تنظیمات: کارتِ «موجودی و بها» ============================== */

/** عنوانِ بخش — همان که `DeskSettings` در ستونِ چپ نشان می‌دهد (`AppRoot`) */
const val STOCK_SETTINGS_TITLE = "موجودی و بها"

/**
 *  تنظیمِ دکان (نه دستگاه): ردیفِ `stock_mode` در دفترچهٔ ثبت، پس با همگام‌سازی به همهٔ
 *  دستگاه‌های همین دکان می‌رسد. پیش‌فرض خاموش؛ روشن/خاموش با تأیید.
 */
@Composable
fun StockModeSection(store: ShopStore, d: ShopData) {
  //  پرچمِ `shop.stock_v2` خاموش ⇒ کارت اصلاً دیده نمی‌شود (RULES، «⛔ استثنا»)
  if (!StockLedger.serverFlag) return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val on = remember(d.auditLog) { StockLedger.modeOn(d) }
  val row = StockLedger.modeRow(d)
  var ask by remember { mutableStateOf<Boolean?>(null) }
  SettingsSection(
    icon = Icons.Filled.SwapHoriz,
    title = STOCK_SETTINGS_TITLE,
    subtitle = if (on) "موتورِ تازه روشن (آزمایشی)" else "خاموش — مثلِ همیشه",
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(
      "روشن: بهای میانگینِ موزون با هزینهٔ جانبیِ خرید، سری و انقضا با برداشتِ FEFO سرِ صندوق (سریِ اسکن‌شده اول)، دفترِ حرکت با کاربر و دلیل، شمارش با بازبینی، انتقال بین محل‌ها و «وظیفهٔ بررسی» برای موجودیِ منفی. برای همهٔ دستگاه‌های همین دکان یکی است. خاموش: همه‌چیز مثل امروز.",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(4.dp))
    Text(
      "آزمایشی: روشِ بهای تمام‌شده هنوز تصمیمِ باز است (D-23)، پس بها و سود «تقریبی»اند. با روشن بودن، فروش از سریِ منقضی بسته است.",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to "خاموش", "on" to "روشن"),
      selected = if (on) "on" else "off",
      onSelect = { pick -> if ((pick == "on") != on) ask = pick == "on" },
    )
    row?.let {
      Spacer(Modifier.height(6.dp))
      Text("آخرین تغییر: ${formatDate(it.date.ifBlank { todayIso() })} — ${it.notes}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
  }
  ask?.let { next ->
    AlertDialog(
      onDismissRequest = { ask = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(if (next) "موتورِ تازهٔ موجودی و بها روشن شود؟" else "موتورِ تازه خاموش شود؟", color = Shop.colors.text) },
      text = {
        Text(
          if (next) "روی همهٔ دستگاه‌های این دکان روشن می‌شود. موجودیِ هر کالا همان عددِ امروز می‌ماند؛ بهای فروشِ تازه به میانگینِ موزون (تقریبی) ثبت می‌شود و سریِ منقضی فروخته نمی‌شود. هر وقت بخواهید خاموش می‌شود."
          else "فروش و خرید به رفتارِ امروز برمی‌گردد. چیزی از دفتر پاک نمی‌شود.",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          ask = null
          val user = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("")
          scope.launch { store.save(StockActions.setMode(d, next, user, todayIso(), System.currentTimeMillis(), ::newId)) }
        }) { Text(if (next) "روشن کن" else "خاموش کن") }
      },
      dismissButton = { TextButton(onClick = { ask = null }) { Text("انصراف") } },
    )
  }
}

/* ============================== نوارِ صفحهٔ انبار ============================== */

/** فقط وقتی موتور روشن است: ارزش به بهای میانگین، وظیفه‌های باز و سری‌های منقضی؛ زدنِ هر کارت ⇐ «دفترِ موجودی» */
@Composable
fun StockStrip(d: ShopData, onOpen: () -> Unit) {
  val on = remember(d.auditLog, StockLedger.serverFlag) { StockLedger.isOn(d) }
  if (!on) return
  val view by produceState<StockLedger.View?>(null, d) { value = withContext(Dispatchers.Default) { StockLedger.replay(d) } }
  val v = view ?: return
  val stats = remember(v, d) { bookStats(d, v, todayIso()) }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    StatCard("ارزشِ موجودی به بهای میانگین", minorText(stats.valueMinor) + " ؋", "تقریبی (D-23)", Modifier.widthIn(min = 190.dp), onClick = onOpen)
    StatCard("وظیفهٔ بررسیِ باز", stats.openTasks.toString(), if (stats.openTasks > 0) "موجودیِ منفی یا فروشِ منقضی" else "همه بررسی شده", Modifier.widthIn(min = 160.dp), onClick = onOpen)
    StatCard("سریِ منقضیِ دارای موجودی", stats.expiredLots.toString(), "فروخته نمی‌شود", Modifier.widthIn(min = 160.dp), onClick = onOpen)
  }
}
