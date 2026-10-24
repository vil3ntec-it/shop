package ir.vil3ntec.tohid.ui.v3.account.profile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import java.io.File

/** دو راهِ گرفتنِ عکسِ حساب — راه‌اندازها بیرونِ برگه می‌مانند تا نتیجه پس از بسته شدنش برسد */
@Stable
internal class AvatarPicker(val camera: () -> Unit, val gallery: () -> Unit)

/**
 *  دوربین یا گالری برای عکسِ حساب (همان سازوکارِ `PhotoSourceSheet` ِ کالا: اجازهٔ
 *  دوربین همین‌جا پرسیده می‌شود، فایلِ موقت با `FileProvider`). عکس فقط روی همین
 *  دستگاه می‌ماند (`PhotoStore.saveAvatar`).
 *
 *  ⚠️ راه‌اندازها بی‌قید ساخته می‌شوند — داخلِ برگه‌ای که بسته می‌شود، نتیجهٔ
 *  عکس هرگز برنمی‌گشت.
 */
@Composable
internal fun rememberAvatarPicker(onPicked: (Uri) -> Unit): AvatarPicker {
  val context = LocalContext.current
  val picked by rememberUpdatedState(onPicked)
  var shot by remember { mutableStateOf<Uri?>(null) }
  val fromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) picked(uri)
  }
  val fromCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
    val target = shot
    shot = null
    if (taken && target != null) picked(target)
  }
  fun openCamera() {
    val uri = cameraTarget(context) ?: return
    shot = uri
    runCatching { fromCamera.launch(uri) }
  }
  val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) openCamera()
  }
  return remember(fromGallery, fromCamera, askCamera) {
    AvatarPicker(
      camera = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else askCamera.launch(Manifest.permission.CAMERA)
      },
      gallery = { runCatching { fromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
    )
  }
}

/** برگهٔ «انتخاب تصویر»: دوربین و گالری؛ برگه با حرکت می‌رود و بعد راه‌انداز باز می‌شود */
@Composable
internal fun AvatarSourceSheet(picker: AvatarPicker, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss, title = StrAccount.photoSource) { closer ->
    Text(
      StrAccount.photoLocal,
      style = V3Type.footnote,
      color = c.inkMuted,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    V3Card(padding = PaddingValues(vertical = 4.dp)) {
      V3ListRow(StrAccount.camera, subtitle = StrAccount.cameraBody, icon = Icons.Outlined.PhotoCamera, hue = V3Hue.BRAND, onClick = { closer.close(picker.camera) })
      V3Divider(start = 64.dp)
      V3ListRow(StrAccount.gallery, subtitle = StrAccount.galleryBody, icon = Icons.Outlined.PhotoLibrary, hue = V3Hue.BLUE, onClick = { closer.close(picker.gallery) })
    }
  }
}

/** فایلِ موقتِ دوربین در کَشِ برنامه (پیش از هر عکس خالی) */
private fun cameraTarget(context: android.content.Context): Uri? = runCatching {
  val dir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() else listFiles()?.forEach { it.delete() } }
  val file = File(dir, "avatar-${System.currentTimeMillis()}.jpg")
  FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()
