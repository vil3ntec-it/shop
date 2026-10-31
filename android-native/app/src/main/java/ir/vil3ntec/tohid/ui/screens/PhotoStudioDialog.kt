package ir.vil3ntec.tohid.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.ui.v3.products.PhotoStudioScreen

/**
 *  استودیوی عکسِ کالا در پنجرهٔ تمام‌صفحه — برای عکسی که گوشیِ جفت‌شده می‌فرستد
 *  (`PhoneLinkHost`). خودِ استودیو همان صفحهٔ نسخهٔ ۳ است
 *  (`ui/v3/products/PhotoStudioScreen.kt`، مسیرِ `products/{id}/photo/studio`).
 */
@Composable
fun PhotoStudioDialog(
  source: Uri,
  onDismiss: () -> Unit,
  /** خروجی آماده — صدازننده ذخیره می‌کند */
  onSave: (Bitmap) -> Unit,
) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { PhotoStudioDialogLegacy(source, onDismiss, onSave); return }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    PhotoStudioScreen(source = source, onBack = onDismiss, onSave = onSave)
  }
}
