package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TwoPane
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules

/**
 *  تنظیماتِ صفحهٔ پهن (کامپیوتر، تبلتِ افقی — `shell.wide`): فهرستِ بخش‌ها در ستونِ آغاز (همان گروه‌ها، بی بخش‌های فقط‌گوشی —
 *  `SettingsLayout.deskSections`) و صفحهٔ همان بخش در ستونِ پایان — **همان** صفحه‌های
 *  گوشی (`SettingsPages`)، بی دکمهٔ برگشت. پیوندِ هر بخش (`settings/security/app-lock`)
 *  همان بخش را باز انتخاب می‌کند.
 */
@Composable
internal fun RouteScope.DeskSettingsScreen(selected: String?) {
  //  کامپیوتر دوربین و نوارِ پایینِ گوشی را ندارد؛ تبلتِ پهن دارد
  val desk = if (Platform.DESKTOP) SettingsLayout.deskSections else SettingsLayout.sections
  var pick by rememberSaveable(selected) { mutableStateOf(selected?.takeIf { it in desk } ?: desk.first()) }
  val facts = rememberSettingsFacts(theme)
  val extra = remember(access) { V3Modules.settingsEntries(access) }
  val lockToggle = rememberLockToggle()
  V3TwoPane(
    listWidth = 320.dp,
    list = {
      SettingsPageFrame(title = Str.settings, onBack = null, largeTitle = false) {
        items(SettingsLayout.GROUPS, key = { it.title }) { g ->
          val rows = g.sections.filter { it in desk || SettingsLayout.isLink(it) }
          val more = extra.filter { it.group == g.title }
          if (rows.isNotEmpty() || more.isNotEmpty()) {
            V3Section(SettingsLayout.groupLabel(g.title)) {
              rows.forEachIndexed { i, key ->
                if (i > 0) V3Divider(start = ROW_DIVIDER_START)
                Box {
                  SettingsRow(key, facts, onClick = {
                    val link = SettingsLayout.LINKS[key]
                    if (link != null) nav.open(link) else pick = key
                  }, onLockToggle = lockToggle)
                  if (key == pick) SelectedMark(Modifier.align(Alignment.CenterStart))
                }
              }
              more.forEach { e ->
                V3Divider(start = ROW_DIVIDER_START)
                V3ListRow(e.label(), icon = e.icon, tile = false, onClick = { nav.open(e.route) })
              }
            }
          }
        }
      }
    },
    detail = {
      AnimatedContent(pick, transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) }, label = "deskSettings") { key ->
        SettingsPages.of(key)?.content?.invoke(this@DeskSettingsScreen, null)
      }
    },
  )
}

/** نشانِ ردیفِ انتخاب‌شده: نوارِ برند در لبهٔ آغاز (رنگ تنها نیست: صفحهٔ کنار همان بخش است) */
@Composable
private fun SelectedMark(modifier: Modifier) {
  Box(modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(PhoneV3Roles.focus(PhoneV3.colors)))
}

