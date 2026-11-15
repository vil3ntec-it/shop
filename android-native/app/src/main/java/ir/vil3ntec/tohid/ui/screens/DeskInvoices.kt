package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.invoiceLabel
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CreditScore
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskCell
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDataShot
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskGrid
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskInfoRow
import ir.vil3ntec.tohid.ui.desk.DeskKpiCard
import ir.vil3ntec.tohid.ui.desk.DeskKpiRow
import ir.vil3ntec.tohid.ui.desk.DeskMenuItem
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskOverflow
import ir.vil3ntec.tohid.ui.desk.DeskPage
import ir.vil3ntec.tohid.ui.desk.DeskPane
import ir.vil3ntec.tohid.ui.desk.DeskPaneHint
import ir.vil3ntec.tohid.ui.desk.DeskSearchField
import ir.vil3ntec.tohid.ui.desk.DeskSkeleton
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskSubhead
import ir.vil3ntec.tohid.ui.desk.DeskToolbar
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskPaneWidth
import ir.vil3ntec.tohid.ui.desk.rememberDeskLoad
import ir.vil3ntec.tohid.ui.v3.RowWhen
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.sales.history.DrawerLine
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryActions
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryBook
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryChannel
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryFilter
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryModel
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryPeriods
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryQuery
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryRow
import ir.vil3ntec.tohid.ui.v3.sales.history.PrintBanner
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import ir.vil3ntec.tohid.ui.v3.sales.history.channelName
import ir.vil3ntec.tohid.ui.v3.sales.history.filterName
import ir.vil3ntec.tohid.ui.v3.sales.history.filteredOf
import ir.vil3ntec.tohid.ui.v3.sales.history.matches
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCustomerPages
import ir.vil3ntec.tohid.core.i18n.StrDesk
import ir.vil3ntec.tohid.core.i18n.StrDeskData
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrLook
import ir.vil3ntec.tohid.core.i18n.StrPayStatus
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrSale

/**
 *  تاریخچهٔ فروش و فاکتورها — **کامپیوتر** (`claude/e-desk-d3b`؛ هم `sales` و هم `sales/table`).
 *
 *  خواستِ صاحب‌کار: «کامپیوتر شبیهِ موبایل نباشد … حرفه‌ای و شیک، آرام». یک صفحه:
 *   • ردیفِ پنج کارتِ KPI ِ همان دوره (فروش، نقد، نسیه، برگشت، خالص — همان جمعِ X/Z ِ `SalesPeriods`)، با تغییر نسبت به
 *     دورهٔ پیش و خطِ روزانهٔ ماه/سال؛
 *   • یک نوارِ ابزار: جست‌وجو (Ctrl+F)، دوره (کادرِ کشوییِ همان `HistoryPeriods`: امروز، دیروز، این ماه، ماه‌های پیشین،
 *     امسال…)، روشِ پرداخت/لغوشده، «کجا»؛ صندوق، اکسل (Ctrl+E) و «…» (چاپِ فهرست Ctrl+P، چاپِ دوره، برگشتِ بی رسید،
 *     فروشِ تازه، جدولِ لغو و برگشتی) — می‌پیچد، بریده نمی‌شود؛
 *   • جدولِ آرامِ تنبل (زمان، شماره، مشتری، اقلام، مبلغ، واژهٔ پرداخت، واژهٔ وضعیت) — بی نشانِ رنگی؛
 *   • ردیف ⇐ پنلِ کنار با اقلام، جمع‌ها، وضعیتِ پرداخت (PAY-07) و کارها: نمایش و چاپ، برگشت، لغو؛ «…»: دیدنِ فاکتور،
 *     چاپِ A4، اصلاح، رسیدهای برگشت، حسابِ مشتری. همان مسیرهای نسخهٔ ۳ (`sales/{id}/return|cancel|fix`)، پس همان
 *     دسترسی‌ها و برگهٔ تأییدِ مدیر.
 *  دفترِ تاریخچه (`HistoryBook`) و جست‌وجوی همهٔ دوره‌ها بیرون از رشتهٔ صفحه ساخته می‌شوند.
 *
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrInvoices`/`StrDeskData` (LOC-06)؛ برگهٔ اکسل/چاپِ A4 داده است و همان فارسی می‌ماند.
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
  val nav = ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav.current
  val v2 = ir.vil3ntec.tohid.ui.v3.sales.history.returnsV2()
  val query = remember { HistoryQuery(HistoryFilter.ALL, "", SalesPeriods.Unit.MONTH) }
  val gregorian = HistoryBook.gregorianNow()
  val book = rememberDeskLoad(d, gregorian) {
    HistoryBook.build(d, ir.vil3ntec.tohid.data.ReportBridge.deviceClock(), ir.vil3ntec.tohid.data.ReportBridge.nowMs(), gregorian)
  }
  //  بی ناوبریِ نسخهٔ ۳ (آزمون‌های قدیم) ⇒ همان جدولِ «لغو و برگشتی» ِ پیشین برای برگشت/لغو/اصلاح
  fun go(path: String) { if (nav != null) nav.open(path) else classic = true }
  val actions = HistoryActions(
    open = { go(SalesPaths.invoice(it)) },
    ret = { go(SalesPaths.returnOf(it)) },
    cancel = { go(SalesPaths.cancelOf(it)) },
    fix = { go(SalesPaths.fixOf(it)) },
    returnReceipt = { go(SalesPaths.returnReceipt(it)) },
    drawer = { go(SalesPaths.DRAWER) },
    noReceipt = if (v2 && nav != null) ({ nav.open(SalesPaths.RETURN_NONE) }) else null,
    scanReceipt = null,
    newSale = onEdit,
    back = null,
    payReview = if (nav != null) ({ nav.open(SalesPaths.PAY_REVIEW) }) else null,
  )
  DeskHistoryPage(
    store = store, d = d, snackbar = snackbar, book = book, query = query, actions = actions,
    returnsV2 = v2, canCancel = null, askCancel = null, drawer = null, reviews = 0, showMoney = true, banner = null,
    shopTag = null, classic = { classic = true },
  )
}

/**
 *  بدنهٔ مشترکِ «تاریخچهٔ فروش» ِ کامپیوتر: `sales` (`HistoryHost`، با صندوق، چاپِ دوره و «نیازمند بررسی») و
 *  `sales/table` ([DeskInvoicesScreen]). [money] = false ⇒ شمارشِ کور (PAY-13): جمع‌های پولیِ دوره دیده نمی‌شوند.
 */
@Composable
internal fun DeskHistoryPage(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  book: HistoryBook?,
  query: HistoryQuery,
  actions: HistoryActions,
  returnsV2: Boolean,
  /** null ⇒ از نقشِ همین دستگاه (`ShopRole.canCancelSale`، `CancelAsk.offered`) */
  canCancel: Boolean?,
  askCancel: Boolean?,
  drawer: DrawerLine?,
  reviews: Int,
  showMoney: Boolean,
  banner: PrintBanner?,
  shopTag: String?,
  classic: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val nav = ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav.current
  val mayCancel = canCancel ?: remember { ir.vil3ntec.tohid.data.ShopRole.canCancelSale(context) }
  val mayAsk = askCancel ?: remember { ir.vil3ntec.tohid.data.CancelAsk.offered(context) }
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  val searchFocus = remember { FocusRequester() }
  var picked by remember { mutableStateOf<String?>(null) }
  var show by remember { mutableStateOf<Sale?>(null) }
  val today = book?.today ?: ir.vil3ntec.tohid.todayIso()

  val period = book?.pick(query.unit, query.picked)
  val term = query.search.trim()
  //  جست‌وجو در همهٔ دوره‌ها (شماره، مشتری، QR ِ رسید) — بیرون از رشتهٔ صفحه
  val found = rememberDeskLoad(d, query.filter, query.channel, term, returnsV2, shopTag) {
    if (term.isEmpty()) null
    else HistoryModel.shown(d, query.filter, term, HistoryModel.ALL_MONTHS, returnsV2, shopTag).filter { HistoryFilter.ALL.matches(it, query.channel) }
  }
  val list: List<Sale> = when {
    term.isNotEmpty() -> found.orEmpty()
    period != null -> filteredOf(period, query)
    else -> emptyList()
  }
  val rows = remember(list, book) { HashMap<String, HistoryRow>() }
  fun rowOf(s: Sale): HistoryRow = rows.getOrPut(s.id) {
    val b = book
    if (b != null) HistoryModel.row(d, s, returnsV2, look = b.look, withDate = true, today = today) else HistoryModel.row(d, s, returnsV2)
  }
  //  عکسِ CI: نخستین ردیف برگزیده
  val shotPick = DeskDataShot.pick.value
  LaunchedEffect(shotPick, list.isNotEmpty()) { if (shotPick == "first") list.firstOrNull()?.let { picked = it.id } }
  val selected = remember(list, picked) { list.indexOfFirst { it.id == picked }.takeIf { it >= 0 } }
  val sale = selected?.let { list[it] }
  val online = remember(d) { d.sales.any { ir.vil3ntec.tohid.data.CashShift.isOnline(it) } }
  val payUser = remember {
    val role = ir.vil3ntec.tohid.data.ShopRole
    DeskPayUser(
      sell = role.canWrite(context, "sales"), manage = !role.isStaff(context),
      me = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim().ifEmpty { role.current(context) },
    )
  }

  fun sheet(): ReportCsv.Sheet {
    val names = d.debtors.associate { it.id to it.name }
    val counts = book?.look?.items
    val rows = mutableListOf(listOf("شماره", "تاریخ", "مشتری", "اقلام", "جمع", "تخفیف", "قابل پرداخت", "پرداخت‌شده", "باقی", "نوع", "فروشنده", "وضعیت")) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
    list.forEach { s ->
      rows += listOf(s.invoiceLabel().orEmpty(), s.date, s.debtorId?.let(names::get).orEmpty(), (counts?.get(s.id)?.size ?: 0).toString(),
        Math.round(s.total).toString(), Math.round(s.discount).toString(), Math.round(s.finalTotal).toString(),
        Math.round(s.paidAmount).toString(), Math.round(s.remaining).toString(),
        ir.vil3ntec.tohid.data.PayFlow.payLabel(s), s.seller, if (s.status == "cancelled") "لغوشده" else "ثبت‌شده") // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
    }
    val span = period?.let { "${it.from}-تا-${it.to}" } ?: today // داده: نامِ پروندهٔ خروجی
    return ReportCsv.Sheet("فاکتورهای-فروش-$span", rows) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
  }
  fun excel() { val sh = sheet(); Platform.saveFile("${sh.name}.xlsx", Xlsx.of(sh))?.let { toast(StrInvoices.savedTo(it)) } }
  fun printList() { sheet().let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } }

  DeskPage(
    Modifier.onPreviewKeyEvent { e ->
      if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
      when {
        e.isPrimaryModifier && e.key == Key.F -> { runCatching { searchFocus.requestFocus() }; true }
        e.isPrimaryModifier && e.key == Key.P -> { printList(); true }
        e.isPrimaryModifier && e.key == Key.E -> { excel(); true }
        e.key == Key.Escape && picked != null && show == null -> { picked = null; true }
        else -> false
      }
    },
  ) {
    banner?.let { b -> DeskBanner(b) }
    HistoryKpis(book, period, showMoney, term.isNotEmpty())
    DeskToolbar(
      end = {
        if (reviews > 0) actions.payReview?.let { DeskButton(StrPayStatus.reviewCount(ir.vil3ntec.tohid.plain(reviews)), it, icon = Icons.Outlined.CreditScore) }
        DeskButton(if (drawer?.open == true) StrDeskData.drawerOpen else StrSale.drawerWord, actions.drawer, icon = Icons.Outlined.PointOfSale)
        DeskButton(StrReports.excel, ::excel, icon = Icons.Outlined.TableChart, shortcut = "Ctrl+E")
        DeskOverflow(
          buildList {
            add(DeskMenuItem(StrDeskData.printList, Icons.Outlined.Print) { printList() })
            val p = period
            val print = actions.print
            if (print != null && p != null && term.isEmpty()) add(DeskMenuItem(StrDeskData.printPeriod, Icons.Outlined.Print) { print(p) })
            actions.noReceipt?.let { add(DeskMenuItem(StrSale.noReceiptReturn, Icons.AutoMirrored.Outlined.AssignmentReturn, divider = true, onClick = it)) }
            add(DeskMenuItem(StrSale.newSale, Icons.Outlined.PointOfSale, onClick = actions.newSale))
            classic?.let { add(DeskMenuItem(StrInvoices.cancelAndReturns, Icons.AutoMirrored.Outlined.ReceiptLong, divider = true, onClick = it)) }
          },
        )
      },
    ) {
      DeskSearchField(query.search, { query.search = it }, StrInvoices.deskSearchHint, Modifier.width(320.dp), searchFocus)
      val choices = remember(book, ir.vil3ntec.tohid.core.i18n.I18n.lang) { book?.let(HistoryPeriods::choices).orEmpty() }
      DeskDropdown(
        value = HistoryPeriods.selected(query),
        options = choices.map { DeskOption(it.key, it.label, it.divider) },
        onSelect = { HistoryPeriods.apply(query, it); picked = null },
        label = StrLook.period,
        shown = HistoryPeriods.label(book, query),
      )
      DeskDropdown(query.filter, HistoryFilter.entries.map { DeskOption(it, filterName(it)) }, { query.filter = it; picked = null }, label = StrDesk.colPayment)
      if (online) DeskDropdown(query.channel, HistoryChannel.entries.map { DeskOption(it, channelName(it)) }, { query.channel = it; picked = null }, label = StrHistory.where)
    }
    if (book == null) {
      DeskSkeleton(Modifier.fillMaxWidth().weight(1f))
      return@DeskPage
    }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
      //  پنل همیشه در پهنای بزرگ (۱۹۲۰)؛ در ۱۳۶۶ فقط با ردیفِ برگزیده تا جدول تنگ نشود
      val paneW = deskPaneWidth(maxWidth)
      val paneOn = sale != null || maxWidth >= 1280.dp
      val mainW = if (paneOn) maxWidth - paneW - DeskSpace.gap else maxWidth
      val compact = mainW < 860.dp
      val day = query.unit == SalesPeriods.Unit.DAY && term.isEmpty()
      val cols = buildList {
        add(DeskColumn(StrDeskData.colWhen, width = if (day) 64.dp else 120.dp))
        add(DeskColumn(StrInvoices.colNumber, width = 84.dp))
        add(DeskColumn(StrPos.customer, weight = 1f))
        if (!compact) add(DeskColumn(StrInvoices.colItems, width = 52.dp, end = true))
        add(DeskColumn(StrDesk.colAmount, width = 116.dp, end = true))
        add(DeskColumn(StrDesk.colPayment, width = 84.dp))
        add(DeskColumn(StrDesk.colStatus, width = 104.dp))
      }
      Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
          DeskNote(if (term.isNotEmpty()) StrDeskData.results(ir.vil3ntec.tohid.plain(list.size)) else StrHistory.invoices(ir.vil3ntec.tohid.plain(list.size)))
          DeskGrid(
            columns = cols,
            count = list.size,
            modifier = Modifier.fillMaxWidth().weight(1f),
            key = { list[it].id },
            selected = selected,
            onPick = { picked = list[it].id },
            onOpen = { show = list[it] },
            rowLabel = { rowOf(list[it]).let { r -> "${r.number} ${r.title}" } },
            empty = {
              if (term.isNotEmpty()) V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, StrSale.noInvoiceMatch)
              else V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, StrInvoices.emptyRange, action = StrSale.newSale, onAction = actions.newSale)
            },
          ) { i, c ->
            val s = list[i]
            val r = rowOf(s)
            val off = s.status == "cancelled"
            when (cols[c].title) {
              StrDeskData.colWhen -> DeskCell(
                if (day) SaleTime.clock(s.createdAt).ifEmpty { "—" }
                else RowWhen.of(book.dayOf(s.id)?.date ?: s.date, today, SaleTime.clock(s.createdAt)),
                muted = true,
              )
              StrInvoices.colNumber -> DeskCell(r.number, muted = off)
              StrPos.customer -> DeskCell(if (s.debtorId != null) r.name else r.title, muted = off || s.debtorId == null)
              StrInvoices.colItems -> DeskCell(ir.vil3ntec.tohid.plain(book.look.items[s.id]?.size ?: 0), muted = true)
              StrDesk.colAmount -> DeskCell(money(s.finalTotal), strong = !off, muted = off)
              StrDesk.colPayment -> DeskCell(if (off) ir.vil3ntec.tohid.data.PayFlow.payLabel(s) else r.payWord, muted = true)
              else -> DeskCell(statusWord(r), muted = true)
            }
          }
        }
        if (paneOn) {
          Box(Modifier.width(paneW).fillMaxHeight()) {
            if (sale == null) DeskPaneHint(Icons.AutoMirrored.Outlined.ReceiptLong, StrDeskData.pickInvoice, Modifier.fillMaxSize())
            else InvoicePane(
              store, d, sale, rowOf(sale), book, actions, returnsV2, mayCancel, mayAsk, payUser, ::toast,
              onShow = { show = sale }, onClose = { picked = null },
              onA4 = { saleSheet(d, sale).let { ir.vil3ntec.tohid.print.ReportPrint.print(context, it, it.name) } },
              onDebtor = sale.debtorId?.let { id -> nav?.let { n -> { n.open(ir.vil3ntec.tohid.ui.v3.money.MoneyPaths.debtor(id)) } } },
            )
          }
        }
      }
    }
  }

  show?.let { s -> InvoiceDialog(d = d, sale = s, onDismiss = { show = null }, onMessage = ::toast) }
}

/** واژهٔ وضعیت: لغوشده، وضعیتِ پرداختِ غیرنقدِ تأییدنشده (PAY-07)، برگشتِ همه/بخشی، وگرنه «ثبت‌شده» — همه کم‌رنگ */
private fun statusWord(r: HistoryRow): String = when {
  r.state == ir.vil3ntec.tohid.ui.v3.sales.history.SaleState.CANCELLED -> StrSale.cancelled
  r.status != null -> r.status.text
  r.refundedAll -> StrDeskData.fullyReturned
  r.refunded > 0 -> StrDeskData.partlyReturned
  else -> StrInvoices.recorded
}

/**
 *  پنج کارتِ دوره — همان جمعِ دفترِ X/Z: فروش (و تغییر نسبت به دورهٔ پیش)، نقد، نسیه و برگشت (سهمشان از فروش) و
 *  خالص. ماه و سال خطِ کوچکِ روزانه/ماهانه دارند. رنگ‌ها آرام و از نقشهٔ یکتای رنگ‌ها (`DeskHues`) — بی صورتی.
 */
@Composable
private fun HistoryKpis(book: HistoryBook?, p: SalesPeriods.Period?, showMoney: Boolean, searching: Boolean) {
  val t = p?.totals ?: SalesPeriods.Totals()
  val prev = remember(book, p?.key) { if (book != null && p != null) previousOf(book, p) else null }
  val spark = remember(book, p?.key) { if (book != null && p != null) sparkOf(book, p) else emptyList() }
  fun kpiAmount(minor: Long) = if (showMoney) "${ir.vil3ntec.tohid.money(minor / 100.0)} ؋" else "—"
  fun pct(now: Long, before: Long?): Int? = if (before == null || before <= 0L) null else (((now - before) * 100.0) / before).toInt()
  fun share(part: Long): String? = if (!showMoney || t.sales <= 0L) null else StrDeskData.shareOfSales(ir.vil3ntec.tohid.plain(Math.round(part * 100.0 / t.sales).toInt()))
  val versus = StrDeskData.versusPrev
  val noPrev = if (searching) null else StrDeskData.noPrevSales
  DeskKpiRow {
    val m = Modifier.weight(1f).fillMaxHeight()
    DeskKpiCard(ir.vil3ntec.tohid.ui.desk.DeskKpi(StrDeskData.kpiSales, kpiAmount(t.sales), Icons.AutoMirrored.Outlined.TrendingUp, DeskHues.tone(V3Hue.SKY),
      pct = if (showMoney) pct(t.sales, prev?.sales) else null, versus = versus, fallback = if (showMoney) noPrev else StrHistory.invoices(ir.vil3ntec.tohid.plain(t.invoices)),
      spark = if (showMoney) spark else emptyList()), m)
    DeskKpiCard(ir.vil3ntec.tohid.ui.desk.DeskKpi(Str.cash, kpiAmount(t.cash), Icons.Outlined.Payments, DeskHues.tone(V3Hue.MINT), fallback = share(t.cash)), m)
    DeskKpiCard(ir.vil3ntec.tohid.ui.desk.DeskKpi(Str.credit, kpiAmount(t.credit), Icons.Outlined.CreditScore, DeskHues.tone(V3Hue.AMBER), fallback = share(t.credit)), m)
    DeskKpiCard(ir.vil3ntec.tohid.ui.desk.DeskKpi(StrHistory.returns, kpiAmount(t.refunds), Icons.AutoMirrored.Outlined.AssignmentReturn, DeskHues.tone(V3Hue.VIOLET),
      fallback = StrDeskData.returnsCount(ir.vil3ntec.tohid.plain(t.returns))), m)
    DeskKpiCard(ir.vil3ntec.tohid.ui.desk.DeskKpi(StrReports.netSales, kpiAmount(t.net), Icons.Outlined.AccountBalanceWallet, DeskHues.tone(V3Hue.BRAND),
      pct = if (showMoney) pct(t.net, prev?.net) else null, versus = versus, fallback = if (showMoney) noPrev else null), m)
  }
}

/** جمعِ دورهٔ درست پیش از [p] (دیروز، ماهِ پیش، پارسال)؛ بی فروش ⇒ null */
private fun previousOf(book: HistoryBook, p: SalesPeriods.Period): SalesPeriods.Totals? {
  val key = when (p.unit) {
    SalesPeriods.Unit.DAY -> CivilDays.addDays(p.key, -1) ?: return null
    SalesPeriods.Unit.MONTH -> {
      val y = p.key.take(4).toIntOrNull() ?: return null
      val m = p.key.drop(5).toIntOrNull() ?: return null
      if (m == 1) (y - 1).toString().padStart(4, '0') + "-12" else y.toString().padStart(4, '0') + "-" + (m - 1).toString().padStart(2, '0')
    }
    SalesPeriods.Unit.YEAR -> (p.key.toIntOrNull() ?: return null).minus(1).toString().padStart(4, '0')
  }
  return book.find(p.unit, key)?.totals?.takeIf { !it.isEmpty }
}

/** خطِ کوچکِ کارتِ فروش: روزهای ماه، یا ماه‌های سال (کهنه‌ترین اول) */
private fun sparkOf(book: HistoryBook, p: SalesPeriods.Period): List<Double> = when (p.unit) {
  SalesPeriods.Unit.DAY -> emptyList()
  SalesPeriods.Unit.MONTH -> book.days.filter { it.date >= p.from && it.date <= p.to }.sortedBy { it.date }.map { it.totals.sales / 100.0 }
  SalesPeriods.Unit.YEAR -> book.periods(SalesPeriods.Unit.MONTH).filter { it.from >= p.from && it.to <= p.to }.sortedBy { it.key }.map { it.totals.sales / 100.0 }
}

/** نوارِ چاپِ در جریان یا تمام‌شده (`PeriodPrint`) — یک خطِ آرام با «لغو» یا «بستن» */
@Composable
private fun DeskBanner(b: PrintBanner) {
  val c = Desk.colors
  Row(Modifier.fillMaxWidth().deskCard(c).padding(horizontal = DeskSpace.card, vertical = DeskSpace.x3), verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Outlined.Print, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(DeskSpace.x3))
    Text(b.text, Modifier.weight(1f), style = DeskType.body, color = c.ink, maxLines = 2)
    if (b.running) DeskButton(Str.cancel, b.onCancel, kind = DeskButtonKind.GHOST) else DeskButton(Str.close, b.onDismiss, kind = DeskButtonKind.GHOST)
  }
}

/** پنلِ فاکتورِ برگزیده: اقلام، جمع‌ها، پرداخت و کارها */
@Composable
private fun InvoicePane(
  store: ShopStore,
  d: ShopData,
  sale: Sale,
  row: HistoryRow,
  book: HistoryBook,
  actions: HistoryActions,
  returnsV2: Boolean,
  canCancel: Boolean,
  askCancel: Boolean,
  payUser: DeskPayUser,
  toast: (String) -> Unit,
  onShow: () -> Unit,
  onClose: () -> Unit,
  onA4: () -> Unit,
  onDebtor: (() -> Unit)?,
) {
  val c = Desk.colors
  val lines = book.look.items[sale.id].orEmpty()
  val when_ = RowWhen.of(book.dayOf(sale.id)?.date ?: sale.date, book.today, SaleTime.clock(sale.createdAt))
  val who = if (sale.debtorId != null) row.name else Str.walkIn
  val off = sale.status == "cancelled"
  DeskPane(
    title = StrDeskData.invoiceTitle(row.number),
    subtitle = listOf(who, when_).filter { it.isNotBlank() }.joinToString(" • "),
    onClose = onClose,
    actions = {
      DeskButton(StrHistory.print, onShow, kind = DeskButtonKind.PRIMARY, icon = Icons.Outlined.Print)
      if (row.canReturn) DeskButton(if (returnsV2) StrSale.returnOrExchange else StrSale.returnWord, { actions.ret(sale.id) }, icon = Icons.AutoMirrored.Outlined.AssignmentReturn)
      if (row.canCancel && (canCancel || askCancel)) DeskButton(StrSale.cancelSale, { actions.cancel(sale.id) }, icon = Icons.Outlined.Block)
      Spacer(Modifier.weight(1f))
      DeskOverflow(
        buildList {
          add(DeskMenuItem(StrSale.viewInvoice, Icons.AutoMirrored.Outlined.ReceiptLong) { actions.open(sale.id) })
          add(DeskMenuItem(StrInvoices.printA4, Icons.Outlined.Print, onClick = onA4))
          if (row.canCancel && canCancel) add(DeskMenuItem(StrSale.fixInvoice, Icons.Outlined.PointOfSale) { actions.fix(sale.id) })
          row.returnDocs.forEach { doc -> add(DeskMenuItem(StrSale.returnReceiptOf(ir.vil3ntec.tohid.data.ReturnFlow.label(doc)), Icons.AutoMirrored.Outlined.ReceiptLong) { actions.returnReceipt(doc) }) }
          onDebtor?.let { add(DeskMenuItem(ir.vil3ntec.tohid.core.i18n.StrMoney.debtor, Icons.Outlined.AccountBalanceWallet, divider = true, onClick = it)) }
        },
        label = StrDeskData.moreForInvoice,
      )
    },
  ) {
    DeskSubhead(StrInvoices.colItems + " · " + ir.vil3ntec.tohid.plain(lines.size))
    Column(Modifier.fillMaxWidth()) {
      lines.forEach { l ->
        Row(Modifier.fillMaxWidth().padding(vertical = DeskSpace.x2), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(book.look.itemName(l), style = DeskType.body, color = c.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${qtyText(l.quantity)} × ${money(l.unitPrice)}", style = DeskType.secondary, color = c.inkSecondary, maxLines = 1)
          }
          Text(money(l.totalPrice), style = DeskType.table, color = c.ink, maxLines = 1)
        }
        DeskDivider()
      }
    }
    DeskInfoRow(StrInvoices.total, money(sale.total))
    if (sale.discount > 0) DeskInfoRow(Str.discount, money(sale.discount))
    DeskInfoRow(StrInvoices.colDue, "${money(sale.finalTotal)} ؋", strong = true)
    DeskInfoRow(StrInvoices.colPaid, money(sale.paidAmount))
    if (sale.remaining > 0.5) DeskInfoRow(StrInvoices.colBalance, money(sale.remaining), valueColor = c.negative)
    DeskInfoRow(StrDesk.colPayment, if (off) ir.vil3ntec.tohid.data.PayFlow.payLabel(sale) else row.payWord)
    DeskInfoRow(StrDesk.colStatus, statusWord(row))
    if (sale.seller.isNotBlank()) DeskInfoRow(StrInvoices.colSeller, sale.seller)
    if (off && sale.cancelReason.isNotBlank()) DeskNote(sale.cancelReason)
    DeskPayStatus(store, d, sale, payUser, toast)   // PAY-07: فاکتورِ بی ردیفِ غیرنقد ⇒ هیچ
  }
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
