package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Stat
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Grow
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths

/** «سود و فروش»: عددِ درشتِ سود/ضرر، صورتِ سود و زیان، فروشِ هر کاربر و پانویسِ امروز */
@Composable
internal fun ProfitTab(d: ShopData, from: String, to: String, spec: ReportListSpec) {
  val r = remember(d, from, to) { ReportEngine.sales(d, from, to) }
  val sellers = remember(d, from, to) { ReportEngine.bySeller(d, from, to) }
  val loss = r.netProfit < 0
  val empty = r.count == 0 && r.expenses == 0.0 && r.returnAmount == 0.0
  ReportList(spec) {
    item(key = "hero") {
      Box(spec.appear("hero", 0).padding(top = 12.dp)) {
        //  ضرر به نامِ خودش، نه «سودِ منفی» — کلمهٔ درست خودش هشدار است
        HeroFigure(if (loss) StrReports.netLoss else StrReports.netProfit, kotlin.math.abs(r.netProfit), Str.invoicesCount(r.count), negative = loss)
      }
    }
    if (empty) {
      item(key = "empty") {
        V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, StrReports.noSalesInRange, action = StrReports.newSale, onAction = { spec.onOpen(SalesPaths.SALE) })
      }
      return@ReportList
    }
    item(key = "statement") {
      V3Section(StrReports.statement, spec.appear("statement", 1)) {
        StatementRow(StrReports.itemsTotal, afn(r.gross))
        StatementRow(StrReports.discount, afnMinus(r.discount))
        StatementRow(StrReports.netSales, afn(r.net), strong = true)
        V3Divider(start = 16.dp)
        StatementRow(StrReports.cogs, afnMinus(r.cogs))
        if (r.returnAmount > 0) StatementRow(StrReports.returns, afnMinus(r.returnAmount))
        StatementRow(if (r.grossProfit < 0) StrReports.grossLoss else StrReports.grossProfit, afn(r.grossProfit), strong = true, negative = r.grossProfit < 0)
        V3Divider(start = 16.dp)
        StatementRow(StrReports.expenses, afnMinus(r.expenses))
        StatementRow(if (loss) StrReports.netLoss else StrReports.netProfit, afn(r.netProfit), strong = true, negative = loss)
      }
    }
    if (sellers.size > 1) item(key = "sellers") {
      V3Section(StrReports.bySeller, spec.appear("sellers", 2)) {
        sellers.forEachIndexed { i, s ->
          if (i > 0) V3Divider(start = 64.dp)
          V3ListRow(s.seller, subtitle = Str.invoicesCount(s.count), icon = Icons.Outlined.Person, hue = V3Hue.BRAND, value = afn(s.net), valueColor = PhoneV3.colors.ink)
        }
      }
    }
    item(key = "foot") {
      Text(StrReports.profitFootnote, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 8.dp))
    }
  }
}

/** یک سطرِ صورتِ حساب: مقدار در پایان؛ سطرِ جمع درشت، منفی با «−» و رنگِ ضرر */
@Composable
private fun StatementRow(label: String, value: String, strong: Boolean = false, negative: Boolean = false) {
  val c = PhoneV3.colors
  V3ListRow(label, tile = false, chevron = false, minHeight = 46.dp, trailing = {
    Text(value, style = if (strong) V3Type.headline else V3Type.callout, color = if (negative) c.negative else if (strong) c.ink else c.inkMuted, maxLines = 1)
  })
}

/**
 *  «محصولات»: ارزشِ موجودی (پولی ⇐ «—» برای شاگرد)، شمارِ تمام‌شده/کم (⇐ فهرستِ کالا با همان
 *  فیلتر)، چهار رتبه با میلهٔ نازکِ سری ۱، و «نیاز به توجه».
 */
@Composable
internal fun ProductsTab(d: ShopData, canSeeMoney: Boolean, spec: ReportListSpec) {
  val c = PhoneV3.colors
  val r = remember(d) { ReportEngine.products(d) }
  ReportList(spec) {
    item(key = "kpi") {
      Column(spec.appear("kpi", 0).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canSeeMoney) V3Stat(StrReports.inventoryValue, r.inventoryValue, ::afn, Modifier.fillMaxWidth(), hue = V3Hue.BRAND)
        else V3Stat(StrReports.inventoryValue, 0.0, { "—" }, Modifier.fillMaxWidth(), caption = StrReports.moneyLocked)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          V3Stat(StrReports.soldOut, r.out.size.toDouble(), { it.toLong().toString() }, Modifier.weight(1f),
            valueColor = if (r.out.isNotEmpty()) c.negative else null, onClick = { spec.onOpen(ProductsPaths.list(filter = "out")) })
          V3Stat(StrReports.lowStock, r.low.size.toDouble(), { it.toLong().toString() }, Modifier.weight(1f),
            hue = V3Hue.ORANGE, onClick = { spec.onOpen(ProductsPaths.list(filter = "low")) })
        }
      }
    }
    if (d.products.isEmpty()) {
      item(key = "empty") { V3EmptyState(Icons.Outlined.Inventory2, StrReports.noProducts, action = StrReports.addProduct, onAction = { spec.onOpen(ProductsPaths.new()) }) }
      return@ReportList
    }
    rankItem("top", 1, StrReports.topSelling, r.topSelling, { it.quantity }, { StrReports.sold(qty(it.quantity)) }, spec)
    rankItem("slow", 2, StrReports.slowest, r.slowest, { it.quantity }, { StrReports.sold(qty(it.quantity)) }, spec)
    if (canSeeMoney) {
      rankItem("profit", 3, StrReports.mostProfitable, r.mostProfitable, { it.profit }, { afn(it.profit) }, spec)
      rankItem("least", 4, StrReports.leastProfitable, r.leastProfitable, { it.profit }, { afn(it.profit) }, spec)
    } else item(key = "locked") {
      V3Message(V3Tone.INFO, StrReports.moneyLocked, Modifier.padding(top = 16.dp), body = StrReports.moneyLockedBody)
    }
    if (r.out.isNotEmpty() || r.low.isNotEmpty()) item(key = "attention") {
      V3Section(StrReports.needsAttention, spec.appear("attention", 5)) {
        val rows = r.out.map { it to true } + r.low.map { it to false }
        rows.forEachIndexed { i, (p, out) ->
          if (i > 0) V3Divider(start = 64.dp)
          V3ListRow(
            p.name, icon = Icons.Outlined.Inventory2, hue = if (out) null else V3Hue.ORANGE, danger = false,
            trailing = { V3StatusPill(if (out) V3Status.CANCELLED else V3Status.AWAITING, if (out) StrReports.soldOut else StrReports.lowStock) },
            onClick = { spec.onOpen(ProductsPaths.item(p.id)) },
          )
        }
      }
    }
    item(key = "end") { Spacer(Modifier.height(8.dp)) }
  }
}

/** کارتِ رتبه: تا پنج ردیف با شماره، مقدار و میلهٔ نازک (نسبت به بزرگ‌ترینِ همان کارت) */
private fun androidx.compose.foundation.lazy.LazyListScope.rankItem(
  key: String,
  order: Int,
  title: String,
  rows: List<ReportEngine.ProductStat>,
  measure: (ReportEngine.ProductStat) -> Double,
  label: (ReportEngine.ProductStat) -> String,
  spec: ReportListSpec,
) = item(key = key) {
  val c = PhoneV3.colors
  val top = rows.take(5)
  val max = top.maxOfOrNull { measure(it) } ?: 0.0
  val grow = rememberV3Grow(key)
  V3Section(title, spec.appear(key, order)) {
    if (top.isEmpty()) Text(StrReports.noData, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(16.dp))
    top.forEachIndexed { i, s ->
      if (i > 0) V3Divider(start = 56.dp)
      V3ListRow(
        s.product.name,
        leading = { RankBadge(i + 1) },
        trailing = {
          Column(horizontalAlignment = Alignment.End) {
            Text(label(s), style = V3Type.subhead, color = c.ink, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            InlineBar(ReportsModel.fraction(measure(s), max), chartColor(c, ChartSeries.SALES), grow, Modifier.width(72.dp))
          }
        },
        onClick = { spec.onOpen(ProductsPaths.item(s.product.id)) },
      )
    }
  }
}

@Composable
private fun RankBadge(n: Int) {
  val c = PhoneV3.colors
  Box(Modifier.size(30.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
    Text(n.toString(), style = V3Type.subhead, color = c.inkMuted)
  }
}
