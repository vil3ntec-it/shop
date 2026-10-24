package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.v3.shell.HomeScreen
import ir.vil3ntec.tohid.ui.v3.shell.HomeShot

/**
 *  حالت‌های ثابتِ پیش‌نمایشِ امروزِ کامپیوتر (`PhoneV3HomePreview`) — همان `HomeShot` ِ
 *  خانهٔ نسخهٔ ۳؛ در فاز ۳ با خودِ پیش‌نمایش برداشته می‌شود.
 */
data class PhoneHomeShot(val pull: Boolean = false, val page: Int = 0, val big: Boolean = false) {
  companion object { val NONE = PhoneHomeShot() }
}

/** خانهٔ گوشی — حالا `HomeScreen` ِ نسخهٔ ۳ (`ui/v3/shell`)؛ این نام فقط برای پیش‌نمایش‌های امروز */
@Composable
fun PhoneHomeScreen(
  d: ShopData,
  onOpen: (String) -> Unit,
  bottomReserve: Dp = PhoneNav.RESERVE,
  shot: PhoneHomeShot = PhoneHomeShot.NONE,
) {
  HomeScreen(d, onOpen, bottomReserve, HomeShot(heroPage = if (shot.pull) 1 else 0, page = shot.page, big = shot.big))
}

/** دو نقطهٔ ورقه‌ها — نقطهٔ فعال کشیده */
@Composable
fun PagerDots(count: Int, current: Int, on: Color, off: Color) {
  if (count < 2) return
  //  ورقه‌ها چپ‌به‌راست‌اند؛ نقطه‌ها هم
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      repeat(count) { i ->
        val w by animateFloatAsState(if (i == current) 18f else 6f, if (Motion.enabled) spring() else snap(), label = "dot")
        Box(Modifier.width(w.dp).height(6.dp).clip(CircleShape).background(if (i == current) on else off))
      }
    }
  }
}

/** دکمهٔ بی‌موجی که با فشار کمی کوچک می‌شود (§G: «press-scale») */
@Composable
fun PressBox(
  modifier: Modifier = Modifier,
  scaleTo: Float = 0.94f,
  label: String? = null,
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val s by animateFloatAsState(if (pressed) scaleTo else 1f, if (Motion.enabled) spring(stiffness = 900f) else snap(), label = "press")
  Box(
    modifier
      .graphicsLayer { scaleX = s; scaleY = s }
      .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { content() }
}
