package ir.vil3ntec.tohid.ui.v3.settings.devices

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import java.io.File

/**
 *  برگهٔ «عکس از کجا؟» برای فرستادن به کامپیوتر: دوربین (عکسِ تازه) یا عکس‌های گوشی.
 *  جزءِ تازه (`Draft`) — در PR برای بردن به کیت نوشته شده تا برگهٔ عکسِ کالا هم
 *  همین را بکشد. راه‌اندازها بی‌قید ساخته می‌شوند (ترتیبِ Compose).
 */
@Composable
internal fun DraftPhotoSource(open: Boolean, onDismiss: () -> Unit, onPicked: (Uri) -> Unit) {
  val context = LocalContext.current
  var shot by remember { mutableStateOf<Uri?>(null) }
  //  اول `onPicked`، بعد بستن: صدازننده عکس را به کارِ خودش می‌برد
  val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) onPicked(uri)
    onDismiss()
  }
  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
    val target = shot
    shot = null
    if (taken && target != null) onPicked(target)
    onDismiss()
  }
  fun openCamera() {
    val uri = cameraTarget(context) ?: return
    shot = uri
    runCatching { camera.launch(uri) }
  }
  //  برنامه‌ای که `CAMERA` را در فهرستِ اجازه‌ها دارد برای عکسِ دوربینِ گوشی هم همان اجازه را لازم دارد
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) openCamera() else onDismiss() }
  if (!open) return
  V3Sheet(onDismiss = onDismiss, title = StrSettings.photoFrom) { _ ->
    SettingsNote(StrSettings.photoForPc)
    V3Card {
      V3ListRow(StrSettings.camera, subtitle = StrSettings.cameraNewPhoto, icon = Icons.Outlined.PhotoCamera, onClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else ask.launch(Manifest.permission.CAMERA)
      })
      V3Divider(start = 64.dp)
      V3ListRow(StrSettings.gallery, subtitle = StrSettings.galleryPick, icon = Icons.Outlined.PhotoLibrary, onClick = {
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      })
    }
  }
}

/** پروندهٔ خالیِ عکسِ دوربین در پوشهٔ موقتِ برنامه (`FileProvider` ِ `.files`) */
private fun cameraTarget(context: android.content.Context): Uri? = runCatching {
  val dir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() else listFiles()?.forEach { it.delete() } }
  FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "shot-${System.currentTimeMillis()}.jpg"))
}.getOrNull()
