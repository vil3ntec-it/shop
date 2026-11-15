package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable

/*
 *  کنترل‌های فرم و فهرستِ کامپیوتر (`claude/e-desk-d3a`: صندوق، کالاها، انبار) — فقط از [DeskStyle]: کادرِ ۳۶dp با خطِ مویی
 *  و حلقهٔ تمرکز، جست‌وجو، فهرستِ کشویی، سرِ جدولِ مرتب‌شدنی، تیک، شمارنده، تراشهٔ فیلتر، حالتِ خالی و پنلِ کناری.
 *  همه فقط نمایش‌اند؛ کار و کلید از صفحه می‌آید. بی انیمیشنِ پیوسته، بی سایه (جز کارت).
 *  ⚠️ API افزودنی است: نام عوض نمی‌شود.
 */

/**
 *  کادرِ متنِ کامپیوتر: سطحِ کارت، خطِ مویی، حلقهٔ تمرکزِ ۲dp (همیشه دیده می‌شود: صندوق‌دار باید بداند تایپ کجا می‌رود)،
 *  نوشتهٔ راهنمای کم‌رنگ و آیکنِ آغاز/پایان. [onKey] پیش از کادر کلید را می‌گیرد (Enter، ↑↓، Esc). [description] نامِ دسترسی
 *  (کادرِ عددیِ بی برچسب)؛ بی آن نوشتهٔ راهنما نامِ کادر است.
 */
@Composable
fun DeskTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  leading: ImageVector? = null,
  trailing: (@Composable RowScope.() -> Unit)? = null,
  height: Dp = DeskSize.control,
  focusRequester: FocusRequester? = null,
  onKey: ((KeyEvent) -> Boolean)? = null,
  isError: Boolean = false,
  enabled: Boolean = true,
  textStyle: TextStyle = DeskType.body,
  textAlign: TextAlign = TextAlign.Start,
  description: String? = null,
  suffix: String? = null,
) {
  val c = Desk.colors
  val src = remember { MutableInteractionSource() }
  val focused by src.collectIsFocusedAsState()
  val border = when {
    isError -> c.negative
    focused -> c.focus
    else -> c.hairline
  }
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    enabled = enabled,
    singleLine = true,
    interactionSource = src,
    textStyle = textStyle.copy(color = if (enabled) c.ink else c.inkMuted, textAlign = textAlign),
    cursorBrush = SolidColor(c.focus),
    modifier = modifier
      .heightIn(min = height)
      .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
      .then(if (onKey != null) Modifier.onPreviewKeyEvent(onKey) else Modifier)
      .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    decorationBox = { inner ->
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = height)
          .clip(DeskRadius.controlShape)
          .background(if (enabled) c.surface else c.surfaceAlt)
          .border(if (focused || isError) DeskSize.focusRing else DeskSize.hairline, border, DeskRadius.controlShape)
          .padding(horizontal = DeskSpace.x3),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (leading != null) {
          Icon(leading, contentDescription = null, tint = if (focused) c.brandInk else c.inkSecondary, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(DeskSpace.x2))
        }
        Box(Modifier.weight(1f), contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
          if (value.isEmpty() && placeholder != null) {
            Text(placeholder, style = textStyle, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          inner()
        }
        if (suffix != null) {
          Spacer(Modifier.width(DeskSpace.x2))
          Text(suffix, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1)
        }
        if (trailing != null) {
          Spacer(Modifier.width(DeskSpace.x2))
          trailing()
        }
      }
    },
  )
}

/** کادرِ جست‌وجوی جدول (آیکنِ ذره‌بین، × برای پاک کردن) */
@Composable
fun DeskSearchField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  focusRequester: FocusRequester? = null,
  clearLabel: String? = null,
  onKey: ((KeyEvent) -> Boolean)? = null,
  extra: (@Composable RowScope.() -> Unit)? = null,
) {
  DeskTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    placeholder = placeholder,
    leading = Icons.Filled.Search,
    focusRequester = focusRequester,
    onKey = onKey,
    trailing = if (value.isEmpty() && extra == null) null else ({
      if (value.isNotEmpty() && clearLabel != null) DeskIconButton(Icons.Filled.Close, clearLabel, size = 24.dp, iconSize = 16.dp) { onValueChange("") }
      extra?.invoke(this)
    }),
  )
}

/** دکمهٔ تک‌آیکنِ بی‌زمینه با نامِ دسترسی (و راهنمای هاورِ اختیاری با میان‌بر) */
@Composable
fun DeskIconButton(
  icon: ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  size: Dp = DeskSize.controlSmall,
  iconSize: Dp = 18.dp,
  enabled: Boolean = true,
  tint: androidx.compose.ui.graphics.Color? = null,
  shortcut: String? = null,
  onClick: () -> Unit,
) {
  val c = Desk.colors
  val body = @Composable {
    Box(
      modifier.size(size).deskClickable(label = label, enabled = enabled, onClick = onClick).semantics { contentDescription = label },
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = if (!enabled) c.inkMuted else tint ?: c.icon, modifier = Modifier.size(iconSize))
    }
  }
  if (shortcut != null) DeskTooltip(label, shortcut) { body() } else body()
}

/** یک گزینهٔ فهرستِ کشویی: مقدار و نوشته */
@Immutable
data class DeskOption<T>(val value: T, val label: String)

/**
 *  فهرستِ کشوییِ فیلتر (دسته، محل): دکمهٔ ۳۶dp با «برچسب: مقدار» و پیکان؛ گزینهٔ «همه» (`null`) اول. انتخاب‌شده تیک دارد.
 */
@Composable
fun <T> DeskDropdown(
  label: String,
  options: List<DeskOption<T>>,
  selected: T?,
  onSelect: (T?) -> Unit,
  allLabel: String,
  modifier: Modifier = Modifier,
) {
  val c = Desk.colors
  var open by remember { mutableStateOf(false) }
  val current = options.firstOrNull { it.value == selected }?.label ?: allLabel
  Box(modifier) {
    Row(
      Modifier
        .heightIn(min = DeskSize.control)
        .border(DeskSize.hairline, if (selected != null) c.focus else c.hairline, DeskRadius.controlShape)
        .deskClickable(label = "$label: $current", rest = c.surface) { open = true }
        .padding(horizontal = DeskSpace.x3),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("$label: ", style = DeskType.label, color = c.inkSecondary, maxLines = 1)
      Text(current, Modifier.widthIn(max = 180.dp), style = DeskType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.width(DeskSpace.x1))
      Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
      val all = listOf<DeskOption<T>?>(null) + options
      all.forEach { o ->
        val on = o?.value == selected
        DropdownMenuItem(
          text = { Text(o?.label ?: allLabel, style = DeskType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          leadingIcon = { if (on) Icon(Icons.Filled.Check, null, tint = c.brandInk, modifier = Modifier.size(16.dp)) else Spacer(Modifier.size(16.dp)) },
          onClick = { open = false; onSelect(o?.value) },
          modifier = Modifier.heightIn(min = DeskSize.control),
        )
      }
    }
  }
}

/** یک ستونِ سرِ جدولِ مرتب‌شدنی: [sort] `null` ⇒ ستون مرتب نمی‌شود */
@Immutable
data class DeskSortColumn<S>(val column: DeskColumn, val sort: S? = null)

/**
 *  سرِ جدولِ مرتب‌شدنی: زدنِ سرستون همان ستون را مرتب می‌کند (دوباره ⇒ وارونه) و پیکانِ کوچک جهت را می‌گوید.
 *  [leading] جای تیکِ «همه» (پهنای ثابتِ [leadingWidth]) — همان جای تیکِ ردیف‌ها.
 */
@Composable
fun <S> DeskSortHead(
  columns: List<DeskSortColumn<S>>,
  sort: S?,
  asc: Boolean,
  onSort: (S) -> Unit,
  modifier: Modifier = Modifier,
  leading: (@Composable () -> Unit)? = null,
  leadingWidth: Dp = 28.dp,
  sortLabel: (String) -> String = { it },
) {
  val c = Desk.colors
  Row(
    modifier.fillMaxWidth().height(DeskSize.tableHead).clip(DeskRadius.controlShape).background(c.surfaceAlt).padding(horizontal = DeskSpace.x3),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
  ) {
    if (leading != null) Box(Modifier.width(leadingWidth), contentAlignment = Alignment.Center) { leading() }
    columns.forEach { sc ->
      val col = sc.column
      val cell = if (col.width != null) Modifier.width(col.width) else Modifier.weight(col.weight)
      val on = sc.sort != null && sc.sort == sort
      Box(cell, contentAlignment = if (col.end) Alignment.CenterEnd else Alignment.CenterStart) {
        if (sc.sort == null) {
          Text(col.title, style = DeskType.tableHead, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
          Row(
            Modifier
              .deskClickable(label = sortLabel(col.title), role = Role.Button) { onSort(sc.sort) }
              .semantics { selected = on; if (on) stateDescription = if (asc) "▲" else "▼" }
              .padding(horizontal = DeskSpace.x1, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(col.title, style = DeskType.tableHead, color = if (on) c.ink else c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (on) {
              Spacer(Modifier.width(2.dp))
              Icon(if (asc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, contentDescription = null, tint = c.ink, modifier = Modifier.size(14.dp))
            }
          }
        }
      }
    }
  }
}

/** تیکِ کوچکِ ردیفِ جدول (۱۸dp، سه‌حالته برای «همه») با نامِ دسترسی */
@Composable
fun DeskCheckbox(state: ToggleableState, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val c = Desk.colors
  val on = state != ToggleableState.Off
  Box(
    modifier
      .size(28.dp)
      .clip(DeskRadius.controlShape)
      .triStateToggleable(state = state, role = Role.Checkbox, onClick = onClick)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .size(18.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        .background(if (on) c.primary else c.surface)
        .border(1.5.dp, if (on) c.primary else c.inkMuted, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
      contentAlignment = Alignment.Center,
    ) {
      when (state) {
        ToggleableState.On -> Icon(Icons.Filled.Check, null, tint = c.onPrimary, modifier = Modifier.size(14.dp))
        ToggleableState.Indeterminate -> Box(Modifier.width(10.dp).height(2.dp).background(c.onPrimary))
        ToggleableState.Off -> Unit
      }
    }
  }
}

/** کلیدِ روشن/خاموشِ کوچک با نوشته (تیک + برچسب، کلِ ردیف زدنی) */
@Composable
fun DeskToggleRow(checked: Boolean, label: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  val c = Desk.colors
  Row(
    modifier.heightIn(min = DeskSize.control).clip(DeskRadius.controlShape).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(end = DeskSpace.x2),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
      Box(
        Modifier.size(18.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(if (checked) c.primary else c.surface)
          .border(1.5.dp, if (checked) c.primary else c.inkMuted, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
      ) { if (checked) Icon(Icons.Filled.Check, null, tint = c.onPrimary, modifier = Modifier.size(14.dp)) }
    }
    Spacer(Modifier.width(DeskSpace.x1))
    Text(label, style = DeskType.body, color = c.ink, maxLines = 1)
  }
}

/**
 *  شمارندهٔ کوچکِ ردیفِ سبد: − عدد + (دکمهٔ ۲۶dp بی‌زمینه، عددِ هم‌پهنا). نام‌های دسترسی از صفحه («یکی کمتر: شیر»).
 *  زدنِ خودِ عدد ([onValue]) پنجرهٔ تعداد/قیمت را باز می‌کند.
 */
@Composable
fun DeskStepper(
  text: String,
  minusLabel: String,
  plusLabel: String,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
  modifier: Modifier = Modifier,
  minusEnabled: Boolean = true,
  plusEnabled: Boolean = true,
  valueLabel: String? = null,
  onValue: (() -> Unit)? = null,
) {
  val c = Desk.colors
  Row(
    modifier.height(DeskSize.controlSmall).clip(DeskRadius.controlShape).border(DeskSize.hairline, c.hairline, DeskRadius.controlShape),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    DeskIconButton(Icons.Filled.Remove, minusLabel, size = 26.dp, iconSize = 15.dp, enabled = minusEnabled, onClick = onMinus)
    Box(
      Modifier.widthIn(min = 40.dp).fillMaxHeight()
        .then(if (onValue != null) Modifier.deskClickable(label = valueLabel ?: text, shape = androidx.compose.ui.graphics.RectangleShape, onClick = onValue) else Modifier)
        .padding(horizontal = DeskSpace.x1),
      contentAlignment = Alignment.Center,
    ) {
      Text(text, style = DeskType.number, color = c.ink, maxLines = 1, textAlign = TextAlign.Center)
    }
    DeskIconButton(Icons.Filled.Add, plusLabel, size = 26.dp, iconSize = 15.dp, enabled = plusEnabled, onClick = onPlus)
  }
}

/**
 *  تراشهٔ فیلتر/دسته: آرام (سطح + خطِ مویی + متنِ دوم)، انتخاب‌شده با کاشیِ نرمِ رنگِ بخش و متنِ خوانای همان ([tone]).
 *  [count] شمارِ خنثیِ کنارِ نوشته.
 */
@Composable
fun DeskChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tone: DeskHue? = null, count: Int? = null, icon: ImageVector? = null) {
  val c = Desk.colors
  val fg = if (selected) tone?.ink ?: c.brandInk else c.inkSecondary
  Row(
    modifier
      .heightIn(min = DeskSize.controlSmall)
      .border(DeskSize.hairline, if (selected) (tone?.solid ?: c.brand).copy(alpha = 0.45f) else c.hairline, DeskRadius.pillShape)
      .deskClickable(label = label, role = Role.Tab, shape = DeskRadius.pillShape, selected = selected, selectedFill = tone?.tile ?: c.selected, rest = c.surface, onClick = onClick)
      .semantics { this.selected = selected }
      .padding(horizontal = DeskSpace.x3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
      Spacer(Modifier.width(DeskSpace.x1))
    }
    Text(label, style = DeskType.label, color = if (selected) fg else c.ink, maxLines = 1)
    if (count != null) {
      Spacer(Modifier.width(DeskSpace.x1 + 2.dp))
      Text(ir.vil3ntec.tohid.plain(count), style = DeskType.badge.copy(fontSize = DeskType.caption.fontSize), color = fg, maxLines = 1)
    }
  }
}

/** ردیفِ افقیِ تراشه‌ها با پیمایش (دسته‌های زیاد) */
@Composable
fun DeskChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  Row(
    modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

/** حالتِ خالی/بی‌نتیجه: کاشیِ آیکنِ رنگِ بخش، عنوان، یک جملهٔ کم‌رنگ و کارِ اختیاری */
@Composable
fun DeskEmpty(
  icon: ImageVector,
  title: String,
  body: String,
  tone: DeskHue,
  modifier: Modifier = Modifier,
  action: String? = null,
  onAction: (() -> Unit)? = null,
) {
  val c = Desk.colors
  Column(modifier.fillMaxSize().padding(DeskSpace.x8), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    DeskIconTile(icon, tone, size = DeskSize.tileLarge + 8.dp, iconSize = 24.dp)
    Spacer(Modifier.height(DeskSpace.x3))
    Text(title, style = DeskType.sectionTitle, color = c.ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(DeskSpace.x1))
    Text(body, Modifier.widthIn(max = 420.dp), style = DeskType.secondary, color = c.inkSecondary, textAlign = TextAlign.Center)
    if (action != null && onAction != null) {
      Spacer(Modifier.height(DeskSpace.x4))
      DeskButton(action, onAction)
    }
  }
}

/**
 *  پنلِ کنارِ جدول (ویرایش، جزئیات): کارت با سرخط، دکمهٔ بستن (Esc) و محتوای پیمایشی که خودِ صفحه می‌دهد.
 */
@Composable
fun DeskSidePanel(
  title: String,
  closeLabel: String,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  content: @Composable () -> Unit,
) {
  val c = Desk.colors
  Column(modifier.deskCard(c)) {
    Row(Modifier.fillMaxWidth().padding(start = DeskSpace.card, end = DeskSpace.x3, top = DeskSpace.x3, bottom = DeskSpace.x2), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        DeskSectionTitle(title)
        if (subtitle != null) Text(subtitle, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      DeskIconButton(Icons.Filled.Close, closeLabel, shortcut = "Esc", onClick = onClose)
    }
    DeskDivider()
    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
  }
}

/** ردیفِ «برچسب — مقدار» ِ جمع‌ها و جزئیات (برچسب کم‌رنگ، مقدارِ هم‌پهنا) */
@Composable
fun DeskValueRow(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false, valueColor: androidx.compose.ui.graphics.Color? = null) {
  val c = Desk.colors
  Row(modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = if (strong) DeskType.bodyStrong else DeskType.secondary, color = if (strong) c.ink else c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(value, style = if (strong) DeskType.number else DeskType.table, color = valueColor ?: c.ink, maxLines = 1)
  }
}

/** میان‌بر و کارش برای نوارِ کلیدهای پایینِ صفحه («F2 مشتری») */
@Immutable
data class DeskKeyHint(val keys: String, val label: String)

/** نوارِ کلیدهای اصلیِ صفحه — کم‌رنگ، یک خط، با پیمایشِ افقی اگر جا نشد؛ فهرستِ کامل در F1 */
@Composable
fun DeskKeyBar(hints: List<DeskKeyHint>, modifier: Modifier = Modifier) {
  val c = Desk.colors
  Row(
    modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.x4),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    hints.forEach { h ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        DeskShortcut(h.keys)
        Spacer(Modifier.width(DeskSpace.x1 + 2.dp))
        Text(h.label, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1)
      }
    }
  }
}

/**
 *  کارتِ عددِ فشرده (۷۲dp) برای بالای جدول‌ها (کالاها، انبار): کاشیِ رنگِ بخش، برچسب، عددِ هم‌پهنا و یک خطِ کم‌رنگ؛ نوارِ
 *  رنگیِ لبهٔ بالا مثلِ `DeskKpiCard`. زدنی ⇒ همان فیلتر؛ [selected] ⇒ خطِ دورِ رنگِ بخش.
 */
@Composable
fun DeskStatTile(
  label: String,
  value: String,
  icon: ImageVector,
  tone: DeskHue,
  modifier: Modifier = Modifier,
  note: String? = null,
  selected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val c = Desk.colors
  Row(
    modifier
      .heightIn(min = 72.dp)
      .deskCard(c)
      .then(if (selected) Modifier.border(1.5.dp, tone.solid, DeskRadius.cardShape) else Modifier)
      .then(if (onClick != null) Modifier.deskClickable(label = "$label: $value", shape = DeskRadius.cardShape, selected = selected, selectedFill = c.surface, onClick = onClick) else Modifier)
      .semantics { this.selected = selected }
      .padding(horizontal = DeskSpace.x4, vertical = DeskSpace.x3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    DeskIconTile(icon, tone, size = 36.dp, iconSize = 19.dp)
    Spacer(Modifier.width(DeskSpace.x3))
    Column(Modifier.weight(1f)) {
      Text(label, style = DeskType.label, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(value, style = DeskType.kpiSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (note != null) Text(note, style = DeskType.caption.copy(fontWeight = DeskType.secondary.fontWeight), color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** کادرِ متن با برچسبِ کوچکِ بالا (فرمِ پنلِ ویرایش) */
@Composable
fun DeskLabeledField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  textAlign: TextAlign = TextAlign.Start,
) {
  val c = Desk.colors
  Column(modifier.padding(bottom = DeskSpace.x3)) {
    Text(label, Modifier.padding(bottom = DeskSpace.x1), style = DeskType.label, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    DeskTextField(value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled, textAlign = textAlign, description = label)
  }
}

/** معنای کارتِ عدد — نه رنگِ بخش: عادی، خوب، هشدار، آگاهی، بد */
enum class DeskTone { NEUTRAL, POSITIVE, WARNING, INFO, NEGATIVE }

/**
 *  پله‌های «معنا» برای کاشیِ کارتِ عدد (فعال، کم، نزدیکِ انقضا، تمام‌شده، منقضی) — همه از رنگِ وضعیتِ همان تم
 *  (`DeskColors.positive/warning/info/negative` و زمینهٔ نرمِ همان)، نه رنگِ همراهِ دستی (`DeskHues` فقط رنگِ بخش است).
 *  سرخ روی کاشیِ خنثی ([DeskColors.surfaceAlt]) می‌نشیند، نه زمینهٔ سرخِ کم‌رنگ (خواستِ صاحب‌کار: «رنگ صورتی کار نگیر»).
 *  `hue` ِ پله فقط جای‌نگه‌دار است (برند)، معنا از رنگ‌هاست.
 */
fun DeskColors.deskTone(t: DeskTone): DeskHue {
  val brand = ir.vil3ntec.tohid.ui.theme.PhoneV3Palette.Hue.BRAND
  return when (t) {
    DeskTone.NEUTRAL -> DeskHue(brand, solid = inkSecondary, tile = surfaceAlt, glyph = ink, ink = ink, onSolid = surface)
    DeskTone.POSITIVE -> DeskHue(brand, solid = positive, tile = positiveSoft, glyph = positive, ink = positive, onSolid = onAlert)
    DeskTone.WARNING -> DeskHue(brand, solid = warning, tile = warningSoft, glyph = warning, ink = warning, onSolid = onAlert)
    DeskTone.INFO -> DeskHue(brand, solid = info, tile = infoSoft, glyph = info, ink = info, onSolid = onAlert)
    DeskTone.NEGATIVE -> DeskHue(brand, solid = negative, tile = surfaceAlt, glyph = negative, ink = negative, onSolid = onAlert)
  }
}

/** همان `deskTone(NEGATIVE)` */
fun DeskColors.deskNegativeTone(): DeskHue = deskTone(DeskTone.NEGATIVE)
