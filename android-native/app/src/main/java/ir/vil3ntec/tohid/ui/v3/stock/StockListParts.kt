package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.ui.theme.V3Section
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3DataRow
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3RowMeta
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3RowTone
import ir.vil3ntec.tohid.ui.v3.V3Summary
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  خلاصهٔ انبار (۶۵s، همان `V3Summary` ِ هر بخش — وسط‌چین و تاشو، کلیدِ `stock`): ارزشِ تقریبی درشت، زیرش سه خانهٔ
 *  کوچک — قلم، کارتن، واحد — با شمارنده؛ و دو تراشهٔ هشدار (تمام‌شده/کم) که فهرست را روی همان فیلتر می‌برند.
 *  عددها همه از `WarehouseEngine.summary`؛ بسته ⇒ یک خطِ «ارزشِ تقریبی · …».
 */
@Composable
internal fun StockStatsCard(summary: WarehouseEngine.Summary, onFilter: (StockFilter) -> Unit, modifier: Modifier = Modifier) {
  V3Summary(
    label = StrStock.statValue,
    value = "${money(summary.value)} ؋",
    modifier = modifier,
    collapseKey = "stock",
    section = V3Section.STOCK,
    extra = {
      Spacer(Modifier.height(12.dp))
      Row(Modifier.fillMaxWidth()) {
        StatCell(StrStock.statItems, summary.products.toDouble(), { money(it) }, Modifier.weight(1f))
        CellDivider()
        StatCell(StrStock.statCartons, summary.cartons, { qty(it) }, Modifier.weight(1f))
        CellDivider()
        StatCell(StrStock.statUnits, summary.units, { qty(it) }, Modifier.weight(1f))
      }
      if (summary.out > 0 || summary.low > 0) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (summary.out > 0) DraftToneChip(V3Tone.ERROR, Icons.Outlined.RemoveShoppingCart, StrStock.alertOut(money(summary.out.toDouble()))) { onFilter(StockFilter.OUT) }
          if (summary.low > 0) DraftToneChip(V3Tone.WARNING, Icons.AutoMirrored.Outlined.TrendingDown, StrStock.alertLow(money(summary.low.toDouble()))) { onFilter(StockFilter.LOW) }
        }
      }
    },
  )
}

@Composable
private fun StatCell(label: String, target: Double, format: (Double) -> String, modifier: Modifier) {
  val c = PhoneV3.colors
  val shown = rememberCountUp(target)
  Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label: ${format(target)}" }, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    Text(format(shown), style = V3Type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun CellDivider() {
  Box(Modifier.padding(vertical = 4.dp).width(1.dp).height(40.dp).background(PhoneV3.colors.hairline))
}

/**
 *  تراشهٔ هشدارِ زدنی (§۹): زمینه و مرز و متنِ همان گونهٔ پیام، با آیکنِ معنادار —
 *  رنگ هرگز تنها نیست. پیشنهادِ کیت (`Draft`): «تراشهٔ گونه‌دار».
 */
@Composable
internal fun DraftToneChip(tone: V3Tone, icon: ImageVector, label: String, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.tone(c, tone)
  val shape = RoundedCornerShape(percent = 50)
  Row(
    Modifier
      .v3Pressable(label = label, onClick = onClick)
      .height(34.dp)
      .clip(shape)
      .background(pair.bg)
      .border(1.dp, pair.border, shape)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = PhoneV3Roles.toneIcon(c, tone), modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(label, style = V3Type.subhead, color = pair.fg, maxLines = 1)
  }
}

/** زیرعنوانِ ردیف: کارتن‌ها، قیمتِ فروش، «چند روز کافی است» (INV-14) و روزهای مانده تا انقضا */
internal fun stockRowSubtitle(row: StockRowUi): String = listOfNotNull(
  row.cartons.takeIf { it > 0 }?.let { StrStock.cartons(qty(it)) },
  StrStock.salePrice(money(row.product.salePrice)),
  StockListModel.coverShown(row)?.let { days ->
    if (row.coverBeyond) StrStock.coverBeyond(money(ir.vil3ntec.tohid.core.purchase.DemandForecast.MAX_COVER_DAYS.toDouble()))
    else StrStock.coverDays(money(days.toDouble()))
  },
  row.expiresInDays?.let { if (it <= 0) StrStock.expiresToday else StrStock.expiresIn(money(it.toDouble())) },
).joinToString(" · ")

/**
 *  وضعیتِ آرامِ ردیف (سادهٔ e-rows-63d، به‌جای قرص): منقضی و تمام‌شده قرمز، کم و نزدیکِ انقضا کهربایی (به همین ترتیبِ
 *  اهمیت)؛ عادی ⇒ هیچ.
 */
internal fun stockPill(row: StockRowUi): V3RowStatus? = when {
  row.expired -> V3RowStatus(StrStock.pillExpired, V3RowTone.DANGER)
  row.level == StockLevel.OUT -> V3RowStatus(StrStock.pillOut, V3RowTone.DANGER)
  row.level == StockLevel.LOW -> V3RowStatus(StrStock.pillLow, V3RowTone.ATTENTION)
  row.expiresInDays != null -> V3RowStatus(StrStock.pillExpiring, V3RowTone.ATTENTION)
  else -> null
}

/**
 *  ردیفِ انبار (کالبدِ `V3DataRow`، ۶۸c): عکس یا کاشیِ حرف، نام، خطِ دومِ یک‌خطی (کارتن، قیمت، «چند روز کافی است»، انقضا)
 *  و خطِ مبلغِ «۱۲ کیلو • کم» — موجودی به رنگِ نوشته، وضعیت واژهٔ آرام (فقط منقضی/تمام‌شده رنگِ خطر). ردیف‌ها یک فهرستِ پیوسته
 *  با خطِ مویی‌اند ([first]/[last] گوشه‌ها). کشیدن به پایان «ورود» (برند)، به آغاز «اصلاح» (خنثی).
 */
@Composable
internal fun StockRowItem(
  row: StockRowUi,
  selected: Boolean,
  onOpen: () -> Unit,
  onEntry: () -> Unit,
  onAdjust: () -> Unit,
  modifier: Modifier = Modifier,
  first: Boolean = true,
  last: Boolean = true,
) {
  val p = row.product
  val c = PhoneV3.colors
  val r = PhoneV3.groupRadius
  val shape = RoundedCornerShape(topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp, bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp)
  //  کامپیوتر (دو ستون): ردیفِ بازِ کنار با مرزِ برند — رنگ تنها نیست، مرز هم هست
  val ring = if (selected) Modifier.border(2.dp, PhoneV3Roles.focus(c), shape) else Modifier
  Column(modifier.clip(shape).background(c.card).then(ring)) {
    V3DataRow(
      title = p.name,
      secondary = stockRowSubtitle(row),
      meta = V3RowMeta(listOf(qty(row.stock), p.unit).filter { it.isNotBlank() }.joinToString(" "), stockPill(row)),
      leading = { V3ProductThumb(p.id, p.name, size = 44.dp) },
      startActions = listOf(V3SwipeAction(Icons.Outlined.MoveToInbox, StrStock.swipeEntry, V3ButtonKind.PRIMARY, onEntry)),
      endActions = listOf(V3SwipeAction(Icons.Outlined.Tune, StrStock.swipeAdjust, V3ButtonKind.NEUTRAL, onAdjust)),
      onClick = onOpen,
    )
    if (!last) V3Divider(start = V3DataRowSpec.PAD_START + 44.dp + V3DataRowSpec.GAP)
  }
}

