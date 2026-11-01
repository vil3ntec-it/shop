package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/**
 *  خطِ زمانیِ عمودی: هر گام یک دایرهٔ تیک‌دار به رنگِ وضعیتِ مقصد، خطِ مویی تا گامِ بعد،
 *  زمان، عنوان، کننده و دلیل/آماده‌تا/یادداشت. گام‌ها بارِ اول پلکانی می‌آیند (کیت)؛ گامِ تازه
 *  تیکش را با فنر می‌گذارد و رنگِ دایره نرم عوض می‌شود.
 */
@Composable
fun OrderTimeline(steps: List<OrderDetailModel.Step>, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val stagger = rememberV3Stagger(steps.size)
  //  گامی که پس از باز شدنِ صفحه رسید (گذارِ تازه) تیکش با فنر می‌نشیند
  val seen = remember { steps.size }
  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
    steps.forEachIndexed { i, s ->
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).v3Stagger(stagger, "step-$i", i)) {
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
          StepDot(s.status, pop = i == steps.lastIndex && steps.size > seen)
          if (i < steps.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(c.hairline))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = if (i < steps.lastIndex) 14.dp else 0.dp)) {
          Text(OrdersModel.stamp(s.at), style = V3Type.caption, color = c.inkMuted)
          Text(s.title, style = V3Type.callout, color = c.ink)
          val extra = listOfNotNull(s.who, s.reason?.let(StrOnline::reasonLine), s.readyBy?.let(StrOnline::readyBy))
          Text(extra.joinToString(" · "), style = V3Type.footnote, color = c.inkMuted)
          if (s.note != null) Text(s.note, style = V3Type.footnote, color = c.ink)
        }
      }
    }
  }
}

/** دایرهٔ گام: رنگِ وضعیت با گذرِ نرم؛ تیکِ گامِ تازه از صفر با فنرِ بازیگوش (بی حرکت ⇐ بی‌درنگ) */
@Composable
private fun StepDot(status: V3Status?, pop: Boolean) {
  val c = PhoneV3.colors
  val pair = status?.let { PhoneV3Roles.status(c, it) }
  val bg by animateColorAsState(pair?.bg ?: c.tile, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "step-bg")
  val check = remember { Animatable(if (pop && Motion.enabled) 0f else 1f) }
  LaunchedEffect(Unit) { if (check.value < 1f) check.animateTo(1f, V3Springs.playful()) }
  Box(Modifier.size(22.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
    Icon(
      Icons.Outlined.Check,
      contentDescription = null,
      tint = pair?.fg ?: c.icon,
      modifier = Modifier.size(14.dp).graphicsLayer { scaleX = check.value; scaleY = check.value },
    )
  }
}
