package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerModel

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt

/*
 *  برگهٔ جزئیاتِ یک تراکنش — ردیفِ فهرست فقط تاریخ، مبلغ و مانده را دارد؛ بقیه این‌جاست
 *  (دستورِ صاحب محصول: «روش که بزنی بیاد که این چی‌ها رو برده و فاکتورِ خریدش این است»).
 *  همه از همان `DebtBook.Entry` (پیوندِ فروش ⇐ اقلامِ فاکتور) — هیچ منطقِ تازه‌ای نیست.
 */

/**
 *  نوع و مبلغ، تاریخ، یادداشت، باز/تسویه و سررسیدِ برد، «بابتِ …» ِ رسید، ماندهٔ پس
 *  از ردیف؛ برای بردِ فاکتوردار شماره و تاریخِ فاکتور و اقلامش. کارها: رسید/چاپ،
 *  سررسید (برد)، حذف (با برگرداندن).
 */
@Composable
internal fun TxDetailSheet(
  tx: DebtTransaction,
  entry: DebtBook.Entry?,
  book: DebtBook.Book?,
  onDismiss: () -> Unit,
  onReceipt: () -> Unit,
  onDue: (() -> Unit)?,
  onDelete: () -> Unit,
) {
  val c = PhoneV3.colors
  val dc = PhoneV3Debt.colors
  val give = tx.type == "give"
  val tint = if (give) dc.owe else dc.settled
  val wash = if (give) dc.oweTint else dc.settledTint

  DebtSheet(
    onDismiss = onDismiss,
    footer = {
      V3PrimaryButton(StrDebtor.receiptPrint, onClick = onReceipt)
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onDue != null) V3SecondaryButton(StrDebtor.setDue, Modifier.weight(1f), onClick = onDue)
        V3DangerTextButton(StrDebtor.delete, Modifier.weight(1f), onClick = onDelete)
      }
    },
  ) {
    //  نوع (قرص) و مبلغِ درشت به رنگِ نوع
    StatusPill(if (give) StrDebtor.giveTitle else StrDebtor.receiveTitle, tint, wash)
    Spacer(Modifier.height(8.dp))
    Text("${if (give) "+" else "−"}${money(tx.amount)}", style = v3Text(28, FontWeight.Bold, 36), color = tint)
    Spacer(Modifier.height(12.dp))

    V3Card(radius = 16.dp, padding = PaddingValues(vertical = 4.dp)) {
      DetailLine(StrDebtor.date, if (tx.date.isBlank()) DebtorLedgerModel.UNDATED else formatDate(tx.date))
      if (tx.notes.isNotBlank()) { V3Divider(start = 14.dp); DetailLine(StrDebtor.notes, tx.notes) }
      if (entry != null) {
        if (entry.give && entry.amount.minor > 0) {
          V3Divider(start = 14.dp)
          DetailLine(StrDebtor.status) {
            StatusPill(
              if (entry.paid) StrDebtor.paidOff else StrDebtor.openAmount(DebtBook.show(entry.open)),
              if (entry.paid) dc.settled else dc.wait,
              if (entry.paid) dc.settledTint else dc.waitTint,
            )
          }
          V3Divider(start = 14.dp)
          DetailLine(StrDebtor.due) {
            val due = entry.dueDate
            if (due == null) Text(StrDebtor.noDue, style = v3Text(14, FontWeight.Medium, 20), color = c.inkMuted)
            else StatusPill(
              "${formatDate(due)}${if (entry.overdue) " — گذشته" else ""}",
              if (entry.overdue) dc.owe else dc.info,
              if (entry.overdue) dc.oweTint else dc.infoTint,
            )
          }
        }
        if (!entry.give && entry.allocations.isNotEmpty() && book != null) {
          V3Divider(start = 14.dp)
          DetailLine(
            StrDebtor.forWhat,
            entry.allocations.joinToString("، ") { a ->
              val g = book.entry(a.txId)
              val what = g?.invoiceLabel?.let { StrDebtor.invoiceNo(it) } ?: "${StrDebtor.giveTag} ${g?.tx?.date?.let(::formatDate).orEmpty()}"
              "$what (${DebtBook.show(a.amount)})"
            },
          )
        }
        if (!entry.give && entry.open.minor > 0) {
          V3Divider(start = 14.dp)
          DetailLine(StrDebtor.prepaid, DebtBook.show(entry.open))
        }
        V3Divider(start = 14.dp)
        DetailLine(StrDebtor.balanceAfter, DebtBook.state(entry.balanceAfter))
      }
    }

    //  بردِ فاکتوردار: «این چی‌ها رو برده» — اقلامِ همان فاکتور با قیمتِ همان لحظه
    if (entry != null && entry.give && entry.lines.isNotEmpty()) {
      Spacer(Modifier.height(14.dp))
      val head = listOfNotNull(entry.invoiceLabel?.let { StrDebtor.invoiceNo(it) }, entry.sale?.date?.let(::formatDate))
      Text(
        (listOf(StrDebtor.items) + head).joinToString(" · "),
        style = v3Text(15, FontWeight.Bold, 21),
        color = c.ink,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
      Spacer(Modifier.height(6.dp))
      V3Card(radius = 16.dp, padding = PaddingValues(vertical = 4.dp)) {
        entry.lines.forEachIndexed { i, l ->
          if (i > 0) V3Divider(start = 14.dp)
          val unit = if (l.unit.isNotBlank()) " ${l.unit}" else ""
          Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(l.name, style = v3Text(14, FontWeight.Medium, 20), color = c.ink)
              Text(
                "${qty(l.quantity)}$unit × ${DebtBook.show(l.unitPrice)}" + if (l.returned > 0) " · مرجوعی ${qty(l.returned)}" else "",
                style = v3Text(12, FontWeight.Normal, 17),
                color = c.inkMuted,
              )
            }
            Text(DebtBook.show(l.total), style = v3Text(14, FontWeight.SemiBold, 20), color = c.ink)
          }
        }
        entry.sale?.let { s ->
          val bits = listOfNotNull(
            s.discount.takeIf { it > 0 }?.let { "تخفیف ${DebtBook.show(DebtBook.minor(it))}" },
            s.paidAmount.takeIf { it > 0 }?.let { "پرداختِ همان لحظه ${DebtBook.show(DebtBook.minor(it))}" },
          )
          if (bits.isNotEmpty()) {
            V3Divider(start = 14.dp)
            Text(bits.joinToString(" · "), style = v3Text(12, FontWeight.Normal, 17), color = c.inkMuted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      V3SecondaryButton(StrDebtor.showInvoice, onClick = onReceipt)
    }
    Spacer(Modifier.height(4.dp))
  }
}

/** یک ردیفِ «برچسب ⇐ مقدار» در کارتِ جزئیات */
@Composable
private fun DetailLine(label: String, value: String) {
  DetailLine(label) {
    Text(value, style = v3Text(14, FontWeight.Medium, 20), color = PhoneV3.colors.ink, textAlign = TextAlign.End)
  }
}

@Composable
private fun DetailLine(label: String, value: @Composable () -> Unit) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = v3Text(13, FontWeight.Normal, 18), color = c.inkMuted)
    Spacer(Modifier.width(12.dp))
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { value() }
  }
}

/** دکمهٔ دومِ «خطر» (حذف): سطحِ درونی با متنِ قرمز — دکمهٔ قرمزِ پُر فقط برای پرسشِ حذف */
@Composable
internal fun V3DangerTextButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(16.dp)
  Box(
    modifier
      .fillMaxWidth()
      .height(52.dp)
      .v3PressScale(onClick, text)
      .clip(shape)
      .background(PhoneV3Debt.colors.oweTint)
      .border(1.dp, c.hairline, shape),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = v3Text(16, FontWeight.SemiBold, 22), color = PhoneV3Debt.colors.owe)
  }
}
