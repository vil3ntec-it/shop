package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3ScopedSearch
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  زیرِ کارتِ خلاصهٔ تاریخچه (۶۵h، صاحب‌کار: «این تاریخچه فروش چیه، من حتی یک درصدش رو هم نفهمیدم؛ دقیق‌تر و ساده‌تر
 *  اش کن و رنگ بده»): هر عدد با نامِ ساده‌اش و رنگِ معنایش ([historyTone]) —
 *   • «برگشت دادید» (سرخ)، «خالصِ امروز» (فروش منهای برگشت، پررنگ)، «قرضِ وصول‌شده» (فیروزه‌ای، **فروش نیست**)؛ ردیفِ صفر نه؛
 *   • حالِ صندوق ردیفِ وضعیت است (نه کارِ سربرگ): «صندوق · بسته» و زدن ⇐ صفحهٔ صندوق؛
 *   • یک کارِ «برگشتِ کالا» به‌جای راهنمای خاکستریِ بلند ⇐ برگه‌ای با همهٔ راه‌های برگشت (QR، جست‌وجو، بی رسید)؛
 *   • سرخطِ هر روزِ فهرست با جمعِ فروشِ همان روز.
 *  جمع‌ها همان X/Z و پایانِ روزند (`SalesPeriods.Totals`)؛ این‌جا عددی ساخته نمی‌شود.
 */

/** یک خطِ نتیجه: نوع (رنگ و آیکن)، نامِ ساده، مبلغِ آماده و توضیحِ کوتاه */
data class ResultLine(val kind: MoneyKind, val label: String, val amount: String, val note: String?)

/** خطِ «برگشت / خالص / قرضِ وصول‌شده» ِ یک دوره — خالص، بی Compose */
object HistoryResult {

  /**
   *  برگشت (با شمار) و خالص فقط وقتی برگشتی بوده (بی برگشت خالص همان فروشِ کارتِ بالاست)؛ قرضِ وصول‌شده فقط وقتی هست.
   *  [netLabel]: «خالصِ امروز»، «خالصِ دیروز»، «خالصِ عقرب ۱۴۰۵».
   */
  fun lines(t: SalesPeriods.Totals, netLabel: String): List<ResultLine> = buildList {
    if (t.returns > 0 || t.refunds != 0L) {
      val refunds = if (t.refunds == 0L) PeriodLabel.amount(0) else "−" + PeriodLabel.amount(t.refunds)
      add(ResultLine(MoneyKind.RETURN, StrHistory.refunded, refunds, StrHistory.refundTimes(plain(t.returns))))
      add(ResultLine(MoneyKind.NET, netLabel, PeriodLabel.amount(t.net), StrHistory.netNote))
    }
    if (t.receipts != 0L) add(ResultLine(MoneyKind.DEBT_IN, StrHistory.debtCollected, PeriodLabel.amount(t.receipts), StrHistory.debtCollectedNote))
  }

  /** «۱ لغوشده و ۲ اینترنتی در این جمع‌ها نیست» — همان مرزِ X/Z؛ هیچ ⇒ null */
  fun excluded(t: SalesPeriods.Totals): String? {
    val parts = listOfNotNull(
      t.cancelled.takeIf { it > 0 }?.let { StrHistory.cancelled(plain(it)) },
      t.online.takeIf { it > 0 }?.let { StrHistory.online(plain(it)) },
    )
    return if (parts.isEmpty()) null else StrHistory.notCounted(parts.joinToString(StrHistory.and))
  }

  /** نامِ خالص: دورهٔ جاریِ روز «خالصِ امروز»، بقیه «خالصِ <دوره>» */
  fun netLabel(current: Boolean, unit: SalesPeriods.Unit, title: String): String =
    if (current && unit == SalesPeriods.Unit.DAY) StrHistory.netToday else StrHistory.netOf(title)
}

/**
 *  کارتِ نتیجهٔ دوره زیرِ خلاصه: خط‌های [HistoryResult.lines]، حالِ صندوق ([drawer]، فقط دورهٔ جاری) و یادداشتِ «بیرون از
 *  جمع». [money] = false (شمارشِ کور، PAY-13) ⇒ بی مبلغ، فقط صندوق. هیچ ⇒ هیچ.
 */
@Composable
internal fun PeriodResult(
  t: SalesPeriods.Totals,
  netLabel: String,
  money: Boolean,
  drawer: DrawerLine?,
  onDrawer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val lines = remember(t, netLabel, money, ir.vil3ntec.tohid.core.i18n.I18n.lang) { if (money) HistoryResult.lines(t, netLabel) else emptyList() }
  val excluded = remember(t, money, ir.vil3ntec.tohid.core.i18n.I18n.lang) { if (money) HistoryResult.excluded(t) else null }
  if (lines.isEmpty() && drawer == null && excluded == null) return
  val c = PhoneV3.colors
  Column(modifier.padding(vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(PhoneV3.groupRadius)).background(c.card)) {
    lines.forEachIndexed { i, l ->
      if (i > 0) V3Divider(start = 60.dp)
      ResultRow(l)
    }
    if (drawer != null) {
      if (lines.isNotEmpty()) V3Divider(start = 60.dp)
      DrawerRow(drawer, onDrawer)
    }
    excluded?.let {
      if (lines.isNotEmpty() || drawer != null) V3Divider()
      Text(it, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
  }
}

/** «↩ برگشت دادید · ۱ بار ......... −۱۰۰ ؋» — مبلغ به رنگِ همان معنا، خالص پررنگ */
@Composable
private fun ResultRow(l: ResultLine) {
  val tone = historyTone(PhoneV3.colors, l.kind)
  V3ListRow(
    title = l.label,
    subtitle = l.note,
    leading = { MoneyTile(l.kind, size = 36.dp) },
    trailing = {
      Text(
        l.amount,
        style = if (l.kind == MoneyKind.NET) V3Type.title else V3Type.headline,
        color = tone.fg,
        maxLines = 1,
        modifier = Modifier.widthIn(max = 168.dp),
      )
    },
    chevron = false,
    modifier = Modifier.semantics(mergeDescendants = true) { moneyKind = l.kind },
  )
}

/**
 *  حالِ صندوق — **وضعیت**، نه دکمهٔ سربرگ (پیش‌تر قرصِ «صندوق بسته است» در کارتِ خلاصه بود و کار به نظر می‌آمد):
 *  «صندوق» با «● باز/بسته» در پایان؛ زیرش ساعت و نقدِ مورد انتظار، یا «برای شمردنِ پولِ دخل بازش کنید». زدن ⇐ صفحهٔ صندوق.
 */
@Composable
private fun DrawerRow(drawer: DrawerLine, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val tone = historyTone(c, if (drawer.open) MoneyKind.CASH else MoneyKind.ONLINE)
  V3ListRow(
    title = StrSale.drawerWord,
    subtitle = if (drawer.open) drawer.text else StrHistory.drawerClosedHint,
    leading = {
      Box(Modifier.size(36.dp).clip(RoundedCornerShape(ir.vil3ntec.tohid.ui.theme.Corner.CARD)).background(tone.bg), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.PointOfSale, contentDescription = null, tint = tone.fg, modifier = Modifier.size(19.dp))
      }
    },
    trailing = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone.fg))
        Spacer(Modifier.width(5.dp))
        Text(if (drawer.open) StrHistory.drawerOpenWord else StrHistory.drawerClosedWord, style = V3Type.subhead, color = tone.fg, maxLines = 1)
      }
    },
    chevron = false,
    onClick = onClick,
  )
}

/** سرخطِ یک روزِ فهرست: «امروز» و «۵ فاکتور» در آغاز، «فروش ۱٬۲۵۰ ؋» ِ همان روز در پایان (شمارشِ کور ⇒ بی مبلغ) */
@Composable
internal fun DayHeader(title: String, totals: SalesPeriods.Totals?, money: Boolean) {
  val c = PhoneV3.colors
  Row(
    Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 8.dp).semantics(mergeDescendants = true) { heading() },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = V3Type.headline, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (totals != null) Text(PeriodLabel.counts(totals), style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (money && totals != null) {
      Spacer(Modifier.width(8.dp))
      Text(StrHistory.daySales(PeriodLabel.amount(totals.sales)), style = V3Type.callout, color = c.ink, maxLines = 1)
    }
  }
}

/** «برگشتِ کالا» — تنها کارِ برگشت زیرِ خلاصه؛ برگه همهٔ راه‌ها را دارد ([ReturnStartSheet]) */
@Composable
internal fun ReturnEntry(onClick: () -> Unit) {
  Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    V3Button(StrHistory.returnGoods, onClick, kind = V3ButtonKind.SECONDARY, icon = Icons.AutoMirrored.Outlined.AssignmentReturn, height = 44.dp)
  }
}

/**
 *  برگهٔ «برگشتِ کالا»: همان راه‌های پیشین، هر کدام با یک خطِ «چه می‌کند» — QR ِ رسید (گوشی)، پیدا کردنِ فاکتور با شماره یا
 *  نامِ مشتری (جست‌وجوی همین صفحه؛ روی کامپیوتر کادرِ همیشگیِ زیرِ عنوان)، و برگشتِ بی رسید. برگشت از ردیف (کشیدن، نگه داشتن،
 *  صفحهٔ فاکتور) همان می‌ماند.
 */
@Composable
internal fun ReturnStartSheet(actions: HistoryActions, onClose: () -> Unit) {
  val chosen = remember { booleanArrayOf(false) }
  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = StrHistory.returnGoods, detents = false) { closer ->
    fun pick(go: () -> Unit) { chosen[0] = true; closer.close { onClose(); go() } }
    actions.scanReceipt?.let { scan ->
      V3ListRow(StrSale.scanReceiptQr, subtitle = StrHistory.scanReceiptHint, icon = Icons.Outlined.QrCodeScanner, hue = V3Hue.BLUE, onClick = { pick(scan) })
      V3Divider(start = 64.dp)
    }
    V3ListRow(
      StrHistory.findInvoice, subtitle = StrHistory.findInvoiceHint, icon = Icons.Outlined.Search, hue = V3Hue.BLUE,
      onClick = { pick { V3ScopedSearch.openCurrent() } },
    )
    actions.noReceipt?.let { none ->
      V3Divider(start = 64.dp)
      V3ListRow(StrSale.noReceiptReturn, subtitle = StrHistory.noReceiptHint, icon = Icons.AutoMirrored.Outlined.AssignmentReturn, hue = V3Hue.CORAL, onClick = { pick(none) })
    }
  }
}

/** «↩ برگشت شد: ۱۰۰ ؋» زیرِ ردیفِ فاکتورِ برگشت‌خورده — سرخ با آیکن، نه رنگِ تنها */
@Composable
internal fun RefundLine(text: String, quiet: Boolean = false) {
  //  [quiet] (ردیفِ فاکتور، ۶۸c): واژه و آیکنِ آرامِ کم‌رنگ — تنها نشانِ رنگیِ ردیف واژهٔ پرداخت است؛ معنا (`moneyKind`) همان
  val c = PhoneV3.colors
  val ink = if (quiet) c.inkMuted else historyTone(c, MoneyKind.RETURN).fg
  Row(Modifier.padding(top = 2.dp).semantics(mergeDescendants = true) { moneyKind = MoneyKind.RETURN }, verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.AutoMirrored.Outlined.AssignmentReturn, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(4.dp))
    Text(text, style = V3Type.footnote, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
