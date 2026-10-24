package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette

/** یک تبِ نوارِ پایینِ ساده */
data class V3Tab(val route: String, val label: String, val icon: ImageVector)

/**
 *  نوارِ پایینِ شناورِ ساده (قرصِ سفید با مرز و سایهٔ نرم، شکلِ r10) برای حالت‌هایی
 *  که نوارِ قابلِ تنظیمِ دکان (`PhoneNavBar`) را ندارند — حالتِ خرید. تبِ فعال:
 *  دایرهٔ surfaceSelected و آیکنِ iconActive، و برچسبِ کوچک زیرِ همه.
 */
@Composable
fun V3TabBar(tabs: List<V3Tab>, current: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(28.dp)
  Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
    Row(
      Modifier
        .widthIn(max = 420.dp)
        .fillMaxWidth(0.92f)
        .height(64.dp)
        .shadow(16.dp, shape, clip = false, ambientColor = c.black.copy(alpha = 0.16f), spotColor = c.black.copy(alpha = 0.16f))
        .clip(shape)
        .background(c.card)
        .border(1.dp, c.hairline, shape)
        .padding(horizontal = 6.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      tabs.forEach { t ->
        val on = t.route == current
        val bg by animateColorAsState(if (on) PhoneV3Palette.selected(c) else c.card, MotionSpec.tweenOf(MotionTokens.SHORT), label = "tabBg")
        Column(
          Modifier
            .weight(1f)
            .v3Pressable(label = t.label, role = Role.Tab, scale = 0.92f) { onPick(t.route) }
            .semantics { selected = on },
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(Modifier.size(width = 48.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(t.icon, contentDescription = null, tint = if (on) PhoneV3Palette.iconActive(c) else c.icon, modifier = Modifier.size(22.dp))
          }
          Text(t.label, style = V3Type.caption, color = if (on) c.ink else c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
