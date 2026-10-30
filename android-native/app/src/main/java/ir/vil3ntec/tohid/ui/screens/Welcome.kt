package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthGate

/**
 *  نامِ پیشینِ دروازهٔ ورود — `MainActivity`، `Main.kt` ِ کامپیوتر و پوسته هنوز این را صدا می‌زنند.
 *  صفحهٔ تازه `ui/v3/onboarding/AuthGate` است (گروهِ G1)؛ این فایل در فاز ۳ با رفتنِ آخرین صدازننده
 *  برداشته می‌شود و `GoogleMark` جای خودش را می‌گیرد. `openOnMap` (پیک هم صدا می‌زند) در `ui/components/common/OpenOnMap.kt` است.
 */
@Composable
fun WelcomeScreen(store: ShopStore, onDone: () -> Unit) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { WelcomeScreenLegacy(store, onDone); return }
  AuthGate(store, onDone = onDone)
}

/**
 *  نشانِ رسمیِ گوگل («G» ِ چهاررنگ) — همان چهار تکهٔ مسیرهای برداریِ خودِ گوگل در دستگاهِ ۴۸×۴۸،
 *  بی هیچ تصویرِ همراه. رنگ‌هایش رنگِ برندِ گوگل‌اند، نه رنگِ سطح (سقفِ `HardcodedColorsTest`).
 */
@Composable
internal fun GoogleMark() {
  //  یک بار خوانده می‌شوند، نه با هر بار کشیده شدنِ کلید
  val marks = remember {
    listOf(
      Color(0xFF4285F4) to PathParser().parsePathString(G_BLUE).toPath(),
      Color(0xFF34A853) to PathParser().parsePathString(G_GREEN).toPath(),
      Color(0xFFFBBC05) to PathParser().parsePathString(G_YELLOW).toPath(),
      Color(0xFFEA4335) to PathParser().parsePathString(G_RED).toPath(),
    )
  }
  Canvas(Modifier.size(20.dp)) {
    val k = size.minDimension / 48f
    scale(k, k, pivot = Offset.Zero) {
      marks.forEach { (tint, path) -> drawPath(path, tint) }
    }
  }
}

private const val G_BLUE =
  "M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11" +
    "c4.16-3.83 6.56-9.47 6.56-16.17z"
private const val G_GREEN =
  "M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07" +
    "H4.34v5.7C7.96 41.07 15.4 46 24 46z"
private const val G_YELLOW =
  "M11.69 28.18C11.25 26.86 11 25.45 11 24s.25-2.86.69-4.18v-5.7H4.34C2.85 17.09 2 20.45 2 24" +
    "s.85 6.91 2.34 9.88l7.35-5.7z"
private const val G_RED =
  "M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12" +
    "l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z"
