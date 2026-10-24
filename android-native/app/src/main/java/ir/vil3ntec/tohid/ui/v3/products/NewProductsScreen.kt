package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Mode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** ناوبریِ فرم‌های کالا — مسیرها را `ProductsRoutes` می‌دهد */
internal class ProductFormNav(
  val back: () -> Unit,
  /** اسکنِ بارکد برای نتیجه (`scan?for=product`) */
  val scan: (onCode: (String) -> Unit) -> Unit,
  /** برگهٔ دسته‌ها برای انتخاب (`products/categories?pick=1`) */
  val pickCategory: (current: String, onPicked: (String) -> Unit) -> Unit,
)

/**
 *  «کالای تازه» (`products/new?barcode=`): کارت‌های ردیف زیرِ هم، فقط یکی باز؛
 *  «+ افزودن ردیف» زیرِ آخرین کارت؛ نوارِ چسبانِ پایین «کالای آماده: ۲ از ۳» و
 *  «ذخیرهٔ همه» (تا یک ردیف آماده نشده خاموش). «ذخیره» یا همه را با هم ثبت می‌کند
 *  (`WarehouseEngine.addProducts`) یا به نخستین ردیفِ خراب می‌پرد. پس از ثبت
 *  `onSaved` با شناسهٔ کالاهای تازه.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewProductsScreen(
  store: ShopStore,
  data: ShopData,
  toasts: V3Toasts,
  barcode: String?,
  nav: ProductFormNav,
  /** ردیف‌های آغازین (عکس‌های صفحه، ورود از فایل) — وگرنه یک ردیفِ خالی با بارکدِ مسیر */
  seed: List<ProductRowDraft>? = null,
  /** خطاها از آغاز دیده شوند (همان حالِ پس از «ذخیره») */
  showErrors: Boolean = false,
  onSaved: (List<String>) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val initial = remember { seed ?: listOf(ProductRowDraft(key = 0, barcode = barcode.orEmpty())) }
  var rows by remember { mutableStateOf(initial) }
  var seq by remember { mutableIntStateOf((initial.maxOfOrNull { it.key } ?: 0) + 1) }
  var expanded by remember { mutableIntStateOf(initial.first().key) }
  var touched by remember { mutableStateOf(FormTouched(all = showErrors)) }
  var banner by remember { mutableStateOf<String?>(null) }
  var shake by remember { mutableStateOf(0 to -1) }
  var saving by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf(false) }
  var errorShake by remember { mutableIntStateOf(0) }
  val stagger = rememberV3Stagger()
  val firstKeys = remember { initial.map { it.key }.toSet() }
  val views = remember { HashMap<Int, BringIntoViewRequester>() }
  val focus = remember { HashMap<String, FocusRequester>() }
  val readyCount = ProductFormModel.readyCount(rows)

  fun update(key: Int, change: (ProductRowDraft) -> ProductRowDraft) { rows = ProductFormModel.update(rows, key, change) }

  fun jump(key: Int) {
    expanded = key
    shake = (shake.first + 1) to key
    scope.launch {
      delay(MotionTokens.MEDIUM.toLong())
      views[key]?.bringIntoView()
      val row = rows.firstOrNull { it.key == key } ?: return@launch
      ProductFormModel.firstField(ProductFormModel.errors(row, Mode.NEW))?.let { f -> runCatching { focus["$key:${f.name}"]?.requestFocus() } }
    }
  }

  fun save() {
    when (val check = ProductFormModel.check(rows)) {
      is ProductFormModel.Check.Broken -> {
        touched = touched.copy(all = true)
        banner = StrProducts.rowIncomplete(check.index + 1)
        jump(rows[check.index].key)
      }
      ProductFormModel.Check.Empty -> {
        touched = touched.copy(all = true)
        banner = StrProducts.needOneRow
        jump(rows.first().key)
      }
      is ProductFormModel.Check.Ok -> {
        val before = store.data.value
        when (val r = WarehouseEngine.addProducts(before, check.drafts, System.currentTimeMillis(), ::newId)) {
          is WarehouseEngine.Result.Failed -> { banner = r.message; errorShake++ }
          is WarehouseEngine.Result.Ok -> {
            banner = null
            saving = true
            persist(scope, store, r.data)
            Haptics.success(context)
            val ids = ProductsListModel.added(before, r.data)
            RecentlyAdded.mark(ids)
            toasts.show(StrProducts.savedMany(ids.size))
            done = true
            scope.launch {
              if (Motion.enabled) delay(SUCCESS_MS)
              onSaved(ids)
            }
          }
        }
      }
    }
  }

  Box(Modifier.fillMaxSize()) {
    V3Form(
      title = StrProducts.newProduct,
      primary = StrProducts.saveAll,
      onPrimary = ::save,
      onBack = nav.back,
      primaryEnabled = readyCount > 0 && !done,
      loading = saving && !done,
      summary = StrProducts.readySummary(readyCount, rows.size),
      dirty = rows != initial && !done,
    ) {
      DraftReveal(banner != null) {
        V3Message(V3Tone.ERROR, StrProducts.saveFailed, Modifier.padding(top = 12.dp).draftShake(errorShake), body = banner.orEmpty())
      }
      Column(Modifier.draftSize()) {
        rows.forEachIndexed { index, row -> key(row.key) {
          val req = views.getOrPut(row.key) { BringIntoViewRequester() }
          val hooks = FieldHooks(
            onChange = { change -> update(row.key, change) },
            onBlur = { f -> touched = touched.touch(row.key, f) },
            onScan = { nav.scan { code -> update(row.key) { it.copy(barcode = code) }; Haptics.confirm(context) } },
            onNewCategory = { nav.pickCategory(row.category) { cat -> update(row.key) { it.copy(category = cat) } } },
            focus = { f -> focus.getOrPut("${row.key}:${f.name}") { FocusRequester() } },
          )
          Spacer(Modifier.height(12.dp))
          ProductRowCard(
            number = index + 1,
            row = row,
            expanded = expanded == row.key,
            ready = ProductFormModel.ready(row),
            canRemove = rows.size > 1,
            shake = if (shake.second == row.key) shake.first else 0,
            onExpand = { expanded = row.key },
            onDuplicate = {
              val copy = ProductFormModel.duplicate(row, seq++)
              rows = rows.toMutableList().also { it.add(index + 1, copy) }
              expanded = copy.key
            },
            onRemove = {
              val at = index
              rows = rows.filterNot { it.key == row.key }
              if (expanded == row.key) expanded = rows.getOrNull((at - 1).coerceAtLeast(0))?.key ?: -1
              toasts.undo(StrProducts.rowRemoved) {
                rows = rows.toMutableList().also { it.add(at.coerceAtMost(it.size), row) }
                expanded = row.key
              }
            },
            //  ورودِ پلکانیِ کارت‌های آغازین؛ کارتِ تازه (افزودن/تکثیر) همان لحظه بالا می‌آید
            modifier = Modifier.bringIntoViewRequester(req).v3Stagger(stagger, row.key, if (row.key in firstKeys) index else 0),
          ) {
            ProductFieldGroups(
              row = row,
              errors = touched.visible(row.key, ProductFormModel.errors(row, Mode.NEW).takeUnless { ProductFormModel.untouched(row) && rows.size > 1 }.orEmpty()),
              categories = data.productCategories,
              units = data.productUnits,
              hooks = hooks,
              section = CardSection,
              mode = Mode.NEW,
              nameHelper = if (index == 0 && rows.size == 1 && row.name.isBlank()) StrProducts.firstRowHint else null,
            )
          }
        } }
      }
      Spacer(Modifier.height(12.dp))
      V3Button(
        StrProducts.addRow,
        {
          val next = ProductRowDraft(key = seq++)
          rows = rows + next
          expanded = next.key
          scope.launch { delay(MotionTokens.MEDIUM.toLong()); views[next.key]?.bringIntoView() }
        },
        Modifier.fillMaxWidth(),
        kind = V3ButtonKind.SECONDARY,
        icon = Icons.Outlined.Add,
      )
      Spacer(Modifier.height(16.dp))
    }
    V3SuccessCheck(done, Modifier.align(Alignment.Center))
  }
}

/** درنگِ تیکِ موفقیت پیش از بستنِ فرم (کیت: ۳۵۰ms + کمی دیدن) */
internal const val SUCCESS_MS = 520L
