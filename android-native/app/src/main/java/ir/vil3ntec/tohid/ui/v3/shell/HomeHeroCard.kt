package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.PagerDots
import ir.vil3ntec.tohid.ui.screens.isDark
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Light
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3PagerItem
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** اندازه‌های قالبِ یکتای کادرِ بزرگ (THEME-V2 «هم‌خوانیِ کادرها») */
internal object HeroSpec {
  val radius = 28.dp
  val margin = 16.dp
  val height = 228.dp
  val gap = 10.dp
}

/** یک تراشهٔ عمل روی کادر؛ `route == null` یعنی فقط نوشته (جای‌نگهدار) */
data class HeroChip(val label: String, val icon: ImageVector?, val route: String?, val haptic: Boolean = false)

/** یک صفحهٔ کادرِ بزرگ: برچسب، عددِ درشت (یا «—»)، زیرنویس و تراشه‌ها — همه یک شکل */
data class HeroFace(
  val key: String,
  val label: String,
  val amount: Double?,
  val caption: String,
  val chips: List<HeroChip>,
  /** عددِ زنده از دفتر (نشانِ تازه‌شدن کنارِ زیرنویس) */
  val live: Boolean = true,
)

/**
 *  پیش‌نویسِ کیت (جای `V3HeroCard` ِ پایه): قالبِ **یکتای** صفحه‌های کادرِ بزرگِ خانه —
 *  «فروشِ امروز»، «صندوق»، «گاوصندوق» — و همان کادری که با کشیدن می‌آید. گوشهٔ ۲۸، بلندیِ ثابت،
 *  سرخطِ «نامِ دکان + زنگ»، عددِ بزرگ و زیرنویس در یک جا، ردیفِ تراشه‌های شیشه‌ای و نشانگرِ
 *  صفحه. گرادیانِ چندرنگ با دو دایرهٔ نرمِ تزئینی (رسم در draw، بی blur). متن همیشه سفید.
 */
@Composable
internal fun DraftHeroCard(
  face: HeroFace,
  shopName: String,
  bellDot: Boolean,
  pages: Int,
  current: Int,
  onOpen: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val p = PhoneV3.colors
  val dark = p.isDark
  val white = PhoneV3Light.card
  val shape = RoundedCornerShape(HeroSpec.radius)
  //  گرادیان از رنگ‌های روزِ کیت (متنِ سفید رویشان خواناست)؛ شب با لایهٔ تیره پایین‌تر
  val brush = remember(dark) {
    Brush.linearGradient(
      listOf(PhoneV3Light.accentDeep, PhoneV3Palette.glyph(PhoneV3Light, PhoneV3Palette.Hue.BLUE), PhoneV3Palette.glyph(PhoneV3Light, PhoneV3Palette.Hue.PURPLE)),
      start = Offset(Float.POSITIVE_INFINITY, 0f),
      end = Offset(0f, Float.POSITIVE_INFINITY),
    )
  }
  val orbA = HomeAccents.color(PhoneV3Light, HomeAccent.MINT).copy(alpha = 0.18f)
  val orbB = HomeAccents.color(PhoneV3Light, HomeAccent.AMBER).copy(alpha = 0.18f)
  val shade = p.black.copy(alpha = if (dark) 0.32f else 0f)
  Column(
    modifier
      .fillMaxWidth()
      .height(HeroSpec.height)
      .clip(shape)
      .background(brush)
      .drawBehind {
        drawCircle(orbA, radius = size.height * 0.62f, center = Offset(size.width * 0.08f, size.height * 1.05f))
        drawCircle(orbB, radius = size.height * 0.42f, center = Offset(size.width * 0.95f, size.height * 0.02f))
        drawRect(shade)
      }
      .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        Modifier.weight(1f, fill = false).clip(RoundedCornerShape(10.dp)).v3Pressable(label = shopName) { onOpen(ShellTargets.PROFILE) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(shopName, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis, style = v3Text(15, FontWeight.SemiBold, 21), modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = white, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.weight(1f))
      Box(
        Modifier.v3Pressable(label = StrKit.notifications, scale = 0.92f) { onOpen(ShellTargets.notifications()) }
          .size(38.dp).clip(CircleShape).background(white.copy(alpha = 0.16f)).border(1.dp, white.copy(alpha = 0.24f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Outlined.Notifications, contentDescription = StrKit.notifications, tint = white, modifier = Modifier.size(20.dp))
        if (bellDot) Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 9.dp).size(8.dp).clip(CircleShape).background(p.negative))
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(face.label, color = white.copy(alpha = 0.8f), style = v3Text(13, FontWeight.Medium, 18), maxLines = 1, overflow = TextOverflow.Ellipsis)
    val shown = rememberCountUp(face.amount ?: 0.0)
    val final = face.amount?.let { "${money(it)} ؋" } ?: "—"
    Text(
      face.amount?.let { "${money(shown)} ؋" } ?: final,
      color = white,
      maxLines = 1,
      style = v3Text(if (final.length > 12) 30 else 36, FontWeight.Bold, 44),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(face.caption, color = white.copy(alpha = 0.8f), style = v3Text(13, FontWeight.Medium, 18), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
      if (face.live) {
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = white.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
      }
    }
    Spacer(Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        face.chips.forEach { chip -> HeroChipView(chip, onOpen) }
      }
      PagerDots(pages, current, white, white.copy(alpha = 0.35f))
    }
  }
}

/** تراشهٔ شیشه‌ایِ سفید (سفید با آلفای ۱۶٪) */
@Composable
private fun HeroChipView(chip: HeroChip, onOpen: (String) -> Unit) {
  val white = PhoneV3Light.card
  val pill = RoundedCornerShape(percent = 50)
  val route = chip.route
  Row(
    Modifier
      .then(if (route != null) Modifier.v3Pressable(label = chip.label, scale = 0.94f, haptic = chip.haptic) { onOpen(route) } else Modifier)
      .height(34.dp)
      .clip(pill)
      .background(white.copy(alpha = 0.16f))
      .border(1.dp, white.copy(alpha = 0.22f), pill)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    chip.icon?.let { Icon(it, contentDescription = null, tint = white, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
    Text(chip.label, color = white, style = v3Text(12, FontWeight.SemiBold, 16), maxLines = 1)
  }
}

/**
 *  کادرِ بزرگِ ورق‌خورِ خانه: هر صفحه همان قالب، با انگشت ۱:۱ و نشستنِ فنری؛ صفحهٔ کناری
 *  کمی کوچک و کم‌رنگ (`v3PagerItem`)، ۱۶dp از لبه‌ها.
 */
@Composable
internal fun HomeHeroPager(view: HomeView, faces: List<HeroFace>, state: PagerState, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  HorizontalPager(
    state = state,
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = HeroSpec.margin),
    pageSpacing = HeroSpec.gap,
    flingBehavior = PagerDefaults.flingBehavior(state, snapAnimationSpec = MotionSpec.sheet()),
    key = { faces[it].key },
  ) { page ->
    DraftHeroCard(faces[page], view.shopName, view.bellDot, faces.size, state.currentPage, onOpen, Modifier.v3PagerItem(state, page))
  }
}
