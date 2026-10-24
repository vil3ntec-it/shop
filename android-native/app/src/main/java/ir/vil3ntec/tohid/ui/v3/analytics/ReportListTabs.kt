package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.ExpiryEngine
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Stat
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Grow
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** فهرستِ بلند: هر ردیف کارتِ مرزدارِ خودش (تنبل، ورودِ پلکانیِ بارِ اول) */
private fun <T> LazyListScope.rowCards(stagger: V3StaggerState, rows: List<T>, key: (T) -> Any, row: @Composable (T) -> Unit) =
  itemsIndexed(rows, key = { _, r -> key(r) }) { i, r ->
    Box(Modifier.v3AnimateItem(this).padding(top = 8.dp).v3Stagger(stagger, key(r), i)) { V3Card(padding = PaddingValues(0.dp)) { row(r) } }
  }

/** «قرض‌داران»: جمعِ طلب و هر نفر با «فروش نسیه — پرداخت‌شده» و مانده ⇐ پروندهٔ همان نفر */
@Composable
internal fun DebtorsTab(d: ShopData, spec: ReportListSpec) {
  val c = PhoneV3.colors
  val rows = remember(d) { ReportEngine.debtors(d) }
  val stagger = rememberV3Stagger()
  ReportList(spec) {
    item(key = "hero") {
      Box(spec.appear("hero", 0).padding(top = 12.dp, bottom = 4.dp)) {
        HeroFigure(StrReports.receivable, rows.sumOf { it.remaining.coerceAtLeast(0.0) }, StrReports.people(rows.count { it.remaining > 0 }))
      }
    }
    if (rows.isEmpty()) {
      item(key = "empty") { V3EmptyState(Icons.Outlined.PeopleOutline, StrReports.noDebtors, action = StrReports.addDebtor, onAction = { spec.onOpen(MoneyPaths.NEW_DEBTOR) }) }
      return@ReportList
    }
    rowCards(stagger, rows, { it.debtor.id }) { r ->
      V3ListRow(
        r.debtor.name,
        subtitle = StrReports.creditVsPaid(afn(r.given), afn(r.received)),
        icon = Icons.Outlined.Person, hue = V3Hue.BLUE,
        value = when {
          r.remaining > 0 -> afn(r.remaining)
          r.remaining < 0 -> StrReports.prepaid(money(-r.remaining))
          else -> StrReports.settled
        },
        valueColor = when {
          r.remaining > 0 -> c.waiting
          r.remaining < 0 -> c.positive
          else -> c.inkMuted
        },
        onClick = { spec.onOpen(MoneyPaths.debtor(r.debtor.id)) },
      )
    }
    item(key = "end") { Spacer(Modifier.height(8.dp)) }
  }
}

/** «خرید»: جمع و بدهی، سهمِ هر تأمین‌کننده (میلهٔ سری ۳ با راهنما) و خریدهای بازه */
@Composable
internal fun PurchasesTab(d: ShopData, from: String, to: String, spec: ReportListSpec) {
  val c = PhoneV3.colors
  val r = remember(d, from, to) { ReportEngine.purchases(d, from, to) }
  val names = remember(d) { d.products.associate { it.id to it.name } }
  val stagger = rememberV3Stagger(from, to)
  ReportList(spec) {
    item(key = "kpi") {
      Row(spec.appear("kpi", 0).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3Stat(StrReports.purchasesTotal, r.total, ::afn, Modifier.weight(1f), hue = V3Hue.PURPLE, caption = Str.invoicesCount(r.count))
        V3Stat(StrReports.supplierDebt, r.debt, ::afn, Modifier.weight(1f), hue = V3Hue.ORANGE, onClick = { spec.onOpen(MoneyPaths.PURCHASING) })
      }
    }
    if (r.rows.isEmpty()) {
      item(key = "empty") { V3EmptyState(Icons.Outlined.LocalShipping, StrReports.noPurchases, action = StrReports.newPurchase, onAction = { spec.onOpen(MoneyPaths.newPurchase()) }) }
      return@ReportList
    }
    item(key = "share") {
      val max = r.bySupplier.maxOfOrNull { it.second } ?: 0.0
      val grow = rememberV3Grow(r.bySupplier)
      val color = chartColor(c, ChartSeries.COSTS)
      V3Section(StrReports.bySupplier, spec.appear("share", 1)) {
        Legend(listOf(color to StrReports.purchasesInRange), Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp))
        r.bySupplier.forEachIndexed { i, (name, total) ->
          val id = d.suppliers.firstOrNull { it.name == name }?.id
          Column(
            Modifier.fillMaxWidth()
              .then(if (id != null) Modifier.v3Pressable(label = name) { spec.onOpen(MoneyPaths.supplier(id)) } else Modifier)
              .padding(horizontal = 16.dp, vertical = 10.dp),
          ) {
            Row {
              Text(name, style = V3Type.callout, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
              Text(afn(total), style = V3Type.subhead, color = c.ink)
            }
            Spacer(Modifier.height(6.dp))
            InlineBar(ReportsModel.fraction(total, max), color, grow, Modifier.fillMaxWidth(), height = 8.dp)
          }
        }
      }
    }
    item(key = "rows-title") { SectionTitle(StrReports.purchaseRows) }
    rowCards(stagger, r.rows, { it.id }) { p ->
      val supplier = d.suppliers.firstOrNull { it.id == p.supplierId }
      V3ListRow(
        names[p.productId] ?: StrReports.unknownProduct,
        subtitle = listOfNotNull(uiDate(p.date), "${qty(p.quantity)} ${p.unit}", supplier?.name).joinToString(" · "),
        icon = Icons.Outlined.LocalShipping, hue = V3Hue.PURPLE,
        value = afn(p.totalAmount), valueColor = c.ink,
        onClick = { spec.onOpen(supplier?.let { MoneyPaths.supplier(it.id) } ?: MoneyPaths.PURCHASING) },
      )
    }
    item(key = "end") { Spacer(Modifier.height(8.dp)) }
  }
}

/** «انقضا»: منقضی، تا N روز و بعدتر — هر محموله ⇐ انبارِ همان کالا */
@Composable
internal fun ExpiryTab(d: ShopData, today: String, spec: ReportListSpec) {
  val c = PhoneV3.colors
  val lots = remember(d, today) { ExpiryEngine.lots(d, today) }
  val near = ExpiryEngine.NEAR_DAYS
  val groups = listOf(
    StrReports.expired to lots.filter { it.expired },
    StrReports.withinDays(near) to lots.filter { !it.expired && it.daysLeft <= near },
    StrReports.laterThan(near) to lots.filter { !it.expired && it.daysLeft > near },
  )
  ReportList(spec) {
    item(key = "kpi") {
      Row(spec.appear("kpi", 0).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val expired = groups[0].second.size
        V3Stat(StrReports.expired, expired.toDouble(), { it.toLong().toString() }, Modifier.weight(1f), valueColor = if (expired > 0) c.negative else null)
        V3Stat(StrReports.withinDays(near), groups[1].second.size.toDouble(), { it.toLong().toString() }, Modifier.weight(1f), hue = V3Hue.ORANGE)
      }
    }
    if (lots.isEmpty()) {
      item(key = "empty") { V3EmptyState(Icons.Outlined.EventBusy, StrReports.noLots, action = StrReports.stockEntry, onAction = { spec.onOpen(StockPaths.entry()) }) }
      return@ReportList
    }
    groups.filter { it.second.isNotEmpty() }.forEachIndexed { gi, (title, rows) ->
      item(key = "g-$title") {
        V3Section(title, spec.appear("g-$title", gi + 1)) {
          rows.forEachIndexed { i, l ->
            if (i > 0) V3Divider(start = 64.dp)
            V3ListRow(
              l.product.name,
              subtitle = listOfNotNull(uiDate(l.entry.expiryDate), l.entry.batch.takeIf { it.isNotBlank() }?.let { StrReports.batch(it) }, "${qty(l.remaining)} ${l.product.unit}").joinToString(" · "),
              icon = Icons.Outlined.EventBusy, hue = if (l.expired) null else V3Hue.ORANGE, danger = l.expired,
              trailing = {
                when {
                  l.expired -> V3StatusPill(V3Status.CANCELLED, StrReports.expired)
                  l.daysLeft <= near -> V3StatusPill(V3Status.AWAITING, StrReports.daysLeft(l.daysLeft))
                  else -> V3StatusPill(V3Status.INACTIVE, StrReports.daysLeft(l.daysLeft))
                }
              },
              onClick = { spec.onOpen(StockPaths.item(l.product.id)) },
            )
          }
        }
      }
    }
    item(key = "end") { Spacer(Modifier.height(8.dp)) }
  }
}

/** «گردش موجودی»: حرکت‌های `ReportEngine.stockLedger` به تفکیکِ کالا (ورود، خروج، خالص) ⇐ حرکت‌های همان کالا */
@Composable
internal fun StockTab(d: ShopData, spec: ReportListSpec) {
  val c = PhoneV3.colors
  val flows = remember(d) { ReportsModel.stockFlow(ReportEngine.stockLedger(d, null, Int.MAX_VALUE), d.products) }
  val stagger = rememberV3Stagger()
  ReportList(spec) {
    if (flows.isEmpty()) {
      item(key = "empty") { V3EmptyState(Icons.Outlined.Inventory2, StrReports.noMovements, action = StrReports.stockEntry, onAction = { spec.onOpen(StockPaths.entry()) }) }
      return@ReportList
    }
    item(key = "pad") { Spacer(Modifier.height(4.dp)) }
    rowCards(stagger, flows, { it.productId }) { f ->
      val unit = f.product?.unit.orEmpty()
      V3ListRow(
        f.product?.name ?: StrReports.unknownProduct,
        subtitle = StrReports.flowLine("+${qty(f.inQty)}", "−${qty(f.outQty)}") + " · " + StrReports.moves(f.moves),
        icon = Icons.Outlined.Inventory2, hue = V3Hue.TEAL,
        value = (if (f.net < 0) "−" else "+") + qty(kotlin.math.abs(f.net)) + " " + unit,
        valueColor = if (f.net > 0) c.positive else c.ink,
        onClick = f.product?.let { p -> { spec.onOpen(StockPaths.movements(p.id)) } },
      )
    }
    item(key = "end") { Spacer(Modifier.height(8.dp)) }
  }
}

/** سرخطِ کوچکِ میانِ فهرست (همان شکلِ سرخطِ `V3Section`) */
@Composable
internal fun SectionTitle(text: String) {
  Text(text, style = V3Type.subhead, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 0.dp))
}
