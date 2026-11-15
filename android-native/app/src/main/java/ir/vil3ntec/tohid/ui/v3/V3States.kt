package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.StrKit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import ir.vil3ntec.tohid.core.text.PlainError
import ir.vil3ntec.tohid.ui.v3.art.V3ArtKind
import ir.vil3ntec.tohid.ui.v3.art.V3Burst
import ir.vil3ntec.tohid.ui.v3.art.V3CheckBurst
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.isDark
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone

/**
 *  حالت‌های صفحه: خالی، در حالِ بار، پیام، وضعیت، و تیکِ موفقیت.
 *
 *  • `V3EmptyState`: تصویرِ متحرکِ همان زمینه (`V3Illustration`، از روی همان آیکن)، عنوان، یک خط
 *    توضیح و **یک** کارِ اصلی؛ آیکنِ خطا (ابرِ قطع، بی وای‌فای…) ⇒ همان الگوی خطا با متنِ ساده‌شده.
 *  • `V3Skeleton`: ردیف‌های خاکستریِ لرزان به‌جای چرخنده (با «کاهشِ حرکت» ثابت).
 *  • `V3Message`: کادرِ پیامِ §۹ (موفق/اطلاع/هشدار/خطا) با آیکنِ معنادار — رنگ تنها نیست.
 *  • `V3StatusPill`: قرصِ §۸ — نقطه + برچسب، همان معنی در همه‌جا.
 *  • `V3Message`: خطا ⇒ `V3InlineError` (تصویرِ کوچک، متنِ ساده‌شده، کارِ ۴۸dp).
 *  • `V3SuccessCheck`: جشنِ ۷۰۰ میلی‌ثانیه‌ای (`V3CheckBurst`): دایره، تیک، حلقه و چند ذره.
 */
@Composable
fun V3EmptyState(
  icon: ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  body: String? = null,
  action: String? = null,
  onAction: (() -> Unit)? = null,
  /** تصویرِ دیگری جز آنچه آیکن می‌گوید (null ⇒ از آیکن: `V3ArtKind.forIcon`) */
  art: V3ArtKind? = null,
) {
  val kind = art ?: V3ArtKind.forIcon(icon)
  if (kind.isError) {
    //  حالتِ «بار نشد» با آیکنِ خطا: همان الگوی خطا — متنِ رسیده (گاهی پیامِ خامِ سرور) ساده می‌شود و خطِ «چه کنید» دارد؛
    //  متنی که خودش می‌گوید چه شد (بی اینترنت، سرور) تصویرِ همان را می‌گیرد
    val said = PlainError.kindOf(title, body)
    val shown = if (art == null && said != PlainError.Kind.OTHER) V3ArtKind.forError(said) else kind
    val hint = V3ErrorText.kind(shown, title, body)
    V3StateLayout(
      art = shown,
      title = PlainError.text(title) ?: V3ErrorText.title(hint),
      modifier = modifier,
      body = PlainError.text(body) ?: V3ErrorText.hint(hint),
      action = action,
      onAction = onAction,
    )
  } else {
    V3StateLayout(kind, title, modifier, body, action, onAction)
  }
}

/**
 *  چند ردیفِ اسکلت (کاشی + دو خط) در یک کارت، با درخششِ روان. فقط رسم عوض می‌شود:
 *  یک قلمِ ثابت (`remember`) که در هر فریم جابه‌جا رسم می‌شود — بی ساختنِ دوباره و بی
 *  بازترکیبِ ردیف‌ها.
 */
@Composable
fun V3Skeleton(rows: Int = 5, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val base = c.tile
  val shine = if (c.isDark) c.hairline else c.card
  val shimmer = if (Motion.enabled) {
    v3LiveFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), "shimmerX")
  } else null
  val brush = remember(base, shine) { Brush.linearGradient(listOf(base, shine, base), start = Offset(-300f, 0f), end = Offset(0f, 0f)) }
  val bone = Modifier.drawBehind {
    val dx = (shimmer?.value ?: 0.5f) * 600f
    translate(left = dx) { drawRect(brush, topLeft = Offset(-dx, 0f), size = size) }
  }
  V3Card(modifier.semantics { contentDescription = StrKit.working }, padding = PaddingValues(vertical = 6.dp)) {
    repeat(rows) { i ->
      if (i > 0) V3Divider(start = 64.dp)
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).then(bone))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).then(bone))
          Spacer(Modifier.height(8.dp))
          Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(5.dp)).then(bone))
        }
      }
    }
  }
}

/** کادرِ پیام (§۹) */
@Composable
fun V3Message(
  tone: V3Tone,
  title: String,
  modifier: Modifier = Modifier,
  body: String? = null,
  action: String? = null,
  onAction: (() -> Unit)? = null,
) {
  if (tone == V3Tone.ERROR) {
    //  خطا: تصویرِ کوچکِ متحرک و متنِ ساده‌شده (`V3ErrorState.kt`)؛ همان کادر و همان ناحیهٔ زنده
    V3InlineError(title, modifier, body, action, onAction)
    return
  }
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.tone(c, tone)
  val icon = when (tone) {
    V3Tone.SUCCESS -> Icons.Outlined.CheckCircle
    V3Tone.INFO -> Icons.Outlined.Info
    V3Tone.WARNING -> Icons.Outlined.WarningAmber
    V3Tone.ERROR -> Icons.Outlined.ErrorOutline
  }
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier.fillMaxWidth().clip(shape).background(pair.bg).border(1.dp, pair.border, shape)
      //  WCAG 4.1.3: خطایی که پس از زدنِ دکمه می‌آید را صفحه‌خوان هم می‌گوید (نه فقط رنگ و جا)
      .then(if (tone == V3Tone.ERROR) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
      .padding(14.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Icon(icon, contentDescription = null, tint = PhoneV3Roles.toneIcon(c, tone), modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = V3Type.body, color = pair.fg)
      if (body != null) Text(body, style = V3Type.footnote, color = pair.fg.copy(alpha = 0.85f))
      if (action != null && onAction != null) {
        Spacer(Modifier.height(6.dp))
        V3TextButton(action, onAction)
      }
    }
  }
}

/** قرصِ وضعیتِ سفارش (§۸): نقطه + برچسب */
@Composable
fun V3StatusPill(status: V3Status, label: String, modifier: Modifier = Modifier) {
  val pair = PhoneV3Roles.status(PhoneV3.colors, status)
  Row(
    modifier.clip(RoundedCornerShape(50)).background(pair.bg).padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(pair.fg))
    Spacer(Modifier.width(6.dp))
    Text(label, style = V3Type.caption, color = pair.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/**
 *  تیکِ موفقیت: جشنِ کوتاهِ [V3Burst.MS] میلی‌ثانیه (`V3CheckBurst`): دایره با فنرِ کوچک باز می‌شود، تیک کشیده می‌شود
 *  و چند ذرهٔ رنگی پخش و محو می‌شوند — یک بوم، یک عدد که فقط در رسم خوانده می‌شود، بی گرفتنِ لمس. با لرزشِ سبکِ
 *  `HapticFeedbackType.Confirm` (اندروید). «کاهشِ حرکت» ⇒ همان دایره و تیکِ پایانی، بی ذره.
 *  @param haptic اگر صفحه خودش لرزشِ «ثبت شد» (`Haptics.success`) را می‌زند، false
 */
@Composable
fun V3SuccessCheck(visible: Boolean, modifier: Modifier = Modifier, size: Dp = 64.dp, haptic: Boolean = true) {
  val c = PhoneV3.colors
  val ok = PhoneV3Roles.tone(c, V3Tone.SUCCESS)
  val sparks = listOf(ok.fg, c.source.brandPrimary, c.source.accent(3), c.source.accent(0))
  val feedback = LocalHapticFeedback.current
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)),
    exit = fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)),
  ) {
    val draw = remember { Animatable(if (Motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
      if (haptic) feedback.performHapticFeedback(HapticFeedbackType.Confirm)
      draw.animateTo(1f, V3Burst.spec())
    }
    V3CheckBurst({ draw.value }, disc = ok.bg, tick = ok.fg, sparks = sparks, modifier = Modifier.size(size), burst = Motion.enabled)
  }
}
