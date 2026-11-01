package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.ExternalCompany
import ir.vil3ntec.tohid.core.delivery.InvoiceCheck
import ir.vil3ntec.tohid.core.delivery.Statement
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  تطبیقِ صورت‌حسابِ شرکتِ بیرونی (`delivery/invoice/{name}`، DLV-19): بازه (این ماه / ماهِ پیش / ۳۰ روز)،
 *  جمع‌های سرور (کرایهٔ بدهیِ دکان، نقدِ COD نزدِ شرکت، خالص در یک جهتِ روشن)، سفارش‌های همان شرکت، و
 *  سنجشِ صورت‌حسابِ نوشته‌شده با `InvoiceCheck` — «کرایه درست ولی نقد نزدِ شرکت» هرگز «همه را بپرداز» نیست.
 */
@Composable
internal fun InvoiceSheet(co: ExternalCompany, repo: DeliveryRepository, onDismiss: () -> Unit, start: Statement? = null) {
  val c = PhoneV3.colors
  val warn = PhoneV3Roles.status(c, V3Status.WARNING).fg
  var period by remember { mutableStateOf("this") }
  var stmt by remember { mutableStateOf(start) }
  var err by remember { mutableStateOf<String?>(null) }
  var total by remember { mutableStateOf("") }
  var count by remember { mutableStateOf("") }
  var lines by remember { mutableStateOf("") }
  var result by remember { mutableStateOf<InvoiceCheck.Result?>(null) }
  var parseErrors by remember { mutableStateOf<List<String>>(emptyList()) }

  LaunchedEffect(period) {
    if (start != null && period == "this") return@LaunchedEffect
    stmt = null
    result = null
    val (from, to) = when (period) {
      "prev" -> jalaliMonth(-1)
      "30" -> (System.currentTimeMillis() - THIRTY_DAYS) to (System.currentTimeMillis() + 60_000L)
      else -> jalaliMonth(0)
    }
    repo.statement(co.name, from, to).onSuccess { stmt = it; err = null }.onFailure { err = it.userMessage }
  }

  V3Sheet(onDismiss = onDismiss, title = StrOnline.dlInvoiceTitle(co.name)) { _ ->
    V3ChipRow(contentPadding = PaddingValues(0.dp)) {
      listOf("this" to StrOnline.dlThisMonth, "prev" to StrOnline.dlPrevMonth, "30" to StrOnline.dlLast30).forEach { (k, l) -> V3Chip(l, period == k, { period = k }) }
    }
    Spacer(Modifier.height(12.dp))
    val s = stmt
    if (s == null) {
      if (err != null) V3Message(V3Tone.ERROR, err.orEmpty()) else V3Skeleton(rows = 2)
      return@V3Sheet
    }
    val delivered = s.rows.count { it.status == "delivered" }
    V3Card(padding = PaddingValues(vertical = 4.dp)) {
      Sum(StrOnline.dlGiven, StrOnline.dlOrders(s.rows.size))
      Sum(StrOnline.dlDeliveredOurs, StrOnline.dlOrders(delivered))
      Sum(StrOnline.dlFaresOwed, OnlineFmt.afn(s.deliveredFareMinor), strong = true)
      Sum(StrOnline.dlCashOwed, OnlineFmt.afn(s.deliveredCashMinor), strong = true, tone = if (s.deliveredCashMinor > 0) warn else null)
      V3Divider()
      DeliveryModel.net(StrOnline.dlNetOurs, s.deliveredFareMinor - s.deliveredCashMinor).let { (l, v) -> Sum(l, v, strong = true) }
      val notDone = s.rows.count { it.status != "delivered" }
      if (notDone > 0) Sum(StrOnline.dlNotDone, StrOnline.dlOrders(notDone), tone = warn)
    }
    if (s.truncated) { Spacer(Modifier.height(8.dp)); V3Message(V3Tone.WARNING, StrOnline.dlTruncated) }
    if (s.rows.isNotEmpty()) V3Section(StrOnline.dlGivenTo(co.name)) {
      s.rows.forEachIndexed { i, r ->
        if (i > 0) V3Divider(start = 64.dp)
        val bits = listOfNotNull(
          r.externalRef.takeIf { it.isNotEmpty() },
          StrOnline.missionStatus(r.status),
          StrOnline.dlFareBit(OnlineFmt.afn(r.fareMinor)),
          r.cashCollectedMinor?.takeIf { it > 0 }?.let { StrOnline.dlCashBit(OnlineFmt.afn(it)) },
          if (r.status == "delivered" && r.expectedCollectMinor > 0 && (r.cashCollectedMinor ?: 0L) != r.expectedCollectMinor) StrOnline.dlExpectedBit(OnlineFmt.afn(r.expectedCollectMinor)) else null,
        )
        V3ListRow(OnlineFmt.orderNo(r.orderNo), subtitle = bits.joinToString(" · "), subtitleLines = 3, icon = Icons.Outlined.LocalShipping, hue = if (r.status == "delivered") V3Hue.MINT else V3Hue.AMBER, chevron = false)
      }
    }

    V3Section(StrOnline.dlCompanyInvoice) {
      val totalMinor = if (total.isBlank()) null else minorOf(total)
      val countN = count.trim().let { if (it.isEmpty()) null else OrderDraft.latin(it).toIntOrNull() }
      Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V3TextField(total, { total = it.take(16); result = null }, StrOnline.dlInvoiceTotal, kind = V3FieldKind.MONEY, required = true,
          error = if (total.isNotBlank() && totalMinor == null) StrOnline.dlBadNumber else null, modifier = Modifier.fillMaxWidth())
        V3TextField(count, { count = it.take(6); result = null }, StrOnline.dlInvoiceCount, kind = V3FieldKind.NUMBER,
          error = if (count.isNotBlank() && countN == null) StrOnline.dlBadNumber else null, modifier = Modifier.fillMaxWidth())
        V3TextField(lines, { lines = it.take(LINES_MAX); result = null }, StrOnline.dlInvoiceLines, helper = StrOnline.dlInvoiceLinesHelp, singleLine = false, modifier = Modifier.fillMaxWidth())
        parseErrors.forEach { Text(it, style = V3Type.footnote, color = PhoneV3Roles.errorText(c)) }
        V3Button(StrOnline.dlCheck, {
          val parsed = InvoiceCheck.parse(lines)
          parseErrors = parsed.errors
          if (parsed.errors.isEmpty()) result = InvoiceCheck.check(s.rows, totalMinor ?: 0L, countN, parsed.lines)
        }, Modifier.fillMaxWidth(), enabled = totalMinor != null && (count.isBlank() || countN != null))
      }
    }

    result?.let { r ->
      Spacer(Modifier.height(12.dp))
      when {
        r.ok -> V3Message(V3Tone.SUCCESS, StrOnline.dlInvoiceOk)
        //  ⛔ کرایه درست است ولی نقدِ دکان نزدِ شرکت است ⇒ «همه را بپرداز» نیست
        r.invoiceMatches -> V3Message(V3Tone.WARNING, StrOnline.dlInvoiceFaresOk)
        else -> V3Message(V3Tone.WARNING, StrOnline.dlInvoiceDiff)
      }
      Spacer(Modifier.height(8.dp))
      V3Card(padding = PaddingValues(vertical = 4.dp)) {
        DeliveryModel.net(StrOnline.dlNetInvoice, r.netToCompanyByInvoiceMinor).let { (l, v) -> Sum(l, v, strong = true) }
        Sum(StrOnline.dlAmountDiff, OnlineFmt.afn(r.amountDiffMinor), strong = true, tone = if (r.amountDiffMinor == 0L) null else PhoneV3Roles.errorText(c))
        r.countDiff?.let { Sum(StrOnline.dlCountDiff, it.toString(), tone = if (it == 0) null else warn) }
      }
      if (r.items.isNotEmpty()) V3Section(null) {
        r.items.forEachIndexed { i, item ->
          if (i > 0) V3Divider(start = 22.dp)
          val bits = listOfNotNull(
            StrOnline.dlInvoiceKind(item.kind.name),
            item.billedMinor?.let { StrOnline.dlBilledBit(OnlineFmt.afn(it)) },
            item.ourFareMinor?.let { StrOnline.dlOursBit(OnlineFmt.afn(it)) },
            item.cashMinor?.takeIf { it > 0 }?.let { StrOnline.dlCashBit(OnlineFmt.afn(it)) },
            item.status.takeIf { it.isNotEmpty() && it != "delivered" }?.let(StrOnline::missionStatus),
          )
          V3ListRow(
            item.key + (item.orderNo?.let { n -> " (#$n)" } ?: ""), subtitle = bits.joinToString(" · "), subtitleLines = 3,
            tile = false, danger = item.kind != InvoiceCheck.Kind.MATCHED, chevron = false,
          )
        }
      }
    }
  }
}

private const val THIRTY_DAYS = 30L * 86_400_000L
private const val LINES_MAX = 20_000
