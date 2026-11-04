package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeliver
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.data.PoBillDto
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.PurchaseOrderRepository
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/* ══════════════════════════ فاکتورِ تأمین‌کننده (PUR-14) ══════════════════════════ */

/**
 *  فاکتورِ کاغذیِ خودِ تأمین‌کننده: شماره (لازم)، تاریخ، و برای هر ردیفِ سفارش مقدار و جمعی که او نوشته — پیش‌پُر
 *  با پذیرفتهٔ رسیدها × فیِ سفارش. ردیفِ خالی یا صفر نمی‌رود. کلیدِ یکتا یک بار برای همین فرم: تلاشِ دوباره همان فاکتور.
 *  هیچ ردیفی در دفتر نمی‌نویسد (بدهیِ تأمین‌کننده همان راهِ رسید را دارد)؛ فقط سومین طرفِ تطبیق است.
 */
@Composable
internal fun PoBill(d: ShopData, repo: PurchaseOrderRepository, order: PurchaseOrderDto, toast: (String) -> Unit, onDone: () -> Unit) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val billId = remember { newId() }
  val names = remember(d) { d.products.associate { it.id to it.name } }
  var rows by remember(order.id) { mutableStateOf(PurchaseOrderBook.billRows(order)) }
  var billNo by remember { mutableStateOf("") }
  var billDate by remember { mutableStateOf(todayIso()) }
  var note by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  fun submit() {
    if (billNo.isBlank()) { error = StrPurchasePages.billNoNeeded; return }
    val lines = PurchaseOrderBook.billLines(rows) ?: run { error = StrPurchasePages.billRowBad; return }
    if (lines.isEmpty()) { error = StrPurchasePages.billNeedRow; return }
    busy = true; error = null
    scope.launch {
      val r = repo.bill(order.id, billId, billNo.trim(), billDate.trim().ifEmpty { null }, note.trim(), lines)
      busy = false
      if (r.valueOrNull() == null) { error = r.errorMessage(); return@launch }
      toast(StrPurchasePages.billSaved(billNo.trim()))
      onDone()
    }
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    Text(StrPurchasePages.billTitle(order.no, order.supplierName), style = MaterialTheme.typography.titleSmall, color = c.text)
    Text(StrPurchasePages.billNote,
      style = MaterialTheme.typography.labelSmall, color = c.muted)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(value = billNo, onValueChange = { billNo = it.take(60); error = null }, label = { Text(StrPurchasePages.billNo) },
        singleLine = true, modifier = Modifier.weight(1f))
      OutlinedTextField(value = billDate, onValueChange = { billDate = it.take(10); error = null }, label = { Text(StrPurchasePages.dateIso) },
        singleLine = true, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    rows.forEachIndexed { i, r ->
      Column(Modifier.padding(vertical = 4.dp)) {
        Text(r.name.ifBlank { names[r.productId] ?: r.productId }, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField(r.qty, { v -> rows = rows.toMutableList().also { it[i] = r.copy(qty = v) }; error = null }, StrPurchasePages.qtyUnit(r.unit), Modifier.weight(1f))
          NumberField(r.total, { v -> rows = rows.toMutableList().also { it[i] = r.copy(total = v) }; error = null }, StrPurchasePages.rowTotalAfn, Modifier.weight(1f))
        }
      }
    }
    OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text(StrDeliver.noteField) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(10.dp))
    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) StrSale.posting else StrPurchasePages.saveBill) }
  }
}

/**
 *  فاکتورهای ثبت‌شدهٔ تأمین‌کننده (باطل‌شده خط‌خورده با دلیل و بی اثر در تطبیق)؛ «باطل» فقط با [PurchaseOrderBook.canVoid].
 *  کامپیوتر (`PoDetail`) و گوشیِ نسخهٔ ۳ (`ui/v3/money/SupplierBillsPages.kt`) همین یکی را می‌کشند.
 */
@Composable
internal fun PoBillsList(order: PurchaseOrderDto, manage: Boolean, busy: Boolean, onVoid: (PoBillDto) -> Unit) {
  if (order.bills.isEmpty()) return
  val c = Shop.colors
  Spacer(Modifier.height(14.dp))
  Text(StrSupplierBills.title, style = MaterialTheme.typography.labelLarge, color = c.muted, fontWeight = FontWeight.SemiBold)
  order.bills.forEach { b ->
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          PurchaseOrderBook.billLine(b),
          color = if (b.active) c.text else c.muted, style = MaterialTheme.typography.bodySmall,
          textDecoration = if (b.active) null else TextDecoration.LineThrough,
        )
        b.voidReason?.takeIf { !b.active }?.let { Text(StrPurchasePages.voidedWhy(it), style = MaterialTheme.typography.labelSmall, color = c.muted) }
      }
      if (PurchaseOrderBook.canVoid(b, manage)) TextButton(onClick = { onVoid(b) }, enabled = !busy) { Text(StrPurchasePages.voidBtn, color = c.danger) }
    }
  }
}

/** سطرهای تطبیقِ سفارش ⇔ رسید ⇔ فاکتورِ تأمین‌کننده (یا فاکتورِ دفتر وقتی تأمین‌کننده فاکتوری نداده) — کامپیوتر و گوشی */
@Composable
internal fun PoMatchLines(d: ShopData, order: PurchaseOrderDto) {
  val c = Shop.colors
  val names = remember(d) { d.products.associate { it.id to it.name } }
  val m = remember(d, order) { PurchaseOrderBook.match(d, order) }
  val supplier = PurchaseOrderBook.hasSupplierBills(order)
  Spacer(Modifier.height(6.dp))
  if (m.matched) Text(PurchaseOrderBook.matchTitle(supplier), style = MaterialTheme.typography.labelSmall, color = c.success)
  else m.lines.filter { it.issues.isNotEmpty() }.forEach { ml ->
    Text(
      "${order.lines.find { it.id == ml.lineId }?.name ?: names[ml.productId] ?: ml.productId}: " +
        ml.issues.joinToString("، ") { PurchaseOrderBook.issueText(it, supplier) },
      style = MaterialTheme.typography.labelSmall, color = c.warning,
    )
  }
}

/**
 *  «باطل کردنِ فاکتور» با دلیلِ لازم — فاکتور پاک نمی‌شود (`POST …/bills/:billId/void`، تکرار بی اثر). [onConfirm] دلیلِ
 *  پیراسته را می‌گیرد؛ فرستادن و نتیجه با صدازننده (کامپیوتر و گوشی).
 */
@Composable
internal fun PoBillVoidDialog(bill: PoBillDto, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  val c = Shop.colors
  var why by remember(bill.id) { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text(StrPurchasePages.voidAsk(bill.billNo), color = c.text) },
    text = {
      Column {
        Text(StrPurchasePages.voidNote,
          style = MaterialTheme.typography.bodySmall, color = c.muted)
        OutlinedTextField(value = why, onValueChange = { why = it.take(300) }, label = { Text(StrLoyalty.adjustReason) }, singleLine = true)
      }
    },
    confirmButton = {
      TextButton(enabled = why.isNotBlank() && !busy, onClick = { onConfirm(why.trim()) }) { Text(StrPurchasePages.voidIt, color = c.danger) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel) } },
  )
}
