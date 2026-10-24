package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.animation.AnimatedVisibility
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
  val focus = remember { HashMap<String, FocusRequester>() }
  val errors = ProductFormModel.errors(row, Mode.EDIT)

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
      if (first == ProductFormModel.Field.UNIT_FACTOR) more = true
      scope.launch { delay(120); runCatching { focus[first.name]?.requestFocus() } }
      return
    }
    val now = System.currentTimeMillis()
    when (val r = WarehouseEngine.editProduct(store.data.value, productId, ProductFormModel.draftEdit(row), todayIso(), now, ::newId)) {
      is WarehouseEngine.Result.Failed -> banner = r.message
      is WarehouseEngine.Result.Ok -> {
        banner = null
        persist(scope, store, r.data)
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
      dirty = row != initial && !done,
    ) {
      banner?.let { V3Message(V3Tone.ERROR, StrProducts.saveFailed, Modifier.padding(top = 12.dp), body = it) }
      val visible = touched.visible(0, errors)
      ProductFieldGroups(row, visible, data.productCategories, data.productUnits, hooks, PageSection, Mode.EDIT)
      Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        CollapsibleHeader(StrProducts.groupMore, more) { more = !more }
        AnimatedVisibility(more) {
          V3Section(null, padding = PaddingValues(16.dp)) { MoreFields(row, visible, data.suppliers, hooks) }
        }
      }
      Spacer(Modifier.height(16.dp))
    }
    V3SuccessCheck(done, Modifier.align(Alignment.Center))
  }
}

/** «بیشتر» از آغاز باز است وقتی کالا چیزی در آن دارد */
private fun hasMore(r: ProductRowDraft): Boolean =
  r.subcategory.isNotBlank() || r.saleUnit.isNotBlank() || r.supplierId.isNotBlank() || !r.active
