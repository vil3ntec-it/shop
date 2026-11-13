package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3HeroCard
import ir.vil3ntec.tohid.ui.v3.V3HeroChip
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/*
 *  تکه‌های صفحهٔ تاریخچه (`HistoryScreen`): کلیدِ روز/ماه/سال، کادرِ دورهٔ جاری، «نقد · نسیه · دیگر»، کارتِ
 *  بازشوِ هر دوره، ردیفِ فاکتور، فیلتر و نوارِ چاپ. رنگ هرگز تنها نیست: هر رنگ برچسب یا آیکن دارد.
 */

private val UNITS = listOf(SalesPeriods.Unit.DAY, SalesPeriods.Unit.MONTH, SalesPeriods.Unit.YEAR)

/** «روز | ماه | سال» زیرِ عنوان؛ با فیلترِ روشن، تراشه‌های همان فیلتر و «پاک کردن» زیرش */
@Composable
internal fun UnitBar(query: HistoryQuery, onFilter: () -> Unit) {
  Column {
    Box(Modifier.padding(horizontal = 16.dp)) {
      V3Segmented(UNITS.map(PeriodLabel::unitName), UNITS.indexOf(query.unit), { query.unit = UNITS[it]; query.picked = null })
    }
    if (query.filtered) {
      Spacer(Modifier.height(8.dp))
      V3ChipRow {
        if (query.filter != HistoryFilter.ALL) V3Chip(filterName(query.filter), true, onFilter, icon = Icons.Outlined.Tune)
        if (query.channel != HistoryChannel.ALL) V3Chip(channelName(query.channel), true, onFilter, icon = Icons.Outlined.Tune)
        V3Chip(StrHistory.clearFilters, false, { query.filter = HistoryFilter.ALL; query.channel = HistoryChannel.ALL }, icon = Icons.Outlined.Close)
      }
    }
  }
}

internal fun filterName(f: HistoryFilter): String = when (f) {
  HistoryFilter.ALL -> StrSale.allChip
  HistoryFilter.CASH -> StrSale.cashPay
  HistoryFilter.CREDIT -> StrSale.creditPay
  HistoryFilter.NON_CASH -> StrSale.nonCash
  HistoryFilter.CANCELLED -> StrSale.cancelledPlural
}

internal fun channelName(c: HistoryChannel): String = when (c) {
  HistoryChannel.ALL -> StrSale.allChip
  HistoryChannel.STORE -> StrHistory.inStore
  HistoryChannel.ONLINE -> StrSale.online
}

/** کادرِ بزرگِ دوره: «جمع فروش» و شمارها؛ شمارشِ کور ⇒ فقط شمار */
@Composable
internal fun PeriodHero(p: SalesPeriods.Period, title: String, money: Boolean, chips: List<V3HeroChip>) {
  val t = p.totals
  val returns = if (money && t.refunds != 0L) " · " + StrHistory.returnsAmount(PeriodLabel.amount(t.refunds)) else ""
  val value = if (money) PeriodLabel.amount(t.sales) else StrHistory.invoices(plain(t.invoices))
  V3HeroCard(
    title = title,
    value = value,
    label = if (money) StrSale.salesTotal else null,
    caption = if (money) PeriodLabel.counts(t) + returns else StrHistory.countsOnly,
    chips = chips,
    //  «چاپ» کنارِ «صندوق باز — … · نقدِ مورد انتظار: …» در گوشیِ ۳۶۰ و درشتیِ ×۱٫۳ جا نمی‌شد و مبلغ بریده می‌شد
    wrapChips = true,
  )
}

/** «نقد · نسیه · دیگر» و زیرش «برگشت» و «خالص» — همان جمع‌های X/Z؛ لغوشده و اینترنتی با یادداشت بیرون از جمع */
@Composable
internal fun MoneySplit(t: SalesPeriods.Totals, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  Column(modifier.fillMaxWidth().clip(shape).background(c.card).border(1.dp, c.hairline, shape).padding(14.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Cell(StrHistory.cash, t.cash, V3Hue.MINT, Modifier.weight(1f))
      Cell(StrHistory.credit, t.credit, V3Hue.AMBER, Modifier.weight(1f))
      Cell(StrHistory.other, t.other, V3Hue.SKY, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    V3Divider()
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        StrHistory.returns + " (" + plain(t.returns) + ")", style = V3Type.footnote, color = c.inkMuted,
      )
      Spacer(Modifier.width(6.dp))
      Text(
        if (t.refunds == 0L) PeriodLabel.amount(0) else "−" + PeriodLabel.amount(t.refunds),
        style = V3Type.callout, color = if (t.refunds == 0L) c.ink else c.negative, modifier = Modifier.weight(1f),
      )
      Text(StrHistory.net + " ", style = V3Type.footnote, color = c.inkMuted)
      Text(PeriodLabel.amount(t.net), style = V3Type.headline, color = c.ink)
    }
    if (t.receipts != 0L) {
      Spacer(Modifier.height(4.dp))
      Text(StrHistory.receipts + ": " + PeriodLabel.amount(t.receipts), style = V3Type.footnote, color = c.inkMuted)
    }
    if (t.cancelled > 0 || t.online > 0) {
      Spacer(Modifier.height(4.dp))
      Text(StrHistory.notInTotals, style = V3Type.caption, color = c.inkMuted)
    }
  }
}

@Composable
private fun Cell(label: String, minor: Long, hue: V3Hue, modifier: Modifier) {
  val c = PhoneV3.colors
  Column(modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(PhoneV3Palette.glyph(c, hue)))
      Spacer(Modifier.width(6.dp))
      Text(label, style = V3Type.footnote, color = c.inkMuted, maxLines = 1)
    }
    Spacer(Modifier.height(2.dp))
    Text(PeriodLabel.amount(minor), style = V3Type.callout, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/**
 *  کارتِ یک دورهٔ پیشین: عنوان، شمارها و مبلغ؛ زدن ⇐ باز/بسته (گوشی) یا برگزیدن (دوستونی). باز ⇒ «نقد · نسیه ·
 *  دیگر» و «چاپ» زیرش، و فاکتورها پس از کارت. [filteredCount] با فیلترِ روشن: «۵ از ۳۲ فاکتور».
 */
@Composable
internal fun PeriodCard(
  p: SalesPeriods.Period,
  title: String,
  money: Boolean,
  open: Boolean,
  onToggle: () -> Unit,
  onPrint: (() -> Unit)?,
  selected: Boolean = false,
  filteredCount: Int? = null,
  /** false ⇒ کارتِ برگزیدنیِ دوستونی: بی پیکانِ باز/بسته */
  toggle: Boolean = true,
) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  val turn by animateFloatAsState(if (open) 180f else 0f, label = "periodChevron")
  val t = p.totals
  val counts = filteredCount?.let { StrHistory.shownOf(plain(it), plain(t.invoices + t.online + t.cancelled)) } ?: PeriodLabel.counts(t)
  val chosen = selected
  Column(
    Modifier
      .padding(vertical = 5.dp)
      .fillMaxWidth()
      .clip(shape)
      .background(if (selected) PhoneV3Palette.selected(c) else c.card)
      .border(1.dp, if (selected) PhoneV3Roles.focus(c) else c.hairline, shape),
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .v3Pressable(label = title, role = Role.Button, onClick = onToggle)
        //  صفحه‌خوان: «باز/بسته» ِ گوشی یا «انتخاب‌شده» ِ دوستونی — نه فقط چرخشِ پیکان یا رنگِ مرز
        .semantics {
          if (toggle) stateDescription = if (open) StrA11y.expanded else StrA11y.collapsed
          else if (chosen) this.selected = true
        }
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(title, style = V3Type.headline, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(counts, style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      if (money) Text(PeriodLabel.amount(t.sales), style = V3Type.headline, color = c.ink, maxLines = 1)
      if (toggle) {
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = c.chevron, modifier = Modifier.size(22.dp).rotate(turn))
      }
    }
    if (open) {
      Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
        if (money) MoneySplit(t)
        if (onPrint != null) {
          Spacer(Modifier.height(8.dp))
          V3Button(StrHistory.printTitle(title), onPrint, kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Print, height = 40.dp)
        }
      }
    }
  }
}

/** ردیفِ یک فاکتور — «#۱۲۴۵ · نام»، ساعت · اقلام، قرصِ پرداخت و مبلغ؛ لغوشده خط‌خورده. کشیدن ⇐ برگشت/لغو؛ نگه داشتن ⇐ همهٔ کارها */
@Composable
internal fun InvoiceRow(s: Sale, ctx: RowCtx, first: Boolean, last: Boolean, withDate: Boolean) {
  val c = PhoneV3.colors
  val row = remember(s, ctx.look, ctx.returnsV2, withDate, ir.vil3ntec.tohid.core.i18n.I18n.lang) { HistoryModel.row(ctx.d, s, ctx.returnsV2, look = ctx.look, withDate = withDate) }
  val cancelled = row.state == SaleState.CANCELLED
  val a = ctx.actions
  Column(Modifier.clip(groupShape(first, last)).background(c.card)) {
    if (!first) V3Divider(start = 64.dp)
    V3ListRow(
      title = "${row.number} · ${row.name}",
      subtitle = row.subtitle,
      leading = {
        V3IconTile(
          if (cancelled) Icons.Outlined.Block else Icons.AutoMirrored.Outlined.ReceiptLong,
          hue = when {
            cancelled -> null
            row.state == SaleState.ONLINE -> V3Hue.SKY
            row.credit -> V3Hue.AMBER
            else -> V3Hue.MINT
          },
          danger = cancelled,
        )
      },
      trailing = {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            PhoneSaleText.amount(row.amount),
            style = V3Type.headline,
            color = if (cancelled) c.inkMuted else c.ink,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
          )
          V3StatusPill(payStatus(row), row.payLabel)
          //  PAY-07: وضعیتِ پرداختِ غیرنقد، فقط وقتی «تأییدشده» نیست
          row.payState?.let { V3StatusPill(PayStatusText.pill(it), PayStatusText.name(it)) }
        }
      },
      chevron = false,
      endActions = buildList {
        if (row.canReturn) add(V3SwipeAction(Icons.AutoMirrored.Outlined.AssignmentReturn, if (ctx.returnsV2) StrSale.returnOrExchange else StrSale.returnWord) { a.ret(row.id) })
        if (row.canCancel && (ctx.canCancel || ctx.askCancel)) add(V3SwipeAction(Icons.Outlined.Block, StrSale.cancelSale, V3ButtonKind.DANGER) { a.cancel(row.id) })
      },
      onLongClick = { ctx.onMenu(row.id) },
      onClick = { a.open(row.id) },
    )
    //  «سرور نپذیرفت: …» — فقط فاکتوری که همین دستگاه ساخت و سرور رد کرد (`RejectedMark.kt`)
    RejectedLine(s.id)
  }
}

/** برگهٔ همهٔ کارهای یک فاکتور — نگه داشتنِ ردیف (کلیکِ راست در کامپیوتر) */
@Composable
internal fun SaleActionsSheet(row: HistoryRow, canCancel: Boolean, returnsV2: Boolean, actions: HistoryActions, askCancel: Boolean = false, onClose: () -> Unit) {
  val chosen = remember { booleanArrayOf(false) }
  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = "${row.number} · ${row.name}", detents = false) { closer ->
    fun pick(go: () -> Unit) { chosen[0] = true; closer.close { onClose(); go() } }
    V3ListRow(StrSale.viewInvoice, icon = Icons.AutoMirrored.Outlined.ReceiptLong, hue = V3Hue.BLUE, onClick = { pick { actions.open(row.id) } })
    if (row.canReturn) V3ListRow(
      if (returnsV2) StrSale.returnOrExchange else StrSale.returnWord,
      icon = Icons.AutoMirrored.Outlined.AssignmentReturn, hue = V3Hue.TEAL, onClick = { pick { actions.ret(row.id) } },
    )
    row.returnDocs.forEach { doc ->
      V3ListRow(StrSale.returnReceiptOf(ir.vil3ntec.tohid.data.ReturnFlow.label(doc)), icon = Icons.AutoMirrored.Outlined.ReceiptLong, onClick = { pick { actions.returnReceipt(doc) } })
    }
    //  لغو و «اصلاح» (که خودش لغو است) فقط با صاحب و مدیر — سرور هم لغوِ شاگرد را رد می‌کند؛ STF-02: نقشِ «نیازمندِ تأیید» روی
    //  Sync v3 فقط «لغو» با برگهٔ مدیر ([askCancel])
    if (row.canCancel && canCancel) V3ListRow(StrSale.fixInvoice, icon = Icons.Outlined.PointOfSale, hue = V3Hue.AMBER, onClick = { pick { actions.fix(row.id) } })
    if (row.canCancel && (canCancel || askCancel)) {
      V3ListRow(StrSale.cancelSale, icon = Icons.Outlined.Block, danger = true, chevron = false, onClick = { pick { actions.cancel(row.id) } })
    }
  }
}

/** فیلتر: نوعِ پرداخت/لغوشده و «کجا» (دکان یا اینترنتی) — کنار، در برگه؛ جمع‌ها عوض نمی‌شوند */
@Composable
internal fun FilterSheet(query: HistoryQuery, onClose: () -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onClose, title = StrHistory.filter, detents = false) { closer ->
    Text(StrHistory.payment, style = V3Type.subhead, color = c.inkMuted, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp))
    V3ChipRow {
      HistoryFilter.entries.forEach { f -> V3Chip(filterName(f), query.filter == f, { query.filter = f }) }
    }
    Text(StrHistory.where, style = V3Type.subhead, color = c.inkMuted, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
    V3ChipRow {
      HistoryChannel.entries.forEach { ch -> V3Chip(channelName(ch), query.channel == ch, { query.channel = ch }) }
    }
    Text(StrHistory.filterNote, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
    Spacer(Modifier.height(14.dp))
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (query.filtered) {
        V3Button(StrHistory.clearFilters, { query.filter = HistoryFilter.ALL; query.channel = HistoryChannel.ALL }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
      }
      V3Button(ir.vil3ntec.tohid.core.i18n.Str.close, { closer.close(onClose) }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
  }
}

/** نوارِ چاپِ تاریخچه: پیشرفت و «لغوِ چاپ»؛ پایان ⇒ پیام و بستن */
@Composable
internal fun PrintBannerCard(b: PrintBanner) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  Column(
    Modifier.padding(top = 6.dp, bottom = 4.dp).fillMaxWidth().clip(shape).background(c.card).border(1.dp, c.hairline, shape)
      .padding(14.dp).semantics { contentDescription = b.text },
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.Print, contentDescription = null, tint = c.icon, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
      Text(b.text, style = V3Type.callout, color = c.ink, modifier = Modifier.weight(1f))
      if (!b.running) Icon(
        Icons.Outlined.Close, contentDescription = ir.vil3ntec.tohid.core.i18n.Str.close, tint = c.icon,
        modifier = Modifier.size(32.dp).v3Pressable(label = ir.vil3ntec.tohid.core.i18n.Str.close, onClick = b.onDismiss).padding(6.dp),
      )
    }
    if (b.running) {
      Spacer(Modifier.height(10.dp))
      LinearProgressIndicator(Modifier.fillMaxWidth())
      Spacer(Modifier.height(10.dp))
      Text(StrHistory.cancelNote, style = V3Type.footnote, color = c.inkMuted)
      Spacer(Modifier.height(8.dp))
      V3Button(StrHistory.cancelPrint, b.onCancel, kind = V3ButtonKind.DANGER, icon = Icons.Outlined.Close, height = 40.dp)
    }
  }
}

@Composable
internal fun EmptyLine(text: String) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  Box(Modifier.fillMaxWidth().clip(shape).background(c.card).border(1.dp, c.hairline, shape).padding(18.dp), contentAlignment = Alignment.Center) {
    Text(text, style = V3Type.callout, color = c.inkMuted)
  }
}

@Composable
internal fun MoreButton(text: String, onClick: () -> Unit) {
  Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
    V3Button(text, onClick, kind = V3ButtonKind.NEUTRAL, height = 40.dp)
  }
}

private fun payStatus(row: HistoryRow): V3Status = when {
  row.state == SaleState.CANCELLED -> V3Status.CANCELLED
  row.state == SaleState.ONLINE -> V3Status.IN_DELIVERY
  row.credit -> V3Status.WARNING
  else -> V3Status.DELIVERED
}

private fun groupShape(first: Boolean, last: Boolean) = RoundedCornerShape(
  topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
  bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp,
)
