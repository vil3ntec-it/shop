package ir.vil3ntec.tohid.ui.v3.online

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Type

/** یک تب با شمار: `attention` ⇐ نقطهٔ «در انتظارِ تأیید» کنارِ عدد (سفارشِ تازه) */
data class CountTab(val id: String, val label: String, val count: Int = 0, val attention: Boolean = false)

/**
 *  تب‌های پیمایشیِ شمارش‌دار با نشانگرِ لغزان — برای صف‌هایی که بیش از چهار
 *  برگه یا برچسبِ بلند دارند (سفارش‌ها، میزِ تحویل) و `V3Segmented` جایشان
 *  نمی‌شود. نشانگر با فنر زیرِ تبِ تازه می‌رود و عدد با هر تغییر یک «پرش»ِ کوچک
 *  دارد. ⇐ `DraftCountTabs`، برای بردن به کیت در PR نام برده شده.
 */
@Composable
fun DraftCountTabs(tabs: List<CountTab>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val density = LocalDensity.current
  //  جای فیزیکیِ هر تب (از چپ، مستقل از جهت) تا نشانگر در راست‌به‌چپ هم درست بنشیند
  val bounds = remember { mutableStateMapOf<String, Pair<Dp, Dp>>() }
  val target = bounds[selected]
  val x by animateDpAsState(target?.first ?: 0.dp, MotionSpec.indicator(), label = "tabX")
  val w by animateDpAsState(target?.second ?: 0.dp, MotionSpec.indicator(), label = "tabW")
  Box(
    modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 16.dp),
  ) {
    Box(
      Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(if (c.source.isDark) c.tile else androidx.compose.ui.graphics.lerp(c.bg, c.hairline, 0.55f))
        .padding(3.dp),
    ) {
      if (target != null) {
        Box(
          Modifier
            .align(AbsoluteAlignment.TopLeft)
            .absoluteOffset(x = x)
            .width(w)
            .height(36.dp)
            .shadow(3.dp, RoundedCornerShape(10.dp), clip = false, ambientColor = c.ink.copy(alpha = 0.12f), spotColor = c.ink.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(10.dp))
            .background(c.card),
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { tab ->
          val on = tab.id == selected
          val ink by animateColorAsState(if (on) c.ink else c.inkMuted, MotionSpec.tweenOf(MotionTokens.SHORT), label = "tabInk")
          Row(
            Modifier
              .heightIn(min = 36.dp)
              .widthIn(min = 56.dp)
              .onGloballyPositioned { lc ->
                with(density) { bounds[tab.id] = lc.positionInParent().x.toDp() to lc.size.width.toDp() }
              }
              .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab.id) })
              .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(tab.label, style = if (on) V3Type.callout.copy(fontWeight = FontWeight.SemiBold) else V3Type.callout, color = ink, maxLines = 1)
            if (tab.count > 0) {
              Spacer(Modifier.width(6.dp))
              CountBadge(tab.count, tab.attention)
            }
          }
        }
      }
    }
  }
}

/** عددِ تب: رنگِ «در انتظارِ تأیید» با نقطه برای «نیازِ توجه»، خاکستری برای بقیه؛ با هر تغییر می‌پرد */
@Composable
private fun CountBadge(count: Int, attention: Boolean) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.status(c, if (attention) V3Status.AWAITING else V3Status.INACTIVE)
  val bump = remember { Animatable(1f) }
  LaunchedEffect(count) {
    if (Motion.enabled) {
      bump.snapTo(1.25f)
      bump.animateTo(1f, MotionSpec.morph())
    }
  }
  Row(
    Modifier
      .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
      .clip(RoundedCornerShape(50))
      .background(pair.bg)
      .padding(horizontal = 7.dp, vertical = 1.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (attention) {
      Box(Modifier.size(6.dp).clip(CircleShape).background(pair.fg))
      Spacer(Modifier.width(4.dp))
    }
    Text(count.toString(), style = V3Type.caption, color = pair.fg)
  }
}
