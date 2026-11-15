package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrRows
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.ui.v3.RowWhen
import ir.vil3ntec.tohid.ui.v3.V3AmountStatus
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3PillAction
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3RowTone

/**
 *  متن‌های آمادهٔ ردیفِ سفارشِ خرید (سادهٔ e-rows-63d): نامِ تأمین‌کننده عنوان است و شماره کوچک در زیرعنوان؛
 *  «۲٬۵۰۰ ؋ • نیمه‌رسیده» به متنِ آرام — «تکمیل» (حالتِ عادیِ پایان) بی وضعیت.
 */
internal class PoRowText(val title: String, val subtitle: String, val amount: String, val status: V3RowStatus?, val struck: Boolean) {
  companion object {
    fun of(o: PurchaseOrderDto, today: String): PoRowText {
      val s = PurchaseOrderBook.status(o)
      val partial = s == PurchaseOrderCore.Status.PARTIAL
      val subtitle = listOfNotNull(
        "#${o.no}",
        StrRows.rows(o.lines.size),
        StrRows.remaining(poAfn(o.totals.remainingMinor)).takeIf { partial },
        o.expectedDate?.takeIf { s == PurchaseOrderCore.Status.SENT || partial }?.let { StrRows.arrives(RowWhen.of(it, today)) },
      ).joinToString(" · ")
      val status = when (s) {
        PurchaseOrderCore.Status.COMPLETE -> null
        PurchaseOrderCore.Status.PARTIAL -> V3RowStatus(PurchaseOrderBook.label(s), V3RowTone.ATTENTION)
        else -> V3RowStatus(PurchaseOrderBook.label(s))
      }
      return PoRowText(
        title = o.supplierName.ifBlank { StrMoney.supplierLabel },
        subtitle = subtitle,
        amount = poAfn(o.totals.orderedMinor) + " ؋",
        status = status,
        struck = s == PurchaseOrderCore.Status.CANCELLED,
      )
    }
  }
}

/**
 *  ردیفِ فهرستِ «سفارش‌های خرید»: زدن ⇐ جزئیات (همهٔ کارها: فرستادن، ویرایش، تطبیق، فاکتورِ تأمین‌کننده، لغو)؛ تنها کارِ
 *  اصلی، «دریافتِ کالا»، قرصِ نرمِ پایانِ ردیف است و فقط وقتی نقش × وضعیت اجازه می‌دهد (`PoForms.actions`).
 */
@Composable
internal fun PoListRow(o: PurchaseOrderDto, perms: PoPermsDto, today: String, onOpen: () -> Unit, onReceive: () -> Unit) {
  val text = remember(o, today, I18n.lang) { PoRowText.of(o, today) }
  val receive = remember(o, perms) { PoForms.actions(o, perms).receive }
  V3ListRow(
    text.title,
    subtitle = text.subtitle,
    subtitleLines = 1,
    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
    below = { V3AmountStatus(text.amount, text.status, struck = text.struck, align = TextAlign.Start) },
    trailing = if (receive) ({ V3PillAction(StrPurchasePages.receiveGoods, onReceive, icon = Icons.Outlined.MoveToInbox) }) else null,
    chevron = false,
    onClick = onOpen,
  )
}
