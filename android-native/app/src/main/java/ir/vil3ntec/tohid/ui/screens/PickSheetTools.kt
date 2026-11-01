package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPick
import ir.vil3ntec.tohid.core.model.PickSheetDto
import ir.vil3ntec.tohid.data.repo.PickingRepository
import ir.vil3ntec.tohid.print.rememberBagLabelPrint
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  ابزارهای برگهٔ بسته‌شده (پشتِ همان پرچمِ خاموشِ `shop.orders.picking`):
 *    • چاپِ برچسبِ کیسه‌ها روی چاپگرِ رسیدِ همین دستگاه (PCK-09، `core/pick/BagLabelPrint`)
 *    • عکسِ کیسه‌های بسته به‌عنوانِ مدرک (PCK-08): فقط پس از تأییدِ «فقط از بسته»، حداکثر ۳ (سرور می‌سنجد)
 *  پلتفرم (چاپگر، دوربین، فشرده‌سازی) پشتِ مرزها: `print/BagLabelsAndroid.kt` و `ParcelPhotoAndroid.kt`.
 */

private const val MAX_PHOTOS = 3

@Composable
internal fun PickPackedTools(s: PickSheetDto, repo: PickingRepository) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val printLabels = rememberBagLabelPrint()
  var note by remember(s.orderId) { mutableStateOf<Pair<Boolean, String>?>(null) }
  var busy by remember(s.orderId) { mutableStateOf(false) }
  var photos by remember(s.orderId) { mutableIntStateOf(-1) }
  var consent by remember(s.orderId) { mutableStateOf(false) }
  var parcelOnly by remember(s.orderId) { mutableStateOf(false) }
  var pick by remember(s.orderId) { mutableStateOf(false) }

  LaunchedEffect(s.orderId) { repo.photos(s.orderId).onSuccess { photos = it.photos.size } }

  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    if (s.labels.isNotEmpty()) {
      TohidSecondaryButton(StrPick.printLabels, enabled = !busy, onClick = {
        busy = true
        scope.launch {
          val msg = printLabels(s.orderNo, s.labels.map { it.qr to StrPick.labelCaption(s.orderNo, it.bag, it.of) })
          note = (msg == StrPick.printed) to msg
          busy = false
        }
      })
    }
    if (photos in 0 until MAX_PHOTOS) {
      TohidSecondaryButton(StrPick.photoTitle, enabled = !busy, onClick = { parcelOnly = false; consent = true })
    }
  }
  if (photos >= 0) Text(StrPick.photos(photos, MAX_PHOTOS), style = MaterialTheme.typography.labelSmall, color = c.muted)
  note?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger) }

  if (consent) {
    AlertDialog(
      onDismissRequest = { consent = false },
      containerColor = c.surfaceSolid,
      title = { Text(StrPick.photoTitle, color = c.text) },
      text = {
        Column {
          Row(Modifier.fillMaxWidth().clickable { parcelOnly = !parcelOnly }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = parcelOnly, onCheckedChange = { parcelOnly = it })
            Text(StrPick.photoConsent, style = MaterialTheme.typography.bodyMedium, color = c.text)
          }
        }
      },
      confirmButton = {
        TextButton(enabled = parcelOnly, onClick = { consent = false; pick = true }) { Text(StrPick.photoTake, color = c.primary) }
      },
      dismissButton = { TextButton(onClick = { consent = false }) { Text(Str.cancel, color = c.muted) } },
    )
  }
  ParcelPhotoSource(open = pick, note = StrPick.photoNote, onDismiss = { pick = false }) { bytes ->
    pick = false
    if (bytes == null) {
      note = false to StrPick.photoBad
      return@ParcelPhotoSource
    }
    busy = true
    scope.launch {
      repo.addPhoto(s.orderId, PickingRepository.newEventId(), parcelBase64(bytes), parcelDeviceTime())
        .onSuccess { photos = (photos.coerceAtLeast(0)) + 1; note = true to StrPick.photoSaved }
        .onFailure { note = false to it.userMessage }
      busy = false
    }
  }
}
