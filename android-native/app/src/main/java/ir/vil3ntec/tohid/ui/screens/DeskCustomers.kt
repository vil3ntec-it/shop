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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import ir.vil3ntec.tohid.core.i18n.StrDeskData
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskCalmRow
import ir.vil3ntec.tohid.ui.desk.DeskCell
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskGrid
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskInfoRow
import ir.vil3ntec.tohid.ui.desk.DeskKpiRow
import ir.vil3ntec.tohid.ui.desk.DeskLetterTile
import ir.vil3ntec.tohid.ui.desk.DeskLink
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskPage
import ir.vil3ntec.tohid.ui.desk.DeskPane
import ir.vil3ntec.tohid.ui.desk.DeskPaneHint
import ir.vil3ntec.tohid.ui.desk.DeskRadius
import ir.vil3ntec.tohid.ui.desk.DeskSearchField
import ir.vil3ntec.tohid.ui.desk.DeskSegmented
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSkeleton
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskStatBox
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskToolbar
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskClickable
import ir.vil3ntec.tohid.ui.desk.rememberDeskLoad
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.v3.RowWhen
import ir.vil3ntec.tohid.ui.v3.V3EmptyState

/**
 *  مشتری‌ها و قرض‌داران — کامپیوتر (`claude/e-desk-d3b`).
 *
 *  خواستهٔ صاحب مخزن: «بیرون حساب‌هاشون مربعی باشه مثل موبایل و داخلش کادری یا شبیه اکسل ستونی و بردگی تاریخ… و فاکتور
 *  یارو هم باشه…»؛ و ۱۴۰۵/۰۸/۲۴: ظاهرِ قرض‌دارانِ گوشی (کارتِ مربعیِ سرخ/سبز، ردیفِ «برد» سرخ و «رسید» سبز، سه جعبهٔ رنگی)
 *  همان بماند. روی کامپیوتر همان هویت، چیده برای میز:
 *  • **بالا**: سه جعبهٔ رنگی (تعداد آبی، جمعِ قرض سرخ، رسیدها سبز) — همان `DebtorsStatsCard`.
 *  • **فهرست در آغاز**: جست‌وجو (Ctrl+F)، صافی و ترتیب (کادرِ کشویی)، «قرض‌دار تازه» (Ctrl+N)، اکسل؛ نمای ردیف (ردیفِ
 *    آرام با کاشیِ حرفِ سرخ/سبز و مانده) یا کارت (مربعِ سرخ/سبز). فهرست تنبل است (۵ هزار مشتری هم) و حسابش بیرون از رشتهٔ صفحه.
 *  • **پرونده در کنار**: نام و مانده به رنگِ حساب، کارها (F7 دریافت، F6 قرضِ دستی، سقف، …؛ Ctrl+P چاپ و Ctrl+E اکسلِ
 *    **همان زبانه**، Esc بستن) و سه زبانه: «صورت‌حساب» (ردیفِ «برد» سرخ، «رسید» سبز؛ فاکتور فقط درونِ ردیفِ «برد» و
 *    زبانهٔ «فاکتورها» — فهرستِ جدایی نیست)، «فاکتورها» و «اقلامِ برداشته».
 *
 *  همهٔ عددها از `CustomerEngine` — از خودِ دفتر، هیچ عددی جدا ذخیره نمی‌شود.
 */
@Composable
fun DeskCustomersScreen(store: ShopStore, d: ShopData, snackbar: SnackbarHostState, onOpenOrder: ((String) -> Unit)? = null) { // CRM-07: سفارشِ پرونده ⇐ `orders/{id}`
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  var openId by remember { mutableStateOf<String?>(null) }
  //  عکس‌های خودکار (`DevShot`، فرمانِ cust:first) ⇐ پروندهٔ بدهکارترین مشتری
  val shotOpen = DeskCustomersShot.open.value
  LaunchedEffect(shotOpen) {
    if (shotOpen == "first") openId = CustomerEngine.summaries(d).maxByOrNull { it.balance }?.id
    else if (shotOpen == "close") openId = null
  }
  val open = openId?.let { id -> d.debtors.find { it.id == id } }
  //  سه جعبهٔ رنگیِ قرض‌داران — همان هویتِ گوشی که صاحب‌کار پسندید (`DebtorsStatsCard`): آبی، سرخ، سبز
  val summary = rememberDeskLoad(d) { DebtorListModel.summary(d) }
  //  «خریدِ مشتری‌ها در این ماه» — همان عددِ کارتِ پیشین
  val month = ir.vil3ntec.tohid.todayIso().take(7)
  val monthBought = rememberDeskLoad(d, month) { d.sales.filter { it.debtorId != null && it.status != "cancelled" && it.date.startsWith(month) }.sumOf { it.finalTotal } }
  //  همهٔ ردیف‌های فهرست یک بار و بیرون از رشتهٔ صفحه (۵ هزار مشتری هم)
  val all = rememberDeskLoad(d) { CustomerCrm.active(d).map { it.id }.toSet().let { on -> CustomerEngine.summaries(d).filter { it.id in on } } }
  DeskPage {
    CustomerStats(summary, all, monthBought)
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
      val listW = (maxWidth * 0.34f).coerceIn(340.dp, 440.dp)
      Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
        CustomerCards(store, d, all, openId, ::toast, Modifier.width(listW).fillMaxHeight()) { openId = it }
        Box(Modifier.weight(1f).fillMaxHeight()) {
          if (open == null) DeskPaneHint(Icons.Outlined.PeopleOutline, StrDeskData.pickCustomer, Modifier.fillMaxSize())
          else key(open.id) { CustomerFile(store, d, snackbar, open.id, onOpenOrder) { openId = null } }
        }
      }
    }
  }
}

/** فقط برای عکس‌های خودکارِ CI (`desktop/.../DevShot.kt`) — در کارِ عادی دست نمی‌خورد */
object DeskCustomersShot {
  val open = mutableStateOf<String?>(null)
  val tab = mutableStateOf(-1)
}

/** رنگ‌های معنای حساب — همان `PhoneV3Debt` ِ کارت‌های سرخ/سبز (روشن و تیره)، نه رنگِ تازه */
private data class DebtTone(val ink: Color, val fill: Color)

/** بدهکار ⇒ سرخ (`owe`)، صاف یا پیش‌پرداخت ⇒ سبز (`settled`) — رنگِ نوشته و زمینهٔ کم‌رنگِ همان (خالص، آزمون‌شده) */
internal fun deskDebtTone(balance: Double, c: ir.vil3ntec.tohid.ui.theme.PhoneV3DebtColors): Pair<Color, Color> =
  if (balance > 0.5) c.owe to c.oweTint else c.settled to c.settledTint

@Composable
private fun debtTone(balance: Double): DebtTone = deskDebtTone(balance, PhoneV3Debt.colors).let { (ink, fill) -> DebtTone(ink, fill) }

/**
 *  سرِ صفحه: سه جعبهٔ رنگیِ قرض‌داران (تعداد آبی، جمعِ قرض سرخ، رسیدها سبز) و کنارشان دو عددِ آرامِ پیشین (دیرکردِ بیش از
 *  ۶۰ روز، خریدِ این ماه) — هیچ عددی گم نشد.
 */
@Composable
private fun CustomerStats(summary: DebtorListModel.Summary?, all: List<CustomerEngine.Summary>?, monthBought: Double?) {
  val c = PhoneV3Debt.colors
  val late = all?.count { (it.debtDays ?: 0) > 60 }
  val debtors = all?.count { it.balance > 0.5 }
  DeskKpiRow {
    DeskStatBox(ir.vil3ntec.tohid.core.i18n.StrMoney.statPeople, summary?.let { plain(it.people) } ?: "—", c.info, c.infoTint, Modifier.weight(1f),
      caption = debtors?.let { ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vDebtors + ": " + ir.vil3ntec.tohid.core.i18n.StrHome.people(it) }, icon = Icons.Outlined.PeopleOutline)
    DeskStatBox(ir.vil3ntec.tohid.core.i18n.StrMoney.statOwed, summary?.let { "${money(it.owed)} ؋" } ?: "—", c.owe, c.oweTint, Modifier.weight(1f),
      caption = late?.let { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lateKpi + ": " + ir.vil3ntec.tohid.core.i18n.StrHome.people(it) }, icon = Icons.Outlined.NorthEast)
    DeskStatBox(ir.vil3ntec.tohid.core.i18n.StrMoney.statReceived, summary?.let { "${money(it.received)} ؋" } ?: "—", c.settled, c.settledTint, Modifier.weight(1f),
      caption = monthBought?.let { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.monthKpi + ": " + money(it) + " ؋" }, icon = Icons.Outlined.SouthWest)
  }
}

/* ══════════════════════════ فهرست: کارت‌ها یا ردیف‌ها ══════════════════════════ */

private enum class Filter(private val text: () -> String) { ALL({ ir.vil3ntec.tohid.core.i18n.Str.all }), DEBT({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.owing }), LATE({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lateFilter }), BUYERS({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.buyers }), CLEAR({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noBalance }); val label: String get() = text() }
private enum class Sort(private val text: () -> String) { DEBT({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.sortMostOwed }), RECENT({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.sortLatest }), BOUGHT({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.sortMostBought }), NAME({ ir.vil3ntec.tohid.core.i18n.StrGrid.name }); val label: String get() = text() }

/**
 *  فهرستِ مشتری‌ها در آغازِ صفحه: جست‌وجو، صافی و ترتیب (کادرِ کشویی)، «مشتریِ تازه»، اکسل و نمای «ردیف/کارت». ردیف همان
 *  ردیفِ آرام است (نام، شماره • آخرین خرید، مانده و واژهٔ حالش) با کاشیِ حرفِ سرخ/سبز؛ کارت همان مربعِ سرخ/سبزِ گوشی.
 */
@Composable
private fun CustomerCards(
  store: ShopStore,
  d: ShopData,
  all: List<CustomerEngine.Summary>?,
  selectedId: String?,
  toast: (String) -> Unit,
  modifier: Modifier,
  onOpen: (String) -> Unit,
) {
  val context = LocalContext.current
  var search by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf(Filter.ALL) }
  var sort by remember { mutableStateOf(Sort.DEBT) }
  var adding by remember { mutableStateOf(false) }
  //  نمای کارتی/جدولی (VISUAL-DIRECTION §۱٫۵) — پیش‌فرضِ مدیریت جدول؛ جست‌وجو، صافی و ترتیب با عوض‌شدنِ نما دست نمی‌خورند
  val view = rememberViewMode("customers", ScreenKind.MANAGEMENT)
  //  پروندهٔ CRM (CRM-02/10/11) — فقط با «گزارش‌های مشتری» روشن (`ShopPolicy.crmReports`، پیش‌فرض خاموش)
  val crm = ShopPolicy.crmReports(context)
  var tagFilter by remember { mutableStateOf<String?>(null) }
  var mergeOpen by remember { mutableStateOf(false) }
  val searchFocus = remember { FocusRequester() }

  //  ادغام‌شده‌ها بیرون (CRM-10)؛ با CRM روشن جست‌وجو روی هر دو شماره و برچسب و صافیِ برچسب
  val list = rememberDeskLoad(all, search, filter, sort, tagFilter, crm) {
    val shown = CustomerProfileForm.visible(d, search, tagFilter, crm).map { it.id }.toSet()
    all.orEmpty().filter { it.id in shown }
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
  }.orEmpty()
  val contacts = ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts() // CRM-15: شمارهٔ مشتری در خروجی
  fun excel() {
    val rows = mutableListOf(listOf("نام", "تلفن", "مانده", "روزِ بدهی", "تعدادِ فاکتور", "جمعِ خرید", "آخرین خرید", "آخرین پرداخت")) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
    list.forEach { s ->
      rows += listOf(s.name, ir.vil3ntec.tohid.ui.components.customerPhoneExport(s.phone, contacts), Math.round(s.balance).toString(), s.debtDays?.toString().orEmpty(), s.invoices.toString(),
        Math.round(s.bought).toString(), s.lastPurchase.orEmpty(), s.lastPaid.orEmpty())
    }
    val sheet = ReportCsv.Sheet("مشتری‌ها-${ir.vil3ntec.tohid.todayIso()}", rows) // داده: برگهٔ اکسل/چاپ (خروجی) زبانِ صفحه را نمی‌گیرد
    Platform.saveFile("${sheet.name}.xlsx", Xlsx.of(sheet))?.let { toast(ir.vil3ntec.tohid.core.i18n.StrSale.savedTo(it)) }
  }
  //  Ctrl+N (پنجره) ⇐ «مشتریِ تازه»
  val add = remember { { adding = true } }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.ui.AppNav.addAction.value = add
    onDispose { if (ir.vil3ntec.tohid.ui.AppNav.addAction.value === add) ir.vil3ntec.tohid.ui.AppNav.addAction.value = null }
  }

  Column(
    modifier
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || !e.isPrimaryModifier) return@onPreviewKeyEvent false
        when (e.key) {
          Key.E -> { excel(); true }
          Key.F -> { runCatching { searchFocus.requestFocus() }; true }
          else -> false
        }
      },
    verticalArrangement = Arrangement.spacedBy(DeskSpace.x2),
  ) {
    DeskSearchField(search, { search = it }, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.searchHint, Modifier.fillMaxWidth(), searchFocus)
    DeskToolbar(
      end = {
        if (view.toggleVisible) DeskSegmented(listOf(ViewMode.TABLE to ir.vil3ntec.tohid.core.i18n.Str.tableView, ViewMode.CARD to ir.vil3ntec.tohid.core.i18n.Str.cardView), view.mode, { view.set(it) })
      },
    ) {
      DeskDropdown(filter, Filter.entries.map { DeskOption(it, it.label) }, { filter = it }, label = StrDeskData.show)
      DeskDropdown(sort, Sort.entries.map { DeskOption(it, it.label) }, { sort = it }, label = StrDeskData.sortLabel)
    }
    if (crm) CrmListTools(d, tagFilter, { tagFilter = it }, onMerge = { mergeOpen = true })
    if (adding) NewCustomerRow(store, d, toast, onCancel = { adding = false }) { onOpen(it); adding = false }
    Row(verticalAlignment = Alignment.CenterVertically) {
      DeskNote(
        ir.vil3ntec.tohid.core.i18n.StrCustomerPages.listHint(list.size, all?.size ?: 0, if (view.mode == ViewMode.TABLE) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.rowWord else ir.vil3ntec.tohid.core.i18n.StrCustomerPages.cardWord),
        Modifier.weight(1f),
        maxLines = 2,
      )
      if (!adding) DeskLink(ir.vil3ntec.tohid.core.i18n.StrMoney.newDebtor, { adding = true })
      DeskIconButton(Icons.Outlined.TableChart, ir.vil3ntec.tohid.core.i18n.StrInvoices.excelKey, ::excel)
    }

    val c = Desk.colors
    when {
      all == null -> DeskSkeleton(Modifier.fillMaxWidth().weight(1f))
      list.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f).deskCard(c), contentAlignment = Alignment.Center) {
        if (all.isEmpty()) V3EmptyState(Icons.Outlined.PeopleOutline, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noneYet, action = ir.vil3ntec.tohid.core.i18n.StrMoney.newDebtor, onAction = { adding = true })
        else V3EmptyState(Icons.Outlined.PeopleOutline, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noneMatch, action = ir.vil3ntec.tohid.core.i18n.StrReports.showAll, onAction = { search = ""; filter = Filter.ALL; tagFilter = null })
      }
      view.mode == ViewMode.TABLE -> {
        val state = rememberLazyListState()
        val today = ir.vil3ntec.tohid.todayIso()
        val sel = list.indexOfFirst { it.id == selectedId }
        LaunchedEffect(selectedId) { if (sel >= 0) { val seen = state.layoutInfo.visibleItemsInfo; if (seen.isEmpty() || sel < seen.first().index || sel > seen.last().index) state.scrollToItem(sel) } }
        LazyColumn(
          Modifier.fillMaxWidth().weight(1f).deskCard(c).padding(vertical = DeskSpace.x1)
            .onPreviewKeyEvent { e ->
              if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              when (e.key) {
                Key.DirectionDown -> { list.getOrNull((sel + 1).coerceAtMost(list.lastIndex))?.let { onOpen(it.id) }; true }
                Key.DirectionUp -> { list.getOrNull((sel - 1).coerceAtLeast(0))?.let { onOpen(it.id) }; true }
                else -> false
              }
            },
          state = state,
        ) {
          items(list, key = { it.id }) { r ->
            val tone = debtTone(r.balance)
            DeskCalmRow(
              title = r.name,
              sub = listOf(
                ir.vil3ntec.tohid.ui.components.customerPhone(r.phone, ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts()),
                r.lastPurchase?.let { RowWhen.of(it, today) }.orEmpty(),
              ).filter { it.isNotBlank() }.joinToString(" • "),
              amount = if (kotlin.math.abs(r.balance) < 0.5) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.settled else "${money(kotlin.math.abs(r.balance))} ؋",
              status = when { r.balance > 0.5 -> r.debtDays?.let { ir.vil3ntec.tohid.core.i18n.Str.days(it) } ?: ir.vil3ntec.tohid.core.i18n.StrCustomerPages.owing; r.balance < -0.5 -> ir.vil3ntec.tohid.core.i18n.StrCustomerPages.inCredit; else -> "" },
              amountColor = if (r.balance > 0.5) tone.ink else if (r.balance < -0.5) tone.ink else null,
              selected = r.id == selectedId,
              leading = { DeskLetterTile(r.name, tone.ink, tone.fill) },
              onClick = { onOpen(r.id) },
            )
          }
        }
      }
      else -> LazyVerticalGrid(
        columns = GridCells.Adaptive(176.dp),
        horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
        verticalArrangement = Arrangement.spacedBy(DeskSpace.x3),
        modifier = Modifier.fillMaxWidth().weight(1f),
      ) {
        items(list, key = { it.id }) { s -> CustomerCard(s, s.id == selectedId) { onOpen(s.id) } }
      }
    }
  }
  if (mergeOpen && crm) CustomerMergeDialog(d, store, onDismiss = { mergeOpen = false }, toast = toast)
}

/** کارتِ مربعیِ هر مشتری — همان کارتِ سرخ/سبزِ گوشی (زمینهٔ کم‌رنگ و مرزِ رنگِ حساب)، به اندازهٔ میز */
@Composable
private fun CustomerCard(s: CustomerEngine.Summary, selected: Boolean, onClick: () -> Unit) {
  val c = Desk.colors
  val tone = debtTone(s.balance)
  val shape = DeskRadius.cardShape
  Column(
    Modifier
      .aspectRatio(1f)
      .clip(shape)
      .background(tone.fill)
      .border(if (selected) DeskSize.focusRing else DeskSize.hairline, if (selected) c.brand else tone.ink, shape)
      .deskClickable(label = s.name, shape = shape, selected = false, onClick = onClick)
      .padding(DeskSpace.x4),
  ) {
    Text(s.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = DeskType.bodyStrong, color = c.ink)
    Text(ir.vil3ntec.tohid.ui.components.customerPhone(s.phone, ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts()).ifBlank { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noPhone }, maxLines = 1, style = DeskType.secondary, color = c.inkSecondary)
    Spacer(Modifier.weight(1f))
    Text(
      when { s.balance > 0.5 -> ir.vil3ntec.tohid.core.i18n.StrCustomerPages.owing; s.balance < -0.5 -> ir.vil3ntec.tohid.core.i18n.StrCustomerPages.inCredit; else -> ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noBalance },
      style = DeskType.label, color = tone.ink,
    )
    Text("${money(kotlin.math.abs(s.balance))} ؋", style = DeskType.kpiSmall, color = tone.ink, maxLines = 1)
    Spacer(Modifier.weight(1f))
    Text(
      s.lastPurchase?.let { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lastBoughtOn(formatDate(it)) } ?: ir.vil3ntec.tohid.core.i18n.StrCustomerPages.noPurchases,
      style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
  }
}

/* ══════════════════════════ داخل: پروندهٔ مشتری ══════════════════════════ */

private enum class FileTab(private val text: () -> String) { LEDGER({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.tabStatement }), INVOICES({ ir.vil3ntec.tohid.core.i18n.Str.invoices }), ITEMS({ ir.vil3ntec.tohid.core.i18n.StrCustomerPages.tabItems }); val label: String get() = text() }

/**
 *  پروندهٔ مشتری — پنلِ پهنِ کنارِ فهرست: سرِ پرونده (کاشیِ سرخ/سبز، نام، شماره و حال)، پنج عدد (مانده به رنگِ حساب)، کارها
 *  (دریافت F7، قرضِ دستی F6، سقفِ اعتبار، …؛ چاپ و اکسلِ همان زبانه)، و سه زبانه: «صورت‌حساب» با ردیف‌های **«برد» سرخ /
 *  «رسید» سبز** (همان هویتِ گوشی)، «فاکتورها» (فاکتورها فقط درونِ ردیف‌های «برد» و همین زبانه — فهرستِ جدایی نیست) و
 *  «اقلامِ برداشته». جزئیاتِ ردیفِ برگزیدهٔ صورت‌حساب زیرِ جدول (رسید، سررسید، فاکتور).
 */
@Composable
private fun CustomerFile(store: ShopStore, d: ShopData, snackbar: SnackbarHostState, id: String, onOpenOrder: ((String) -> Unit)?, onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  val debtor = d.debtors.first { it.id == id }
  val s = remember(d, id) { CustomerEngine.summary(d, debtor) }
  val today = ir.vil3ntec.tohid.todayIso()
  val c = Desk.colors
  val debt = PhoneV3Debt.colors

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

  val tone = debtTone(s.balance)
  Column(
    Modifier
      .fillMaxSize()
      .deskCard(c)
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
      },
  ) {
    //  سرِ پرونده
    Row(Modifier.fillMaxWidth().padding(start = DeskSpace.card, end = DeskSpace.x3, top = DeskSpace.x4, bottom = DeskSpace.x3), verticalAlignment = Alignment.CenterVertically) {
      DeskLetterTile(debtor.name, tone.ink, tone.fill, size = DeskSize.tileLarge)
      Spacer(Modifier.width(DeskSpace.x3))
      Column(Modifier.weight(1f)) {
        Text(debtor.name, style = DeskType.sectionTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOf(ir.vil3ntec.tohid.ui.components.customerPhone(debtor.phone, ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts()), DebtorEngine.stateText(s.balance), debtor.notes).filter { it.isNotBlank() }.joinToString(" • "),
          style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        CrmExtraLine(debtor)
      }
      //  مانده — به رنگِ حساب (سرخ بدهکار، سبز صاف)، همان معنای کارتِ گوشی
      Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = DeskSpace.x3)) {
        Text(ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vDebtRemaining, style = DeskType.label, color = c.inkSecondary, maxLines = 1)
        Text("${money(s.balance)} ؋", style = DeskType.kpiSmall, color = tone.ink, maxLines = 1)
      }
      DeskIconButton(Icons.Outlined.Close, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.escBack, onBack)
    }
    DeskDivider()
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = DeskSpace.card, vertical = DeskSpace.x4)) {
    //  پنجرهٔ کوتاه (۷۶۸) ⇒ چهار عددِ آرام یک خطِ کم‌رنگ می‌شوند تا جدول جا داشته باشد
    val tall = maxHeight >= 560.dp
    val wide = maxWidth >= 900.dp
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
      if (tall) DeskKpiRow {
        MiniStat(ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vPurchaseTotal, "${money(s.bought)} ؋", Modifier.weight(1f))
        MiniStat(ir.vil3ntec.tohid.core.i18n.Str.invoices, plain(s.invoices), Modifier.weight(0.8f))
        MiniStat(ir.vil3ntec.tohid.core.i18n.StrPriceCheck.lastPurchase, s.lastPurchase?.let { formatDate(it) } ?: "—", Modifier.weight(1f))
        MiniStat(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lastPaid, s.lastPaid?.let { formatDate(it) } ?: "—", Modifier.weight(1f))
      } else DeskNote(
        listOf(
          ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vPurchaseTotal + ": " + money(s.bought) + " ؋",
          ir.vil3ntec.tohid.core.i18n.Str.invoices + ": " + plain(s.invoices),
          ir.vil3ntec.tohid.core.i18n.StrPriceCheck.lastPurchase + ": " + (s.lastPurchase?.let { formatDate(it) } ?: "—"),
          ir.vil3ntec.tohid.core.i18n.StrCustomerPages.lastPaid + ": " + (s.lastPaid?.let { formatDate(it) } ?: "—"),
        ).joinToString(" • "),
      )
      book?.let { DebtBookBanner(it) }
      DeskToolbar {
        DeskButton(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.receivePayment, { entry = DebtorEngine.Kind.RECEIVE }, kind = DeskButtonKind.PRIMARY, icon = Icons.Outlined.SouthWest, shortcut = "F7")
        DeskButton(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.manualCredit, { entry = DebtorEngine.Kind.GIVE }, icon = Icons.Outlined.NorthEast, shortcut = "F6")
        DeskButton(ir.vil3ntec.tohid.core.i18n.StrExpenses.auditCreditLimitSet, { limitOpen = true })
        if (crm) DeskButton(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.secondNumber, { profileOpen = true })
        StatementImageButton(d, id, ::toast)
        CrmPricingButton(store, d, id, ::toast)
        CustomerPricesButton(store, d, id, ::toast, Modifier) // POS-18: همان دکمهٔ پروندهٔ گوشی، پیش‌فرض خاموش
        CustomerCardButton(store, d, id, ::toast, Modifier) // CRM-16، پیش‌فرض خاموش
        StoreCreditGrantButton(store, d, id, ::toast, Modifier) // PRM-15: همان دکمهٔ پروندهٔ گوشی (`GiftCardPref`)
        CustomerErasureButton(d.debtors.find { it.id == id }?.phone) // SEC-10، فقط صاحب و پرچمِ روشن
      }
      entry?.let { kind ->
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
      ir.vil3ntec.tohid.ui.components.pos.LoyaltyHistory(d, id, Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), store, ::toast)
      ir.vil3ntec.tohid.ui.components.pos.LayawayCustomerPanel(d, store, id, ::toast) // POS-27: امانی‌های همین مشتری (`LayawayPref`)
      CustomerOnlineOrdersPanel(d, id, Modifier.padding(top = 8.dp), onOpen = onOpenOrder) // CRM-07: سفارش‌های اینترنتیِ باز/لغوشده (بخشِ سفارش و اجازهٔ «تماس»)
      //  CRM-15: بخشِ «چه کسی دید» در پرونده نیست (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳)؛ ثبتِ دیدنِ بالا همان (فقط «سابقهٔ عملیات»)

      //  زبانه‌ها
      val invoices = remember(d, id) { CustomerEngine.invoices(d, id) }
      val ledger = remember(d, id) { CustomerEngine.ledger(d, id) }
      val items = remember(d, id) { CustomerEngine.items(d, id) }
      DeskToolbar(
        end = {
          DeskButton(ir.vil3ntec.tohid.core.i18n.StrHistory.print, ::print, icon = Icons.Outlined.Print, shortcut = "Ctrl+P")
          DeskButton(ir.vil3ntec.tohid.core.i18n.StrReports.excel, ::excel, icon = Icons.Outlined.TableChart, shortcut = "Ctrl+E")
        },
      ) {
        DeskSegmented(
          FileTab.values().map { t ->
            val n = when (t) { FileTab.LEDGER -> ledger.size; FileTab.INVOICES -> invoices.size; FileTab.ITEMS -> items.size }
            t to "${t.label} (${plain(n)})"
          },
          tab, { tab = it },
        )
      }

      BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        when (tab) {
          FileTab.LEDGER -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
            val cols = buildList {
              if (wide) add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.colRow, width = 44.dp))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colDate, width = 96.dp))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colKind, width = 64.dp))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrReports.note, weight = 1f))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice, width = 72.dp))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrDesk.colAmount, width = 112.dp, end = true))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vDebtRemaining, width = 112.dp, end = true))
              if (wide) add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.colDue, width = 150.dp))
            }
            val sel = ledger.indexOfFirst { it.tx.id == pickedTx }.takeIf { it >= 0 }
            DeskGrid(
              cols, ledger.size, Modifier.fillMaxWidth().weight(1f),
              key = { ledger[it].tx.id },
              selected = sel,
              onPick = { pickedTx = ledger[it].tx.id },
              rowLabel = { i -> ledger[i].let { l -> (if (l.give > 0) ir.vil3ntec.tohid.core.i18n.StrMoney.giveTag else ir.vil3ntec.tohid.core.i18n.StrMoney.receiveTag) + " " + money(if (l.give > 0) l.give else l.receive) } },
              //  هویتِ دفترِ قرضِ گوشی: ردیفِ «برد» سرخِ کم‌رنگ، «رسید» سبزِ کم‌رنگ
              wash = { i -> if (ledger[i].give > 0) debt.oweTint else debt.settledTint },
              footer = buildList {
                if (wide) add("")
                add(""); add(""); add(ir.vil3ntec.tohid.core.i18n.StrInvoices.total); add("")
                add(money(ledger.sumOf { it.give } - ledger.sumOf { it.receive })); add(money(s.balance))
                if (wide) add(book?.overdueAmount?.takeIf { it.minor > 0 }?.let { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.overdueAmount(DebtBook.show(it)) }.orEmpty())
              },
              empty = { V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.ledgerEmpty) },
            ) { i, col ->
              val l = ledger[i]
              val give = l.give > 0
              val ink = if (give) debt.owe else debt.settled
              when (cols[col].title) {
                ir.vil3ntec.tohid.core.i18n.StrCustomerPages.colRow -> DeskCell(plain(l.row), muted = true)
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colDate -> DeskCell(formatDate(l.tx.date))
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colKind -> Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(if (give) Icons.Outlined.NorthEast else Icons.Outlined.SouthWest, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
                  Spacer(Modifier.width(DeskSpace.x1))
                  DeskCell(if (give) ir.vil3ntec.tohid.core.i18n.StrMoney.giveTag else ir.vil3ntec.tohid.core.i18n.StrMoney.receiveTag, color = ink, strong = true)
                }
                //  نوع در ستونِ خودش است؛ شرحِ خالی «—» ِ کم‌رنگ، نه تکرارِ «برد»/«رسید»
                ir.vil3ntec.tohid.core.i18n.StrReports.note -> DeskCell(l.tx.notes.ifBlank { "—" }, muted = l.tx.notes.isBlank())
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice -> {
                  val sale = l.saleId?.let { sid -> invoices.find { it.id == sid } }
                  if (l.invoiceLabel != null && sale != null) DeskLink("#${l.invoiceLabel}", { picked = sale.id; showInvoice = sale })
                  else DeskCell(l.invoiceLabel?.let { "#$it" }.orEmpty(), muted = true)
                }
                ir.vil3ntec.tohid.core.i18n.StrDesk.colAmount -> DeskCell((if (give) "+" else "−") + money(if (give) l.give else l.receive), color = ink, strong = true)
                ir.vil3ntec.tohid.core.i18n.StrSimpleAsk.vDebtRemaining -> DeskCell(money(l.balance), strong = true)
                else -> book?.entry(l.tx.id)?.takeIf { it.give && it.amount.minor > 0 }?.let { e ->
                  val due = e.dueDate?.let { formatDate(it) + " · " }.orEmpty()
                  DeskCell(
                    due + if (e.paid) ir.vil3ntec.tohid.core.i18n.StrMoney.settled else ir.vil3ntec.tohid.core.i18n.StrDebtPages.openAmount(DebtBook.show(e.open)),
                    color = when { e.overdue -> debt.owe; e.paid -> debt.settled; else -> c.inkSecondary },
                  )
                } ?: DeskCell("")
              }
            }
            DeskNote(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.ledgerNote, maxLines = 2)
            //  جزئیاتِ ردیفِ برگزیده — زیرِ جدول (پهنای پنل برای جدول می‌ماند)
            val e = pickedTx?.let { book?.entry(it) }
            if (book != null && e != null) {
              Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(DeskRadius.controlShape).background(c.surfaceAlt).verticalScroll(rememberScrollState()).padding(DeskSpace.x4), verticalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Column(Modifier.weight(1f)) {
                    Text(
                      ir.vil3ntec.tohid.core.i18n.StrCustomerPages.rowDetail(e.row, if (e.give) ir.vil3ntec.tohid.core.i18n.StrMoney.giveTag else ir.vil3ntec.tohid.core.i18n.StrMoney.receiveTag, DebtBook.show(e.amount)),
                      style = DeskType.bodyStrong, color = if (e.give) debt.owe else debt.settled,
                    )
                    Text(formatDate(e.tx.date) + (e.invoiceLabel?.let { ir.vil3ntec.tohid.core.i18n.StrDebtPages.invoiceTail(it) } ?: ""), style = DeskType.secondary, color = c.inkSecondary)
                  }
                  DeskButton(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.receiptBtn, { receiptFor = e.tx.id }, kind = DeskButtonKind.PRIMARY)
                  if (e.give) { Spacer(Modifier.width(DeskSpace.x2)); DeskButton(ir.vil3ntec.tohid.core.i18n.StrMoney.due, { dueFor = e.tx.id }) }
                  e.sale?.let { sale -> Spacer(Modifier.width(DeskSpace.x2)); DeskButton(ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice, { picked = sale.id; tab = FileTab.INVOICES }) }
                  Spacer(Modifier.width(DeskSpace.x1))
                  DeskIconButton(Icons.Outlined.Close, ir.vil3ntec.tohid.core.i18n.Str.close, { pickedTx = null })
                }
                DebtEntryDetail(book, e, maxLines = 40)
              }
            }
          }

          FileTab.INVOICES -> { val side = maxWidth >= 1000.dp; Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
            //  زبانه باز شد و فاکتوری انتخاب نشده ⇐ تازه‌ترین، تا اقلامش همان‌جا دیده شود
            LaunchedEffect(invoices) { if (picked == null || invoices.none { it.id == picked }) picked = invoices.firstOrNull()?.id }
            val counts = remember(d) { d.saleItems.groupingBy { it.saleId }.eachCount() }
            val cols = buildList {
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice, width = 80.dp))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colDate, weight = 1f))
              if (wide) add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colItems, width = 52.dp, end = true))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.payable, width = 112.dp, end = true))
              if (wide) add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colPaid, width = 104.dp, end = true))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colBalance, width = 104.dp, end = true))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colKind, width = 72.dp))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrMoney.status, width = 84.dp))
            }
            val live = invoices.filter { it.status != "cancelled" }
            DeskGrid(
              cols, invoices.size, Modifier.weight(1f).fillMaxHeight(),
              key = { invoices[it].id },
              selected = invoices.indexOfFirst { it.id == picked }.takeIf { it >= 0 },
              onPick = { picked = invoices[it].id; if (!side) showInvoice = invoices[it] },
              onOpen = { showInvoice = invoices[it] },
              rowLabel = { "#${invoices[it].invoiceLabel() ?: "—"}" },
              footer = buildList {
                add(ir.vil3ntec.tohid.core.i18n.StrInvoices.total); add("")
                if (wide) add("")
                add(money(s.bought))
                if (wide) add(money(live.sumOf { it.paidAmount }))
                add(money(live.sumOf { it.remaining })); add(""); add("")
              },
              empty = { V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.invoicesEmpty) },
            ) { i, col ->
              val v = invoices[i]
              val off = v.status == "cancelled"
              when (cols[col].title) {
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice -> DeskCell("#${v.invoiceLabel() ?: "—"}", muted = off)
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colDate -> DeskCell(formatDate(v.date), muted = true)
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colItems -> DeskCell(plain(counts[v.id] ?: 0), muted = true)
                ir.vil3ntec.tohid.core.i18n.StrCustomerPages.payable -> DeskCell(money(v.finalTotal), strong = !off, muted = off)
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colPaid -> DeskCell(money(v.paidAmount), muted = true)
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colBalance -> DeskCell(if (v.remaining > 0.5) money(v.remaining) else "", color = debt.owe)
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colKind -> DeskCell(ir.vil3ntec.tohid.data.PayFlow.payLabel(v), muted = true)
                //  CRM-07: فاکتورِ سفارشِ اینترنتیِ تحویل‌شده نشانِ «اینترنتی» دارد
                else -> DeskCell(when { off -> ir.vil3ntec.tohid.core.i18n.StrSale.cancelled; v.isOnlineOrder() -> StrDebtor.online; else -> ir.vil3ntec.tohid.core.i18n.StrInvoices.recorded }, muted = true)
              }
            }
            //  اقلامِ فاکتورِ انتخاب‌شده — کنارِ جدول وقتی جا هست؛ وگرنه زدنِ ردیف خودِ فاکتور را باز می‌کند
            if (side) invoices.find { it.id == picked }?.let { sale ->
              InvoicePeek(d, sale, Modifier.width(360.dp).fillMaxHeight()) { showInvoice = sale }
            }
          } }

          FileTab.ITEMS -> {
            val cols = buildList {
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colDate, width = 96.dp))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice, width = 72.dp))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrMoney.item, weight = 1f))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrSale.qty, width = 60.dp, end = true))
              if (wide) add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrOnline.unitPrice, width = 104.dp, end = true))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrInvoices.total, width = 112.dp, end = true))
              add(DeskColumn(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.colReturned, width = 68.dp, end = true))
            }
            DeskGrid(
              cols, items.size, Modifier.fillMaxSize(),
              onPick = { i -> picked = items[i].saleId; tab = FileTab.INVOICES },
              rowLabel = { items[it].name },
              footer = buildList { add(""); add(""); add(ir.vil3ntec.tohid.core.i18n.StrInvoices.total); add(""); if (wide) add(""); add(money(items.sumOf { it.total })); add("") },
              empty = { V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.itemsEmpty) },
            ) { i, col ->
              val l = items[i]
              when (cols[col].title) {
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colDate -> DeskCell(formatDate(l.date), muted = true)
                ir.vil3ntec.tohid.core.i18n.StrInvoices.colInvoice -> DeskCell("#${l.invoiceLabel ?: "—"}", muted = true)
                ir.vil3ntec.tohid.core.i18n.StrMoney.item -> DeskCell(l.name)
                ir.vil3ntec.tohid.core.i18n.StrSale.qty -> DeskCell(qty(l.quantity))
                ir.vil3ntec.tohid.core.i18n.StrOnline.unitPrice -> DeskCell(money(l.unitPrice), muted = true)
                ir.vil3ntec.tohid.core.i18n.StrInvoices.total -> DeskCell(money(l.total), strong = true)
                else -> DeskCell(if (l.returned > 0) qty(l.returned) else "", color = debt.owe)
              }
            }
          }
        }
      }
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
    CrmProfileDialog(debtor, onDismiss = { profileOpen = false }) { phone2, tags, email ->
      when (val r = CustomerProfileForm.setProfile(d, id, phone2, tags, email)) {
        is DebtorEngine.Result.Failed -> toast(r.message)
        is DebtorEngine.Result.Ok -> { scope.launch { store.save(d, r.data) }; profileOpen = false; toast(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.profileSaved) }
      }
    }
  }
}

/** عددِ آرامِ پرونده: برچسب و مقدار روی سطحِ دوم */
@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
  val c = Desk.colors
  Column(modifier.fillMaxHeight().clip(DeskRadius.cardShape).background(c.surfaceAlt).padding(horizontal = DeskSpace.x4, vertical = DeskSpace.x4)) {
    Text(label, style = DeskType.label, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(DeskSpace.x2))
    Text(value, style = DeskType.kpiSmall.copy(fontSize = DeskType.sectionTitle.fontSize), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

private fun qty(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

@Composable
private fun NewCustomerRow(store: ShopStore, d: ShopData, toast: (String) -> Unit, onCancel: () -> Unit, onAdded: (String) -> Unit) {
  val scope = rememberCoroutineScope(); val limitRules = ir.vil3ntec.tohid.ui.components.rememberCreditLimitRules() // CRM-03 #۲۵
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  fun save() {
    if (name.isBlank()) return
    when (val r = DebtorEngine.add(d, ir.vil3ntec.tohid.data.CreditLimitGate.quickDraft(limitRules, DebtorEngine.DebtorDraft(name = name, phone = phone)), System.currentTimeMillis(), ::newId)) {
      is DebtorEngine.Result.Failed -> toast(r.message)
      is DebtorEngine.Result.Ok -> { scope.launch { store.save(d, r.data) }; onAdded(r.id); name = ""; phone = "" }
    }
  }
  val c = Desk.colors
  Column(Modifier.fillMaxWidth().clip(DeskRadius.cardShape).background(c.surface).border(DeskSize.hairline, c.hairline, DeskRadius.cardShape).padding(DeskSpace.x3), verticalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
    DeskTextField(name, { name = it }, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.newCustomerName, Modifier.fillMaxWidth(), focus, onEnter = ::save, onEscape = onCancel)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
      DeskTextField(phone, { phone = it }, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.phone, Modifier.weight(1f), onEnter = ::save, onEscape = onCancel)
      DeskButton(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.addKey, ::save, kind = DeskButtonKind.PRIMARY, enabled = name.isNotBlank())
      DeskButton(ir.vil3ntec.tohid.core.i18n.Str.cancel, onCancel, kind = DeskButtonKind.GHOST, shortcut = "Esc")
    }
  }
}

/** ردیفِ ثبتِ دریافت/قرض — مبلغ، شرح، Enter؛ سبزِ «رسید» یا سرخِ «برد» (همان معنای دفتر) */
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
  val receive = kind == DebtorEngine.Kind.RECEIVE
  val debt = PhoneV3Debt.colors
  val ink = if (receive) debt.settled else debt.owe
  val c = Desk.colors
  Row(
    Modifier.fillMaxWidth().clip(DeskRadius.controlShape).background(if (receive) debt.settledTint else debt.oweTint)
      .border(DeskSize.hairline, ink, DeskRadius.controlShape).padding(DeskSpace.x2),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2),
  ) {
    Icon(if (receive) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
    Text(if (receive) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.receivePayment else ir.vil3ntec.tohid.core.i18n.StrCustomerPages.manualCredit, style = DeskType.bodyStrong, color = c.ink, maxLines = 1)
    DeskTextField(amount, { amount = ir.vil3ntec.tohid.numericInput(it) }, ir.vil3ntec.tohid.core.i18n.StrCustomerPages.amountAfn, Modifier.width(150.dp), focus, onEnter = ::go, onEscape = onCancel)
    DeskTextField(notes, { notes = it }, ir.vil3ntec.tohid.core.i18n.StrReports.note, Modifier.weight(1f), onEnter = ::go, onEscape = onCancel)
    if (!receive) {
      DeskTextField(
        dueText, { dueText = ir.vil3ntec.tohid.latinDigits(it).filter { ch -> ch.isDigit() || ch == '/' || ch == '-' } },
        ir.vil3ntec.tohid.core.i18n.StrMoney.dueField + " ۱۴۰۵/۰۸/۰۱", Modifier.width(170.dp), isError = dueBroken, onEnter = ::go, onEscape = onCancel,
      )
    }
    DeskButton(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.saveKey, ::go, kind = DeskButtonKind.PRIMARY, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && !dueBroken)
    DeskButton(ir.vil3ntec.tohid.core.i18n.Str.cancel, onCancel, kind = DeskButtonKind.GHOST, shortcut = "Esc")
  }
}
