package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** «۱۴:۳۲» برای امروز، وگرنه تاریخِ خورشیدی */
internal fun txWhen(tx: Transactions.Tx, today: String): String =
  if (tx.date == today && tx.at > 0) {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = tx.at }
    "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
  } else formatDate(tx.date)

/**
 *  یک تراکنش به شکلِ عکسِ ۳: کارتِ گرد، دایرهٔ جهت (↓ پول آمد / ↑ پول رفت، با رنگ و آیکن —
 *  رنگ تنها نیست)، چه کسی، «نوع · زمان»، مبلغ و شمارِ اقلام.
 */
@Composable
internal fun TxRow(tx: Transactions.Tx, today: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val tone = PhoneV3Roles.tone(c, if (tx.incoming) V3Tone.SUCCESS else V3Tone.INFO)
  val shape = RoundedCornerShape(16.dp)
  Row(
    modifier
      .fillMaxWidth()
      .v3Pressable(label = tx.who, onClick = onClick)
      .heightIn(min = 68.dp)
      .clip(shape)
      .background(c.card)
      .border(1.dp, c.hairline, shape)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(tone.bg), contentAlignment = Alignment.Center) {
      Icon(if (tx.incoming) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, contentDescription = null, tint = PhoneV3Roles.toneIcon(c, if (tx.incoming) V3Tone.SUCCESS else V3Tone.INFO), modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(tx.who, style = v3Text(15, FontWeight.SemiBold, 21), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      val sub = listOfNotNull(Transactions.kindLabel(tx.kind), StrHome.txCancelled.takeIf { tx.cancelled }, txWhen(tx, today)).joinToString(" · ")
      Text(sub, style = v3Text(12, FontWeight.Medium, 17), color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
      Text(
        "${money(tx.amount)} ؋",
        style = v3Text(15, FontWeight.Bold, 21).copy(textDecoration = if (tx.cancelled) TextDecoration.LineThrough else null),
        color = if (tx.cancelled) c.inkMuted else c.ink,
        maxLines = 1,
      )
      if (tx.itemCount > 0) Text(StrHome.txItems(tx.itemCount), style = v3Text(12, FontWeight.Medium, 17), color = c.inkMuted, maxLines = 1)
    }
  }
}

/**
 *  برگهٔ یک تراکنش: مبلغ، نوع، زمان و شمارهٔ فاکتور، **اقلام** (همان کارتِ اقلامِ برگهٔ
 *  قرض‌دار)، تخفیف و پرداختِ همان لحظه، و «دیدنِ فاکتور» / «صفحهٔ تأمین‌کننده».
 */
@Composable
internal fun TxSheet(d: ShopData, tx: Transactions.Tx, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
  val c = PhoneV3.colors
  val lines = remember(d, tx) { Transactions.lines(d, tx) }
  val sale = remember(d, tx) { Transactions.sale(d, tx) }
  V3Sheet(onDismiss = onDismiss, title = tx.who) { closer ->
    Row(verticalAlignment = Alignment.CenterVertically) {
      V3StatusPill(
        if (tx.cancelled) V3Status.CANCELLED else if (tx.incoming) V3Status.DELIVERED else V3Status.IN_DELIVERY,
        if (tx.cancelled) StrHome.txCancelled else Transactions.kindLabel(tx.kind),
      )
      Spacer(Modifier.weight(1f))
      Text("${money(tx.amount)} ؋", style = v3Text(26, FontWeight.Bold, 34), color = c.ink)
    }
    Spacer(Modifier.height(6.dp))
    Text(
      listOfNotNull(tx.invoice?.let { StrHome.invoiceNo(it) }, formatDate(tx.date), txWhen(tx, tx.date).takeIf { tx.at > 0 }).distinct().joinToString(" · "),
      style = v3Text(13, FontWeight.Medium, 18),
      color = c.inkMuted,
    )
    Spacer(Modifier.height(14.dp))
    Text(StrHome.txItemsTitle, style = v3Text(15, FontWeight.Bold, 21), color = c.ink, modifier = Modifier.padding(horizontal = 4.dp))
    Spacer(Modifier.height(6.dp))
    DraftInvoiceItems(
      lines,
      footer = listOfNotNull(
        sale?.discount?.takeIf { it > 0 }?.let { StrHome.txDiscount(money(it)) },
        sale?.paidAmount?.takeIf { it > 0 && sale.debtorId != null }?.let { StrHome.txPaidNow(money(it)) },
      ),
    )
    tx.route?.let { route ->
      Spacer(Modifier.height(14.dp))
      V3Button(
        if (tx.kind == Transactions.Kind.PURCHASE) StrHome.txShowSupplier else StrHome.txShowInvoice,
        onClick = { closer.close { onOpen(route) } },
        kind = V3ButtonKind.SECONDARY,
      )
    }
  }
}

/**
 *  پیش‌نویسِ کیت (برای یکی شدن با کارتِ اقلامِ برگهٔ قرض‌دار، #354 `TxDetailSheet`): هر قلم
 *  نام، «مقدار واحد × قیمتِ یک» (+ مرجوعی) و جمع؛ خطِ پایانیِ تخفیف/پرداخت.
 */
@Composable
internal fun DraftInvoiceItems(lines: List<Transactions.Line>, footer: List<String> = emptyList()) {
  val c = PhoneV3.colors
  V3Card(radius = 16.dp, padding = PaddingValues(vertical = 4.dp)) {
    if (lines.isEmpty()) {
      Text(StrHome.txNoItems, style = v3Text(13, FontWeight.Medium, 18), color = c.inkMuted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
    lines.forEachIndexed { i, l ->
      if (i > 0) V3Divider(start = 14.dp)
      val unit = if (l.unit.isNotBlank()) " ${l.unit}" else ""
      Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
          Text(l.name, style = v3Text(14, FontWeight.Medium, 20), color = c.ink)
          Text(
            "${qty(l.quantity)}$unit × ${money(l.unitPrice)}" + if (l.returned > 0) " · " + StrHome.txReturned(qty(l.returned)) else "",
            style = v3Text(12, FontWeight.Normal, 17),
            color = c.inkMuted,
          )
        }
        Text(money(l.total), style = v3Text(14, FontWeight.SemiBold, 20), color = c.ink)
      }
    }
    if (footer.isNotEmpty()) {
      V3Divider(start = 14.dp)
      Text(footer.joinToString(" · "), style = v3Text(12, FontWeight.Normal, 17), color = c.inkMuted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
  }
}
