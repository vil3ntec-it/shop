package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import ir.vil3ntec.tohid.core.i18n.StrReach
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ir.vil3ntec.tohid.ui.v3.V3SummaryAction
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.V3ListRow
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
  var showAll by rememberSaveable { mutableStateOf(false) }
  var allPays by rememberSaveable { mutableStateOf(false) }
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
      Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        val overdue = bought.count { it.pay == PurchaseViews.Pay.OVERDUE }
        V3Summary(
          label = when (state) {
            SuppliersModel.State.OWE -> StrMoney.ourDebt
            SuppliersModel.State.PREPAID -> StrMoney.prepaidToSupplier
            SuppliersModel.State.SETTLED -> StrMoney.accountSettled
          },
          value = MoneyText.afn(kotlin.math.abs(totals.debt)),
          caption = "${StrMoney.totalBought} ${MoneyText.afn(totals.bought)} · ${StrMoney.totalPaid} ${MoneyText.afn(totals.paid)}",
          note = overdue.takeIf { it > 0 }?.let(StrPurchases::overdueCount),
          actions = listOf(V3SummaryAction(StrMoney.pay, Icons.Outlined.Payments, onClick = actions.onPay)),
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
        Bought(bought, today, showAll, actions) { showAll = true }
        Payments(payments, today, allPays) { allPays = true }
        Spacer(Modifier.height(4.dp))
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

/** خریدهای همین تأمین‌کننده با همان ردیفِ فهرستِ خریدها؛ تازه‌ترین بالا */
@Composable
private fun Bought(list: List<PurchaseViews.View>, today: String, showAll: Boolean, actions: SupplierLedgerActions, onShowAll: () -> Unit) {
  val shown = if (showAll) list else list.take(SuppliersModel.SHOWN)
  val more = list.size - shown.size
  V3Section(
    title = StrPurchases.tabPurchases,
    action = if (more > 0) "${StrMoney.showAll} (${list.size})" else null,
    onAction = if (more > 0) onShowAll else null,
  ) {
    if (shown.isEmpty()) {
      Text(StrPurchases.emptyTitle, style = v3Text(14, FontWeight.Medium, 20), color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
      //  گامِ بعدیِ روشن (e-rows-63d): همان «خریدِ تازه»
      Box(Modifier.padding(start = 12.dp, bottom = 10.dp)) { V3PillAction(StrPurchases.newPurchase, actions.onNewPurchase) }
    }
    val p = actions.purchases
    shown.forEachIndexed { i, v ->
      if (i > 0) V3Divider(start = 64.dp)
      PurchaseListRow(
        v, today,
        onOpen = { p?.onOpen?.invoke(v) },
        onPay = if (p != null && v.kind != PurchaseViews.Kind.RETURN && v.remaining > 0.5) ({ p.onPay(v) }) else null,
        onReceive = p?.onReceive?.takeIf { v.isOrder }?.let { f -> { f(v) } },
      )
    }
  }
}

/** پرداخت‌های بعدیِ همین تأمین‌کننده (پرداختِ هنگامِ خرید در خودِ ردیفِ خرید است) */
@Composable
private fun Payments(list: List<ir.vil3ntec.tohid.data.SupplierPayment>, today: String, showAll: Boolean, onShowAll: () -> Unit) {
  val shown = if (showAll) list else list.take(SuppliersModel.SHOWN)
  val more = list.size - shown.size
  V3Section(
    title = StrPurchases.payments,
    action = if (more > 0) "${StrMoney.showAll} (${list.size})" else null,
    onAction = if (more > 0) onShowAll else null,
  ) {
    if (shown.isEmpty()) {
      Text(StrPurchases.noPayments, style = v3Text(14, FontWeight.Medium, 20), color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(16.dp))
    }
    shown.forEachIndexed { i, pay ->
      if (i > 0) V3Divider(start = 64.dp)
      V3ListRow(
        title = pay.notes.ifBlank { StrMoney.payment },
        subtitle = MoneyText.short(pay.date, today),
        icon = Icons.Outlined.Payments,
        trailing = { Text(MoneyText.afn(pay.amount), style = v3Text(14, FontWeight.SemiBold, 20), color = LedgerTone.SETTLED.ink()) },
        chevron = false,
      )
    }
  }
}
