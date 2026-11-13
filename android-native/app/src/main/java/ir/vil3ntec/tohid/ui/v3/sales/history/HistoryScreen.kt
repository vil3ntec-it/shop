package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrPayStatus
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3HeroChip
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TwoPane
import ir.vil3ntec.tohid.ui.v3.V3Type

/** کارهای تاریخچه — هر کدام یک مسیر */
@Stable
class HistoryActions(
  val open: (String) -> Unit,
  val ret: (String) -> Unit,
  val cancel: (String) -> Unit,
  val fix: (String) -> Unit,
  val returnReceipt: (String) -> Unit,
  val drawer: () -> Unit,
  /** «برگشت و تعویض» روشن ⇒ برگشتِ بی رسید */
  val noReceipt: (() -> Unit)?,
  /** «برگشت و تعویض» روشن و گوشی ⇒ QRِ رسید با دوربین */
  val scanReceipt: (() -> Unit)?,
  val newSale: () -> Unit,
  val back: (() -> Unit)?,
  /** PAY-07 — فهرستِ «نیازمند بررسی» (فقط صاحب و مدیر) */
  val payReview: (() -> Unit)? = null,
  /** «چاپ» ِ یک روز، ماه یا سال — برگهٔ تأیید (`HistoryPrintSheet`)؛ null ⇒ دکمه‌ای نیست */
  val print: ((SalesPeriods.Period) -> Unit)? = null,
  /** کامپیوتر: جدولِ اکسلیِ همهٔ فاکتورها (`DeskInvoicesScreen`) */
  val table: (() -> Unit)? = null,
)

/** نما، فیلتر و جستجو — در مسیر می‌مانند تا QRِ رسید جستجو را پر کند */
@Stable
class HistoryQuery(
  filter: HistoryFilter,
  search: String,
  unit: SalesPeriods.Unit = SalesPeriods.Unit.DAY,
  channel: HistoryChannel = HistoryChannel.ALL,
) {
  var filter by mutableStateOf(filter)
  var search by mutableStateOf(search)
  var unit by mutableStateOf(unit)
  var channel by mutableStateOf(channel)

  /** بخش‌های بازِ فهرست (کلیدِ دوره)؛ دورهٔ جاری همیشه باز است */
  val open = mutableStateMapOf<String, Boolean>()

  /** شمارِ ردیف‌های نشان‌داده در هر دوره («نمایشِ … دیگر») */
  val shown = mutableStateMapOf<String, Int>()

  /** چیدمانِ دوستونی: دورهٔ برگزیده؛ null ⇒ دورهٔ جاری */
  var picked by mutableStateOf<String?>(null)

  val filtered: Boolean get() = filter != HistoryFilter.ALL || channel != HistoryChannel.ALL

  fun limit(key: String): Int = shown[key] ?: HISTORY_PAGE
}

/** حالِ صندوق برای سربرگ و کادرِ بالا */
data class DrawerLine(val open: Boolean, val text: String)

/** نوارِ چاپِ در جریان یا تمام‌شده (`PeriodPrint`) */
data class PrintBanner(val text: String, val running: Boolean, val onCancel: () -> Unit, val onDismiss: () -> Unit)

/** ردیف‌های هر دوره، صفحه به صفحه — دوره‌ای با هزاران فاکتور یک‌جا ساخته نمی‌شود */
const val HISTORY_PAGE = 50

/** پهنایی که از آن دو ستون می‌شود (تبلت و کامپیوتر) */
private val TWO_PANE_MIN: Dp = 720.dp

/**
 *  تاریخچهٔ فروش (`sales`): پیش‌فرض **امروز** — همهٔ فاکتورهای امروز با هم و جمعِ امروز بالای آن (مبلغ، شمار،
 *  نقد/نسیه/دیگر، برگشت و خالص). کلیدِ «روز / ماه / سال» زیرِ عنوان؛ هر روز، ماه یا سال بخشِ خودش با جمع و
 *  فاکتورهایش است و باز و بسته می‌شود. جمع‌ها همان دفترِ X/Z و پایانِ روزند (`SalesPeriods`) و فیلتر فقط فهرست را
 *  کوتاه می‌کند. جستجو و فیلتر در سربرگ‌اند (کنار، نه سرِ راه). «چاپ» ِ هر دوره ⇐ برگهٔ تأیید با شمار و کاغذ.
 *  گوشی: یک ستون؛ پهن (تبلت، کامپیوتر): فهرستِ دوره‌ها در آغاز و دورهٔ برگزیده کنارش.
 *  [book] تا ساخته شدن در پس‌زمینه null است (اسکلت). [money] = false ⇒ شمارشِ کور (PAY-13): جمعِ پولی دیده نمی‌شود.
 */
@Composable
fun HistoryScreen(
  d: ShopData,
  book: HistoryBook?,
  returnsV2: Boolean,
  canCancel: Boolean,
  drawer: DrawerLine,
  query: HistoryQuery,
  actions: HistoryActions,
  bottomReserve: Dp = 0.dp,
  /** نشانِ همین دکان در QRِ فاکتورِ نسخهٔ ۲ (`ReceiptTokens.shopTag`) */
  shopTag: String? = null,
  /** PAY-07 — شمارِ پرداخت‌های «نیازمند بررسی» (صاحب و مدیر)؛ صفر ⇒ تراشه‌ای نیست */
  reviews: Int = 0,
  money: Boolean = true,
  banner: PrintBanner? = null,
  /** STF-02 — «لغو» با برگهٔ تأییدِ مدیر (`CancelAsk.offered`)؛ «اصلاح» همان [canCancel] */
  askCancel: Boolean = false,
) {
  var searching by rememberSaveable { mutableStateOf(query.search.isNotEmpty()) }
  var filterSheet by remember { mutableStateOf(false) }
  var menuFor by remember { mutableStateOf<String?>(null) }
  val ctx = RowCtx(d, book?.look, returnsV2, canCancel, actions, askCancel) { menuFor = it }

  V3Scaffold(
    title = Str.salesHistory,
    onBack = actions.back,
    actions = buildList {
      add(V3Action(Icons.Outlined.PointOfSale, StrSale.drawerWord, dot = drawer.open, onClick = actions.drawer))
      add(V3Action(Icons.Outlined.Search, StrSale.searchAlt) { searching = !searching })
      add(V3Action(Icons.Outlined.Tune, StrHistory.filter, dot = query.filtered) { filterSheet = true })
      actions.table?.let { add(V3Action(Icons.Outlined.TableChart, StrHistory.table, onClick = it)) }
    },
    bottomReserve = bottomReserve,
    search = if (searching || query.search.isNotEmpty()) ({
      V3SearchField(
        value = query.search,
        onValueChange = { query.search = ir.vil3ntec.tohid.latinDigits(it) },
        placeholder = StrSale.historySearch,
        onScan = actions.scanReceipt,
      )
    }) else null,
    chips = { UnitBar(query) { filterSheet = true } },
  ) { pad ->
    val padding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 16.dp)
    when {
      book == null -> V3Skeleton(rows = 6, modifier = Modifier.padding(16.dp))
      query.search.isNotBlank() -> SearchResults(d, ctx, query, padding, shopTag)
      else -> BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TWO_PANE_MIN) TwoPane(book, ctx, query, padding, drawer, reviews, money, banner)
        else PhoneList(book, ctx, query, padding, drawer, reviews, money, banner)
      }
    }
  }

  if (filterSheet) FilterSheet(query) { filterSheet = false }
  menuFor?.let { id ->
    d.sales.find { it.id == id }?.let { s ->
      SaleActionsSheet(HistoryModel.row(d, s, returnsV2, look = book?.look), canCancel, returnsV2, actions, askCancel = askCancel, onClose = { menuFor = null })
    }
  }
}

/** چیزهای مشترکِ ساختنِ ردیف — یک شیء برای همهٔ بخش‌ها */
internal class RowCtx(
  val d: ShopData,
  val look: RowLookup?,
  val returnsV2: Boolean,
  val canCancel: Boolean,
  val actions: HistoryActions,
  /** STF-02 — «لغو» با برگهٔ مدیر */
  val askCancel: Boolean,
  val onMenu: (String) -> Unit,
)

/**
 *  کادرِ بالا: دورهٔ جاری (یا برگزیده در دوستونی) — مبلغ، شمار، و تراشه‌های چاپ، بررسی و صندوق. تراشه‌ها به ردیفِ بعد
 *  می‌روند (`wrapChips`)؛ صندوقِ بلند واپسین است تا «چاپ» و «بررسی» با هم در ردیفِ نخست بمانند.
 */
@Composable
private fun Top(p: SalesPeriods.Period, title: String, ctx: RowCtx, drawer: DrawerLine?, reviews: Int, money: Boolean) {
  val a = ctx.actions
  val chips = listOfNotNull(
    a.print?.let { print -> V3HeroChip(StrHistory.print, Icons.Outlined.Print, onClick = { print(p) }) },
    a.payReview?.takeIf { reviews > 0 }?.let { V3HeroChip(StrPayStatus.reviewCount(plain(reviews)), Icons.Outlined.FactCheck, onClick = it) },
    drawer?.let { V3HeroChip(it.text, Icons.Outlined.PointOfSale, onClick = a.drawer) },
  )
  Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
    PeriodHero(p, title, money, chips)
    if (money) {
      Spacer(Modifier.height(10.dp))
      MoneySplit(p.totals)
    }
  }
}

@Composable
private fun PhoneList(
  book: HistoryBook, ctx: RowCtx, query: HistoryQuery, padding: PaddingValues,
  drawer: DrawerLine, reviews: Int, money: Boolean, banner: PrintBanner?,
) {
  val unit = query.unit
  val filter = query.filter
  val channel = query.channel
  val current = remember(book, unit) { book.current(unit) }
  val currentList = remember(book, unit, filter, channel) { filteredOf(current, query) }
  //  با فیلتر فقط دوره‌هایی که فاکتورِ پیدا دارند؛ بی فیلتر همان فهرست‌ها (بی کپی)
  val olderLists = remember(book, unit, filter, channel) {
    book.older(unit).map { it to filteredOf(it, query) }.filter { (_, list) -> !query.filtered || list.isNotEmpty() }
  }
  LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
    banner?.let { b -> item(key = "banner") { PrintBannerCard(b) } }
    item(key = "top-" + current.key) { Top(current, PeriodLabel.current(current, book.gregorian), ctx, drawer, reviews, money) }
    returnsHint(ctx)
    invoices(current, currentList, ctx, query, empty = if (unit == SalesPeriods.Unit.DAY) StrHistory.emptyToday else StrHistory.emptyPeriod)
    setAside(ctx)
    if (olderLists.isNotEmpty()) item(key = "earlier") { GroupTitle(StrHistory.earlier) }
    olderLists.forEach { (p, list) ->
      val open = query.open[p.key] == true || (query.filtered && list.isNotEmpty() && query.open[p.key] == null)
      item(key = "p-" + unit.name + p.key) {
        PeriodCard(
          p, PeriodLabel.title(p, book.today, book.gregorian), money, open,
          onToggle = { query.open[p.key] = !open },
          onPrint = ctx.actions.print?.let { print -> { print(p) } },
          filteredCount = list.size.takeIf { query.filtered },
        )
      }
      if (open) invoices(p, list, ctx, query, empty = StrHistory.emptyPeriod)
    }
  }
}

@Composable
private fun TwoPane(
  book: HistoryBook, ctx: RowCtx, query: HistoryQuery, padding: PaddingValues,
  drawer: DrawerLine, reviews: Int, money: Boolean, banner: PrintBanner?,
) {
  val unit = query.unit
  val current = book.current(unit)
  val all = listOf(current) + book.older(unit)
  val picked = all.firstOrNull { it.key == query.picked } ?: current
  V3TwoPane(
    listWidth = 360.dp,
    list = {
      LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 12.dp, top = 6.dp, bottom = padding.calculateBottomPadding())) {
        items(all, key = { "p-" + unit.name + it.key }) { p ->
          val title = if (p.key == current.key) PeriodLabel.current(p, book.gregorian) else PeriodLabel.title(p, book.today, book.gregorian)
          PeriodCard(
            p, title, money, open = false, selected = p.key == picked.key,
            onToggle = { query.picked = p.key }, onPrint = null,
            filteredCount = if (query.filtered) filteredOf(p, query).size else null, toggle = false,
          )
        }
      }
    },
    detail = {
      val list = filteredOf(picked, query)
      LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        banner?.let { b -> item(key = "banner") { PrintBannerCard(b) } }
        val title = if (picked.key == current.key) PeriodLabel.current(picked, book.gregorian) else PeriodLabel.full(picked, book.gregorian)
        item(key = "top-" + picked.key) { Top(picked, title, ctx, drawer.takeIf { picked.key == current.key }, reviews, money) }
        if (picked.key == current.key) returnsHint(ctx)
        invoices(picked, list, ctx, query, empty = StrHistory.emptyPeriod)
        if (picked.key == current.key) setAside(ctx)
      }
    },
  )
}

/** «برگشت و تعویض» روشن: راهنما و «برگشتِ بی رسید» — همان جای پیشین، زیرِ کادرِ امروز */
/** سندهای ردشده‌ای که صاحب کنار گذاشت/اصلاح کرد — بیرون از جمعِ دوره (`RejectedMark.kt`) */
private fun LazyListScope.setAside(ctx: RowCtx) {
  if (ctx.d.setAside.isEmpty()) return
  item(key = "set-aside") { SetAsideSection(ctx.d.setAside) }
}

private fun LazyListScope.returnsHint(ctx: RowCtx) {
  if (!ctx.returnsV2) return
  item(key = "returns") {
    val c = PhoneV3.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
      Text(StrSale.returnsHint, style = V3Type.footnote, color = c.inkMuted)
      ctx.actions.noReceipt?.let {
        Spacer(Modifier.height(6.dp))
        V3Button(StrSale.noReceiptReturn, it, kind = V3ButtonKind.SECONDARY, icon = Icons.AutoMirrored.Outlined.AssignmentReturn, height = 40.dp)
      }
    }
  }
}

/** فاکتورهای یک دوره، [HISTORY_PAGE] تا [HISTORY_PAGE] */
private fun LazyListScope.invoices(p: SalesPeriods.Period, list: List<Sale>, ctx: RowCtx, query: HistoryQuery, empty: String) {
  if (list.isEmpty()) {
    item(key = "empty-" + p.key) {
      if (p.sales.isEmpty() && p.unit == SalesPeriods.Unit.DAY && ctx.d.sales.isEmpty()) {
        V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, StrSale.noSalesYet, action = StrSale.newSale, onAction = ctx.actions.newSale)
      } else EmptyLine(if (query.filtered && p.sales.isNotEmpty()) StrSale.noInvoiceMatch else empty)
    }
    return
  }
  val limit = query.limit(p.key)
  val shown = if (list.size > limit) list.subList(0, limit) else list
  val withDate = p.unit != SalesPeriods.Unit.DAY
  items(shown.size, key = { "s-" + shown[it].id }) { i ->
    InvoiceRow(shown[i], ctx, first = i == 0, last = i == shown.lastIndex, withDate = withDate)
  }
  if (list.size > limit) item(key = "more-" + p.key) {
    MoreButton(StrHistory.more(plain(minOf(HISTORY_PAGE, list.size - limit)))) { query.shown[p.key] = limit + HISTORY_PAGE }
  }
}

/** جستجو (شمارهٔ فاکتور، مشتری یا QRِ رسید) در همهٔ روزها — همان `HistoryModel.shown` ِ پیشین، با فیلترِ «کجا» */
@Composable
private fun SearchResults(d: ShopData, ctx: RowCtx, query: HistoryQuery, padding: PaddingValues, shopTag: String?) {
  val found = remember(d, query.filter, query.channel, query.search, ctx.returnsV2, shopTag) {
    HistoryModel.shown(d, query.filter, query.search, HistoryModel.ALL_MONTHS, ctx.returnsV2, shopTag)
      .filter { HistoryFilter.ALL.matches(it, query.channel) }
  }
  LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
    if (found.isEmpty()) {
      item(key = "none") { V3EmptyState(Icons.Outlined.SearchOff, StrSale.noInvoiceMatch) }
      return@LazyColumn
    }
    item(key = "count") { GroupTitle(StrHistory.invoices(plain(found.size))) }
    val limit = query.limit("search")
    val shown = if (found.size > limit) found.subList(0, limit) else found
    items(shown.size, key = { "s-" + shown[it].id }) { i -> InvoiceRow(shown[i], ctx, first = i == 0, last = i == shown.lastIndex, withDate = true) }
    if (found.size > limit) item(key = "more") { MoreButton(StrHistory.more(plain(minOf(HISTORY_PAGE, found.size - limit)))) { query.shown["search"] = limit + HISTORY_PAGE } }
  }
}

/** فاکتورهای پیدای یک دوره با فیلتر؛ بی فیلتر همان فهرست (بی کپی) */
internal fun filteredOf(p: SalesPeriods.Period, query: HistoryQuery): List<Sale> =
  if (!query.filtered) p.sales else p.sales.filter { query.filter.matches(it, query.channel) }

@Composable
internal fun GroupTitle(text: String) {
  Box(Modifier.fillMaxWidth().padding(start = 6.dp, top = 18.dp, bottom = 8.dp)) {
    Text(text, style = V3Type.subhead, color = PhoneV3.colors.inkMuted, modifier = Modifier.semantics { heading() })
  }
}
