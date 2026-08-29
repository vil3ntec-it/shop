package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  تاریخچهٔ فروش.
 *
 *  هر فاکتوری که ثبت شده اینجاست — از جمله لغوشده‌ها. فاکتور هیچ‌وقت پاک
 *  نمی‌شود، فقط لغو می‌شود؛ فروشی که افتاده حقیقتی است که در گزارشِ سود و
 *  حسابِ قرض‌دار اثر گذاشته، و برداشتنش آن عددها را عوض می‌کند.
 */
@Composable
fun SalesHistoryScreen(store: ShopStore, d: ShopData, snackbar: SnackbarHostState) {
  val scope = rememberCoroutineScope()

  var filter by rememberSaveable { mutableStateOf("all") }
  var invoiceFor by remember { mutableStateOf<Sale?>(null) }
  var returnFor by remember { mutableStateOf<Sale?>(null) }
  var cancelFor by remember { mutableStateOf<Sale?>(null) }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  fun apply(result: SalesEngine.Result, done: String, after: () -> Unit = {}) {
    when (result) {
      is SalesEngine.Result.Failed -> toast(result.message)
      is SalesEngine.Result.Ok -> {
        scope.launch { store.save(result.data) }
        toast(done)
        after()
      }
    }
  }

  val shown = d.sales
    .filter {
      when (filter) {
        "cash" -> it.status != "cancelled" && it.paymentMethod == "cash"
        "credit" -> it.status != "cancelled" && it.paymentMethod == "credit"
        "cancelled" -> it.status == "cancelled"
        else -> true
      }
    }
    .sortedByDescending { it.createdAt }

  val active = d.sales.filter { it.status != "cancelled" }

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
    item {
      Text(
        "فاکتورهای ثبت‌شده، مرجوعی و لغو فروش",
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
      Spacer(Modifier.height(14.dp))
    }

    item {
      StatTile(
        label = "جمع فروش",
        value = "${money(active.sumOf { it.finalTotal })} افغانی",
        hint = "${plain(active.size)} فاکتور — ${plain(d.sales.size - active.size)} لغوشده",
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))
    }

    item {
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf("all" to "همه", "cash" to "نقدی", "credit" to "نسیه", "cancelled" to "لغوشده").forEach { (id, label) ->
          FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) })
        }
      }
      Spacer(Modifier.height(14.dp))
    }

    if (shown.isEmpty()) {
      item { Panel { EmptyNote("فاکتوری با این فیلتر پیدا نشد.") } }
    } else {
      /*
       *  جدولِ فاکتورها.
       *
       *  یک سرستون در بالا و بعد ردیف‌ها — نه یک کارت برای هر فاکتور.
       *  کارت‌ها هر کدام سرستونِ خودشان را داشتند و برای مقایسهٔ دو
       *  فاکتور باید متن خوانده می‌شد. با ستونِ ثابت، چشم از بالا به
       *  پایین می‌رود و خودش مقایسه می‌کند.
       */
      item {
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm))
            .background(Shop.colors.surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          InvoiceCell("شماره", 0.7f, head = true)
          InvoiceCell("تاریخ", 1.1f, head = true)
          InvoiceCell("نام", 1.3f, head = true)
          InvoiceCell("نوع", 0.8f, head = true)
          InvoiceCell("مبلغ", 1.2f, head = true, end = true)
          Spacer(Modifier.width(28.dp))
        }
      }

      itemsIndexed(shown, key = { _, s -> s.id }) { index, sale ->
        SaleRow(
          d = d,
          sale = sale,
          striped = index % 2 == 1,
          last = index == shown.lastIndex,
          onInvoice = { invoiceFor = sale },
          onReturn = { returnFor = sale },
          onCancel = { cancelFor = sale },
        )
      }
    }
  }

  /* ---------------------------- پنجره‌ها ---------------------------- */

  invoiceFor?.let { sale ->
    InvoiceDialog(d = d, sale = sale, onDismiss = { invoiceFor = null }, onMessage = ::toast)
  }

  returnFor?.let { sale ->
    ReturnDialog(
      d = d,
      sale = sale,
      onDismiss = { returnFor = null },
      onConfirm = { quantities, reason ->
        apply(
          SalesEngine.recordReturn(d, sale.id, quantities, reason, todayIso(), System.currentTimeMillis(), ::newId),
          "مرجوعی با موفقیت ثبت شد",
        ) { returnFor = null }
      },
    )
  }

  cancelFor?.let { sale ->
    AlertDialog(
      onDismissRequest = { cancelFor = null },
      containerColor = Shop.colors.surface,
      title = { Text("لغو فروش؟", color = Shop.colors.text) },
      text = {
        Text(
          SalesEngine.cancelWarning(sale),
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          apply(
            SalesEngine.cancel(d, sale.id, todayIso(), System.currentTimeMillis(), ::newId),
            "فروش لغو شد",
          ) { cancelFor = null }
        }) { Text("لغو فروش", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { cancelFor = null }) { Text("بازگشت") } },
    )
  }
}

/* ============================ تکه‌ها ============================ */

/**
 *  یک ردیفِ جدول.
 *
 *  زدنِ ردیف، فاکتور را باز می‌کند. مرجوعی و لغو زیرِ دکمهٔ سه‌نقطهٔ
 *  آخرِ ردیف‌اند: در یک جدول، سه دکمهٔ کنارِ هر ردیف همان شلوغی‌ای را
 *  برمی‌گرداند که جدول برای رفعش آمده بود.
 */
@Composable
private fun SaleRow(
  d: ShopData,
  sale: Sale,
  striped: Boolean,
  last: Boolean,
  onInvoice: () -> Unit,
  onReturn: () -> Unit,
  onCancel: () -> Unit,
) {
  val cancelled = sale.status == "cancelled"
  val debtor = sale.debtorId?.let { id -> d.debtors.find { it.id == id } }
  val canReturn = !cancelled && d.saleItems.any { it.saleId == sale.id && SalesEngine.returnable(it) > 0 }
  var menu by remember { mutableStateOf(false) }

  val kindTint = when {
    cancelled -> Shop.colors.danger
    sale.paymentMethod == "credit" -> Shop.colors.warning
    else -> Shop.colors.success
  }

  Column {
    Row(
      Modifier
        .fillMaxWidth()
        .then(
          if (last) Modifier.clip(RoundedCornerShape(bottomStart = Radius.sm, bottomEnd = Radius.sm))
          else Modifier
        )
        // ردیفِ یکی‌درمیانِ کم‌رنگ: چشم روی جدولِ بلند خط را گم نمی‌کند
        .background(if (striped) Shop.colors.surface2.copy(alpha = 0.45f) else Shop.colors.surface)
        .clickable(onClick = onInvoice)
        .padding(horizontal = 12.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      InvoiceCell(plain(sale.invoiceNumber ?: 0), 0.7f, bold = true)
      InvoiceCell(formatDate(sale.date), 1.1f)
      InvoiceCell(debtor?.name ?: "نقدی", 1.3f)
      InvoiceCell(
        when {
          cancelled -> "لغوشده"
          sale.paymentMethod == "credit" -> "نسیه"
          else -> "نقدی"
        },
        0.8f,
        tint = kindTint,
      )
      InvoiceCell(
        money(sale.finalTotal),
        1.2f,
        end = true,
        bold = true,
        tint = if (cancelled) Shop.colors.muted else Shop.colors.text,
      )
      Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
          Icon(
            Icons.Filled.MoreVert,
            contentDescription = "کارها",
            tint = Shop.colors.muted2,
            modifier = Modifier.size(17.dp),
          )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(text = { Text("دیدن فاکتور") }, onClick = { menu = false; onInvoice() })
          if (canReturn) {
            DropdownMenuItem(text = { Text("مرجوعی") }, onClick = { menu = false; onReturn() })
          }
          if (!cancelled) {
            DropdownMenuItem(
              text = { Text("لغو فروش", color = Shop.colors.danger) },
              onClick = { menu = false; onCancel() },
            )
          }
        }
      }
    }
    if (!last) HorizontalDivider(color = Shop.colors.fieldBorder.copy(alpha = 0.35f))
  }
}

@Composable
private fun ReturnDialog(
  d: ShopData,
  sale: Sale,
  onDismiss: () -> Unit,
  onConfirm: (Map<String, Double>, String) -> Unit,
) {
  val items = d.saleItems.filter { it.saleId == sale.id && SalesEngine.returnable(it) > 0 }
  var quantities by remember { mutableStateOf(mapOf<String, String>()) }
  var reason by remember { mutableStateOf("") }

  val total = items.sumOf { item ->
    val wanted = (quantities[item.id]?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, SalesEngine.returnable(item))
    if (item.quantity > 0) Math.round(item.totalPrice / item.quantity * wanted).toDouble() else 0.0
  }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surface, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("مرجوعی — فاکتور #${plain(sale.invoiceNumber ?: 0)}",
          style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
          EmptyNote("موردی برای مرجوعی وجود ندارد.")
        } else {
          items.forEach { item ->
            val product = d.products.find { it.id == item.productId }
            val allowed = SalesEngine.returnable(item)
            Column(Modifier.padding(bottom = 12.dp)) {
              Text(
                "${product?.name ?: "(محصول حذف‌شده)"} — قابل مرجوعی: ${qty(allowed)}${product?.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
              Spacer(Modifier.height(6.dp))
              NumberField(
                value = quantities[item.id].orEmpty(),
                onValueChange = { quantities = quantities + (item.id to it) },
                label = "مقدار مرجوعی",
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }

          if (total > 0) {
            Panel {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("مبلغ مرجوعی", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
                Text("${money(total)} افغانی", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
              }
            }
            Spacer(Modifier.height(10.dp))
          }

          OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("دلیل (اختیاری)") },
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            enabled = items.isNotEmpty(),
            onClick = {
              onConfirm(
                quantities.mapNotNull { (id, text) ->
                  text.toDoubleOrNull()?.takeIf { it > 0 }?.let { id to it }
                }.toMap(),
                reason.trim(),
              )
            },
            modifier = Modifier.weight(1f),
          ) { Text("ثبت مرجوعی") }
        }
      }
    }
    }
  }
}

/**
 *  یک خانهٔ جدولِ فاکتور.
 *
 *  پهنای هر ستون با `weight` است نه با عددِ ثابت: نامِ بلند ستونِ کناری
 *  را هل نمی‌دهد و ردیف‌ها روی هر پهنای صفحه هم‌تراز می‌مانند.
 */

@Composable
private fun RowScope.InvoiceCell(
  text: String,
  weight: Float,
  head: Boolean = false,
  end: Boolean = false,
  tint: Color? = null,
  bold: Boolean = false,
) {
  Text(
    text,
    style = if (head) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
    color = tint ?: if (head) Shop.colors.muted2 else Shop.colors.text,
    fontWeight = if (bold) FontWeight.Bold else null,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    textAlign = if (end) TextAlign.End else TextAlign.Start,
    modifier = Modifier.weight(weight).padding(end = 6.dp),
  )
}
