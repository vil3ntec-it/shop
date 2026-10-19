package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CallToAction
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import androidx.compose.runtime.saveable.rememberSaveable

/**
 *  اجزای صفحهٔ تنظیمات.
 *
 *  یک `SettingsScreen` سه جور کشیده می‌شود و هر بخش (`SettingsSection`) خودش
 *  می‌داند کجاست:
 *   • **گوشی، فهرست** (`PhoneSettingsMode.Root`، تصویرِ مرجعِ r5): هر بخش یک
 *     ردیفِ کوتاه با آیکنِ خطیِ تک‌رنگ و مقدارِ خاکستری / کلیدِ سبز / فلش است
 *     و زدنش صفحهٔ خودِ همان بخش را باز می‌کند — بی آکاردئون و بی حبابِ رنگی.
 *   • **گوشی، صفحهٔ بخش** (`PhoneSettingsMode.Detail`): فقط همان بخش، با همان
 *     محتوای پیشین در یک کارتِ سفید.
 *   • **کامپیوتر** (`LocalSettingsPick`): ستونِ کناری و بخشِ انتخاب‌شده، مثلِ قبل.
 *  کارِ هیچ تنظیمی عوض نشده؛ فقط جای نشستنش.
 */

/** گوشی: فهرست یا صفحهٔ یک بخش؛ null یعنی کامپیوتر (`LocalSettingsPick`) */
sealed interface PhoneSettingsMode {
  class Root(val open: (String) -> Unit) : PhoneSettingsMode
  class Detail(val title: String) : PhoneSettingsMode
}

val LocalPhoneSettings = staticCompositionLocalOf<PhoneSettingsMode?> { null }

/**
 *  درخواستِ بازکردنِ تنظیمات روی یک گروه یا یک بخش — از گروهِ «تنظیمات»ِ
 *  «بیشتر» و از پیش‌نمایشِ عکس‌ها. `origin` صفحه‌ای است که «برگشت»ِ ریشهٔ
 *  تنظیمات به آن می‌رود وقتی `AppRoot` راهِ برگشت نداده.
 */
object SettingsNav {
  data class Request(val group: String? = null, val detail: String? = null)

  val pending = mutableStateOf<Request?>(null)
  var origin by mutableStateOf("more")

  fun open(from: String, group: String? = null, detail: String? = null) {
    origin = from
    pending.value = if (group == null && detail == null) null else Request(group, detail)
    ir.vil3ntec.tohid.ui.AppNav.go("settings")
  }
}

/**
 *  پیش‌نمایشِ عکس‌های خودکار (`PhonePreview`): کلیدِ «قفلِ برنامه» را روشن نشان
 *  بده تا کلیدِ سبزِ r5 در عکس دیده شود — رمزِ واقعی دست نمی‌خورد. در برنامهٔ
 *  دستِ کاربر همیشه null است.
 */
object SettingsShot {
  var lockOn: Boolean? = null
}

/** آیکنِ خطیِ ردیفِ هر بخش و هر گروه (r5) — همه تک‌رنگ */
internal fun settingsIcon(key: String): ImageVector? = when (key) {
  "هشدارها" -> Icons.Outlined.Notifications
  "ظاهر" -> Icons.Outlined.LightMode
  SettingsLayout.NAV_BAR -> Icons.Outlined.CallToAction
  "زبان" -> Icons.Outlined.Language
  "تاریخ و رقم" -> Icons.Outlined.CalendarToday
  "قفل برنامه" -> Icons.Outlined.Lock
  "کد شاگرد" -> Icons.Outlined.Key
  "دستگاه‌های وارد شده" -> Icons.Outlined.PhoneAndroid
  "تغییر رمز حساب" -> Icons.Outlined.Password
  "کالای منقضی" -> Icons.Outlined.EventBusy
  SettingsLayout.PRINTER -> Icons.Outlined.Print
  "دوربین بارکدخوان" -> Icons.Outlined.QrCodeScanner
  "همگام‌سازی" -> Icons.Outlined.Sync
  "داده‌ها" -> Icons.Outlined.Backup
  "درباره برنامه" -> Icons.Outlined.Info
  //  گروه‌ها (گروهِ «تنظیمات»ِ صفحهٔ «بیشتر»؛ «داده‌ها» همان کلیدِ بخشِ پشتیبان است)
  SettingsLayout.GENERAL -> Icons.Outlined.Tune
  SettingsLayout.SECURITY -> Icons.Outlined.Shield
  SettingsLayout.SALES -> Icons.Outlined.PointOfSale
  SettingsLayout.DEVICES -> Icons.Outlined.QrCodeScanner
  SettingsLayout.ABOUT -> Icons.Outlined.Info
  else -> null
}

/**
 *  یک بخشِ تنظیمات.
 *
 *  `value` مقدارِ کوتاهِ خاکستریِ ته ردیف است (r5)، و `toggle`/`onToggle` کلیدِ
 *  سبزِ خودِ ردیف (مثلِ Face IDِ r5) برای بخشی که روشن/خاموش دارد؛ زدنِ خودِ
 *  ردیف همچنان صفحهٔ بخش را باز می‌کند.
 */
@Composable
fun SettingsSection(
  icon: ImageVector,
  title: String,
  subtitle: String,
  tint: Color = Shop.colors.primary,
  value: String? = null,
  toggle: Boolean? = null,
  onToggle: ((Boolean) -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  //  کامپیوتر: فقط بخشِ انتخاب‌شده در ستونِ راستِ «تنظیمات» و همیشه باز (`DeskSettings`)
  val pick = LocalSettingsPick.current
  if (pick != null) {
    if (pick != title) return
    DeskSettingsSection(icon, title, subtitle, tint, content)
    return
  }
  when (val mode = LocalPhoneSettings.current) {
    is PhoneSettingsMode.Root -> V3Row(
      icon = settingsIcon(title) ?: icon,
      label = SettingsLayout.labelOf(title),
      value = value,
      toggle = toggle,
      onToggle = onToggle,
      onClick = { mode.open(title) },
    )
    is PhoneSettingsMode.Detail -> if (mode.title == title) SettingsDetailBody(subtitle, content)
    null -> SettingsDetailBody(subtitle, content)
  }
}

/** صفحهٔ یک بخش روی گوشی: یک خطِ خاکستری و محتوای پیشینِ بخش در کارتِ سفید */
@Composable
private fun SettingsDetailBody(subtitle: String, content: @Composable ColumnScope.() -> Unit) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth()) {
    if (subtitle.isNotBlank()) {
      Text(
        subtitle,
        style = v3Text(13, FontWeight.Medium, 19),
        color = c.inkMuted,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 10.dp),
      )
    }
    V3Card(padding = PaddingValues(16.dp), content = content)
  }
}

/**
 *  ردیفی در فهرستِ تنظیماتِ گوشی که صفحهٔ دیگری از برنامه را باز می‌کند
 *  (`SettingsLayout.LINKS`). کامپیوتر و صفحهٔ هر بخش آن را نمی‌کشند.
 */
@Composable
fun SettingsLink(title: String, value: String? = null, onClick: () -> Unit) {
  if (LocalSettingsPick.current != null) return
  if (LocalPhoneSettings.current !is PhoneSettingsMode.Root) return
  V3Row(icon = settingsIcon(title), label = SettingsLayout.labelOf(title), value = value, onClick = onClick)
}

/**
 *  گروهِ تنظیمات — سرخطِ خاکستری بیرونِ کارت و یک کارتِ سفیدِ گرد (r5).
 *
 *  صفحهٔ یک بخش و کامپیوتر هیچ سرخط و کارتی نمی‌کشند — فقط همان بخشِ
 *  انتخاب‌شده دیده می‌شود.
 */
@Composable
fun SettingsGroup(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (LocalSettingsPick.current != null || LocalPhoneSettings.current !is PhoneSettingsMode.Root) {
    Column(modifier, content = content)
    return
  }
  Column(modifier.fillMaxWidth().padding(top = 14.dp)) {
    V3GroupTitle(title)
    V3Card(content = content)
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
 *
 *  درونِ صفحهٔ تنظیماتِ گوشی همان ردیفِ r5 است (`V3Row`): آیکنِ تک‌رنگ روی
 *  کاشیِ خاکستری، بی خطِ دور و بی رنگِ جدا برای هر ردیف.
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
  if (LocalPhoneSettings.current != null && trailing == null) {
    V3Row(icon = icon, label = title, modifier = modifier, value = value, description = description, onClick = onClick)
    return
  }
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
 *  درونِ تنظیماتِ گوشی تک‌رنگ است (r5): قرصِ سفید روی نوارِ خاکستری.
 */
@Composable
fun SegmentedChoice(
  options: List<Pair<String, String>>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (LocalPhoneSettings.current != null) {
    V3Segmented(options, selected, onSelect, modifier)
    return
  }
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
      val bg by animateColorAsState(
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

/** گزینه‌های تک‌رنگِ گوشی: قرصِ سفیدِ سایه‌دار روی نوارِ خاکستری */
@Composable
private fun V3Segmented(
  options: List<Pair<String, String>>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier,
) {
  val c = PhoneV3.colors
  Row(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(c.bg)
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    options.forEach { (id, label) ->
      val active = selected == id
      val bg by animateColorAsState(
        if (active) c.card else c.card.copy(alpha = 0f), tween(if (Motion.enabled) 200 else 0), label = "seg3",
      )
      Box(
        Modifier
          .weight(1f)
          .heightIn(min = 38.dp)
          .then(
            if (active) Modifier.shadow(2.dp, RoundedCornerShape(10.dp), clip = false, ambientColor = c.ink.copy(alpha = 0.12f), spotColor = c.ink.copy(alpha = 0.12f))
            else Modifier,
          )
          .clip(RoundedCornerShape(10.dp))
          .background(bg)
          .clickable(role = Role.RadioButton) { onSelect(id) }
          .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = v3Text(14, if (active) FontWeight.SemiBold else FontWeight.Medium, 20),
          color = if (active) c.ink else c.inkMuted,
          maxLines = 1,
        )
      }
    }
  }
}


/** کامپیوتر: عنوانِ بخشی که ستونِ راستِ تنظیمات نشان می‌دهد؛ null یعنی گوشی */
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
 *
 *  بخشی که `AppRoot` در فهرستش ندارد (تازه‌آمده‌ها مثلِ «زبان» و «همگام‌سازی»)
 *  خودکار کنارِ هم‌گروهش می‌نشیند (`SettingsLayout.deskOrder`) تا روی کامپیوتر
 *  هیچ بخشی بی‌راه نماند.
 */
@Composable
fun DeskSettings(sections: List<Pair<String, ImageVector>>, content: @Composable () -> Unit) {
  val all = remember(sections) {
    val given = sections.toMap()
    SettingsLayout.deskOrder(sections.map { it.first }).map { it to (given[it] ?: settingsIcon(it) ?: Icons.Outlined.Settings) }
  }
  var pick by rememberSaveable { mutableStateOf(all.first().first) }
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
      all.forEach { (title, icon) ->
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
