package ir.vil3ntec.tohid.ui.v3.online

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import ir.vil3ntec.tohid.core.i18n.StrA11y
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import ir.vil3ntec.tohid.ui.v3.v3EdgeFade
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Type

/** یک تب با شمار: `attention` ⇐ نقطهٔ «در انتظارِ تأیید» کنارِ عدد (سفارشِ تازه) */
data class CountTab(val id: String, val label: String, val count: Int = 0, val attention: Boolean = false)

/**
 *  تب‌های پیمایشیِ شمارش‌دار با نشانگرِ لغزان — برای صف‌هایی که بیش از چهار
 *  برگه یا برچسبِ بلند دارند (سفارش‌ها، میزِ تحویل) و `V3Segmented` جایشان
 *  نمی‌شود. نشانگر با فنر زیرِ تبِ تازه می‌رود و عدد با هر تغییر یک «پرش»ِ کوچک
 *  دارد. ⇐ `DraftCountTabs`، برای بردن به کیت در PR نام برده شده.
 */
@Composable
fun DraftCountTabs(
  tabs: List<CountTab>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
  /**
   *  ۱۴۰۵/۰۸/۲۴ (صفِ سفارش‌ها، «تمام‌شده» در ۳۶۰dp بریده بود): همهٔ تب‌ها اگر جا هست در پهنای صفحه پهن می‌شوند (هیچ تبِ
   *  نیمه‌پیدا)؛ فقط وقتی نمی‌شود (زبانِ بلند، نوشتهٔ درشت) همان پیمایشِ محو‌شونده. false ⇒ همان پیمایشِ همیشه (میزِ تحویل).
   */
  fit: Boolean = false,
) {
  if (fit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
      val avail = maxWidth - 32.dp - 6.dp
      CountTabsBody(tabs, selected, onSelect, Modifier, avail)
    }
    return
  }
  CountTabsBody(tabs, selected, onSelect, modifier, null)
}

/** اندازهٔ نوشته و جای شمار در حالتِ `fit`: null ⇒ پهنای طبیعی (پیمایش) */
private data class TabFit(val sp: Float, val stacked: Boolean)

/**
 *  نخستین چیدمانی که همهٔ تب‌ها در [avail] جا شوند: نوشتهٔ ۱۴، ۱۳ با شمارِ کنارِ نام، بعد ۱۳ و ۱۲ با شمارِ زیرِ نام (پهنای هر تب
 *  = بلندترینِ نام یا شمار، نه جمعشان). هیچ‌کدام ⇒ null و همان پیمایشِ محو‌شونده.
 */
@Composable
private fun rememberTabFit(tabs: List<CountTab>, avail: Dp): TabFit? {
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val label = V3Type.callout.copy(fontWeight = FontWeight.SemiBold)
  val caption = V3Type.caption
  return remember(tabs, avail, label, caption, density) {
    with(density) {
      fun badge(t: CountTab): Int = if (t.count <= 0) 0 else
        measurer.measure(t.count.toString(), caption).size.width + 14.dp.roundToPx() + (if (t.attention) 10.dp.roundToPx() else 0)
      val pad: Int = (FIT_PAD * 2).roundToPx()
      val gap: Int = 6.dp.roundToPx()
      val room: Int = avail.roundToPx()
      listOf(TabFit(14f, false), TabFit(13f, false), TabFit(13f, true), TabFit(12f, true)).firstOrNull { f ->
        val style = label.copy(fontSize = f.sp.sp)
        var total = 0
        for (t in tabs) {
          val text: Int = measurer.measure(t.label, style, maxLines = 1).size.width
          val b: Int = badge(t)
          total += pad + if (f.stacked) maxOf(text, b) else text + (if (b > 0) gap + b else 0)
        }
        total <= room
      }
    }
  }
}

/** پهلوی هر تب در حالتِ `fit` */
private val FIT_PAD = 8.dp

@Composable
private fun CountTabsBody(tabs: List<CountTab>, selected: String, onSelect: (String) -> Unit, modifier: Modifier, fitTo: Dp?) {
  val c = PhoneV3.colors
  val density = LocalDensity.current
  val fit = fitTo?.let { rememberTabFit(tabs, it) }
  val fitting = fitTo != null && fit != null
  //  جای فیزیکیِ هر تب (از چپ، مستقل از جهت) و بلندی‌اش تا نشانگر در راست‌به‌چپ و حالتِ دوخطی هم درست بنشیند
  val bounds = remember { mutableStateMapOf<String, Pair<Dp, Dp>>() }
  val heights = remember { mutableStateMapOf<String, Dp>() }
  val target = bounds[selected]
  val x by animateDpAsState(target?.first ?: 0.dp, MotionSpec.indicator(), label = "tabX")
  val w by animateDpAsState(target?.second ?: 0.dp, MotionSpec.indicator(), label = "tabW")
  //  ۶۸c: لبه‌ای که پشتش تبِ دیگری هست نرم محو می‌شود — تبِ نیمه‌پیدا «بریده» دیده نمی‌شود (`v3EdgeFade`)
  val scroll = rememberScrollState()
  Box(
    modifier
      .fillMaxWidth()
      .v3EdgeFade(scroll)
      .horizontalScroll(scroll)
      .padding(horizontal = 16.dp),
  ) {
    Box(
      Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(if (c.source.isDark) c.tile else androidx.compose.ui.graphics.lerp(c.bg, c.hairline, 0.55f))
        .padding(3.dp),
    ) {
      if (target != null) {
        Box(
          Modifier
            .align(AbsoluteAlignment.TopLeft)
            .absoluteOffset(x = x)
            .width(w)
            .height(heights[selected] ?: 36.dp)
            .shadow(3.dp, RoundedCornerShape(10.dp), clip = false, ambientColor = c.ink.copy(alpha = 0.12f), spotColor = c.ink.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(10.dp))
            .background(c.card),
        )
      }
      FitRow(if (fitting) fitTo else null) {
        tabs.forEach { tab ->
          val on = tab.id == selected
          val ink by animateColorAsState(if (on) c.ink else c.inkMuted, MotionSpec.tweenOf(MotionTokens.SHORT), label = "tabInk")
          val base = if (on) V3Type.callout.copy(fontWeight = FontWeight.SemiBold) else V3Type.callout
          val style = if (fit != null) base.copy(fontSize = fit.sp.sp) else base
          val cell = Modifier
            .heightIn(min = 36.dp)
            .widthIn(min = if (fitting) 0.dp else 56.dp)
            .onGloballyPositioned { lc ->
              with(density) {
                bounds[tab.id] = lc.positionInParent().x.toDp() to lc.size.width.toDp()
                heights[tab.id] = lc.size.height.toDp()
              }
            }
            .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab.id) })
            //  رنگ و نقطهٔ «نیازِ توجه» ِ شمار برای صفحه‌خوان هم نوشته می‌شود («تازه، ۳، نیاز به توجه»)
            .then(
              if (tab.attention && tab.count > 0) Modifier.clearAndSetSemantics { contentDescription = StrA11y.tabNeedsAttention(tab.label, tab.count) }
              else Modifier,
            )
            .padding(horizontal = if (fitting) FIT_PAD else 14.dp, vertical = if (fit?.stacked == true) 5.dp else 8.dp)
          if (fit?.stacked == true) {
            Column(cell, horizontalAlignment = Alignment.CenterHorizontally) {
              Text(tab.label, style = style, color = ink, maxLines = 1)
              //  جای شمار همیشه هست تا نام‌ها هم‌تراز بمانند
              Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) { if (tab.count > 0) CountBadge(tab.count, tab.attention) }
            }
          } else {
            Row(cell, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
              Text(tab.label, style = style, color = ink, maxLines = 1)
              if (tab.count > 0) {
                Spacer(Modifier.width(6.dp))
                CountBadge(tab.count, tab.attention)
              }
            }
          }
        }
      }
    }
  }
}

/**
 *  ردیفِ تب‌ها: [fitTo] null ⇒ همان `Row` (پهنای طبیعی)؛ وگرنه اگر جمعِ پهنای طبیعی در [fitTo] جا می‌شود، جای خالی میانِ همه
 *  پخش می‌شود (همه در یک نگاه)، و اگر نمی‌شود همان پهنای طبیعی و پیمایش.
 */
@Composable
private fun FitRow(fitTo: Dp?, content: @Composable () -> Unit) {
  if (fitTo == null) {
    Row(verticalAlignment = Alignment.CenterVertically) { content() }
    return
  }
  Layout(content) { ms, cs ->
    val natural = ms.map { it.maxIntrinsicWidth(cs.maxHeight) }
    val avail = fitTo.roundToPx()
    val total = natural.sum()
    val extra = if (ms.isNotEmpty() && total < avail) (avail - total) / ms.size else 0
    val ps = ms.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(natural[i] + extra).copy(minHeight = 0, maxHeight = cs.maxHeight)) }
    val h = ps.maxOfOrNull { it.height } ?: 0
    layout(ps.sumOf { it.width }, h) {
      var x = 0
      ps.forEach { p -> p.placeRelative(x, (h - p.height) / 2); x += p.width }
    }
  }
}

/** عددِ تب: رنگِ «در انتظارِ تأیید» با نقطه برای «نیازِ توجه»، خاکستری برای بقیه؛ با هر تغییر می‌پرد */
@Composable
private fun CountBadge(count: Int, attention: Boolean) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.status(c, if (attention) V3Status.AWAITING else V3Status.INACTIVE)
  val bump = remember { Animatable(1f) }
  LaunchedEffect(count) {
    if (Motion.enabled) {
      bump.snapTo(1.25f)
      bump.animateTo(1f, MotionSpec.morph())
    }
  }
  Row(
    Modifier
      .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
      .clip(RoundedCornerShape(50))
      .background(pair.bg)
      .padding(horizontal = 7.dp, vertical = 1.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (attention) {
      Box(Modifier.size(6.dp).clip(CircleShape).background(pair.fg))
      Spacer(Modifier.width(4.dp))
    }
    Text(count.toString(), style = V3Type.caption, color = pair.fg)
  }
}
