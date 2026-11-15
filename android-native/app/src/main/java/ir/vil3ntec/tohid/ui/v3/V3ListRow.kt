package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.StrA11y
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.iconTile
import ir.vil3ntec.tohid.ui.screens.pressed
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** رنگِ نشانهٔ کاشی (VILL3N r7: برند، سبزآبی، آبی، نارنجی، بنفش) — `null` یعنی خاکستریِ r5 */
typealias V3Hue = PhoneV3Palette.Hue

/** کارِ کشیدنی روی ردیف (ویرایش، حذف، بیشتر) */
data class V3SwipeAction(
  val icon: ImageVector,
  val label: String,
  val kind: V3ButtonKind = V3ButtonKind.NEUTRAL,
  val onClick: () -> Unit,
)

/** کاشیِ آیکنِ ردیف: سطحِ درونی (surfaceElevated) با نشانهٔ رنگی یا خاکستری */
@Composable
fun V3IconTile(icon: ImageVector, hue: V3Hue? = null, danger: Boolean = false, size: Dp = 40.dp, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val tint = when {
    danger -> c.negative
    hue != null -> PhoneV3Palette.glyph(c, hue)
    else -> c.icon
  }
  //  THEME-V2: آیکنِ رنگی روی پس‌زمینهٔ نرمِ همان رنگ
  val bg = if (hue != null && !danger) PhoneV3Palette.soft(c, hue) else c.iconTile
  Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.53f))
  }
}

/**
 *  نمای ردیف: `LIST` ردیفِ داده (کالا، فاکتور، قرض‌دار — همان پیشین)؛ `NAV` ردیفِ ناوبری و تنظیمات (`V3NavRow`) —
 *  همان «بیشتر» که صاحب‌کار پسندید (۶۵r).
 */
enum class V3RowLook { LIST, NAV }

/**
 *  نمای پیش‌فرضِ ردیف‌های یک صفحه: قابِ تنظیمات (`SettingsPageFrame`) `NAV` می‌گذارد تا **هر** ردیفِ درونِ صفحه‌های
 *  تنظیمات — ردیفِ بخش، کلید (`V3ToggleRow`)، ردیفِ کار و فهرستِ انتخاب — همان ردیفِ «بیشتر» باشد (۶۵r).
 */
val LocalV3RowLook = androidx.compose.runtime.compositionLocalOf { V3RowLook.LIST }

/** رنگِ کاشیِ ردیفِ `NAV` ای که رنگِ خودش را نگفته — قابِ تنظیمات رنگِ همان بخش را می‌دهد (`SettingsHues`) */
val LocalV3RowHue = androidx.compose.runtime.compositionLocalOf<V3Hue?> { null }

/**
 *  هر `V3ListRow`/`V3ToggleRow` ِ درونِ [content] همان ردیفِ «بیشتر» (`NAV`) با کاشیِ [hue] (وقتی رنگِ خودش را نگفته)
 *  — برای صفحه‌های ناوبری و تنظیمات‌مانند (تنظیمات، «حساب من» و برگه‌هایش، اشتراک، مرکزِ مدیریت)، نه فهرستِ داده.
 */
@Composable
fun V3NavRows(hue: V3Hue?, content: @Composable () -> Unit) {
  androidx.compose.runtime.CompositionLocalProvider(LocalV3RowLook provides V3RowLook.NAV, LocalV3RowHue provides hue, content = content)
}

/**
 *  اندازه‌های **یک** سبکِ ردیفِ ناوبری و تنظیمات (۶۵r، خواستِ صاحب‌کار: «دقیق همه چی منظم باشه شبیهِ اون عکسِ اول»)،
 *  از روی خودِ «بیشتر» اندازه گرفته: کاشیِ ۴۰ با گوشهٔ ۱۲ و نشانهٔ ۲۱، عنوانِ یک‌خطیِ ۱۶ (`V3Type.rowTitle`)، خطِ
 *  راهنمای یک‌خطیِ کم‌رنگِ ۱۳ (`V3Type.rowHelp`)، فاصلهٔ آغاز ۱۲ / پایان ۱۶ / بالا و پایین ۸، کاشی تا متن ۱۲،
 *  بلندیِ کمِ ۵۶ (ردیفِ دوخطی هم ۲۲ + ۱۸ + ۱۶ = ۵۶) و خطِ جداکننده از زیرِ متن (۶۴). کناره‌های صفحه ۲۰.
 *  عدد را در صفحه ننویس؛ همین‌ها (`V3NavRowGuardTest`).
 */
object V3NavRowSpec {
  val TILE: Dp = 40.dp
  val MIN_HEIGHT: Dp = 56.dp
  val PAD_START: Dp = 12.dp
  val PAD_END: Dp = 16.dp
  val PAD_V: Dp = 8.dp
  val GAP: Dp = 12.dp
  /** ردیفِ بی آیکن (فهرستِ وضعیت): متن از ۱۶ */
  val PAD_START_PLAIN: Dp = 16.dp
  /** خطِ مویی از زیرِ متن: آغاز + کاشی + فاصله */
  val DIVIDER_START: Dp = PAD_START + TILE + GAP
  /** کناره‌های فهرست در صفحه (همان «بیشتر») */
  val GUTTER: Dp = 20.dp
  /** پهنای بیشینهٔ مقدارِ پایانِ ردیف */
  val VALUE_MAX: Dp = 160.dp
  const val TITLE_SP = 16
  const val HELP_SP = 13
  /** نشانِ آزمون روی کاشی و بدنهٔ ردیف (فقط `testTag`؛ صفحه‌خوان آن را نمی‌خواند) */
  const val TILE_TAG = "v3-nav-tile"
  const val ROW_TAG = "v3-nav-row"
}

/**
 *  **ردیفِ ناوبری و تنظیماتِ همهٔ برنامه** (۶۵r) — «بیشتر»، صفحهٔ اول و دسته‌های تنظیمات، صفحه‌های بخش، دستگاه‌ها،
 *  «حساب من»، مرکزِ مدیریت و پشتیبانی همین را می‌کشند: کاشیِ رنگیِ آیکن (`V3IconTile`، رنگ از نقشهٔ همان بخش)،
 *  عنوانِ یک‌خطی، خطِ راهنمای یک‌خطیِ کم‌رنگ (`help`، هرگز دو خط)، و در پایان یا مقدارِ کم‌رنگ کنارِ پیکان
 *  (`value`، با نقطهٔ وضعیتِ اختیاریِ `valueDot`)، یا کلید (`toggle`/`onToggle`)، یا `trailing` ِ دلخواه (برچسبِ «آزمایشی»).
 *  آیکنِ بی‌کاشی در این ردیف نیست؛ ردیفِ بی آیکن (فهرستِ وضعیت) فقط متن دارد.
 */
@Composable
fun V3NavRow(
  title: String,
  icon: ImageVector?,
  hue: V3Hue?,
  modifier: Modifier = Modifier,
  help: String? = null,
  value: String? = null,
  valueColor: Color? = null,
  valueDot: Color? = null,
  toggle: Boolean? = null,
  onToggle: ((Boolean) -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  danger: Boolean = false,
  chevron: Boolean = true,
  selected: Boolean? = null,
  onClick: (() -> Unit)? = null,
) {
  V3ListRow(
    title = title,
    modifier = modifier,
    subtitle = help,
    icon = icon,
    hue = hue,
    value = value,
    valueColor = valueColor,
    valueDot = valueDot,
    trailing = when {
      toggle != null -> ({ V3Toggle(toggle, title, onToggle) })
      else -> trailing
    },
    danger = danger,
    chevron = chevron,
    minHeight = V3NavRowSpec.MIN_HEIGHT,
    selected = selected,
    look = V3RowLook.NAV,
    onClick = onClick,
  )
}

/**
 *  ردیفِ فهرستِ نسخهٔ ۳ — یک پیاده‌سازی برای «بیشتر»، «تنظیمات» و هر فهرستِ تازه:
 *  `leading`ِ دلخواه (عکسِ کالا `V3ProductThumb`، آواتار) یا کاشیِ آیکن، عنوان و
 *  زیرعنوان، و در پایان مقدار، `trailing`ِ دلخواه (قرص، کلید، مبلغ) و فلشِ «جلو»
 *  (آینه در راست‌به‌چپ).
 *
 *  • `endActions`/`startActions`: کشیدنِ ردیف به سمتِ آغاز کارهای پایان را نشان
 *    می‌دهد و برعکس (مثلِ iOS، آینه‌شده)؛ گذشتن از آستانه یک تیکِ لرزشی می‌دهد و
 *    کشیدنِ تا بیش از نیمه کارِ اولِ همان سو را اجرا می‌کند. همه برای صفحه‌خوان
 *    «کارِ سفارشی»اند.
 *  • `selected`: حالتِ انتخابِ گروهی — دایرهٔ تیک در آغاز (null = بی انتخاب).
 *  • `onLongClick`: نگه‌داشتن (معمولاً شروعِ انتخاب یا برگهٔ کارها)؛ صفحه‌خوان آن را «کارهای بیشتر» می‌نامد.
 *  • `role`: نقشِ ردیف برای صفحه‌خوان (`V3ToggleRow` ⇒ کلید).
 *  • `below`: خطِ سومِ دلخواه زیرِ زیرعنوان («۲٬۵۰۰ ؋ • نیمه‌رسیده» با `V3AmountStatus`) وقتی پایانِ ردیف کارِ اصلی است.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun V3ListRow(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  hue: V3Hue? = null,
  /** false ⇒ آیکنِ بی‌کاشی (فهرستِ r4) */
  tile: Boolean = true,
  leading: (@Composable () -> Unit)? = null,
  value: String? = null,
  valueColor: Color? = null,
  trailing: (@Composable () -> Unit)? = null,
  danger: Boolean = false,
  chevron: Boolean = true,
  minHeight: Dp = PhoneV3.rowHeight,
  subtitleLines: Int = 2,
  startActions: List<V3SwipeAction> = emptyList(),
  endActions: List<V3SwipeAction> = emptyList(),
  selected: Boolean? = null,
  onLongClick: (() -> Unit)? = null,
  role: Role = Role.Button,
  below: (@Composable () -> Unit)? = null,
  /** `NAV` ⇒ همان ردیفِ «بیشتر» (`V3NavRow`): کاشیِ همیشگی، عنوانِ ۱۶، راهنمای یک‌خطیِ ۱۳؛ null ⇒ نمای صفحه (`LocalV3RowLook`) */
  look: V3RowLook? = null,
  /** نقطهٔ وضعیتِ کوچکِ پیش از مقدار (همگام‌سازی: رنگ + نوشته، نه رنگِ تنها) */
  valueDot: Color? = null,
  onClick: (() -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val haptic = LocalHapticFeedback.current
  val nav = (look ?: LocalV3RowLook.current) == V3RowLook.NAV
  //  ردیفِ ناوبری آیکنِ بی‌کاشی ندارد (۶۵r)؛ رنگِ نگفته ⇐ رنگِ بخشِ صفحه
  val tiled = icon != null && (tile || nav)
  val tileHue = hue ?: if (nav) LocalV3RowHue.current else null
  val minH = if (nav) maxOf(minHeight, V3NavRowSpec.MIN_HEIGHT) else minHeight
  val startPad = when {
    tile && !nav || leading != null || selected != null || tiled -> V3NavRowSpec.PAD_START
    nav -> V3NavRowSpec.PAD_START_PLAIN
    else -> 22.dp
  }
  val body: @Composable (Modifier) -> Unit = { m ->
    Row(
      m
        .fillMaxWidth()
        .heightIn(min = minH)
        .background(if (selected == true) PhoneV3Palette.selected(c) else c.card)
        .then(
          if (onClick != null || onLongClick != null) Modifier.rowClick(c.pressed, title, role, onClick ?: {}, onLongClick?.let { l -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } })
          else Modifier,
        )
        .semantics { if (selected != null) this.selected = selected }
        .then(if (nav) Modifier.testTag(V3NavRowSpec.ROW_TAG) else Modifier)
        .padding(start = startPad, end = V3NavRowSpec.PAD_END, top = V3NavRowSpec.PAD_V, bottom = V3NavRowSpec.PAD_V),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AnimatedVisibility(selected != null, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
        Row { SelectionDot(selected == true); Spacer(Modifier.width(12.dp)) }
      }
      when {
        leading != null -> { leading(); Spacer(Modifier.width(12.dp)) }
        icon != null && tiled -> {
          V3IconTile(icon, tileHue, danger, V3NavRowSpec.TILE, if (nav) Modifier.testTag(V3NavRowSpec.TILE_TAG) else Modifier)
          Spacer(Modifier.width(V3NavRowSpec.GAP))
        }
        icon != null -> {
          Icon(icon, contentDescription = null, tint = if (danger) c.negative else c.icon, modifier = Modifier.size(24.dp))
          Spacer(Modifier.width(14.dp))
        }
      }
      Column(Modifier.weight(1f)) {
        Text(title, style = if (nav) V3Type.rowTitle else V3Type.body, color = if (danger) c.negative else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
          //  ناوبری: راهنما همیشه یک خط (۶۵r — «هرگز دو و سه خط»)
          Text(
            subtitle,
            style = if (nav) V3Type.rowHelp else V3Type.footnote,
            color = c.inkMuted,
            maxLines = if (nav) 1 else subtitleLines,
            overflow = TextOverflow.Ellipsis,
          )
        }
        below?.invoke()
      }
      if (value != null) {
        Spacer(Modifier.width(8.dp))
        if (nav) NavValue(value, valueColor ?: c.inkMuted, valueDot)
        else Text(value, style = V3Type.subhead, color = valueColor ?: c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = V3NavRowSpec.VALUE_MAX))
      }
      if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
      if (onClick != null && chevron && trailing == null && selected == null) {
        Spacer(Modifier.width(4.dp))
        V3Chevron(if (tile || nav) c.chevron else c.icon)
      }
    }
  }
  if (startActions.isEmpty() && endActions.isEmpty()) body(modifier)
  else V3Swipeable(startActions, endActions, modifier) { body(Modifier) }
}

/** مقدارِ کم‌رنگِ ردیفِ ناوبری، درست پیش از پیکان: مقدارِ تازه با محوِ نرم جای قبلی می‌نشیند */
@Composable
private fun NavValue(value: String, color: Color, dot: Color?) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = V3NavRowSpec.VALUE_MAX)) {
    if (dot != null) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
      Spacer(Modifier.width(6.dp))
    }
    AnimatedContent(value, transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) }, label = "navValue") {
      Text(it, style = V3Type.rowHelp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** پُرشدنِ نرمِ هنگامِ فشار (بی موجِ رنگی) با زدن و نگه‌داشتن */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.rowClick(highlight: Color, label: String, role: Role, onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
  val src = remember { MutableInteractionSource() }
  val down by src.collectIsPressedAsState()
  val bg by animateColorAsState(if (down) highlight else highlight.copy(alpha = 0f), MotionSpec.tweenOf(120), label = "rowPress")
  this
    .combinedClickable(
      interactionSource = src, indication = null, role = role, onClickLabel = label,
      onLongClickLabel = if (onLongClick != null) StrA11y.moreActions else null, onLongClick = onLongClick, onClick = onClick,
    )
    .background(bg)
}

@Composable
private fun SelectionDot(on: Boolean) {
  val c = PhoneV3.colors
  Box(
    Modifier
      .size(24.dp)
      .clip(CircleShape)
      .background(if (on) PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).bg else c.card)
      .border(1.5.dp, if (on) PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).bg else PhoneV3Roles.inputBorder(c), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    if (on) Icon(Icons.Outlined.Check, contentDescription = null, tint = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).fg, modifier = Modifier.size(16.dp))
  }
}

/** فلشِ «جلو»: در راست‌به‌چپ رو به چپ */
@Composable
fun V3Chevron(tint: Color = PhoneV3.colors.chevron) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Icon(if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
}

private val ACTION_WIDTH = 76.dp

@Composable
private fun V3Swipeable(
  start: List<V3SwipeAction>,
  end: List<V3SwipeAction>,
  modifier: Modifier,
  content: @Composable () -> Unit,
) {
  val c = PhoneV3.colors
  val density = LocalDensity.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  //  جابه‌جاییِ فیزیکی به «سمتِ آغاز» در راست‌به‌چپ مثبت (به راست) است
  val toStart = if (rtl) 1f else -1f
  val slot = with(density) { ACTION_WIDTH.toPx() }
  val offset = remember { Animatable(0f) }
  val width = remember { mutableFloatStateOf(1f) }
  val ticked = remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  fun settle(target: Float) = scope.launch { offset.animateTo(target, MotionSpec.sheet()) }
  //  کارهای زیرِ ردیف تا کشیده نشده‌اند پنهان‌اند: نه صفحه‌خوان (همان «کارِ سفارشی» ِ ردیف را دارد) و نه Tab به آن‌ها نمی‌رسد
  val covered by remember { derivedStateOf { offset.value == 0f } }

  Box(
    modifier
      .fillMaxWidth()
      .semantics {
        customActions = (end + start).map { a -> CustomAccessibilityAction(a.label) { a.onClick(); true } }
      },
  ) {
    //  کارهای پایان (پیدا با کشیدن به آغاز) و کارهای آغاز (با کشیدن به پایان)
    Row(Modifier.matchParentSize().background(c.tile).then(if (covered) Modifier.clearAndSetSemantics {} else Modifier)) {
      ActionCells(start, enabled = !covered) { a -> settle(0f); a.onClick() }
      Spacer(Modifier.weight(1f))
      ActionCells(end, enabled = !covered) { a -> settle(0f); a.onClick() }
    }
    Box(
      Modifier
        .offset { IntOffset(offset.value.roundToInt(), 0) }
        .draggable(
          orientation = Orientation.Horizontal,
          state = rememberDraggableState { delta ->
            //  مثبت = کارهای پایان پیدا می‌شوند؛ منفی = کارهای آغاز
            val revealed = (offset.value + delta) * toStart
            val max = if (end.isEmpty()) 0f else width.floatValue * 0.85f
            val min = if (start.isEmpty()) 0f else -width.floatValue * 0.85f
            val clamped = revealed.coerceIn(min, max)
            val past = kotlin.math.abs(clamped) > width.floatValue * 0.5f
            if (past != ticked.value) {
              ticked.value = past
              if (past) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            scope.launch { offset.snapTo(clamped * toStart) }
          },
          onDragStopped = { velocity ->
            val revealed = offset.value * toStart
            val v = velocity * toStart
            ticked.value = false
            when {
              revealed > width.floatValue * 0.5f && end.isNotEmpty() -> { settle(0f); end.first().onClick() }
              revealed < -width.floatValue * 0.5f && start.isNotEmpty() -> { settle(0f); start.first().onClick() }
              (revealed > slot * end.size / 2 || v > 1200f) && end.isNotEmpty() -> settle(slot * end.size * toStart)
              (revealed < -slot * start.size / 2 || v < -1200f) && start.isNotEmpty() -> settle(-slot * start.size * toStart)
              else -> settle(0f)
            }
          },
        )
        .onSizeChanged { width.floatValue = it.width.toFloat() },
    ) { content() }
  }
}

@Composable
private fun ActionCells(actions: List<V3SwipeAction>, enabled: Boolean, onPick: (V3SwipeAction) -> Unit) {
  val c = PhoneV3.colors
  actions.forEach { a ->
    val pair = PhoneV3Roles.button(c, a.kind)
    Column(
      Modifier
        .width(ACTION_WIDTH)
        .fillMaxHeight()
        .background(pair.bg)
        .v3Pressable(enabled = enabled, label = a.label, haptic = true) { onPick(a) },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(a.icon, contentDescription = null, tint = pair.fg, modifier = Modifier.size(20.dp))
      Text(a.label, style = V3Type.caption, color = pair.fg, maxLines = 1)
    }
  }
}
