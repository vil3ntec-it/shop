package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/** یک کارِ سربرگ (حداکثر دو تا): آیکنِ گرد با برچسبِ دسترس‌پذیری و نقطهٔ اختیاری */
data class V3Action(
  val icon: ImageVector,
  val label: String,
  val dot: Boolean = false,
  val onClick: () -> Unit,
)

/** یک کارِ منوی «…» سربرگ — در برگهٔ پایین، مثلِ برگهٔ کارهای iOS */
data class V3MenuItem(
  val icon: ImageVector,
  val label: String,
  val danger: Boolean = false,
  val onClick: () -> Unit,
)

/**
 *  حالتِ سربرگِ جمع‌شونده: `offset` از صفر (باز) تا `-max` (جمع). محتوای
 *  پیمایش‌پذیرِ زیرِ آن (هر `LazyColumn` یا `verticalScroll`) با `nestedScroll`
 *  اول عنوانِ درشت را جمع می‌کند و بعد خودش می‌رود؛ برگشت در بالای فهرست باز
 *  می‌کند. رها کردن در میانه با فنر به نزدیک‌ترین حالت می‌نشیند.
 */
@Stable
class V3HeaderState {
  var max by mutableFloatStateOf(0f)
    internal set
  var offset by mutableFloatStateOf(0f)
    internal set

  /** صفر = باز، یک = جمع */
  val fraction: Float get() = if (max <= 0f) 0f else (-offset / max).coerceIn(0f, 1f)

  /** آخرین جهتِ پیمایش پایین بود؟ (دکمهٔ شناور جمع می‌شود) */
  var scrolledDown by mutableStateOf(false)
    internal set

  internal fun consume(dy: Float): Float {
    val next = (offset + dy).coerceIn(-max, 0f)
    val used = next - offset
    offset = next
    return used
  }
}

@Composable
fun rememberV3HeaderState(): V3HeaderState = remember { V3HeaderState() }

/**
 *  قابِ هر صفحهٔ گوشیِ نسخهٔ ۳ — جای سربرگِ آبیِ قدیم (`TohidTopBar`).
 *
 *  بالا: دکمهٔ گردِ برگشت در آغاز (اگر راهی به عقب هست)، تا دو کار و «…» در
 *  پایان، و عنوانِ درشتِ ۳۰sp زیرش که با پیمایش جمع می‌شود و عنوانِ کوچکِ وسط
 *  جایش را می‌گیرد (مثلِ iOS). زیرِ عنوان به ترتیب: `notice` (نوارِ اشتراک/
 *  به‌روزرسانی که پوسته می‌دهد)، `search` و `chips` — این سه جمع نمی‌شوند.
 *
 *  پایین: `bottomReserve` جای نوارِ شناورِ پایین است (صفحه‌های نوار)، و
 *  `bottomBar` نوارِ چسبان (فرم‌ها، `V3ActionBar`)؛ هر دو در `PaddingValues`ِ
 *  محتوا حساب شده‌اند تا آخرین ردیف زیرِ چیزی نماند. `fab` با پیمایش جمع می‌شود.
 */
@Composable
fun V3Scaffold(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  actions: List<V3Action> = emptyList(),
  overflow: List<V3MenuItem> = emptyList(),
  search: (@Composable () -> Unit)? = null,
  chips: (@Composable () -> Unit)? = null,
  notice: (@Composable () -> Unit)? = LocalV3Notice.current,
  bottomReserve: Dp = 0.dp,
  /** false ⇒ فقط عنوانِ کوچکِ وسط (فرم‌ها و صفحه‌های کوتاه) */
  largeTitle: Boolean = true,
  state: V3HeaderState = rememberV3HeaderState(),
  fab: (@Composable BoxScope.(expanded: Boolean) -> Unit)? = null,
  bottomBar: (@Composable () -> Unit)? = null,
  /** زمینهٔ ویژه به‌جای `bg` (مثلاً `Modifier.frosted(PhoneV3.colors)` در «حساب من»)؛ سربرگ و عنوانِ درشت شفاف می‌شوند */
  backdrop: Modifier? = null,
  content: @Composable (PaddingValues) -> Unit,
) {
  val c = PhoneV3.colors
  val density = LocalDensity.current
  val maxPx = with(density) { (if (subtitle != null) LARGE_WITH_SUB else LARGE).toPx() }
  SideEffect { state.max = if (largeTitle) maxPx else 0f }
  val settle = remember { Animatable(0f) }
  val connection = remember(state) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < -1f) state.scrolledDown = true else if (available.y > 1f) state.scrolledDown = false
        return if (available.y < 0f) Offset(0f, state.consume(available.y)) else Offset.Zero
      }

      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y > 0f) Offset(0f, state.consume(available.y)) else Offset.Zero

      override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val f = state.fraction
        if (f > 0f && f < 1f) {
          settle.snapTo(state.offset)
          settle.animateTo(if (f > 0.5f) -state.max else 0f, MotionSpec.headerSettle) { state.offset = value }
        }
        return Velocity.Zero
      }
    }
  }
  var barHeight by remember { mutableFloatStateOf(0f) }
  var menuOpen by remember { mutableStateOf(false) }
  val navInset = LocalV3SystemBottom.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val bottomBarHeight = with(density) { barHeight.toDp() }
  val headerActions = actions.take(2) + if (overflow.isEmpty()) emptyList() else listOf(V3Action(Icons.Outlined.MoreHoriz, StrKit.more) { menuOpen = true })

  Box(if (backdrop != null) modifier.fillMaxSize().then(backdrop) else modifier.fillMaxSize().background(c.bg)) {
    Column(Modifier.fillMaxSize().nestedScroll(connection)) {
      V3HeaderBar(title, onBack, headerActions, showTitle = !largeTitle || state.fraction > 0.65f, divider = if (largeTitle) state.fraction else 1f, transparent = backdrop != null)
      if (largeTitle) V3LargeTitle(title, subtitle, state, transparent = backdrop != null)
      if (notice != null) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) { notice() }
      if (search != null) Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) { search() }
      if (chips != null) Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { chips() }
      Box(Modifier.weight(1f).fillMaxWidth()) {
        content(PaddingValues(bottom = bottomReserve + bottomBarHeight + (if (bottomBar == null) navInset else 0.dp) + 16.dp))
      }
    }
    if (bottomBar != null) {
      Box(
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(bottom = bottomReserve)
          .onSizeChanged { barHeight = it.height.toFloat() },
      ) { bottomBar() }
    }
    if (fab != null) {
      Box(
        Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 16.dp, bottom = bottomReserve + bottomBarHeight + (if (bottomReserve == 0.dp && bottomBar == null) navInset else 0.dp) + 16.dp),
      ) { fab(!state.scrolledDown) }
    }
  }
  if (menuOpen) {
    V3Sheet(onDismiss = { menuOpen = false }) { closer ->
      V3Card(padding = PaddingValues(vertical = 4.dp)) {
        overflow.forEachIndexed { i, item ->
          if (i > 0) V3Divider(start = 64.dp)
          V3ListRow(item.label, icon = item.icon, danger = item.danger, chevron = false, onClick = { closer.close(item.onClick) })
        }
      }
    }
  }
}

/**
 *  نوارِ پیامِ پوسته (اشتراک رو به پایان، نسخهٔ تازه) که `V3Scaffold` بی‌پارامترِ
 *  `notice` خودش زیرِ عنوان می‌گذارد — پوسته (`AppRoot`) یک بار فراهمش می‌کند.
 */
val LocalV3Notice = androidx.compose.runtime.compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 *  پوسته‌ای که خودش جای نوارِ سیستم (پایین) را گرفته این را `0.dp` می‌گذارد تا
 *  `V3Scaffold`/`V3FormBar` دوباره حسابش نکنند؛ null ⇒ خودشان می‌سنجند.
 */
val LocalV3SystemBottom = androidx.compose.runtime.compositionLocalOf<Dp?> { null }

private val LARGE = 52.dp
private val LARGE_WITH_SUB = 74.dp

/** ردیفِ بالای سربرگ: برگشت، عنوانِ کوچکِ وسط (وقتی جمع شده)، و کارها */
@Composable
internal fun V3HeaderBar(
  title: String,
  onBack: (() -> Unit)?,
  actions: List<V3Action>,
  showTitle: Boolean,
  /** پیدا شدنِ خطِ مویی زیرِ سربرگ (۰ تا ۱) */
  divider: Float,
  /** روی `backdrop` ِ صفحه: بی زمینه */
  transparent: Boolean = false,
) {
  val c = PhoneV3.colors
  val alpha by animateFloatAsState(if (showTitle) 1f else 0f, MotionSpec.tweenOf(150), label = "barTitle")
  Column(Modifier.fillMaxWidth().background(if (transparent) Color.Transparent else c.bg).windowInsetsPadding(WindowInsets.statusBars)) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
      if (onBack != null) Box(Modifier.align(Alignment.CenterStart)) { V3BackButton(onBack) }
      Text(
        title,
        style = V3Type.headline,
        color = c.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 104.dp).graphicsLayer { this.alpha = alpha },
      )
      if (actions.isNotEmpty()) {
        Row(Modifier.align(Alignment.CenterEnd)) {
          actions.forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.width(8.dp))
            V3IconButton(a.icon, a.label, dot = a.dot, onClick = a.onClick)
          }
        }
      }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).graphicsLayer { this.alpha = divider }.background(c.hairline))
  }
}

/** عنوانِ درشتِ جمع‌شونده (و زیرعنوانِ اختیاری) */
@Composable
private fun V3LargeTitle(title: String, subtitle: String?, state: V3HeaderState, transparent: Boolean = false) {
  val c = PhoneV3.colors
  val density = LocalDensity.current
  val full = if (subtitle != null) LARGE_WITH_SUB else LARGE
  val visible = with(density) { (full.toPx() + state.offset).coerceAtLeast(0f).toDp() }
  Box(Modifier.fillMaxWidth().height(visible).background(if (transparent) Color.Transparent else c.bg)) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 20.dp)
        .graphicsLayer {
          alpha = (1f - state.fraction * 1.6f).coerceIn(0f, 1f)
          translationY = state.offset * 0.35f
        },
    ) {
      Text(title, style = V3Type.largeTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
      if (subtitle != null) Text(subtitle, style = V3Type.subhead, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/**
 *  دو ستونِ صفحهٔ پهن (کامپیوتر، تبلتِ افقی): فهرست در آغاز با پهنای ثابت و
 *  جزئیات در بقیه؛ بی جزئیات ⇒ فقط فهرست. روی گوشی صفحه‌ها همان فهرست را
 *  می‌کشند و جزئیات مسیرِ خودش است.
 */
@Composable
fun V3TwoPane(
  modifier: Modifier = Modifier,
  listWidth: Dp = 400.dp,
  list: @Composable () -> Unit,
  detail: (@Composable () -> Unit)? = null,
) {
  val c = PhoneV3.colors
  Row(modifier.fillMaxSize().background(c.bg)) {
    Box(Modifier.width(listWidth).fillMaxHeight()) { list() }
    if (detail != null) {
      Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
      Box(Modifier.weight(1f).fillMaxHeight()) { detail() }
    }
  }
}
