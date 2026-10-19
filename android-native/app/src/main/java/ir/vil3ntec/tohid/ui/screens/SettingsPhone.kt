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

/** نامِ تم در تنظیمات (بندِ C: روشن / تیره / خودکار) */
internal fun themeLabel(t: ThemeChoice): String = when (t) {
  ThemeChoice.LIGHT -> "روشن"
  ThemeChoice.DARK -> "تیره"
  ThemeChoice.SYSTEM -> "خودکار"
}

/**
 *  قابِ تنظیماتِ گوشی (r5 «After»): زمینهٔ خاکستریِ روشن، سربرگ با دکمهٔ گردِ
 *  برگشت و عنوانِ وسط، و یا فهرستِ گروه‌ها یا صفحهٔ یک بخش.
 *
 *  ناوبریِ درونی است — `AppRoot` فقط مسیرِ «settings» را می‌شناسد: زدنِ یک
 *  ردیف صفحهٔ همان بخش را از سمتِ پایان می‌آورد (`V3Pages`)، و «برگشت» (دکمه
 *  یا کلیدِ برگشتِ گوشی) اول به فهرست برمی‌گردد. جای اسکرولِ فهرست هنگامِ
 *  برگشت می‌ماند.
 */
@Composable
internal fun PhoneSettingsFrame(onBack: (() -> Unit)?, body: LazyListScope.() -> Unit) {
  val c = PhoneV3.colors
  var detail by rememberSaveable { mutableStateOf<String?>(null) }
  val rootList = rememberLazyListState()
  val root = remember { PhoneSettingsMode.Root { detail = it } }

  //  «بیشتر ⇐ تنظیمات ⇐ گروه» (و پیش‌نمایشِ عکس‌ها): روی یک گروه یا یک بخش باز شو
  val pending = SettingsNav.pending.value
  LaunchedEffect(pending) {
    val req = pending ?: return@LaunchedEffect
    SettingsNav.pending.value = null
    if (req.detail != null) {
      detail = req.detail
    } else {
      detail = null
      val i = SettingsLayout.groupIndex(req.group.orEmpty())
      if (i >= 0) rootList.scrollToItem(i)
    }
  }

  BackHandler(enabled = detail != null) { detail = null }

  Column(
    Modifier
      .fillMaxSize()
      .background(c.bg)
      .windowInsetsPadding(WindowInsets.statusBars),
  ) {
    V3Pages(detail, depth = { if (it == null) 0 else 1 }) { d ->
      val mode = remember(d) { if (d == null) root else PhoneSettingsMode.Detail(d) }
      CompositionLocalProvider(LocalPhoneSettings provides mode) {
        Column(Modifier.fillMaxSize()) {
          V3Header(
            title = d?.let(SettingsLayout::labelOf) ?: "تنظیمات",
            onBack = if (d != null) ({ detail = null }) else (onBack ?: { AppNav.go(SettingsNav.origin) }),
          )
          LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = if (d == null) rootList else rememberLazyListState(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (d == null) 0.dp else 8.dp, bottom = 32.dp),
            content = body,
          )
        }
      }
    }
  }
}
