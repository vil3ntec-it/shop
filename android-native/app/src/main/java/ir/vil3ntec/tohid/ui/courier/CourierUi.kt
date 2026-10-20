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
import androidx.compose.foundation.border
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
import ir.vil3ntec.tohid.ui.theme.PhoneV3Tone
import kotlinx.coroutines.delay

/*
 *  تکه‌های ظاهرِ حالتِ پیک: شکل از گوشیِ نسخهٔ ۳ و مرجعِ پیکِ صاحب محصول
 *  (design-ref/vill3n/courier-ref.jpg)، رنگ فقط از سیستمِ رنگِ VILL3N
 *  (`PhoneV3.colors` و `PhoneV3.ui`): زمینهٔ روشن، کارتِ سفید با مرزِ دیدنی و سایهٔ
 *  سبک، سطحِ تیرهٔ سرمه‌ای، دکمهٔ اصلیِ سبزآبی. هر حرکت با `Motion.enabled` خاموش.
 */

internal val PillShape = RoundedCornerShape(percent = 50)
internal val CardShape = RoundedCornerShape(20.dp)
internal val GroupShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(14.dp)
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

/** کارت: رنگ + مرزِ دیدنی + سایهٔ سبک (سیستمِ رنگ §۶: سایه هرگز جای مرز نیست) */
internal fun Modifier.v3Card(p: PhoneV3Colors, shape: Shape = CardShape, elevation: Dp = 4.dp): Modifier =
  this
    .shadow(elevation, shape, clip = false, ambientColor = p.black.copy(alpha = 0.08f), spotColor = p.black.copy(alpha = 0.08f))
    .background(p.card, shape)
    .border(1.dp, p.hairline, shape)

/** سطحِ تیرهٔ سرمه‌ای (کارتِ «مأموریتِ جاری»، برگهٔ پیگیری) */
internal fun Modifier.v3Dark(p: PhoneV3Colors, shape: Shape = CardShape): Modifier =
  this.background(p.black, shape).border(1.dp, p.blackRaised, shape)

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

internal enum class ButtonTone { BRAND, NEGATIVE }

/** دکمهٔ اصلیِ هر پله — قرصِ سبزآبی، ۵۶dp؛ متن با پله عوض می‌شود و نرم می‌لغزد */
@Composable
internal fun V3PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  busy: Boolean = false,
  tone: ButtonTone = ButtonTone.BRAND,
  icon: ImageVector? = null,
  onDark: Boolean = false,
) {
  val ui = PhoneV3.ui
  val interaction = remember { MutableInteractionSource() }
  val scale = pressScale(interaction)
  val bg by animateColorAsState(
    when {
      !enabled -> ui.disabledBg
      tone == ButtonTone.NEGATIVE -> ui.danger
      onDark -> ui.primaryOnDark
      else -> ui.primary
    },
    animationSpec = tween(motionMs(200)), label = "primaryBg",
  )
  val fg = when {
    !enabled -> ui.disabledFg
    tone == ButtonTone.NEGATIVE -> ui.onDanger
    onDark -> ui.onPrimaryOnDark
    else -> ui.onPrimary
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

/** دکمهٔ دوم — «خنثی» ِ سیستمِ رنگ (روی سرمه‌ای: سطحِ برآمده) */
@Composable
internal fun V3SecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  tone: ButtonTone = ButtonTone.BRAND,
  enabled: Boolean = true,
  onDark: Boolean = false,
) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  val interaction = remember { MutableInteractionSource() }
  val scale = pressScale(interaction)
  val bg = when {
    onDark -> p.blackRaised
    else -> ui.neutralBg
  }
  val fg = when {
    !enabled -> ui.disabledFg
    tone == ButtonTone.NEGATIVE -> if (onDark) PhoneV3UiDarkNegative else ui.cancelled.fg
    onDark -> p.onBlack
    else -> ui.neutralFg
  }
  Box(
    modifier
      .height(52.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(PillShape)
      .background(bg)
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

/** متنِ «ناموفق/رد» روی سطحِ سرمه‌ای — همان سرخِ وضعیتِ «لغو»ِ شب */
private val PhoneV3UiDarkNegative: Color
  get() = ir.vil3ntec.tohid.ui.theme.PhoneV3UiDark.cancelled.fg

/** دکمهٔ گرد با آیکنِ خطی (مثلِ زنگِ مرجع) — ۴۴dp، با چرخش هنگامِ کار و نقطهٔ تازه */
@Composable
internal fun RoundIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
  spin: Boolean = false,
  size: Dp = 44.dp,
  dot: Boolean = false,
) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  val interaction = remember { MutableInteractionSource() }
  val scale = pressScale(interaction, 0.92f)
  val angle = if (spin && Motion.enabled) {
    val turn = rememberInfiniteTransition(label = "spin")
    turn.animateFloat(
      initialValue = 0f, targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinAngle",
    ).value
  } else 0f
  Box(Modifier.size(size)) {
    Box(
      Modifier
        .fillMaxSize()
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .v3Card(p, CircleShape, elevation = 2.dp)
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
        tint = if (enabled || spin) p.ink else p.chevron,
        modifier = Modifier.size(22.dp).rotate(angle),
      )
    }
    if (dot) {
      Box(
        Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 10.dp).size(9.dp)
          .background(ui.danger, CircleShape).border(1.5.dp, p.card, CircleShape),
      )
    }
  }
}

/** برچسبِ وضعیت: متن + رنگ + نقطه روی زمینهٔ کم‌رنگِ همان وضعیت (§۸) */
@Composable
internal fun TonePill(text: String, tone: PhoneV3Tone, modifier: Modifier = Modifier) {
  val fg by animateColorAsState(tone.fg, animationSpec = tween(motionMs(260)), label = "pillFg")
  val bg by animateColorAsState(tone.bg, animationSpec = tween(motionMs(260)), label = "pillBg")
  Row(
    modifier.background(bg, PillShape).padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(7.dp).background(fg, CircleShape))
    Spacer(Modifier.width(6.dp))
    Crossfade(targetState = text, animationSpec = tween(motionMs(220)), label = "pillText") { t ->
      Text(t, style = v3Style(12.sp, FontWeight.SemiBold), color = fg, maxLines = 1)
    }
  }
}

/** برچسبِ اطلاعات با آیکنِ خطی (نقد، کدِ تحویل، …) روی سطحِ درونی */
@Composable
internal fun InfoChip(icon: ImageVector, text: String, positive: Boolean = false) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  val fg = if (positive) ui.success.fg else p.ink
  Row(
    Modifier.background(if (positive) ui.success.bg else p.tile, PillShape)
      .border(1.dp, if (positive) ui.success.bg else p.hairline, PillShape)
      .padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = if (positive) ui.success.fg else p.icon, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(text, style = v3Style(13.sp, FontWeight.Medium), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** یادداشتِ هشدار (دکان پس خواسته، …) — متن و رنگ و آیکن، نه فقط رنگ */
@Composable
internal fun WarnNote(text: String) {
  val ui = PhoneV3.ui
  Row(
    Modifier.fillMaxWidth().background(ui.cancelled.bg, RoundedCornerShape(14.dp)).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Info, contentDescription = null, tint = ui.cancelled.fg, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(10.dp))
    Text(text, style = v3Style(14.sp, FontWeight.Medium), color = ui.cancelled.fg)
  }
}

/**
 *  دو زبانه — قرصِ انتخاب با فنر جابه‌جا می‌شود. روی سطحِ سرمه‌ای (برگهٔ پیگیری،
 *  مثلِ «Tracking / Shipping Detail» ِ مرجع) قرصِ سبزآبیِ روشن.
 */
@Composable
internal fun TwoTabs(
  first: Pair<String, Int?>,
  second: Pair<String, Int?>,
  firstSelected: Boolean,
  onSelect: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  onDark: Boolean = false,
) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  val track = if (onDark) Modifier.background(p.blackRaised, PillShape) else Modifier.v3Card(p, PillShape, elevation = 2.dp)
  BoxWithConstraints(modifier.fillMaxWidth().height(48.dp).then(track).padding(4.dp)) {
    val half = maxWidth / 2
    val x by animateDpAsState(
      targetValue = if (firstSelected) 0.dp else half,
      animationSpec = if (Motion.enabled) spring(dampingRatio = 0.8f, stiffness = 500f) else snap(),
      label = "tabX",
    )
    Box(Modifier.offset(x = x).width(half).fillMaxHeight().background(if (onDark) ui.primaryOnDark else ui.primary, PillShape))
    Row(Modifier.fillMaxSize()) {
      listOf(first to true, second to false).forEach { (tab, isFirst) ->
        val selected = isFirst == firstSelected
        val on = if (onDark) ui.onPrimaryOnDark else ui.onPrimary
        val off = if (onDark) p.onBlackMuted else p.ink
        val fg by animateColorAsState(if (selected) on else off, tween(motionMs(200)), label = "tabFg")
        Row(
          Modifier.weight(1f).fillMaxHeight().clip(PillShape)
            .clickable(role = Role.Tab) { onSelect(isFirst) },
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(tab.first, style = v3Style(14.sp, FontWeight.Bold), color = fg, maxLines = 1)
          tab.second?.let {
            Spacer(Modifier.width(6.dp))
            Text("$it", style = v3Style(13.sp, FontWeight.Medium), color = fg.copy(alpha = 0.7f))
          }
        }
      }
    }
  }
}

/** کادرِ ورودی: برچسب بالا، سطحِ درونی با مرزِ دیدنی */
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
    if (label.isNotEmpty()) {
      Text(label, style = v3Style(13.sp, FontWeight.Medium), color = p.inkMuted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    }
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
          Modifier.fillMaxWidth().heightIn(min = if (big) 64.dp else 54.dp)
            .background(p.tile, FieldShape)
            .border(1.dp, p.hairline, FieldShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, style = style.copy(color = p.inkMuted.copy(alpha = 0.8f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          inner()
        }
      },
    )
  }
}

/** کادرِ جست‌وجوی قرصی (مثلِ «Resi number» ِ مرجع) با ذره‌بینِ خطی */
@Composable
internal fun SearchPill(value: String, onValueChange: (String) -> Unit, placeholder: String, icon: ImageVector) {
  val p = PhoneV3.colors
  val style = v3Style(15.sp, FontWeight.Medium).copy(color = p.ink)
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    textStyle = style,
    cursorBrush = SolidColor(p.ink),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    modifier = Modifier.fillMaxWidth(),
    decorationBox = { inner ->
      Row(
        Modifier.fillMaxWidth().height(50.dp).background(p.card, PillShape).border(1.dp, p.hairline, PillShape)
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(icon, contentDescription = null, tint = p.icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
          if (value.isEmpty()) Text(placeholder, style = style.copy(color = p.inkMuted), maxLines = 1)
          inner()
        }
      }
    },
  )
}

/** گروهِ انتخابِ یکی از چند (دلیل‌ها) — تیکِ برند کنارِ انتخاب */
@Composable
internal fun ChoiceGroup(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  Column(Modifier.fillMaxWidth().background(p.tile, GroupShape).border(1.dp, p.hairline, GroupShape)) {
    options.forEachIndexed { i, (id, label) ->
      val on = id == selected
      Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(GroupShape)
          .clickable(role = Role.RadioButton) { onSelect(id) }
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(label, style = v3Style(15.sp, if (on) FontWeight.Bold else FontWeight.Normal), color = p.ink, modifier = Modifier.weight(1f))
        val fill by animateColorAsState(if (on) ui.primary else p.card, tween(motionMs(180)), label = "choice")
        Box(
          Modifier.size(24.dp).background(fill, CircleShape).border(1.dp, if (on) ui.primary else p.hairline, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          if (on) Icon(Icons.Outlined.Check, contentDescription = null, tint = ui.onPrimary, modifier = Modifier.size(16.dp))
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

/** عنوانِ برگه */
@Composable
internal fun SheetTitle(text: String, sub: String? = null) {
  val p = PhoneV3.colors
  Text(text, style = v3Style(22.sp, FontWeight.Bold), color = p.ink)
  if (!sub.isNullOrBlank()) {
    Spacer(Modifier.height(4.dp))
    Text(sub, style = v3Style(14.sp), color = p.inkMuted)
  }
}

/** سرخطِ بخش (مثلِ «Current Tracking» ِ مرجع) با پیوندِ اختیاریِ «همه» */
@Composable
internal fun SectionHead(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = v3Style(17.sp, FontWeight.Bold), color = p.ink, modifier = Modifier.weight(1f))
    if (action != null && onAction != null) {
      Text(
        action, style = v3Style(14.sp, FontWeight.SemiBold), color = ui.iconActive,
        modifier = Modifier.clip(PillShape).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
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

/** عددِ وسطِ برگه (مبلغِ نقد) — بزرگ و پُررنگ */
@Composable
internal fun BigAmount(label: String, amount: String) {
  val p = PhoneV3.colors
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = v3Style(14.sp), color = p.inkMuted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(2.dp))
    Text(amount, style = v3Style(34.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center, maxLines = 1)
  }
}
