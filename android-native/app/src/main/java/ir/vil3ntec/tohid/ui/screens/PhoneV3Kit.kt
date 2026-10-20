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
import androidx.compose.foundation.border
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
import ir.vil3ntec.tohid.ui.theme.PhoneV3Support
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

/** شب؟ (کارت و زمینهٔ تیره) */
internal val PhoneV3Colors.isDark: Boolean get() = bg.red < 0.3f

/** زمینهٔ کاشیِ آیکنِ ردیف (r5): سطحِ درونی، نه رنگی — روز نزدیکِ زمینه، شب `tile` */
internal val PhoneV3Colors.iconTile: Color get() = if (isDark) tile else lerp(card, bg, 0.9f)

/** پُرشدنِ ردیف هنگامِ فشار */
internal val PhoneV3Colors.pressed: Color get() = if (isDark) tile else lerp(card, bg, 0.9f)

/** مرزِ دیدنیِ هر کارت در هر دو تم (VILL3N §۶: کارتِ سفید روی زمینهٔ روشن بی مرز نه) */
internal val PhoneV3Colors.border: Color get() = hairline

/**
 *  زمینهٔ مه‌گرفتهٔ «حساب من» (r4): خاکستریِ روشن با دو لکهٔ بزرگِ محوِ روشن و
 *  سایهٔ نرمِ پایین — نه یک‌دست. همه از توکن‌های `PhoneV3` ساخته می‌شوند.
 */
internal fun Modifier.frosted(c: PhoneV3Colors): Modifier = drawBehind {
  val dark = c.isDark
  /*
   *  شکل از r4، رنگ از VILL3N: زمینهٔ خاکستری‌آبیِ روشن با لکه‌های بزرگِ محو —
   *  سایهٔ سردِ کنارِ زنگ و عکس، روشن پشتِ عنوان، سبزآبیِ برند پشتِ لبهٔ کارت، آبیِ
   *  کم‌رنگ در حاشیهٔ پایان و سبزآبیِ پشتیبان پایین. تفاوتِ روشنی در سربرگ ~۴۵ از
   *  ۲۵۵؛ متنِ تیره روی تیره‌ترین جا هم بیش از ۷ به ۱. جای لکه‌ها در راست‌به‌چپ قرینه.
   */
  val base = if (dark) c.bg else lerp(c.bg, c.inkMuted, 0.10f)
  drawRect(base)
  val w = size.width
  val h = size.height
  val rtl = layoutDirection == LayoutDirection.Rtl
  fun blob(x: Float, y: Float, r: Float, color: Color, a0: Float, a1: Float) = drawRect(
    Brush.radialGradient(
      listOf(color.copy(alpha = a0), color.copy(alpha = a1), color.copy(alpha = 0f)),
      center = Offset(w * (if (rtl) x else 1f - x), h * y), radius = w * r,
    ),
  )
  val shade = if (dark) c.black else lerp(base, c.ink, 0.16f)
  val light = if (dark) lerp(c.card, c.tile, 0.6f) else c.card
  val brand = lerp(c.card, c.accent, if (dark) 0.30f else 0.20f)
  val sky = lerp(c.card, PhoneV3Support.blue, if (dark) 0.28f else 0.16f)
  val mint = lerp(c.card, PhoneV3Support.teal, if (dark) 0.26f else 0.18f)
  blob(0.10f, 0.15f, 0.60f, shade, 0.85f, 0.40f)
  blob(0.80f, 0.04f, 0.65f, light, if (dark) 0.55f else 0.95f, if (dark) 0.25f else 0.55f)
  blob(0.58f, 0.25f, 0.58f, brand, if (dark) 0.60f else 0.85f, if (dark) 0.25f else 0.40f)
  blob(1.00f, 0.56f, 0.50f, sky, if (dark) 0.55f else 0.90f, if (dark) 0.22f else 0.45f)
  blob(0.00f, 0.86f, 0.60f, mint, if (dark) 0.50f else 0.90f, if (dark) 0.20f else 0.45f)
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

/** دکمهٔ گردِ سفید با سایهٔ نرم (زنگِ r4، برگشتِ r5) — همان `V3IconButton`ِ کیت */
@Composable
internal fun V3RoundButton(
  icon: ImageVector,
  description: String,
  size: Dp = 44.dp,
  iconSize: Dp = 22.dp,
  dot: Boolean = false,
  onClick: () -> Unit,
) = ir.vil3ntec.tohid.ui.v3.V3IconButton(icon, description, size, iconSize, dot, onClick)

/** دکمهٔ برگشت: در راست‌به‌چپ رو به راست (سمتِ آغاز) — همان `V3BackButton`ِ کیت */
@Composable
internal fun V3BackButton(onClick: () -> Unit) = ir.vil3ntec.tohid.ui.v3.V3BackButton(onClick)

/** سربرگِ r5: دکمهٔ گردِ برگشت در آغاز، عنوانِ وسط */
@Composable
internal fun V3Header(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  Box(modifier.fillMaxWidth().height(60.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
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

/**
 *  سرخطِ گروه، بیرونِ کارت (r5): ۱۵sp و نیم‌پُررنگ، خاکستریِ تیره (میانِ متن و
 *  متنِ کم‌رنگ) تا از نامِ ردیف‌ها جدا خوانده شود.
 */
@Composable
internal fun V3GroupTitle(text: String, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  Text(
    text,
    style = v3Text(15, FontWeight.SemiBold, 22),
    color = lerp(c.ink, c.inkMuted, 0.25f),
    modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 9.dp).semantics { heading() },
  )
}

/** کارتِ گروه (شکلِ r5): گوشهٔ ۱۸ و مرزِ دیدنیِ ۱dp در هر دو تم (VILL3N §۶) — همان `V3Card`ِ کیت */
@Composable
internal fun V3Card(
  modifier: Modifier = Modifier,
  radius: Dp = PhoneV3.groupRadius,
  padding: PaddingValues = PaddingValues(vertical = 8.dp),
  content: @Composable ColumnScope.() -> Unit,
) = ir.vil3ntec.tohid.ui.v3.V3Card(modifier, radius, padding, content)

/**
 *  ردیفِ r5 — همان `V3ListRow`ِ کیت (یک پیاده‌سازی برای «بیشتر»، «تنظیمات» و
 *  «حساب من»): کاشیِ آیکن (رنگیِ `hue` یا خاکستری)، نام، و در پایان مقدار /
 *  کلید / فلش. `tile = false` همان ردیفِ فهرستِ r4 است.
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
  hue: ir.vil3ntec.tohid.ui.v3.V3Hue? = null,
  onClick: (() -> Unit)? = null,
) = ir.vil3ntec.tohid.ui.v3.V3ListRow(
  title = label,
  modifier = modifier,
  subtitle = description,
  icon = icon,
  hue = hue,
  tile = tile,
  value = value,
  trailing = toggle?.let { on -> { ir.vil3ntec.tohid.ui.v3.V3Toggle(on, label, onToggle) } },
  danger = danger,
  chevron = chevron,
  minHeight = height,
  onClick = onClick,
)

/** خطِ مویی میانِ ردیف‌های فهرستِ r4 (سراسری) */
@Composable
internal fun V3Divider(start: Dp = 0.dp) = ir.vil3ntec.tohid.ui.v3.V3Divider(start)

/** کلیدِ r5 — همان `V3Toggle`ِ کیت */
@Composable
internal fun V3Toggle(checked: Boolean, label: String, onToggle: ((Boolean) -> Unit)?) =
  ir.vil3ntec.tohid.ui.v3.V3Toggle(checked, label, onToggle)

/** جابه‌جاییِ زیرصفحه‌ها (بندِ G) — همان `V3PageSwitch`ِ کیت (shared-axis، آینه در راست‌به‌چپ) */
@Composable
internal fun <T> V3Pages(target: T, depth: (T) -> Int, content: @Composable (T) -> Unit) =
  ir.vil3ntec.tohid.ui.v3.V3PageSwitch(target, depth, content = content)
