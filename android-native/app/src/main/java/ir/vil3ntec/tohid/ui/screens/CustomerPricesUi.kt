package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCustomerPrices
import ir.vil3ntec.tohid.core.sale.CustomerPrice
import ir.vil3ntec.tohid.data.CustomerPrices
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.PriceCheckData
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.SpecialPrice
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  POS-18 — پنجرهٔ «قیمتِ خاصِ این مشتری»: فهرست، افزودن (جست‌وجوی کالا با همان جست‌وجوی صندوق)، ویرایش و حذف. فقط
 *  رابط: بی Context و بی نوشتن؛ ذخیره و حذف با [onSave]/[onRemove] (درِ نوشتن در `CustomerPricesAndroid.kt`، منطق در
 *  `CustomerPrices`). [allowed] = `CustomerPrice.canEdit`؛ بی اجازه فقط خواندنی.
 */
@Composable
internal fun CustomerPricesDialog(
  d: ShopData,
  debtor: Debtor,
  allowed: Boolean,
  today: String,
  onDismiss: () -> Unit,
  onSave: (replacing: String?, entry: SpecialPrice) -> Unit,
  onRemove: (productId: String) -> Unit,
) {
  //  فهرست عوض شد (ذخیره یا حذفِ موفق) ⇒ برگشت به فهرست؛ ردِ نوشتن ⇒ فرم سرِ جایش
  var editing by remember(debtor.id, debtor.specialPrices) { mutableStateOf<CustomerPriceEditor?>(null) }
  val products = remember(d.products) { d.products.associateBy { it.id } }
  val ed = editing
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text("${StrCustomerPrices.title} — ${debtor.name}", color = Shop.colors.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (ed == null) {
          val list = debtor.specialPrices.orEmpty()
          if (list.isEmpty()) Text(StrCustomerPrices.empty, color = Shop.colors.muted)
          list.forEach { e ->
            val p = products[e.productId]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(p?.name ?: e.productId, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
                Text(
                  StrCustomerPrices.line(money(e.priceMinor / 100.0), money(p?.salePrice ?: 0.0)),
                  style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
                )
                customerPriceRange(e, today)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2) }
              }
              if (allowed) {
                TextButton(onClick = { editing = CustomerPriceEditor.of(e) }) { Text(StrCustomerPrices.edit) }
                TextButton(onClick = { onRemove(e.productId) }) { Text(StrCustomerPrices.remove, color = Shop.colors.danger) }
              }
            }
          }
          Spacer(Modifier.height(8.dp))
          Text(StrCustomerPrices.note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
          if (!allowed) Text(StrCustomerPrices.notAllowed, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
        } else {
          CustomerPriceForm(d, ed, today) { editing = it }
        }
      }
    },
    confirmButton = {
      when {
        ed != null -> TextButton(enabled = ed.ready(), onClick = { ed.entry()?.let { onSave(ed.replacing, it) } }) { Text(StrCustomerPrices.save) }
        allowed -> TextButton(onClick = { editing = CustomerPriceEditor() }) { Text(StrCustomerPrices.add) }
        else -> {}
      }
    },
    dismissButton = {
      TextButton(onClick = { if (ed != null) editing = null else onDismiss() }) { Text(if (ed != null) Str.cancel else Str.close) }
    },
  )
}

/** حالِ فرمِ یک ردیف (متنِ کادرها)؛ تاریخ خورشیدی یا میلادی (`Jalali.parseTyped`) */
internal data class CustomerPriceEditor(
  val replacing: String? = null,
  val productId: String = "",
  val query: String = "",
  val price: String = "",
  val from: String = "",
  val to: String = "",
) {
  private fun date(text: String): String? = text.trim().takeIf { it.isNotEmpty() }?.let { Jalali.parseTyped(it) }
  fun dateBroken(text: String): Boolean = text.isNotBlank() && date(text) == null
  fun priceMinor(): Long? = parseNumber(price)?.takeIf { it > 0 }?.let(CustomerPrices::minor)
  fun ready(): Boolean = productId.isNotEmpty() && priceMinor() != null && !dateBroken(from) && !dateBroken(to)
  fun entry(): SpecialPrice? = priceMinor()?.takeIf { ready() }?.let { SpecialPrice(productId, it, date(from), date(to)) }

  companion object {
    fun of(e: SpecialPrice) = CustomerPriceEditor(
      replacing = e.productId, productId = e.productId, price = (e.priceMinor / 100.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() },
      from = e.from?.let(::formatDate).orEmpty(), to = e.to?.let(::formatDate).orEmpty(),
    )
  }
}

/** «از … تا …» با «گذشته»/«هنوز نه»؛ بی بازه ⇒ `null` */
internal fun customerPriceRange(e: SpecialPrice, today: String): String? {
  if (e.from == null && e.to == null) return null
  val r = StrCustomerPrices.range(e.from?.let(::formatDate) ?: "…", e.to?.let(::formatDate) ?: "…")
  val c = CustomerPrice.Entry(e.productId, e.priceMinor, e.from, e.to)
  return when {
    CustomerPrice.active(c, today) -> r
    e.to != null && today > e.to -> "$r · ${StrCustomerPrices.expired}"
    else -> "$r · ${StrCustomerPrices.notYet}"
  }
}

@Composable
private fun CustomerPriceForm(d: ShopData, ed: CustomerPriceEditor, today: String, onChange: (CustomerPriceEditor) -> Unit) {
  val picked = d.products.find { it.id == ed.productId }
  if (ed.replacing == null) {
    OutlinedTextField(
      value = ed.query,
      onValueChange = { onChange(ed.copy(query = it.take(60), productId = "")) },
      singleLine = true,
      label = { Text(StrCustomerPrices.search) },
      modifier = Modifier.fillMaxWidth(),
    )
    if (picked == null && ed.query.isNotBlank()) {
      val hits = remember(d.products, ed.query) { PriceCheckData.matches(d, ed.query) }
      if (hits.isEmpty()) Text(StrCustomerPrices.noMatch, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      hits.forEach { p ->
        TextButton(onClick = { onChange(ed.copy(productId = p.id, query = p.name)) }, modifier = Modifier.fillMaxWidth()) {
          Text("${p.name} — ${money(p.salePrice)}", color = Shop.colors.text)
        }
      }
    }
  }
  picked?.let {
    Spacer(Modifier.height(6.dp))
    Text(it.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
  }
  Spacer(Modifier.height(8.dp))
  OutlinedTextField(
    value = ed.price,
    onValueChange = { onChange(ed.copy(price = ir.vil3ntec.tohid.numericInput(it).take(14))) },
    singleLine = true,
    label = { Text(StrCustomerPrices.price) },
    isError = ed.price.isNotBlank() && ed.priceMinor() == null,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = Modifier.fillMaxWidth(),
  )
  val sample = remember(today) { formatDate(today) }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
      value = ed.from,
      onValueChange = { onChange(ed.copy(from = it.take(12))) },
      singleLine = true,
      label = { Text(StrCustomerPrices.from) },
      placeholder = { Text(sample) },
      isError = ed.dateBroken(ed.from),
      modifier = Modifier.weight(1f),
    )
    OutlinedTextField(
      value = ed.to,
      onValueChange = { onChange(ed.copy(to = it.take(12))) },
      singleLine = true,
      label = { Text(StrCustomerPrices.to) },
      placeholder = { Text(sample) },
      isError = ed.dateBroken(ed.to),
      modifier = Modifier.weight(1f),
    )
  }
}
