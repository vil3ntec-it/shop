package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.Str

/*
 *  پنلِ سفارش (VISUAL-DIRECTION §۲، سمتِ چپ در RTL): سربرگ با شمارِ اقلام،
 *  مشتری، اقلام یا حالتِ خالی، خلاصهٔ پرداخت، شبکهٔ روش‌ها و «ثبتِ فروش».
 *  این‌ها فقط نمایش‌اند؛ هر کار از صفحه (`DeskSaleScreen`) می‌آید.
 */

/** قابِ پنل — هر بخش یک slot است تا صفحه کار و کلیدش را خودش نگه دارد */
@Composable
fun OrderPanel(
  itemCount: Int,
  width: Dp,
  customer: @Composable () -> Unit,
  items: @Composable ColumnScope.() -> Unit,
  summary: @Composable () -> Unit,
  methods: @Composable () -> Unit,
  actions: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Shop.colors.surfaceSolid,
    shape = ShopShape.cardLarge,
    border = BorderStroke(1.dp, Shop.colors.border),
    modifier = modifier.width(width).fillMaxHeight(),
  ) {
    Column(Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(StrSale.orderList, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        CountPill(StrSale.linesCount(itemCount))
      }
      Spacer(Modifier.height(10.dp))
      customer()
      Spacer(Modifier.height(10.dp))
      HorizontalDivider(color = Shop.colors.border)
      Column(Modifier.weight(1f).fillMaxWidth()) { items() }
      HorizontalDivider(color = Shop.colors.border)
      Spacer(Modifier.height(8.dp))
      summary()
      Spacer(Modifier.height(10.dp))
      methods()
      Spacer(Modifier.height(12.dp))
      actions()
    }
  }
}

@Composable
private fun CountPill(text: String) {
  Text(
    text,
    Modifier
      .border(1.dp, Shop.colors.border, ShopShape.chip)
      .padding(horizontal = 10.dp, vertical = 3.dp),
    style = MaterialTheme.typography.labelSmall,
    color = Shop.colors.muted,
  )
}

/** ردیفِ مشتری: مشتریِ گذری یا قرض‌دارِ انتخاب‌شده، با F2 و «برداشتن» */
@Composable
fun CustomerRow(
  name: String?,
  detail: String?,
  pickHint: String,
  target: Dp,
  onPick: () -> Unit,
  onRemove: () -> Unit,
) {
  Text(StrSale.customerKey(pickHint), style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  Spacer(Modifier.height(4.dp))
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f).heightIn(min = target)) {
      Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(name ?: Str.walkIn, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (name != null) TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = target)) { Text(StrSale.remove) }
  }
  detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted) }
}

/** یک ردیفِ سبد برای پنل */
class OrderLineView(
  val id: String,
  val name: String,
  val unit: String,
  val quantity: Double,
  val price: Double,
  /** قیمتِ دستی (F3) — به رنگِ اصلی */
  val customPrice: Boolean,
)

/** اقلامِ سبد یا حالتِ خالی با راهنما */
@Composable
fun OrderLines(
  lines: List<OrderLineView>,
  selected: String?,
  target: Dp,
  state: LazyListState,
  onSelect: (String) -> Unit,
  onEdit: (String) -> Unit,
  onMinus: (String) -> Unit,
  onPlus: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (lines.isEmpty()) {
    Column(
      modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(Icons.Filled.ShoppingBasket, contentDescription = null, tint = Shop.colors.muted2, modifier = Modifier.size(28.dp))
      Spacer(Modifier.height(6.dp))
      Text(Str.cartEmpty, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      Spacer(Modifier.height(4.dp))
      Text(
        StrSale.scanHint,
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, textAlign = TextAlign.Center,
      )
    }
    return
  }
  LazyColumn(state = state, modifier = modifier.fillMaxWidth()) {
    itemsIndexed(lines, key = { _, l -> l.id }) { i, line ->
      val on = line.id == selected
      Row(
        Modifier
          .fillMaxWidth()
          .background(if (on) Shop.colors.primaryTint else Color.Transparent)
          .semantics { this.selected = on }
          .clickable(onClickLabel = StrSale.selectRow) { onSelect(line.id) }
          .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("${i + 1}", Modifier.width(20.dp), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        Column(Modifier.weight(1f)) {
          Text(line.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            "${money(line.price)} ؋",
            Modifier
              .clickable(onClickLabel = StrSale.editQtyPrice) { onEdit(line.id) }
              .semantics { role = Role.Button },
            style = MaterialTheme.typography.labelSmall,
            color = if (line.customPrice) Shop.colors.primary else Shop.colors.muted,
          )
        }
        QtyStepper(line.quantity, line.name, onMinus = { onMinus(line.id) }, onPlus = { onPlus(line.id) }, target = target, unit = line.unit)
        Text(
          money(SalesEngine.lineTotal(line.price, line.quantity)),
          Modifier.width(84.dp),
          style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text,
          textAlign = TextAlign.End, maxLines = 1,
        )
      }
      if (i < lines.size - 1) HorizontalDivider(color = Shop.colors.border.copy(alpha = 0.6f))
    }
  }
}

/**
 *  خلاصهٔ پرداخت: ردیف‌های `PosSummary.rows` (جمع و تخفیف)، جای کادرِ تخفیف
 *  برای کسی که مجوز دارد، و «مبلغِ قابلِ پرداخت» درشت.
 */
@Composable
fun PaymentSummary(
  rows: List<PosSummaryRow>,
  payable: Double,
  extra: @Composable () -> Unit = {},
) {
  Text(StrSale.paymentSummary, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  Spacer(Modifier.height(4.dp))
  rows.forEach { r ->
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(r.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (r.negative) Shop.colors.danger else Shop.colors.muted)
      Text(
        "${if (r.negative) "−" else ""}${money(r.amount)} ؋",
        style = MaterialTheme.typography.bodySmall, color = if (r.negative) Shop.colors.danger else Shop.colors.text,
      )
    }
  }
  extra()
  Spacer(Modifier.height(6.dp))
  Row(
    Modifier
      .fillMaxWidth()
      .background(Shop.colors.surface2, ShopShape.card)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(StrSale.payable, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
    Text("${money(payable)} ؋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
  }
}

/** شبکهٔ روشِ پرداخت — فقط `PosPayments.configured()`؛ روشِ فعال با رنگ و حاشیه */
@Composable
fun PaymentMethodGrid(
  methods: List<PosPayMethod>,
  selected: SalesEngine.Payment,
  target: Dp,
  onSelect: (PosPayMethod) -> Unit,
) {
  Text(StrSale.payMethod, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  Spacer(Modifier.height(4.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    methods.forEach { m ->
      val on = m.payment == selected
      Surface(
        selected = on,
        onClick = { onSelect(m) },
        shape = ShopShape.button,
        color = if (on) Shop.colors.primaryTint else Shop.colors.surfaceSolid,
        contentColor = if (on) Shop.colors.primaryDark else Shop.colors.text,
        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) Shop.colors.primary else Shop.colors.fieldBorder),
        modifier = Modifier.weight(1f).heightIn(min = maxOf(target, 44.dp)).semantics { role = Role.RadioButton },
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
          Text(m.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
        }
      }
    }
  }
}

/** «چاپِ آخر» (ثانویه) و «ثبتِ فروش» (اصلی، درشت) — دکمهٔ ثبت در حینِ ثبت خاموش است (POS-14) */
@Composable
fun OrderActions(
  postHint: String,
  printHint: String,
  canPost: Boolean,
  posting: Boolean,
  canPrint: Boolean,
  onPost: () -> Unit,
  onPrint: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = onPrint, enabled = canPrint, modifier = Modifier.weight(1f).height(52.dp)) {
      Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(StrSale.printLast(printHint), maxLines = 1)
    }
    Button(onClick = onPost, enabled = canPost && !posting, modifier = Modifier.weight(1.6f).height(52.dp)) {
      Text(if (posting) StrSale.posting else StrSale.recordSaleKey(postHint), style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
  }
}
