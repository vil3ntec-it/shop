package ir.vil3ntec.tohid.ui.v3.account

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec

/**
 *  نوارِ پیشرفت (دورهٔ اشتراک): از صفر تا مقدار با فنرِ بی‌جهش «رشد» می‌کند — فقط
 *  `graphicsLayer.scaleX` (بی چیدنِ دوباره در هر فریم)، از سمتِ آغاز (RTL آینه).
 *  با «کاهشِ حرکت» بی‌درنگ. پیش‌نویسِ گروهِ حساب برای کیت (`V3Progress`).
 */
@Composable
internal fun DraftProgressBar(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
  val target = fraction.coerceIn(0f, 1f)
  val p = remember { Animatable(if (Motion.enabled) 0f else target) }
  LaunchedEffect(target) { if (Motion.enabled) p.animateTo(target, MotionSpec.sheet()) else p.snapTo(target) }
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track), contentAlignment = Alignment.CenterStart) {
    Box(
      Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .graphicsLayer {
          scaleX = p.value
          transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
        }
        .clip(RoundedCornerShape(50))
        .background(color),
    )
  }
}
