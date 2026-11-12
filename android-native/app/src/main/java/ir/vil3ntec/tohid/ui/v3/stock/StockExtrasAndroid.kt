package ir.vil3ntec.tohid.ui.v3.stock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.UploadFile
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
import ir.vil3ntec.tohid.core.i18n.StrLabel
import ir.vil3ntec.tohid.core.i18n.StrImageChanges
import ir.vil3ntec.tohid.core.i18n.StrImageStudio
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.i18n.StrTransfer
import ir.vil3ntec.tohid.core.product.ImportTable
import ir.vil3ntec.tohid.data.CountShift
import ir.vil3ntec.tohid.data.CountToolsPref
import ir.vil3ntec.tohid.data.FileName
import ir.vil3ntec.tohid.data.ImageBank
import ir.vil3ntec.tohid.data.LabelToolsPref
import ir.vil3ntec.tohid.data.ProductImport
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.StockLedger
import ir.vil3ntec.tohid.data.StockPlacesPref
import ir.vil3ntec.tohid.data.StockTransferFlag
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.BulkProductSheet
import ir.vil3ntec.tohid.ui.screens.ImageBulkImportSheet
import ir.vil3ntec.tohid.ui.screens.LabelSheetWired
import ir.vil3ntec.tohid.ui.screens.ProductImportDialog
import ir.vil3ntec.tohid.ui.screens.ProductImportHistoryDialog
import ir.vil3ntec.tohid.ui.screens.StockBookNav
import ir.vil3ntec.tohid.ui.screens.StockBookScreen
import ir.vil3ntec.tohid.ui.screens.StockCountToolsWired
import ir.vil3ntec.tohid.ui.screens.StockPlacesEntry
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  مرزِ پلتفرمِ کارهای افزودهٔ «انبار» (`StockExtras`): تنظیم‌ها و نقشِ همین دستگاه، و میزبانیِ همان برگه‌ها و
 *  پنجره‌های پیشینِ `ui/screens` در مسیرهای نسخهٔ ۳ — بی رونوشتِ منطق؛ نوشتن با همان `StockWriter` ِ انبار.
 */

/** INV-01: «موجودی به تفکیکِ محل» در فهرستِ انبار — «محل‌های نگه‌داری» ِ همین دستگاه و دفترِ تازهٔ خاموش (روشن ⇒ محل‌ها در «دفترِ موجودی») */
@Composable
internal fun RouteScope.rememberStockPlacesOn(): Boolean {
  val context = LocalContext.current
  return remember(data.auditLog, StockLedger.serverFlag) { StockPlacesPref.places(context) && !StockLedger.isOn(data) }
}

/** «…» ِ انبار: کارهای دیدنیِ همین دستگاه و نقش */
@Composable
internal fun RouteScope.rememberStockExtras(): List<V3MenuItem> {
  val context = LocalContext.current
  val stockOn = remember(data.auditLog, StockLedger.serverFlag) { StockLedger.isOn(data) }
  val flags = remember(stockOn) {
    StockExtras.Flags(
      places = StockPlacesPref.places(context),
      repack = StockPlacesPref.repack(context),
      staff = ShopRole.current(context) == ShopRole.STAFF,
      stockOn = stockOn,
      labelTools = LabelToolsPref.on(context),
    )
  }
  //  IMG-PRO-17: «تغییرِ دسته‌ایِ عکس‌ها» فقط با بانکِ روشن (پرچمِ `shop.image_bank`؛ پرسشِ همان `/config`)
  LaunchedEffect(Unit) { if (ImageBank.enabled.value == null) ImageBank.refresh(context) }
  val imageChanges = ImageChangesGate.visible(ImageBank.enabled.value, flags.staff)
  //  INV-11: پرچمِ `shop.catalog` و `stock.move` (همان `/catalog/config`؛ حداکثر هر ده دقیقه یک بار) — خاموش ⇒ هیچ
  LaunchedEffect(Unit) { StockTransferFlag.refresh(context) }
  val stored = remember { StockTransferFlag.on(context) }
  val transfers = StockTransferFlag.visible.value ?: stored
  return StockExtras.of(flags.copy(imageChanges = imageChanges, transfers = transfers)).map { x ->
    when (x) {
      StockExtra.PLACES -> V3MenuItem(Icons.Outlined.SwapHoriz, StrReach.places) { nav.open(x.route) }
      StockExtra.LABELS -> V3MenuItem(Icons.Outlined.Print, StrLabel.openButton) { nav.open(x.route) }
      StockExtra.BOOK -> V3MenuItem(Icons.Outlined.MenuBook, StrReach.stockBook) { nav.open(x.route) }
      StockExtra.BULK_PRODUCTS -> V3MenuItem(Icons.AutoMirrored.Outlined.PlaylistAdd, StrReach.bulkProducts) { nav.open(x.route) }
      StockExtra.IMAGES -> V3MenuItem(Icons.Outlined.AddPhotoAlternate, StrReach.imageImport) { nav.open(x.route) }
      StockExtra.IMAGE_CHANGES -> V3MenuItem(Icons.Outlined.Collections, StrImageChanges.title) { nav.open(x.route) }
      StockExtra.IMAGE_STUDIO -> V3MenuItem(Icons.Outlined.AutoFixHigh, StrImageStudio.title) { nav.open(x.route) }
      StockExtra.TRANSFERS -> V3MenuItem(Icons.Outlined.LocalShipping, StrTransfer.title) { nav.open(x.route) }
    }
  }
}

/** مسیرِ «انبارگردانی» برای همین دستگاه (`StockExtras.countRoute`) */
@Composable
internal fun RouteScope.rememberCountRoute(): String {
  val context = LocalContext.current
  val stockOn = remember(data.auditLog, StockLedger.serverFlag) { StockLedger.isOn(data) }
  val tools = remember { CountToolsPref.on(context) }
  return StockExtras.countRoute(stockOn, tools)
}

/** PRD-20 روشن؟ (`ShopPolicy.productTools`) */
@Composable
internal fun rememberProductTools(): Boolean {
  val context = LocalContext.current
  return remember { ShopPolicy.productTools(context) }
}

/** INV-06/07/08 — انبارگردانیِ تازه (اسکن، چند دستگاه، شمارشِ کور)؛ ثبت با «اصلاحِ شمارش» (S20) و لحظهٔ شمارش (INV-10) */
@Composable
internal fun RouteScope.CountToolsRoute() {
  val context = LocalContext.current
  val writer = rememberStockWriter()
  val export = rememberCsvExport { ok -> if (ok) writer.info(StrStock.fileSaved) else writer.fail(StrStock.fileFailed) }
  StockCountToolsWired(
    context, data,
    onDismiss = { back() },
    onExport = export,
    onSave = { counted, at, note ->
      val shift = CountShift.of(data, at)
      val n = WarehouseEngine.countLines(data, counted, shift).size
      writer.commit(data, 
        WarehouseEngine.stockCount(data, counted, note, writer.today, writer.now, writer.newId, shift, WarehouseEngine.AdjustKind.COUNT, userId = writer.user, countedAt = at),
        StrStock.countSaved(money(n.toDouble())),
      ).let { failed -> if (failed != null) writer.fail(failed) else back() }
    },
  )
}

/** INV-20/21 — محل‌ها و انتقال، بسته‌بندیِ دوباره (دکمه‌ها و پنجره‌های خودِ `StockPlacesEntry`) */
@Composable
internal fun RouteScope.StockPlacesRoute() {
  val context = LocalContext.current
  val stockOn = remember(data.auditLog, StockLedger.serverFlag) { StockLedger.isOn(data) }
  val writer = rememberStockWriter()
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
    StockPlacesEntry(context, store, data, stockOn) { writer.info(it) }
  }
}

/** PRD-23/HW-04 — برگهٔ برچسب و بارکدِ داخلی */
@Composable
internal fun RouteScope.StockLabelsRoute() {
  val context = LocalContext.current
  val writer = rememberStockWriter()
  LabelSheetWired(context, data, onDismiss = { back() }) { result, done -> writer.commit(data, result, done)?.let(writer::fail) }
}

/** دفترِ موجودی (موتورِ تازه): `?tab=count` ⇒ همان زبانه */
@Composable
internal fun RouteScope.StockBookRoute() {
  val tab = StockExtras.bookTab(args.query("tab"))
  LaunchedEffect(tab) { if (tab != null) StockBookNav.tab.value = tab }
  StockBookScreen(store, data, snackbar) { nav.open(SettingsPaths.section(SettingsLayout.STOCK_MODE)) }
}

/** ثبتِ چند کالا با هم */
@Composable
internal fun RouteScope.BulkProductsRoute() {
  val writer = rememberStockWriter()
  BulkProductSheet(data, onDismiss = { back() }) { drafts ->
    val failed = writer.commit(data, WarehouseEngine.addProducts(data, drafts, writer.now, writer.newId), StrStock.imported(money(drafts.size.toDouble())))
    if (failed != null) writer.fail(failed) else back()
  }
}

/** ورودِ گروهیِ عکس‌ها (v3 ۳۶.۸) */
@Composable
internal fun RouteScope.ImageImportRoute() {
  ImageBulkImportSheet(store, data) { back() }
}

/** PRD-20 — سابقهٔ ورود از فایل */
@Composable
internal fun RouteScope.ImportHistoryRoute() {
  ProductImportHistoryDialog(ProductImport.history(data)) { back() }
}

/**
 *  PRD-20 — ورود از xlsx یا CSV با پیش‌نمایشِ کامل (`ProductImportDialog`)، فایلِ ردیف‌های رد و «سابقهٔ ورود از
 *  فایل»؛ خواندن با سقفِ `ImportTable.MAX_FILE_BYTES` پیش از رسیدن به حافظه (همان صفحهٔ انبارِ پیشین).
 */
@Composable
internal fun RouteScope.ProductToolsImportRoute(writer: StockWriter) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var file by remember { mutableStateOf<ProductImport.Picked?>(null) }
  var asked by rememberSaveable { mutableStateOf(false) }
  var pendingReport by remember { mutableStateOf<ByteArray?>(null) }
  val saveReport = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
  ) { uri ->
    val bytes = pendingReport
    pendingReport = null
    if (uri == null || bytes == null) return@rememberLauncherForActivityResult
    scope.launch {
      val ok = runCatching {
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream") }
      }.isSuccess
      if (ok) writer.info(StrReach.rejectSaved) else writer.fail(StrReach.fileNotSaved)
    }
  }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val read = runCatching {
        withContext(Dispatchers.IO) {
          val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (total <= ImportTable.MAX_FILE_BYTES) {
              val n = input.read(buf)
              if (n < 0) break
              out.write(buf, 0, n)
              total += n
            }
            out.toByteArray()
          } ?: error("no stream")
          FileName.of(context, uri) to ProductImport.read(bytes)
        }
      }.getOrNull()
      when (val r = read?.second) {
        null -> writer.fail(StrReach.fileUnreadable)
        is ImportTable.Read.Failed -> writer.fail(r.reason)
        is ImportTable.Read.Table -> file = ProductImport.Picked(read.first.ifBlank { StrStock.importTitle }, r.rows)
      }
    }
  }
  LaunchedEffect(Unit) { if (!asked) { asked = true; picker.launch("*/*") } }
  val imported = remember(data.auditLog) { data.auditLog.any { it.type == ProductImport.AUDIT_TYPE } }
  V3Scaffold(
    title = StrStock.importTitle,
    onBack = { back() },
    largeTitle = false,
    overflow = if (imported) listOf(V3MenuItem(Icons.Outlined.History, StrReach.importHistory) { nav.open(StockPaths.IMPORT_HISTORY) }) else emptyList(),
  ) { pad ->
    Column(Modifier.fillMaxSize().padding(pad)) {
      V3EmptyState(Icons.Outlined.UploadFile, StrStock.importTitle, body = StrReach.importPickBody, action = StrReach.importPick, onAction = { picker.launch("*/*") })
    }
  }
  file?.let { f ->
    ProductImportDialog(
      d = data,
      file = f,
      onDismiss = { file = null },
      onErrorReport = { bytes, name -> pendingReport = bytes; saveReport.launch(name) },
      onDone = { result ->
        file = null
        val failed = writer.commit(data, result, StrReach.imported)
        if (failed != null) writer.fail(failed) else back()
      },
    )
  }
}
