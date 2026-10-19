package ir.vil3ntec.tohid.ui.courier

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import kotlinx.coroutines.delay

/*
 *  تکه‌های ظاهرِ گوشیِ نسخهٔ ۳ برای حالتِ پیک (design-ref/phone-v3/SPEC.md).
 *
 *  فقط از توکن‌های `PhoneV3`: زمینهٔ خاکستریِ روشن، کارتِ سفید با سایهٔ نرم
 *  (نه خطِ تیره)، آیکنِ خطیِ تک‌رنگ، دکمهٔ اصلیِ قرصیِ پُررنگ و بزرگ برای
 *  کارِ یک‌دستی. هر حرکت با `Motion.enabled` خاموش می‌شود (بخشِ G).
 */

internal val PillShape = RoundedCornerShape(percent = 50)
internal val CardShape = RoundedCornerShape(24.dp)
internal val GroupShape = RoundedCornerShape(PhoneV3.groupRadius)
private val FieldShape = RoundedCornerShape(16.dp)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** مدتِ حرکت — صفر وقتی کاربر یا گوشی حرکت را خاموش کرده */
internal fun motionMs(ms: Int): Int = if (Motion.enabled) ms else 0

/** متنِ برنامه با همان قلمِ فارسیِ تم، فقط اندازه و وزن عوض می‌شود */
@Composable
internal fun v3Style(size: TextUnit, weight: FontWeight = FontWeight.Normal): TextStyle =
  MaterialTheme.typography.bodyLarge.copy(
    fontSize = size,
    fontWeight = weight,
    lineHeight = size * 1.45f,
    letterSpacing = 0.sp,
  )

/** کارتِ سفید با سایهٔ نرم — جای خطِ دورِ تیره */
internal fun Modifier.v3Card(p: PhoneV3Colors, shape: Shape = CardShape, elevation: Dp = 10.dp): Modifier =
  this
    .shadow(elevation, shape, clip = false, ambientColor = p.black.copy(alpha = 0.10f), spotColor = p.black.copy(alpha = 0.10f))
    .background(p.card, shape)

/** فشار = کمی کوچک شدن (بخشِ G) */
@Composable
private fun pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Float {
  val down by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (down && Motion.enabled) pressed else 1f,
    animationSpec = if (Motion.enabled) spring(dampingRatio = 0.6f, stiffness = 900f) else snap(),
    label = "press",
  )
  return scale
}

internal enum class ButtonTone { INK, NEGATIVE }

/** دکمهٔ اصلیِ هر پله — قرصِ پُررنگ، ۵۶dp، با جابه‌جاییِ نرمِ متن وقتی پله عوض می‌شود */
@Composable
internal fun V3PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  busy: Boolean = false,
  tone: ButtonTone = ButtonTone.INK,
  icon: ImageVector? = null,
) {
  val p = PhoneV3.colors
  val interaction = remember { MutableInteractionSource() }
  val scale = pressScale(interaction)
  val bg by animateColorAsState(
    when {
      !enabled -> p.hairline
      tone == ButtonTone.NEGATIVE -> p.negative
      else -> p.ink
    },
    animationSpec = tween(motionMs(200)), label = "primaryBg",
  )
  val fg = when {
    !enabled -> p.inkMuted
    tone == ButtonTone.NEGATIVE -> p.onBlack
    else -> p.card
  }
  Box(
    modifier
      .height(56.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(PillShape)
      .background(bg)
      .clickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        enabled = enabled && !busy,
        role = Role.Button,
        onClick = onClick,
      )
      .padding(horizontal = 20.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (busy) {
      CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp)
    } else {
      AnimatedContent(
        targetState = text,
        transitionSpec = {
          if (Motion.enabled) {
            (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260))) togetherWith
              (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(200)))
          } else EnterTransition.None togetherWith ExitTransition.None
        },
        label = "primaryText",
      ) { label ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
          }
          Text(label, style = v3Style(17.sp, FontWeight.Bold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

/** دکمهٔ دوم — قرصِ خاکستریِ روشن روی کارتِ سفید، بی خطِ دور */
@Composable
internal fun V3SecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  tone: ButtonTone = ButtonTone.INK,
  enabled: Boolean = true,
) {
  val p = PhoneV3.colors
  val interaction = remember { MutableInteractionSource() }
  val scale = pressScale(interaction)
  val fg = when {
    !enabled -> p.chevron
    tone == ButtonTone.NEGATIVE -> p.negative
    else -> p.ink
  }
  Box(
    modifier
      .height(52.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(PillShape)
      .background(p.bg)
      .clickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
      )
      .padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
      }
      Text(text, style = v3Style(15.sp, FontWeight.SemiBold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** دکمهٔ گردِ سفید با آیکنِ خطی (مثلِ زنگِ r4) — ۴۴dp، با چرخش هنگامِ کار */
@Composable
internal fun RoundIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
  spin: Boolean = false,
  size: Dp = 44.dp,
) {
  val p = PhoneV3.colors
  val interaction = remember { MutableInteractionSource() }
  val scale = pressScale(interaction, 0.92f)
  val angle = if (spin && Motion.enabled) {
    val turn = rememberInfiniteTransition(label = "spin")
    turn.animateFloat(
      initialValue = 0f, targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinAngle",
    ).value
  } else 0f
  Box(
    Modifier
      .size(size)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .v3Card(p, CircleShape, elevation = 6.dp)
      .clip(CircleShape)
      .clickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      icon, contentDescription = contentDescription,
      tint = if (enabled || spin) p.icon else p.chevron,
      modifier = Modifier.size(22.dp).rotate(angle),
    )
  }
}

/** برچسبِ کوچکِ قرصی: نقطهٔ رنگی + متن (وضعیت همیشه متن + رنگ + نقطه) */
@Composable
internal fun DotChip(text: String, dot: Color, modifier: Modifier = Modifier, fill: Color? = null) {
  val p = PhoneV3.colors
  val dotColor by animateColorAsState(dot, animationSpec = tween(motionMs(260)), label = "dot")
  Row(
    modifier.background(fill ?: p.bg, PillShape).padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(7.dp).background(dotColor, CircleShape))
    Spacer(Modifier.width(6.dp))
    Crossfade(targetState = text, animationSpec = tween(motionMs(220)), label = "chipText") { t ->
      Text(t, style = v3Style(12.sp, FontWeight.Medium), color = p.ink, maxLines = 1)
    }
  }
}

/** برچسبِ اطلاعات با آیکنِ خطی (نقد، کدِ تحویل، عکس، …) */
@Composable
internal fun InfoChip(icon: ImageVector, text: String, positive: Boolean = false) {
  val p = PhoneV3.colors
  val fg = if (positive) p.positive else p.ink
  Row(
    Modifier.background(p.bg, PillShape).padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = if (positive) p.positive else p.icon, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(text, style = v3Style(13.sp, FontWeight.Medium), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** یادداشتِ هشدار (دکان پس خواسته، …) — متن و رنگ و آیکن، نه فقط رنگ */
@Composable
internal fun WarnNote(text: String) {
  val p = PhoneV3.colors
  Row(
    Modifier.fillMaxWidth().background(p.negative.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Info, contentDescription = null, tint = p.negative, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(10.dp))
    Text(text, style = v3Style(14.sp, FontWeight.Medium), color = p.negative)
  }
}

/** دو زبانهٔ «فعال / پایان‌یافته» — قرصِ پُررنگِ انتخاب با فنر جابه‌جا می‌شود */
@Composable
internal fun TwoTabs(
  first: Pair<String, Int>,
  second: Pair<String, Int>,
  firstSelected: Boolean,
  onSelect: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val p = PhoneV3.colors
  BoxWithConstraints(
    modifier.fillMaxWidth().height(52.dp).v3Card(p, PillShape, elevation = 6.dp).padding(4.dp),
  ) {
    val half = maxWidth / 2
    val x by animateDpAsState(
      targetValue = if (firstSelected) 0.dp else half,
      animationSpec = if (Motion.enabled) spring(dampingRatio = 0.8f, stiffness = 500f) else snap(),
      label = "tabX",
    )
    Box(Modifier.offset(x = x).width(half).fillMaxHeight().background(p.ink, PillShape))
    Row(Modifier.fillMaxSize()) {
      listOf(first to true, second to false).forEach { (tab, isFirst) ->
        val selected = isFirst == firstSelected
        val fg by animateColorAsState(if (selected) p.card else p.ink, tween(motionMs(200)), label = "tabFg")
        Row(
          Modifier.weight(1f).fillMaxHeight().clip(PillShape)
            .clickable(role = Role.Tab) { onSelect(isFirst) },
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(tab.first, style = v3Style(15.sp, FontWeight.Bold), color = fg)
          Spacer(Modifier.width(6.dp))
          Text("${tab.second}", style = v3Style(13.sp, FontWeight.Medium), color = fg.copy(alpha = 0.65f))
        }
      }
    }
  }
}

/** کادرِ ورودیِ نسخهٔ ۳: برچسب بالا، زمینهٔ خاکستریِ روشن، گوشهٔ ۱۶ */
@Composable
internal fun V3Field(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String = "",
  ltr: Boolean = false,
  keyboardType: KeyboardType = KeyboardType.Text,
  capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
  singleLine: Boolean = true,
  big: Boolean = false,
) {
  val p = PhoneV3.colors
  val style = v3Style(if (big) 24.sp else 17.sp, if (big) FontWeight.Bold else FontWeight.Medium).copy(
    color = p.ink,
    textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
  )
  Column(modifier.fillMaxWidth()) {
    Text(label, style = v3Style(13.sp, FontWeight.Medium), color = p.inkMuted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = singleLine,
      textStyle = style,
      cursorBrush = SolidColor(p.ink),
      keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
      modifier = Modifier.fillMaxWidth(),
      decorationBox = { inner ->
        Box(
          Modifier.fillMaxWidth().heightIn(min = if (big) 64.dp else 56.dp)
            .background(p.bg, FieldShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, style = style.copy(color = p.inkMuted.copy(alpha = 0.75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          inner()
        }
      },
    )
  }
}

/** گروهِ انتخابِ یکی از چند (دلیل‌ها) — مثلِ کارت‌های r5، تیکِ پُررنگ کنارِ انتخاب */
@Composable
internal fun ChoiceGroup(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
  val p = PhoneV3.colors
  Column(Modifier.fillMaxWidth().background(p.bg, GroupShape)) {
    options.forEachIndexed { i, (id, label) ->
      val on = id == selected
      Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(GroupShape)
          .clickable(role = Role.RadioButton) { onSelect(id) }
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(label, style = v3Style(15.sp, if (on) FontWeight.Bold else FontWeight.Normal), color = p.ink, modifier = Modifier.weight(1f))
        val fill by animateColorAsState(if (on) p.ink else p.card, tween(motionMs(180)), label = "choice")
        Box(Modifier.size(24.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
          if (on) Icon(Icons.Outlined.Check, contentDescription = null, tint = p.card, modifier = Modifier.size(16.dp))
        }
      }
      if (i < options.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(p.hairline))
    }
  }
}

/**
 *  برگهٔ پایینی روی صفحه (جای کادرِ وسطِ صفحه): دکمه‌ها زیرِ شست می‌مانند.
 *  ورود = بالا آمدن با فنر و تیره شدنِ زمینه؛ زدنِ زمینه یا «برگشت» = بستن.
 */
@Composable
internal fun V3Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  val p = PhoneV3.colors
  androidx.activity.compose.BackHandler(onBack = onDismiss)
  var on by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { on = true }
  val scrim by animateFloatAsState(if (on) 0.45f else 0f, tween(motionMs(220)), label = "scrim")
  val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
  Box(Modifier.fillMaxSize()) {
    Box(
      Modifier.fillMaxSize().background(p.black.copy(alpha = scrim))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    AnimatedVisibility(
      visibleState = shown,
      modifier = Modifier.align(Alignment.BottomCenter),
      enter = slideInVertically(
        if (Motion.enabled) spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow) else snap<IntOffset>(),
      ) { it } + fadeIn(tween(motionMs(160))),
      label = "sheet",
    ) {
      Column(
        Modifier.fillMaxWidth()
          .background(p.card, SheetShape)
          //  زدنِ خودِ برگه آن را نمی‌بندد
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
          .navigationBarsPadding()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
      ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(p.hairline, PillShape))
        Spacer(Modifier.height(16.dp))
        content()
      }
    }
  }
}

/** عنوانِ برگه — بزرگ و پُررنگ مثلِ r2 */
@Composable
internal fun SheetTitle(text: String, sub: String? = null) {
  val p = PhoneV3.colors
  Text(text, style = v3Style(22.sp, FontWeight.Bold), color = p.ink)
  if (!sub.isNullOrBlank()) {
    Spacer(Modifier.height(4.dp))
    Text(sub, style = v3Style(14.sp), color = p.inkMuted)
  }
}

/** ورودِ پلکانیِ ردیفِ فهرست (بخشِ G): کم‌رنگ ⇐ پُررنگ و کمی بالا آمدن، فقط بارِ اول */
@Composable
internal fun rememberAppear(key: String, index: Int, seen: MutableSet<String>): Animatable<Float, AnimationVector1D> {
  val first = key !in seen
  val appear = remember(key) { Animatable(if (first && Motion.enabled) 0f else 1f) }
  LaunchedEffect(key) {
    seen += key
    if (appear.value < 1f) {
      delay(minOf(index, 6) * 55L)
      appear.animateTo(1f, tween(300))
    }
  }
  return appear
}

/** عددِ وسطِ برگه (مبلغِ نقد) — بزرگ و پُررنگ مثلِ r8 */
@Composable
internal fun BigAmount(label: String, amount: String) {
  val p = PhoneV3.colors
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = v3Style(14.sp), color = p.inkMuted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(2.dp))
    Text(amount, style = v3Style(34.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center, maxLines = 1)
  }
}
