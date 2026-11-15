package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
import ir.vil3ntec.tohid.ui.v3.V3AmountStatus
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Choice
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Dropdown
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3PillAction
import ir.vil3ntec.tohid.ui.v3.V3Summary
import ir.vil3ntec.tohid.ui.v3.V3SummaryAction

/**
 *  ردیفِ «خریدها»: زدن ⇐ جزئیات (سفارشِ باز ⇐ صفحهٔ همان سفارش). تنها کارِ اصلی قرصِ پایانِ ردیف است: سفارشِ رسیدنی
 *  ⇒ «دریافتِ کالا»، خریدِ دارای مانده ⇒ «پرداخت»؛ بقیه بی قرص (قاعدهٔ ۶۳d: یک قرص، بیشتر نه).
 */
@Composable
internal fun PurchaseListRow(v: PurchaseViews.View, today: String, onOpen: () -> Unit, onPay: (() -> Unit)?, onReceive: (() -> Unit)?) {
  val text = remember(v, today, I18n.lang) { PurchaseRowText.of(v, today) }
  val pill: (@Composable () -> Unit)? = when {
    onReceive != null -> ({ V3PillAction(StrPurchasePages.receiveGoods, onReceive, icon = Icons.Outlined.MoveToInbox) })
    onPay != null -> ({ V3PillAction(StrMoney.pay, onPay, icon = Icons.Outlined.Payments) })
    else -> null
  }
  V3ListRow(
    text.title,
    subtitle = text.subtitle,
    subtitleLines = 1,
    icon = when (v.kind) {
      PurchaseViews.Kind.RETURN -> Icons.AutoMirrored.Outlined.AssignmentReturn
      PurchaseViews.Kind.ORDER -> Icons.Outlined.LocalShipping
      PurchaseViews.Kind.BILL -> Icons.AutoMirrored.Outlined.ReceiptLong
    },
    below = { V3AmountStatus(text.amount, text.pay, align = TextAlign.Start, second = text.receipt) },
    trailing = pill,
    chevron = pill == null,
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

/** پالایه‌های فهرست — یک ردیفِ افقیِ فشردهٔ کادرهای کشویی زیرِ عنوان (تأمین‌کننده، پرداخت، رسیدن، دوره) */
@Composable
internal fun PurchaseFilterRow(d: ShopData, views: List<PurchaseViews.View>, today: String, filter: PurchaseViews.Filter, onFilter: (PurchaseViews.Filter) -> Unit) {
  val greg = PurchaseRowText.gregorian()
  V3ChipRow {
    val suppliers = remember(d, I18n.lang) {
      listOf(V3Choice("", StrPurchases.allSuppliers)) + d.suppliers.sortedBy { it.name }.map { V3Choice(it.id, it.name) }
    }
    V3Dropdown(
      value = filter.supplierId?.let { id -> d.suppliers.firstOrNull { it.id == id }?.name } ?: StrPurchases.allSuppliers,
      choices = suppliers, selected = filter.supplierId.orEmpty(),
      onSelect = { onFilter(filter.copy(supplierId = it.ifEmpty { null })) }, label = StrMoney.supplierLabel,
    )
    val pays = remember(I18n.lang) {
      listOf(
        V3Choice(PurchaseViews.PayFilter.ALL.name, StrPurchases.payAll),
        V3Choice(PurchaseViews.PayFilter.UNPAID.name, StrPurchases.unpaid),
        V3Choice(PurchaseViews.PayFilter.OVERDUE.name, StrPurchases.overdueFilter),
        V3Choice(PurchaseViews.PayFilter.PAID.name, StrPurchases.paid),
      )
    }
    V3Dropdown(
      value = pays.first { it.key == filter.pay.name }.label, choices = pays, selected = filter.pay.name,
      onSelect = { k -> onFilter(filter.copy(pay = PurchaseViews.PayFilter.valueOf(k))) }, label = StrPurchases.payLabel,
    )
    val receipts = remember(I18n.lang) {
      listOf(
        V3Choice(PurchaseViews.ReceiptFilter.ALL.name, StrPurchases.receiptAll),
        V3Choice(PurchaseViews.ReceiptFilter.RECEIVED.name, StrPurchases.received),
        V3Choice(PurchaseViews.ReceiptFilter.NOT_RECEIVED.name, StrPurchases.notReceivedFilter),
      )
    }
    V3Dropdown(
      value = receipts.first { it.key == filter.receipt.name }.label, choices = receipts, selected = filter.receipt.name,
      onSelect = { k -> onFilter(filter.copy(receipt = PurchaseViews.ReceiptFilter.valueOf(k))) }, label = StrPurchases.receiptLabel,
    )
    val periods = remember(views, today, greg, I18n.lang) {
      listOf(V3Choice("", StrPurchases.allTime)) + PurchasePeriods.keys(views.map { it.date }, today, greg).mapIndexed { i, k ->
        V3Choice(k, PurchaseRowText.period(k, today, greg), divider = i == 1 || k.startsWith("y:"))
      }
    }
    V3Dropdown(
      value = PurchaseRowText.period(filter.period, today, greg), choices = periods, selected = filter.period.orEmpty(),
      onSelect = { onFilter(filter.copy(period = it.ifEmpty { null })) }, label = ir.vil3ntec.tohid.core.i18n.StrLook.period,
    )
  }
}

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
      if (!last) V3Divider(start = 64.dp)
    }
  }
}
