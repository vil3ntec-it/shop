package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrLook
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Section
import ir.vil3ntec.tohid.ui.theme.tone

/** یک کارِ کوچکِ زیرِ خلاصه (چاپ، «به تفکیکِ دسته»، حالِ صندوق) */
@Immutable
data class V3SummaryAction(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/**
 *  خلاصهٔ آرامِ بالای هر بخش — جای کادرِ بزرگِ گرادیانی (`V3HeroCard`) که صاحب‌کار در هیچ بخشی جز خانه نمی‌خواهد
 *  (۱۴۰۵/۰۸/۲۴: «این نوع مدل سربرگ اصلاً توی برنامه نباشه»). بی گرادیان: برچسبِ کوچک، عددِ درشت، یک خطِ کم‌رنگ (شمار،
 *  دوره) و کارهای کوچکِ متنی زیرش. فقط دو وزنِ نوشته (معمولی و نیم‌پررنگ).
 *
 *  ۶۵s (صاحب‌کار: «همه سربرگ‌ها وسط باشن و کشویی … و رنگِ خودشون داشته باشن»): همه‌چیز **وسط‌چین** در کارتِ تمام‌پهنا
 *  ([V3SummaryFrame])؛ [section] ⇒ تهِ‌رنگ، مرز و عددِ همان بخش (`SectionColors`)؛ با [collapseKey] (شناسهٔ پایدارِ صفحه، مثلِ
 *  مسیر) **تاشو** — بسته یک خطِ «برچسب · عدد»، و حالتِ هر صفحه روی همین دستگاه می‌ماند ([SummaryFold]). [corner] (کادرِ
 *  کشوییِ دوره) در گوشهٔ مقابلِ پیکان؛ [extra] چیزِ بیشترِ زیرِ عدد (نمودارِ تفکیک `V3SummaryBars`، خانه‌های شمار) که با
 *  بستن پنهان می‌شود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun V3Summary(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  caption: String? = null,
  /** خطِ کم‌رنگِ دوم (مثلاً ساعتِ دکان زیرِ جملهٔ وضعیت) */
  note: String? = null,
  actions: List<V3SummaryAction> = emptyList(),
  /** شناسهٔ پایدارِ صفحه برای حالتِ باز/بسته؛ `null` ⇒ تاشو نیست (فقط وسط‌چین) */
  collapseKey: String? = null,
  /** زیرِ خطِ کم‌رنگ و پیش از کارها؛ با بستن پنهان */
  extra: (@Composable ColumnScope.() -> Unit)? = null,
  /** رنگِ بخش (`SectionColors`)؛ `null` ⇒ کارتِ معمولیِ تم */
  section: V3Section? = null,
  /** گوشهٔ مقابلِ پیکان (کادرِ کشوییِ دوره)؛ با بستن پنهان */
  corner: (@Composable () -> Unit)? = null,
) {
  val sc = summaryColors(section)
  V3SummaryFrame(label, value, modifier, collapseKey = collapseKey, section = section, corner = corner, bottom = if (actions.isEmpty()) 16.dp else 8.dp) {
    Text(
      value,
      style = v3Text(if (value.length > 14) 24 else 28, FontWeight.SemiBold, 36),
      color = sc.strong,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
    if (caption != null) {
      Spacer(Modifier.height(2.dp))
      Text(caption, style = V3Type.subhead, color = sc.label, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
    if (note != null) Text(note, style = V3Type.footnote, color = sc.label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    extra?.invoke(this)
    if (actions.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      //  کارها وسط، و اگر جا نشد به ردیفِ بعد (نه بریده) — `HistoryPrintWiringTest`
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEach { a -> V3PillAction(a.label, a.onClick, icon = a.icon) }
      }
    }
  }
}

/** رنگ‌های کارتِ خلاصه: با بخش ⇒ جدولِ `SectionColors`، وگرنه کارتِ معمولیِ تم */
@Immutable
data class SummaryColors(val surface: Color, val edge: Color?, val label: Color, val strong: Color, val ink: Color, val chevron: Color, val track: Color)

@Composable
fun summaryColors(section: V3Section?): SummaryColors {
  val c = PhoneV3.colors
  val t = section?.tone() ?: return SummaryColors(c.card, null, c.inkMuted, c.ink, c.ink, c.chevron, c.tile)
  return SummaryColors(t.tint, t.edge, t.label, t.strong, t.ink, t.strong, t.edge)
}

/**
 *  قابِ مشترکِ هر خلاصهٔ بالای صفحه (`V3Summary`، «عددِ اصلیِ» گزارش‌ها): کارتِ تمام‌پهنا، هر نوشته وسط‌چین، با [section]
 *  تهِ‌رنگ و مرزِ همان بخش. با [collapseKey]: سرخط یک دکمهٔ تمام‌پهنا با بلندیِ دست‌کم ۴۸dp است (نقش «دکمه»، حالتِ
 *  «باز/بسته» برای صفحه‌خوان) با پیکان در گوشهٔ آغاز و [corner] در گوشهٔ پایان؛ بسته ⇒ فقط یک خطِ «[label] · [value]»؛
 *  باز ⇒ [body]. بزرگ/کوچک شدن با `animateContentSize` و فقط وقتی `Motion.enabled` (کاهشِ حرکت ⇒ همان دم).
 *  [labelColor]/[valueColor] برای عددِ ضرر (رنگ تنها نیست: برچسب خودش «ضرر» است).
 */
@Composable
fun V3SummaryFrame(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  collapseKey: String? = null,
  labelColor: Color? = null,
  valueColor: Color? = null,
  bottom: Dp = 16.dp,
  section: V3Section? = null,
  corner: (@Composable () -> Unit)? = null,
  body: @Composable ColumnScope.() -> Unit,
) {
  val sc = summaryColors(section)
  val fold = collapseKey?.let { rememberSummaryFold(it) }
  val open = fold?.folded != true
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  //  چیدمانِ کارت در **هر زبان همان** (صاحب‌کار: «لی‌اوتش به دیگه جهت نره حتی با عوض کردن زبان»): پیکان در راست، کادرِ کشویی در چپ،
  //  نمودار نام در راست و مبلغ در چپ — جهتِ چیدمان ثابتِ راست‌به‌چپ؛ جهتِ خودِ نوشته از محتوا. درونِ کادرِ کشویی جهتِ زبان ([corner])
  val language = LocalLayoutDirection.current
  val wrapped: @Composable () -> Unit = { if (corner != null) CompositionLocalProvider(LocalLayoutDirection provides language) { corner() } }
  val inLanguage: (@Composable () -> Unit)? = if (corner == null) null else wrapped
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
  Column(
    modifier
      .fillMaxWidth()
      .testTag(V3Anatomy.SUMMARY)
      .clip(shape)
      .background(sc.surface)
      .then(if (sc.edge != null) Modifier.border(1.dp, sc.edge, shape) else Modifier)
      .then(if (fold != null && Motion.enabled) Modifier.animateContentSize(MotionSpec.sheet()) else Modifier)
      .padding(start = 16.dp, end = 16.dp, top = if (fold != null) 0.dp else if (corner != null) 8.dp else 16.dp, bottom = if (open) bottom else 0.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (fold == null) {
      if (inLanguage != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { inLanguage() }
      Text(
        label,
        style = V3Type.subhead,
        color = labelColor ?: sc.label,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
      )
      Spacer(Modifier.height(4.dp))
    } else {
      SummaryHeader(label, value, open, labelColor ?: sc.label, valueColor ?: sc.strong, sc.chevron, inLanguage, fold::toggle)
    }
    if (open) body()
  }
  }
}

/**
 *  سرخطِ تاشو. بی [corner]: یک ردیف — برچسب (باز) یا «برچسب · عدد» (بسته) وسط و پیکان در گوشهٔ آغاز (راست در فارسی).
 *  با [corner] و باز: ردیفِ بالا پیکان در آغاز و [corner] در پایان، برچسب وسطِ زیرش (کادرِ کشویی خودش دکمه است و زدنش
 *  کارت را جمع نمی‌کند).
 */
@Composable
private fun SummaryHeader(
  label: String,
  value: String,
  open: Boolean,
  labelColor: Color,
  valueColor: Color,
  chevron: Color,
  corner: (@Composable () -> Unit)?,
  onToggle: () -> Unit,
) {
  val turn by animateFloatAsState(if (open) 180f else 0f, MotionSpec.sheet(), label = "summaryChevron")
  val press = Modifier
    .fillMaxWidth()
    .v3Pressable(label = if (open) StrLook.foldSummary else StrLook.unfoldSummary, role = Role.Button, scale = 1f, onClick = onToggle)
    .semantics {
      heading()
      stateDescription = if (open) StrA11y.expanded else StrA11y.collapsed
    }
  val arrow = @Composable { m: Modifier ->
    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = chevron, modifier = m.size(22.dp).rotate(turn))
  }
  if (open && corner != null) {
    Column(press, horizontalAlignment = Alignment.CenterHorizontally) {
      Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        arrow(Modifier)
        Spacer(Modifier.weight(1f))
        corner()
      }
      Text(label, style = V3Type.subhead, color = labelColor, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
      Spacer(Modifier.height(4.dp))
    }
    return
  }
  Box(press.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
    val line = if (open) buildAnnotatedString { append(label) } else buildAnnotatedString {
      append(label)
      append(" · ")
      withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.SemiBold)) { append(value) }
    }
    Text(
      line,
      style = V3Type.subhead,
      color = labelColor,
      maxLines = if (open) 2 else 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      //  جای پیکان در هر دو سو، تا نوشته درست وسطِ کارت بماند
      modifier = Modifier.padding(horizontal = 30.dp),
    )
    arrow(Modifier.align(Alignment.CenterStart))
  }
}

/** حالتِ باز/بستهٔ خلاصهٔ یک صفحه: خوانده از [SummaryFold] و هر زدن همان‌جا نوشته می‌شود */
@Stable
class SummaryFoldState internal constructor(private val store: ViewModePref.Store, private val key: String, initial: Boolean) {
  var folded by mutableStateOf(initial)
    private set

  fun toggle() {
    folded = !folded
    SummaryFold.save(store, key, folded)
  }
}

/**
 *  «بسته» ِ خلاصهٔ هر صفحه — **ترجیحِ نمایشیِ همین دستگاه** در `tohid-ui` (همان پروندهٔ `view_mode` و چیدمانِ نوار:
 *  `ViewModePref.PREFS`)؛ همگام نمی‌شود و در پشتیبان نمی‌رود. کلید `summary_folded:<صفحه>`؛ نبودن ⇒ باز (پیش‌فرض).
 */
object SummaryFold {
  private const val PREFIX = "summary_folded"

  fun key(screen: String): String {
    require(screen.isNotBlank()) { "screen id is required" }
    return "$PREFIX:" + screen.trim().map { if (it.isLetterOrDigit() || it in "-_./:") it else '_' }.joinToString("")
  }

  fun folded(store: ViewModePref.Store, screen: String): Boolean = runCatching { store.get(key(screen)) }.getOrNull() == "1"

  fun save(store: ViewModePref.Store, screen: String, folded: Boolean) {
    runCatching { store.put(key(screen), if (folded) "1" else "0") }
  }

}
