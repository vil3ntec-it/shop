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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    if (billNo.isBlank()) { error = "شمارهٔ فاکتورِ تأمین‌کننده را بنویسید"; return }
    val lines = PurchaseOrderBook.billLines(rows) ?: run { error = "مقدار یا مبلغِ یک ردیف درست نیست"; return }
    if (lines.isEmpty()) { error = "دست‌کم یک ردیف در فاکتور لازم است"; return }
    busy = true; error = null
    scope.launch {
      val r = repo.bill(order.id, billId, billNo.trim(), billDate.trim().ifEmpty { null }, note.trim(), lines)
      busy = false
      if (r.valueOrNull() == null) { error = r.errorMessage(); return@launch }
      toast("فاکتورِ «${billNo.trim()}» ثبت شد")
      onDone()
    }
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    Text("فاکتورِ تأمین‌کننده — سفارشِ #${order.no} · ${order.supplierName}", style = MaterialTheme.typography.titleSmall, color = c.text)
    Text("همان چیزی را بنویسید که روی فاکتورِ کاغذیِ تأمین‌کننده است؛ برنامه آن را با سفارش و رسیدها تطبیق می‌دهد.",
      style = MaterialTheme.typography.labelSmall, color = c.muted)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(value = billNo, onValueChange = { billNo = it.take(60); error = null }, label = { Text("شمارهٔ فاکتور") },
        singleLine = true, modifier = Modifier.weight(1f))
      OutlinedTextField(value = billDate, onValueChange = { billDate = it.take(10); error = null }, label = { Text("تاریخ (YYYY-MM-DD)") },
        singleLine = true, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    rows.forEachIndexed { i, r ->
      Column(Modifier.padding(vertical = 4.dp)) {
        Text(r.name.ifBlank { names[r.productId] ?: r.productId }, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField(r.qty, { v -> rows = rows.toMutableList().also { it[i] = r.copy(qty = v) }; error = null }, "مقدار (${r.unit})", Modifier.weight(1f))
          NumberField(r.total, { v -> rows = rows.toMutableList().also { it[i] = r.copy(total = v) }; error = null }, "جمعِ ردیف (؋)", Modifier.weight(1f))
        }
      }
    }
    OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("یادداشت") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(10.dp))
    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حالِ ثبت…" else "ثبتِ فاکتور") }
  }
}
