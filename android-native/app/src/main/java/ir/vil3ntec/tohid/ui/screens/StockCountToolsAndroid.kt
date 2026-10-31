package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrCount
import ir.vil3ntec.tohid.core.stock.CountMerge
import ir.vil3ntec.tohid.data.CountToolsPref
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  مرزِ پلتفرمِ «انبارگردانیِ تازه» (INV-06/07/08): کلیدِ کور، نامِ این دستگاه، و فرستادن/خواندنِ برگه با فایل
 *  (بی اینترنت؛ پیام‌رسان، کابل یا پوشهٔ مشترک). برگهٔ این شمارش شناسهٔ خودش را دارد تا فرستادنِ دوباره همان
 *  برگه را جایگزین کند، نه دو بار جمع.
 */
@Composable
fun StockCountToolsWired(
  context: android.content.Context,
  d: ShopData,
  onDismiss: () -> Unit,
  onExport: (ReportCsv.Sheet) -> Unit,
  onSave: (Map<String, Double>, String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val blind = remember { CountToolsPref.blind(context) }
  val device = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim() }
  val sheetId = remember { newId() }
  var imported by remember { mutableStateOf(listOf<CountMerge.Sheet>()) }
  var notice by remember { mutableStateOf<String?>(null) }
  var pending by remember { mutableStateOf<String?>(null) }

  val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
    val text = pending
    pending = null
    if (uri == null || text == null) return@rememberLauncherForActivityResult
    scope.launch {
      notice = runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
        }
        null
      }.getOrElse { StrCount.writeFailed }
    }
  }
  val openText = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val text = runCatching {
        withContext(Dispatchers.IO) {
          //  برگه کوچک است؛ فایلِ بزرگ‌تر از ۲ مگابایت برگه نیست
          context.contentResolver.openInputStream(uri)?.use { s ->
            val max = 2 * 1024 * 1024
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() <= max) {
              val n = s.read(buf)
              if (n < 0) break
              out.write(buf, 0, n)
            }
            if (out.size() > max) null else out.toByteArray().toString(Charsets.UTF_8)
          }
        }
      }.getOrNull()
      val sheet = text?.let { CountMerge.decode(it) }
      notice = when {
        text == null -> StrCount.readFailed
        sheet == null -> StrCount.badSheet
        sheet.sheetId == sheetId -> StrCount.ownSheet
        else -> {
          imported = imported.filter { it.sheetId != sheet.sheetId } + sheet
          StrCount.sheetAdded(sheet.device.ifBlank { StrCount.thisDevice })
        }
      }
    }
  }

  StockCountToolsSheet(
    d = d,
    blind = blind,
    ownSheetId = sheetId,
    imported = imported,
    notice = notice,
    onDismiss = onDismiss,
    onExport = onExport,
    onShare = { own ->
      pending = CountMerge.encode(CountMerge.Sheet(sheetId, device.ifBlank { StrCount.thisDevice }, System.currentTimeMillis(), own))
      saveText.launch("${StrCount.fileName(todayIso())}.txt")
    },
    onImport = { openText.launch("*/*") },
    onRemoveSheet = { id -> imported = imported.filter { it.sheetId != id } },
    onSave = onSave,
  )
}

/** بخشِ «انبارگردانیِ تازه» با خواندن و نوشتنِ `CountToolsPref` — تا `Settings` یک خط بخواهد */
@Composable
fun CountToolsSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(CountToolsPref.on(context)) }
  var blind by remember { mutableStateOf(CountToolsPref.blind(context)) }
  CountToolsSettings(
    on = on,
    blind = blind,
    onChange = { on = it; CountToolsPref.setOn(context, it) },
    onBlind = { blind = it; CountToolsPref.setBlind(context, it) },
  )
}
