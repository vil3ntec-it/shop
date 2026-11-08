package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrLineSeller
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.core.staff.LineSeller
import ir.vil3ntec.tohid.data.CartSellers
import ir.vil3ntec.tohid.data.LineSellerPref
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.TillUser
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.theme.Shape

/**
 *  POS-23 / STF-08 — «فروشندهٔ ردیف‌ها» در صندوق (گوشیِ نسخهٔ ۳ و کامپیوتر، همین یک کد)؛ فایلِ مرزیِ پلتفرم.
 *
 *  ⛔ فقط با تنظیمِ روشنِ دکان (`LineSellerPref`)؛ خاموش ⇒ [LineSellerButton] هیچ چیز نمی‌کشد و Shift+F2 مالِ «مشتری» است.
 *  برگه: یک فروشنده (فروشندهٔ فاکتور، خودم، یا — فقط صاحب و مدیر — کارمندانِ فعالِ دکان) و ردیف‌هایی که به نامِ او
 *  می‌روند؛ ردیفِ انتخاب‌شدهٔ صندوق پیش‌گزیده است و بی آن همهٔ ردیف‌ها. نوشتن فقط از `CartSellers.assign` (مجوز همان‌جا).
 *  فهرستِ کارمندان: کاربرانِ صندوقِ همین دستگاه (PIN، بی اینترنت) و برای صاحب و مدیر اعضای فعالِ دکان از سرور.
 */
object LineSellerTill {
  /** تنظیمِ دکان روشن است؟ صفحه یک بار برای هر بار کشیدن می‌پرسد */
  @Composable
  fun on(): Boolean {
    val context = LocalContext.current
    return remember { LineSellerPref.on(context) }
  }
}

/** نام‌های کارمندانِ دکان برای برگه — بی اینترنت فقط کاربرانِ صندوقِ همین دستگاه */
@Composable
fun rememberLineSellerStaff(othersAllowed: Boolean): List<String> {
  val context = LocalContext.current
  var names by remember { mutableStateOf(runCatching { TillUser.users(context).filter { !it.locked }.map { it.name } }.getOrDefault(emptyList())) }
  LaunchedEffect(othersAllowed) {
    if (!othersAllowed || !Backend.isReady(context)) return@LaunchedEffect
    Backend.shop(context).members().onSuccess { page ->
      names = names + page.members.filter { it.status == "active" }.map { it.name }
    }
  }
  return names
}

/** دکمهٔ کنارِ سبد — فقط با تنظیمِ روشن و سبدِ ناخالی ([show]) */
@Composable
fun LineSellerButton(show: Boolean, onClick: () -> Unit) {
  if (!show || !LineSellerTill.on()) return
  TextButton(onClick = onClick) {
    Icon(Icons.Outlined.Badge, contentDescription = null)
    Text(StrLineSeller.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/**
 *  برگهٔ «فروشندهٔ ردیف». [invoice] فروشندهٔ فاکتور (کاربرِ صندوق، `TillUser.me`)، [preselect] ردیفِ انتخاب‌شده
 *  (`productId`؛ `null` ⇒ همهٔ ردیف‌ها). [onApply] سبدِ تازه را می‌گیرد؛ `false` ⇒ وسطِ جابه‌جاییِ فاکتور بود و برگه
 *  باز می‌ماند.
 */
@Composable
fun LineSellerSheet(
  d: ShopData,
  cart: List<SalesEngine.CartLine>,
  invoice: String,
  preselect: String?,
  onClose: () -> Unit,
  onApply: (List<SalesEngine.CartLine>) -> Boolean,
) {
  val context = LocalContext.current
  val others = remember { LineSellerPref.othersAllowed(context) }
  val staff = rememberLineSellerStaff(others)
  val choices = LineSeller.choices(invoice, staff, others)
  val first = preselect?.let { id -> cart.firstOrNull { it.productId == id } }
  var who by remember { mutableStateOf(first?.let { CartSellers.of(it, invoice) }?.takeIf { it != invoice.trim() }) }
  var picked by remember { mutableStateOf(if (first != null) setOf(first.productId) else cart.map { it.productId }.toSet()) }
  var error by remember { mutableStateOf(false) }
  val names = remember(d.products) { d.products.associate { it.id to it.name } }
  AlertDialog(
    onDismissRequest = onClose,
    shape = Shape.dialog,
    title = { Text(StrLineSeller.sheetTitle) },
    text = {
      Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        Text(StrLineSeller.seller, style = MaterialTheme.typography.labelLarge)
        SellerChoice(StrLineSeller.invoiceSeller(invoice.trim()), who == null) { who = null }
        choices.filter { it != invoice.trim() }.forEach { name -> SellerChoice(name, who == name) { who = name } }
        if (!others) Text(StrLineSeller.onlyMe, style = MaterialTheme.typography.bodySmall)
        Text(StrLineSeller.whichLines, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        cart.forEach { line ->
          val label = (if (line.free) line.label else names[line.productId].orEmpty()) + " · " + CartSellers.of(line, invoice)
          Row(
            Modifier.fillMaxWidth().clickable { picked = if (line.productId in picked) picked - line.productId else picked + line.productId },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = line.productId in picked, onCheckedChange = null)
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        if (error) Text(StrLineSeller.pickLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }
    },
    confirmButton = {
      TextButton(onClick = {
        if (picked.isEmpty()) { error = true; return@TextButton }
        val next = CartSellers.assign(cart, picked, who, invoice, invoice, others)
        if (onApply(next)) onClose()
      }) { Text(StrSalePages.record) }
    },
    dismissButton = { TextButton(onClick = onClose) { Text(Str.cancel) } },
  )
}

@Composable
private fun SellerChoice(text: String, selected: Boolean, onPick: () -> Unit) {
  Row(Modifier.fillMaxWidth().clickable(onClick = onPick), verticalAlignment = Alignment.CenterVertically) {
    RadioButton(selected = selected, onClick = null)
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
