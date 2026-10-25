package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3HeroCard
import ir.vil3ntec.tohid.ui.v3.V3HeroChip
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3PickerSheet
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

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
)

/** فیلتر، جستجو و ماه — در مسیر می‌مانند تا QRِ رسید جستجو را پر کند */
@Stable
class HistoryQuery(filter: HistoryFilter, search: String, month: String) {
  var filter by mutableStateOf(filter)
  var search by mutableStateOf(search)
  var month by mutableStateOf(month)
}

/** حالِ صندوق برای سربرگ و کارتِ آمار */
data class DrawerLine(val open: Boolean, val text: String)

/**
 *  تاریخچهٔ فروش (`sales`، VILL3N §G5): عنوانِ درشت، «صندوق» (نقطهٔ برند وقتی باز است) و
 *  جستجو در سربرگ؛ کارتِ آبیِ «جمع فروش» با «۱۲ فاکتور — ۲ لغوشده» و خطِ صندوق؛ تراشه‌های
 *  پرداخت و ماه (برگهٔ ماه)؛ فاکتورها روزبه‌روز با سرخطِ چسبان — «#۱۲۴۵» و نام، ساعت · اقلام،
 *  قرصِ پرداخت و مبلغ؛ لغوشده خط‌خورده. کشیدن به پایان ⇐ «مرجوعی» و «لغو»؛ نگه داشتن ⇐ برگهٔ
 *  همهٔ کارها (جایگزینِ دسترس‌پذیرِ کشیدن). هیچ فاکتوری ⇐ «فروشِ تازه».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
  d: ShopData,
  today: String,
  yesterday: String,
  returnsV2: Boolean,
  canCancel: Boolean,
  drawer: DrawerLine,
  query: HistoryQuery,
  actions: HistoryActions,
  bottomReserve: androidx.compose.ui.unit.Dp = 0.dp,
  /** نشانِ همین دکان در QRِ فاکتورِ نسخهٔ ۲ (`ReceiptTokens.shopTag`) */
  shopTag: String? = null,
) {
  val c = PhoneV3.colors
  var searching by rememberSaveable { mutableStateOf(query.search.isNotEmpty()) }
  var monthSheet by remember { mutableStateOf(false) }
  var menuFor by remember { mutableStateOf<String?>(null) }
  val months = remember(d.sales) { HistoryModel.months(d) }
  val shown = remember(d, query.filter, query.search, query.month, returnsV2, shopTag) {
    HistoryModel.shown(d, query.filter, query.search, query.month, returnsV2, shopTag)
  }
  val days = remember(shown) { HistoryModel.days(shown) }
  val stats = remember(d.sales) { HistoryModel.stats(d) }
  val stagger = rememberV3Stagger(query.filter, query.month)

  V3Scaffold(
    title = Str.salesHistory,
    onBack = actions.back,
    actions = listOf(
      V3Action(Icons.Outlined.PointOfSale, StrSale.drawerWord, dot = drawer.open, onClick = actions.drawer),
      V3Action(Icons.Outlined.Search, StrSale.searchAlt) { searching = !searching },
    ),
    bottomReserve = bottomReserve,
    search = if (searching || query.search.isNotEmpty()) ({
      V3SearchField(
        value = query.search,
        onValueChange = { query.search = ir.vil3ntec.tohid.latinDigits(it) },
        placeholder = StrSale.historySearch,
        onScan = actions.scanReceipt,
      )
    }) else null,
    chips = {
      V3ChipRow {
        listOf(
          HistoryFilter.ALL to StrSale.allChip, HistoryFilter.CASH to StrSale.cashPay, HistoryFilter.CREDIT to StrSale.creditPay,
          HistoryFilter.NON_CASH to StrSale.nonCash, HistoryFilter.CANCELLED to StrSale.cancelledPlural,
        ).forEach { (f, label) -> V3Chip(label, query.filter == f, { query.filter = f }) }
        V3Chip(HistoryModel.monthLabel(query.month), query.month != HistoryModel.ALL_MONTHS, { monthSheet = true }, icon = Icons.Outlined.CalendarMonth)
      }
    },
  ) { pad ->
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 16.dp)) {
      item(key = "stats") {
        V3HeroCard(
          title = StrSale.salesTotal,
          value = PhoneSaleText.amount(stats.total),
          caption = HistoryModel.statsLine(stats),
          chips = listOf(V3HeroChip(drawer.text, Icons.Outlined.PointOfSale, onClick = actions.drawer)),
          modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
      }
      if (returnsV2) item(key = "returns") {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
          Text(StrSale.returnsHint, style = V3Type.footnote, color = c.inkMuted)
          actions.noReceipt?.let {
            Spacer(Modifier.height(6.dp))
            V3Button(StrSale.noReceiptReturn, it, kind = V3ButtonKind.SECONDARY, icon = Icons.AutoMirrored.Outlined.AssignmentReturn, height = 40.dp)
          }
        }
      }
      if (shown.isEmpty()) {
        item(key = "empty") {
          if (d.sales.isEmpty()) V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, StrSale.noSalesYet, action = StrSale.newSale, onAction = actions.newSale)
          else V3EmptyState(Icons.Outlined.SearchOff, StrSale.noInvoiceMatch)
        }
      }
      days.forEachIndexed { di, day ->
        stickyHeader(key = "d-" + day.date) {
          Text(
            HistoryModel.dayTitle(day.date, today, yesterday),
            style = V3Type.subhead,
            color = c.inkMuted,
            modifier = Modifier.fillMaxWidth().background(c.bg).padding(start = 6.dp, top = 14.dp, bottom = 8.dp).semantics { heading() },
          )
        }
        itemsIndexed(day.sales, key = { _, s -> s.id }) { i, sale ->
          val row = HistoryModel.row(d, sale, returnsV2)
          val first = i == 0
          val last = i == day.sales.lastIndex
          Column(Modifier.v3Stagger(stagger, sale.id, di + i).clip(groupShape(first, last)).background(c.card)) {
            if (!first) V3Divider(start = 64.dp)
            SaleRow(row, canCancel, returnsV2, actions, onMenu = { menuFor = sale.id })
          }
        }
      }
    }
  }

  if (monthSheet) {
    V3PickerSheet(
      title = StrSale.monthWord,
      options = months.map(HistoryModel::monthLabel),
      selected = HistoryModel.monthLabel(query.month),
      onPick = { label -> months.firstOrNull { HistoryModel.monthLabel(it) == label }?.let { query.month = it }; monthSheet = false },
      onDismiss = { monthSheet = false },
      allowNew = false,
    )
  }
  menuFor?.let { id ->
    d.sales.find { it.id == id }?.let { s ->
      SaleActionsSheet(HistoryModel.row(d, s, returnsV2), canCancel, returnsV2, actions, onClose = { menuFor = null })
    }
  }
}

@Composable
private fun SaleRow(row: HistoryRow, canCancel: Boolean, returnsV2: Boolean, actions: HistoryActions, onMenu: () -> Unit) {
  val c = PhoneV3.colors
  val cancelled = row.state == SaleState.CANCELLED
  V3ListRow(
    title = "${row.number} · ${row.name}",
    subtitle = row.subtitle,
    leading = {
      V3IconTile(
        if (cancelled) Icons.Outlined.Block else Icons.AutoMirrored.Outlined.ReceiptLong,
        hue = when {
          cancelled -> null
          row.state == SaleState.ONLINE -> V3Hue.SKY
          row.credit -> V3Hue.AMBER
          else -> V3Hue.MINT
        },
        danger = cancelled,
      )
    },
    trailing = {
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          PhoneSaleText.amount(row.amount),
          style = V3Type.headline,
          color = if (cancelled) c.inkMuted else c.ink,
          textDecoration = if (cancelled) TextDecoration.LineThrough else null,
        )
        V3StatusPill(payStatus(row), row.payLabel)
      }
    },
    chevron = false,
    endActions = buildList {
      if (row.canReturn) add(V3SwipeAction(Icons.AutoMirrored.Outlined.AssignmentReturn, if (returnsV2) StrSale.returnOrExchange else StrSale.returnWord) { actions.ret(row.id) })
      if (row.canCancel && canCancel) add(V3SwipeAction(Icons.Outlined.Block, StrSale.cancelSale, V3ButtonKind.DANGER) { actions.cancel(row.id) })
    },
    onLongClick = onMenu,
    onClick = { actions.open(row.id) },
  )
}

/** برگهٔ همهٔ کارهای یک فاکتور — نگه داشتنِ ردیف (کلیکِ راست در کامپیوتر) */
@Composable
private fun SaleActionsSheet(row: HistoryRow, canCancel: Boolean, returnsV2: Boolean, actions: HistoryActions, onClose: () -> Unit) {
  val chosen = remember { booleanArrayOf(false) }
  ir.vil3ntec.tohid.ui.v3.V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = "${row.number} · ${row.name}", detents = false) { closer ->
    fun pick(go: () -> Unit) { chosen[0] = true; closer.close { onClose(); go() } }
    V3ListRow(StrSale.viewInvoice, icon = Icons.AutoMirrored.Outlined.ReceiptLong, hue = V3Hue.BLUE, onClick = { pick { actions.open(row.id) } })
    if (row.canReturn) V3ListRow(
      if (returnsV2) StrSale.returnOrExchange else StrSale.returnWord,
      icon = Icons.AutoMirrored.Outlined.AssignmentReturn, hue = V3Hue.TEAL, onClick = { pick { actions.ret(row.id) } },
    )
    row.returnDocs.forEach { doc ->
      V3ListRow(StrSale.returnReceiptOf(ir.vil3ntec.tohid.data.ReturnFlow.label(doc)), icon = Icons.AutoMirrored.Outlined.ReceiptLong, onClick = { pick { actions.returnReceipt(doc) } })
    }
    //  لغو و «اصلاح» (که خودش لغو است) فقط با صاحب و مدیر — سرور هم لغوِ شاگرد را رد می‌کند
    if (row.canCancel && canCancel) {
      V3ListRow(StrSale.fixInvoice, icon = Icons.Outlined.PointOfSale, hue = V3Hue.AMBER, onClick = { pick { actions.fix(row.id) } })
      V3ListRow(StrSale.cancelSale, icon = Icons.Outlined.Block, danger = true, chevron = false, onClick = { pick { actions.cancel(row.id) } })
    }
  }
}

private fun payStatus(row: HistoryRow): V3Status = when {
  row.state == SaleState.CANCELLED -> V3Status.CANCELLED
  row.state == SaleState.ONLINE -> V3Status.IN_DELIVERY
  row.credit -> V3Status.WARNING
  else -> V3Status.DELIVERED
}

private fun groupShape(first: Boolean, last: Boolean) = RoundedCornerShape(
  topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
  bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp,
)
