package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.isDark
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles

/**
 *  تراشهٔ نسخهٔ ۳ (قرصی، گوشهٔ کامل): انتخاب‌نشده سطحِ کارت با مرزِ borderInput،
 *  انتخاب‌شده surfaceSelected با مرز و متنِ برند و تیکِ کوچک — رنگ هرگز تنها نیست.
 *  `count` شمارِ کوچکِ کنارِ برچسب («منقضی ۳»).
 */
@Composable
fun V3Chip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  count: Int? = null,
) {
  val c = PhoneV3.colors
  val bg by animateColorAsState(if (selected) PhoneV3Palette.selected(c) else c.card, MotionSpec.tweenOf(MotionTokens.SHORT), label = "chipBg")
  val edge by animateColorAsState(if (selected) PhoneV3Roles.focus(c) else PhoneV3Roles.inputBorder(c), MotionSpec.tweenOf(MotionTokens.SHORT), label = "chipEdge")
  val ink = if (selected) PhoneV3Roles.brandText(c) else c.ink
  val shape = RoundedCornerShape(percent = 50)
  Row(
    modifier
      .v3Pressable(label = label, role = Role.Tab, onClick = onClick)
      .heightIn(min = 38.dp)
      .clip(shape)
      .background(bg)
      .border(1.dp, edge, shape)
      .semantics { this.selected = selected }
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    when {
      selected -> { Icon(Icons.Outlined.Check, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)) }
      icon != null -> { Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)) }
    }
    Text(label, style = v3Text(14, FontWeight.Medium, 20), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (count != null) {
      Spacer(Modifier.width(6.dp))
      Text(
        count.toString(),
        style = v3Text(12, FontWeight.SemiBold, 16),
        color = if (selected) PhoneV3Roles.brandText(c) else c.inkMuted,
      )
    }
  }
}

/** تراشهٔ «+ تازه» — همان اندازه، با مرزِ خط‌چین‌نما (کم‌رنگ) */
@Composable
fun V3AddChip(label: String, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(percent = 50)
  Row(
    Modifier
      .v3Pressable(label = label, onClick = onClick)
      .heightIn(min = 38.dp)
      .clip(shape)
      .background(c.tile)
      .border(1.dp, c.hairline, shape)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Add, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text(label, style = v3Text(14, FontWeight.Medium, 20), color = PhoneV3Roles.brandText(c))
  }
}

/** ردیفِ افقیِ تراشه‌ها با پیمایش (دسته‌ها، فیلترها) */
@Composable
fun V3ChipRow(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
  content: @Composable () -> Unit,
) {
  Row(
    modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) { content() }
}

/**
 *  تب‌های بخش‌بندی‌شده با نشانگرِ لغزان: قرصِ سفید (شب: سطحِ بالاتر) با سایهٔ نرم
 *  زیرِ گزینهٔ انتخاب‌شده، که با فنر به جای تازه می‌رود — در راست‌به‌چپ آینه.
 */
@Composable
fun V3Segmented(
  options: List<String>,
  selected: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val track = RoundedCornerShape(12.dp)
  BoxWithConstraints(
    modifier
      .fillMaxWidth()
      //  درشتیِ نوشتهٔ سیستم (۱٫۳–۲×): بلندی با آن بزرگ می‌شود، نوشته بیرون نمی‌زند (AUDIT-NAV S4)
      .height(40.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
      .clip(track)
      .background(if (c.isDark) c.tile else androidx.compose.ui.graphics.lerp(c.bg, c.hairline, 0.55f))
      .padding(3.dp),
  ) {
    val w = maxWidth / options.size.coerceAtLeast(1)
    val x by animateDpAsState(w * selected.coerceIn(0, options.lastIndex.coerceAtLeast(0)), MotionSpec.indicator(), label = "segX")
    Box(
      Modifier
        .offset { IntOffset(x.roundToPx(), 0) }
        .width(w)
        .fillMaxHeight()
        .shadow(3.dp, RoundedCornerShape(10.dp), clip = false, ambientColor = c.ink.copy(alpha = 0.12f), spotColor = c.ink.copy(alpha = 0.12f))
        .clip(RoundedCornerShape(10.dp))
        .background(c.card),
    )
    Row(Modifier.fillMaxWidth().fillMaxHeight()) {
      options.forEachIndexed { i, label ->
        val on = i == selected
        val ink by animateColorAsState(if (on) c.ink else c.inkMuted, MotionSpec.tweenOf(MotionTokens.SHORT), label = "segInk")
        Box(
          Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
          contentAlignment = Alignment.Center,
        ) {
          Text(label, style = v3Text(14, if (on) FontWeight.SemiBold else FontWeight.Medium, 20), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
