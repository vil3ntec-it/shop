package ir.vil3ntec.tohid.ui.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.ui.v3.products.DraftPhotoSourceSheet

/**
 *  «عکس را از کجا بگیریم؟» — دوربین یا گالری.
 *
 *  پیاده‌سازی (برگهٔ نسخهٔ ۳ با همان راه‌اندازها، اجازهٔ دوربین و ترتیبِ
 *  `onPicked` ⇐ `onDismiss`) در `ui/v3/products/DraftPhotoSourceSheet.kt` است؛ این
 *  نام برای صدازننده‌های امروز («حساب من»، پیک، دستگاه‌ها، پیوندِ گوشی) می‌ماند تا
 *  پایه برگه را به کیت ببرد (بازسازیِ VILL3N، فاز ۳).
 */
@Composable
fun PhotoSourceSheet(
  open: Boolean,
  onDismiss: () -> Unit,
  /** نوشتهٔ راهنمای زیرِ گزینه‌ها */
  note: String = StrProducts.photoNote,
  onPicked: (Uri) -> Unit,
) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { PhotoSourceSheetLegacy(open, onDismiss, note, onPicked); return }
  DraftPhotoSourceSheet(open = open, onDismiss = onDismiss, note = note, onPicked = onPicked)
}
