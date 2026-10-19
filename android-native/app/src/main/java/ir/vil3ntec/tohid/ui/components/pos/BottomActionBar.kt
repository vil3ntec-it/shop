package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape

/** برچسبِ کوچکِ کلید روی دکمه («F4»، «Ctrl+6») — از `KeyMap.X.hint` */
@Composable
fun PosKeyHint(hint: String, modifier: Modifier = Modifier) {
  Text(
    hint,
    modifier
      .border(1.dp, Shop.colors.border, ShopShape.badge)
      .padding(horizontal = 6.dp, vertical = 1.dp),
    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
    color = Shop.colors.muted,
    maxLines = 1,
  )
}

/**
 *  یک کارِ نوارِ پایین. فقط کارِ واقعی: هر دکمه به همان کاری وصل است که
 *  کلیدش می‌کند. `enabled = false` یعنی همین حالا شدنی نیست (مثلاً هنوز
 *  فاکتوری برای چاپ نیست)، نه «ساخته نشده».
 */
class PosBarAction(
  val label: String,
  val icon: ImageVector,
  val hint: String?,
  val enabled: Boolean = true,
  /** کارِ پاک‌کننده — رنگِ خطر فقط برای همین */
  val danger: Boolean = false,
  val onClick: () -> Unit,
)

/**
 *  نوارِ عملِ پایینِ صندوق (VISUAL-DIRECTION §۲). در پنجرهٔ باریک افقی
 *  می‌لغزد و هیچ دکمه‌ای بریده نمی‌شود؛ ته‌اش خلاصهٔ کلیدهای کادر می‌آید.
 */
@Composable
fun BottomActionBar(
  actions: List<PosBarAction>,
  keysLine: String,
  target: Dp,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Shop.colors.surfaceSolid,
    shape = ShopShape.card,
    border = BorderStroke(1.dp, Shop.colors.border),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      actions.forEach { a ->
        val tint = if (a.danger) Shop.colors.danger else Shop.colors.text
        OutlinedButton(
          onClick = a.onClick,
          enabled = a.enabled,
          border = BorderStroke(1.dp, if (a.danger && a.enabled) Shop.colors.danger.copy(alpha = 0.5f) else Shop.colors.fieldBorder),
          modifier = Modifier.heightIn(min = target),
        ) {
          Icon(a.icon, contentDescription = null, tint = if (a.enabled) tint else Shop.colors.muted2, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(a.label, color = if (a.enabled) tint else Shop.colors.muted2, maxLines = 1)
          a.hint?.let {
            Spacer(Modifier.width(6.dp))
            PosKeyHint(it)
          }
        }
      }
      Spacer(Modifier.width(8.dp))
      Text(
        keysLine,
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 *  فقط برای عکس‌های CI (`DevShot`، `posview:table|card`) — مثلِ `DeskCustomersShot`.
 *  صفحه آن را مثلِ کلیکِ کاربر روی دکمهٔ نما اجرا می‌کند و دوباره خالی می‌کند.
 */
object PosShot {
  val view = mutableStateOf<ViewMode?>(null)
}
