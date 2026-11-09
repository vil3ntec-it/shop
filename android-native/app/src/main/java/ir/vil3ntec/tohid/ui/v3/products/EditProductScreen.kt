package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.ui.v3.v3Shake
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.ProductCycle
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Mode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** هر گروه یک `V3Section` (سرخط بیرونِ کارت) — چیدمانِ فرمِ ویرایش */
private val PageSection: FieldSection = { title, content ->
  V3Section(title, padding = PaddingValues(16.dp)) { content() }
}

/**
 *  «ویرایش کالا» (`products/{id}/edit`): همان سه گروهِ کادرِ «کالای تازه» و گروهِ
 *  جمع‌شوندهٔ «بیشتر» (زیردسته، واحدِ فروشِ دوم، تأمین‌کننده، «فعال در فروش»).
 *  ذخیره = `WarehouseEngine.editProduct`؛ ردِ موتور بالای فرم، نه پنجره.
 */
@Composable
internal fun EditProductScreen(
  store: ShopStore,
  data: ShopData,
  toasts: V3Toasts,
  productId: String,
  nav: ProductFormNav,
  onSaved: () -> Unit,
) {
  val product = data.products.find { it.id == productId }
  if (product == null) {
    V3Scaffold(title = StrProducts.editTitle, onBack = nav.back, largeTitle = false) { pad ->
      Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
        V3EmptyState(Icons.Outlined.Inventory2, StrProducts.missingTitle, body = StrProducts.missingBody, action = StrKit.back, onAction = nav.back)
      }
    }
    return
  }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val initial = remember(productId) { ProductFormModel.of(product) }
  var row by remember(productId) { mutableStateOf(initial) }
  var touched by remember { mutableStateOf(FormTouched()) }
  var banner by remember { mutableStateOf<String?>(null) }
  var more by remember(productId) { mutableStateOf(hasMore(initial)) }
  var done by remember { mutableStateOf(false) }
  var shake by remember { mutableIntStateOf(0) }
  val stagger = rememberV3Stagger(productId)
  val focus = remember { HashMap<String, FocusRequester>() }
  //  AI-06: فقط وقتی دستهٔ کالا خالی است دیده می‌شود
  val suggest = rememberCategorySuggest(data)
  val errors = ProductFormModel.errors(row, Mode.EDIT)
  //  PRD-07: «واحدهای خرید» فقط با `ShopPolicy.productTools` (پیش‌فرض خاموش ⇒ همان فرمِ پیشین و `purchaseUnits = null`)
  val toolsOn = remember { ir.vil3ntec.tohid.data.ShopPolicy.productTools(context) }
  val canEditUnits = remember { ir.vil3ntec.tohid.data.ShopRole.canWrite(context, "products") }
  //  PRD-12: قیمتِ عمده فقط با «لیستِ قیمت در صندوق» (پیش‌فرض خاموش ⇒ همان فرم و `wholesalePrice = null`)
  val wholesaleOn = remember { ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs.on(context) }
  val unitsInitial = remember(productId) { ir.vil3ntec.tohid.ui.screens.purchaseUnitDrafts(product) }
  var units by remember(productId) { mutableStateOf(unitsInitial) }

  val hooks = FieldHooks(
    onChange = { change -> row = change(row) },
    onBlur = { f -> touched = touched.touch(0, f) },
    onScan = { nav.scan { code -> row = row.copy(barcode = code); Haptics.confirm(context) } },
    onNewCategory = { nav.pickCategory(row.category) { cat -> row = row.copy(category = cat) } },
    focus = { f -> focus.getOrPut(f.name) { FocusRequester() } },
  )

  fun save() {
    val first = ProductFormModel.firstField(errors)
    if (first != null) {
      touched = touched.copy(all = true)
      shake++
      if (first == ProductFormModel.Field.UNIT_FACTOR || first == ProductFormModel.Field.CYCLE) more = true
      scope.launch { delay(120); runCatching { focus[first.name]?.requestFocus() } }
      return
    }
    val now = System.currentTimeMillis()
    val base = store.data.value
    val draft = ProductFormModel.draftEdit(row).let { d0 -> d0.copy(purchaseUnits = if (toolsOn && canEditUnits) units else null, wholesalePrice = d0.wholesalePrice.takeIf { wholesaleOn }) }
    when (val r = WarehouseEngine.editProduct(base, productId, draft, todayIso(), now, ::newId)) {
      is WarehouseEngine.Result.Failed -> { banner = r.message; shake++ }
      is WarehouseEngine.Result.Ok -> {
        banner = null
        persist(scope, store, base, ProductCycle.apply(r.data, productId, row.cycle))   //  MKT-06
        Haptics.success(context)
        toasts.show(StrProducts.editSaved)
        done = true
        scope.launch {
          if (Motion.enabled) delay(SUCCESS_MS)
          onSaved()
        }
      }
    }
  }

  Box(Modifier.fillMaxSize()) {
    V3Form(
      title = StrProducts.editTitle,
      primary = StrProducts.saveChanges,
      onPrimary = ::save,
      onBack = nav.back,
      primaryEnabled = !done,
      dirty = (row != initial || units != unitsInitial) && !done,
    ) {
      V3Collapse(banner != null) {
        V3Message(V3Tone.ERROR, StrProducts.saveFailed, Modifier.padding(top = 12.dp).v3Shake(shake.takeIf { it > 0 }), body = banner.orEmpty())
      }
      val visible = touched.visible(0, errors)
      Column(Modifier.v3Shake(shake.takeIf { it > 0 }).v3Stagger(stagger, "fields", 0)) {
        ProductFieldGroups(row, visible, data.productCategories, data.productUnits, hooks, PageSection, Mode.EDIT, suggest = suggest, wholesale = wholesaleOn)
      }
      Column(Modifier.fillMaxWidth().padding(top = 8.dp).v3Stagger(stagger, "more", 1)) {
        CollapsibleHeader(StrProducts.groupMore, more) { more = !more }
        V3Collapse(more) {
          V3Section(null, padding = PaddingValues(16.dp)) { MoreFields(row, visible, data.suppliers, hooks) }
        }
      }
      if (toolsOn) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).v3Stagger(stagger, "units", 2)) {
          V3Section(null, padding = PaddingValues(16.dp)) {
            ir.vil3ntec.tohid.ui.screens.PurchaseUnitEditor(units, row.unit, data.suppliers, canEditUnits) { units = it }
          }
        }
      }
      Spacer(Modifier.height(16.dp))
    }
    V3SuccessCheck(done, Modifier.align(Alignment.Center), haptic = false)
  }
}

/** «بیشتر» از آغاز باز است وقتی کالا چیزی در آن دارد */
private fun hasMore(r: ProductRowDraft): Boolean =
  r.subcategory.isNotBlank() || r.saleUnit.isNotBlank() || r.supplierId.isNotBlank() || !r.active || r.cycle.isNotBlank()
