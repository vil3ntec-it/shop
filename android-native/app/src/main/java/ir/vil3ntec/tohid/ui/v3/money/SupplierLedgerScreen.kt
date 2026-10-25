package ir.vil3ntec.tohid.ui.v3.money

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SouthWest
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
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
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
)

/**
 *  حسابِ یک تأمین‌کننده (`purchasing/suppliers/{id}`) — همان «حساب» ِ قرض‌دار (`AccountLedger`):
 *  سه عدد (کل خرید · کل پرداخت · بدهیِ ما)، تراشه‌های تماس (زنگ، کپیِ آدرس)، خریدها و پرداخت‌ها
 *  در یک فهرستِ تاریخ‌دار (۱۰ تا و «نمایشِ همه»)، و نوارِ چسبانِ «پرداخت» با «ثبت خرید».
 */
@Composable
internal fun SupplierLedgerScreen(id: String, d: ShopData, actions: SupplierLedgerActions) {
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
  val totals = remember(d, id) { SuppliersModel.totals(d, id) }
  val lines = remember(d, id) { SuppliersModel.lines(d, id) }
  var showAll by rememberSaveable { mutableStateOf(false) }
  var askDelete by remember { mutableStateOf(false) }
  val state = SuppliersModel.state(totals.debt)
  val navInset = LocalV3SystemBottom.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  V3Scaffold(
    title = supplier.name,
    onBack = actions.onBack,
    largeTitle = false,
    overflow = listOf(
      V3MenuItem(Icons.Outlined.Edit, StrMoney.edit, onClick = actions.onEdit),
      V3MenuItem(Icons.Outlined.DeleteOutline, StrMoney.delete, danger = true) { askDelete = true },
    ),
    bottomBar = {
      LedgerActionBar(
        title = StrMoney.pay,
        hint = StrMoney.owedLine(supplier.name, money(totals.debt.coerceAtLeast(0.0))),
        onMain = actions.onPay,
        icon = Icons.Outlined.Payments,
        quick = listOf(LedgerQuick(StrMoney.newPurchase, LedgerTone.OWE, actions.onNewPurchase)),
        modifier = Modifier.padding(bottom = navInset),
      )
    },
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        LedgerTotals(
          listOf(
            LedgerTotal(StrMoney.totalBought, totals.bought, LedgerTone.NEUTRAL),
            LedgerTotal(StrMoney.totalPaid, totals.paid, if (totals.paid > 0) LedgerTone.SETTLED else LedgerTone.NEUTRAL),
            LedgerTotal(
              StrMoney.ourDebt,
              kotlin.math.abs(totals.debt),
              when (state) {
                SuppliersModel.State.OWE -> LedgerTone.OWE
                SuppliersModel.State.PREPAID -> LedgerTone.SETTLED
                SuppliersModel.State.SETTLED -> LedgerTone.NEUTRAL
              },
              text = if (state == SuppliersModel.State.SETTLED) StrMoney.accountSettled else null,
            ),
          ),
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
        History(d, lines, showAll) { showAll = true }
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

/** خریدها (↗، بدهیِ ما بالا) و پرداخت‌ها (↙) در یک فهرست، تازه‌ترین اول */
@Composable
private fun History(d: ShopData, lines: List<SuppliersModel.Line>, showAll: Boolean, onShowAll: () -> Unit) {
  val shown = if (showAll) lines else lines.take(SuppliersModel.SHOWN)
  val more = lines.size - shown.size
  V3Section(
    title = StrMoney.history,
    action = if (more > 0) "${StrMoney.showAll} (${lines.size})" else null,
    onAction = if (more > 0) onShowAll else null,
  ) {
    if (shown.isEmpty()) {
      Text(StrMoney.noHistory, style = v3Text(14, FontWeight.Medium, 20), color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(16.dp))
    }
    shown.forEachIndexed { i, l ->
      if (i > 0) V3Divider(start = 64.dp)
      val purchase = l.kind == SuppliersModel.Kind.PURCHASE
      val title = if (purchase) {
        val name = d.products.find { it.id == l.productId }?.name ?: StrMoney.deletedProduct
        "$name — ${MoneyText.qtyUnit(l.quantity, l.unit)} × ${money(l.unitPrice)}"
      } else l.notes.ifBlank { StrMoney.payment }
      val sub = if (purchase) {
        "${MoneyText.date(l.date)} — " + if (l.left > 0) StrMoney.leftToPay(money(l.left)) else StrMoney.purchaseSettled
      } else MoneyText.date(l.date)
      V3ListRow(
        title = title,
        subtitle = sub,
        icon = if (purchase) Icons.Outlined.NorthEast else Icons.Outlined.SouthWest,
        trailing = {
          Text(
            MoneyText.afn(l.amount),
            style = v3Text(14, FontWeight.SemiBold, 20),
            color = if (purchase) PhoneV3.colors.ink else LedgerTone.SETTLED.ink(),
          )
        },
      )
    }
  }
}
