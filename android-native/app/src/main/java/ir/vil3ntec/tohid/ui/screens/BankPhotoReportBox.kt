package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrImagePages
import ir.vil3ntec.tohid.core.image.BankPhotoReport
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 *  §۳۷.۷ «گزارشِ عکسِ اشتباه یا بستهٔ قدیمی» زیرِ کارتِ منبعِ عکس — فقط برای عکسِ مرکزیِ دیده‌شده
 *  (`BankPhotoReport.reportable`)؛ نوع از فهرستِ سرور، یادداشتِ اختیاری، یک بار برای همین کارت (پس از «رسید»
 *  دکمه می‌رود). عکس عوض نمی‌شود — مدیرِ بانک گزارش را در پنل می‌بیند.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankPhotoReportBox(imageId: String?, send: suspend (imageId: String, kind: String, note: String) -> Unit) {
  val id = imageId ?: return
  val scope = rememberCoroutineScope()
  var open by remember(id) { mutableStateOf(false) }
  var kind by remember(id) { mutableStateOf("") }
  var note by remember(id) { mutableStateOf("") }
  var busy by remember(id) { mutableStateOf(false) }
  var sent by remember(id) { mutableStateOf(false) }
  var problem by remember(id) { mutableStateOf<String?>(null) }
  if (sent) { StatusPill(StrImagePages.reportSent, PillTone.SUCCESS); return }
  if (!open) { OutlinedButton(onClick = { open = true }) { Text(StrImagePages.reportPhoto) }; return }
  Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(StrImagePages.reportPhoto, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      BankPhotoReport.KINDS.forEach { k ->
        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(labelOf(k)) })
      }
    }
    OutlinedTextField(
      value = note, onValueChange = { note = it.take(BankPhotoReport.NOTE_MAX) }, label = { Text(StrImagePages.reportNote) },
      modifier = Modifier.fillMaxWidth(), maxLines = 3,
    )
    problem?.let { StatusPill(it, PillTone.DANGER) }
    OutlinedButton(
      onClick = {
        busy = true; problem = null
        scope.launch {
          try {
            send(id, kind, note)
            sent = true
          } catch (c: CancellationException) {
            throw c
          } catch (f: ApiFailure) {
            problem = if (f is ApiFailure.Offline || f is ApiFailure.Timeout) StrImagePages.offlineRetry else f.userMessage
          } catch (e: Exception) {
            problem = StrImagePages.notDone
          } finally {
            busy = false
          }
        }
      },
      enabled = !busy && BankPhotoReport.body(kind, note) != null,
    ) { Text(StrImagePages.reportSend) }
  }
}

private fun labelOf(kind: String): String = when (kind) {
  "wrong_product" -> StrImagePages.reportWrongProduct
  "old_packaging" -> StrImagePages.reportOldPackaging
  "quality" -> StrImagePages.reportQuality
  else -> StrImagePages.reportOther
}
