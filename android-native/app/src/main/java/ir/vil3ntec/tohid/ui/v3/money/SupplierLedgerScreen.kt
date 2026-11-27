package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.ui.theme.V3Section
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import ir.vil3ntec.tohid.core.i18n.StrReach
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3Summary
import ir.vil3ntec.tohid.ui.v3.V3Type
import androidx.compose.material.icons.outlined.CurrencyExchange
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.ui.v3.V3SummaryAction
import ir.vil3ntec.tohid.core.i18n.StrPurchaseWorkflow
import ir.vil3ntec.tohid.data.SupplierPayment
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.V3DataRow
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.V3RowMeta
import ir.vil3ntec.tohid.ui.v3.V3PillAction
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section

/** کارهای حسابِ یک تأمین‌کننده */
internal class SupplierLedgerActions(
  val onBack: (() -> Unit)?,
  val onPay: () -> Unit,
  val onNewPurchase: () -> Unit,
  val onEdit: () -> Unit,
  val onDelete: () -> Unit,
  /** PUR-08/S25: برگشت به تأمین‌کننده با سندِ کسر (`SupplierReturnAndroid.kt`) */
  val onReturn: (() -> Unit)? = null,
  /** e-rows-63b: ردیف‌های «خریدها» همان کارهای فهرستِ زبانهٔ خرید (جزئیات، «پرداخت»، «دریافتِ کالا») */
  val purchases: PurchasesActions? = null,
)

/**
 *  صفحهٔ یک تأمین‌کننده (`purchasing/suppliers/{id}`، e-rows-63b): خلاصهٔ آرامِ حساب (بدهیِ ما / پیش‌پرداخت / صاف، کل خرید و کل
 *  پرداخت، شمارِ سررسیدگذشته‌ها) با «پرداخت»، تراشه‌های تماس، **خریدهایش** با همان ردیفِ فهرستِ خریدها (اقلام، پرداخت، رسیدن؛
 *  ۱۰ تا و «نمایشِ همه») و **پرداخت‌هایش**؛ تنها دکمهٔ شناور «خریدِ تازه» (پیش‌پُر با همین تأمین‌کننده). نوارِ چسبانِ پیشین نیست.
 */
@Composable
internal fun SupplierLedgerScreen(id: String, d: ShopData, actions: SupplierLedgerActions, orders: List<PurchaseOrderDto> = emptyList()) {
  val supplier = d.suppliers.find { it.id == id }
  if (supplier == null) {
    V3Scaffold(title = StrMoney.supplierLabel, onBack = actions.onBack, largeTitle = false) { pad ->
      Column(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
        V3EmptyState(Icons.Outlined.LocalShipping, StrMoney.emptySuppliers)
      }
    }
    return
  }
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val toasts = LocalV3Toasts.current
  val today = remember { todayIso() }
  val totals = remember(d, id) { SuppliersModel.totals(d, id) }
  val bought = remember(d, id, orders, today) { PurchaseViews.ofSupplier(d, id, today, orders) }
  val payments = remember(d, id) { d.supplierPayments.filter { it.supplierId == id }.sortedWith(compareByDescending<ir.vil3ntec.tohid.data.SupplierPayment> { it.date }.thenByDescending { it.createdAt }) }
  val activity = remember(bought, payments) {
    (bought.flatMap { view ->
      val paid = view.invoice?.lines?.sumOf { it.paidAmount } ?: 0.0
      listOfNotNull(SupplierActivity.Bought(view), SupplierActivity.AtPurchase(view, paid).takeIf { paid > 0 && view.kind == PurchaseViews.Kind.BILL })
    } + payments.map { SupplierActivity.Paid(it) })
      .sortedWith(compareByDescending<SupplierActivity> { it.date }.thenByDescending { it.at }.thenBy { it.key })
  }
  var paymentDetails by rememberSaveable(id) { mutableStateOf<String?>(null) }
  var askDelete by remember { mutableStateOf(false) }
  val state = SuppliersModel.state(totals.debt)

  V3Scaffold(
    title = supplier.name,
    onBack = actions.onBack,
    largeTitle = false,
    overflow = listOfNotNull(
      V3MenuItem(Icons.Outlined.Edit, StrMoney.edit, onClick = actions.onEdit),
      actions.onReturn?.let { V3MenuItem(Icons.AutoMirrored.Outlined.AssignmentReturn, StrReach.supplierReturn, onClick = it) },
      V3MenuItem(Icons.Outlined.DeleteOutline, StrMoney.delete, danger = true) { askDelete = true },
    ),
    fab = { expanded -> V3Fab(Icons.Outlined.AddShoppingCart, StrPurchases.newPurchase, expanded, actions.onNewPurchase) },
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 80.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        item(key = "supplier-summary") {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val overdue = bought.count { it.pay == PurchaseViews.Pay.OVERDUE }
            V3Summary(
              label = when (state) {
                SuppliersModel.State.OWE -> StrMoney.ourDebt
                SuppliersModel.State.PREPAID -> StrMoney.prepaidToSupplier
                SuppliersModel.State.SETTLED -> StrMoney.accountSettled
              },
              value = MoneyText.preciseAfn(kotlin.math.abs(totals.debt)),

              note = overdue.takeIf { it > 0 }?.let(StrPurchases::overdueCount),
              actions = listOf(V3SummaryAction(StrMoney.pay, Icons.Outlined.Payments, onClick = actions.onPay)),
              //  یک حالت برای همهٔ تأمین‌کننده‌ها (صفحه، نه هر نفر)
              collapseKey = "purchasing/supplier",
              section = V3Section.PURCHASES,
            )
            if (supplier.phone.isNotBlank() || supplier.address.isNotBlank()) {
              V3ChipRow(contentPadding = PaddingValues(0.dp)) {
                if (supplier.phone.isNotBlank()) {
                  V3Chip(StrMoney.call, selected = false, icon = Icons.Outlined.Call, onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + DebtorLedgerModel.dialable(supplier.phone)))) }
                  })
                }
                if (supplier.address.isNotBlank()) {
                  V3Chip(StrMoney.copyAddress, selected = false, icon = Icons.Outlined.ContentCopy, onClick = {
                    clipboard.setText(AnnotatedString(supplier.address))
                    toasts?.show(StrMoney.copied)
                  })
                }
              }
            }
            if (supplier.notes.isNotBlank()) Text(supplier.notes, style = v3Text(13, FontWeight.Normal, 19), color = PhoneV3.colors.inkMuted)
            //  PUR-11: بدهی به تفکیکِ ارز — ماندهٔ هر ارز با ارزشِ دفتریِ افغانیِ همان (جزوِ بدهیِ افغانیِ بالا)
            val fxAccounts = remember(d, id) { ir.vil3ntec.tohid.data.SupplierFx.accounts(d, id) }
            if (fxAccounts.isNotEmpty()) FxAccounts(fxAccounts)
            LedgerTotals(listOf(
              LedgerTotal(StrMoney.totalBought, totals.bought, LedgerTone.OWE),
              LedgerTotal(StrMoney.totalPaid, totals.paid, LedgerTone.SETTLED),
              LedgerTotal(StrMoney.remainingLabel, kotlin.math.abs(totals.debt), if (totals.debt > 0) LedgerTone.OWE else LedgerTone.SETTLED),
            ))
          }
        }
        item(key = "activity-heading") {
          Text(StrPurchaseWorkflow.statement, style = V3Type.headline, color = PhoneV3.colors.ink, modifier = Modifier.padding(top = 8.dp))
        }
        if (activity.isEmpty()) item(key = "empty-activity") {
          V3Section(null) {
            Text(StrPurchases.emptyTitle, style = V3Type.subhead, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(12.dp))
            V3PillAction(StrPurchases.newPurchase, actions.onNewPurchase)
          }
        }
        items(activity, key = { it.key }) { entry ->
          when (entry) {
            is SupplierActivity.Bought -> {
              val view = entry.view
              LedgerRow(
                date = MoneyText.short(entry.date, today),
                amount = PurchaseRowText.amount(view),
                after = if (view.no > 0) "#${view.no}" else StrPurchases.lines(view.lines.size),
                tone = if (view.kind == PurchaseViews.Kind.RETURN) LedgerTone.SETTLED else LedgerTone.OWE,
                up = view.kind != PurchaseViews.Kind.RETURN,
                kindLabel = if (view.kind == PurchaseViews.Kind.RETURN) StrReach.supplierReturn else StrPurchases.purchase,
                onClick = { actions.purchases?.onOpen?.invoke(view) },
                onLongClick = null, onDelete = null,
              )
            }
            is SupplierActivity.AtPurchase -> LedgerRow(
              date = MoneyText.short(entry.date, today), amount = MoneyText.preciseAfn(entry.amount),
              after = StrMoney.payment, tone = LedgerTone.SETTLED, up = false, kindLabel = StrMoney.payment,
              onClick = { actions.purchases?.onOpen?.invoke(entry.view) }, onLongClick = null, onDelete = null,
            )
            is SupplierActivity.Paid -> LedgerRow(
              date = MoneyText.short(entry.date, today), amount = supplierPaidAmount(entry.pay),
              after = StrMoney.payment, tone = LedgerTone.SETTLED, up = false, kindLabel = StrMoney.payment,
              onClick = { paymentDetails = entry.pay.id }, onLongClick = null, onDelete = null,
            )
          }
        }
      }
    }
  }

  payments.firstOrNull { it.id == paymentDetails }?.let { pay ->
    V3Sheet(onDismiss = { paymentDetails = null }, title = StrMoney.payment) {
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(supplierPaidAmount(pay), style = V3Type.headline, color = LedgerTone.SETTLED.ink())
        Text(MoneyText.short(pay.date, today), style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
        if (pay.notes.isNotBlank()) Text(pay.notes, style = V3Type.subhead)
        ir.vil3ntec.tohid.data.SupplierFx.rateOf(pay)?.let { rate ->
          Text(ir.vil3ntec.tohid.core.purchase.PurchaseFx.rateLine(rate), style = V3Type.footnote)
        }
      }
    }
  }

  if (askDelete) {
    V3ConfirmSheet(
      title = StrMoney.deleteSupplierTitle,
      message = StrMoney.deleteSupplierBody(supplier.name),
      confirm = StrMoney.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = actions.onDelete,
      onDismiss = { askDelete = false },
    )
  }
}

private sealed interface SupplierActivity {
  val key: String
  val date: String
  val at: Long

  data class Bought(val view: PurchaseViews.View) : SupplierActivity {
    override val key get() = "purchase:${view.key}"
    override val date get() = view.date
    override val at get() = view.createdAt
  }

  data class AtPurchase(val view: PurchaseViews.View, val amount: Double) : SupplierActivity {
    override val key get() = "purchase-payment:${view.key}"
    override val date get() = view.date
    override val at get() = view.createdAt
  }

  data class Paid(val pay: SupplierPayment) : SupplierActivity {
    override val key get() = "payment:${pay.id}"
    override val date get() = pay.date
    override val at get() = pay.createdAt
  }
}

private fun supplierPaidAmount(pay: SupplierPayment): String {
  val currency = ir.vil3ntec.tohid.data.SupplierFx.cur(pay.currency)
  val minor = pay.fxAmountMinor
  return if (currency != null && minor != null) ir.vil3ntec.tohid.data.SupplierFx.text(minor, currency)
  else MoneyText.preciseAfn(pay.afnPaidMinor?.let { it / 100.0 } ?: pay.amount)
}

/** PUR-11: ماندهٔ هر ارزِ تأمین‌کننده (همان `SupplierFx.accounts`) — ارز، مانده به همان ارز و ارزشِ دفتری */
@Composable
private fun FxAccounts(list: List<ir.vil3ntec.tohid.core.purchase.PurchaseFx.Account>) {
  V3Section(title = StrPurchaseFx.byCurrency) {
    list.forEachIndexed { i, a ->
      if (i > 0) V3Divider(start = V3DataRowSpec.DIVIDER_START)
      val fx = ir.vil3ntec.tohid.data.SafeCurrencies.format(kotlin.math.abs(a.fx), a.currency)
      V3DataRow(
        title = ir.vil3ntec.tohid.data.SafeCurrencies.name(a.currency),
        secondary = if (a.fx >= 0) StrPurchaseFx.owed(fx, MoneyText.preciseAfn(a.afn / 100.0)) else StrPurchaseFx.credit(fx),
        meta = V3RowMeta(ir.vil3ntec.tohid.data.SupplierFx.ltr(fx)),
        icon = Icons.Outlined.CurrencyExchange,
      )
    }
    Text(StrPurchaseFx.bookValueNote, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(16.dp))
  }
}
