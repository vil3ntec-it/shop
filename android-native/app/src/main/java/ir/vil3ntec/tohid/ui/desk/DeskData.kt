package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 *  ═══════════════════════════════════════════════════════════════════════════
 *  اجزای صفحه‌های داده‌ایِ کامپیوتر (`claude/e-desk-d3b`): تاریخچهٔ فروش، مشتری‌ها، خرید، مصارف، رسیدها و گزارش‌ها —
 *  فقط روی [DeskStyle] و [DeskKit] (فاصله، گِردی، قلم و رنگ از همان یک منبع). خواستِ صاحب‌کار: فهرستِ آرام مثلِ
 *  نمونه‌اش — یک خطِ عنوان، یک خطِ کم‌رنگ («تاریخ • ساعت»)، «مبلغ • وضعیت» به واژه، بی نشانِ رنگی و بی خطِ رنگارنگ، و
 *  دست‌بالا **یک** کارِ خنثی در هر ردیف. پالایه‌ها در یک نوارِ ابزار که هرگز بریده نمی‌شود ([DeskToolbar]، می‌پیچد)،
 *  جمع‌ها کارتِ KPI، و جزئیات در پنلِ کنار ([DeskPane]). فهرستِ بلند همیشه تنبل ([DeskGrid] روی `LazyColumn`)، حسابِ
 *  سنگین بیرون از رشتهٔ صفحه ([rememberDeskLoad])، بی انیمیشنِ پیوسته، بی تاری؛ سایه فقط روی کارت.
 *  ⚠️ API فقط افزوده می‌شود (همان قاعدهٔ `DeskStyle`).
 *  ═══════════════════════════════════════════════════════════════════════════
 */

/* ───────────────────────────── صفحه ───────────────────────────── */

/** پهنای پنلِ جزئیات برای پهنای در دسترس — ۳۶٪، میانِ ۳۶۰ و ۴۸۰ */
fun deskPaneWidth(available: Dp): Dp = (available * 0.36f).coerceIn(360.dp, 480.dp)

/** زمینه و کنارهٔ صفحهٔ داده‌ای؛ فاصلهٔ بخش‌ها همان [DeskSpace.gap] */
@Composable
fun DeskPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val c = Desk.colors
  Column(
    modifier.fillMaxSize().background(c.app).padding(horizontal = DeskSpace.page, vertical = DeskSpace.x4),
    verticalArrangement = Arrangement.spacedBy(DeskSpace.gap),
    content = content,
  )
}

/** ردیفِ کارت‌های KPI — هم‌بلند، هم‌پهن */
@Composable
fun DeskKpiRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap), content = content)
}

/* ───────────────────────────── نوارِ ابزار ───────────────────────────── */

/**
 *  نوارِ پالایه و کار — هرگز بریده نمی‌شود: جا که تنگ شد به ردیفِ بعد می‌پیچد. [start] (جست‌وجو، کادرهای کشویی) در آغاز و
 *  [end] (کارها) در پایان، با جای خالیِ کشسان میانشان.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeskToolbar(
  modifier: Modifier = Modifier,
  end: (@Composable FlowRowScope.() -> Unit)? = null,
  start: @Composable FlowRowScope.() -> Unit,
) {
  FlowRow(
    modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2),
    verticalArrangement = Arrangement.spacedBy(DeskSpace.x2),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    start()
    if (end != null) {
      Spacer(Modifier.weight(1f).widthIn(min = 0.dp).height(DeskSize.control))
      end()
    }
  }
}

/** کادرِ جست‌وجوی ۳۶dp با ذره‌بین و «پاک کردن» */
@Composable
fun DeskSearchField(
  value: String,
  onChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  focus: FocusRequester? = null,
) {
  val c = Desk.colors
  var focused by remember { mutableStateOf(false) }
  val edge = if (focused) c.focus else c.hairline
  Row(
    modifier
      .height(DeskSize.control)
      .clip(DeskRadius.controlShape)
      .background(c.surface)
      .border(if (focused) DeskSize.focusRing else DeskSize.hairline, edge, DeskRadius.controlShape)
      .padding(horizontal = DeskSpace.x3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Search, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(DeskSpace.x2))
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (value.isEmpty()) Text(placeholder, style = DeskType.body, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = DeskType.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.brand),
        modifier = Modifier
          .fillMaxWidth()
          .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
          .onFocusChanged { focused = it.isFocused }
          .semantics { contentDescription = placeholder },
      )
    }
    if (value.isNotEmpty()) {
      Spacer(Modifier.width(DeskSpace.x1))
      Box(
        Modifier.size(24.dp).deskClickable(label = StrKit.clear, shape = DeskRadius.pillShape) { onChange("") },
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Outlined.Close, contentDescription = StrKit.clear, tint = c.inkSecondary, modifier = Modifier.size(16.dp)) }
    }
  }
}

/**
 *  کادرِ نوشتنِ ۳۶dp ِ کامپیوتر (مبلغ، نام، شرح): راهنما درونِ کادر و همان نام برای صفحه‌خوان؛ Enter = [onEnter]،
 *  Esc = [onEscape]؛ [isError] ⇒ مرزِ سرخ.
 */
@Composable
fun DeskTextField(
  value: String,
  onChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  focus: FocusRequester? = null,
  isError: Boolean = false,
  onEnter: (() -> Unit)? = null,
  onEscape: (() -> Unit)? = null,
) {
  val c = Desk.colors
  var focused by remember { mutableStateOf(false) }
  val edge = when { isError -> c.negative; focused -> c.focus; else -> c.hairline }
  Box(
    modifier
      .height(DeskSize.control)
      .clip(DeskRadius.controlShape)
      .background(c.surface)
      .border(if (focused || isError) DeskSize.focusRing else DeskSize.hairline, edge, DeskRadius.controlShape)
      .padding(horizontal = DeskSpace.x3),
    contentAlignment = Alignment.CenterStart,
  ) {
    if (value.isEmpty()) Text(placeholder, style = DeskType.body, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    BasicTextField(
      value = value,
      onValueChange = onChange,
      singleLine = true,
      textStyle = DeskType.body.copy(color = c.ink),
      cursorBrush = SolidColor(c.brand),
      modifier = Modifier
        .fillMaxWidth()
        .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .onPreviewKeyEvent { e ->
          if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          when (e.key) {
            Key.Enter, Key.NumPadEnter -> onEnter?.let { it(); true } ?: false
            Key.Escape -> onEscape?.let { it(); true } ?: false
            else -> false
          }
        }
        .semantics { contentDescription = placeholder },
    )
  }
}

/** یک گزینهٔ کادرِ کشویی; [divider] ⇒ خطِ مویی پیش از آن */
@Immutable
data class DeskOption<T>(val value: T, val label: String, val divider: Boolean = false)

/**
 *  کادرِ کشوییِ کامپیوتر (۳۶dp): «برچسب: مقدار ▾» ⇐ فهرستِ گزینه‌ها با تیکِ گزینهٔ جاری — همان قاعدهٔ «دوره و تاریخ همه‌جا یک
 *  کادرِ کشویی» (۶۳w)، به اندازهٔ ماوس.
 */
@Composable
fun <T> DeskDropdown(
  value: T,
  options: List<DeskOption<T>>,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  icon: ImageVector? = null,
  /** نوشتهٔ کادر وقتی [value] در [options] نیست (دورهٔ جابه‌جاشده، «ماه…») */
  shown: String? = null,
) {
  val c = Desk.colors
  var open by remember { mutableStateOf(false) }
  val current = shown ?: options.firstOrNull { it.value == value }?.label.orEmpty()
  Box(modifier) {
    Row(
      Modifier
        .height(DeskSize.control)
        .border(DeskSize.hairline, c.hairline, DeskRadius.controlShape)
        .deskClickable(label = listOfNotNull(label, current).joinToString(": "), role = Role.DropdownList, rest = c.surface) { open = true }
        .padding(start = DeskSpace.x3, end = DeskSpace.x2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(DeskSpace.x2))
      }
      if (label != null) {
        Text("$label:", style = DeskType.label, color = c.inkSecondary, maxLines = 1)
        Spacer(Modifier.width(DeskSpace.x1 + 2.dp))
      }
      Text(current, style = DeskType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
      Spacer(Modifier.width(DeskSpace.x1))
      Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
    }
    DeskMenu(open, onDismiss = { open = false }) {
      options.forEach { o ->
        if (o.divider) DeskDivider(Modifier.padding(vertical = DeskSpace.x1))
        DeskMenuRow(o.label, selected = o.value == value) { open = false; onSelect(o.value) }
      }
    }
  }
}

/** یک کارِ منوی «…» */
@Immutable
data class DeskMenuItem(val label: String, val icon: ImageVector? = null, val danger: Boolean = false, val divider: Boolean = false, val onClick: () -> Unit)

/** دکمهٔ «…» ِ کارهای کمتر — هیچ کاری بی‌صدا گم نمی‌شود، فقط یک لایه پایین‌تر می‌رود */
@Composable
fun DeskOverflow(items: List<DeskMenuItem>, modifier: Modifier = Modifier, label: String = StrA11y.moreActions) {
  if (items.isEmpty()) return
  var open by remember { mutableStateOf(false) }
  Box(modifier) {
    DeskIconButton(Icons.Outlined.MoreHoriz, label, onClick = { open = true }, bordered = true)
    DeskMenu(open, onDismiss = { open = false }) {
      items.forEach { m ->
        if (m.divider) DeskDivider(Modifier.padding(vertical = DeskSpace.x1))
        DeskMenuRow(m.label, icon = m.icon, danger = m.danger) { open = false; m.onClick() }
      }
    }
  }
}

/** پوستهٔ منوی کشویی: کارت، خطِ مویی، بی سایهٔ سنگین */
@Composable
fun DeskMenu(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  val c = Desk.colors
  DropdownMenu(
    expanded = open,
    onDismissRequest = onDismiss,
    shape = DeskRadius.cardShape,
    containerColor = c.surface,
    tonalElevation = 0.dp,
    shadowElevation = 6.dp,
    border = androidx.compose.foundation.BorderStroke(DeskSize.hairline, c.hairline),
    modifier = Modifier.widthIn(min = 200.dp).padding(horizontal = DeskSpace.x1),
    content = content,
  )
}

/** یک ردیفِ منو (۳۶dp): تیکِ انتخاب یا آیکن، نوشته */
@Composable
fun DeskMenuRow(text: String, selected: Boolean = false, icon: ImageVector? = null, danger: Boolean = false, onClick: () -> Unit) {
  val c = Desk.colors
  val ink = if (danger) c.negative else c.ink
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = DeskSize.control)
      .deskClickable(label = text, role = Role.Button, onClick = onClick)
      .semantics { this.selected = selected }
      .padding(horizontal = DeskSpace.x3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
      when {
        selected -> Icon(Icons.Outlined.Check, contentDescription = null, tint = c.brandInk, modifier = Modifier.size(16.dp))
        icon != null -> Icon(icon, contentDescription = null, tint = if (danger) c.negative else c.inkSecondary, modifier = Modifier.size(16.dp))
      }
    }
    Spacer(Modifier.width(DeskSpace.x2))
    Text(text, style = if (selected) DeskType.bodyStrong else DeskType.body, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** دکمهٔ تک‌آیکنِ ۳۶dp با راهنمای هاور؛ [bordered] ⇒ همان خطِ دورِ دکمهٔ دوم */
@Composable
fun DeskIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, bordered: Boolean = false, tint: Color? = null) {
  val c = Desk.colors
  DeskTooltip(label) {
    Box(
      modifier
        .size(DeskSize.control)
        .then(if (bordered) Modifier.border(DeskSize.hairline, c.hairline, DeskRadius.controlShape) else Modifier)
        .deskClickable(label = label, enabled = enabled, rest = if (bordered) c.surface else Color.Transparent, onClick = onClick)
        .semantics { contentDescription = label },
      contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = if (!enabled) c.inkMuted else tint ?: c.inkSecondary, modifier = Modifier.size(18.dp)) }
  }
}

/* ───────────────────────────── جدول ───────────────────────────── */

/**
 *  ردیفِ جدولِ برگزیدنی (۴۴dp): زیرِ ماوس روشن، برگزیده با سطحِ انتخاب و نوارِ ۳dp ِ برند در آغاز — نه رنگِ دیگری.
 *  [wash] (اختیاری) زمینهٔ معنادارِ همهٔ ردیف — فقط دفترِ قرض («برد» سرخ / «رسید» سبز، هویتِ قرض‌داران).
 */
@Composable
fun DeskDataRow(
  columns: List<DeskColumn>,
  label: String,
  selected: Boolean,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  wash: Color? = null,
  cell: @Composable (Int) -> Unit,
) {
  val c = Desk.colors
  val rest = wash ?: Color.Transparent
  val bar = c.brand
  Column(modifier) {
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = DeskSize.tableRow)
        .then(
          if (onClick != null) Modifier.deskClickable(label = label, selected = selected, rest = rest, onClick = onClick)
          else Modifier.background(if (selected) c.selected else rest),
        )
        .semantics { this.selected = selected }
        .then(if (selected) Modifier.drawBehind {
          val w = DeskSize.accentBar.toPx()
          val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
          drawRect(bar, topLeft = Offset(x, 0f), size = Size(w, size.height))
        } else Modifier)
        .padding(horizontal = DeskSpace.x3),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
    ) {
      columns.forEachIndexed { i, col ->
        Box(
          if (col.width != null) Modifier.width(col.width) else Modifier.weight(col.weight),
          contentAlignment = if (col.end) Alignment.CenterEnd else Alignment.CenterStart,
        ) { cell(i) }
      }
    }
    DeskDivider()
  }
}

/** نوشتهٔ یک خانهٔ جدول — رقمِ هم‌پهنا، یک خط؛ [tone]: معمولی، کم‌رنگ، پررنگ یا رنگِ معنادار */
@Composable
fun DeskCell(text: String, modifier: Modifier = Modifier, muted: Boolean = false, strong: Boolean = false, color: Color? = null) {
  val c = Desk.colors
  Text(
    text,
    modifier,
    style = if (strong) DeskType.table.copy(fontWeight = DeskType.bodyStrong.fontWeight) else DeskType.table,
    color = color ?: if (muted) c.inkSecondary else c.ink,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

/**
 *  جدولِ کارت‌دارِ تنبل: سرِ جدول، ردیف‌ها در `LazyColumn` (فقط ردیف‌های پیدا ساخته می‌شوند — ۱۵۰ هزار قلم هم)، جمعِ پایین
 *  (اختیاری) و خالیِ خودش. ↑/↓ ردیفِ برگزیده را جابه‌جا می‌کند، PageUp/PageDown ده‌تا، Home/End سر و ته، Enter = [onOpen].
 *  ردیفِ برگزیده خودش به دید می‌آید.
 */
@Composable
fun DeskGrid(
  columns: List<DeskColumn>,
  count: Int,
  modifier: Modifier = Modifier,
  key: ((Int) -> Any)? = null,
  selected: Int? = null,
  onPick: ((Int) -> Unit)? = null,
  onOpen: ((Int) -> Unit)? = null,
  rowLabel: (Int) -> String = { "" },
  footer: List<String>? = null,
  state: LazyListState = rememberLazyListState(),
  wash: ((Int) -> Color?)? = null,
  empty: (@Composable () -> Unit)? = null,
  cell: @Composable (row: Int, col: Int) -> Unit,
) {
  val c = Desk.colors
  val pick by rememberUpdatedState(onPick)
  val open by rememberUpdatedState(onOpen)
  LaunchedEffect(selected, count) {
    val s = selected ?: return@LaunchedEffect
    if (s !in 0 until count) return@LaunchedEffect
    val seen = state.layoutInfo.visibleItemsInfo
    if (seen.isEmpty() || s < seen.first().index || s > seen.last().index) state.scrollToItem((s - 2).coerceAtLeast(0))
  }
  Column(
    modifier
      .deskCard(c)
      .onPreviewKeyEvent { e ->
        val p = pick ?: return@onPreviewKeyEvent false
        if (e.type != KeyEventType.KeyDown || count == 0) return@onPreviewKeyEvent false
        val now = selected ?: -1
        val next = when (e.key) {
          Key.DirectionDown -> now + 1
          Key.DirectionUp -> if (now < 0) 0 else now - 1
          Key.PageDown -> now + 10
          Key.PageUp -> now - 10
          Key.MoveHome -> 0
          Key.MoveEnd -> count - 1
          Key.Enter, Key.NumPadEnter -> { if (now >= 0) open?.invoke(now); return@onPreviewKeyEvent now >= 0 && open != null }
          else -> return@onPreviewKeyEvent false
        }
        p(next.coerceIn(0, count - 1))
        true
      }
      .focusable(),
  ) {
    DeskTableHead(columns, Modifier.padding(start = DeskSpace.x2, end = DeskSpace.x2, top = DeskSpace.x2))
    Box(Modifier.weight(1f).fillMaxWidth()) {
      if (count == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { empty?.invoke() }
      } else {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = DeskSpace.x2), state = state) {
          items(count, key = key) { i ->
            DeskDataRow(
              columns, rowLabel(i), selected = i == selected,
              onClick = onPick?.let { p -> { p(i) } },
              wash = wash?.invoke(i),
            ) { col -> cell(i, col) }
          }
        }
      }
    }
    if (footer != null && count > 0) {
      Row(
        Modifier.fillMaxWidth().padding(DeskSpace.x2).height(DeskSize.tableHead).clip(DeskRadius.controlShape).background(c.surfaceAlt)
          .padding(horizontal = DeskSpace.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
      ) {
        columns.forEachIndexed { i, col ->
          Box(if (col.width != null) Modifier.width(col.width) else Modifier.weight(col.weight), contentAlignment = if (col.end) Alignment.CenterEnd else Alignment.CenterStart) {
            Text(footer.getOrElse(i) { "" }, style = DeskType.table.copy(fontWeight = DeskType.bodyStrong.fontWeight), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}

/* ───────────────────────────── ردیفِ آرام ───────────────────────────── */

/**
 *  ردیفِ آرامِ فهرست (۵۶dp) — نمونهٔ صاحب‌کار: [title] یک خط، [sub] یک خطِ کم‌رنگ («تاریخ • ساعت»)؛ در پایان [amount] و زیرش
 *  [status] به واژهٔ کم‌رنگ. نه نشانِ رنگی، نه خطِ رنگارنگ؛ [action] دست‌بالا یک کارِ خنثی. [amountColor] فقط برای معنای
 *  پول (بدهکار/صاف ِ قرض‌داران)، نه زینت.
 */
@Composable
fun DeskCalmRow(
  title: String,
  sub: String,
  amount: String,
  status: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  amountColor: Color? = null,
  leading: (@Composable () -> Unit)? = null,
  action: (@Composable () -> Unit)? = null,
) {
  val c = Desk.colors
  Column(modifier) {
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .deskClickable(label = title, selected = selected, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = DeskSpace.x3, vertical = DeskSpace.x2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (leading != null) {
        leading()
        Spacer(Modifier.width(DeskSpace.x3))
      }
      Column(Modifier.weight(1f)) {
        Text(title, style = DeskType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Spacer(Modifier.width(DeskSpace.x3))
      Column(horizontalAlignment = Alignment.End) {
        Text(amount, style = DeskType.number, color = amountColor ?: c.ink, maxLines = 1)
        if (status.isNotEmpty()) Text(status, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1)
      }
      if (action != null) {
        Spacer(Modifier.width(DeskSpace.x2))
        action()
      }
    }
    DeskDivider(Modifier.padding(horizontal = DeskSpace.x3))
  }
}

/* ───────────────────────────── پنلِ جزئیات ───────────────────────────── */

/**
 *  پنلِ جزئیاتِ کنارِ جدول: سرخط (عنوان، زیرعنوانِ کم‌رنگ، «بستن» = Esc ِ صفحه)، بدنهٔ پیمایشی و نوارِ کارهای چسبانِ پایین.
 */
@Composable
fun DeskPane(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  leading: (@Composable () -> Unit)? = null,
  onClose: (() -> Unit)? = null,
  scroll: Boolean = true,
  actions: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val c = Desk.colors
  Column(modifier.deskCard(c)) {
    Row(Modifier.fillMaxWidth().padding(start = DeskSpace.card, end = DeskSpace.x3, top = DeskSpace.x4, bottom = DeskSpace.x3), verticalAlignment = Alignment.CenterVertically) {
      if (leading != null) {
        leading()
        Spacer(Modifier.width(DeskSpace.x3))
      }
      Column(Modifier.weight(1f)) {
        Text(title, style = DeskType.sectionTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        if (!subtitle.isNullOrEmpty()) Text(subtitle, style = DeskType.secondary, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      if (onClose != null) DeskIconButton(Icons.Outlined.Close, Str.close, onClose)
    }
    DeskDivider()
    Column(
      Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        .padding(horizontal = DeskSpace.card, vertical = DeskSpace.x4),
      verticalArrangement = Arrangement.spacedBy(DeskSpace.x3),
      content = content,
    )
    if (actions != null) {
      DeskDivider()
      //  کارهای پنل در پهنای تنگ به ردیفِ بعد می‌پیچند — هیچ دکمه‌ای بریده یا پنهان نمی‌شود
      DeskActionsFlow(Modifier.fillMaxWidth().padding(horizontal = DeskSpace.x4, vertical = DeskSpace.x3), actions)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeskActionsFlow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
  FlowRow(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2),
    verticalArrangement = Arrangement.spacedBy(DeskSpace.x2),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) { content() }
}

/** پنلِ خالی: «یک ردیف را بزنید…» با آیکنِ آرام — هیچ کاری پشتش نیست، پس دکمه هم نه */
@Composable
fun DeskPaneHint(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
  val c = Desk.colors
  Column(
    modifier.deskCard(c).padding(DeskSpace.x8),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(Modifier.size(48.dp).clip(DeskRadius.pillShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.height(DeskSpace.x3))
    Text(text, style = DeskType.secondary, color = c.inkSecondary, textAlign = TextAlign.Center)
  }
}

/** سرخطِ کوچکِ بخشِ درونِ پنل */
@Composable
fun DeskSubhead(text: String, modifier: Modifier = Modifier) {
  Text(text, modifier.semantics { heading() }, style = DeskType.label, color = Desk.colors.inkSecondary, maxLines = 1)
}

/** برچسب و مقدار در یک خط (جمع‌های فاکتور، اطلاعاتِ پرونده) */
@Composable
fun DeskInfoRow(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false, valueColor: Color? = null) {
  val c = Desk.colors
  Row(modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.width(DeskSpace.x3))
    Text(value, style = if (strong) DeskType.number else DeskType.table, color = valueColor ?: c.ink, maxLines = 1)
  }
}

/* ───────────────────────────── جعبهٔ عددِ قرض‌داران ───────────────────────────── */

/**
 *  جعبهٔ عددِ رنگی — همان هویتِ «سه جعبهٔ رنگیِ» قرض‌دارانِ گوشی که صاحب‌کار پسندید (زمینهٔ کم‌رنگ و مرزِ همان رنگ، عددِ
 *  پررنگ)، به اندازهٔ میز: برچسب بالا، عددِ درشت به همان رنگ، خطِ کوچکِ زیر.
 */
@Composable
fun DeskStatBox(label: String, value: String, ink: Color, fill: Color, modifier: Modifier = Modifier, caption: String? = null, icon: ImageVector? = null) {
  val c = Desk.colors
  Column(
    modifier
      .fillMaxHeight()
      .clip(DeskRadius.cardShape)
      .background(fill)
      .border(DeskSize.hairline, ink, DeskRadius.cardShape)
      .semantics(mergeDescendants = true) {}
      .padding(horizontal = DeskSpace.card, vertical = DeskSpace.x4),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(DeskSpace.x2))
      }
      Text(label, style = DeskType.label, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(DeskSpace.x2))
    Text(value, style = DeskType.kpiSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (caption != null) Text(caption, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/* ───────────────────────────── کاشیِ حرف ───────────────────────────── */

/** کاشیِ گوشه‌گردِ حرفِ اولِ نام (مشتری، تأمین‌کننده) — [ink]/[fill] معنای حساب (سرخ بدهکار، سبز صاف) */
@Composable
fun DeskLetterTile(name: String, ink: Color, fill: Color, modifier: Modifier = Modifier, size: Dp = DeskSize.tile) {
  Box(modifier.size(size).clip(DeskRadius.tileShape).background(fill), contentAlignment = Alignment.Center) {
    Text(name.trim().take(1).ifBlank { "؟" }, style = DeskType.bodyStrong, color = ink, maxLines = 1)
  }
}

/* ───────────────────────────── بار و حالت ───────────────────────────── */

/**
 *  حسابِ سنگینِ صفحه بیرون از رشتهٔ صفحه (`Dispatchers.Default`) — تا نخستین نتیجه `null` (اسکلت)، پس از آن مقدارِ پیشین می‌ماند
 *  تا تازه برسد (بی چشمک). خطا ⇒ همان مقدارِ پیشین (صفحه نمی‌افتد).
 */
@Composable
fun <T> rememberDeskLoad(vararg keys: Any?, compute: () -> T): T? {
  val latest by rememberUpdatedState(compute)
  var value by remember { mutableStateOf<T?>(null) }
  LaunchedEffect(*keys) {
    val next = withContext(Dispatchers.Default) { runCatching { latest() } }
    next.onSuccess { value = it }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
  }
  return value
}

/** اسکلتِ ایستای جدول تا داده برسد — بی انیمیشن */
@Composable
fun DeskSkeleton(modifier: Modifier = Modifier, rows: Int = 8) {
  val c = Desk.colors
  Column(modifier.deskCard(c).padding(DeskSpace.card), verticalArrangement = Arrangement.spacedBy(DeskSpace.x4)) {
    repeat(rows) { i ->
      Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x4)) {
        Box(Modifier.weight(if (i % 3 == 0) 0.5f else 0.35f).height(12.dp).clip(DeskRadius.pillShape).background(c.surfaceAlt))
        Box(Modifier.weight(0.2f).height(12.dp).clip(DeskRadius.pillShape).background(c.surfaceAlt))
        Box(Modifier.weight(0.15f).height(12.dp).clip(DeskRadius.pillShape).background(c.surfaceAlt))
      }
    }
  }
}

/** متنِ کم‌رنگِ یک‌خطی (شمارِ ردیف، «از … تا …») */
@Composable
fun DeskNote(text: String, modifier: Modifier = Modifier, style: TextStyle = DeskType.secondary, maxLines: Int = 1) {
  Text(text, modifier, style = style, color = Desk.colors.inkSecondary, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** دو ستونِ جدول/پنل با پهنای پنلِ [deskPaneWidth]؛ [pane] null ⇒ همهٔ پهنا برای [main] */
@Composable
fun DeskSplit(modifier: Modifier = Modifier, pane: (@Composable (Dp) -> Unit)?, main: @Composable () -> Unit) {
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val w = deskPaneWidth(maxWidth)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
      Box(Modifier.weight(1f).fillMaxHeight()) { main() }
      if (pane != null) Box(Modifier.width(w).fillMaxHeight()) { pane(w) }
    }
  }
}
