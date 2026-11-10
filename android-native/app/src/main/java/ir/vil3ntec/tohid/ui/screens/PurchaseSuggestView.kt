package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.purchase.DemandForecast
import ir.vil3ntec.tohid.core.purchase.ReorderCore
import ir.vil3ntec.tohid.core.purchase.SmartDemand
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.PurchaseSuggest
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.PurchaseOrderRepository
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  «پیشنهادِ سفارش» در «سفارش‌های خرید» — INV-13 (مقدار با دلیل و درجهٔ اطمینان از فروش، زمانِ تأمین
 *  و فصل) و INV-14 («این موجودی چند روز کافی است» و سفارش با یک کلید). §۶.۵، S23.
 *
 *  حساب در `core/purchase/DemandForecast` و ورودی‌اش در `data/PurchaseSuggest`. «ساختِ پیش‌نویس» برای هر
 *  تأمین‌کننده یک کلید است و فقط پیش‌نویس می‌سازد؛ کلیدِ یکتای هر تأمین‌کننده تا بسته شدنِ همین
 *  صفحه ثابت است، پس دو بار زدن یا قطعِ نت دو سفارش نمی‌سازد. همان پرچمِ `shop.purchase_orders`.
 *  AI-07: با تنظیمِ خاموشِ «پیش‌بینیِ تقاضا» (`SmartForecastPref`) روش، پیش‌بینیِ هفت روزِ آینده و نوسان هم دیده می‌شود؛
 *  خاموش ⇒ همان صفحهٔ پیشین.
 */
@Composable
internal fun PoSuggest(
  d: ShopData,
  repo: PurchaseOrderRepository,
  perms: PoPermsDto,
  toast: (String) -> Unit,
  onCreated: (PurchaseOrderDto) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var orders by remember { mutableStateOf<List<PurchaseOrderDto>?>(null) }
  //  PUR-03: «در راه» از همهٔ سفارش‌های باز (نه فقط ۵۰ فرستادهٔ تازه‌تر)
  LaunchedEffect(Unit) { orders = repo.planningOrders() }
  var onlyDue by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  val keys = remember { HashMap<String, String>() }
  val smart = rememberSmartForecastOn()
  val rows = remember(d, orders, smart) { orders?.let { PurchaseSuggest.rows(d, it, todayIso(), smart) } }

  fun oneKey(supplierId: String) {
    val supplier = d.suppliers.find { it.id == supplierId } ?: return
    val lines = PurchaseSuggest.draftLines(rows.orEmpty(), supplierId)
    if (lines.isEmpty()) { toast(StrPurchasePages.noNeedFromSupplier); return }
    PurchaseSuggest.check(supplierId, lines)?.let { error = StrPurchasePages.suggestInvalid(it.code); return }
    val key = keys.getOrPut(supplierId) { newId() }
    busy = supplierId; error = null
    scope.launch {
      val r = repo.create(key, PurchaseOrderRepository.Draft(supplier.id, supplier.name, lines, null, "از «پیشنهادِ سفارش»")) // داده: یادداشتی که با پیش‌نویسِ سفارش ذخیره می‌شود
      busy = null
      r.valueOrNull()?.let { toast(StrPurchasePages.draftCreatedNotSent(it.no)); onCreated(it) }
        ?: run { error = r.errorMessage() }
    }
  }

  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    FilterChip(selected = onlyDue, onClick = { onlyDue = true }, label = { Text(StrReach.tabReorder) }, shape = Shape.chip)
    FilterChip(selected = !onlyDue, onClick = { onlyDue = false }, label = { Text(StrPurchasePages.allDaysLeft) }, shape = Shape.chip)
  }
  Text(
    StrPurchasePages.suggestNote1 +
      StrPurchasePages.suggestNote2 +
      (if (smart) StrPurchasePages.suggestNoteSmart else ""),
    style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(vertical = 6.dp),
  )
  error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    val all = rows
    if (all == null) { Text(StrReports.loading, color = c.muted, style = MaterialTheme.typography.bodySmall); return@Column }
    val shown = if (onlyDue) all.filter { it.due } else all
    if (shown.isEmpty()) { EmptyNote(if (onlyDue) StrPurchasePages.nothingDueToday else StrPurchasePages.noActiveItems); return@Column }
    for ((sid, group) in shown.groupBy { it.supplierId }) {
      val name = sid?.let { s -> d.suppliers.find { it.id == s }?.name } ?: StrPurchasePages.noSupplier
      val due = group.count { it.due }
      Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sid != null && perms.manage && due > 0) {
          Button(onClick = { oneKey(sid) }, enabled = busy == null) {
            Text(if (busy == sid) StrSettings.buildingDots else StrPurchasePages.createDraftN(due))
          }
        }
      }
      if (sid == null) Text(StrPurchasePages.noSupplierNote, style = MaterialTheme.typography.labelSmall, color = c.muted)
      group.forEach { r -> SuggestRow(r) }
      HorizontalDivider(color = c.border)
    }
  }
}

@Composable
private fun SuggestRow(r: PurchaseSuggest.Row) {
  val c = Shop.colors
  val f = r.forecast
  Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(r.product.name, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
      StatusPill(DemandForecast.confidenceText(f.confidence), confidenceTone(f.confidence))
    }
    Text(
      StrPurchasePages.enoughFor(DemandForecast.coverText(f, LocaleFormat.DIGITS_LATN)) +
        (if (r.due) StrPurchasePages.suggestQty(qty(PurchaseOrderBook.units(f.qtyMilli)), r.product.unit) else ""),
      style = MaterialTheme.typography.bodySmall, color = if (r.due) c.text else c.muted,
    )
    Text(DemandForecast.why(f, LocaleFormat.DIGITS_LATN), style = MaterialTheme.typography.labelSmall, color = c.muted)
    f.smart?.let { s ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(SmartDemand.methodText(s.method), PillTone.INFO)
        Spacer(Modifier.width(6.dp))
        Text(StrPurchasePages.next7Days(SmartDemand.nextDaysText(s, 7, LocaleFormat.DIGITS_LATN)), style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
    }
  }
}

private fun confidenceTone(x: ReorderCore.Confidence): PillTone = when (x) {
  ReorderCore.Confidence.HIGH -> PillTone.SUCCESS
  ReorderCore.Confidence.MEDIUM -> PillTone.INFO
  ReorderCore.Confidence.LOW -> PillTone.WARNING
}
