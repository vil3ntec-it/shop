package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.v3.onboarding.EmailCodeContent

/**
 *  نامِ پیشینِ «ورود با ایمیل و کدِ شش‌رقمی» (بندِ ۲۱٫۴) برای میزبان‌های قدیمی که برگهٔ خودشان را
 *  دارند (`MySubscription`). مسیرِ تازه `auth/code` است (`EmailCodeSheet`، G1)؛ فاز ۳ این را برمی‌دارد.
 */
@Composable
fun CodeLoginSheet(onClose: () -> Unit, onSignedIn: () -> Unit) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { CodeLoginSheetLegacy(onClose, onSignedIn); return }
  Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
    EmailCodeContent(onClose = onClose, onSignedIn = onSignedIn, showTitle = true)
  }
}
