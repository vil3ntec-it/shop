package ir.vil3ntec.tohid.ui.v3.stock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.ReportCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  ذخیرهٔ یک برگهٔ CSV با انتخاب‌گرِ «ذخیره در…» ِ سیستم — همان راهِ صفحهٔ پیشینِ انبار
 *  (`ReportCsv.text` با UTF-8). `onDone(true)` یعنی نوشته شد؛ لغوِ کاربر چیزی نمی‌گوید.
 */
@Composable
internal fun rememberCsvExport(onDone: (Boolean) -> Unit): (ReportCsv.Sheet) -> Unit {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var pending by remember { mutableStateOf<ReportCsv.Sheet?>(null) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    val sheet = pending
    pending = null
    if (uri == null || sheet == null) return@rememberLauncherForActivityResult
    scope.launch {
      val ok = runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { it.write(ReportCsv.text(sheet).toByteArray(Charsets.UTF_8)) } ?: error("no stream")
        }
      }.isSuccess
      onDone(ok)
    }
  }
  return { sheet -> pending = sheet; launcher.launch("${sheet.name}.csv") }
}
