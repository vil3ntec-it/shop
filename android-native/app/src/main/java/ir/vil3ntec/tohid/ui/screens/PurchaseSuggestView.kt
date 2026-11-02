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
  LaunchedEffect(Unit) { orders = repo.list("sent").valueOrNull()?.orders.orEmpty() }
  var onlyDue by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  val keys = remember { HashMap<String, String>() }
  val smart = rememberSmartForecastOn()
  val rows = remember(d, orders, smart) { orders?.let { PurchaseSuggest.rows(d, it, todayIso(), smart) } }

  fun oneKey(supplierId: String) {
    val supplier = d.suppliers.find { it.id == supplierId } ?: return
    val lines = PurchaseSuggest.draftLines(rows.orEmpty(), supplierId)
    if (lines.isEmpty()) { toast("از این تأمین‌کننده کالایی سفارش نمی‌خواهد"); return }
    PurchaseSuggest.check(supplierId, lines)?.let { error = "پیشنهاد درست نیست (${it.code})"; return }
    val key = keys.getOrPut(supplierId) { newId() }
    busy = supplierId; error = null
    scope.launch {
      val r = repo.create(key, PurchaseOrderRepository.Draft(supplier.id, supplier.name, lines, null, "از «پیشنهادِ سفارش»"))
      busy = null
      r.valueOrNull()?.let { toast("پیش‌نویسِ سفارشِ #${it.no} ساخته شد — هنوز فرستاده نشده"); onCreated(it) }
        ?: run { error = r.errorMessage() }
    }
  }

  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    FilterChip(selected = onlyDue, onClick = { onlyDue = true }, label = { Text("نیاز به سفارش") }, shape = Shape.chip)
    FilterChip(selected = !onlyDue, onClick = { onlyDue = false }, label = { Text("همه: چند روز کافی است") }, shape = Shape.chip)
  }
  Text(
    "از فروشِ ۹۰ روزِ اخیر (روزِ هفته و همین روزهای پارسال)، زمانِ تأمینِ هر تأمین‌کننده و «در راه». " +
      "«ساختِ پیش‌نویس» فقط پیش‌نویس می‌سازد؛ فرستادن با خودتان است." +
      (if (smart) " پیش‌بینیِ تقاضا روشن است: پرفروش با میانگینِ متحرکِ ۲۸ روز، کم‌فروش با Croston؛ اطمینان از درازای داده و نوسان." else ""),
    style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(vertical = 6.dp),
  )
  error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    val all = rows
    if (all == null) { Text("در حالِ خواندن…", color = c.muted, style = MaterialTheme.typography.bodySmall); return@Column }
    val shown = if (onlyDue) all.filter { it.due } else all
    if (shown.isEmpty()) { EmptyNote(if (onlyDue) "امروز کالایی سفارش نمی‌خواهد." else "کالای فعالی نیست."); return@Column }
    for ((sid, group) in shown.groupBy { it.supplierId }) {
      val name = sid?.let { s -> d.suppliers.find { it.id == s }?.name } ?: "بی تأمین‌کننده"
      val due = group.count { it.due }
      Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sid != null && perms.manage && due > 0) {
          Button(onClick = { oneKey(sid) }, enabled = busy == null) {
            Text(if (busy == sid) "در حالِ ساختن…" else "ساختِ پیش‌نویس ($due کالا)")
          }
        }
      }
      if (sid == null) Text("تأمین‌کننده‌ای روی این کالاها نیست و از کسی خریده نشده‌اند؛ سفارش را دستی بسازید.", style = MaterialTheme.typography.labelSmall, color = c.muted)
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
      "کافی برای: ${DemandForecast.coverText(f, LocaleFormat.DIGITS_LATN)}" +
        (if (r.due) " · پیشنهاد: ${qty(PurchaseOrderBook.units(f.qtyMilli))} ${r.product.unit}" else ""),
      style = MaterialTheme.typography.bodySmall, color = if (r.due) c.text else c.muted,
    )
    Text(DemandForecast.why(f, LocaleFormat.DIGITS_LATN), style = MaterialTheme.typography.labelSmall, color = c.muted)
    f.smart?.let { s ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(SmartDemand.methodText(s.method), PillTone.INFO)
        Spacer(Modifier.width(6.dp))
        Text("۷ روزِ آینده: ${SmartDemand.nextDaysText(s, 7, LocaleFormat.DIGITS_LATN)}", style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
    }
  }
}

private fun confidenceTone(x: ReorderCore.Confidence): PillTone = when (x) {
  ReorderCore.Confidence.HIGH -> PillTone.SUCCESS
  ReorderCore.Confidence.MEDIUM -> PillTone.INFO
  ReorderCore.Confidence.LOW -> PillTone.WARNING
}
