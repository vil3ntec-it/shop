package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrPurchaseUnits
import ir.vil3ntec.tohid.core.product.DecimalText
import ir.vil3ntec.tohid.core.product.PurchaseUnits
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  PRD-07 — «واحدهای خرید» در فرمِ کالا، یک جزءِ مشترکِ گوشیِ نسخهٔ ۳ (`EditProductScreen`) و کامپیوتر (`DeskProducts`)،
 *  فقط با `ShopPolicy.productTools`. هر واحد: نام (کارتن)، چند واحدِ پایه (۲۴ دانه؛ اعشار فقط برای واحدِ وزنی)، تأمین‌کنندهٔ
 *  پیش‌فرض و بارکدِ اختیاری؛ افزودن، ویرایش و برداشتن. سنجش کارِ موتور است (`WarehouseEngine` ⇐ `PurchaseUnits.check`).
 *  [canEdit] = نقش اجازهٔ نوشتنِ کالا دارد (`ShopRole.canWrite(…, "products")`)؛ نه ⇒ فقط فهرست، بی کادر.
 */
@Composable
fun PurchaseUnitEditor(
  units: List<PurchaseUnits.Draft>,
  unit: String,
  suppliers: List<Supplier>,
  canEdit: Boolean,
  onChange: (List<PurchaseUnits.Draft>) -> Unit,
) {
  val c = Shop.colors
  val u = unit.trim().ifEmpty { StrProducts.unitWord }
  fun set(i: Int, p: PurchaseUnits.Draft) = onChange(units.mapIndexed { j, old -> if (j == i) p else old })
  Column(Modifier.fillMaxWidth()) {
    Text(StrPurchaseUnits.title, style = MaterialTheme.typography.labelMedium, color = c.muted)
    Text(StrPurchaseUnits.help(u), style = MaterialTheme.typography.labelSmall, color = c.muted)
    if (!canEdit) {
      units.forEach { p ->
        val f = DecimalText.scaled(p.factor, 3) ?: 0L
        Text(PurchaseUnits.label(PurchaseUnits.Item(p.name, f), u), style = MaterialTheme.typography.bodySmall, color = c.text)
      }
      Text(StrPurchaseUnits.readOnly, style = MaterialTheme.typography.labelSmall, color = c.muted)
      return@Column
    }
    units.forEachIndexed { i, p ->
      Spacer(Modifier.height(6.dp))
      //  نام با پهنای کامل: سه‌تا کنارِ هم در گوشیِ ۳۶۰dp کادرِ نام ~۱۱۰dp می‌شد و برچسبِ «نامِ واحد (مثلاً کارتن)» در آن جا نمی‌شد
      OutlinedTextField(
        value = p.name,
        onValueChange = { set(i, p.copy(name = it)) },
        label = { Text(StrPurchaseUnits.name) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = p.factor,
          onValueChange = { set(i, p.copy(factor = ir.vil3ntec.tohid.numericInput(it))) },
          label = { Text(StrPurchaseUnits.factor(u)) },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(units.filterIndexed { j, _ -> j != i }) }) { Text(StrPurchaseUnits.remove) }
      }
      OutlinedTextField(
        value = p.barcode,
        onValueChange = { set(i, p.copy(barcode = it)) },
        label = { Text(StrPurchaseUnits.barcode) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      if (suppliers.isNotEmpty()) {
        Text(StrPurchaseUnits.supplier, style = MaterialTheme.typography.labelSmall, color = c.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(selected = p.supplierId.isBlank(), onClick = { set(i, p.copy(supplierId = "")) }, label = { Text(StrPurchaseUnits.anySupplier) })
          suppliers.sortedBy { it.name }.forEach { s ->
            FilterChip(selected = p.supplierId == s.id, onClick = { set(i, p.copy(supplierId = s.id)) }, label = { Text(s.name) })
          }
        }
      }
    }
    if (units.size < PurchaseUnits.MAX_UNITS) {
      TextButton(onClick = { onChange(units + PurchaseUnits.Draft()) }) { Text(StrPurchaseUnits.add) }
    }
  }
}

/** واحدهای ذخیره‌شدهٔ کالا ⇒ ردیف‌های فرم */
fun purchaseUnitDrafts(p: ir.vil3ntec.tohid.data.Product): List<PurchaseUnits.Draft> =
  p.purchaseUnits.orEmpty().map { PurchaseUnits.draftOf(ir.vil3ntec.tohid.data.ShopStore.coreOf(it)) }

/**
 *  PRD-07 — انتخابِ واحدِ یک ردیفِ خرید یا سفارش (کامپیوتر: فاکتورِ خرید و سفارشِ خرید): واحدِ پایه یا یکی از واحدهای خریدِ
 *  کالا، و زیرش مقدارِ پایه و بهای هر واحدِ پایه از هسته (`PurchaseUnits.baseCost`، همان حسابی که ثبت می‌کند). [unit] نامِ
 *  واحدِ برگزیده؛ null = پایه. [total] جمعِ ردیف به افغانی.
 */
@Composable
fun PurchaseUnitPicker(product: ir.vil3ntec.tohid.data.Product, unit: String?, qty: String, total: Double, onPick: (String?) -> Unit) {
  val c = Shop.colors
  val all = product.purchaseUnits.orEmpty().map(ir.vil3ntec.tohid.data.ShopStore::coreOf)
  val chosen = PurchaseUnits.find(all, unit)
  Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 44.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(StrPurchaseUnits.unit, style = MaterialTheme.typography.labelSmall, color = c.muted)
    FilterChip(selected = chosen == null, onClick = { onPick(null) }, label = { Text(StrPurchaseUnits.baseOnly(product.unit)) })
    all.forEach { u ->
      FilterChip(selected = chosen == u, onClick = { onPick(u.name) }, label = { Text(PurchaseUnits.label(u, product.unit)) })
    }
  }
  val q = qty.toDoubleOrNull()
  if (chosen != null && q != null && q > 0 && total >= 0) {
    val bc = runCatching {
      PurchaseUnits.baseCost(ir.vil3ntec.tohid.data.StockLedger.milli(q), chosen.factorMilli, ir.vil3ntec.tohid.data.StockLedger.minor(total))
    }.getOrNull()
    if (bc != null) {
      Text(
        StrPurchaseUnits.lineBase(ir.vil3ntec.tohid.qty(bc.baseQtyMilli / 1000.0), product.unit, ir.vil3ntec.tohid.money(bc.unitCostMinor / 100.0)),
        Modifier.padding(start = 44.dp),
        style = MaterialTheme.typography.labelSmall,
        color = c.muted,
      )
    }
  }
}
