package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 *  زدن روی جایی بیرون از کادرِ نوشتن ⇒ تمرکز برمی‌خیزد و صفحه‌کلیدِ گوشی بسته می‌شود (صاحب‌کار ۱۴۰۵/۰۸/۲۳).
 *  یک جا برای همهٔ صفحه‌های کیت: ریشهٔ `V3Scaffold` و صفحهٔ جست‌وجو (`SearchScreen`، قابِ خودش را دارد).
 *
 *  فقط زدنی که هیچ جزءِ زیرین نخورده می‌رسد: کادر، دکمه، تراشه و ردیف زدنِ خودشان را می‌خورند و همان کارِ امروز را
 *  می‌کنند (زدنِ خودِ کادر تمرکز را نگه می‌دارد)؛ کشیدن و پیمایش هم زدن نیست. صندوق (`SaleScreen` با بارکدخوانِ
 *  `hidScanner`) روی `V3Scaffold` نیست، پس تمرکزِ بارکدخوان دست نمی‌خورد.
 */
fun Modifier.v3ClearFocusOnTap(): Modifier = composed {
  val focus = LocalFocusManager.current
  pointerInput(focus) { detectTapGestures(onTap = { focus.clearFocus() }) }
}
