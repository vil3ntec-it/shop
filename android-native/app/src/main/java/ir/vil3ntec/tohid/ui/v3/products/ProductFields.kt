package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.ui.graphics.graphicsLayer
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import androidx.compose.animation.core.animateFloatAsState
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrNewProduct
import ir.vil3ntec.tohid.core.i18n.StrPriceCheck
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.product.ProductSuggest
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Field
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Problem

/** متنِ خطای درجا */
internal fun problemText(p: Problem): String = when (p) {
  Problem.REQUIRED -> StrKit.required
  Problem.NOT_A_NUMBER -> StrKit.notANumber
  Problem.BAD_DATE -> StrNewProduct.badDate
  Problem.MADE_AFTER_EXPIRY -> StrGoods.madeAfterExpiry
  Problem.NEEDS_STOCK -> StrNewProduct.stockForDates
}

/** جای فرم برای هر گروهِ کادرهای «ویرایش کالا» (بخشِ صفحه) */
internal typealias FieldSection = @Composable (title: String, content: @Composable ColumnScope.() -> Unit) -> Unit

/** هرچه کادرهای کالا از صفحه می‌خواهند */
internal class FieldHooks(
  val onChange: ((ProductRowDraft) -> ProductRowDraft) -> Unit,
  /** کادری که تمرکز را از دست داد ⇐ از این پس خطایش دیده شود */
  val onBlur: (Field) -> Unit,
  val onScan: () -> Unit,
  val focus: (Field) -> FocusRequester,
)

/**
 *  کادرهای «ویرایش کالا» در سه گروه — «اصلی» (نام، دسته)، «قیمت» (خرید | فروش و سودِ
 *  هر واحد، عمده)، «انبار» (واحد، حداقل موجودی، بارکد با اسکن)؛ «بیشتر» جدا (`MoreFields`).
 *  دسته و واحد کادرِ کشویی‌اند (`ChoiceDropdown`، «+ تازه» تهِ فهرست). فرمِ «کالای جدید» از
 *  ۱۴۰۵/۰۸/۲۴ چیدمانِ جمع‌وجورِ خودش را دارد (`NewProductFields`).
 */
@Composable
internal fun ProductFieldGroups(
  row: ProductRowDraft,
  errors: Map<Field, Problem>,
  categories: List<String>,
  units: List<String>,
  hooks: FieldHooks,
  section: FieldSection,
  mode: ProductFormModel.Mode,
  nameHelper: String? = null,
  /** AI-06: دستهٔ پیشنهادی از نام (`rememberCategorySuggest`)؛ null ⇒ هیچ */
  suggest: ((String) -> List<ProductSuggest.Pick>)? = null,
  /** PRD-12: کادرِ «قیمتِ عمده» — فقط ویرایش (فرمِ «کالای جدید» از ۱۴۰۵/۰۸/۲۴ ندارد)، فقط با «لیستِ قیمت در صندوق» روشن */
  wholesale: Boolean = false,
) {
  val strict = mode == ProductFormModel.Mode.NEW
  var adding by remember { mutableStateOf<Field?>(null) }
  //  فقط تا دسته خالی است؛ زدنِ تراشه همان انتخابِ دسته است
  val picks = remember(row.name, row.category, suggest) {
    if (suggest == null || row.category.isNotBlank()) emptyList() else suggest(row.name)
  }
  section(StrProducts.groupMain) {
    V3TextField(
      row.name, { v -> hooks.onChange { it.copy(name = v) } }, StrProducts.name,
      modifier = Modifier.fillMaxWidth().field(Field.NAME, hooks),
      placeholder = StrProducts.namePlaceholder, helper = nameHelper, error = errors[Field.NAME]?.let(::problemText), required = true,
    )
    V3Collapse(picks.isNotEmpty()) {
      CategorySuggestions(picks) { cat -> hooks.onChange { it.copy(category = cat) } }
    }
    Spacer(Modifier.height(14.dp))
    ChoiceDropdown(
      StrProducts.category, row.category, categories, { v -> hooks.onChange { it.copy(category = v) } },
      Modifier.fillMaxWidth(), required = strict, error = errors[Field.CATEGORY]?.let(::problemText),
      addLabel = StrProducts.newCategory, onAdd = { adding = Field.CATEGORY }, onClosed = { hooks.onBlur(Field.CATEGORY) },
    )
  }
  section(StrProducts.groupPrice) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(
        row.purchase, { v -> hooks.onChange { it.copy(purchase = v) } }, StrProducts.purchasePrice,
        modifier = Modifier.weight(1f).field(Field.PURCHASE, hooks), kind = V3FieldKind.MONEY, error = errors[Field.PURCHASE]?.let(::problemText), required = strict,
      )
      V3TextField(
        row.sale, { v -> hooks.onChange { it.copy(sale = v) } }, StrProducts.salePrice,
        modifier = Modifier.weight(1f).field(Field.SALE, hooks), kind = V3FieldKind.MONEY, error = errors[Field.SALE]?.let(::problemText), required = true,
      )
    }
    ProfitLine(row)
    if (wholesale) {
      Spacer(Modifier.height(10.dp))
      V3TextField(
        row.wholesale, { v -> hooks.onChange { it.copy(wholesale = v) } }, StrPriceCheck.wholesale,
        modifier = Modifier.fillMaxWidth().field(Field.WHOLESALE, hooks), kind = V3FieldKind.MONEY,
        helper = StrProducts.wholesaleHelper, error = errors[Field.WHOLESALE]?.let(::problemText),
      )
    }
  }
  section(StrProducts.groupStock) {
    ChoiceDropdown(
      StrProducts.unit, row.unit, units, { v -> hooks.onChange { it.copy(unit = v) } },
      Modifier.fillMaxWidth(), required = strict, error = errors[Field.UNIT]?.let(::problemText),
      addLabel = StrProducts.newUnit, onAdd = { adding = Field.UNIT }, onClosed = { hooks.onBlur(Field.UNIT) },
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(row.minStock, { v -> hooks.onChange { it.copy(minStock = v) } }, StrProducts.minStock, Modifier.weight(1f), kind = V3FieldKind.NUMBER)
      V3TextField(
        row.barcode, { v -> hooks.onChange { it.copy(barcode = v) } }, StrProducts.barcode, Modifier.weight(1.3f),
        kind = V3FieldKind.BARCODE, placeholder = StrProducts.barcodePlaceholder, onScan = hooks.onScan,
      )
    }
  }
  when (adding) {
    Field.CATEGORY -> NewValueSheet(StrProducts.newCategory, StrProducts.category, onDismiss = { adding = null }) { v -> hooks.onChange { it.copy(category = v) } }
    Field.UNIT -> NewValueSheet(StrProducts.newUnit, StrProducts.unit, onDismiss = { adding = null }) { v -> hooks.onChange { it.copy(unit = v) } }
    else -> Unit
  }
}

/**
 *  «بیشتر» (فقط ویرایش، جمع‌شده): زیردسته، واحدِ فروشِ دوم و ضریبش، تأمین‌کننده (کادرِ کشویی)،
 *  و کلیدِ «فعال در فروش».
 */
@Composable
internal fun ColumnScope.MoreFields(
  row: ProductRowDraft,
  errors: Map<Field, Problem>,
  suppliers: List<Supplier>,
  hooks: FieldHooks,
) {
  V3TextField(row.subcategory, { v -> hooks.onChange { it.copy(subcategory = v) } }, StrProducts.subcategory, Modifier.fillMaxWidth())
  Spacer(Modifier.height(14.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    V3TextField(
      row.saleUnit, { v -> hooks.onChange { it.copy(saleUnit = v) } }, StrProducts.saleUnit, Modifier.weight(1f),
      placeholder = StrProducts.saleUnitPlaceholder,
    )
    V3TextField(
      row.unitFactor, { v -> hooks.onChange { it.copy(unitFactor = v) } }, StrProducts.factor,
      Modifier.weight(1f).field(Field.UNIT_FACTOR, hooks), kind = V3FieldKind.NUMBER,
      helper = row.unit.takeIf { it.isNotBlank() }?.let(StrProducts::factorHelper),
      error = errors[Field.UNIT_FACTOR]?.let(::problemText),
    )
  }
  Spacer(Modifier.height(14.dp))
  //  MKT-06: اختیاری؛ خالی ⇒ بی یادآوری
  V3TextField(
    row.cycle, { v -> hooks.onChange { it.copy(cycle = v) } }, StrProducts.cycle,
    Modifier.fillMaxWidth().field(Field.CYCLE, hooks), kind = V3FieldKind.NUMBER,
    helper = StrProducts.cycleHelper, error = errors[Field.CYCLE]?.let { StrProducts.cycleBad },
  )
  if (suppliers.isNotEmpty()) {
    Spacer(Modifier.height(14.dp))
    val names = suppliers.associate { it.id to it.name }
    ChoiceDropdown(
      StrProducts.supplier, row.supplierId, suppliers.map { it.id }, { v -> hooks.onChange { it.copy(supplierId = v) } },
      Modifier.fillMaxWidth(), noneLabel = StrProducts.noSupplier, display = { id -> names[id] ?: id },
    )
  }
  Spacer(Modifier.height(10.dp))
  V3ToggleRow(StrProducts.activeInSale, row.active, { on -> hooks.onChange { it.copy(active = on) } }, subtitle = StrProducts.activeHint)
}

/** سرِ گروهِ جمع‌شونده («بیشتر») با فلشی که می‌چرخد */
@Composable
internal fun CollapsibleHeader(title: String, open: Boolean, onToggle: () -> Unit) {
  val c = PhoneV3.colors
  val turn by animateFloatAsState(if (open) 180f else 0f, MotionSpec.indicator(), label = "moreTurn")
  Row(
    Modifier.fillMaxWidth().v3Pressable(label = title, onClick = onToggle).padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = V3Type.headline, color = c.ink, modifier = Modifier.weight(1f))
    Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = c.icon, modifier = Modifier.graphicsLayer { rotationZ = turn })
  }
}

/** «سودِ هر عدد: ۲۰ ؋» — زیان با برچسبِ «زیان» و رنگِ خطا (رنگ تنها نیست) */
@Composable
internal fun ProfitLine(row: ProductRowDraft) {
  val profit = ProductFormModel.unitProfit(row)
  V3Collapse(profit != null) { ProfitText(row, profit ?: 0.0) }
}

@Composable
private fun ProfitText(row: ProductRowDraft, profit: Double) {
  val c = PhoneV3.colors
  val loss = profit < 0
  Row(Modifier.padding(top = 10.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    if (loss) { V3StatusPill(V3Status.CANCELLED, StrProducts.loss); Spacer(Modifier.width(8.dp)) }
    Text(
      StrProducts.profitLine(row.unit.ifBlank { StrProducts.unitWord }, money(profit)),
      style = V3Type.subhead.copy(fontWeight = FontWeight.SemiBold),
      color = if (loss) PhoneV3Roles.errorText(c) else PhoneV3Roles.brandText(c),
    )
  }
}

/** برگهٔ کوچکِ «واحدِ تازه»: یک کادر و «افزودن» */
@Composable
internal fun NewValueSheet(title: String, label: String, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
  var value by remember { mutableStateOf("") }
  V3Sheet(onDismiss = onDismiss, title = title) { closer ->
    V3TextField(value, { value = it }, label, Modifier.fillMaxWidth(), onImeAction = { value.trim().takeIf { it.isNotEmpty() }?.let { v -> closer.close { onAdd(v) } } })
    Spacer(Modifier.height(14.dp))
    V3Button(StrKit.add, { val v = value.trim(); if (v.isNotEmpty()) closer.close { onAdd(v) } }, Modifier.fillMaxWidth(), enabled = value.isNotBlank(), haptic = true)
  }
}

/** تمرکزِ کادر (برای پرش به نخستین خطا) و «از دست دادنِ تمرکز» (نشان دادنِ خطایش) */
internal fun Modifier.field(f: Field, hooks: FieldHooks): Modifier = composed {
  var had by remember { mutableStateOf(false) }
  this
    .focusRequester(hooks.focus(f))
    .onFocusChanged { s ->
      if (had && !s.hasFocus) hooks.onBlur(f)
      had = s.hasFocus
    }
}
