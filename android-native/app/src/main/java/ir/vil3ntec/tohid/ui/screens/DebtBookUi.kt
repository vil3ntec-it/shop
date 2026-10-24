package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.isoMillis
import ir.vil3ntec.tohid.latinDigits
import ir.vil3ntec.tohid.print.ReportPrint
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  دفترِ قرض‌داران (v3 §22.2-A5) — تکه‌های مشترکِ گوشی (`Debtors.kt`) و
 *  کامپیوتر (`DeskCustomers.kt`). همهٔ عددها از `DebtBook` می‌آیند؛ این‌جا فقط
 *  نشان داده می‌شوند.
 */

/** سربرگِ حساب: بردهای عقب‌افتاده، نزدیک‌ترین سررسید، سقفِ اعتبار. چیزی نبود ⇒ هیچ. */
@Composable
internal fun DebtBookBanner(book: DebtBook.Book) {
  val lines = buildList {
    if (book.overdue.isNotEmpty()) {
      add("${book.overdue.size} برد از سررسید گذشته — ${DebtBook.show(book.overdueAmount)} افغانی" to Shop.colors.danger)
    }
    book.nextDue?.let { add("سررسیدِ بعدی ${formatDate(it.dueDate!!)} — ${DebtBook.show(it.open)} افغانی" to Shop.colors.primary) }
    book.credit?.let { c ->
      if (c.over) add("${DebtBook.show(c.excess)} افغانی بیش از سقفِ اعتبار (${DebtBook.show(c.limit)})" to Shop.colors.danger)
      else add("سقفِ اعتبار ${DebtBook.show(c.limit)} — تا سقف ${DebtBook.show(c.headroom)} افغانی" to Shop.colors.muted)
    }
  }
  if (lines.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    lines.forEach { (text, tint) -> DebtPill(text, tint) }
  }
}

/**
 *  جزئیاتِ یک ردیف زیرِ خودش: اقلامِ فاکتورِ برد با قیمتِ همان لحظه، تخفیف و
 *  پرداختِ همان لحظه، سررسید و باقیِ همان برد؛ یا این‌که رسید بابتِ کدام برد بود.
 *  و ماندهٔ حساب پس از همین ردیف.
 */
@Composable
internal fun DebtEntryDetail(book: DebtBook.Book, e: DebtBook.Entry, maxLines: Int = 8) {
  val small = MaterialTheme.typography.labelSmall
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    e.lines.take(maxLines).forEach { l ->
      val unit = if (l.unit.isNotBlank()) " ${l.unit}" else ""
      Text(
        "• ${l.name} — ${qty(l.quantity)}$unit × ${DebtBook.show(l.unitPrice)} = ${DebtBook.show(l.total)}" +
          if (l.returned > 0) " (مرجوعی ${qty(l.returned)})" else "",
        style = small, color = Shop.colors.muted,
      )
    }
    if (e.lines.size > maxLines) Text("+${e.lines.size - maxLines} قلمِ دیگر — در رسید", style = small, color = Shop.colors.muted2)
    e.sale?.takeIf { e.give && e.lines.isNotEmpty() }?.let { s ->
      val bits = listOfNotNull(
        s.discount.takeIf { it > 0 }?.let { "تخفیف ${DebtBook.show(DebtBook.minor(it))}" },
        s.paidAmount.takeIf { it > 0 }?.let { "پرداختِ همان لحظه ${DebtBook.show(DebtBook.minor(it))}" },
      )
      if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = small, color = Shop.colors.muted)
    }
    if (e.give && e.amount.minor > 0) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        e.dueDate?.let { due ->
          DebtPill("سررسید ${formatDate(due)}${if (e.overdue) " — گذشته" else ""}", if (e.overdue) Shop.colors.danger else Shop.colors.primary)
        }
        DebtPill(
          if (e.paid) "تسویه شد" else "باز ${DebtBook.show(e.open)}",
          if (e.paid) Shop.colors.success else Shop.colors.warning,
        )
      }
    }
    if (!e.give && e.allocations.isNotEmpty()) {
      Text(
        "بابتِ " + e.allocations.joinToString("، ") { a ->
          val g = book.entry(a.txId)
          val what = g?.invoiceLabel?.let { "فاکتور #$it" } ?: "برد ${g?.tx?.date?.let(::formatDate).orEmpty()}"
          "$what (${DebtBook.show(a.amount)})"
        },
        style = small, color = Shop.colors.muted,
      )
    }
    if (!e.give && e.open.minor > 0) Text("پیش‌پرداخت ${DebtBook.show(e.open)}", style = small, color = Shop.colors.success)
    Text("مانده پس از این ردیف: ${DebtBook.state(e.balanceAfter)}", style = small, color = Shop.colors.muted2)
  }
}

/** وضعیت = متن + رنگ + نقطه (VISUAL-DIRECTION §۱ بند ۳) — متن به رنگِ متن تا کنتراست بماند */
@Composable
private fun DebtPill(text: String, dot: androidx.compose.ui.graphics.Color) {
  Row(
    Modifier
      .clip(RoundedCornerShape(999.dp))
      .background(dot.copy(alpha = 0.12f))
      .padding(horizontal = 8.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(dot))
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
  }
}

/** نامِ دکان برای سرِ رسید — همان که فاکتور می‌گیرد (`PrintJob`) */
private fun storeName(context: Context): String =
  context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "") ?: ""

/**
 *  رسیدِ یک ردیف — چاپ/PDF با چاپِ سیستم؛ روی گوشی «فرستادن» (واتساپ، پیامک…)،
 *  روی کامپیوتر «کپیِ متن» (فرستادنِ اندروید آن‌جا معنا ندارد و دکمهٔ بی‌کار نمی‌گذاریم).
 */
@Composable
internal fun DebtReceiptDialog(
  d: ShopData,
  debtorId: String,
  txId: String,
  onDismiss: () -> Unit,
  onDue: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val receipt = remember(d, debtorId, txId) { DebtBook.receipt(d, debtorId, txId, storeName(context), todayIso()) }
  if (receipt == null) {
    LaunchedEffect(Unit) { onDismiss() }
    return
  }
  var copied by remember { mutableStateOf(false) }
  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
      Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
          Text(receipt.title, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(10.dp))
          receipt.rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
              Text(k, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, modifier = Modifier.weight(1f))
              if (v.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(v, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text, fontWeight = FontWeight.Bold)
              }
            }
          }
          Spacer(Modifier.height(16.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ReportPrint.print(context, receipt.sheet(), receipt.title) }, modifier = Modifier.weight(1f)) {
              Text("چاپ / PDF")
            }
            if (Platform.DESKTOP) {
              OutlinedButton(onClick = { clipboard.setText(AnnotatedString(receipt.text())); copied = true }, modifier = Modifier.weight(1f)) {
                Text(if (copied) "کپی شد" else "کپیِ متن")
              }
            } else {
              OutlinedButton(
                onClick = {
                  runCatching {
                    val send = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, receipt.title)
                      putExtra(Intent.EXTRA_TEXT, receipt.text())
                    }
                    context.startActivity(Intent.createChooser(send, "فرستادنِ رسید").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                  }
                },
                modifier = Modifier.weight(1f),
              ) { Text("فرستادن") }
            }
          }
          Spacer(Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDue != null) OutlinedButton(onClick = onDue, modifier = Modifier.weight(1f)) { Text("سررسید") }
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("بستن") }
          }
        }
      }
    }
  }
}

/** امروز + n روز، به شکلِ `yyyy-MM-dd` */
internal fun plusDays(iso: String, days: Int): String {
  val c = java.util.Calendar.getInstance().apply {
    timeInMillis = isoMillis(iso)
    add(java.util.Calendar.DAY_OF_MONTH, days)
  }
  val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
  val dd = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  return "${c.get(java.util.Calendar.YEAR)}-$m-$dd"
}

/**
 *  کادرِ سررسید — خورشیدی نوشته می‌شود (مثلِ `DateField`)، `yyyy-MM-dd` ذخیره
 *  می‌شود. خالی یعنی «سررسید ندارد». سه میان‌بُر: یک هفته، دو هفته، یک ماه.
 */
@Composable
internal fun DueDateField(value: String, onValueChange: (String) -> Unit) {
  var text by remember { mutableStateOf(if (value.isBlank()) "" else formatDate(value)) }
  LaunchedEffect(value) {
    if (Jalali.parseTyped(text) != value.ifBlank { null }) text = if (value.isBlank()) "" else formatDate(value)
  }
  val broken = text.isNotBlank() && Jalali.parseTyped(text) == null
  //  نمونه با همان `formatDate` ِ بقیهٔ صفحه (رقمِ لاتین؛ تنظیمِ رقمِ برنامه با قلم نشانش می‌دهد)
  val sample = remember { formatDate(plusDays(todayIso(), 7)) }
  Text("سررسید (اختیاری)", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  Spacer(Modifier.height(6.dp))
  OutlinedTextField(
    value = text,
    onValueChange = { raw ->
      text = latinDigits(raw).filter { c -> c.isDigit() || c == '/' || c == '-' }
      if (text.isBlank()) onValueChange("") else Jalali.parseTyped(text)?.let(onValueChange)
    },
    placeholder = { Text(sample) },
    singleLine = true,
    isError = broken,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    supportingText = {
      Text(if (broken) "تاریخ خوانده نشد — مثل $sample بنویسید" else "خالی یعنی سررسید ندارد", style = MaterialTheme.typography.labelSmall)
    },
    modifier = Modifier.fillMaxWidth(),
  )
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    listOf(7 to "یک هفته", 14 to "دو هفته", 30 to "یک ماه").forEach { (n, label) ->
      TextButton(onClick = { onValueChange(plusDays(todayIso(), n)) }) { Text(label) }
    }
    if (value.isNotBlank()) TextButton(onClick = { onValueChange("") }) { Text("بی سررسید", color = Shop.colors.muted) }
  }
}

/** گذاشتن/عوض کردن/برداشتنِ سررسیدِ یک بردِ ثبت‌شده (فروشِ نسیه هم) */
@Composable
internal fun DueDateDialog(entry: DebtBook.Entry, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
  var due by remember(entry.tx.id) { mutableStateOf(entry.dueDate.orEmpty()) }
  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
      Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
          Text("سررسیدِ برد", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
          Text(
            "${formatDate(entry.tx.date)} — ${DebtBook.show(entry.amount)} افغانی" +
              (entry.invoiceLabel?.let { " — فاکتور #$it" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
          )
          Spacer(Modifier.height(12.dp))
          DueDateField(due) { due = it }
          Spacer(Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
            Button(onClick = { onSave(due.ifBlank { null }) }, modifier = Modifier.weight(1f)) { Text("ذخیره") }
          }
        }
      }
    }
  }
}

/** برد از سقفِ اعتبار بیرون می‌زند — فقط با تأییدِ صریح ثبت می‌شود و ردش می‌ماند (CRM-03) */
@Composable
internal fun OverLimitDialog(credit: DebtBook.Credit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text("بیش از سقفِ اعتبار", color = Shop.colors.danger) },
    text = {
      Text(
        DebtBook.overLimitText(credit) + ".\nاگر ثبت شود، در «دفترچهٔ ثبت» با همین مبلغ می‌ماند.",
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
      )
    },
    confirmButton = { TextButton(onClick = onConfirm) { Text("باز هم ثبت شود", color = Shop.colors.danger) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
  )
}

/** کادرِ سقفِ اعتبار — خالی یعنی بی سقف (همان رفتارِ همیشه) */
@Composable
internal fun CreditLimitField(value: String, onValueChange: (String) -> Unit) {
  AmountField(value, onValueChange, "سقفِ اعتبار (اختیاری)")
  Text(
    "خالی یعنی بی سقف. بردِ بیش از سقف فقط با تأیید ثبت می‌شود.",
    style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
  )
}

/** متنِ کادرِ سقف ⇒ عدد؛ خالی ⇒ `null` (بی سقف)؛ نوشتهٔ نامعتبر ⇒ NaN تا موتور ردش کند، نه این‌که بی‌صدا «بی سقف» شود */
internal fun parseLimit(text: String): Double? =
  latinDigits(text).trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: Double.NaN }

internal fun limitText(v: Double?): String = v?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }.orEmpty()
