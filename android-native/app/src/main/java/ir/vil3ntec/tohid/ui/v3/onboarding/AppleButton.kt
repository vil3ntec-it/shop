package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  «ورود با اپل» — فقط آیفون (`AuthEnv.apple`). شکلِ رهنمودِ اپل (HIG، Sign in with Apple): زمینهٔ سیاه
 *  با نوشته و نشانِ سفید در حالتِ روشن، و وارونه در حالتِ تیره — این‌جا با رنگِ متن و رنگِ صفحهٔ همان
 *  پوسته (`ink`/`bg`)، بی هیچ رنگِ تازه؛ نشان پیش از نوشته و هم‌قدِ آن، دکمه هم‌پهنای دکمه‌های دیگرِ دروازه.
 */
@Composable
internal fun AppleButton(enabled: Boolean, onClick: () -> Unit) {
  val c = PhoneV3.colors
  Row(
    Modifier
      .fillMaxWidth()
      .v3Pressable(enabled = enabled, label = StrAuth.appleSignIn, onClick = onClick)
      .heightIn(min = GATE_BUTTON)
      .clip(RoundedCornerShape(V3Space.controlRadius))
      .background(c.ink)
      .alpha(if (enabled) 1f else 0.5f)
      .padding(horizontal = 18.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppleMark(c.bg)
    Spacer(Modifier.width(8.dp))
    Text(StrAuth.appleSignIn, style = v3Text(15, FontWeight.SemiBold, 22), color = c.bg, maxLines = 1)
  }
}

/** نشانِ اپل — یک مسیرِ برداری در دستگاهِ ۲۴×۲۴، به رنگِ نوشتهٔ دکمه (نه رنگِ ثابت) */
@Composable
private fun AppleMark(tint: Color) {
  val path = remember { PathParser().parsePathString(APPLE_LOGO).toPath() }
  Canvas(Modifier.size(18.dp)) {
    val k = size.minDimension / 24f
    scale(k, k, pivot = Offset.Zero) { drawPath(path, tint) }
  }
}

private const val APPLE_LOGO =
  "M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09" +
    " 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48" +
    " 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559" +
    "-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83" +
    "-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701"
