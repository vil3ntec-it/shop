package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  خرید و تأمین‌کننده — تکه‌های مشترکِ گوشی (`Purchasing.kt`) و کامپیوتر (`DeskPurchasing.kt`):
 *  برگشت به تأمین‌کننده با سندِ کسر (PUR-08) و یادآورِ سررسید (PUR-07). منطق همه در `PurchaseBook`.
 *  «ثبتِ» برگشت و سررسید فقط وقتی موتورِ تازهٔ موجودی و بها روشن است دیده می‌شود (تنظیمِ دکان).
 */

/**
 *  یادآورِ سررسید: فاکتورهای سررسیدگذشته و نزدیک به سررسید با ماندهٔ **واقعی** (پرداخت‌های بعدی و
 *  سندهای کسر FIFO روی قدیمی‌ترین فاکتورها، `PurchaseBook.open`)؛ چیزی نبود ⇒ هیچ. مثلِ ثبتِ
 *  سررسید، فقط وقتی موتورِ تازهٔ موجودی و بها روشن است.
 */
@Composable
fun SupplierDueReminder(d: ShopData, today: String, onOpenSupplier: ((String) -> Unit)? = null) {
  val on = remember(d.auditLog) { ir.vil3ntec.tohid.data.StockLedger.isOn(d) }
  if (!on) return
  val late = remember(d, today) { PurchaseBook.overdue(d, today) }
  val soon = remember(d, today) { PurchaseBook.dueSoon(d, today) }
  if (late.isEmpty() && soon.isEmpty()) return
  val names = remember(d) { d.suppliers.associate { it.id to it.name } }
  Panel(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("سررسیدِ پرداخت به تأمین‌کننده", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, modifier = Modifier.weight(1f))
      if (late.isNotEmpty()) StatusPill("${late.size} گذشته — ${money(late.sumOf { it.remaining })} ؋", PillTone.DANGER)
      if (soon.isNotEmpty()) { Spacer(Modifier.width(6.dp)); StatusPill("${soon.size} تا هفتهٔ بعد", PillTone.WARNING) }
    }
    Spacer(Modifier.height(6.dp))
    (late + soon).take(6).forEach { due ->
      val inv = due.invoice
      val overdue = due in late
      Row(
        Modifier.fillMaxWidth().clip(Shape.button)
          .then(if (onOpenSupplier != null) Modifier.clickable { onOpenSupplier(inv.supplierId) } else Modifier)
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "${names[inv.supplierId] ?: "—"} · ${if (inv.no > 0) "فاکتورِ #${inv.no}" else "خرید"} · سررسید ${formatDate(inv.dueDate ?: "")}",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.text, modifier = Modifier.weight(1f),
        )
        Text("${money(due.remaining)} ؋", style = MaterialTheme.typography.bodySmall, color = if (overdue) Shop.colors.danger else Shop.colors.warning)
      }
    }
  }
}

/**
 *  برگشت به تأمین‌کننده: کالا از انبار بیرون و سندِ کسر بدهی را کم می‌کند (`PurchaseBook.supplierReturn`).
 *  قیمتِ پیش‌فرض همان آخرین قیمتِ خرید از همین تأمین‌کننده است (PUR-09).
 */
@Composable
fun SupplierReturnDialog(
  d: ShopData,
  supplierId: String,
  onDismiss: () -> Unit,
  /** `key`: کلیدِ یکتای همین پنجره — دو کلیک یک سند (`PurchaseBook.supplierReturn`) */
  onSave: (productId: String, quantity: Double, unitPrice: Double, reason: String, date: String, key: String) -> Unit,
) {
  val key = remember { ir.vil3ntec.tohid.sync.v1.Ulid.next() }
  var saving by remember { mutableStateOf(false) }
  val options = remember(d, supplierId) { PurchaseBook.boughtFrom(d, supplierId) }
  var productId by remember { mutableStateOf(options.firstOrNull()?.id) }
  var amount by remember { mutableStateOf("") }
  var price by remember(productId) {
    mutableStateOf(productId?.let { PurchaseBook.lastPrice(d, supplierId, it) }?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "")
  }
  var reason by remember { mutableStateOf("") }
  var date by remember { mutableStateOf(formatDate(ir.vil3ntec.tohid.todayIso())) }
  val supplier = d.suppliers.find { it.id == supplierId }
  val product = d.products.find { it.id == productId }
  val total = (amount.toDoubleOrNull() ?: 0.0) * (price.toDoubleOrNull() ?: 0.0)

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
      Surface(color = Shop.colors.surfaceSolid, shape = Shape.dialog, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
          Text("برگشت به تأمین‌کننده", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
          Text(
            "${supplier?.name.orEmpty()} — کالا از انبار بیرون می‌رود و سندِ کسر از بدهیِ ما به او کم می‌کند. صندوق دست نمی‌خورد.",
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
          )
          Spacer(Modifier.height(12.dp))
          if (options.isEmpty()) {
            EmptyNote("از این تأمین‌کننده خریدی ثبت نشده است")
          } else {
            ChipPicker(
              title = "کالا",
              options = options.map { it.name },
              selected = product?.name.orEmpty(),
              onSelect = { name -> productId = options.firstOrNull { it.name == name }?.id },
              placeholder = "",
              allowNew = false,
            )
            product?.let {
              Text("موجودی: ${qty(ShopStore.stock(d, it.id))} ${it.unit} · قابلِ برگشت به همین تأمین‌کننده: ${qty(PurchaseBook.returnable(d, supplierId, it.id))}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              NumberField(amount, { amount = it }, "مقدار", Modifier.weight(1f))
              NumberField(price, { price = it }, "قیمتِ هر واحد", Modifier.weight(1f))
            }
            if (total > 0) {
              Spacer(Modifier.height(6.dp))
              Text("سندِ کسر: ${money(total)} ؋", style = MaterialTheme.typography.bodySmall, color = Shop.colors.success)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(reason, { reason = it }, label = { Text("دلیل (الزامی) — مثلاً خراب رسید") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(date, { date = it }, label = { Text("تاریخ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          }
          Spacer(Modifier.height(16.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
            Button(
              enabled = productId != null && !saving,
              onClick = {
                if (saving) return@Button
                val iso = if (date.isBlank()) "" else ir.vil3ntec.tohid.Jalali.parseTyped(date) ?: "?"
                saving = true
                //  ردشده (دلیلِ خالی، مقدارِ بیش از موجودی…) ⇒ پنجره می‌ماند و دکمه دوباره باز می‌شود
                onSave(productId ?: return@Button, amount.toDoubleOrNull() ?: 0.0, price.toDoubleOrNull() ?: -1.0, reason, iso, key)
                saving = false
              },
              modifier = Modifier.weight(1f),
            ) { Text("ثبتِ برگشت") }
          }
        }
      }
    }
  }
}
