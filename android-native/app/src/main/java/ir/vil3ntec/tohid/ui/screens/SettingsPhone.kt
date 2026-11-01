package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths

/** نامِ تم در تنظیمات (بندِ C: روشن / تیره / خودکار) */
internal fun themeLabel(t: ThemeChoice): String = when (t) {
  ThemeChoice.LIGHT -> "روشن"
  ThemeChoice.DARK -> "تیره"
  ThemeChoice.SYSTEM -> "خودکار"
}

/**
 *  مسیرِ تنظیماتی که باز است (`settings`، `settings/security`،
 *  `settings/security/app-lock`) — مسیرِ تنظیمات (`SettingsRoutes`) می‌دهد.
 *  `group` یعنی صفحهٔ همان گروه، `section` یعنی صفحهٔ همان بخش.
 */
data class SettingsTarget(val group: String? = null, val section: String? = null)

val LocalSettingsTarget = compositionLocalOf { SettingsTarget() }

/**
 *  قابِ تنظیماتِ گوشی (r5): زمینهٔ خاکستریِ روشن، سربرگ با دکمهٔ گردِ برگشت و
 *  عنوانِ وسط، و یا فهرستِ گروه‌ها، یا **صفحهٔ یک گروه** (فقط ردیف‌های همان گروه —
 *  «بیشتر ⇐ تنظیمات ⇐ امنیت» مستقیم همین‌جا می‌آید)، یا صفحهٔ یک بخش.
 *
 *  با مسیریاب (برنامه): هر ردیف مسیرِ خودش را باز می‌کند (`SettingsPaths.section`)
 *  و «برگشت» به همان جایی می‌رود که از آن آمده — جابه‌جایی با حرکتِ پوسته.
 *  بی مسیریاب (پیش‌نمایشِ عکس‌ها): همان ناوبریِ درونیِ پیشین با `SettingsNav`.
 */
@Composable
internal fun PhoneSettingsFrame(onBack: (() -> Unit)?, body: LazyListScope.() -> Unit) {
  val nav = LocalV3Nav.current
  if (nav != null) RoutedSettingsFrame(LocalSettingsTarget.current, onBack ?: { nav.back(); Unit }, { nav.open(SettingsPaths.section(it)) }, body)
  else LocalSettingsFrame(onBack, body)
}

@Composable
private fun RoutedSettingsFrame(target: SettingsTarget, onBack: () -> Unit, open: (String) -> Unit, body: LazyListScope.() -> Unit) {
  val mode = remember(target) {
    when {
      target.section != null -> PhoneSettingsMode.Detail(target.section)
      target.group != null -> PhoneSettingsMode.Group(target.group, open)
      else -> PhoneSettingsMode.Root(open)
    }
  }
  val title = target.section?.let(SettingsLayout::labelOf) ?: target.group ?: "تنظیمات"
  SettingsFrameBody(title, onBack, mode, rememberLazyListState(), top = if (mode is PhoneSettingsMode.Root) 0.dp else 8.dp, body)
}

/** ناوبریِ درونیِ پیشین (بی مسیریاب): ریشه ⇄ یک بخش، با `SettingsNav.pending` */
@Composable
private fun LocalSettingsFrame(onBack: (() -> Unit)?, body: LazyListScope.() -> Unit) {
  var detail by rememberSaveable { mutableStateOf<String?>(null) }
  var group by rememberSaveable { mutableStateOf<String?>(null) }
  val rootList = rememberLazyListState()
  val root = remember { PhoneSettingsMode.Root { detail = it } }

  val pending = SettingsNav.pending.value
  LaunchedEffect(pending) {
    val req = pending ?: return@LaunchedEffect
    SettingsNav.pending.value = null
    detail = req.detail
    group = if (req.detail == null) req.group else null
  }

  BackHandler(enabled = detail != null || group != null) { if (detail != null) detail = null else group = null }

  V3Pages(detail ?: group, depth = { if (it == null) 0 else 1 }) { key ->
    val mode = remember(key) {
      when {
        detail != null -> PhoneSettingsMode.Detail(key!!)
        group != null -> PhoneSettingsMode.Group(key!!) { detail = it }
        else -> root
      }
    }
    SettingsFrameBody(
      title = when (mode) {
        is PhoneSettingsMode.Detail -> SettingsLayout.labelOf(mode.title)
        is PhoneSettingsMode.Group -> mode.title
        else -> "تنظیمات"
      },
      onBack = if (key != null) ({ if (detail != null) detail = null else group = null }) else (onBack ?: { AppNav.go(SettingsNav.origin) }),
      mode = mode,
      list = if (key == null) rootList else rememberLazyListState(),
      top = if (key == null) 0.dp else 8.dp,
      body = body,
    )
  }
}

@Composable
private fun SettingsFrameBody(
  title: String,
  onBack: () -> Unit,
  mode: PhoneSettingsMode,
  list: androidx.compose.foundation.lazy.LazyListState,
  top: androidx.compose.ui.unit.Dp,
  body: LazyListScope.() -> Unit,
) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.statusBars)) {
    CompositionLocalProvider(LocalPhoneSettings provides mode) {
      V3Header(title = title, onBack = onBack)
      LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        state = list,
        //  r5: کارت‌ها ۲۰dp از لبه
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = 32.dp),
        content = body,
      )
    }
  }
}
