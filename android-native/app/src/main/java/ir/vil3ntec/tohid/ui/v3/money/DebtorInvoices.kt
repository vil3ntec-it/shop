package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.v3.InvoicePiece
import ir.vil3ntec.tohid.ui.v3.InvoiceRefText
import ir.vil3ntec.tohid.ui.v3.InvoiceRefs
import ir.vil3ntec.tohid.ui.v3.V3AmountStatus
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3RowTone
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryModel

/** چند فاکتورِ آخرِ مشتری — بقیه در «فروش‌ها» */
private const val SALES_SHOWN = 20

/** یک ردیفِ «فاکتورهای این مشتری» — متن‌ها آماده (`DebtorInvoiceModel.rows`) */
internal data class DebtorInvoiceRow(
  val id: String,
  /** نامِ کالاها («برنج، روغن و ۲ قلمِ دیگر») — مشتری همین پرونده است، پس عنوانِ معنادار کالاست، نه شماره */
  val title: String,
  /** «۲۸ میزان · #۱۲» — شماره کوچک در پایان و زدنی (`InvoiceRef`) */
  val subtitle: List<InvoicePiece>,
  val amount: String,
  /** «• لغوشده» (آرام، مبلغ خط‌خورده) یا «• مانده ۲۰۰» (سرخِ بدهی)؛ تسویه ⇒ null */
  val status: V3RowStatus?,
  val cancelled: Boolean,
)

internal object DebtorInvoiceModel {
  /** ردیف‌های نشان‌داده (تا [SALES_SHOWN]) — اقلام یک بار گروه می‌شوند، نه یک گشتن برای هر فاکتور */
  fun rows(d: ShopData, account: DebtorEngine.Account, today: String): List<DebtorInvoiceRow> {
    val shown = account.sales.take(SALES_SHOWN)
    val ids = shown.mapTo(HashSet()) { it.id }
    val items = d.saleItems.filter { it.saleId in ids }.groupBy { it.saleId }
    return shown.map { sale ->
      val cancelled = sale.status == "cancelled"
      val label = sale.invoiceLabel() ?: "0"
      DebtorInvoiceRow(
        id = sale.id,
        title = HistoryModel.itemsTitle(items[sale.id].orEmpty().map { SalesEngine.itemName(d, it) }),
        subtitle = listOf(InvoiceRefs.plain(MoneyText.short(sale.date, today) + " · "), InvoiceRefs.ref(label, sale.id, text = "#$label")),
        amount = MoneyText.afn(sale.finalTotal),
        status = when {
          cancelled -> V3RowStatus(StrMoney.cancelled)
          sale.remaining > 0 -> V3RowStatus(StrMoney.remaining(money(sale.remaining)), V3RowTone.DEBT)
          else -> null
        },
        cancelled = cancelled,
      )
    }
  }
}

/** فاکتورهای همین مشتری (خودِ خرید، نه فقط ردیفِ بدهی) — زدنِ ردیف ⇐ همان فاکتور در «فروش‌ها» */
@Composable
internal fun DebtorInvoices(account: DebtorEngine.Account, rows: List<DebtorInvoiceRow>, onOpen: (String) -> Unit, onOlder: () -> Unit) {
  val older = account.sales.size - SALES_SHOWN
  V3Section(
    title = StrMoney.invoices(account.sales.size),
    action = if (older > 0) StrMoney.olderInvoices(older) else null,
    onAction = if (older > 0) onOlder else null,
  ) {
    rows.forEachIndexed { i, row ->
      if (i > 0) V3Divider(start = 64.dp)
      V3ListRow(
        title = row.title,
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        below = { InvoiceRefText(row.subtitle, debtorId = account.debtor.id, maxLines = 1) },
        trailing = { V3AmountStatus(row.amount, row.status, Modifier.widthIn(max = 168.dp), struck = row.cancelled) },
        chevron = false,
        onClick = { onOpen(row.id) },
      )
    }
  }
}
