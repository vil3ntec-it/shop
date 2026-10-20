package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind

/**
 *  دکمهٔ شناورِ کارِ اصلیِ صفحه («کالای تازه»): کامل (آیکن + برچسب) وقتی
 *  فهرست در بالاست، و با پیمایش به پایین با فنر به دایره جمع می‌شود (morph) —
 *  `V3Scaffold` همان `expanded` را می‌دهد. همان کار روی کامپیوتر با Ctrl+N
 *  (`AppNav.addAction`) هم صدا زده می‌شود.
 */
@Composable
fun V3Fab(
  icon: ImageVector,
  label: String,
  expanded: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
  val latest = rememberUpdatedState(onClick)
  DisposableEffect(Unit) {
    val action = { latest.value() }
    AppNav.addAction.value = action
    onDispose { if (AppNav.addAction.value === action) AppNav.addAction.value = null }
  }
  val shape = RoundedCornerShape(18.dp)
  Row(
    modifier
      .v3Pressable(label = label, scale = 0.94f, haptic = true, role = Role.Button, onClick = onClick)
      .height(56.dp)
      .shadow(10.dp, shape, ambientColor = pair.bg.copy(alpha = 0.5f), spotColor = pair.bg.copy(alpha = 0.5f))
      .clip(shape)
      .background(pair.bg)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = if (expanded) null else label, tint = pair.fg, modifier = Modifier.size(24.dp))
    AnimatedVisibility(
      visible = expanded,
      enter = expandHorizontally(MotionSpec.morph()) + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)),
      exit = shrinkHorizontally(MotionSpec.morph()) + fadeOut(MotionSpec.tweenOf(90)),
    ) {
      Row {
        Spacer(Modifier.width(8.dp))
        Text(label, style = V3Type.headline, color = pair.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(4.dp))
      }
    }
  }
}

/** یک کارِ نوارِ انتخاب */
data class V3BarAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 *  نوارِ کارهای گروهی (وقتی چند ردیف انتخاب شده): سطحِ تیرهٔ وارونه با شمار،
 *  کارها، و × برای پاک کردنِ انتخاب؛ از پایین با فنر می‌آید.
 */
@Composable
fun V3ActionBar(
  count: Int,
  actions: List<V3BarAction>,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  AnimatedVisibility(
    visible = count > 0,
    modifier = modifier,
    enter = slideInVertically(MotionSpec.sheet()) { it } + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)),
    exit = slideOutVertically(MotionSpec.sheet()) { it } + fadeOut(MotionSpec.tweenOf(90)),
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(c.black)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      BarIcon(Icons.Outlined.Close, StrKit.clear, false, onClear)
      Text(StrKit.selectedCount(count), style = V3Type.body, color = c.onBlack, modifier = Modifier.weight(1f))
      actions.forEach { a -> BarIcon(a.icon, a.label, a.danger, a.onClick) }
    }
  }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, danger: Boolean, onClick: () -> Unit) {
  val c = PhoneV3.colors
  Row(
    Modifier.clip(RoundedCornerShape(14.dp)).v3Pressable(label = label, scale = 0.92f, haptic = true, onClick = onClick).padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = label, tint = if (danger) PhoneV3Roles.dangerOnInverse else c.onBlack, modifier = Modifier.size(22.dp))
  }
}
