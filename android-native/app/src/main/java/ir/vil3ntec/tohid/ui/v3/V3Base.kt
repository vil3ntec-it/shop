package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.StrKit
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/**
 *  پایه‌های کیتِ نسخهٔ ۳: جدولِ نوشته (شبیهِ SF)، کارت، دکمهٔ گردِ آیکن، دکمهٔ
 *  برگشت و کلید. `PhoneV3Kit.kt`ِ قدیم برای صفحه‌های پیشین همین‌ها را صدا می‌زند.
 */
object V3Type {
  /** عنوانِ درشتِ صفحه */
  val largeTitle: TextStyle @Composable get() = v3Text(30, FontWeight.Bold, 40)
  /** عنوانِ کارت/برگه */
  val title: TextStyle @Composable get() = v3Text(19, FontWeight.SemiBold, 26)
  val headline: TextStyle @Composable get() = v3Text(17, FontWeight.SemiBold, 24)
  val body: TextStyle @Composable get() = v3Text(15, FontWeight.Medium, 22)
  val callout: TextStyle @Composable get() = v3Text(14, FontWeight.Medium, 20)
  val subhead: TextStyle @Composable get() = v3Text(13, FontWeight.Medium, 18)
  val footnote: TextStyle @Composable get() = v3Text(12, FontWeight.Medium, 17)
  val caption: TextStyle @Composable get() = v3Text(11, FontWeight.SemiBold, 15)
  /** عددِ درشتِ آمار و پول */
  val figure: TextStyle @Composable get() = v3Text(24, FontWeight.Bold, 30)
}

/** کارتِ مرزدار (سطحِ یک روی سطحِ صفر) — گوشهٔ گروهِ r5 */
@Composable
fun V3Card(
  modifier: Modifier = Modifier,
  radius: Dp = PhoneV3.groupRadius,
  padding: PaddingValues = PaddingValues(vertical = 8.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(radius)
  Column(
    modifier
      .fillMaxWidth()
      .clip(shape)
      .background(c.card)
      .border(1.dp, c.hairline, shape)
      .padding(padding),
    content = content,
  )
}

/** دکمهٔ گردِ آیکن (سربرگ، زنگ): کارتِ سفید با مرز و سایهٔ نرم؛ فشار ۰٫۹۲ */
@Composable
fun V3IconButton(
  icon: ImageVector,
  label: String,
  size: Dp = 44.dp,
  iconSize: Dp = 22.dp,
  dot: Boolean = false,
  onClick: () -> Unit,
) {
  val c = PhoneV3.colors
  Box {
    Box(
      Modifier
        .v3Pressable(label = label, scale = 0.92f, onClick = onClick)
        .size(size)
        .shadow(6.dp, CircleShape, clip = false, ambientColor = c.ink.copy(alpha = 0.10f), spotColor = c.ink.copy(alpha = 0.10f))
        .clip(CircleShape)
        .background(c.card)
        .border(1.dp, c.hairline, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = label, tint = c.icon, modifier = Modifier.size(iconSize))
    }
    if (dot) {
      Box(
        Modifier
          .align(Alignment.TopEnd)
          .padding(top = size * 0.2f, end = size * 0.22f)
          .size(9.dp)
          .clip(CircleShape)
          .background(c.card)
          .padding(1.5.dp)
          .clip(CircleShape)
          .background(c.negative),
      )
    }
  }
}

/** برگشت: در راست‌به‌چپ رو به راست (سمتِ آغاز) */
@Composable
fun V3BackButton(onClick: () -> Unit) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  V3IconButton(if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft, StrKit.back, size = 44.dp, iconSize = 24.dp, onClick = onClick)
}

/**
 *  کلید (r5): قرص با گویِ سفید که با فنر می‌لغزد؛ «روشن» = برند (VILL3N: نه سبزِ
 *  iOS). در راست‌به‌چپ گوی در پایان (چپ) یعنی روشن.
 */
@Composable
fun V3Toggle(checked: Boolean, label: String, onToggle: ((Boolean) -> Unit)?) {
  val c = PhoneV3.colors
  val x by animateDpAsState(if (checked) 22.dp else 2.dp, MotionSpec.indicator(), label = "knob")
  val track by animateColorAsState(if (checked) c.toggleOn else lerp(c.hairline, c.inkMuted, 0.18f), MotionSpec.tweenOf(MotionTokens.SHORT), label = "track")
  Box(
    Modifier
      .size(width = 51.dp, height = 31.dp)
      .clip(RoundedCornerShape(percent = 50))
      .background(track)
      .then(
        if (onToggle != null) Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
        else Modifier,
      )
      .semantics { contentDescription = label },
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      Modifier
        .offset { androidx.compose.ui.unit.IntOffset(x.roundToPx(), 0) }
        .size(27.dp)
        .shadow(2.dp, CircleShape, clip = false)
        .clip(CircleShape)
        .background(c.onBlack),
    )
  }
}
