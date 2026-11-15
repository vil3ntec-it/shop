package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.ui.theme.V3Section
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.data.PurchasePeriods
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Choice
import ir.vil3ntec.tohid.ui.v3.V3DataRow
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Dropdown
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3RowAction
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Summary
import ir.vil3ntec.tohid.ui.v3.V3SummaryAction
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  ردیفِ «خریدها» (کالبدِ `V3DataRow`، ۶۸c): نامِ تأمین‌کننده؛ «دیروز · چای سبز»؛ «۸٬۳۲۰ ؋ • ۳٬۳۲۰ ؋ مانده». زدن ⇐ جزئیات
 *  (سفارشِ باز ⇐ صفحهٔ همان سفارش، با همهٔ جزئیاتِ پرداخت و رسیدن). تنها کارِ اصلی قرصِ خنثیِ پایانِ ردیف است: سفارشِ رسیدنی
 *  ⇒ «دریافتِ کالا»، خریدِ دارای مانده ⇒ «پرداخت»؛ بقیه پیکان (یک قرص، بیشتر نه).
 */
@Composable
internal fun PurchaseListRow(v: PurchaseViews.View, today: String, onOpen: () -> Unit, onPay: (() -> Unit)?, onReceive: (() -> Unit)?) {
  val text = remember(v, today, I18n.lang) { PurchaseRowText.of(v, today) }
  V3DataRow(
    text.title,
    secondary = text.subtitle,
    meta = text.meta,
    icon = when (v.kind) {
      PurchaseViews.Kind.RETURN -> Icons.AutoMirrored.Outlined.AssignmentReturn
      PurchaseViews.Kind.ORDER -> Icons.Outlined.LocalShipping
      PurchaseViews.Kind.BILL -> Icons.AutoMirrored.Outlined.ReceiptLong
    },
    action = when {
      onReceive != null -> V3RowAction(StrPurchasePages.receiveGoods, Icons.Outlined.MoveToInbox, onClick = onReceive)
      onPay != null -> V3RowAction(StrMoney.pay, Icons.Outlined.Payments, onClick = onPay)
      else -> null
    },
    onClick = onOpen,
  )
}

/** کارهای فهرستِ خریدها */
internal class PurchasesActions(
  val onOpen: (PurchaseViews.View) -> Unit,
  /** «پرداخت» ِ ردیف: برگهٔ پرداختِ تأمین‌کننده با ماندهٔ همین خرید */
  val onPay: (PurchaseViews.View) -> Unit,
  /** «دریافتِ کالا» ِ سفارشِ باز — null ⇒ نقش اجازه ندارد یا سفارشی بار نشده */
  val onReceive: ((PurchaseViews.View) -> Unit)?,
  val onNew: () -> Unit,
)

/**
 *  پالایه‌های فهرست (۶۸c، صاحب‌کار: کادرِ سومِ «رسیدن:» بریده می‌شد): **یک** ردیفِ ثابت که هرگز بریده نمی‌شود — کادرِ کشوییِ
 *  دوره (قاعدهٔ «تاریخ با کادرِ کشویی») و یک دکمهٔ «فیلتر» با شمارِ پالایه‌های روشن؛ تأمین‌کننده، پرداخت و رسیدن در برگهٔ همان
 *  دکمه ([PurchaseFilterSheet]) — هر سه همان کادرهای کشوییِ پیشین.
 */
@Composable
internal fun PurchaseFilterRow(d: ShopData, views: List<PurchaseViews.View>, today: String, filter: PurchaseViews.Filter, onFilter: (PurchaseViews.Filter) -> Unit) {
  val greg = PurchaseRowText.gregorian()
  var sheet by remember { mutableStateOf(false) }
  val active = listOf(filter.supplierId != null, filter.pay != PurchaseViews.PayFilter.ALL, filter.receipt != PurchaseViews.ReceiptFilter.ALL).count { it }
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(PURCHASE_FILTER_ROW),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val periods = remember(views, today, greg, I18n.lang) {
      listOf(V3Choice("", StrPurchases.allTime)) + PurchasePeriods.keys(views.map { it.date }, today, greg).mapIndexed { i, k ->
        V3Choice(k, PurchaseRowText.period(k, today, greg), divider = i == 1 || k.startsWith("y:"))
      }
    }
    V3Dropdown(
      value = PurchaseRowText.period(filter.period, today, greg), choices = periods, selected = filter.period.orEmpty(),
      onSelect = { onFilter(filter.copy(period = it.ifEmpty { null })) }, label = ir.vil3ntec.tohid.core.i18n.StrLook.period,
      modifier = Modifier.weight(1f, fill = false),
    )
    //  همان بلندیِ کادرِ کشویی (۴۴) تا ردیف یک‌دست باشد
    V3Chip(
      StrHistory.filter, selected = active > 0, onClick = { sheet = true }, icon = Icons.Outlined.Tune,
      count = active.takeIf { it > 0 }, modifier = Modifier.heightIn(min = 44.dp),
    )
  }
  if (sheet) PurchaseFilterSheet(d, filter, onFilter) { sheet = false }
}

/** برگهٔ «فیلتر» ِ خریدها: تأمین‌کننده، پرداخت و رسیدن (همان کادرهای کشویی) و «پاک کردنِ فیلترها» */
@Composable
private fun PurchaseFilterSheet(d: ShopData, filter: PurchaseViews.Filter, onFilter: (PurchaseViews.Filter) -> Unit, onClose: () -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onClose, title = StrHistory.filter, detents = false) { closer ->
    val suppliers = remember(d, I18n.lang) {
      listOf(V3Choice("", StrPurchases.allSuppliers)) + d.suppliers.sortedBy { it.name }.map { V3Choice(it.id, it.name) }
    }
    val pays = remember(I18n.lang) {
      listOf(
        V3Choice(PurchaseViews.PayFilter.ALL.name, StrPurchases.payAll),
        V3Choice(PurchaseViews.PayFilter.UNPAID.name, StrPurchases.unpaid),
        V3Choice(PurchaseViews.PayFilter.OVERDUE.name, StrPurchases.overdueFilter),
        V3Choice(PurchaseViews.PayFilter.PAID.name, StrPurchases.paid),
      )
    }
    val receipts = remember(I18n.lang) {
      listOf(
        V3Choice(PurchaseViews.ReceiptFilter.ALL.name, StrPurchases.receiptAll),
        V3Choice(PurchaseViews.ReceiptFilter.RECEIVED.name, StrPurchases.received),
        V3Choice(PurchaseViews.ReceiptFilter.NOT_RECEIVED.name, StrPurchases.notReceivedFilter),
      )
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(StrMoney.supplierLabel, style = V3Type.subhead, color = c.inkMuted)
      V3Dropdown(
        value = filter.supplierId?.let { id -> d.suppliers.firstOrNull { it.id == id }?.name } ?: StrPurchases.allSuppliers,
        choices = suppliers, selected = filter.supplierId.orEmpty(),
        onSelect = { onFilter(filter.copy(supplierId = it.ifEmpty { null })) }, label = StrMoney.supplierLabel, modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(6.dp))
      Text(StrPurchases.payLabel, style = V3Type.subhead, color = c.inkMuted)
      V3Dropdown(
        value = pays.first { it.key == filter.pay.name }.label, choices = pays, selected = filter.pay.name,
        onSelect = { k -> onFilter(filter.copy(pay = PurchaseViews.PayFilter.valueOf(k))) }, label = StrPurchases.payLabel, modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(6.dp))
      Text(StrPurchases.receiptLabel, style = V3Type.subhead, color = c.inkMuted)
      V3Dropdown(
        value = receipts.first { it.key == filter.receipt.name }.label, choices = receipts, selected = filter.receipt.name,
        onSelect = { k -> onFilter(filter.copy(receipt = PurchaseViews.ReceiptFilter.valueOf(k))) }, label = StrPurchases.receiptLabel, modifier = Modifier.fillMaxWidth(),
      )
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      val any = filter.supplierId != null || filter.pay != PurchaseViews.PayFilter.ALL || filter.receipt != PurchaseViews.ReceiptFilter.ALL
      if (any) {
        V3Button(
          StrHistory.clearFilters,
          { onFilter(filter.copy(supplierId = null, pay = PurchaseViews.PayFilter.ALL, receipt = PurchaseViews.ReceiptFilter.ALL)) },
          Modifier.weight(1f), kind = V3ButtonKind.SECONDARY,
        )
      }
      V3Button(ir.vil3ntec.tohid.core.i18n.Str.close, { closer.close(onClose) }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
  }
}

/** نشانِ آزمون روی ردیفِ پالایهٔ خریدها (فقط `testTag`) */
internal const val PURCHASE_FILTER_ROW = "purchase-filter-row"

/**
 *  بدنهٔ زبانهٔ «خریدها»: خلاصهٔ آرام (جمعِ خرید، شمار، مانده، سررسیدگذشته‌ها) و فهرستِ تازه‌ترین بالا؛ خالی ⇒ راهنما با
 *  «خریدِ تازه»، پالایهٔ بی نتیجه ⇒ «پاک کردنِ فیلترها».
 */
internal fun LazyListScope.purchasesContent(
  shown: List<PurchaseViews.View>,
  any: Boolean,
  today: String,
  actions: PurchasesActions,
  doors: List<V3SummaryAction>,
  onClear: () -> Unit,
) {
  item(key = "p-summary") {
    val t = remember(shown) { PurchaseViews.totals(shown) }
    V3Summary(
      label = StrPurchases.totalBought,
      value = MoneyText.afn(t.bought),
      caption = listOfNotNull(
        StrPurchases.count(t.count),
        t.remaining.takeIf { it > 0.5 }?.let { StrPurchases.leftAll(ir.vil3ntec.tohid.money(it)) },
      ).joinToString(" · "),
      note = t.overdue.takeIf { it > 0 }?.let(StrPurchases::overdueCount),
      actions = doors,
      collapseKey = MoneyPaths.PURCHASING,
      section = V3Section.PURCHASES,
    )
    Spacer(Modifier.height(16.dp))
  }
  if (shown.isEmpty()) {
    item(key = "p-empty") {
      V3Card {
        if (!any) V3EmptyState(Icons.Outlined.ShoppingBag, StrPurchases.emptyTitle, body = StrPurchases.emptyBody, action = StrPurchases.newPurchase, onAction = actions.onNew)
        else V3EmptyState(Icons.Outlined.ShoppingBag, StrPurchases.noneFound, action = StrHistory.clearFilters, onAction = onClear)
      }
    }
    return
  }
  itemsIndexed(shown, key = { _, v -> "p:" + v.key }) { i, v ->
    val r = PhoneV3.groupRadius
    val first = i == 0
    val last = i == shown.lastIndex
    val shape = RoundedCornerShape(topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp, bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp)
    Column(Modifier.clip(shape).background(PhoneV3.colors.card)) {
      PurchaseListRow(
        v, today,
        onOpen = { actions.onOpen(v) },
        onPay = if (v.kind != PurchaseViews.Kind.RETURN && v.remaining > 0.5) ({ actions.onPay(v) }) else null,
        onReceive = actions.onReceive?.takeIf { v.isOrder }?.let { f -> { f(v) } },
      )
      if (!last) V3Divider(start = V3DataRowSpec.DIVIDER_START)
    }
  }
}
