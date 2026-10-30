package ir.vil3ntec.tohid.ui.v3.money

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.print.ReportPrint
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.v3Shake
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3InvoiceItems
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill

/** کارهای برگهٔ یک تراکنش — مسیرِ صدازننده (`MoneyRoutes`) پُرش می‌کند */
internal class DebtorTxSheetActions(
  val onClose: () -> Unit,
  /** حذف با برگرداندن (`MoneyUndo`) */
  val onDelete: () -> Unit,
  /** سررسیدِ تازهٔ برد (`DebtorEngine.setDueDate`)؛ null ⇒ برداشتنِ سررسید */
  val onSaveDue: (String?) -> Unit,
)

private enum class TxStep { DETAILS, DUE }

/**
 *  برگهٔ یک تراکنش (`debtors/{id}/tx/{txId}`): قرصِ نوع و مبلغِ درشت، کارتِ جزئیات (تاریخ،
 *  یادداشت، باز/تسویه، سررسید، «بابتِ …»، ماندهٔ پس از ردیف)، اقلامِ فاکتورِ برد، و کارها: چاپ/PDF،
 *  فرستادن (گوشی) یا کپیِ متن (کامپیوتر)، سررسید (برد) و حذف. «سررسید» گامِ دومِ همین برگه است.
 */
@Composable
internal fun DebtorTxSheet(
  tx: DebtTransaction,
  book: DebtBook.Book?,
  receipt: DebtBook.Receipt?,
  desk: Boolean,
  actions: DebtorTxSheetActions,
) {
  val entry = remember(book, tx.id) { book?.entry(tx.id) }
  var step by rememberSaveable { mutableStateOf(TxStep.DETAILS) }
  V3Sheet(onDismiss = actions.onClose) { closer ->
    V3PageSwitch(step, depth = { it.ordinal }) { s ->
      when (s) {
        TxStep.DETAILS -> Column {
          TxDetails(tx, entry, book)
          Spacer(Modifier.height(16.dp))
          ReceiptActions(receipt, desk)
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tx.type == "give") {
              V3Button(StrMoney.setDue, { step = TxStep.DUE }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.Event)
            }
            V3Button(StrMoney.delete, { closer.close(actions.onDelete) }, Modifier.weight(1f), kind = V3ButtonKind.DANGER, haptic = true)
          }
        }
        TxStep.DUE -> DueStep(
          entry = entry,
          onCancel = { step = TxStep.DETAILS },
          onSave = { due -> closer.close { actions.onSaveDue(due) } },
        )
      }
    }
  }
}

@Composable
private fun TxDetails(tx: DebtTransaction, entry: DebtBook.Entry?, book: DebtBook.Book?) {
  val give = tx.type == "give"
  val tone = if (give) LedgerTone.OWE else LedgerTone.SETTLED
  Column {
    V3StatusPill(if (give) V3Status.CANCELLED else V3Status.DELIVERED, if (give) StrMoney.giveTitle else StrMoney.receiveTitle)
    Spacer(Modifier.height(8.dp))
    Text(MoneyText.signed(tx.amount, give), style = v3Text(28, FontWeight.Bold, 36), color = tone.ink())
    Spacer(Modifier.height(12.dp))
    val lines = remember(tx, entry, book) { DebtorTxModel.lines(tx, entry, book) }
    V3Card(padding = PaddingValues(vertical = 4.dp)) {
      lines.forEachIndexed { i, line ->
        if (i > 0) V3Divider(start = 14.dp)
        DetailLine(line)
      }
    }
    val items = remember(entry) { DebtorTxModel.items(entry) }
    if (entry != null && items.isNotEmpty()) {
      Spacer(Modifier.height(14.dp))
      V3InvoiceItems(items, title = DebtorTxModel.itemsTitle(entry), footer = DebtorTxModel.itemsFooter(entry))
    }
  }
}

@Composable
private fun DetailLine(line: DebtorTxModel.Line) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(line.label, style = v3Text(13, FontWeight.Normal, 18), color = c.inkMuted)
    Spacer(Modifier.width(12.dp))
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
      val pill = line.pill
      if (pill == null) Text(line.value, style = v3Text(14, FontWeight.Medium, 20), color = c.ink, textAlign = TextAlign.End)
      else V3StatusPill(
        when (pill) {
          DebtorTxModel.Pill.SETTLED -> V3Status.DELIVERED
          DebtorTxModel.Pill.WAIT -> V3Status.AWAITING
          DebtorTxModel.Pill.INFO -> V3Status.IN_DELIVERY
          DebtorTxModel.Pill.OWE -> V3Status.CANCELLED
        },
        line.value,
      )
    }
  }
}

/** نامِ دکان برای سرِ رسید — همان که فاکتور می‌گیرد (`PrintJob`) */
internal fun storeName(context: Context): String =
  runCatching { context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "") }.getOrNull().orEmpty()

/** رسیدِ ردیف: چاپ/PDF با چاپِ سیستم؛ گوشی «فرستادن»، کامپیوتر «کپیِ متن» (دکمهٔ بی‌کار نمی‌گذاریم) */
@Composable
private fun ReceiptActions(receipt: DebtBook.Receipt?, desk: Boolean) {
  receipt ?: return
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  var copied by remember { mutableStateOf(false) }
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    V3Button(StrMoney.printPdf, { ReportPrint.print(context, receipt.sheet(), receipt.title) }, Modifier.weight(1f), icon = Icons.Outlined.Print)
    if (desk) {
      V3Button(
        if (copied) StrMoney.copied else StrMoney.copyText,
        { clipboard.setText(AnnotatedString(receipt.text())); copied = true },
        Modifier.weight(1f),
        kind = V3ButtonKind.SECONDARY,
        icon = Icons.Outlined.ContentCopy,
      )
    } else {
      V3Button(
        StrMoney.sendReceipt,
        {
          runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_SUBJECT, receipt.title)
              putExtra(Intent.EXTRA_TEXT, receipt.text())
            }
            context.startActivity(Intent.createChooser(send, StrMoney.sendReceiptTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          }
        },
        Modifier.weight(1f),
        kind = V3ButtonKind.SECONDARY,
        icon = Icons.AutoMirrored.Outlined.Send,
      )
    }
  }
}

/** گامِ دوم: گذاشتن/عوض کردن/برداشتنِ سررسیدِ یک بردِ ثبت‌شده (فروشِ نسیه هم) */
@Composable
private fun DueStep(entry: DebtBook.Entry?, onCancel: () -> Unit, onSave: (String?) -> Unit) {
  var due by rememberSaveable { mutableStateOf(entry?.dueDate.orEmpty()) }
  LaunchedEffect(entry?.tx?.id) { due = entry?.dueDate.orEmpty() }
  Column {
    Text(StrMoney.dueTitle, style = v3Text(19, FontWeight.Bold, 26), color = PhoneV3.colors.ink)
    if (entry != null) {
      Text(
        listOfNotNull(MoneyText.date(entry.tx.date), "${DebtBook.show(entry.amount)} ؋", entry.invoiceLabel?.let { StrMoney.invoiceNo(it) }).joinToString(" — "),
        style = v3Text(13, FontWeight.Normal, 19),
        color = PhoneV3.colors.inkMuted,
      )
    }
    Spacer(Modifier.height(14.dp))
    var dueText by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(0) }
    DueDateEditor(due, { due = it }, Modifier.v3Shake(shake), onText = { dueText = it })
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrKit.cancel, onCancel, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      V3Button(StrKit.save, { if (DebtorTxModel.dueBroken(dueText)) shake++ else onSave(due.ifBlank { null }) }, Modifier.weight(1.4f), haptic = true)
    }
  }
}

/** همان رسیدِ امروز (`DebtBook.receipt`) برای یک ردیف */
internal fun receiptOf(context: Context, d: ir.vil3ntec.tohid.data.ShopData, debtorId: String, txId: String): DebtBook.Receipt? =
  DebtBook.receipt(d, debtorId, txId, storeName(context), todayIso())
