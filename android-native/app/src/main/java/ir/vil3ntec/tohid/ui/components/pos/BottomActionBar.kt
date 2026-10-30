package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape

/** برچسبِ کوچکِ کلید روی دکمه («F4»، «Ctrl+6») — از `KeyMap.X.hint`؛ قرصِ نرم، بی خطِ دور */
@Composable
fun PosKeyHint(hint: String, modifier: Modifier = Modifier) {
  Text(
    hint,
    modifier
      .clip(ShopShape.chip)
      .background(Shop.colors.surface2)
      .padding(horizontal = 6.dp, vertical = 1.dp),
    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
    color = Shop.colors.muted,
    maxLines = 1,
  )
}

/**
 *  خطِ راهنمای کلیدها — باریک و کم‌رنگ، زیرِ کاتالوگ (نسخهٔ ۲).
 *
 *  نسخهٔ ۱ یک نوارِ پایینِ پر از دکمه داشت که همان کارهای سبد و منو را دوباره
 *  نشان می‌داد (نقدِ صاحب محصول: «همه‌چیز دو جا»). کارها حالا سرِ جای خودشان‌اند
 *  — «قلمِ آزاد» و «خالی کردن» در سربرگِ سبد، «ویرایشِ ردیف» با زدنِ قیمتِ ردیف یا
 *  F3، «فهرستِ فروش‌ها» در منوی کناری (Ctrl+6) — و این‌جا فقط یادآوریِ کلیدهاست.
 *  فهرستِ کامل همیشه در F1.
 */
@Composable
fun PosHintLine(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
    style = MaterialTheme.typography.labelSmall,
    color = Shop.colors.muted2,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

/**
 *  فقط برای عکس‌های CI (`DevShot`، `posview:table|card`) — مثلِ `DeskCustomersShot`.
 *  صفحه آن را مثلِ کلیکِ کاربر روی دکمهٔ نما اجرا می‌کند و دوباره خالی می‌کند.
 */
object PosShot {
  val view = mutableStateOf<ViewMode?>(null)
}
