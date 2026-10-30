package ir.vil3ntec.tohid.ui.v3.products

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import java.io.File

/**
 *  «عکس را از کجا بگیریم؟» — دوربین یا گالری، روی برگهٔ نسخهٔ ۳. رفتار عیناً همان
 *  `PhotoSourceSheet` ِ پیشین است (`ui/screens/PhotoPick.kt` حالا همین را صدا می‌زند،
 *  پس «حساب من»، پیک، دستگاه‌ها و پیوندِ گوشی هم همین را می‌بینند):
 *
 *   • راه‌اندازها بی‌قید و شرط ساخته می‌شوند (داخلِ `if` ثبتشان گم می‌شود)؛
 *   • اول `onPicked` و بعد `onDismiss` (صدازننده «عکسِ کدام کالا» را با بستن پاک می‌کند)؛
 *   • اجازهٔ دوربین پرسیده می‌شود (برنامه `CAMERA` را برای بارکد دارد) و با «بله»
 *     همان لحظه دوربین باز می‌شود؛
 *   • عکسِ دوربین در کَشِ برنامه و با `FileProvider` (نه مسیرِ خام)، پوشه پیش از هر عکس خالی.
 *
 *  پیش‌نویس برای کیت (برگهٔ انتخابِ عکسِ مشترک) — فهرستِ «Draft» در PR.
 */
@Composable
internal fun DraftPhotoSourceSheet(
  open: Boolean,
  onDismiss: () -> Unit,
  title: String = StrProducts.pickImage,
  note: String = StrProducts.photoNote,
  onPicked: (Uri) -> Unit,
) {
  val context = LocalContext.current
  var shot by remember { mutableStateOf<Uri?>(null) }

  val fromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) onPicked(uri)
    onDismiss()
  }
  val fromCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
    val target = shot
    shot = null
    if (taken && target != null) onPicked(target)
    onDismiss()
  }

  fun openCamera() {
    val uri = cameraTarget(context) ?: return
    shot = uri
    runCatching { fromCamera.launch(uri) }
  }

  val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) openCamera() else onDismiss()
  }

  if (!open) return

  V3Sheet(onDismiss = onDismiss, title = title) {
    V3Card(padding = PaddingValues(vertical = 4.dp)) {
      V3ListRow(
        title = if (Platform.DESKTOP) StrProducts.cameraDesk else StrProducts.camera,
        subtitle = StrProducts.cameraDetail,
        icon = Icons.Outlined.PhotoCamera,
        hue = V3Hue.BRAND,
        onClick = {
          val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
          if (granted) openCamera() else askCamera.launch(Manifest.permission.CAMERA)
        },
      )
      V3Divider(start = 64.dp)
      V3ListRow(
        title = StrProducts.gallery,
        subtitle = StrProducts.galleryDetail,
        icon = Icons.Outlined.PhotoLibrary,
        hue = V3Hue.BLUE,
        onClick = { fromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
      )
    }
    Text(note, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp))
  }
}

/** فایلی که دوربین عکس را در آن می‌نویسد — کَشِ برنامه، با `FileProvider`؛ پوشه پیش از هر عکس خالی می‌شود */
private fun cameraTarget(context: Context): Uri? = runCatching {
  val dir = File(context.cacheDir, "camera").apply {
    if (!exists()) mkdirs() else listFiles()?.forEach { it.delete() }
  }
  val file = File(dir, "shot-${System.currentTimeMillis()}.jpg")
  FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()
