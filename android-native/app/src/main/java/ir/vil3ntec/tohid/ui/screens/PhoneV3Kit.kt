package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import kotlinx.coroutines.delay

/**
 *  تکه‌های مشترکِ صفحه‌های گوشی نسخهٔ ۳ که «حساب من» (r4)، «تنظیمات» (r5) و
 *  «بیشتر» از آن‌ها ساخته می‌شوند — یک جا، تا سه صفحه یک زبان داشته باشند.
 *
 *  رنگ فقط از `PhoneV3`؛ آیکن‌ها خطیِ تک‌رنگ. هر انیمیشن با `Motion.enabled`
 *  (کاهشِ حرکتِ گوشی یا انتخابِ کاربر) صفر می‌شود و هیچ‌کدام جلوی لمس را نمی‌گیرد.
 */

/** سبکِ متن با همان قلم و همان تنظیمِ رقمِ برنامه (`shopTypography`) */
@Composable
internal fun v3Text(size: Int, weight: FontWeight = FontWeight.Medium, line: Int = size + size / 2): TextStyle =
  MaterialTheme.typography.bodyLarge.copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

private fun ms(full: Int): Int = if (Motion.enabled) full else 0

/** زمینهٔ کاشیِ آیکنِ ردیف (r5): خاکستریِ خیلی روشن، نه رنگی */
internal val PhoneV3Colors.iconTile: Color get() = lerp(card, bg, 0.75f)

/** پُرشدنِ ردیف هنگامِ فشار */
internal val PhoneV3Colors.pressed: Color get() = lerp(card, bg, 0.9f)

/**
 *  زمینهٔ مه‌گرفتهٔ «حساب من» (r4): خاکستریِ روشن با دو لکهٔ بزرگِ محوِ روشن و
 *  سایهٔ نرمِ پایین — نه یک‌دست. همه از توکن‌های `PhoneV3` ساخته می‌شوند.
 */
internal fun Modifier.frosted(c: PhoneV3Colors): Modifier = drawBehind {
  val dark = c.bg.red < 0.3f
  val base = if (dark) c.bg else lerp(c.bg, c.inkMuted, 0.10f)
  drawRect(base)
  val w = size.width
  val h = size.height
  drawRect(
    Brush.radialGradient(
      listOf(c.card.copy(alpha = if (dark) 0.55f else 0.95f), c.card.copy(alpha = 0f)),
      center = Offset(w * 0.18f, h * 0.30f), radius = w * 0.85f,
    ),
  )
  drawRect(
    Brush.radialGradient(
      listOf(c.tile.copy(alpha = if (dark) 0.9f else 0.85f), c.tile.copy(alpha = 0f)),
      center = Offset(w * 0.95f, h * 0.62f), radius = w * 0.75f,
    ),
  )
  drawRect(
    Brush.radialGradient(
      listOf(lerp(base, c.inkMuted, if (dark) 0.08f else 0.22f), base.copy(alpha = 0f)),
      center = Offset(w * 1.0f, h * 1.0f), radius = w * 0.8f,
    ),
  )
}

/** پُرشدنِ نرمِ هنگامِ فشار + زدن؛ بی موجِ رنگی (r5) */
internal fun Modifier.v3Press(
  highlight: Color,
  role: Role = Role.Button,
  label: String? = null,
  onClick: (() -> Unit)?,
): Modifier = if (onClick == null) this else composed {
  val src = remember { MutableInteractionSource() }
  val down by src.collectIsPressedAsState()
  val bg by animateColorAsState(if (down) highlight else highlight.copy(alpha = 0f), tween(ms(120)), label = "press")
  this
    .clickable(interactionSource = src, indication = null, role = role, onClickLabel = label, onClick = onClick)
    .background(bg)
}

/** کمی کوچک شدن هنگامِ فشار — برای دکمه‌های گرد */
internal fun Modifier.v3PressScale(onClick: () -> Unit, label: String?): Modifier = composed {
  val src = remember { MutableInteractionSource() }
  val down by src.collectIsPressedAsState()
  val scale by androidx.compose.animation.core.animateFloatAsState(
    if (down && Motion.enabled) 0.92f else 1f, spring(stiffness = 700f), label = "pressScale",
  )
  this
    .graphicsLayer { scaleX = scale; scaleY = scale }
    .clickable(interactionSource = src, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
}

/** پیدا شدنِ نرم (کم‌رنگ ⇐ پُررنگ، کمی بالا آمدن) در نخستین نمایش */
@Composable
internal fun rememberAppear(delayMs: Int = 0): Float {
  val a = remember { Animatable(if (Motion.enabled) 0f else 1f) }
  LaunchedEffect(Unit) {
    if (a.value < 1f) {
      delay(delayMs.toLong())
      a.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }
  }
  return a.value
}

internal fun Modifier.appear(p: Float): Modifier = graphicsLayer {
  alpha = p
  translationY = (1f - p) * 10.dp.toPx()
}

/** دکمهٔ گردِ سفید با سایهٔ نرم (زنگِ r4، برگشتِ r5) */
@Composable
internal fun V3RoundButton(
  icon: ImageVector,
  description: String,
  size: Dp = 44.dp,
  iconSize: Dp = 22.dp,
  dot: Boolean = false,
  onClick: () -> Unit,
) {
  val c = PhoneV3.colors
  Box {
    Box(
      Modifier
        .v3PressScale(onClick, description)
        .size(size)
        .shadow(6.dp, CircleShape, clip = false, ambientColor = c.ink.copy(alpha = 0.10f), spotColor = c.ink.copy(alpha = 0.10f))
        .clip(CircleShape)
        .background(c.card),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = description, tint = c.icon, modifier = Modifier.size(iconSize))
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

/** دکمهٔ برگشت: در راست‌به‌چپ رو به راست (سمتِ آغاز) */
@Composable
internal fun V3BackButton(onClick: () -> Unit) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  V3RoundButton(
    if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
    "بازگشت", size = 40.dp, iconSize = 24.dp, onClick = onClick,
  )
}

/** سربرگِ r5: دکمهٔ گردِ برگشت در آغاز، عنوانِ وسط */
@Composable
internal fun V3Header(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  Box(modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
    if (onBack != null) Box(Modifier.align(Alignment.CenterStart)) { V3BackButton(onBack) }
    Text(
      title,
      style = v3Text(19, FontWeight.SemiBold, 26),
      color = c.ink,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 52.dp).semantics { heading() },
    )
  }
}

/** عنوانِ درشتِ صفحهٔ نوار (r4 «Profil») در آغاز، و هرچه در پایانِ ردیف بیاید */
@Composable
internal fun V3LargeHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
  val c = PhoneV3.colors
  Row(
    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 18.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      title,
      style = v3Text(30, FontWeight.Bold, 40),
      color = c.ink,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f).semantics { heading() },
    )
    trailing?.invoke()
  }
}

/** سرخطِ خاکستریِ گروه، بیرونِ کارت (r5) */
@Composable
internal fun V3GroupTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = v3Text(14, FontWeight.SemiBold, 20),
    color = PhoneV3.colors.inkMuted,
    modifier = modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp).semantics { heading() },
  )
}

/** کارتِ سفیدِ گروه (r5): گوشهٔ ۱۸، بی خطِ دور و بی سایه */
@Composable
internal fun V3Card(
  modifier: Modifier = Modifier,
  radius: Dp = PhoneV3.groupRadius,
  padding: PaddingValues = PaddingValues(vertical = 6.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(radius))
      .background(PhoneV3.colors.card)
      .padding(padding),
    content = content,
  )
}

/**
 *  ردیفِ r5: کاشیِ خاکستریِ روشن با آیکنِ خطیِ تک‌رنگ، نام، و در پایان مقدارِ
 *  خاکستری / کلیدِ سبز / فلشِ کم‌رنگ. `tile = false` همان ردیفِ فهرستِ r4 است
 *  (آیکنِ بی‌کاشی، ردیفِ بلندتر).
 */
@Composable
internal fun V3Row(
  icon: ImageVector?,
  label: String,
  modifier: Modifier = Modifier,
  value: String? = null,
  description: String? = null,
  danger: Boolean = false,
  tile: Boolean = true,
  height: Dp = PhoneV3.rowHeight,
  toggle: Boolean? = null,
  onToggle: ((Boolean) -> Unit)? = null,
  chevron: Boolean = true,
  onClick: (() -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val ink = if (danger) c.negative else c.ink
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = height)
      .v3Press(c.pressed, label = label, onClick = onClick)
      .padding(start = if (tile) 12.dp else 22.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      if (tile) {
        Box(
          Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.iconTile),
          contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (danger) c.negative else c.icon, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(12.dp))
      } else {
        Icon(icon, contentDescription = null, tint = if (danger) c.negative else c.icon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
      }
    }
    Column(Modifier.weight(1f)) {
      Text(label, style = v3Text(15, FontWeight.Medium, 22), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (description != null) {
        Text(description, style = v3Text(12, FontWeight.Medium, 17), color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
    }
    if (value != null) {
      Spacer(Modifier.width(8.dp))
      Text(
        value,
        style = v3Text(13, FontWeight.Medium, 18),
        color = c.inkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 150.dp),
      )
    }
    when {
      toggle != null -> { Spacer(Modifier.width(10.dp)); V3Toggle(toggle, label, onToggle) }
      onClick != null && chevron -> {
        Spacer(Modifier.width(4.dp))
        //  فلشِ «جلو»: در راست‌به‌چپ رو به چپ (سمتِ پایان)
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Icon(
          if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
          contentDescription = null, tint = c.chevron, modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}

/** خطِ مویی میانِ ردیف‌های فهرستِ r4 (سراسری) */
@Composable
internal fun V3Divider(start: Dp = 0.dp) {
  Box(Modifier.padding(start = start).fillMaxWidth().height(1.dp).background(PhoneV3.colors.hairline))
}

/**
 *  کلیدِ سبزِ r5 (Face ID) — قرصی با گویِ سفید که می‌لغزد؛ رنگ و جای گوی
 *  انیمیشن دارند. در راست‌به‌چپ «روشن» یعنی گوی در پایان (چپ).
 */
@Composable
internal fun V3Toggle(checked: Boolean, label: String, onToggle: ((Boolean) -> Unit)?) {
  val c = PhoneV3.colors
  val x by animateDpAsState(if (checked) 22.dp else 2.dp, if (Motion.enabled) spring<Dp>(dampingRatio = 0.7f, stiffness = 600f) else tween<Dp>(0), label = "knob")
  val track by animateColorAsState(if (checked) c.toggleOn else lerp(c.hairline, c.inkMuted, 0.18f), tween(ms(200)), label = "track")
  Box(
    Modifier
      .size(width = 51.dp, height = 31.dp)
      .clip(RoundedCornerShape(percent = 50))
      .background(track)
      .then(
        if (onToggle != null) Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
        else Modifier,
      ),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      Modifier
        .offset(x = x)
        .size(27.dp)
        .shadow(2.dp, CircleShape, clip = false)
        .clip(CircleShape)
        .background(c.onBlack),
    )
  }
}

/**
 *  جابه‌جاییِ زیرصفحه‌ها (بندِ G): رفتن به عمق از سمتِ پایان می‌لغزد و کم‌رنگ
 *  پیدا می‌شود (~۲۲۰ms)، برگشت از سمتِ آغاز. با «کاهشِ حرکت» بی‌درنگ.
 */
@Composable
internal fun <T> V3Pages(target: T, depth: (T) -> Int, content: @Composable (T) -> Unit) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  AnimatedContent(
    targetState = target,
    transitionSpec = {
      val forward = depth(targetState) >= depth(initialState)
      //  «پایان» در راست‌به‌چپ سمتِ چپ است
      val fromEnd = if (rtl) -1 else 1
      val sign = if (forward) fromEnd else -fromEnd
      (slideInHorizontally(tween<IntOffset>(ms(220), easing = FastOutSlowInEasing)) { sign * it / 4 } + fadeIn(tween(ms(220))))
        .togetherWith(fadeOut(tween(ms(120))))
    },
    label = "v3pages",
  ) { content(it) }
}
