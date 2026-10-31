package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.ui.theme.Shop

// i18n: سه‌زبانه — نوشته‌ها در `StrInvoices`/`StrPos`/`StrSale`/`Str`؛ پیامِ «رسیدِ دکانِ دیگر» دادهٔ `ReturnFlow` است.

/**
 *  «حالتِ برگشت» ِ صندوقِ کامپیوتر — Shift+F9 (`sale.returnMode`؛ RET-01، RET-05؛ S13، S15).
 *
 *  فقط با برگشت و تعویضِ تازهٔ روشن (`ReturnSettings`) باز می‌شود (`PosKeys.returnMode`)؛ خاموش ⇒
 *  کلید همان کارِ امروز را می‌کند و این پنجره هرگز نمی‌آید. پیدا کردن همان `ReturnFlow.find` ِ
 *  «فاکتورها» است (QRِ رسید با اسکنر، شمارهٔ فاکتور با رقمِ فارسی هم، نام یا تلفنِ مشتری)، و فرم و
 *  رسید همان `ReturnFlowDialog` و `ReturnReceiptDialog`: این‌جا هیچ حسابی نیست، فقط راهِ رسیدن.
 */
object ReturnMode {
  /** بیشترین ردیفِ فهرست — اسکنِ QR یک ردیف می‌دهد؛ نام و تلفن تازه‌ترین‌ها را */
  const val LIMIT = 8

  /** فاکتورهای پیدا شده، تازه‌ترین اول؛ لغوشده هم می‌آید تا بگوییم چرا برنمی‌گردد */
  fun found(d: ShopData, term: String, shopTag: String?): List<Sale> =
    if (term.isBlank()) emptyList() else ReturnFlow.find(d, term, LIMIT, shopTag)

  /** Enter (یا پایانِ اسکن): فقط یک فاکتورِ زنده ⇒ همان؛ چندتا یا لغوشده ⇒ `null` و فهرست می‌ماند */
  fun pick(found: List<Sale>): Sale? = found.singleOrNull()?.takeIf { it.status != "cancelled" }
}

/**
 *  جریانِ کاملِ حالتِ برگشت: پیدا کردن ⇐ فرمِ برگشت/تعویض (با رسید، یا «برگشتِ بی رسید») ⇐ رسیدِ برگشت.
 *  Esc در فرم به همین جست‌وجو برمی‌گردد؛ بستنِ رسید یا جست‌وجو صندوق را برمی‌گرداند ([onClose]).
 *  سبدِ صندوق دست نمی‌خورد (تعویض سبدِ خودش را در فرم دارد).
 */
@Composable
fun ReturnModeHost(d: ShopData, store: ShopStore, shopTag: String, onClose: () -> Unit, onMessage: (String) -> Unit) {
  var sale by remember { mutableStateOf<Sale?>(null) }
  var noReceipt by remember { mutableStateOf(false) }
  var receipt by remember { mutableStateOf<String?>(null) }
  val posted = receipt
  val picked = sale
  when {
    posted != null -> ReturnReceiptDialog(d = d, docId = posted, onDismiss = onClose, onMessage = onMessage)
    picked != null -> ReturnFlowDialog(
      d = d, sale = picked, store = store,
      onDismiss = { sale = null },
      onPosted = { id, note -> sale = null; onMessage(note); receipt = id },
    )
    noReceipt -> ReturnFlowDialog(
      d = d, sale = null, store = store,
      onDismiss = { noReceipt = false },
      onPosted = { id, note -> noReceipt = false; onMessage(note); receipt = id },
    )
    else -> ReturnModeDialog(d, shopTag, onPick = { sale = it }, onNoReceipt = { noReceipt = true }, onDismiss = onClose, onMessage = onMessage)
  }
}

/** پنجرهٔ پیدا کردنِ رسید: کادرِ اسکن/جست‌وجو با فوکوس، فهرستِ کوتاه و «برگشتِ بی رسید» */
@Composable
fun ReturnModeDialog(
  d: ShopData,
  shopTag: String,
  onPick: (Sale) -> Unit,
  onNoReceipt: () -> Unit,
  onDismiss: () -> Unit,
  onMessage: (String) -> Unit,
) {
  var term by remember { mutableStateOf("") }
  val found = remember(d, term) { ReturnMode.found(d, term, shopTag) }
  val focus = remember { FocusRequester() }

  fun enter() {
    if (ReturnFlow.isForeignReceipt(term, shopTag)) { onMessage(ReturnFlow.FOREIGN_RECEIPT); return }
    ReturnMode.pick(found)?.let(onPick)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrInvoices.returnOrExchange, color = Shop.colors.text) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = term,
          onValueChange = { term = ir.vil3ntec.tohid.latinDigits(it) },
          placeholder = { Text(StrInvoices.searchHint) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
              if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { enter(); true } else false
            },
        )
        //  اسکنر همان لحظه در کادر بنویسد — درونِ خودِ پنجره، مثلِ پنجره‌های دیگرِ صندوق
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Text(StrInvoices.returnHint, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        if (term.isNotBlank() && found.isEmpty()) {
          Text(StrInvoices.noMatch, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        }
        found.forEach { s -> FoundRow(d, s) { onPick(s) } }
        Spacer(Modifier.height(2.dp))
      }
    },
    confirmButton = { TextButton(onClick = onNoReceipt) { Text(StrPos.returnNoReceipt) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.goBack) } },
  )
}

/** یک فاکتورِ پیدا شده: شماره، مشتری، تاریخ و مبلغ؛ لغوشده نشان دارد و انتخاب نمی‌شود */
@Composable
private fun FoundRow(d: ShopData, s: Sale, onClick: () -> Unit) {
  val live = s.status != "cancelled"
  val who = s.debtorId?.let { id -> d.debtors.find { it.id == id }?.name }.orEmpty()
  Row(
    Modifier.fillMaxWidth().clickable(enabled = live, onClick = onClick).padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("#${s.invoiceLabel() ?: "0"}", style = MaterialTheme.typography.bodyMedium, color = if (live) Shop.colors.text else Shop.colors.muted)
    Text(
      listOf(who, ir.vil3ntec.tohid.displayDate(s.date)).filter { it.isNotBlank() }.joinToString(" · "),
      style = MaterialTheme.typography.bodySmall,
      color = Shop.colors.muted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Text(if (live) ir.vil3ntec.tohid.money(s.finalTotal) else StrSale.cancelled, style = MaterialTheme.typography.bodyMedium, color = if (live) Shop.colors.text else Shop.colors.danger)
  }
}
