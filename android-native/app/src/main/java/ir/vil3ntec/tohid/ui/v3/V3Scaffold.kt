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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.layoutId
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
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.heightIn
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

  /** بلندیِ واقعیِ عنوانِ درشت (با درشتیِ نوشتهٔ سیستم) — عنوانِ کوچکِ سربرگ از روی همین */
  var titleMax by mutableFloatStateOf(0f)
    internal set

  /** صفر = باز، یک = جمع */
  val fraction: Float get() = if (max <= 0f) 0f else (-offset / max).coerceIn(0f, 1f)

  /** چقدر از عنوانِ درشت رفته (۰ تا ۱) */
  val titleFraction: Float get() = if (titleMax <= 0f) 0f else (-offset / titleMax).coerceIn(0f, 1f)

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
 *
 *  هیچ ردیفی میانِ محتوا و نوارِ شناورِ پایین نیست (صاحب‌کار ۱۴۰۵/۰۸/۲۴، جای `V3Dock` ِ ۶۳r): تراشه‌ها یک ردیفِ
 *  افقیِ فشرده زیرِ عنوان‌اند؛ کارها آیکنِ سربرگ‌اند — دو تای اول پیدا، بقیه در «…» (پیش‌تر سومی بی‌صدا گم می‌شد)؛
 *  تنها چیزِ پایین `fab` است. `search` روی گوشی و تبلت فقط با دکمهٔ جست‌وجوی نوارِ پایین (`V3ScopedSearch`) یا،
 *  وقتی نوار آن دکمه را ندارد، ذره‌بینِ سربرگ باز می‌شود: نوارِ جست‌وجو جای سربرگ، فهرستِ همین بخش زیرش؛ کامپیوتر
 *  (`LocalDeskLayout`) کادر را همیشه زیرِ عنوان دارد.
 */
/**
 *  کامپیوتر: عنوانی که `DeskHeader` ِ پوسته بالای همین صفحه نشان می‌دهد (`AppRoot`). وقتی عنوانِ [V3Scaffold] همان است،
 *  نوارِ صفحه عنوان را دوباره نمی‌نویسد (فقط برگشت و دکمه‌ها) — «عنوان دو بار» ِ e-desk-d1. صفحهٔ تودرتو با عنوانِ دیگر همان می‌ماند.
 */
val LocalDeskPageTitle = androidx.compose.runtime.compositionLocalOf<String?> { null }

@Composable
fun V3Scaffold(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  actions: List<V3Action> = emptyList(),
  overflow: List<V3MenuItem> = emptyList(),
  search: V3Search? = null,
  chips: (@Composable () -> Unit)? = null,
  notice: (@Composable () -> Unit)? = LocalV3Notice.current,
  bottomReserve: Dp = 0.dp,
  /**
   *  true ⇒ عنوانِ درشتِ زیرِ نوار که با پیمایش جمع می‌شود. پیش‌فرض از ۶۵s `false` (صاحب‌کار: عنوان بالا در نوار، وسطِ
   *  برگشت و راهنما — مثلِ «قرض‌داران»)؛ بخشی که عنوانِ درشت می‌خواهد آن را با دلیل روشن می‌کند.
   */
  largeTitle: Boolean = false,
  state: V3HeaderState = rememberV3HeaderState(),
  fab: (@Composable BoxScope.(expanded: Boolean) -> Unit)? = null,
  bottomBar: (@Composable () -> Unit)? = null,
  /** زمینهٔ ویژه به‌جای `bg` (مثلاً `Modifier.frosted(PhoneV3.colors)` در «حساب من»)؛ سربرگ و عنوانِ درشت شفاف می‌شوند */
  backdrop: Modifier? = null,
  content: @Composable (PaddingValues) -> Unit,
) {
  //  گوشی و تبلت: جست‌وجوی بخش با دکمهٔ نوار (یا ذره‌بینِ سربرگ)؛ کامپیوتر: کادرِ همیشگیِ زیرِ عنوان
  val thumb = !ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout.current
  val finder = rememberV3SearchToggle(search, enabled = thumb)
  val searching = thumb && search != null && finder.shown
  val navSearch = LocalV3NavSearch.current
  val c = PhoneV3.colors
  val density = LocalDensity.current
  //  پنجرهٔ کوتاه (افقی، دوپاره): عنوانِ درشت نه — همه‌جا برای فهرست (AUDIT-NAV S3)
  val large = largeTitle && !searching && LocalConfiguration.current.screenHeightDp >= COMPACT_HEIGHT
  //  عنوانِ کوچکِ سربرگ فقط وقتی عوض می‌شود که مرز رد شود — نه هر فریم (AUDIT-NAV F1)
  val deskTitle = LocalDeskPageTitle.current
  val shownAbove = ir.vil3ntec.tohid.ui.Platform.DESKTOP && deskTitle != null && deskTitle == title
  val showTitle by remember(large, shownAbove) { derivedStateOf { !shownAbove && (!large || state.titleFraction > 0.65f) } }
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
  //  OPS-06: «راهنما» ِ صفحهٔ ریشه (`LocalV3Guide`، فقط وقتی راهنمای آماده هست) — آیکنِ سربرگ اگر جا هست (تا سه آیکن)،
  //  وگرنه نخستین ردیفِ «…»
  val guide = LocalV3Guide.current
  var guideOpen by remember { mutableStateOf(false) }
  val find = if (thumb && search != null && !navSearch) listOf(V3Action(Icons.Outlined.Search, StrKit.search, onClick = finder.open)) else emptyList()
  //  ۶۵s: عنوانِ وسطِ نوار هرگز زیرِ آیکن نمی‌رود — از پهنای پنجره و پهنای خودِ عنوان، دست‌بالا دو کار (راهنما هم یک کار است،
  //  واپسین) پیدا و بقیه در «…» (`V3HeaderActions.visibleFor`)؛ راهنمای رفته به «…» نخستین ردیفِ آن است
  val guideAction = guide?.let { listOf(V3Action(Icons.AutoMirrored.Outlined.HelpOutline, StrKit.guide) { guideOpen = true }) }.orEmpty()
  val candidates = find + actions + guideAction
  val measurer = rememberTextMeasurer()
  val titleStyle = V3Type.headline
  val titlePx = remember(title, titleStyle, measurer) { measurer.measure(title, titleStyle, maxLines = 1).size.width.toFloat() }
  val barPx = with(density) { (LocalConfiguration.current.screenWidthDp.dp - 32.dp).toPx() }
  val iconPx = with(density) { 44.dp.toPx() }
  val gapPx = with(density) { 8.dp.toPx() }
  val visible = V3HeaderActions.visibleFor(candidates.size, overflow.isNotEmpty()) { icons ->
    val row = if (icons == 0) 0f else icons * iconPx + (icons - 1) * gapPx
    titlePx + 2 * (maxOf(if (onBack != null) iconPx else 0f, row) + gapPx) <= barPx
  }
  val split = V3HeaderActions.split(candidates, overflow, visible)
  val shown = split.shown
  val menu = split.menu.partition { it.label == StrKit.guide && guide != null }.let { (g, rest) -> g + rest }
  val headerActions = shown +
    (if (menu.isEmpty()) emptyList() else listOf(V3Action(Icons.Outlined.MoreHoriz, StrKit.more, dot = split.menuDot) { menuOpen = true }))

  //  زدنِ بیرون از کادرِ نوشتن ⇒ صفحه‌کلید بسته (`v3ClearFocusOnTap`، یک جا برای همهٔ صفحه‌های کیت)
  Box((if (backdrop != null) modifier.fillMaxSize().then(backdrop) else modifier.fillMaxSize().background(c.bg)).v3ClearFocusOnTap()) {
    Column(Modifier.fillMaxSize().nestedScroll(connection)) {
      if (searching && search != null) V3SearchBar(search, finder.close, transparent = backdrop != null)
      else V3HeaderBar(title, onBack, headerActions, showTitle = showTitle, divider = { if (large) state.fraction else 1f }, transparent = backdrop != null, heading = !large)
      //  عنوانِ درشت، پیام‌ها، جست‌وجو (کامپیوتر) و تراشه‌ها با هم جمع می‌شوند تا فهرست جا بگیرد (S3);
      //  بلندی با درشتیِ نوشته اندازه گرفته می‌شود، نه عددِ ثابت (S4)
      V3Collapsing(state, transparent = backdrop != null) {
        if (large) V3LargeTitle(title, subtitle, state)
        //  بی عنوانِ درشت، زیرنویس (حالِ صفحه) یک خطِ کوچکِ وسط زیرِ نوار است — گم نمی‌شود
        else if (subtitle != null && !searching) V3BarSubtitle(subtitle)
        if (notice != null) V3NoticeSlot(notice)
        if (!thumb && search != null) V3InlineSearch(search)
        if (chips != null) Box(Modifier.fillMaxWidth().padding(top = if (large) 0.dp else 8.dp, bottom = 8.dp)) { chips() }
      }
      //  زیرِ محتوا فقط نوارِ چسبان و جای نوارِ شناور/سیستم — هیچ ردیفِ دیگری
      val under = bottomReserve + bottomBarHeight + (if (bottomBar == null) navInset else 0.dp)
      Box(Modifier.weight(1f).fillMaxWidth()) {
        content(PaddingValues(bottom = under + 16.dp))
        if (fab != null) {
          Box(
            Modifier
              .align(Alignment.BottomEnd)
              .padding(end = 16.dp, bottom = bottomReserve + bottomBarHeight + (if (bottomReserve == 0.dp && bottomBar == null) navInset else 0.dp) + 16.dp),
          ) { fab(!state.scrolledDown) }
        }
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
  }
  if (guideOpen && guide != null) V3GuideSheet(guide) { guideOpen = false }
  if (menuOpen) {
    V3Sheet(onDismiss = { menuOpen = false }, detents = false) { closer ->
      V3Card(padding = PaddingValues(vertical = 4.dp)) {
        menu.forEachIndexed { i, item ->
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

/**
 *  جای نوارهای پیام (AUDIT-NAV S7): چند نوار **زیرِ هم** با فاصلهٔ ۸، و وقتی هیچ‌کدام چیزی
 *  نشان نمی‌دهد هیچ جایی نمی‌گیرد (پیش‌تر روی هم می‌افتادند و ۸dp ِ خالی همیشه بود).
 */
@Composable
private fun V3NoticeSlot(notice: @Composable () -> Unit) {
  androidx.compose.ui.layout.Layout(content = notice, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
    val side = 16.dp.roundToPx()
    val edge = 4.dp.roundToPx()
    val gap = 8.dp.roundToPx()
    val inner = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0))
    val shown = measurables.map { it.measure(inner) }.filter { it.height > 0 }
    if (shown.isEmpty()) return@Layout layout(constraints.maxWidth, 0) {}
    val height = 2 * edge + shown.sumOf { it.height } + gap * (shown.size - 1)
    layout(constraints.maxWidth, height) {
      var y = edge
      shown.forEach { it.placeRelative(side, y); y += it.height + gap }
    }
  }
}

private const val BAR_BACK = "bar-back"
private const val BAR_TITLE = "bar-title"
private const val BAR_ACTIONS = "bar-actions"

private val LARGE = 52.dp
private val LARGE_WITH_SUB = 74.dp

/** ردیفِ بالای سربرگ: برگشت، عنوانِ کوچکِ وسط (وقتی جمع شده)، و کارها */
@Composable
internal fun V3HeaderBar(
  title: String,
  onBack: (() -> Unit)?,
  actions: List<V3Action>,
  showTitle: Boolean,
  /** پیدا شدنِ خطِ مویی زیرِ سربرگ (۰ تا ۱) — در رسم خوانده می‌شود، نه در ترکیب */
  divider: () -> Float,
  /** روی `backdrop` ِ صفحه: بی زمینه */
  transparent: Boolean = false,
  /** عنوانِ نوار تنها عنوانِ صفحه است (بی عنوانِ درشت) ⇒ سرخطِ صفحه‌خوان */
  heading: Boolean = false,
) {
  val c = PhoneV3.colors
  val alpha by animateFloatAsState(if (showTitle) 1f else 0f, MotionSpec.tweenOf(150), label = "barTitle")
  Column(Modifier.fillMaxWidth().background(if (transparent) Color.Transparent else c.bg).windowInsetsPadding(WindowInsets.statusBars)) {
    //  ۶۵s: عنوان وسطِ نوار میانِ برگشت و کارها؛ اگر کارهای یک سو پهن‌اند و وسط جا نیست، وسطِ جای خالیِ میانشان — هرگز زیرِ آیکن.
    //  چیدمانِ نوار در **هر زبان همان** است (صاحب‌کار: «لی‌اوتش به دیگه جهت نره حتی با عوض کردن زبان»): برگشت در راست، کارها در
    //  چپ، عنوان وسط — جهتِ چیدمان ثابتِ راست‌به‌چپ؛ جهتِ خودِ نوشته از محتوایش (انگلیسی همان چپ‌به‌راست خوانده می‌شود)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    androidx.compose.ui.layout.Layout(
      modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
      content = {
        Box(Modifier.layoutId(BAR_BACK)) { if (onBack != null) V3BackButton(onBack) }
        Text(
          title,
          style = V3Type.headline,
          color = c.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.layoutId(BAR_TITLE).graphicsLayer { this.alpha = alpha }.then(if (heading) Modifier.semantics { heading() } else Modifier),
        )
        Row(Modifier.layoutId(BAR_ACTIONS)) {
          actions.forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.width(8.dp))
            V3IconButton(a.icon, a.label, dot = a.dot, onClick = a.onClick)
          }
        }
      },
    ) { measurables, constraints ->
      val loose = constraints.copy(minWidth = 0, minHeight = 0)
      val back = measurables.first { it.layoutId == BAR_BACK }.measure(loose)
      val acts = measurables.first { it.layoutId == BAR_ACTIONS }.measure(loose)
      val gap = 8.dp.roundToPx()
      val w = constraints.maxWidth
      val free = (w - back.width - acts.width - 2 * gap).coerceAtLeast(0)
      val title = measurables.first { it.layoutId == BAR_TITLE }.measure(loose.copy(maxWidth = free))
      val h = maxOf(constraints.minHeight, back.height, acts.height, title.height)
      val centred = (w - title.width) / 2
      val x = if (centred >= back.width + gap && centred + title.width <= w - acts.width - gap) centred else back.width + gap + (free - title.width) / 2
      layout(w, h) {
        back.placeRelative(0, (h - back.height) / 2)
        title.placeRelative(x, (h - title.height) / 2)
        acts.placeRelative(w - acts.width, (h - acts.height) / 2)
      }
    }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).graphicsLayer { this.alpha = divider() }.background(c.hairline))
  }
}

/**
 *  ناحیهٔ جمع‌شوندهٔ زیرِ سربرگ: بلندیِ طبیعیِ خودش را اندازه می‌گیرد (`state.max`) و با
 *  پیمایش از بالا بریده می‌شود. `offset` فقط در مرحلهٔ چیدمان خوانده می‌شود — هیچ بازترکیبی
 *  در هر فریم نیست (AUDIT-NAV F1).
 */
@Composable
private fun V3Collapsing(state: V3HeaderState, transparent: Boolean, content: @Composable () -> Unit) {
  val c = PhoneV3.colors
  Column(
    Modifier
      .fillMaxWidth()
      .clipToBounds()
      .layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val full = p.height
        if (state.max != full.toFloat()) state.max = full.toFloat()
        val shown = (full + state.offset.roundToInt()).coerceIn(0, full)
        layout(p.width, shown) { p.placeRelative(0, shown - full) }
      }
      .background(if (transparent) Color.Transparent else c.bg),
  ) { content() }
}

/** عنوانِ درشت (و زیرعنوانِ اختیاری) — بلندیِ طبیعی، کم‌رنگ‌شدن و جابه‌جایی فقط در رسم */
@Composable
private fun V3LargeTitle(title: String, subtitle: String?, state: V3HeaderState) {
  val c = PhoneV3.colors
  Column(
    Modifier
      .fillMaxWidth()
      .heightIn(min = if (subtitle != null) LARGE_WITH_SUB else LARGE)
      .onSizeChanged { state.titleMax = it.height.toFloat() }
      .padding(start = 20.dp, end = 20.dp)
      .graphicsLayer {
        alpha = (1f - state.titleFraction * 1.6f).coerceIn(0f, 1f)
        translationY = state.offset * 0.35f
      },
  ) {
    Text(title, style = V3Type.largeTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
    if (subtitle != null) Text(subtitle, style = V3Type.subhead, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}

/** زیرنویسِ صفحه وقتی عنوان در نوار است: یک خطِ کم‌رنگِ وسط */
@Composable
private fun V3BarSubtitle(subtitle: String) {
  Text(
    subtitle,
    style = V3Type.footnote,
    color = PhoneV3.colors.inkMuted,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 6.dp),
  )
}

/** زیرِ این بلندیِ پنجره (dp) عنوانِ درشت نیست */
private const val COMPACT_HEIGHT = 480

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
