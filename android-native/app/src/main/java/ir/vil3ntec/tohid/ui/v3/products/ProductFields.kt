package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
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
}

/** جای فرم برای هر گروهِ کادرها: کارتِ ردیف (کالای تازه) یا بخشِ صفحه (ویرایش) */
internal typealias FieldSection = @Composable (title: String, content: @Composable ColumnScope.() -> Unit) -> Unit

/** هرچه کادرهای کالا از صفحه می‌خواهند */
internal class FieldHooks(
  val onChange: ((ProductRowDraft) -> ProductRowDraft) -> Unit,
  /** کادری که تمرکز را از دست داد ⇐ از این پس خطایش دیده شود */
  val onBlur: (Field) -> Unit,
  val onScan: () -> Unit,
  val onNewCategory: () -> Unit,
  val focus: (Field) -> FocusRequester,
)

/**
 *  کادرهای یک کالا در سه گروه — «اصلی» (نام، دسته)، «قیمت» (خرید | فروش و سودِ
 *  هر واحد)، «انبار» (واحد، حداقل موجودی، بارکد با اسکن). مشترکِ فرمِ چندردیفیِ
 *  «کالای تازه» و «ویرایش کالا»؛ «بیشتر» فقط در ویرایش (`MoreFields`).
 */
@OptIn(ExperimentalLayoutApi::class)
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
) {
  val strict = mode == ProductFormModel.Mode.NEW
  var newUnit by remember { mutableStateOf(false) }
  section(StrProducts.groupMain) {
    V3TextField(
      row.name, { v -> hooks.onChange { it.copy(name = v) } }, StrProducts.name,
      modifier = Modifier.fillMaxWidth().field(Field.NAME, hooks),
      placeholder = StrProducts.namePlaceholder, helper = nameHelper, error = errors[Field.NAME]?.let(::problemText), required = true,
    )
    Spacer(Modifier.height(14.dp))
    ChipGroup(StrProducts.category, errors[Field.CATEGORY], required = strict) {
      (categories + listOfNotNull(row.category.takeIf { it.isNotBlank() && it !in categories })).forEach { cat ->
        V3Chip(cat, row.category == cat, { hooks.onChange { it.copy(category = ProductFormModel.toggle(it.category, cat)) } })
      }
      V3AddChip(StrProducts.newCategory, hooks.onNewCategory)
    }
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
  }
  section(StrProducts.groupStock) {
    ChipGroup(StrProducts.unit, errors[Field.UNIT], required = strict) {
      (units + listOfNotNull(row.unit.takeIf { it.isNotBlank() && it !in units })).forEach { u ->
        V3Chip(u, row.unit == u, { hooks.onChange { it.copy(unit = ProductFormModel.toggle(it.unit, u)) } })
      }
      V3AddChip(StrProducts.newUnit) { newUnit = true }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(row.minStock, { v -> hooks.onChange { it.copy(minStock = v) } }, StrProducts.minStock, Modifier.weight(1f), kind = V3FieldKind.NUMBER)
      V3TextField(
        row.barcode, { v -> hooks.onChange { it.copy(barcode = v) } }, StrProducts.barcode, Modifier.weight(1.3f),
        kind = V3FieldKind.BARCODE, placeholder = StrProducts.barcodePlaceholder, onScan = hooks.onScan,
      )
    }
  }
  if (newUnit) NewValueSheet(StrProducts.newUnit, StrProducts.unit, onDismiss = { newUnit = false }) { v -> hooks.onChange { it.copy(unit = v) } }
}

/**
 *  «بیشتر» (فقط ویرایش، جمع‌شده): زیردسته، واحدِ فروشِ دوم و ضریبش، تأمین‌کننده،
 *  و کلیدِ «فعال در فروش».
 */
@OptIn(ExperimentalLayoutApi::class)
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
  if (suppliers.isNotEmpty()) {
    Spacer(Modifier.height(14.dp))
    ChipGroup(StrProducts.supplier, null, required = false) {
      V3Chip(StrProducts.noSupplier, row.supplierId.isBlank(), { hooks.onChange { it.copy(supplierId = "") } })
      suppliers.forEach { s -> V3Chip(s.name, row.supplierId == s.id, { hooks.onChange { it.copy(supplierId = s.id) } }) }
    }
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

/** گروهِ تراشه با برچسب (مثلِ برچسبِ کادر) و خطای درجا */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(label: String, error: Problem?, required: Boolean = true, chips: @Composable () -> Unit) {
  val c = PhoneV3.colors
  Column {
    Row(Modifier.padding(start = 2.dp, bottom = 8.dp)) {
      Text(label, style = V3Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.inkMuted)
      if (required) Text(" *", style = V3Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = PhoneV3Roles.errorText(c))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    AnimatedVisibility(error != null) {
      Row(Modifier.padding(start = 2.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = PhoneV3Roles.errorText(c), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(error?.let(::problemText).orEmpty(), style = V3Type.footnote, color = PhoneV3Roles.errorText(c))
      }
    }
  }
}

/** «سودِ هر عدد: ۲۰ ؋» — زیان با برچسبِ «زیان» و رنگِ خطا (رنگ تنها نیست) */
@Composable
private fun ProfitLine(row: ProductRowDraft) {
  val c = PhoneV3.colors
  val profit = ProductFormModel.unitProfit(row) ?: return
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
private fun Modifier.field(f: Field, hooks: FieldHooks): Modifier = composed {
  var had by remember { mutableStateOf(false) }
  this
    .focusRequester(hooks.focus(f))
    .onFocusChanged { s ->
      if (had && !s.hasFocus) hooks.onBlur(f)
      had = s.hasFocus
    }
}
