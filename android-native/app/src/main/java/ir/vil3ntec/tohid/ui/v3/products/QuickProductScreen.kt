package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrNewProduct
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.product.ProductSuggest
import ir.vil3ntec.tohid.data.DEFAULT_UNITS
import ir.vil3ntec.tohid.data.OpeningStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Field
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Mode
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Problem
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** نشانِ آزمونِ فرمِ ساده (فقط `testTag`؛ صفحه‌خوان نمی‌خواند) */
object QuickProductSpec {
  const val MORE_TAG = "quick-product-more"
}

/**
 *  «کالای تازه» ِ گوشی (`products/new`، دکمهٔ شناورِ «کالاها»؛ ۱۴۰۵/۰۸/۲۴، صاحب‌کار: فرمِ چندردیفی «برای افزودنِ یک کالا خیلی
 *  سنگین است»): **یک** کالا — دو کادرِ لازم بالا (نامِ کالا، قیمتِ فروش) و زیرشان «بیشتر» ِ جمع‌شده با یک خطِ کم‌رنگ از آنچه
 *  درونش است؛ باز ⇒ قیمتِ خرید (و سودِ هر واحد)، دسته‌بندی | واحد (کادرِ کشویی با «+ تازه» تهِ فهرست)، کد/بارکد با اسکن، موجودی
 *  در انبار و تاریخِ تولید | انقضا. واحدِ خالی ⇐ نخستین واحدِ دکان (`ProductFormModel.draftQuick`)؛ قاعده‌ها `Mode.QUICK`.
 *  «حداقلِ موجودی» و «قیمتِ عمده» مثلِ فرمِ چندردیفی این‌جا نیستند («ویرایش کالا» دارد). ذخیره همان `OpeningStock.save` ِ یک ردیف
 *  (کالا و، اگر موجودی نوشته شده، ورودِ انبارش با یک `ShopStore.save`)؛ پس از نخستین ثبت دوباره نمی‌نویسد. «ثبتِ چند کالا با هم»
 *  (فرمِ چندردیفی) در «…» ِ فهرستِ کالاها و انبار است. پس از ثبت `onSaved(ids, stocked)`.
 */
@Composable
internal fun QuickProductScreen(
  store: ShopStore,
  data: ShopData,
  toasts: V3Toasts,
  barcode: String?,
  nav: ProductFormNav,
  /** پیش‌نویسِ آغازین (عکس‌ها و آزمون) — وگرنه خالی با بارکدِ مسیر */
  seed: ProductRowDraft? = null,
  /** «بیشتر» از آغاز باز (عکس‌ها)؛ null ⇒ فقط اگر چیزی در آن پر است (بارکدِ اسکن‌شده) */
  startMore: Boolean? = null,
  onSaved: (ids: List<String>, stocked: Boolean) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val defaultUnit = remember(data.productUnits) { data.productUnits.firstOrNull { it.isNotBlank() } ?: DEFAULT_UNITS.first() }
  val initial = remember { seed ?: ProductRowDraft(barcode = barcode.orEmpty(), unit = defaultUnit) }
  var row by remember { mutableStateOf(initial) }
  var more by remember { mutableStateOf(startMore ?: ProductFormModel.quickHasMore(initial)) }
  var touched by remember { mutableStateOf(FormTouched()) }
  var banner by remember { mutableStateOf<String?>(null) }
  var saving by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf(false) }
  var shake by remember { mutableIntStateOf(0) }
  val focus = remember { HashMap<Field, FocusRequester>() }
  val suggest = rememberCategorySuggest(data)
  val today = remember { ir.vil3ntec.tohid.todayIso() }
  val errors = ProductFormModel.errors(row, Mode.QUICK)
  val visible = touched.visible(0, errors)

  val hooks = FieldHooks(
    onChange = { change -> row = change(row) },
    onBlur = { f -> touched = touched.touch(0, f) },
    onScan = { nav.scan { code -> row = row.copy(barcode = code); Haptics.confirm(context) } },
    focus = { f -> focus.getOrPut(f) { FocusRequester() } },
  )

  fun save() {
    //  دو بار زدن پیش از بسته شدنِ فرم: نخستین ثبت بس است
    if (saving || done) return
    //  از همین حالِ فرم (نه مقدارِ ساخته‌شده در ترکیبِ پیشین)
    val first = ProductFormModel.quickFirst(ProductFormModel.errors(row, Mode.QUICK))
    if (first != null) {
      touched = touched.copy(all = true)
      shake++
      if (first in ProductFormModel.QUICK_MORE) more = true
      scope.launch { delay(if (first in ProductFormModel.QUICK_MORE) 220 else 80); runCatching { focus[first]?.requestFocus() } }
      return
    }
    val before = store.data.value
    val one = ProductFormModel.quickRow(row, defaultUnit)
    when (val r = OpeningStock.save(before, listOf(one), today, System.currentTimeMillis(), ::newId)) {
      is WarehouseEngine.Result.Failed -> { banner = r.message; shake++ }
      is WarehouseEngine.Result.Ok -> {
        banner = null
        saving = true
        persist(scope, store, before, r.data)
        Haptics.success(context)
        val ids = ProductsListModel.added(before, r.data)
        RecentlyAdded.mark(ids)
        val stocked = one.stock != null
        val name = row.name.trim()
        toasts.show(if (stocked) StrNewProduct.savedOneWithStock(name) else StrNewProduct.savedOne(name))
        done = true
        scope.launch {
          if (Motion.enabled) delay(SUCCESS_MS)
          onSaved(ids, stocked)
        }
      }
    }
  }

  Box(Modifier.fillMaxSize()) {
    V3Form(
      title = StrProducts.newProduct,
      primary = StrKit.save,
      onPrimary = ::save,
      onBack = nav.back,
      primaryEnabled = !done,
      loading = saving && !done,
      dirty = row != initial && !done,
    ) {
      V3Collapse(banner != null) {
        V3Message(V3Tone.ERROR, StrProducts.saveFailed, Modifier.padding(top = 12.dp).v3Shake(shake.takeIf { it > 0 }), body = banner.orEmpty())
      }
      Column(Modifier.v3Shake(shake.takeIf { it > 0 && banner == null })) {
        V3Section(null, padding = PaddingValues(16.dp)) {
          QuickRequiredFields(row, visible, hooks, suggest)
        }
        V3Section(null, padding = PaddingValues(0.dp)) {
          QuickMoreHeader(open = more, summary = moreSummary(row, defaultUnit, today)) { more = !more }
          V3Collapse(more) {
            Column(Modifier.fillMaxWidth()) {
              V3Divider()
              Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                QuickMoreFields(row, visible, data.productCategories, data.productUnits, hooks, today)
              }
            }
          }
        }
      }
      Spacer(Modifier.height(16.dp))
    }
    V3SuccessCheck(done, Modifier.align(Alignment.Center), haptic = false)
  }
}

/** دو کادرِ لازم: نامِ کالا (با دستهٔ پیشنهادیِ AI-06 تا دسته خالی است) و قیمتِ فروش */
@Composable
private fun QuickRequiredFields(
  row: ProductRowDraft,
  errors: Map<Field, Problem>,
  hooks: FieldHooks,
  suggest: ((String) -> List<ProductSuggest.Pick>)?,
) {
  val picks = remember(row.name, row.category, suggest) {
    if (suggest == null || row.category.isNotBlank()) emptyList() else suggest(row.name)
  }
  V3TextField(
    row.name, { v -> hooks.onChange { it.copy(name = v) } }, StrProducts.name,
    modifier = Modifier.fillMaxWidth().field(Field.NAME, hooks),
    placeholder = StrProducts.namePlaceholder, error = errors[Field.NAME]?.let(::problemText), required = true,
  )
  V3Collapse(picks.isNotEmpty()) {
    CategorySuggestions(picks) { cat -> hooks.onChange { it.copy(category = cat) } }
  }
  Spacer(Modifier.height(12.dp))
  V3TextField(
    row.sale, { v -> hooks.onChange { it.copy(sale = v) } }, StrProducts.salePrice,
    modifier = Modifier.fillMaxWidth().field(Field.SALE, hooks), kind = V3FieldKind.MONEY,
    error = errors[Field.SALE]?.let(::problemText), required = true,
  )
}

/**
 *  سرِ «بیشتر»: عنوان و یک خطِ کم‌رنگ — بسته و خالی ⇒ آنچه درونش است («قیمتِ خرید، دسته، …»)، پر ⇒ همان مقدارها («خرید ۱۰۰ ؋ ·
 *  خوراکی · ۲۴ عدد») تا بی باز کردن دیده شوند. دکمهٔ تمام‌پهنا با حالتِ «باز/بسته» برای صفحه‌خوان.
 */
@Composable
private fun QuickMoreHeader(open: Boolean, summary: String, onToggle: () -> Unit) {
  val c = PhoneV3.colors
  val turn by animateFloatAsState(if (open) 180f else 0f, MotionSpec.indicator(), label = "quickMore")
  Row(
    Modifier
      .fillMaxWidth()
      .testTag(QuickProductSpec.MORE_TAG)
      .v3Pressable(label = StrProducts.groupMore, onClick = onToggle)
      .semantics { stateDescription = if (open) StrA11y.expanded else StrA11y.collapsed }
      .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(StrProducts.groupMore, style = V3Type.rowTitle, color = c.ink)
      Text(summary, style = V3Type.rowHelp, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = c.icon, modifier = Modifier.graphicsLayer { rotationZ = turn })
  }
}

/** خطِ کم‌رنگِ زیرِ «بیشتر»: مقدارهای پرشده (واحدِ پیش‌فرض گفته نمی‌شود)، وگرنه فهرستِ آنچه درونش است */
internal fun moreSummary(row: ProductRowDraft, defaultUnit: String, today: String): String {
  val unit = row.unit.ifBlank { defaultUnit }
  val filled = listOfNotNull(
    row.purchase.toDoubleOrNull()?.let { "${StrProducts.purchasePrice} ${money(it)} ؋" },
    row.category.takeIf { it.isNotBlank() },
    row.unit.takeIf { it.isNotBlank() && it != defaultUnit },
    row.barcode.trim().takeIf { it.isNotEmpty() },
    NewProductStock.qty(row)?.takeIf { it > 0 }?.let { "${StrNewProduct.stock} ${qty(it)} $unit" },
    NewProductStock.iso(row.expiry)?.takeIf { it.isNotEmpty() }?.let { "${StrStock.expiry} ${formatDate(it)}" },
  )
  return if (filled.isEmpty()) StrNewProduct.moreHint else filled.joinToString(" · ")
}

/** درونِ «بیشتر»: قیمتِ خرید و سود، دسته | واحد (کشویی)، کد/بارکد با اسکن، موجودی در انبار و تاریخِ تولید | انقضا */
@Composable
private fun QuickMoreFields(
  row: ProductRowDraft,
  errors: Map<Field, Problem>,
  categories: List<String>,
  units: List<String>,
  hooks: FieldHooks,
  today: String,
) {
  var adding by remember { mutableStateOf<Field?>(null) }
  val unit = row.unit.ifBlank { StrProducts.unitWord }
  V3TextField(
    row.purchase, { v -> hooks.onChange { it.copy(purchase = v) } }, StrProducts.purchasePrice,
    modifier = Modifier.fillMaxWidth().field(Field.PURCHASE, hooks), kind = V3FieldKind.MONEY, error = errors[Field.PURCHASE]?.let(::problemText),
  )
  ProfitLine(row)
  Spacer(Modifier.height(12.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    ChoiceDropdown(
      StrProducts.category, row.category, categories, { v -> hooks.onChange { it.copy(category = v) } },
      Modifier.weight(1f).field(Field.CATEGORY, hooks), error = errors[Field.CATEGORY]?.let(::problemText),
      addLabel = StrProducts.newCategory, onAdd = { adding = Field.CATEGORY }, onClosed = { hooks.onBlur(Field.CATEGORY) },
    )
    ChoiceDropdown(
      StrProducts.unit, row.unit, units, { v -> hooks.onChange { it.copy(unit = v) } },
      Modifier.weight(1f).field(Field.UNIT, hooks), error = errors[Field.UNIT]?.let(::problemText),
      addLabel = StrProducts.newUnit, onAdd = { adding = Field.UNIT }, onClosed = { hooks.onBlur(Field.UNIT) },
    )
  }
  Spacer(Modifier.height(12.dp))
  V3TextField(
    row.barcode, { v -> hooks.onChange { it.copy(barcode = v) } }, StrNewProduct.code, Modifier.fillMaxWidth(),
    kind = V3FieldKind.BARCODE, placeholder = StrProducts.barcodePlaceholder, onScan = hooks.onScan,
  )
  Spacer(Modifier.height(12.dp))
  V3TextField(
    row.stock, { v -> hooks.onChange { it.copy(stock = v) } }, StrNewProduct.stock,
    Modifier.fillMaxWidth().field(Field.STOCK, hooks), kind = V3FieldKind.NUMBER, suffix = row.unit.ifBlank { null },
    helper = NewProductStock.total(row)?.let { StrNewProduct.stockHelper(qty(NewProductStock.qty(row) ?: 0.0), unit, money(it)) },
    error = errors[Field.STOCK]?.let(::problemText),
  )
  Spacer(Modifier.height(12.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    DateBox(row.made, { v -> hooks.onChange { it.copy(made = v) } }, StrStock.made, Field.MADE, errors, hooks, today, Modifier.weight(1f))
    DateBox(row.expiry, { v -> hooks.onChange { it.copy(expiry = v) } }, StrStock.expiry, Field.EXPIRY, errors, hooks, today, Modifier.weight(1f), expiryNote(row, today))
  }
  when (adding) {
    Field.CATEGORY -> NewValueSheet(StrProducts.newCategory, StrProducts.category, onDismiss = { adding = null }) { v -> hooks.onChange { it.copy(category = v) } }
    Field.UNIT -> NewValueSheet(StrProducts.newUnit, StrProducts.unit, onDismiss = { adding = null }) { v -> hooks.onChange { it.copy(unit = v) } }
    else -> Unit
  }
}
