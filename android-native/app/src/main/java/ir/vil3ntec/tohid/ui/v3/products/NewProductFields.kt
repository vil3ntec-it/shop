package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrNewProduct
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.product.ProductSuggest
import ir.vil3ntec.tohid.data.ExpiryEngine
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Field
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Problem

/**
 *  کادرهای یک کالا در فرمِ «کالای جدید» (۱۴۰۵/۰۸/۲۴، جای سه گروهِ «اصلی/قیمت/انبار»): لازم‌ها اول و بی سرگروه —
 *  نام؛ دسته‌بندی | واحد (کادرِ کشویی، «+ تازه» تهِ همان فهرست)؛ قیمتِ خرید | قیمتِ فروش (و سودِ هر واحد)؛ کد/بارکد با
 *  اسکن — سپس قابِ کم‌رنگِ «اختیاری»: موجودی در انبار و تاریخِ تولید | تاریخِ انقضا. «حداقلِ موجودی» و «قیمتِ عمده» این‌جا
 *  نیستند (در «ویرایش کالا» هستند). قاعده‌ها در `ProductFormModel` و `NewProductStock`.
 */
@Composable
internal fun NewProductFields(
  row: ProductRowDraft,
  errors: Map<Field, Problem>,
  categories: List<String>,
  units: List<String>,
  hooks: FieldHooks,
  today: String,
  nameHelper: String? = null,
  /** AI-06: دستهٔ پیشنهادی از نام (`rememberCategorySuggest`)؛ null ⇒ هیچ */
  suggest: ((String) -> List<ProductSuggest.Pick>)? = null,
) {
  var adding by remember { mutableStateOf<Field?>(null) }
  //  فقط تا دسته خالی است؛ زدنِ تراشه همان انتخابِ دسته است
  val picks = remember(row.name, row.category, suggest) {
    if (suggest == null || row.category.isNotBlank()) emptyList() else suggest(row.name)
  }
  Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
    V3TextField(
      row.name, { v -> hooks.onChange { it.copy(name = v) } }, StrProducts.name,
      modifier = Modifier.fillMaxWidth().field(Field.NAME, hooks),
      placeholder = StrProducts.namePlaceholder, helper = nameHelper, error = errors[Field.NAME]?.let(::problemText), required = true,
    )
    V3Collapse(picks.isNotEmpty()) {
      CategorySuggestions(picks) { cat -> hooks.onChange { it.copy(category = cat) } }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ChoiceDropdown(
        StrProducts.category, row.category, categories, { v -> hooks.onChange { it.copy(category = v) } },
        Modifier.weight(1f).field(Field.CATEGORY, hooks), required = true, error = errors[Field.CATEGORY]?.let(::problemText),
        addLabel = StrProducts.newCategory, onAdd = { adding = Field.CATEGORY }, onClosed = { hooks.onBlur(Field.CATEGORY) },
      )
      ChoiceDropdown(
        StrProducts.unit, row.unit, units, { v -> hooks.onChange { it.copy(unit = v) } },
        Modifier.weight(1f).field(Field.UNIT, hooks), required = true, error = errors[Field.UNIT]?.let(::problemText),
        addLabel = StrProducts.newUnit, onAdd = { adding = Field.UNIT }, onClosed = { hooks.onBlur(Field.UNIT) },
      )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(
        row.purchase, { v -> hooks.onChange { it.copy(purchase = v) } }, StrProducts.purchasePrice,
        modifier = Modifier.weight(1f).field(Field.PURCHASE, hooks), kind = V3FieldKind.MONEY, error = errors[Field.PURCHASE]?.let(::problemText), required = true,
      )
      V3TextField(
        row.sale, { v -> hooks.onChange { it.copy(sale = v) } }, StrProducts.salePrice,
        modifier = Modifier.weight(1f).field(Field.SALE, hooks), kind = V3FieldKind.MONEY, error = errors[Field.SALE]?.let(::problemText), required = true,
      )
    }
    ProfitLine(row)
    Spacer(Modifier.height(12.dp))
    V3TextField(
      row.barcode, { v -> hooks.onChange { it.copy(barcode = v) } }, StrNewProduct.code, Modifier.fillMaxWidth(),
      kind = V3FieldKind.BARCODE, placeholder = StrProducts.barcodePlaceholder, onScan = hooks.onScan,
    )
    Spacer(Modifier.height(14.dp))
    OptionalGroup(row, errors, hooks, today)
  }
  when (adding) {
    Field.CATEGORY -> NewValueSheet(StrProducts.newCategory, StrProducts.category, onDismiss = { adding = null }) { v -> hooks.onChange { it.copy(category = v) } }
    Field.UNIT -> NewValueSheet(StrProducts.newUnit, StrProducts.unit, onDismiss = { adding = null }) { v -> hooks.onChange { it.copy(unit = v) } }
    else -> Unit
  }
}

/** قابِ کم‌رنگِ «اختیاری»: دیده می‌شود ولی سبک — موجودی در انبار (به واحدِ کالا) و دو تاریخِ خورشیدیِ جمع‌وجور */
@Composable
private fun OptionalGroup(row: ProductRowDraft, errors: Map<Field, Problem>, hooks: FieldHooks, today: String) {
  val c = PhoneV3.colors
  val unit = row.unit.ifBlank { StrProducts.unitWord }
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(V3Space.cardRadius)).background(c.bg).padding(horizontal = 12.dp, vertical = 10.dp),
  ) {
    Text(StrNewProduct.optional, style = V3Type.caption, color = c.inkMuted)
    Text(StrNewProduct.optionalNote, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
    V3TextField(
      row.stock, { v -> hooks.onChange { it.copy(stock = v) } }, StrNewProduct.stock,
      Modifier.fillMaxWidth().field(Field.STOCK, hooks), kind = V3FieldKind.NUMBER, suffix = row.unit.ifBlank { null },
      helper = NewProductStock.total(row)?.let { StrNewProduct.stockHelper(qty(NewProductStock.qty(row) ?: 0.0), unit, money(it)) },
      error = errors[Field.STOCK]?.let(::problemText),
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      DateBox(row.made, { v -> hooks.onChange { it.copy(made = v) } }, StrStock.made, Field.MADE, errors, hooks, today, Modifier.weight(1f))
      DateBox(row.expiry, { v -> hooks.onChange { it.copy(expiry = v) } }, StrStock.expiry, Field.EXPIRY, errors, hooks, today, Modifier.weight(1f), expiryNote(row, today))
    }
  }
}

/** تاریخِ جمع‌وجور: فقط رقم (صفحه‌کلیدِ عددی)، «۱۴۰۵/۰۸/۲۴» با جداکنندهٔ خودکار؛ نمونهٔ کادر تاریخِ امروز است */
@Composable
internal fun DateBox(
  value: String,
  onValue: (String) -> Unit,
  label: String,
  f: Field,
  errors: Map<Field, Problem>,
  hooks: FieldHooks,
  today: String,
  modifier: Modifier,
  helper: String? = null,
) {
  V3TextField(
    value, { v -> onValue(NewProductStock.dateDigits(v)) }, label, modifier.field(f, hooks),
    kind = V3FieldKind.DATE, placeholder = formatDate(today), helper = helper,
    error = errors[f]?.let(::problemText), mask = DateMask,
  )
}

/** زیرِ «تاریخِ انقضا»: چند روز مانده، «نزدیکِ انقضا» (همان مرزِ هشدار) یا «گذشته» */
internal fun expiryNote(row: ProductRowDraft, today: String): String? {
  val days = NewProductStock.expiryDays(row, today) ?: return null
  return when {
    days < 0 -> StrNewProduct.alreadyExpired
    days == 0L -> StrNewProduct.expiresToday
    days <= ExpiryEngine.nearDays -> StrNewProduct.nearExpiry(days)
    else -> StrNewProduct.daysLeft(days)
  }
}
