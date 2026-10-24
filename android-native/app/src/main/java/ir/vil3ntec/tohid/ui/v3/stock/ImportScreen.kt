package ir.vil3ntec.tohid.ui.v3.stock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ProductImport
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  `stock/import` با انتخاب‌گرِ فایلِ سیستم: بارِ اول خودش باز می‌شود؛ فایل خوانده و
 *  `ProductImport.plan` می‌شود (روی رشتهٔ پس‌زمینه) و پیش‌نمایش می‌آید.
 */
@Composable
fun StockImportRoute(data: ShopData, onSave: (ProductImport.Plan) -> String?, onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var state by remember { mutableStateOf<ImportState>(ImportState.Idle) }
  var asked by rememberSaveable { mutableStateOf(false) }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val text = runCatching {
        withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
      }.getOrNull()
      state = withContext(Dispatchers.Default) { ImportModel.read(data, text) }
    }
  }
  LaunchedEffect(Unit) { if (!asked) { asked = true; picker.launch("*/*") } }
  ImportScreen(state, onPick = { picker.launch("*/*") }, onSave = onSave, onBack = onBack)
}

/**
 *  پیش‌نمایشِ ورود از فایل: کارتِ جمع («۴۰ کالا آمادهٔ ثبت · ۳ ردیف ثبت نمی‌شود»)، ردیف‌های
 *  ردشده با دلیل (۵۰ تای اول + «و n ردیفِ دیگر»)، کالاهای آماده، و دکمهٔ چسبانِ «ثبت ۴۰
 *  کالا». فایلِ ناخوانا یا بی ستونِ نام ⇐ حالتِ خطا با «انتخابِ فایلِ دیگر».
 */
@Composable
fun ImportScreen(state: ImportState, onPick: () -> Unit, onSave: (ProductImport.Plan) -> String?, onBack: () -> Unit) {
  val c = PhoneV3.colors
  var failure by remember(state) { mutableStateOf<String?>(null) }
  val plan = (state as? ImportState.Ready)?.plan
  val stagger = rememberV3Stagger(state)
  V3Scaffold(
    title = StrStock.importTitle,
    onBack = onBack,
    largeTitle = false,
    notice = null,
    bottomBar = if (plan == null) null else ({
      V3FormBar(
        primary = StrStock.importSave(money(plan.drafts.size.toDouble())),
        onPrimary = { failure = onSave(plan) },
        primaryEnabled = plan.drafts.isNotEmpty(),
        secondary = StrStock.pickOtherFile,
        onSecondary = onPick,
      )
    }),
  ) { pad ->
    when (state) {
      ImportState.Idle -> V3EmptyState(Icons.Outlined.UploadFile, StrStock.importIntroTitle, body = StrStock.importIntroBody, action = StrStock.pickFile, onAction = onPick)
      is ImportState.Failed -> V3EmptyState(Icons.Outlined.ErrorOutline, state.message, body = StrStock.importIntroBody, action = StrStock.pickOtherFile, onAction = onPick)
      is ImportState.Ready -> LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(0.dp),
      ) {
        val p = state.plan
        item(key = "summary") {
          V3Message(
            if (p.skipped.isEmpty()) V3Tone.SUCCESS else V3Tone.WARNING,
            StrStock.importReady(money(p.drafts.size.toDouble())),
            Modifier.padding(top = 8.dp).v3Stagger(stagger, "summary", 0),
            body = p.skipped.takeIf { it.isNotEmpty() }?.let { StrStock.importSkipped(money(it.size.toDouble())) },
          )
          failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 10.dp).v3Shake(it)) }
        }
        if (p.skipped.isNotEmpty()) item(key = "skipped") {
          V3Section(StrStock.skippedTitle, modifier = Modifier.v3Stagger(stagger, "skipped", 1)) {
            ImportModel.shownSkips(p).forEachIndexed { i, sk ->
              if (i > 0) V3Divider(start = 64.dp)
              V3ListRow(
                title = listOf(StrStock.skippedRow(money(sk.row.toDouble())), sk.name.takeIf { it.isNotBlank() }?.let { "«$it»" }).filterNotNull().joinToString(" "),
                subtitle = sk.reason,
                icon = Icons.Outlined.WarningAmber,
                chevron = false,
              )
            }
            val hidden = ImportModel.hiddenSkips(p)
            if (hidden > 0) {
              V3Divider(start = 64.dp)
              V3ListRow(StrStock.moreSkipped(money(hidden.toDouble())), chevron = false, tile = false)
            }
          }
        }
        if (p.drafts.isNotEmpty()) item(key = "preview") {
          V3Section(StrStock.previewTitle, modifier = Modifier.v3Stagger(stagger, "preview", 2)) {
            ImportModel.shownDrafts(p).forEachIndexed { i, draft ->
              if (i > 0) V3Divider(start = 64.dp)
              V3ListRow(
                title = draft.name,
                subtitle = listOf(draft.category, draft.unit).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                leading = { V3ProductThumb("import-$i", draft.name, size = 40.dp) },
                value = StrStock.salePrice(money(draft.salePrice.coerceAtLeast(0.0))),
                valueColor = PhoneV3Roles.brandText(c),
                chevron = false,
              )
            }
            val more = ImportModel.hiddenDrafts(p)
            if (more > 0) {
              V3Divider(start = 64.dp)
              V3ListRow(StrStock.moreDrafts(money(more.toDouble())), chevron = false, tile = false)
            }
          }
        }
      }
    }
  }
}
