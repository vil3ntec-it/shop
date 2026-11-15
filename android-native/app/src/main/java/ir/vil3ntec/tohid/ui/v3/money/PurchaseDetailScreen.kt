package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3AmountStatus
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3QuietStatus
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3RowTone
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules

/** کارهای جزئیاتِ یک خرید — هر کدام همان راهِ امروز (برگهٔ پرداخت، دریافتِ سفارش، برگشت، چاپ) */
internal class PurchaseDetailActions(
  val onBack: () -> Unit,
  val onSupplier: (String) -> Unit,
  /** زدنِ ردیف ⇐ صفحهٔ کالا؛ کالای حذف‌شده زدنی نیست */
  val onProduct: (String) -> Unit,
  /** «پرداخت» با ماندهٔ همین خرید (برگهٔ پرداختِ تأمین‌کننده، یادداشتِ `PurchaseBook.payNote`) */
  val onPay: (PurchaseViews.View) -> Unit,
  /** «دریافتِ کالا» ِ سفارش — null ⇒ نقش اجازه ندارد */
  val onReceive: ((String) -> Unit)?,
  val onOrder: ((String) -> Unit)?,
  /** «برگشت به تأمین‌کننده» (سندِ کسر، PUR-08) */
  val onReturn: (String) -> Unit,
  val onPrint: (PurchaseViews.View) -> Unit,
)

/** متن‌های آمادهٔ یک ردیفِ جزئیات (خالص): «۲ کارتن × ۱٬۲۰۰ ؋ (۴۸ دانه)» و «بارکد … · سری … · انقضا …» */
internal object PurchaseLineText {
  fun qty(l: PurchaseViews.Line): String {
    val main = "${MoneyText.qtyUnit(l.qty, l.unit)} × ${money(l.unitCost)} ؋"
    return l.baseQty?.let { "$main (${MoneyText.qtyUnit(it, l.baseUnit)})" } ?: main
  }

  /**
   *  بارکد، سری و تاریخ چپ‌به‌راستِ جدا (LRI…PDI): کنارِ «·» و واژهٔ فارسی، رقم و حرفِ لاتین بی آن جابه‌جا خوانده می‌شدند
   *  («انقضا · A-17 بارکد 626… · سری»)
   */
  fun ltr(s: String): String = "\u2066$s\u2069"

  fun codes(l: PurchaseViews.Line): String = listOfNotNull(
    l.code?.let { StrPurchases.barcode(ltr(it)) } ?: StrPurchases.noCode,
    l.batch.takeIf { it.isNotBlank() }?.let { StrPurchases.batch(ltr(it)) },
    l.expiry.takeIf { it.isNotBlank() }?.let { StrPurchases.expires(ltr(formatDate(it))) },
  ).joinToString(" · ")

  /** رسیدنِ ردیف: خریدِ دفتر «رسیده»؛ سفارش «رسیده ۲ از ۵» (کمتر از سفارش ⇒ کهربایی) */
  fun receipt(v: PurchaseViews.View, l: PurchaseViews.Line): V3RowStatus? = when {
    v.kind == PurchaseViews.Kind.RETURN -> null
    !v.isOrder -> V3RowStatus(StrPurchases.received)
    else -> V3RowStatus(
      StrPurchases.receivedOf(ir.vil3ntec.tohid.qty(l.received), ir.vil3ntec.tohid.qty(l.ordered)),
      if (l.received + 1e-9 >= l.ordered) V3RowTone.QUIET else V3RowTone.ATTENTION,
    )
  }
}

/**
 *  جزئیاتِ یک خرید (`purchasing/purchases/{key}`، e-rows-63b): تأمین‌کننده (زدنی)، هر ردیف با نامِ کالا، **بارکد/کد**، مقدار و
 *  واحد، فی، جمع، سری و انقضا و رسیدنِ همان ردیف (زدن ⇐ کالا)؛ جمع، پرداخت‌شده، مانده و سررسید؛ و کارها: «پرداخت» (بخشی هم)،
 *  «دریافتِ کالا» ِ سفارشِ باز، «برگشت به تأمین‌کننده»، «چاپ یا اشتراک». نوارِ چسبان ندارد؛ کارها تهِ همان صفحه‌اند.
 */
@Composable
internal fun PurchaseDetailScreen(v: PurchaseViews.View?, today: String, actions: PurchaseDetailActions) {
  if (v == null) {
    V3Scaffold(title = StrPurchases.purchase, onBack = actions.onBack, largeTitle = false) { pad ->
      Column(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) { V3EmptyState(Icons.Outlined.Inventory2, StrPurchases.notFound) }
    }
    return
  }
  val text = remember(v, today, I18n.lang) { PurchaseRowText.of(v, today) }
  val title = when {
    v.isOrder -> StrPurchases.orderNo(v.no)
    v.kind == PurchaseViews.Kind.RETURN -> StrReach.supplierReturn
    v.no > 0 -> StrPurchases.title(v.no)
    else -> StrPurchases.purchase
  }
  val bill = v.kind == PurchaseViews.Kind.BILL
  V3Scaffold(
    title = title,
    onBack = actions.onBack,
    largeTitle = false,
    overflow = listOfNotNull(
      V3MenuItem(Icons.Outlined.Print, StrPurchases.printShare) { actions.onPrint(v) }.takeIf { v.invoice != null },
      V3MenuItem(Icons.AutoMirrored.Outlined.AssignmentReturn, StrReach.supplierReturn) { actions.onReturn(v.supplierId) }.takeIf { bill },
    ),
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        V3Section(null) {
          V3ListRow(
            text.title,
            subtitle = listOfNotNull(formatDate(v.date), v.orderNo?.takeIf { !v.isOrder }?.let(StrPurchases::fromOrder)).joinToString(" · "),
            leading = { LetterAvatar(v.supplierName, V3Modules.hueOf("purchasing")) },
            below = { V3AmountStatus(text.amount, text.pay, align = TextAlign.Start, second = text.receipt) },
            onClick = { actions.onSupplier(v.supplierId) },
          )
        }
        V3Section(StrPurchases.lines(v.lines.size)) {
          v.lines.forEachIndexed { i, l ->
            if (i > 0) V3Divider(start = 16.dp)
            val known = l.name.isNotBlank()
            V3ListRow(
              l.name.ifBlank { StrMoney.deletedProduct },
              subtitle = PurchaseLineText.qty(l),
              tile = false,
              value = MoneyText.afn(kotlin.math.abs(l.total)),
              below = {
                Column {
                  Text(PurchaseLineText.codes(l), style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
                  PurchaseLineText.receipt(v, l)?.let { V3QuietStatus(it) }
                }
              },
              chevron = known,
              onClick = if (known) ({ actions.onProduct(l.productId) }) else null,
            )
          }
        }
        V3Section(null) {
          Totals(v)
        }
        if (v.notes.isNotBlank()) Text(v.notes, style = V3Type.subhead, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(horizontal = 4.dp))
        if (v.isOrder) V3Message(V3Tone.INFO, StrPurchases.pendingNote)
        if (v.kind == PurchaseViews.Kind.RETURN) V3Message(V3Tone.INFO, StrPurchases.returnNote)
        DetailButtons(v, actions)
      }
    }
  }
}

@Composable
private fun Totals(v: PurchaseViews.View) {
  val rows = buildList {
    add(StrPurchases.total to MoneyText.afn(kotlin.math.abs(v.total)))
    if (v.kind != PurchaseViews.Kind.RETURN && v.billed > 0.5) {
      add(StrPurchases.paidLabel to MoneyText.afn(v.paid))
      add(StrPurchases.remaining to MoneyText.afn(v.remaining))
    }
    v.dueDate?.let { add(StrPurchasePages.payDue to formatDate(it)) }
    if (v.landed > 0.5) add(StrPurchases.landed to MoneyText.afn(v.landed))
  }
  rows.forEachIndexed { i, (label, value) ->
    if (i > 0) V3Divider(start = 16.dp)
    V3ListRow(label, tile = false, value = value, chevron = false)
  }
}

/** کارهای تهِ صفحه: کارِ اصلی (پرداخت یا دریافت) پررنگ، بقیه ثانوی — همه در همان ستون، نه نوارِ چسبان */
@Composable
private fun DetailButtons(v: PurchaseViews.View, actions: PurchaseDetailActions) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val receive = actions.onReceive?.takeIf { v.isOrder && v.orderId != null }
    if (receive != null) V3Button(StrPurchasePages.receiveGoods, { receive(v.orderId!!) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.MoveToInbox)
    if (v.kind != PurchaseViews.Kind.RETURN && v.remaining > 0.5) {
      V3Button(
        StrMoney.pay, { actions.onPay(v) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Payments,
        kind = if (receive != null) V3ButtonKind.SECONDARY else V3ButtonKind.PRIMARY,
      )
    }
    val order = actions.onOrder?.takeIf { v.orderId != null }
    if (order != null) V3Button(StrPurchases.openOrder, { order(v.orderId!!) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.LocalShipping)
    if (v.invoice != null) V3Button(StrPurchases.printShare, { actions.onPrint(v) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Print)
    Spacer(Modifier.height(4.dp))
  }
}
