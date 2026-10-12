package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import ir.vil3ntec.tohid.ui.theme.glassSurface
import androidx.compose.runtime.saveable.rememberSaveable

/**
 *  اجزای صفحهٔ تنظیمات.
 *
 *  تنظیمات پیش از این یک ستونِ بلند از متن روی زمینهٔ خالی بود: کاربر باید
 *  کل صفحه را می‌خواند تا چیزی را پیدا کند. حالا هر موضوع یک کارتِ جدا
 *  است که باز و بسته می‌شود، و هر ردیف آیکنِ خودش را دارد تا چشم بتواند
 *  اسکن کند نه اینکه بخواند.
 */

/**
 *  یک بخشِ تنظیمات — کارتی که باز و بسته می‌شود.
 *
 *  بخشِ بسته فقط یک ردیف جا می‌گیرد، پس تمامِ موضوع‌ها در یک صفحه دیده
 *  می‌شوند و کاربر می‌داند برنامه چه چیزهایی دارد.
 */
@Composable
fun SettingsSection(
  icon: ImageVector,
  title: String,
  subtitle: String,
  tint: Color = Shop.colors.primary,
  initiallyOpen: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  //  کامپیوتر: فقط بخشِ انتخاب‌شده در ستونِ راستِ «تنظیمات» و همیشه باز (`DeskSettings`)
  val pick = LocalSettingsPick.current
  if (pick != null) {
    if (pick != title) return
    DeskSettingsSection(icon, title, subtitle, tint, content)
    return
  }
  var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
  val colors = Shop.colors
  val turn by animateFloatAsState(
    targetValue = if (open) 180f else 0f,
    animationSpec = tween(if (Motion.enabled) 260 else 0, easing = FastOutSlowInEasing),
    label = "chevron",
  )

  Column(
    Modifier
      .fillMaxWidth()
      .glassSurface(Shape.card, colors.surface, colors.sheen, colors.border, glow = colors.glow)
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .clickable { open = !open }
        .padding(Space.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconBubble(icon, tint)
      Spacer(Modifier.width(Space.sm))
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.muted)
      }
      Icon(
        Icons.Filled.ExpandMore,
        contentDescription = if (open) "بستن" else "باز کردن",
        tint = colors.muted,
        modifier = Modifier.rotate(turn),
      )
    }

    AnimatedVisibility(
      visible = open,
      enter = fadeIn(tween(180)) + expandVertically(tween(240, easing = FastOutSlowInEasing)),
      exit = fadeOut(tween(140)) + shrinkVertically(tween(200, easing = FastOutSlowInEasing)),
    ) {
      Column(
        Modifier.padding(start = Space.md, end = Space.md, bottom = Space.md),
        content = content,
      )
    }
  }
}

/** ظرفِ گردِ آیکن — همان چیزی که ردیف‌ها را قابلِ اسکن می‌کند */
@Composable
fun IconBubble(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp = 38.dp) {
  Box(
    Modifier
      .size(size)
      .clip(Shape.icon)
      .background(tint.copy(alpha = 0.16f)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
  }
}

/**
 *  یک ردیفِ تنظیمات: آیکن، عنوان، توضیحِ کوتاه، و چیزی در سمتِ دیگر.
 *
 *  توضیحِ کوتاه اختیاری است ولی جایی که هست، کاربر را از حدس‌زدن نجات
 *  می‌دهد: «پشتیبان‌گیری» به‌تنهایی نمی‌گوید آخرین بار کِی بوده.
 */
@Composable
fun SettingsRow(
  icon: ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  description: String? = null,
  tint: Color = Shop.colors.primary,
  value: String? = null,
  onClick: (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  val colors = Shop.colors
  Row(
    modifier
      .fillMaxWidth()
      // فاصله تا ردیفِ بالا و پایین. چون **پیش از** clip می‌آید، از خودِ
      // کادر کم می‌شود نه از داخلش — یعنی یک حاشیهٔ واقعی بینِ ردیف‌ها، نه
      // فضای خالیِ داخلِ کادر. بدونش ردیف‌ها به هم می‌چسبیدند و فهرست یک
      // تودهٔ یکدست می‌شد. قرینه است تا بالا و پایینِ پنل هم یک‌اندازه
      // بماند.
      .padding(vertical = 4.dp)
      .clip(Shape.chip)
      // خطِ دورِ ردیف. کم‌رنگ است ولی هست: بدونش چند ردیفِ پشتِ سرِ هم
      // یک تودهٔ یکدست می‌شوند و معلوم نیست هرکدام تا کجاست.
      .border(1.dp, colors.fieldBorder.copy(alpha = 0.45f), Shape.chip)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(vertical = Space.sm, horizontal = Space.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconBubble(icon, tint, size = 34.dp)
    Spacer(Modifier.width(Space.sm))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text)
      if (description != null) {
        Text(description, style = MaterialTheme.typography.labelSmall, color = colors.muted2)
      }
    }
    when {
      trailing != null -> trailing()
      value != null -> Text(value, style = MaterialTheme.typography.labelMedium, color = colors.muted)
      onClick != null -> Icon(
        Icons.Filled.ChevronLeft,
        contentDescription = null,
        tint = colors.muted2,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/**
 *  انتخابِ یکی از چند گزینه، به شکلِ بخش‌بندیِ لغزان.
 *
 *  به‌جای چند دکمهٔ رادیویی زیرِ هم: یک نوار که نشانگرش زیرِ گزینهٔ
 *  انتخاب‌شده می‌لغزد. هم جای کمتری می‌گیرد، هم عوض‌کردنش یک لمس است.
 */
@Composable
fun SegmentedChoice(
  options: List<Pair<String, String>>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = Shop.colors
  Row(
    modifier
      .fillMaxWidth()
      .clip(Shape.button)
      .background(colors.surface2.copy(alpha = 0.7f))
      .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    options.forEach { (id, label) ->
      val active = selected == id
      val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) colors.primary.copy(alpha = 0.22f) else Color.Transparent,
        animationSpec = tween(if (Motion.enabled) 220 else 0),
        label = "seg",
      )
      Box(
        Modifier
          .weight(1f)
          .clip(Shape.chip)
          .background(bg)
          .clickable { onSelect(id) }
          .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelLarge,
          color = if (active) colors.primary else colors.muted,
          fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
      }
    }
  }
}


/** کامپیوتر: عنوانِ بخشی که ستونِ راستِ تنظیمات نشان می‌دهد؛ null یعنی گوشی (همهٔ بخش‌ها، تاشونده) */
val LocalSettingsPick = staticCompositionLocalOf<String?> { null }

@Composable
private fun DeskSettingsSection(icon: ImageVector, title: String, subtitle: String, tint: Color, content: @Composable ColumnScope.() -> Unit) {
  Column(
    Modifier
      .fillMaxWidth()
      .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
      .background(Shop.colors.surfaceSolid)
      .border(1.dp, Shop.colors.border, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
      .padding(24.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconBubble(icon, tint, 44.dp)
      Spacer(Modifier.width(14.dp))
      Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted)
      }
    }
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Shop.colors.border))
    Spacer(Modifier.height(18.dp))
    Column(Modifier.widthIn(max = 720.dp), content = content)
  }
}

/**
 *  تنظیماتِ کامپیوتر — دو ستون، مثلِ تنظیماتِ ویندوز و برنامه‌های کامپیوتری:
 *  فهرستِ بخش‌ها کنار، محتوای بخشِ انتخاب‌شده (همیشه باز) در وسط. همان
 *  `SettingsScreen`ِ گوشی رسم می‌شود؛ `SettingsSection` با `LocalSettingsPick`
 *  فقط بخشِ انتخاب‌شده را می‌کشد.
 */
@Composable
fun DeskSettings(sections: List<Pair<String, ImageVector>>, content: @Composable () -> Unit) {
  var pick by rememberSaveable { mutableStateOf(sections.first().first) }
  Row(Modifier.fillMaxSize().padding(16.dp)) {
    Column(
      Modifier
        .width(240.dp)
        .fillMaxHeight()
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
        .background(Shop.colors.surfaceSolid)
        .border(1.dp, Shop.colors.border, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
        .padding(10.dp),
    ) {
      Text("تنظیمات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text, modifier = Modifier.padding(10.dp))
      sections.forEach { (title, icon) ->
        val on = title == pick
        Row(
          Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(if (on) Shop.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { pick = title }
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(icon, null, tint = if (on) Shop.colors.primary else Shop.colors.muted, modifier = Modifier.size(19.dp))
          Spacer(Modifier.width(10.dp))
          Text(title, style = MaterialTheme.typography.bodyMedium, color = if (on) Shop.colors.primary else Shop.colors.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
        }
      }
    }
    Spacer(Modifier.width(16.dp))
    Box(Modifier.weight(1f).fillMaxHeight()) {
      CompositionLocalProvider(LocalSettingsPick provides pick) { content() }
    }
  }
}
